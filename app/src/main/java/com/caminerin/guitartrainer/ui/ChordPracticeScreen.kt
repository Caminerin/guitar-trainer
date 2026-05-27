package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.ChordSynth
import com.caminerin.guitartrainer.audio.TickPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val CP_BG = Color(0xFF1A1A1A)
private val CP_TOOLBAR = Color(0xFF1E1E1E)
private val CP_PRIMARY = Color(0xFF7B1FA2)
private val CP_ACTIVE = Color(0xFFFFC107)
private val CP_EMPTY = Color(0xFF2A2A2A)
private val CP_SLOT = Color(0xFF3A3A3A)

// A practice progression is a list of measures, each with N subdivisions and a chord assigned to each
data class ChordSlot(
    val chordId: String? = null
)

data class Measure(
    val subdivisions: List<ChordSlot> = listOf(ChordSlot())
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordPracticeScreen(onBack: () -> Unit, onGoToVisualizer: (() -> Unit)? = null) {
    val context = LocalContext.current
    LaunchedEffect(Unit) { ChordRepository.loadChords(context) }

    LaunchedEffect(Unit) { ScaleChordRepository.load(context) }
    LaunchedEffect(Unit) { SongRepository.load(context) }

    var modeKey by rememberSaveable { mutableStateOf(true) } // true = by tonality, false = free
    var selectedKey by rememberSaveable { mutableIntStateOf(0) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var measureCount by rememberSaveable { mutableIntStateOf(4) }

    // The progression: list of measures, each with subdivisions, each with optional chordId
    val measures = remember { mutableStateListOf<Measure>().also {
        repeat(4) { _ -> it.add(Measure()) }
    } }

    // sync measures count with the selector
    LaunchedEffect(measureCount) {
        while (measures.size < measureCount) measures.add(Measure())
        while (measures.size > measureCount) measures.removeAt(measures.size - 1)
    }

    var showKeyCircle by remember { mutableStateOf(false) }
    var showChordPicker by remember { mutableStateOf<Pair<Int, Int>?>(null) } // measureIdx, subIdx
    var showSongPicker by remember { mutableStateOf(false) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var showBpmSelector by remember { mutableStateOf(false) }
    var showMeasuresSelector by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }
    var showMeasureSubSelector by remember { mutableStateOf<Int?>(null) }


    var isPlaying by remember { mutableStateOf(false) }
    var currentMeasure by remember { mutableIntStateOf(-1) }
    var currentSub by remember { mutableIntStateOf(-1) }

    val tickPlayer = remember { TickPlayer() }
    DisposableEffect(Unit) {
        onDispose { tickPlayer.release() }
    }

    val measuresState = rememberUpdatedState(measures.toList())
    val bpmState = rememberUpdatedState(bpm)

    // Playback loop - LaunchedEffect cancels when isPlaying changes, so the coroutine stops cleanly
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            currentMeasure = -1
            currentSub = -1
            return@LaunchedEffect
        }
        try {
            while (isActive) {
                val mList = measuresState.value
                for ((mi, m) in mList.withIndex()) {
                    for ((si, slot) in m.subdivisions.withIndex()) {
                        currentMeasure = mi
                        currentSub = si
                        slot.chordId?.let { id ->
                            val chord = ChordRepository.getChords().firstOrNull { it.id == id }
                            chord?.let { ChordSynth.playChord(it.frets, 800) }
                        }
                        tickPlayer.tick()
                        val beatMs = 60000L / bpmState.value
                        val subMs = beatMs / m.subdivisions.size.coerceAtLeast(1)
                        delay(subMs.coerceAtLeast(50L))
                    }
                }
            }
        } finally {
            currentMeasure = -1
            currentSub = -1
        }
    }

    // Filter chords by tonality if needed
    val availableChords = remember(modeKey, selectedKey) {
        if (modeKey) {
            val scaleChords = ScaleChordRepository.getChordsForScale("Mayor (J\u00f3nica)", selectedKey)
            if (scaleChords.isNotEmpty()) {
                val allowedRoots = scaleChords.map { AMERICAN_NOTE_NAMES[it.rootSemitone] }.distinct()
                ChordRepository.getChords().filter { it.root in allowedRoots }
            } else {
                val majorScale = ALL_SCALES.firstOrNull { it.name.contains("Mayor (J\u00f3nica)") } ?: ALL_SCALES.first()
                val allowedRoots = majorScale.intervals.map { (selectedKey + it) % 12 }
                    .map { AMERICAN_NOTE_NAMES[it] }
                ChordRepository.getChords().filter { it.root in allowedRoots }
            }
        } else {
            ChordRepository.getChords()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CP_BG)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CP_TOOLBAR)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Mode selector
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (modeKey) CP_PRIMARY.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f))
                        .clickable { showModeSelector = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (modeKey) "Por tonalidad" else "Todos",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Key selector (only if modeKey)
                if (modeKey) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(CHROMATIC_COLORS[selectedKey].copy(alpha = 0.4f))
                            .clickable { showKeyCircle = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(getChromaticNames(selectedKey)[selectedKey], color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // BPM
                BpmToolbarButton(bpm) { showBpmSelector = true }

                // Measures count
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF00BCD4).copy(alpha = 0.25f))
                        .clickable { showMeasuresSelector = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("$measureCount comp.", color = Color(0xFF80DEEA), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // Song picker button (more visible)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (currentSong != null) Color(0xFFFFC107).copy(alpha = 0.4f) else Color(0xFFFFC107).copy(alpha = 0.15f))
                        .clickable { showSongPicker = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        currentSong?.let { "\u266A ${it.title}" } ?: "\u266A Canciones",
                        color = if (currentSong != null) Color(0xFFFFC107) else Color(0xFFFFD54F),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                // Go to visualizer
                if (onGoToVisualizer != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF5C6BC0).copy(alpha = 0.3f))
                            .clickable { onGoToVisualizer() }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("Visualizar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // Play / Stop button
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFFE53935) else Color(0xFF43A047))
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (isPlaying) "Parar" else "Probar",
                        tint = Color.White,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // Song info bar
            currentSong?.let { song ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A1A3A))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("${song.title} \u2022 ${song.artist}",
                            color = Color(0xFFFFC107), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                        Text("Rasgueo: ${song.strumPattern} \u2022 Cejilla: traste ${song.capo} \u2022 ${song.key}",
                            color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { currentSong = null }
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("\u2715", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                    }
                }
            }

            // Progression grid
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(8.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    measures.forEachIndexed { mi, measure ->
                        MeasureCell(
                            measureIndex = mi,
                            measure = measure,
                            isActiveMeasure = mi == currentMeasure,
                            currentSub = if (mi == currentMeasure) currentSub else -1,
                            onSlotClick = { si -> showChordPicker = mi to si },
                            onSubdivide = { showMeasureSubSelector = mi },
                            getChordLabel = { id ->
                                if (id == null) "—"
                                else ChordRepository.getChords().firstOrNull { it.id == id }?.displayName ?: "—"
                            }
                        )
                    }
                }
            }

            // Active chord diagram
            val activeChord = if (currentMeasure >= 0 && currentSub >= 0) {
                measures.getOrNull(currentMeasure)?.subdivisions?.getOrNull(currentSub)?.chordId?.let { id ->
                    ChordRepository.getChords().firstOrNull { it.id == id }
                }
            } else null

            if (activeChord != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF222222))
                        .padding(8.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawSmallChord(activeChord)
                    }
                }
            }
        }

        // Key selector overlay
        if (showKeyCircle) {
            ChromaticCircleOverlay(
                selectedNote = selectedKey,
                onNoteSelected = { selectedKey = it; showKeyCircle = false },
                onDismiss = { showKeyCircle = false }
            )
        }

        // Chord picker overlay
        showChordPicker?.let { (mi, si) ->
            ChordPickerOverlay(
                chords = availableChords,
                onPick = { id ->
                    val m = measures[mi]
                    val newSubs = m.subdivisions.toMutableList()
                    newSubs[si] = ChordSlot(id)
                    measures[mi] = Measure(newSubs)
                    showChordPicker = null
                },
                onPlay = { id ->
                    val chord = ChordRepository.getChords().firstOrNull { it.id == id }
                    chord?.let { ChordSynth.playChord(it.frets, 1500) }
                },
                onDismiss = { showChordPicker = null }
            )
        }

        // Song picker overlay
        if (showSongPicker) {
            SongPickerOverlay(
                songs = SongRepository.getSongs(),
                onPick = { song ->
                    currentSong = song
                    bpm = song.bpmStart
                    measureCount = song.measuresUsed
                    // Auto-fill measures with song chords
                    measures.clear()
                    val allChords = ChordRepository.getChords()
                    song.measures.forEach { measure ->
                        val chordId = findChordIdByName(measure.chordSymbol, allChords)
                        measures.add(Measure(listOf(ChordSlot(chordId))))
                    }
                    // Fill remaining measures if needed
                    while (measures.size < measureCount) measures.add(Measure())
                    modeKey = false
                    showSongPicker = false
                },
                onDismiss = { showSongPicker = false }
            )
        }

        // BPM selector overlay
        if (showBpmSelector) {
            BpmSelectorOverlay(
                bpm = bpm,
                onBpmChange = { bpm = it },
                onDismiss = { showBpmSelector = false }
            )
        }

        // Measures selector overlay
        if (showMeasuresSelector) {
            MeasuresSelectorOverlay(
                count = measureCount,
                onCountChange = { measureCount = it },
                onDismiss = { showMeasuresSelector = false }
            )
        }

        // Mode selector overlay
        if (showModeSelector) {
            ChordModeSelectorOverlay(
                isTonalityMode = modeKey,
                onSelectTonality = { modeKey = true },
                onSelectFree = { modeKey = false },
                onDismiss = { showModeSelector = false }
            )
        }

        // Measure subdivision selector overlay
        showMeasureSubSelector?.let { mi ->
            val measure = measures.getOrNull(mi)
            if (measure != null) {
                MeasureSubdivisionOverlay(
                    current = measure.subdivisions.size,
                    onSelect = { count ->
                        val firstChord = measure.subdivisions.firstOrNull()?.chordId
                        measures[mi] = Measure(List(count) { ChordSlot(firstChord) })
                        showMeasureSubSelector = null
                    },
                    onDismiss = { showMeasureSubSelector = null }
                )
            }
        }
    }
}

private fun findChordIdByName(name: String, chords: List<ChordShape>): String? {
    val normalized = name.trim()
    if (normalized.isEmpty()) return null
    // Try exact match on displayName first
    chords.firstOrNull { it.displayName.equals(normalized, ignoreCase = true) }
        ?.let { return it.id }
    // Try matching root+quality
    chords.filter { matchesChordName(it, normalized) }
        .minByOrNull { it.priority }
        ?.let { return it.id }
    return null
}

private fun matchesChordName(chord: ChordShape, name: String): Boolean {
    val root = extractChordRoot(name)
    val quality = name.removePrefix(root).trim()
    if (chord.root != root && chord.root != enharmonicEquivalent(root)) return false
    val qualityMatch = when (quality.lowercase()) {
        "", "maj", "major" -> chord.qualityLabel == "major"
        "m", "min", "minor" -> chord.qualityLabel == "minor"
        "7" -> chord.qualityLabel == "7"
        "m7", "min7" -> chord.qualityLabel == "m7"
        "maj7" -> chord.qualityLabel == "maj7"
        "dim" -> chord.qualityLabel == "dim"
        "sus2" -> chord.qualityLabel == "sus2"
        "sus4" -> chord.qualityLabel == "sus4"
        "add9", "add(9)" -> chord.qualityLabel.contains("add9")
        else -> chord.qualityLabel.contains(quality, ignoreCase = true)
    }
    return qualityMatch
}

private fun extractChordRoot(name: String): String {
    if (name.isEmpty()) return ""
    val first = name[0]
    if (name.length >= 2 && (name[1] == '#' || name[1] == 'b')) return "${first}${name[1]}"
    return "$first"
}

private fun enharmonicEquivalent(note: String): String {
    return when (note) {
        "Db" -> "C#"; "C#" -> "Db"
        "Eb" -> "D#"; "D#" -> "Eb"
        "Gb" -> "F#"; "F#" -> "Gb"
        "Ab" -> "G#"; "G#" -> "Ab"
        "Bb" -> "A#"; "A#" -> "Bb"
        else -> note
    }
}

@Composable
private fun MeasureCell(
    measureIndex: Int,
    measure: Measure,
    isActiveMeasure: Boolean,
    currentSub: Int,
    onSlotClick: (Int) -> Unit,
    onSubdivide: () -> Unit,
    getChordLabel: (String?) -> String
) {
    Column(
        modifier = Modifier
            .width(180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (isActiveMeasure) CP_ACTIVE.copy(alpha = 0.15f) else CP_EMPTY)
            .padding(6.dp)
    ) {
        // Header: compás number + subdivide button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Comp\u00e1s ${measureIndex + 1}",
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 11.sp, fontWeight = FontWeight.Bold)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(CP_PRIMARY.copy(alpha = 0.3f))
                    .clickable { onSubdivide() }
                    .padding(horizontal = 8.dp, vertical = 3.dp)
            ) {
                Text("\u00f7${measure.subdivisions.size}",
                    color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
        // Slots row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            measure.subdivisions.forEachIndexed { si, slot ->
                val isCurrent = si == currentSub
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) CP_ACTIVE else CP_SLOT)
                        .clickable { onSlotClick(si) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        getChordLabel(slot.chordId),
                        color = if (isCurrent) Color.Black else Color.White,
                        fontSize = if (measure.subdivisions.size <= 2) 18.sp else 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ChordPickerOverlay(
    chords: List<ChordShape>,
    onPick: (String) -> Unit,
    onPlay: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var selectedQuality by remember { mutableStateOf<String?>(null) }
    var selectedLevel by remember { mutableStateOf<String?>(null) }

    val filtered = chords.filter { chord ->
        (selectedQuality == null || chord.quality == selectedQuality) &&
        (selectedLevel == null || chord.level == selectedLevel)
    }

    val grouped = filtered.groupBy { it.displayName }
        .toList()
        .sortedBy { (name, _) -> name }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .fillMaxHeight(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E1E))
                .clickable(enabled = false) {}
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Elige acorde", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Quality filter chips
            Text("Tipo:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedQuality == null) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedQuality = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Todos", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                ChordQuality.entries.forEach { q ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedQuality == q.csvValue) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedQuality = if (selectedQuality == q.csvValue) null else q.csvValue }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(q.displayName, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Level filter chips
            Text("Nivel:", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
            Spacer(modifier = Modifier.height(4.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedLevel == null) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedLevel = null }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Todos", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                ChordLevel.entries.forEach { lvl ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedLevel == lvl.csvValue) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedLevel = if (selectedLevel == lvl.csvValue) null else lvl.csvValue }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(lvl.displayName, color = Color.White, fontSize = 12.sp)
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            // Chord grid
            Box(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(rememberScrollState())) {
                if (grouped.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        Text("No hay acordes con estos filtros", color = Color.White.copy(alpha = 0.5f), fontSize = 14.sp)
                    }
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        grouped.forEach { (name, list) ->
                            val first = list.minByOrNull { it.priority } ?: list.first()
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(CP_PRIMARY.copy(alpha = 0.25f))
                                    .padding(2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .clickable { onPick(first.id) }
                                        .padding(horizontal = 14.dp, vertical = 10.dp)
                                ) {
                                    Text(name, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { onPlay(first.id) },
                                    modifier = Modifier.size(36.dp)
                                ) {
                                    Icon(Icons.Default.VolumeUp, "Probar", tint = Color.White, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSmallChord(chord: ChordShape) {
    val w = size.width
    val h = size.height
    val frets = chord.frets
    if (frets.size < 6) return

    val minFret = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0
    val maxFret = frets.filterNotNull().filter { it > 0 }.maxOrNull() ?: 0
    val startFret = if (maxFret <= 4) 0 else (minFret - 1).coerceAtLeast(0)
    val fretsToShow = 5.coerceAtLeast(maxFret - startFret + 1)

    val topPad = h * 0.1f
    val bottomPad = h * 0.05f
    val leftPad = w * 0.06f
    val rightPad = w * 0.04f
    val fbTop = topPad
    val fbBottom = h - bottomPad
    val fbHeight = fbBottom - fbTop
    val stringSpacing = fbHeight / 7f
    val fbLeft = leftPad
    val fbRight = w - rightPad
    val fretWidth = (fbRight - fbLeft) / fretsToShow

    drawRoundRect(
        color = Color(0xFF3E2415),
        topLeft = Offset(fbLeft, fbTop - 4f),
        size = Size(fbRight - fbLeft, fbHeight + 8f),
        cornerRadius = CornerRadius(4f)
    )

    if (startFret == 0) {
        drawRect(color = Color(0xFFF0EAD6), topLeft = Offset(fbLeft, fbTop - 6f), size = Size(8f, fbHeight + 12f))
    }

    for (fret in 1..fretsToShow) {
        val x = fbLeft + fret * fretWidth
        drawLine(Color(0xFFBBBBBB), Offset(x, fbTop - 2f), Offset(x, fbBottom + 2f), strokeWidth = 1.5f)
    }

    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawLine(Color(0xFFD0C4B0), Offset(fbLeft, y), Offset(fbRight, y), strokeWidth = 1.5f)
    }

    val chordColor = Color(0xFF7B1FA2)
    val noteRadius = (stringSpacing * 0.35f).coerceIn(8f, 22f)
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 22f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    for (s in 0 until 6) {
        val fretVal = frets[s]
        val y = fbTop + stringSpacing * (6 - s)
        when {
            fretVal == null -> {
                labelPaint.color = android.graphics.Color.argb(180, 220, 80, 80)
                drawContext.canvas.nativeCanvas.drawText("X", fbLeft * 0.5f, y + 8f, labelPaint)
                labelPaint.color = android.graphics.Color.WHITE
            }
            fretVal == 0 -> {
                val cx = fbLeft * 0.5f
                val noteColor = Color(0xFF43A047)
                drawCircle(Color(0x55000000), noteRadius + 2f, Offset(cx + 1f, y + 1.5f))
                drawCircle(noteColor, noteRadius, Offset(cx, y))
                drawCircle(Color(0x44000000), noteRadius, Offset(cx, y), style = Stroke(1.5f))
            }
            else -> {
                val displayPos = fretVal - startFret
                if (displayPos in 1..fretsToShow) {
                    val cx = fbLeft + (displayPos - 0.5f) * fretWidth
                    drawCircle(chordColor, noteRadius, Offset(cx, y))
                    drawCircle(Color(0x44000000), noteRadius, Offset(cx, y), style = Stroke(1.5f))
                }
            }
        }
    }

    val namePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 30f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(chord.displayName, w / 2f, fbTop - 4f, namePaint)
}
