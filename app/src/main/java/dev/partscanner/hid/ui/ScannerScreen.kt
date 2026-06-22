package dev.partscanner.hid.ui

import android.Manifest
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import dev.partscanner.hid.barcode.BarcodeTextNormalizer
import dev.partscanner.hid.domain.BarcodeSendMode
import dev.partscanner.hid.domain.BluetoothHost
import dev.partscanner.hid.domain.DetectedBarcode
import dev.partscanner.hid.domain.HidSpeed
import dev.partscanner.hid.domain.HidConnectionState
import dev.partscanner.hid.domain.ScannerBarcodeFormat
import dev.partscanner.hid.domain.ScannerUiState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerScreen(
    state: ScannerUiState,
    onPermissionsGranted: () -> Unit,
    onDetections: (List<DetectedBarcode>) -> Unit,
    onDetectorError: (Throwable) -> Unit,
    onSelectDetection: (Int) -> Unit,
    onConnect: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onBarcodeFormatEnabledChanged: (ScannerBarcodeFormat, Boolean) -> Unit,
    onHidSpeedSelected: (HidSpeed) -> Unit,
    onSendModeSelected: (BarcodeSendMode) -> Unit,
    onToggleScanLock: () -> Unit,
    onSend: () -> Unit,
    onDismissHostChooser: () -> Unit,
    onHostSelected: (BluetoothHost) -> Unit,
) {
    val context = LocalContext.current
    val permissions = remember { requiredPermissions() }
    var showControls by rememberSaveable { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (permissions.all { grants[it] == true || hasPermission(context, it) }) {
            onPermissionsGranted()
        }
    }

    LaunchedEffect(Unit) {
        if (permissions.all { hasPermission(context, it) }) {
            onPermissionsGranted()
        } else {
            permissionLauncher.launch(permissions)
        }
    }

    Surface(color = Color.Black, modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize()) {
            CameraPreview(
                enabledBarcodeFormats = state.enabledBarcodeFormats,
                onDetections = onDetections,
                onError = onDetectorError,
                modifier = Modifier.fillMaxSize(),
            )
            ViewfinderOverlay(modifier = Modifier.fillMaxSize())
            BarcodeOverlay(
                detections = state.detections,
                selectedIdentity = state.selectedIdentity,
                onSelect = onSelectDetection,
                modifier = Modifier.fillMaxSize(),
            )

            SelectedBarcodeBanner(
                text = state.selectedOutputText(),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 18.dp, end = 76.dp),
            )

            IconButton(
                onClick = { showControls = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(top = 10.dp, end = 12.dp)
                    .background(Color.Black.copy(alpha = 0.48f), CircleShape),
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Open scanner controls",
                    tint = Color.White,
                )
            }

            ScanLockButton(
                locked = state.isScanLocked,
                onToggle = onToggleScanLock,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(top = 10.dp, start = 12.dp),
            )

            ScannerActionButton(
                selectedBarcodeAvailable = state.selectedBarcode != null,
                hidConnectionState = state.hidConnectionState,
                isSending = state.isSending,
                onConnect = onConnect,
                onSend = onSend,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 34.dp),
            )
        }
    }

    if (showControls) {
        ModalBottomSheet(
            sheetState = sheetState,
            onDismissRequest = { showControls = false },
            containerColor = Color(0xFF101114),
            contentColor = Color.White,
        ) {
            ScannerControlPane(
                state = state,
                onConnect = onConnect,
                onMakeDiscoverable = onMakeDiscoverable,
                onBarcodeFormatEnabledChanged = onBarcodeFormatEnabledChanged,
                onHidSpeedSelected = onHidSpeedSelected,
                onSendModeSelected = onSendModeSelected,
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 22.dp)
                    .padding(bottom = 24.dp),
            )
        }
    }

    if (state.showHostChooser) {
        HostChooserDialog(
            state = state,
            onDismiss = onDismissHostChooser,
            onHostSelected = onHostSelected,
        )
    }
}

private fun ScannerUiState.selectedOutputText(): String? {
    val barcode = selectedBarcode ?: return null
    return when (sendMode) {
        BarcodeSendMode.FULL_TEXT -> BarcodeTextNormalizer.normalizeForKeyboardWedge(barcode)
        BarcodeSendMode.PARSED_MPN -> parsedBarcode?.manufacturerPartNumber
            ?: BarcodeTextNormalizer.normalizeForKeyboardWedge(barcode)
    }
}

@Composable
private fun SelectedBarcodeBanner(
    text: String?,
    modifier: Modifier = Modifier,
) {
    val clipboard = LocalClipboardManager.current

    AnimatedVisibility(
        visible = text != null,
        enter = slideInVertically { -it } + fadeIn(),
        exit = slideOutVertically { -it } + fadeOut(),
        modifier = modifier,
    ) {
        Surface(
            color = Color(0xEE101114),
            contentColor = Color.White,
            shape = MaterialTheme.shapes.small,
            tonalElevation = 6.dp,
            modifier = Modifier.clickable {
                clipboard.setText(AnnotatedString(text.orEmpty()))
            },
        ) {
            Column(
                verticalArrangement = Arrangement.spacedBy(3.dp),
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(
                    text = "Selected",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color(0xFF4FDC78),
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = text.orEmpty(),
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    color = Color(0xFFECEFF4),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ScanLockButton(
    locked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        color = if (locked) Color(0xEE4FDC78) else Color.Black.copy(alpha = 0.48f),
        contentColor = if (locked) Color(0xFF06130B) else Color.White,
        shape = CircleShape,
        modifier = modifier.clickable(onClick = onToggle),
    ) {
        Text(
            text = if (locked) "Lock" else "Live",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ScannerActionButton(
    selectedBarcodeAvailable: Boolean,
    hidConnectionState: HidConnectionState,
    isSending: Boolean,
    onConnect: () -> Unit,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connected = hidConnectionState == HidConnectionState.CONNECTED
    val busy = isSending || hidConnectionState == HidConnectionState.CONNECTING
    val enabled = when {
        busy -> true
        connected -> selectedBarcodeAvailable
        else -> true
    }

    FilledIconButton(
        onClick = {
            if (busy) return@FilledIconButton
            if (connected) onSend() else onConnect()
        },
        enabled = enabled,
        shape = CircleShape,
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFF4FDC78),
            contentColor = Color(0xFF06130B),
            disabledContainerColor = Color.Black.copy(alpha = 0.48f),
            disabledContentColor = Color.White.copy(alpha = 0.38f),
        ),
        modifier = modifier
            .size(84.dp)
            .alpha(if (enabled) 1f else 0.82f),
    ) {
        if (busy) {
            CircularProgressIndicator(
                color = Color(0xFF06130B),
                strokeWidth = 3.dp,
                modifier = Modifier.size(34.dp),
            )
        } else if (!connected) {
            ConnectGlyph(modifier = Modifier.size(38.dp))
        } else {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send selected barcode",
                modifier = Modifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun ConnectGlyph(
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val stroke = Stroke(width = size.minDimension * 0.12f, cap = StrokeCap.Round)
        val left = center.copy(x = size.width * 0.28f)
        val right = center.copy(x = size.width * 0.72f)
        drawCircle(Color(0xFF06130B), radius = size.minDimension * 0.16f, center = left, style = stroke)
        drawCircle(Color(0xFF06130B), radius = size.minDimension * 0.16f, center = right, style = stroke)
        drawLine(
            color = Color(0xFF06130B),
            start = center.copy(x = size.width * 0.40f),
            end = center.copy(x = size.width * 0.60f),
            strokeWidth = size.minDimension * 0.12f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun ScannerControlPane(
    state: ScannerUiState,
    onConnect: () -> Unit,
    onMakeDiscoverable: () -> Unit,
    onBarcodeFormatEnabledChanged: (ScannerBarcodeFormat, Boolean) -> Unit,
    onHidSpeedSelected: (HidSpeed) -> Unit,
    onSendModeSelected: (BarcodeSendMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    var discoverableRequested by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Scanner",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = state.status,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFC9CED8),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(onClick = onConnect, modifier = Modifier.fillMaxWidth()) {
                Text("Connect HID")
            }
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Discoverable",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Request host pairing visibility",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFFB7BDC8),
                )
            }
            Switch(
                checked = discoverableRequested,
                onCheckedChange = { enabled ->
                    discoverableRequested = enabled
                    if (enabled) onMakeDiscoverable()
                },
            )
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        SendSettings(
            state = state,
            onHidSpeedSelected = onHidSpeedSelected,
            onSendModeSelected = onSendModeSelected,
        )

        HorizontalDivider(color = Color.White.copy(alpha = 0.12f))

        BarcodeFormatSettings(
            enabledFormats = state.enabledBarcodeFormats,
            onBarcodeFormatEnabledChanged = onBarcodeFormatEnabledChanged,
        )
    }
}

@Composable
private fun SendSettings(
    state: ScannerUiState,
    onHidSpeedSelected: (HidSpeed) -> Unit,
    onSendModeSelected: (BarcodeSendMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Send",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )

        OptionRow(
            label = "Mode",
            options = BarcodeSendMode.entries,
            selected = state.sendMode,
            labelOf = { it.label },
            onSelected = onSendModeSelected,
        )

        OptionRow(
            label = "Speed",
            options = HidSpeed.entries,
            selected = state.hidSpeed,
            labelOf = { it.label },
            onSelected = onHidSpeedSelected,
        )

        val parsed = state.parsedBarcode
        if (parsed != null) {
            Text(
                text = parsed.summary,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                color = Color(0xFFB7BDC8),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun <T> OptionRow(
    label: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelected: (T) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = Color(0xFFB7BDC8),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { option ->
                Button(
                    onClick = { onSelected(option) },
                    enabled = option != selected,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(
                        text = labelOf(option),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodeFormatSettings(
    enabledFormats: Set<ScannerBarcodeFormat>,
    onBarcodeFormatEnabledChanged: (ScannerBarcodeFormat, Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "Barcode formats",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "${enabledFormats.size} enabled",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFFB7BDC8),
            )
        }

        ScannerBarcodeFormat.entries.chunked(2).forEach { rowFormats ->
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                rowFormats.forEach { format ->
                    BarcodeFormatToggle(
                        format = format,
                        checked = format in enabledFormats,
                        canDisable = enabledFormats.size > 1,
                        onBarcodeFormatEnabledChanged = onBarcodeFormatEnabledChanged,
                        modifier = Modifier.weight(1f),
                    )
                }
                if (rowFormats.size == 1) {
                    Box(modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun BarcodeFormatToggle(
    format: ScannerBarcodeFormat,
    checked: Boolean,
    canDisable: Boolean,
    onBarcodeFormatEnabledChanged: (ScannerBarcodeFormat, Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = modifier,
    ) {
        Text(
            text = format.label,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFECEFF4),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Switch(
            checked = checked,
            enabled = checked.not() || canDisable,
            onCheckedChange = { enabled -> onBarcodeFormatEnabledChanged(format, enabled) },
        )
    }
}

@Composable
private fun HostChooserDialog(
    state: ScannerUiState,
    onDismiss: () -> Unit,
    onHostSelected: (BluetoothHost) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
        title = { Text("Connect HID") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (state.hosts.isEmpty()) {
                    Text("Pair this phone with the host first, then retry.")
                } else {
                    state.hosts.forEach { host ->
                        TextButton(onClick = { onHostSelected(host) }) {
                            Text(host.label, modifier = Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        },
    )
}

private fun requiredPermissions(): Array<String> {
    val permissions = mutableListOf(Manifest.permission.CAMERA)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        permissions += Manifest.permission.BLUETOOTH_CONNECT
        permissions += Manifest.permission.BLUETOOTH_SCAN
        permissions += Manifest.permission.BLUETOOTH_ADVERTISE
    }
    return permissions.toTypedArray()
}

private fun hasPermission(context: android.content.Context, permission: String): Boolean {
    return ContextCompat.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
}
