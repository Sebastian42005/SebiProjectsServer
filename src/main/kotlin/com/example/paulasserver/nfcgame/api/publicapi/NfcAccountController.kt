package com.example.paulasserver.nfcgame.api.publicapi

import com.example.paulasserver.nfcgame.api.dto.DeviceClaimRequest
import com.example.paulasserver.nfcgame.api.dto.DeviceResponse
import com.example.paulasserver.nfcgame.application.NfcGameMapper
import com.example.paulasserver.nfcgame.persistence.repository.NfcDeviceRepository
import com.example.paulasserver.security.AuthenticatedUser
import jakarta.validation.Valid
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/public/account")
class NfcAccountController(
    private val deviceRepository: NfcDeviceRepository,
    private val mapper: NfcGameMapper,
) {
    @GetMapping("/devices")
    fun devices(
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ): List<DeviceResponse> {
        val accountId = userAccountId(user)
        return deviceRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).map(mapper::toDeviceResponse)
    }

    @PostMapping("/devices/claim")
    fun claimDevice(
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
        @Valid @RequestBody request: DeviceClaimRequest,
    ): DeviceResponse {
        val accountId = userAccountId(user)
        val pairingCode = request.pairingCode?.trim()?.takeIf { it.isNotBlank() }
            ?: request.deviceKey?.trim()?.takeIf { it.isNotBlank() }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Pairing code is required")
        val device = deviceRepository.findByPairingCode(pairingCode)
            ?: request.deviceKey?.trim()?.takeIf { it.isNotBlank() }?.let(deviceRepository::findByDeviceKey)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Pairing code not found")
        if (device.accountId != null && device.accountId != accountId) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Device is already linked to another account")
        }
        device.accountId = accountId
        device.active = true
        return mapper.toDeviceResponse(deviceRepository.save(device))
    }

    private fun userAccountId(user: AuthenticatedUser?): Long =
        user?.id ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")
}
