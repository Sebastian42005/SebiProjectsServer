package com.example.sebisprojectsserver.repositories

import com.example.sebisprojectsserver.dto.ImageDto
import com.example.sebisprojectsserver.entities.ImageCategory
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface ImageCategoryRepository : JpaRepository<ImageCategory, Long> {

    @Query("""
        select new com.example.sebisprojectsserver.dto.ImageDto(i.id, i.contentType)
        from ImageCategory c
        join c.images i
        where lower(trim(c.name)) = lower(trim(:name))
    """)
    fun getImageIdsByCategoryName(name: String): List<ImageDto>
}
