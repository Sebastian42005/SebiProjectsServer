package com.example.sebisprojectsserver.service

import com.example.sebisprojectsserver.dto.ImageDto
import com.example.sebisprojectsserver.entities.ImageCategory
import com.example.sebisprojectsserver.repositories.ImageCategoryRepository
import org.springframework.stereotype.Service

@Service
class ImageCategoryService(
    private val imageCategoryRepository: ImageCategoryRepository,
) {

    fun getAllCategories(): List<ImageCategory> {
        return imageCategoryRepository.findAll()
    }

    fun getCategoryImages(name: String): List<ImageDto> {
        return imageCategoryRepository.getImageIdsByCategoryName(name)
    }

    fun getOrCreateImageCategory(categoryName: String): ImageCategory {
        val imageCategoryNames = getAllCategories()
        return imageCategoryNames.find { it.name.trim().equals(categoryName, ignoreCase = true) }
            ?: create(categoryName)
    }

    fun create(categoryName: String): ImageCategory {
        return imageCategoryRepository.save(ImageCategory().apply { name = categoryName })
    }
}