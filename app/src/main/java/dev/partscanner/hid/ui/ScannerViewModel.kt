package dev.partscanner.hid.ui

import android.app.Application
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.partscanner.hid.barcode.BarcodeIdentity
import dev.partscanner.hid.barcode.BarcodeTextNormalizer
import dev.partscanner.hid.barcode.StableBarcodeTracker
import dev.partscanner.hid.bluetooth.BluetoothHidManager
import dev.partscanner.hid.domain.DetectedBarcode
import dev.partscanner.hid.domain.ScannerUiState
import dev.partscanner.hid.domain.ScannerBarcodeFormat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ScannerViewModel(application: Application) : AndroidViewModel(application) {
    private val hidManager = BluetoothHidManager(application)
    private val tracker = StableBarcodeTracker()
    private val preferences = application.getSharedPreferences(PreferencesName, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(preferences.loadScannerUiState())
    val uiState: StateFlow<ScannerUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            hidManager.status.collect { status ->
                _uiState.update { it.copy(status = status) }
            }
        }
        viewModelScope.launch {
            hidManager.connectionState.collect { connectionState ->
                _uiState.update { it.copy(hidConnectionState = connectionState) }
            }
        }
    }

    fun onPermissionsGranted() {
        hidManager.start()
    }

    fun onDetections(detections: List<DetectedBarcode>) {
        val stableDetections = tracker.update(detections, System.currentTimeMillis())
        val current = _uiState.value
        val selectedIndex = stableDetections.indexOfFirst {
            BarcodeIdentity.identityOf(it.barcode) == current.selectedIdentity
        }

        val selectedDetection = when {
            selectedIndex >= 0 -> stableDetections[selectedIndex]
            stableDetections.size == 1 -> stableDetections.first()
            else -> null
        }

        _uiState.update { state ->
            state.copy(
                detections = stableDetections,
                selectedIdentity = selectedDetection?.let { BarcodeIdentity.identityOf(it.barcode) },
                selectedBarcode = selectedDetection?.barcode,
                status = when {
                    stableDetections.isEmpty() -> state.status
                    selectedDetection != null -> "Selected barcode"
                    else -> "Tap a highlighted barcode"
                },
            )
        }
    }

    fun setBarcodeFormatEnabled(format: ScannerBarcodeFormat, enabled: Boolean) {
        tracker.clear()
        _uiState.update { state ->
            val updatedFormats = if (enabled) {
                state.enabledBarcodeFormats + format
            } else {
                state.enabledBarcodeFormats - format
            }
            if (updatedFormats.isEmpty()) {
                state.copy(status = "At least one barcode format must stay enabled")
            } else {
                preferences.edit()
                    .putStringSet(PrefEnabledBarcodeFormats, updatedFormats.map { it.name }.toSet())
                    .apply()
                state.copy(
                    enabledBarcodeFormats = updatedFormats,
                    detections = emptyList(),
                    selectedIdentity = null,
                    selectedBarcode = null,
                    status = "Barcode formats updated",
                )
            }
        }
    }

    fun selectDetection(index: Int) {
        val detection = _uiState.value.detections.getOrNull(index) ?: return
        _uiState.update {
            it.copy(
                selectedIdentity = BarcodeIdentity.identityOf(detection.barcode),
                selectedBarcode = detection.barcode,
                status = "Selected barcode",
            )
        }
    }

    fun onDetectorError(message: String) {
        _uiState.update { it.copy(status = "Scan error: $message") }
    }

    fun showHostChooser() {
        _uiState.update {
            it.copy(
                hosts = hidManager.bondedHosts(),
                showHostChooser = true,
            )
        }
    }

    fun dismissHostChooser() {
        _uiState.update { it.copy(showHostChooser = false) }
    }

    fun connectHost(device: BluetoothDevice) {
        dismissHostChooser()
        hidManager.connect(device)
    }

    fun sendSelectedBarcode() {
        if (!_uiState.value.isHidConnected) {
            showHostChooser()
            return
        }
        val barcode = _uiState.value.selectedBarcode ?: return
        if (_uiState.value.isSending) return
        val text = BarcodeTextNormalizer.normalizeForKeyboardWedge(barcode)
        viewModelScope.launch {
            _uiState.update { it.copy(isSending = true, status = "Sending barcode") }
            try {
                hidManager.typeLine(text)
                _uiState.update { it.copy(status = "Sent barcode") }
            } finally {
                _uiState.update { it.copy(isSending = false) }
            }
        }
    }

    private fun SharedPreferences.loadScannerUiState(): ScannerUiState {
        val enabledFormats = getStringSet(PrefEnabledBarcodeFormats, null)
            ?.mapNotNull { name -> ScannerBarcodeFormat.entries.find { it.name == name } }
            ?.toSet()
            ?.takeIf { it.isNotEmpty() }
            ?: ScannerBarcodeFormat.defaultEnabled
        return ScannerUiState(
            enabledBarcodeFormats = enabledFormats,
        )
    }

    private companion object {
        const val PreferencesName = "scanner_settings"
        const val PrefEnabledBarcodeFormats = "enabled_barcode_formats"
    }
}
