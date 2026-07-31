import re

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

# Add imports for lifecycle and state collection
imports = """import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
"""

content = content.replace("import androidx.compose.runtime.Composable", imports + "import androidx.compose.runtime.Composable")

# Update signature
old_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit) {"
new_sig = "fun DashboardScreen(username: String, onLogout: () -> Unit, viewModel: DashboardViewModel = viewModel()) {"
content = content.replace(old_sig, new_sig)

# Add state variables at the beginning of DashboardScreen
states = """
    val isConnected by viewModel.isConnected.collectAsState()
    val eegAttention by viewModel.eegAttention.collectAsState()
    val emgStrength by viewModel.emgStrength.collectAsState()
    val latency by viewModel.latency.collectAsState()
    val battery by viewModel.battery.collectAsState()
    val eegDataPoints by viewModel.eegDataPoints.collectAsState()
    val emgDataPoints by viewModel.emgDataPoints.collectAsState()
"""

# Insert states after val borderLight = Color(0xFFE2E8F0)
content = content.replace("val borderLight = Color(0xFFE2E8F0)", "val borderLight = Color(0xFFE2E8F0)\n" + states)

# Modify System Online text
content = content.replace('Text("System Online", style = MaterialTheme.typography.labelSmall, color = textGray)', 'Text(if (isConnected) "ESP32 Connected" else "System Offline", style = MaterialTheme.typography.labelSmall, color = textGray)')

content = content.replace('Box(modifier = Modifier.size(8.dp).background(greenColor, CircleShape))', 'Box(modifier = Modifier.size(8.dp).background(if (isConnected) greenColor else Color.Red, CircleShape))')


# Modify Start Session button to toggle connection
old_btn = """Button(
                            onClick = { },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonBlue)
                        ) {
                            Text("Start Session", color = Color.White, fontWeight = FontWeight.Medium)
                        }"""
new_btn = """Button(
                            onClick = { viewModel.toggleConnection() },
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = if (isConnected) Color(0xFFEF4444) else buttonBlue)
                        ) {
                            Text(if (isConnected) "Disconnect" else "Connect ESP32", color = Color.White, fontWeight = FontWeight.Medium)
                        }"""
content = content.replace(old_btn, new_btn)

# Modify Hardware and AI stats
content = content.replace('StatItem(Icons.Outlined.MonitorHeart, "Hardware", "Stable", darkText, textGray)', 'StatItem(Icons.Outlined.MonitorHeart, "Hardware", if (isConnected) "Stable" else "Wait", darkText, textGray)')
content = content.replace('StatItem(Icons.Outlined.Wifi, "Latency", "2ms", darkText, textGray)', 'StatItem(Icons.Outlined.Wifi, "Latency", if (isConnected) "${latency}ms" else "--", darkText, textGray)')
content = content.replace('StatItem(Icons.Outlined.BatteryFull, "Battery", "92%", darkText, textGray)', 'StatItem(Icons.Outlined.BatteryFull, "Battery", if (isConnected) "${battery}%" else "--", darkText, textGray)')

# Update Live Bio Signals texts
content = content.replace('Text("82%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = darkText)', 'Text(if (isConnected) "${eegAttention}%" else "--", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = darkText)')
content = content.replace('Text("76%", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = darkText)', 'Text(if (isConnected) "${emgStrength}%" else "--", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = darkText)')

# Update EEG Chart
old_eeg_chart = """val width = size.width
                                val height = size.height
                                val path = Path()
                                path.moveTo(0f, height / 2)
                                for (i in 0..100) {
                                    val x = (i / 100f) * width
                                    val y = (height / 2) + (Math.sin(i.toDouble() * 0.4) * 15f).toFloat()
                                    path.lineTo(x, y)
                                }
                                drawPath(path, color = Color(0xFF64748B), style = Stroke(width = 2f))"""
new_eeg_chart = """val width = size.width
                                val height = size.height
                                val path = Path()
                                val stepX = width / (eegDataPoints.size - 1)
                                path.moveTo(0f, height / 2)
                                eegDataPoints.forEachIndexed { index, value ->
                                    val x = index * stepX
                                    val y = (height / 2) - value
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = Color(0xFF64748B), style = Stroke(width = 2f))"""
content = content.replace(old_eeg_chart, new_eeg_chart)

# Update EMG Chart
old_emg_chart = """val width = size.width
                                val height = size.height
                                val path = Path()
                                path.moveTo(0f, height / 2)
                                var currentX = 0f
                                while (currentX < width) {
                                    val step = (Math.random() * 15 + 5).toFloat()
                                    val newX = Math.min(currentX + step, width)
                                    val newY = if (currentX < width * 0.6f) {
                                        (height / 2) + ((Math.random() - 0.5) * 35f).toFloat()
                                    } else {
                                        height / 2
                                    }
                                    path.lineTo(newX, newY)
                                    currentX = newX
                                }
                                drawPath(path, color = Color(0xFF64748B), style = Stroke(width = 2f))"""

new_emg_chart = """val width = size.width
                                val height = size.height
                                val path = Path()
                                val stepX = width / (emgDataPoints.size - 1)
                                path.moveTo(0f, height / 2)
                                emgDataPoints.forEachIndexed { index, value ->
                                    val x = index * stepX
                                    val y = (height / 2) - value
                                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                                }
                                drawPath(path, color = Color(0xFF64748B), style = Stroke(width = 2f))"""
content = content.replace(old_emg_chart, new_emg_chart)

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)
