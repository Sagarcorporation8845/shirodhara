package com.zenevo.shirodhara.utils

import android.content.Context
import android.net.wifi.WifiConfiguration
import android.net.wifi.WifiManager
import android.net.wifi.WifiNetworkSpecifier
import android.net.NetworkRequest
import android.net.NetworkCapabilities
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import android.provider.Settings
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class WifiManager(private val context: Context) {
    private val wifiManager = context.getSystemService(Context.WIFI_SERVICE) as WifiManager
    private val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    
    suspend fun connectToShirodharaWifi(): Boolean {
        // Check if we have the required permissions
        if (!Settings.System.canWrite(context)) {
            throw SecurityException("WRITE_SETTINGS permission not granted")
        }
        
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            connectToWifiAndroid10Plus()
        } else {
            connectToWifiLegacy()
        }
    }
    
    private suspend fun connectToWifiAndroid10Plus(): Boolean {
        return suspendCancellableCoroutine { continuation ->
            try {
                val specifier = WifiNetworkSpecifier.Builder()
                    .setSsid("Shirodhara")
                    .setWpa2Passphrase("Zenevo@123")
                    .build()
                
                val request = NetworkRequest.Builder()
                    .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
                    .removeCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .setNetworkSpecifier(specifier)
                    .build()
                
                val networkCallback = object : ConnectivityManager.NetworkCallback() {
                    override fun onAvailable(network: Network) {
                        connectivityManager.bindProcessToNetwork(network)
                        continuation.resume(true)
                    }
                    
                    override fun onUnavailable() {
                        continuation.resume(false)
                    }
                }
                
                connectivityManager.requestNetwork(request, networkCallback)
                
                continuation.invokeOnCancellation {
                    connectivityManager.unregisterNetworkCallback(networkCallback)
                }
            } catch (e: Exception) {
                continuation.resume(false)
            }
        }
    }
    
    private fun connectToWifiLegacy(): Boolean {
        if (!wifiManager.isWifiEnabled) {
            wifiManager.isWifiEnabled = true
        }
        
        val conf = WifiConfiguration().apply {
            SSID = "\"Shirodhara\""
            preSharedKey = "\"Zenevo@123\""
        }
        
        val netId = wifiManager.addNetwork(conf)
        wifiManager.disconnect()
        wifiManager.enableNetwork(netId, true)
        return wifiManager.reconnect()
    }
} 