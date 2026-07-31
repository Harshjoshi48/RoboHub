import re

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

imports = """import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
"""
content = content.replace("import androidx.compose.runtime.Composable", imports + "import androidx.compose.runtime.Composable")

# Add dialog state to DashboardScreen
new_states = """
    val context = LocalContext.current
    var showPermissionDialog by remember { mutableStateOf(false) }

    val locationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            viewModel.toggleConnection() // Assuming this connects since it wasn't connected
        } else {
            showPermissionDialog = true
        }
    }
"""
content = content.replace("    val therapyProgress by viewModel.therapyProgress.collectAsState()", "    val therapyProgress by viewModel.therapyProgress.collectAsState()\n" + new_states)


# Update the button
old_btn = """                        Button(
                            onClick = { viewModel.toggleConnection() },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color(0xFFEF4444) else buttonBlue)
                        ) {
                            Text(if (isConnected) "Disconnect" else "Connect ESP32", color = Color.White, fontWeight = FontWeight.Medium)
                        }"""

new_btn = """                        Button(
                            onClick = { 
                                if (isConnected) {
                                    viewModel.toggleConnection()
                                } else {
                                    val isGranted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.ACCESS_FINE_LOCATION
                                    ) == PackageManager.PERMISSION_GRANTED
                                    
                                    if (isGranted) {
                                        viewModel.toggleConnection()
                                    } else {
                                        locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
                                    }
                                }
                            },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color(0xFFEF4444) else buttonBlue)
                        ) {
                            Text(if (isConnected) "Disconnect" else "Connect ESP32", color = Color.White, fontWeight = FontWeight.Medium)
                        }"""
content = content.replace(old_btn, new_btn)

# Add permission dialog at the end of Scaffold
dialog_code = """
        if (showPermissionDialog) {
            AlertDialog(
                onDismissRequest = { showPermissionDialog = false },
                title = { Text("Permission Required") },
                text = { Text("Location permission is required to connect to the ESP32 via Wi-Fi. Please enable it in the app settings.") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showPermissionDialog = false
                            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                data = Uri.fromParts("package", context.packageName, null)
                            }
                            context.startActivity(intent)
                        }
                    ) {
                        Text("Open Settings")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showPermissionDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
"""
content = content.replace("    }\n}", "    }\n" + dialog_code + "\n}")

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)
