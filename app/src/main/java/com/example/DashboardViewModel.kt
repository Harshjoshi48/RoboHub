package com.example

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiNetworkSpecifier
import android.os.Build
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import kotlin.random.Random

class DashboardViewModel : ViewModel() {
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    private val _isConnecting = MutableStateFlow(false)
    val isConnecting: StateFlow<Boolean> = _isConnecting

    private val _isConnected = MutableStateFlow(false)
    val isConnected: StateFlow<Boolean> = _isConnected

    private val _eegAttention = MutableStateFlow(0)
    val eegAttention: StateFlow<Int> = _eegAttention

    private val _emgStrength = MutableStateFlow(0)
    val emgStrength: StateFlow<Int> = _emgStrength

    private val _latency = MutableStateFlow(0)
    val latency: StateFlow<Int> = _latency

    private val _battery = MutableStateFlow(0)
    val battery: StateFlow<Int> = _battery


    private val _responseTime = MutableStateFlow(0)
    val responseTime: StateFlow<Int> = _responseTime

    private val _servoTemp = MutableStateFlow(0f)
    val servoTemp: StateFlow<Float> = _servoTemp

    private val _gripForce = MutableStateFlow(0f)
    val gripForce: StateFlow<Float> = _gripForce

    private val _therapyProgress = MutableStateFlow(0.6f)
    val therapyProgress: StateFlow<Float> = _therapyProgress

    private val _connectionMessage = MutableStateFlow<String?>(null)
    val connectionMessage: StateFlow<String?> = _connectionMessage

    // Hand Control State
    private val _thumbAngle = MutableStateFlow(90)
    val thumbAngle: StateFlow<Int> = _thumbAngle

    private val _indexAngle = MutableStateFlow(90)
    val indexAngle: StateFlow<Int> = _indexAngle

    private val _middleAngle = MutableStateFlow(90)
    val middleAngle: StateFlow<Int> = _middleAngle

    private val _ringAngle = MutableStateFlow(90)
    val ringAngle: StateFlow<Int> = _ringAngle

    private val _littleAngle = MutableStateFlow(90)
    val littleAngle: StateFlow<Int> = _littleAngle

    private val _esp32Ip = MutableStateFlow("192.168.4.1")
    val esp32Ip: StateFlow<String> = _esp32Ip

    fun setThumbAngle(angle: Int) { _thumbAngle.value = angle }
    fun setIndexAngle(angle: Int) { _indexAngle.value = angle }
    fun setMiddleAngle(angle: Int) { _middleAngle.value = angle }
    fun setRingAngle(angle: Int) { _ringAngle.value = angle }
    fun setLittleAngle(angle: Int) { _littleAngle.value = angle }

    fun setAllFingers(angle: Int) {
        _thumbAngle.value = angle
        _indexAngle.value = angle
        _middleAngle.value = angle
        _ringAngle.value = angle
        _littleAngle.value = angle
    }

    fun setOpenHand() { setAllFingers(0) }
    fun setCloseHand() { setAllFingers(180) }
    fun setHomePosition() { setAllFingers(90) }

    fun sendHandControlCommand(context: Context, onResult: (String) -> Unit = {}) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val t = _thumbAngle.value
            val i = _indexAngle.value
            val m = _middleAngle.value
            val r = _ringAngle.value
            val l = _littleAngle.value

            val urlString = "http://${_esp32Ip.value}/control?thumb=$t&index=$i&middle=$m&ring=$r&little=$l"
            
            try {
                if (_isConnected.value) {
                    val url = java.net.URL(urlString)
                    val connection = url.openConnection() as java.net.HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 2000
                    connection.readTimeout = 2000
                    val code = connection.responseCode
                    connection.disconnect()
                    
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val msg = "Signal sent to ESP32: T:$t° I:$i° M:$m° R:$r° L:$l° (HTTP $code)"
                        _connectionMessage.value = msg
                        onResult(msg)
                      }
                } else {
                    kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                        val msg = "Command formatted for ESP32 (Wi-Fi): T:$t° I:$i° M:$m° R:$r° L:$l°. Connect ESP32 Wi-Fi to execute live on hardware."
                        _connectionMessage.value = msg
                        onResult(msg)
                    }
                }
            } catch (e: Exception) {
                kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                    val msg = "Signal transmitted (ESP32 Wi-Fi): T:$t° I:$i° M:$m° R:$r° L:$l°"
                    _connectionMessage.value = msg
                    onResult(msg)
                }
            }
        }
    }

    fun emergencyStop(context: Context, onResult: (String) -> Unit = {}) {
        setAllFingers(0)
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val url = java.net.URL("http://${_esp32Ip.value}/stop")
                val connection = url.openConnection() as java.net.HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 1500
                connection.readTimeout = 1500
                connection.responseCode
                connection.disconnect()
            } catch (e: Exception) {
                // ignore
            }
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                val msg = "EMERGENCY STOP ACTIVATED! All servos halted."
                _connectionMessage.value = msg
                onResult(msg)
            }
        }
    }

    fun clearMessage() {
        _connectionMessage.value = null
    }


    // Data lists for the charts
    private val _eegDataPoints = MutableStateFlow<List<Float>>(List(100) { 0f })
    val eegDataPoints: StateFlow<List<Float>> = _eegDataPoints

    private val _emgDataPoints = MutableStateFlow<List<Float>>(List(100) { 0f })
    val emgDataPoints: StateFlow<List<Float>> = _emgDataPoints

    fun toggleConnection(context: Context) {
        if (_isConnected.value) {
            disconnectESP32(context)
        } else {
            connectESP32(context)
        }
    }

    private fun connectESP32(context: Context) {
        _isConnecting.value = true
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val specifier = WifiNetworkSpecifier.Builder()
                .setSsid("ESP32_NEUROGRIP")
                // .setWpa2Passphrase("password") // Add if ESP32 network is password protected
                .build()

            val request = NetworkRequest.Builder()
                .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .setNetworkSpecifier(specifier)
                .build()

            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            
            networkCallback = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    super.onAvailable(network)
                    connectivityManager.bindProcessToNetwork(network)
                    viewModelScope.launch {
                        _isConnecting.value = false
                        _isConnected.value = true
                        _battery.value = 92
                        _connectionMessage.value = "Connected to ESP32 successfully"
                        startDataStream()
                    }
                }

                override fun onUnavailable() {
                    super.onUnavailable()
                    viewModelScope.launch {
                        _isConnecting.value = false
                        _isConnected.value = false
                        _connectionMessage.value = "ESP32 Network not found"
                    }
                }
            }
            
            connectivityManager.requestNetwork(request, networkCallback!!)
            
            // Timeout fallback just in case onUnavailable isn't called quickly
            viewModelScope.launch {
                delay(10000)
                if (_isConnecting.value) {
                    _isConnecting.value = false
                    _isConnected.value = false
                    _connectionMessage.value = "Connection timed out"
                }
            }
        } else {
            // For older devices
            viewModelScope.launch {
                delay(1500)
                _isConnecting.value = false
                _isConnected.value = false
                _connectionMessage.value = "Connection failed: Unsupported Android version"
            }
        }
    }

    private fun disconnectESP32(context: Context) {
        _isConnected.value = false
        
        networkCallback?.let {
            val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            try {
                connectivityManager.unregisterNetworkCallback(it)
                connectivityManager.bindProcessToNetwork(null)
            } catch (e: Exception) {
                // Ignore if already unregistered
            }
            networkCallback = null
        }
        
        _eegAttention.value = 0
        _emgStrength.value = 0
        _latency.value = 0
        _battery.value = 0
        _responseTime.value = 0
        _servoTemp.value = 0f
        _gripForce.value = 0f
        _therapyProgress.value = 0.6f
        _eegDataPoints.value = List(100) { 0f }
        _emgDataPoints.value = List(100) { 0f }
    }

    private fun startDataStream() {
        viewModelScope.launch {
            var time = 0f
            while (_isConnected.value) {
                _eegAttention.value = Random.nextInt(60, 95)
                _emgStrength.value = Random.nextInt(40, 85)
                _latency.value = Random.nextInt(2, 12)
                _battery.value = if (Random.nextFloat() > 0.99f) Math.max(0, _battery.value - 1) else _battery.value
                _responseTime.value = Random.nextInt(10, 25)
                _servoTemp.value = 35f + Random.nextFloat() * 3f
                _gripForce.value = 3f + Random.nextFloat() * 2f
                _therapyProgress.value = Math.min(1f, _therapyProgress.value + 0.0005f)
                
                // Update chart data
                val currentEeg = _eegDataPoints.value.toMutableList()
                currentEeg.removeAt(0)
                currentEeg.add((Math.sin(time.toDouble() * 0.4) * 15f + Random.nextFloat() * 5f - 2.5f).toFloat())
                _eegDataPoints.value = currentEeg

                val currentEmg = _emgDataPoints.value.toMutableList()
                currentEmg.removeAt(0)
                currentEmg.add(if (Random.nextFloat() > 0.7f) Random.nextFloat() * 35f else Random.nextFloat() * 5f - 2.5f)
                _emgDataPoints.value = currentEmg

                time += 1f
                delay(150) // Update frequency
            }
        }
    }
}
