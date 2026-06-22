package dev.partscanner.hid.domain

import android.bluetooth.BluetoothDevice
import android.graphics.RectF
import java.nio.charset.StandardCharsets

class ScannedBarcode(
    val rawValue: String?,
    rawBytes: ByteArray?,
) {
    private val bytes = rawBytes?.clone() ?: ByteArray(0)

    fun rawBytes(): ByteArray = bytes.clone()

    fun displayValue(): String {
        rawValue?.takeIf { it.isNotBlank() }?.let { return it }
        if (bytes.isEmpty()) return ""
        return String(bytes, StandardCharsets.UTF_8)
    }

    fun hexBytes(): String {
        if (bytes.isEmpty()) return "(no raw bytes)"
        return bytes.joinToString(" ") { "%02X".format(it.toInt() and 0xff) }
    }
}

data class DetectedBarcode(
    val barcode: ScannedBarcode,
    val boundingBox: RectF,
)

data class BluetoothHost(
    val device: BluetoothDevice,
    val label: String,
)

enum class HidConnectionState {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
}

enum class HidSpeed(
    val label: String,
    val keyDownMillis: Long,
    val interKeyGapMillis: Long,
) {
    FAST("Fast", 0L, 0L),
    NORMAL("Normal", 1L, 0L),
    COMPATIBLE("Compatible", 1L, 1L),
}

enum class BarcodeSendMode(val label: String) {
    FULL_TEXT("Full"),
    PARSED_MPN("MPN"),
}

data class ParsedBarcode(
    val distributor: String,
    val manufacturerPartNumber: String?,
    val summary: String,
)

enum class ScannerBarcodeFormat(val label: String) {
    AZTEC("Aztec"),
    CODABAR("Codabar"),
    CODE_39("Code 39"),
    CODE_93("Code 93"),
    CODE_128("Code 128"),
    DATA_MATRIX("Data Matrix"),
    EAN_8("EAN-8"),
    EAN_13("EAN-13"),
    ITF("ITF"),
    PDF_417("PDF417"),
    QR_CODE("QR"),
    UPC_A("UPC-A"),
    UPC_E("UPC-E");

    companion object {
        val defaultEnabled: Set<ScannerBarcodeFormat> = setOf(
            DATA_MATRIX,
            QR_CODE,
            CODE_128,
            CODE_39,
        )
    }
}

data class ScannerUiState(
    val status: String = "Starting",
    val detections: List<DetectedBarcode> = emptyList(),
    val selectedIdentity: String? = null,
    val selectedBarcode: ScannedBarcode? = null,
    val hosts: List<BluetoothHost> = emptyList(),
    val showHostChooser: Boolean = false,
    val hidConnectionState: HidConnectionState = HidConnectionState.DISCONNECTED,
    val enabledBarcodeFormats: Set<ScannerBarcodeFormat> = ScannerBarcodeFormat.defaultEnabled,
    val hidSpeed: HidSpeed = HidSpeed.NORMAL,
    val sendMode: BarcodeSendMode = BarcodeSendMode.FULL_TEXT,
    val parsedBarcode: ParsedBarcode? = null,
    val isScanLocked: Boolean = false,
    val isSending: Boolean = false,
) {
    val isHidConnected: Boolean get() = hidConnectionState == HidConnectionState.CONNECTED
    val canSend: Boolean get() = selectedBarcode != null && isHidConnected && !isSending
}
