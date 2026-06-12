Put the compiled ESP32 firmware binary for `NfcGameDevice.ino` in this directory.

Default backend settings expect:

- file: `NfcGameDevice.bin`
- version env var: `NFC_OTA_VERSION`
- enable env var: `NFC_OTA_ENABLED=true`

The device checks `/api/device/firmware/latest/manifest` with its device credentials and downloads `/api/device/firmware/latest/bin` when the configured backend version differs from the firmware's `FIRMWARE_VERSION`.
