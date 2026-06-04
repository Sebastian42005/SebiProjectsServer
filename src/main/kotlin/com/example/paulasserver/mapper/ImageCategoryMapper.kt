package com.example.paulasserver.mapper

import com.example.paulasserver.dto.ImageCategoryDto
import com.example.paulasserver.entities.ImageCategory

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