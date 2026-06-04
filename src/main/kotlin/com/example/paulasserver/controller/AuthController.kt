package com.example.paulasserver.controller

import com.example.paulasserver.dto.AuthMeResponse
import com.example.paulasserver.dto.LoginRequest
import com.example.paulasserver.entities.AppRole
import com.example.paulasserver.entities.AppUser
import com.example.paulasserver.nfcgame.api.dto.NfcRegisterRequest
import com.example.paulasserver.repositories.AppUserRepository
import com.example.paulasserver.security.AppSecurityProperties
import com.example.paulasserver.security.AuthenticatedUser
import com.example.paulasserver.security.JwtService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.security.SecureRandom
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val securityProperties: AppSecurityProperties,
    private val tvLoginService: TvLoginService,
) {

    @PostMapping("/login")
    fun login(
        @Valid @RequestBody request: LoginRequest,
        response: HttpServletResponse,
    ): AuthMeResponse {
        val user = appUserRepository.findByUsername(request.username.trim())
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")

        if (!passwordEncoder.matches(request.password, user.passwordHash)) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid credentials")
        }

        response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(jwtService.generateToken(user)).toString())
        return AuthMeResponse(
            authenticated = true,
            username = user.username,
            role = user.role.name,
        )
    }

    @PostMapping("/register")
    fun register(
        @Valid @RequestBody request: NfcRegisterRequest,
        response: HttpServletResponse,
    ): AuthMeResponse {
        val username = request.username.trim()
        if (username.length < 3) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Username must have at least 3 characters")
        }
        if (request.password.length < 6) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Password must have at least 6 characters")
        }
        if (appUserRepository.findByUsername(username) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Username already exists")
        }

        val user = appUserRepository.save(
            AppUser().apply {
                this.username = username
                passwordHash = passwordEncoder.encode(request.password) ?: throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not encode password")
                role = AppRole.USER
            },
        )
        response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(jwtService.generateToken(user)).toString())
        return AuthMeResponse(authenticated = true, username = user.username, role = user.role.name)
    }

    @PostMapping("/logout")
    fun logout(response: HttpServletResponse): AuthMeResponse {
        response.addHeader(HttpHeaders.SET_COOKIE, clearAuthCookie().toString())
        return AuthMeResponse(authenticated = false)
    }

    @GetMapping("/me")
    fun me(@RequestAttribute(name = "authenticatedUser", required = false) user: AuthenticatedUser?): AuthMeResponse {
        if (user == null) {
            return AuthMeResponse(authenticated = false)
        }

        return AuthMeResponse(
            authenticated = true,
            username = user.username,
            role = user.role,
        )
    }

    @PostMapping("/tv-login")
    fun createTvLogin(): TvLoginStartResponse = tvLoginService.createLogin()

    @GetMapping("/tv-login/{requestId}")
    fun pollTvLogin(
        @PathVariable requestId: String,
        response: HttpServletResponse,
    ): TvLoginStatusResponse {
        val status = tvLoginService.status(requestId)
        if (status.token != null) {
            response.addHeader(HttpHeaders.SET_COOKIE, buildAuthCookie(status.token).toString())
        }
        return TvLoginStatusResponse(
            status = status.status,
            authenticated = status.token != null,
            username = status.username,
            role = status.role,
        )
    }

    @PostMapping("/tv-login/{requestId}/approve")
    fun approveTvLogin(
        @PathVariable requestId: String,
        @RequestBody request: TvLoginApproveRequest,
        @RequestAttribute(name = "authenticatedUser", required = false) authenticatedUser: AuthenticatedUser?,
    ): TvLoginApproveResponse {
        val userId = authenticatedUser?.id
            ?: throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")
        val user = appUserRepository.findById(userId).orElseThrow {
            ResponseStatusException(HttpStatus.UNAUTHORIZED, "Login required")
        }
        tvLoginService.approve(requestId, request.code, user, jwtService.generateToken(user))
        return TvLoginApproveResponse(approved = true)
    }

    private fun buildAuthCookie(token: String): ResponseCookie {
        return ResponseCookie.from(securityProperties.jwt.cookieName, token)
            .httpOnly(true)
            .secure(securityProperties.jwt.secureCookie)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ofMinutes(securityProperties.jwt.expirationMinutes))
            .build()
    }

    private fun clearAuthCookie(): ResponseCookie {
        return ResponseCookie.from(securityProperties.jwt.cookieName, "")
            .httpOnly(true)
            .secure(securityProperties.jwt.secureCookie)
            .sameSite("Strict")
            .path("/")
            .maxAge(Duration.ZERO)
            .build()
    }
}

data class TvLoginStartResponse(
    val requestId: String,
    val code: String,
    val expiresAt: Instant,
)

data class TvLoginApproveRequest(
    val code: String,
)

data class TvLoginApproveResponse(
    val approved: Boolean,
)

data class TvLoginStatusResponse(
    val status: String,
    val authenticated: Boolean = false,
    val username: String? = null,
    val role: String? = null,
)

private data class TvLoginSession(
    val requestId: String,
    val code: String,
    val expiresAt: Instant,
    val username: String? = null,
    val role: String? = null,
    val token: String? = null,
)

data class TvLoginStatus(
    val status: String,
    val username: String? = null,
    val role: String? = null,
    val token: String? = null,
)

@Service
class TvLoginService {
    private val random = SecureRandom()
    private val sessions = ConcurrentHashMap<String, TvLoginSession>()

    fun createLogin(): TvLoginStartResponse {
        pruneExpired()
        val requestId = UUID.randomUUID().toString()
        val code = random.nextInt(1_000_000).toString().padStart(6, '0')
        val expiresAt = Instant.now().plus(Duration.ofMinutes(10))
        sessions[requestId] = TvLoginSession(requestId = requestId, code = code, expiresAt = expiresAt)
        return TvLoginStartResponse(requestId = requestId, code = code, expiresAt = expiresAt)
    }

    fun approve(requestId: String, code: String, user: AppUser, token: String) {
        val session = requireActiveSession(requestId)
        if (session.code != code.trim()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid TV code")
        }
        sessions[requestId] = session.copy(username = user.username, role = user.role.name, token = token)
    }

    fun status(requestId: String): TvLoginStatus {
        val session = sessions[requestId] ?: return TvLoginStatus(status = "UNKNOWN")
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(requestId)
            return TvLoginStatus(status = "EXPIRED")
        }
        return if (session.token == null) {
            TvLoginStatus(status = "PENDING")
        } else {
            sessions.remove(requestId)
            TvLoginStatus(status = "APPROVED", username = session.username, role = session.role, token = session.token)
        }
    }

    private fun requireActiveSession(requestId: String): TvLoginSession {
        val session = sessions[requestId] ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TV login not found")
        if (session.expiresAt.isBefore(Instant.now())) {
            sessions.remove(requestId)
            throw ResponseStatusException(HttpStatus.GONE, "TV login expired")
        }
        return session
    }

    private fun pruneExpired() {
        val now = Instant.now()
        sessions.entries.removeIf { it.value.expiresAt.isBefore(now) }
    }
}
