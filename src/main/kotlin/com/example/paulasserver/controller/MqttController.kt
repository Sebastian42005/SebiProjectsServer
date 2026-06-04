package com.example.paulasserver.controller

import com.example.paulasserver.service.MqttService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/home-assistant")
class MqttController(private val mqttService: MqttService) {

    @PostMapping("/send-alexa")
    fun sendMessageToAlexa(@RequestParam("message") message: String): ResponseEntity<String> {
        return mqttService.send("alexa/say", message)
    }

    @PostMapping("/nerv-sebi")
    fun nervSebi(): ResponseEntity<String> {
        return mqttService.send("nerv", message = null)
    }

    @PostMapping("/play-voice-message")
    fun playVoiceMessage(): ResponseEntity<String> {
        return mqttService.send("voice-message", message = null)
    }
}
