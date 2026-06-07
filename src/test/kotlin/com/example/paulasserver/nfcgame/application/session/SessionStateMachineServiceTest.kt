package com.example.paulasserver.nfcgame.application.session

import com.example.paulasserver.nfcgame.application.statistics.NfcStatisticsService
import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.EventType
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcCard
import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowEdge
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowNode
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameSession
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayer
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionRound
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeam
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeamMember
import com.example.paulasserver.nfcgame.persistence.repository.NfcCardRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowEdgeRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowNodeRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameResultRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameSessionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcGameTemplateRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcMoneyTransactionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcPlayerRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionAccountRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionRoundRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamMemberRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionTeamRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcSessionValueRepository
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyCollection
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.util.Optional
import java.util.UUID

class SessionStateMachineServiceTest {
    private val cardRepository = mock(NfcCardRepository::class.java)
    private val gameTemplateRepository = mock(NfcGameTemplateRepository::class.java)
    private val playerRepository = mock(NfcPlayerRepository::class.java)
    private val sessionRepository = mock(NfcGameSessionRepository::class.java)
    private val flowNodeRepository = mock(NfcFlowNodeRepository::class.java)
    private val flowEdgeRepository = mock(NfcFlowEdgeRepository::class.java)
    private val teamRepository = mock(NfcSessionTeamRepository::class.java)
    private val memberRepository = mock(NfcSessionTeamMemberRepository::class.java)
    private val roundRepository = mock(NfcSessionRoundRepository::class.java)
    private val accountRepository = mock(NfcSessionAccountRepository::class.java)
    private val valueRepository = mock(NfcSessionValueRepository::class.java)
    private val moneyTransactionRepository = mock(NfcMoneyTransactionRepository::class.java)
    private val resultRepository = mock(NfcGameResultRepository::class.java)
    private val statisticsService = mock(NfcStatisticsService::class.java)
    private val objectMapper = ObjectMapper().registerKotlinModule()

    private val service = SessionStateMachineService(
        cardRepository,
        gameTemplateRepository,
        playerRepository,
        sessionRepository,
        flowNodeRepository,
        flowEdgeRepository,
        teamRepository,
        memberRepository,
        roundRepository,
        accountRepository,
        valueRepository,
        moneyTransactionRepository,
        resultRepository,
        statisticsService,
        objectMapper,
    )

    @Test
    fun `player scan that awards a point does not create a timeline message without a log node`() {
        val accountId = 42L
        val deviceId = UUID.randomUUID()
        val gameTemplateId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val teamId = UUID.randomUUID()
        val waitNodeId = UUID.randomUUID()
        val awardNodeId = UUID.randomUUID()

        val device = NfcDevice().apply {
            id = deviceId
            this.accountId = accountId
        }
        val session = NfcGameSession().apply {
            id = sessionId
            this.accountId = accountId
            this.gameTemplateId = gameTemplateId
            this.deviceId = deviceId
            status = SessionStatus.RUNNING
            currentStateKey = waitNodeId.toString()
        }
        val card = NfcCard().apply {
            cardUid = "TEDDY-CARD"
            cardType = CardType.PLAYER
            status = CardStatus.ASSIGNED
            this.playerId = playerId
            this.accountId = accountId
        }
        val player = NfcPlayer().apply {
            id = playerId
            name = "Teddy"
            active = true
            this.accountId = accountId
        }
        val team = NfcSessionTeam().apply {
            id = teamId
            this.sessionId = sessionId
            name = "Team 1"
            teamOrder = 1
        }
        val member = NfcSessionTeamMember().apply {
            id = UUID.randomUUID()
            sessionTeamId = teamId
            this.playerId = playerId
        }
        val waitNode = NfcFlowNode().apply {
            id = waitNodeId
            this.gameTemplateId = gameTemplateId
            type = "WAIT_PLAYER_CARD"
            title = "Spielerkarte scannen"
            configJson = """{"storeAs":"scannedPlayer"}"""
        }
        val awardNode = NfcFlowNode().apply {
            id = awardNodeId
            this.gameTemplateId = gameTemplateId
            type = "CHANGE_VALUE"
            title = "Punkt vergeben"
            configJson = """{"points":1,"target":"scannedPlayer"}"""
        }
        val scanToAward = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = waitNodeId
            targetNodeId = awardNodeId
            eventType = "CARD_SCANNED"
            priority = 1
        }
        val awardToWait = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = awardNodeId
            targetNodeId = waitNodeId
            eventType = "NEXT"
            priority = 2
        }

        `when`(cardRepository.findByCardUid("TEDDY-CARD")).thenReturn(card)
        `when`(sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, listOf(SessionStatus.LOBBY, SessionStatus.CONFIGURING, SessionStatus.BUILDING_TEAMS, SessionStatus.READY, SessionStatus.RUNNING))).thenReturn(session)
        `when`(playerRepository.findById(playerId)).thenReturn(Optional.of(player))
        `when`(teamRepository.findById(teamId)).thenReturn(Optional.of(team))
        `when`(teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)).thenReturn(listOf(team))
        `when`(memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, listOf(teamId))).thenReturn(member)
        `when`(flowNodeRepository.findById(waitNodeId)).thenReturn(Optional.of(waitNode))
        `when`(flowNodeRepository.findById(awardNodeId)).thenReturn(Optional.of(awardNode))
        `when`(flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(gameTemplateId)).thenReturn(listOf(scanToAward, awardToWait))
        `when`(roundRepository.save(any(NfcSessionRound::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        `when`(sessionRepository.save(any(NfcGameSession::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        stubNoStoredTeamPoints(sessionId, teamId)

        val result = service.handleCardScan(device, "teddy-card")

        assertNull(result.timelineMessage)
        assertEquals(1, session.currentRoundNumber)
        assertTrue(session.currentStateKey.startsWith(waitNodeId.toString()))
        assertNotNull(result.screen)
        verify(roundRepository, never()).saveAll(anyCollection<NfcSessionRound>())
    }

    @Test
    fun `legacy running player scan enters the builder flow and uses the configured log event`() {
        val accountId = 42L
        val deviceId = UUID.randomUUID()
        val gameTemplateId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val teamId = UUID.randomUUID()
        val waitNodeId = UUID.randomUUID()
        val awardNodeId = UUID.randomUUID()
        val logNodeId = UUID.randomUUID()

        val device = NfcDevice().apply {
            id = deviceId
            this.accountId = accountId
        }
        val session = NfcGameSession().apply {
            id = sessionId
            this.accountId = accountId
            this.gameTemplateId = gameTemplateId
            this.deviceId = deviceId
            status = SessionStatus.RUNNING
            currentStateKey = "running"
        }
        val card = NfcCard().apply {
            cardUid = "TEDDY-CARD"
            cardType = CardType.PLAYER
            status = CardStatus.ASSIGNED
            this.playerId = playerId
            this.accountId = accountId
        }
        val player = NfcPlayer().apply {
            id = playerId
            name = "Teddy"
            active = true
            this.accountId = accountId
        }
        val team = NfcSessionTeam().apply {
            id = teamId
            this.sessionId = sessionId
            name = "Team 1"
            teamOrder = 1
        }
        val member = NfcSessionTeamMember().apply {
            id = UUID.randomUUID()
            sessionTeamId = teamId
            this.playerId = playerId
        }
        val waitNode = NfcFlowNode().apply {
            id = waitNodeId
            this.gameTemplateId = gameTemplateId
            type = "WAIT_PLAYER_CARD"
            title = "Spielerkarte scannen"
            configJson = """{"storeAs":"scannedPlayer"}"""
            sortOrder = 1
        }
        val awardNode = NfcFlowNode().apply {
            id = awardNodeId
            this.gameTemplateId = gameTemplateId
            type = "CHANGE_VALUE"
            title = "Punkt vergeben"
            configJson = """{"points":1,"target":"scannedPlayer"}"""
            sortOrder = 2
        }
        val logNode = NfcFlowNode().apply {
            id = logNodeId
            this.gameTemplateId = gameTemplateId
            type = "LOG_EVENT"
            title = "Timeline schreiben"
            configJson = """{"template":"{scannedPlayer} hat {amount} Test bekommen."}"""
            sortOrder = 3
        }
        val scanToAward = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = waitNodeId
            targetNodeId = awardNodeId
            eventType = "CARD_SCANNED"
            priority = 1
        }
        val awardToLog = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = awardNodeId
            targetNodeId = logNodeId
            eventType = "NEXT"
            priority = 2
        }

        `when`(cardRepository.findByCardUid("TEDDY-CARD")).thenReturn(card)
        `when`(sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, listOf(SessionStatus.LOBBY, SessionStatus.CONFIGURING, SessionStatus.BUILDING_TEAMS, SessionStatus.READY, SessionStatus.RUNNING))).thenReturn(session)
        `when`(playerRepository.findById(playerId)).thenReturn(Optional.of(player))
        `when`(teamRepository.findById(teamId)).thenReturn(Optional.of(team))
        `when`(teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)).thenReturn(listOf(team))
        `when`(memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, listOf(teamId))).thenReturn(member)
        `when`(flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(gameTemplateId)).thenReturn(listOf(waitNode, awardNode, logNode))
        `when`(flowNodeRepository.findById(awardNodeId)).thenReturn(Optional.of(awardNode))
        `when`(flowNodeRepository.findById(logNodeId)).thenReturn(Optional.of(logNode))
        `when`(flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(gameTemplateId)).thenReturn(listOf(scanToAward, awardToLog))
        `when`(roundRepository.save(any(NfcSessionRound::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        `when`(sessionRepository.save(any(NfcGameSession::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        stubNoStoredTeamPoints(sessionId, teamId)

        val result = service.handleCardScan(device, "teddy-card")

        assertEquals("Teddy hat 1 Test bekommen.", result.timelineMessage)
        assertEquals(1, session.currentRoundNumber)
        verify(roundRepository, never()).saveAll(anyCollection<NfcSessionRound>())
    }

    @Test
    fun `builder templates can render the current placement for a scanned player`() {
        val accountId = 42L
        val deviceId = UUID.randomUUID()
        val gameTemplateId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val leadingTeamId = UUID.randomUUID()
        val scannedTeamId = UUID.randomUUID()
        val waitNodeId = UUID.randomUUID()
        val logNodeId = UUID.randomUUID()

        val device = NfcDevice().apply {
            id = deviceId
            this.accountId = accountId
        }
        val template = NfcGameTemplate().apply {
            id = gameTemplateId
            dashboardMetricSource = "points"
            dashboardMetricSortDirection = "DESC"
        }
        val session = NfcGameSession().apply {
            id = sessionId
            this.accountId = accountId
            this.gameTemplateId = gameTemplateId
            this.deviceId = deviceId
            status = SessionStatus.RUNNING
            currentStateKey = waitNodeId.toString()
        }
        val card = NfcCard().apply {
            cardUid = "TEDDY-CARD"
            cardType = CardType.PLAYER
            status = CardStatus.ASSIGNED
            this.playerId = playerId
            this.accountId = accountId
        }
        val player = NfcPlayer().apply {
            id = playerId
            name = "Teddy"
            active = true
            this.accountId = accountId
        }
        val leadingTeam = NfcSessionTeam().apply {
            id = leadingTeamId
            this.sessionId = sessionId
            name = "Team 1"
            teamOrder = 1
        }
        val scannedTeam = NfcSessionTeam().apply {
            id = scannedTeamId
            this.sessionId = sessionId
            name = "Team 2"
            teamOrder = 2
        }
        val member = NfcSessionTeamMember().apply {
            id = UUID.randomUUID()
            sessionTeamId = scannedTeamId
            this.playerId = playerId
        }
        val rounds = listOf(
            round(sessionId, leadingTeamId, 1, 3),
            round(sessionId, scannedTeamId, 2, 1),
        )
        val waitNode = NfcFlowNode().apply {
            id = waitNodeId
            this.gameTemplateId = gameTemplateId
            type = "WAIT_PLAYER_CARD"
            title = "Spielerkarte scannen"
            configJson = """{"storeAs":"scannedPlayer"}"""
            sortOrder = 1
        }
        val logNode = NfcFlowNode().apply {
            id = logNodeId
            this.gameTemplateId = gameTemplateId
            type = "LOG_EVENT"
            title = "Timeline schreiben"
            configJson = """{"template":"{scannedPlayer.name} ist Platz {scannedPlayer.placement} / {scannedPlayer.rank} / {scannedPlayer.platzierung}."}"""
            sortOrder = 2
        }
        val scanToLog = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = waitNodeId
            targetNodeId = logNodeId
            eventType = "CARD_SCANNED"
            priority = 1
        }

        `when`(cardRepository.findByCardUid("TEDDY-CARD")).thenReturn(card)
        `when`(sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, listOf(SessionStatus.LOBBY, SessionStatus.CONFIGURING, SessionStatus.BUILDING_TEAMS, SessionStatus.READY, SessionStatus.RUNNING))).thenReturn(session)
        `when`(gameTemplateRepository.findById(gameTemplateId)).thenReturn(Optional.of(template))
        `when`(playerRepository.findById(playerId)).thenReturn(Optional.of(player))
        `when`(teamRepository.findById(scannedTeamId)).thenReturn(Optional.of(scannedTeam))
        `when`(teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)).thenReturn(listOf(leadingTeam, scannedTeam))
        `when`(memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, listOf(leadingTeamId, scannedTeamId))).thenReturn(member)
        `when`(flowNodeRepository.findById(waitNodeId)).thenReturn(Optional.of(waitNode))
        `when`(flowNodeRepository.findById(logNodeId)).thenReturn(Optional.of(logNode))
        `when`(flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(gameTemplateId)).thenReturn(listOf(scanToLog))
        `when`(roundRepository.findAllBySessionIdOrderByRoundNumberAsc(sessionId)).thenReturn(rounds)
        `when`(sessionRepository.save(any(NfcGameSession::class.java))).thenAnswer { invocation -> invocation.arguments[0] }

        val result = service.handleCardScan(device, "teddy-card")

        assertEquals("Teddy ist Platz 2 / 2 / 2.", result.timelineMessage)
    }

    @Test
    fun `award flow uses the configured log event template instead of hardcoded points text`() {
        val accountId = 42L
        val deviceId = UUID.randomUUID()
        val gameTemplateId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val teamId = UUID.randomUUID()
        val waitNodeId = UUID.randomUUID()
        val awardNodeId = UUID.randomUUID()
        val logNodeId = UUID.randomUUID()
        val checkNodeId = UUID.randomUUID()

        val device = NfcDevice().apply {
            id = deviceId
            this.accountId = accountId
        }
        val session = NfcGameSession().apply {
            id = sessionId
            this.accountId = accountId
            this.gameTemplateId = gameTemplateId
            this.deviceId = deviceId
            status = SessionStatus.RUNNING
            currentStateKey = waitNodeId.toString()
        }
        val card = NfcCard().apply {
            cardUid = "TEDDY-CARD"
            cardType = CardType.PLAYER
            status = CardStatus.ASSIGNED
            this.playerId = playerId
            this.accountId = accountId
        }
        val player = NfcPlayer().apply {
            id = playerId
            name = "Teddy"
            active = true
            this.accountId = accountId
        }
        val team = NfcSessionTeam().apply {
            id = teamId
            this.sessionId = sessionId
            name = "Team 1"
            teamOrder = 1
        }
        val member = NfcSessionTeamMember().apply {
            id = UUID.randomUUID()
            sessionTeamId = teamId
            this.playerId = playerId
        }
        val waitNode = NfcFlowNode().apply {
            id = waitNodeId
            this.gameTemplateId = gameTemplateId
            type = "WAIT_PLAYER_CARD"
            title = "Spielerkarte scannen"
            configJson = """{"storeAs":"scannedPlayer"}"""
            sortOrder = 1
        }
        val awardNode = NfcFlowNode().apply {
            id = awardNodeId
            this.gameTemplateId = gameTemplateId
            type = "CHANGE_VALUE"
            title = "Punkt vergeben"
            configJson = """{"points":1,"target":"scannedPlayer"}"""
            sortOrder = 2
        }
        val logNode = NfcFlowNode().apply {
            id = logNodeId
            this.gameTemplateId = gameTemplateId
            type = "LOG_EVENT"
            title = "Timeline schreiben"
            configJson = """{"template":"{scannedPlayer} hat {amount} Nudeln bekommen."}"""
            sortOrder = 3
        }
        val checkNode = NfcFlowNode().apply {
            id = checkNodeId
            this.gameTemplateId = gameTemplateId
            type = "IF_ELSE"
            title = "Rundenlimit pruefen"
            configJson = """{"expression":"roundLimit == null || currentRound < roundLimit"}"""
            sortOrder = 4
        }
        val scanToAward = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = waitNodeId
            targetNodeId = awardNodeId
            eventType = "CARD_SCANNED"
            priority = 1
        }
        val awardToLog = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = awardNodeId
            targetNodeId = logNodeId
            eventType = "NEXT"
            priority = 3
        }
        val oldAwardToCheck = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = awardNodeId
            targetNodeId = checkNodeId
            eventType = "NEXT"
            priority = 2
        }
        val logToWait = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = logNodeId
            targetNodeId = waitNodeId
            eventType = "NEXT"
            priority = 4
        }

        `when`(cardRepository.findByCardUid("TEDDY-CARD")).thenReturn(card)
        `when`(sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, listOf(SessionStatus.LOBBY, SessionStatus.CONFIGURING, SessionStatus.BUILDING_TEAMS, SessionStatus.READY, SessionStatus.RUNNING))).thenReturn(session)
        `when`(playerRepository.findById(playerId)).thenReturn(Optional.of(player))
        `when`(teamRepository.findById(teamId)).thenReturn(Optional.of(team))
        `when`(teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)).thenReturn(listOf(team))
        `when`(memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, listOf(teamId))).thenReturn(member)
        `when`(flowNodeRepository.findById(waitNodeId)).thenReturn(Optional.of(waitNode))
        `when`(flowNodeRepository.findById(awardNodeId)).thenReturn(Optional.of(awardNode))
        `when`(flowNodeRepository.findById(logNodeId)).thenReturn(Optional.of(logNode))
        `when`(flowNodeRepository.findById(checkNodeId)).thenReturn(Optional.of(checkNode))
        `when`(flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(gameTemplateId)).thenReturn(listOf(waitNode, awardNode, logNode, checkNode))
        `when`(flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(gameTemplateId)).thenReturn(listOf(scanToAward, oldAwardToCheck, awardToLog, logToWait))
        `when`(roundRepository.save(any(NfcSessionRound::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        `when`(sessionRepository.save(any(NfcGameSession::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        stubNoStoredTeamPoints(sessionId, teamId)

        val result = service.handleCardScan(device, "teddy-card")

        assertEquals("Teddy hat 1 Nudeln bekommen.", result.timelineMessage)
        assertEquals(1, session.currentRoundNumber)
        verify(roundRepository, never()).saveAll(anyCollection<NfcSessionRound>())
    }

    @Test
    fun `unlimited menu flow advances through show message and logs the awarded point`() {
        val accountId = 42L
        val deviceId = UUID.randomUUID()
        val gameTemplateId = UUID.randomUUID()
        val sessionId = UUID.randomUUID()
        val playerId = UUID.randomUUID()
        val teamId = UUID.randomUUID()
        val menuNodeId = UUID.randomUUID()
        val showNodeId = UUID.randomUUID()
        val waitNodeId = UUID.randomUUID()
        val awardNodeId = UUID.randomUUID()
        val logNodeId = UUID.randomUUID()

        val device = NfcDevice().apply {
            id = deviceId
            this.accountId = accountId
        }
        val session = NfcGameSession().apply {
            id = sessionId
            this.accountId = accountId
            this.gameTemplateId = gameTemplateId
            this.deviceId = deviceId
            status = SessionStatus.RUNNING
            currentStateKey = menuNodeId.toString()
        }
        val card = NfcCard().apply {
            cardUid = "TEDDY-CARD"
            cardType = CardType.PLAYER
            status = CardStatus.ASSIGNED
            this.playerId = playerId
            this.accountId = accountId
        }
        val player = NfcPlayer().apply {
            id = playerId
            name = "Teddy"
            active = true
            this.accountId = accountId
        }
        val team = NfcSessionTeam().apply {
            id = teamId
            this.sessionId = sessionId
            name = "Team 1"
            teamOrder = 1
        }
        val member = NfcSessionTeamMember().apply {
            id = UUID.randomUUID()
            sessionTeamId = teamId
            this.playerId = playerId
        }
        val menuNode = NfcFlowNode().apply {
            id = menuNodeId
            this.gameTemplateId = gameTemplateId
            type = "MENU"
            title = "Rundenmodus waehlen"
            configJson = """{"options":["Unbegrenzt","Begrenzt"]}"""
            sortOrder = 1
        }
        val showNode = NfcFlowNode().apply {
            id = showNodeId
            this.gameTemplateId = gameTemplateId
            type = "SHOW_MESSAGE"
            title = "Unbegrenzt spielen"
            configJson = """{"text":"Spiel laeuft ohne Rundenlimit"}"""
            sortOrder = 2
        }
        val waitNode = NfcFlowNode().apply {
            id = waitNodeId
            this.gameTemplateId = gameTemplateId
            type = "WAIT_PLAYER_CARD"
            title = "Spielerkarte scannen"
            configJson = """{"storeAs":"scannedPlayer"}"""
            sortOrder = 3
        }
        val awardNode = NfcFlowNode().apply {
            id = awardNodeId
            this.gameTemplateId = gameTemplateId
            type = "CHANGE_VALUE"
            title = "Punkt vergeben"
            configJson = """{"points":1,"target":"scannedPlayer"}"""
            sortOrder = 4
        }
        val logNode = NfcFlowNode().apply {
            id = logNodeId
            this.gameTemplateId = gameTemplateId
            type = "LOG_EVENT"
            title = "Timeline schreiben"
            configJson = """{"template":"{scannedPlayer} hat {amount} Testssssss bekommen."}"""
            sortOrder = 5
        }
        val unlimitedEdge = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = menuNodeId
            targetNodeId = showNodeId
            eventType = "UNLIMITED_SELECTED"
            priority = 1
            conditionConfigJson = """{"selection":"Unbegrenzt"}"""
        }
        val showToWait = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = showNodeId
            targetNodeId = waitNodeId
            eventType = "NEXT"
            priority = 2
        }
        val scanToAward = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = waitNodeId
            targetNodeId = awardNodeId
            eventType = "CARD_SCANNED"
            priority = 3
        }
        val awardToLog = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = awardNodeId
            targetNodeId = logNodeId
            eventType = "NEXT"
            priority = 4
        }
        val logToWait = NfcFlowEdge().apply {
            id = UUID.randomUUID()
            this.gameTemplateId = gameTemplateId
            sourceNodeId = logNodeId
            targetNodeId = waitNodeId
            eventType = "NEXT"
            priority = 5
        }

        `when`(sessionRepository.findById(sessionId)).thenReturn(Optional.of(session))
        `when`(sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, listOf(SessionStatus.LOBBY, SessionStatus.CONFIGURING, SessionStatus.BUILDING_TEAMS, SessionStatus.READY, SessionStatus.RUNNING))).thenReturn(session)
        `when`(cardRepository.findByCardUid("TEDDY-CARD")).thenReturn(card)
        `when`(playerRepository.findById(playerId)).thenReturn(Optional.of(player))
        `when`(teamRepository.findById(teamId)).thenReturn(Optional.of(team))
        `when`(teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)).thenReturn(listOf(team))
        `when`(memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, listOf(teamId))).thenReturn(member)
        `when`(flowNodeRepository.findById(menuNodeId)).thenReturn(Optional.of(menuNode))
        `when`(flowNodeRepository.findById(showNodeId)).thenReturn(Optional.of(showNode))
        `when`(flowNodeRepository.findById(waitNodeId)).thenReturn(Optional.of(waitNode))
        `when`(flowNodeRepository.findById(awardNodeId)).thenReturn(Optional.of(awardNode))
        `when`(flowNodeRepository.findById(logNodeId)).thenReturn(Optional.of(logNode))
        `when`(flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(gameTemplateId)).thenReturn(listOf(unlimitedEdge, showToWait, scanToAward, awardToLog, logToWait))
        `when`(roundRepository.save(any(NfcSessionRound::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        `when`(sessionRepository.save(any(NfcGameSession::class.java))).thenAnswer { invocation -> invocation.arguments[0] }
        stubNoStoredTeamPoints(sessionId, teamId)

        val menuResult = service.handleInput(sessionId, EventType.TOUCH_MENU_SELECT, mapOf("label" to "Unbegrenzt"))

        assertTrue(session.currentStateKey.startsWith(waitNodeId.toString()))
        assertEquals("Spielerkarte scannen", menuResult.screen.title)

        val scanResult = service.handleCardScan(device, "teddy-card")

        assertEquals("Teddy hat 1 Testssssss bekommen.", scanResult.timelineMessage)
        assertEquals(1, session.currentRoundNumber)
        verify(roundRepository, never()).saveAll(anyCollection<NfcSessionRound>())
    }

    private fun round(sessionId: UUID, winningTeamId: UUID, roundNumber: Int, points: Int): NfcSessionRound =
        NfcSessionRound().apply {
            this.sessionId = sessionId
            this.winningTeamId = winningTeamId
            this.roundNumber = roundNumber
            awardedPointsPerMember = points
        }

    private fun stubNoStoredTeamPoints(sessionId: UUID, teamId: UUID) {
        `when`(roundRepository.findAllBySessionIdOrderByRoundNumberAsc(sessionId)).thenReturn(emptyList())
        `when`(valueRepository.findBySessionIdAndOwnerTypeAndOwnerIdAndValueKey(sessionId, OwnerType.TEAM, teamId, "points")).thenReturn(null)
    }

}
