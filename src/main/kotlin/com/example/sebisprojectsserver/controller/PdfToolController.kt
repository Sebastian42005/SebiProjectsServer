package com.example.sebisprojectsserver.controller

import com.example.sebisprojectsserver.service.PdfCompressionService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/tools/pdf")
class PdfToolController(
    private val pdfCompressionService: PdfCompressionService,
) {

    @PostMapping("/compress", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE], produces = [MediaType.APPLICATION_PDF_VALUE])
    fun compress(
        @RequestParam("file") file: MultipartFile,
        @RequestParam("targetSizeBytes") targetSizeBytes: Long,
    ): ResponseEntity<ByteArrayResource> {
        val result = pdfCompressionService.compress(file, targetSizeBytes)
        val fileName = compressedFileName(file.originalFilename)

        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .contentLength(result.compressedSize)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"$fileName\"")
            .header("X-Original-Size", result.originalSize.toString())
            .header("X-Compressed-Size", result.compressedSize.toString())
            .header("X-Reached-Target", result.reachedTarget.toString())
            .body(ByteArrayResource(result.bytes))
    }

    private fun compressedFileName(originalFilename: String?): String {
        val baseName = originalFilename
            ?.substringBeforeLast('.', missingDelimiterValue = originalFilename)
            ?.replace(Regex("""[^A-Za-z0-9._-]+"""), "-")
            ?.trim('-', '.', '_')
            ?.takeIf { it.isNotBlank() }
            ?: "dokument"

        return "$baseName-komprimiert.pdf"
    }
}
