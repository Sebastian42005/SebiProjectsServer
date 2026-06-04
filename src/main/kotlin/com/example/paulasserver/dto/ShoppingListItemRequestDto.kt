package com.example.paulasserver.dto

class ShoppingListItemRequestDto(
    val name: String,
    var additionalInfo: Map<String, String>? = null
)