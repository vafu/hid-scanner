package dev.partscanner.hid.bluetooth

data class KeyStroke(
    val modifier: Byte,
    val usage: Byte,
)

object HidKeyMapper {
    private const val SHIFT: Byte = 0x02

    fun fromChar(c: Char): KeyStroke? = when {
        c in 'a'..'z' -> KeyStroke(0, (0x04 + c.code - 'a'.code).toByte())
        c in 'A'..'Z' -> KeyStroke(SHIFT, (0x04 + c.code - 'A'.code).toByte())
        c in '1'..'9' -> KeyStroke(0, (0x1e + c.code - '1'.code).toByte())
        c == '0' -> KeyStroke(0, 0x27)
        else -> punctuation[c]
    }

    private val punctuation = mapOf(
        '\n' to KeyStroke(0, 0x28),
        '\t' to KeyStroke(0, 0x2b),
        ' ' to KeyStroke(0, 0x2c),
        '-' to KeyStroke(0, 0x2d),
        '_' to KeyStroke(SHIFT, 0x2d),
        '=' to KeyStroke(0, 0x2e),
        '+' to KeyStroke(SHIFT, 0x2e),
        '[' to KeyStroke(0, 0x2f),
        '{' to KeyStroke(SHIFT, 0x2f),
        ']' to KeyStroke(0, 0x30),
        '}' to KeyStroke(SHIFT, 0x30),
        '\\' to KeyStroke(0, 0x31),
        '|' to KeyStroke(SHIFT, 0x31),
        ';' to KeyStroke(0, 0x33),
        ':' to KeyStroke(SHIFT, 0x33),
        '\'' to KeyStroke(0, 0x34),
        '"' to KeyStroke(SHIFT, 0x34),
        '`' to KeyStroke(0, 0x35),
        '~' to KeyStroke(SHIFT, 0x35),
        ',' to KeyStroke(0, 0x36),
        '<' to KeyStroke(SHIFT, 0x36),
        '.' to KeyStroke(0, 0x37),
        '>' to KeyStroke(SHIFT, 0x37),
        '/' to KeyStroke(0, 0x38),
        '?' to KeyStroke(SHIFT, 0x38),
        '!' to KeyStroke(SHIFT, 0x1e),
        '@' to KeyStroke(SHIFT, 0x1f),
        '#' to KeyStroke(SHIFT, 0x20),
        '$' to KeyStroke(SHIFT, 0x21),
        '%' to KeyStroke(SHIFT, 0x22),
        '^' to KeyStroke(SHIFT, 0x23),
        '&' to KeyStroke(SHIFT, 0x24),
        '*' to KeyStroke(SHIFT, 0x25),
        '(' to KeyStroke(SHIFT, 0x26),
        ')' to KeyStroke(SHIFT, 0x27),
    )
}
