package dev.partscanner.hid.barcode

import dev.partscanner.hid.domain.ParsedBarcode
import dev.partscanner.hid.domain.ScannedBarcode
import java.util.Locale

object BarcodeParser {
    fun preview(barcode: ScannedBarcode): ParsedBarcode? {
        val normalized = BarcodeTextNormalizer.normalizeForKeyboardWedge(barcode)
        val raw = barcode.displayValue()
        return parseJsonLike(raw)
            ?: parseKeyValue(normalized)
            ?: parseEcia(normalized)
    }

    private fun parseJsonLike(raw: String): ParsedBarcode? {
        val lower = raw.lowercase(Locale.US)
        if (!lower.contains("mpn") && !lower.contains("part")) return null
        val mpn = listOf("mpn", "mfrPartNumber", "manufacturerPartNumber", "partNumber")
            .firstNotNullOfOrNull { key -> jsonField(raw, key) }
            ?.takeIf { it.isNotBlank() }
        return ParsedBarcode(
            distributor = if (lower.contains("lcsc")) "LCSC" else "JSON label",
            manufacturerPartNumber = mpn,
            summary = listOfNotNull("JSON-ish label", mpn?.let { "MPN $it" }).joinToString(" - "),
        )
    }

    private fun parseKeyValue(normalized: String): ParsedBarcode? {
        val fields = normalized.split("{GS}", "{RS}", "\n", "\r", "\t", ";", ",")
            .mapNotNull { token ->
                val separator = listOf("=", ":", "|").firstOrNull { it in token } ?: return@mapNotNull null
                val key = token.substringBefore(separator).trim().lowercase(Locale.US)
                val value = token.substringAfter(separator).trim()
                key to value
            }
            .toMap()
        if (fields.isEmpty()) return null

        val mpn = firstField(
            fields,
            "mpn",
            "mfr part number",
            "manufacturer part number",
            "manufacturerpartnumber",
            "part number",
            "partnumber",
        )
        val distributor = when {
            fields.keys.any { "mouser" in it } || fields.values.any { "mouser" in it.lowercase(Locale.US) } -> "Mouser"
            fields.keys.any { "digikey" in it || "digi-key" in it } || fields.values.any { value ->
                val lower = value.lowercase(Locale.US)
                "digikey" in lower || "digi-key" in lower
            } -> "Digi-Key"
            fields.keys.any { "lcsc" in it } || fields.values.any { "lcsc" in it.lowercase(Locale.US) } -> "LCSC"
            else -> "Key/value label"
        }
        return ParsedBarcode(
            distributor = distributor,
            manufacturerPartNumber = mpn,
            summary = listOfNotNull(distributor, mpn?.let { "MPN $it" }).joinToString(" - "),
        )
    }

    private fun parseEcia(normalized: String): ParsedBarcode? {
        val tokens = normalized.split("{GS}", "{RS}", "\n", "\r", "\t")
            .flatMap { it.split(" ") }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (tokens.none { it.startsWith("[)>") || it == "06" || it == "05" }) return null

        val mpn = tokens.firstNotNullOfOrNull { token ->
            when {
                token.startsWith("1P") && token.length > 2 -> token.drop(2)
                token.startsWith("P") && token.length > 1 -> token.drop(1)
                else -> null
            }
        }
        return ParsedBarcode(
            distributor = "ANSI / ECIA",
            manufacturerPartNumber = mpn,
            summary = listOfNotNull("ANSI / ECIA label", mpn?.let { "MPN $it" }).joinToString(" - "),
        )
    }

    private fun jsonField(raw: String, key: String): String? {
        val escapedKey = Regex.escape(key)
        val pattern = Regex("\"$escapedKey\"\\s*:\\s*\"([^\"]+)\"", RegexOption.IGNORE_CASE)
        return pattern.find(raw)?.groupValues?.getOrNull(1)
    }

    private fun firstField(fields: Map<String, String>, vararg keys: String): String? {
        return keys.firstNotNullOfOrNull { key -> fields[key] }
    }
}
