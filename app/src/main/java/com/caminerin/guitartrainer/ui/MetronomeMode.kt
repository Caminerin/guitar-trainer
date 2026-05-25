package com.caminerin.guitartrainer.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
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
    val currentSubBeat by engine.currentSubBeat.collectAsState()
    val currentMeasure by engine.currentMeasure.collectAsState()
    val currentBpm by engine.currentBpm.collectAsState()
    val elapsedSeconds by engine.elapsedSeconds.collectAsState()

    var bpm by remember { mutableIntStateOf(120) }
    var bpmSlider by remember { mutableFloatStateOf(120f) }
    var beatsPerMeasure by remember { mutableIntStateOf(4) }
    var subdivision by remember { mutableIntStateOf(1) }
    var sound by remember { mutableStateOf(MetronomeSound.CLICK) }
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
            val config = MetronomeConfig(
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
            engine.start(config)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Beat visualization
        BeatVisualizer(
            beatsPerMeasure = beatsPerMeasure,
            subdivision = subdivision,
            currentBeat = if (isPlaying) currentBeat else -1,
            currentSubBeat = if (isPlaying) currentSubBeat else -1
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Measure counter & elapsed time
        if (isPlaying) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Text(
                    text = "Compás $currentMeasure",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                Text(
                    text = formatTime(elapsedSeconds),
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )
                if (currentBpm != bpm) {
                    Text(
                        text = "$currentBpm BPM",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        // Timer countdown
        if (isPlaying && timerEnabled) {
            val remaining = if (timerMode == "measures") {
                "Faltan ${(timerMeasures - currentMeasure).coerceAtLeast(0)} compases"
            } else {
                "Faltan ${formatTime((timerSeconds - elapsedSeconds).coerceAtLeast(0))}"
            }
            Text(
                text = remaining,
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.tertiary
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        // BPM display and controls
        Text(
            text = "$bpm",
            fontSize = 56.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "BPM",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )

        Spacer(modifier = Modifier.height(8.dp))

        // BPM buttons
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BpmButton("-5") { bpm = (bpm - 5).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
            Spacer(modifier = Modifier.width(8.dp))
            BpmButton("-1") { bpm = (bpm - 1).coerceAtLeast(20); bpmSlider = bpm.toFloat() }
            Spacer(modifier = Modifier.width(24.dp))
            BpmButton("+1") { bpm = (bpm + 1).coerceAtMost(300); bpmSlider = bpm.toFloat() }
            Spacer(modifier = Modifier.width(8.dp))
            BpmButton("+5") { bpm = (bpm + 5).coerceAtMost(300); bpmSlider = bpm.toFloat() }
        }

        // BPM slider
        Slider(
            value = bpmSlider,
            onValueChange = { bpmSlider = it; bpm = it.toInt() },
            valueRange = 20f..300f,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Time signature & subdivision
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Beats per measure
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Compás", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    listOf(2, 3, 4, 5, 6, 7).forEach { beats ->
                        SmallChip(
                            text = "$beats",
                            isSelected = beatsPerMeasure == beats,
                            onClick = { beatsPerMeasure = beats }
                        )
                        if (beats < 7) Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            // Subdivision
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Subdivisión", fontSize = 12.sp, color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f))
                Spacer(modifier = Modifier.height(4.dp))
                Row {
                    listOf(
                        1 to "♩",
                        2 to "♫",
                        3 to "3",
                        4 to "♬"
                    ).forEach { (sub, label) ->
                        SmallChip(
                            text = label,
                            isSelected = subdivision == sub,
                            onClick = { subdivision = sub }
                        )
                        if (sub < 4) Spacer(modifier = Modifier.width(4.dp))
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sound selector
        Box {
            OutlinedButton(onClick = { soundMenuExpanded = true }) {
                Text("Sonido: ${sound.displayName}")
            }
            DropdownMenu(
                expanded = soundMenuExpanded,
                onDismissRequest = { soundMenuExpanded = false }
            ) {
                MetronomeSound.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.displayName) },
                        onClick = { sound = s; soundMenuExpanded = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Training mode section
        SectionHeader("Modo entrenamiento")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Activar", fontSize = 14.sp)
            Switch(checked = trainingEnabled, onCheckedChange = { trainingEnabled = it })
        }
        if (trainingEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Cada", fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BpmButton("-") { trainingInterval = (trainingInterval - 1).coerceAtLeast(1) }
                    Text(" $trainingInterval compases ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    BpmButton("+") { trainingInterval = (trainingInterval + 1).coerceAtMost(32) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Subir", fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BpmButton("-") { trainingBpmChange = (trainingBpmChange - 1).coerceAtLeast(1) }
                    Text(" $trainingBpmChange BPM ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    BpmButton("+") { trainingBpmChange = (trainingBpmChange + 1).coerceAtMost(20) }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hasta", fontSize = 13.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BpmButton("-") { trainingMaxBpm = (trainingMaxBpm - 5).coerceAtLeast(bpm + 10) }
                    Text(" $trainingMaxBpm BPM ", fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    BpmButton("+") { trainingMaxBpm = (trainingMaxBpm + 5).coerceAtMost(300) }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Timer section
        SectionHeader("Temporizador")
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Activar", fontSize = 14.sp)
            Switch(checked = timerEnabled, onCheckedChange = { timerEnabled = it })
        }
        if (timerEnabled) {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                SmallChip(
                    text = "Compases",
                    isSelected = timerMode == "measures",
                    onClick = { timerMode = "measures" }
                )
                Spacer(modifier = Modifier.width(8.dp))
                SmallChip(
                    text = "Tiempo",
                    isSelected = timerMode == "time",
                    onClick = { timerMode = "time" }
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            if (timerMode == "measures") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BpmButton("-4") { timerMeasures = (timerMeasures - 4).coerceAtLeast(4) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("$timerMeasures compases", fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    BpmButton("+4") { timerMeasures = (timerMeasures + 4).coerceAtMost(128) }
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    BpmButton("-10") { timerSeconds = (timerSeconds - 10).coerceAtLeast(10) }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(formatTime(timerSeconds), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.width(8.dp))
                    BpmButton("+10") { timerSeconds = (timerSeconds + 10).coerceAtMost(600) }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Play/Stop button
        FilledIconButton(
            onClick = {
                if (isPlaying) {
                    engine.stop()
                } else {
                    playTrigger++
                }
            },
            modifier = Modifier.size(72.dp),
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = if (isPlaying) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.primary
            )
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (isPlaying) "Parar" else "Iniciar",
                modifier = Modifier.size(36.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun BeatVisualizer(
    beatsPerMeasure: Int,
    subdivision: Int,
    currentBeat: Int,
    currentSubBeat: Int
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        horizontalArrangement = Arrangement.Center
    ) {
        for (beat in 0 until beatsPerMeasure) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Main beat circle
                val isCurrentBeat = beat == currentBeat
                val beatColor by animateColorAsState(
                    targetValue = when {
                        isCurrentBeat && beat == 0 -> Color(0xFFF44336)
                        isCurrentBeat -> Color(0xFF4CAF50)
                        else -> MaterialTheme.colorScheme.surfaceVariant
                    },
                    label = "beatColor"
                )

                Box(
                    modifier = Modifier
                        .size(if (beat == 0) 28.dp else 24.dp)
                        .clip(CircleShape)
                        .background(beatColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "${beat + 1}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrentBeat) Color.White
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Subdivision dots
                if (subdivision > 1) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        for (sub in 0 until subdivision) {
                            val isCurrentSub = isCurrentBeat && sub == currentSubBeat
                            Box(
                                modifier = Modifier
                                    .size(6.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isCurrentSub) MaterialTheme.colorScheme.primary
                                        else MaterialTheme.colorScheme.surfaceVariant
                                    )
                            )
                            if (sub < subdivision - 1) Spacer(modifier = Modifier.width(2.dp))
                        }
                    }
                }
            }
            if (beat < beatsPerMeasure - 1) {
                Spacer(modifier = Modifier.width(12.dp))
            }
        }
    }
}

@Composable
private fun BpmButton(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SmallChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
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
private fun SectionHeader(title: String) {
    Text(
        text = title,
        fontSize = 14.sp,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 4.dp)
    )
}

private fun formatTime(seconds: Int): String {
    val min = seconds / 60
    val sec = seconds % 60
    return "%d:%02d".format(min, sec)
}
