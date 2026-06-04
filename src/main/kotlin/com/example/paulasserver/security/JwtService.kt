package com.example.paulasserver.security

import com.example.paulasserver.entities.AppUser
import io.jsonwebtoken.Jwts
import io.jsonwebtoken.security.Keys
import org.springframework.stereotype.Service
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date

@Service
class JwtService(
    private val securityProperties: AppSecurityProperties,
) {

    private val signingKey = Keys.hmacShaKeyFor(securityProperties.jwt.secret.toByteArray(StandardCharsets.UTF_8))

    fun generateToken(user: AppUser): String {
        val now = Instant.now()
        return Jwts.builder()
            .subject(user.id.toString())
            .claim("username", user.username)
            .claim("role", user.role.name)
            .issuedAt(Date.from(now))
            .expiration(Date.from(now.plus(securityProperties.jwt.expirationMinutes, ChronoUnit.MINUTES)))
            .signWith(signingKey)
            .compact()
    }

    fun extractUserId(token: String): Long {
        val claims = Jwts.parser()
            .verifyWith(signingKey)
            .build()
            .parseSignedClaims(token)
            .payload

        return claims.subject.toLong()
    }
}
