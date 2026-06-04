package com.example.paulasserver.entities

import com.fasterxml.jackson.annotation.JsonIgnore
import jakarta.persistence.CascadeType
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.OneToMany
import jakarta.persistence.Table

@Entity
@Table(name = "image_category")
class ImageCategory(
    @Id
    @GeneratedValue
    var id: Long = 0,

    var name: String = "",

    @JsonIgnore
    @OneToMany(mappedBy = "imageCategory", cascade = [CascadeType.ALL], orphanRemoval = true)
    var images: MutableList<Image> = mutableListOf()
)
