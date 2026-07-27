package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

@Composable
fun RoboticHandTestScreen(onBack: () -> Unit, onComplete: () -> Unit) {
    val primaryBlue = Color(0xFF0052CC)
    val textGray = Color(0xFF727785)
    val lightBg = Color(0xFFF8F9FA)
    val redColor = Color(0xFFD32F2F)

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
            IconButton(onClick = onBack) {
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
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.height(3.dp).width(25.dp).background(primaryBlue, RoundedCornerShape(1.5.dp)))
                    Box(modifier = Modifier.height(3.dp).width(25.dp).background(primaryBlue, RoundedCornerShape(1.5.dp)))
                    Box(modifier = Modifier.height(3.dp).width(25.dp).background(primaryBlue, RoundedCornerShape(1.5.dp)))
                    Box(modifier = Modifier.height(3.dp).width(25.dp).background(primaryBlue, RoundedCornerShape(1.5.dp)))
                    Box(modifier = Modifier.height(3.dp).width(25.dp).background(primaryBlue, RoundedCornerShape(1.5.dp)))
                    Box(modifier = Modifier.height(3.dp).width(25.dp).background(Color(0xFFEBEBEB), RoundedCornerShape(1.5.dp)))
                }
                Text("STEP 5 OF 6", style = MaterialTheme.typography.labelSmall, color = textGray, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            }

            Text(
                text = "FIRST-TIME SETUP",
                style = MaterialTheme.typography.labelSmall,
                color = textGray,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Robotic Hand Test",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Test every movement to ensure your NeuroGrip system is working perfectly.",
                style = MaterialTheme.typography.bodyMedium,
                color = textGray,
                lineHeight = 20.sp
            )
            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Functional Movement Test", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("NeuroGrip will automatically verify every finger movement and confirm that all control methods are working correctly.", style = MaterialTheme.typography.bodySmall, color = textGray, lineHeight = 16.sp)
                }
            }

            // Offline Status and Hand Visualization
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.align(Alignment.Start).background(redColor.copy(alpha = 0.1f), RoundedCornerShape(12.dp)).padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Box(modifier = Modifier.size(6.dp).background(redColor, CircleShape))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("OFFLINE", style = MaterialTheme.typography.labelSmall, color = redColor, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    }
                    Spacer(modifier = Modifier.height(32.dp))
                    
                    // Custom Hand Visualization from Image
                    Box(
                        modifier = Modifier.size(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        // Inner glow
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            drawCircle(
                                brush = Brush.radialGradient(
                                    colors = listOf(primaryBlue.copy(alpha = 0.15f), Color.Transparent)
                                ),
                                radius = size.minDimension / 2
                            )
                            
                            // Outer subtle ring
                            drawCircle(
                                color = primaryBlue.copy(alpha = 0.1f),
                                radius = size.minDimension / 2,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Top faint ring segment (simulating the arc in the image)
                        Canvas(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                            drawArc(
                                color = Color.Gray.copy(alpha = 0.2f),
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        // Bottom faint ring segment
                        Canvas(modifier = Modifier.fillMaxSize().padding(20.dp)) {
                            drawArc(
                                color = primaryBlue.copy(alpha = 0.4f),
                                startAngle = 0f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }

                        Icon(
                            imageVector = Icons.Default.PanTool, 
                            contentDescription = "Robotic Hand", 
                            tint = Color(0xFF9098A3), 
                            modifier = Modifier.size(72.dp)
                        )
                    }
                    
                    Spacer(modifier = Modifier.height(40.dp))
                    
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HandJointInfo("Thumb", "0°")
                        HandJointInfo("Index", "0°")
                        HandJointInfo("Middle", "0°")
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                        HandJointInfo("Ring", "0°")
                        HandJointInfo("Little", "0°")
                        HandJointInfo("Wrist", "0°")
                    }
                }
            }

            // Movement Protocol
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text("Movement Protocol", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    Spacer(modifier = Modifier.height(16.dp))
                    ProtocolItem("1. Open Hand", "-")
                    ProtocolItem("2. Close Hand", "-")
                    ProtocolItem("3. Pinch Grip", "-")
                    ProtocolItem("4. Wrist Rotation", "-")
                    ProtocolItem("5. Point Gesture", "-")
                    ProtocolItem("6. Thumbs Up", "-")
                }
            }

            // Control Modes
            Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ControlModeCard("BRAIN CONTROL", android.R.drawable.ic_menu_info_details, modifier = Modifier.weight(1f))
                ControlModeCard("MUSCLE CONTROL", android.R.drawable.ic_menu_sort_by_size, modifier = Modifier.weight(1f))
                ControlModeCard("APP CONTROL", android.R.drawable.ic_menu_manage, modifier = Modifier.weight(1f))
            }

            // System Diagnostics
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(painter = painterResource(android.R.drawable.checkbox_on_background), contentDescription = null, tint = primaryBlue, modifier = Modifier.size(24.dp))
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("System Diagnostics", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.height(20.dp))
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            DiagnosticItem("Servo Motors", "-")
                            DiagnosticItem("Response Time", "-")
                            DiagnosticItem("Battery", "-")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            DiagnosticItem("Accuracy", "-")
                            DiagnosticItem("Communication", "-")
                            DiagnosticItem("Temperature", "-")
                        }
                    }
                }
            }

            // AI Diagnostics Insight
            Card(
                modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryBlue),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
            ) {
                Row(modifier = Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(48.dp).background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
                        Icon(painter = painterResource(android.R.drawable.ic_menu_info_details), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp))
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("AI Diagnostics Insight", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.White)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("NeuroGrip AI has reached premium medical specifications. All systems are stable.", style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.9f), lineHeight = 16.sp)
                    }
                }
            }

            Button(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
            ) {
                Text("COMPLETE SETUP", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
            }
            
            Spacer(modifier = Modifier.height(16.dp))

            OutlinedButton(
                onClick = {}, // Refresh/run test again
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp),
                border = BorderStroke(1.dp, primaryBlue)
            ) {
                Icon(imageVector = Icons.Default.Refresh, contentDescription = null, tint = primaryBlue, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("RUN TEST AGAIN", style = MaterialTheme.typography.titleMedium, color = primaryBlue, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            TextButton(
                onClick = onComplete,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                shape = RoundedCornerShape(28.dp)
            ) {
                Text("CONFIGURE LATER", style = MaterialTheme.typography.titleMedium, color = textGray, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(40.dp))
        }
    }
}

@Composable
fun HandJointInfo(name: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
fun ProtocolItem(name: String, status: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp).background(Color(0xFFFFEBEE), RoundedCornerShape(12.dp)).padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(name, style = MaterialTheme.typography.bodyMedium, color = Color.Black, fontWeight = FontWeight.Medium)
        Text(status, style = MaterialTheme.typography.bodyMedium, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun ControlModeCard(title: String, iconRes: Int, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.aspectRatio(0.8f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEBEBEB)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(painter = painterResource(id = iconRes), contentDescription = null, tint = Color.Gray, modifier = Modifier.size(28.dp))
            Spacer(modifier = Modifier.height(12.dp))
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Black, fontSize = 9.sp, textAlign = TextAlign.Center, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.size(24.dp).background(Color(0xFFF1F8FF), CircleShape), contentAlignment = Alignment.Center) {
                Icon(painter = painterResource(android.R.drawable.arrow_up_float), contentDescription = null, tint = Color(0xFF0052CC), modifier = Modifier.size(14.dp))
            }
        }
    }
}

@Composable
fun DiagnosticItem(name: String, value: String) {
    Column(modifier = Modifier.padding(bottom = 16.dp)) {
        Text(name, style = MaterialTheme.typography.labelSmall, color = Color.Gray, fontWeight = FontWeight.Medium)
        Spacer(modifier = Modifier.height(4.dp))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = Color.Black, fontWeight = FontWeight.Bold)
    }
}
