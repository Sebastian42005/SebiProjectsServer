package com.example.paulasserver.nfcgame.integration

import com.example.paulasserver.nfcgame.application.publicapi.NfcPublicQueryService
import com.example.paulasserver.nfcgame.application.session.SessionStateMachineService
import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.domain.RoundLimitType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcCard
import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowEdge
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowNode
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameSession
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayer
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeam
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeamMember
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionValue
import com.example.paulasserver.nfcgame.persistence.repository.NfcCardRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcDeviceRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowEdgeRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowNodeRepository
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
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionValueRepository
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.boot.test.context.SpringBootTest
import org.springframework.transaction.support.TransactionTemplate
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

@SpringBootTest
class NfcGamePointsIntegrationTest @Autowired constructor(
    private val stateMachineService: SessionStateMachineService,
    private val publicQueryService: NfcPublicQueryService,
    private val deviceRepository: NfcDeviceRepository,
    private val playerRepository: NfcPlayerRepository,
    private val cardRepository: NfcCardRepository,
    private val gameTemplateRepository: NfcGameTemplateRepository,
    private val flowNodeRepository: NfcFlowNodeRepository,
    private val flowEdgeRepository: NfcFlowEdgeRepository,
    private val sessionRepository: NfcGameSessionRepository,
    private val teamRepository: NfcSessionTeamRepository,
    private val memberRepository: NfcSessionTeamMemberRepository,
    private val roundRepository: NfcSessionRoundRepository,
    private val valueRepository: NfcSessionValueRepository,
    private val accountRepository: NfcSessionAccountRepository,
    private val moneyTransactionRepository: NfcMoneyTransactionRepository,
    private val resultRepository: NfcGameResultRepository,
    private val eventRepository: NfcSessionEventRepository,
    private val statsRepository: NfcPlayerStatsProjectionRepository,
    private val transactionTemplate: TransactionTemplate,
) {
    private val accountIdsToClean = mutableSetOf<Long>()

    @AfterEach
    fun cleanUp() {
        accountIdsToClean.forEach(::deleteAccountData)
        accountIdsToClean.clear()
    }

    @Test
    fun `finishing a game assigns configured global placement points to every player and exposes them on leaderboard and game night board`() {
        val accountId = nextAccountId()
        val game = game(accountId, "Basisdaten Punkte Spiel").apply {
            globalWinnerPoints = 11
            globalSecondPlacePoints = 7
            globalThirdPlacePoints = 3
            dashboardMetricSource = "points"
            dashboardMetricSortDirection = "DESC"
        }
        gameTemplateRepository.save(game)
        val device = device(accountId)
        val winnerOne = player(accountId, "Ada Gewinnerin")
        val winnerTwo = player(accountId, "Ben Gewinner")
        val second = player(accountId, "Cara Zweite")
        val third = player(accountId, "Dino Dritter")
        val session = session(accountId, game, device)
        val winnerTeam = team(session, 1, "Team Gold", winnerOne, winnerTwo)
        val secondTeam = team(session, 2, "Team Silber", second)
        val thirdTeam = team(session, 3, "Team Bronze", third)

        sessionValue(session, winnerTeam, "points", 8)
        sessionValue(session, secondTeam, "points", 5)
        sessionValue(session, thirdTeam, "points", 2)

        val result = stateMachineService.finishSessionById(requireNotNull(session.id), "INTEGRATION_TEST_FINISH")

        assertEquals(SessionStatus.FINISHED, result.session?.status)
        assertEquals(requireNotNull(winnerTeam.id), resultRepository.findBySessionId(requireNotNull(session.id))?.winningTeamId)
        assertPlayerStats(requireNotNull(winnerOne.id), gamesPlayed = 1, gamesWon = 1, roundsWon = 0, totalPoints = 11)
        assertPlayerStats(requireNotNull(winnerTwo.id), gamesPlayed = 1, gamesWon = 1, roundsWon = 0, totalPoints = 11)
        assertPlayerStats(requireNotNull(second.id), gamesPlayed = 1, gamesWon = 0, roundsWon = 0, totalPoints = 7)
        assertPlayerStats(requireNotNull(third.id), gamesPlayed = 1, gamesWon = 0, roundsWon = 0, totalPoints = 3)

        val leaderboard = publicQueryService.getLeaderboard(accountId)
        assertEquals(setOf(requireNotNull(winnerOne.id), requireNotNull(winnerTwo.id)), leaderboard.take(2).map { it.playerId }.toSet())
        assertEquals(11, leaderboard.first { it.playerId == winnerOne.id }.totalPoints)
        assertEquals(11, leaderboard.first { it.playerId == winnerTwo.id }.totalPoints)
        assertEquals(7, leaderboard.first { it.playerId == second.id }.totalPoints)
        assertEquals(3, leaderboard.first { it.playerId == third.id }.totalPoints)
        val players = publicQueryService.listPlayers(accountId)
        assertEquals(11, players.first { it.id == winnerOne.id }.totalPoints)
        assertEquals(11, players.first { it.id == winnerTwo.id }.totalPoints)
        assertEquals(7, players.first { it.id == second.id }.totalPoints)
        assertEquals(3, players.first { it.id == third.id }.totalPoints)

        val board = publicQueryService.getSession(requireNotNull(session.id), accountId)
        assertEquals(SessionStatus.FINISHED, board.status)
        assertEquals(requireNotNull(winnerTeam.id), board.result?.winningTeamId)
        assertBigDecimalEquals("8", board.teams.first { it.id == winnerTeam.id }.dashboardMetricValue)
        assertBigDecimalEquals("5", board.teams.first { it.id == secondTeam.id }.dashboardMetricValue)
        assertBigDecimalEquals("2", board.teams.first { it.id == thirdTeam.id }.dashboardMetricValue)
        assertEquals(11, board.teams.first { it.id == winnerTeam.id }.placementGlobalPointsAwarded)
        assertEquals(7, board.teams.first { it.id == secondTeam.id }.placementGlobalPointsAwarded)
        assertEquals(3, board.teams.first { it.id == thirdTeam.id }.placementGlobalPointsAwarded)
        assertEquals(11, board.teams.first { it.id == winnerTeam.id }.globalPointsAwarded)
        assertEquals(7, board.teams.first { it.id == secondTeam.id }.globalPointsAwarded)
        assertEquals(3, board.teams.first { it.id == thirdTeam.id }.globalPointsAwarded)

        val gameNightBoard = publicQueryService.getActiveSession(accountId)
        assertNotNull(gameNightBoard)
        assertEquals(board.id, gameNightBoard?.id)
        assertEquals(11, gameNightBoard?.teams?.first { it.id == winnerTeam.id }?.placementGlobalPointsAwarded)
    }

    @Test
    fun `ADD_GLOBAL_POINTS stays pending for career stats until the session is finished`() {
        val accountId = nextAccountId()
        val game = game(accountId, "Builder Globalpunkte Spiel").apply {
            globalWinnerPoints = 0
            dashboardMetricSource = "points"
            dashboardMetricSortDirection = "DESC"
        }
        gameTemplateRepository.save(game)
        val device = device(accountId)
        val winner = player(accountId, "Nora Scan")
        val other = player(accountId, "Otto Ohne Punkte")
        card(accountId, "NORA-CARD-${requireNotNull(winner.id)}", winner)
        val session = session(accountId, game, device)
        val winnerTeam = team(session, 1, "Team Scan", winner)
        val otherTeam = team(session, 2, "Team Leer", other)
        val waitNode = flowNode(game, "WAIT_PLAYER_CARD", "Gewinner scannen", """{"storeAs":"winner"}""", 1)
        val addGlobalNode = flowNode(game, "ADD_GLOBAL_POINTS", "Globale Punkte vergeben", """{"points":4,"target":"winner"}""", 2)
        val logNode = flowNode(game, "LOG_EVENT", "Log", """{"template":"{winner.name} bekommt {amount} globale Punkte."}""", 3)
        flowEdge(game, waitNode, addGlobalNode, "CARD_SCANNED", 1)
        flowEdge(game, addGlobalNode, logNode, "NEXT", 2)
        flowEdge(game, logNode, waitNode, "NEXT", 3)
        session.currentStateKey = requireNotNull(waitNode.id).toString()
        sessionRepository.save(session)

        val scanResult = stateMachineService.handleCardScan(device, "nora-card-${winner.id}")

        assertEquals(SessionStatus.RUNNING, scanResult.session?.status)
        assertEquals("Nora Scan bekommt 4 globale Punkte.", scanResult.timelineMessage)
        assertEquals(1, scanResult.session?.currentRoundNumber)
        assertTrue(valueRepository.findAllBySessionIdAndValueKey(requireNotNull(session.id), "points").isEmpty())
        val rounds = roundRepository.findAllBySessionIdOrderByRoundNumberAsc(requireNotNull(session.id))
        assertEquals(1, rounds.size)
        assertEquals(requireNotNull(winnerTeam.id), rounds.single().winningTeamId)
        assertEquals(4, rounds.single().awardedPointsPerMember)
        assertPlayerStatsMissing(requireNotNull(winner.id))
        assertTrue(statsRepository.findById(requireNotNull(other.id)).isEmpty)

        val board = publicQueryService.getSession(requireNotNull(session.id), accountId)
        assertBigDecimalEquals("4", board.teams.first { it.id == winnerTeam.id }.dashboardMetricValue)
        assertBigDecimalEquals("0", board.teams.first { it.id == otherTeam.id }.dashboardMetricValue)
        assertEquals(4, board.teams.first { it.id == winnerTeam.id }.roundGlobalPointsAwarded)
        assertEquals(4, board.teams.first { it.id == winnerTeam.id }.globalPointsAwarded)
        assertEquals(0, board.teams.first { it.id == otherTeam.id }.globalPointsAwarded)

        assertTrue(publicQueryService.getLeaderboard(accountId).isEmpty())

        stateMachineService.finishSessionById(requireNotNull(session.id), "INTEGRATION_TEST_FINISH")

        assertPlayerStats(requireNotNull(winner.id), gamesPlayed = 1, gamesWon = 1, roundsWon = 1, totalPoints = 4)
        assertPlayerStats(requireNotNull(other.id), gamesPlayed = 1, gamesWon = 0, roundsWon = 0, totalPoints = 0)
        val leaderboardEntry = publicQueryService.getLeaderboard(accountId).first()
        assertEquals(requireNotNull(winner.id), leaderboardEntry.playerId)
        assertEquals(4, leaderboardEntry.totalPoints)
        assertEquals(1, leaderboardEntry.roundsWon)
        assertEquals(1, leaderboardEntry.gamesPlayed)
    }

    @Test
    fun `builder game points drive the board winner and basis-data points are added only when the game finishes`() {
        val accountId = nextAccountId()
        val game = game(accountId, "Builder Spielpunkte Spiel").apply {
            globalWinnerPoints = 9
            globalSecondPlacePoints = 4
            dashboardMetricSource = "points"
            dashboardMetricSortDirection = "DESC"
        }
        gameTemplateRepository.save(game)
        val device = device(accountId)
        val winner = player(accountId, "Vera Value")
        val runnerUp = player(accountId, "Rudi Runner")
        card(accountId, "VERA-CARD-${requireNotNull(winner.id)}", winner)
        val session = session(accountId, game, device)
        val winnerTeam = team(session, 1, "Team Value", winner)
        val runnerUpTeam = team(session, 2, "Team Runner", runnerUp)
        sessionValue(session, runnerUpTeam, "points", 2)
        val waitNode = flowNode(game, "WAIT_PLAYER_CARD", "Gewinner scannen", """{"storeAs":"winner"}""", 1)
        val changeValueNode = flowNode(game, "CHANGE_VALUE", "Spielpunkte vergeben", """{"points":5,"target":"winner","valueKey":"points"}""", 2)
        val logNode = flowNode(game, "LOG_EVENT", "Log", """{"template":"{winner.name} steht bei {winner.points} Spielpunkten."}""", 3)
        flowEdge(game, waitNode, changeValueNode, "CARD_SCANNED", 1)
        flowEdge(game, changeValueNode, logNode, "NEXT", 2)
        flowEdge(game, logNode, waitNode, "NEXT", 3)
        session.currentStateKey = requireNotNull(waitNode.id).toString()
        sessionRepository.save(session)

        val scanResult = stateMachineService.handleCardScan(device, "vera-card-${winner.id}")

        assertEquals("Vera Value steht bei 5 Spielpunkten.", scanResult.timelineMessage)
        assertBigDecimalEquals(
            "5",
            valueRepository.findBySessionIdAndOwnerTypeAndOwnerIdAndValueKey(
                requireNotNull(session.id),
                OwnerType.TEAM,
                requireNotNull(winnerTeam.id),
                "points",
            )?.value,
        )
        assertPlayerStatsMissing(requireNotNull(winner.id))
        val liveBoard = publicQueryService.getSession(requireNotNull(session.id), accountId)
        assertBigDecimalEquals("5", liveBoard.teams.first { it.id == winnerTeam.id }.dashboardMetricValue)
        assertBigDecimalEquals("2", liveBoard.teams.first { it.id == runnerUpTeam.id }.dashboardMetricValue)
        assertEquals(0, liveBoard.teams.first { it.id == winnerTeam.id }.globalPointsAwarded)

        stateMachineService.finishSessionById(requireNotNull(session.id), "INTEGRATION_TEST_FINISH")

        assertPlayerStats(requireNotNull(winner.id), gamesPlayed = 1, gamesWon = 1, roundsWon = 0, totalPoints = 9)
        assertPlayerStats(requireNotNull(runnerUp.id), gamesPlayed = 1, gamesWon = 0, roundsWon = 0, totalPoints = 4)
        val finishedBoard = publicQueryService.getSession(requireNotNull(session.id), accountId)
        assertEquals(requireNotNull(winnerTeam.id), finishedBoard.result?.winningTeamId)
        assertEquals(9, finishedBoard.teams.first { it.id == winnerTeam.id }.placementGlobalPointsAwarded)
        assertEquals(4, finishedBoard.teams.first { it.id == runnerUpTeam.id }.placementGlobalPointsAwarded)
        assertEquals(listOf(requireNotNull(winner.id), requireNotNull(runnerUp.id)), publicQueryService.getLeaderboard(accountId).map { it.playerId })
    }

    private fun nextAccountId(): Long =
        nextAccount.getAndIncrement().also {
            deleteAccountData(it)
            accountIdsToClean.add(it)
        }

    private fun game(accountId: Long, name: String): NfcGameTemplate =
        gameTemplateRepository.save(
            NfcGameTemplate().apply {
                this.name = "$name $accountId"
                this.accountId = accountId
                active = true
                allowTeams = true
                minTeamSize = 1
                maxTeamSize = 4
                dashboardMetricSource = "points"
                dashboardMetricLabel = "Punkte"
                dashboardMetricSortDirection = "DESC"
                dashboardStatusSource = "currentRound"
                dashboardStatusMaxSource = "roundLimit"
                createdAt = Instant.now()
                updatedAt = Instant.now()
            },
        )

    private fun device(accountId: Long): NfcDevice =
        deviceRepository.save(
            NfcDevice().apply {
                name = "points-it-device-$accountId-${UUID.randomUUID()}"
                deviceKey = "points-it-key-$accountId"
                this.accountId = accountId
                active = true
            },
        )

    private fun player(accountId: Long, name: String): NfcPlayer =
        playerRepository.save(
            NfcPlayer().apply {
                this.name = name
                this.accountId = accountId
                active = true
            },
        )

    private fun card(accountId: Long, uid: String, player: NfcPlayer): NfcCard =
        cardRepository.save(
            NfcCard().apply {
                cardUid = uid.uppercase()
                cardType = CardType.PLAYER
                status = CardStatus.ASSIGNED
                playerId = requireNotNull(player.id)
                this.accountId = accountId
            },
        )

    private fun session(accountId: Long, game: NfcGameTemplate, device: NfcDevice): NfcGameSession =
        sessionRepository.save(
            NfcGameSession().apply {
                gameTemplateId = requireNotNull(game.id)
                deviceId = requireNotNull(device.id)
                this.accountId = accountId
                status = SessionStatus.RUNNING
                currentStateKey = "running"
                roundLimitType = RoundLimitType.NONE
                currentRoundNumber = 0
                startedAt = Instant.now()
            },
        )

    private fun team(session: NfcGameSession, order: Int, name: String, vararg players: NfcPlayer): NfcSessionTeam {
        val team = teamRepository.save(
            NfcSessionTeam().apply {
                sessionId = requireNotNull(session.id)
                this.name = name
                teamOrder = order
                targetSize = players.size
                status = "COMPLETE"
            },
        )
        players.forEach { player ->
            memberRepository.save(
                NfcSessionTeamMember().apply {
                    sessionTeamId = requireNotNull(team.id)
                    playerId = requireNotNull(player.id)
                },
            )
        }
        return team
    }

    private fun sessionValue(session: NfcGameSession, team: NfcSessionTeam, key: String, value: Long): NfcSessionValue =
        valueRepository.save(
            NfcSessionValue().apply {
                sessionId = requireNotNull(session.id)
                ownerType = OwnerType.TEAM
                ownerId = requireNotNull(team.id)
                valueKey = key
                this.value = BigDecimal.valueOf(value)
            },
        )

    private fun flowNode(game: NfcGameTemplate, type: String, title: String, configJson: String, order: Int): NfcFlowNode =
        flowNodeRepository.save(
            NfcFlowNode().apply {
                gameTemplateId = requireNotNull(game.id)
                this.type = type
                this.title = title
                x = 0
                y = order * 100
                this.configJson = configJson
                sortOrder = order
            },
        )

    private fun flowEdge(game: NfcGameTemplate, source: NfcFlowNode, target: NfcFlowNode, eventType: String, priority: Int): NfcFlowEdge =
        flowEdgeRepository.save(
            NfcFlowEdge().apply {
                gameTemplateId = requireNotNull(game.id)
                sourceNodeId = requireNotNull(source.id)
                targetNodeId = requireNotNull(target.id)
                this.eventType = eventType
                this.priority = priority
            },
        )

    private fun assertPlayerStats(playerId: UUID, gamesPlayed: Long, gamesWon: Long, roundsWon: Long, totalPoints: Long) {
        val stats = statsRepository.findById(playerId).orElseThrow { AssertionError("Missing stats for player $playerId") }
        assertEquals(gamesPlayed, stats.gamesPlayed)
        assertEquals(gamesWon, stats.gamesWon)
        assertEquals(roundsWon, stats.roundsWon)
        assertEquals(totalPoints, stats.totalPoints)
        assertEquals(if (gamesPlayed == 0L) 0.0 else gamesWon.toDouble() / gamesPlayed, stats.winRate)
    }

    private fun assertPlayerStatsMissing(playerId: UUID) {
        assertTrue(statsRepository.findById(playerId).isEmpty, "Expected no global stats yet for player $playerId")
    }

    private fun assertBigDecimalEquals(expected: String, actual: BigDecimal?) {
        assertNotNull(actual)
        assertEquals(0, BigDecimal(expected).compareTo(actual))
    }

    private fun deleteAccountData(accountId: Long) {
        transactionTemplate.executeWithoutResult {
            val playerIds = playerRepository.findAllByAccountIdOrderByNameAsc(accountId).mapNotNull { it.id }
            val sessions = sessionRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId)
            sessions.forEach { session ->
                val sessionId = requireNotNull(session.id)
                eventRepository.deleteAllBySessionId(sessionId)
                moneyTransactionRepository.deleteAllBySessionId(sessionId)
                accountRepository.deleteAllBySessionId(sessionId)
                valueRepository.deleteAllBySessionId(sessionId)
                roundRepository.deleteAllBySessionId(sessionId)
                resultRepository.deleteBySessionId(sessionId)
                val teamIds = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId).mapNotNull { it.id }
                if (teamIds.isNotEmpty()) {
                    memberRepository.deleteAllBySessionTeamIdIn(teamIds)
                }
                teamRepository.deleteAllBySessionId(sessionId)
            }
            sessionRepository.deleteAll(sessions)
            cardRepository.deleteAllByAccountId(accountId)
            gameTemplateRepository.findAllByAccountIdOrderByUpdatedAtDesc(accountId).forEach { game ->
                val gameId = requireNotNull(game.id)
                flowEdgeRepository.deleteAllByGameTemplateId(gameId)
                flowNodeRepository.deleteAllByGameTemplateId(gameId)
            }
            gameTemplateRepository.deleteAllByAccountId(accountId)
            statsRepository.deleteAllById(playerIds)
            playerRepository.deleteAllByAccountId(accountId)
            deviceRepository.deleteAllByAccountId(accountId)
        }
    }

    companion object {
        private val nextAccount = AtomicLong(9_000_000L)
    }
}
