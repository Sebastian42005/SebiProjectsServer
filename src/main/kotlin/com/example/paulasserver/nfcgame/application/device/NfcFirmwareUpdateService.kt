package com.example.paulasserver.nfcgame.application.device

import com.example.paulasserver.nfcgame.api.dto.DeviceFirmwareManifestResponse
import com.example.paulasserver.nfcgame.security.DeviceAuthenticator
import jakarta.validation.constraints.NotBlank
import org.springframework.boot.context.properties.ConfigurationProperties
import org.springframework.core.io.FileSystemResource
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.validation.annotation.Validated
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.security.DigestInputStream
import java.security.MessageDigest
import java.util.HexFormat

@Validated
@ConfigurationProperties(prefix = "app.nfc-game.ota")
data class NfcOtaProperties(
    val enabled: Boolean = false,
    @field:NotBlank
    val version: String = "1.0.0",
    @field:NotBlank
    val storagePath: String = "firmware/nfc-game-device",
    @field:NotBlank
    val fileName: String = "NfcGameDevice.bin",
    val force: Boolean = false,
    val releaseNotes: String? = null,
)

data class NfcFirmwareFile(
    val version: String,
    val fileName: String,
    val path: Path,
    val size: Long,
    val md5: String,
) {
    val resource: FileSystemResource
        get() = FileSystemResource(path)
}

@Service
class NfcFirmwareUpdateService(
    private val deviceAuthenticator: DeviceAuthenticator,
    private val otaProperties: NfcOtaProperties,
) {
    fun manifest(
        deviceId: String,
        deviceKey: String,
        currentVersion: String?,
        firmwareUrl: String,
    ): DeviceFirmwareManifestResponse {
        deviceAuthenticator.authenticate(deviceId, deviceKey)

        val current = currentVersion.orEmpty().trim().ifBlank { "unknown" }
        val latest = otaProperties.version.trim()
        val updateAvailable = otaProperties.enabled && (otaProperties.force || current != latest)

        if (!updateAvailable) {
            return DeviceFirmwareManifestResponse(
                updateAvailable = false,
                currentVersion = current,
                latestVersion = latest,
                force = otaProperties.force,
                releaseNotes = otaProperties.releaseNotes?.takeIf { it.isNotBlank() },
            )
        }

        val firmware = resolveFirmwareFile()

        return DeviceFirmwareManifestResponse(
            updateAvailable = true,
            currentVersion = current,
            latestVersion = firmware.version,
            firmwareUrl = firmwareUrl,
            size = firmware.size,
            md5 = firmware.md5,
            force = otaProperties.force,
            releaseNotes = otaProperties.releaseNotes?.takeIf { it.isNotBlank() },
        )
    }

    fun firmware(deviceId: String, deviceKey: String): NfcFirmwareFile {
        deviceAuthenticator.authenticate(deviceId, deviceKey)

        if (!otaProperties.enabled) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "OTA firmware is disabled")
        }

        return resolveFirmwareFile()
    }

    private fun resolveFirmwareFile(): NfcFirmwareFile {
        val storageRoot = Path.of(otaProperties.storagePath).toAbsolutePath().normalize()
        val firmwarePath = storageRoot.resolve(otaProperties.fileName).normalize()

        if (!firmwarePath.startsWith(storageRoot)) {
            throw ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Invalid firmware file configuration")
        }

        if (!Files.isRegularFile(firmwarePath) || !Files.isReadable(firmwarePath)) {
            throw ResponseStatusException(
                HttpStatus.NOT_FOUND,
                "Firmware file not found: ${otaProperties.fileName}",
            )
        }

        return NfcFirmwareFile(
            version = otaProperties.version.trim(),
            fileName = otaProperties.fileName,
            path = firmwarePath,
            size = Files.size(firmwarePath),
            md5 = md5Hex(firmwarePath),
        )
    }

    private fun md5Hex(path: Path): String {
        val digest = MessageDigest.getInstance("MD5")

        Files.newInputStream(path).use { input ->
            DigestInputStream(input, digest).use { stream ->
                val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                while (stream.read(buffer) != -1) {
                    // DigestInputStream updates the digest while bytes are consumed.
                }
            }
        }

        return HexFormat.of().formatHex(digest.digest())
    }
}
