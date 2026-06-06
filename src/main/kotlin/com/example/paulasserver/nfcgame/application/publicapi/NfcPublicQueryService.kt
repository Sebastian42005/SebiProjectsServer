package com.example.paulasserver.nfcgame.application.publicapi

import com.example.paulasserver.nfcgame.api.dto.GameResultResponse
import com.example.paulasserver.nfcgame.api.dto.LeaderboardEntryResponse
import com.example.paulasserver.nfcgame.api.dto.PlayerStatsResponse
import com.example.paulasserver.nfcgame.api.dto.SessionSummaryResponse
import com.example.paulasserver.nfcgame.api.dto.SessionRoundResponse
import com.example.paulasserver.nfcgame.api.dto.TeamMemberResponse
import com.example.paulasserver.nfcgame.api.dto.TeamResponse
import com.example.paulasserver.nfcgame.api.dto.TimelineEventResponse
import com.example.paulasserver.nfcgame.application.NfcGameMapper
import com.example.paulasserver.nfcgame.application.admin.NfcGameBuilderService
import com.example.paulasserver.nfcgame.domain.GamePublicationStatus
import com.example.paulasserver.nfcgame.application.session.SessionStateMachineService
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameSession
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayerStatsProjection
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionRound
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionValue
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameResultRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameSessionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameTemplateRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowNodeRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerStatsProjectionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionAccountRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionEventRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionRoundRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamMemberRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionValueRepository
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.messaging.simp.SimpMessagingTemplate
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.util.UUID

@Service
class NfcPublicQueryService(
    private val sessionRepository: NfcGameSessionRepository,
    private val gameTemplateRepository: NfcGameTemplateRepository,
    private val teamRepository: NfcSessionTeamRepository,
    private val memberRepository: NfcSessionTeamMemberRepository,
    private val playerRepository: NfcPlayerRepository,
    private val flowNodeRepository: NfcFlowNodeRepository,
    private val accountRepository: NfcSessionAccountRepository,
    private val resultRepository: NfcGameResultRepository,
    private val eventRepository: NfcSessionEventRepository,
    private val roundRepository: NfcSessionRoundRepository,
    private val valueRepository: NfcSessionValueRepository,
    private val statsRepository: NfcPlayerStatsProjectionRepository,
    private val mapper: NfcGameMapper,
    private val gameBuilderService: NfcGameBuilderService,
    private val stateMachineService: SessionStateMachineService,
    private val messagingTemplate: SimpMessagingTemplate,
    private val objectMapper: ObjectMapper,
) {
    private val liveStatuses = listOf(
        SessionStatus.LOBBY,
        SessionStatus.CONFIGURING,
        SessionStatus.BUILDING_TEAMS,
        SessionStatus.READY,
        SessionStatus.RUNNING,
    )
    private val dashboardStatuses = liveStatuses + SessionStatus.FINISHED

    fun getActiveSession(accountId: Long?): SessionSummaryResponse? =
        accountId?.let {
            sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(it, dashboardStatuses)
        }?.let(::toSessionSummary)

    fun getSession(sessionId: UUID, accountId: Long?): SessionSummaryResponse =
        toSessionSummary(requireOwnedSession(sessionId, accountId))

    fun finishSession(sessionId: UUID, accountId: Long?): SessionSummaryResponse {
        requireOwnedSession(sessionId, accountId)
        val result = stateMachineService.finishSessionById(sessionId)
        val session = result.session ?: throw notFound("Session not found")
        val summary = toSessionSummary(session)
        messagingTemplate.convertAndSend("/topic/sessions/active", summary)
        messagingTemplate.convertAndSend("/topic/sessions/$sessionId", summary)
        messagingTemplate.convertAndSend("/topic/leaderboard", getLeaderboard(accountId))
        return summary
    }

    fun getTimeline(sessionId: UUID, accountId: Long?): List<TimelineEventResponse> {
        requireOwnedSession(sessionId, accountId)
        return eventRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId).map {
            TimelineEventResponse(
                id = requireNotNull(it.id),
                eventType = it.eventType,
                payload = readMap(it.payloadJson),
                createdAt = it.createdAt,
            )
        }
    }

    fun listPlayers(accountId: Long?) =
        accountId?.let(playerRepository::findAllByAccountIdAndActiveTrueOrderByNameAsc)
            .orEmpty()
            .map(mapper::toPlayerResponse)

    fun listGames(accountId: Long?) =
        accountId?.let(gameTemplateRepository::findAllByAccountIdAndActiveTrueOrderByNameAsc)
            .orEmpty()
            .map(mapper::toGameTemplateResponse)

    fun listPublicGames() =
        gameTemplateRepository.findAllByPublicationStatusAndActiveTrueOrderByNameAsc(GamePublicationStatus.PUBLISHED)
            .map { mapper.toGameTemplateResponse(it, ownedByCurrentAccount = false) }

    fun addPublicGameToLibrary(gameId: UUID) =
        gameBuilderService.copyPublicGameToCurrentAccount(gameId)

    fun getPlayerImage(playerId: UUID, accountId: Long?): ResponseEntity<ByteArray> {
        val player = playerRepository.findById(playerId).orElseThrow { notFound("Player not found") }
        if (accountId == null || player.accountId != accountId) throw notFound("Player not found")
        val content = player.imageContent ?: throw notFound("Player image not found")
        val contentType = player.imageContentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.valueOf(contentType))
            .body(content)
    }

    fun getGameImage(gameId: UUID, accountId: Long?): ResponseEntity<ByteArray> {
        val game = gameTemplateRepository.findById(gameId).orElseThrow { notFound("Game template not found") }
        val canReadPublic = game.active && game.publicationStatus == GamePublicationStatus.PUBLISHED
        if ((accountId == null || game.accountId != accountId) && !canReadPublic) throw notFound("Game template not found")
        val content = game.imageContent ?: throw notFound("Game image not found")
        val contentType = game.imageContentType ?: MediaType.APPLICATION_OCTET_STREAM_VALUE
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.valueOf(contentType))
            .body(content)
    }

    fun getHistory(accountId: Long?): List<SessionSummaryResponse> =
        accountId?.let(sessionRepository::findAllByAccountIdOrderByCreatedAtDesc)
            .orEmpty()
            .map(::toSessionSummary)

    fun getPlayerStats(playerId: UUID, accountId: Long?): PlayerStatsResponse {
        val player = playerRepository.findById(playerId).orElse(null)
        if (accountId == null || player?.accountId != accountId) throw notFound("Player not found")
        val stats = statsRepository.findById(playerId).orElseGet {
            NfcPlayerStatsProjection().apply {
                this.playerId = playerId
            }
        }
        return PlayerStatsResponse(
            playerId = playerId,
            playerName = player?.name,
            gamesPlayed = stats.gamesPlayed,
            gamesWon = stats.gamesWon,
            roundsWon = stats.roundsWon,
            totalPoints = stats.totalPoints,
            winRate = stats.winRate,
            updatedAt = stats.updatedAt,
        )
    }

    fun getLeaderboard(accountId: Long? = null): List<LeaderboardEntryResponse> {
        val allowedPlayerIds = accountId?.let {
            playerRepository.findAllByAccountIdOrderByNameAsc(it).mapNotNull { player -> player.id }.toSet()
        } ?: return emptyList()
        return statsRepository.findAllByOrderByTotalPointsDescGamesWonDescWinRateDesc()
            .filter { it.playerId in allowedPlayerIds }
            .mapIndexed { index, stats ->
                val player = stats.playerId?.let { playerRepository.findById(it).orElse(null) }
                LeaderboardEntryResponse(
                    rank = index + 1,
                    playerId = requireNotNull(stats.playerId),
                    playerName = player?.name,
                    imageUrl = player?.let(::playerImageUrl),
                    gamesPlayed = stats.gamesPlayed,
                    gamesWon = stats.gamesWon,
                    roundsWon = stats.roundsWon,
                    totalPoints = stats.totalPoints,
                    winRate = stats.winRate,
                )
            }
    }

    fun getGameStats(gameId: UUID, accountId: Long?): Map<String, Any?> {
        val game = gameTemplateRepository.findById(gameId).orElseThrow { notFound("Game template not found") }
        if (accountId == null || game.accountId != accountId) {
            throw notFound("Game template not found")
        }
        val sessions = sessionRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).filter { it.gameTemplateId == gameId }
        return mapOf(
            "gameTemplateId" to gameId,
            "sessionsPlayed" to sessions.count { it.status == SessionStatus.FINISHED },
            "activeSessions" to sessions.count { it.status in liveStatuses },
            "lastSessionId" to sessions.firstOrNull()?.id,
        )
    }

    private fun playerImageUrl(player: com.example.paulasserver.nfcgame.persistence.entity.NfcPlayer): String? =
        player.imageContentType?.let { "/api/public/players/${player.id}/image" } ?: player.imageUrl

    private fun gameImageUrl(template: com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate?): String? =
        template?.imageContentType?.let { "/api/public/games/${template.id}/image" } ?: template?.imageUrl

    fun toSessionSummary(session: NfcGameSession): SessionSummaryResponse {
        val sessionId = requireNotNull(session.id)
        val template = session.gameTemplateId?.let { gameTemplateRepository.findById(it).orElse(null) }
        val accounts = accountRepository.findAllBySessionId(sessionId)
        val result = resultRepository.findBySessionId(sessionId)
        val dashboardMetricConfig = dashboardMetricConfig(template)
        val rounds = roundRepository.findAllBySessionIdOrderByRoundNumberAsc(sessionId)
        val values = valueRepository.findAllBySessionId(sessionId)

        return SessionSummaryResponse(
            id = sessionId,
            gameTemplateId = requireNotNull(session.gameTemplateId),
            gameName = template?.name,
            gameImageUrl = gameImageUrl(template),
            moneyCurrency = dashboardMetricConfig.suffix,
            showBalancesOnDashboard = dashboardMetricConfig.source.equals("balance", ignoreCase = true) || dashboardMetricConfig.source.equals("money", ignoreCase = true),
            dashboardMetricSource = dashboardMetricConfig.source,
            dashboardMetricLabel = dashboardMetricConfig.label,
            dashboardMetricSuffix = dashboardMetricConfig.suffix,
            dashboardMetricSortDirection = dashboardMetricConfig.sortDirection,
            dashboardMetricDisplayType = dashboardMetricConfig.displayType,
            dashboardStatusSource = dashboardMetricConfig.statusSource,
            dashboardStatusLabel = dashboardMetricConfig.statusLabel,
            dashboardStatusSuffix = dashboardMetricConfig.statusSuffix,
            dashboardStatusMaxSource = dashboardMetricConfig.statusMaxSource,
            dashboardStatusDisplayType = dashboardMetricConfig.statusDisplayType,
            dashboardStatusValue = dashboardMetricConfig.statusSource
                ?.let { dashboardStatusValue(it, session, rounds, values, dashboardMetricConfig.sortDirection) },
            dashboardStatusLimit = dashboardMetricConfig.statusMaxSource
                ?.let { dashboardStatusValue(it, session, rounds, values, dashboardMetricConfig.sortDirection) }
                ?.takeIf { it > BigDecimal.ZERO },
            deviceId = requireNotNull(session.deviceId),
            status = session.status,
            currentStateKey = session.currentStateKey,
            roundLimitType = session.roundLimitType,
            roundLimit = session.roundLimit,
            currentRoundNumber = effectiveCurrentRoundNumber(session, rounds),
            createdAt = session.createdAt,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId).map { team ->
                val teamId = requireNotNull(team.id)
                val balance = values.firstOrNull { it.ownerType == OwnerType.TEAM && it.ownerId == teamId && it.valueKey == "money" }?.value
                    ?: accounts.firstOrNull { it.ownerType == OwnerType.TEAM && it.teamId == teamId }?.balance
                TeamResponse(
                    id = teamId,
                    name = team.name,
                    teamOrder = team.teamOrder,
                    targetSize = team.targetSize,
                    status = team.status,
                    balance = balance,
                    dashboardMetricValue = dashboardMetricValue(dashboardMetricConfig.source, teamId, balance, rounds, values),
                    members = memberRepository.findAllBySessionTeamId(teamId).map { member ->
                        val player = member.playerId?.let { playerRepository.findById(it).orElse(null) }
                        TeamMemberResponse(
                            playerId = requireNotNull(member.playerId),
                            playerName = player?.name,
                            imageUrl = player?.let(::playerImageUrl),
                            joinedAt = member.joinedAt,
                        )
                    },
                )
            },
            rounds = rounds.map { round ->
                SessionRoundResponse(
                    roundNumber = round.roundNumber,
                    winningTeamId = round.winningTeamId,
                    awardedPointsPerMember = round.awardedPointsPerMember,
                    createdAt = round.createdAt,
                )
            },
            result = result?.let {
                GameResultResponse(
                    winningTeamId = it.winningTeamId,
                    endReason = it.endReason,
                    createdAt = it.createdAt,
                )
            },
        )
    }

    private fun readMap(json: String): Map<String, Any?> =
        objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})

    private fun dashboardMetricConfig(template: com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate?): DashboardMetricConfig {
        val gameTemplateId = template?.id
        val bankConfig = gameTemplateId
            ?.let { flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(it) }
            ?.firstOrNull { it.type == "ENABLE_BANK" }
            ?.let { readMap(it.configJson) }
            .orEmpty()
        val fallbackSource = template?.dashboardMetricSource ?: if (bankConfig.isNotEmpty()) "money" else "points"
        return DashboardMetricConfig(
            source = fallbackSource,
            label = template?.dashboardMetricLabel?.trim()
                ?: if (fallbackSource.equals("balance", ignoreCase = true) || fallbackSource.equals("money", ignoreCase = true)) "Geld" else "Punkte",
            suffix = template?.dashboardMetricSuffix?.takeIf { it.isNotBlank() }
                ?: bankConfig["currency"]?.toString()?.takeIf { it.isNotBlank() },
            sortDirection = template?.dashboardMetricSortDirection?.takeIf { it.equals("ASC", true) || it.equals("DESC", true) }?.uppercase()
                ?: "DESC",
            displayType = template?.dashboardMetricDisplayType?.takeIf { it.isNotBlank() }?.uppercase() ?: "RACE_BAR",
            statusSource = template?.dashboardStatusSource?.trim()?.takeIf { it.isNotBlank() } ?: if (template == null) "currentRound" else null,
            statusLabel = template?.dashboardStatusLabel?.trim() ?: "Runde",
            statusSuffix = template?.dashboardStatusSuffix?.takeIf { it.isNotBlank() },
            statusMaxSource = template?.dashboardStatusMaxSource?.takeIf { it.isNotBlank() },
            statusDisplayType = template?.dashboardStatusDisplayType?.takeIf { it.isNotBlank() }?.uppercase() ?: "PROGRESS_BAR",
        )
    }

    private fun dashboardStatusValue(
        source: String,
        session: NfcGameSession,
        rounds: List<NfcSessionRound>,
        values: List<NfcSessionValue>,
        sortDirection: String,
    ): BigDecimal? =
        when (normalizeValueKey(source)) {
            "currentround", "round", "currentroundnumber" -> effectiveCurrentRoundNumber(session, rounds).toBigDecimal()
            "roundlimit" -> session.roundLimit?.toBigDecimal()
            "players", "playercount", "totalplayers" -> teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
                .sumOf { team -> memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).size }
                .toBigDecimal()
            "teams", "teamcount" -> teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
                .count { it.status != "CONFIGURING" }
                .toBigDecimal()
            else -> {
                val normalizedSource = normalizeValueKey(source)
                val teamValues = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id)).map { team ->
                    val teamId = requireNotNull(team.id)
                    val balance = valueRepository.findAllBySessionId(requireNotNull(session.id))
                        .firstOrNull { it.ownerType == OwnerType.TEAM && it.ownerId == teamId && it.valueKey == "money" }
                        ?.value
                    dashboardMetricValue(normalizedSource, teamId, balance, rounds, values)
                }
                if (sortDirection.equals("ASC", ignoreCase = true)) teamValues.minOrNull() else teamValues.maxOrNull()
            }
        }

    private fun dashboardMetricValue(
        source: String,
        teamId: UUID,
        balance: BigDecimal?,
        rounds: List<NfcSessionRound>,
        values: List<NfcSessionValue>,
    ): BigDecimal =
        values.firstOrNull { it.ownerType == OwnerType.TEAM && it.ownerId == teamId && it.valueKey == normalizeValueKey(source) }?.value
            ?: when (normalizeValueKey(source)) {
            "money" -> balance ?: BigDecimal.ZERO
            "rounds", "wins", "roundwins" -> BigDecimal.valueOf(rounds.count { it.winningTeamId == teamId }.toLong())
            else -> BigDecimal.valueOf(
                rounds
                    .filter { it.winningTeamId == teamId }
                    .sumOf { it.awardedPointsPerMember }
                    .toLong(),
            )
        }

    private fun effectiveCurrentRoundNumber(session: NfcGameSession, rounds: List<NfcSessionRound>): Int {
        val maxRecordedRound = rounds.maxOfOrNull { it.roundNumber } ?: 0
        return if (maxRecordedRound > 0) maxRecordedRound else session.currentRoundNumber
    }

    private fun normalizeValueKey(valueKey: String): String =
        when (valueKey.trim().lowercase()) {
            "score", "punkt", "punkte" -> "points"
            "balance", "kontostand", "geld" -> "money"
            else -> valueKey.trim().lowercase()
        }

    private data class DashboardMetricConfig(
        val source: String = "points",
        val label: String = "Punkte",
        val suffix: String? = null,
        val sortDirection: String = "DESC",
        val displayType: String = "RACE_BAR",
        val statusSource: String? = "currentRound",
        val statusLabel: String = "Runde",
        val statusSuffix: String? = null,
        val statusMaxSource: String? = "roundLimit",
        val statusDisplayType: String = "PROGRESS_BAR",
    )

    private fun notFound(message: String) = ResponseStatusException(HttpStatus.NOT_FOUND, message)

    private fun requireOwnedSession(sessionId: UUID, accountId: Long?): NfcGameSession {
        val session = sessionRepository.findById(sessionId).orElseThrow { notFound("Session not found") }
        if (accountId == null || session.accountId != accountId) {
            throw notFound("Session not found")
        }
        return session
    }
}
