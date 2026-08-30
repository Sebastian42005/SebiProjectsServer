package com.example.sebisprojectsserver.mapper

import com.example.sebisprojectsserver.dto.ShoppingListItemResponseDto
import com.example.sebisprojectsserver.entities.ShoppingListItem

class ShoppingListMapper {
    fun toDto(shoppingListItem: ShoppingListItem): ShoppingListItemResponseDto {
        return ShoppingListItemResponseDto(
            id = shoppingListItem.id!!,
            name = shoppingListItem.name!!,
            additionalInfo = shoppingListItem.additionalInfo,
            iconUrl = shoppingListItem.iconUrl,
        )
    }
}