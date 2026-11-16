package com.example

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelUuid
import androidx.core.content.ContextCompat
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Strict BLE Connection Status:
 * - CONNECTED: Only when the ESP32 (NeuroGrip) BLE GATT connection AND Service/Characteristic UUID verification succeed.
 * - DISCONNECTED: When no connection exists or when GATT UUID verification fails.
 * - CONNECTING: While scanning (Phase 1 UUID or Phase 2 NeuroGrip name fallback), connecting GATT, or verifying UUIDs.
 * - RECONNECTING: When attempting to restore an unexpectedly lost connection to a previously verified NeuroGrip device.
 */
enum class BleConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING
}

/**
 * Tracks the active stage of the Dual-Method BLE Discovery & Verification pipeline.
 */
enum class BleDiscoveryPhase {
    IDLE,
    PHASE_1_SERVICE_UUID_SCAN,
    PHASE_2_NEUROGRIP_NAME_FALLBACK,
    GATT_SERVICE_VERIFICATION
}

/**
 * Records how the target ESP32 device was discovered.
 */
enum class BleDiscoveryMethod {
    NONE,
    SERVICE_UUID_PRIMARY,
    NEUROGRIP_NAME_FALLBACK,
    CACHED_VERIFIED_DEVICE
}

data class Esp32BleUiState(
    val connectionStatus: BleConnectionStatus = BleConnectionStatus.DISCONNECTED,
    val isScanning: Boolean = false,
    val discoveryPhase: BleDiscoveryPhase = BleDiscoveryPhase.IDLE,
    val discoveryMethod: BleDiscoveryMethod = BleDiscoveryMethod.NONE,
    val isGattVerified: Boolean = false,
    val verificationDetails: String = "Awaiting GATT Service & Characteristic verification",
    val targetDeviceName: String = Esp32BleManager.TARGET_DEVICE_NAME,
    val deviceName: String = "${Esp32BleManager.TARGET_DEVICE_NAME} (Not Detected)",
    val deviceAddress: String? = null,
    val rssi: Int? = null,
    val lastSentCommand: String = "None",
    val lastCommandSendSuccess: Boolean? = null,
    val lastSentTimestamp: String? = null,
    val lastReceivedResponse: String = "Waiting for NeuroGrip BLE notification...",
    val lastReceivedTimestamp: String? = null,
    val statusMessage: String = "Ready: Priority 1 Service UUID scan -> Priority 2 'NeuroGrip' name fallback",
    val errorMessage: String? = null,
    val isCommandInFlight: Boolean = false,
    val serviceUuid: String = Esp32BleManager.DEFAULT_SERVICE_UUID,
    val writeCharUuid: String = Esp32BleManager.DEFAULT_WRITE_CHAR_UUID,
    val notifyCharUuid: String = Esp32BleManager.DEFAULT_NOTIFY_CHAR_UUID,
    val commandOnPayload: String = "ON",
    val commandOffPayload: String = "OFF",
    val notifyEnabled: Boolean = false
)

/**
 * Native Android Kotlin Dual-Method BLE Manager for the "NeuroGrip" ESP32 device:
 * 1. Priority 1: Scans for ESP32 using its existing BLE Service UUID.
 * 2. Priority 2 (Fallback): If UUID-based scanning does not find the device, scans specifically
 *    for Bluetooth device name "NeuroGrip". Never connects to unrelated Bluetooth devices.
 * 3. Stops scanning immediately upon finding the target device and initiates a single-flight GATT connection.
 * 4. Performs GATT service discovery and strictly verifies that the expected existing Service UUID
 *    and Characteristic UUID exist.
 * 5. If UUID verification fails, immediately disconnects and displays an explicit error.
 * 6. Enables ON/OFF commands ONLY after successful GATT service and characteristic verification.
 */
class Esp32BleManager(context: Context) {

    companion object {
        // Exact target Bluetooth device name for fallback discovery
        const val TARGET_DEVICE_NAME = "NeuroGrip"

        // Existing standard ESP32 Nordic UART Service (NUS) UUIDs
        const val DEFAULT_SERVICE_UUID = "6E400001-B5A3-F393-E0A9-E50E24DCCA9E"
        const val DEFAULT_WRITE_CHAR_UUID = "6E400002-B5A3-F393-E0A9-E50E24DCCA9E"
        const val DEFAULT_NOTIFY_CHAR_UUID = "6E400003-B5A3-F393-E0A9-E50E24DCCA9E"

        // Existing standard Espressif ESP32 BLE Arduino firmware UUIDs
        const val ESPRESSIF_ALT_SERVICE_UUID = "4FAFC201-1FB5-459E-8FCC-C5C9C331914B"
        const val ESPRESSIF_ALT_CHAR_UUID = "BEB5483E-36E1-4688-B7F5-EA07361B26A8"

        // Standard Client Characteristic Configuration Descriptor (CCCD) for BLE Notifications
        const val CCCD_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        private const val PHASE_1_UUID_SCAN_TIMEOUT_MS = 4_000L
        private const val PHASE_2_NAME_SCAN_TIMEOUT_MS = 6_000L
        private const val RECONNECT_DELAY_MS = 2_000L
        private const val MAX_RECONNECT_ATTEMPTS = 5
        private const val RSSI_POLL_INTERVAL_MS = 3_000L
        private const val PREFS_NAME = "esp32_ble_prefs"

        @Volatile
        private var instance: Esp32BleManager? = null

        fun getInstance(context: Context): Esp32BleManager {
            return instance ?: synchronized(this) {
                instance ?: Esp32BleManager(context.applicationContext).also { instance = it }
            }
        }
    }

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val connectionLock = Any()

    private val bluetoothManager: BluetoothManager? =
        appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager
    private val bluetoothAdapter: BluetoothAdapter?
        get() = bluetoothManager?.adapter

    private var bluetoothGatt: BluetoothGatt? = null
    private var writeCharacteristic: BluetoothGattCharacteristic? = null
    private var notifyCharacteristic: BluetoothGattCharacteristic? = null
    private var lastVerifiedDevice: BluetoothDevice? = null

    private var userInitiatedDisconnect = false
    private var reconnectAttempts = 0
    private var isScanningInternal = false
    private var activeScanPhase: BleDiscoveryPhase = BleDiscoveryPhase.IDLE
    private var isConnectingOrConnectedInternal = false
    private var isWriteInProgress = false
    private var pendingCommandLabel: String? = null
    private var isReceiverRegistered = false

    private val _uiState = MutableStateFlow(
        Esp32BleUiState(
            serviceUuid = prefs.getString("service_uuid", DEFAULT_SERVICE_UUID) ?: DEFAULT_SERVICE_UUID,
            writeCharUuid = prefs.getString("write_char_uuid", DEFAULT_WRITE_CHAR_UUID) ?: DEFAULT_WRITE_CHAR_UUID,
            notifyCharUuid = prefs.getString("notify_char_uuid", DEFAULT_NOTIFY_CHAR_UUID) ?: DEFAULT_NOTIFY_CHAR_UUID,
            commandOnPayload = prefs.getString("cmd_on", "ON") ?: "ON",
            commandOffPayload = prefs.getString("cmd_off", "OFF") ?: "OFF"
        )
    )
    val uiState: StateFlow<Esp32BleUiState> = _uiState.asStateFlow()

    /**
     * Triggered when Phase 1 (Service UUID scan) finishes without finding the device.
     * Automatically transitions to Phase 2 ("NeuroGrip" device name fallback scan).
     */
    private val phase1TimeoutRunnable = Runnable {
        val shouldTransitionToNameFallback = synchronized(connectionLock) {
            isScanningInternal &&
                activeScanPhase == BleDiscoveryPhase.PHASE_1_SERVICE_UUID_SCAN &&
                _uiState.value.connectionStatus != BleConnectionStatus.CONNECTED &&
                !isConnectingOrConnectedInternal
        }
        if (shouldTransitionToNameFallback) {
            stopCurrentScannerCallOnly()
            startPhase2NeuroGripNameFallbackScan()
        }
    }

    /**
     * Triggered when Phase 2 ("NeuroGrip" name fallback scan) finishes without finding the device.
     */
    private val phase2TimeoutRunnable = Runnable {
        if (isScanningInternal && activeScanPhase == BleDiscoveryPhase.PHASE_2_NEUROGRIP_NAME_FALLBACK) {
            stopScanInternal()
            synchronized(connectionLock) {
                if (_uiState.value.connectionStatus != BleConnectionStatus.CONNECTED && !isConnectingOrConnectedInternal) {
                    updateState {
                        copy(
                            connectionStatus = BleConnectionStatus.DISCONNECTED,
                            isScanning = false,
                            discoveryPhase = BleDiscoveryPhase.IDLE,
                            isGattVerified = false,
                            statusMessage = "Scan complete: Neither Service UUID (${serviceUuid.take(8)}...) nor device '$TARGET_DEVICE_NAME' found.",
                            errorMessage = "ESP32 '$TARGET_DEVICE_NAME' not found via Service UUID or device name fallback. Ensure '$TARGET_DEVICE_NAME' is powered on and advertising BLE."
                        )
                    }
                }
            }
        }
    }

    private val rssiPollRunnable = object : Runnable {
        @SuppressLint("MissingPermission")
        override fun run() {
            val gatt = bluetoothGatt
            if (_uiState.value.connectionStatus == BleConnectionStatus.CONNECTED &&
                _uiState.value.isGattVerified &&
                gatt != null &&
                hasBlePermissions()
            ) {
                try {
                    gatt.readRemoteRssi()
                } catch (_: Exception) {
                }
                mainHandler.postDelayed(this, RSSI_POLL_INTERVAL_MS)
            }
        }
    }

    private val reconnectRunnable = Runnable {
        val targetDevice = lastVerifiedDevice
        if (!userInitiatedDisconnect && targetDevice != null && hasBlePermissions() && isBluetoothEnabled()) {
            connectToDeviceInternal(
                device = targetDevice,
                discoveryMethod = BleDiscoveryMethod.CACHED_VERIFIED_DEVICE,
                isAutoReconnect = true
            )
        } else {
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    isGattVerified = false,
                    statusMessage = "Auto-reconnect aborted (Bluetooth unavailable or device unverified)."
                )
            }
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)
                when (state) {
                    BluetoothAdapter.STATE_TURNING_OFF, BluetoothAdapter.STATE_OFF -> {
                        stopScanInternal()
                        releaseGattResources()
                        updateState {
                            copy(
                                connectionStatus = BleConnectionStatus.DISCONNECTED,
                                isScanning = false,
                                discoveryPhase = BleDiscoveryPhase.IDLE,
                                isGattVerified = false,
                                verificationDetails = "Bluetooth turned OFF",
                                isCommandInFlight = false,
                                notifyEnabled = false,
                                statusMessage = "Bluetooth is turned OFF",
                                errorMessage = "Bluetooth was disabled. Please turn on Bluetooth to connect to $TARGET_DEVICE_NAME."
                            )
                        }
                    }
                    BluetoothAdapter.STATE_ON -> {
                        updateState {
                            copy(
                                errorMessage = null,
                                statusMessage = "Bluetooth enabled. Ready to connect to $TARGET_DEVICE_NAME."
                            )
                        }
                    }
                }
            }
        }
    }

    init {
        registerBluetoothStateReceiver()
    }

    private fun registerBluetoothStateReceiver() {
        if (!isReceiverRegistered) {
            try {
                val filter = IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED)
                appContext.registerReceiver(bluetoothStateReceiver, filter)
                isReceiverRegistered = true
            } catch (_: Exception) {
            }
        }
    }

    fun hasBlePermissions(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
        } else {
            ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH) == PackageManager.PERMISSION_GRANTED &&
                ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.BLUETOOTH_ADMIN) == PackageManager.PERMISSION_GRANTED &&
                (ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(appContext, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED)
        }
    }

    fun isBluetoothEnabled(): Boolean {
        return bluetoothAdapter?.isEnabled == true
    }

    fun updateBleConfiguration(
        serviceUuid: String,
        writeCharUuid: String,
        notifyCharUuid: String,
        commandOnPayload: String,
        commandOffPayload: String
    ) {
        val cleanService = serviceUuid.trim()
        val cleanWrite = writeCharUuid.trim()
        val cleanNotify = notifyCharUuid.trim()
        val cleanOn = commandOnPayload.trim().ifEmpty { "ON" }
        val cleanOff = commandOffPayload.trim().ifEmpty { "OFF" }

        prefs.edit()
            .putString("service_uuid", cleanService)
            .putString("write_char_uuid", cleanWrite)
            .putString("notify_char_uuid", cleanNotify)
            .putString("cmd_on", cleanOn)
            .putString("cmd_off", cleanOff)
            .apply()

        updateState {
            copy(
                serviceUuid = cleanService,
                writeCharUuid = cleanWrite,
                notifyCharUuid = cleanNotify,
                commandOnPayload = cleanOn,
                commandOffPayload = cleanOff,
                statusMessage = "Updated existing ESP32 GATT UUID configuration",
                errorMessage = null
            )
        }
    }

    /**
     * Pure verification helper that checks whether a discovered map of GATT Service UUIDs
     * to Characteristic UUIDs satisfies the expected existing ESP32 Service UUID and Write Characteristic UUID.
     * Used by both live GATT discovery and deterministic unit tests.
     */
    fun verifyExpectedUuidsInGattMap(
        discoveredServices: Map<UUID, List<UUID>>,
        expectedServiceUuidStr: String = _uiState.value.serviceUuid,
        expectedWriteCharUuidStr: String = _uiState.value.writeCharUuid
    ): Pair<UUID, UUID>? {
        val candidateServiceUuids = listOfNotNull(
            parseUuidOrNull(expectedServiceUuidStr),
            parseUuidOrNull(DEFAULT_SERVICE_UUID),
            parseUuidOrNull(ESPRESSIF_ALT_SERVICE_UUID)
        ).distinct()

        val candidateWriteCharUuids = listOfNotNull(
            parseUuidOrNull(expectedWriteCharUuidStr),
            parseUuidOrNull(DEFAULT_WRITE_CHAR_UUID),
            parseUuidOrNull(ESPRESSIF_ALT_CHAR_UUID)
        ).distinct()

        for (serviceUuid in candidateServiceUuids) {
            val charsInService = discoveredServices.entries.firstOrNull { (k, _) ->
                k.toString().equals(serviceUuid.toString(), ignoreCase = true)
            }?.value ?: continue

            for (writeUuid in candidateWriteCharUuids) {
                val matchedWrite = charsInService.firstOrNull { c ->
                    c.toString().equals(writeUuid.toString(), ignoreCase = true)
                }
                if (matchedWrite != null) {
                    return Pair(serviceUuid, matchedWrite)
                }
            }
        }
        return null
    }

    /**
     * Initiates the Dual-Method BLE Connection workflow:
     * - Prevents duplicate scanning and connection attempts.
     * - Connects directly if a previously verified NeuroGrip device is cached, otherwise starts
     *   Priority 1 (Service UUID scan) followed by Priority 2 ("NeuroGrip" name fallback scan).
     */
    @SuppressLint("MissingPermission")
    fun connectOrScanAndConnect() {
        if (!hasBlePermissions()) {
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    isGattVerified = false,
                    errorMessage = "Bluetooth permissions denied. Please grant Bluetooth Scan & Connect permissions.",
                    statusMessage = "Missing Bluetooth permissions"
                )
            }
            return
        }

        if (!isBluetoothEnabled()) {
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    isGattVerified = false,
                    errorMessage = "Bluetooth is disabled on this device. Please turn on Bluetooth.",
                    statusMessage = "Bluetooth is turned OFF"
                )
            }
            return
        }

        synchronized(connectionLock) {
            val currentStatus = _uiState.value.connectionStatus
            if (currentStatus == BleConnectionStatus.CONNECTED ||
                currentStatus == BleConnectionStatus.CONNECTING ||
                isScanningInternal ||
                isConnectingOrConnectedInternal
            ) {
                // Avoid duplicate scanning and connection attempts
                return
            }
            userInitiatedDisconnect = false
            reconnectAttempts = 0
        }

        val cachedVerified = lastVerifiedDevice
        if (cachedVerified != null) {
            connectToDeviceInternal(
                device = cachedVerified,
                discoveryMethod = BleDiscoveryMethod.CACHED_VERIFIED_DEVICE,
                isAutoReconnect = false
            )
            return
        }

        startFastBleScan(autoConnectOnDetect = true)
    }

    /**
     * Starts the Dual-Method BLE scan:
     * Priority 1: Scan for ESP32 using its existing BLE Service UUID.
     * Priority 2: If not found within 4 seconds, automatically fall back to scanning for Bluetooth device name "NeuroGrip".
     */
    @SuppressLint("MissingPermission")
    fun startFastBleScan(autoConnectOnDetect: Boolean = true) {
        if (!hasBlePermissions()) {
            updateState {
                copy(
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    errorMessage = "Bluetooth permissions required to scan for $TARGET_DEVICE_NAME.",
                    statusMessage = "Permission denied"
                )
            }
            return
        }

        if (!isBluetoothEnabled()) {
            updateState {
                copy(
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    errorMessage = "Bluetooth is disabled. Enable Bluetooth first.",
                    statusMessage = "Bluetooth OFF"
                )
            }
            return
        }

        val scanner: BluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            updateState {
                copy(
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    errorMessage = "BLE Scanner hardware unavailable on this device.",
                    statusMessage = "BLE unavailable"
                )
            }
            return
        }

        synchronized(connectionLock) {
            if (isScanningInternal || isConnectingOrConnectedInternal) {
                return
            }
            if (_uiState.value.connectionStatus == BleConnectionStatus.CONNECTED) {
                stopScanInternal()
                return
            }
            isScanningInternal = true
            activeScanPhase = BleDiscoveryPhase.PHASE_1_SERVICE_UUID_SCAN
        }

        updateState {
            copy(
                connectionStatus = if (autoConnectOnDetect) BleConnectionStatus.CONNECTING else connectionStatus,
                isScanning = true,
                discoveryPhase = BleDiscoveryPhase.PHASE_1_SERVICE_UUID_SCAN,
                discoveryMethod = BleDiscoveryMethod.NONE,
                isGattVerified = false,
                verificationDetails = "Step 1/2: Scanning by existing BLE Service UUID...",
                errorMessage = null,
                statusMessage = "Priority 1: Scanning for ESP32 Service UUID (${serviceUuid.take(8)}...)..."
            )
        }

        mainHandler.removeCallbacks(phase1TimeoutRunnable)
        mainHandler.removeCallbacks(phase2TimeoutRunnable)
        mainHandler.postDelayed(phase1TimeoutRunnable, PHASE_1_UUID_SCAN_TIMEOUT_MS)

        try {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
                .build()

            val uuidFilters = buildServiceUuidScanFilters()
            scanner.startScan(uuidFilters, scanSettings, scanCallback)
        } catch (e: Exception) {
            synchronized(connectionLock) {
                isScanningInternal = false
                activeScanPhase = BleDiscoveryPhase.IDLE
            }
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    errorMessage = "Failed to start UUID BLE scan: ${e.localizedMessage}",
                    statusMessage = "BLE scan error"
                )
            }
        }
    }

    /**
     * Priority 2 Fallback: Scans specifically for Bluetooth device name "NeuroGrip"
     * when UUID-based scanning does not find the device (e.g., when 128-bit UUID is omitted from ADV packet).
     */
    @SuppressLint("MissingPermission")
    private fun startPhase2NeuroGripNameFallbackScan() {
        if (!hasBlePermissions() || !isBluetoothEnabled()) {
            stopScanInternal()
            return
        }

        val scanner: BluetoothLeScanner = bluetoothAdapter?.bluetoothLeScanner ?: run {
            stopScanInternal()
            return
        }

        synchronized(connectionLock) {
            if (_uiState.value.connectionStatus == BleConnectionStatus.CONNECTED || isConnectingOrConnectedInternal) {
                return
            }
            isScanningInternal = true
            activeScanPhase = BleDiscoveryPhase.PHASE_2_NEUROGRIP_NAME_FALLBACK
        }

        updateState {
            copy(
                connectionStatus = BleConnectionStatus.CONNECTING,
                isScanning = true,
                discoveryPhase = BleDiscoveryPhase.PHASE_2_NEUROGRIP_NAME_FALLBACK,
                verificationDetails = "Step 2/2: Service UUID not in ADV packet. Fallback scanning for '$TARGET_DEVICE_NAME'...",
                errorMessage = null,
                statusMessage = "Priority 2 Fallback: Scanning for Bluetooth device name '$TARGET_DEVICE_NAME'..."
            )
        }

        mainHandler.removeCallbacks(phase1TimeoutRunnable)
        mainHandler.removeCallbacks(phase2TimeoutRunnable)
        mainHandler.postDelayed(phase2TimeoutRunnable, PHASE_2_NAME_SCAN_TIMEOUT_MS)

        try {
            val scanSettings = ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setReportDelay(0L)
                .build()

            // Use unfiltered low-latency scan + strict exact "NeuroGrip" name check in callback
            // so devices that place the local name in the Scan Response packet are reliably found.
            scanner.startScan(emptyList<ScanFilter>(), scanSettings, scanCallback)
        } catch (e: Exception) {
            stopScanInternal()
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    errorMessage = "Fallback scan for '$TARGET_DEVICE_NAME' failed: ${e.localizedMessage}",
                    statusMessage = "Fallback scan error"
                )
            }
        }
    }

    private fun buildServiceUuidScanFilters(): List<ScanFilter> {
        val filters = mutableListOf<ScanFilter>()
        val candidateUuids = listOfNotNull(
            parseUuidOrNull(_uiState.value.serviceUuid),
            parseUuidOrNull(DEFAULT_SERVICE_UUID),
            parseUuidOrNull(ESPRESSIF_ALT_SERVICE_UUID)
        ).distinct()

        for (uuid in candidateUuids) {
            try {
                filters.add(
                    ScanFilter.Builder()
                        .setServiceUuid(ParcelUuid(uuid))
                        .build()
                )
            } catch (_: Exception) {
            }
        }
        return filters
    }

    @SuppressLint("MissingPermission")
    private fun stopCurrentScannerCallOnly() {
        try {
            if (hasBlePermissions() && isBluetoothEnabled()) {
                bluetoothAdapter?.bluetoothLeScanner?.stopScan(scanCallback)
            }
        } catch (_: Exception) {
        }
    }

    @SuppressLint("MissingPermission")
    private fun stopScanInternal() {
        mainHandler.removeCallbacks(phase1TimeoutRunnable)
        mainHandler.removeCallbacks(phase2TimeoutRunnable)
        if (!isScanningInternal && activeScanPhase == BleDiscoveryPhase.IDLE) return
        synchronized(connectionLock) {
            isScanningInternal = false
            activeScanPhase = BleDiscoveryPhase.IDLE
        }
        stopCurrentScannerCallOnly()
        updateState { copy(isScanning = false) }
    }

    private val scanCallback = object : ScanCallback() {
        @SuppressLint("MissingPermission")
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            val scanResult = result ?: return
            val device = scanResult.device ?: return
            val scanRecord = scanResult.scanRecord

            val currentPhase = synchronized(connectionLock) {
                if (!isScanningInternal || isConnectingOrConnectedInternal) return
                activeScanPhase
            }

            val targetServiceUuidStr = _uiState.value.serviceUuid
            val advertisedUuids: List<ParcelUuid> = scanRecord?.serviceUuids ?: emptyList()
            val advertisedName = (device.name ?: scanRecord?.deviceName ?: "").trim()

            // Check if advertised packet contains our existing ESP32 Service UUID
            val matchesExpectedServiceUuid = advertisedUuids.any { parcelUuid ->
                val u = parcelUuid.uuid.toString()
                u.equals(targetServiceUuidStr, ignoreCase = true) ||
                    u.equals(DEFAULT_SERVICE_UUID, ignoreCase = true) ||
                    u.equals(ESPRESSIF_ALT_SERVICE_UUID, ignoreCase = true)
            }

            // Check if advertised device name strictly matches "NeuroGrip" (never connect to unrelated devices)
            val matchesNeuroGripName = advertisedName.equals(TARGET_DEVICE_NAME, ignoreCase = true)

            val matchedMethod: BleDiscoveryMethod? = when (currentPhase) {
                BleDiscoveryPhase.PHASE_1_SERVICE_UUID_SCAN -> {
                    if (matchesExpectedServiceUuid) BleDiscoveryMethod.SERVICE_UUID_PRIMARY else null
                }
                BleDiscoveryPhase.PHASE_2_NEUROGRIP_NAME_FALLBACK -> {
                    when {
                        matchesExpectedServiceUuid -> BleDiscoveryMethod.SERVICE_UUID_PRIMARY
                        matchesNeuroGripName -> BleDiscoveryMethod.NEUROGRIP_NAME_FALLBACK
                        else -> null
                    }
                }
                else -> null
            }

            if (matchedMethod != null) {
                val resolvedName = advertisedName.ifBlank { TARGET_DEVICE_NAME }
                val rssiVal = scanResult.rssi

                // Stop scanning immediately after finding the target device
                stopScanInternal()

                updateState {
                    copy(
                        deviceName = resolvedName,
                        deviceAddress = device.address,
                        rssi = rssiVal,
                        discoveryMethod = matchedMethod,
                        discoveryPhase = BleDiscoveryPhase.GATT_SERVICE_VERIFICATION,
                        statusMessage = when (matchedMethod) {
                            BleDiscoveryMethod.SERVICE_UUID_PRIMARY ->
                                "Found '$resolvedName' via BLE Service UUID ($rssiVal dBm). Connecting GATT..."
                            BleDiscoveryMethod.NEUROGRIP_NAME_FALLBACK ->
                                "Found '$resolvedName' via Name Fallback ($rssiVal dBm). Connecting GATT to verify UUIDs..."
                            else ->
                                "Found '$resolvedName' ($rssiVal dBm). Connecting GATT..."
                        }
                    )
                }

                connectToDeviceInternal(
                    device = device,
                    discoveryMethod = matchedMethod,
                    isAutoReconnect = false
                )
            }
        }

        override fun onScanFailed(errorCode: Int) {
            stopScanInternal()
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    isGattVerified = false,
                    errorMessage = "BLE Scan failed (Error code: $errorCode)",
                    statusMessage = "Scan failed"
                )
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun connectToDeviceInternal(
        device: BluetoothDevice,
        discoveryMethod: BleDiscoveryMethod,
        isAutoReconnect: Boolean
    ) {
        if (!hasBlePermissions() || !isBluetoothEnabled()) return

        synchronized(connectionLock) {
            if (_uiState.value.connectionStatus == BleConnectionStatus.CONNECTED && _uiState.value.isGattVerified) {
                return
            }
            if (isConnectingOrConnectedInternal && !isAutoReconnect) {
                return
            }
            isConnectingOrConnectedInternal = true
        }

        // Guarantee scanning is stopped before initiating GATT connection
        stopScanInternal()

        val resolvedName = try {
            device.name?.takeIf { it.isNotBlank() } ?: _uiState.value.deviceName
        } catch (_: Exception) {
            _uiState.value.deviceName
        }

        updateState {
            copy(
                connectionStatus = if (isAutoReconnect) BleConnectionStatus.RECONNECTING else BleConnectionStatus.CONNECTING,
                discoveryPhase = BleDiscoveryPhase.GATT_SERVICE_VERIFICATION,
                discoveryMethod = discoveryMethod,
                isGattVerified = false,
                verificationDetails = "Connecting GATT & discovering services on $resolvedName...",
                deviceName = resolvedName,
                deviceAddress = device.address,
                errorMessage = null,
                statusMessage = if (isAutoReconnect) {
                    "Reconnecting to $resolvedName (Attempt $reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)..."
                } else {
                    "Connecting to $resolvedName (${device.address})..."
                }
            )
        }

        try {
            bluetoothGatt?.let { staleGatt ->
                try {
                    staleGatt.close()
                } catch (_: Exception) {
                }
            }
            bluetoothGatt = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                device.connectGatt(appContext, false, gattCallback, BluetoothDevice.TRANSPORT_LE)
            } else {
                device.connectGatt(appContext, false, gattCallback)
            }
        } catch (e: Exception) {
            synchronized(connectionLock) {
                isConnectingOrConnectedInternal = false
            }
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    isGattVerified = false,
                    errorMessage = "BLE GATT connection error: ${e.localizedMessage}",
                    statusMessage = "Connection failed"
                )
            }
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {

        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        try {
                            gatt.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH)
                        } catch (_: Exception) {
                        }

                        updateState {
                            copy(
                                connectionStatus = BleConnectionStatus.CONNECTING,
                                discoveryPhase = BleDiscoveryPhase.GATT_SERVICE_VERIFICATION,
                                isGattVerified = false,
                                verificationDetails = "GATT link active. Discovering & verifying Service/Characteristic UUIDs...",
                                statusMessage = "GATT connected. Discovering services to verify UUIDs..."
                            )
                        }
                        mainHandler.post {
                            try {
                                gatt.discoverServices()
                            } catch (_: Exception) {
                            }
                        }
                    } else {
                        handleDisconnectionOrFailure(gatt, status, "GATT connection failed with status $status")
                    }
                }

                BluetoothProfile.STATE_DISCONNECTED -> {
                    handleDisconnectionOrFailure(gatt, status, "BLE link disconnected (status $status)")
                }
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                handleDisconnectionOrFailure(gatt, status, "GATT service discovery failed (status $status)")
                return
            }

            // Build map of discovered Service UUID -> list of Characteristic UUIDs
            val discoveredMap = gatt.services.associate { svc ->
                svc.uuid to svc.characteristics.map { ch -> ch.uuid }
            }

            // Strictly verify that the expected existing Service UUID and Write Characteristic UUID exist
            val verifiedPair = verifyExpectedUuidsInGattMap(
                discoveredServices = discoveredMap,
                expectedServiceUuidStr = _uiState.value.serviceUuid,
                expectedWriteCharUuidStr = _uiState.value.writeCharUuid
            )

            if (verifiedPair == null) {
                handleUuidVerificationFailure(
                    gatt = gatt,
                    expectedServiceUuid = _uiState.value.serviceUuid,
                    expectedWriteCharUuid = _uiState.value.writeCharUuid
                )
                return
            }

            val (verifiedServiceUuid, verifiedWriteCharUuid) = verifiedPair
            val targetService = gatt.getService(verifiedServiceUuid)
            val foundWriteChar = targetService?.getCharacteristic(verifiedWriteCharUuid)

            // Verify that the characteristic actually supports BLE Write
            val isWritable = foundWriteChar != null && (
                (foundWriteChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0 ||
                    (foundWriteChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE) != 0
                )

            if (targetService == null || foundWriteChar == null || !isWritable) {
                handleUuidVerificationFailure(
                    gatt = gatt,
                    expectedServiceUuid = _uiState.value.serviceUuid,
                    expectedWriteCharUuid = _uiState.value.writeCharUuid
                )
                return
            }

            // Locate Notify Characteristic within the verified Service
            val configuredNotifyUuid = parseUuidOrNull(_uiState.value.notifyCharUuid)
            val foundNotifyChar = (configuredNotifyUuid?.let { targetService.getCharacteristic(it) })
                ?: (parseUuidOrNull(DEFAULT_NOTIFY_CHAR_UUID)?.let { targetService.getCharacteristic(it) })
                ?: (parseUuidOrNull(ESPRESSIF_ALT_CHAR_UUID)?.let { targetService.getCharacteristic(it) })
                ?: targetService.characteristics.firstOrNull { ch ->
                    (ch.properties and BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0 ||
                        (ch.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0
                }

            writeCharacteristic = foundWriteChar
            notifyCharacteristic = foundNotifyChar
            lastVerifiedDevice = gatt.device

            var notificationsSubscribed = false
            if (foundNotifyChar != null) {
                notificationsSubscribed = enableCharacteristicNotification(gatt, foundNotifyChar)
            }

            reconnectAttempts = 0
            val deviceDisplayName = try {
                gatt.device.name?.takeIf { it.isNotBlank() } ?: TARGET_DEVICE_NAME
            } catch (_: Exception) {
                TARGET_DEVICE_NAME
            }

            val verifiedSvcStr = targetService.uuid.toString().uppercase(Locale.US)
            val verifiedWriteStr = foundWriteChar.uuid.toString().uppercase(Locale.US)

            // Transition to CONNECTED and enable ON/OFF commands ONLY after UUID verification succeeds
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.CONNECTED,
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    isGattVerified = true,
                    verificationDetails = "Verified Service (${verifiedSvcStr.take(8)}...) & Char (${verifiedWriteStr.take(8)}...)",
                    deviceName = deviceDisplayName,
                    deviceAddress = gatt.device.address,
                    serviceUuid = verifiedSvcStr,
                    writeCharUuid = verifiedWriteStr,
                    notifyCharUuid = foundNotifyChar?.uuid?.toString()?.uppercase(Locale.US) ?: notifyCharUuid,
                    notifyEnabled = notificationsSubscribed,
                    errorMessage = null,
                    statusMessage = "CONNECTED to $deviceDisplayName — GATT Service & Characteristic UUIDs verified"
                )
            }

            mainHandler.removeCallbacks(rssiPollRunnable)
            mainHandler.post(rssiPollRunnable)
        }

        override fun onReadRemoteRssi(gatt: BluetoothGatt?, rssi: Int, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateState { copy(rssi = rssi) }
            }
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?,
            status: Int
        ) {
            val sentCmd = pendingCommandLabel ?: "Command"
            isWriteInProgress = false
            pendingCommandLabel = null
            val timestamp = formatTimestamp(System.currentTimeMillis())

            if (status == BluetoothGatt.GATT_SUCCESS) {
                updateState {
                    copy(
                        isCommandInFlight = false,
                        lastSentCommand = sentCmd,
                        lastCommandSendSuccess = true,
                        lastSentTimestamp = timestamp,
                        errorMessage = null,
                        statusMessage = "Command '$sentCmd' written to $TARGET_DEVICE_NAME ($timestamp)"
                    )
                }
            } else {
                updateState {
                    copy(
                        isCommandInFlight = false,
                        lastSentCommand = sentCmd,
                        lastCommandSendSuccess = false,
                        lastSentTimestamp = timestamp,
                        errorMessage = "BLE Write failed (GATT status: $status)",
                        statusMessage = "Failed to send '$sentCmd' to $TARGET_DEVICE_NAME"
                    )
                }
            }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray
        ) {
            handleIncomingNotificationBytes(value)
        }

        @Suppress("DEPRECATION")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt?,
            characteristic: BluetoothGattCharacteristic?
        ) {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                val bytes = characteristic?.value ?: return
                handleIncomingNotificationBytes(bytes)
            }
        }
    }

    /**
     * Immediately disconnects and cleans up GATT resources when the connected device does not
     * expose the expected existing Service UUID and Characteristic UUID.
     */
    @SuppressLint("MissingPermission")
    private fun handleUuidVerificationFailure(
        gatt: BluetoothGatt?,
        expectedServiceUuid: String,
        expectedWriteCharUuid: String
    ) {
        mainHandler.removeCallbacks(rssiPollRunnable)
        mainHandler.removeCallbacks(reconnectRunnable)
        isWriteInProgress = false
        pendingCommandLabel = null
        writeCharacteristic = null
        notifyCharacteristic = null
        // Clear cached device so we never auto-reconnect to a device that failed UUID verification
        lastVerifiedDevice = null

        try {
            if (hasBlePermissions()) {
                gatt?.disconnect()
            }
        } catch (_: Exception) {
        }
        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        if (bluetoothGatt == gatt) {
            bluetoothGatt = null
        }

        synchronized(connectionLock) {
            isConnectingOrConnectedInternal = false
        }

        val errMsg = "UUID verification failed: Expected Service UUID (${expectedServiceUuid.take(8)}...) and Characteristic UUID (${expectedWriteCharUuid.take(8)}...) were not found on the device. Disconnected."

        updateState {
            copy(
                connectionStatus = BleConnectionStatus.DISCONNECTED,
                isScanning = false,
                discoveryPhase = BleDiscoveryPhase.IDLE,
                isGattVerified = false,
                verificationDetails = "Verification FAILED — Disconnected from unverified device",
                isCommandInFlight = false,
                notifyEnabled = false,
                rssi = null,
                statusMessage = "Disconnected: GATT Service/Characteristic UUID verification failed.",
                errorMessage = errMsg
            )
        }
    }

    private fun handleIncomingNotificationBytes(bytes: ByteArray) {
        if (bytes.isEmpty()) return
        val decodedText = try {
            String(bytes, Charsets.UTF_8).trim()
        } catch (_: Exception) {
            bytes.joinToString(" ") { String.format("%02X", it) }
        }
        val timestamp = formatTimestamp(System.currentTimeMillis())
        updateState {
            copy(
                lastReceivedResponse = decodedText,
                lastReceivedTimestamp = timestamp,
                statusMessage = "$TARGET_DEVICE_NAME Notify ACK: $decodedText ($timestamp)"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun enableCharacteristicNotification(
        gatt: BluetoothGatt,
        characteristic: BluetoothGattCharacteristic
    ): Boolean {
        return try {
            val success = gatt.setCharacteristicNotification(characteristic, true)
            val cccdUuid = parseUuidOrNull(CCCD_UUID)
            val descriptor = cccdUuid?.let { characteristic.getDescriptor(it) }
            if (descriptor != null) {
                val enableValue = if ((characteristic.properties and BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
                    BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
                } else {
                    BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
                }
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    gatt.writeDescriptor(descriptor, enableValue)
                } else {
                    @Suppress("DEPRECATION")
                    descriptor.value = enableValue
                    @Suppress("DEPRECATION")
                    gatt.writeDescriptor(descriptor)
                }
            }
            success
        } catch (_: Exception) {
            false
        }
    }

    @SuppressLint("MissingPermission")
    private fun handleDisconnectionOrFailure(gatt: BluetoothGatt?, status: Int, reason: String) {
        mainHandler.removeCallbacks(rssiPollRunnable)
        isWriteInProgress = false
        pendingCommandLabel = null
        writeCharacteristic = null
        notifyCharacteristic = null

        try {
            gatt?.close()
        } catch (_: Exception) {
        }
        if (bluetoothGatt == gatt) {
            bluetoothGatt = null
        }

        synchronized(connectionLock) {
            isConnectingOrConnectedInternal = false
        }

        // Automatically handle unexpected disconnections for a previously verified NeuroGrip device
        if (!userInitiatedDisconnect &&
            lastVerifiedDevice != null &&
            reconnectAttempts < MAX_RECONNECT_ATTEMPTS &&
            isBluetoothEnabled() &&
            hasBlePermissions()
        ) {
            reconnectAttempts += 1
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.RECONNECTING,
                    discoveryPhase = BleDiscoveryPhase.GATT_SERVICE_VERIFICATION,
                    isGattVerified = false,
                    verificationDetails = "Connection lost. Re-verifying GATT on reconnect ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)...",
                    isCommandInFlight = false,
                    notifyEnabled = false,
                    statusMessage = "Connection lost. Reconnecting to $TARGET_DEVICE_NAME ($reconnectAttempts/$MAX_RECONNECT_ATTEMPTS)...",
                    errorMessage = reason
                )
            }
            mainHandler.removeCallbacks(reconnectRunnable)
            mainHandler.postDelayed(reconnectRunnable, RECONNECT_DELAY_MS)
        } else {
            updateState {
                copy(
                    connectionStatus = BleConnectionStatus.DISCONNECTED,
                    isScanning = false,
                    discoveryPhase = BleDiscoveryPhase.IDLE,
                    isGattVerified = false,
                    verificationDetails = if (userInitiatedDisconnect) {
                        "Disconnected by user"
                    } else {
                        "Disconnected — GATT verification required on next connect"
                    },
                    isCommandInFlight = false,
                    notifyEnabled = false,
                    rssi = null,
                    statusMessage = if (userInitiatedDisconnect) "Disconnected from $TARGET_DEVICE_NAME" else reason,
                    errorMessage = if (userInitiatedDisconnect) null else reason
                )
            }
        }
    }

    /**
     * Sends ON command to the verified ESP32 BLE Write Characteristic.
     * Enabled ONLY after successful GATT service and characteristic verification.
     */
    fun sendOnCommand(onComplete: ((Boolean, String) -> Unit)? = null) {
        val payload = _uiState.value.commandOnPayload
        sendRawCommand(payload, onComplete)
    }

    /**
     * Sends OFF command to the verified ESP32 BLE Write Characteristic.
     * Enabled ONLY after successful GATT service and characteristic verification.
     */
    fun sendOffCommand(onComplete: ((Boolean, String) -> Unit)? = null) {
        val payload = _uiState.value.commandOffPayload
        sendRawCommand(payload, onComplete)
    }

    /**
     * Sends a command string directly to the ESP32 Write Characteristic.
     * Strictly gated on both CONNECTED state and `isGattVerified == true`.
     */
    @SuppressLint("MissingPermission")
    fun sendRawCommand(command: String, onComplete: ((Boolean, String) -> Unit)? = null) {
        val currentState = _uiState.value
        if (currentState.connectionStatus != BleConnectionStatus.CONNECTED || !currentState.isGattVerified) {
            val msg = "Cannot send '$command': $TARGET_DEVICE_NAME GATT Service & Characteristic UUIDs are not verified."
            updateState {
                copy(
                    lastSentCommand = command,
                    lastCommandSendSuccess = false,
                    lastSentTimestamp = formatTimestamp(System.currentTimeMillis()),
                    errorMessage = msg
                )
            }
            onComplete?.invoke(false, msg)
            return
        }

        if (isWriteInProgress) {
            val msg = "Previous BLE command still in flight. Please wait."
            onComplete?.invoke(false, msg)
            return
        }

        val gatt = bluetoothGatt
        val writeChar = writeCharacteristic
        if (gatt == null || writeChar == null) {
            val msg = "Verified Write Characteristic (${currentState.writeCharUuid.take(8)}...) unavailable."
            updateState {
                copy(
                    lastSentCommand = command,
                    lastCommandSendSuccess = false,
                    errorMessage = msg
                )
            }
            onComplete?.invoke(false, msg)
            return
        }

        isWriteInProgress = true
        pendingCommandLabel = command
        val payloadBytes = command.toByteArray(Charsets.UTF_8)

        val supportsWriteWithResponse =
            (writeChar.properties and BluetoothGattCharacteristic.PROPERTY_WRITE) != 0
        val writeType = if (supportsWriteWithResponse) {
            BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
        } else {
            BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
        }

        updateState {
            copy(
                isCommandInFlight = true,
                errorMessage = null,
                statusMessage = "Sending '$command' to $TARGET_DEVICE_NAME via verified BLE Characteristic..."
            )
        }

        try {
            val initiated: Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val result = gatt.writeCharacteristic(writeChar, payloadBytes, writeType)
                result == BluetoothGatt.GATT_SUCCESS
            } else {
                @Suppress("DEPRECATION")
                writeChar.writeType = writeType
                @Suppress("DEPRECATION")
                writeChar.value = payloadBytes
                @Suppress("DEPRECATION")
                gatt.writeCharacteristic(writeChar)
            }

            if (!initiated) {
                isWriteInProgress = false
                pendingCommandLabel = null
                val timestamp = formatTimestamp(System.currentTimeMillis())
                val msg = "BLE writeCharacteristic initiation rejected by GATT stack."
                updateState {
                    copy(
                        isCommandInFlight = false,
                        lastSentCommand = command,
                        lastCommandSendSuccess = false,
                        lastSentTimestamp = timestamp,
                        errorMessage = msg,
                        statusMessage = "Failed to send '$command'"
                    )
                }
                onComplete?.invoke(false, msg)
            } else {
                onComplete?.invoke(true, "BLE command '$command' dispatched to $TARGET_DEVICE_NAME")
            }
        } catch (e: Exception) {
            isWriteInProgress = false
            pendingCommandLabel = null
            val msg = "BLE write error: ${e.localizedMessage}"
            updateState {
                copy(
                    isCommandInFlight = false,
                    lastSentCommand = command,
                    lastCommandSendSuccess = false,
                    errorMessage = msg
                )
            }
            onComplete?.invoke(false, msg)
        }
    }

    /**
     * Cleanly disconnects from the ESP32 and releases all BluetoothGatt resources.
     */
    @SuppressLint("MissingPermission")
    fun disconnect() {
        userInitiatedDisconnect = true
        reconnectAttempts = 0
        mainHandler.removeCallbacks(reconnectRunnable)
        mainHandler.removeCallbacks(rssiPollRunnable)
        stopScanInternal()
        releaseGattResources()

        updateState {
            copy(
                connectionStatus = BleConnectionStatus.DISCONNECTED,
                isScanning = false,
                discoveryPhase = BleDiscoveryPhase.IDLE,
                isGattVerified = false,
                verificationDetails = "Disconnected — GATT verification required on next connect",
                isCommandInFlight = false,
                notifyEnabled = false,
                rssi = null,
                errorMessage = null,
                statusMessage = "Disconnected from $TARGET_DEVICE_NAME"
            )
        }
    }

    @SuppressLint("MissingPermission")
    private fun releaseGattResources() {
        isWriteInProgress = false
        pendingCommandLabel = null
        writeCharacteristic = null
        notifyCharacteristic = null

        val gatt = bluetoothGatt
        bluetoothGatt = null
        if (gatt != null) {
            try {
                if (hasBlePermissions()) {
                    gatt.disconnect()
                }
            } catch (_: Exception) {
            }
            try {
                gatt.close()
            } catch (_: Exception) {
            }
        }
        synchronized(connectionLock) {
            isConnectingOrConnectedInternal = false
        }
    }

    private fun parseUuidOrNull(uuidStr: String): UUID? {
        return try {
            UUID.fromString(uuidStr.trim())
        } catch (_: Exception) {
            null
        }
    }

    private fun formatTimestamp(epochMs: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
        return sdf.format(Date(epochMs))
    }

    private inline fun updateState(reducer: Esp32BleUiState.() -> Esp32BleUiState) {
        _uiState.value = _uiState.value.reducer()
    }
}
