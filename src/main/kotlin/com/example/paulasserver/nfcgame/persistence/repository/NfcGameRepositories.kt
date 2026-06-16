package com.example.paulasserver.nfcgame.persistence.repository

import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.GamePublicationStatus
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.persistence.entity.NfcAdminUser
import com.example.paulasserver.nfcgame.persistence.entity.NfcCard
import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowDefinition
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowEdge
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowNode
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowState
import com.example.paulasserver.nfcgame.persistence.entity.NfcFlowTransition
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameResult
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameSession
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameRating
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate
import com.example.paulasserver.nfcgame.persistence.entity.NfcMoneyTransaction
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayer
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayerStatsProjection
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionAccount
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionEvent
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionRound
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeam
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionTeamMember
import com.example.paulasserver.nfcgame.persistence.entity.NfcSessionValue
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import java.util.UUID

interface NfcAdminUserRepository : JpaRepository<NfcAdminUser, UUID> {
    fun findByUsername(username: String): NfcAdminUser?
}

interface NfcDeviceRepository : JpaRepository<NfcDevice, UUID> {
    fun findByName(name: String): NfcDevice?
    fun findByNameAndDeviceKey(name: String, deviceKey: String): NfcDevice?
    fun findByDeviceKey(deviceKey: String): NfcDevice?
    fun findByPairingCode(pairingCode: String): NfcDevice?
    fun findAllByAccountIdOrderByCreatedAtDesc(accountId: Long): List<NfcDevice>
    fun deleteAllByAccountId(accountId: Long)
}

interface NfcPlayerRepository : JpaRepository<NfcPlayer, UUID> {
    fun findAllByActiveTrueOrderByNameAsc(): List<NfcPlayer>
    fun findAllByAccountIdOrderByNameAsc(accountId: Long): List<NfcPlayer>
    fun findAllByAccountIdAndActiveTrueOrderByNameAsc(accountId: Long): List<NfcPlayer>
    fun deleteAllByAccountId(accountId: Long)
}

interface NfcCardRepository : JpaRepository<NfcCard, UUID> {
    fun findByCardUid(cardUid: String): NfcCard?
    fun findAllByStatusOrderByCreatedAtDesc(status: CardStatus): List<NfcCard>
    fun findAllByAccountIdOrderByCreatedAtDesc(accountId: Long): List<NfcCard>
    fun findAllByAccountIdAndStatusOrderByCreatedAtDesc(accountId: Long, status: CardStatus): List<NfcCard>
    fun findAllByAccountIdAndCardTypeAndStatusOrderByCreatedAtDesc(
        accountId: Long,
        cardType: CardType,
        status: CardStatus,
    ): List<NfcCard>
    fun findFirstByGameTemplateIdAndStatus(gameTemplateId: UUID, status: CardStatus): NfcCard?
    fun deleteAllByAccountId(accountId: Long)
}

interface NfcGameTemplateRepository : JpaRepository<NfcGameTemplate, UUID> {
    fun findAllByActiveTrueOrderByNameAsc(): List<NfcGameTemplate>
    fun findAllByOrderByUpdatedAtDesc(): List<NfcGameTemplate>
    fun findAllByAccountIdOrderByUpdatedAtDesc(accountId: Long): List<NfcGameTemplate>
    fun findAllByAccountIdAndActiveTrueOrderByNameAsc(accountId: Long): List<NfcGameTemplate>
    fun findAllByPublicationStatusAndActiveTrueOrderByUpdatedAtDesc(status: GamePublicationStatus): List<NfcGameTemplate>
    fun findAllByPublicationStatusAndActiveTrueOrderByNameAsc(status: GamePublicationStatus): List<NfcGameTemplate>
    fun findAllByPublicationStatusInAndActiveTrueOrderByUpdatedAtDesc(statuses: Collection<GamePublicationStatus>): List<NfcGameTemplate>
    fun deleteAllByAccountId(accountId: Long)
}

interface NfcGameRatingRepository : JpaRepository<NfcGameRating, UUID> {
    fun findByGameTemplateIdAndAccountId(gameTemplateId: UUID, accountId: Long): NfcGameRating?
    fun countByGameTemplateId(gameTemplateId: UUID): Long

    @Query("select avg(r.rating) from NfcGameRating r where r.gameTemplateId = :gameTemplateId")
    fun averageRatingByGameTemplateId(gameTemplateId: UUID): Double?
}

interface NfcFlowNodeRepository : JpaRepository<NfcFlowNode, UUID> {
    fun findAllByGameTemplateIdOrderBySortOrderAsc(gameTemplateId: UUID): List<NfcFlowNode>
    fun deleteAllByGameTemplateId(gameTemplateId: UUID)
}

interface NfcFlowEdgeRepository : JpaRepository<NfcFlowEdge, UUID> {
    fun findAllByGameTemplateIdOrderByPriorityAsc(gameTemplateId: UUID): List<NfcFlowEdge>
    fun deleteAllByGameTemplateId(gameTemplateId: UUID)
}

interface NfcFlowDefinitionRepository : JpaRepository<NfcFlowDefinition, UUID> {
    fun findFirstByGameTemplateIdAndActiveTrueOrderByVersionDesc(gameTemplateId: UUID): NfcFlowDefinition?
    fun findAllByGameTemplateIdOrderByVersionDesc(gameTemplateId: UUID): List<NfcFlowDefinition>
}

interface NfcFlowStateRepository : JpaRepository<NfcFlowState, UUID> {
    fun findAllByFlowDefinitionIdOrderBySortOrderAsc(flowDefinitionId: UUID): List<NfcFlowState>
    fun deleteAllByFlowDefinitionId(flowDefinitionId: UUID)
}

interface NfcFlowTransitionRepository : JpaRepository<NfcFlowTransition, UUID> {
    fun findAllByFlowDefinitionIdOrderBySortOrderAsc(flowDefinitionId: UUID): List<NfcFlowTransition>
    fun deleteAllByFlowDefinitionId(flowDefinitionId: UUID)
}

interface NfcGameSessionRepository : JpaRepository<NfcGameSession, UUID> {
    fun findFirstByStatusInOrderByCreatedAtDesc(statuses: Collection<SessionStatus>): NfcGameSession?
    fun findFirstByAccountIdAndStatusInOrderByCreatedAtDesc(accountId: Long, statuses: Collection<SessionStatus>): NfcGameSession?
    fun findAllByStatusInOrderByCreatedAtDesc(statuses: Collection<SessionStatus>): List<NfcGameSession>
    fun findAllByAccountIdAndStatusInOrderByCreatedAtDesc(accountId: Long, statuses: Collection<SessionStatus>): List<NfcGameSession>
    fun findAllByOrderByCreatedAtDesc(): List<NfcGameSession>
    fun findAllByAccountIdOrderByCreatedAtDesc(accountId: Long): List<NfcGameSession>
    fun deleteAllByAccountId(accountId: Long)
}

interface NfcSessionTeamRepository : JpaRepository<NfcSessionTeam, UUID> {
    fun findAllBySessionIdOrderByTeamOrderAsc(sessionId: UUID): List<NfcSessionTeam>
    fun deleteAllBySessionId(sessionId: UUID)
}

interface NfcSessionTeamMemberRepository : JpaRepository<NfcSessionTeamMember, UUID> {
    fun findAllBySessionTeamId(sessionTeamId: UUID): List<NfcSessionTeamMember>
    fun existsBySessionTeamIdAndPlayerId(sessionTeamId: UUID, playerId: UUID): Boolean
    fun deleteAllBySessionTeamIdIn(sessionTeamIds: Collection<UUID>)

    @Query(
        """
        select m from NfcSessionTeamMember m
        where m.playerId = :playerId
        and m.sessionTeamId in :teamIds
        """,
    )
    fun findByPlayerIdAndSessionTeamIdIn(playerId: UUID, teamIds: Collection<UUID>): NfcSessionTeamMember?
}

interface NfcSessionRoundRepository : JpaRepository<NfcSessionRound, UUID> {
    fun findAllBySessionIdOrderByRoundNumberAsc(sessionId: UUID): List<NfcSessionRound>
    fun countBySessionId(sessionId: UUID): Long
    fun countByWinningTeamId(teamId: UUID): Long
    fun deleteAllBySessionId(sessionId: UUID)
}

interface NfcSessionAccountRepository : JpaRepository<NfcSessionAccount, UUID> {
    fun findAllBySessionId(sessionId: UUID): List<NfcSessionAccount>
    fun deleteAllBySessionId(sessionId: UUID)
}

interface NfcSessionValueRepository : JpaRepository<NfcSessionValue, UUID> {
    fun findAllBySessionId(sessionId: UUID): List<NfcSessionValue>
    fun findAllBySessionIdAndValueKey(sessionId: UUID, valueKey: String): List<NfcSessionValue>
    fun findBySessionIdAndOwnerTypeAndOwnerIdAndValueKey(sessionId: UUID, ownerType: OwnerType, ownerId: UUID, valueKey: String): NfcSessionValue?
    fun deleteAllBySessionId(sessionId: UUID)
}

interface NfcMoneyTransactionRepository : JpaRepository<NfcMoneyTransaction, UUID> {
    fun findAllBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<NfcMoneyTransaction>
    fun deleteAllBySessionId(sessionId: UUID)
}

interface NfcGameResultRepository : JpaRepository<NfcGameResult, UUID> {
    fun findBySessionId(sessionId: UUID): NfcGameResult?
    fun deleteBySessionId(sessionId: UUID)
}

interface NfcSessionEventRepository : JpaRepository<NfcSessionEvent, UUID> {
    fun findAllBySessionIdOrderByCreatedAtAsc(sessionId: UUID): List<NfcSessionEvent>
    fun deleteAllBySessionId(sessionId: UUID)
}

interface NfcPlayerStatsProjectionRepository : JpaRepository<NfcPlayerStatsProjection, UUID> {
    fun findAllByOrderByTotalPointsDescGamesWonDescWinRateDesc(): List<NfcPlayerStatsProjection>
}
