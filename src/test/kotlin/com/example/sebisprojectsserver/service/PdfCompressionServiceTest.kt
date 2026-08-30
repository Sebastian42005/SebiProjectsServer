package com.example.sebisprojectsserver.service

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.common.PDRectangle
import org.apache.pdfbox.pdmodel.graphics.image.LosslessFactory
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.util.Random

class PdfCompressionServiceTest {

    private val service = PdfCompressionService()

    @Test
    fun `compress creates smaller readable pdf for image heavy document`() {
        val sourcePdf = imageHeavyPdf()
        val file = MockMultipartFile("file", "scan.pdf", "application/pdf", sourcePdf)

        val result = service.compress(file, sourcePdf.size / 3L)

        assertTrue(result.compressedSize < result.originalSize)
        Loader.loadPDF(result.bytes).use { compressedPdf ->
            assertEquals(1, compressedPdf.numberOfPages)
        }
    }

    private fun imageHeavyPdf(): ByteArray {
        val image = BufferedImage(1200, 1600, BufferedImage.TYPE_INT_RGB)
        val random = Random(42)

        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val red = random.nextInt(256)
                val green = random.nextInt(256)
                val blue = random.nextInt(256)
                image.setRGB(x, y, Color(red, green, blue).rgb)
            }
        }

        return PDDocument().use { document ->
            val page = PDPage(PDRectangle.A4)
            document.addPage(page)
            val pageImage = LosslessFactory.createFromImage(document, image)

            PDPageContentStream(document, page).use { contentStream ->
                contentStream.drawImage(pageImage, 0f, 0f, PDRectangle.A4.width, PDRectangle.A4.height)
            }

            ByteArrayOutputStream().use { output ->
                document.save(output)
                output.toByteArray()
            }
        }
    }
}
