package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.dto.ShoppingListItemRequestDto
import com.example.sebisprojectsserver.dto.ShoppingListItemResponseDto
import com.example.sebisprojectsserver.mapper.ShoppingListMapper
import com.example.sebisprojectsserver.service.ShoppingListService
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/shopping-list")
class ShoppingListController(
    private val shoppingListService: ShoppingListService,
    private val shoppingListMapper: ShoppingListMapper = ShoppingListMapper()
) {
    @PostMapping
    fun createShoppingListItem(@RequestBody shoppingListItemRequestDto: ShoppingListItemRequestDto): ShoppingListItemResponseDto {
        return shoppingListMapper.toDto(shoppingListService.uploadShoppingListItem(shoppingListItemRequestDto.name, shoppingListItemRequestDto.additionalInfo))
    }

    @GetMapping
    fun getUncheckedShoppingList(): List<ShoppingListItemResponseDto> {
        return shoppingListService.getUncheckedShoppingList().map { shoppingListMapper.toDto(it) }
    }

    @GetMapping("/names")
    fun getPreviousNames(): Map<String, List<String>> {
        return mapOf(Pair("items", shoppingListService.getPreviousNames()))
    }

    @PatchMapping("/{id}/check")
    fun updateShoppingListItem(@PathVariable("id") shoppingListId: Long) {
        shoppingListService.checkShoppingListItem(shoppingListId)
    }
}