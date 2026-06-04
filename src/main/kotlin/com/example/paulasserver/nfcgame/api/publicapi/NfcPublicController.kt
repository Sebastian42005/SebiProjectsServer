package com.example.paulasserver.nfcgame.api.publicapi

import com.example.paulasserver.nfcgame.application.publicapi.NfcPublicQueryService
import com.example.paulasserver.security.AuthenticatedUser
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/public")
class NfcPublicController(
    private val publicQueryService: NfcPublicQueryService,
) {
    @GetMapping("/sessions/active")
    fun activeSession(@RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?) =
        publicQueryService.getActiveSession(user?.id)

    @GetMapping("/sessions/{sessionId}")
    fun session(
        @PathVariable sessionId: UUID,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ) = publicQueryService.getSession(sessionId, user?.id)

    @PostMapping("/sessions/{sessionId}/finish")
    fun finishSession(
        @PathVariable sessionId: UUID,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ) = publicQueryService.finishSession(sessionId, user?.id)

    @GetMapping("/sessions/{sessionId}/timeline")
    fun timeline(
        @PathVariable sessionId: UUID,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ) = publicQueryService.getTimeline(sessionId, user?.id)

    @GetMapping("/leaderboard")
    fun leaderboard(@RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?) =
        publicQueryService.getLeaderboard(user?.id)

    @GetMapping("/players/{playerId}/stats")
    fun playerStats(
        @PathVariable playerId: UUID,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ) = publicQueryService.getPlayerStats(playerId, user?.id)

    @GetMapping("/players")
    fun players(@RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?) =
        publicQueryService.listPlayers(user?.id)

    @GetMapping("/players/{playerId}/image")
    fun playerImage(
        @PathVariable playerId: UUID,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ): ResponseEntity<ByteArray> = publicQueryService.getPlayerImage(playerId, user?.id)

    @GetMapping("/games")
    fun games(@RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?) =
        publicQueryService.listGames(user?.id)

    @GetMapping("/games/{gameId}/image")
    fun gameImage(
        @PathVariable gameId: UUID,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ): ResponseEntity<ByteArray> = publicQueryService.getGameImage(gameId, user?.id)

    @GetMapping("/games/{gameId}/stats")
    fun gameStats(
        @PathVariable gameId: UUID,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ) = publicQueryService.getGameStats(gameId, user?.id)

    @GetMapping("/history")
    fun history(@RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?) =
        publicQueryService.getHistory(user?.id)
}
