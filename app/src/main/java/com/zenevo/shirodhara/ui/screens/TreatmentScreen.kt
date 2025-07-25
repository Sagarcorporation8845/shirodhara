package com.zenevo.shirodhara.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import com.zenevo.shirodhara.ui.components.ConnectionIndicator
import com.zenevo.shirodhara.ui.theme.*
import com.zenevo.shirodhara.ui.viewmodel.ShirodharaViewModel
import com.zenevo.shirodhara.ui.viewmodel.TreatmentState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TreatmentScreen(
    navController: NavController,
    duration: Int,
    temperature: Int,
    viewModel: ShirodharaViewModel = viewModel()
) {
    val healthData by viewModel.healthData.collectAsState()
    val isConnected by viewModel.isConnected.collectAsState()
    val treatmentState by viewModel.treatmentState.collectAsState()

    // Set parameters when the screen is first composed
    LaunchedEffect(Unit) {
        viewModel.setTreatmentParameters(duration, temperature)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Shirodhara Treatment") },
                navigationIcon = {
                    IconButton(onClick = {
                        viewModel.stopTreatment() // Ensure everything stops
                        viewModel.resetToIdle()
                        navController.popBackStack()
                    }) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .background(BackgroundCream),
            contentAlignment = Alignment.Center
        ) {
            when (treatmentState) {
                is TreatmentState.Heating, TreatmentState.Ready, TreatmentState.InProgress ->
                    TreatmentInProgressContent(
                        navController = navController, // Pass NavController
                        viewModel = viewModel,
                        treatmentState = treatmentState,
                        targetTemp = healthData?.target_temperature ?: temperature,
                        actualTemp = healthData?.temperature ?: 0f,
                        remainingTime = healthData?.remaining_time ?: (duration * 60L)
                    )
                is TreatmentState.Completed ->
                    TreatmentCompletedContent {
                        viewModel.resetToIdle()
                        navController.popBackStack()
                    }
                is TreatmentState.Error -> {
                    // Handle error state if needed
                    Text("An error occurred. Please go back.", color = ErrorRed)
                }
                else -> {
                    // Show a loading indicator or idle state
                    CircularProgressIndicator()
                }
            }
        }
    }
}

@Composable
fun TreatmentInProgressContent(
    navController: NavController, // Add NavController to handle navigation
    viewModel: ShirodharaViewModel,
    treatmentState: TreatmentState,
    targetTemp: Int,
    actualTemp: Float,
    remainingTime: Long
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Status Text
        Text(
            text = when (treatmentState) {
                is TreatmentState.Heating -> "Heating in Progress..."
                is TreatmentState.Ready -> "Ready to Start"
                is TreatmentState.InProgress -> "Treatment in Progress"
                else -> ""
            },
            style = MaterialTheme.typography.headlineSmall,
            color = DarkBlue,
            fontWeight = FontWeight.Bold
        )

        // Timer or Temperature Display
        if (treatmentState is TreatmentState.InProgress) {
            TimerCircle(time = remainingTime)
        } else {
            TemperatureDisplay(target = targetTemp, actual = actualTemp)
        }

        // Action Button Column
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = treatmentState is TreatmentState.Ready) {
                Button(
                    onClick = { viewModel.startTreatment() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SuccessGreen),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("Start Treatment", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // FIX: The onClick for this button now correctly navigates back.
            Button(
                onClick = {
                    viewModel.stopTreatment()
                    viewModel.resetToIdle()
                    navController.popBackStack()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = ErrorRed),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Cancel Treatment", fontSize = 18.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
fun TreatmentCompletedContent(onFinish: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = Modifier.padding(24.dp)
    ) {
        Text(
            "Treatment Completed!",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = DarkBlue
        )
        Spacer(modifier = Modifier.height(32.dp))
        Button(
            onClick = onFinish,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp)
        ) {
            Text("Go to Dashboard", fontSize = 18.sp)
        }
    }
}

@Composable
fun TimerCircle(time: Long) {
    Box(
        modifier = Modifier
            .size(220.dp)
            .clip(CircleShape)
            .background(PrimaryBlue.copy(alpha = 0.1f))
            .padding(16.dp)
            .clip(CircleShape)
            .background(PrimaryBlue),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Remaining Time", color = Color.White, fontSize = 18.sp)
            Text(
                formatTime(time),
                color = Color.White,
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun TemperatureDisplay(target: Int, actual: Float) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceAround,
        verticalAlignment = Alignment.CenterVertically
    ) {
        TempGauge(label = "Target", value = "$target°C", color = PrimaryBlue)
        TempGauge(label = "Actual", value = "${String.format("%.1f", actual)}°C", color = SuccessGreen)
    }
}

@Composable
fun TempGauge(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, color = TextDark, style = MaterialTheme.typography.titleMedium)
        Spacer(modifier = Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(color.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text(value, color = color, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
    }
}

private fun formatTime(seconds: Long): String {
    val minutes = seconds / 60
    val remainingSeconds = seconds % 60
    return String.format("%02d:%02d", minutes, remainingSeconds)
}
