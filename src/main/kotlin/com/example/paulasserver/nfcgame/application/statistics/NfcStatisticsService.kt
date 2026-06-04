package com.example.paulasserver.nfcgame.application.statistics

import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayerStatsProjection
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameResultRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameSessionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerStatsProjectionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionRoundRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamMemberRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
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

    fun recordGameFinished(allTeamIds: Collection<UUID>, winningTeamId: UUID?) {
        allTeamIds.forEach { teamId ->
            memberRepository.findAllBySessionTeamId(teamId).forEach { member ->
                val playerId = requireNotNull(member.playerId)
                val stats = statsRepository.findById(playerId).orElseGet {
                    NfcPlayerStatsProjection().apply { this.playerId = playerId }
                }
                stats.gamesPlayed += 1
                if (teamId == winningTeamId) {
                    stats.gamesWon += 1
                    stats.totalPoints += 5
                }
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
                teamIds.forEach { teamId ->
                    memberRepository.findAllBySessionTeamId(teamId).forEach { member ->
                        val stats = statsByPlayerId.getOrCreate(requireNotNull(member.playerId))
                        stats.gamesPlayed += 1
                        if (teamId == winningTeamId) {
                            stats.gamesWon += 1
                            stats.totalPoints += 5
                        }
                        stats.winRate = if (stats.gamesPlayed == 0L) 0.0 else stats.gamesWon.toDouble() / stats.gamesPlayed
                        stats.updatedAt = Instant.now()
                    }
                }
            }
        }

        statsRepository.saveAll(statsByPlayerId.values)
    }

    private fun MutableMap<UUID, NfcPlayerStatsProjection>.getOrCreate(playerId: UUID): NfcPlayerStatsProjection =
        getOrPut(playerId) {
            NfcPlayerStatsProjection().apply { this.playerId = playerId }
        }
}
