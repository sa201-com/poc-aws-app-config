package com.sa201.poc_aws_app_config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.context.annotation.Configuration
import org.springframework.web.client.RestClient

@Configuration
class Utility(
    @Value("\${aws_app_config.endpoint}")
    private val appConfigEndpoint: String,
    @Value("\${aws_app_config.application_id}")
    private val applicationId: String,
    @Value("\${aws_app_config.environment_id}")
    private val environmentId: String,
    @Value("\${aws_app_config.configuration_id}")
    private val configurationId: String,
) {
    private final val logger = LoggerFactory.getLogger(this.javaClass)

    init {
        logger.info("applicationId: $applicationId")
        logger.info("environmentId: $environmentId")
        logger.info("configurationId: $configurationId")
    }

    fun getAwsConfigPath(): String {
        return """/applications/${applicationId}/environments/${environmentId}/configurations/${configurationId}"""
    }

    fun requestAppConfig(): String? {
        val getAwsConfigPath = getAwsConfigPath()

        logger.info("requestAppConfig - appConfigEndpoint: $appConfigEndpoint, getAwsConfigPath: $getAwsConfigPath")

        val httpClient = RestClient
            .builder()
            .baseUrl(appConfigEndpoint)
            .build()

        val response = httpClient
            .get()
            .uri(getAwsConfigPath)
            .retrieve()
            .body(String::class.java)

        logger.debug("requestAppConfig - response: $response")

        return response
    }
}