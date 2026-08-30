package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.dto.ImageDto
import com.example.sebisprojectsserver.entities.Image
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ImageRepository: JpaRepository<Image, Long> {

    @Query("SELECT new com.example.sebisprojectsserver.dto.ImageDto(i.id, i.contentType) FROM Image i")
    fun getAllDto(): List<ImageDto>
}