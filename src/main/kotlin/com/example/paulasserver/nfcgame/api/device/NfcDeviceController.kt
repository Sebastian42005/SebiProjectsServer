package com.example.paulasserver.nfcgame.api.device

import com.example.paulasserver.nfcgame.api.dto.DeviceEventRequest
import com.example.paulasserver.nfcgame.api.dto.DeviceEventResponse
import com.example.paulasserver.nfcgame.api.dto.DeviceProvisioningResponse
import com.example.paulasserver.nfcgame.api.dto.DeviceRequest
import com.example.paulasserver.nfcgame.application.device.NfcDeviceEventService
import jakarta.validation.Valid
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestHeader
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/device")
class NfcDeviceController(
    private val deviceEventService: NfcDeviceEventService,
) {
    @PostMapping("/events")
    fun handleEvent(@Valid @RequestBody request: DeviceEventRequest): DeviceEventResponse =
        deviceEventService.handleEvent(request)

    @PostMapping("/register")
    fun registerDevice(@Valid @RequestBody request: DeviceRequest): DeviceProvisioningResponse =
        deviceEventService.registerDevice(request)

    @GetMapping("/sessions/{sessionId}/screen")
    fun currentScreen(
        @PathVariable sessionId: UUID,
        @RequestHeader("X-Device-Id") deviceId: String,
        @RequestHeader("X-Device-Key") deviceKey: String,
    ): DeviceEventResponse = deviceEventService.currentScreen(deviceId, deviceKey, sessionId)

    @GetMapping("/health")
    fun health() = deviceEventService.health()
}
