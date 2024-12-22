package com.sa201.poc_aws_app_config

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.MediaType
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.servlet.ModelAndView
import java.time.Duration
import java.time.Instant

@RestController
class RestController(
    @Autowired
    val config: ApplicationScopeConfig,
    @Autowired
    val utility: Utility
) {

    final private val logger = LoggerFactory.getLogger(javaClass)
    val mapper = jacksonObjectMapper()

    fun logRequestTime(requestId: String): Instant {
        val now = Instant.now()
        logger.debug("$requestId is requested at $now")

        return now
    }

    fun logResponseTime(requestId: String, requestTime: Instant): Long {
        val now = Instant.now()
        val processingTime = Duration.between(requestTime, now).toMillis()
        logger.debug("$requestId is responded at $now, It takes ${processingTime} milliseconds")

        return processingTime
    }

    /**
     *  Returns AWS AppConfig configuration at **request** time
     */
    @GetMapping(
        path = ["/config-update-on-request-scope"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun onRequestScope(): Map<String, Any?> {
        val requested = logRequestTime("requestId")

        val responseBody = utility.requestAppConfig()
        var jsonBody: JsonNode = mapper.createObjectNode()

        if(responseBody != null){
            jsonBody = mapper.readTree(responseBody)
        }

        val processingTime = logResponseTime("requestId", requested)

        return mapOf(
            "processing_time" to processingTime,
            "config_value" to jsonBody
        )
    }


    /**
     *  Returns AWS AppConfig configuration at **scheduled** time
     *  <img src="{@docRoot/}/doc/img/how_aws_app_config_agent_works.png" />
     *
     * @return      the image at the specified URL
     * @see         com.sa201.poc_aws_app_config.ApplicationScopeConfig
     */
    @GetMapping(
        path = ["/config-update-on-application-scope"],
        produces = [MediaType.APPLICATION_JSON_VALUE]
    )
    fun onApplicationScope(): Map<String, Any?> {
        val requested = logRequestTime("requestId")
        logger.info("lastCacheTime: ${config.lastCacheTime}")

        val processingTime = logResponseTime("requestId", requested)
        var jsonBody: JsonNode = mapper.createObjectNode()

        if(config.latestConfig != null){
            jsonBody = mapper.readTree(config.latestConfig)
        }

        return mapOf(
            "processing_time" to processingTime,
            "config_value" to jsonBody
        )
    }

    @GetMapping("/welcome-instant")
    fun welcomeInstant(
        mav: ModelAndView,
    ): ModelAndView {
        val latestConfig = utility.requestAppConfig()
        return setTemplate(mav, latestConfig)
    }

    @GetMapping("/welcome-cached")
    fun welcomeCached(
        mav: ModelAndView,
    ): ModelAndView {
        return setTemplate(mav, config.latestConfig, config.lastCacheTime)
    }

    fun setTemplate(mav: ModelAndView, config: String?, cacheTime: Instant? = null): ModelAndView{
        mav.viewName = "welcome"
        val latestConfig = utility.requestAppConfig()
        if(latestConfig == null){
            mav.addObject("welcomeMessage", "App config is not connected")
        } else {
            val jsonBody = mapper.readTree(latestConfig)
            val isUnderMaintenance = jsonBody["Maintenance"]["flag"].booleanValue()
            if(isUnderMaintenance){
                mav.viewName = "maintenance"

                val welcomeMsg = jsonBody["Maintenance"]["msg"].textValue()
                mav.addObject("maintenanceMessage", welcomeMsg)
            } else {
                val welcomeMsg = jsonBody["Welcome"]["msg"].textValue()
                mav.addObject("welcomeMessage", welcomeMsg)
            }
        }

        if(cacheTime != null){
            mav.addObject("cacheTime", cacheTime.toString())
        } else {
            mav.addObject("cacheTime", "INSTANT")
        }

        return mav
    }
}
