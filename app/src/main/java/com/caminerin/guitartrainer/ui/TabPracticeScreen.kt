package com.caminerin.guitartrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TabPracticeScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    var status by remember { mutableStateOf("Sin LaunchedEffect - test UI") }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text("Tabs", color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        Text(status, color = Color(0xFFB0BEC5), fontSize = 14.sp)

        if (loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = Color(0xFF7C4DFF))
        }

        if (errorMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(errorMsg!!, color = Color(0xFFFF5252), fontSize = 12.sp)
        }
    }
}
