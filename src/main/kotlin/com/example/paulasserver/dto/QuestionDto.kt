package com.example.paulasserver.dto

data class QuestionDto(
    val id: Long?,
    val question: String?,
    val answer: String?,
    val category: String?,
    val userKnows: Boolean?,
    val hasImage: Boolean,
)