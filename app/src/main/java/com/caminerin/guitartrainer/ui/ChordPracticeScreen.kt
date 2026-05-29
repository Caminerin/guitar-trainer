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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
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
import com.caminerin.guitartrainer.audio.StrumEngine
import com.caminerin.guitartrainer.audio.StrumPatternLibrary


import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
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
    val chordId: String? = null,
    val strumDirection: String = "D" // D=down, U=up, -=silent
)

data class Measure(
    val subdivisions: List<ChordSlot> = listOf(ChordSlot()),
    val strumPattern: String? = null
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordPracticeScreen(onBack: () -> Unit, onGoToVisualizer: (() -> Unit)? = null) {
    val context = LocalContext.current
    var dataLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ChordRepository.loadChords(context)
        ScaleChordRepository.load(context)
        SongRepository.load(context)
        ChordSynth.init(context)
        StrumEngine.init(context)
        dataLoaded = true
    }

    val prefs = remember { context.getSharedPreferences("chord_progressions", android.content.Context.MODE_PRIVATE) }

    var modeKey by rememberSaveable { mutableStateOf(true) } // true = by tonality, false = free
    var selectedKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedScaleName by rememberSaveable { mutableStateOf("Mayor (Jónica)") }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var beatsPerMeasure by rememberSaveable { mutableIntStateOf(4) }
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
    var showScaleSelector by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showChordPicker by remember { mutableStateOf<Pair<Int, Int>?>(null) } // measureIdx, subIdx
    var showSongPicker by remember { mutableStateOf(false) }
    var currentSong by remember { mutableStateOf<Song?>(null) }
    var showBpmSelector by remember { mutableStateOf(false) }
    var showMeasuresSelector by remember { mutableStateOf(false) }
    var showModeSelector by remember { mutableStateOf(false) }
    var showMeasureSubSelector by remember { mutableStateOf<Int?>(null) }
    var showBeatsSelector by remember { mutableStateOf(false) }
    var selectedSlotChordId by remember { mutableStateOf<String?>(null) }
    var selectedPattern by remember { mutableStateOf(StrumPatternLibrary.default) }


    var isPlaying by remember { mutableStateOf(false) }

    var useTargetBpm by remember { mutableStateOf(false) }
    var currentMeasure by remember { mutableIntStateOf(-1) }
    var currentSub by remember { mutableIntStateOf(-1) }

    // Section-based playback state
    var currentSectionIdx by remember { mutableIntStateOf(-1) }
    var loopSectionIdx by remember { mutableIntStateOf(-1) } // -1 = play all, >= 0 = loop that section


    DisposableEffect(Unit) {
        onDispose { StrumEngine.mute() }
    }

    val measuresState = rememberUpdatedState(measures.toList())
    val bpmState = rememberUpdatedState(bpm)
    val useTargetBpmState = rememberUpdatedState(useTargetBpm)
    val currentSongState = rememberUpdatedState(currentSong)
    val loopSectionState = rememberUpdatedState(loopSectionIdx)


    val songHasSwing = currentSong?.swing == true ||
        currentSong?.feel?.contains("shuffle", ignoreCase = true) == true ||
        currentSong?.feel?.contains("ternario", ignoreCase = true) == true ||
        currentSong?.feel?.contains("swing", ignoreCase = true) == true
    val humanRng = remember { java.util.Random() }
    val selectedPatternState = rememberUpdatedState(selectedPattern)

    // Pattern-based playback loop with accents, swing, humanization, section loop, speed trainer
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            currentMeasure = -1
            currentSub = -1
            currentSectionIdx = -1
            return@LaunchedEffect
        }
        try {
            while (isActive) {
                val mList = measuresState.value
                val song = currentSongState.value
                val loopIdx = loopSectionState.value

                val effectiveBpm = when {
                    useTargetBpmState.value && song != null -> song.bpmTarget
                    else -> bpmState.value
                }

                val hasSwing = song?.swing == true || (song?.feel?.let {
                    it.contains("shuffle", ignoreCase = true) ||
                    it.contains("ternario", ignoreCase = true) ||
                    it.contains("swing", ignoreCase = true)
                } ?: false)
                val swingRatio = if (hasSwing) 0.67f else 0.5f

                // Determine which measures to play (section loop or all)
                val measuresToPlay: List<IndexedValue<Measure>>
                if (loopIdx >= 0 && song != null && song.sections.isNotEmpty()) {
                    // Find flat measure range for the looped section
                    var flatIdx = 0
                    var startFlat = 0
                    var endFlat = 0
                    for ((si, sec) in song.sections.withIndex()) {
                        if (si == loopIdx) startFlat = flatIdx
                        flatIdx += sec.measures.size
                        if (si == loopIdx) { endFlat = flatIdx; break }
                    }
                    currentSectionIdx = loopIdx
                    measuresToPlay = mList.withIndex().toList().filter { it.index in startFlat until endFlat }
                } else {
                    measuresToPlay = mList.withIndex().toList()
                }

                for ((mi, m) in measuresToPlay) {
                    // Track current section index
                    if (song != null && song.sections.isNotEmpty()) {
                        var flatIdx = 0
                        for ((si, sec) in song.sections.withIndex()) {
                            if (mi < flatIdx + sec.measures.size) {
                                currentSectionIdx = si; break
                            }
                            flatIdx += sec.measures.size
                        }
                    }

                    val numSubs = m.subdivisions.size.coerceAtLeast(1)
                    val pat = selectedPatternState.value
                    for ((si, slot) in m.subdivisions.withIndex()) {
                        if (!isActive) break
                        currentMeasure = mi
                        currentSub = si
                        val beatMs = 60000L / effectiveBpm
                        val measureMs = beatMs * beatsPerMeasure
                        val baseSubMs = measureMs.toFloat() / numSubs

                        val subsPerBeat = numSubs / beatsPerMeasure.coerceAtLeast(1)
                        val subWithinBeat = if (subsPerBeat > 1) si % subsPerBeat else 0
                        val swingSubMs = if (subsPerBeat == 2) {
                            if (subWithinBeat == 0) (baseSubMs * 2f * swingRatio).toLong()
                            else (baseSubMs * 2f * (1f - swingRatio)).toLong()
                        } else baseSubMs.toLong()

                        val jitterMs = (humanRng.nextGaussian() * 5.0).toLong().coerceIn(-8L, 8L)
                        val finalSubMs = (swingSubMs + jitterMs).coerceAtLeast(30L)

                        // Resolve stroke from pattern when song has no per-slot direction,
                        // or fall back to the slot's own direction
                        val patStroke = pat.strokes.getOrNull(si % pat.strokes.size)
                        val strokeDir = slot.strumDirection
                        val usePatternStroke = (song == null && patStroke != null)

                        val effectiveDir: StrumEngine.Direction
                        val effectiveVelocity: Float
                        if (usePatternStroke && patStroke != null) {
                            effectiveDir = patStroke.direction
                            val accentBoost = if (patStroke.accent) 1.0f else 0.85f
                            val ghostDamp = if (patStroke.ghost) 0.25f else 1.0f
                            effectiveVelocity = patStroke.velocity * accentBoost * ghostDamp
                        } else {
                            effectiveDir = when {
                                strokeDir == "-" -> StrumEngine.Direction.REST
                                strokeDir == "x" || strokeDir == "X" -> StrumEngine.Direction.MUTE
                                strokeDir == "U" -> StrumEngine.Direction.UP
                                else -> StrumEngine.Direction.DOWN
                            }
                            effectiveVelocity = when {
                                effectiveDir == StrumEngine.Direction.REST -> 0f
                                si == 0 -> 1.0f
                                subsPerBeat > 1 && subWithinBeat == 0 -> 0.85f
                                effectiveDir == StrumEngine.Direction.MUTE -> 0.5f
                                effectiveDir == StrumEngine.Direction.UP -> 0.55f
                                else -> 0.7f
                            }
                        }

                        if (effectiveDir != StrumEngine.Direction.REST) {
                            slot.chordId?.let { id ->
                                val chord = ChordRepository.getChords().firstOrNull { it.id == id }
                                var nextStrum = numSubs
                                for (ns in (si + 1) until numSubs) {
                                    if (m.subdivisions[ns].strumDirection != "-") {
                                        nextStrum = ns; break
                                    }
                                }
                                val durationMs = (baseSubMs * (nextStrum - si) + 200).toInt().coerceAtLeast(400)

                                chord?.let {
                                    StrumEngine.strum(
                                        frets = it.frets,
                                        direction = effectiveDir,
                                        velocity = effectiveVelocity,
                                        durationMs = durationMs
                                    )
                                }
                            }
                        }
                        delay(finalSubMs)
                    }
                }


            }
        } finally {
            StrumEngine.mute()
            ChordSynth.stop()
            currentMeasure = -1
            currentSub = -1
            currentSectionIdx = -1
        }
    }

    // Filter chords by tonality if needed — match root AND quality (diatonic harmony)
    val availableChords = remember(modeKey, selectedKey, selectedScaleName, dataLoaded) {
        if (modeKey) {
            val offset = getRelativeMajorOffset(selectedScaleName)
            val scaleChords = ScaleChordRepository.getChordsForScale(selectedScaleName, selectedKey, offset)
            val allowedKeys = scaleChords.map { sc ->
                sc.rootSemitone to normalizeScaleQuality(sc.quality)
            }.toSet()
            ChordRepository.getChords().filter { chord ->
                val chordRootSemitone = AMERICAN_NOTE_NAMES.indexOf(chord.root)
                val chordQualityNorm = normalizeChordQuality(chord.qualityLabel)
                allowedKeys.any { (rootSt, qualNorm) ->
                    rootSt == chordRootSemitone && qualNorm == chordQualityNorm
                }
            }
        } else {
            ChordRepository.getChords()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(CP_BG)) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Toolbar
            FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CP_TOOLBAR)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                // Key selector (Raíz) — always visible
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CHROMATIC_COLORS[selectedKey].copy(alpha = 0.4f))
                        .clickable { showKeyCircle = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(getChromaticNames(selectedKey)[selectedKey], color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Catálogo selector
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (modeKey) CP_PRIMARY.copy(alpha = 0.6f) else Color.White.copy(alpha = 0.15f))
                        .clickable { showModeSelector = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(if (modeKey) "Catálogo" else "Todos",
                        color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (modeKey) {
                    // Scale selector
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF5C6BC0).copy(alpha = 0.25f))
                            .clickable { showScaleSelector = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        val shortName = selectedScaleName.replace(" (Jónica)", "").replace(" (Eólica)", "")
                        Text(shortName, color = Color(0xFFB0BEC5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }

                    // Info button
                    IconButton(onClick = { showInfo = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.Info, "Info", tint = Color(0xFF90CAF9), modifier = Modifier.size(18.dp))
                    }
                }

                // BPM
                BpmToolbarButton(bpm) { showBpmSelector = true }

                // Beats per measure
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C4DFF).copy(alpha = 0.25f))
                        .clickable { showBeatsSelector = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("$beatsPerMeasure/4", color = Color(0xFFB388FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

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

                // Play / Stop button
                IconButton(
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFFE53935) else Color(0xFF43A047))
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        if (isPlaying) "Parar" else "Probar",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            // Song info bar
            currentSong?.let { song ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF2A1A3A))
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${song.title} \u2022 ${song.artist}",
                                color = Color(0xFFFFC107), fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            val feelLabel = if (songHasSwing) " \u2022 Swing" else ""
                            Text("Cejilla: traste ${song.capo} \u2022 ${song.key}$feelLabel",
                                color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp, maxLines = 1)
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.White.copy(alpha = 0.08f))
                                .clickable {
                                    currentSong = null
                                    loopSectionIdx = -1
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("\u2715", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                        }
                    }

                    // Section bar — scrollable row showing sections with loop toggle
                    if (song.sections.isNotEmpty()) {
                        val sectionListState = rememberLazyListState()
                        LazyRow(
                            state = sectionListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(song.sections.size) { idx ->
                                val sec = song.sections[idx]
                                val isActive = idx == currentSectionIdx
                                val isLooped = idx == loopSectionIdx
                                val sectionLabel = when (sec.name) {
                                    "verso" -> "Verso"
                                    "estribillo" -> "Estrib."
                                    "puente" -> "Puente"
                                    "final" -> "Final"
                                    else -> sec.name.replaceFirstChar { it.uppercase() }
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(
                                            when {
                                                isLooped -> Color(0xFFFF9800).copy(alpha = 0.5f)
                                                isActive -> Color(0xFF7B1FA2).copy(alpha = 0.5f)
                                                else -> Color.White.copy(alpha = 0.08f)
                                            }
                                        )
                                        .clickable {
                                            loopSectionIdx = if (loopSectionIdx == idx) -1 else idx
                                        }
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text(
                                        "$sectionLabel (${sec.measures.size})" + if (isLooped) " \u21BB" else "",
                                        color = when {
                                            isLooped -> Color(0xFFFFCC80)
                                            isActive -> Color(0xFFCE93D8)
                                            else -> Color.White.copy(alpha = 0.5f)
                                        },
                                        fontSize = 10.sp,
                                        fontWeight = if (isActive || isLooped) FontWeight.Bold else FontWeight.Normal
                                    )
                                }
                            }
                        }
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
                            onSlotClick = { si ->
                                val chordId = measure.subdivisions.getOrNull(si)?.chordId
                                if (chordId != null) selectedSlotChordId = chordId
                                showChordPicker = mi to si
                            },
                            onSubdivide = { showMeasureSubSelector = mi },
                            getChordLabel = { id ->
                                if (id == null) "—"
                                else {
                                    val chord = ChordRepository.getChords().firstOrNull { it.id == id }
                                    if (chord != null && modeKey) {
                                        val offset = getRelativeMajorOffset(selectedScaleName)
                                        chord.getDisplayName(selectedKey, offset)
                                    } else chord?.displayName ?: "—"
                                }
                            }
                        )
                    }
                }
            }

            // Active chord diagram (playing chord or selected chord)
            val activeChord = if (currentMeasure >= 0 && currentSub >= 0) {
                measures.getOrNull(currentMeasure)?.subdivisions?.getOrNull(currentSub)?.chordId?.let { id ->
                    ChordRepository.getChords().firstOrNull { it.id == id }
                }
            } else {
                selectedSlotChordId?.let { id ->
                    ChordRepository.getChords().firstOrNull { it.id == id }
                }
            }

            if (activeChord != null) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp)
                        .background(Color(0xFF222222))
                        .padding(8.dp)
                ) {
                    val chordTonalRoot = if (modeKey) selectedKey else -1
                    val chordOffset = if (modeKey) getRelativeMajorOffset(selectedScaleName) else 0
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawSmallChord(activeChord, chordTonalRoot, chordOffset)
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

        // Scale selector overlay
        if (showScaleSelector) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showScaleSelector = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth(0.8f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable(enabled = false) {}
                        .padding(16.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Escala", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    ALL_SCALES.forEach { scaleEntry ->
                        val name = scaleEntry.name
                        val isSelected = name == selectedScaleName ||
                            name.lowercase().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u") ==
                            selectedScaleName.lowercase().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u")
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) CP_PRIMARY.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable { selectedScaleName = name; showScaleSelector = false }
                                .padding(horizontal = 12.dp, vertical = 10.dp)
                        ) {
                            Text(name, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                                fontSize = 15.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                        }
                    }
                }
            }
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
                    chord?.let {
                        StrumEngine.strum(it.frets, StrumEngine.Direction.DOWN, 0.85f, 1500)
                    }
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
                    bpm = song.bpmTarget
                    useTargetBpm = true
                    loopSectionIdx = -1
                    val songBeats = song.meter.split("/").firstOrNull()?.trim()?.toIntOrNull() ?: 4
                    beatsPerMeasure = songBeats
                    val subdivs = song.subdivisions.coerceIn(1, 8)
                    measures.clear()
                    val allChords = ChordRepository.getChords()

                    // Load from JSON sections
                    for (section in song.sections) {
                        val pattern = section.pattern
                        for (secMeasure in section.measures) {
                            val slots = mutableListOf<ChordSlot>()
                            val perSubChords = secMeasure.chordsPerSub
                            for (s in 0 until subdivs) {
                                val chordName = if (perSubChords.isNotEmpty())
                                    perSubChords.getOrElse(s) { perSubChords.lastOrNull().orEmpty() }
                                else
                                    secMeasure.chords.firstOrNull().orEmpty()
                                val chordId = findOpenChordIdByName(chordName, allChords)
                                val direction = if (s < pattern.size) {
                                    when (pattern[s].type) {
                                        "down" -> "D"
                                        "up" -> "U"
                                        "mute" -> "x"
                                        else -> "-"
                                    }
                                } else "D"
                                slots.add(ChordSlot(chordId, direction))
                            }
                            val patternStr = pattern.joinToString(" ") {
                                when (it.type) { "down" -> "D"; "up" -> "U"; "mute" -> "x"; else -> "-" }
                            }
                            measures.add(Measure(slots, strumPattern = patternStr.takeIf { it.isNotBlank() }))
                        }
                    }
                    measureCount = measures.size.coerceAtLeast(1)
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

        // Beats per measure selector overlay
        if (showBeatsSelector) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.7f))
                    .clickable { showBeatsSelector = false },
                contentAlignment = Alignment.Center
            ) {
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFF2A2A2A))
                        .clickable(enabled = false) {}
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Compás", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    listOf(2, 3, 4, 5, 6, 7, 8).forEach { beats ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (beatsPerMeasure == beats) CP_PRIMARY.copy(alpha = 0.5f) else Color.Transparent)
                                .clickable { beatsPerMeasure = beats; showBeatsSelector = false }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("$beats/4", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
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

        // Scale info sheet overlay
        if (showInfo && modeKey) {
            val infoScale = ALL_SCALES.find {
                it.name.lowercase().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u") ==
                selectedScaleName.lowercase().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u")
            }
            if (infoScale != null) {
                ScaleInfoSheet(
                    rootNote = selectedKey,
                    scale = infoScale,
                    onDismiss = { showInfo = false }
                )
            } else {
                showInfo = false
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

private fun findOpenChordIdByName(name: String, chords: List<ChordShape>): String? {
    val normalized = name.trim()
    if (normalized.isEmpty()) return null
    val matches = chords.filter { matchesChordName(it, normalized) }
    if (matches.isEmpty()) return null
    // Prefer open-position chords: 4+ strings, lowest max_fret, most strings, then priority
    return matches.sortedWith(compareBy(
        { if (it.frets.count { f -> f != null } >= 4) 0 else 1 },
        { it.maxFret },
        { -(it.frets.count { f -> f != null }) },
        { it.priority }
    )).first().id
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
                Text("Subdiv. ${measure.subdivisions.size}",
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
        // Strum direction indicators per subdivision
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            measure.subdivisions.forEach { slot ->
                val dirColor = when (slot.strumDirection) {
                    "D" -> Color(0xFF81C784) // green
                    "U" -> Color(0xFF64B5F6) // blue
                    else -> Color.White.copy(alpha = 0.3f) // dim for silence
                }
                val dirSymbol = when (slot.strumDirection) {
                    "D" -> "\u2193" // ↓
                    "U" -> "\u2191" // ↑
                    else -> "\u2014" // —
                }
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Text(dirSymbol, color = dirColor, fontSize = 14.sp, fontWeight = FontWeight.Bold)
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
    var selectedGroup by remember { mutableStateOf<QualityGroup?>(null) }
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var showLevelPicker by remember { mutableStateOf(false) }
    var showTypePicker by remember { mutableStateOf(false) }

    val filtered = chords.filter { chord ->
        val groupMatch = selectedGroup == null || ChordQuality.entries.any { q ->
            q.csvValue == chord.quality && q.group == selectedGroup
        }
        val levelMatch = selectedLevel == null || chord.level == selectedLevel
        groupMatch && levelMatch
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

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CP_PRIMARY.copy(alpha = 0.6f))
                        .clickable { showLevelPicker = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val levelLabel = if (selectedLevel == null) "Todos"
                        else ChordLevel.entries.firstOrNull { it.csvValue == selectedLevel }?.displayName ?: "Todos"
                    Text("Nivel: $levelLabel", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF5C6BC0).copy(alpha = 0.6f))
                        .clickable { showTypePicker = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    val typeLabel = selectedGroup?.displayName ?: "Todos"
                    Text("Tipo: $typeLabel", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

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

    // Level picker modal
    if (showLevelPicker) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { showLevelPicker = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Nivel", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedLevel == null) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedLevel = null; showLevelPicker = false }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("Todos", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                ChordLevel.entries.filter { it != ChordLevel.ALL }.forEach { lvl ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedLevel == lvl.csvValue) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedLevel = lvl.csvValue; showLevelPicker = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(lvl.displayName, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Type picker modal
    if (showTypePicker) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { showTypePicker = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.7f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("Tipo de acorde", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedGroup == null) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedGroup = null; showTypePicker = false }
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text("Todos", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }
                QualityGroup.entries.forEach { grp ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selectedGroup == grp) CP_PRIMARY else Color.White.copy(alpha = 0.08f))
                            .clickable { selectedGroup = grp; showTypePicker = false }
                            .padding(horizontal = 14.dp, vertical = 10.dp)
                    ) {
                        Text(grp.displayName, color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        }
    }
}

private fun DrawScope.drawSmallChord(chord: ChordShape, tonalRoot: Int = -1, relativeMajorOffset: Int = 0) {
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
    drawContext.canvas.nativeCanvas.drawText(chord.getDisplayName(tonalRoot, relativeMajorOffset), w / 2f, fbTop - 4f, namePaint)
}

private fun normalizeScaleQuality(quality: String): String {
    return when (quality.trim()) {
        "" -> "major"
        "m" -> "minor"
        "dim" -> "dim"
        "aug" -> "aug"
        "7" -> "7"
        "m7" -> "m7"
        "maj7" -> "maj7"
        "m7b5" -> "m7b5"
        "dim7" -> "dim7"
        "mMaj7" -> "mMaj7"
        else -> quality.trim().lowercase()
    }
}

private fun normalizeChordQuality(qualityLabel: String): String {
    return when (qualityLabel.trim()) {
        "major" -> "major"
        "minor" -> "minor"
        "dominant7" -> "7"
        "diminished" -> "dim"
        "augmented" -> "aug"
        "7", "dom7" -> "7"
        "maj7" -> "maj7"
        "m7" -> "m7"
        "m7b5", "half_diminished7" -> "m7b5"
        "dim7", "diminished7" -> "dim7"
        "sus2" -> "sus2"
        "sus4" -> "sus4"
        else -> qualityLabel.trim().lowercase()
    }
}
