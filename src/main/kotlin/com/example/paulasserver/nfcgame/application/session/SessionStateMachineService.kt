package com.example.paulasserver.nfcgame.application.session

import com.example.paulasserver.nfcgame.api.dto.MenuItem
import com.example.paulasserver.nfcgame.api.dto.ScreenModel
import com.example.paulasserver.nfcgame.application.statistics.NfcStatisticsService
import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.EventType
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.domain.RoundLimitType
import com.example.paulasserver.nfcgame.domain.ScreenType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcCard
import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowNode
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameResult
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameSession
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate
import com.example.paulasserver.nfcgame.persistence.entity.NfcMoneyTransaction
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionAccount
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionRound
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeam
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeamMember
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionValue
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
import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.math.MathContext
import java.time.Instant
import java.util.UUID

data class StateMachineResult(
    val session: NfcGameSession?,
    val screen: ScreenModel,
    val effects: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val timelineMessage: String? = null,
)

private data class BankTarget(
    val accountId: UUID,
    val label: String,
    val balance: BigDecimal,
)

private data class BankUiState(
    val mode: String,
    val targetIndex: Int,
    val targetAccountId: UUID?,
    val amount: Int,
    val message: String?,
)

private data class BankStepConfig(
    val smallStep: Int,
    val largeStep: Int,
    val minAmount: Int,
    val maxAmount: Int,
    val currency: String,
)

@Service
class SessionStateMachineService(
    private val cardRepository: NfcCardRepository,
    private val gameTemplateRepository: NfcGameTemplateRepository,
    private val playerRepository: NfcPlayerRepository,
    private val sessionRepository: NfcGameSessionRepository,
    private val flowNodeRepository: NfcFlowNodeRepository,
    private val flowEdgeRepository: NfcFlowEdgeRepository,
    private val teamRepository: NfcSessionTeamRepository,
    private val memberRepository: NfcSessionTeamMemberRepository,
    private val roundRepository: NfcSessionRoundRepository,
    private val accountRepository: NfcSessionAccountRepository,
    private val valueRepository: NfcSessionValueRepository,
    private val moneyTransactionRepository: NfcMoneyTransactionRepository,
    private val resultRepository: NfcGameResultRepository,
    private val statisticsService: NfcStatisticsService,
    private val objectMapper: ObjectMapper,
) {
    private val activeStatuses = listOf(
        SessionStatus.LOBBY,
        SessionStatus.CONFIGURING,
        SessionStatus.BUILDING_TEAMS,
        SessionStatus.READY,
        SessionStatus.RUNNING,
    )

    @Transactional
    fun handleCardScan(device: NfcDevice, cardUid: String): StateMachineResult {
        val normalizedUid = cardUid.trim().uppercase()
        val card = cardRepository.findByCardUid(normalizedUid)
            ?: return storeUnknownCard(normalizedUid, device.accountId)

        if (card.status == CardStatus.DISABLED) {
            return error("Karte deaktiviert", "Diese NFC-Karte ist deaktiviert.")
        }
        if (card.status != CardStatus.ASSIGNED) {
            return error("Karte nicht zugewiesen", "Bitte im Adminbereich zuweisen.")
        }

        return when (card.cardType) {
            CardType.GAME -> handleGameCard(device, card)
            CardType.PLAYER -> handlePlayerCard(card, device.accountId)
            CardType.UNKNOWN -> error("Karte nicht zugewiesen", "Bitte im Adminbereich als Spieler- oder Spielkarte zuweisen.")
        }
    }

    @Transactional
    fun handleReset(device: NfcDevice): StateMachineResult {
        val session = findActiveSession(device.accountId)
            ?: return message("Keine aktive Session", "Es gibt gerade nichts zurueckzusetzen.")

        session.status = SessionStatus.RESET
        session.currentStateKey = "reset"
        session.endedAt = Instant.now()
        sessionRepository.save(session)
        return StateMachineResult(
            session = session,
            screen = ScreenModel(
                screenType = ScreenType.MESSAGE,
                title = "Session zurueckgesetzt",
                subtitle = "Masterdaten bleiben unveraendert.",
                context = mapOf("deviceId" to device.id),
            ),
            effects = listOf("BEEP_RESET"),
        )
    }

    fun currentScreen(sessionId: UUID): StateMachineResult {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        return StateMachineResult(session = session, screen = buildScreen(session))
    }

    fun handleInput(sessionId: UUID, eventType: EventType, payload: Map<String, Any?> = emptyMap()): StateMachineResult {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        if (session.status == SessionStatus.BUILDING_TEAMS && session.currentStateKey == TEAM_SIZE_STATE) {
            return handleTeamSizeInput(session, eventType, payload)
        }
        if (session.status == SessionStatus.RUNNING && isEconomySession(session) && currentNodeKey(session).startsWith(BANK_STATE_PREFIX)) {
            return handleBankInput(session, eventType, payload)
        }
        if (session.status !in setOf(SessionStatus.CONFIGURING, SessionStatus.RUNNING)) {
            return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_INFO"))
        }
        val node = currentFlowNode(session) ?: return StateMachineResult(session = session, screen = buildScreen(session))
        if (handleFlowSelectionInput(session, node, eventType, payload)) {
            return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_INFO"))
        }
        val context = if (session.status == SessionStatus.RUNNING) {
            flowContext(session) + flowInputContext(session, node, eventType, payload)
        } else {
            emptyMap()
        }
        if (node.type == "NUMBER_PICKER" && (isConfirmEvent(eventType) || eventType == EventType.TOUCH_NUMBER_SET)) {
            applyNumberPickerValue(session, node)
        }
        if (node.type == "MENU" && (isConfirmEvent(eventType) || eventType == EventType.TOUCH_MENU_SELECT)) {
            applyMenuSelection(session, node, payload)
        }
        val next = nextNodeForInput(session, node, eventType, payload) ?: return StateMachineResult(session = session, screen = buildScreen(session))
        return if (session.status == SessionStatus.RUNNING) {
            enterRunningFlowNode(session, next, context, listOf("BEEP_INFO"))
        } else {
            moveToRuntimeNodeOrTeamSetup(session, next)
            StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_INFO"))
        }
    }

    @Transactional
    fun finishSessionById(sessionId: UUID, endReason: String = "MANUAL_DASHBOARD_END"): StateMachineResult {
        val session = sessionRepository.findById(sessionId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Session not found")
        }
        if (session.status == SessionStatus.FINISHED) {
            return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_INFO"))
        }
        if (session.status !in activeStatuses) {
            return StateMachineResult(
                session = session,
                screen = buildScreen(session),
                effects = listOf("BEEP_ERROR"),
                errors = listOf("Session kann in Status ${session.status} nicht beendet werden."),
            )
        }
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
        val winner = calculateConfiguredWinner(session, teams)
        finishSession(session, winner, endReason, teams.mapNotNull { it.id })
        return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_WIN"))
    }

    private fun handleGameCard(device: NfcDevice, card: NfcCard): StateMachineResult {
        val templateId = card.gameTemplateId ?: return error("Spielkarte ohne Spiel", "Bitte Game Template zuweisen.")
        val template = gameTemplateRepository.findById(templateId).orElse(null)
            ?: return error("Spiel nicht gefunden", "Das verknuepfte Game Template fehlt.")
        if (!template.active) {
            return error("Spiel deaktiviert", "Dieses Game Template ist nicht aktiv.")
        }

        val activeSession = findActiveSessionForGame(templateId, device.accountId)
        if (activeSession == null) {
            val session = createSession(device, template)
            return StateMachineResult(
                session = session,
                screen = buildScreen(session),
                effects = listOf("BEEP_SUCCESS"),
            )
        }

        if (activeSession.status == SessionStatus.CONFIGURING) {
            activeSession.status = SessionStatus.BUILDING_TEAMS
            activeSession.currentStateKey = TEAM_SIZE_STATE
            ensureSetupTeam(activeSession)
            sessionRepository.save(activeSession)
            return StateMachineResult(
                session = activeSession,
                screen = buildScreen(activeSession),
                effects = listOf("BEEP_INFO"),
                errors = listOf("Teamsetup startet zuerst. Bitte Teamgröße wählen."),
            )
        }

        if (activeSession.status in setOf(SessionStatus.BUILDING_TEAMS, SessionStatus.LOBBY, SessionStatus.READY)) {
            if (activeSession.currentStateKey == TEAM_SIZE_STATE) {
                if (!hasCompletedTeam(activeSession)) {
                    return StateMachineResult(
                        session = activeSession,
                        screen = buildScreen(activeSession),
                        effects = listOf("BEEP_ERROR"),
                        errors = listOf("Zuerst Teamgröße auswählen und Spieler scannen."),
                    )
                }
                removeEmptySetupTeams(activeSession, includeSizedSetupTeams = true)
                startSession(activeSession)
                return StateMachineResult(
                    session = activeSession,
                    screen = buildScreen(activeSession),
                    effects = listOf("BEEP_START"),
                )
            }
            val missingPlayers = missingPlayers(activeSession)
            if (missingPlayers > 0) {
                return StateMachineResult(
                    session = activeSession,
                    screen = buildScreen(activeSession),
                    effects = listOf("BEEP_ERROR"),
                    errors = listOf("Noch $missingPlayers Spieler scannen, danach die Spielkarte erneut scannen."),
                )
            }
            removeEmptySetupTeams(activeSession)
            startSession(activeSession)
            return StateMachineResult(
                session = activeSession,
                screen = buildScreen(activeSession),
                effects = listOf("BEEP_START"),
            )
        }

        if (activeSession.status == SessionStatus.RUNNING) {
            val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(activeSession.id))
            val winner = calculateConfiguredWinner(activeSession, teams)
            finishSession(activeSession, winner, "GAME_CARD_ENDED", teams.mapNotNull { it.id })
            return StateMachineResult(
                session = activeSession,
                screen = buildScreen(activeSession),
                effects = listOf("BEEP_WIN"),
            )
        }

        return StateMachineResult(
            session = activeSession,
            screen = buildScreen(activeSession),
            effects = listOf("BEEP_INFO"),
        )
    }

    private fun handlePlayerCard(card: NfcCard, accountId: Long?): StateMachineResult {
        val session = findActiveSession(accountId)
            ?: return error("Keine aktive Session", "Zuerst eine Spielkarte scannen.")
        val playerId = card.playerId ?: return error("Spieler fehlt", "Diese Karte ist keinem Spieler zugeordnet.")
        val player = playerRepository.findById(playerId).orElse(null)
            ?: return error("Spieler nicht gefunden", "Die Karten-Zuordnung ist ungueltig.")
        if (!player.active) {
            return error("Spieler inaktiv", "${player.name} ist deaktiviert.")
        }

        return when (session.status) {
            SessionStatus.CONFIGURING -> StateMachineResult(
                session = session,
                screen = buildScreen(session),
                effects = listOf("BEEP_ERROR"),
                errors = listOf("Erst Spieloptionen auswaehlen."),
            )
            SessionStatus.BUILDING_TEAMS, SessionStatus.LOBBY -> {
                if (session.currentStateKey == TEAM_SIZE_STATE) {
                    confirmTeamSizeForFirstPlayerScan(session)
                }
                addPlayerToTeam(session, playerId)
            }
            SessionStatus.RUNNING -> {
                val node = currentFlowNode(session)
                if (node != null && node.type in setOf("WAIT_PLAYER_CARD", "WAIT_ANY_CARD")) {
                    handleRunningPlayerScan(session, node, playerId)
                } else if (isEconomySession(session) && currentNodeKey(session).startsWith(BANK_STATE_PREFIX)) {
                    handleBankPaymentScan(session, playerId)
                } else {
                    recordWinFromPlayerCard(session, playerId)
                }
            }
            SessionStatus.READY -> StateMachineResult(session, buildScreen(session), effects = listOf("BEEP_INFO"))
            else -> error("Aktion nicht erlaubt", "In Status ${session.status} kann kein Spieler gescannt werden.")
        }
    }

    private fun createSession(device: NfcDevice, template: NfcGameTemplate): NfcGameSession {
        val session = sessionRepository.save(
            NfcGameSession().apply {
                gameTemplateId = requireNotNull(template.id)
                deviceId = requireNotNull(device.id)
                accountId = device.accountId
                status = SessionStatus.BUILDING_TEAMS
                currentStateKey = TEAM_SIZE_STATE
                roundLimitType = RoundLimitType.NONE
                roundLimit = null
            },
        )
        val sessionId = requireNotNull(session.id)
        val teams = (1..1).map { order ->
            NfcSessionTeam().apply {
                this.sessionId = sessionId
                name = "Team $order"
                teamOrder = order
                targetSize = 0
                status = "CONFIGURING"
            }
        }
        val savedTeams = teamRepository.saveAll(teams)

        if (isEconomyTemplate(template)) {
            val startCapital = economyStartCapital(template)
            val accounts = savedTeams.map { team ->
                NfcSessionAccount().apply {
                    this.sessionId = sessionId
                    ownerType = OwnerType.TEAM
                    teamId = requireNotNull(team.id)
                    balance = startCapital
                }
            } + NfcSessionAccount().apply {
                this.sessionId = sessionId
                ownerType = OwnerType.BANK
                balance = startCapital
            }
            val savedAccounts = accountRepository.saveAll(accounts)
            valueRepository.saveAll(
                savedAccounts.mapNotNull { account ->
                    val ownerId = when (account.ownerType) {
                        OwnerType.TEAM -> account.teamId
                        OwnerType.BANK -> account.id
                    } ?: return@mapNotNull null
                    NfcSessionValue().apply {
                        this.sessionId = sessionId
                        ownerType = account.ownerType
                        this.ownerId = ownerId
                        valueKey = "money"
                        value = startCapital
                    }
                },
            )
        }

        return session
    }

    private fun addPlayerToTeam(session: NfcGameSession, playerId: UUID): StateMachineResult {
        val sessionId = requireNotNull(session.id)
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)
        val teamIds = teams.mapNotNull { it.id }
        if (memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, teamIds) != null) {
            return StateMachineResult(
                session = session,
                screen = buildScreen(session),
                effects = listOf("BEEP_ERROR"),
                errors = listOf("Player already belongs to a team in this session"),
            )
        }

        val targetTeam = activeScanTeam(teams)
        if (targetTeam == null) {
            ensureSetupTeam(session)
            session.status = SessionStatus.BUILDING_TEAMS
            session.currentStateKey = TEAM_SIZE_STATE
            sessionRepository.save(session)
            return StateMachineResult(
                session = session,
                screen = buildScreen(session),
                effects = listOf("BEEP_ERROR"),
                errors = listOf("Zuerst Teamgröße auswählen."),
            )
        }
        if (targetTeam.targetSize <= 0) {
            session.currentStateKey = TEAM_SIZE_STATE
            sessionRepository.save(session)
            return StateMachineResult(
                session = session,
                screen = buildScreen(session),
                effects = listOf("BEEP_ERROR"),
                errors = listOf("Zuerst Teamgröße auswählen."),
            )
        }
        val currentCount = memberRepository.findAllBySessionTeamId(requireNotNull(targetTeam.id)).size

        val targetTeamId = requireNotNull(targetTeam.id)
        memberRepository.save(
            NfcSessionTeamMember().apply {
                sessionTeamId = targetTeamId
                this.playerId = playerId
            },
        )
        val newCount = currentCount + 1
        targetTeam.status = if (newCount >= targetTeam.targetSize) "COMPLETE" else "OPEN"
        teamRepository.save(targetTeam)
        session.status = SessionStatus.BUILDING_TEAMS
        session.currentStateKey = if (newCount >= targetTeam.targetSize) {
            ensureSetupTeam(session)
            TEAM_SIZE_STATE
        } else {
            PLAYER_SCAN_STATE
        }
        sessionRepository.save(session)

        val freshSession = reloadSession(session)
        return StateMachineResult(session = freshSession, screen = buildScreen(freshSession), effects = listOf("BEEP_SUCCESS"))
    }

    private fun recordWinFromPlayerCard(session: NfcGameSession, playerId: UUID): StateMachineResult {
        val sessionId = requireNotNull(session.id)
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)
        val membership = memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, teams.mapNotNull { it.id })
            ?: return error("Spieler nicht im Spiel", "Dieser Spieler gehoert zu keinem Team in der Session.")
        val winningTeamId = requireNotNull(membership.sessionTeamId)

        val currentNode = currentFlowNode(session) ?: implicitRuntimePlayerWaitNode(session)
        val nextNodeId = currentNode
            ?.takeIf { it.type in setOf("WAIT_PLAYER_CARD", "WAIT_ANY_CARD") }
            ?.let { nextNodeByEvent(it, "CARD_SCANNED") ?: nextNodeByEvent(it, "NEXT") }
        if (nextNodeId != null) {
            return continueRunningFlowAfterPlayerScan(session, teams, playerId, winningTeamId, nextNodeId)
        }

        recordRoundWin(session, winningTeamId, 1)
        return if (roundLimitReached(session)) {
            finishSession(session, calculateConfiguredWinner(session, teams), "ROUND_LIMIT_REACHED", teams.mapNotNull { it.id })
            StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_WIN"))
        } else {
            sessionRepository.save(session)
            StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_SUCCESS"))
        }
    }

    private fun implicitRuntimePlayerWaitNode(session: NfcGameSession): NfcFlowNode? {
        if (currentNodeKey(session) != "running") return null
        val gameId = session.gameTemplateId ?: return null
        return flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(gameId)
            .firstOrNull { it.type in setOf("WAIT_PLAYER_CARD", "WAIT_ANY_CARD") }
    }

    private fun handleRunningPlayerScan(session: NfcGameSession, node: NfcFlowNode, playerId: UUID): StateMachineResult {
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
        val membership = memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, teams.mapNotNull { it.id })
            ?: return error("Spieler nicht im Spiel", "Dieser Spieler gehoert zu keinem Team in der Session.")
        val nextNodeId = nextNodeByEvent(node, "CARD_SCANNED") ?: nextNodeByEvent(node, "NEXT")
        if (nextNodeId == null) {
            return recordWinFromPlayerCard(session, playerId)
        }
        val context = flowContext(session) + mapOf(
            "player" to playerId.toString(),
            "lastScannedPlayer" to playerId.toString(),
            "team" to requireNotNull(membership.sessionTeamId).toString(),
            "lastScannedTeam" to requireNotNull(membership.sessionTeamId).toString(),
        ) + scanStoreContext(node, playerId, requireNotNull(membership.sessionTeamId))
        return enterRunningFlowNode(session, nextNodeId, context, listOf("BEEP_SUCCESS"))
    }

    private fun scanStoreContext(node: NfcFlowNode, playerId: UUID, teamId: UUID): Map<String, String> {
        val storeAs = readMap(node.configJson)["storeAs"]?.toString()?.takeIf { it.isNotBlank() } ?: return emptyMap()
        return mapOf(storeAs to playerId.toString(), "${storeAs}Team" to teamId.toString())
    }

    private fun continueRunningFlowAfterPlayerScan(
        session: NfcGameSession,
        teams: List<NfcSessionTeam>,
        scannedPlayerId: UUID,
        scannedWinningTeamId: UUID,
        firstNodeId: UUID,
    ): StateMachineResult {
        var nodeId: UUID? = firstNodeId
        var winningTeamId: UUID? = scannedWinningTeamId
        var roundRecorded = false
        var timelineMessage: String? = null
        var runtimeContext = mapOf(
            "player" to scannedPlayerId.toString(),
            "scannedPlayer" to scannedPlayerId.toString(),
            "lastScannedPlayer" to scannedPlayerId.toString(),
            "team" to scannedWinningTeamId.toString(),
            "scannedPlayerTeam" to scannedWinningTeamId.toString(),
            "lastScannedTeam" to scannedWinningTeamId.toString(),
        )
        repeat(MAX_RUNTIME_STEPS) {
            val node = nodeId?.let { flowNodeRepository.findById(it).orElse(null) }
                ?: return finishFlowFallback(session, teams, winningTeamId, timelineMessage)
            when (node.type) {
                "AWARD_POINTS", "AWARD_ROUND_WIN" -> {
                    if (!roundRecorded) {
                        val resultContext = applyPointsNode(session, node, readMap(node.configJson), runtimeContext)
                        roundRecorded = true
                        runtimeContext = runtimeContext + resultContext
                    }
                    nodeId = nextNodeByEventPreferringType(node, "NEXT", "LOG_EVENT")
                }

                "IF_ELSE", "CONDITION", "BRANCH" -> {
                    nodeId = nextNodeByEvent(node, if (evaluateRuntimeCondition(session, node, runtimeContext)) "TRUE" else "FALSE")
                }

                "LOG_EVENT" -> {
                    timelineMessage = timelineMessageForNode(session, node, runtimeContext)
                    nodeId = nextNodeByEvent(node, "NEXT")
                }

                "CALCULATE" -> {
                    runtimeContext = runtimeContext + calculateFlowValue(session, node, readMap(node.configJson), runtimeContext)
                    nodeId = nextNodeByEvent(node, "NEXT")
                }

                "END_GAME" -> {
                    finishSession(session, calculateConfiguredWinner(session, teams), "FLOW_ENDED", teams.mapNotNull { it.id })
                    return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_WIN"), timelineMessage = timelineMessage)
                }

                "WAIT_PLAYER_CARD", "WAIT_ANY_CARD", "WAIT_GAME_CARD" -> {
                    session.status = SessionStatus.RUNNING
                    session.currentStateKey = flowStateKey(node, context = runtimeContext)
                    sessionRepository.save(session)
                    return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_SUCCESS"), timelineMessage = timelineMessage)
                }

                else -> nodeId = nextNodeByEvent(node, "NEXT")
            }
        }
        return finishFlowFallback(session, teams, winningTeamId, timelineMessage)
    }

    private fun finishFlowFallback(
        session: NfcGameSession,
        teams: List<NfcSessionTeam>,
        winningTeamId: UUID?,
        timelineMessage: String? = null,
    ): StateMachineResult {
        val calculatedWinner = calculateConfiguredWinner(session, teams)
        val winnerForResult = if (calculatedWinner != null || hasRecordedRounds(session)) calculatedWinner else winningTeamId
        finishSession(session, winnerForResult, "FLOW_ENDED", teams.mapNotNull { it.id })
        return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_WIN"), timelineMessage = timelineMessage)
    }

    private fun recordRoundWin(session: NfcGameSession, winningTeamId: UUID, points: Int) {
        val sessionId = requireNotNull(session.id)
        val nextRound = session.currentRoundNumber + 1
        roundRepository.save(
            NfcSessionRound().apply {
                this.sessionId = sessionId
                roundNumber = nextRound
                this.winningTeamId = winningTeamId
                awardedPointsPerMember = points
            },
        )
        statisticsService.recordRoundWin(winningTeamId, points.toLong())
        session.currentRoundNumber = nextRound
    }

    private fun recordSessionPoints(session: NfcGameSession, teamId: UUID, points: Int) {
        recordSessionPoints(session, listOf(teamId), points)
    }

    private fun recordSessionPoints(session: NfcGameSession, teamIds: Collection<UUID>, points: Int, advanceRound: Boolean = true) {
        recordSessionPointDeltas(
            session,
            teamIds.associateWith { BigDecimal.valueOf(points.toLong()) },
            advanceRound,
        )
    }

    private fun recordSessionPointDeltas(session: NfcGameSession, deltasByTeam: Map<UUID, BigDecimal>, advanceRound: Boolean = true) {
        val currentRound = effectiveCurrentRoundNumber(session)
        val nonZeroDeltas = deltasByTeam
            .mapValues { it.value.toInt() }
            .filterValues { it != 0 }
        if (nonZeroDeltas.isEmpty()) return
        val sessionId = requireNotNull(session.id)
        val roundNumber = if (advanceRound) currentRound + 1 else currentRound
        roundRepository.saveAll(
            nonZeroDeltas.map { (teamId, points) ->
                NfcSessionRound().apply {
                    this.sessionId = sessionId
                    this.roundNumber = roundNumber
                    winningTeamId = teamId
                    awardedPointsPerMember = points
                }
            },
        )
        if (advanceRound) {
            session.currentRoundNumber = roundNumber
        }
    }

    private fun setSessionPoints(session: NfcGameSession, teamIds: Collection<UUID>, points: Int) {
        val deltasByTeam = teamIds.associateWith { teamId -> points - sessionPointsForTeam(session, teamId) }
            .filterValues { it != 0 }
        if (deltasByTeam.isEmpty()) return
        val sessionId = requireNotNull(session.id)
        roundRepository.saveAll(
            deltasByTeam.map { (teamId, delta) ->
                NfcSessionRound().apply {
                    this.sessionId = sessionId
                    roundNumber = session.currentRoundNumber
                    winningTeamId = teamId
                    awardedPointsPerMember = delta
                }
            },
        )
    }

    private fun pointsForNode(session: NfcGameSession, config: Map<String, Any?>, context: Map<String, String>): Int {
        val pointsFrom = config["pointsFrom"]?.toString()?.takeIf { it.isNotBlank() }
            ?: config["valueFrom"]?.toString()?.takeIf { it.isNotBlank() }
        return pointsFrom?.let { numericValueForExpression(session, it, context)?.toInt() }
            ?: intConfig(config, "points")
            ?: 1
    }

    private fun numericValueForExpression(session: NfcGameSession, expression: String, context: Map<String, String>): BigDecimal? =
        ArithmeticExpressionParser(expression) { token -> numericValueForToken(session, token, context) }.parse()

    private fun numericValueForToken(session: NfcGameSession, token: String, context: Map<String, String>): BigDecimal? {
        val key = token.trim().removePrefix("{").removeSuffix("}")
        context[key]?.toBigDecimalOrNull()?.let { return it }
        when (key) {
            "currentRound", "round", "currentRoundNumber" -> return effectiveCurrentRoundNumber(session).toBigDecimal()
            "roundLimit" -> return session.roundLimit?.toBigDecimal()
        }
        val parts = key.split('.', limit = 2)
        if (parts.size != 2) return key.toBigDecimalOrNull()
        val owner = parts[0]
        val keyName = normalizeValueKey(parts[1])
        return when (keyName) {
            "rounds", "wins" -> teamIdForBuilderReference(session, owner, context)
                ?.let { roundWinsForTeam(session, it).toBigDecimal() }
            else -> valueForBuilderReference(session, owner, keyName, context)
        }
    }

    private fun calculateFlowValue(session: NfcGameSession, node: NfcFlowNode, config: Map<String, Any?>, context: Map<String, String>): Map<String, String> {
        val target = config["targetVariable"]?.toString()?.takeIf { it.isNotBlank() && it != "custom" }
            ?: config["variableName"]?.toString()?.takeIf { it.isNotBlank() }
            ?: config["storeAs"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        val expression = config["expression"]?.toString()?.takeIf { it.isNotBlank() }
            ?: config["formula"]?.toString()?.takeIf { it.isNotBlank() }
            ?: return emptyMap()
        val value = numericValueForExpression(session, expression, context) ?: return emptyMap()
        return mapOf(target.trim().removePrefix("{").removeSuffix("}") to value.stripTrailingZeros().toPlainString())
    }

    private fun displayValueForExpression(session: NfcGameSession, expression: String, context: Map<String, String>): String? {
        val key = expression.trim().removePrefix("{").removeSuffix("}")
        val parts = key.split('.', limit = 2)
        if (parts.size != 2) {
            return context[key]?.let { displayValueForVariable(session, key, it) }
                ?: numericValueForExpression(session, key, context)?.stripTrailingZeros()?.toPlainString()
        }
        val owner = parts[0]
        return when (parts[1].lowercase()) {
            "name" -> playerIdForBuilderReference(owner, context)?.let { playerName(it.toString()) }
                ?: accountForBuilderReference(session, owner, context)
                ?.let { accountLabel(session, requireNotNull(it.id).toString()) }
                ?: teamIdForBuilderReference(session, owner, context)?.let { teamLabel(it) }
            "team", "teamname" -> teamIdForBuilderReference(session, owner, context)?.let { teamLabel(it) }
            "points", "score", "rounds", "wins", "money", "balance" -> numericValueForExpression(session, key, context)
                ?.stripTrailingZeros()
                ?.toPlainString()
            else -> null
        }
    }

    private fun playerIdForBuilderReference(reference: String, context: Map<String, String>): UUID? {
        val value = context[reference]
            ?: when (reference) {
                "player", "scannedPlayer", "lastScannedPlayer" -> context["player"] ?: context["scannedPlayer"] ?: context["lastScannedPlayer"]
                else -> null
            }
        return value?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }

    private fun teamIdForBuilderReference(session: NfcGameSession, reference: String, context: Map<String, String>): UUID? {
        context["${reference}Team"]?.let { value -> runCatching { UUID.fromString(value) }.getOrNull()?.let { return it } }
        val directTeamValue = context[reference]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (reference in setOf("team", "scannedTeam", "lastScannedTeam", "lastAwardedTeam", "winner")) {
            directTeamValue?.let { return it }
        }
        if (reference == "selectedTarget" || reference == "selectedAccount" || reference == "target") {
            accountForBuilderReference(session, reference, context)?.teamId?.let { return it }
        }
        val playerId = playerIdForBuilderReference(reference, context) ?: return null
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
        return memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, teams.mapNotNull { it.id })?.sessionTeamId
    }

    private fun accountForBuilderReference(session: NfcGameSession, reference: String, context: Map<String, String>): NfcSessionAccount? {
        val sessionId = requireNotNull(session.id)
        val accountIdValue = when (reference) {
            "selectedTarget", "selectedAccount", "target" -> context["targetAccountId"] ?: context["target"]
            "payer", "payerAccount" -> context["payerAccountId"]
            else -> context[reference]
        }
        accountIdValue
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
            ?.let { accountId ->
                accountRepository.findById(accountId).orElse(null)
                    ?.takeIf { it.sessionId == session.id }
                    ?.let { return it }
            }
        val teamId = teamIdForBuilderReferenceWithoutAccountLookup(session, reference, context) ?: return null
        return accountRepository.findAllBySessionId(sessionId).firstOrNull { it.ownerType == OwnerType.TEAM && it.teamId == teamId }
    }

    private data class ValueOwner(val ownerType: OwnerType, val ownerId: UUID)

    private fun valueOwnerForBuilderReference(session: NfcGameSession, reference: String, context: Map<String, String>): ValueOwner? {
        accountForBuilderReference(session, reference, context)?.let { account ->
            return when (account.ownerType) {
                OwnerType.BANK -> ValueOwner(OwnerType.BANK, requireNotNull(account.id))
                OwnerType.TEAM -> account.teamId?.let { ValueOwner(OwnerType.TEAM, it) }
            }
        }
        return teamIdForBuilderReferenceWithoutAccountLookup(session, reference, context)?.let { ValueOwner(OwnerType.TEAM, it) }
    }

    private fun valueForBuilderReference(session: NfcGameSession, reference: String, valueKey: String, context: Map<String, String>): BigDecimal? {
        val owner = valueOwnerForBuilderReference(session, reference, context) ?: return null
        return sessionValue(session, owner.ownerType, owner.ownerId, valueKey)
            ?: legacyValueForBuilderReference(session, reference, valueKey, context)
            ?: BigDecimal.ZERO
    }

    private fun sessionValue(session: NfcGameSession, ownerType: OwnerType, ownerId: UUID, valueKey: String): BigDecimal? =
        valueRepository.findBySessionIdAndOwnerTypeAndOwnerIdAndValueKey(requireNotNull(session.id), ownerType, ownerId, normalizeValueKey(valueKey))?.value

    private fun changeSessionValue(
        session: NfcGameSession,
        ownerType: OwnerType,
        ownerId: UUID,
        valueKey: String,
        amount: BigDecimal,
        operation: String,
    ): BigDecimal {
        val sessionId = requireNotNull(session.id)
        val normalizedKey = normalizeValueKey(valueKey)
        val entity = valueRepository.findBySessionIdAndOwnerTypeAndOwnerIdAndValueKey(sessionId, ownerType, ownerId, normalizedKey)
            ?: NfcSessionValue().apply {
                this.sessionId = sessionId
                this.ownerType = ownerType
                this.ownerId = ownerId
                this.valueKey = normalizedKey
                value = legacyValueForOwner(session, ownerType, ownerId, normalizedKey) ?: BigDecimal.ZERO
            }
        entity.value = when (operation.uppercase()) {
            "SET" -> amount
            "SUBTRACT", "SUB", "DEDUCT" -> entity.value.subtract(amount)
            else -> entity.value.add(amount)
        }
        valueRepository.save(entity)
        syncLegacyMoneyValue(session, ownerType, ownerId, normalizedKey, entity.value)
        return entity.value
    }

    private fun legacyValueForBuilderReference(session: NfcGameSession, reference: String, valueKey: String, context: Map<String, String>): BigDecimal? =
        when (normalizeValueKey(valueKey)) {
            "points" -> teamIdForBuilderReference(session, reference, context)?.let { sessionPointsForTeam(session, it).toBigDecimal() }
            "rounds", "wins", "roundwins" -> teamIdForBuilderReference(session, reference, context)?.let { roundWinsForTeam(session, it).toBigDecimal() }
            "money" -> accountForBuilderReference(session, reference, context)?.balance
            else -> null
        }

    private fun legacyValueForOwner(session: NfcGameSession, ownerType: OwnerType, ownerId: UUID, valueKey: String): BigDecimal? =
        when (normalizeValueKey(valueKey)) {
            "points" -> if (ownerType == OwnerType.TEAM) sessionPointsForTeam(session, ownerId).toBigDecimal() else null
            "rounds", "wins", "roundwins" -> if (ownerType == OwnerType.TEAM) roundWinsForTeam(session, ownerId).toBigDecimal() else null
            "money" -> accountRepository.findAllBySessionId(requireNotNull(session.id))
                .firstOrNull { account ->
                    when (ownerType) {
                        OwnerType.BANK -> account.ownerType == OwnerType.BANK && account.id == ownerId
                        OwnerType.TEAM -> account.ownerType == OwnerType.TEAM && account.teamId == ownerId
                    }
                }
                ?.balance
            else -> null
        }

    private fun syncLegacyMoneyValue(session: NfcGameSession, ownerType: OwnerType, ownerId: UUID, valueKey: String, value: BigDecimal) {
        if (normalizeValueKey(valueKey) != "money") return
        val account = accountRepository.findAllBySessionId(requireNotNull(session.id))
            .firstOrNull { account ->
                when (ownerType) {
                    OwnerType.BANK -> account.ownerType == OwnerType.BANK && account.id == ownerId
                    OwnerType.TEAM -> account.ownerType == OwnerType.TEAM && account.teamId == ownerId
                }
            } ?: return
        account.balance = value
        accountRepository.save(account)
    }

    private fun normalizeValueKey(valueKey: String): String =
        when (valueKey.trim().removePrefix("{").removeSuffix("}").lowercase()) {
            "score", "punkt", "punkte" -> "points"
            "balance", "kontostand", "geld" -> "money"
            else -> valueKey.trim().removePrefix("{").removeSuffix("}").lowercase()
        }

    private fun teamIdForBuilderReferenceWithoutAccountLookup(session: NfcGameSession, reference: String, context: Map<String, String>): UUID? {
        context["${reference}Team"]?.let { value -> runCatching { UUID.fromString(value) }.getOrNull()?.let { return it } }
        val directTeamValue = context[reference]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        if (reference in setOf("team", "scannedTeam", "lastScannedTeam", "lastAwardedTeam", "winner")) {
            directTeamValue?.let { return it }
        }
        val playerId = playerIdForBuilderReference(reference, context) ?: return null
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
        return memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, teams.mapNotNull { it.id })?.sessionTeamId
    }

    private fun sessionPointsForTeam(session: NfcGameSession, teamId: UUID): Int =
        roundRepository.findAllBySessionIdOrderByRoundNumberAsc(requireNotNull(session.id))
            .filter { it.winningTeamId == teamId }
            .sumOf { it.awardedPointsPerMember }

    private fun roundWinsForTeam(session: NfcGameSession, teamId: UUID): Int =
        roundRepository.findAllBySessionIdOrderByRoundNumberAsc(requireNotNull(session.id))
            .count { it.winningTeamId == teamId }

    private fun targetTeamForPoints(
        session: NfcGameSession,
        config: Map<String, Any?>,
        context: Map<String, String>,
    ): UUID? {
        val target = (config["targetVariable"]?.toString()?.takeIf { it.isNotBlank() }
            ?: config["target"]?.toString()?.takeIf { it.isNotBlank() })
            ?.trim()
            ?.removePrefix("{")
            ?.removeSuffix("}")
            ?: "team"
        context["${target}Team"]?.let { return runCatching { UUID.fromString(it) }.getOrNull() }
        if (target.contains('.')) {
            return teamIdForBuilderReference(session, target.substringBefore('.'), context)
        }
        if (target in setOf("selectedTarget", "selectedAccount", "target")) {
            return accountForBuilderReference(session, target, context)?.teamId
        }
        if (target in setOf("lastScannedTeam", "scannedTeam", "team")) {
            return context["team"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        }
        val playerId = context[target]
            ?: if (target in setOf("lastScannedPlayer", "scannedPlayer", "player")) context["player"] else null
        val playerUuid = playerId?.let { runCatching { UUID.fromString(it) }.getOrNull() } ?: return null
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
        return memberRepository.findByPlayerIdAndSessionTeamIdIn(playerUuid, teams.mapNotNull { it.id })?.sessionTeamId
    }

    private fun targetTeamsForPoints(
        session: NfcGameSession,
        config: Map<String, Any?>,
        context: Map<String, String>,
    ): List<UUID> {
        val target = (config["targetVariable"]?.toString()?.takeIf { it.isNotBlank() }
            ?: config["target"]?.toString()?.takeIf { it.isNotBlank() })
            ?.trim()
            ?.removePrefix("{")
            ?.removeSuffix("}")
            ?: "team"
        if (target in setOf("allTeams", "allPlayers", "everyone")) {
            return teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id)).mapNotNull { it.id }
        }
        return targetTeamForPoints(session, config, context)?.let(::listOf).orEmpty()
    }

    private fun targetValueOwners(
        session: NfcGameSession,
        config: Map<String, Any?>,
        context: Map<String, String>,
    ): List<ValueOwner> {
        val target = (config["targetVariable"]?.toString()?.takeIf { it.isNotBlank() }
            ?: config["target"]?.toString()?.takeIf { it.isNotBlank() })
            ?.trim()
            ?.removePrefix("{")
            ?.removeSuffix("}")
            ?: "team"
        if (target in setOf("allTeams", "allPlayers", "everyone")) {
            return teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
                .mapNotNull { team -> team.id?.let { ValueOwner(OwnerType.TEAM, it) } }
        }
        return valueOwnerForBuilderReference(session, target.substringBefore('.'), context)?.let(::listOf).orEmpty()
    }

    private fun applyPointsNode(
        session: NfcGameSession,
        node: NfcFlowNode,
        config: Map<String, Any?>,
        context: Map<String, String>,
    ): Map<String, String> {
        val isGlobalValueNode = node.type == "AWARD_ROUND_WIN" || config["scope"]?.toString() == "GLOBAL_STATS"
        val points = if (isGlobalValueNode) {
            pointsForNode(session, config, context).coerceAtLeast(0)
        } else {
            pointsForNode(session, config, context)
        }
        val valueKey = if (isGlobalValueNode) {
            "points"
        } else {
            normalizeValueKey(config["valueKey"]?.toString()?.takeIf { it.isNotBlank() } ?: "points")
        }
        val targetOwners = targetValueOwners(session, config, context)
        if (targetOwners.isEmpty()) return emptyMap()
        val expression = if (!isGlobalValueNode) {
            config["expression"]?.toString()?.takeIf { it.isNotBlank() }
                ?: config["formula"]?.toString()?.takeIf { it.isNotBlank() }
        } else {
            null
        }
        val operation = if (isGlobalValueNode) {
            "ADD"
        } else {
            config["operation"]?.toString()?.uppercase().takeUnless { it.isNullOrBlank() } ?: "ADD"
        }
        val amount = BigDecimal.valueOf(points.toLong())
        val valueDeltas = mutableMapOf<ValueOwner, BigDecimal>()
        val values = if (isGlobalValueNode) {
            targetOwners.associateWith { amount }
        } else if (expression != null) {
            targetOwners.mapNotNull { owner ->
                val current = sessionValue(session, owner.ownerType, owner.ownerId, valueKey)
                    ?: legacyValueForOwner(session, owner.ownerType, owner.ownerId, valueKey)
                    ?: BigDecimal.ZERO
                val calculated = numericValueForExpression(
                    session,
                    expression,
                    context + mapOf("current" to current.stripTrailingZeros().toPlainString()),
                ) ?: return@mapNotNull null
                valueDeltas[owner] = calculated.subtract(current)
                owner to changeSessionValue(session, owner.ownerType, owner.ownerId, valueKey, calculated, "SET")
            }.toMap()
        } else {
            targetOwners.associateWith { owner ->
                val current = sessionValue(session, owner.ownerType, owner.ownerId, valueKey)
                    ?: legacyValueForOwner(session, owner.ownerType, owner.ownerId, valueKey)
                    ?: BigDecimal.ZERO
                val changed = changeSessionValue(session, owner.ownerType, owner.ownerId, valueKey, amount, operation)
                valueDeltas[owner] = changed.subtract(current)
                changed
            }
        }
        if (isGlobalValueNode) {
            targetOwners.filter { it.ownerType == OwnerType.TEAM }.forEach { recordRoundWin(session, it.ownerId, points) }
        } else if (valueKey == "points" && config["advanceRound"] != false) {
            recordSessionPointDeltas(
                session,
                valueDeltas
                    .filterKeys { it.ownerType == OwnerType.TEAM }
                    .mapKeys { it.key.ownerId },
            )
        }
        val targetLabel = if (targetOwners.size == 1) labelForValueOwner(session, targetOwners.first()) else "Alle Teams"
        val firstDelta = valueDeltas[targetOwners.first()] ?: amount
        val displayedAmount = if (isGlobalValueNode) amount else firstDelta.abs()
        return buildMap {
            put("amount", displayedAmount.stripTrailingZeros().toPlainString())
            put("lastAwardedPoints", displayedAmount.stripTrailingZeros().toPlainString())
            put("lastDelta", firstDelta.stripTrailingZeros().toPlainString())
            putAll(mapOf(
            "valueKey" to valueKey,
            "targetLabel" to targetLabel,
            "lastAwardedTeam" to targetOwners.first().ownerId.toString(),
            "lastValue" to (values[targetOwners.first()] ?: BigDecimal.ZERO).stripTrailingZeros().toPlainString(),
            ))
        }
    }

    private fun labelForValueOwner(session: NfcGameSession, owner: ValueOwner): String =
        when (owner.ownerType) {
            OwnerType.BANK -> "Bank"
            OwnerType.TEAM -> teamLabel(owner.ownerId)
        }

    private fun evaluateRuntimeCondition(
        session: NfcGameSession,
        node: NfcFlowNode,
        context: Map<String, String>,
    ): Boolean {
        val expression = readMap(node.configJson)["expression"]?.toString().orEmpty()
        if (expression.contains("roundLimit", ignoreCase = true)) {
            if (session.roundLimit == null && expression.contains("roundLimit == null", ignoreCase = true)) return true
            evaluateNumericCondition(session, expression.substringAfter("||").trim(), context)?.let { return it }
            return !roundLimitReached(session)
        }
        evaluateNumericCondition(session, expression, context)?.let { return it }
        if (expression.contains("balance", ignoreCase = true)) {
            val balance = conditionAccountBalance(session, expression, context) ?: return false
            val normalizedExpression = expression.replace("\\s+".toRegex(), "")
            val legacyMonopolyBankruptCheck =
                normalizedExpression.contains("selectedAccount.balance<0", ignoreCase = true) &&
                    !normalizedExpression.contains("<=", ignoreCase = true)
            return when {
                expression.contains("<=", ignoreCase = true) -> balance <= BigDecimal.ZERO
                expression.contains("<", ignoreCase = true) -> if (legacyMonopolyBankruptCheck) balance <= BigDecimal.ZERO else balance < BigDecimal.ZERO
                expression.contains(">=", ignoreCase = true) -> balance >= BigDecimal.ZERO
                expression.contains(">", ignoreCase = true) -> balance > BigDecimal.ZERO
                else -> false
            }
        }
        return false
    }

    private fun evaluateNumericCondition(session: NfcGameSession, expression: String, context: Map<String, String>): Boolean? {
        val match = Regex("""^\s*([A-Za-z][A-Za-z0-9]*(?:\.[A-Za-z][A-Za-z0-9]*)?)\s*(<=|>=|==|!=|<|>)\s*(-?\d+(?:\.\d+)?|[A-Za-z][A-Za-z0-9]*(?:\.[A-Za-z][A-Za-z0-9]*)?)\s*$""")
            .matchEntire(expression)
            ?: return null
        val left = numericValueForExpression(session, match.groupValues[1], context) ?: return false
        val rightToken = match.groupValues[3]
        val right = rightToken.toBigDecimalOrNull() ?: numericValueForExpression(session, rightToken, context) ?: return false
        return when (match.groupValues[2]) {
            "<=" -> left <= right
            ">=" -> left >= right
            "==" -> left.compareTo(right) == 0
            "!=" -> left.compareTo(right) != 0
            "<" -> left < right
            ">" -> left > right
            else -> false
        }
    }

    private fun conditionAccountBalance(
        session: NfcGameSession,
        expression: String,
        context: Map<String, String>,
    ): BigDecimal? {
        val accountId = when {
            expression.contains("payer", ignoreCase = true) -> context["payerAccountId"]
            expression.contains("target", ignoreCase = true) -> context["targetAccountId"] ?: context["target"]
            expression.contains("selectedAccount", ignoreCase = true) -> context["payerAccountId"] ?: context["targetAccountId"] ?: context["target"]
            else -> null
        }?.let { runCatching { UUID.fromString(it) }.getOrNull() }

        if (accountId != null) {
            return accountRepository.findById(accountId).orElse(null)
                ?.takeIf { it.sessionId == session.id }
                ?.balance
        }

        return accountRepository.findAllBySessionId(requireNotNull(session.id))
            .filter { it.ownerType == OwnerType.TEAM }
            .minOfOrNull { it.balance }
    }

    private fun roundLimitReached(session: NfcGameSession): Boolean =
        session.roundLimitType == RoundLimitType.ROUNDS &&
            session.roundLimit != null &&
            effectiveCurrentRoundNumber(session) >= requireNotNull(session.roundLimit)

    private fun effectiveCurrentRoundNumber(session: NfcGameSession): Int {
        val sessionId = requireNotNull(session.id)
        val maxRecordedRound = roundRepository.findAllBySessionIdOrderByRoundNumberAsc(sessionId)
            .maxOfOrNull { it.roundNumber }
            ?: 0
        return if (maxRecordedRound > 0) maxRecordedRound else session.currentRoundNumber
    }

    private fun calculateWinnerByRounds(session: NfcGameSession, teams: List<NfcSessionTeam>): UUID? {
        val teamIds = teams.mapNotNull { it.id }.toSet()
        val roundWins = roundRepository.findAllBySessionIdOrderByRoundNumberAsc(requireNotNull(session.id))
            .mapNotNull { it.winningTeamId }
            .filter { it in teamIds }
            .groupingBy { it }
            .eachCount()
        if (roundWins.isEmpty()) return null
        val bestScore = roundWins.maxOf { it.value }
        val winners = roundWins.filterValues { it == bestScore }.keys
        return if (winners.size == 1) winners.first() else null
    }

    private fun calculateConfiguredWinner(session: NfcGameSession, teams: List<NfcSessionTeam>): UUID? {
        val template = session.gameTemplateId
            ?.let { gameTemplateRepository.findById(it).orElse(null) }
        val valueKey = normalizeValueKey(template?.dashboardMetricSource?.takeIf { it.isNotBlank() } ?: "points")
        val lowest = template?.dashboardMetricSortDirection?.equals("ASC", ignoreCase = true) == true
        return calculateWinnerBySessionValue(session, teams, valueKey, lowest)
    }

    private fun calculateWinnerBySessionValue(session: NfcGameSession, teams: List<NfcSessionTeam>, valueKey: String, lowest: Boolean): UUID? {
        val teamIds = teams.mapNotNull { it.id }
        if (teamIds.isEmpty()) return null
        val valuesByTeam = teamIds.associateWith { teamId ->
            sessionValue(session, OwnerType.TEAM, teamId, valueKey)
                ?: legacyValueForOwner(session, OwnerType.TEAM, teamId, valueKey)
                ?: BigDecimal.ZERO
        }
        val bestValue = if (lowest) valuesByTeam.minOf { it.value } else valuesByTeam.maxOf { it.value }
        val winners = valuesByTeam.filterValues { it == bestValue }.keys
        return if (winners.size == 1) winners.first() else null
    }

    private fun hasRecordedRounds(session: NfcGameSession): Boolean =
        roundRepository.findAllBySessionIdOrderByRoundNumberAsc(requireNotNull(session.id)).isNotEmpty()

    private fun handleBankPaymentScan(session: NfcGameSession, payerPlayerId: UUID): StateMachineResult {
        val state = bankUiState(session)
        if (state.mode != BANK_PAY_SCAN_MODE) {
            return StateMachineResult(session, buildScreen(session), effects = listOf("BEEP_INFO"))
        }
        val payerAccount = accountForPlayer(session, payerPlayerId)
            ?: return StateMachineResult(
                session = session,
                screen = buildScreen(session),
                effects = listOf("BEEP_ERROR"),
                errors = listOf("Keine Kreditkarte im Spiel."),
            )
        val targetAccount = state.targetAccountId?.let { id ->
            accountRepository.findById(id).orElse(null)?.takeIf { it.sessionId == session.id }
        } ?: return StateMachineResult(
            session = session,
            screen = buildScreen(session),
            effects = listOf("BEEP_ERROR"),
            errors = listOf("Zuerst Empfaenger auswaehlen."),
        )
        if (payerAccount.id == targetAccount.id) {
            return StateMachineResult(session, buildScreen(session), effects = listOf("BEEP_ERROR"), errors = listOf("Zahler und Empfaenger sind gleich."))
        }
        val payerName = playerRepository.findById(payerPlayerId).orElse(null)?.name ?: "Ein Spieler"
        val targetLabel = bankTargets(session).firstOrNull { it.accountId == targetAccount.id }?.label ?: "Empfaenger"
        val currency = bankStepConfig(session).currency
        val amount = BigDecimal.valueOf(state.amount.toLong())
        val payerOwner = ValueOwner(OwnerType.TEAM, requireNotNull(payerAccount.teamId))
        val targetOwner = when (targetAccount.ownerType) {
            OwnerType.BANK -> ValueOwner(OwnerType.BANK, requireNotNull(targetAccount.id))
            OwnerType.TEAM -> ValueOwner(OwnerType.TEAM, requireNotNull(targetAccount.teamId))
        }
        val payerBalance = sessionValue(session, payerOwner.ownerType, payerOwner.ownerId, "money") ?: payerAccount.balance
        if (payerBalance < amount) {
            return StateMachineResult(session, buildScreen(session), effects = listOf("BEEP_ERROR"), errors = listOf("Nicht genug Guthaben."))
        }
        changeSessionValue(session, payerOwner.ownerType, payerOwner.ownerId, "money", amount, "SUBTRACT")
        changeSessionValue(session, targetOwner.ownerType, targetOwner.ownerId, "money", amount, "ADD")
        moneyTransactionRepository.save(
            NfcMoneyTransaction().apply {
                sessionId = session.id
                fromAccountId = payerAccount.id
                toAccountId = targetAccount.id
                this.amount = amount
                initiatedByPlayerId = payerPlayerId
            },
        )
        session.currentStateKey = bankStateKey(BANK_TARGET_MODE, selectedIndex = state.targetIndex, amount = state.amount, message = "transfer")
        sessionRepository.save(session)
        val timelineMessage = formatTimelineTemplate(session, payerName, targetLabel, state.amount, currency)
        return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_SUCCESS"), timelineMessage = timelineMessage)
    }

    private fun formatTimelineTemplate(
        session: NfcGameSession,
        payer: String,
        target: String,
        amount: Int,
        currency: String,
        templateOverride: String? = null,
        extraPlaceholders: Map<String, String> = emptyMap(),
    ): String {
        val nodes = session.gameTemplateId
            ?.let { flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(it) }
            .orEmpty()
        val template = templateOverride?.takeIf { it.isNotBlank() }
            ?: nodes.firstOrNull { it.type == "LOG_EVENT" }?.let { readMap(it.configJson)["template"]?.toString() }
            ?: nodes.firstOrNull { it.type == "MONEY_TRANSFER" }?.let { readMap(it.configJson)["timelineTemplate"]?.toString() }
            ?: "Timeline Ereignis gespeichert."
        return extraPlaceholders.entries.fold(
            template
            .replace("{payer}", payer)
            .replace("{target}", target)
            .replace("{amount}", amount.toString())
                .replace("{currency}", currency),
        ) { message, (key, value) -> message.replace("{$key}", value) }
    }

    private fun finishSession(
        session: NfcGameSession,
        winningTeamId: UUID?,
        endReason: String,
        allTeamIds: Collection<UUID>,
    ) {
        session.status = SessionStatus.FINISHED
        session.currentStateKey = "finished"
        session.endedAt = Instant.now()
        sessionRepository.save(session)
        if (resultRepository.findBySessionId(requireNotNull(session.id)) == null) {
            resultRepository.save(
                NfcGameResult().apply {
                    sessionId = session.id
                    this.winningTeamId = winningTeamId
                    this.endReason = endReason
                },
            )
        }
        statisticsService.recordGameFinished(allTeamIds, winningTeamId)
    }

    private fun buildScreen(session: NfcGameSession): ScreenModel {
        val sessionId = requireNotNull(session.id)
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)
        return when (session.status) {
            SessionStatus.CONFIGURING -> buildFlowScreen(session) ?: buildPlayerScanScreen(session, teams)
            SessionStatus.BUILDING_TEAMS, SessionStatus.LOBBY -> {
                if (session.currentStateKey == TEAM_SIZE_STATE) buildTeamSizeScreen(session, teams)
                else buildPlayerScanScreen(session, teams)
            }

            SessionStatus.READY -> ScreenModel(
                screenType = ScreenType.MENU,
                title = "Bereit",
                subtitle = "Spielkarte scannen zum Starten",
                menuItems = listOf(MenuItem("Start", "START")),
                selectedIndex = 0,
                context = mapOf("sessionStatus" to session.status.name),
            )

            SessionStatus.RUNNING -> {
                if (isEconomySession(session) && currentNodeKey(session).startsWith(BANK_STATE_PREFIX)) {
                    buildBankScreen(session)
                } else {
                    buildFlowScreen(session) ?: ScreenModel(
                        screenType = ScreenType.WAITING_FOR_SCAN,
                        title = "Spiel laeuft",
                        subtitle = "Spielerkarte fuer Gewinn scannen",
                        lines = listOf("Runde ${effectiveCurrentRoundNumber(session) + 1}", "Spielkarte scannt = beenden"),
                        context = mapOf("sessionStatus" to session.status.name),
                    )
                }
            }

            SessionStatus.FINISHED -> ScreenModel(
                screenType = ScreenType.RESULT,
                title = "Spiel beendet",
                subtitle = "Ergebnis gespeichert",
                context = mapOf("sessionStatus" to session.status.name),
            )

            SessionStatus.RESET -> ScreenModel(
                screenType = ScreenType.MESSAGE,
                title = "Zurueckgesetzt",
                subtitle = "Session wurde beendet",
                context = mapOf("sessionStatus" to session.status.name),
            )

            else -> ScreenModel(
                screenType = ScreenType.MESSAGE,
                title = session.status.name,
                context = mapOf("sessionStatus" to session.status.name),
            )
        }
    }

    private fun storeUnknownCard(cardUid: String, accountId: Long?): StateMachineResult {
        cardRepository.save(
            NfcCard().apply {
                this.cardUid = cardUid
                this.accountId = accountId
                cardType = CardType.UNKNOWN
                status = CardStatus.UNASSIGNED
            },
        )
        return StateMachineResult(
            session = findActiveSession(accountId),
            screen = ScreenModel(
                screenType = ScreenType.MESSAGE,
                title = "Unbekannte Karte",
                subtitle = "Karte wurde gespeichert und kann im Adminbereich zugewiesen werden.",
                lines = listOf(cardUid),
                context = mapOf("cardUid" to cardUid),
            ),
            effects = listOf("BEEP_INFO"),
        )
    }

    private fun buildPlayerScanScreen(session: NfcGameSession, teams: List<NfcSessionTeam>): ScreenModel =
        activeScanTeam(teams)?.let { team ->
            val members = memberRepository.findAllBySessionTeamId(requireNotNull(team.id))
            val names = members.mapNotNull { member ->
                member.playerId?.let { playerRepository.findById(it).orElse(null)?.name }
            }
            ScreenModel(
                screenType = ScreenType.WAITING_FOR_SCAN,
                title = "Spieler scannen",
                subtitle = "${team.name}: ${members.size}/${team.targetSize}",
                lines = names.take(3).map { "- $it" } + if (names.size > 3) listOf("+${names.size - 3} weitere") else emptyList(),
                context = mapOf("sessionStatus" to session.status.name),
            )
        } ?: ScreenModel(
            screenType = ScreenType.WAITING_FOR_SCAN,
            title = "Team komplett",
            subtitle = "Spielkarte starten oder naechstes Team waehlen.",
            lines = compactTeamSummary(teams),
            context = mapOf("sessionStatus" to session.status.name),
        )

    private fun buildTeamSizeScreen(session: NfcGameSession, teams: List<NfcSessionTeam>): ScreenModel {
        val setupTeam = setupTeamOrNull(teams)
        val teamName = setupTeam?.name ?: "Team ${(teams.maxOfOrNull { it.teamOrder } ?: 0) + 1}"
        val currentSize = setupTeam?.targetSize?.takeIf { it > 0 } ?: 1
        val completedTeams = teams.filter { team ->
            team.targetSize > 0 && memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).size >= team.targetSize
        }
        val hasCompletedTeam = completedTeams.isNotEmpty()
        val previousLine = completedTeams.lastOrNull()?.let { team ->
            "Fertig: ${team.name} (${team.targetSize})"
        }
        val startHint = if (hasCompletedTeam) listOf("Spielkarte scannen = mit bisherigen Teams starten") else emptyList()
        return ScreenModel(
            screenType = ScreenType.NUMBER_PICKER,
            title = "Teamgröße wählen",
            subtitle = "Wie viele Spieler sollen in $teamName sein?",
            lines = listOfNotNull(
                "Teams fertig: ${completedTeams.size}",
                previousLine,
                "Touch: Teamgroesse direkt setzen",
            ) + startHint,
            numberValue = currentSize,
            context = mapOf(
                "sessionStatus" to session.status.name,
                "setupState" to TEAM_SIZE_STATE,
                "numberSmallStep" to 1,
                "numberLargeStep" to 1,
            ),
        )
    }

    private fun buildFlowScreen(session: NfcGameSession): ScreenModel? {
        val node = currentFlowNode(session) ?: return null
        val config = readMap(node.configJson)
        val context = flowContext(session)
        val title = renderBuilderTemplate(session, node.title, context) ?: node.title
        val text = renderBuilderTemplate(session, config["text"]?.toString(), context)
        return when (node.type) {
            "START" -> {
                val next = nextNodeByEvent(node, "NEXT")
                if (next != null) {
                    val nextNode = flowNodeRepository.findById(next).orElse(null)
                    session.currentStateKey = nextNode?.let { flowStateKey(it) } ?: next.toString()
                    sessionRepository.save(session)
                    buildFlowScreen(session)
                } else {
                    ScreenModel(ScreenType.MESSAGE, title, text, context = mapOf("sessionStatus" to session.status.name))
                }
            }

            "MENU" -> {
                val items = menuItemsForNode(session, node)
                ScreenModel(
                    screenType = ScreenType.MENU,
                    title = title,
                    subtitle = text,
                    menuItems = items,
                    selectedIndex = currentSelectedIndex(session, items.size),
                    context = mapOf("sessionStatus" to session.status.name, "nodeType" to node.type),
                )
            }

            "NUMBER_PICKER" -> {
                val min = intConfig(config, "min") ?: 1
                val max = intConfig(config, "max") ?: 99
                val smallStep = intConfig(config, "smallStep") ?: intConfig(config, "step") ?: 1
                val largeStep = intConfig(config, "largeStep") ?: smallStep
                ScreenModel(
                    screenType = ScreenType.NUMBER_PICKER,
                    title = title,
                    subtitle = text,
                    lines = listOf("Touch: Wert setzen"),
                    numberValue = currentNumberValue(session, node),
                    context = mapOf(
                        "sessionStatus" to session.status.name,
                        "nodeType" to node.type,
                        "min" to min,
                        "max" to max,
                        "numberSmallStep" to smallStep,
                        "numberLargeStep" to largeStep,
                    ),
                )
            }

            "WAIT_PLAYER_CARD", "WAIT_ANY_CARD", "WAIT_GAME_CARD" -> ScreenModel(
                screenType = ScreenType.WAITING_FOR_SCAN,
                title = title,
                subtitle = text,
                context = mapOf("sessionStatus" to session.status.name, "nodeType" to node.type),
            )

            else -> ScreenModel(
                screenType = ScreenType.MESSAGE,
                title = title,
                subtitle = text,
                context = mapOf(
                    "sessionStatus" to session.status.name,
                    "nodeType" to node.type,
                    "continueMode" to (config["continueMode"]?.toString() ?: "AUTO"),
                ),
            )
        }
    }

    private fun startSession(session: NfcGameSession) {
        session.status = SessionStatus.RUNNING
        session.startedAt = Instant.now()
        val template = gameTemplateRepository.findById(requireNotNull(session.gameTemplateId)).orElse(null)
        val firstNodeId = template?.let { firstFlowNodeAfterStart(it) }
            ?: flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(requireNotNull(session.gameTemplateId))
                .firstOrNull { it.type in setOf("WAIT_PLAYER_CARD", "WAIT_ANY_CARD", "WAIT_GAME_CARD") }
                ?.id
        if (firstNodeId != null) {
            enterRunningFlowNode(session, firstNodeId, emptyMap(), emptyList())
        } else {
            session.currentStateKey = "running"
            sessionRepository.save(session)
        }
    }

    private fun missingPlayers(session: NfcGameSession): Int {
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
        val team = activeScanTeam(teams) ?: return if (hasCompletedTeam(teams)) 0 else 1
        val current = memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).size
        return (team.targetSize - current).coerceAtLeast(0)
    }

    private fun compactTeamSummary(teams: List<NfcSessionTeam>): List<String> {
        val completedTeams = teams.filter { team ->
            team.targetSize > 0 && memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).isNotEmpty()
        }
        val lastTeam = completedTeams.lastOrNull() ?: return emptyList()
        val members = memberRepository.findAllBySessionTeamId(requireNotNull(lastTeam.id))
        return listOf("${lastTeam.name}: ${members.size}/${lastTeam.targetSize}")
    }

    private fun activeScanTeam(teams: List<NfcSessionTeam>): NfcSessionTeam? =
        teams.firstOrNull { team ->
            team.status != "CONFIGURING" && team.targetSize > 0 && memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).size < team.targetSize
        }

    private fun setupTeamOrNull(teams: List<NfcSessionTeam>): NfcSessionTeam? =
        teams.firstOrNull { team ->
            (team.status == "CONFIGURING" || team.targetSize <= 0) && memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).isEmpty()
        }

    private fun hasCompletedTeam(session: NfcGameSession): Boolean =
        hasCompletedTeam(teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id)))

    private fun hasCompletedTeam(teams: List<NfcSessionTeam>): Boolean =
        teams.any { team ->
            team.targetSize > 0 && memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).size >= team.targetSize
        }

    private fun ensureSetupTeam(session: NfcGameSession): NfcSessionTeam {
        val sessionId = requireNotNull(session.id)
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)
        return setupTeamOrNull(teams) ?: createSetupTeam(session, teams)
    }

    private fun createSetupTeam(session: NfcGameSession, teams: List<NfcSessionTeam>): NfcSessionTeam {
        val sessionId = requireNotNull(session.id)
        val nextOrder = (teams.maxOfOrNull { it.teamOrder } ?: 0) + 1
        val team = teamRepository.save(
            NfcSessionTeam().apply {
                this.sessionId = sessionId
                name = "Team $nextOrder"
                teamOrder = nextOrder
                targetSize = 0
                status = "CONFIGURING"
            },
        )
        createTeamAccountIfNeeded(session, team)
        return team
    }

    private fun createTeamAccountIfNeeded(session: NfcGameSession, team: NfcSessionTeam) {
        val template = session.gameTemplateId?.let { gameTemplateRepository.findById(it).orElse(null) } ?: return
        if (!isEconomySession(session)) return
        val sessionId = requireNotNull(session.id)
        val teamId = requireNotNull(team.id)
        val startCapital = economyStartCapital(template)
        val accountExists = accountRepository.findAllBySessionId(sessionId).any { it.teamId == teamId }
        if (!accountExists) {
            accountRepository.save(
                NfcSessionAccount().apply {
                    this.sessionId = sessionId
                    ownerType = OwnerType.TEAM
                    this.teamId = teamId
                    balance = startCapital
                },
            )
        }
    }

    private fun isEconomySession(session: NfcGameSession): Boolean {
        val template = session.gameTemplateId?.let { gameTemplateRepository.findById(it).orElse(null) } ?: return false
        return isEconomyTemplate(template)
    }

    private fun isEconomyTemplate(template: NfcGameTemplate): Boolean =
        template.economyEnabled ||
            flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(requireNotNull(template.id)).any {
                val config = readMap(it.configJson)
                it.type in setOf("ENABLE_BANK", "MONEY_TRANSFER") ||
                    normalizeValueKey(config["valueKey"]?.toString().orEmpty()) == "money" ||
                    config.values.any { value -> value?.toString()?.contains(".money", ignoreCase = true) == true }
            }

    private fun economyStartCapital(template: NfcGameTemplate): BigDecimal {
        val bankNode = flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(requireNotNull(template.id))
            .firstOrNull { it.type == "ENABLE_BANK" }
        val configured = bankNode?.let { readMap(it.configJson)["startCapital"] }
        return configured?.toString()?.toBigDecimalOrNull() ?: template.startCapital
    }

    private fun removeEmptySetupTeams(session: NfcGameSession, includeSizedSetupTeams: Boolean = false) {
        val sessionId = requireNotNull(session.id)
        teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)
            .filter { team ->
                (team.status == "CONFIGURING" || team.targetSize <= 0 || includeSizedSetupTeams) &&
                    memberRepository.findAllBySessionTeamId(requireNotNull(team.id)).isEmpty()
            }
            .forEach { team ->
                accountRepository.findAllBySessionId(sessionId)
                    .filter { it.teamId == team.id }
                    .forEach { accountRepository.delete(it) }
                teamRepository.delete(team)
            }
    }

    private fun firstRuntimeNodeId(template: NfcGameTemplate): UUID? {
        val gameId = requireNotNull(template.id)
        val nodes = flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(gameId)
        if (nodes.isEmpty()) return null
        val startId = template.startNodeId ?: nodes.firstOrNull { it.type == "START" }?.id ?: return nodes.firstNotNullOfOrNull { it.id }
        val start = nodes.firstOrNull { it.id == startId } ?: return startId
        val nextId = nextNodeByEvent(start, "NEXT") ?: startId
        val nextNode = flowNodeRepository.findById(nextId).orElse(null) ?: return nextId
        return if (nextNode.type == "ENABLE_BANK") null else nextId
    }

    private fun firstFlowNodeAfterStart(template: NfcGameTemplate): UUID? {
        val gameId = requireNotNull(template.id)
        val nodes = flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(gameId)
        if (nodes.isEmpty()) return null
        val startId = template.startNodeId ?: nodes.firstOrNull { it.type == "START" }?.id ?: return nodes.firstNotNullOfOrNull { it.id }
        val start = nodes.firstOrNull { it.id == startId } ?: return startId
        return nextNodeByEvent(start, "NEXT") ?: startId
    }

    private fun currentFlowNode(session: NfcGameSession): NfcFlowNode? {
        val nodeId = runCatching { UUID.fromString(currentNodeKey(session)) }.getOrNull() ?: return null
        return flowNodeRepository.findById(nodeId).orElse(null)
    }

    private fun nextNodeForInput(session: NfcGameSession, node: NfcFlowNode, eventType: EventType, payload: Map<String, Any?>): UUID? =
        when (node.type) {
            "MENU" -> {
                val edges = flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(requireNotNull(node.gameTemplateId))
                    .filter { it.sourceNodeId == node.id }
                val items = menuItemsForNode(session, node)
                val selected = when {
                    eventType == EventType.TOUCH_MENU_SELECT -> selectedMenuItemFromTouchPayload(items, payload)
                    isConfirmEvent(eventType) -> items.getOrNull(currentSelectedIndex(session, items.size))
                    else -> null
                }
                if (selected == null) {
                    null
                } else {
                    edges.firstOrNull { edge ->
                        val condition = readMap(edge.conditionConfigJson)
                        val expected = condition["selection"]?.toString()
                        expected == selected?.label ||
                            expected == selected?.value ||
                            (expected == "{teams}" && selected?.label != "Bank")
                    }?.targetNodeId
                        ?: edges.firstOrNull { readMap(it.conditionConfigJson).isEmpty() }?.targetNodeId
                        ?: edges.getOrNull(items.indexOf(selected).coerceAtLeast(0))?.targetNodeId
                }
            }

            "NUMBER_PICKER" -> if (isConfirmEvent(eventType) || eventType == EventType.TOUCH_NUMBER_SET) nextNodeByEvent(node, "VALUE_CONFIRMED") else null
            else -> if (isConfirmEvent(eventType)) nextNodeByEvent(node, "NEXT") else null
        }

    private fun enterRunningFlowNode(
        session: NfcGameSession,
        firstNodeId: UUID,
        context: Map<String, String>,
        effects: List<String>,
    ): StateMachineResult {
        var nodeId: UUID? = firstNodeId
        var runtimeContext = context
        var timelineMessage: String? = null
        repeat(MAX_RUNTIME_STEPS) {
            val node = nodeId?.let { flowNodeRepository.findById(it).orElse(null) }
                ?: return StateMachineResult(session = session, screen = buildScreen(session), effects = effects, timelineMessage = timelineMessage)
            when (node.type) {
                "AWARD_POINTS", "AWARD_ROUND_WIN" -> {
                    val config = readMap(node.configJson)
                    runtimeContext = runtimeContext + applyPointsNode(session, node, config, runtimeContext)
                    nodeId = nextNodeByEventPreferringType(node, "NEXT", "LOG_EVENT")
                }

                "START", "ENABLE_BANK", "DASHBOARD_METRIC" -> nodeId = nextNodeByEvent(node, "NEXT")

                "SHOW_MESSAGE" -> {
                    val continueMode = readMap(node.configJson)["continueMode"]?.toString()?.uppercase()
                    if (continueMode == "BUTTON") {
                        session.currentStateKey = flowStateKey(node, context = runtimeContext)
                        sessionRepository.save(session)
                        return StateMachineResult(session = session, screen = buildScreen(session), effects = effects, timelineMessage = timelineMessage)
                    }
                    nodeId = nextNodeByEvent(node, "NEXT")
                }

                "LOG_EVENT" -> {
                    timelineMessage = timelineMessageForNode(session, node, runtimeContext)
                    nodeId = nextNodeByEvent(node, "NEXT")
                }

                "CALCULATE" -> {
                    runtimeContext = runtimeContext + calculateFlowValue(session, node, readMap(node.configJson), runtimeContext)
                    nodeId = nextNodeByEvent(node, "NEXT")
                }

                "MONEY_TRANSFER" -> {
                    val result = executeMoneyTransferNode(session, node, runtimeContext)
                    if (result != null) {
                        runtimeContext = runtimeContext + result.context
                        timelineMessage = result.timelineMessage
                        nodeId = nextNodeByEvent(node, "NEXT")
                    } else {
                        session.currentStateKey = flowStateKey(node, context = runtimeContext)
                        sessionRepository.save(session)
                        return StateMachineResult(
                            session = session,
                            screen = buildScreen(session),
                            effects = listOf("BEEP_ERROR"),
                            errors = listOf("Geldtransfer konnte aus dem Flow nicht ausgefuehrt werden."),
                        )
                    }
                }

                "IF_ELSE", "CONDITION", "BRANCH" -> {
                    nodeId = nextNodeByEvent(node, if (evaluateRuntimeCondition(session, node, runtimeContext)) "TRUE" else "FALSE")
                }

                "END_GAME" -> {
                    val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
                    finishSession(session, calculateConfiguredWinner(session, teams), "FLOW_ENDED", teams.mapNotNull { it.id })
                    return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_WIN"), timelineMessage = timelineMessage)
                }

                else -> {
                    session.status = SessionStatus.RUNNING
                    session.currentStateKey = flowStateKey(node, context = runtimeContext)
                    sessionRepository.save(session)
                    return StateMachineResult(session = session, screen = buildScreen(session), effects = effects, timelineMessage = timelineMessage)
                }
            }
        }
        session.currentStateKey = "running"
        sessionRepository.save(session)
        return StateMachineResult(session = session, screen = buildScreen(session), effects = effects, timelineMessage = timelineMessage)
    }

    private data class MoneyTransferExecution(
        val context: Map<String, String>,
        val timelineMessage: String,
    )

    private fun executeMoneyTransferNode(
        session: NfcGameSession,
        node: NfcFlowNode,
        context: Map<String, String>,
    ): MoneyTransferExecution? {
        val config = readMap(node.configJson)
        val amountFrom = config["amountFrom"]?.toString()?.takeIf { it.isNotBlank() }
            ?: config["valueFrom"]?.toString()?.takeIf { it.isNotBlank() }
        val amount = amountFrom?.let { numericValueForExpression(session, it, context)?.toInt() }
            ?: context["amount"]?.toIntOrNull()
            ?: intConfig(config, "amount")
            ?: intConfig(config, "value")
            ?: bankStepConfig(session).smallStep
        val source = config["source"]?.toString()?.takeIf { it.isNotBlank() } ?: "player"
        val payerPlayerId = playerIdForBuilderReference(source, context)
            ?: context["player"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val selectedAction = context["selection"].orEmpty()
        val payerAccount = when {
            selectedAction.contains("Auszahlen", ignoreCase = true) -> bankAccount(session)
            config["source"]?.toString() == "bank" -> bankAccount(session)
            payerPlayerId != null -> accountForPlayer(session, payerPlayerId)
            else -> accountForBuilderReference(session, source, context)
        } ?: return null
        val targetExpression = config["target"]?.toString()?.takeIf { it.isNotBlank() }
        val targetAccount = when {
            targetExpression == "selectedTarget" || targetExpression == "selectedAccount" -> accountForBuilderReference(session, targetExpression, context)
            targetExpression != null && targetExpression != "bank" -> accountForBuilderReference(session, targetExpression, context)
            context["target"] != null -> accountRepository.findById(UUID.fromString(requireNotNull(context["target"]))).orElse(null)
                ?.takeIf { it.sessionId == session.id }
            selectedAction.contains("Einzahlen", ignoreCase = true) -> bankAccount(session)
            selectedAction.contains("Auszahlen", ignoreCase = true) && payerPlayerId != null -> accountForPlayer(session, payerPlayerId)
            targetExpression == "bank" -> bankAccount(session)
            else -> context["targetAccountId"]?.let { accountRepository.findById(UUID.fromString(it)).orElse(null) }
        } ?: return null
        if (payerAccount.id == targetAccount.id) return null
        val amountValue = BigDecimal.valueOf(amount.toLong())
        val payerOwner = when (payerAccount.ownerType) {
            OwnerType.BANK -> ValueOwner(OwnerType.BANK, requireNotNull(payerAccount.id))
            OwnerType.TEAM -> ValueOwner(OwnerType.TEAM, requireNotNull(payerAccount.teamId))
        }
        val targetOwner = when (targetAccount.ownerType) {
            OwnerType.BANK -> ValueOwner(OwnerType.BANK, requireNotNull(targetAccount.id))
            OwnerType.TEAM -> ValueOwner(OwnerType.TEAM, requireNotNull(targetAccount.teamId))
        }
        val payerBalance = sessionValue(session, payerOwner.ownerType, payerOwner.ownerId, "money") ?: payerAccount.balance
        if (payerAccount.ownerType != OwnerType.BANK && payerBalance < amountValue) return null
        changeSessionValue(session, payerOwner.ownerType, payerOwner.ownerId, "money", amountValue, "SUBTRACT")
        changeSessionValue(session, targetOwner.ownerType, targetOwner.ownerId, "money", amountValue, "ADD")
        moneyTransactionRepository.save(
            NfcMoneyTransaction().apply {
                sessionId = session.id
                fromAccountId = payerAccount.id
                toAccountId = targetAccount.id
                this.amount = amountValue
                initiatedByPlayerId = payerPlayerId
            },
        )
        val payerName = payerPlayerId?.let { playerRepository.findById(it).orElse(null)?.name }
            ?: if (payerAccount.ownerType == OwnerType.BANK) "Bank" else "Ein Spieler"
        val targetLabel = bankTargets(session).firstOrNull { it.accountId == targetAccount.id }?.label
            ?: if (targetAccount.ownerType == OwnerType.BANK) "Bank" else "Empfaenger"
        val currency = bankStepConfig(session).currency
        val message = formatTimelineTemplate(session, payerName, targetLabel, amount, currency)
        return MoneyTransferExecution(
            context = mapOf(
                "amount" to amount.toString(),
                "payerAccountId" to requireNotNull(payerAccount.id).toString(),
                "targetAccountId" to requireNotNull(targetAccount.id).toString(),
            ),
            timelineMessage = message,
        )
    }

    private fun timelineMessageForNode(session: NfcGameSession, node: NfcFlowNode, context: Map<String, String>): String {
        val scannedPlayerName = (context["scannedPlayer"] ?: context["player"])?.let { playerName(it) }
        val targetLabel = context["targetLabel"]
            ?: context["targetAccountId"]?.let { accountLabel(session, it) }
            ?: context["target"]?.let { accountLabel(session, it) }
            ?: context["lastAwardedTeam"]?.let { runCatching { UUID.fromString(it) }.getOrNull() }?.let { teamLabel(it) }
            ?: "Empfaenger"
        val amount = context["amount"]?.toIntOrNull()
            ?: context["lastAwardedPoints"]?.toIntOrNull()
            ?: 0
        val placeholders = context.mapValues { (key, value) -> displayValueForVariable(session, key, value) }.toMutableMap()
        val template = readMap(node.configJson)["template"]?.toString()
        template
            ?.let { Regex("""\{([A-Za-z][A-Za-z0-9]*(?:\.[A-Za-z][A-Za-z0-9]*)?)\}""").findAll(it) }
            ?.map { it.groupValues[1] }
            ?.forEach { key ->
                displayValueForExpression(session, key, context)?.let { placeholders[key] = it }
            }
        scannedPlayerName?.let {
            placeholders["player"] = it
            placeholders["scannedPlayer"] = it
        }
        placeholders["target"] = targetLabel
        placeholders["targetLabel"] = targetLabel
        placeholders["amount"] = amount.toString()
        placeholders["currency"] = bankStepConfig(session).currency
        return formatTimelineTemplate(
            session,
            scannedPlayerName ?: "Ein Spieler",
            targetLabel,
            amount,
            bankStepConfig(session).currency,
            templateOverride = template,
            extraPlaceholders = placeholders,
        )
    }

    private fun displayValueForVariable(session: NfcGameSession, key: String, value: String): String {
        if (value.isBlank()) return value
        val uuid = runCatching { UUID.fromString(value) }.getOrNull() ?: return value
        if (key.endsWith("Team", ignoreCase = true) || key in setOf("team", "scannedTeam", "lastScannedTeam", "lastAwardedTeam", "winner")) {
            return teamLabel(uuid)
        }
        playerName(value)?.let { return it }
        accountLabel(session, value)?.let { return it }
        return teamRepository.findById(uuid).orElse(null)?.name ?: value
    }

    private fun renderBuilderTemplate(session: NfcGameSession, template: String?, context: Map<String, String>): String? {
        val raw = template?.takeIf { it.isNotBlank() } ?: return template
        return Regex("""\{([A-Za-z][A-Za-z0-9]*(?:\.[A-Za-z][A-Za-z0-9]*)?)\}""").replace(raw) { match ->
            val key = match.groupValues[1]
            builderTemplateValue(session, key, context) ?: match.value
        }
    }

    private fun builderTemplateValue(session: NfcGameSession, key: String, context: Map<String, String>): String? =
        when (key) {
            "teams" -> teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
                .joinToString(", ") { it.name }
            "currency" -> bankStepConfig(session).currency
            else -> displayValueForExpression(session, key, context)
        }

    private fun playerName(playerId: String): String? =
        runCatching { UUID.fromString(playerId) }.getOrNull()
            ?.let { playerRepository.findById(it).orElse(null)?.name }

    private fun accountLabel(session: NfcGameSession, accountId: String): String? =
        runCatching { UUID.fromString(accountId) }.getOrNull()
            ?.let { id -> bankTargets(session).firstOrNull { it.accountId == id }?.label }

    private fun teamLabel(teamId: UUID): String =
        teamRepository.findById(teamId).orElse(null)?.name ?: "Team"

    private fun bankAccount(session: NfcGameSession): NfcSessionAccount? =
        accountRepository.findAllBySessionId(requireNotNull(session.id)).firstOrNull { it.ownerType == OwnerType.BANK }

    private fun flowInputContext(session: NfcGameSession, node: NfcFlowNode, eventType: EventType, payload: Map<String, Any?>): Map<String, String> {
        if (!isConfirmEvent(eventType) && eventType != EventType.TOUCH_MENU_SELECT && eventType != EventType.TOUCH_NUMBER_SET) return emptyMap()
        return when (node.type) {
            "MENU" -> {
                val items = menuItemsForNode(session, node)
                val selected = if (eventType == EventType.TOUCH_MENU_SELECT) {
                    selectedMenuItemFromTouchPayload(items, payload)
                } else {
                    items.getOrNull(currentSelectedIndex(session, items.size))
                }
                val storeAs = readMap(node.configJson)["storeAs"]?.toString()?.takeIf { it.isNotBlank() }
                buildMap {
                    selected?.let {
                        if (isDynamicAccountMenu(node)) {
                            put("target", it.value)
                            storeAs?.let { key -> put(key, it.value) }
                        } else {
                            put("selection", it.label)
                            storeAs?.let { key -> put(key, it.label) }
                        }
                    }
                }
            }

            "NUMBER_PICKER" -> {
                val config = readMap(node.configJson)
                val storeAs = config["storeAs"]?.toString()
                    ?: if (node.title.contains("Betrag", ignoreCase = true)) "amount" else "number"
                mapOf(storeAs to currentNumberValue(session, node).toString())
            }

            else -> emptyMap()
        }
    }

    private fun menuItemsForNode(session: NfcGameSession, node: NfcFlowNode): List<MenuItem> {
        val config = readMap(node.configJson)
        if (config["optionsSource"]?.toString() == "playersAndBank") {
            return bankTargets(session).map { MenuItem(it.label, it.accountId.toString()) }
        }
        val bank = accountRepository.findAllBySessionId(requireNotNull(session.id))
            .firstOrNull { it.ownerType == OwnerType.BANK }
        return stringList(config["options"]).flatMap { option ->
            when {
                option.equals("{teams}", ignoreCase = true) -> bankTargets(session)
                    .filter { it.label != "Bank" }
                    .map { MenuItem(it.label, it.accountId.toString()) }
                option.equals("{bank}", ignoreCase = true) || option.equals("Bank", ignoreCase = true) ->
                    listOfNotNull(bank?.id?.let { MenuItem("Bank", it.toString()) })
                else -> listOf(MenuItem(option, option))
            }
        }
    }

    private fun isDynamicAccountMenu(node: NfcFlowNode): Boolean {
        val config = readMap(node.configJson)
        return config["optionsSource"]?.toString() == "playersAndBank" ||
            stringList(config["options"]).any {
                it.equals("{teams}", ignoreCase = true) ||
                    it.equals("{bank}", ignoreCase = true) ||
                    it.equals("Bank", ignoreCase = true)
            }
    }

    private fun handleFlowSelectionInput(
        session: NfcGameSession,
        node: NfcFlowNode,
        eventType: EventType,
        payload: Map<String, Any?>,
    ): Boolean {
        if (node.type == "MENU") {
            if (eventType == EventType.TOUCH_MENU_SELECT) {
                val items = menuItemsForNode(session, node)
                val selected = selectedMenuItemFromTouchPayload(items, payload)
                if (selected == null) return false
                val next = items.indexOf(selected).coerceAtLeast(0)
                session.currentStateKey = flowStateKey(node, selectedIndex = next, context = flowContext(session))
                sessionRepository.save(session)
                return false
            }
            val optionCount = menuItemsForNode(session, node).size
            if (optionCount <= 0) return false
            val delta = when (eventType) {
                EventType.JOYSTICK_DOWN, EventType.JOYSTICK_RIGHT -> 1
                EventType.JOYSTICK_UP, EventType.JOYSTICK_LEFT -> -1
                else -> return false
            }
            val current = currentSelectedIndex(session, optionCount)
            val next = (current + delta + optionCount) % optionCount
            session.currentStateKey = flowStateKey(node, selectedIndex = next, context = flowContext(session))
            sessionRepository.save(session)
            return true
        }

        if (node.type == "NUMBER_PICKER") {
            val config = readMap(node.configJson)
            val min = intConfig(config, "min") ?: 1
            val max = intConfig(config, "max") ?: 99
            if (eventType == EventType.TOUCH_NUMBER_SET) {
                val rawValue = (payload["value"] as? Number)?.toInt() ?: payload["value"]?.toString()?.toIntOrNull() ?: return false
                val next = rawValue.coerceIn(min, max)
                session.currentStateKey = flowStateKey(node, numberValue = next, context = flowContext(session))
                sessionRepository.save(session)
                return false
            }
            val smallStep = intConfig(config, "smallStep") ?: intConfig(config, "step") ?: 1
            val largeStep = intConfig(config, "largeStep") ?: smallStep
            val delta = when (eventType) {
                EventType.JOYSTICK_RIGHT -> smallStep
                EventType.JOYSTICK_LEFT -> -smallStep
                EventType.JOYSTICK_UP -> largeStep
                EventType.JOYSTICK_DOWN -> -largeStep
                else -> return false
            }
            val next = (currentNumberValue(session, node) + delta).coerceIn(min, max)
            session.currentStateKey = flowStateKey(node, numberValue = next, context = flowContext(session))
            sessionRepository.save(session)
            return true
        }

        return false
    }

    private fun isConfirmEvent(eventType: EventType): Boolean =
        eventType == EventType.JOYSTICK_PRESS || eventType == EventType.TOUCH_CONFIRM

    private fun selectedMenuItemFromTouchPayload(items: List<MenuItem>, payload: Map<String, Any?>): MenuItem? {
        if (items.isEmpty()) return null
        val index = (payload["index"] as? Number)?.toInt()
        if (index != null) {
            return items.getOrNull(index.coerceIn(0, items.lastIndex))
        }
        val value = payload["value"]?.toString()
        if (!value.isNullOrBlank()) {
            return items.firstOrNull { it.value == value || it.label == value }
        }
        val label = payload["label"]?.toString()
        if (!label.isNullOrBlank()) {
            return items.firstOrNull { it.label == label }
        }
        return null
    }

    private fun handleBankInput(session: NfcGameSession, eventType: EventType, payload: Map<String, Any?>): StateMachineResult {
        val state = bankUiState(session)
        when (state.mode) {
            BANK_TARGET_MODE -> {
                val options = bankTargets(session)
                if (options.isEmpty()) return StateMachineResult(session, buildScreen(session), effects = listOf("BEEP_ERROR"), errors = listOf("Keine Konten vorhanden."))
                when (eventType) {
                    EventType.TOUCH_MENU_SELECT -> {
                        val indexFromPayload = (payload["index"] as? Number)?.toInt()
                        val valueFromPayload = payload["value"]?.toString()
                        val selected = when {
                            indexFromPayload != null -> options.getOrNull(indexFromPayload.coerceIn(0, options.lastIndex))
                            !valueFromPayload.isNullOrBlank() -> options.firstOrNull { it.accountId.toString() == valueFromPayload || it.label == valueFromPayload }
                            else -> null
                        } ?: options.getOrNull(state.targetIndex.coerceIn(0, options.lastIndex))
                        if (selected != null) {
                            val selectedIndex = options.indexOf(selected).coerceAtLeast(0)
                            session.currentStateKey = bankStateKey(BANK_AMOUNT_MODE, selectedIndex = selectedIndex, targetAccountId = selected.accountId, amount = state.amount)
                        }
                    }
                    EventType.JOYSTICK_DOWN, EventType.JOYSTICK_RIGHT -> {
                        val next = (state.targetIndex + 1).floorMod(options.size)
                        session.currentStateKey = bankStateKey(BANK_TARGET_MODE, selectedIndex = next, amount = state.amount)
                    }
                    EventType.JOYSTICK_UP, EventType.JOYSTICK_LEFT -> {
                        val next = (state.targetIndex - 1).floorMod(options.size)
                        session.currentStateKey = bankStateKey(BANK_TARGET_MODE, selectedIndex = next, amount = state.amount)
                    }
                    EventType.JOYSTICK_PRESS -> {
                        val selected = options[state.targetIndex.coerceIn(0, options.lastIndex)]
                        session.currentStateKey = bankStateKey(BANK_AMOUNT_MODE, selectedIndex = state.targetIndex, targetAccountId = selected.accountId, amount = state.amount)
                    }
                    else -> Unit
                }
            }
            BANK_AMOUNT_MODE -> {
                val steps = bankStepConfig(session)
                when (eventType) {
                    EventType.TOUCH_NUMBER_SET -> {
                        val rawValue = (payload["value"] as? Number)?.toInt() ?: payload["value"]?.toString()?.toIntOrNull()
                        if (rawValue != null) {
                            session.currentStateKey = bankStateKey(
                                BANK_PAY_SCAN_MODE,
                                selectedIndex = state.targetIndex,
                                targetAccountId = state.targetAccountId,
                                amount = rawValue.coerceIn(steps.minAmount, steps.maxAmount),
                            )
                        }
                    }
                    EventType.JOYSTICK_RIGHT -> {
                        session.currentStateKey = bankStateKey(BANK_AMOUNT_MODE, selectedIndex = state.targetIndex, targetAccountId = state.targetAccountId, amount = (state.amount + steps.smallStep).coerceAtMost(steps.maxAmount))
                    }
                    EventType.JOYSTICK_LEFT -> {
                        session.currentStateKey = bankStateKey(BANK_AMOUNT_MODE, selectedIndex = state.targetIndex, targetAccountId = state.targetAccountId, amount = (state.amount - steps.smallStep).coerceAtLeast(steps.minAmount))
                    }
                    EventType.JOYSTICK_UP -> {
                        session.currentStateKey = bankStateKey(BANK_AMOUNT_MODE, selectedIndex = state.targetIndex, targetAccountId = state.targetAccountId, amount = (state.amount + steps.largeStep).coerceAtMost(steps.maxAmount))
                    }
                    EventType.JOYSTICK_DOWN -> {
                        session.currentStateKey = bankStateKey(BANK_AMOUNT_MODE, selectedIndex = state.targetIndex, targetAccountId = state.targetAccountId, amount = (state.amount - steps.largeStep).coerceAtLeast(steps.minAmount))
                    }
                    EventType.JOYSTICK_PRESS -> {
                        session.currentStateKey = bankStateKey(BANK_PAY_SCAN_MODE, selectedIndex = state.targetIndex, targetAccountId = state.targetAccountId, amount = state.amount)
                    }
                    EventType.TOUCH_CONFIRM -> {
                        session.currentStateKey = bankStateKey(BANK_PAY_SCAN_MODE, selectedIndex = state.targetIndex, targetAccountId = state.targetAccountId, amount = state.amount)
                    }
                    else -> Unit
                }
            }
            BANK_PAY_SCAN_MODE -> {
                if (isConfirmEvent(eventType)) {
                    session.currentStateKey = bankStateKey(BANK_TARGET_MODE, selectedIndex = state.targetIndex, amount = state.amount)
                }
            }
        }
        sessionRepository.save(session)
        return StateMachineResult(session, buildScreen(session), effects = listOf("BEEP_INFO"))
    }

    private fun buildBankScreen(session: NfcGameSession): ScreenModel {
        val state = bankUiState(session)
        val targets = bankTargets(session)
        return when (state.mode) {
            BANK_TARGET_MODE -> {
                val selected = targets.getOrNull(state.targetIndex.coerceIn(0, (targets.size - 1).coerceAtLeast(0)))
                ScreenModel(
                    screenType = ScreenType.MENU,
                    title = "Empfaenger waehlen",
                    subtitle = selected?.let { "Aktuell: ${it.label}" } ?: "Spieler oder Bank",
                    menuItems = targets.map { MenuItem(it.label, it.accountId.toString()) },
                    selectedIndex = state.targetIndex.coerceIn(0, (targets.size - 1).coerceAtLeast(0)),
                    lines = listOfNotNull(
                        state.message?.takeIf { it == "transfer" }?.let { "Transfer gebucht" },
                        "Touch: Empfaenger antippen",
                        "Spielkarte scannt = beenden",
                    ),
                    context = mapOf("sessionStatus" to session.status.name, "bankMode" to state.mode),
                )
            }

            BANK_AMOUNT_MODE -> {
                val steps = bankStepConfig(session)
                val target = state.targetAccountId?.let { id -> targets.firstOrNull { it.accountId == id } }
                ScreenModel(
                    screenType = ScreenType.NUMBER_PICKER,
                    title = "Betrag waehlen",
                    subtitle = "An ${target?.label ?: "Empfaenger"}",
                    numberValue = state.amount,
                    lines = listOf(
                        "Kleine Schritte: ${steps.smallStep}",
                        "Grosse Schritte: ${steps.largeStep}",
                        "Touch: Wert setzen uebernimmt sofort",
                    ),
                    context = mapOf(
                        "sessionStatus" to session.status.name,
                        "bankMode" to state.mode,
                        "numberSmallStep" to steps.smallStep,
                        "numberLargeStep" to steps.largeStep,
                    ),
                )
            }

            else -> {
                val target = state.targetAccountId?.let { id -> targets.firstOrNull { it.accountId == id } }
                ScreenModel(
                    screenType = ScreenType.WAITING_FOR_SCAN,
                    title = "Zahler scannen",
                    subtitle = "${state.amount} an ${target?.label ?: "Empfaenger"}",
                    lines = listOf("Karte des zahlenden Spielers scannen"),
                    context = mapOf("sessionStatus" to session.status.name, "bankMode" to state.mode),
                )
            }
        }
    }

    private fun bankTargets(session: NfcGameSession): List<BankTarget> {
        val sessionId = requireNotNull(session.id)
        val accounts = accountRepository.findAllBySessionId(sessionId)
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId)
        val teamTargets = teams.mapNotNull { team ->
            val account = accounts.firstOrNull { it.ownerType == OwnerType.TEAM && it.teamId == team.id } ?: return@mapNotNull null
            val memberNames = memberRepository.findAllBySessionTeamId(requireNotNull(team.id))
                .mapNotNull { member -> member.playerId?.let { playerRepository.findById(it).orElse(null)?.name } }
                .take(2)
                .joinToString(", ")
                .takeIf { it.isNotBlank() }
            BankTarget(
                accountId = requireNotNull(account.id),
                label = memberNames?.let { "${team.name}: $it" } ?: team.name,
                balance = sessionValue(session, OwnerType.TEAM, requireNotNull(team.id), "money") ?: account.balance,
            )
        }
        val bankTarget = accounts.firstOrNull { it.ownerType == OwnerType.BANK }?.let {
            BankTarget(requireNotNull(it.id), "Bank", sessionValue(session, OwnerType.BANK, requireNotNull(it.id), "money") ?: it.balance)
        }
        return teamTargets + listOfNotNull(bankTarget)
    }

    private fun accountForPlayer(session: NfcGameSession, playerId: UUID): NfcSessionAccount? {
        val teams = teamRepository.findAllBySessionIdOrderByTeamOrderAsc(requireNotNull(session.id))
        val membership = memberRepository.findByPlayerIdAndSessionTeamIdIn(playerId, teams.mapNotNull { it.id }) ?: return null
        return accountRepository.findAllBySessionId(requireNotNull(session.id))
            .firstOrNull { it.ownerType == OwnerType.TEAM && it.teamId == membership.sessionTeamId }
    }

    private fun bankUiState(session: NfcGameSession): BankUiState {
        val tokens = session.currentStateKey.split("|")
        val mode = tokens.firstOrNull()?.takeIf { it.startsWith(BANK_STATE_PREFIX) } ?: BANK_TARGET_MODE
        val steps = bankStepConfig(session)
        return BankUiState(
            mode = mode,
            targetIndex = stateInt(session, "sel") ?: 0,
            targetAccountId = stateUuid(session, "target"),
            amount = (stateInt(session, "amount") ?: steps.smallStep.coerceAtLeast(steps.minAmount)).coerceIn(steps.minAmount, steps.maxAmount),
            message = stateString(session, "msg"),
        )
    }

    private fun bankStepConfig(session: NfcGameSession): BankStepConfig {
        val nodes = session.gameTemplateId
            ?.let { flowNodeRepository.findAllByGameTemplateIdOrderBySortOrderAsc(it) }
            .orEmpty()
        val moneyConfig = nodes.firstOrNull { it.type == "MONEY_TRANSFER" }?.let { readMap(it.configJson) }.orEmpty()
        val amountConfig = nodes.firstOrNull { it.type == "NUMBER_PICKER" && readMap(it.configJson)["storeAs"]?.toString() == "amount" }
            ?.let { readMap(it.configJson) }
            .orEmpty()
        val bankConfig = nodes.firstOrNull { it.type == "ENABLE_BANK" }?.let { readMap(it.configJson) }.orEmpty()
        return BankStepConfig(
            smallStep = intConfig(moneyConfig, "smallStep") ?: intConfig(amountConfig, "smallStep") ?: intConfig(amountConfig, "step") ?: 5,
            largeStep = intConfig(moneyConfig, "largeStep") ?: intConfig(amountConfig, "largeStep") ?: 50,
            minAmount = intConfig(moneyConfig, "min") ?: intConfig(amountConfig, "min") ?: 5,
            maxAmount = intConfig(moneyConfig, "max") ?: intConfig(amountConfig, "max") ?: 5000,
            currency = bankConfig["currency"]?.toString()?.takeIf { it.isNotBlank() } ?: "€",
        )
    }

    private fun bankStateKey(
        mode: String,
        selectedIndex: Int = 0,
        targetAccountId: UUID? = null,
        amount: Int = 50,
        message: String? = null,
    ): String =
        listOfNotNull(
            mode,
            "sel=$selectedIndex",
            targetAccountId?.let { "target=$it" },
            "amount=$amount",
            message?.let { "msg=$it" },
        ).joinToString("|")

    private fun moveToRuntimeNodeOrTeamSetup(session: NfcGameSession, nodeId: UUID) {
        val nextNode = flowNodeRepository.findById(nodeId).orElse(null)
        if (nextNode?.type in setOf("WAIT_PLAYER_CARD", "WAIT_ANY_CARD", "WAIT_GAME_CARD")) {
            session.status = SessionStatus.BUILDING_TEAMS
            session.currentStateKey = TEAM_SIZE_STATE
            sessionRepository.save(session)
            ensureSetupTeam(session)
        } else {
            session.currentStateKey = nextNode?.let { flowStateKey(it) } ?: nodeId.toString()
            sessionRepository.save(session)
        }
    }

    private fun handleTeamSizeInput(session: NfcGameSession, eventType: EventType, payload: Map<String, Any?>): StateMachineResult {
        val team = ensureSetupTeam(session)
        when (eventType) {
            EventType.TOUCH_NUMBER_SET -> {
                val rawValue = (payload["value"] as? Number)?.toInt() ?: payload["value"]?.toString()?.toIntOrNull()
                if (rawValue != null) {
                    team.targetSize = rawValue.coerceIn(1, 20)
                    team.status = "CONFIGURING"
                    teamRepository.save(team)
                    if (payload["commit"] == true || payload["commit"]?.toString()?.toBooleanStrictOrNull() == true) {
                        team.status = "OPEN"
                        teamRepository.save(team)
                        session.status = SessionStatus.BUILDING_TEAMS
                        session.currentStateKey = PLAYER_SCAN_STATE
                        sessionRepository.save(session)
                    }
                }
            }
            EventType.JOYSTICK_UP,
            EventType.JOYSTICK_RIGHT,
            -> {
                team.targetSize = (team.targetSize.coerceAtLeast(1) + 1).coerceAtMost(20)
                team.status = "CONFIGURING"
                teamRepository.save(team)
            }
            EventType.JOYSTICK_DOWN,
            EventType.JOYSTICK_LEFT,
            -> {
                team.targetSize = (team.targetSize.coerceAtLeast(1) - 1).coerceAtLeast(1)
                team.status = "CONFIGURING"
                teamRepository.save(team)
            }
            EventType.JOYSTICK_PRESS -> {
                team.targetSize = team.targetSize.coerceAtLeast(1)
                team.status = "OPEN"
                teamRepository.save(team)
                session.status = SessionStatus.BUILDING_TEAMS
                session.currentStateKey = PLAYER_SCAN_STATE
                sessionRepository.save(session)
            }
            EventType.TOUCH_CONFIRM -> {
                val rawValue = (payload["value"] as? Number)?.toInt() ?: payload["value"]?.toString()?.toIntOrNull()
                if (rawValue != null) {
                    team.targetSize = rawValue.coerceIn(1, 20)
                }
                team.targetSize = team.targetSize.coerceAtLeast(1)
                team.status = "OPEN"
                teamRepository.save(team)
                session.status = SessionStatus.BUILDING_TEAMS
                session.currentStateKey = PLAYER_SCAN_STATE
                sessionRepository.save(session)
            }
            else -> Unit
        }
        return StateMachineResult(session = session, screen = buildScreen(session), effects = listOf("BEEP_INFO"))
    }

    private fun confirmTeamSizeForFirstPlayerScan(session: NfcGameSession) {
        val team = ensureSetupTeam(session)
        team.targetSize = team.targetSize.coerceAtLeast(1)
        team.status = "OPEN"
        teamRepository.save(team)
        session.status = SessionStatus.BUILDING_TEAMS
        session.currentStateKey = PLAYER_SCAN_STATE
        sessionRepository.save(session)
    }

    private fun currentNodeKey(session: NfcGameSession): String =
        session.currentStateKey.substringBefore("|")

    private fun reloadSession(session: NfcGameSession): NfcGameSession =
        sessionRepository.findById(requireNotNull(session.id)).orElse(session)

    private fun currentSelectedIndex(session: NfcGameSession, optionCount: Int): Int {
        if (optionCount <= 0) return 0
        return (stateInt(session, "sel") ?: 0).coerceIn(0, optionCount - 1)
    }

    private fun currentNumberValue(session: NfcGameSession, node: NfcFlowNode): Int {
        val config = readMap(node.configJson)
        val min = intConfig(config, "min") ?: 1
        val max = intConfig(config, "max") ?: 99
        val fallback = intConfig(config, "value") ?: min
        return (stateInt(session, "num") ?: fallback).coerceIn(min, max)
    }

    private fun applyNumberPickerValue(session: NfcGameSession, node: NfcFlowNode) {
        val config = readMap(node.configJson)
        val storesRoundLimit = config["storeAs"]?.toString() == "roundLimit"
        if (!storesRoundLimit) return
        session.roundLimitType = RoundLimitType.ROUNDS
        session.roundLimit = currentNumberValue(session, node)
        sessionRepository.save(session)
    }

    private fun applyMenuSelection(session: NfcGameSession, node: NfcFlowNode, payload: Map<String, Any?> = emptyMap()) {
        val options = stringList(readMap(node.configJson)["options"])
        val selected = if (payload.isNotEmpty()) {
            payload["label"]?.toString()?.takeIf { it.isNotBlank() }
                ?: payload["value"]?.toString()?.takeIf { it.isNotBlank() }
                ?: options.getOrNull(currentSelectedIndex(session, options.size)).orEmpty()
        } else {
            options.getOrNull(currentSelectedIndex(session, options.size)).orEmpty()
        }
        if (selected.contains("Unbegrenzt", ignoreCase = true)) {
            session.roundLimitType = RoundLimitType.NONE
            session.roundLimit = null
            sessionRepository.save(session)
        } else if (selected.contains("Begrenzt", ignoreCase = true)) {
            session.roundLimitType = RoundLimitType.ROUNDS
            sessionRepository.save(session)
        }
    }

    private fun flowStateKey(
        node: NfcFlowNode?,
        selectedIndex: Int? = null,
        numberValue: Int? = null,
        context: Map<String, String> = emptyMap(),
    ): String {
        val nodeId = node?.id?.toString() ?: return ""
        val suffix = listOfNotNull(
            selectedIndex?.let { "sel=$it" },
            numberValue?.let { "num=$it" },
        ) + context.entries.map { "${it.key}=${it.value}" }
        return if (suffix.isEmpty()) nodeId else "$nodeId|${suffix.joinToString("|")}"
    }

    private fun flowContext(session: NfcGameSession): Map<String, String> =
        session.currentStateKey
            .split("|")
            .drop(1)
            .mapNotNull { token ->
                val parts = token.split("=", limit = 2)
                val key = parts.getOrNull(0)
                val value = parts.getOrNull(1)
                if (key != null && value != null && key !in setOf("sel", "num")) key to value else null
            }
            .toMap()

    private fun stateInt(session: NfcGameSession, key: String): Int? =
        stateString(session, key)?.toIntOrNull()

    private fun stateUuid(session: NfcGameSession, key: String): UUID? =
        stateString(session, key)?.let { runCatching { UUID.fromString(it) }.getOrNull() }

    private fun stateString(session: NfcGameSession, key: String): String? =
        session.currentStateKey
            .split("|")
            .drop(1)
            .firstNotNullOfOrNull { token ->
                val parts = token.split("=", limit = 2)
                parts.getOrNull(1)?.takeIf { parts.firstOrNull() == key }
            }

    private fun intConfig(config: Map<String, Any?>, key: String): Int? =
        (config[key] as? Number)?.toInt() ?: config[key]?.toString()?.toIntOrNull()

    private fun Int.floorMod(modulus: Int): Int =
        ((this % modulus) + modulus) % modulus

    private fun nextNodeByEvent(node: NfcFlowNode, eventType: String): UUID? =
        flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(requireNotNull(node.gameTemplateId))
            .firstOrNull { it.sourceNodeId == node.id && it.eventType == eventType }
            ?.targetNodeId

    private fun nextNodeByEventPreferringType(node: NfcFlowNode, eventType: String, preferredType: String): UUID? {
        val edges = flowEdgeRepository.findAllByGameTemplateIdOrderByPriorityAsc(requireNotNull(node.gameTemplateId))
            .filter { it.sourceNodeId == node.id && it.eventType == eventType }
        return edges.firstNotNullOfOrNull { edge ->
            edge.targetNodeId
                ?.let { flowNodeRepository.findById(it).orElse(null) }
                ?.takeIf { it.type == preferredType }
                ?.id
        } ?: edges.firstOrNull()?.targetNodeId
    }

    private fun readMap(json: String): Map<String, Any?> =
        objectMapper.readValue(json, object : TypeReference<Map<String, Any?>>() {})

    private fun stringList(value: Any?): List<String> =
        (value as? List<*>)?.mapNotNull { it?.toString() }.orEmpty()

    private fun findActiveSession(accountId: Long? = null) =
        if (accountId != null) {
            sessionRepository.findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, activeStatuses)
        } else {
            null
        }

    private fun findActiveSessionForGame(gameTemplateId: UUID, accountId: Long?) =
        if (accountId != null) {
            sessionRepository.findAllByAccountIdAndStatusInOrderByCreatedAtDesc(accountId, activeStatuses)
        } else {
            emptyList()
        }
            .firstOrNull { it.gameTemplateId == gameTemplateId }

    private fun message(title: String, subtitle: String) = StateMachineResult(
        session = null,
        screen = ScreenModel(ScreenType.MESSAGE, title, subtitle),
        effects = listOf("BEEP_INFO"),
    )

    private fun error(title: String, subtitle: String) = StateMachineResult(
        session = findActiveSession(),
        screen = ScreenModel(ScreenType.ERROR, title, subtitle),
        effects = listOf("BEEP_ERROR"),
        errors = listOf(subtitle),
    )

    companion object {
        private const val TEAM_SIZE_STATE = "setup-team-size"
        private const val PLAYER_SCAN_STATE = "scan-team-players"
        private const val BANK_STATE_PREFIX = "bank:"
        private const val BANK_TARGET_MODE = "bank:target"
        private const val BANK_AMOUNT_MODE = "bank:amount"
        private const val BANK_PAY_SCAN_MODE = "bank:pay-scan"
        private const val MAX_RUNTIME_STEPS = 50
    }
}

private class ArithmeticExpressionParser(
    private val source: String,
    private val resolveToken: (String) -> BigDecimal?,
) {
    private var index = 0

    fun parse(): BigDecimal? {
        val value = parseExpression() ?: return null
        skipWhitespace()
        return if (index == source.length) value else null
    }

    private fun parseExpression(): BigDecimal? {
        var value = parseTerm() ?: return null
        while (true) {
            skipWhitespace()
            value = when (peek()) {
                '+' -> {
                    index += 1
                    value + (parseTerm() ?: return null)
                }
                '-' -> {
                    index += 1
                    value - (parseTerm() ?: return null)
                }
                else -> return value
            }
        }
    }

    private fun parseTerm(): BigDecimal? {
        var value = parseFactor() ?: return null
        while (true) {
            skipWhitespace()
            value = when (peek()) {
                '*' -> {
                    index += 1
                    value * (parseFactor() ?: return null)
                }
                '/' -> {
                    index += 1
                    val divisor = parseFactor() ?: return null
                    if (divisor.compareTo(BigDecimal.ZERO) == 0) return null
                    value.divide(divisor, MathContext.DECIMAL64)
                }
                else -> return value
            }
        }
    }

    private fun parseFactor(): BigDecimal? {
        skipWhitespace()
        return when (peek()) {
            '+' -> {
                index += 1
                parseFactor()
            }
            '-' -> {
                index += 1
                parseFactor()?.negate()
            }
            '(' -> {
                index += 1
                val value = parseExpression() ?: return null
                skipWhitespace()
                if (peek() != ')') return null
                index += 1
                value
            }
            else -> parseValue()
        }
    }

    private fun parseValue(): BigDecimal? {
        skipWhitespace()
        if (peek() == '{') {
            index += 1
            val start = index
            while (index < source.length && source[index] != '}') index += 1
            if (index >= source.length) return null
            val token = source.substring(start, index)
            index += 1
            return resolveToken(token)
        }
        val start = index
        if (peek()?.isDigit() == true || peek() == '.') {
            while (index < source.length && (source[index].isDigit() || source[index] == '.')) index += 1
            return source.substring(start, index).toBigDecimalOrNull()
        }
        while (index < source.length && (source[index].isLetterOrDigit() || source[index] in setOf('_', '.'))) index += 1
        if (index == start) return null
        return resolveToken(source.substring(start, index))
    }

    private fun skipWhitespace() {
        while (index < source.length && source[index].isWhitespace()) index += 1
    }

    private fun peek(): Char? = source.getOrNull(index)
}
