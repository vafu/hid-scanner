package dev.partscanner.hid

import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import dev.partscanner.hid.ui.ScannerScreen
import dev.partscanner.hid.ui.ScannerViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: ScannerViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by viewModel.uiState.collectAsState()
            MaterialTheme(colorScheme = scannerColorScheme()) {
                ScannerScreen(
                    state = state,
                    onPermissionsGranted = viewModel::onPermissionsGranted,
                    onDetections = viewModel::onDetections,
                    onDetectorError = { error -> viewModel.onDetectorError(error.message ?: "Scanner error") },
                    onSelectDetection = viewModel::selectDetection,
                    onConnect = viewModel::showHostChooser,
                    onMakeDiscoverable = ::makeDiscoverable,
                    onBarcodeFormatEnabledChanged = viewModel::setBarcodeFormatEnabled,
                    onHidSpeedSelected = viewModel::setHidSpeed,
                    onSendModeSelected = viewModel::setSendMode,
                    onToggleScanLock = viewModel::toggleScanLock,
                    onSend = viewModel::sendSelectedBarcode,
                    onDismissHostChooser = viewModel::dismissHostChooser,
                    onHostSelected = { host -> viewModel.connectHost(host.device) },
                )
            }
        }
    }

    private fun makeDiscoverable() {
        val intent = Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE)
            .putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300)
        startActivity(intent)
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_VOLUME_UP || event.keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            if (event.action == KeyEvent.ACTION_UP && event.repeatCount == 0) {
                viewModel.sendSelectedBarcode()
            }
            return true
        }
        return super.dispatchKeyEvent(event)
    }
}

private fun scannerColorScheme(): ColorScheme {
    return darkColorScheme(
        primary = Color(0xFF79A8FF),
        secondary = Color(0xFF4FDC78),
        background = Color.Black,
        surface = Color(0xFF101114),
    )
}
