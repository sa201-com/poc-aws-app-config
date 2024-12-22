package com.sa201.poc_aws_app_config

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class AwsAppConfigTbDemoApplication

fun main(args: Array<String>) {
    runApplication<AwsAppConfigTbDemoApplication>(*args)
}
