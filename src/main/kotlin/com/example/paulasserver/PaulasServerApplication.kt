package com.example.paulasserver

import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
@ConfigurationPropertiesScan
class PaulasServerApplication

fun main(args: Array<String>) {
	runApplication<PaulasServerApplication>(*args)
}
