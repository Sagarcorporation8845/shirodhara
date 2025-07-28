package com.zenevo.shirodhara.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zenevo.shirodhara.R
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onTimeout: () -> Unit) {
    // This effect will run once when the screen is first displayed.
    LaunchedEffect(Unit) {
        delay(3000) // Wait for 3 seconds.
        onTimeout() // Call the function to navigate to the next screen.
    }

    // A simple Box to center the content on the screen.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFf7e9d7)), // Custom background color.
        contentAlignment = Alignment.Center
    ) {
        // Column to arrange the logo and text vertically.
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Image(
                painter = painterResource(id = R.drawable.logo),
                contentDescription = "App Logo",
                modifier = Modifier.size(250.dp) // Adjust the size of your logo as needed.
            )

            Spacer(modifier = Modifier.height(16.dp)) // Space between logo and text.

            Text(
                text = "Shirodhara",
                fontSize = 32.sp, // Adjust font size as needed.
                fontWeight = FontWeight.Bold,
                color = Color(0xFF4E342E) // A dark brown color to complement the background.
            )
        }
    }
}
