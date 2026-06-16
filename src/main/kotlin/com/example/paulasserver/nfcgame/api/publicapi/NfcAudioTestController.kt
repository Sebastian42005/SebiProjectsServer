package com.example.paulasserver.nfcgame.api.publicapi

import com.example.paulasserver.nfcgame.application.device.NfcAudioTestService
import org.springframework.core.io.ByteArrayResource
import org.springframework.http.CacheControl
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RestController
@RequestMapping("/api/public/nfc-game/audio-test")
class NfcAudioTestController(
    private val audioTestService: NfcAudioTestService,
) {
    @PostMapping("/upload", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    fun upload(@RequestParam("file") file: MultipartFile) = audioTestService.upload(file)

    @GetMapping("/status")
    fun status() = audioTestService.publicStatus()

    @GetMapping("/latest.mp3", produces = ["audio/mpeg"])
    fun latestMp3(): ResponseEntity<ByteArrayResource> {
        val bytes = audioTestService.latestMp3Bytes()
        return ResponseEntity.ok()
            .cacheControl(CacheControl.noStore())
            .contentType(MediaType.valueOf("audio/mpeg"))
            .contentLength(bytes.size.toLong())
            .body(ByteArrayResource(bytes))
    }
}
