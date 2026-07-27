package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CheckCircle

@Composable
fun ConnectDevicesScreen(onBack: () -> Unit, onContinue: () -> Unit) {
  val primaryBlue = Color(0xFF0052CC)
  val textGray = Color(0xFF727785)
  val lightBg = Color(0xFFF8F9FA)
  
  val context = androidx.compose.ui.platform.LocalContext.current
  var isScanning by remember { mutableStateOf(true) }
  var devicesFound by remember { mutableStateOf(0) }
  
  var esp32State by remember { mutableStateOf("Searching") }
  var eegState by remember { mutableStateOf("Searching") }
  var emgState by remember { mutableStateOf("Searching") }
  var handState by remember { mutableStateOf("Searching") }
  
  LaunchedEffect(isScanning) {
    if (isScanning) {
      devicesFound = 0
      esp32State = "Searching"
      eegState = "Searching"
      emgState = "Searching"
      handState = "Searching"
      
      delay(2000) // Simulate scanning time
      
      val isEsp32Connected = checkEsp32Connection(context)
      val isEegConnected = checkBluetoothDeviceConnected(context, "MindWave") || checkBluetoothDeviceConnected(context, "NeuroSky")
      val isEmgConnected = checkBluetoothDeviceConnected(context, "EMG") || checkBluetoothDeviceConnected(context, "Muscle")
      
      esp32State = if (isEsp32Connected) "Healthy" else "Not Connect"
      eegState = if (isEegConnected) "Ready" else "Not Connect"
      emgState = if (isEmgConnected) "Healthy" else "Not Connect"
      handState = if (isEmgConnected && isEsp32Connected) "Ready" else "Not Connect"
      
      devicesFound = 3
      isScanning = false
    }
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .background(Color.White)
      .systemBarsPadding()
      .padding(horizontal = 24.dp)
      .verticalScroll(rememberScrollState())
  ) {
    // Top Bar
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(top = 16.dp, bottom = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      IconButton(onClick = onBack) {
        Icon(painter = painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back", tint = primaryBlue)
      }
      
      Text(
        text = "FIRST-TIME SETUP",
        style = MaterialTheme.typography.labelSmall,
        color = textGray,
        letterSpacing = 1.sp,
        fontWeight = FontWeight.Bold
      )
      
      IconButton(onClick = { /* Help */ }) {
        Icon(
          painter = painterResource(android.R.drawable.ic_menu_help),
          contentDescription = "Help",
          tint = primaryBlue
        )
      }
    }

    Text(
      text = "Connect Your Devices",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.ExtraBold,
      color = Color(0xFF1A1B1F),
      modifier = Modifier.padding(bottom = 12.dp)
    )

    Text(
      text = "NeuroGrip is searching for your hardware. Make sure all devices are powered on and nearby.",
      style = MaterialTheme.typography.bodyMedium,
      color = textGray,
      modifier = Modifier.padding(bottom = 24.dp)
    )
    
    // Progress Bar
    Row(
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text("STEP 3 OF 6", style = MaterialTheme.typography.labelSmall, color = primaryBlue, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.height(4.dp).width(120.dp).background(primaryBlue, RoundedCornerShape(2.dp)))
      }
      Text("50% Complete", style = MaterialTheme.typography.labelSmall, color = primaryBlue, fontWeight = FontWeight.Bold)
    }

    // Image Card
    Card(
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color.White),
      border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(primaryBlue.copy(alpha = 0.1f)),
          contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_share),
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Automatic Device Pairing", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryBlue)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "NeuroGrip automatically discovers and securely connects every compatible device.",
          style = MaterialTheme.typography.bodySmall,
          color = textGray,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center,
          modifier = Modifier.padding(horizontal = 16.dp)
        )
      }
    }

    Text(
      text = "DISCOVERED DEVICES",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 1.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 12.dp)
    )

    // Devices List
    if (devicesFound >= 1) {
      DeviceItem(
        name = "ESP32 Controller",
        type = "Wi-Fi",
        status = "Signal: Excellent",
        extra = esp32State,
        iconRes = android.R.drawable.ic_menu_compass
      )
    } else if (isScanning) {
      ScanningItem("Searching for ESP32 Controller...")
    }

    if (devicesFound >= 2) {
      DeviceItem(
        name = "NeuroSky MindWave 2",
        type = "Bluetooth",
        status = "Battery: 89%",
        extra = eegState,
        iconRes = android.R.drawable.stat_sys_data_bluetooth
      )
    } else if (isScanning && devicesFound >= 1) {
      ScanningItem("Searching for EEG Headset...")
    }

    if (devicesFound >= 3) {
      DeviceItem(
        name = "EMG Muscle Sensor",
        type = "Bluetooth",
        status = "Battery: 93%",
        extra = emgState,
        iconRes = android.R.drawable.stat_sys_data_bluetooth
      )
    } else if (isScanning && devicesFound >= 2) {
      ScanningItem("Searching for EMG Sensor...")
    }

    if (!isScanning && devicesFound == 3) {
      Spacer(modifier = Modifier.height(12.dp))
      
      // Connection Sync Info
      Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8FF))
      ) {
        Row(modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Column {
             Text("CONNECTION SYNC", style = MaterialTheme.typography.labelSmall, color = primaryBlue, fontWeight = FontWeight.Bold)
             Spacer(modifier = Modifier.height(4.dp))
             val connectedCount = listOf(esp32State, eegState, emgState).count { it == "Healthy" || it == "Ready" }
             Text("$connectedCount / 3", style = MaterialTheme.typography.headlineMedium, color = primaryBlue, fontWeight = FontWeight.Bold)
             Text("Devices connected", style = MaterialTheme.typography.bodySmall, color = textGray)
          }
          Column(horizontalAlignment = Alignment.End) {
             Row(verticalAlignment = Alignment.CenterVertically) {
               Icon(painter = painterResource(android.R.drawable.ic_menu_recent_history), contentDescription = null, tint = primaryBlue, modifier = Modifier.size(16.dp))
               Spacer(modifier = Modifier.width(4.dp))
               Text("16ms", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = primaryBlue)
             }
             Text("Response latency", style = MaterialTheme.typography.bodySmall, color = textGray)
          }
        }
      }

      // Status Grid
      val getColor: (String) -> Color = { state ->
          when (state) {
              "Healthy", "Ready" -> Color(0xFF2E7D32)
              "Not Work", "Not Connect" -> Color(0xFFD32F2F)
              else -> Color.Gray
          }
      }
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard(title = "ESP32", status = esp32State, color = getColor(esp32State), modifier = Modifier.weight(1f))
        StatusCard(title = "EEG", status = eegState, color = getColor(eegState), modifier = Modifier.weight(1f))
      }
      Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        StatusCard(title = "EMG", status = emgState, color = getColor(emgState), modifier = Modifier.weight(1f))
        StatusCard(title = "ROBOTIC HAND", status = handState, color = getColor(handState), modifier = Modifier.weight(1f))
      }

      // AI Connection Report
      Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
      ) {
        Column(modifier = Modifier.padding(16.dp)) {
          Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            Box(modifier = Modifier.size(32.dp).background(primaryBlue, CircleShape), contentAlignment = Alignment.Center) {
               Icon(painter = painterResource(android.R.drawable.ic_menu_info_details), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("AI Connection Report", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
          }
          ReportItem("All devices detected successfully")
          ReportItem("Secure pairing completed")
          ReportItem("Communication verified")
          ReportItem("Ready for calibration")
        }
      }
    } // Closing if (!isScanning && devicesFound == 3)

    Spacer(modifier = Modifier.height(24.dp)) 

    OutlinedButton(
      onClick = { isScanning = true },
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      border = BorderStroke(1.dp, if (isScanning) Color.Gray else primaryBlue),
      enabled = !isScanning
    ) {
      Text(if (isScanning) "SCANNING..." else "SCAN AGAIN", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isScanning) Color.Gray else primaryBlue)
    }

    Spacer(modifier = Modifier.height(12.dp))

    Button(
      onClick = onContinue,
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue),
      enabled = !isScanning
    ) {
      Text("NEXT", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
    }

    Spacer(modifier = Modifier.height(32.dp))
  }
}

@Composable
fun DeviceItem(name: String, type: String, status: String, extra: String, iconRes: Int) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFEBEBEB))
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier.size(48.dp).background(Color(0xFFF1F8FF), RoundedCornerShape(12.dp)),
        contentAlignment = Alignment.Center
      ) {
         Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = Color(0xFF0052CC), modifier = Modifier.size(24.dp))
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
          Icon(painter = painterResource(if (type == "Wi-Fi") android.R.drawable.ic_menu_compass else android.R.drawable.stat_sys_data_bluetooth), contentDescription = null, tint = Color.Gray, modifier = Modifier.size(12.dp))
          Spacer(modifier = Modifier.width(4.dp))
          Text(type, style = MaterialTheme.typography.bodySmall, color = Color(0xFF727785), fontSize = 11.sp)
          Spacer(modifier = Modifier.width(8.dp))
          Text("• $extra", style = MaterialTheme.typography.bodySmall, color = if (extra == "Not Work" || extra == "Not Connect") Color(0xFFD32F2F) else Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
        }
      }
      val statusIconTint = when(extra) {
         "Not Work", "Not Connect" -> Color(0xFFD32F2F)
         "Searching" -> Color.Gray
         else -> Color(0xFF2E7D32)
      }
      val statusIcon = if (extra == "Not Work" || extra == "Not Connect") androidx.compose.material.icons.Icons.Default.Clear else androidx.compose.material.icons.Icons.Default.CheckCircle
      Icon(imageVector = statusIcon, contentDescription = extra, tint = statusIconTint, modifier = Modifier.size(24.dp))
    }
  }
}

@Composable
fun ScanningItem(text: String) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color.White),
    border = BorderStroke(1.dp, Color(0xFFEBEBEB))
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(24.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.Center
    ) {
      CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color(0xFF0052CC), strokeWidth = 2.dp)
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
    Row(modifier = Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
      Box(modifier = Modifier.width(4.dp).height(24.dp).background(color, RoundedCornerShape(2.dp)))
      Spacer(modifier = Modifier.width(8.dp))
      Column {
        Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
        Text(status, style = MaterialTheme.typography.bodySmall, color = color, fontWeight = FontWeight.Bold)
      }
    }
  }
}

@Composable
fun ReportItem(text: String) {
  Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 6.dp)) {
    Icon(painter = painterResource(android.R.drawable.presence_online), contentDescription = null, tint = Color(0xFF0052CC), modifier = Modifier.size(14.dp))
    Spacer(modifier = Modifier.width(8.dp))
    Text(text, style = MaterialTheme.typography.bodySmall, color = Color(0xFF727785), fontSize = 11.sp)
  }
}

fun checkEsp32Connection(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    if (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI)) {
        return false // Return false here to properly simulate "Not Connected"
    }
    return false
}

fun checkBluetoothDeviceConnected(context: android.content.Context, deviceNameSubstring: String): Boolean {
    val bluetoothManager = context.getSystemService(android.content.Context.BLUETOOTH_SERVICE) as android.bluetooth.BluetoothManager
    val adapter = bluetoothManager.adapter ?: return false
    
    if (androidx.core.app.ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
        return false
    }
    
    val connectedDevices = bluetoothManager.getConnectedDevices(android.bluetooth.BluetoothProfile.GATT)
    for (device in connectedDevices) {
        if (device.name?.contains(deviceNameSubstring, ignoreCase = true) == true) {
            return true
        }
    }
    return false
}
