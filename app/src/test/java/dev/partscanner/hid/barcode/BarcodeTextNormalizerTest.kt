package dev.partscanner.hid.barcode

import dev.partscanner.hid.domain.ScannedBarcode
import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeTextNormalizerTest {
    @Test
    fun normalizesAsciiControlSeparators() {
        val barcode = ScannedBarcode(
            rawValue = null,
            rawBytes = byteArrayOf(
                'A'.code.toByte(),
                0x1d,
                'B'.code.toByte(),
                0x1e,
                'C'.code.toByte(),
                0x04,
            ),
        )

        assertEquals("A{GS}B{RS}C{EOT}", BarcodeTextNormalizer.normalizeForKeyboardWedge(barcode))
    }

    @Test
    fun usesDisplayValueWhenRawBytesAreMissing() {
        val barcode = ScannedBarcode(rawValue = "DISPLAY", rawBytes = null)

        assertEquals("DISPLAY", BarcodeTextNormalizer.normalizeForKeyboardWedge(barcode))
    }
}
