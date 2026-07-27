package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

@Composable
fun PermissionsScreen(onBack: () -> Unit, onContinue: () -> Unit) {
  val context = LocalContext.current
  val primaryBlue = Color(0xFF2F80ED)
  val textGray = Color(0xFF727785)
  val lightBg = Color(0xFFF8F9FA)

  // Permission states
  var hasBluetooth by remember { mutableStateOf(checkBluetoothPermission(context)) }
  var hasLocation by remember { mutableStateOf(checkLocationPermission(context)) }
  var hasNotification by remember { mutableStateOf(checkNotificationPermission(context)) }
  var hasCamera by remember { mutableStateOf(checkCameraPermission(context)) }
  var hasStorage by remember { mutableStateOf(checkStoragePermission(context)) }
  var hasInternet by remember { mutableStateOf(checkInternetConnection(context)) }

  val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
  DisposableEffect(lifecycleOwner) {
    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
      if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
        hasBluetooth = checkBluetoothPermission(context)
        hasLocation = checkLocationPermission(context)
        hasNotification = checkNotificationPermission(context)
        hasCamera = checkCameraPermission(context)
        hasStorage = checkStoragePermission(context)
        hasInternet = checkInternetConnection(context)
      }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose {
      lifecycleOwner.lifecycle.removeObserver(observer)
    }
  }

  var showIncompleteDialog by remember { mutableStateOf(false) }

  val bluetoothPermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
  } else {
    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
  }

  val locationPermissions = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
  
  val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.POST_NOTIFICATIONS)
  } else {
    emptyArray()
  }

  val cameraPermission = arrayOf(Manifest.permission.CAMERA)
  
  val storagePermissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
  } else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }

  val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestMultiplePermissions()
  ) { permissions ->
    hasBluetooth = checkBluetoothPermission(context)
    hasLocation = checkLocationPermission(context)
    hasNotification = checkNotificationPermission(context)
    hasCamera = checkCameraPermission(context)
    hasStorage = checkStoragePermission(context)
  }

  if (showIncompleteDialog) {
    AlertDialog(
      onDismissRequest = { showIncompleteDialog = false },
      title = { Text("Permissions Required") },
      text = { Text("Please allow required permissions (Bluetooth, Wi-Fi/Location) to continue to the next step.") },
      confirmButton = {
        TextButton(onClick = { showIncompleteDialog = false }) {
          Text("OK")
        }
      }
    )
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
        text = "NeuroGrip",
        style = MaterialTheme.typography.titleLarge,
        color = Color.LightGray,
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

    // Progress Bar
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Column {
        Text("STEP 2 OF 4", style = MaterialTheme.typography.labelSmall, color = primaryBlue, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Box(modifier = Modifier.height(4.dp).width(60.dp).background(primaryBlue, RoundedCornerShape(2.dp)))
      }
      Text("33% COMPLETE", style = MaterialTheme.typography.labelSmall, color = primaryBlue, fontWeight = FontWeight.Bold)
    }

    Spacer(modifier = Modifier.height(24.dp))

    Text(
      text = "FIRST-TIME SETUP",
      style = MaterialTheme.typography.labelSmall,
      color = textGray,
      letterSpacing = 1.5.sp,
      fontWeight = FontWeight.Bold,
      modifier = Modifier.padding(bottom = 8.dp)
    )

    Text(
      text = "Permissions &\nConnectivity",
      style = MaterialTheme.typography.headlineMedium,
      fontWeight = FontWeight.ExtraBold,
      color = Color(0xFF1A1B1F),
      modifier = Modifier.padding(bottom = 12.dp)
    )

    Text(
      text = "Allow the required permissions so NeuroGrip can safely connect to your robotic hand system.",
      style = MaterialTheme.typography.bodyMedium,
      color = textGray,
      modifier = Modifier.padding(bottom = 24.dp)
    )

    // Image Card
    Card(
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = lightBg)
    ) {
      Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.padding(16.dp)) {
        Box(
          modifier = Modifier
            .fillMaxWidth()
            .height(160.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(primaryBlue.copy(alpha = 0.1f)),
          contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_dialog_info),
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text("Preparing Your Device", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(8.dp))
        Text(
          "NeuroGrip requires a few permissions to securely communicate with your medical devices and provide intelligent rehabilitation.",
          style = MaterialTheme.typography.bodySmall,
          color = textGray,
          textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
      }
    }

    val handleToggle: (Boolean, Boolean, Array<String>) -> Unit = { requestedState, currentState, permissions ->
      if (requestedState && !currentState) {
        launcher.launch(permissions)
      } else if (!requestedState && currentState) {
        android.widget.Toast.makeText(context, "Permissions must be revoked in device App Settings", android.widget.Toast.LENGTH_LONG).show()
        val intent = android.content.Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = android.net.Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
      }
    }

    // Permission Toggles
    PermissionItem(
      title = "Bluetooth",
      desc = "Required to connect ESP32, NeuroSky EEG and EMG Sensor.",
      isRequired = true,
      isGranted = hasBluetooth,
      onToggle = { handleToggle(it, hasBluetooth, bluetoothPermissions) }
    )
    
    PermissionItem(
      title = "Wi-Fi / Local Network",
      desc = "Required for communication with the ESP32 controller.",
      isRequired = true,
      isGranted = hasLocation,
      onToggle = { handleToggle(it, hasLocation, locationPermissions) }
    )
    
    if (notificationPermission.isNotEmpty()) {
      PermissionItem(
        title = "Notifications",
        desc = "Receive therapy reminders, alerts and device status.",
        isRequired = false,
        isGranted = hasNotification,
        onToggle = { handleToggle(it, hasNotification, notificationPermission) }
      )
    }
    
    PermissionItem(
      title = "Camera",
      desc = "Used for QR device pairing and future AI Vision features.",
      isRequired = false,
      isGranted = hasCamera,
      onToggle = { handleToggle(it, hasCamera, cameraPermission) }
    )
    
    PermissionItem(
      title = "Storage",
      desc = "Save reports, therapy history and PDF exports.",
      isRequired = false,
      isGranted = hasStorage,
      onToggle = { handleToggle(it, hasStorage, storagePermissions) }
    )

    Spacer(modifier = Modifier.height(24.dp))
    
    // System Readiness
    Card(
      modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8FF))
    ) {
      Column(modifier = Modifier.padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp)) {
          Icon(painter = painterResource(android.R.drawable.ic_menu_info_details), contentDescription = null, tint = primaryBlue, modifier = Modifier.size(20.dp))
          Spacer(modifier = Modifier.width(8.dp))
          Text("System Readiness", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
        }
        Row(modifier = Modifier.fillMaxWidth()) {
          Column(modifier = Modifier.weight(1f)) {
            Text("BLUETOOTH", style = MaterialTheme.typography.labelSmall, color = textGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.size(6.dp).background(if (hasBluetooth) Color.Green else Color.Gray, CircleShape))
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (hasBluetooth) "Ready" else "Waiting", style = MaterialTheme.typography.bodySmall, color = Color.Black)
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text("INTERNET", style = MaterialTheme.typography.labelSmall, color = textGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.size(6.dp).background(if (hasInternet) Color.Green else Color.Gray, CircleShape))
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (hasInternet) "Connected" else "Not Connected", style = MaterialTheme.typography.bodySmall, color = Color.Black)
            }
          }
          Column(modifier = Modifier.weight(1f)) {
            Text("WI-FI", style = MaterialTheme.typography.labelSmall, color = textGray)
            Row(verticalAlignment = Alignment.CenterVertically) {
              Box(modifier = Modifier.size(6.dp).background(if (hasLocation) Color.Green else Color.Gray, CircleShape))
              Spacer(modifier = Modifier.width(4.dp))
              Text(if (hasLocation) "Ready" else "Waiting", style = MaterialTheme.typography.bodySmall, color = Color.Black)
            }
          }
        }
      }
    }
    
    // Medical Data Protection
    Card(
      modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
      shape = RoundedCornerShape(16.dp),
      colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
    ) {
      Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(40.dp).background(primaryBlue, CircleShape), contentAlignment = Alignment.Center) {
           Icon(painter = painterResource(android.R.drawable.ic_secure), contentDescription = null, tint = Color.White)
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column {
          Text("Your Medical Data is Protected", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
          Row(modifier = Modifier.padding(top = 4.dp)) {
            Column {
               Text("✓ End-to-End Encryption", style = MaterialTheme.typography.bodySmall, color = textGray, fontSize = 10.sp)
               Text("✓ Secure Device Pairing", style = MaterialTheme.typography.bodySmall, color = textGray, fontSize = 10.sp)
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column {
               Text("✓ HIPAA Compliant", style = MaterialTheme.typography.bodySmall, color = textGray, fontSize = 10.sp)
            }
          }
        }
      }
    }

    // Allow & Continue Button
    Button(
      onClick = {
        val allRequiredGranted = hasBluetooth && hasLocation
        if (!allRequiredGranted) {
           val permissionsToRequest = mutableListOf<String>()
           if (!hasBluetooth) permissionsToRequest.addAll(bluetoothPermissions)
           if (!hasLocation) permissionsToRequest.addAll(locationPermissions)
           launcher.launch(permissionsToRequest.toTypedArray())
           showIncompleteDialog = true
        } else {
           onContinue()
        }
      },
      modifier = Modifier
        .fillMaxWidth()
        .height(52.dp),
      shape = RoundedCornerShape(26.dp),
      colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
    ) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(painter = painterResource(android.R.drawable.checkbox_on_background), contentDescription = "Allow", tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text("Allow & Continue", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
      }
    }

    Spacer(modifier = Modifier.height(12.dp))
    
    TextButton(
      onClick = onContinue,
      modifier = Modifier.fillMaxWidth()
    ) {
      Text("Configure Later", color = textGray)
    }
    
    Spacer(modifier = Modifier.height(32.dp))
  }
}

@Composable
fun PermissionItem(title: String, desc: String, isRequired: Boolean, isGranted: Boolean, onToggle: (Boolean) -> Unit) {
  Card(
    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
    shape = RoundedCornerShape(16.dp),
    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA)),
    border = BorderStroke(1.dp, Color(0xFFEBEBEB))
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(16.dp),
      verticalAlignment = Alignment.CenterVertically
    ) {
      Box(
        modifier = Modifier.size(40.dp).background(Color(0xFFE8F0FE), CircleShape),
        contentAlignment = Alignment.Center
      ) {
         // Placeholder for icon, ideally you'd have specific icons for each.
         val iconRes = when(title) {
           "Bluetooth" -> android.R.drawable.stat_sys_data_bluetooth
           "Wi-Fi / Local Network" -> android.R.drawable.ic_menu_compass // placeholder
           "Notifications" -> android.R.drawable.ic_popup_reminder
           "Camera" -> android.R.drawable.ic_menu_camera
           "Storage" -> android.R.drawable.ic_menu_save
           else -> android.R.drawable.ic_menu_info_details
         }
         Icon(painter = painterResource(id = iconRes), contentDescription = title, tint = Color(0xFF2F80ED), modifier = Modifier.size(24.dp))
      }
      Spacer(modifier = Modifier.width(16.dp))
      Column(modifier = Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
        Spacer(modifier = Modifier.height(4.dp))
        Text(desc, style = MaterialTheme.typography.bodySmall, color = Color(0xFF727785))
        Spacer(modifier = Modifier.height(8.dp))
        if (isRequired) {
          Box(modifier = Modifier.background(if (isGranted) Color(0xFFE8F5E9) else Color(0xFFFFEBEE), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
             Text(if (isGranted) "GRANTED" else "NOT GRANTED", fontSize = 10.sp, color = if (isGranted) Color(0xFF2E7D32) else Color(0xFFC62828), fontWeight = FontWeight.Bold)
          }
        } else {
          Box(modifier = Modifier.background(Color(0xFFF5F5F5), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp)) {
             Text("OPTIONAL", fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
          }
        }
      }
      Switch(
        checked = isGranted,
        onCheckedChange = { onToggle(it) },
        colors = SwitchDefaults.colors(checkedThumbColor = Color.White, checkedTrackColor = Color(0xFF2F80ED))
      )
    }
  }
}

fun checkBluetoothPermission(context: android.content.Context): Boolean {
  val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
    arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN)
  } else {
    arrayOf(Manifest.permission.BLUETOOTH, Manifest.permission.BLUETOOTH_ADMIN)
  }
  return permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

fun checkLocationPermission(context: android.content.Context): Boolean {
  return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
         ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
}

fun checkNotificationPermission(context: android.content.Context): Boolean {
  return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
  } else true
}

fun checkCameraPermission(context: android.content.Context): Boolean {
  return ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
}

fun checkStoragePermission(context: android.content.Context): Boolean {
  val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    arrayOf(Manifest.permission.READ_MEDIA_IMAGES, Manifest.permission.READ_MEDIA_VIDEO)
  } else {
    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
  }
  return permissions.all { ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED }
}

fun checkInternetConnection(context: android.content.Context): Boolean {
    val connectivityManager = context.getSystemService(android.content.Context.CONNECTIVITY_SERVICE) as android.net.ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
