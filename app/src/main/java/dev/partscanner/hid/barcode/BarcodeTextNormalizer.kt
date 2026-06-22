package dev.partscanner.hid.barcode

import dev.partscanner.hid.domain.ScannedBarcode

object BarcodeTextNormalizer {
    fun normalizeForKeyboardWedge(barcode: ScannedBarcode): String {
        val rawBytes = barcode.rawBytes()
        if (rawBytes.isEmpty()) {
            return barcode.displayValue()
        }

        return buildString(rawBytes.size) {
            for (byte in rawBytes) {
                when (val value = byte.toInt() and 0xff) {
                    0x1d -> append("{GS}")
                    0x1e -> append("{RS}")
                    0x04 -> append("{EOT}")
                    in 0x20..0x7e -> append(value.toChar())
                }
            }
        }
    }
}

object BarcodeIdentity {
    fun identityOf(barcode: ScannedBarcode): String {
        val display = barcode.displayValue()
        return display.ifBlank { barcode.hexBytes() }
    }
}
