package com.caminerin.guitartrainer.ui

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.MetronomeConfig
import com.caminerin.guitartrainer.audio.MetronomeEngine
import com.caminerin.guitartrainer.audio.MetronomeSound

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

    var bpm by remember { mutableIntStateOf(120) }
    var bpmSlider by remember { mutableFloatStateOf(120f) }
    var beatsPerMeasure by remember { mutableIntStateOf(4) }
    var subdivision by remember { mutableIntStateOf(1) }
    var sound by remember { mutableStateOf(MetronomeSound.CLICK) }

    var beatsMenuExpanded by remember { mutableStateOf(false) }
    var subdivisionMenuExpanded by remember { mutableStateOf(false) }
    var soundMenuExpanded by remember { mutableStateOf(false) }

    // Training mode
    var trainingEnabled by remember { mutableStateOf(false) }
    var trainingInterval by remember { mutableIntStateOf(4) }
    var trainingBpmChange by remember { mutableIntStateOf(5) }
    var trainingMaxBpm by remember { mutableIntStateOf(200) }

    // Timer
    var timerEnabled by remember { mutableStateOf(false) }
    var timerMode by remember { mutableStateOf("measures") }
    var timerMeasures by remember { mutableIntStateOf(16) }
    var timerSeconds by remember { mutableIntStateOf(60) }

    // Play trigger
    var playTrigger by remember { mutableIntStateOf(0) }

    LaunchedEffect(playTrigger) {
        if (playTrigger > 0) {
            engine.start(
                MetronomeConfig(
                    bpm = bpm,
                    beatsPerMeasure = beatsPerMeasure,
                    subdivision = subdivision,
                    sound = sound,
                    trainingEnabled = trainingEnabled,
                    trainingIntervalBeats = trainingInterval,
                    trainingBpmChange = trainingBpmChange,
                    trainingMaxBpm = trainingMaxBpm,
                    timerEnabled = timerEnabled,
                    timerMeasures = if (timerMode == "measures") timerMeasures else 0,
                    timerSeconds = if (timerMode == "time") timerSeconds else 0
                )
            )
        }
    }

    // Real-time config updates: write to engine's live fields whenever settings change
    LaunchedEffect(bpm) { engine.liveBpm = bpm }
    LaunchedEffect(subdivision) { engine.liveSubdivision = subdivision }
    LaunchedEffect(beatsPerMeasure) { engine.liveBeatsPerMeasure = beatsPerMeasure }
    LaunchedEffect(sound) { engine.liveSound = sound }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left: Play + BPM + beats
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                BeatVisualizer(
                    beatsPerMeasure = beatsPerMeasure,
                    currentBeat = if (isPlaying) currentBeat else -1
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    FilledIconButton(
                        onClick = { if (isPlaying) engine.stop() else playTrigger++ },
                        modifier = Modifier.size(56.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPlaying) Color(0xFFF44336) else Color(0xFF4CAF50)
                        )
                    ) {
                        Icon(
                            imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp),
                            tint = Color.White
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(text = "$bpm", fontSize = 42.sp, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.onBackground)
                        Text("BPM", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
                    }
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.Center) {
                    PillButton("-5") { bpm = (bpm - 5).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
                    Spacer(modifier = Modifier.width(4.dp))
                    PillButton("-1") { bpm = (bpm - 1).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
                    Spacer(modifier = Modifier.width(12.dp))
                    PillButton("+1") { bpm = (bpm + 1).coerceAtMost(300); bpmSlider = bpm.toFloat() }
                    Spacer(modifier = Modifier.width(4.dp))
                    PillButton("+5") { bpm = (bpm + 5).coerceAtMost(300); bpmSlider = bpm.toFloat() }
                }
                Slider(
                    value = bpmSlider,
                    onValueChange = { bpmSlider = it; bpm = it.toInt() },
                    valueRange = 20f..300f,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Right: settings + cards
            Column(
                modifier = Modifier
                    .weight(0.6f)
                    .fillMaxHeight()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 4.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                MetronomeSettings(
                    beatsPerMeasure = beatsPerMeasure,
                    beatsMenuExpanded = beatsMenuExpanded,
                    onBeatsMenuToggle = { beatsMenuExpanded = it },
                    onBeatsSelected = { beatsPerMeasure = it; beatsMenuExpanded = false },
                    subdivision = subdivision,
                    subdivisionMenuExpanded = subdivisionMenuExpanded,
                    onSubdivisionMenuToggle = { subdivisionMenuExpanded = it },
                    onSubdivisionSelected = { subdivision = it; subdivisionMenuExpanded = false },
                    sound = sound,
                    soundMenuExpanded = soundMenuExpanded,
                    onSoundMenuToggle = { soundMenuExpanded = it },
                    onSoundSelected = { sound = it; soundMenuExpanded = false }
                )
                Spacer(modifier = Modifier.height(8.dp))
                MetronomeCards(
                    isPlaying = isPlaying,
                    currentBpm = currentBpm,
                    bpm = bpm,
                    currentMeasure = currentMeasure,
                    elapsedSeconds = elapsedSeconds,
                    trainingEnabled = trainingEnabled,
                    onTrainingToggle = { trainingEnabled = !trainingEnabled },
                    trainingInterval = trainingInterval,
                    onTrainingIntervalChange = { trainingInterval = it },
                    trainingBpmChange = trainingBpmChange,
                    onTrainingBpmChangeChange = { trainingBpmChange = it },
                    trainingMaxBpm = trainingMaxBpm,
                    onTrainingMaxBpmChange = { trainingMaxBpm = it },
                    timerEnabled = timerEnabled,
                    onTimerToggle = { timerEnabled = !timerEnabled },
                    timerMode = timerMode,
                    onTimerModeChange = { timerMode = it },
                    timerMeasures = timerMeasures,
                    onTimerMeasuresChange = { timerMeasures = it },
                    timerSeconds = timerSeconds,
                    onTimerSecondsChange = { timerSeconds = it }
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
        BeatVisualizer(
            beatsPerMeasure = beatsPerMeasure,
            currentBeat = if (isPlaying) currentBeat else -1
        )

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
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
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text("BPM", fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
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

        Spacer(modifier = Modifier.height(4.dp))

        // Compás + Subdivisión + Sonido in a row of dropdown buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Compás dropdown
            Box {
                OutlinedButton(onClick = { beatsMenuExpanded = true }) {
                    Text("$beatsPerMeasure/4", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = beatsMenuExpanded, onDismissRequest = { beatsMenuExpanded = false }) {
                    (2..12).forEach { n ->
                        DropdownMenuItem(
                            text = { Text("$n/4") },
                            onClick = { beatsPerMeasure = n; beatsMenuExpanded = false }
                        )
                    }
                }
            }

            // Subdivisión dropdown
            Box {
                OutlinedButton(onClick = { subdivisionMenuExpanded = true }) {
                    val subLabel = when (subdivision) {
                        1 -> "♩"
                        2 -> "♪♪"
                        3 -> "♪♪♪"
                        4 -> "♬"
                        else -> "$subdivision"
                    }
                    Text(subLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = subdivisionMenuExpanded, onDismissRequest = { subdivisionMenuExpanded = false }) {
                    listOf(
                        1 to "♩ Negras",
                        2 to "♪♪ Corcheas",
                        3 to "♪♪♪ Tresillos",
                        4 to "♬ Semicorcheas"
                    ).forEach { (sub, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { subdivision = sub; subdivisionMenuExpanded = false }
                        )
                    }
                }
            }

            // Sonido dropdown
            Box {
                OutlinedButton(onClick = { soundMenuExpanded = true }) {
                    Text("♪ ${sound.displayName}", fontSize = 12.sp)
                }
                DropdownMenu(expanded = soundMenuExpanded, onDismissRequest = { soundMenuExpanded = false }) {
                    MetronomeSound.entries.forEach { s ->
                        DropdownMenuItem(
                            text = { Text(s.displayName) },
                            onClick = { sound = s; soundMenuExpanded = false }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

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
                    text = "$currentBpm → $trainingMaxBpm BPM",
                    fontSize = 12.sp,
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

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
                    label = "Subir",
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
    } // else (portrait)
}

@Composable
private fun MetronomeSettings(
    beatsPerMeasure: Int,
    beatsMenuExpanded: Boolean,
    onBeatsMenuToggle: (Boolean) -> Unit,
    onBeatsSelected: (Int) -> Unit,
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
        Box {
            OutlinedButton(onClick = { onBeatsMenuToggle(true) }) {
                Text("$beatsPerMeasure/4", fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = beatsMenuExpanded, onDismissRequest = { onBeatsMenuToggle(false) }) {
                (2..12).forEach { n ->
                    DropdownMenuItem(text = { Text("$n/4") }, onClick = { onBeatsSelected(n) })
                }
            }
        }
        Box {
            OutlinedButton(onClick = { onSubdivisionMenuToggle(true) }) {
                val subLabel = when (subdivision) {
                    1 -> "\u2669"; 2 -> "\u266a\u266a"; 3 -> "\u266a\u266a\u266a"; 4 -> "\u266c"; else -> "$subdivision"
                }
                Text(subLabel, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }
            DropdownMenu(expanded = subdivisionMenuExpanded, onDismissRequest = { onSubdivisionMenuToggle(false) }) {
                listOf(1 to "\u2669 Negras", 2 to "\u266a\u266a Corcheas", 3 to "\u266a\u266a\u266a Tresillos", 4 to "\u266c Semicorcheas").forEach { (sub, label) ->
                    DropdownMenuItem(text = { Text(label) }, onClick = { onSubdivisionSelected(sub) })
                }
            }
        }
        Box {
            OutlinedButton(onClick = { onSoundMenuToggle(true) }) {
                Text("\u266a ${sound.displayName}", fontSize = 12.sp)
            }
            DropdownMenu(expanded = soundMenuExpanded, onDismissRequest = { onSoundMenuToggle(false) }) {
                MetronomeSound.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.displayName) }, onClick = { onSoundSelected(s) })
                }
            }
        }
    }
}

@Composable
private fun MetronomeCards(
    isPlaying: Boolean,
    currentBpm: Int,
    bpm: Int,
    currentMeasure: Int,
    elapsedSeconds: Int,
    trainingEnabled: Boolean,
    onTrainingToggle: () -> Unit,
    trainingInterval: Int,
    onTrainingIntervalChange: (Int) -> Unit,
    trainingBpmChange: Int,
    onTrainingBpmChangeChange: (Int) -> Unit,
    trainingMaxBpm: Int,
    onTrainingMaxBpmChange: (Int) -> Unit,
    timerEnabled: Boolean,
    onTimerToggle: () -> Unit,
    timerMode: String,
    onTimerModeChange: (String) -> Unit,
    timerMeasures: Int,
    onTimerMeasuresChange: (Int) -> Unit,
    timerSeconds: Int,
    onTimerSecondsChange: (Int) -> Unit
) {
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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ValueSelector("Cada", "$trainingInterval", "comp.",
                onMinus = { onTrainingIntervalChange((trainingInterval - 1).coerceAtLeast(1)) },
                onPlus = { onTrainingIntervalChange((trainingInterval + 1).coerceAtMost(32)) })
            ValueSelector("Subir", "+$trainingBpmChange", "BPM",
                onMinus = { onTrainingBpmChangeChange((trainingBpmChange - 1).coerceAtLeast(1)) },
                onPlus = { onTrainingBpmChangeChange((trainingBpmChange + 1).coerceAtMost(20)) })
            ValueSelector("Hasta", "$trainingMaxBpm", "BPM",
                onMinus = { onTrainingMaxBpmChange((trainingMaxBpm - 5).coerceAtLeast(bpm + 10)) },
                onPlus = { onTrainingMaxBpmChange((trainingMaxBpm + 5).coerceAtMost(300)) })
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

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
        Spacer(modifier = Modifier.height(10.dp))
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
// Components
// ═══════════════════════════════════════════════════════════

@Composable
private fun BeatVisualizer(
    beatsPerMeasure: Int,
    currentBeat: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        for (beat in 0 until beatsPerMeasure) {
            val isActive = beat == currentBeat
            val color by animateColorAsState(
                targetValue = when {
                    isActive && beat == 0 -> Color(0xFFF44336)
                    isActive -> Color(0xFF4CAF50)
                    beat == 0 -> Color(0xFFF44336).copy(alpha = 0.2f)
                    else -> MaterialTheme.colorScheme.surfaceVariant
                },
                animationSpec = tween(80),
                label = "color"
            )

            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${beat + 1}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (isActive) Color.White
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }
            if (beat < beatsPerMeasure - 1) Spacer(modifier = Modifier.width(6.dp))
        }
    }
}

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
        Column(modifier = Modifier.padding(14.dp)) {
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
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (enabled) activeColor else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.weight(1f))
                TogglePill(enabled = enabled, activeColor = activeColor, onClick = onToggle)
            }

            if (enabled) {
                Spacer(modifier = Modifier.height(12.dp))
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
            MiniButton("−", onMinus)
            Spacer(modifier = Modifier.width(6.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(value, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                if (unit.isNotEmpty()) {
                    Text(unit, fontSize = 9.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f))
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
        PillButton("−") { onMinus() }
        Spacer(modifier = Modifier.width(16.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            if (unit.isNotEmpty()) {
                Text(unit, fontSize = 11.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f))
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
            .clip(RoundedCornerShape(10.dp))
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
            .clip(RoundedCornerShape(10.dp))
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

private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}
