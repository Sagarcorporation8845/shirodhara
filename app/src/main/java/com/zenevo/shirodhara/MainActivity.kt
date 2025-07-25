package com.zenevo.shirodhara

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.zenevo.shirodhara.navigation.AppNavigation
import com.zenevo.shirodhara.ui.theme.ShirodharaTheme
import com.zenevo.shirodhara.utils.WifiManager
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var wifiManager: WifiManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        wifiManager = WifiManager(this)
        
        // Check and request WRITE_SETTINGS permission
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        } else {
            connectToWifi()
        }
        
        setContent {
            ShirodharaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    AppNavigation()
                }
            }
        }
    }
    
    override fun onResume() {
        super.onResume()
        if (Settings.System.canWrite(this)) {
            connectToWifi()
        }
    }
    
    private fun connectToWifi() {
        lifecycleScope.launch {
            try {
                wifiManager.connectToShirodharaWifi()
            } catch (e: Exception) {
                // Handle connection error
                e.printStackTrace()
            }
        }
    }
}