package com.caminerin.guitartrainer.ui

import android.Manifest
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import com.caminerin.guitartrainer.audio.DrumEngine
import com.caminerin.guitartrainer.audio.DrumStyle
import com.caminerin.guitartrainer.audio.TickPlayer
import kotlinx.coroutines.*

private val CH_BG = SHARED_BG
private val CH_BAR = SHARED_TOOLBAR
private val CH_ACCENT = SHARED_ACCENT
private val CH_CARD = AppColors.cardBg
private val CH_GREEN = AppColors.success
private val CH_RED = AppColors.error

// Quality filter chips
private enum class QualityFilter(val label: String, val qualities: Set<String>) {
    TRIADA("Tríada", setOf("major", "minor", "dim", "aug", "sus2", "sus4", "5")),
    CUATRIADA("Cuatríada", setOf("7", "maj7", "m7", "dim7", "m7b5", "mMaj7", "7sus4")),
    EXTENSION("Extensiones", setOf("add9", "9", "maj9", "m9", "mMaj9", "6", "6/9", "m6", "m6/9", "9sus4", "madd9", "m11", "m13", "m7b9"))
}

@Composable
fun ChordChallengeScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var dataLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ChordRepository.loadChords(context)
        dataLoaded = true
    }

    // Screen state
    var selectedChords by remember { mutableStateOf(listOf<ChordShape>()) }
    var phase by remember { mutableStateOf("select") } // select, config, exercise, result
    var resultBpm by remember { mutableIntStateOf(0) }

    // Config state
    var measuresToAdvance by remember { mutableIntStateOf(4) }
    var bpmIncrement by remember { mutableIntStateOf(5) }
    var useDrums by remember { mutableStateOf(false) }
    var drumStyle by remember { mutableStateOf(DrumStyle.ROCK) }
    var startBpm by remember { mutableIntStateOf(40) }

    Box(modifier = Modifier.fillMaxSize().background(CH_BG)) {
        if (!dataLoaded) return@Box

        when (phase) {
            "select" -> ChordSelectionPhase(
                selectedChords = selectedChords,
                onChordToggle = { chord ->
                    selectedChords = if (selectedChords.any { it.id == chord.id }) {
                        selectedChords.filter { it.id != chord.id }
                    } else if (selectedChords.size < 4) {
                        selectedChords + chord
                    } else selectedChords
                },
                onClear = { selectedChords = emptyList() },
                onNext = { phase = "config" },
                onBack = onBack
            )
            "config" -> ConfigPhase(
                selectedChords = selectedChords,
                measuresToAdvance = measuresToAdvance,
                onMeasuresChange = { measuresToAdvance = it },
                bpmIncrement = bpmIncrement,
                onBpmIncrementChange = { bpmIncrement = it },
                startBpm = startBpm,
                onStartBpmChange = { startBpm = it },
                useDrums = useDrums,
                onUseDrumsChange = { useDrums = it },
                drumStyle = drumStyle,
                onDrumStyleChange = { drumStyle = it },
                onBack = { phase = "select" },
                onStart = { phase = "exercise" }
            )
            "exercise" -> ExercisePhase(
                chords = selectedChords,
                measuresToAdvance = measuresToAdvance,
                bpmIncrement = bpmIncrement,
                startBpm = startBpm,
                useDrums = useDrums,
                drumStyle = drumStyle,
                onFinish = { maxBpm ->
                    resultBpm = maxBpm
                    phase = "result"
                }
            )
            "result" -> ResultPhase(
                maxBpm = resultBpm,
                chords = selectedChords,
                onRepeat = { phase = "exercise" },
                onBack = { phase = "select" }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// Phase 1: Chord Selection with filters
// ═══════════════════════════════════════════════════════
@Composable
private fun ChordSelectionPhase(
    selectedChords: List<ChordShape>,
    onChordToggle: (ChordShape) -> Unit,
    onClear: () -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    var filterRoot by remember { mutableStateOf<String?>(null) }
    var filterQuality by remember { mutableStateOf(QualityFilter.TRIADA) }
    var fretRange by remember { mutableStateOf(0f..3f) }

    val allChords = ChordRepository.getChords()
    val filtered = remember(filterRoot, filterQuality, fretRange, allChords) {
        allChords.filter { chord ->
            (filterRoot == null || chord.root == filterRoot) &&
            chord.quality in filterQuality.qualities &&
            chord.maxFret >= fretRange.start.toInt() &&
            chord.maxFret <= fretRange.endInclusive.toInt() &&
            chord.frets.any { it != null } // exclude empty chords
        }.distinctBy { "${it.root}_${it.qualityLabel}" }
            .sortedBy { AMERICAN_NOTE_NAMES.indexOf(it.root) * 100 + it.priority }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CH_BAR)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text("Reto: Cambio de Acordes", color = CH_ACCENT, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            if (selectedChords.isNotEmpty()) {
                TextButton(onClick = onClear) {
                    Text("Limpiar", color = Color.White.copy(0.5f), fontSize = 11.sp)
                }
            }
        }

        // Info text
        Text(
            "Toca al ritmo del metrónomo. La app detecta cuándo tocas, no qué acorde tocas. Asegúrate de hacer el cambio correcto.",
            color = Color.White.copy(0.5f), fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        // Filter: Root
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text("Tónica:", color = Color.White.copy(0.5f), fontSize = 10.sp,
                modifier = Modifier.align(Alignment.CenterVertically))
            // "Todas" chip
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (filterRoot == null) CH_ACCENT else Color.White.copy(0.08f))
                    .clickable { filterRoot = null }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("Todas", fontSize = 10.sp,
                    color = if (filterRoot == null) Color.Black else Color.White)
            }
            AMERICAN_NOTE_NAMES.forEach { note ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (filterRoot == note) CH_ACCENT else Color.White.copy(0.08f))
                        .clickable { filterRoot = note }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text(note, fontSize = 10.sp,
                        color = if (filterRoot == note) Color.Black else Color.White)
                }
            }
        }

        // Filter: Quality
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            QualityFilter.entries.forEach { qf ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (filterQuality == qf) CH_ACCENT else Color.White.copy(0.08f))
                        .clickable { filterQuality = qf }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(qf.label, fontSize = 11.sp,
                        color = if (filterQuality == qf) Color.Black else Color.White)
                }
            }
        }

        // Filter: Fret range
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Trastes: ${fretRange.start.toInt()}-${fretRange.endInclusive.toInt()}",
                color = Color.White.copy(0.5f), fontSize = 10.sp,
                modifier = Modifier.width(80.dp))
            RangeSlider(
                value = fretRange,
                onValueChange = { fretRange = it },
                valueRange = 0f..12f,
                steps = 11,
                colors = SliderDefaults.colors(
                    thumbColor = CH_ACCENT, activeTrackColor = CH_ACCENT
                ),
                modifier = Modifier.weight(1f).height(28.dp)
            )
        }

        // Counter
        Text(
            "${filtered.size} acordes disponibles",
            color = Color.White.copy(0.4f), fontSize = 10.sp,
            modifier = Modifier.padding(horizontal = 12.dp)
        )

        // Selected chords bar
        if (selectedChords.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CH_BAR)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Selección:", color = CH_ACCENT, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                selectedChords.forEach { chord ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(CH_ACCENT)
                            .clickable { onChordToggle(chord) }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(chord.displayName, color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Text("${selectedChords.size}/4", color = Color.White.copy(0.5f), fontSize = 10.sp)
            }
        }

        // Chord grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(72.dp),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(filtered, key = { it.id }) { chord ->
                val isSelected = selectedChords.any { it.id == chord.id }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isSelected) CH_ACCENT.copy(0.3f) else CH_CARD)
                        .border(
                            width = if (isSelected) 2.dp else 0.dp,
                            color = if (isSelected) CH_ACCENT else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onChordToggle(chord) }
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        chord.displayName,
                        color = if (isSelected) CH_ACCENT else Color.White,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Start button
        if (selectedChords.size >= 2) {
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CH_GREEN)
            ) {
                Text("Configurar (${selectedChords.size} acordes)", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Phase 2: Configuration
// ═══════════════════════════════════════════════════════
@Composable
private fun ConfigPhase(
    selectedChords: List<ChordShape>,
    measuresToAdvance: Int,
    onMeasuresChange: (Int) -> Unit,
    bpmIncrement: Int,
    onBpmIncrementChange: (Int) -> Unit,
    startBpm: Int,
    onStartBpmChange: (Int) -> Unit,
    useDrums: Boolean,
    onUseDrumsChange: (Boolean) -> Unit,
    drumStyle: DrumStyle,
    onDrumStyleChange: (DrumStyle) -> Unit,
    onBack: () -> Unit,
    onStart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CH_BAR)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Text("Configurar Reto", color = CH_ACCENT, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Selected chords display
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            selectedChords.forEach { chord ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CH_ACCENT.copy(0.2f))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(chord.displayName, color = CH_ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Start BPM
        ConfigSlider(
            label = "BPM inicial",
            value = startBpm,
            range = 30..80,
            onValueChange = onStartBpmChange,
            valueLabel = "$startBpm BPM"
        )

        // Measures to advance
        ConfigSlider(
            label = "Compases para subir",
            value = measuresToAdvance,
            range = 2..8,
            onValueChange = onMeasuresChange,
            valueLabel = "$measuresToAdvance compases"
        )

        // BPM increment
        ConfigSlider(
            label = "Incremento de BPM",
            value = bpmIncrement,
            range = 2..10,
            onValueChange = onBpmIncrementChange,
            valueLabel = "+$bpmIncrement BPM"
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Accompaniment: click vs drums
        Text("Acompañamiento", color = Color.White, fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp))
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!useDrums) CH_ACCENT else Color.White.copy(0.08f))
                    .clickable { onUseDrumsChange(false) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Click metrónomo", color = if (!useDrums) Color.Black else Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (useDrums) CH_ACCENT else Color.White.copy(0.08f))
                    .clickable { onUseDrumsChange(true) }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text("Batería", color = if (useDrums) Color.Black else Color.White,
                    fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Drum style selector (only if drums selected)
        if (useDrums) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                DrumStyle.entries.forEach { style ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (drumStyle == style) CH_ACCENT else Color.White.copy(0.08f))
                            .clickable { onDrumStyleChange(style) }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(style.displayName, fontSize = 10.sp,
                            color = if (drumStyle == style) Color.Black else Color.White)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onStart,
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = ButtonDefaults.buttonColors(containerColor = CH_GREEN)
        ) {
            Icon(Icons.Default.PlayArrow, null, modifier = Modifier.size(24.dp))
            Spacer(modifier = Modifier.width(8.dp))
            Text("Empezar", fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun ConfigSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
    valueLabel: String
) {
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, color = Color.White, fontSize = 13.sp)
            Text(valueLabel, color = CH_ACCENT, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValueChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = range.last - range.first - 1,
            colors = SliderDefaults.colors(
                thumbColor = CH_ACCENT, activeTrackColor = CH_ACCENT
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

// ═══════════════════════════════════════════════════════
// Phase 3: Exercise (onset detection + progressive BPM)
// ═══════════════════════════════════════════════════════
@Composable
private fun ExercisePhase(
    chords: List<ChordShape>,
    measuresToAdvance: Int,
    bpmIncrement: Int,
    startBpm: Int,
    useDrums: Boolean,
    drumStyle: DrumStyle,
    onFinish: (Int) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var currentBpm by remember { mutableIntStateOf(startBpm) }
    var currentChordIdx by remember { mutableIntStateOf(0) }
    var currentBeat by remember { mutableIntStateOf(-1) }
    var consecutiveGoodMeasures by remember { mutableIntStateOf(0) }
    var isCountdown by remember { mutableStateOf(true) }
    var countdownNum by remember { mutableIntStateOf(3) }
    var flashColor by remember { mutableStateOf<Color?>(null) }
    var exerciseJob by remember { mutableStateOf<Job?>(null) }
    var drumJob by remember { mutableStateOf<Job?>(null) }
    var maxBpmReached by remember { mutableIntStateOf(startBpm) }
    var isRunning by remember { mutableStateOf(true) }
    val beatsPerMeasure = 4

    val tickPlayer = remember { TickPlayer() }

    // Onset detection state
    var lastOnsetTime by remember { mutableLongStateOf(0L) }
    var onsetDetected by remember { mutableStateOf(false) }

    // Ref-counted DrumEngine init
    LaunchedEffect(Unit) { DrumEngine.addRef(context) }
    DisposableEffect(Unit) {
        onDispose {
            exerciseJob?.cancel()
            drumJob?.cancel()
            tickPlayer.release()
            DrumEngine.stop()
            DrumEngine.releaseRef()
        }
    }

    // Main exercise loop
    LaunchedEffect(isRunning) {
        if (!isRunning) return@LaunchedEffect

        // Countdown
        for (i in 3 downTo 1) {
            countdownNum = i
            delay(800)
        }
        isCountdown = false

        // Start mic listening for onset detection
        val hasPermission = ContextCompat.checkSelfPermission(
            context, Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        var audioRecord: AudioRecord? = null
        if (hasPermission) {
            try {
                val bufSize = AudioRecord.getMinBufferSize(
                    44100, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT
                )
                @Suppress("MissingPermission")
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC, 44100,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT,
                    bufSize.coerceAtLeast(4096)
                )
                audioRecord.startRecording()
            } catch (e: Exception) {
                android.util.Log.w("ChordChallenge", "AudioRecord init failed", e)
                audioRecord = null
            }
        }
        val micBuffer = ShortArray(2048)

        // Start drums or metronome
        if (useDrums) {
            drumJob = scope.launch {
                DrumEngine.playLoop(context, drumStyle, currentBpm, beatsPerMeasure)
            }
        }

        var beat = 0
        var measureCount = 0
        try {
            while (isActive && isRunning) {
                currentBeat = beat % beatsPerMeasure
                currentChordIdx = (measureCount / 1) % chords.size

                // Play tick if not using drums
                if (!useDrums) {
                    withContext(Dispatchers.IO) {
                        tickPlayer.playBeat(currentBpm)
                    }
                } else {
                    delay(60_000L / currentBpm.toLong())
                }

                // Check mic for onset in this beat window
                if (audioRecord != null) {
                    val read = audioRecord.read(micBuffer, 0, micBuffer.size)
                    if (read > 0) {
                        var sumSq = 0.0
                        for (i in 0 until read) {
                            val s = micBuffer[i].toFloat() / Short.MAX_VALUE
                            sumSq += s * s
                        }
                        val rms = kotlin.math.sqrt(sumSq / read)
                        if (rms > 0.005) {
                            onsetDetected = true
                            lastOnsetTime = System.currentTimeMillis()
                            flashColor = CH_GREEN
                        }
                    }
                }

                beat++
                if (beat % beatsPerMeasure == 0) {
                    // End of measure
                    if (onsetDetected) {
                        consecutiveGoodMeasures++
                        onsetDetected = false
                    } else {
                        // Failed - stop exercise
                        flashColor = CH_RED
                        delay(500)
                        isRunning = false
                        // audioRecord released in finally block below
                        onFinish(maxBpmReached)
                        return@LaunchedEffect
                    }

                    measureCount++
                    // Change chord at each measure
                    currentChordIdx = measureCount % chords.size

                    // Check if should advance BPM
                    if (consecutiveGoodMeasures >= measuresToAdvance) {
                        consecutiveGoodMeasures = 0
                        currentBpm += bpmIncrement
                        maxBpmReached = currentBpm

                        // Restart drums at new BPM
                        if (useDrums) {
                            drumJob?.cancel()
                            DrumEngine.stop()
                            drumJob = scope.launch {
                                DrumEngine.playLoop(context, drumStyle, currentBpm, beatsPerMeasure)
                            }
                        }
                    }

                    // Reset flash
                    flashColor = null
                }
            }
        } finally {
            // Always release mic, even if coroutine is cancelled
            try { audioRecord?.stop() } catch (_: Exception) { }
            try { audioRecord?.release() } catch (_: Exception) { }
        }
    }

    // UI
    Column(
        modifier = Modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (isCountdown) {
            Text(
                "$countdownNum",
                color = CH_ACCENT,
                fontSize = 80.sp,
                fontWeight = FontWeight.Black
            )
            Text("Prepárate...", color = Color.White.copy(0.6f), fontSize = 16.sp)
        } else {
            // BPM display
            Text(
                "$currentBpm BPM",
                color = CH_ACCENT,
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "Máximo: $maxBpmReached BPM",
                color = Color.White.copy(0.4f),
                fontSize = 12.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Current chord with flash feedback
            val bgColor by animateColorAsState(
                targetValue = flashColor ?: CH_CARD, label = "flash"
            )
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(bgColor),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (currentChordIdx in chords.indices) {
                        Text(
                            chords[currentChordIdx].displayName,
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Beat indicators
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (b in 0 until beatsPerMeasure) {
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(
                                if (b <= currentBeat && currentBeat >= 0) CH_ACCENT
                                else Color.White.copy(0.15f)
                            )
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Progress bar (measures until next BPM up)
            Text(
                "Compás ${consecutiveGoodMeasures + 1} / $measuresToAdvance",
                color = Color.White.copy(0.5f),
                fontSize = 11.sp
            )
            LinearProgressIndicator(
                progress = (consecutiveGoodMeasures.toFloat() + 1) / measuresToAdvance,
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = CH_GREEN,
                trackColor = Color.White.copy(0.1f),
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Stop button
            Button(
                onClick = {
                    isRunning = false
                    exerciseJob?.cancel()
                    drumJob?.cancel()
                    DrumEngine.stop()
                    onFinish(maxBpmReached)
                },
                colors = ButtonDefaults.buttonColors(containerColor = CH_RED),
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Icon(Icons.Default.Stop, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Parar", fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Phase 4: Results
// ═══════════════════════════════════════════════════════
@Composable
private fun ResultPhase(
    maxBpm: Int,
    chords: List<ChordShape>,
    onRepeat: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text("Resultado", color = CH_ACCENT, fontSize = 24.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            "Has llegado a",
            color = Color.White.copy(0.6f),
            fontSize = 16.sp
        )
        Text(
            "$maxBpm BPM",
            color = CH_GREEN,
            fontSize = 56.sp,
            fontWeight = FontWeight.Black
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Show chord names
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            chords.forEach { chord ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(CH_CARD)
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(chord.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedButton(onClick = onBack) {
                Text("Volver", color = Color.White)
            }
            Button(
                onClick = onRepeat,
                colors = ButtonDefaults.buttonColors(containerColor = CH_GREEN)
            ) {
                Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Repetir", fontWeight = FontWeight.Bold)
            }
        }
    }
}
