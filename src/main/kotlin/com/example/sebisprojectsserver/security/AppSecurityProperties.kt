package com.example.sebisprojectsserver.security

import jakarta.validation.Valid
import jakarta.validation.constraints.AssertTrue
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.validation.annotation.Validated

@Validated
@ConfigurationProperties(prefix = "app.security")
data class AppSecurityProperties(
    val allowedOrigins: List<String> = listOf(
        "http://localhost:4200",
        "http://localhost:4300",
        "https://sebis-projects.at",
        "https://www.sebis-projects.at",
    ),
    @field:Valid
    val jwt: JwtProperties = JwtProperties(),
    @field:Valid
    val admin: AdminProperties = AdminProperties(),
) {
    @AssertTrue(message = "app.security.jwt.secret must be at least 32 characters long")
    fun isJwtSecretStrongEnough(): Boolean {
        return jwt.secret.length >= 32
    }
}

data class JwtProperties(
    @field:NotBlank
    val secret: String = "",
    @field:Min(5)
    val expirationMinutes: Long = 43200,
    val cookieName: String = "SEBIS_PROJECTS_ACCESS_TOKEN",
    val secureCookie: Boolean = false,
)

data class AdminProperties(
    @field:NotBlank
    val username: String = "",
    @field:NotBlank
    val password: String = "",
)
