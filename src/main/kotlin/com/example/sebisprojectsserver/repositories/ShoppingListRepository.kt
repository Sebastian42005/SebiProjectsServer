package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.entities.ShoppingListItem
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ShoppingListRepository: JpaRepository<ShoppingListItem, Long> {

    @Query("SELECT e FROM ShoppingListItem e WHERE e.isChecked = false")
    fun findAllUncheckedShoppingListItems(): List<ShoppingListItem>

    @Query("SELECT DISTINCT e.name FROM ShoppingListItem e")
    fun findAllNames(): List<String>
}