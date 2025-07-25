package com.zenevo.shirodhara

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
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

        // Check and request WRITE_SETTINGS permission if needed, but don't connect automatically
        if (!Settings.System.canWrite(this)) {
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
        }

        setContent {
            ShirodharaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    // Pass the connectToWifi function to AppNavigation
                    AppNavigation(onFindDevice = ::connectToWifi)
                }
            }
        }
    }

    private fun connectToWifi() {
        // First, check for permission. If not granted, prompt the user.
        if (!Settings.System.canWrite(this)) {
            Toast.makeText(this, "Permission required to connect to device", Toast.LENGTH_LONG).show()
            val intent = Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS)
            intent.data = Uri.parse("package:$packageName")
            startActivity(intent)
            return
        }

        // Launch the connection coroutine
        lifecycleScope.launch {
            try {
                Toast.makeText(this@MainActivity, "Searching for device...", Toast.LENGTH_SHORT).show()
                val success = wifiManager.connectToShirodharaWifi()
                if (success) {
                    Toast.makeText(this@MainActivity, "Device connected!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Could not find device.", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                // Handle connection error
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error connecting to device.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
