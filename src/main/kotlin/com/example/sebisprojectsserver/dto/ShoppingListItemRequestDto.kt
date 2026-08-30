package com.example.sebisprojectsserver.dto

class ShoppingListItemRequestDto(
    val name: String,
    var additionalInfo: Map<String, String>? = null
)