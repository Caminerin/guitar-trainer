package com.caminerin.guitartrainer.ui

import android.content.res.Configuration
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector

enum class AppMode(val title: String, val icon: ImageVector) {
    TUNER("Afinar", Icons.Default.Tune),
    METRONOME("Metr\u00f3nomo", Icons.Default.Speed),
    TRAINER("Entrenador", Icons.Default.School)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pitchResult: PitchDetector.PitchResult?,
    isListening: Boolean
) {
    var selectedTab by rememberSaveable { mutableStateOf(0) }
    var isFullscreenScale by rememberSaveable { mutableStateOf(false) }
    val modes = AppMode.entries
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isFullscreenScale) {
        if (isLandscape) {
            ScaleFretboardScreen(onBack = { isFullscreenScale = false })
        } else {
            RotatePhoneMessage(onBack = { isFullscreenScale = false })
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Guitar Trainer") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TabRow(selectedTabIndex = selectedTab) {
                modes.forEachIndexed { index, mode ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index },
                        text = { Text(mode.title) },
                        icon = { Icon(mode.icon, contentDescription = mode.title) }
                    )
                }
            }

            when (modes[selectedTab]) {
                AppMode.TUNER -> TunerMode(pitchResult = pitchResult)
                AppMode.METRONOME -> MetronomeMode()
                AppMode.TRAINER -> TrainerMode(onOpenScales = { isFullscreenScale = true })
            }
        }
    }
}

@Composable
private fun RotatePhoneMessage(onBack: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ScreenRotation,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Gira el m\u00f3vil en horizontal",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "El entrenador de escalas necesita la pantalla en horizontal para mostrar el m\u00e1stil completo",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        androidx.compose.material3.TextButton(onClick = onBack) {
            Text("Volver", fontSize = 16.sp)
        }
    }
}
