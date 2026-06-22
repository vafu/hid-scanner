package dev.partscanner.hid.barcode

import dev.partscanner.hid.domain.ScannedBarcode
import org.junit.Assert.assertEquals
import org.junit.Test

class BarcodeParserTest {
    @Test
    fun extractsLCSCJsonPartNumber() {
        val barcode = ScannedBarcode(
            rawValue = """{"lcsc":"C123","mpn":"NE555DR","qty":10}""",
            rawBytes = null,
        )

        val parsed = BarcodeParser.preview(barcode)

        assertEquals("LCSC", parsed?.distributor)
        assertEquals("NE555DR", parsed?.manufacturerPartNumber)
    }

    @Test
    fun extractsKeyValuePartNumber() {
        val barcode = ScannedBarcode(
            rawValue = null,
            rawBytes = "Distributor=Mouser${0x1d.toChar()}MPN=RC0603FR-0710KL".toByteArray(),
        )

        val parsed = BarcodeParser.preview(barcode)

        assertEquals("Mouser", parsed?.distributor)
        assertEquals("RC0603FR-0710KL", parsed?.manufacturerPartNumber)
    }

    @Test
    fun extractsEciaPartNumber() {
        val barcode = ScannedBarcode(
            rawValue = null,
            rawBytes = "[)>${0x1e.toChar()}06${0x1d.toChar()}1PSTM32F103C8T6${0x1e.toChar()}".toByteArray(),
        )

        val parsed = BarcodeParser.preview(barcode)

        assertEquals("ANSI / ECIA", parsed?.distributor)
        assertEquals("STM32F103C8T6", parsed?.manufacturerPartNumber)
    }
}
