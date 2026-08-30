package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.entities.HabitCompletion
import org.springframework.data.jpa.repository.JpaRepository
import java.time.LocalDate

interface HabitCompletionRepository : JpaRepository<HabitCompletion, Long> {
    fun findByRequestKey(requestKey: String): HabitCompletion?

    fun findFirstByHabitIdAndCompletionDateAndUndoneFalse(habitId: Long, completionDate: LocalDate): HabitCompletion?

    fun findAllByHabitIdInAndCompletionDateBetweenAndUndoneFalse(
        habitIds: List<Long>,
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<HabitCompletion>

    fun findAllByCompletionDateBetweenAndUndoneFalse(
        startDate: LocalDate,
        endDate: LocalDate,
    ): List<HabitCompletion>
}
