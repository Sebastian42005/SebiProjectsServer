package com.example.sebisprojectsserver.entities

import jakarta.persistence.*
import java.time.Instant

@Table(
    name = "instagram_redemption",
    uniqueConstraints = [
        UniqueConstraint(name = "uk_instagram_redemption_request_key", columnNames = ["requestKey"]),
    ],
)
@Entity
class InstagramRedemption {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE)
    var id: Long? = null

    @Column(nullable = false)
    var requestKey: String = ""

    @Column(nullable = false)
    var minutes: Int = 0

    @Column(nullable = false)
    var redeemedAt: Instant = Instant.now()

    @Column(nullable = false)
    var unlockedUntil: Instant = Instant.now()
}
