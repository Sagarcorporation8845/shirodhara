package com.zenevo.shirodhara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import androidx.navigation.compose.currentBackStackEntryAsState
import com.zenevo.shirodhara.ui.components.ConnectionIndicator
import com.zenevo.shirodhara.ui.theme.*
import com.zenevo.shirodhara.ui.viewmodel.ShirodharaViewModel
import com.zenevo.shirodhara.ui.viewmodel.TreatmentState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentScreen(
    viewModel: ShirodharaViewModel = viewModel(),
    navController: NavController
) {
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val initialDuration = navBackStackEntry?.arguments?.getString("duration")?.toIntOrNull() ?: 30
    val initialTemperature = navBackStackEntry?.arguments?.getString("temperature")?.toIntOrNull() ?: 37
    
    val healthData by viewModel.healthData.collectAsState()
    val treatmentState by viewModel.treatmentState.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    
    // Effect to start treatment when screen is first shown
    LaunchedEffect(Unit) {
        viewModel.startTreatment(
            duration = initialDuration,
            temperature = initialTemperature,
            startTreatmentNow = true
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Treatment in Progress") },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    ConnectionIndicator(
                        isConnected = isConnected,
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Timer Circle
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(CircleShape)
                    .background(PrimaryBlue),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Remaining Time",
                        color = Color.White,
                        fontSize = 16.sp
                    )
                    Text(
                        text = formatTime(healthData?.remaining_time ?: 0L),
                        color = Color.White,
                        fontSize = 40.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            // Temperature Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = Color(0xFF2C2C2C)
                ),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Temperature",
                        color = PrimaryBlue,
                        fontSize = 24.sp,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        // Target Temperature
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Target",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF1976D2).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${healthData?.target_temperature ?: initialTemperature}°C",
                                    color = Color(0xFF1976D2),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                        
                        // Actual Temperature
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Actual",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                            Box(
                                modifier = Modifier
                                    .size(100.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF4CAF50).copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "${String.format("%.1f", healthData?.temperature ?: 0.0)}°C",
                                    color = Color(0xFF4CAF50),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // Cancel Treatment Button
            Button(
                onClick = { navController.popBackStack() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFFD32F2F)
                ),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "Cancel Treatment",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private fun formatTime(milliseconds: Long): String {
    val seconds = milliseconds / 1000
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
} 