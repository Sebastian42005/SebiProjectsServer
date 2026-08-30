package com.example.sebisprojectsserver.publisher

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "app.mqtt")
data class MqttProperties(
    val brokerUrl: String = "tcp://home.sebi4.com:1883",
    val username: String = "homeassistant",
    val password: String = "S4Bi2OO5",
)
