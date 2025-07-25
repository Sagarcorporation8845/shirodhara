package com.zenevo.shirodhara.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenevo.shirodhara.ui.theme.*
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onStartTreatment: (duration: Int, temperature: Int) -> Unit
) {
    var durationMinutes by remember { mutableStateOf(30) }
    var temperature by remember { mutableStateOf(37) }
    
    val maxDuration = 60
    val maxTemperature = 45
    
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = BackgroundCream
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Text(
                text = "Shirodhara Treatment",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = DarkBlue,
                modifier = Modifier.padding(vertical = 16.dp)
            )
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Treatment settings card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Treatment Settings",
                        style = MaterialTheme.typography.titleLarge,
                        color = PrimaryBlue,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )
                    
                    // Duration setting
                    SettingSection(
                        title = "Duration",
                        value = "$durationMinutes min",
                        valueRange = 5f..maxDuration.toFloat(),
                        currentValue = durationMinutes.toFloat(),
                        onValueChange = { durationMinutes = it.roundToInt() },
                        steps = (maxDuration / 5) - 1,
                        maxLabel = "$maxDuration min"
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    // Temperature setting
                    SettingSection(
                        title = "Temperature",
                        value = "$temperature °C",
                        valueRange = 30f..maxTemperature.toFloat(),
                        currentValue = temperature.toFloat(),
                        onValueChange = { temperature = it.roundToInt() },
                        steps = (maxTemperature - 30) - 1,
                        maxLabel = "$maxTemperature °C"
                    )
                }
            }
            
            Spacer(modifier = Modifier.weight(1f))
            
            // Start treatment button
            Button(
                onClick = { onStartTreatment(durationMinutes, temperature) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = PrimaryBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "Start Treatment",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingSection(
    title: String,
    value: String,
    valueRange: ClosedFloatingPointRange<Float>,
    currentValue: Float,
    onValueChange: (Float) -> Unit,
    steps: Int,
    maxLabel: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextDark
            )
            
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(LightBlue, PrimaryBlue)
                        )
                    )
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = value,
                    color = MaterialTheme.colorScheme.surface,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
        
        Spacer(modifier = Modifier.height(8.dp))
        
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Min",
                fontSize = 12.sp,
                modifier = Modifier.width(30.dp),
                textAlign = TextAlign.Center
            )
            
            Slider(
                value = currentValue,
                onValueChange = onValueChange,
                valueRange = valueRange,
                steps = steps,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp),
                colors = SliderDefaults.colors(
                    thumbColor = AccentAmber,
                    activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = LightBlue.copy(alpha = 0.3f)
                )
            )
            
            Text(
                text = maxLabel,
                fontSize = 12.sp,
                modifier = Modifier.width(40.dp),
                textAlign = TextAlign.Center
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DashboardScreenPreview() {
    ShirodharaTheme {
        DashboardScreen(onStartTreatment = { _, _ -> })
    }
} 