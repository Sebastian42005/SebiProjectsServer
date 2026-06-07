package com.example.paulasserver.nfcgame.application.statistics

import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameSession
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayerStatsProjection
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameResultRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameSessionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameTemplateRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerStatsProjectionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionRoundRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionAccountRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamMemberRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionValueRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@Service
class NfcStatisticsService(
    private val statsRepository: NfcPlayerStatsProjectionRepository,
    private val sessionRepository: NfcGameSessionRepository,
    private val teamRepository: NfcSessionTeamRepository,
    private val memberRepository: NfcSessionTeamMemberRepository,
    private val roundRepository: NfcSessionRoundRepository,
    private val resultRepository: NfcGameResultRepository,
    private val gameTemplateRepository: NfcGameTemplateRepository,
    private val valueRepository: NfcSessionValueRepository,
    private val accountRepository: NfcSessionAccountRepository,
) {
    fun recordRoundWin(winningTeamId: UUID, pointsPerMember: Long = 1) {
        memberRepository.findAllBySessionTeamId(winningTeamId).forEach { member ->
            val playerId = requireNotNull(member.playerId)
            val stats = statsRepository.findById(playerId).orElseGet {
                NfcPlayerStatsProjection().apply { this.playerId = playerId }
            }
            stats.roundsWon += 1
            stats.totalPoints += pointsPerMember
            stats.updatedAt = Instant.now()
            statsRepository.save(stats)
        }
    }

    fun recordGameFinished(
        allTeamIds: Collection<UUID>,
        winningTeamId: UUID?,
        placementPointsByTeam: Map<UUID, Long> = emptyMap(),
    ) {
        allTeamIds.forEach { teamId ->
            memberRepository.findAllBySessionTeamId(teamId).forEach { member ->
                val playerId = requireNotNull(member.playerId)
                val stats = statsRepository.findById(playerId).orElseGet {
                    NfcPlayerStatsProjection().apply { this.playerId = playerId }
                }
                stats.gamesPlayed += 1
                if (teamId == winningTeamId) {
                    stats.gamesWon += 1
                }
                stats.totalPoints += placementPointsByTeam[teamId] ?: 0
                stats.winRate = if (stats.gamesPlayed == 0L) 0.0 else stats.gamesWon.toDouble() / stats.gamesPlayed
                stats.updatedAt = Instant.now()
                statsRepository.save(stats)
            }
        }
    }

    @Transactional
    fun rebuildFromSessions() {
        val statsByPlayerId = statsRepository.findAll()
            .associateBy { requireNotNull(it.playerId) }
            .toMutableMap()

        statsByPlayerId.values.forEach {
            it.gamesPlayed = 0
            it.gamesWon = 0
            it.roundsWon = 0
            it.totalPoints = 0
            it.winRate = 0.0
            it.updatedAt = Instant.now()
        }

        sessionRepository.findAllByOrderByCreatedAtDesc().forEach { session ->
            val sessionId = requireNotNull(session.id)
            val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)
            val teamIds = teams.mapNotNull { it.id }

            roundRepository.findAllBySessionIdOrderByRoundNumberAsc(sessionId).forEach { round ->
                val winningTeamId = round.winningTeamId ?: return@forEach
                memberRepository.findAllBySessionTeamId(winningTeamId).forEach { member ->
                    val stats = statsByPlayerId.getOrCreate(requireNotNull(member.playerId))
                    stats.roundsWon += 1
                    stats.totalPoints += round.awardedPointsPerMember.toLong()
                    stats.updatedAt = Instant.now()
                }
            }

            if (session.status == SessionStatus.FINISHED) {
                val winningTeamId = resultRepository.findBySessionId(sessionId)?.winningTeamId
                val placementPointsByTeam = placementPointAwards(session, teamIds, winningTeamId)
                teamIds.forEach { teamId ->
                    memberRepository.findAllBySessionTeamId(teamId).forEach { member ->
                        val stats = statsByPlayerId.getOrCreate(requireNotNull(member.playerId))
                        stats.gamesPlayed += 1
                        if (teamId == winningTeamId) {
                            stats.gamesWon += 1
                        }
                        stats.totalPoints += placementPointsByTeam[teamId] ?: 0
                        stats.winRate = if (stats.gamesPlayed == 0L) 0.0 else stats.gamesWon.toDouble() / stats.gamesPlayed
                        stats.updatedAt = Instant.now()
                    }
                }
            }
        }

        statsRepository.saveAll(statsByPlayerId.values)
    }

    private fun placementPointAwards(session: NfcGameSession, teamIds: List<UUID>, winningTeamId: UUID?): Map<UUID, Long> {
        if (winningTeamId == null) return emptyMap()
        val template = gameTemplateRepository.findById(requireNotNull(session.gameTemplateId)).orElse(null)
        val awards = linkedMapOf<UUID, Long>()
        val winnerPoints = template?.globalWinnerPoints ?: 5
        if (winnerPoints > 0) awards[winningTeamId] = winnerPoints

        val remaining = rankedTeamIds(session, teamIds.filter { it != winningTeamId })
        listOf(template?.globalSecondPlacePoints, template?.globalThirdPlacePoints)
            .forEachIndexed { index, points ->
                val teamId = remaining.getOrNull(index) ?: return@forEachIndexed
                val positivePoints = points?.takeIf { it > 0 } ?: return@forEachIndexed
                awards[teamId] = positivePoints
            }
        return awards
    }

    private fun rankedTeamIds(session: NfcGameSession, teamIds: List<UUID>): List<UUID> {
        val template = gameTemplateRepository.findById(requireNotNull(session.gameTemplateId)).orElse(null)
        val source = normalizeValueKey(template?.dashboardMetricSource?.takeIf { it.isNotBlank() } ?: "points")
        val lowest = template?.dashboardMetricSortDirection?.equals("ASC", ignoreCase = true) == true
        val rounds = roundRepository.findAllBySessionIdOrderByRoundNumberAsc(requireNotNull(session.id))
        val values = valueRepository.findAllBySessionId(requireNotNull(session.id))
        val accounts = accountRepository.findAllBySessionId(requireNotNull(session.id))
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
            .filter { it.id in teamIds }
        val byValue = if (lowest) {
            compareBy<Pair<UUID, BigDecimal>> { it.second }
        } else {
            compareByDescending<Pair<UUID, BigDecimal>> { it.second }
        }
        return teams
            .mapNotNull { team ->
                val teamId = team.id ?: return@mapNotNull null
                teamId to (
                    values.firstOrNull { it.ownerType == OwnerType.TEAM && it.ownerId == teamId && it.valueKey == source }?.value
                        ?: accounts.firstOrNull { it.ownerType == OwnerType.TEAM && it.teamId == teamId && source == "money" }?.balance
                        ?: BigDecimal.valueOf(rounds.filter { it.winningTeamId == teamId }.sumOf { it.awardedPointsPerMember }.toLong())
                    )
            }
            .sortedWith(byValue.thenBy { pair -> teams.firstOrNull { it.id == pair.first }?.teamOrder ?: Int.MAX_VALUE })
            .map { it.first }
    }

    private fun normalizeValueKey(valueKey: String): String =
        when (valueKey.trim().removePrefix("{").removeSuffix("}").lowercase()) {
            "score", "punkt", "punkte" -> "points"
            "balance", "kontostand", "geld" -> "money"
            else -> valueKey.trim().removePrefix("{").removeSuffix("}").lowercase()
        }

    private fun MutableMap<UUID, NfcPlayerStatsProjection>.getOrCreate(playerId: UUID): NfcPlayerStatsProjection =
        getOrPut(playerId) {
            NfcPlayerStatsProjection().apply { this.playerId = playerId }
        }
}
