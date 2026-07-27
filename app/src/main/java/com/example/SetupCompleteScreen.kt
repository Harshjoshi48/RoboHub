package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun SetupCompleteScreen(onGoToDashboard: () -> Unit, onViewTutorial: () -> Unit) {
    val primaryBlue = Color(0xFF0052CC)
    val textGray = Color(0xFF727785)
    val lightBg = Color(0xFFF8F9FA)
    val greenColor = Color(0xFF2E7D32)
    val lightGreen = Color(0xFFE8F5E9)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(lightBg)
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        // Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onGoToDashboard) { // Or back, but we shouldn't really go back from here
                Icon(painter = painterResource(android.R.drawable.ic_menu_revert), contentDescription = "Back", tint = primaryBlue)
            }
            Text(
                text = "NeuroGrip",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = primaryBlue
            )
            IconButton(onClick = { /* Help */ }) {
                Icon(painter = painterResource(android.R.drawable.ic_menu_help), contentDescription = "Help", tint = primaryBlue)
            }
        }

        Column(modifier = Modifier.padding(horizontal = 24.dp)) {
            Spacer(modifier = Modifier.height(16.dp))
            // Progress Bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("STEP 6 of 6", style = MaterialTheme.typography.labelSmall, color = greenColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                Text("100% Complete", style = MaterialTheme.typography.labelSmall, color = greenColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }
            Box(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp).height(4.dp).background(greenColor, RoundedCornerShape(2.dp)))

            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "SETUP COMPLETED",
                    style = MaterialTheme.typography.labelSmall,
                    color = textGray,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "You're All Set!",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.Black
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Your NeuroGrip system is ready to\nbegin intelligent robotic hand\nrehabilitation.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = textGray,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
            Spacer(modifier = Modifier.height(24.dp))

            // Hero Card
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(lightGreen),
                        contentAlignment = Alignment.Center
                    ) {
                        // Graphic composition instead of image to look polished
                        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.PrecisionManufacturing, contentDescription = null, tint = primaryBlue, modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.width(16.dp))
                            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = greenColor, modifier = Modifier.size(48.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(greenColor, CircleShape))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("NeuroGrip Ready", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = greenColor)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "All devices are connected, calibrated, and verified successfully. You can now control your robotic hand using Brain Signals, Muscle Signals and the Mobile app.",
                        style = MaterialTheme.typography.bodySmall,
                        color = textGray,
                        lineHeight = 16.sp
                    )
                }
            }

            // System Verification
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("System Verification", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    VerificationItem("ESP32 Core Controller", Icons.Default.Memory)
                    VerificationItem("EEG Neural Link", Icons.Default.Psychology)
                    VerificationItem("EMG Muscle Sensor", Icons.Default.MonitorHeart)
                    VerificationItem("Bimodal Calibration", Icons.Default.Tune)
                    VerificationItem("Robotic Hand Test", Icons.Default.PanTool)
                }
            }

            // Overall Health
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Overall Health", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("System Integrity is perfect.", style = MaterialTheme.typography.bodySmall, color = textGray)
                    }
                    Box(modifier = Modifier.size(64.dp).border(4.dp, greenColor, CircleShape), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("100%", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = greenColor)
                            Text("HEALTH", style = MaterialTheme.typography.labelSmall, color = greenColor, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Grid Stats
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("HARDWARE", "100%", modifier = Modifier.weight(1f))
                StatCard("MERGING", "100%", modifier = Modifier.weight(1f))
            }
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("CONNECTIVITY", "100%", modifier = Modifier.weight(1f))
                StatCard("SAFETY", "100%", modifier = Modifier.weight(1f))
            }

            // Active Control Modes
            Text(
                text = "Active Control Modes",
                style = MaterialTheme.typography.titleMedium,
                color = Color.Black,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 16.dp)
            )
            ControlModeListItem("Brain Control", "EEG Signal Processing active", Icons.Default.Security)
            ControlModeListItem("Muscle Control", "Muscular EMG Support", Icons.Default.OpenWith)
            ControlModeListItem("App Control", "Manual & Remote Control", Icons.Default.PhoneAndroid)

            // What's Next
            Card(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryBlue.copy(alpha = 0.05f)),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                border = BorderStroke(1.dp, primaryBlue.copy(alpha = 0.1f))
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("What's Next?", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    NextStepItem(Icons.Default.Bolt, "Control the robotic hand in real time through the device.")
                    NextStepItem(Icons.Default.Sync, "Start automated rehabilitation sessions tailored to your needs.")
                    NextStepItem(Icons.Default.Speed, "Monitor progress and signal improvement over time.")
                }
            }

            Button(
                onClick = onGoToDashboard,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("GO TO DASHBOARD", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = Color.White)
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))

            OutlinedButton(
                onClick = onViewTutorial, // Tutorial
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, primaryBlue.copy(alpha = 0.5f)),
                colors = ButtonDefaults.outlinedButtonColors(containerColor = primaryBlue.copy(alpha = 0.05f))
            ) {
                Text("VIEW QUICK TUTORIAL", style = MaterialTheme.typography.titleMedium, color = primaryBlue, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(24.dp))
            
            Text(
                text = "You can run NeuroGrip's setup again anytime in the System settings.",
                style = MaterialTheme.typography.bodySmall,
                color = textGray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp)
            )
        }
    }
}

@Composable
fun VerificationItem(name: String, icon: ImageVector) {
    val greenColor = Color(0xFF2E7D32)
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(imageVector = icon, contentDescription = null, tint = greenColor, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(12.dp))
            Text(name, style = MaterialTheme.typography.bodyMedium, color = Color.Black)
        }
        Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Verified", tint = greenColor, modifier = Modifier.size(20.dp))
    }
}

@Composable
fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    val greenColor = Color(0xFF2E7D32)
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                Icon(imageVector = Icons.Default.CheckCircle, contentDescription = null, tint = greenColor, modifier = Modifier.size(16.dp))
            }
        }
    }
}

@Composable
fun ControlModeListItem(title: String, subtitle: String, icon: ImageVector) {
    val greenColor = Color(0xFF2E7D32)
    Card(
        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(Color(0xFFF1F8FF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(imageVector = icon, contentDescription = null, tint = Color(0xFF0052CC), modifier = Modifier.size(20.dp))
            }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.Gray)
            }
            Box(modifier = Modifier.size(12.dp).background(greenColor, CircleShape))
        }
    }
}

@Composable
fun NextStepItem(icon: ImageVector, text: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp), verticalAlignment = Alignment.Top) {
        Icon(imageVector = icon, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Color.Black, lineHeight = 20.sp)
    }
}
