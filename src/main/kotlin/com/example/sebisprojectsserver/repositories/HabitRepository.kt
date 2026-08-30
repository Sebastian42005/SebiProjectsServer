package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.entities.Habit
import org.springframework.data.jpa.repository.JpaRepository

interface HabitRepository : JpaRepository<Habit, Long> {
    fun findAllByActiveTrueOrderByCreatedAtAsc(): List<Habit>
}
