package com.example.paulasserver.nfcgame.api.admin

import com.example.paulasserver.dto.AuthMeResponse
import com.example.paulasserver.dto.LoginRequest
import com.example.paulasserver.nfcgame.api.dto.AdminAccountSummaryResponse
import com.example.paulasserver.nfcgame.api.dto.CardAssignRequest
import com.example.paulasserver.nfcgame.api.dto.CardResponse
import com.example.paulasserver.nfcgame.api.dto.DeviceRequest
import com.example.paulasserver.nfcgame.api.dto.DeviceResponse
import com.example.paulasserver.nfcgame.api.dto.FlowDefinitionRequest
import com.example.paulasserver.nfcgame.api.dto.FlowDefinitionResponse
import com.example.paulasserver.nfcgame.api.dto.FlowValidationResponse
import com.example.paulasserver.nfcgame.api.dto.GameBasicRequest
import com.example.paulasserver.nfcgame.api.dto.GameFlowRequest
import com.example.paulasserver.nfcgame.api.dto.GameFlowResponse
import com.example.paulasserver.nfcgame.api.dto.GameTemplateRequest
import com.example.paulasserver.nfcgame.api.dto.GameTemplateResponse
import com.example.paulasserver.nfcgame.api.dto.PlayerActiveRequest
import com.example.paulasserver.nfcgame.api.dto.PlayerPointsRequest
import com.example.paulasserver.nfcgame.api.dto.PlayerRequest
import com.example.paulasserver.nfcgame.api.dto.PlayerResponse
import com.example.paulasserver.nfcgame.application.admin.NfcAccountManagementService
import com.example.paulasserver.nfcgame.application.admin.NfcGameBuilderService
import com.example.paulasserver.nfcgame.application.admin.NfcAdminService
import com.example.paulasserver.repositories.AppUserRepository
import com.example.paulasserver.security.AppSecurityProperties
import com.example.paulasserver.security.AuthenticatedUser
import com.example.paulasserver.security.JwtService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.http.ResponseCookie
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PatchMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestAttribute
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.util.UUID

@RestController
@RequestMapping("/api/admin/auth")
class NfcAdminAuthController(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val securityProperties: AppSecurityProperties,
) {
    @PostMapping("/login")
    fun login(@Valid @RequestBody request: LoginRequest, response: HttpServletResponse): AuthMeResponse {
        val user = appUserRepository.findByUsername(request.username.trim())
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }
        response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(jwtService.generateToken(user)).toString())
        return AuthMeResponse(authenticated = true, username = user.username, role = user.role.name)
    }

    private fun buildAuthCookie(token: String): ResponseCookie =
        ResponseCookie.from(securityProperties.jwt.cookieName, token)
            .httpOnly(true)
            .secure(securityProperties.jwt.secureCookie)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofMinutes(securityProperties.jwt.expirationMinutes))
            .build()
}

@RestController
@RequestMapping("/api/admin")
class NfcAdminController(
    private val adminService: NfcAdminService,
    private val gameBuilderService: NfcGameBuilderService,
    private val accountManagementService: NfcAccountManagementService,
) {
    @GetMapping("/accounts")
    fun listAccounts(
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ): List<AdminAccountSummaryResponse> = accountManagementService.listAccounts(user)

    @DeleteMapping("/accounts/{id}")
    fun deleteAccount(
        @PathVariable id: Long,
        @RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?,
    ) = accountManagementService.deleteAccount(id, user)

    @GetMapping("/players")
    fun listPlayers(): List<PlayerResponse> = adminService.listPlayers()

    @PostMapping("/players")
    fun createPlayer(@Valid @RequestBody request: PlayerRequest): PlayerResponse = adminService.createPlayer(request)

    @PutMapping("/players/{id}")
    fun updatePlayer(@PathVariable id: UUID, @Valid @RequestBody request: PlayerRequest): PlayerResponse =
        adminService.updatePlayer(id, request)

    @PatchMapping("/players/{id}/active")
    fun updatePlayerActive(@PathVariable id: UUID, @RequestBody request: PlayerActiveRequest): PlayerResponse =
        adminService.updatePlayerActive(id, request.active)

    @PatchMapping("/players/{id}/points")
    fun updatePlayerPoints(@PathVariable id: UUID, @RequestBody request: PlayerPointsRequest): PlayerResponse =
        adminService.updatePlayerPoints(id, request.totalPoints)

    @PostMapping("/players/{id}/image")
    fun uploadPlayerImage(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
    ): PlayerResponse = adminService.setPlayerImage(id, file)

    @GetMapping("/players/{id}/image")
    fun getPlayerImage(@PathVariable id: UUID): ResponseEntity<ByteArray> = adminService.getPlayerImage(id)

    @DeleteMapping("/players/{id}")
    fun deletePlayer(@PathVariable id: UUID) = adminService.softDeletePlayer(id)

    @DeleteMapping("/sessions/{id}")
    fun deleteSession(@PathVariable id: UUID) = adminService.deleteSession(id)

    @GetMapping("/cards")
    fun listCards(): List<CardResponse> = adminService.listCards()

    @GetMapping("/cards/unassigned")
    fun listUnassignedCards(): List<CardResponse> = adminService.listUnassignedCards()

    @PostMapping("/cards/assign")
    fun assignCard(@Valid @RequestBody request: CardAssignRequest): CardResponse = adminService.assignCard(request)

    @GetMapping("/devices")
    fun listDevices(): List<DeviceResponse> = adminService.listDevices()

    @PostMapping("/devices")
    fun createDevice(@Valid @RequestBody request: DeviceRequest): DeviceResponse = adminService.createDevice(request)

    @PutMapping("/devices/{id}")
    fun updateDevice(@PathVariable id: UUID, @Valid @RequestBody request: DeviceRequest): DeviceResponse =
        adminService.updateDevice(id, request)

    @GetMapping("/game-templates")
    fun listGameTemplates(): List<GameTemplateResponse> = adminService.listGameTemplates()

    @PostMapping("/game-templates")
    fun createGameTemplate(@Valid @RequestBody request: GameTemplateRequest): GameTemplateResponse =
        adminService.createGameTemplate(request)

    @PutMapping("/game-templates/{id}")
    fun updateGameTemplate(@PathVariable id: UUID, @Valid @RequestBody request: GameTemplateRequest): GameTemplateResponse =
        adminService.updateGameTemplate(id, request)

    @PostMapping("/game-templates/{id}/flow")
    fun createFlow(@PathVariable id: UUID, @Valid @RequestBody request: FlowDefinitionRequest): FlowDefinitionResponse =
        adminService.replaceFlow(id, request)

    @PutMapping("/game-templates/{id}/flow")
    fun replaceFlow(@PathVariable id: UUID, @Valid @RequestBody request: FlowDefinitionRequest): FlowDefinitionResponse =
        adminService.replaceFlow(id, request)

    @GetMapping("/game-templates/{id}/flow")
    fun getFlow(@PathVariable id: UUID): FlowDefinitionResponse = adminService.getActiveFlow(id)

    @GetMapping("/games")
    fun listGames(): List<GameTemplateResponse> = gameBuilderService.listGames()

    @GetMapping("/games/publication-requests")
    fun listPublicationRequests(): List<GameTemplateResponse> = gameBuilderService.listPublicationRequests()

    @PostMapping("/games")
    fun createGame(@Valid @RequestBody request: GameBasicRequest): GameTemplateResponse =
        gameBuilderService.createGame(request)

    @GetMapping("/games/{id}")
    fun getGame(@PathVariable id: UUID): GameTemplateResponse = gameBuilderService.getGame(id)

    @PostMapping("/games/{id}/image")
    fun uploadGameImage(
        @PathVariable id: UUID,
        @RequestParam("file") file: MultipartFile,
    ): GameTemplateResponse = gameBuilderService.setGameImage(id, file)

    @GetMapping("/games/{id}/image")
    fun getGameImage(@PathVariable id: UUID): ResponseEntity<ByteArray> = gameBuilderService.getGameImage(id)

    @PutMapping("/games/{id}")
    fun updateGame(@PathVariable id: UUID, @Valid @RequestBody request: GameBasicRequest): GameTemplateResponse =
        gameBuilderService.updateGame(id, request)

    @DeleteMapping("/games/{id}")
    fun deleteGame(@PathVariable id: UUID) = gameBuilderService.deleteGame(id)

    @PostMapping("/games/{id}/duplicate")
    fun duplicateGame(@PathVariable id: UUID): GameTemplateResponse = gameBuilderService.duplicateGame(id)

    @PostMapping("/games/{id}/publication-request")
    fun requestPublication(@PathVariable id: UUID): GameTemplateResponse = gameBuilderService.requestPublication(id)

    @PostMapping("/games/{id}/approve-publication")
    fun approvePublication(@PathVariable id: UUID): GameTemplateResponse = gameBuilderService.approvePublication(id)

    @PostMapping("/games/{id}/reject-publication")
    fun rejectPublication(@PathVariable id: UUID): GameTemplateResponse = gameBuilderService.rejectPublication(id)

    @GetMapping("/games/{id}/flow")
    fun getGameFlow(@PathVariable id: UUID): GameFlowResponse = gameBuilderService.getFlow(id)

    @PutMapping("/games/{id}/flow")
    fun saveGameFlow(@PathVariable id: UUID, @Valid @RequestBody request: GameFlowRequest): GameFlowResponse =
        gameBuilderService.saveFlow(id, request)

    @PostMapping("/games/{id}/validate")
    fun validateGameFlow(@PathVariable id: UUID): FlowValidationResponse = gameBuilderService.validateFlow(id)
}
