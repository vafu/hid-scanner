package dev.partscanner.hid.bluetooth

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothHidDevice
import android.bluetooth.BluetoothHidDeviceAppQosSettings
import android.bluetooth.BluetoothHidDeviceAppSdpSettings
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import dev.partscanner.hid.domain.BluetoothHost
import dev.partscanner.hid.domain.HidSpeed
import dev.partscanner.hid.domain.HidConnectionState
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import java.util.concurrent.Executor
import java.util.concurrent.Executors

class BluetoothHidManager(
    context: Context,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val appContext = context.applicationContext
    private val callbackExecutor: Executor = Executors.newSingleThreadExecutor()
    private val _status = MutableStateFlow("Bluetooth idle")
    val status: StateFlow<String> = _status
    private val _connectionState = MutableStateFlow(HidConnectionState.DISCONNECTED)
    val connectionState: StateFlow<HidConnectionState> = _connectionState

    private var hidDevice: BluetoothHidDevice? = null
    private var connectedHost: BluetoothDevice? = null
    private var profileRequested = false
    private var appRegistered = false
    private var registrationInFlight = false
    private var connectionInFlight = false

    private val profileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = proxy as BluetoothHidDevice
            _status.value = "HID service connected; registering keyboard"
            registerApp()
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile != BluetoothProfile.HID_DEVICE) return
            hidDevice = null
            connectedHost = null
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "HID service disconnected"
        }
    }

    private val callback = object : BluetoothHidDevice.Callback() {
        override fun onAppStatusChanged(pluggedDevice: BluetoothDevice?, registered: Boolean) {
            appRegistered = registered
            registrationInFlight = false
            _status.value = if (registered) "HID keyboard registered" else "HID keyboard unregistered"
        }

        override fun onConnectionStateChanged(device: BluetoothDevice, state: Int) {
            when (state) {
                BluetoothProfile.STATE_CONNECTED -> {
                    connectedHost = device
                    connectionInFlight = false
                    _connectionState.value = HidConnectionState.CONNECTED
                    _status.value = "Connected to ${device.safeName()}"
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    if (device == connectedHost) connectedHost = null
                    connectionInFlight = false
                    _connectionState.value = HidConnectionState.DISCONNECTED
                    _status.value = "Disconnected from ${device.safeName()}"
                }
                BluetoothProfile.STATE_CONNECTING -> {
                    connectionInFlight = true
                    _connectionState.value = HidConnectionState.CONNECTING
                    _status.value = "Connecting to ${device.safeName()}"
                }
                BluetoothProfile.STATE_DISCONNECTING -> {
                    _status.value = "Disconnecting from ${device.safeName()}"
                }
            }
        }
    }

    fun start() {
        val adapter = BluetoothAdapter.getDefaultAdapter()
        if (adapter == null) {
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "Bluetooth unavailable"
            return
        }
        if (!hasBluetoothConnectPermission()) {
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "Bluetooth permission required"
            return
        }
        if (profileRequested || hidDevice != null) {
            _status.value = if (appRegistered) "HID keyboard registered" else "HID service already requested"
            return
        }
        profileRequested = true
        _status.value = "Requesting HID Device profile"
        val requested = adapter.getProfileProxy(appContext, profileListener, BluetoothProfile.HID_DEVICE)
        if (!requested) {
            profileRequested = false
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "Could not request HID Device profile"
        }
    }

    fun bondedHosts(): List<BluetoothHost> {
        if (!hasBluetoothConnectPermission()) return emptyList()
        val adapter = BluetoothAdapter.getDefaultAdapter() ?: return emptyList()
        return adapter.bondedDevices
            .map { device -> BluetoothHost(device, device.safeName()) }
            .sortedBy { it.label.lowercase() }
    }

    @SuppressLint("MissingPermission")
    fun connect(device: BluetoothDevice) {
        val profile = hidDevice
        if (profile == null) {
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "HID service not ready"
            return
        }
        if (!hasBluetoothConnectPermission()) {
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "Bluetooth permission required"
            return
        }
        if (connectedHost == device) {
            _status.value = "Already connected to ${device.safeName()}"
            return
        }
        if (connectionInFlight) {
            _status.value = "Connection already in progress"
            return
        }
        connectionInFlight = true
        _connectionState.value = HidConnectionState.CONNECTING
        _status.value = "Connecting to ${device.safeName()}"
        if (!profile.connect(device)) {
            connectionInFlight = false
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "HID connect call was rejected for ${device.safeName()}"
        }
    }

    suspend fun typeLine(value: String, speed: HidSpeed) = withContext(ioDispatcher) {
        val host = connectedHost
        val profile = hidDevice
        if (host == null || profile == null) {
            _connectionState.value = HidConnectionState.DISCONNECTED
            _status.value = "No HID host connected"
            return@withContext
        }
        for (char in value) {
            HidKeyMapper.fromChar(char)?.let { stroke ->
                sendKey(profile, host, stroke, speed)
            }
        }
        sendKey(profile, host, KeyStroke(0, 0x28), speed)
    }

    @SuppressLint("MissingPermission")
    private fun registerApp() {
        val profile = hidDevice ?: return
        if (!hasBluetoothConnectPermission()) {
            _status.value = "Bluetooth permission required"
            return
        }
        if (appRegistered) {
            _status.value = "HID keyboard already registered"
            return
        }
        if (registrationInFlight) {
            _status.value = "HID registration already in progress"
            return
        }
        registrationInFlight = true
        val sdp = BluetoothHidDeviceAppSdpSettings(
            "SmartParts HID Scanner",
            "Phone camera barcode scanner",
            "Codex",
            SUBCLASS_KEYBOARD,
            KEYBOARD_DESCRIPTOR,
        )
        val qos: BluetoothHidDeviceAppQosSettings? = null
        val accepted = profile.registerApp(sdp, qos, qos, callbackExecutor, callback)
        _status.value = if (accepted) "HID registration submitted" else "HID registration rejected"
        if (!accepted) registrationInFlight = false
    }

    @SuppressLint("MissingPermission")
    private fun sendKey(
        profile: BluetoothHidDevice,
        host: BluetoothDevice,
        stroke: KeyStroke,
        speed: HidSpeed,
    ) {
        val down = byteArrayOf(stroke.modifier, 0, stroke.usage, 0, 0, 0, 0, 0)
        val up = byteArrayOf(0, 0, 0, 0, 0, 0, 0, 0)
        val downSent = profile.sendReport(host, 0, down)
        if (speed.keyDownMillis > 0) Thread.sleep(speed.keyDownMillis)
        val upSent = profile.sendReport(host, 0, up)
        if (speed.interKeyGapMillis > 0) Thread.sleep(speed.interKeyGapMillis)
        if (!downSent || !upSent) {
            _status.value = "HID send failed"
        }
    }

    private fun hasBluetoothConnectPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) ==
            PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun BluetoothDevice.safeName(): String {
        if (!hasBluetoothConnectPermission()) return "(unknown)"
        return name ?: address
    }

    private companion object {
        const val SUBCLASS_KEYBOARD: Byte = 0x40

        val KEYBOARD_DESCRIPTOR = byteArrayOf(
            0x05, 0x01, 0x09, 0x06, 0xA1.toByte(), 0x01,
            0x05, 0x07, 0x19, 0xE0.toByte(), 0x29, 0xE7.toByte(),
            0x15, 0x00, 0x25, 0x01, 0x75, 0x01, 0x95.toByte(), 0x08,
            0x81.toByte(), 0x02, 0x95.toByte(), 0x01, 0x75, 0x08,
            0x81.toByte(), 0x01, 0x95.toByte(), 0x05, 0x75, 0x01,
            0x05, 0x08, 0x19, 0x01, 0x29, 0x05, 0x91.toByte(), 0x02,
            0x95.toByte(), 0x01, 0x75, 0x03, 0x91.toByte(), 0x01,
            0x95.toByte(), 0x06, 0x75, 0x08, 0x15, 0x00, 0x25, 0x65,
            0x05, 0x07, 0x19, 0x00, 0x29, 0x65, 0x81.toByte(), 0x00,
            0xC0.toByte(),
        )
    }
}
