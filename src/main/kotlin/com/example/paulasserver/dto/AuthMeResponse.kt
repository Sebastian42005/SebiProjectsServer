package com.example.paulasserver.dto

data class AuthMeResponse(
    val authenticated: Boolean,
    val username: String? = null,
    val role: String? = null,
)
