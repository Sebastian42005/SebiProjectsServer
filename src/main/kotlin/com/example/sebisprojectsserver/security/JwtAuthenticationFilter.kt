package com.example.sebisprojectsserver.security

import com.example.sebisprojectsserver.repositories.AppUserRepository
import io.jsonwebtoken.JwtException
import jakarta.servlet.FilterChain
import jakarta.servlet.http.HttpServletRequest
import jakarta.servlet.http.HttpServletResponse
import org.springframework.http.HttpHeaders
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.context.SecurityContextHolder
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource
import org.springframework.stereotype.Component
import org.springframework.web.filter.OncePerRequestFilter

@Component
class JwtAuthenticationFilter(
    private val jwtService: JwtService,
    private val appUserRepository: AppUserRepository,
    private val securityProperties: AppSecurityProperties,
) : OncePerRequestFilter() {

    override fun doFilterInternal(
        request: HttpServletRequest,
        response: HttpServletResponse,
        filterChain: FilterChain,
    ) {
        val token = resolveToken(request)

        if (token != null && SecurityContextHolder.getContext().authentication == null) {
            try {
                val userId = jwtService.extractUserId(token)
                val user = appUserRepository.findById(userId).orElse(null)
                if (user != null) {
                    val principal = AuthenticatedUser(
                        id = user.id ?: userId,
                        username = user.username,
                        role = user.role.name,
                    )
                    val authentication = UsernamePasswordAuthenticationToken(
                        principal,
                        null,
                        listOf(SimpleGrantedAuthority("ROLE_${user.role.name}")),
                    )
                    authentication.details = WebAuthenticationDetailsSource().buildDetails(request)
                    SecurityContextHolder.getContext().authentication = authentication
                    request.setAttribute("authenticatedUser", principal)
                }
            } catch (_: JwtException) {
                SecurityContextHolder.clearContext()
            } catch (_: IllegalArgumentException) {
                SecurityContextHolder.clearContext()
            }
        }

        filterChain.doFilter(request, response)
    }

    private fun resolveToken(request: HttpServletRequest): String? {
        val cookieToken = request.cookies
            ?.firstOrNull { it.name == securityProperties.jwt.cookieName }
            ?.value
            ?.takeIf { it.isNotBlank() }
        if (cookieToken != null) {
            return cookieToken
        }

        val authorizationHeader = request.getHeader(HttpHeaders.AUTHORIZATION)
            ?.takeIf { it.startsWith("Bearer ") }

        return authorizationHeader?.removePrefix("Bearer ")?.trim()?.takeIf { it.isNotBlank() }
    }
}
