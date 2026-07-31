import re

with open('./app/src/main/java/com/example/DashboardScreen.kt', 'r') as f:
    content = f.read()

# Add states
new_states = """
    val responseTime by viewModel.responseTime.collectAsState()
    val servoTemp by viewModel.servoTemp.collectAsState()
    val gripForce by viewModel.gripForce.collectAsState()
    val therapyProgress by viewModel.therapyProgress.collectAsState()
"""
content = content.replace("    val emgDataPoints by viewModel.emgDataPoints.collectAsState()", "    val emgDataPoints by viewModel.emgDataPoints.collectAsState()\n" + new_states)

# Replace top bar Profile & Name
old_profile = """                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "https://i.pravatar.cc/100?img=11",
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text("Good", style = MaterialTheme.typography.bodySmall, color = textGray)
                        Text("Morning, $username", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = darkText)
                    }
                }"""

new_profile = """                Row(verticalAlignment = Alignment.CenterVertically) {
                    AsyncImage(
                        model = "https://i.pravatar.cc/100?img=11",
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        val displayUser = if (username.length > 10) username.take(10) else username
                        Text(displayUser, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold, color = darkText)
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(6.dp).background(if (isConnected) greenColor else Color.Red, CircleShape))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isConnected) "System Online" else "System Offline", style = MaterialTheme.typography.labelSmall, color = textGray, fontSize = 9.sp)
                        }
                    }
                }"""
content = content.replace(old_profile, new_profile)


# Replace Logo
old_logo = """                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(imageVector = Icons.Outlined.PrecisionManufacturing, contentDescription = "Logo", tint = buttonBlue, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NeuroGrip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = buttonBlue)
                }"""

new_logo = """                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("🧠", fontSize = 24.sp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("NeuroGrip", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = buttonBlue)
                }"""
content = content.replace(old_logo, new_logo)

# Remove System Offline from Hero Card
hero_status_regex = r'                        Row\(verticalAlignment = Alignment\.CenterVertically\) \{\s*Box\(modifier = Modifier\.size\(8\.dp\)\.background\(if \(isConnected\) greenColor else Color\.Red, CircleShape\)\)\s*Spacer\(modifier = Modifier\.width\(8\.dp\)\)\s*Text\(if \(isConnected\) "ESP32 Connected" else "System Offline", style = MaterialTheme\.typography\.labelSmall, color = textGray\)\s*\}\s*Spacer\(modifier = Modifier\.height\(16\.dp\)\)\s*'
content = re.sub(hero_status_regex, '', content)

# Update Prosthetic Status section
old_status_bars = """                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(56.dp), color = Color(0xFFF1F5F9), strokeWidth = 5.dp)
                                        CircularProgressIndicator(progress = { 0.6f }, modifier = Modifier.size(56.dp), color = buttonBlue, strokeWidth = 5.dp)
                                        Text("60%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = darkText)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("12 / 20 mins", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = darkText)
                                        Text("Today's Progress", style = MaterialTheme.typography.labelSmall, color = textGray)
                                    }"""

new_status_bars = """                                    Box(contentAlignment = Alignment.Center) {
                                        CircularProgressIndicator(progress = { 1f }, modifier = Modifier.size(56.dp), color = Color(0xFFF1F5F9), strokeWidth = 5.dp)
                                        CircularProgressIndicator(progress = { therapyProgress }, modifier = Modifier.size(56.dp), color = buttonBlue, strokeWidth = 5.dp)
                                        Text("${(therapyProgress * 100).toInt()}%", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold, color = darkText)
                                    }
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Column {
                                        Text("${(therapyProgress * 20).toInt()} / 20 mins", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = darkText)
                                        Text("Today's Progress", style = MaterialTheme.typography.labelSmall, color = textGray)
                                    }"""
content = content.replace(old_status_bars, new_status_bars)


old_status_lines = """                            Column(modifier = Modifier.weight(1f)) {
                                StatusLine("Response Time", "15ms", darkText, textGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusLine("Servo Temperature", "36.5°C", darkText, textGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusLine("Grip Force", "4.2 N/m", darkText, textGray)
                            }"""

new_status_lines = """                            Column(modifier = Modifier.weight(1f)) {
                                StatusLine("Response Time", if(isConnected) "${responseTime}ms" else "--", darkText, textGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusLine("Servo Temperature", if(isConnected) String.format("%.1f°C", servoTemp) else "--", darkText, textGray)
                                Spacer(modifier = Modifier.height(8.dp))
                                StatusLine("Grip Force", if(isConnected) String.format("%.1f N/m", gripForce) else "--", darkText, textGray)
                            }"""
content = content.replace(old_status_lines, new_status_lines)


with open('./app/src/main/java/com/example/DashboardScreen.kt', 'w') as f:
    f.write(content)
