package com.example.paulasserver.service

import com.example.paulasserver.dto.ShoppingListItemResponseDto
import com.example.paulasserver.entities.ShoppingListItem
import com.example.paulasserver.repositories.ShoppingListRepository
import org.springframework.stereotype.Service

@Service
class ShoppingListService(
    private val repository: ShoppingListRepository,
    private val iconService: IconUrlService,
) {

    fun uploadShoppingListItem(name: String, additionalInfo: Map<String, String>? = null): ShoppingListItem {
        val shoppingListItem = ShoppingListItem().apply {
            this.name = name
            this.additionalInfo = additionalInfo
            this.iconUrl = iconService.iconSvgUrlForGermanTerm(name)
        }
        return repository.save(shoppingListItem)
    }

    fun getUncheckedShoppingList(): List<ShoppingListItem> {
        return repository.findAllUncheckedShoppingListItems()
    }

    fun getPreviousNames(): List<String> {
        return repository.findAllNames()
    }

    fun checkShoppingListItem(shoppingListId: Long) {
        val shoppingListItem = repository.findById(shoppingListId).orElseThrow()
        shoppingListItem.isChecked = true
        repository.save(shoppingListItem)
    }
}