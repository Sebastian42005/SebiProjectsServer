package com.example.paulasserver.controller

import com.example.paulasserver.dto.ImageDto
import com.example.paulasserver.service.ImageService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/images")
class ImageController(
    private val imageService: ImageService
) {

    @PostMapping
    fun uploadImage(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("lastModified") lastModified: Long,
        @RequestParam("category") category: String,
    ) {
        imageService.uploadImage(file, lastModified, category)
    }

    @GetMapping("/{id}")
    fun getImage(@PathVariable id: Long): ResponseEntity<ByteArray> {
        return imageService.getImage(id)
    }

    @GetMapping
    fun getImageList(): List<ImageDto> {
        return imageService.getAllImageIds()
    }
}