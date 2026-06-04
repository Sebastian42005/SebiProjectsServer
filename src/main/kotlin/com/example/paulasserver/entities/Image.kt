package com.example.paulasserver.entities

import jakarta.persistence.*

@Entity
@Table(name = "images")
class Image(
    @Id
    @GeneratedValue
    var id: Long = 0,

    var name: String = "",
    var content: ByteArray = ByteArray(0),

    var contentType: String? = null,

    var lastModified: Long? = null,

    @ManyToOne
    var imageCategory: ImageCategory? = null
)
