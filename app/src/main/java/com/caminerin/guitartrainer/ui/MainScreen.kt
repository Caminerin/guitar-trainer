package com.caminerin.guitartrainer.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Piano
import androidx.compose.material.icons.filled.Quiz
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector

private enum class NavDestination(val label: String, val icon: ImageVector) {
    SCALES("Escalas", Icons.Default.MusicNote),
    CHORDS("Acordes", Icons.Default.Piano),
    RIFFS("Riffs", Icons.Default.Audiotrack),
    QUIZ("Quiz", Icons.Default.Quiz),
    TUNER("Afinar", Icons.Default.Tune),
    METRONOME("Metro", Icons.Default.Speed)
}

enum class AppMode(val title: String, val icon: ImageVector) {
    VISUALIZER("Visualizador", Icons.Default.MusicNote),
    PRACTICE("Práctica", Icons.Default.Piano),
    QUIZ("Quiz", Icons.Default.Quiz),
    TOOLS("Herramientas", Icons.Default.Tune)
}

@Composable
fun MainScreen(
    pitchResult: PitchDetector.PitchResult?,
    isListening: Boolean
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        NoteFormatPreference.load(context)
        AccidentalPreference.load(context)
        AppPreferences.load(context)
        DegreeColorPrefs.load(context)
    }

    var selectedNav by rememberSaveable { mutableIntStateOf(AppPreferences.lastTab.coerceIn(0, NavDestination.entries.size - 1)) }
    var showSettings by remember { mutableStateOf(false) }
    val destinations = NavDestination.entries
    var previousNav by remember { mutableIntStateOf(selectedNav) }

    BackHandler {
        (context as? Activity)?.finish()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Content area
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = selectedNav,
                    transitionSpec = {
                        val direction = if (targetState > previousNav) 1 else -1
                        (slideInHorizontally { direction * it / 4 } + fadeIn())
                            .togetherWith(slideOutHorizontally { -direction * it / 4 } + fadeOut())
                    },
                    label = "nav_transition"
                ) { navIndex ->
                    val dest = destinations[navIndex]
                    when (dest) {
                        NavDestination.SCALES -> UnifiedScalesScreen(pitchResult = pitchResult)
                        NavDestination.CHORDS -> UnifiedChordsScreen()
                        NavDestination.RIFFS -> RiffPracticeScreen(
                            onBack = { selectedNav = 0 },
                            showBackButton = false
                        )
                        NavDestination.QUIZ -> ScaleQuizScreen(
                            onBack = { selectedNav = 0 },
                            pitchResult = pitchResult,
                            showBackButton = false
                        )
                        NavDestination.TUNER -> {
                            TunerMode(pitchResult = pitchResult)
                        }
                        NavDestination.METRONOME -> {
                            MetronomeMode()
                        }
                    }
                }
            }

            // Bottom Navigation Bar — custom compact row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .background(AppColors.navBar),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, dest ->
                    val isSelected = selectedNav == index
                    val itemColor = if (isSelected) AppColors.navSelected else AppColors.navUnselected
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                hapticTick(context)
                                previousNav = selectedNav
                                selectedNav = index
                                AppPreferences.saveTab(index, context)
                            }
                            .padding(vertical = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            dest.icon,
                            contentDescription = dest.label,
                            tint = itemColor,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            dest.label,
                            color = itemColor,
                            fontSize = 9.sp,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // Settings button — only on Tuner/Metronome (other screens have top-right UI)
        val showSettingsBtn = selectedNav in listOf(NavDestination.TUNER.ordinal, NavDestination.METRONOME.ordinal)
        if (showSettingsBtn) {
            IconButton(
                onClick = { showSettings = true },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 4.dp, top = 4.dp)
            ) {
                Icon(Icons.Default.Settings, "Ajustes", tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(18.dp))
            }
        }

        if (showSettings) {
            SettingsOverlay(context = context, onDismiss = { showSettings = false })
        }
    }
}

fun hapticTick(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            @Suppress("DEPRECATION")
            val v = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v?.vibrate(VibrationEffect.createOneShot(15, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        }
    } catch (_: Exception) { }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsOverlay(context: Context, onDismiss: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.overlay)
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(16.dp))
                .background(AppColors.surfaceVariant)
                .clickable(
                    indication = null,
                    interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                ) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Ajustes", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)

            // Nomenclature
            Text("Nomenclatura", color = AppColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NoteFormat.entries.forEach { fmt ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (NoteFormatPreference.current == fmt) AppColors.tertiary else Color.White.copy(alpha = 0.1f))
                            .clickable { NoteFormatPreference.set(fmt, context) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(fmt.label, color = AppColors.text, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            val colorPalette = listOf(
                Color(0xFFE53935), Color(0xFFD81B60), Color(0xFF8E24AA), Color(0xFF5E35B1),
                Color(0xFF3949AB), Color(0xFF1E88E5), Color(0xFF00ACC1), Color(0xFF00897B),
                Color(0xFF43A047), Color(0xFF7CB342), Color(0xFFFDD835), Color(0xFFFF8F00),
                Color(0xFFFF6D00), Color(0xFF6D4C41), Color(0xFF546E7A), Color(0xFF26A69A)
            )

            // Degree colors - Scale
            Text("Colores de grado (escalas)", color = AppColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val scaleDegs = listOf(
                "Tónica (I)" to ("tonic" to DegreeColorPrefs.tonicColor),
                "Tercera (III)" to ("third" to DegreeColorPrefs.thirdColor),
                "Quinta (V)" to ("fifth" to DegreeColorPrefs.fifthColor),
                "Otros" to ("other" to DegreeColorPrefs.otherColor)
            )
            scaleDegs.forEach { (label, keyColor) ->
                val (key, currentColor) = keyColor
                var expanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(currentColor)
                            .clickable { expanded = !expanded }
                    )
                    Text(label, color = AppColors.text, fontSize = 13.sp,
                        modifier = Modifier.clickable { expanded = !expanded })
                }
                if (expanded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)) {
                        colorPalette.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(c)
                                    .then(if (c == currentColor) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                                    .clickable { DegreeColorPrefs.setScaleColor(key, c, true, context); expanded = false }
                            )
                        }
                    }
                }
            }

            // Degree colors - Chords
            Text("Colores de intervalo (acordes)", color = AppColors.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            val chordDegs = listOf(
                "Raíz (1)" to ("root" to DegreeColorPrefs.chordRootColor),
                "Tercera (3)" to ("third" to DegreeColorPrefs.chordThirdColor),
                "Quinta (5)" to ("fifth" to DegreeColorPrefs.chordFifthColor),
                "Otros" to ("other" to DegreeColorPrefs.chordOtherColor)
            )
            chordDegs.forEach { (label, keyColor) ->
                val (key, currentColor) = keyColor
                var expanded by remember { mutableStateOf(false) }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(currentColor)
                            .clickable { expanded = !expanded }
                    )
                    Text(label, color = AppColors.text, fontSize = 13.sp,
                        modifier = Modifier.clickable { expanded = !expanded })
                }
                if (expanded) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.padding(start = 32.dp, top = 4.dp, bottom = 4.dp)) {
                        colorPalette.forEach { c ->
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(c)
                                    .then(if (c == currentColor) Modifier.border(2.dp, Color.White, RoundedCornerShape(4.dp)) else Modifier)
                                    .clickable { DegreeColorPrefs.setChordColor(key, c, true, context); expanded = false }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.tertiary)
                    .clickable { onDismiss() }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text("Cerrar", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ===== UNIFIED SCREENS WITH TOGGLE =====

@Composable
private fun ModeToggle(
    leftLabel: String,
    rightLabel: String,
    isLeftSelected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(AppColors.surfaceVariant)
            .padding(2.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isLeftSelected) AppColors.primary else Color.Transparent)
                .clickable { onToggle(true) }
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                leftLabel,
                color = if (isLeftSelected) AppColors.onPrimary else AppColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (!isLeftSelected) AppColors.primary else Color.Transparent)
                .clickable { onToggle(false) }
                .padding(horizontal = 14.dp, vertical = 5.dp)
        ) {
            Text(
                rightLabel,
                color = if (!isLeftSelected) AppColors.onPrimary else AppColors.textMuted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun UnifiedScalesScreen(pitchResult: PitchDetector.PitchResult?) {
    var isViewMode by rememberSaveable { mutableStateOf(true) }

    Box(modifier = Modifier.fillMaxSize()) {
        if (isViewMode) {
            ScaleFretboardScreen(onBack = {}, showBackButton = false)
        } else {
            CagedPracticeScreen(onBack = { isViewMode = true }, pitchResult = pitchResult)
        }

        // Toggle at bottom-left to avoid conflicting with screen controls at top
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 8.dp, bottom = 6.dp)
        ) {
            ModeToggle(
                leftLabel = "Ver",
                rightLabel = "Practicar",
                isLeftSelected = isViewMode,
                onToggle = { isViewMode = it }
            )
        }
    }
}

@Composable
private fun UnifiedChordsScreen() {
    var isViewMode by rememberSaveable { mutableStateOf(true) }

    // No overlay toggle needed — ChordPracticeScreen already has
    // internal [Digitaciones|Progresiones|Canciones|Visualizar] tabs
    if (isViewMode) {
        ChordVisualizerScreen(
            onBack = {},
            onGoToPractice = { isViewMode = false },
            showBackButton = false
        )
    } else {
        ChordPracticeScreen(
            onBack = { isViewMode = true },
            onGoToVisualizer = { isViewMode = true }
        )
    }
}

@Composable
fun ForceLandscape() {
    val context = LocalContext.current
    DisposableEffect(Unit) {
        val activity = context as? Activity
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}
