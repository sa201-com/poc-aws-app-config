package com.sa201.poc_aws_app_config

import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.context.event.ApplicationReadyEvent
import org.springframework.context.annotation.Lazy
import org.springframework.context.event.EventListener
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import org.springframework.web.context.annotation.ApplicationScope
import java.time.Instant

@ApplicationScope
@Component
@Lazy(false)
class ApplicationScopeConfig(
    @Autowired
    val utility: Utility
) {
    val logger = LoggerFactory.getLogger(this::class.java)

    var lastCacheTime = Instant.MIN

    var latestConfig: String? = null

    @EventListener(ApplicationReadyEvent::class, condition = """@environment.getProperty("aws_app_config.scheduler_enabled")""")
    @Scheduled(
        fixedRateString = "\${aws_app_config.poll_time_to_agent}",
        initialDelayString = "\${aws_app_config.poll_time_to_agent}"
    )
    fun updateConfig() {
        logger.info("awsConfig is updating")

        val responseBody = utility.requestAppConfig()

        latestConfig = responseBody!!
        lastCacheTime = Instant.now()
    }
}