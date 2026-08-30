package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.dto.AuthMeResponse
import com.example.sebisprojectsserver.dto.LoginRequest
import com.example.sebisprojectsserver.dto.RegisterRequest
import com.example.sebisprojectsserver.entities.AppRole
import com.example.sebisprojectsserver.entities.AppUser
import com.example.sebisprojectsserver.repositories.AppUserRepository
import com.example.sebisprojectsserver.security.AppSecurityProperties
import com.example.sebisprojectsserver.security.AuthenticatedUser
import com.example.sebisprojectsserver.security.JwtService
import jakarta.servlet.http.HttpServletResponse
import jakarta.validation.Valid
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseCookie
import org.springframework.security.crypto.password.PasswordEncoder
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.Duration

@RestController
@RequestMapping("/api/auth")
class AuthController(
    private val appUserRepository: AppUserRepository,
    private val passwordEncoder: PasswordEncoder,
    private val jwtService: JwtService,
    private val securityProperties: AppSecurityProperties,
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
        @Valid @RequestBody request: RegisterRequest,
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
