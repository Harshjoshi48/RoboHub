with open('./app/src/main/java/com/example/AIAssistantScreen.kt', 'r') as f:
    content = f.read()

imports = """import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.widget.Toast"""
content = content.replace("import android.widget.Toast", imports)

helpers = """
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
"""
content = content.replace("@OptIn(ExperimentalMaterial3Api::class)\n@Composable\n", helpers)

val_coroutine = """    val coroutineScope = rememberCoroutineScope()"""
val_online = """    val coroutineScope = rememberCoroutineScope()
    
    val isOnline by rememberNetworkConnectivityState()"""
content = content.replace(val_coroutine, val_online)


old_online_ui = """                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Box(modifier = Modifier.size(6.dp).background(Color(0xFF10B981), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI ONLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF10B981))
                            }"""

new_online_ui = """                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                                Box(modifier = Modifier.size(6.dp).background(if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444), CircleShape))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(if (isOnline) "AI ONLINE" else "AI OFFLINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = if (isOnline) Color(0xFF10B981) else Color(0xFFEF4444))
                            }"""
content = content.replace(old_online_ui, new_online_ui)

with open('./app/src/main/java/com/example/AIAssistantScreen.kt', 'w') as f:
    f.write(content)
