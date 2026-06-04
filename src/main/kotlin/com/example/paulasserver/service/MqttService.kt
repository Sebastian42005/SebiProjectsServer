package com.example.paulasserver.service

import com.example.paulasserver.publisher.MqttPublisher
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service

@Service
class MqttService(val mqttPublisher: MqttPublisher) {

    fun send(topic: String, message: String?): ResponseEntity<String> {
        return try {
            mqttPublisher.publish(topic, message ?: "")
            ResponseEntity.ok("Message sent")
        } catch (e: Exception) {
            ResponseEntity.status(500).body(e.message)
        }
    }
}