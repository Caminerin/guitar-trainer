package com.caminerin.guitartrainer.ui

import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.DisposableEffect
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.caminerin.guitartrainer.audio.DrumEngine
import com.caminerin.guitartrainer.audio.DrumStyle
import com.caminerin.guitartrainer.audio.MetronomeConfig
import com.caminerin.guitartrainer.audio.MetronomeEngine
import com.caminerin.guitartrainer.audio.MetronomeSound
import com.caminerin.guitartrainer.audio.TrainingDirection
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState as rememberHScrollState
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope


// Tempo presets with musical terms
private data class TempoPreset(val name: String, val bpmMin: Int, val bpmMax: Int) {
    val midBpm: Int get() = (bpmMin + bpmMax) / 2
}

private val TEMPO_PRESETS = listOf(
    TempoPreset("Grave", 20, 39),
    TempoPreset("Largo", 40, 60),
    TempoPreset("Larghetto", 61, 65),
    TempoPreset("Adagio", 66, 76),
    TempoPreset("Andante", 77, 107),
    TempoPreset("Moderato", 108, 119),
    TempoPreset("Allegro", 120, 155),
    TempoPreset("Vivace", 156, 175),
    TempoPreset("Presto", 176, 200),
    TempoPreset("Prestissimo", 201, 300)
)

// Time signatures
private data class TimeSignature(val beats: Int, val unit: Int, val label: String)

private val TIME_SIGNATURES = listOf(
    TimeSignature(2, 4, "2/4"),
    TimeSignature(3, 4, "3/4"),
    TimeSignature(4, 4, "4/4"),
    TimeSignature(5, 4, "5/4"),
    TimeSignature(6, 8, "6/8"),
    TimeSignature(7, 8, "7/8"),
    TimeSignature(9, 8, "9/8"),
    TimeSignature(12, 8, "12/8")
)

private fun getTempoName(bpm: Int): String {
    return TEMPO_PRESETS.firstOrNull { bpm in it.bpmMin..it.bpmMax }?.name ?: ""
}

@Composable
fun MetronomeMode(
    modifier: Modifier = Modifier
) {
    val engine = remember { MetronomeEngine() }
    val isPlaying by engine.isPlaying.collectAsState()
    val currentBeat by engine.currentBeat.collectAsState()
    val currentMeasure by engine.currentMeasure.collectAsState()
    val currentBpm by engine.currentBpm.collectAsState()
    val elapsedSeconds by engine.elapsedSeconds.collectAsState()
    val isCountingIn by engine.isCountingIn.collectAsState()
    val isMutedBar by engine.isMutedBar.collectAsState()

    var bpm by remember { mutableIntStateOf(120) }
    var bpmSlider by remember { mutableFloatStateOf(120f) }
    var beatsPerMeasure by remember { mutableIntStateOf(4) }
    var beatUnit by remember { mutableIntStateOf(4) }
    var subdivision by remember { mutableIntStateOf(1) }
    var sound by remember { mutableStateOf(MetronomeSound.CLICK) }

    var timeSigMenuExpanded by remember { mutableStateOf(false) }
    var subdivisionMenuExpanded by remember { mutableStateOf(false) }
    var soundMenuExpanded by remember { mutableStateOf(false) }
    var tempoPresetMenuExpanded by remember { mutableStateOf(false) }

    // Accent pattern: true = accented
    var accentPattern by remember { mutableStateOf(List(4) { it == 0 }) }

    // Swing
    var swingPercent by remember { mutableIntStateOf(50) }

    // Count-in (on/off toggle, always 1 bar)
    var countInEnabled by remember { mutableStateOf(false) }

    // Mute bars
    var muteEnabled by remember { mutableStateOf(false) }
    var muteBarsPlay by remember { mutableIntStateOf(4) }
    var muteBarsSilent by remember { mutableIntStateOf(4) }

    // Training mode
    var trainingEnabled by remember { mutableStateOf(false) }
    var trainingInterval by remember { mutableIntStateOf(4) }
    var trainingBpmChange by remember { mutableIntStateOf(5) }
    var trainingMaxBpm by remember { mutableIntStateOf(200) }
    var trainingDirection by remember { mutableStateOf(TrainingDirection.UP) }

    // Timer
    var timerEnabled by remember { mutableStateOf(false) }
    var timerMode by remember { mutableStateOf("measures") }
    var timerMeasures by remember { mutableIntStateOf(16) }
    var timerSeconds by remember { mutableIntStateOf(60) }

    // Haptic
    var hapticEnabled by remember { mutableStateOf(false) }

    // Drum backing track
    var selectedDrumStyle by remember { mutableStateOf<DrumStyle?>(null) }
    var drumJob by remember { mutableStateOf<Job?>(null) }
    val drumScope = rememberCoroutineScope()

    // Tap tempo state
    var lastTapTime by remember { mutableLongStateOf(0L) }
    var tapCount by remember { mutableIntStateOf(0) }
    var tapSum by remember { mutableLongStateOf(0L) }

    // Play trigger
    var playTrigger by remember { mutableIntStateOf(0) }

    // Keep accent pattern in sync with beats per measure
    LaunchedEffect(beatsPerMeasure) {
        if (accentPattern.size != beatsPerMeasure) {
            accentPattern = List(beatsPerMeasure) { it == 0 }
        }
    }

    LaunchedEffect(playTrigger) {
        if (playTrigger > 0) {
            engine.start(
                MetronomeConfig(
                    bpm = bpm,
                    beatsPerMeasure = beatsPerMeasure,
                    beatUnit = beatUnit,
                    subdivision = subdivision,
                    sound = sound,
                    accentPattern = accentPattern,
                    swingPercent = swingPercent,
                    countInBars = if (countInEnabled) 1 else 0,
                    muteEnabled = muteEnabled,
                    muteBarsPlay = muteBarsPlay,
                    muteBarsSilent = muteBarsSilent,
                    trainingEnabled = trainingEnabled,
                    trainingIntervalBeats = trainingInterval,
                    trainingBpmChange = trainingBpmChange,
                    trainingMaxBpm = trainingMaxBpm,
                    trainingDirection = trainingDirection,
                    timerEnabled = timerEnabled,
                    timerMeasures = if (timerMode == "measures") timerMeasures else 0,
                    timerSeconds = if (timerMode == "time") timerSeconds else 0
                )
            )
        }
    }

    // Real-time config updates
    LaunchedEffect(bpm) { engine.liveBpm = bpm }
    LaunchedEffect(subdivision) { engine.liveSubdivision = subdivision }
    LaunchedEffect(beatsPerMeasure) { engine.liveBeatsPerMeasure = beatsPerMeasure }
    LaunchedEffect(sound) { engine.liveSound = sound }
    LaunchedEffect(accentPattern) { engine.liveAccentPattern = accentPattern }
    LaunchedEffect(swingPercent) {
        engine.liveSwingPercent = swingPercent
        if (swingPercent > 50 && subdivision < 2) {
            subdivision = 2
        }
    }

    // Drum engine: start/stop with metronome
    val context = LocalContext.current
    LaunchedEffect(isPlaying, selectedDrumStyle) {
        drumJob?.cancel()
        DrumEngine.stop()
        if (isPlaying && selectedDrumStyle != null) {
            drumJob = drumScope.launch {
                DrumEngine.playLoop(
                    context = context,
                    style = selectedDrumStyle!!,
                    bpm = bpm,
                    beatsPerMeasure = beatsPerMeasure
                )
            }
        }
    }
    DisposableEffect(Unit) {
        onDispose { drumJob?.cancel(); DrumEngine.release() }
    }

    // Haptic feedback: trigger from engine's audio thread for sync
    DisposableEffect(hapticEnabled) {
        engine.onBeatCallback = if (hapticEnabled) {
            { triggerHaptic(context) }
        } else null
        onDispose { engine.onBeatCallback = null }
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Play + BPM + slider
            Column(
                modifier = Modifier
                    .weight(0.45f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BeatVisualizerEnhanced(
                    beatsPerMeasure = beatsPerMeasure,
                    currentBeat = if (isPlaying) currentBeat else -1,
                    accentPattern = accentPattern,
                    onToggleAccent = { idx ->
                        accentPattern = accentPattern.toMutableList().also { it[idx] = !it[idx] }
                    },
                    isCountingIn = isCountingIn,
                    isMutedBar = isMutedBar
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Status badges
                if (isPlaying) {
                    StatusBadges(isCountingIn, isMutedBar, swingPercent)
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    // Tap Tempo button
                    TapTempoButton(
                        size = 36,
                        onTap = {
                            val now = System.currentTimeMillis()
                            if (now - lastTapTime < 2000 && lastTapTime > 0) {
                                val interval = now - lastTapTime
                                tapSum += interval
                                tapCount++
                                val avgInterval = tapSum / tapCount
                                val newBpm = (60000.0 / avgInterval).toInt().coerceIn(20, 300)
                                bpm = newBpm
                                bpmSlider = newBpm.toFloat()
                            } else {
                                tapCount = 0
                                tapSum = 0
                            }
                            lastTapTime = now
                        }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    FilledIconButton(
                        onClick = { if (isPlaying) engine.stop() else playTrigger++ },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPlaying) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$bpm", fontSize = 32.sp, fontWeight = FontWeight.Black, color = Color.White)
                        val tempoName = getTempoName(bpm)
                        if (tempoName.isNotEmpty()) {
                            Text(tempoName, fontSize = 9.sp, color = Color(0xFFFFC107))
                        }
                        Text("BPM", fontSize = 10.sp, color = Color.White.copy(alpha = 0.5f))
                    }
                }
                Slider(
                    value = bpmSlider,
                    onValueChange = { bpmSlider = it; bpm = it.toInt() },
                    valueRange = 20f..300f,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                )
                Row(horizontalArrangement = Arrangement.Center) {
                    PillButton("-5") { bpm = (bpm - 5).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
                    Spacer(modifier = Modifier.width(3.dp))
                    PillButton("-1") { bpm = (bpm - 1).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
                    Spacer(modifier = Modifier.width(8.dp))
                    PillButton("+1") { bpm = (bpm + 1).coerceAtMost(300); bpmSlider = bpm.toFloat() }
                    Spacer(modifier = Modifier.width(3.dp))
                    PillButton("+5") { bpm = (bpm + 5).coerceAtMost(300); bpmSlider = bpm.toFloat() }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: settings + cards (scrollable)
            Column(
                modifier = Modifier
                    .weight(0.55f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MetronomeSettingsRow(
                    beatsPerMeasure = beatsPerMeasure,
                    beatUnit = beatUnit,
                    timeSigMenuExpanded = timeSigMenuExpanded,
                    onTimeSigMenuToggle = { timeSigMenuExpanded = it },
                    onTimeSigSelected = { ts ->
                        beatsPerMeasure = ts.beats; beatUnit = ts.unit; timeSigMenuExpanded = false
                    },
                    subdivision = subdivision,
                    subdivisionMenuExpanded = subdivisionMenuExpanded,
                    onSubdivisionMenuToggle = { subdivisionMenuExpanded = it },
                    onSubdivisionSelected = { subdivision = it; subdivisionMenuExpanded = false },
                    sound = sound,
                    soundMenuExpanded = soundMenuExpanded,
                    onSoundMenuToggle = { soundMenuExpanded = it },
                    onSoundSelected = { sound = it; soundMenuExpanded = false }
                )
                Spacer(modifier = Modifier.height(4.dp))

                // Quick settings row: count-in, swing, haptic, tempo presets
                QuickSettingsRow(
                    countInEnabled = countInEnabled,
                    onCountInToggle = { countInEnabled = !countInEnabled },
                    swingPercent = swingPercent,
                    onSwingChange = { swingPercent = it },
                    hapticEnabled = hapticEnabled,
                    onHapticToggle = { hapticEnabled = !hapticEnabled },
                    tempoPresetMenuExpanded = tempoPresetMenuExpanded,
                    onTempoPresetMenuToggle = { tempoPresetMenuExpanded = it },
                    onTempoPresetSelected = { preset ->
                        bpm = preset.midBpm; bpmSlider = bpm.toFloat()
                        tempoPresetMenuExpanded = false
                    }
                )

                Spacer(modifier = Modifier.height(4.dp))
                AllCards(
                    isPlaying, currentBpm, bpm, currentMeasure, elapsedSeconds,
                    muteEnabled, { muteEnabled = !muteEnabled },
                    muteBarsPlay, { muteBarsPlay = it },
                    muteBarsSilent, { muteBarsSilent = it },
                    trainingEnabled, { trainingEnabled = !trainingEnabled },
                    trainingInterval, { trainingInterval = it },
                    trainingBpmChange, { trainingBpmChange = it },
                    trainingMaxBpm, { trainingMaxBpm = it },
                    trainingDirection, { trainingDirection = it },
                    timerEnabled, { timerEnabled = !timerEnabled },
                    timerMode, { timerMode = it },
                    timerMeasures, { timerMeasures = it },
                    timerSeconds, { timerSeconds = it }
                )
            }
        }
    } else {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BeatVisualizerEnhanced(
                beatsPerMeasure = beatsPerMeasure,
                currentBeat = if (isPlaying) currentBeat else -1,
                accentPattern = accentPattern,
                onToggleAccent = { idx ->
                    accentPattern = accentPattern.toMutableList().also { it[idx] = !it[idx] }
                },
                isCountingIn = isCountingIn,
                isMutedBar = isMutedBar
            )

            // Status badges
            if (isPlaying) {
                Spacer(modifier = Modifier.height(4.dp))
                StatusBadges(isCountingIn, isMutedBar, swingPercent)
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                // Tap Tempo
                TapTempoButton(
                    size = 52,
                    onTap = {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 2000 && lastTapTime > 0) {
                            val interval = now - lastTapTime
                            tapSum += interval
                            tapCount++
                            val avgInterval = tapSum / tapCount
                            val newBpm = (60000.0 / avgInterval).toInt().coerceIn(20, 300)
                            bpm = newBpm
                            bpmSlider = newBpm.toFloat()
                        } else {
                            tapCount = 0
                            tapSum = 0
                        }
                        lastTapTime = now
                    }
                )

                Spacer(modifier = Modifier.width(12.dp))

                FilledIconButton(
                    onClick = { if (isPlaying) engine.stop() else playTrigger++ },
                    modifier = Modifier.size(68.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = if (isPlaying) Color(0xFFF44336) else Color(0xFF4CAF50)
                    )
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = null,
                        modifier = Modifier.size(36.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(24.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$bpm",
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Black,
                        color = Color.White
                    )
                    val tempoName = getTempoName(bpm)
                    if (tempoName.isNotEmpty()) {
                        Text(tempoName, fontSize = 12.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold)
                    }
                    Text("BPM", fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.Center) {
                PillButton("-5") { bpm = (bpm - 5).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
                Spacer(modifier = Modifier.width(6.dp))
                PillButton("-1") { bpm = (bpm - 1).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
                Spacer(modifier = Modifier.width(16.dp))
                PillButton("+1") { bpm = (bpm + 1).coerceAtMost(300); bpmSlider = bpm.toFloat() }
                Spacer(modifier = Modifier.width(6.dp))
                PillButton("+5") { bpm = (bpm + 5).coerceAtMost(300); bpmSlider = bpm.toFloat() }
            }

            Slider(
                value = bpmSlider,
                onValueChange = { bpmSlider = it; bpm = it.toInt() },
                valueRange = 20f..300f,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
            )

            // Tempo preset button (opens overlay)
            OutlinedButton(onClick = { tempoPresetMenuExpanded = true }) {
                val tempoName = getTempoName(bpm)
                Text(
                    if (tempoName.isNotEmpty()) "$tempoName \u25BC" else "Tempo \u25BC",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFC107)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Time sig + Subdivision + Sound buttons (open overlays)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                OutlinedButton(onClick = { timeSigMenuExpanded = true }) {
                    Text("$beatsPerMeasure/$beatUnit", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { subdivisionMenuExpanded = true }) {
                    val subLabel = when (subdivision) {
                        1 -> "\u2669"; 2 -> "\u266a\u266a"; 3 -> "\u266a\u266a\u266a"; 4 -> "\u266c"; else -> "$subdivision"
                    }
                    Text(subLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                OutlinedButton(onClick = { soundMenuExpanded = true }) {
                    Text("\u266a ${sound.displayName}", fontSize = 12.sp)
                }
            }

            // Overlay dialogs
            if (timeSigMenuExpanded) {
                MetronomeOverlaySelector(title = "Compás", onDismiss = { timeSigMenuExpanded = false }) {
                    TIME_SIGNATURES.forEach { ts ->
                        MetronomeOverlayItem(
                            text = ts.label,
                            isSelected = beatsPerMeasure == ts.beats && beatUnit == ts.unit,
                            onClick = { beatsPerMeasure = ts.beats; beatUnit = ts.unit; timeSigMenuExpanded = false }
                        )
                    }
                }
            }
            if (subdivisionMenuExpanded) {
                MetronomeOverlaySelector(title = "Subdivisión", onDismiss = { subdivisionMenuExpanded = false }) {
                    listOf(1 to "\u2669 Negras", 2 to "\u266a\u266a Corcheas", 3 to "\u266a\u266a\u266a Tresillos", 4 to "\u266c Semicorcheas").forEach { (sub, label) ->
                        MetronomeOverlayItem(text = label, isSelected = subdivision == sub, onClick = { subdivision = sub; subdivisionMenuExpanded = false })
                    }
                }
            }
            if (soundMenuExpanded) {
                MetronomeOverlaySelector(title = "Tipo de clic", onDismiss = { soundMenuExpanded = false }) {
                    MetronomeSound.entries.forEach { s ->
                        MetronomeOverlayItem(text = s.displayName, isSelected = sound == s, onClick = { sound = s; soundMenuExpanded = false })
                    }
                }
            }
            if (tempoPresetMenuExpanded) {
                MetronomeOverlaySelector(title = "Tempo", onDismiss = { tempoPresetMenuExpanded = false }) {
                    TEMPO_PRESETS.forEach { preset ->
                        MetronomeOverlayItem(
                            text = "${preset.name} (${preset.bpmMin}–${preset.bpmMax} BPM)",
                            isSelected = bpm in preset.bpmMin..preset.bpmMax,
                            onClick = { bpm = preset.midBpm; bpmSlider = bpm.toFloat(); tempoPresetMenuExpanded = false }
                        )
                    }
                }
            }

            // Drum style selector (horizontal chips)
            Text("Batería", fontSize = 12.sp, color = Color(0xFFFFC107), fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // "Off" chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (selectedDrumStyle == null) Color(0xFFFFC107) else Color(0xFF2A2A3E))
                        .clickable {
                            selectedDrumStyle = null
                            drumJob?.cancel()
                            DrumEngine.stop()
                        }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Off", fontSize = 11.sp, color = if (selectedDrumStyle == null) Color.Black else Color.White)
                }
                DrumStyle.entries.forEach { style ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selectedDrumStyle == style) Color(0xFFFFC107) else Color(0xFF2A2A3E))
                            .clickable { selectedDrumStyle = style }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(style.displayName, fontSize = 11.sp, color = if (selectedDrumStyle == style) Color.Black else Color.White)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Count-in + Swing + Haptic row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Count-in toggle button
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (countInEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { countInEnabled = !countInEnabled },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "3 2 1",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Black,
                            color = if (countInEnabled) Color.White else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Count-in",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (countInEnabled) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f)
                    )
                }

                // Swing
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Swing", fontSize = 13.sp, fontWeight = FontWeight.Bold, color = Color.White.copy(alpha = 0.7f))
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PillButton("\u2212") { swingPercent = (swingPercent - 5).coerceAtLeast(50) }
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            "$swingPercent%",
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (swingPercent > 50) Color(0xFFFFC107) else Color.White
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        PillButton("+") { swingPercent = (swingPercent + 5).coerceAtMost(75) }
                    }
                }

                // Haptic toggle
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (hapticEnabled) Color(0xFF9C27B0).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)
                            .clickable { hapticEnabled = !hapticEnabled },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Vibration,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = if (hapticEnabled) Color(0xFF9C27B0) else Color.White.copy(alpha = 0.5f)
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        if (hapticEnabled) "Vibración ON" else "Vibración",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (hapticEnabled) Color(0xFF9C27B0) else Color.White.copy(alpha = 0.5f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mute bars card
            FeatureCard(
                icon = Icons.Default.VolumeOff,
                title = "Barras mudas",
                enabled = muteEnabled,
                onToggle = { muteEnabled = !muteEnabled },
                activeColor = Color(0xFF9C27B0)
            ) {
                Text(
                    "Alterna compases con sonido y sin sonido para desarrollar tu oido interno",
                    fontSize = 11.sp,
                    color = Color.White.copy(alpha = 0.5f)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ValueSelector(
                        label = "Suena",
                        value = "$muteBarsPlay",
                        unit = "comp.",
                        onMinus = { muteBarsPlay = (muteBarsPlay - 1).coerceAtLeast(1) },
                        onPlus = { muteBarsPlay = (muteBarsPlay + 1).coerceAtMost(16) }
                    )
                    ValueSelector(
                        label = "Silencio",
                        value = "$muteBarsSilent",
                        unit = "comp.",
                        onMinus = { muteBarsSilent = (muteBarsSilent - 1).coerceAtLeast(1) },
                        onPlus = { muteBarsSilent = (muteBarsSilent + 1).coerceAtMost(16) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Training card
            FeatureCard(
                icon = Icons.Default.FitnessCenter,
                title = "Entrenamiento",
                enabled = trainingEnabled,
                onToggle = { trainingEnabled = !trainingEnabled },
                activeColor = Color(0xFF2196F3)
            ) {
                if (isPlaying && trainingEnabled) {
                    val progress = ((currentBpm - bpm).toFloat() / (trainingMaxBpm - bpm).toFloat()).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFF2196F3),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "$currentBpm \u2192 $trainingMaxBpm BPM",
                        fontSize = 12.sp,
                        color = Color(0xFF2196F3),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Direction selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TrainingDirection.entries.forEach { dir ->
                        Pill(dir.displayName, trainingDirection == dir) { trainingDirection = dir }
                        Spacer(modifier = Modifier.width(4.dp))
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ValueSelector(
                        label = "Cada",
                        value = "$trainingInterval",
                        unit = "comp.",
                        onMinus = { trainingInterval = (trainingInterval - 1).coerceAtLeast(1) },
                        onPlus = { trainingInterval = (trainingInterval + 1).coerceAtMost(32) }
                    )
                    ValueSelector(
                        label = "Cambio",
                        value = "+$trainingBpmChange",
                        unit = "BPM",
                        onMinus = { trainingBpmChange = (trainingBpmChange - 1).coerceAtLeast(1) },
                        onPlus = { trainingBpmChange = (trainingBpmChange + 1).coerceAtMost(20) }
                    )
                    ValueSelector(
                        label = "Hasta",
                        value = "$trainingMaxBpm",
                        unit = "BPM",
                        onMinus = { trainingMaxBpm = (trainingMaxBpm - 5).coerceAtLeast(bpm + 10) },
                        onPlus = { trainingMaxBpm = (trainingMaxBpm + 5).coerceAtMost(300) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Timer card
            FeatureCard(
                icon = Icons.Default.Timer,
                title = "Temporizador",
                enabled = timerEnabled,
                onToggle = { timerEnabled = !timerEnabled },
                activeColor = Color(0xFFFF9800)
            ) {
                if (isPlaying && timerEnabled) {
                    val remaining = if (timerMode == "measures") {
                        "${(timerMeasures - currentMeasure).coerceAtLeast(0)} compases restantes"
                    } else {
                        "${formatTime((timerSeconds - elapsedSeconds).coerceAtLeast(0))} restantes"
                    }
                    val progress = if (timerMode == "measures") {
                        (currentMeasure.toFloat() / timerMeasures).coerceIn(0f, 1f)
                    } else {
                        (elapsedSeconds.toFloat() / timerSeconds).coerceIn(0f, 1f)
                    }
                    LinearProgressIndicator(
                        progress = progress,
                        modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color = Color(0xFFFF9800),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = remaining,
                        fontSize = 12.sp,
                        color = Color(0xFFFF9800),
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                // Mode toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Pill("Compases", timerMode == "measures") { timerMode = "measures" }
                    Spacer(modifier = Modifier.width(8.dp))
                    Pill("Tiempo", timerMode == "time") { timerMode = "time" }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (timerMode == "measures") {
                    BigValueSelector(
                        value = "$timerMeasures",
                        unit = "compases",
                        onMinus = { timerMeasures = (timerMeasures - 4).coerceAtLeast(4) },
                        onPlus = { timerMeasures = (timerMeasures + 4).coerceAtMost(128) }
                    )
                } else {
                    BigValueSelector(
                        value = formatTime(timerSeconds),
                        unit = "",
                        onMinus = { timerSeconds = (timerSeconds - 10).coerceAtLeast(10) },
                        onPlus = { timerSeconds = (timerSeconds + 10).coerceAtMost(600) }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Tap Tempo Button
// ═══════════════════════════════════════════════════════════

@Composable
private fun TapTempoButton(size: Int, onTap: () -> Unit) {
    FilledIconButton(
        onClick = onTap,
        modifier = Modifier.size(size.dp),
        colors = IconButtonDefaults.filledIconButtonColors(
            containerColor = Color(0xFFFFC107)
        )
    ) {
        Icon(
            Icons.Default.TouchApp,
            contentDescription = "Tap Tempo",
            modifier = Modifier.size((size * 0.55f).toInt().dp),
            tint = Color.Black
        )
    }
}

// ═══════════════════════════════════════════════════════════
// Status Badges
// ═══════════════════════════════════════════════════════════

@Composable
private fun StatusBadges(isCountingIn: Boolean, isMutedBar: Boolean, swingPercent: Int) {
    Row(horizontalArrangement = Arrangement.Center) {
        if (isCountingIn) {
            Badge("COUNT-IN", Color(0xFFFFC107))
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (isMutedBar) {
            Badge("SILENCIO", Color(0xFF9C27B0))
            Spacer(modifier = Modifier.width(6.dp))
        }
        if (swingPercent > 50) {
            Badge("SWING $swingPercent%", Color(0xFFFF9800))
        }
    }
}

@Composable
private fun Badge(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.2f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(text, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

// ═══════════════════════════════════════════════════════════
// Enhanced Beat Visualizer with clickable accents
// ═══════════════════════════════════════════════════════════

@Composable
private fun BeatVisualizerEnhanced(
    beatsPerMeasure: Int,
    currentBeat: Int,
    accentPattern: List<Boolean>,
    onToggleAccent: (Int) -> Unit,
    isCountingIn: Boolean,
    isMutedBar: Boolean
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        for (beat in 0 until beatsPerMeasure) {
            val isActive = beat == currentBeat
            val isAccented = accentPattern.getOrElse(beat) { beat == 0 }

            val baseColor = when {
                isMutedBar && isActive -> Color(0xFF9C27B0).copy(alpha = 0.4f)
                isCountingIn && isActive -> Color(0xFFFFC107)
                isActive && isAccented -> Color(0xFFF44336)
                isActive -> Color(0xFF4CAF50)
                isAccented -> Color(0xFFF44336).copy(alpha = 0.25f)
                else -> MaterialTheme.colorScheme.surfaceVariant
            }

            val color by animateColorAsState(targetValue = baseColor, animationSpec = tween(80), label = "color")

            val pulseScale by animateFloatAsState(
                targetValue = if (isActive) 1.2f else 1.0f,
                animationSpec = tween(100),
                label = "pulse"
            )

            Box(
                modifier = Modifier
                    .size(32.dp)
                    .scale(pulseScale)
                    .clip(CircleShape)
                    .background(color)
                    .then(
                        if (isAccented) Modifier.border(2.dp, Color(0xFFF44336).copy(alpha = 0.6f), CircleShape)
                        else Modifier
                    )
                    .clickable { onToggleAccent(beat) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${beat + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (beat < beatsPerMeasure - 1) Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Settings Row (landscape)
// ═══════════════════════════════════════════════════════════

@Composable
private fun MetronomeSettingsRow(
    beatsPerMeasure: Int,
    beatUnit: Int,
    timeSigMenuExpanded: Boolean,
    onTimeSigMenuToggle: (Boolean) -> Unit,
    onTimeSigSelected: (TimeSignature) -> Unit,
    subdivision: Int,
    subdivisionMenuExpanded: Boolean,
    onSubdivisionMenuToggle: (Boolean) -> Unit,
    onSubdivisionSelected: (Int) -> Unit,
    sound: MetronomeSound,
    soundMenuExpanded: Boolean,
    onSoundMenuToggle: (Boolean) -> Unit,
    onSoundSelected: (MetronomeSound) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        OutlinedButton(onClick = { onTimeSigMenuToggle(true) }) {
            Text("$beatsPerMeasure/$beatUnit", fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { onSubdivisionMenuToggle(true) }) {
            val subLabel = when (subdivision) {
                1 -> "\u2669"; 2 -> "\u266a\u266a"; 3 -> "\u266a\u266a\u266a"; 4 -> "\u266c"; else -> "$subdivision"
            }
            Text(subLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }
        OutlinedButton(onClick = { onSoundMenuToggle(true) }) {
            Text("\u266a ${sound.displayName}", fontSize = 12.sp)
        }
    }

    if (timeSigMenuExpanded) {
        MetronomeOverlaySelector(title = "Compás", onDismiss = { onTimeSigMenuToggle(false) }) {
            TIME_SIGNATURES.forEach { ts ->
                MetronomeOverlayItem(text = ts.label, isSelected = beatsPerMeasure == ts.beats && beatUnit == ts.unit, onClick = { onTimeSigSelected(ts) })
            }
        }
    }
    if (subdivisionMenuExpanded) {
        MetronomeOverlaySelector(title = "Subdivisión", onDismiss = { onSubdivisionMenuToggle(false) }) {
            listOf(1 to "\u2669 Negras", 2 to "\u266a\u266a Corcheas", 3 to "\u266a\u266a\u266a Tresillos", 4 to "\u266c Semicorcheas").forEach { (sub, label) ->
                MetronomeOverlayItem(text = label, isSelected = subdivision == sub, onClick = { onSubdivisionSelected(sub) })
            }
        }
    }
    if (soundMenuExpanded) {
        MetronomeOverlaySelector(title = "Tipo de clic", onDismiss = { onSoundMenuToggle(false) }) {
            MetronomeSound.entries.forEach { s ->
                MetronomeOverlayItem(text = s.displayName, isSelected = sound == s, onClick = { onSoundSelected(s) })
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Quick Settings Row (landscape)
// ═══════════════════════════════════════════════════════════

@Composable
private fun QuickSettingsRow(
    countInEnabled: Boolean,
    onCountInToggle: () -> Unit,
    swingPercent: Int,
    onSwingChange: (Int) -> Unit,
    hapticEnabled: Boolean,
    onHapticToggle: () -> Unit,
    tempoPresetMenuExpanded: Boolean,
    onTempoPresetMenuToggle: (Boolean) -> Unit,
    onTempoPresetSelected: (TempoPreset) -> Unit
) {
    // Count-in + Swing + Haptic + Tempo row
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Count-in toggle
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (countInEnabled) Color(0xFF4CAF50) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onCountInToggle() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "3 2 1",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Black,
                    color = if (countInEnabled) Color.White else Color.White.copy(alpha = 0.5f)
                )
            }
            Text(
                "Count-in",
                fontSize = 9.sp,
                color = if (countInEnabled) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.5f)
            )
        }
        // Swing
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Swing:", fontSize = 12.sp, color = Color.White.copy(alpha = 0.7f))
            Spacer(modifier = Modifier.width(6.dp))
            MiniButton("\u2212") { onSwingChange((swingPercent - 5).coerceAtLeast(50)) }
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                "$swingPercent%",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = if (swingPercent > 50) Color(0xFFFFC107) else Color.White.copy(alpha = 0.6f)
            )
            Spacer(modifier = Modifier.width(6.dp))
            MiniButton("+") { onSwingChange((swingPercent + 5).coerceAtMost(75)) }
        }
        // Haptic
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (hapticEnabled) Color(0xFF9C27B0).copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onHapticToggle() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Vibration,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = if (hapticEnabled) Color(0xFF9C27B0) else Color.White.copy(alpha = 0.4f)
                )
            }
            Text(
                if (hapticEnabled) "ON" else "OFF",
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                color = if (hapticEnabled) Color(0xFF9C27B0) else Color.White.copy(alpha = 0.4f)
            )
        }
        // Tempo presets
        OutlinedButton(onClick = { onTempoPresetMenuToggle(true) }) {
            Text("Tempo \u25BC", fontSize = 11.sp)
        }
    }

    if (tempoPresetMenuExpanded) {
        MetronomeOverlaySelector(title = "Tempo", onDismiss = { onTempoPresetMenuToggle(false) }) {
            TEMPO_PRESETS.forEach { preset ->
                MetronomeOverlayItem(
                    text = "${preset.name} (${preset.bpmMin}\u2013${preset.bpmMax} BPM)",
                    isSelected = false,
                    onClick = { onTempoPresetSelected(preset) }
                )
            }
        }
    }
}

// ═══════════════════════════════════════════════════════════
// All feature cards
// ═══════════════════════════════════════════════════════════

@Composable
private fun AllCards(
    isPlaying: Boolean,
    currentBpm: Int,
    bpm: Int,
    currentMeasure: Int,
    elapsedSeconds: Int,
    muteEnabled: Boolean,
    onMuteToggle: () -> Unit,
    muteBarsPlay: Int,
    onMuteBarsPlayChange: (Int) -> Unit,
    muteBarsSilent: Int,
    onMuteBarsSilentChange: (Int) -> Unit,
    trainingEnabled: Boolean,
    onTrainingToggle: () -> Unit,
    trainingInterval: Int,
    onTrainingIntervalChange: (Int) -> Unit,
    trainingBpmChange: Int,
    onTrainingBpmChangeChange: (Int) -> Unit,
    trainingMaxBpm: Int,
    onTrainingMaxBpmChange: (Int) -> Unit,
    trainingDirection: TrainingDirection,
    onTrainingDirectionChange: (TrainingDirection) -> Unit,
    timerEnabled: Boolean,
    onTimerToggle: () -> Unit,
    timerMode: String,
    onTimerModeChange: (String) -> Unit,
    timerMeasures: Int,
    onTimerMeasuresChange: (Int) -> Unit,
    timerSeconds: Int,
    onTimerSecondsChange: (Int) -> Unit
) {
    // Mute bars
    FeatureCard(
        icon = Icons.Default.VolumeOff,
        title = "Barras mudas",
        enabled = muteEnabled,
        onToggle = onMuteToggle,
        activeColor = Color(0xFF9C27B0)
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ValueSelector("Suena", "$muteBarsPlay", "comp.",
                onMinus = { onMuteBarsPlayChange((muteBarsPlay - 1).coerceAtLeast(1)) },
                onPlus = { onMuteBarsPlayChange((muteBarsPlay + 1).coerceAtMost(16)) })
            ValueSelector("Silencio", "$muteBarsSilent", "comp.",
                onMinus = { onMuteBarsSilentChange((muteBarsSilent - 1).coerceAtLeast(1)) },
                onPlus = { onMuteBarsSilentChange((muteBarsSilent + 1).coerceAtMost(16)) })
        }
    }
    Spacer(modifier = Modifier.height(4.dp))

    // Training
    FeatureCard(
        icon = Icons.Default.FitnessCenter,
        title = "Entrenamiento",
        enabled = trainingEnabled,
        onToggle = onTrainingToggle,
        activeColor = Color(0xFF2196F3)
    ) {
        if (isPlaying && trainingEnabled) {
            val progress = ((currentBpm - bpm).toFloat() / (trainingMaxBpm - bpm).toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(0xFF2196F3),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text("$currentBpm \u2192 $trainingMaxBpm BPM", fontSize = 12.sp, color = Color(0xFF2196F3), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            TrainingDirection.entries.forEach { dir ->
                Pill(dir.displayName, trainingDirection == dir) { onTrainingDirectionChange(dir) }
                Spacer(modifier = Modifier.width(4.dp))
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ValueSelector("Cada", "$trainingInterval", "comp.",
                onMinus = { onTrainingIntervalChange((trainingInterval - 1).coerceAtLeast(1)) },
                onPlus = { onTrainingIntervalChange((trainingInterval + 1).coerceAtMost(32)) })
            ValueSelector("Cambio", "+$trainingBpmChange", "BPM",
                onMinus = { onTrainingBpmChangeChange((trainingBpmChange - 1).coerceAtLeast(1)) },
                onPlus = { onTrainingBpmChangeChange((trainingBpmChange + 1).coerceAtMost(20)) })
            ValueSelector("Hasta", "$trainingMaxBpm", "BPM",
                onMinus = { onTrainingMaxBpmChange((trainingMaxBpm - 5).coerceAtLeast(bpm + 10)) },
                onPlus = { onTrainingMaxBpmChange((trainingMaxBpm + 5).coerceAtMost(300)) })
        }
    }

    Spacer(modifier = Modifier.height(4.dp))

    // Timer
    FeatureCard(
        icon = Icons.Default.Timer,
        title = "Temporizador",
        enabled = timerEnabled,
        onToggle = onTimerToggle,
        activeColor = Color(0xFFFF9800)
    ) {
        if (isPlaying && timerEnabled) {
            val remaining = if (timerMode == "measures") {
                "${(timerMeasures - currentMeasure).coerceAtLeast(0)} compases restantes"
            } else {
                "${formatTime((timerSeconds - elapsedSeconds).coerceAtLeast(0))} restantes"
            }
            val progress = if (timerMode == "measures") {
                (currentMeasure.toFloat() / timerMeasures).coerceIn(0f, 1f)
            } else {
                (elapsedSeconds.toFloat() / timerSeconds).coerceIn(0f, 1f)
            }
            LinearProgressIndicator(
                progress = progress,
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                color = Color(0xFFFF9800),
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(remaining, fontSize = 12.sp, color = Color(0xFFFF9800), fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            Pill("Compases", timerMode == "measures") { onTimerModeChange("measures") }
            Spacer(modifier = Modifier.width(8.dp))
            Pill("Tiempo", timerMode == "time") { onTimerModeChange("time") }
        }
        Spacer(modifier = Modifier.height(6.dp))
        if (timerMode == "measures") {
            BigValueSelector("$timerMeasures", "compases",
                onMinus = { onTimerMeasuresChange((timerMeasures - 4).coerceAtLeast(4)) },
                onPlus = { onTimerMeasuresChange((timerMeasures + 4).coerceAtMost(128)) })
        } else {
            BigValueSelector(formatTime(timerSeconds), "",
                onMinus = { onTimerSecondsChange((timerSeconds - 10).coerceAtLeast(10)) },
                onPlus = { onTimerSecondsChange((timerSeconds + 10).coerceAtMost(600)) })
        }
    }
}

// ═══════════════════════════════════════════════════════════
// Shared Components
// ═══════════════════════════════════════════════════════════

@Composable
private fun FeatureCard(
    icon: ImageVector,
    title: String,
    enabled: Boolean,
    onToggle: () -> Unit,
    activeColor: Color,
    content: @Composable () -> Unit
) {
    val borderColor by animateColorAsState(
        targetValue = if (enabled) activeColor else Color.Transparent,
        label = "border"
    )

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    val cardPadding = if (isLandscape) 8.dp else 14.dp

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = if (enabled) 2.dp else 0.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            ),
        colors = CardDefaults.cardColors(
            containerColor = if (enabled)
                activeColor.copy(alpha = 0.05f)
            else
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(modifier = Modifier.padding(cardPadding)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onToggle),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(if (isLandscape) 16.dp else 20.dp)
                )
                Spacer(modifier = Modifier.width(if (isLandscape) 4.dp else 8.dp))
                Text(
                    text = title,
                    fontSize = if (isLandscape) 13.sp else 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                TogglePill(enabled = enabled, activeColor = activeColor, onClick = onToggle)
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(if (isLandscape) 6.dp else 12.dp))
                content()
            }
        }
    }
}

@Composable
private fun TogglePill(enabled: Boolean, activeColor: Color, onClick: () -> Unit) {
    val bgColor by animateColorAsState(
        targetValue = if (enabled) activeColor else MaterialTheme.colorScheme.surfaceVariant,
        label = "toggleBg"
    )
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = if (enabled) "ON" else "OFF",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ValueSelector(
    label: String,
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
        Spacer(modifier = Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MiniButton("\u2212", onMinus)
            Spacer(modifier = Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.White)
                if (unit.isNotEmpty()) {
                    Text(unit, fontSize = 9.sp, color = Color.White.copy(alpha = 0.5f))
                }
            }
            Spacer(modifier = Modifier.width(6.dp))
            MiniButton("+", onPlus)
        }
    }
}

@Composable
private fun BigValueSelector(
    value: String,
    unit: String,
    onMinus: () -> Unit,
    onPlus: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        PillButton("\u2212") { onMinus() }
        Spacer(modifier = Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = Color.White)
            if (unit.isNotEmpty()) {
                Text(unit, fontSize = 11.sp, color = Color.White.copy(alpha = 0.5f))
            }
        }
        Spacer(modifier = Modifier.width(16.dp))
        PillButton("+") { onPlus() }
    }
}

@Composable
private fun MiniButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, fontSize = 14.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Pill(text: String, isSelected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(
                if (isSelected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 13.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun MetronomeOverlaySelector(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .heightIn(max = 480.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(title, color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Icon(
                        Icons.Default.Close, "Cerrar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp).clickable { onDismiss() }
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .weight(1f, fill = false)
                        .verticalScroll(rememberScrollState())
                ) {
                    content()
                }
            }
        }
    }
}

@Composable
private fun MetronomeOverlayItem(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF4CAF50) else Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text,
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}

@Suppress("DEPRECATION")
private fun triggerHaptic(context: Context) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator?.vibrate(
            VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
        )
    } else {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        vibrator?.vibrate(VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}


