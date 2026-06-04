package com.example.paulasserver.mapper

import com.example.paulasserver.dto.ImageDto
import com.example.paulasserver.entities.Image

class ImageMapper {
    fun toDto(imageCategory: Image): ImageDto {
        return ImageDto(
            id = imageCategory.id,
            type = imageCategory.contentType
        )
    }
}