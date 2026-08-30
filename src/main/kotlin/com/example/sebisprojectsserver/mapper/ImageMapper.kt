package com.example.sebisprojectsserver.mapper

import com.example.sebisprojectsserver.dto.ImageDto
import com.example.sebisprojectsserver.entities.Image

class ImageMapper {
    fun toDto(imageCategory: Image): ImageDto {
        return ImageDto(
            id = imageCategory.id,
            type = imageCategory.contentType
        )
    }
}