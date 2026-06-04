package com.example.paulasserver.nfcgame.api.websocket

import org.springframework.context.annotation.Configuration
import org.springframework.messaging.simp.config.MessageBrokerRegistry
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker
import org.springframework.web.socket.config.annotation.StompEndpointRegistry
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer

@Configuration
@EnableWebSocketMessageBroker
class NfcWebSocketConfig : WebSocketMessageBrokerConfigurer {
    override fun configureMessageBroker(registry: MessageBrokerRegistry) {
        registry.enableSimpleBroker("/topic")
        registry.setApplicationDestinationPrefixes("/app")
    }

    override fun registerStompEndpoints(registry: StompEndpointRegistry) {
        registry.addEndpoint("/ws/nfc").setAllowedOriginPatterns("*")
        registry.addEndpoint("/ws/nfc").setAllowedOriginPatterns("*").withSockJS()
        registry.addEndpoint("/api/ws/nfc").setAllowedOriginPatterns("*")
        registry.addEndpoint("/api/ws/nfc").setAllowedOriginPatterns("*").withSockJS()
    }
}
