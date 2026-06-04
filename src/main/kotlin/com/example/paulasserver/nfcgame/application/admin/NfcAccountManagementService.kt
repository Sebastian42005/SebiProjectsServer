package com.example.paulasserver.nfcgame.application.admin

import com.example.paulasserver.entities.AppUser
import com.example.paulasserver.nfcgame.api.dto.AdminAccountSummaryResponse
import com.example.paulasserver.nfcgame.application.statistics.NfcStatisticsService
import com.example.paulasserver.nfcgame.persistence.repository.NfcCardRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcDeviceRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowDefinitionRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowEdgeRepository
import com.example.paulasserver.nfcgame.persistence.repository.NfcFlowNodeRepository
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
import com.example.paulasserver.repositories.AppUserRepository
import com.example.paulasserver.security.AuthenticatedUser
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

private const val ACCOUNT_MANAGER_USERNAME = "administrator4"

@Service
class NfcAccountManagementService(
    private val appUserRepository: AppUserRepository,
    private val playerRepository: NfcPlayerRepository,
    private val statsRepository: NfcPlayerStatsProjectionRepository,
    private val cardRepository: NfcCardRepository,
    private val deviceRepository: NfcDeviceRepository,
    private val gameTemplateRepository: NfcGameTemplateRepository,
    private val flowNodeRepository: NfcFlowNodeRepository,
    private val flowEdgeRepository: NfcFlowEdgeRepository,
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
) {
    fun listAccounts(currentUser: AuthenticatedUser?): List<AdminAccountSummaryResponse> {
        requireAccountManager(currentUser)
        return appUserRepository.findAllByOrderByIdAsc().map(::toSummary)
    }

    @Transactional
    fun deleteAccount(accountId: Long, currentUser: AuthenticatedUser?) {
        val manager = requireAccountManager(currentUser)
        if (accountId == manager.id) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "You cannot delete the account you are currently using")
        }

        val user = appUserRepository.findById(accountId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found")
        }

        deleteSessions(accountId)
        deleteCardsDevicesGamesAndPlayers(accountId)
        appUserRepository.delete(user)
        statisticsService.rebuildFromSessions()
    }

    private fun deleteSessions(accountId: Long) {
        val sessions = sessionRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId)

        for (session in sessions) {
            val sessionId = session.id ?: continue
            val teamIds = sessionTeamRepository.findAllBySessionIdOrderByTeamOrderAsc(sessionId).mapNotNull { it.id }

            sessionEventRepository.deleteAllBySessionId(sessionId)
            moneyTransactionRepository.deleteAllBySessionId(sessionId)
            gameResultRepository.deleteBySessionId(sessionId)
            sessionRoundRepository.deleteAllBySessionId(sessionId)
            sessionAccountRepository.deleteAllBySessionId(sessionId)

            if (teamIds.isNotEmpty()) {
                sessionTeamMemberRepository.deleteAllBySessionTeamIdIn(teamIds)
            }

            sessionTeamRepository.deleteAllBySessionId(sessionId)
        }

        sessionRepository.deleteAllByAccountId(accountId)
    }

    private fun deleteCardsDevicesGamesAndPlayers(accountId: Long) {
        val players = playerRepository.findAllByAccountIdOrderByNameAsc(accountId)
        val games = gameTemplateRepository.findAllByAccountIdOrderByUpdatedAtDesc(accountId)

        cardRepository.deleteAllByAccountId(accountId)
        deviceRepository.deleteAllByAccountId(accountId)

        for (game in games) {
            val gameId = game.id ?: continue
            flowNodeRepository.deleteAllByGameTemplateId(gameId)
            flowEdgeRepository.deleteAllByGameTemplateId(gameId)

            val definitions = flowDefinitionRepository.findAllByGameTemplateIdOrderByVersionDesc(gameId)
            for (definition in definitions) {
                val definitionId = definition.id ?: continue
                flowStateRepository.deleteAllByFlowDefinitionId(definitionId)
                flowTransitionRepository.deleteAllByFlowDefinitionId(definitionId)
            }
            flowDefinitionRepository.deleteAll(definitions)
        }

        gameTemplateRepository.deleteAllByAccountId(accountId)

        for (player in players) {
            player.id?.let { statsRepository.deleteById(it) }
        }
        playerRepository.deleteAllByAccountId(accountId)
    }

    private fun toSummary(user: AppUser): AdminAccountSummaryResponse {
        val accountId = requireNotNull(user.id)
        return AdminAccountSummaryResponse(
            id = accountId,
            username = user.username,
            role = user.role.name,
            playerCount = playerRepository.findAllByAccountIdOrderByNameAsc(accountId).size,
            cardCount = cardRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).size,
            deviceCount = deviceRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).size,
            gameCount = gameTemplateRepository.findAllByAccountIdOrderByUpdatedAtDesc(accountId).size,
            sessionCount = sessionRepository.findAllByAccountIdOrderByCreatedAtDesc(accountId).size,
        )
    }

    private fun requireAccountManager(user: AuthenticatedUser?): AuthenticatedUser {
        val currentUser = user ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")
        if (currentUser.username != ACCOUNT_MANAGER_USERNAME) {
            throw ResponseStatusException(HttpStatus.FORBIDDEN, "Only administrator4 can manage accounts")
        }
        return currentUser
    }
}
