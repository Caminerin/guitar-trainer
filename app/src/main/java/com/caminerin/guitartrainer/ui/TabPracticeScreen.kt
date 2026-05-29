package com.caminerin.guitartrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun TabPracticeScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("Iniciando...") }
    var catalogSize by remember { mutableStateOf(0) }
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        try {
            status = "Descargando catálogo..."
            TabRepository.loadCatalog(context)
            val err = TabRepository.loadError
            if (err != null) {
                errorMsg = err
                status = "Error"
            } else {
                catalogSize = TabRepository.getCatalog().size
                status = "Catálogo cargado: $catalogSize canciones"
            }
        } catch (e: Exception) {
            errorMsg = "Excepción: ${e.javaClass.simpleName}: ${e.message}"
            status = "Error"
        }
        loading = false
    }

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF121212))
            .padding(16.dp)
    ) {
        Text(
            "Tabs",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(16.dp))

        Text(
            status,
            color = if (errorMsg != null) Color(0xFFFF5252) else Color(0xFFB0BEC5),
            fontSize = 14.sp
        )

        if (loading) {
            Spacer(Modifier.height(16.dp))
            CircularProgressIndicator(color = Color(0xFF7C4DFF))
        }

        if (errorMsg != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                errorMsg!!,
                color = Color(0xFFFF5252),
                fontSize = 12.sp
            )
        }

        if (!loading && errorMsg == null && catalogSize > 0) {
            Spacer(Modifier.height(16.dp))
            Text(
                "$catalogSize canciones disponibles",
                color = Color(0xFF4CAF50),
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            val sample = TabRepository.getCatalog().take(20)
            sample.forEach { entry ->
                Text(
                    "${entry.artist} - ${entry.song}",
                    color = Color.White,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            }

            if (catalogSize > 20) {
                Text(
                    "... y ${catalogSize - 20} más",
                    color = Color(0xFFB0BEC5),
                    fontSize = 12.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}
