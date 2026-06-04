package com.example.paulasserver.service

import com.example.paulasserver.dto.ImageDto
import com.example.paulasserver.entities.Image
import com.example.paulasserver.repositories.ImageRepository
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile

@Service
class ImageService(
    private val imageRepository: ImageRepository,
    private val imageCategoryService: ImageCategoryService,
) {

    fun uploadImage(multipartFile: MultipartFile, lastModified: Long, categoryName: String): Image {
        val imageCategory = imageCategoryService.getOrCreateImageCategory(categoryName)

        return imageRepository.save(
            Image(
                name = multipartFile.originalFilename ?: multipartFile.name,
                content = multipartFile.bytes,
                contentType = multipartFile.contentType!!,
                lastModified = lastModified,
                imageCategory = imageCategory
            )
        )
    }

    fun getImage(imageId: Long): ResponseEntity<ByteArray> {
        val image = imageRepository.findById(imageId).orElseThrow()
        return ResponseEntity.status(HttpStatus.OK)
            .contentType(MediaType.valueOf(image.contentType!!))
            .body(image.content)
    }

    fun getAllImageIds(): List<ImageDto> {
        return imageRepository.getAllDto()
    }
}