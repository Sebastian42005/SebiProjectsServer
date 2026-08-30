package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.dto.*
import com.example.sebisprojectsserver.service.HabitService
import jakarta.validation.Valid
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/habits")
class HabitController(
    private val habitService: HabitService,
) {
    @GetMapping("/overview")
    fun overview(
        @RequestParam(required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
        date: LocalDate?,
    ): HabitOverviewDto {
        return habitService.overview(date ?: LocalDate.now())
    }

    @GetMapping("/month")
    fun month(
        @RequestParam year: Int,
        @RequestParam month: Int,
    ): HabitMonthDto {
        return habitService.month(year, month)
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    fun create(@Valid @RequestBody request: HabitRequest): HabitDto {
        return habitService.createHabit(request)
    }

    @PutMapping("/{id}")
    fun update(@PathVariable id: Long, @Valid @RequestBody request: HabitRequest): HabitDto {
        return habitService.updateHabit(id, request)
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(@PathVariable id: Long) {
        habitService.deleteHabit(id)
    }

    @PostMapping("/{id}/complete")
    fun complete(@PathVariable id: Long, @Valid @RequestBody request: CompleteHabitRequest): HabitCompletionDto {
        return habitService.completeHabit(id, request)
    }

    @DeleteMapping("/completions/{id}")
    fun undo(@PathVariable id: Long): HabitCompletionDto {
        return habitService.undoCompletion(id)
    }

    @PostMapping("/instagram/redeem")
    fun redeemInstagramMinutes(@Valid @RequestBody request: RedeemInstagramMinutesRequest): InstagramAccountDto {
        return habitService.redeemInstagramMinutes(request)
    }
}
