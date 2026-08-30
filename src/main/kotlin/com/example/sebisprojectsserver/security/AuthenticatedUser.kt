package com.example.sebisprojectsserver.security

data class AuthenticatedUser(
    val id: Long,
    val username: String,
    val role: String,
)
