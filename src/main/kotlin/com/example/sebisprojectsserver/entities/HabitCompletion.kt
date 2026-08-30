package com.example.sebisprojectsserver.entities

import jakarta.persistence.*
import java.time.Instant
import java.time.LocalDate

@Table(
    name = "habit_completion",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_habit_completion_request_key", columnNames = ["requestKey"]),
    ],
)
@Entity
class HabitCompletion {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long? = null

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    var habit: Habit? = null

    @Column(nullable = false)
    var completionDate: LocalDate = LocalDate.now()

    @Column(nullable = false)
    var completedAt: Instant = Instant.now()

    @Column(nullable = false)
    var rewardMinutes: Int = 0

    @Column(nullable = false)
    var requestKey: String = ""

    @Column(nullable = false)
    var undone: Boolean = false

    var undoneAt: Instant? = null
}
