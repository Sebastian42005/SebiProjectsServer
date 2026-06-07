package com.example.paulasserver.nfcgame.application.admin

import com.example.paulasserver.nfcgame.api.dto.CardAssignRequest
import com.example.paulasserver.nfcgame.api.dto.CardResponse
import com.example.paulasserver.nfcgame.api.dto.DeviceRequest
import com.example.paulasserver.nfcgame.api.dto.DeviceResponse
import com.example.paulasserver.nfcgame.api.dto.FlowDefinitionRequest
import com.example.paulasserver.nfcgame.api.dto.FlowDefinitionResponse
import com.example.paulasserver.nfcgame.api.dto.FlowStateResponse
import com.example.paulasserver.nfcgame.api.dto.FlowTransitionResponse
import com.example.paulasserver.nfcgame.api.dto.GameTemplateRequest
import com.example.paulasserver.nfcgame.api.dto.GameTemplateResponse
import com.example.paulasserver.nfcgame.api.dto.PlayerRequest
import com.example.paulasserver.nfcgame.api.dto.PlayerResponse
import com.example.paulasserver.nfcgame.application.NfcGameMapper
import com.example.paulasserver.nfcgame.application.statistics.NfcStatisticsService
import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.persistence.entity.NfcCard
import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowDefinition
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowState
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowTransition
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayer
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayerStatsProjection
import com.example.paulasserver.nfcgame.persistence.repository.NfcCardRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcDeviceRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowDefinitionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowStateRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowTransitionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameResultRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameSessionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameTemplateRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcMoneyTransactionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerStatsProjectionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionAccountRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionEventRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionRoundRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamMemberRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

private const val PUBLICATION_MANAGER_USERNAME = "administrator4"

@Service
class NfcAdminService(
    private val playerRepository: NfcPlayerRepository,
    private val statsRepository: NfcPlayerStatsProjectionRepository,
    private val cardRepository: NfcCardRepository,
    private val deviceRepository: NfcDeviceRepository,
    private val gameTemplateRepository: NfcGameTemplateRepository,
    private val flowDefinitionRepository: NfcFlowDefinitionRepository,
    private val flowStateRepository: NfcFlowStateRepository,
    private val flowTransitionRepository: NfcFlowTransitionRepository,
    private val sessionRepository: NfcGameSessionRepository,
    private val sessionTeamRepository: NfcSessionTeamRepository,
    private val sessionTeamMemberRepository: NfcSessionTeamMemberRepository,
    private val sessionRoundRepository: NfcSessionRoundRepository,
    private val sessionAccountRepository: NfcSessionAccountRepository,
    private val moneyTransactionRepository: NfcMoneyTransactionRepository,
    private val gameResultRepository: NfcGameResultRepository,
    private val sessionEventRepository: NfcSessionEventRepository,
    private val statisticsService: NfcStatisticsService,
    private val mapper: NfcGameMapper,
    private val objectMapper: ObjectMapper,
) {
    fun listPlayers(): List<PlayerResponse> =
        playerRepository.findAllByAccountIdOrderByNameAsc(currentAccountId())
            .map(::toPlayerResponse)

    fun createPlayer(request: PlayerRequest): PlayerResponse {
        val player = NfcPlayer().applyPlayerRequest(request).apply { accountId = currentAccountId() }
        return toPlayerResponse(playerRepository.save(player))
    }

    fun updatePlayer(id: UUID, request: PlayerRequest): PlayerResponse {
        val player = ownedPlayer(id)
        return toPlayerResponse(playerRepository.save(player.applyPlayerRequest(request)))
    }

    fun updatePlayerActive(id: UUID, active: Boolean): PlayerResponse {
        val player = ownedPlayer(id)
        player.active = active
        return toPlayerResponse(playerRepository.save(player))
    }

    fun updatePlayerPoints(id: UUID, totalPoints: Long): PlayerResponse {
        val player = ownedPlayer(id)
        val stats = statsRepository.findById(id).orElseGet {
            NfcPlayerStatsProjection().apply { playerId = id }
        }
        stats.totalPoints = totalPoints
        stats.updatedAt = java.time.Instant.now()
        statsRepository.save(stats)
        return mapper.toPlayerResponse(player, totalPoints = stats.totalPoints)
    }

    fun setPlayerImage(id: UUID, file: MultipartFile): PlayerResponse {
        val player = ownedPlayer(id)
        if (file.isEmpty) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Image file is empty")
        }
        val contentType = file.contentType ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Image content type is required")
        if (!contentType.startsWith("image/")) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image files are supported")
        }
        player.imageContent = file.bytes
        player.imageContentType = contentType
        player.imageFileName = file.originalFilename ?: file.name
        player.imageUrl = null
        return toPlayerResponse(playerRepository.save(player))
    }

    fun getPlayerImage(id: UUID): ResponseEntity<ByteArray> {
        val player = ownedPlayer(id)
        val content = player.imageContent ?: throw notFound("Player image not found")
        val contentType = player.imageContentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.valueOf(contentType))
            .body(content)
    }

    fun softDeletePlayer(id: UUID) {
        val player = ownedPlayer(id)
        player.active = false
        playerRepository.save(player)
    }

    @Transactional
    fun deleteSession(id: UUID) {
        val session = sessionRepository.findById(id).orElseThrow { notFound("Session not found") }
        if (session.accountId != currentAccountId()) throw notFound("Session not found")

        val teamIds = sessionTeamRepository.findAllBySessionIdOrderByTeamOrderAsc(id).mapNotNull { it.id }
        sessionEventRepository.deleteAllBySessionId(id)
        moneyTransactionRepository.deleteAllBySessionId(id)
        gameResultRepository.deleteBySessionId(id)
        sessionRoundRepository.deleteAllBySessionId(id)
        sessionAccountRepository.deleteAllBySessionId(id)
        if (teamIds.isNotEmpty()) {
            sessionTeamMemberRepository.deleteAllBySessionTeamIdIn(teamIds)
        }
        sessionTeamRepository.deleteAllBySessionId(id)
        sessionRepository.deleteById(id)
        sessionRepository.flush()
        statisticsService.rebuildFromSessions()
    }

    fun listCards(): List<CardResponse> =
        cardRepository.findAllByAccountIdOrderByCreatedAtDesc(currentAccountId())
            .map(mapper::toCardResponse)

    fun listUnassignedCards(): List<CardResponse> =
        cardRepository.findAllByAccountIdAndStatusOrderByCreatedAtDesc(currentAccountId(), CardStatus.UNASSIGNED)
            .map(mapper::toCardResponse)

    fun assignCard(request: CardAssignRequest): CardResponse {
        val normalizedUid = normalizeUid(request.cardUid)
        val card = cardRepository.findByCardUid(normalizedUid) ?: NfcCard().apply { cardUid = normalizedUid }
        validateAssignment(request)
        val accountId = currentAccountId()
        if (card.accountId != null && card.accountId != accountId) throw notFound("Card not found")
        card.accountId = accountId
        card.cardType = request.cardType
        card.status = CardStatus.ASSIGNED
        card.playerId = if (request.cardType == CardType.PLAYER) request.playerId else null
        card.gameTemplateId = if (request.cardType == CardType.GAME) request.gameTemplateId else null
        return mapper.toCardResponse(cardRepository.save(card))
    }

    fun listDevices(): List<DeviceResponse> =
        deviceRepository.findAllByAccountIdOrderByCreatedAtDesc(currentAccountId())
            .map(mapper::toDeviceResponse)

    fun createDevice(request: DeviceRequest): DeviceResponse {
        if (deviceRepository.findByName(request.name.trim()) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Device name already exists")
        }
        val device = NfcDevice().applyDeviceRequest(request).apply { accountId = currentAccountId() }
        return mapper.toDeviceResponse(deviceRepository.save(device))
    }

    fun updateDevice(id: UUID, request: DeviceRequest): DeviceResponse {
        val device = ownedDevice(id)
        return mapper.toDeviceResponse(deviceRepository.save(device.applyDeviceRequest(request)))
    }

    fun listGameTemplates(): List<GameTemplateResponse> =
        gameTemplateRepository.findAllByAccountIdOrderByUpdatedAtDesc(currentAccountId())
            .map(mapper::toGameTemplateResponse)

    fun createGameTemplate(request: GameTemplateRequest): GameTemplateResponse {
        val template = NfcGameTemplate().applyGameTemplateRequest(request).apply { accountId = currentAccountId() }
        return mapper.toGameTemplateResponse(gameTemplateRepository.save(template))
    }

    fun updateGameTemplate(id: UUID, request: GameTemplateRequest): GameTemplateResponse {
        val template = ownedGameTemplate(id)
        return mapper.toGameTemplateResponse(gameTemplateRepository.save(template.applyGameTemplateRequest(request)))
    }

    @Transactional
    fun replaceFlow(gameTemplateId: UUID, request: FlowDefinitionRequest): FlowDefinitionResponse {
        ownedGameTemplate(gameTemplateId)

        flowDefinitionRepository.findAllByGameTemplateIdOrderByVersionDesc(gameTemplateId)
            .filter { it.active && request.active }
            .forEach {
                it.active = false
                flowDefinitionRepository.save(it)
            }

        val definition = flowDefinitionRepository.save(
            NfcFlowDefinition().apply {
                this.gameTemplateId = gameTemplateId
                version = request.version
                active = request.active
                startStateKey = request.startStateKey
            },
        )
        val definitionId = requireNotNull(definition.id)

        val states = request.states.map {
            NfcFlowState().apply {
                flowDefinitionId = definitionId
                stateKey = it.stateKey
                stateType = it.stateType
                title = it.title
                subtitle = it.subtitle
                configJson = objectMapper.writeValueAsString(it.config)
                sortOrder = it.sortOrder
            }
        }
        val transitions = request.transitions.map {
            NfcFlowTransition().apply {
                flowDefinitionId = definitionId
                fromStateKey = it.fromStateKey
                eventType = it.eventType
                conditionJson = objectMapper.writeValueAsString(it.condition)
                actionJson = objectMapper.writeValueAsString(it.action)
                toStateKey = it.toStateKey
                sortOrder = it.sortOrder
            }
        }

        return toFlowResponse(
            definition,
            flowStateRepository.saveAll(states),
            flowTransitionRepository.saveAll(transitions),
        )
    }

    fun getActiveFlow(gameTemplateId: UUID): FlowDefinitionResponse {
        val definition = flowDefinitionRepository.findFirstByGameTemplateIdAndActiveTrueOrderByVersionDesc(gameTemplateId)
            ?: throw notFound("Active flow not found")
        return toFlowResponse(
            definition,
            flowStateRepository.findAllByFlowDefinitionIdOrderBySortOrderAsc(requireNotNull(definition.id)),
            flowTransitionRepository.findAllByFlowDefinitionIdOrderBySortOrderAsc(requireNotNull(definition.id)),
        )
    }

    private fun validateAssignment(request: CardAssignRequest) {
        when (request.cardType) {
            CardType.PLAYER -> {
                val playerId = request.playerId ?: throw badRequest("PLAYER cards require playerId")
                ownedPlayer(playerId)
                if (request.gameTemplateId != null) throw badRequest("PLAYER cards cannot have gameTemplateId")
            }

            CardType.GAME -> {
                val gameTemplateId = request.gameTemplateId ?: throw badRequest("GAME cards require gameTemplateId")
                ownedGameTemplate(gameTemplateId)
                if (request.playerId != null) throw badRequest("GAME cards cannot have playerId")
            }

            CardType.UNKNOWN -> throw badRequest("UNKNOWN cards cannot be assigned directly")
        }
    }

    private fun toPlayerResponse(player: NfcPlayer): PlayerResponse {
        val playerId = requireNotNull(player.id)
        val totalPoints = statsRepository.findById(playerId).orElse(null)?.totalPoints ?: 0
        return mapper.toPlayerResponse(player, totalPoints = totalPoints)
    }

    fun currentAccountId(): Long {
        return currentUser().id
    }

    fun canManagePublicationReviews(): Boolean =
        currentUser().username == PUBLICATION_MANAGER_USERNAME

    fun requirePublicationManager() {
        if (!canManagePublicationReviews()) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrator4 can manage publication reviews")
        }
    }

    private fun currentUser(): com.example.paulasserver.security.AuthenticatedUser {
        val principal = SecurityContextHolder.getContext().authentication?.principal
        if (principal is com.example.paulasserver.security.AuthenticatedUser) {
            return principal
        }
        throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")
    }

    private fun ownedPlayer(id: UUID): NfcPlayer =
        playerRepository.findById(id).orElseThrow { notFound("Player not found") }.also {
            if (it.accountId != currentAccountId()) throw notFound("Player not found")
        }

    private fun ownedDevice(id: UUID): NfcDevice =
        deviceRepository.findById(id).orElseThrow { notFound("Device not found") }.also {
            if (it.accountId != currentAccountId()) throw notFound("Device not found")
        }

    fun ownedGameTemplate(id: UUID): NfcGameTemplate =
        gameTemplateRepository.findById(id).orElseThrow { notFound("Game template not found") }.also {
            if (it.accountId != currentAccountId() && !canManagePublicationReviews()) throw notFound("Game template not found")
        }

    private fun NfcPlayer.applyPlayerRequest(request: PlayerRequest) = apply {
        name = request.name.trim()
        description = request.description
        imageUrl = request.imageUrl?.takeIf { it.isNotBlank() }
        if (!imageUrl.isNullOrBlank()) {
            imageContent = null
            imageContentType = null
            imageFileName = null
        }
        active = request.active
    }

    private fun NfcDevice.applyDeviceRequest(request: DeviceRequest) = apply {
        name = request.name.trim()
        deviceKey = request.deviceKey
        active = request.active
    }

    private fun NfcGameTemplate.applyGameTemplateRequest(request: GameTemplateRequest) = apply {
        if (request.minTeamSize < 1 || request.maxTeamSize < request.minTeamSize) {
            throw badRequest("Team size bounds are invalid")
        }
        name = request.name.trim()
        description = request.description
        imageUrl = request.imageUrl
        active = request.active
        allowTeams = request.allowTeams
        minTeamSize = request.minTeamSize
        maxTeamSize = request.maxTeamSize
        supportsRoundLimit = request.supportsRoundLimit
        economyEnabled = request.economyEnabled
        startCapital = request.startCapital
        smallStep = request.smallStep
        largeStep = request.largeStep
        winRuleType = request.winRuleType
        globalWinnerPoints = request.globalWinnerPoints.coerceAtLeast(0)
        globalSecondPlacePoints = request.globalSecondPlacePoints?.coerceAtLeast(0)
        globalThirdPlacePoints = request.globalThirdPlacePoints?.coerceAtLeast(0)
        dashboardMetricSource = request.dashboardMetricSource?.takeIf { it.isNotBlank() } ?: "points"
        dashboardMetricLabel = request.dashboardMetricLabel?.trim() ?: "Punkte"
        dashboardMetricSuffix = request.dashboardMetricSuffix?.takeIf { it.isNotBlank() }
        dashboardMetricSortDirection = request.dashboardMetricSortDirection?.takeIf { it.equals("ASC", true) || it.equals("DESC", true) }?.uppercase() ?: "DESC"
        dashboardMetricDisplayType = request.dashboardMetricDisplayType?.takeIf { it.isNotBlank() }?.uppercase() ?: "RACE_BAR"
        dashboardStatusSource = request.dashboardStatusSource?.trim()?.takeIf { it.isNotBlank() }
        dashboardStatusLabel = request.dashboardStatusLabel?.trim() ?: "Runde"
        dashboardStatusSuffix = request.dashboardStatusSuffix?.takeIf { it.isNotBlank() }
        dashboardStatusMaxSource = request.dashboardStatusMaxSource?.takeIf { it.isNotBlank() }
        dashboardStatusDisplayType = request.dashboardStatusDisplayType?.takeIf { it.isNotBlank() }?.uppercase() ?: "PROGRESS_BAR"
    }

    private fun toFlowResponse(
        definition: NfcFlowDefinition,
        states: List<NfcFlowState>,
        transitions: List<NfcFlowTransition>,
    ) = FlowDefinitionResponse(
        id = requireNotNull(definition.id),
        gameTemplateId = requireNotNull(definition.gameTemplateId),
        version = definition.version,
        active = definition.active,
        startStateKey = definition.startStateKey,
        createdAt = definition.createdAt,
        states = states.map {
            FlowStateResponse(
                id = requireNotNull(it.id),
                stateKey = it.stateKey,
                stateType = it.stateType,
                title = it.title,
                subtitle = it.subtitle,
                config = readMap(it.configJson),
                sortOrder = it.sortOrder,
            )
        },
        transitions = transitions.map {
            FlowTransitionResponse(
                id = requireNotNull(it.id),
                fromStateKey = it.fromStateKey,
                eventType = it.eventType,
                condition = readMap(it.conditionJson),
                action = readMap(it.actionJson),
                toStateKey = it.toStateKey,
                sortOrder = it.sortOrder,
            )
        },
    )

    private fun readMap(json: String): Map<String, Any?> =
        objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})

    private fun normalizeUid(uid: String) = uid.trim().uppercase()

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
    private fun notFound(message: String) = ResponseStatusException(HttpStatus.NOT_FOUND, message)
}
