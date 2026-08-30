package com.example.sebisprojectsserver.mapper

import com.example.sebisprojectsserver.dto.ImageCategoryDto
import com.example.sebisprojectsserver.entities.ImageCategory

class ImageCategoryMapper {
    fun toDto(imageCategory: ImageCategory): ImageCategoryDto {
        return ImageCategoryDto(
            id = imageCategory.id,
            name = imageCategory.name,
            imageId = imageCategory.images.firstOrNull()?.id,
            imageAmount = imageCategory.images.size
        )
    }
}