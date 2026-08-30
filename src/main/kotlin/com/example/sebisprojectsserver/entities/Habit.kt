package com.example.sebisprojectsserver.entities

import jakarta.persistence.*

enum class HabitFrequency {
    DAILY,
    WEEKLY,
    MONTHLY,
}

@Table(name = "habit")
@Entity
class Habit {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long? = null

    @Column(nullable = false)
    var name: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var frequency: HabitFrequency = HabitFrequency.DAILY

    @Column(nullable = false)
    var targetCount: Int = 1

    @Column(nullable = false)
    var rewardMinutes: Int = 0

    @Column(nullable = false)
    var active: Boolean = true

    @Column(nullable = false)
    var createdAt: Long = System.currentTimeMillis()

    @Column(nullable = false)
    var updatedAt: Long = System.currentTimeMillis()
}
