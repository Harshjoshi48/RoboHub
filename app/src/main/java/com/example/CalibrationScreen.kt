package com.example

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.RadioButtonChecked

import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sin

@Composable
fun CalibrationScreen(onBack: () -> Unit, onComplete: () -> Unit) {
    val primaryBlue = Color(0xFF0052CC)
    val textGray = Color(0xFF727785)
    val context = LocalContext.current
    
    val isEegConnected = checkBluetoothDeviceConnected(context, "MindWave") || checkBluetoothDeviceConnected(context, "NeuroSky")
    val isEmgConnected = checkBluetoothDeviceConnected(context, "EMG") || checkBluetoothDeviceConnected(context, "Muscle")

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
                .padding(top = 16.dp, bottom = 16.dp),
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

        // Progress Bar (Step 4 of 6)
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(modifier = Modifier.height(2.dp).width(25.dp).background(primaryBlue))
                Box(modifier = Modifier.height(2.dp).width(25.dp).background(primaryBlue))
                Box(modifier = Modifier.height(2.dp).width(25.dp).background(primaryBlue))
                Box(modifier = Modifier.height(2.dp).width(25.dp).background(primaryBlue))
                Box(modifier = Modifier.height(2.dp).width(60.dp).background(Color(0xFFEBEBEB)))
            }
            Text("STEP 4 OF 6", style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 8.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(200.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(primaryBlue.copy(alpha = 0.1f))
                .padding(bottom = 24.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(android.R.drawable.ic_menu_compass),
                contentDescription = null,
                tint = primaryBlue,
                modifier = Modifier.size(72.dp)
            )
        }

        Text(
            text = "Personalized AI\nCalibration",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = primaryBlue,
            lineHeight = 36.sp
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Relax, then gently move your hand as instructed while NeuroGrip builds your personal control profile.",
            style = MaterialTheme.typography.bodyMedium,
            color = textGray,
            lineHeight = 20.sp
        )
        Spacer(modifier = Modifier.height(24.dp))

        // Brain & Muscle Cards
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            DataCard(
                title = "BRAIN",
                isConnected = true, 
                waveFrequency = 0.05f, 
                stat1Label = "Attention",
                stat1Value = "84%",
                stat2Label = "Meditation",
                stat2Value = "78%",
                isSpiky = false,
                modifier = Modifier.weight(1f)
            )
            DataCard(
                title = "MUSCLE",
                isConnected = true, 
                waveFrequency = 0.15f,
                stat1Label = "Activity",
                stat1Value = "82%",
                stat2Label = "Contact",
                stat2Value = "Perfect",
                isSpiky = true,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))

        // Current Stage
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFEBEBEB))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(contentAlignment = Alignment.Center, modifier = Modifier.size(56.dp)) {
                    CircularProgressIndicator(progress = 0.78f, color = primaryBlue, strokeWidth = 4.dp, modifier = Modifier.fillMaxSize())
                    Text("78%", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = primaryBlue)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("CURRENT STAGE", style = MaterialTheme.typography.labelSmall, color = primaryBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Learning Brain & Muscle\nPatterns", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
                }
            }
        }

        Text(
            text = "CALIBRATION TASKS",
            style = MaterialTheme.typography.labelSmall,
            color = Color.Black,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
            modifier = Modifier.padding(bottom = 12.dp)
        )

        // Task 1: Completed
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 16.dp, start = 8.dp)) {
            Icon(imageVector = Icons.Default.CheckCircle, contentDescription = "Completed", tint = primaryBlue, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(16.dp))
            Text("Rest Position Baseline", style = MaterialTheme.typography.titleSmall, color = Color.Gray, fontWeight = FontWeight.Normal)
        }

        // Task 2: Active
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.5.dp, primaryBlue)
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.RadioButtonChecked, contentDescription = "Active", tint = primaryBlue, modifier = Modifier.size(24.dp)) // Used presence online as placeholder for radio checked
                Spacer(modifier = Modifier.width(16.dp))
                Text("Close Your Hand Slowly", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = Color.Black)
            }
        }

        // AI Status Section
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(
                modifier = Modifier.weight(1f).aspectRatio(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = primaryBlue)
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text("OVERALL AI SCORE", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.8f), fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("80-\n100%", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, lineHeight = 32.sp)
                        Spacer(modifier = Modifier.weight(1f))
                        Icon(painter = painterResource(android.R.drawable.ic_menu_upload), contentDescription = null, tint = Color.White, modifier = Modifier.size(24.dp).padding(bottom = 4.dp))
                    }
                }
            }
            Card(
                modifier = Modifier.weight(1f).aspectRatio(1f),
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F8FF))
            ) {
                Column(modifier = Modifier.padding(16.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
                    Text("AI LEARNING STATUS", style = MaterialTheme.typography.labelSmall, color = primaryBlue, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Optimizing", style = MaterialTheme.typography.titleMedium, color = primaryBlue, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Syncing neural streams with prosthetic micro-actuators.", style = MaterialTheme.typography.bodySmall, color = textGray, lineHeight = 16.sp)
                }
            }
        }

        // Helpful Tip
        Card(
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.Top) {
                Box(modifier = Modifier.size(36.dp).background(primaryBlue, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(painter = painterResource(android.R.drawable.ic_dialog_info), contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(16.dp))
                Column {
                    Text("Helpful Tip", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = primaryBlue)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Blink naturally and try to breathe steadily. Mental clarity helps the AI isolate your muscle intent faster.", style = MaterialTheme.typography.bodySmall, color = textGray, lineHeight = 18.sp)
                }
            }
        }

        Button(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
        ) {
            Text("SAVE CALIBRATION", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color.White)
        }
        
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onComplete,
            modifier = Modifier.fillMaxWidth().height(56.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, primaryBlue)
        ) {
            Text("configer later", style = MaterialTheme.typography.titleMedium, color = primaryBlue, fontWeight = FontWeight.Normal)
        }

        Spacer(modifier = Modifier.height(40.dp))
    }
}

@Composable
fun DataCard(
    title: String, 
    isConnected: Boolean, 
    waveFrequency: Float, 
    stat1Label: String,
    stat1Value: String,
    stat2Label: String,
    stat2Value: String,
    isSpiky: Boolean,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.aspectRatio(0.85f),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFEBEBEB))
    ) {
        Column(modifier = Modifier.padding(16.dp).fillMaxSize()) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
            Spacer(modifier = Modifier.weight(1f))
            
            if (isConnected) {
                var phase by remember { mutableStateOf(0f) }
                LaunchedEffect(Unit) {
                    while(true) {
                        phase -= 0.1f
                        delay(16)
                    }
                }
                
                Box(modifier = Modifier.fillMaxWidth().height(40.dp)) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val path = Path()
                        val height = size.height
                        val width = size.width
                        val centerY = height / 2
                        val amplitude = height * 0.4f
                        
                        path.moveTo(0f, centerY)
                        for (x in 0..width.toInt() step 2) {
                            var y = centerY + amplitude * sin(x * waveFrequency + phase)
                            if (isSpiky) {
                                // Add some pseudo-random noise for EMG
                                y += (Math.random() - 0.5f).toFloat() * 10f * sin(x * waveFrequency * 3 + phase)
                            }
                            if (x == 0) path.moveTo(x.toFloat(), y)
                            else path.lineTo(x.toFloat(), y)
                        }
                        
                        drawPath(path, color = Color(0xFF00B0FF), style = Stroke(width = 3f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                
                Spacer(modifier = Modifier.weight(1f))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stat1Label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF727785), fontSize = 10.sp)
                    Text(stat1Value, style = MaterialTheme.typography.bodySmall, color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(stat2Label, style = MaterialTheme.typography.bodySmall, color = Color(0xFF727785), fontSize = 10.sp)
                    Text(stat2Value, style = MaterialTheme.typography.bodySmall, color = if (stat2Value == "Perfect") Color(0xFF2E7D32) else Color.Black, fontWeight = FontWeight.Bold, fontSize = 10.sp)
                }
            }
        }
    }
}
