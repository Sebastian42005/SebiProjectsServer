package com.example.sebisprojectsserver.entities

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Table(name = "instagram_account")
@Entity
class InstagramAccount {
    @Id
    var id: Long = 1

    @Column(nullable = false)
    var availableMinutes: Int = 0

    var activeUnlockUntil: Instant? = null

    var lockPublishedAt: Instant? = null

    @Column(nullable = false)
    var updatedAt: Instant = Instant.now()
}
