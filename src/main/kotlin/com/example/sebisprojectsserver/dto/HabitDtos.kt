package com.example.sebisprojectsserver.dto

import com.example.sebisprojectsserver.entities.HabitFrequency
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.NotNull
import java.time.Instant
import java.time.LocalDate

data class HabitRequest(
    @field:NotBlank
    val name: String,
    @field:NotNull
    val frequency: HabitFrequency,
    @field:Min(1)
    val targetCount: Int,
    @field:Min(0)
    val rewardMinutes: Int,
)

data class CompleteHabitRequest(
    @field:NotBlank
    val requestKey: String,
    val date: LocalDate? = null,
)

data class RedeemInstagramMinutesRequest(
    @field:Min(1)
    val minutes: Int,
    @field:NotBlank
    val requestKey: String,
)

data class HabitDto(
    val id: Long,
    val name: String,
    val frequency: HabitFrequency,
    val targetCount: Int,
    val rewardMinutes: Int,
)

data class HabitCompletionDto(
    val id: Long,
    val habitId: Long,
    val habitName: String,
    val frequency: HabitFrequency,
    val completionDate: LocalDate,
    val completedAt: Instant,
    val rewardMinutes: Int,
)

data class HabitProgressDto(
    val habit: HabitDto,
    val completedCount: Int,
    val targetCount: Int,
    val targetReached: Boolean,
    val canCompleteToday: Boolean,
    val completions: List<HabitCompletionDto>,
)

data class InstagramAccountDto(
    val availableMinutes: Int,
    val unlockedUntil: Instant?,
    val active: Boolean,
    val remainingSeconds: Long,
)

data class HabitOverviewDto(
    val account: InstagramAccountDto,
    val habits: List<HabitProgressDto>,
)

data class HabitCalendarDayDto(
    val date: LocalDate,
    val completions: List<HabitCompletionDto>,
    val dailyRelevantCount: Int,
    val dailyCompletedCount: Int,
    val allDailyHabitsDone: Boolean,
)

data class HabitMonthDto(
    val year: Int,
    val month: Int,
    val days: List<HabitCalendarDayDto>,
)
