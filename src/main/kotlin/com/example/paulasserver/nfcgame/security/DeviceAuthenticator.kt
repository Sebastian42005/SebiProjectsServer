package com.example.paulasserver.nfcgame.security

import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.repository.NfcDeviceRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class DeviceAuthenticator(
    private val deviceRepository: NfcDeviceRepository,
) {
    fun authenticate(deviceId: String, deviceKey: String): NfcDevice {
        val device = deviceRepository.findByNameAndDeviceKey(deviceId.trim(), deviceKey)
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid device credentials")
        if (!device.active) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Device is inactive")
        }
        device.lastSeenAt = Instant.now()
        return deviceRepository.save(device)
    }
}
