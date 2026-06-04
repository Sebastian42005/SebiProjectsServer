package com.example.paulasserver.entities

import jakarta.persistence.*

@Entity
@Table(name = "app_user")
class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long? = null

    @Column(nullable = false, unique = true)
    var username: String = ""

    @Column(nullable = false)
    var passwordHash: String = ""

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    var role: AppRole = AppRole.USER
}
