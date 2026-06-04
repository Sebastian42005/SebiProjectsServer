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
import com.example.paulasserver.nfcgame.application.session.SessionStateMachineService
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameSession
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayerStatsProjection
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
    private val statsRepository: NfcPlayerStatsProjectionRepository,
    private val mapper: NfcGameMapper,
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
        if (accountId == null || game.accountId != accountId) throw notFound("Game template not found")
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

        return SessionSummaryResponse(
            id = sessionId,
            gameTemplateId = requireNotNull(session.gameTemplateId),
            gameName = template?.name,
            gameImageUrl = gameImageUrl(template),
            moneyCurrency = dashboardMetricConfig.suffix,
            showBalancesOnDashboard = dashboardMetricConfig.source.equals("balance", ignoreCase = true),
            dashboardMetricSource = dashboardMetricConfig.source,
            dashboardMetricLabel = dashboardMetricConfig.label,
            dashboardMetricSuffix = dashboardMetricConfig.suffix,
            dashboardMetricSortDirection = dashboardMetricConfig.sortDirection,
            deviceId = requireNotNull(session.deviceId),
            status = session.status,
            currentStateKey = session.currentStateKey,
            roundLimitType = session.roundLimitType,
            roundLimit = session.roundLimit,
            currentRoundNumber = session.currentRoundNumber,
            createdAt = session.createdAt,
            startedAt = session.startedAt,
            endedAt = session.endedAt,
            teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId).map { team ->
                val teamId = requireNotNull(team.id)
                val balance = accounts.firstOrNull { it.ownerType == OwnerType.TEAM && it.teamId == teamId }?.balance
                TeamResponse(
                    id = teamId,
                    name = team.name,
                    teamOrder = team.teamOrder,
                    targetSize = team.targetSize,
                    status = team.status,
                    balance = balance,
                    dashboardMetricValue = dashboardMetricValue(dashboardMetricConfig.source, teamId, balance, rounds),
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
        val dashboardNodeConfig = gameTemplateId
            ?.let { flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(it) }
            ?.firstOrNull { it.type == "DASHBOARD_METRIC" }
            ?.let { readMap(it.configJson) }
            .orEmpty()
        val bankConfig = gameTemplateId
            ?.let { flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(it) }
            ?.firstOrNull { it.type == "ENABLE_BANK" }
            ?.let { readMap(it.configJson) }
            .orEmpty()
        val fallbackSource = template?.dashboardMetricSource ?: if (bankConfig.isNotEmpty()) "balance" else "points"
        return DashboardMetricConfig(
            source = dashboardNodeConfig["source"]?.toString()?.takeIf { it.isNotBlank() }
                ?: fallbackSource,
            label = dashboardNodeConfig["label"]?.toString()?.takeIf { it.isNotBlank() }
                ?: template?.dashboardMetricLabel?.takeIf { it.isNotBlank() }
                ?: if (fallbackSource.equals("balance", ignoreCase = true)) "Kontostand" else "Punkte",
            suffix = dashboardNodeConfig["suffix"]?.toString()?.takeIf { it.isNotBlank() }
                ?: template?.dashboardMetricSuffix?.takeIf { it.isNotBlank() }
                ?: bankConfig["currency"]?.toString()?.takeIf { it.isNotBlank() },
            sortDirection = dashboardNodeConfig["sortDirection"]?.toString()?.takeIf { it.equals("ASC", true) || it.equals("DESC", true) }?.uppercase()
                ?: template?.dashboardMetricSortDirection?.takeIf { it.equals("ASC", true) || it.equals("DESC", true) }?.uppercase()
                ?: "DESC",
        )
    }

    private fun dashboardMetricValue(
        source: String,
        teamId: UUID,
        balance: BigDecimal?,
        rounds: List<com.example.paulasserver.nfcgame.persistence.entity.NfcSessionRound>,
    ): BigDecimal =
        when (source.lowercase()) {
            "balance", "money", "kontostand" -> balance ?: BigDecimal.ZERO
            "rounds", "wins", "roundwins" -> BigDecimal.valueOf(rounds.count { it.winningTeamId == teamId }.toLong())
            else -> BigDecimal.valueOf(
                rounds
                    .filter { it.winningTeamId == teamId }
                    .sumOf { it.awardedPointsPerMember }
                    .toLong(),
            )
        }

    private data class DashboardMetricConfig(
        val source: String = "points",
        val label: String = "Punkte",
        val suffix: String? = null,
        val sortDirection: String = "DESC",
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
