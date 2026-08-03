package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Message
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.SmartToy
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import androidx.lifecycle.viewmodel.compose.viewModel

data class ChatMessage(val id: String = java.util.UUID.randomUUID().toString(), val text: String, val isUser: Boolean)


@Composable
fun rememberNetworkConnectivityState(): State<Boolean> {
    val context = LocalContext.current
    val isOnline = remember { mutableStateOf(checkNetworkConnectivity(context)) }

    DisposableEffect(context) {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                isOnline.value = true
            }
            override fun onLost(network: Network) {
                isOnline.value = false
            }
        }
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        connectivityManager.registerNetworkCallback(request, networkCallback)
        
        onDispose {
            connectivityManager.unregisterNetworkCallback(networkCallback)
        }
    }
    
    return isOnline
}

fun checkNetworkConnectivity(context: Context): Boolean {
    val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = connectivityManager.activeNetwork ?: return false
    val activeNetwork = connectivityManager.getNetworkCapabilities(network) ?: return false
    return when {
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> true
        activeNetwork.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> true
        else -> false
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIAssistantScreen(
    viewModel: DashboardViewModel = viewModel(),
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    val isOnline by rememberNetworkConnectivityState()
    
    val primaryBlue = Color(0xFF2F80ED)
    val lightBg = Color(0xFFF8FAFC)
    val darkText = Color(0xFF1E293B)
    val textGray = Color(0xFF64748B)

    var messages by remember { mutableStateOf(listOf<ChatMessage>()) }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    
    val listState = rememberLazyListState()

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    val suggestions = listOf(
        "🧠 How do I improve brain signal quality?",
        "💪 How do I calibrate the EMG sensor?",
        "✋ Show me how to control the hand.",
        "📡 My device won't connect."
    )

    fun sendMessage(text: String) {
        if (text.isBlank()) return
        
        val userMsg = ChatMessage(text = text, isUser = true)
        messages = messages + userMsg
        inputText = ""
        isTyping = true
        
        coroutineScope.launch {
            delay(1500)
            val prefs = context.getSharedPreferences("app_settings", android.content.Context.MODE_PRIVATE)
            val apiKey = prefs.getString("gemini_api_key", "") ?: ""
            val reply = if (apiKey.isNotBlank()) {
                "API Key is configured! Ready to send real AI requests."
            } else {
                "This is a simulated response. Please configure the Gemini API key in the admin panel."
            }
            messages = messages + ChatMessage(text = reply, isUser = false)
            isTyping = false
        }
    }

    Scaffold(
        topBar = {
            Column {
                TopAppBar(
                    title = {
                        Column {
                            Text("AI Assistant", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = darkText)
                            Text("NeuroGrip Smart Assistant", fontSize = 12.sp, color = textGray)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Box(modifier = Modifier.size(6.dp).background(if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isOnline) "AI ONLINE" else "AI OFFLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444))
                            }
                        }
                    },
                    navigationIcon = {
                        Row {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = darkText)
                            }
                            IconButton(onClick = { Toast.makeText(context, "Profile clicked", Toast.LENGTH_SHORT).show() }) {
                                Box(
                                    modifier = Modifier.size(36.dp).background(Color(0xFFE2E8F0), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(Icons.Default.Person, contentDescription = "Profile", tint = primaryBlue, modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    },
                    actions = {
                        IconButton(onClick = { messages = emptyList() }) {
                            Icon(Icons.AutoMirrored.Filled.Message, contentDescription = "New Chat", tint = primaryBlue)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.White)
                )
                HorizontalDivider(color = Color(0xFFE2E8F0), thickness = 1.dp)
            }
        },
        containerColor = lightBg,
        bottomBar = {
            Surface(
                color = Color.White,
                shadowElevation = 8.dp,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { Toast.makeText(context, "Voice input clicked", Toast.LENGTH_SHORT).show() }) {
                        Icon(Icons.Default.Mic, contentDescription = "Voice Input", tint = textGray)
                    }
                    
                    OutlinedTextField(
                        value = inputText,
                        onValueChange = { inputText = it },
                        placeholder = { Text("Ask NeuroGrip AI...", color = Color(0xFFA0AEC0)) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                        shape = RoundedCornerShape(24.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = darkText,
                            unfocusedTextColor = darkText,
                            focusedBorderColor = Color(0xFFE2E8F0),
                            unfocusedBorderColor = Color(0xFFE2E8F0),
                            focusedContainerColor = Color(0xFFF1F5F9),
                            unfocusedContainerColor = Color(0xFFF1F5F9)
                        ),
                        singleLine = true,
                        maxLines = 1
                    )
                    
                    IconButton(
                        onClick = { sendMessage(inputText) },
                        modifier = Modifier.background(primaryBlue, CircleShape).size(40.dp)
                    ) {
                        Icon(Icons.Default.ArrowUpward, contentDescription = "Send", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            if (messages.isEmpty()) {
                // Quick Suggestions
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("QUICK SUGGESTIONS", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = textGray, modifier = Modifier.padding(bottom = 12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SuggestionCard(suggestions[0], Modifier.weight(1f)) { sendMessage(suggestions[0]) }
                        SuggestionCard(suggestions[1], Modifier.weight(1f)) { sendMessage(suggestions[1]) }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        SuggestionCard(suggestions[2], Modifier.weight(1f)) { sendMessage(suggestions[2]) }
                        SuggestionCard(suggestions[3], Modifier.weight(1f)) { sendMessage(suggestions[3]) }
                    }
                }
            }
            
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize().weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(messages) { msg ->
                    if (msg.isUser) {
                        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) {
                            Box(
                                modifier = Modifier
                                    .background(primaryBlue, RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 16.dp, bottomEnd = 4.dp))
                                    .padding(16.dp)
                                    .widthIn(max = 280.dp)
                            ) {
                                Text(msg.text, color = Color.White, fontSize = 15.sp)
                            }
                        }
                    } else {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                            Box(
                                modifier = Modifier.size(28.dp).background(Color(0xFFE2E8F0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = "AI", tint = primaryBlue, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                                    .padding(16.dp)
                                    .widthIn(max = 280.dp)
                            ) {
                                Text(msg.text, color = darkText, fontSize = 15.sp)
                            }
                        }
                    }
                }
                
                if (isTyping) {
                    item {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Start, verticalAlignment = Alignment.Bottom) {
                            Box(
                                modifier = Modifier.size(28.dp).background(Color(0xFFE2E8F0), CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.SmartToy, contentDescription = "AI", tint = primaryBlue, modifier = Modifier.size(16.dp))
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp, bottomStart = 4.dp, bottomEnd = 16.dp))
                                    .padding(horizontal = 16.dp, vertical = 12.dp)
                            ) {
                                Text("Typing...", color = textGray, fontSize = 14.sp, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SuggestionCard(text: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.height(100.dp).clickable { onClick() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Box(modifier = Modifier.fillMaxSize().padding(12.dp), contentAlignment = Alignment.CenterStart) {
            Text(text, fontSize = 14.sp, color = Color(0xFF1E293B), lineHeight = 20.sp)
        }
    }
}
