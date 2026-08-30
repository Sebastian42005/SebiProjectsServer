package com.example.sebisprojectsserver.publisher

import org.eclipse.paho.client.mqttv3.MqttClient
import org.eclipse.paho.client.mqttv3.MqttConnectOptions
import org.eclipse.paho.client.mqttv3.MqttMessage
import org.eclipse.paho.client.mqttv3.persist.MemoryPersistence
import org.springframework.stereotype.Service

@Service
class MqttPublisher(
    private val mqttProperties: MqttProperties,
) {

    private lateinit var client: MqttClient

    @Synchronized
    private fun connectIfNeeded() {
        if (::client.isInitialized && client.isConnected) {
            return
        }

        client = MqttClient(mqttProperties.brokerUrl, MqttClient.generateClientId(), MemoryPersistence())
        val options = MqttConnectOptions().apply {
            if (mqttProperties.username.isNotBlank()) {
                userName = mqttProperties.username
            }
            if (mqttProperties.password.isNotBlank()) {
                password = mqttProperties.password.toCharArray()
            }
            connectionTimeout = 3
            isAutomaticReconnect = true
        }
        options.isCleanSession = true
        client.connect(options)
    }

    fun publish(topic: String, payload: String) {
        connectIfNeeded()
        val message = MqttMessage(payload.toByteArray())
        client.publish(topic, message)
    }
}
