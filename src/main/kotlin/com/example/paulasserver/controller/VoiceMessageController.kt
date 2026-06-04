package com.example.paulasserver.controller

import com.example.paulasserver.service.MqttService
import com.example.paulasserver.service.VoiceMessageService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.HttpHeaders
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/voice-messages")
class VoiceMessageController(
    private val service: VoiceMessageService,
    private val mqttService: MqttService,
) {
    data class UploadResponse(val id: Long)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile): UploadResponse {
        val id = service.uploadAndStoreAsMp3(file)
        this.mqttService.send("voice-message", null)
        return UploadResponse(id)
    }

    @GetMapping("/{id}.mp3", produces = ["audio/mpeg"])
    fun getMp3(@PathVariable id: Long): ResponseEntity<ByteArray> {
        val bytes = service.getMp3Bytes(id)
        return ResponseEntity.ok()
            .contentType(MediaType.valueOf("audio/mpeg"))
            .body(bytes)
    }

    @GetMapping("/latest/duration")
    fun getLatestDuration(): Map<String, Double> {
        return mapOf(Pair("duration", this.service.getLatestDuration()))
    }

    @GetMapping("/latest", produces = ["audio/mpeg"])
    fun getMp3(@RequestHeader(value = "Range", required = false) rangeHeader: String?): ResponseEntity<Any> {
        val bytes = service.getLatestMp3Bytes() // ByteArray mit kompletter MP3
        val total = bytes.size.toLong()

        val commonHeaders = HttpHeaders().apply {
            add(HttpHeaders.ACCEPT_RANGES, "bytes")
            contentType = MediaType.valueOf("audio/mpeg")
        }

        if (rangeHeader.isNullOrBlank()) {
            // No Range header -> return full file with 200
            commonHeaders.contentLength = total
            return ResponseEntity.ok()
                .headers(commonHeaders)
                .body(ByteArrayResource(bytes))
        }

        // Parse "bytes=start-end" (we handle single range)
        val m = Regex("""bytes=(\d*)-(\d*)""").find(rangeHeader)
            ?: return ResponseEntity.status(416).headers(commonHeaders).build() // invalid Range

        val startStr = m.groupValues[1]
        val endStr = m.groupValues[2]

        val start = if (startStr.isBlank()) 0L else startStr.toLong()
        val end = if (endStr.isBlank()) total - 1 else endStr.toLong()

        if (start >= total || start < 0 || end < start) {
            val headers416 = HttpHeaders(commonHeaders)
            headers416.add(HttpHeaders.CONTENT_RANGE, "bytes */$total")
            return ResponseEntity.status(416).headers(headers416).build()
        }

        val actualEnd = minOf(end, total - 1)
        val chunkSize = (actualEnd - start + 1).toInt()

        val chunk = bytes.copyOfRange(start.toInt(), (actualEnd + 1).toInt())

        val headers206 = HttpHeaders(commonHeaders).apply {
            add(HttpHeaders.CONTENT_RANGE, "bytes $start-$actualEnd/$total")
            contentLength = chunkSize.toLong()
        }

        return ResponseEntity.status(206)
            .headers(headers206)
            .body(ByteArrayResource(chunk))
    }
}
