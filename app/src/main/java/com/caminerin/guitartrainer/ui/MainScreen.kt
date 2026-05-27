package com.caminerin.guitartrainer.ui

import android.app.Activity
import android.content.res.Configuration
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.RemoveRedEye
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector

private enum class FullscreenMode {
    NONE,
    SCALES,
    CHORDS,
    CAGED,
    QUIZ,
    CHORD_PRACTICE
}

enum class AppMode(val title: String, val icon: ImageVector) {
    VISUALIZER("Visualizador", Icons.Default.RemoveRedEye),
    PRACTICE("Pr\u00e1ctica", Icons.Default.FitnessCenter),
    QUIZ("Quiz", Icons.Default.Quiz),
    TOOLS("Herramientas", Icons.Default.Build)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    pitchResult: PitchDetector.PitchResult?,
    isListening: Boolean
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        NoteFormatPreference.load(context)
        AppPreferences.load(context)
        DegreeColorPrefs.load(context)
    }

    var selectedTab by rememberSaveable { mutableStateOf(AppPreferences.lastTab) }
    var fullscreenMode by rememberSaveable { mutableStateOf(FullscreenMode.NONE.name) }
    var showExitDialog by remember { mutableStateOf(false) }
    val modes = AppMode.entries
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    val currentMode = try { FullscreenMode.valueOf(fullscreenMode) } catch (_: Exception) { FullscreenMode.NONE }

    // Back handler: if in fullscreen mode, go back to menu; if in menu, show exit dialog
    BackHandler {
        if (currentMode != FullscreenMode.NONE) {
            fullscreenMode = FullscreenMode.NONE.name
        } else {
            showExitDialog = true
        }
    }

    when (currentMode) {
        FullscreenMode.SCALES -> {
            if (isLandscape) ScaleFretboardScreen(onBack = { fullscreenMode = FullscreenMode.NONE.name })
            else RotatePhoneMessage(onBack = { fullscreenMode = FullscreenMode.NONE.name })
            return
        }
        FullscreenMode.CHORDS -> {
            if (isLandscape) ChordVisualizerScreen(
                onBack = { fullscreenMode = FullscreenMode.NONE.name },
                onGoToPractice = { fullscreenMode = FullscreenMode.CHORD_PRACTICE.name }
            )
            else RotatePhoneMessage(onBack = { fullscreenMode = FullscreenMode.NONE.name })
            return
        }
        FullscreenMode.CAGED -> {
            if (isLandscape) CagedPracticeScreen(onBack = { fullscreenMode = FullscreenMode.NONE.name })
            else RotatePhoneMessage(onBack = { fullscreenMode = FullscreenMode.NONE.name })
            return
        }
        FullscreenMode.QUIZ -> {
            if (isLandscape) ScaleQuizScreen(onBack = { fullscreenMode = FullscreenMode.NONE.name })
            else RotatePhoneMessage(onBack = { fullscreenMode = FullscreenMode.NONE.name })
            return
        }
        FullscreenMode.CHORD_PRACTICE -> {
            ChordPracticeScreen(
                onBack = { fullscreenMode = FullscreenMode.NONE.name },
                onGoToVisualizer = { fullscreenMode = FullscreenMode.CHORDS.name }
            )
            return
        }
        FullscreenMode.NONE -> { /* show normal UI below */ }
    }

    // Exit confirmation dialog
    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("\u00bfSalir de Guitar Trainer?") },
            confirmButton = {
                TextButton(onClick = {
                    showExitDialog = false
                    (context as? Activity)?.finish()
                }) {
                    Text("Salir", color = Color(0xFFE53935))
                }
            },
            dismissButton = {
                TextButton(onClick = { showExitDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Styled app name
                        Text(
                            "Guitar",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFFC107)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            "Trainer",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Light,
                            fontStyle = FontStyle.Italic,
                            color = Color(0xFFB0BEC5)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "\uD83C\uDFB8",
                            fontSize = 18.sp
                        )
                    }
                },
                actions = {
                    Box(
                        modifier = Modifier
                            .padding(end = 12.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF5C6BC0).copy(alpha = 0.3f))
                            .clickable {
                                val newFormat = if (NoteFormatPreference.current == NoteFormat.AMERICAN)
                                    NoteFormat.EUROPEAN else NoteFormat.AMERICAN
                                NoteFormatPreference.set(newFormat, context)
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(
                            NoteFormatPreference.current.label,
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF1A1A1A),
                    titleContentColor = Color.White
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF1A1A1A))
                .padding(padding)
        ) {
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color(0xFF252525),
                contentColor = Color.White
            ) {
                modes.forEachIndexed { index, mode ->
                    Tab(
                        selected = selectedTab == index,
                        onClick = { selectedTab = index; AppPreferences.saveTab(index, context) },
                        text = {
                            Text(
                                mode.title,
                                fontSize = 11.sp,
                                color = if (selectedTab == index) Color(0xFFFFC107) else Color.White.copy(alpha = 0.6f)
                            )
                        },
                        icon = {
                            Icon(
                                mode.icon,
                                contentDescription = mode.title,
                                modifier = Modifier.size(20.dp),
                                tint = if (selectedTab == index) Color(0xFFFFC107) else Color.White.copy(alpha = 0.6f)
                            )
                        }
                    )
                }
            }

            when (modes[selectedTab]) {
                AppMode.VISUALIZER -> VisualizerMenu(
                    onOpenScales = { fullscreenMode = FullscreenMode.SCALES.name },
                    onOpenChords = { fullscreenMode = FullscreenMode.CHORDS.name }
                )
                AppMode.PRACTICE -> PracticeMenu(
                    onOpenCagedPractice = { fullscreenMode = FullscreenMode.CAGED.name },
                    onOpenChordPractice = { fullscreenMode = FullscreenMode.CHORD_PRACTICE.name }
                )
                AppMode.QUIZ -> QuizMenu(
                    onOpenQuiz = { fullscreenMode = FullscreenMode.QUIZ.name }
                )
                AppMode.TOOLS -> ToolsMenu(
                    pitchResult = pitchResult
                )
            }
        }
    }
}

@Composable
private fun RotatePhoneMessage(onBack: () -> Unit) {
    BackHandler { onBack() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A1A))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            imageVector = Icons.Default.ScreenRotation,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = Color(0xFFFFC107).copy(alpha = 0.7f)
        )
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "Gira el m\u00f3vil en horizontal",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color.White,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "Esta vista necesita la pantalla en horizontal para mostrar el m\u00e1stil completo",
            fontSize = 15.sp,
            color = Color.White.copy(alpha = 0.6f),
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(32.dp))
        TextButton(onClick = onBack) {
            Text("Volver", fontSize = 16.sp, color = Color(0xFFFFC107))
        }
    }
}
