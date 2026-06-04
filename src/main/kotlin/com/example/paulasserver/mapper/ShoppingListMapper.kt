package com.example.paulasserver.mapper

import com.example.paulasserver.dto.ShoppingListItemResponseDto
import com.example.paulasserver.entities.ShoppingListItem

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