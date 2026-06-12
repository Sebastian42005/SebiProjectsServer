package com.example.paulasserver.nfcgame.api.dto

import com.example.paulasserver.nfcgame.domain.CardStatus
import com.example.paulasserver.nfcgame.domain.CardType
import com.example.paulasserver.nfcgame.domain.EventType
import com.example.paulasserver.nfcgame.domain.GamePublicationStatus
import com.example.paulasserver.nfcgame.domain.RoundLimitType
import com.example.paulasserver.nfcgame.domain.ScreenType
import com.example.paulasserver.nfcgame.domain.SessionStatus
import com.example.paulasserver.nfcgame.domain.WinRuleType
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Positive
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

data class NfcLoginRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
)

data class NfcLoginResponse(
    val authenticated: Boolean,
    val username: String? = null,
    val role: String? = null,
    val token: String? = null,
)

data class AdminAccountSummaryResponse(
    val id: Long,
    val username: String,
    val role: String,
    val playerCount: Int,
    val cardCount: Int,
    val deviceCount: Int,
    val gameCount: Int,
    val sessionCount: Int,
)

data class NfcRegisterRequest(
    @field:NotBlank val username: String,
    @field:NotBlank val password: String,
)

data class DeviceClaimRequest(
    val pairingCode: String? = null,
    val deviceKey: String? = null,
)

data class PlayerRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val active: Boolean = true,
)

data class PlayerActiveRequest(
    val active: Boolean,
)

data class PlayerPointsRequest(
    val totalPoints: Long,
)

data class PlayerResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val active: Boolean,
    val totalPoints: Long = 0,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class DeviceRequest(
    @field:NotBlank val name: String,
    @field:NotBlank val deviceKey: String,
    val active: Boolean = true,
)

data class DeviceResponse(
    val id: UUID,
    val name: String,
    val active: Boolean,
    val linked: Boolean = false,
    val lastSeenAt: Instant?,
    val createdAt: Instant,
)

data class DeviceProvisioningResponse(
    val id: UUID,
    val name: String,
    val active: Boolean,
    val linked: Boolean,
    val pairingCode: String,
    val lastSeenAt: Instant?,
    val createdAt: Instant,
)

data class DeviceFirmwareManifestResponse(
    val updateAvailable: Boolean,
    val currentVersion: String,
    val latestVersion: String,
    val firmwareUrl: String? = null,
    val size: Long? = null,
    val md5: String? = null,
    val force: Boolean = false,
    val releaseNotes: String? = null,
)

data class GameTemplateRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val active: Boolean = true,
    val allowTeams: Boolean = true,
    val minTeamSize: Int = 1,
    val maxTeamSize: Int = 2,
    val supportsRoundLimit: Boolean = false,
    val economyEnabled: Boolean = false,
    val startCapital: BigDecimal = BigDecimal.ZERO,
    val smallStep: BigDecimal = BigDecimal.ONE,
    val largeStep: BigDecimal = BigDecimal.TEN,
    val winRuleType: WinRuleType = WinRuleType.FIRST_TO_WIN,
    val globalWinnerPoints: Long = 5,
    val globalSecondPlacePoints: Long? = null,
    val globalThirdPlacePoints: Long? = null,
    val dashboardMetricSource: String? = "points",
    val dashboardMetricLabel: String? = "Punkte",
    val dashboardMetricSuffix: String? = null,
    val dashboardMetricSortDirection: String? = "DESC",
    val dashboardMetricDisplayType: String? = "RACE_BAR",
    val dashboardMetricMaxSource: String? = null,
    val dashboardStatusSource: String? = "currentRound",
    val dashboardStatusLabel: String? = "Runde",
    val dashboardStatusSuffix: String? = null,
    val dashboardStatusMaxSource: String? = "roundLimit",
    val dashboardStatusDisplayType: String? = "PROGRESS_BAR",
)

data class GameBasicRequest(
    @field:NotBlank val name: String,
    val description: String? = null,
    val imageUrl: String? = null,
    val cardUid: String? = null,
    val active: Boolean = true,
    val globalWinnerPoints: Long = 5,
    val globalSecondPlacePoints: Long? = null,
    val globalThirdPlacePoints: Long? = null,
    val dashboardMetricSource: String? = "points",
    val dashboardMetricLabel: String? = "Punkte",
    val dashboardMetricSuffix: String? = null,
    val dashboardMetricSortDirection: String? = "DESC",
    val dashboardMetricDisplayType: String? = "RACE_BAR",
    val dashboardMetricMaxSource: String? = null,
    val dashboardStatusSource: String? = "currentRound",
    val dashboardStatusLabel: String? = "Runde",
    val dashboardStatusSuffix: String? = null,
    val dashboardStatusMaxSource: String? = "roundLimit",
    val dashboardStatusDisplayType: String? = "PROGRESS_BAR",
)

data class GameTemplateResponse(
    val id: UUID,
    val name: String,
    val description: String?,
    val imageUrl: String?,
    val active: Boolean,
    val publicationStatus: GamePublicationStatus = GamePublicationStatus.DRAFT,
    val version: Int = 1,
    val startNodeId: UUID? = null,
    val cardUid: String? = null,
    val allowTeams: Boolean,
    val minTeamSize: Int,
    val maxTeamSize: Int,
    val supportsRoundLimit: Boolean,
    val economyEnabled: Boolean,
    val startCapital: BigDecimal,
    val smallStep: BigDecimal,
    val largeStep: BigDecimal,
    val winRuleType: WinRuleType,
    val globalWinnerPoints: Long,
    val globalSecondPlacePoints: Long?,
    val globalThirdPlacePoints: Long?,
    val dashboardMetricSource: String?,
    val dashboardMetricLabel: String?,
    val dashboardMetricSuffix: String?,
    val dashboardMetricSortDirection: String?,
    val dashboardMetricDisplayType: String?,
    val dashboardMetricMaxSource: String?,
    val dashboardStatusSource: String?,
    val dashboardStatusLabel: String?,
    val dashboardStatusSuffix: String?,
    val dashboardStatusMaxSource: String?,
    val dashboardStatusDisplayType: String?,
    val ownedByCurrentAccount: Boolean = true,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FlowNodeRequest(
    val id: UUID? = null,
    @field:NotBlank val type: String,
    @field:NotBlank val title: String,
    val x: Int,
    val y: Int,
    val config: Map<String, Any?> = emptyMap(),
    val uiConfig: Map<String, Any?> = emptyMap(),
    val order: Int = 0,
)

data class FlowNodeResponse(
    val id: UUID,
    val type: String,
    val title: String,
    val x: Int,
    val y: Int,
    val config: Map<String, Any?>,
    val uiConfig: Map<String, Any?>,
    val order: Int,
)

data class FlowEdgeRequest(
    val id: UUID? = null,
    val sourceNodeId: UUID,
    val targetNodeId: UUID,
    @field:NotBlank val eventType: String,
    val conditionType: String? = null,
    val conditionConfig: Map<String, Any?> = emptyMap(),
    val priority: Int = 0,
)

data class FlowEdgeResponse(
    val id: UUID,
    val sourceNodeId: UUID,
    val targetNodeId: UUID,
    val eventType: String,
    val conditionType: String?,
    val conditionConfig: Map<String, Any?>,
    val priority: Int,
)

data class GameFlowRequest(
    val startNodeId: UUID? = null,
    val nodes: List<FlowNodeRequest> = emptyList(),
    val edges: List<FlowEdgeRequest> = emptyList(),
)

data class GameFlowResponse(
    val gameTemplateId: UUID,
    val startNodeId: UUID?,
    val nodes: List<FlowNodeResponse>,
    val edges: List<FlowEdgeResponse>,
)

data class FlowValidationIssue(
    val severity: String,
    val message: String,
    val nodeId: UUID? = null,
    val edgeId: UUID? = null,
)

data class FlowValidationResponse(
    val valid: Boolean,
    val issues: List<FlowValidationIssue>,
)

data class CardAssignRequest(
    @field:NotBlank val cardUid: String,
    val cardType: CardType,
    val playerId: UUID? = null,
    val gameTemplateId: UUID? = null,
)

data class CardResponse(
    val id: UUID,
    val cardUid: String,
    val cardType: CardType,
    val status: CardStatus,
    val playerId: UUID?,
    val gameTemplateId: UUID?,
    val createdAt: Instant,
    val updatedAt: Instant,
)

data class FlowDefinitionRequest(
    val version: Int = 1,
    val active: Boolean = true,
    @field:NotBlank val startStateKey: String = "lobby",
    val states: List<FlowStateRequest> = emptyList(),
    val transitions: List<FlowTransitionRequest> = emptyList(),
)

data class FlowStateRequest(
    @field:NotBlank val stateKey: String,
    @field:NotBlank val stateType: String,
    @field:NotBlank val title: String,
    val subtitle: String? = null,
    val config: Map<String, Any?> = emptyMap(),
    val sortOrder: Int = 0,
)

data class FlowTransitionRequest(
    @field:NotBlank val fromStateKey: String,
    @field:NotBlank val eventType: String,
    val condition: Map<String, Any?> = emptyMap(),
    val action: Map<String, Any?> = emptyMap(),
    @field:NotBlank val toStateKey: String,
    val sortOrder: Int = 0,
)

data class FlowDefinitionResponse(
    val id: UUID,
    val gameTemplateId: UUID,
    val version: Int,
    val active: Boolean,
    val startStateKey: String,
    val createdAt: Instant,
    val states: List<FlowStateResponse>,
    val transitions: List<FlowTransitionResponse>,
)

data class FlowStateResponse(
    val id: UUID,
    val stateKey: String,
    val stateType: String,
    val title: String,
    val subtitle: String?,
    val config: Map<String, Any?>,
    val sortOrder: Int,
)

data class FlowTransitionResponse(
    val id: UUID,
    val fromStateKey: String,
    val eventType: String,
    val condition: Map<String, Any?>,
    val action: Map<String, Any?>,
    val toStateKey: String,
    val sortOrder: Int,
)

data class DeviceEventRequest(
    @field:NotBlank val deviceId: String,
    @field:NotBlank val deviceKey: String,
    val sessionId: String? = null,
    val currentStateKey: String? = null,
    val eventType: EventType,
    val cardUid: String? = null,
    val payload: Map<String, Any?> = emptyMap(),
    val occurredAt: Instant? = null,
)

data class DeviceEventResponse(
    val sessionId: UUID?,
    val status: SessionStatus?,
    val currentStateKey: String?,
    val screen: ScreenModel,
    val effects: List<String> = emptyList(),
    val errors: List<String> = emptyList(),
    val scannedCardType: CardType? = null,
    val scannedPlayerName: String? = null,
    val uiHints: DeviceUiHints = DeviceUiHints(),
)

data class ScreenModel(
    val screenType: ScreenType,
    val title: String,
    val subtitle: String? = null,
    val lines: List<String> = emptyList(),
    val menuItems: List<MenuItem> = emptyList(),
    val selectedIndex: Int? = null,
    val numberValue: Int? = null,
    val context: Map<String, Any?> = emptyMap(),
)

data class MenuItem(
    val label: String,
    val value: String,
)

data class DeviceUiHints(
    val predictions: List<DeviceUiPrediction> = emptyList(),
    val allowedPlayerCardUids: List<String> = emptyList(),
    val allowedGameCardUids: List<String> = emptyList(),
)

data class DeviceUiPrediction(
    val eventType: EventType,
    val match: Map<String, Any?> = emptyMap(),
    val currentStateKey: String?,
    val status: SessionStatus?,
    val screen: ScreenModel,
)

data class SessionSummaryResponse(
    val id: UUID,
    val gameTemplateId: UUID,
    val gameName: String?,
    val gameImageUrl: String? = null,
    val moneyCurrency: String? = null,
    val showBalancesOnDashboard: Boolean = false,
    val dashboardMetricSource: String = "points",
    val dashboardMetricLabel: String = "Punkte",
    val dashboardMetricSuffix: String? = null,
    val dashboardMetricSortDirection: String = "DESC",
    val dashboardMetricDisplayType: String = "RACE_BAR",
    val dashboardMetricMaxSource: String? = null,
    val dashboardMetricMax: BigDecimal? = null,
    val dashboardStatusSource: String? = "currentRound",
    val dashboardStatusLabel: String = "Runde",
    val dashboardStatusSuffix: String? = null,
    val dashboardStatusMaxSource: String? = "roundLimit",
    val dashboardStatusDisplayType: String = "PROGRESS_BAR",
    val dashboardStatusValue: BigDecimal? = null,
    val dashboardStatusLimit: BigDecimal? = null,
    val deviceId: UUID,
    val status: SessionStatus,
    val currentStateKey: String,
    val roundLimitType: RoundLimitType,
    val roundLimit: Int?,
    val currentRoundNumber: Int,
    val createdAt: Instant,
    val startedAt: Instant?,
    val endedAt: Instant?,
    val teams: List<TeamResponse> = emptyList(),
    val rounds: List<SessionRoundResponse> = emptyList(),
    val result: GameResultResponse? = null,
)

data class TeamResponse(
    val id: UUID,
    val name: String,
    val teamOrder: Int,
    val targetSize: Int,
    val status: String,
    val members: List<TeamMemberResponse>,
    val balance: BigDecimal? = null,
    val dashboardMetricValue: BigDecimal? = null,
    val placementRank: Int? = null,
    val roundGlobalPointsAwarded: Long = 0,
    val placementGlobalPointsAwarded: Long = 0,
    val globalPointsAwarded: Long = 0,
)

data class TeamMemberResponse(
    val playerId: UUID,
    val playerName: String?,
    val imageUrl: String?,
    val joinedAt: Instant,
)

data class SessionRoundResponse(
    val roundNumber: Int,
    val winningTeamId: UUID?,
    val awardedPointsPerMember: Int,
    val createdAt: Instant,
)

data class GameResultResponse(
    val winningTeamId: UUID?,
    val endReason: String,
    val createdAt: Instant,
)

data class TimelineEventResponse(
    val id: UUID,
    val eventType: EventType,
    val payload: Map<String, Any?>,
    val createdAt: Instant,
)

data class PlayerStatsResponse(
    val playerId: UUID,
    val playerName: String?,
    val gamesPlayed: Long,
    val gamesWon: Long,
    val roundsWon: Long,
    val totalPoints: Long,
    val winRate: Double,
    val updatedAt: Instant,
)

data class LeaderboardEntryResponse(
    val rank: Int,
    val playerId: UUID,
    val playerName: String?,
    val imageUrl: String?,
    val gamesPlayed: Long,
    val gamesWon: Long,
    val roundsWon: Long,
    val totalPoints: Long,
    val winRate: Double,
)

data class MoneyTransferRequest(
    val sessionId: UUID,
    val fromAccountId: UUID,
    val toAccountId: UUID,
    @field:Positive val amount: BigDecimal,
    val initiatedByPlayerId: UUID? = null,
)
