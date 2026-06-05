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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoStories
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.ads.AdManager
import com.caminerin.guitartrainer.audio.AudioProcessor
import com.caminerin.guitartrainer.audio.NoteEvent
import com.caminerin.guitartrainer.audio.NoteRecognizer
import com.caminerin.guitartrainer.audio.PitchDetector
import com.caminerin.guitartrainer.audio.ScaleEvaluation
import java.util.Calendar

// ===== NEW NAVIGATION: 3 TABS =====

private enum class NavDestination(val label: String, val icon: ImageVector) {
    LIBRARY("Biblioteca", Icons.Default.AutoStories),
    PRACTICE("Practicar", Icons.Default.FitnessCenter),
    TOOLS("Herramientas", Icons.Default.Build)
}

// ===== GRADIENT COLORS FOR CARDS (Warm Dark style) =====
object GradientColors {
    val scalesStart = Color(0xFF1A2E10)
    val scalesEnd = Color(0xFF3E7B1E)
    val chordsStart = Color(0xFF1A1430)
    val chordsEnd = Color(0xFF4527A0)
    val tabsStart = Color(0xFF2E1020)
    val tabsEnd = Color(0xFF880E4F)
    val quizStart = Color(0xFF2E2000)
    val quizEnd = Color(0xFFE6A000)
    val retoStart = Color(0xFF2E0A00)
    val retoEnd = Color(0xFFBF360C)
    val tunerStart = Color(0xFF0A201A)
    val tunerEnd = Color(0xFF00695C)
    val metroStart = Color(0xFF180A00)
    val metroEnd = Color(0xFFBF360C)
    val grooveStart = Color(0xFF1A0A2E)
    val grooveEnd = Color(0xFF6A1B9A)
    val settingsStart = Color(0xFF1A1714)
    val settingsEnd = Color(0xFF4E4238)

    val backgroundGradient = Brush.linearGradient(
        colors = listOf(Color(0xFF0F0D0A), Color(0xFF12100C))
    )
    val titleGradient = Brush.horizontalGradient(
        colors = listOf(Color(0xFFD4960A), Color(0xFFE6A000))
    )
    val accent = Color(0xFFD4960A)
}

@Composable
fun MainScreen(
    pitchResult: PitchDetector.PitchResult?,
    isListening: Boolean,
    noteEvent: NoteEvent? = null,
    noteRecognizer: NoteRecognizer? = null,
    audioProcessor: AudioProcessor? = null,
    scaleEvaluation: ScaleEvaluation? = null
) {
    val context = LocalContext.current
    val activity = context as? Activity
    LaunchedEffect(Unit) {
        NoteFormatPreference.load(context)
        AccidentalPreference.load(context)
        AppPreferences.load(context)
        DegreeColorPrefs.load(context)
    }

    // ----- Estado de anuncios -----
    var adFree by remember { mutableStateOf(AdManager.isAdFree()) }
    var bannerDismissed by rememberSaveable { mutableStateOf(false) }
    var rewardProgress by remember { mutableIntStateOf(AdManager.rewardCount) }

    // Callback para que AdManager avise cuando cambia el estado ad-free
    DisposableEffect(Unit) {
        AdManager.onAdFreeChanged = {
            adFree = AdManager.isAdFree()
            rewardProgress = AdManager.rewardCount
        }
        onDispose { AdManager.onAdFreeChanged = null }
    }

    // Check if tuner splash should show (first time today)
    var showTunerSplash by rememberSaveable { mutableStateOf(shouldShowTunerToday(context)) }

    var selectedNav by rememberSaveable { mutableIntStateOf(AppPreferences.lastTab.coerceIn(0, NavDestination.entries.size - 1)) }
    var previousNav by remember { mutableIntStateOf(selectedNav) }

    // Sub-navigation state
    var practiceSubScreen by rememberSaveable { mutableIntStateOf(-1) } // -1 = hub
    var librarySubScreen by rememberSaveable { mutableIntStateOf(-1) }
    var toolsSubScreen by rememberSaveable { mutableIntStateOf(-1) }
    var chordsSubScreen by rememberSaveable { mutableIntStateOf(-1) } // -1=selection, 0=progresiones, 1=reto

    // Intersticial: contar salidas de ejercicio al hub
    var prevPractice by remember { mutableIntStateOf(practiceSubScreen) }
    var prevLibrary by remember { mutableIntStateOf(librarySubScreen) }
    var prevTools by remember { mutableIntStateOf(toolsSubScreen) }
    LaunchedEffect(practiceSubScreen) {
        if (prevPractice >= 0 && practiceSubScreen < 0) activity?.let { AdManager.onExerciseCompleted(it) }
        prevPractice = practiceSubScreen
    }
    LaunchedEffect(librarySubScreen) {
        if (prevLibrary >= 0 && librarySubScreen < 0) activity?.let { AdManager.onExerciseCompleted(it) }
        prevLibrary = librarySubScreen
    }
    LaunchedEffect(toolsSubScreen) {
        if (prevTools >= 0 && toolsSubScreen < 0) activity?.let { AdManager.onExerciseCompleted(it) }
        prevTools = toolsSubScreen
    }

    val destinations = NavDestination.entries

    BackHandler {
        when {
            showTunerSplash -> (context as? Activity)?.finish()
            chordsSubScreen >= 0 -> chordsSubScreen = -1
            practiceSubScreen >= 0 -> practiceSubScreen = -1
            librarySubScreen >= 0 -> librarySubScreen = -1
            toolsSubScreen >= 0 -> toolsSubScreen = -1
            else -> (context as? Activity)?.finish()
        }
    }

    if (showTunerSplash) {
        TunerSplashScreen(
            pitchResult = pitchResult,
            onSkip = {
                markTunerShownToday(context)
                showTunerSplash = false
            }
        )
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientColors.backgroundGradient)
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
                        NavDestination.LIBRARY -> LibrarySection(
                            subScreen = librarySubScreen,
                            onSubScreenChange = { librarySubScreen = it },
                            pitchResult = pitchResult,
                            noteEvent = noteEvent,
                            noteRecognizer = noteRecognizer
                        )
                        NavDestination.PRACTICE -> PracticeSection(
                            subScreen = practiceSubScreen,
                            onSubScreenChange = { practiceSubScreen = it },
                            chordsSubScreen = chordsSubScreen,
                            onChordsSubScreenChange = { chordsSubScreen = it },
                            pitchResult = pitchResult,
                            noteEvent = noteEvent,
                            noteRecognizer = noteRecognizer,
                            audioProcessor = audioProcessor,
                            scaleEvaluation = scaleEvaluation
                        )
                        NavDestination.TOOLS -> ToolsSection(
                            subScreen = toolsSubScreen,
                            onSubScreenChange = { toolsSubScreen = it },
                            pitchResult = pitchResult
                        )
                    }
                }
            }

            // ----- Zona de anuncios: solo en hubs, solo si no es ad-free -----
            val atHub = librarySubScreen < 0 && practiceSubScreen < 0 &&
                    chordsSubScreen < 0 && toolsSubScreen < 0
            if (atHub && !adFree) {
                // Chip recompensado: "Ver anuncio (X/3 para 24h sin anuncios)"
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0908))
                        .clickable {
                            activity?.let { act ->
                                AdManager.showRewarded(act) {
                                    adFree = AdManager.isAdFree()
                                    rewardProgress = AdManager.rewardCount
                                }
                            }
                        }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        Icons.Default.CardGiftcard,
                        contentDescription = null,
                        tint = GradientColors.accent,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        "Sin anuncios 24h \u2192 ver ${rewardProgress}/3 anuncios",
                        fontSize = 11.sp,
                        color = GradientColors.accent
                    )
                }

                // Banner cerrable con X
                if (!bannerDismissed) {
                    DismissibleAdBanner(
                        onDismiss = { bannerDismissed = true },
                        modifier = Modifier.background(Color(0xFF0A0908))
                    )
                }
            } else if (atHub && adFree) {
                // Indicador de tiempo restante sin anuncios
                val remainMs = AdManager.adFreeRemainingMs()
                val h = remainMs / 3_600_000
                val m = (remainMs % 3_600_000) / 60_000
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0A0908))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        "\uD83C\uDF81 Sin anuncios: ${h}h ${m}m restantes",
                        fontSize = 11.sp,
                        color = Color(0xFF4CAF50)
                    )
                }
            }

            // Bottom Navigation Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color(0xFF0A0908).copy(alpha = 0.95f)),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                destinations.forEachIndexed { index, dest ->
                    val isSelected = selectedNav == index
                    val itemColor = if (isSelected) GradientColors.accent else Color(0xFF5A5040)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                hapticTick(context)
                                if (selectedNav == index) {
                                    // Tap on current tab → go back to hub
                                    when (index) {
                                        NavDestination.LIBRARY.ordinal -> librarySubScreen = -1
                                        NavDestination.PRACTICE.ordinal -> { practiceSubScreen = -1; chordsSubScreen = -1 }
                                        NavDestination.TOOLS.ordinal -> toolsSubScreen = -1
                                    }
                                } else {
                                    previousNav = selectedNav
                                    selectedNav = index
                                    AppPreferences.saveTab(index, context)
                                    // Reset sub-screens when switching tabs
                                    if (index != NavDestination.PRACTICE.ordinal) practiceSubScreen = -1
                                    if (index != NavDestination.LIBRARY.ordinal) librarySubScreen = -1
                                    if (index != NavDestination.TOOLS.ordinal) toolsSubScreen = -1
                                    chordsSubScreen = -1
                                }
                            }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            dest.icon,
                            contentDescription = dest.label,
                            tint = itemColor,
                            modifier = Modifier.size(22.dp)
                        )
                        Text(
                            dest.label,
                            color = itemColor,
                            fontSize = 10.sp,
                            maxLines = 1,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }
    }
}

// ===== TUNER SPLASH (1st time of day) =====

private fun shouldShowTunerToday(context: Context): Boolean {
    val prefs = context.getSharedPreferences("guitar_prefs", Context.MODE_PRIVATE)
    val lastShown = prefs.getLong("tuner_splash_last_day", 0L)
    val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) +
            Calendar.getInstance().get(Calendar.YEAR) * 1000
    return lastShown.toInt() != today
}

private fun markTunerShownToday(context: Context) {
    val today = Calendar.getInstance().get(Calendar.DAY_OF_YEAR) +
            Calendar.getInstance().get(Calendar.YEAR) * 1000
    context.getSharedPreferences("guitar_prefs", Context.MODE_PRIVATE)
        .edit().putLong("tuner_splash_last_day", today.toLong()).apply()
}

@Composable
private fun TunerSplashScreen(
    pitchResult: PitchDetector.PitchResult?,
    onSkip: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(GradientColors.backgroundGradient)
    ) {
        // Skip button - top right, always visible
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 12.dp, end = 16.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(GradientColors.accent)
                .clickable { onSkip() }
                .padding(horizontal = 20.dp, vertical = 8.dp)
        ) {
            Text(
                "Continuar →",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Title - top center
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Guitar Trainer",
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = GradientColors.accent
            )
            Text(
                "Afina tu guitarra",
                fontSize = 11.sp,
                color = Color(0xFF8B7D6B)
            )
        }

        // Tuner takes remaining space
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 40.dp)
        ) {
            TunerMode(pitchResult = pitchResult)
        }
    }
}

// ===== LIBRARY SECTION =====

@Composable
private fun LibrarySection(
    subScreen: Int,
    onSubScreenChange: (Int) -> Unit,
    pitchResult: PitchDetector.PitchResult?,
    noteEvent: NoteEvent?,
    noteRecognizer: NoteRecognizer?
) {
    when (subScreen) {
        -1 -> LibraryHub(onItemClick = { onSubScreenChange(it) })
        0 -> ScaleFretboardScreen(onBack = { onSubScreenChange(-1) }, showBackButton = true)
        1 -> ChordVisualizerScreen(
            onBack = { onSubScreenChange(-1) },
            onGoToPractice = {},
            showBackButton = true
        )
    }
}

@Composable
private fun LibraryHub(onItemClick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Text(
            "Biblioteca",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = GradientColors.accent
        )
        Text(
            "Consulta y aprende",
            fontSize = 11.sp,
            color = Color(0xFF8B7D6B)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().height(120.dp)
        ) {
            GridCard(
                emoji = "🎸",
                label = "Escalas",
                subtitle = "Mástil + posiciones",
                gradientStart = GradientColors.scalesStart,
                gradientEnd = GradientColors.scalesEnd,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onItemClick(0) }
            )
            GridCard(
                emoji = "🎹",
                label = "Acordes",
                subtitle = "Diagramas + formas",
                gradientStart = GradientColors.chordsStart,
                gradientEnd = GradientColors.chordsEnd,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                onClick = { onItemClick(1) }
            )
        }
    }
}

// ===== PRACTICE SECTION =====

@Composable
private fun PracticeSection(
    subScreen: Int,
    onSubScreenChange: (Int) -> Unit,
    chordsSubScreen: Int,
    onChordsSubScreenChange: (Int) -> Unit,
    pitchResult: PitchDetector.PitchResult?,
    noteEvent: NoteEvent?,
    noteRecognizer: NoteRecognizer?,
    audioProcessor: AudioProcessor? = null,
    scaleEvaluation: ScaleEvaluation? = null
) {
    when (subScreen) {
        -1 -> PracticeHub(onItemClick = { onSubScreenChange(it) })
        0 -> CagedPracticeScreen(
            onBack = { onSubScreenChange(-1) },
            pitchResult = pitchResult,
            noteEvent = noteEvent,
            noteRecognizer = noteRecognizer,
            audioProcessor = audioProcessor,
            scaleEvaluation = scaleEvaluation
        )
        1 -> ChordsSubSection(
            chordsSubScreen = chordsSubScreen,
            onChordsSubScreenChange = onChordsSubScreenChange,
            onBack = { onSubScreenChange(-1) }
        )
        2 -> TabPracticeScreen(
            onBack = { onSubScreenChange(-1) },
            showBackButton = true
        )
        3 -> QuizHubScreen(
            onBack = { onSubScreenChange(-1) },
            pitchResult = pitchResult,
            showBackButton = true
        )
        4 -> GrooveTrainerScreen(
            onBack = { onSubScreenChange(-1) }
        )
    }
}

@Composable
private fun PracticeHub(onItemClick: (Int) -> Unit) {
    // Rejilla 3x2 que se ajusta al alto disponible: TODOS los botones caben sin scroll.
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Text(
            "Practicar",
            fontSize = 18.sp,
            fontWeight = FontWeight.ExtraBold,
            color = GradientColors.accent
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.fillMaxWidth().weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                GridCard(
                    emoji = "🎸",
                    label = "Escalas",
                    subtitle = "Con guitarra real",
                    gradientStart = GradientColors.scalesStart,
                    gradientEnd = GradientColors.scalesEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(0) }
                )
                GridCard(
                    emoji = "🎹",
                    label = "Acordes",
                    subtitle = "Progresiones + Reto",
                    gradientStart = GradientColors.chordsStart,
                    gradientEnd = GradientColors.chordsEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(1) }
                )
                GridCard(
                    emoji = "🎵",
                    label = "Tabs",
                    subtitle = "Loop + BPM",
                    gradientStart = GradientColors.tabsStart,
                    gradientEnd = GradientColors.tabsEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(2) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                GridCard(
                    emoji = "🧠",
                    label = "Quiz",
                    subtitle = "Oído + Teoría",
                    gradientStart = GradientColors.quizStart,
                    gradientEnd = GradientColors.quizEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(3) }
                )
                GridCard(
                    emoji = "🥁",
                    label = "Caja de Ritmos",
                    subtitle = "Groove Trainer",
                    gradientStart = GradientColors.grooveStart,
                    gradientEnd = GradientColors.grooveEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(4) }
                )
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ===== CHORDS SUB-SECTION =====

@Composable
private fun ChordsSubSection(
    chordsSubScreen: Int,
    onChordsSubScreenChange: (Int) -> Unit,
    onBack: () -> Unit
) {
    when (chordsSubScreen) {
        -1 -> ChordsSelectionScreen(
            onSelect = { onChordsSubScreenChange(it) },
            onBack = onBack
        )
        0 -> ChordPracticeScreen(
            onBack = { onChordsSubScreenChange(-1) },
            onGoToVisualizer = {}
        )
        1 -> ChordChallengeScreen(
            onBack = { onChordsSubScreenChange(-1) }
        )
    }
}

@Composable
private fun ChordsSelectionScreen(
    onSelect: (Int) -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 16.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = GradientColors.accent,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Acordes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            SubOptionCard(
                emoji = "🎹",
                title = "Progresiones",
                subtitle = "Cambios con metrónomo y batería",
                gradientStart = GradientColors.chordsStart,
                gradientEnd = GradientColors.chordsEnd,
                onClick = { onSelect(0) }
            )
            SubOptionCard(
                emoji = "⚡",
                title = "Reto de velocidad",
                subtitle = "Elige entre 2 y 4 acordes y descubre cuántos cambios aguantas",
                gradientStart = GradientColors.retoStart,
                gradientEnd = GradientColors.retoEnd,
                onClick = { onSelect(1) }
            )
        }
    }
}

// ===== TOOLS SECTION =====

@Composable
private fun ToolsSection(
    subScreen: Int,
    onSubScreenChange: (Int) -> Unit,
    pitchResult: PitchDetector.PitchResult?
) {
    when (subScreen) {
        -1 -> ToolsHub(onItemClick = { onSubScreenChange(it) })
        0 -> ToolScreenWrapper(title = "Afinador", onBack = { onSubScreenChange(-1) }) {
            TunerMode(pitchResult = pitchResult)
        }
        1 -> ToolScreenWrapper(title = "Metrónomo", onBack = { onSubScreenChange(-1) }) {
            MetronomeMode()
        }
        2 -> SettingsScreen(onBack = { onSubScreenChange(-1) })
    }
}

@Composable
private fun ToolScreenWrapper(title: String, onBack: () -> Unit, content: @Composable () -> Unit) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = GradientColors.accent,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                title,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        Box(modifier = Modifier.weight(1f)) {
            content()
        }
    }
}

@Composable
private fun ToolsHub(onItemClick: (Int) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(
            "Herramientas",
            fontSize = 22.sp,
            fontWeight = FontWeight.ExtraBold,
            color = GradientColors.accent
        )
        Text(
            "Utilidades",
            fontSize = 11.sp,
            color = Color(0xFF8B7D6B)
        )
        Spacer(modifier = Modifier.height(10.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                GridCard(
                    emoji = "🎯",
                    label = "Afinador",
                    subtitle = "Afina tu guitarra",
                    gradientStart = GradientColors.tunerStart,
                    gradientEnd = GradientColors.tunerEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(0) }
                )
                GridCard(
                    emoji = "🥁",
                    label = "Metrónomo",
                    subtitle = "Tempo + batería",
                    gradientStart = GradientColors.metroStart,
                    gradientEnd = GradientColors.metroEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(1) }
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                GridCard(
                    emoji = "⚙️",
                    label = "Ajustes",
                    subtitle = "Colores, notación",
                    gradientStart = GradientColors.settingsStart,
                    gradientEnd = GradientColors.settingsEnd,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    onClick = { onItemClick(2) }
                )
                // Empty spacer for alignment
                Spacer(modifier = Modifier.weight(1f))
            }
        }
    }
}

// ===== SETTINGS SCREEN (extracted from old SettingsOverlay) =====

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(bottom = 8.dp)
        ) {
            Icon(
                Icons.Default.ArrowBack,
                contentDescription = "Volver",
                tint = GradientColors.accent,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onBack() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "Ajustes",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }

        // Nomenclature
        Text("Nomenclatura", color = Color(0xFF8B7D6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            NoteFormat.entries.forEach { fmt ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (NoteFormatPreference.current == fmt) GradientColors.accent else Color.White.copy(alpha = 0.1f))
                        .clickable { NoteFormatPreference.set(fmt, context) }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(fmt.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
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
        Text("Colores de grado (escalas)", color = Color(0xFF8B7D6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text(label, color = Color.White, fontSize = 13.sp,
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
        Text("Colores de intervalo (acordes)", color = Color(0xFF8B7D6B), fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
                Text(label, color = Color.White, fontSize = 13.sp,
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
    }
}

// ===== SHARED GRID CARD COMPONENT =====

@Composable
private fun GridCard(
    emoji: String,
    label: String,
    subtitle: String,
    gradientStart: Color,
    gradientEnd: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxHeight()
        ) {
            Text(emoji, fontSize = 22.sp)
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                label,
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
            Text(
                subtitle,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1
            )
        }
    }
}

// ===== SUB-OPTION CARD (for Acordes selection) =====

@Composable
private fun SubOptionCard(
    emoji: String,
    title: String,
    subtitle: String,
    gradientStart: Color,
    gradientEnd: Color,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(gradientStart, gradientEnd)))
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, fontSize = 26.sp)
        Column {
            Text(title, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(alpha = 0.7f), fontSize = 11.sp)
        }
    }
}

// ===== UTILITIES =====

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
