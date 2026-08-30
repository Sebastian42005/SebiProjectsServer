package com.example.sebisprojectsserver.dto

class ShoppingListItemResponseDto(
    val id: Long,
    val name: String,
    val iconUrl: String?,
    var additionalInfo: Map<String, String>? = null,
)