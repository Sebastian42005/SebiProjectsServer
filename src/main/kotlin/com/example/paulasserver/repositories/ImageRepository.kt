package com.example.paulasserver.repositories

import com.example.paulasserver.dto.ImageDto
import com.example.paulasserver.entities.Image
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ImageRepository: JpaRepository<Image, Long> {

    @Query("SELECT new com.example.paulasserver.dto.ImageDto(i.id, i.contentType) FROM Image i")
    fun getAllDto(): List<ImageDto>
}