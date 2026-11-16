package com.example

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.BluetoothDisabled
import androidx.compose.material.icons.filled.BluetoothSearching
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsInputAntenna
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel

@Composable
fun ConnectDevicesScreen(
    onBack: () -> Unit,
    onContinue: () -> Unit,
    dashboardViewModel: DashboardViewModel = viewModel()
) {
    val primaryBlue = Color(0xFF0052CC)
    val textGray = Color(0xFF727785)
    val greenConnected = Color(0xFF15803D)
    val amberConnecting = Color(0xFFD97706)
    val redDisconnected = Color(0xFFD32F2F)

    val context = LocalContext.current
    val bleManager = remember(context) { dashboardViewModel.bindBleManager(context) }
    val bleState by bleManager.uiState.collectAsStateWithLifecycle()

    var showUuidConfig by remember { mutableStateOf(false) }
    var serviceUuidInput by remember(bleState.serviceUuid) { mutableStateOf(bleState.serviceUuid) }
    var writeUuidInput by remember(bleState.writeCharUuid) { mutableStateOf(bleState.writeCharUuid) }
    var notifyUuidInput by remember(bleState.notifyCharUuid) { mutableStateOf(bleState.notifyCharUuid) }
    var cmdOnInput by remember(bleState.commandOnPayload) { mutableStateOf(bleState.commandOnPayload) }
    var cmdOffInput by remember(bleState.commandOffPayload) { mutableStateOf(bleState.commandOffPayload) }

    // Real Bluetooth Connected checks for auxiliary sensors
    var isEegConnected by remember { mutableStateOf(false) }
    var isEmgConnected by remember { mutableStateOf(false) }

    val requiredBlePermissions = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT
            )
        } else {
            arrayOf(
                Manifest.permission.BLUETOOTH,
                Manifest.permission.BLUETOOTH_ADMIN,
                Manifest.permission.ACCESS_FINE_LOCATION
            )
        }
    }

    val enableBluetoothLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        if (bleManager.isBluetoothEnabled()) {
            bleManager.connectOrScanAndConnect()
        } else {
            Toast.makeText(context, "Please enable Bluetooth to connect to ESP32", Toast.LENGTH_SHORT).show()
        }
    }

    val blePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grantResults ->
        val allGranted = grantResults.values.all { it }
        if (allGranted || bleManager.hasBlePermissions()) {
            if (!bleManager.isBluetoothEnabled()) {
                enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
            } else {
                bleManager.connectOrScanAndConnect()
            }
        } else {
            Toast.makeText(
                context,
                "Bluetooth Scan & Connect permissions are required for ESP32 BLE Fast Connection.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    // Automatically check auxiliary sensors and initiate fast BLE scan when permissions are already granted
    LaunchedEffect(Unit) {
        isEegConnected = checkBluetoothDeviceConnected(context, "MindWave") ||
            checkBluetoothDeviceConnected(context, "NeuroSky")
        isEmgConnected = checkBluetoothDeviceConnected(context, "EMG") ||
            checkBluetoothDeviceConnected(context, "Muscle")
        if (bleManager.hasBlePermissions() &&
            bleManager.isBluetoothEnabled() &&
            bleState.connectionStatus == BleConnectionStatus.DISCONNECTED
        ) {
            bleManager.startFastBleScan(autoConnectOnDetect = true)
        }
    }

    val isEsp32BleConnected = bleState.connectionStatus == BleConnectionStatus.CONNECTED && bleState.isGattVerified
    val esp32StatusText = bleState.connectionStatus.name // CONNECTED, DISCONNECTED, CONNECTING, RECONNECTING
    val eegState = if (isEegConnected) "CONNECTED" else "DISCONNECTED"
    val emgState = if (isEmgConnected) "CONNECTED" else "DISCONNECTED"
    val handState = if (isEsp32BleConnected) "CONNECTED" else "DISCONNECTED"

    val discoveryMethodText = when (bleState.discoveryMethod) {
        BleDiscoveryMethod.SERVICE_UUID_PRIMARY -> "UUID Scan (Primary)"
        BleDiscoveryMethod.NEUROGRIP_NAME_FALLBACK -> "Name 'NeuroGrip' (Fallback)"
        BleDiscoveryMethod.CACHED_VERIFIED_DEVICE -> "Verified Device Cache"
        BleDiscoveryMethod.NONE -> when (bleState.discoveryPhase) {
            BleDiscoveryPhase.PHASE_1_SERVICE_UUID_SCAN -> "Phase 1: UUID Scan"
            BleDiscoveryPhase.PHASE_2_NEUROGRIP_NAME_FALLBACK -> "Phase 2: 'NeuroGrip' Scan"
            BleDiscoveryPhase.GATT_SERVICE_VERIFICATION -> "Verifying GATT UUIDs"
            BleDiscoveryPhase.IDLE -> "Dual-Method Ready"
        }
    }

    val statusColor: (String) -> Color = { state ->
        when (state) {
            "CONNECTED", "Healthy", "Ready" -> greenConnected
            "CONNECTING", "RECONNECTING" -> amberConnecting
            else -> redDisconnected
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter
    ) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .widthIn(max = 640.dp)
            .padding(horizontal = 24.dp)
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(android.R.drawable.ic_menu_revert),
                    contentDescription = "Back",
                    tint = primaryBlue
                )
            }

            Text(
                text = "FIRST-TIME SETUP • BLE FAST CONNECT",
                style = MaterialTheme.typography.labelSmall,
                color = textGray,
                letterSpacing = 1.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp)
            )

            IconButton(onClick = { showUuidConfig = !showUuidConfig }) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Configure ESP32 UUIDs",
                    tint = primaryBlue
                )
            }
        }

        Text(
            text = "Connect Your Devices",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = Color(0xFF1A1B1F),
            modifier = Modifier.padding(bottom = 8.dp)
        )

        Text(
            text = "Dual-method BLE connection for NeuroGrip (ESP32): Priority 1 scans by existing BLE Service UUID; Priority 2 falls back to device name \"NeuroGrip\" and strictly verifies GATT Service & Characteristic UUIDs.",
            style = MaterialTheme.typography.bodyMedium,
            color = textGray,
            modifier = Modifier.padding(bottom = 20.dp)
        )

        // Progress Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    "STEP 3 OF 5",
                    style = MaterialTheme.typography.labelSmall,
                    color = primaryBlue,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .height(4.dp)
                        .width(120.dp)
                        .background(primaryBlue, RoundedCornerShape(2.dp))
                )
            }
            Text(
                if (isEsp32BleConnected) "ESP32 Connected ✓" else "60% Complete",
                style = MaterialTheme.typography.labelSmall,
                color = if (isEsp32BleConnected) greenConnected else primaryBlue,
                fontWeight = FontWeight.Bold
            )
        }

        // =========================================================================
        // ESP32 BLE FAST CONNECTION LIVE CONSOLE CARD
        // =========================================================================
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 20.dp)
                .testTag("esp32_ble_fast_connection_card"),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(
                containerColor = if (isEsp32BleConnected) Color(0xFFF0FDF4) else Color(0xFFF8FAFC)
            ),
            border = BorderStroke(
                1.5.dp,
                when (bleState.connectionStatus) {
                    BleConnectionStatus.CONNECTED -> Color(0xFF22C55E)
                    BleConnectionStatus.CONNECTING, BleConnectionStatus.RECONNECTING -> Color(0xFFF59E0B)
                    BleConnectionStatus.DISCONNECTED -> Color(0xFFCBD5E1)
                }
            )
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                // Header Row with Live Connection Status Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .background(
                                    when (bleState.connectionStatus) {
                                        BleConnectionStatus.CONNECTED -> Color(0xFFDCFCE7)
                                        BleConnectionStatus.CONNECTING, BleConnectionStatus.RECONNECTING -> Color(0xFFFEF3C7)
                                        BleConnectionStatus.DISCONNECTED -> Color(0xFFE0E7FF)
                                    },
                                    RoundedCornerShape(12.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            val bleIcon = when (bleState.connectionStatus) {
                                BleConnectionStatus.CONNECTED -> Icons.Default.BluetoothConnected
                                BleConnectionStatus.CONNECTING, BleConnectionStatus.RECONNECTING -> Icons.Default.BluetoothSearching
                                BleConnectionStatus.DISCONNECTED -> Icons.Default.Bluetooth
                            }
                            Icon(
                                imageVector = bleIcon,
                                contentDescription = "ESP32 BLE Status",
                                tint = statusColor(esp32StatusText),
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "NeuroGrip • ESP32 Dual-Method BLE",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A)
                            )
                            Text(
                                text = bleState.deviceName + (bleState.deviceAddress?.let { " ($it)" } ?: ""),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF475569),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.testTag("esp32_device_name_text")
                            )
                        }
                    }

                    // Strict Live Connection Status Badge
                    Surface(
                        color = statusColor(esp32StatusText).copy(alpha = 0.14f),
                        shape = RoundedCornerShape(999.dp),
                        border = BorderStroke(1.dp, statusColor(esp32StatusText))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .background(statusColor(esp32StatusText), CircleShape)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = esp32StatusText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = statusColor(esp32StatusText),
                                fontSize = 11.sp,
                                modifier = Modifier.testTag("esp32_connection_status_badge")
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Telemetry Metrics Row: Discovery Method, GATT UUID Verification, Signal Strength (RSSI)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BleMetricTile(
                        label = "DISCOVERY",
                        value = discoveryMethodText,
                        valueColor = primaryBlue,
                        modifier = Modifier.weight(1f)
                    )
                    BleMetricTile(
                        label = "GATT UUID VERIFY",
                        value = if (bleState.isGattVerified) "VERIFIED ✓" else "UNVERIFIED",
                        valueColor = if (bleState.isGattVerified) greenConnected else textGray,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("esp32_gatt_verified_badge")
                    )
                    BleMetricTile(
                        label = "SIGNAL (RSSI)",
                        value = bleState.rssi?.let { "$it dBm" } ?: "-- dBm",
                        valueColor = if (bleState.rssi != null) primaryBlue else textGray,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("esp32_rssi_text")
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                // GATT Verification details strip
                Text(
                    text = bleState.verificationDetails,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (bleState.isGattVerified) greenConnected else Color(0xFF475569),
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                    modifier = Modifier.testTag("esp32_verification_details_text")
                )

                Spacer(modifier = Modifier.height(10.dp))

                // Live Status / Error Message Banner
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = if (bleState.errorMessage != null) Color(0xFFFEF2F2) else Color.White,
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(
                        1.dp,
                        if (bleState.errorMessage != null) Color(0xFFFECACA) else Color(0xFFE2E8F0)
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (bleState.connectionStatus == BleConnectionStatus.CONNECTING ||
                            bleState.connectionStatus == BleConnectionStatus.RECONNECTING ||
                            bleState.isScanning
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = primaryBlue
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                        }
                        Text(
                            text = bleState.errorMessage ?: bleState.statusMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (bleState.errorMessage != null) redDisconnected else Color(0xFF334155),
                            fontSize = 12.sp
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Primary Connect / Disconnect Button
                val isBusyConnecting = bleState.connectionStatus == BleConnectionStatus.CONNECTING ||
                    bleState.connectionStatus == BleConnectionStatus.RECONNECTING

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            if (isEsp32BleConnected || isBusyConnecting) {
                                bleManager.disconnect()
                            } else {
                                if (!bleManager.hasBlePermissions()) {
                                    blePermissionLauncher.launch(requiredBlePermissions)
                                } else if (!bleManager.isBluetoothEnabled()) {
                                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                } else {
                                    bleManager.connectOrScanAndConnect()
                                }
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("esp32_connect_disconnect_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = when (bleState.connectionStatus) {
                                BleConnectionStatus.CONNECTED -> redDisconnected
                                BleConnectionStatus.CONNECTING, BleConnectionStatus.RECONNECTING -> amberConnecting
                                BleConnectionStatus.DISCONNECTED -> primaryBlue
                            }
                        )
                    ) {
                        Icon(
                            imageVector = if (isEsp32BleConnected) Icons.Default.BluetoothDisabled else Icons.Default.BluetoothConnected,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = when (bleState.connectionStatus) {
                                BleConnectionStatus.CONNECTED -> "DISCONNECT ESP32"
                                BleConnectionStatus.CONNECTING -> "CONNECTING... (TAP TO CANCEL)"
                                BleConnectionStatus.RECONNECTING -> "RECONNECTING... (STOP)"
                                BleConnectionStatus.DISCONNECTED -> "CONNECT ESP32 (BLE)"
                            },
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                            textAlign = TextAlign.Center
                        )
                    }

                    if (!isEsp32BleConnected) {
                        OutlinedIconButton(
                            onClick = {
                                if (!bleManager.hasBlePermissions()) {
                                    blePermissionLauncher.launch(requiredBlePermissions)
                                } else if (!bleManager.isBluetoothEnabled()) {
                                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                                } else {
                                    bleManager.startFastBleScan(autoConnectOnDetect = true)
                                }
                            },
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("esp32_rescan_button"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, primaryBlue)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Scan for ESP32 BLE",
                                tint = primaryBlue
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider(color = Color(0xFFE2E8F0))
                Spacer(modifier = Modifier.height(14.dp))

                // =========================================================================
                // FAST SIGNAL / DATA SENDING (ON / OFF BUTTONS)
                // =========================================================================
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "FAST SIGNAL / BLE WRITE COMMANDS",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF475569),
                        letterSpacing = 0.8.sp,
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = if (isEsp32BleConnected) "GATT UUIDs Verified • Ready" else "Requires Verified GATT UUIDs",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (isEsp32BleConnected) greenConnected else textGray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 10.sp,
                        textAlign = TextAlign.End
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            dashboardViewModel.sendBleOnCommand(context)
                        },
                        enabled = isEsp32BleConnected && bleState.isGattVerified && !bleState.isCommandInFlight,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("esp32_cmd_on_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF16A34A),
                            disabledContainerColor = Color(0xFFCBD5E1)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Send ON",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "ON (${bleState.commandOnPayload})",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }

                    Button(
                        onClick = {
                            dashboardViewModel.sendBleOffCommand(context)
                        },
                        enabled = isEsp32BleConnected && bleState.isGattVerified && !bleState.isCommandInFlight,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 50.dp)
                            .testTag("esp32_cmd_off_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFFDC2626),
                            disabledContainerColor = Color(0xFFCBD5E1)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Send OFF",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "OFF (${bleState.commandOffPayload})",
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Last Sent Command & Last Received Response Telemetry Boxes
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "LAST SENT COMMAND",
                                style = MaterialTheme.typography.labelSmall,
                                color = textGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = bleState.lastSentCommand,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.ExtraBold,
                                color = Color(0xFF0F172A),
                                modifier = Modifier.testTag("esp32_last_sent_command_text")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            val deliveryLabel = when (bleState.lastCommandSendSuccess) {
                                true -> "Sent ✓ (${bleState.lastSentTimestamp ?: ""})"
                                false -> "Send Failed ✗"
                                null -> "No command sent yet"
                            }
                            val deliveryColor = when (bleState.lastCommandSendSuccess) {
                                true -> greenConnected
                                false -> redDisconnected
                                null -> textGray
                            }
                            Text(
                                text = deliveryLabel,
                                style = MaterialTheme.typography.bodySmall,
                                color = deliveryColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }

                    Surface(
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "LAST RECEIVED RESPONSE",
                                style = MaterialTheme.typography.labelSmall,
                                color = textGray,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = bleState.lastReceivedResponse,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = if (bleState.lastReceivedTimestamp != null) primaryBlue else Color(0xFF475569),
                                maxLines = 2,
                                modifier = Modifier.testTag("esp32_last_received_response_text")
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = bleState.lastReceivedTimestamp?.let { "BLE Notify @ $it" } ?: "Awaiting BLE Notify",
                                style = MaterialTheme.typography.bodySmall,
                                color = textGray,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Expandable ESP32 Firmware GATT UUID Configuration
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showUuidConfig = !showUuidConfig }
                        .padding(vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SettingsInputAntenna,
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "ESP32 Firmware GATT UUIDs & Payload Config",
                            style = MaterialTheme.typography.labelSmall,
                            color = primaryBlue,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Icon(
                        imageVector = if (showUuidConfig) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle UUID Config",
                        tint = primaryBlue
                    )
                }

                AnimatedVisibility(visible = showUuidConfig) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Matches your existing ESP32 BLE firmware without modifying firmware UUIDs. Pre-loaded with Nordic UART (6E400001...) & Espressif BLE defaults (4FAFC201...).",
                            style = MaterialTheme.typography.bodySmall,
                            color = textGray,
                            fontSize = 11.sp
                        )
                        OutlinedTextField(
                            value = serviceUuidInput,
                            onValueChange = { serviceUuidInput = it },
                            label = { Text("ESP32 BLE Service UUID", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = writeUuidInput,
                            onValueChange = { writeUuidInput = it },
                            label = { Text("BLE Write Characteristic UUID", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = notifyUuidInput,
                            onValueChange = { notifyUuidInput = it },
                            label = { Text("BLE Notify Characteristic UUID", fontSize = 11.sp) },
                            singleLine = true,
                            textStyle = LocalTextStyle.current.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = cmdOnInput,
                                onValueChange = { cmdOnInput = it },
                                label = { Text("ON Payload", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = cmdOffInput,
                                onValueChange = { cmdOffInput = it },
                                label = { Text("OFF Payload", fontSize = 11.sp) },
                                singleLine = true,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = {
                                    serviceUuidInput = Esp32BleManager.ESPRESSIF_ALT_SERVICE_UUID
                                    writeUuidInput = Esp32BleManager.ESPRESSIF_ALT_CHAR_UUID
                                    notifyUuidInput = Esp32BleManager.ESPRESSIF_ALT_CHAR_UUID
                                    bleManager.updateBleConfiguration(
                                        serviceUuidInput,
                                        writeUuidInput,
                                        notifyUuidInput,
                                        cmdOnInput,
                                        cmdOffInput
                                    )
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                            ) {
                                Text("Use Espressif UUID", fontSize = 11.sp, textAlign = TextAlign.Center)
                            }
                            Button(
                                onClick = {
                                    bleManager.updateBleConfiguration(
                                        serviceUuidInput,
                                        writeUuidInput,
                                        notifyUuidInput,
                                        cmdOnInput,
                                        cmdOffInput
                                    )
                                    showUuidConfig = false
                                    Toast.makeText(context, "ESP32 BLE UUIDs saved", Toast.LENGTH_SHORT).show()
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                            ) {
                                Text("Apply UUIDs", fontSize = 11.sp, color = Color.White, textAlign = TextAlign.Center)
                            }
                        }
                    }
                }
            }
        }

        // =========================================================================
        // DISCOVERED DEVICES SECTION (LIVE REFLECTION OF ESP32 BLE CONNECTION)
        // =========================================================================
        Text(
            text = "DISCOVERED DEVICES & HARDWARE NODES",
            style = MaterialTheme.typography.labelSmall,
            color = textGray,
            letterSpacing = 1.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        DeviceItem(
            name = if (isEsp32BleConnected) bleState.deviceName else "ESP32 Controller (${bleState.deviceName})",
            type = "BLE GATT",
            status = bleState.rssi?.let { "RSSI: $it dBm" } ?: "Service UUID Detection",
            extra = esp32StatusText,
            iconRes = android.R.drawable.stat_sys_data_bluetooth
        )

        DeviceItem(
            name = "NeuroSky MindWave 2",
            type = "Bluetooth",
            status = if (isEegConnected) "Signal: Active" else "Not Paired",
            extra = eegState,
            iconRes = android.R.drawable.stat_sys_data_bluetooth
        )

        DeviceItem(
            name = "EMG Muscle Sensor",
            type = "Bluetooth",
            status = if (isEmgConnected) "Signal: Active" else "Not Paired",
            extra = emgState,
            iconRes = android.R.drawable.stat_sys_data_bluetooth
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Connection Sync Info
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8FF))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "CONNECTION SYNC",
                        style = MaterialTheme.typography.labelSmall,
                        color = primaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    val connectedCount = listOf(isEsp32BleConnected, isEegConnected, isEmgConnected).count { it }
                    Text(
                        "$connectedCount / 3",
                        style = MaterialTheme.typography.headlineMedium,
                        color = primaryBlue,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        if (isEsp32BleConnected) "ESP32 BLE Connected & Active" else "Devices connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isEsp32BleConnected) greenConnected else textGray,
                        fontWeight = if (isEsp32BleConnected) FontWeight.Bold else FontWeight.Normal
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_recent_history),
                            contentDescription = null,
                            tint = primaryBlue,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = bleState.rssi?.let { "$it dBm" } ?: "--",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = primaryBlue
                        )
                    }
                    Text("BLE Signal Strength", style = MaterialTheme.typography.bodySmall, color = textGray)
                }
            }
        }

        // Status Grid showing live ESP32 status right on the Connect Your Devices page
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "ESP32 BLE",
                status = esp32StatusText,
                color = statusColor(esp32StatusText),
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "EEG",
                status = eegState,
                color = statusColor(eegState),
                modifier = Modifier.weight(1f)
            )
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            StatusCard(
                title = "EMG",
                status = emgState,
                color = statusColor(emgState),
                modifier = Modifier.weight(1f)
            )
            StatusCard(
                title = "ROBOTIC HAND",
                status = handState,
                color = statusColor(handState),
                modifier = Modifier.weight(1f)
            )
        }

        // Live BLE Connection Report Card
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(if (isEsp32BleConnected) greenConnected else primaryBlue, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = painterResource(android.R.drawable.ic_menu_info_details),
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Text(
                        "Live BLE Diagnostic Report",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
                ReportItem(
                    if (isEsp32BleConnected) {
                        "ESP32 Connected: ${bleState.deviceName} (${bleState.deviceAddress ?: "BLE"})"
                    } else {
                        "ESP32 Status: $esp32StatusText — Tap 'CONNECT ESP32 (BLE)' above"
                    }
                )
                ReportItem("Service UUID: ${bleState.serviceUuid}")
                ReportItem("Last Command: ${bleState.lastSentCommand} | Last Notify: ${bleState.lastReceivedResponse}")
                ReportItem(
                    if (isEsp32BleConnected) "GATT Write & Notify ready for calibration" else "Awaiting live ESP32 BLE GATT connection"
                )
            }
        }

        OutlinedButton(
            onClick = {
                isEegConnected = checkBluetoothDeviceConnected(context, "MindWave") ||
                    checkBluetoothDeviceConnected(context, "NeuroSky")
                isEmgConnected = checkBluetoothDeviceConnected(context, "EMG") ||
                    checkBluetoothDeviceConnected(context, "Muscle")
                if (!bleManager.hasBlePermissions()) {
                    blePermissionLauncher.launch(requiredBlePermissions)
                } else if (!bleManager.isBluetoothEnabled()) {
                    enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
                } else {
                    bleManager.startFastBleScan(autoConnectOnDetect = true)
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            border = BorderStroke(1.dp, if (bleState.isScanning) Color.Gray else primaryBlue),
            enabled = !bleState.isScanning
        ) {
            Text(
                if (bleState.isScanning) "SCANNING BLE..." else "SCAN AGAIN",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = if (bleState.isScanning) Color.Gray else primaryBlue,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Button(
            onClick = onContinue,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 52.dp),
            shape = RoundedCornerShape(26.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
        ) {
            Text(
                "NEXT",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
    }
}

@Composable
private fun BleMetricTile(
    label: String,
    value: String,
    valueColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = Color.White,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF64748B),
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = value,
                style = MaterialTheme.typography.bodySmall,
                color = valueColor,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 11.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

@Composable
fun DeviceItem(name: String, type: String, status: String, extra: String, iconRes: Int) {
    val isConnected = extra == "CONNECTED" || extra == "Healthy" || extra == "Ready"
    val isConnecting = extra == "CONNECTING" || extra == "RECONNECTING" || extra == "Searching"

    val statusColor = when {
        isConnected -> Color(0xFF15803D)
        isConnecting -> Color(0xFFD97706)
        else -> Color(0xFFD32F2F)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isConnected) Color(0xFFF0FDF4) else Color.White
        ),
        border = BorderStroke(
            1.dp,
            if (isConnected) Color(0xFF86EFAC) else Color(0xFFEBEBEB)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .background(
                        if (isConnected) Color(0xFFDCFCE7) else Color(0xFFF1F8FF),
                        RoundedCornerShape(12.dp)
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(id = iconRes),
                    contentDescription = null,
                    tint = if (isConnected) Color(0xFF15803D) else Color(0xFF0052CC),
                    modifier = Modifier.size(24.dp)
                )
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        "$type • $status",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF727785),
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    Text(
                        "• $extra",
                        style = MaterialTheme.typography.bodySmall,
                        color = statusColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold
                    )
                }
            }
            val statusIcon = if (isConnected) Icons.Default.CheckCircle else Icons.Default.Clear
            Icon(
                imageVector = statusIcon,
                contentDescription = extra,
                tint = statusColor,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun ScanningItem(text: String) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                color = Color(0xFF0052CC),
                strokeWidth = 2.dp
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, color = Color(0xFF727785))
        }
    }
}

@Composable
fun StatusCard(title: String, status: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(color, RoundedCornerShape(2.dp))
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    status,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun ReportItem(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
        Icon(
            painter = painterResource(android.R.drawable.presence_online),
            contentDescription = null,
            tint = Color(0xFF0052CC),
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF727785), fontSize = 11.sp)
    }
}

fun checkEsp32Connection(context: android.content.Context): Boolean {
    val state = Esp32BleManager.getInstance(context).uiState.value
    return state.connectionStatus == BleConnectionStatus.CONNECTED && state.isGattVerified
}

fun checkBluetoothDeviceConnected(context: android.content.Context, deviceNameSubstring: String): Boolean {
    val bluetoothManager =
        context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as? android.bluetooth.BluetoothManager
            ?: return false
    val adapter = bluetoothManager.adapter ?: return false
    if (!adapter.isEnabled) return false

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (androidx.core.app.ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            return false
        }
    }

    return try {
        val connectedDevices = bluetoothManager.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT)
        connectedDevices.any { device ->
            device.name?.contains(deviceNameSubstring, ignoreCase = true) == true
        }
    } catch (_: Exception) {
        false
    }
}
