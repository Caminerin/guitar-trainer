package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.School
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import com.caminerin.guitartrainer.audio.PitchDetector

enum class AppMode(val title: String, val icon: ImageVector) {
    TUNER("Afinar", Icons.Default.Tune),
    METRONOME("Metrónomo", Icons.Default.Speed),
    TRAINER("Entrenador", Icons.Default.School)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pitchResult: PitchDetector.PitchResult?,
    isListening: Boolean
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var isFullscreenScale by remember { mutableStateOf(false) }
    val modes = AppMode.entries

    if (isFullscreenScale) {
        ScaleFretboardScreen(onBack = { isFullscreenScale = false })
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
