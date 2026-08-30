package com.example.sebisprojectsserver.entities

import jakarta.persistence.*

@Entity
@Table(name = "voice_message")
class VoiceMessage(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @Lob
    @Column(nullable = false)
    var content: ByteArray = ByteArray(0),

    @Column(nullable = false)
    var contentType: String = "audio/mpeg",

    var originalContentType: String? = null,
    var seconds: Double? = 0.0,

    @Column(nullable = false)
    var createdAt: Long = System.currentTimeMillis(),
)
