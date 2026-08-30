package com.example.sebisprojectsserver.dto

import jakarta.validation.constraints.NotBlank

data class RegisterRequest(
    @field:NotBlank
    val username: String = "",
    @field:NotBlank
    val password: String = "",
)
