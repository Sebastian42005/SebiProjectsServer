package com.example.paulasserver.nfcgame.application.device

import com.example.paulasserver.nfcgame.api.dto.DeviceEventRequest
import com.example.paulasserver.nfcgame.api.dto.DeviceEventResponse
import com.example.paulasserver.nfcgame.api.dto.DeviceProvisioningResponse
import com.example.paulasserver.nfcgame.api.dto.DeviceRequest
import com.example.paulasserver.nfcgame.api.dto.MoneyTransferRequest
import com.example.paulasserver.nfcgame.api.dto.ScreenModel
import com.example.paulasserver.nfcgame.application.NfcGameMapper
import com.example.paulasserver.nfcgame.application.publicapi.NfcPublicQueryService
import com.example.paulasserver.nfcgame.application.session.SessionStateMachineService
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.EventType
import com.example.paulasserver.nfcgame.domain.ScreenType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcMoneyTransaction
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionEvent
import com.example.paulasserver.nfcgame.persistence.repository.NfcCardRepository
import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.repository.NfcDeviceRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameSessionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcMoneyTransactionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionAccountRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionEventRepository
import com.example.paulasserver.nfcgame.security.DeviceAuthenticator
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionSynchronization
import org.springframework.transaction.support.TransactionSynchronizationManager
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom

@Service
class NfcDeviceEventService(
    private val deviceAuthenticator: DeviceAuthenticator,
    private val stateMachineService: SessionStateMachineService,
    private val eventRepository: NfcSessionEventRepository,
    private val accountRepository: NfcSessionAccountRepository,
    private val moneyTransactionRepository: NfcMoneyTransactionRepository,
    private val cardRepository: NfcCardRepository,
    private val deviceRepository: NfcDeviceRepository,
    private val sessionRepository: NfcGameSessionRepository,
    private val playerRepository: NfcPlayerRepository,
    private val publicQueryService: NfcPublicQueryService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val mapper: NfcGameMapper,
    private val objectMapper: ObjectMapper,
) {
    private val activeInputStatuses = listOf(
        SessionStatus.LOBBY,
        SessionStatus.CONFIGURING,
        SessionStatus.BUILDING_TEAMS,
        SessionStatus.READY,
        SessionStatus.RUNNING,
    )
    private val pairingCodeRandom = SecureRandom()

    @Transactional
    fun handleEvent(request: DeviceEventRequest): DeviceEventResponse {
        val device = deviceAuthenticator.authenticate(request.deviceId, request.deviceKey)
        val requestSessionId = parseUuid(request.sessionId)
        val result = when (request.eventType) {
            EventType.CARD_SCANNED,
            EventType.GAME_CARD_SCANNED,
            EventType.PLAYER_CARD_SCANNED,
            -> {
                val cardUid = request.cardUid?.takeIf { it.isNotBlank() }
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "cardUid is required for card scan events")
                stateMachineService.handleCardScan(device, cardUid)
            }

            EventType.JOYSTICK_LONG_PRESS,
            EventType.RESET_TRIGGERED,
            -> stateMachineService.handleReset(device)

            else -> {
                val sessionId = requestSessionId
                    ?: device.accountId?.let {
                        sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(it, activeInputStatuses)?.id
                    }
                    ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "sessionId is required")
                stateMachineService.handleInput(
                    sessionId,
                    request.eventType,
                    request.payload,
                )
            }
        }

        eventRepository.save(
            NfcSessionEvent().apply {
                sessionId = result.session?.id ?: requestSessionId
                deviceId = requireNotNull(device.id)
                eventType = request.eventType
                payloadJson = objectMapper.writeValueAsString(
                    request.payload + mapOf(
                        "cardUid" to request.cardUid,
                        "occurredAt" to request.occurredAt?.toString(),
                        "deviceStateKey" to request.currentStateKey,
                        "timelineMessage" to result.timelineMessage,
                    ),
                )
            },
        )
        publishSessionUpdatesAfterCommit(result.session?.id)

        val scanFeedback = if (request.eventType in setOf(EventType.CARD_SCANNED, EventType.GAME_CARD_SCANNED, EventType.PLAYER_CARD_SCANNED)) {
            resolveScanFeedback(request.cardUid)
        } else {
            null
        }

        return DeviceEventResponse(
            sessionId = result.session?.id,
            status = result.session?.status,
            currentStateKey = result.session?.currentStateKey,
            screen = result.screen,
            effects = result.effects,
            errors = result.errors,
            scannedCardType = scanFeedback?.cardType,
            scannedPlayerName = scanFeedback?.playerName,
        )
    }

    fun currentScreen(deviceId: String, deviceKey: String, sessionId: java.util.UUID): DeviceEventResponse {
        val device = deviceAuthenticator.authenticate(deviceId, deviceKey)
        val session = sessionRepository.findById(sessionId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        if (device.accountId != null && session.accountId != device.accountId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        val result = stateMachineService.currentScreen(sessionId)
        return DeviceEventResponse(
            sessionId = result.session?.id,
            status = result.session?.status,
            currentStateKey = result.session?.currentStateKey,
            screen = result.screen,
            effects = result.effects,
            errors = result.errors,
        )
    }

    @Transactional
    fun transferMoney(request: MoneyTransferRequest) {
        val from = accountRepository.findById(request.fromAccountId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Source account not found")
        }
        val to = accountRepository.findById(request.toAccountId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Target account not found")
        }
        if (from.sessionId != request.sessionId || to.sessionId != request.sessionId) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Accounts do not belong to the session")
        }
        if (from.balance < request.amount) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Insufficient balance")
        }
        from.balance = from.balance.subtract(request.amount)
        to.balance = to.balance.add(request.amount)
        accountRepository.save(from)
        accountRepository.save(to)
        moneyTransactionRepository.save(
            NfcMoneyTransaction().apply {
                sessionId = request.sessionId
                fromAccountId = request.fromAccountId
                toAccountId = request.toAccountId
                amount = request.amount
                initiatedByPlayerId = request.initiatedByPlayerId
            },
        )
        publishSessionUpdatesAfterCommit(request.sessionId)
    }

    fun health() = ScreenModel(
        screenType = ScreenType.MESSAGE,
        title = "Device API ok",
        subtitle = "Backend erreichbar",
    )

    fun registerDevice(request: DeviceRequest): DeviceProvisioningResponse {
        val name = request.name.trim()
        val existing = deviceRepository.findByName(name)
        if (existing != null) {
            if (existing.deviceKey != request.deviceKey) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "Device id already exists with another key")
            }
            existing.active = request.active
            if (existing.pairingCode.isNullOrBlank()) {
                existing.pairingCode = generatePairingCode()
            }
            return toProvisioningResponse(deviceRepository.save(existing))
        }
        val device = NfcDevice().apply {
            this.name = name
            deviceKey = request.deviceKey
            pairingCode = generatePairingCode()
            active = request.active
        }
        return toProvisioningResponse(deviceRepository.save(device))
    }

    private fun generatePairingCode(): String {
        repeat(30) {
            val code = pairingCodeRandom.nextInt(1_000_000).toString().padStart(6, '0')
            if (deviceRepository.findByPairingCode(code) == null) {
                return code
            }
        }
        throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not generate pairing code")
    }

    private fun toProvisioningResponse(device: NfcDevice) = DeviceProvisioningResponse(
        id = requireNotNull(device.id),
        name = device.name,
        active = device.active,
        linked = device.accountId != null,
        pairingCode = requireNotNull(device.pairingCode),
        lastSeenAt = device.lastSeenAt,
        createdAt = device.createdAt,
    )

    private fun publishSessionUpdates(sessionId: java.util.UUID?) {
        val rawSession = sessionId?.let { sessionRepository.findById(it).orElse(null) }
        val accountId = rawSession?.accountId
        val active = publicQueryService.getActiveSession(accountId)
        val session = sessionId?.let { runCatching { publicQueryService.getSession(it, accountId) }.getOrNull() }
        messagingTemplate.convertAndSend("/topic/sessions/active", active ?: session ?: mapOf("active" to false))
        if (sessionId != null) {
            if (session != null) {
                messagingTemplate.convertAndSend("/topic/sessions/$sessionId", session)
            }
        }
        messagingTemplate.convertAndSend("/topic/leaderboard", publicQueryService.getLeaderboard(accountId))
    }

    private fun publishSessionUpdatesAfterCommit(sessionId: java.util.UUID?) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publishSessionUpdates(sessionId)
            return
        }
        TransactionSynchronizationManager.registerSynchronization(
            object : TransactionSynchronization {
                override fun afterCommit() {
                    publishSessionUpdates(sessionId)
                }
            },
        )
    }

    private data class ScanFeedback(
        val cardType: CardType,
        val playerName: String? = null,
    )

    private fun resolveScanFeedback(cardUid: String?): ScanFeedback? {
        val normalizedUid = cardUid?.trim()?.uppercase()?.takeIf { it.isNotBlank() } ?: return null
        val card = cardRepository.findByCardUid(normalizedUid) ?: return ScanFeedback(cardType = CardType.UNKNOWN)
        if (card.cardType != CardType.PLAYER) {
            return ScanFeedback(cardType = card.cardType)
        }
        val playerName = card.playerId?.let { playerRepository.findById(it).orElse(null)?.name }
        return ScanFeedback(cardType = CardType.PLAYER, playerName = playerName)
    }

    private fun parseUuid(rawValue: String?): java.util.UUID? =
        rawValue
            ?.trim()
            ?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { java.util.UUID.fromString(it) }.getOrNull() }
}
