package dev.partscanner.hid.ui

import android.content.Context
import android.content.SharedPreferences
import dev.partscanner.hid.domain.BarcodeSendMode
import dev.partscanner.hid.domain.HidSpeed
import dev.partscanner.hid.domain.ScannerBarcodeFormat

class ScannerSettingsRepository(context: Context) {
    private val preferences = context.applicationContext
        .getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)

    fun load(): ScannerSettings {
        return ScannerSettings(
            enabledBarcodeFormats = preferences.loadBarcodeFormats(),
            hidSpeed = preferences.loadEnum(PrefHidSpeed, HidSpeed.NORMAL),
            sendMode = preferences.loadEnum(PrefSendMode, BarcodeSendMode.FULL_TEXT),
        )
    }

    fun saveBarcodeFormats(formats: Set<ScannerBarcodeFormat>) {
        preferences.edit()
            .putStringSet(PrefEnabledBarcodeFormats, formats.map { it.name }.toSet())
            .apply()
    }

    fun saveHidSpeed(speed: HidSpeed) {
        preferences.edit().putString(PrefHidSpeed, speed.name).apply()
    }

    fun saveSendMode(mode: BarcodeSendMode) {
        preferences.edit().putString(PrefSendMode, mode.name).apply()
    }

    private fun SharedPreferences.loadBarcodeFormats(): Set<ScannerBarcodeFormat> {
        return getStringSet(PrefEnabledBarcodeFormats, null)
            ?.mapNotNull { name -> ScannerBarcodeFormat.entries.find { it.name == name } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: ScannerBarcodeFormat.defaultEnabled
    }

    private inline fun <reified T : Enum<T>> SharedPreferences.loadEnum(key: String, default: T): T {
        val name = getString(key, null) ?: return default
        return enumValues<T>().firstOrNull { it.name == name } ?: default
    }

    private companion object {
        const val PreferencesName = "scanner_settings"
        const val PrefEnabledBarcodeFormats = "enabled_barcode_formats"
        const val PrefHidSpeed = "hid_speed"
        const val PrefSendMode = "send_mode"
    }
}

data class ScannerSettings(
    val enabledBarcodeFormats: Set<ScannerBarcodeFormat>,
    val hidSpeed: HidSpeed,
    val sendMode: BarcodeSendMode,
)
