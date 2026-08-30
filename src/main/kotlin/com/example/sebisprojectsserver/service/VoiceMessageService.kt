package com.example.sebisprojectsserver.service

import com.example.sebisprojectsserver.entities.VoiceMessage
import com.example.sebisprojectsserver.repositories.VoiceMessageRepository
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import java.io.File
import java.nio.file.Files
import kotlin.io.path.createTempDirectory

@Service
class VoiceMessageService(
    private val repo: VoiceMessageRepository
) {

    fun uploadAndStoreAsMp3(file: MultipartFile): Long {
        require(!file.isEmpty) { "File is empty" }

        val workDir = createTempDirectory("voice-upload-").toFile()

        val inputExt = guessExt(file.originalFilename ?: "", file.contentType ?: "")
        val inputFile = File(workDir, "input.$inputExt")
        val mp3File = File(workDir, "out.mp3")

        try {
            // save input
            file.inputStream.use { input ->
                inputFile.outputStream().use { out -> input.copyTo(out) }
            }

            // convert to mp3
            convertToMp3WithFfmpeg(inputFile, mp3File)

            val mp3Bytes = Files.readAllBytes(mp3File.toPath())

            val entity = VoiceMessage(
                content = mp3Bytes,
                contentType = "audio/mpeg",
                seconds = getDurationInSeconds(mp3File),
                originalContentType = file.contentType
            )
            return repo.save(entity).id
        } finally {
            // cleanup
            inputFile.delete()
            mp3File.delete()
            workDir.delete()
        }
    }

    fun getMp3Bytes(id: Long): ByteArray {
        val msg = repo.findById(id).orElseThrow { IllegalArgumentException("VoiceMessage not found: $id") }
        return msg.content
    }

    @Transactional(readOnly = true)
    fun getLatestMp3Bytes(): ByteArray {
        val latest = repo
            .getLatestVoiceMessage(PageRequest.of(0, 1)).firstOrNull()
            ?: throw IllegalArgumentException("No voice message found")
        return latest.content
    }

    @Transactional(readOnly = true)
    fun getLatestDuration(): Double {
        val latest = repo
            .getLatestVoiceMessageDuration(PageRequest.of(0, 1)).firstOrNull()
            ?: throw IllegalArgumentException("No voice message found")
        return latest
    }

    private fun convertToMp3WithFfmpeg(input: File, output: File) {
        val cmd = listOf(
            "ffmpeg",
            "-y",
            "-i", input.absolutePath,
            "-vn",
            "-codec:a", "libmp3lame",
            "-b:a", "128k",
            output.absolutePath
        )

        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        val proc = pb.start()
        val log = proc.inputStream.bufferedReader().readText()
        val code = proc.waitFor()

        if (code != 0) {
            output.delete()
            throw IllegalStateException("ffmpeg failed ($code): $log")
        }
    }

    private fun guessExt(originalName: String, contentType: String): String {
        val lower = originalName.lowercase()
        return when {
            lower.endsWith(".webm") || contentType.contains("webm") -> "webm"
            lower.endsWith(".ogg") || contentType.contains("ogg") -> "ogg"
            lower.endsWith(".wav") || contentType.contains("wav") -> "wav"
            lower.endsWith(".m4a") || contentType.contains("m4a") || contentType.contains("mp4") -> "m4a"
            else -> "bin"
        }
    }

    private fun getDurationInSeconds(file: File): Double {
        val cmd = listOf(
            "ffprobe",
            "-v", "error",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            file.absolutePath
        )

        val pb = ProcessBuilder(cmd).redirectErrorStream(true)
        val proc = pb.start()

        val output = proc.inputStream.bufferedReader().readText().trim()
        val code = proc.waitFor()

        if (code != 0 || output.isBlank()) {
            throw IllegalStateException("ffprobe failed: $output")
        }

        return output.toDouble()
    }
}
