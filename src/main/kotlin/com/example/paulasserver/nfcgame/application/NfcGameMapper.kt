package com.example.paulasserver.nfcgame.application

import com.example.paulasserver.nfcgame.api.dto.CardResponse
import com.example.paulasserver.nfcgame.api.dto.DeviceResponse
import com.example.paulasserver.nfcgame.api.dto.GameTemplateResponse
import com.example.paulasserver.nfcgame.api.dto.PlayerResponse
import com.example.paulasserver.nfcgame.persistence.entity.NfcCard
import com.example.paulasserver.nfcgame.persistence.entity.NfcDevice
import com.example.paulasserver.nfcgame.persistence.entity.NfcGameTemplate
import com.example.paulasserver.nfcgame.persistence.entity.NfcPlayer
import org.springframework.stereotype.Component

@Component
class NfcGameMapper {
    fun toPlayerResponse(player: NfcPlayer, totalPoints: Long = 0) = PlayerResponse(
        id = requireNotNull(player.id),
        name = player.name,
        description = player.description,
        imageUrl = player.imageContentType?.let { "/api/public/players/${player.id}/image" } ?: player.imageUrl,
        active = player.active,
        totalPoints = totalPoints,
        createdAt = player.createdAt,
        updatedAt = player.updatedAt,
    )

    fun toCardResponse(card: NfcCard) = CardResponse(
        id = requireNotNull(card.id),
        cardUid = card.cardUid,
        cardType = card.cardType,
        status = card.status,
        playerId = card.playerId,
        gameTemplateId = card.gameTemplateId,
        createdAt = card.createdAt,
        updatedAt = card.updatedAt,
    )

    fun toDeviceResponse(device: NfcDevice) = DeviceResponse(
        id = requireNotNull(device.id),
        name = device.name,
        active = device.active,
        linked = device.accountId != null,
        lastSeenAt = device.lastSeenAt,
        createdAt = device.createdAt,
    )

    fun toGameTemplateResponse(
        template: NfcGameTemplate,
        cardUid: String? = null,
        ownedByCurrentAccount: Boolean = true,
    ) = GameTemplateResponse(
        id = requireNotNull(template.id),
        name = template.name,
        description = template.description,
        imageUrl = template.imageContentType?.let { "/api/public/games/${template.id}/image" } ?: template.imageUrl,
        active = template.active,
        publicationStatus = template.publicationStatus,
        version = template.flowVersion,
        startNodeId = template.startNodeId,
        cardUid = cardUid,
        allowTeams = template.allowTeams,
        minTeamSize = template.minTeamSize,
        maxTeamSize = template.maxTeamSize,
        supportsRoundLimit = template.supportsRoundLimit,
        economyEnabled = template.economyEnabled,
        startCapital = template.startCapital,
        smallStep = template.smallStep,
        largeStep = template.largeStep,
        winRuleType = template.winRuleType,
        dashboardMetricSource = template.dashboardMetricSource,
        dashboardMetricLabel = template.dashboardMetricLabel,
        dashboardMetricSuffix = template.dashboardMetricSuffix,
        dashboardMetricSortDirection = template.dashboardMetricSortDirection,
        dashboardMetricDisplayType = template.dashboardMetricDisplayType,
        dashboardStatusSource = template.dashboardStatusSource,
        dashboardStatusLabel = template.dashboardStatusLabel,
        dashboardStatusSuffix = template.dashboardStatusSuffix,
        dashboardStatusMaxSource = template.dashboardStatusMaxSource,
        dashboardStatusDisplayType = template.dashboardStatusDisplayType,
        ownedByCurrentAccount = ownedByCurrentAccount,
        createdAt = template.createdAt,
        updatedAt = template.updatedAt,
    )
}
