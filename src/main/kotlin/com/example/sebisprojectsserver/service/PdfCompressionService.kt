package com.example.sebisprojectsserver.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.JPEGFactory
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.io.ByteArrayOutputStream

data class PdfCompressionResult(
    val bytes: ByteArray,
    val reachedTarget: Boolean,
    val originalSize: Long,
    val compressedSize: Long,
)

@Service
class PdfCompressionService {

    private val compressionProfiles = listOf(
        CompressionProfile(dpi = 260f, jpegQuality = 0.94f),
        CompressionProfile(dpi = 240f, jpegQuality = 0.92f),
        CompressionProfile(dpi = 220f, jpegQuality = 0.9f),
        CompressionProfile(dpi = 205f, jpegQuality = 0.88f),
        CompressionProfile(dpi = 190f, jpegQuality = 0.86f),
        CompressionProfile(dpi = 175f, jpegQuality = 0.84f),
        CompressionProfile(dpi = 160f, jpegQuality = 0.82f),
        CompressionProfile(dpi = 150f, jpegQuality = 0.82f),
        CompressionProfile(dpi = 138f, jpegQuality = 0.8f),
        CompressionProfile(dpi = 125f, jpegQuality = 0.76f),
        CompressionProfile(dpi = 115f, jpegQuality = 0.72f),
        CompressionProfile(dpi = 105f, jpegQuality = 0.68f),
        CompressionProfile(dpi = 90f, jpegQuality = 0.6f),
        CompressionProfile(dpi = 76f, jpegQuality = 0.52f),
        CompressionProfile(dpi = 64f, jpegQuality = 0.44f),
        CompressionProfile(dpi = 54f, jpegQuality = 0.36f),
        CompressionProfile(dpi = 44f, jpegQuality = 0.3f),
        CompressionProfile(dpi = 36f, jpegQuality = 0.24f),
    )

    fun compress(file: MultipartFile, targetSizeBytes: Long): PdfCompressionResult {
        if (!isPdf(file)) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Bitte lade eine PDF-Datei hoch.")
        }

        if (targetSizeBytes < MIN_TARGET_BYTES) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Die Zielgröße ist zu klein.")
        }

        val sourceBytes = file.bytes

        if (sourceBytes.isEmpty()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Die PDF-Datei ist leer.")
        }

        validatePdf(sourceBytes)

        var smallestCandidate: ByteArray? = null
        var bestCandidateUnderTarget: ByteArray? = null

        for (profile in compressionProfiles) {
            val candidate = renderCompressedPdf(sourceBytes, profile)

            if (smallestCandidate == null || candidate.size < smallestCandidate.size) {
                smallestCandidate = candidate
            }

            if (candidate.size <= targetSizeBytes) {
                if (bestCandidateUnderTarget == null || candidate.size > bestCandidateUnderTarget.size) {
                    bestCandidateUnderTarget = candidate
                }
            }
        }

        bestCandidateUnderTarget?.let { compressedBytes ->
            return PdfCompressionResult(
                bytes = compressedBytes,
                reachedTarget = true,
                originalSize = sourceBytes.size.toLong(),
                compressedSize = compressedBytes.size.toLong(),
            )
        }

        val compressedBytes = smallestCandidate
            ?: throw ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "Die PDF-Datei konnte nicht verarbeitet werden.")

        if (compressedBytes.size >= sourceBytes.size) {
            throw ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY,
                "Diese PDF-Datei konnte nicht sinnvoll kleiner gemacht werden.",
            )
        }

        return PdfCompressionResult(
            bytes = compressedBytes,
            reachedTarget = false,
            originalSize = sourceBytes.size.toLong(),
            compressedSize = compressedBytes.size.toLong(),
        )
    }

    private fun validatePdf(sourceBytes: ByteArray) {
        try {
            Loader.loadPDF(sourceBytes).use { sourceDocument ->
                if (sourceDocument.numberOfPages <= 0) {
                    throw ResponseStatusException(HttpStatus.BAD_REQUEST, "In diesem PDF wurden keine Seiten gefunden.")
                }
            }
        } catch (error: ResponseStatusException) {
            throw error
        } catch (error: Exception) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Die PDF-Datei konnte nicht gelesen werden.", error)
        }
    }

    private fun renderCompressedPdf(sourceBytes: ByteArray, profile: CompressionProfile): ByteArray {
        return Loader.loadPDF(sourceBytes).use { sourceDocument ->
            PDDocument().use { targetDocument ->
                val renderer = PDFRenderer(sourceDocument)

                for (pageIndex in 0 until sourceDocument.numberOfPages) {
                    val sourcePage = sourceDocument.getPage(pageIndex)
                    val renderedPage = renderer.renderImageWithDPI(pageIndex, profile.dpi, ImageType.RGB)
                    val targetPageBox = targetPageBox(sourcePage.cropBox, renderedPage.width, renderedPage.height)
                    val targetPage = PDPage(targetPageBox)
                    val pageImage = JPEGFactory.createFromImage(targetDocument, renderedPage, profile.jpegQuality)

                    targetDocument.addPage(targetPage)
                    PDPageContentStream(targetDocument, targetPage).use { contentStream ->
                        contentStream.drawImage(pageImage, 0f, 0f, targetPageBox.width, targetPageBox.height)
                    }
                }

                ByteArrayOutputStream().use { output ->
                    targetDocument.save(output)
                    output.toByteArray()
                }
            }
        }
    }

    private fun targetPageBox(sourcePageBox: PDRectangle, imageWidth: Int, imageHeight: Int): PDRectangle {
        val sourceIsLandscape = sourcePageBox.width > sourcePageBox.height
        val imageIsLandscape = imageWidth > imageHeight

        return if (sourceIsLandscape == imageIsLandscape) {
            PDRectangle(sourcePageBox.width, sourcePageBox.height)
        } else {
            PDRectangle(sourcePageBox.height, sourcePageBox.width)
        }
    }

    private fun isPdf(file: MultipartFile): Boolean {
        return file.contentType == "application/pdf" || file.originalFilename?.lowercase()?.endsWith(".pdf") == true
    }

    private data class CompressionProfile(
        val dpi: Float,
        val jpegQuality: Float,
    )

    private companion object {
        private const val MIN_TARGET_BYTES = 50L * 1024L
    }
}
