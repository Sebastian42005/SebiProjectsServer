package com.example.paulasserver.nfcgame.persistence.entity

import com.example.paulasserver.nfcgame.domain.AdminRole
import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.EventType
import com.example.paulasserver.nfcgame.domain.GamePublicationStatus
import com.example.paulasserver.nfcgame.domain.OwnerType
import com.example.paulasserver.nfcgame.domain.RoundLimitType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.domain.WinRuleType
import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Index
import jakarta.persistence.MappedSuperclass
import jakarta.persistence.PrePersist
import jakarta.persistence.PreUpdate
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import jakarta.validation.constraints.AssertTrue
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@MappedSuperclass
abstract class NfcUuidEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    var id: UUID? = null
}

@Entity
@Table(name = "nfc_admin_user", indexes = [Index(name = "idx_nfc_admin_user_username", columnList = "username")])
class NfcAdminUser : NfcUuidEntity() {
    @Column(nullable = false, unique = true)
    var username: String = ""

    @Column(nullable = false)
    var passwordHash: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: AdminRole = AdminRole.ROLE_ADMIN

    @Column(nullable = false)
    var active: Boolean = true

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(
    name = "nfc_device",
    indexes = [
        Index(name = "idx_nfc_device_name", columnList = "name"),
        Index(name = "idx_nfc_device_account", columnList = "account_id"),
    ],
)
class NfcDevice : NfcUuidEntity() {
    @Column(nullable = false, unique = true)
    var name: String = ""

    @Column(nullable = false)
    var deviceKey: String = ""

    @Column(name = "pairing_code", unique = true)
    var pairingCode: String? = null

    @Column(nullable = false)
    var active: Boolean = true

    @Column(name = "account_id")
    var accountId: Long? = null

    var lastSeenAt: Instant? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(
    name = "nfc_player",
    indexes = [
        Index(name = "idx_nfc_player_active", columnList = "active"),
        Index(name = "idx_nfc_player_account", columnList = "account_id"),
    ],
)
class NfcPlayer : NfcUuidEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "text")
    var description: String? = null

    var imageUrl: String? = null

    @Column(columnDefinition = "bytea")
    var imageContent: ByteArray? = null

    var imageContentType: String? = null

    var imageFileName: String? = null

    @Column(nullable = false)
    var active: Boolean = true

    @Column(name = "account_id")
    var accountId: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(
    name = "nfc_game_template",
    indexes = [
        Index(name = "idx_nfc_game_template_active", columnList = "active"),
        Index(name = "idx_nfc_game_template_account", columnList = "account_id"),
    ],
)
class NfcGameTemplate : NfcUuidEntity() {
    @Column(nullable = false)
    var name: String = ""

    @Column(columnDefinition = "text")
    var description: String? = null

    var imageUrl: String? = null

    @Column(columnDefinition = "bytea")
    var imageContent: ByteArray? = null

    var imageContentType: String? = null

    var imageFileName: String? = null

    @Column(nullable = false)
    var active: Boolean = true

    @Column(name = "account_id")
    var accountId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var publicationStatus: GamePublicationStatus = GamePublicationStatus.DRAFT

    @Column(nullable = false)
    var flowVersion: Int = 1

    @Column(name = "start_node_id")
    var startNodeId: UUID? = null

    @Column(nullable = false)
    var allowTeams: Boolean = true

    @Column(nullable = false)
    var minTeamSize: Int = 1

    @Column(nullable = false)
    var maxTeamSize: Int = 2

    @Column(nullable = false)
    var supportsRoundLimit: Boolean = false

    @Column(nullable = false)
    var economyEnabled: Boolean = false

    @Column(nullable = false, precision = 14, scale = 2)
    var startCapital: BigDecimal = BigDecimal.ZERO

    @Column(nullable = false, precision = 14, scale = 2)
    var smallStep: BigDecimal = BigDecimal.ONE

    @Column(nullable = false, precision = 14, scale = 2)
    var largeStep: BigDecimal = BigDecimal.TEN

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var winRuleType: WinRuleType = WinRuleType.FIRST_TO_WIN

    @Column(nullable = false)
    var globalWinnerPoints: Long = 5

    var globalSecondPlacePoints: Long? = null

    var globalThirdPlacePoints: Long? = null

    var dashboardMetricSource: String? = "points"

    var dashboardMetricLabel: String? = "Punkte"

    var dashboardMetricSuffix: String? = null

    var dashboardMetricSortDirection: String? = "DESC"

    var dashboardMetricDisplayType: String? = "RACE_BAR"

    var dashboardMetricMaxSource: String? = null

    var dashboardStatusSource: String? = "currentRound"

    var dashboardStatusLabel: String? = "Runde"

    var dashboardStatusSuffix: String? = null

    var dashboardStatusMaxSource: String? = "roundLimit"

    var dashboardStatusDisplayType: String? = "PROGRESS_BAR"

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(
    name = "nfc_flow_node",
    indexes = [
        Index(name = "idx_nfc_flow_node_game_template", columnList = "game_template_id"),
        Index(name = "idx_nfc_flow_node_type", columnList = "type"),
    ],
)
class NfcFlowNode : NfcUuidEntity() {
    @Column(name = "game_template_id", nullable = false)
    var gameTemplateId: UUID? = null

    @Column(nullable = false)
    var type: String = ""

    @Column(nullable = false)
    var title: String = ""

    @Column(nullable = false)
    var x: Int = 0

    @Column(nullable = false)
    var y: Int = 0

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var configJson: String = "{}"

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var uiConfigJson: String = "{}"

    @Column(nullable = false)
    var sortOrder: Int = 0
}

@Entity
@Table(
    name = "nfc_flow_edge",
    indexes = [
        Index(name = "idx_nfc_flow_edge_game_template", columnList = "game_template_id"),
        Index(name = "idx_nfc_flow_edge_source", columnList = "source_node_id"),
        Index(name = "idx_nfc_flow_edge_target", columnList = "target_node_id"),
    ],
)
class NfcFlowEdge : NfcUuidEntity() {
    @Column(name = "game_template_id", nullable = false)
    var gameTemplateId: UUID? = null

    @Column(name = "source_node_id", nullable = false)
    var sourceNodeId: UUID? = null

    @Column(name = "target_node_id", nullable = false)
    var targetNodeId: UUID? = null

    @Column(nullable = false)
    var eventType: String = "NEXT"

    var conditionType: String? = null

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var conditionConfigJson: String = "{}"

    @Column(nullable = false)
    var priority: Int = 0
}

@Entity
@Table(
    name = "nfc_card",
    uniqueConstraints = [UniqueConstraint(name = "uk_nfc_card_uid", columnNames = ["card_uid"])],
    indexes = [
        Index(name = "idx_nfc_card_player", columnList = "player_id"),
        Index(name = "idx_nfc_card_game_template", columnList = "game_template_id"),
        Index(name = "idx_nfc_card_account", columnList = "account_id"),
    ],
)
class NfcCard : NfcUuidEntity() {
    @Column(name = "card_uid", nullable = false)
    var cardUid: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var cardType: CardType = CardType.UNKNOWN

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: CardStatus = CardStatus.UNASSIGNED

    @Column(name = "player_id")
    var playerId: UUID? = null

    @Column(name = "game_template_id")
    var gameTemplateId: UUID? = null

    @Column(name = "account_id")
    var accountId: Long? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()

    @AssertTrue(message = "Assigned cards need exactly one matching target")
    fun hasValidAssignment(): Boolean {
        if (status != CardStatus.ASSIGNED) {
            return playerId == null && gameTemplateId == null
        }

        return when (cardType) {
            CardType.PLAYER -> playerId != null && gameTemplateId == null
            CardType.GAME -> gameTemplateId != null && playerId == null
            CardType.UNKNOWN -> false
        }
    }

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(
    name = "nfc_flow_definition",
    indexes = [Index(name = "idx_nfc_flow_definition_game_template", columnList = "game_template_id")],
)
class NfcFlowDefinition : NfcUuidEntity() {
    @Column(name = "game_template_id", nullable = false)
    var gameTemplateId: UUID? = null

    @Column(nullable = false)
    var version: Int = 1

    @Column(nullable = false)
    var active: Boolean = true

    @Column(nullable = false)
    var startStateKey: String = "lobby"

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(
    name = "nfc_flow_state",
    uniqueConstraints = [UniqueConstraint(name = "uk_nfc_flow_state_key", columnNames = ["flow_definition_id", "state_key"])],
)
class NfcFlowState : NfcUuidEntity() {
    @Column(name = "flow_definition_id", nullable = false)
    var flowDefinitionId: UUID? = null

    @Column(name = "state_key", nullable = false)
    var stateKey: String = ""

    @Column(nullable = false)
    var stateType: String = ""

    @Column(nullable = false)
    var title: String = ""

    var subtitle: String? = null

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var configJson: String = "{}"

    @Column(nullable = false)
    var sortOrder: Int = 0
}

@Entity
@Table(name = "nfc_flow_transition")
class NfcFlowTransition : NfcUuidEntity() {
    @Column(name = "flow_definition_id", nullable = false)
    var flowDefinitionId: UUID? = null

    @Column(nullable = false)
    var fromStateKey: String = ""

    @Column(nullable = false)
    var eventType: String = ""

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var conditionJson: String = "{}"

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var actionJson: String = "{}"

    @Column(nullable = false)
    var toStateKey: String = ""

    @Column(nullable = false)
    var sortOrder: Int = 0
}

@Entity
@Table(
    name = "nfc_game_session",
    indexes = [
        Index(name = "idx_nfc_game_session_status", columnList = "status"),
        Index(name = "idx_nfc_game_session_account_status", columnList = "account_id,status"),
    ],
)
class NfcGameSession : NfcUuidEntity() {
    @Column(name = "game_template_id", nullable = false)
    var gameTemplateId: UUID? = null

    @Column(name = "device_id", nullable = false)
    var deviceId: UUID? = null

    @Column(name = "account_id")
    var accountId: Long? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var status: SessionStatus = SessionStatus.LOBBY

    @Column(nullable = false, columnDefinition = "text")
    var currentStateKey: String = "lobby"

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var roundLimitType: RoundLimitType = RoundLimitType.NONE

    var roundLimit: Int? = null

    @Column(nullable = false)
    var currentRoundNumber: Int = 0

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    var startedAt: Instant? = null

    var endedAt: Instant? = null
}

@Entity
@Table(name = "nfc_session_team", indexes = [Index(name = "idx_nfc_session_team_session", columnList = "session_id")])
class NfcSessionTeam : NfcUuidEntity() {
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID? = null

    @Column(nullable = false)
    var name: String = ""

    @Column(nullable = false)
    var teamOrder: Int = 1

    @Column(nullable = false)
    var targetSize: Int = 1

    @Column(nullable = false)
    var status: String = "OPEN"

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(
    name = "nfc_session_team_member",
    uniqueConstraints = [UniqueConstraint(name = "uk_nfc_session_team_member_team_player", columnNames = ["session_team_id", "player_id"])],
)
class NfcSessionTeamMember : NfcUuidEntity() {
    @Column(name = "session_team_id", nullable = false)
    var sessionTeamId: UUID? = null

    @Column(name = "player_id", nullable = false)
    var playerId: UUID? = null

    @Column(nullable = false, updatable = false)
    var joinedAt: Instant = Instant.now()
}

@Entity
@Table(name = "nfc_session_round", indexes = [Index(name = "idx_nfc_session_round_session", columnList = "session_id")])
class NfcSessionRound : NfcUuidEntity() {
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID? = null

    @Column(nullable = false)
    var roundNumber: Int = 1

    @Column(name = "winning_team_id")
    var winningTeamId: UUID? = null

    @Column(nullable = false)
    var awardedPointsPerMember: Int = 1

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "nfc_session_account")
class NfcSessionAccount : NfcUuidEntity() {
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var ownerType: OwnerType = OwnerType.TEAM

    @Column(name = "team_id")
    var teamId: UUID? = null

    @Column(nullable = false, precision = 14, scale = 2)
    var balance: BigDecimal = BigDecimal.ZERO

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(
    name = "nfc_session_value",
    uniqueConstraints = [UniqueConstraint(name = "uk_nfc_session_value_owner_key", columnNames = ["session_id", "owner_type", "owner_id", "value_key"])],
    indexes = [
        Index(name = "idx_nfc_session_value_session", columnList = "session_id"),
        Index(name = "idx_nfc_session_value_key", columnList = "value_key"),
    ],
)
class NfcSessionValue : NfcUuidEntity() {
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    var ownerType: OwnerType = OwnerType.TEAM

    @Column(name = "owner_id", nullable = false)
    var ownerId: UUID? = null

    @Column(name = "value_key", nullable = false)
    var valueKey: String = "points"

    @Column(nullable = false, precision = 14, scale = 2)
    var value: BigDecimal = BigDecimal.ZERO

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()

    @PreUpdate
    fun touch() {
        updatedAt = Instant.now()
    }
}

@Entity
@Table(name = "nfc_money_transaction")
class NfcMoneyTransaction : NfcUuidEntity() {
    @Column(name = "session_id", nullable = false)
    var sessionId: UUID? = null

    @Column(name = "from_account_id", nullable = false)
    var fromAccountId: UUID? = null

    @Column(name = "to_account_id", nullable = false)
    var toAccountId: UUID? = null

    @Column(nullable = false, precision = 14, scale = 2)
    var amount: BigDecimal = BigDecimal.ZERO

    @Column(name = "initiated_by_player_id")
    var initiatedByPlayerId: UUID? = null

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "nfc_game_result")
class NfcGameResult : NfcUuidEntity() {
    @Column(name = "session_id", nullable = false, unique = true)
    var sessionId: UUID? = null

    @Column(name = "winning_team_id")
    var winningTeamId: UUID? = null

    @Column(nullable = false)
    var endReason: String = ""

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()
}

@Entity
@Table(name = "nfc_session_event", indexes = [Index(name = "idx_nfc_session_event_session", columnList = "session_id")])
class NfcSessionEvent : NfcUuidEntity() {
    @Column(name = "session_id")
    var sessionId: UUID? = null

    @Column(name = "device_id", nullable = false)
    var deviceId: UUID? = null

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var eventType: EventType = EventType.JOYSTICK_PRESS

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    var payloadJson: String = "{}"

    @Column(nullable = false, updatable = false)
    var createdAt: Instant = Instant.now()

    @PrePersist
    fun onCreate() {
        if (createdAt.epochSecond == 0L) {
            createdAt = Instant.now()
        }
    }
}

@Entity
@Table(name = "nfc_player_stats_projection")
class NfcPlayerStatsProjection {
    @Id
    @Column(name = "player_id", nullable = false, updatable = false)
    var playerId: UUID? = null

    @Column(nullable = false)
    var gamesPlayed: Long = 0

    @Column(nullable = false)
    var gamesWon: Long = 0

    @Column(nullable = false)
    var roundsWon: Long = 0

    @Column(nullable = false)
    var totalPoints: Long = 0

    @Column(nullable = false)
    var winRate: Double = 0.0

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}
