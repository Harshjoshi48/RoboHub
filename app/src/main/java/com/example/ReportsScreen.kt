package com.example

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.*

data class ReportHistoryItem(
    val title: String,
    val dateRange: String,
    val generatedDate: String,
    val fileSize: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val primaryBlue = Color(0xFF0055AA)
    val lightBg = Color(0xFFF8FAFC)
    val darkText = Color(0xFF1E293B)
    val textGray = Color(0xFF64748B)

    var startDate by remember { mutableStateOf("Oct 1, 2023") }
    var endDate by remember { mutableStateOf("Oct 31, 2023") }
    
    var isGenerating by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    
    var showHistory by remember { mutableStateOf(false) }
    
    val mockHistory = listOf(
        ReportHistoryItem("Smart Report - October", "Oct 1, 2023 - Oct 31, 2023", "Generated: Nov 1, 2023 at 10:00 AM", "2.4 MB"),
        ReportHistoryItem("Smart Report - September", "Sep 1, 2023 - Sep 30, 2023", "Generated: Oct 2, 2023 at 09:15 AM", "2.1 MB"),
        ReportHistoryItem("Smart Report - August", "Aug 1, 2023 - Aug 31, 2023", "Generated: Sep 1, 2023 at 11:30 AM", "2.5 MB")
    )
    
    // DatePickerDialogs
    val calendar = Calendar.getInstance()
    val startDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val monthStr = calendar.apply { set(Calendar.MONTH, month) }.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)
            startDate = "$monthStr $dayOfMonth, $year"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    val endDialog = DatePickerDialog(
        context,
        { _, year, month, dayOfMonth ->
            val monthStr = calendar.apply { set(Calendar.MONTH, month) }.getDisplayName(Calendar.MONTH, Calendar.SHORT, Locale.US)
            endDate = "$monthStr $dayOfMonth, $year"
        },
        calendar.get(Calendar.YEAR),
        calendar.get(Calendar.MONTH),
        calendar.get(Calendar.DAY_OF_MONTH)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { 
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (showHistory) "Report History" else "Reports", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = primaryBlue)
                        if (!showHistory) {
                            Text("Export & Analytics", fontSize = 12.sp, color = textGray)
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (showHistory) {
                            showHistory = false
                        } else {
                            onBack()
                        }
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = primaryBlue)
                    }
                },
                actions = {
                    if (!showHistory) {
                        IconButton(onClick = { showHistory = true }) {
                            Icon(Icons.Default.History, contentDescription = "History", tint = primaryBlue)
                        }
                    } else {
                        IconButton(onClick = { showHistory = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close History", tint = primaryBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = lightBg)
            )
        },
        containerColor = lightBg
    ) { padding ->
        if (showHistory) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                mockHistory.forEach { report ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .background(Color(0xFFEFF6FF), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Outlined.Description, contentDescription = null, tint = primaryBlue, modifier = Modifier.size(20.dp))
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(report.title, fontWeight = FontWeight.Bold, fontSize = 16.sp, color = darkText)
                                    Text("Range: ${report.dateRange}", fontSize = 12.sp, color = textGray)
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            HorizontalDivider(color = Color(0xFFF1F5F9))
                            Spacer(modifier = Modifier.height(12.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(report.generatedDate, fontSize = 11.sp, color = textGray)
                                    Text("Format: PDF • Size: ${report.fileSize}", fontSize = 11.sp, color = textGray)
                                }
                                Row {
                                    IconButton(onClick = {
                                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"))
                                        context.startActivity(browserIntent)
                                    }) {
                                        Icon(Icons.Outlined.Visibility, contentDescription = "View", tint = primaryBlue)
                                    }
                                    IconButton(onClick = {
                                        Toast.makeText(context, "Downloading ${report.title}...", Toast.LENGTH_SHORT).show()
                                        coroutineScope.launch {
                                            delay(1500)
                                            Toast.makeText(context, "Download complete!", Toast.LENGTH_SHORT).show()
                                        }
                                    }) {
                                        Icon(Icons.Outlined.Download, contentDescription = "Download", tint = primaryBlue)
                                    }
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp)
            ) {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Header Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFECFDF5), RoundedCornerShape(16.dp))
                                    .padding(horizontal = 10.dp, vertical = 4.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Ready", color = Color(0xFF047857), fontSize = 12.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                        
                        Box(
                            modifier = Modifier
                                .size(64.dp)
                                .background(Color(0xFFEFF6FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Outlined.Description, contentDescription = null, tint = primaryBlue, modifier = Modifier.size(32.dp))
                        }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Generate Smart Reports", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = darkText)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Create detailed reports about robotic hand activity, brain signals, muscle signals, AI insights, and overall system performance.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = textGray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Date Range
                Text("Date Range", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                Spacer(modifier = Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    // Start Date
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { startDialog.show() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("Start Date", fontSize = 12.sp, color = textGray)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(startDate, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = darkText)
                            }
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = textGray, modifier = Modifier.size(20.dp))
                        }
                    }
                    
                    // End Date
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { endDialog.show() },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFE2E8F0))
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text("End Date", fontSize = 12.sp, color = primaryBlue)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(endDate, fontSize = 14.sp, fontWeight = FontWeight.Medium, color = darkText)
                            }
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = textGray, modifier = Modifier.size(20.dp))
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(24.dp))
                
                // Export Format
                Text("Export Format", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = darkText)
                Spacer(modifier = Modifier.height(12.dp))
                
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    border = BorderStroke(2.dp, primaryBlue)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Icon(Icons.Outlined.PictureAsPdf, contentDescription = null, tint = primaryBlue)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("PDF", fontWeight = FontWeight.Bold, color = primaryBlue)
                    }
                }
                
                Spacer(modifier = Modifier.height(32.dp))
                
                // Actions
                Button(
                    onClick = {
                        if (!isGenerating) {
                            isGenerating = true
                            coroutineScope.launch {
                                val timeToWait = (3000..6000).random().toLong()
                                delay(timeToWait)
                                isGenerating = false
                                Toast.makeText(context, "Report Generated Successfully!", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = primaryBlue)
                ) {
                    if (isGenerating) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Generating...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Generate Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.w3.org/WAI/ER/tests/xhtml/testfiles/resources/pdf/dummy.pdf"))
                        context.startActivity(browserIntent)
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = primaryBlue
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    Text("Open Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Button(
                    onClick = {
                        if (!isDownloading) {
                            isDownloading = true
                            coroutineScope.launch {
                                delay(1500)
                                isDownloading = false
                                Toast.makeText(context, "Report Downloaded to Files", Toast.LENGTH_SHORT).show()
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(16.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFF1F5F9),
                        contentColor = primaryBlue
                    ),
                    elevation = ButtonDefaults.buttonElevation(0.dp)
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp), color = primaryBlue, strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Downloading...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    } else {
                        Text("Download Report", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
                
                Spacer(modifier = Modifier.height(40.dp))
            }
        }
    }
}
