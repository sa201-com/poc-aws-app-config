package com.sa201.poc_aws_app_config

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.*
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.retry.support.RetryTemplate
import org.springframework.test.context.DynamicPropertyRegistry
import org.springframework.test.context.DynamicPropertySource
import org.testcontainers.containers.GenericContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import org.testcontainers.utility.DockerImageName
import org.testcontainers.utility.MountableFile
import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import kotlin.io.path.fileSize
import kotlin.io.path.writeText
import kotlin.random.Random

private const val applicationId: String = "unit_app"
private const val environmentId: String = "test_runtime"
private const val configurationId: String = "main_json"

@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties = [
        "aws_app_config.scheduler_enabled=false",
        "aws_app_config.poll_time_to_agent=9999999",
        // For test on host
        // "aws_app_config.endpoint=http://localhost:2772",
         "aws_app_config.application_id=$applicationId",
         "aws_app_config.environment_id=$environmentId",
         "aws_app_config.configuration_id=$configurationId",
    ]
)
@TestMethodOrder(MethodOrderer.OrderAnnotation::class)
@Testcontainers
@DisplayName("Aws AppConfig Agent response spec")
class AwsAppConfigAgentResponseSpec(
    @Autowired
    val config: ApplicationScopeConfig
) {

    val logger = LoggerFactory.getLogger(this::class.java)


    companion object {
        val firstVerificationValue = Random.nextLong()
        val secondVerificationValue = Random.nextLong()

        private val containerDevelopmentDirectory = "/tmp/aws_app_config"
        private val appConfigFileName = "$applicationId:$environmentId:$configurationId.json"
        private val appAgentPort = 2772

        lateinit var tmpDir: Path
        val mapper = jacksonObjectMapper()

        @Container
        val appConfigAgentContainer = GenericContainer(
            DockerImageName.parse("public.ecr.aws/aws-appconfig/aws-appconfig-agent:2.0.13147")
        )
            .withEnv(mapOf(
                "AWS_REGION" to "ap-northeast-1",
                "LOG_LEVEL" to "debug",
                "POLL_INTERVAL" to "2",
                "LOCAL_DEVELOPMENT_DIRECTORY" to containerDevelopmentDirectory
            ))
            .withExposedPorts(appAgentPort)

        @JvmStatic
        @BeforeAll
        fun before() {
            tmpDir = Files.createTempDirectory("aws_app_config_test")
        }

        @JvmStatic
        @AfterAll
        fun after(): Unit {
            tmpDir.toFile().deleteRecursively()
        }

        @JvmStatic
        @DynamicPropertySource
        fun properties(registry: DynamicPropertyRegistry) {
            val hostPort = appConfigAgentContainer.getMappedPort(appAgentPort)
            registry.add("aws_app_config.endpoint", {"http://localhost:${hostPort}"})
        }
    }

    @Test
    @Order(1)
    @DisplayName("01: Add configuration profile")
    fun writeConfiguration() {
        val appConfigFile = tmpDir.resolve("1.json")
        appConfigFile.writeText(
            // language=json
            """
            {
              "feature_A": {
                "enabled": false,
                "verification_key": $firstVerificationValue
              }
            }
            """.trimIndent()
        )

        assertThat(appConfigFile.fileSize()).isGreaterThan(0)

        val mountingFile = MountableFile.forHostPath(appConfigFile)
        appConfigAgentContainer.copyFileToContainer(mountingFile, "$containerDevelopmentDirectory/$appConfigFileName")
    }

    @Test
    @Order(2)
    @DisplayName("02: Retrieve AppConfig configuration on the first try")
    fun retrieveConfiguration01() {
        config.updateConfig()

        assertThat(config.latestConfig).isNotNull()
        val responseJson = mapper.readTree(config.latestConfig)

        assertThat(responseJson["feature_A"]["enabled"]).isNotNull()
        assertThat(responseJson["feature_A"]["enabled"].booleanValue()).isFalse()
        assertThat(responseJson["feature_A"]["verification_key"]).isNotNull()
        assertThat(responseJson["feature_A"]["verification_key"].longValue()).isEqualTo(firstVerificationValue)
    }

    @Test
    @Order(3)
    @DisplayName("03: Update configuration profile")
    fun updateConfiguration() {
        val appConfigFile = tmpDir.resolve("2.json")
        appConfigFile.writeText(
            // language=json
            """
            {
              "feature_A": {
                "enabled": false,
                "verification_key": $secondVerificationValue
              }
            }
            """.trimIndent()
        )

        assertThat(appConfigFile.fileSize()).isGreaterThan(0)

        val mountingFile = MountableFile.forHostPath(appConfigFile)
        appConfigAgentContainer.copyFileToContainer(mountingFile, "$containerDevelopmentDirectory/$appConfigFileName")
    }

    @Test
    @Order(4)
    @DisplayName("04: Retrieve updated AppConfig configuration")
    fun retrieveConfigurationAgain() {
        RetryTemplate
            .builder()
            .maxAttempts(5)
            .fixedBackoff(Duration.ofSeconds(2))
            .retryOn { ex ->
                if(ex is AssertionError){
                    logger.info("AssertionError was thrown: ${ex.stackTraceToString()}")
                    true
                } else {
                    false
                }
            }
            .build()
            .execute<Any, RuntimeException>{ ctx ->
                config.updateConfig()

                logger.debug("latestConfig: ${config.latestConfig}")

                assertThat(config.latestConfig).isNotNull()
                val responseJson = mapper.readTree(config.latestConfig)

                assertThat(responseJson["feature_A"]["enabled"]).isNotNull()
                assertThat(responseJson["feature_A"]["enabled"].booleanValue()).isFalse()
                assertThat(responseJson["feature_A"]["verification_key"]).isNotNull()
                assertThat(responseJson["feature_A"]["verification_key"].longValue()).isEqualTo(secondVerificationValue)

                logger.info("It has been retrieved ${ctx.retryCount} times")
            }
    }

}
