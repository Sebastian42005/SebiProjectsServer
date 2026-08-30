package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.dto.ImageCategoryDto
import com.example.sebisprojectsserver.dto.ImageDto
import com.example.sebisprojectsserver.mapper.ImageCategoryMapper
import com.example.sebisprojectsserver.service.ImageCategoryService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/image-category")
class ImageCategoryController(
    private val imageCategoryService: ImageCategoryService,
    private val imageCategoryMapper: ImageCategoryMapper = ImageCategoryMapper()
) {

    @GetMapping("/{name}")
    fun getCategoryImages(@PathVariable name: String): ResponseEntity<List<ImageDto>> {
        return ResponseEntity.ok(imageCategoryService.getCategoryImages(name))
    }

    @GetMapping
    fun getCategories(): ResponseEntity<List<ImageCategoryDto>> {
        return ResponseEntity.ok(imageCategoryService.getAllCategories().map { imageCategoryMapper.toDto(it) })
    }
}