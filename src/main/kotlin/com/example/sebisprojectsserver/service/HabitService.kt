package com.example.sebisprojectsserver.service

import com.example.sebisprojectsserver.dto.*
import com.example.sebisprojectsserver.entities.*
import com.example.sebisprojectsserver.repositories.HabitCompletionRepository
import com.example.sebisprojectsserver.repositories.HabitRepository
import com.example.sebisprojectsserver.repositories.InstagramAccountRepository
import com.example.sebisprojectsserver.repositories.InstagramRedemptionRepository
import com.fasterxml.jackson.databind.ObjectMapper
import jakarta.annotation.PostConstruct
import jakarta.transaction.Transactional
import org.springframework.http.HttpStatus
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.*
import java.time.temporal.TemporalAdjusters

@Service
class HabitService(
    private val habitRepository: HabitRepository,
    private val completionRepository: HabitCompletionRepository,
    private val instagramAccountRepository: InstagramAccountRepository,
    private val redemptionRepository: InstagramRedemptionRepository,
    private val mqttService: MqttService,
    private val objectMapper: ObjectMapper,
) {
    private val instagramTopic = "instagram/access"
    private val clock: Clock = Clock.systemDefaultZone()

    @PostConstruct
    fun recoverUnlockState() {
        try {
            publishExpiredLockIfNeeded()
        } catch (_: Exception) {
            // The scheduled retry keeps the app bootable if MQTT is temporarily unavailable.
        }
    }

    fun overview(date: LocalDate = LocalDate.now(clock)): HabitOverviewDto {
        val habits = habitRepository.findAllByActiveTrueOrderByCreatedAtAsc()
        val completions = completionsForRelevantPeriods(habits, date)
        return HabitOverviewDto(
            account = accountDto(currentAccount()),
            habits = habits.map { habit -> progressDto(habit, date, completions) },
        )
    }

    fun month(year: Int, month: Int): HabitMonthDto {
        val start = LocalDate.of(year, month, 1)
        val end = start.withDayOfMonth(start.lengthOfMonth())
        val dailyHabits = habitRepository.findAllByActiveTrueOrderByCreatedAtAsc()
            .filter { it.frequency == HabitFrequency.DAILY }
        val completionsByDate = completionRepository.findAllByCompletionDateBetweenAndUndoneFalse(start, end)
            .map(::completionDto)
            .groupBy { it.completionDate }

        val days = (0 until start.lengthOfMonth()).map { offset ->
            val date = start.plusDays(offset.toLong())
            val completions = completionsByDate[date].orEmpty()
            val dailyCompletedCount = completions
                .filter { it.frequency == HabitFrequency.DAILY }
                .map { it.habitId }
                .distinct()
                .size

            HabitCalendarDayDto(
                date = date,
                completions = completions,
                dailyRelevantCount = dailyHabits.size,
                dailyCompletedCount = dailyCompletedCount,
                allDailyHabitsDone = dailyHabits.isNotEmpty() && dailyCompletedCount >= dailyHabits.size,
            )
        }

        return HabitMonthDto(year = year, month = month, days = days)
    }

    @Transactional
    fun createHabit(request: HabitRequest): HabitDto {
        val habit = Habit().apply {
            name = request.name.trim()
            frequency = request.frequency
            targetCount = request.targetCount
            rewardMinutes = request.rewardMinutes
        }
        return habitDto(habitRepository.save(habit))
    }

    @Transactional
    fun updateHabit(id: Long, request: HabitRequest): HabitDto {
        val habit = activeHabit(id)
        habit.name = request.name.trim()
        habit.frequency = request.frequency
        habit.targetCount = request.targetCount
        habit.rewardMinutes = request.rewardMinutes
        habit.updatedAt = System.currentTimeMillis()
        return habitDto(habitRepository.save(habit))
    }

    @Transactional
    fun deleteHabit(id: Long) {
        val habit = activeHabit(id)
        habit.active = false
        habit.updatedAt = System.currentTimeMillis()
        habitRepository.save(habit)
    }

    @Transactional
    fun completeHabit(id: Long, request: CompleteHabitRequest): HabitCompletionDto {
        completionRepository.findByRequestKey(request.requestKey)?.let {
            return completionDto(it)
        }

        val habit = activeHabit(id)
        val date = request.date ?: LocalDate.now(clock)

        if (habit.frequency == HabitFrequency.DAILY) {
            completionRepository.findFirstByHabitIdAndCompletionDateAndUndoneFalse(id, date)?.let {
                return completionDto(it)
            }
        }

        val account = accountForUpdate()
        account.availableMinutes += habit.rewardMinutes
        account.updatedAt = Instant.now(clock)

        val completion = HabitCompletion().apply {
            this.habit = habit
            this.completionDate = date
            this.rewardMinutes = habit.rewardMinutes
            this.requestKey = request.requestKey
        }

        instagramAccountRepository.save(account)
        return completionDto(completionRepository.save(completion))
    }

    @Transactional
    fun undoCompletion(id: Long): HabitCompletionDto {
        val completion = completionRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Habit completion not found")
        }

        if (completion.undone) {
            return completionDto(completion)
        }

        val account = accountForUpdate()
        if (account.availableMinutes < completion.rewardMinutes) {
            throw ResponseStatusException(
                HttpStatus.CONFLICT,
                "Nicht genug verfügbare Instagram-Minuten, um diese Erledigung rückgängig zu machen.",
            )
        }

        account.availableMinutes -= completion.rewardMinutes
        account.updatedAt = Instant.now(clock)
        completion.undone = true
        completion.undoneAt = Instant.now(clock)

        instagramAccountRepository.save(account)
        return completionDto(completionRepository.save(completion))
    }

    @Transactional
    fun redeemInstagramMinutes(request: RedeemInstagramMinutesRequest): InstagramAccountDto {
        redemptionRepository.findByRequestKey(request.requestKey)?.let {
            return accountDto(currentAccount())
        }

        val now = Instant.now(clock)
        val account = accountForUpdate()
        if (request.minutes > account.availableMinutes) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Es sind nicht genug Instagram-Minuten verfügbar.")
        }

        val unlockStart = account.activeUnlockUntil?.takeIf { it.isAfter(now) } ?: now
        val unlockedUntil = unlockStart.plus(Duration.ofMinutes(request.minutes.toLong()))

        account.availableMinutes -= request.minutes
        account.activeUnlockUntil = unlockedUntil
        account.lockPublishedAt = null
        account.updatedAt = now

        val redemption = InstagramRedemption().apply {
            requestKey = request.requestKey
            minutes = request.minutes
            redeemedAt = now
            this.unlockedUntil = unlockedUntil
        }

        instagramAccountRepository.save(account)
        redemptionRepository.save(redemption)

        publishInstagramEvent {
            publishUnlock(request.minutes, unlockedUntil)
        }
        return accountDto(account)
    }

    @Scheduled(fixedDelay = 30_000)
    @Transactional
    fun publishExpiredLockIfNeeded() {
        val account = instagramAccountRepository.findSingletonForUpdate() ?: return
        val unlockUntil = account.activeUnlockUntil ?: return
        if (unlockUntil.isAfter(Instant.now(clock)) || account.lockPublishedAt != null) {
            return
        }

        try {
            mqttService.publish(
                instagramTopic,
                objectMapper.writeValueAsString(
                    mapOf(
                        "action" to "LOCK",
                        "unlockedUntil" to unlockUntil.toString(),
                    ),
                ),
            )
        } catch (_: Exception) {
            return
        }

        account.lockPublishedAt = Instant.now(clock)
        account.updatedAt = Instant.now(clock)
        instagramAccountRepository.save(account)
    }

    private fun activeHabit(id: Long): Habit {
        val habit = habitRepository.findById(id).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Habit not found")
        }
        if (!habit.active) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Habit not found")
        }
        return habit
    }

    private fun completionsForRelevantPeriods(habits: List<Habit>, date: LocalDate): List<HabitCompletion> {
        if (habits.isEmpty()) {
            return emptyList()
        }

        val minStart = habits.minOf { periodBounds(date, it.frequency).first }
        val maxEnd = habits.maxOf { periodBounds(date, it.frequency).second }
        return completionRepository.findAllByHabitIdInAndCompletionDateBetweenAndUndoneFalse(
            habits.mapNotNull { it.id },
            minStart,
            maxEnd,
        )
    }

    private fun progressDto(habit: Habit, date: LocalDate, completions: List<HabitCompletion>): HabitProgressDto {
        val habitId = habit.id ?: throw IllegalStateException("Habit id missing")
        val (periodStart, periodEnd) = periodBounds(date, habit.frequency)
        val periodCompletions = completions
            .filter { it.habit?.id == habitId && !it.undone && !it.completionDate.isBefore(periodStart) && !it.completionDate.isAfter(periodEnd) }
            .sortedBy { it.completedAt }
            .map(::completionDto)

        return HabitProgressDto(
            habit = habitDto(habit),
            completedCount = periodCompletions.size,
            targetCount = habit.targetCount,
            targetReached = periodCompletions.size >= habit.targetCount,
            canCompleteToday = habit.frequency != HabitFrequency.DAILY || periodCompletions.none { it.completionDate == date },
            completions = periodCompletions,
        )
    }

    private fun periodBounds(date: LocalDate, frequency: HabitFrequency): Pair<LocalDate, LocalDate> {
        return when (frequency) {
            HabitFrequency.DAILY -> date to date
            HabitFrequency.WEEKLY -> {
                val start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                start to start.plusDays(6)
            }
            HabitFrequency.MONTHLY -> date.withDayOfMonth(1) to date.withDayOfMonth(date.lengthOfMonth())
        }
    }

    private fun accountForUpdate(): InstagramAccount {
        return instagramAccountRepository.findSingletonForUpdate() ?: instagramAccountRepository.save(InstagramAccount())
    }

    private fun currentAccount(): InstagramAccount {
        return instagramAccountRepository.findById(1).orElseGet {
            instagramAccountRepository.save(InstagramAccount())
        }
    }

    private fun accountDto(account: InstagramAccount): InstagramAccountDto {
        val now = Instant.now(clock)
        val unlockUntil = account.activeUnlockUntil
        val active = unlockUntil?.isAfter(now) == true
        return InstagramAccountDto(
            availableMinutes = account.availableMinutes,
            unlockedUntil = unlockUntil?.takeIf { active },
            active = active,
            remainingSeconds = if (active) Duration.between(now, unlockUntil).seconds.coerceAtLeast(0) else 0,
        )
    }

    private fun habitDto(habit: Habit): HabitDto {
        return HabitDto(
            id = habit.id ?: throw IllegalStateException("Habit id missing"),
            name = habit.name,
            frequency = habit.frequency,
            targetCount = habit.targetCount,
            rewardMinutes = habit.rewardMinutes,
        )
    }

    private fun completionDto(completion: HabitCompletion): HabitCompletionDto {
        val habit = completion.habit ?: throw IllegalStateException("Habit completion has no habit")
        return HabitCompletionDto(
            id = completion.id ?: throw IllegalStateException("Habit completion id missing"),
            habitId = habit.id ?: throw IllegalStateException("Habit id missing"),
            habitName = habit.name,
            frequency = habit.frequency,
            completionDate = completion.completionDate,
            completedAt = completion.completedAt,
            rewardMinutes = completion.rewardMinutes,
        )
    }

    private fun publishUnlock(durationMinutes: Int, unlockedUntil: Instant) {
        mqttService.publish(
            instagramTopic,
            objectMapper.writeValueAsString(
                mapOf(
                    "action" to "UNLOCK",
                    "durationMinutes" to durationMinutes,
                    "unlockedUntil" to unlockedUntil.toString(),
                ),
            ),
        )
    }

    private fun publishInstagramEvent(publish: () -> Unit) {
        try {
            publish()
        } catch (error: Exception) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Instagram konnte nicht freigeschaltet werden, weil MQTT nicht erreichbar oder nicht autorisiert ist.",
                error,
            )
        }
    }
}
