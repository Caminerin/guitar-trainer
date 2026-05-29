package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
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
import androidx.compose.runtime.rememberUpdatedState
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
import com.caminerin.guitartrainer.audio.StrumEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val SP_BG = Color(0xFF121212)
private val SP_TOP_BAR = Color(0xFF1A1A2E)
private val SP_ACCENT = Color(0xFFFFC107)
private val SP_SECTION_ACTIVE = Color(0xFF7B1FA2)

/**
 * Song-based chord practice player.
 * Shows current chord large + fretboard, next chord, strum pattern animated,
 * section loop, real-time BPM control.
 */
@Composable
fun ChordPracticeScreen(onBack: () -> Unit, onGoToVisualizer: (() -> Unit)? = null) {
    val context = LocalContext.current
    var dataLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        ChordRepository.loadChords(context)
        SongRepository.load(context)
        StrumEngine.init(context)
        dataLoaded = true
    }

    var currentSong by remember { mutableStateOf<Song?>(null) }
    var showSongPicker by remember { mutableStateOf(true) }
    var isPlaying by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(80) }
    var loopSectionIdx by remember { mutableIntStateOf(-1) }

    // Playback position tracking
    var currentFlatIdx by remember { mutableIntStateOf(-1) }
    var currentStrokeIdx by remember { mutableIntStateOf(-1) }
    var currentSectionIdx by remember { mutableIntStateOf(-1) }

    DisposableEffect(Unit) {
        onDispose { StrumEngine.mute() }
    }

    // Build flat playback slots from current song
    val playbackSlots = remember(currentSong) {
        val song = currentSong ?: return@remember emptyList<PlaybackSlot>()
        val allChords = ChordRepository.getChords()
        val subdivs = song.subdivisions.coerceIn(1, 8)
        val slots = mutableListOf<PlaybackSlot>()
        for ((secIdx, section) in song.sections.withIndex()) {
            val pattern = section.pattern
            for ((measIdx, secMeasure) in section.measures.withIndex()) {
                for (s in 0 until subdivs) {
                    val chordName = if (secMeasure.chordsPerSub.isNotEmpty())
                        secMeasure.chordsPerSub.getOrElse(s) { secMeasure.chordsPerSub.lastOrNull().orEmpty() }
                    else
                        secMeasure.chords.firstOrNull().orEmpty()
                    val chordId = findOpenChordIdByName(chordName, allChords)
                    val strokeInfo = if (s < pattern.size) pattern[s] else null
                    val direction = when (strokeInfo?.type) {
                        "down" -> "D"
                        "up" -> "U"
                        "mute" -> "x"
                        else -> "-"
                    }
                    val velocity = strokeInfo?.vel ?: 0.7f
                    slots.add(PlaybackSlot(
                        chordName = chordName,
                        chordId = chordId,
                        strumDirection = direction,
                        sectionIdx = secIdx,
                        measureInSection = measIdx,
                        subIdx = s,
                        velocity = velocity
                    ))
                }
            }
        }
        slots
    }

    // Compute section ranges for looping
    val sectionRanges = remember(currentSong, playbackSlots) {
        val song = currentSong ?: return@remember emptyList<IntRange>()
        val ranges = mutableListOf<IntRange>()
        var offset = 0
        val subdivs = song.subdivisions.coerceIn(1, 8)
        for (section in song.sections) {
            val count = section.measures.size * subdivs
            ranges.add(offset until (offset + count))
            offset += count
        }
        ranges
    }

    val bpmState = rememberUpdatedState(bpm)
    val loopState = rememberUpdatedState(loopSectionIdx)

    // Playback loop
    LaunchedEffect(isPlaying, playbackSlots) {
        if (!isPlaying || playbackSlots.isEmpty()) {
            currentFlatIdx = -1
            currentStrokeIdx = -1
            currentSectionIdx = -1
            return@LaunchedEffect
        }
        val song = currentSong ?: return@LaunchedEffect
        val subdivs = song.subdivisions.coerceIn(1, 8)
        val beatsPerMeasure = song.meter.split("/").firstOrNull()?.trim()?.toIntOrNull() ?: 4
        val hasSwing = song.swing || song.feel.let {
            it.contains("shuffle", ignoreCase = true) ||
            it.contains("ternario", ignoreCase = true) ||
            it.contains("swing", ignoreCase = true)
        }
        val swingRatio = if (hasSwing) 0.67f else 0.5f
        val humanRng = java.util.Random()

        try {
            while (isActive) {
                val loopIdx = loopState.value
                val slotsToPlay = if (loopIdx >= 0 && loopIdx < sectionRanges.size) {
                    val range = sectionRanges[loopIdx]
                    playbackSlots.slice(range).mapIndexed { i, slot -> IndexedValue(range.first + i, slot) }
                } else {
                    playbackSlots.withIndex().toList()
                }

                for ((flatIdx, slot) in slotsToPlay) {
                    if (!isActive) break
                    currentFlatIdx = flatIdx
                    currentStrokeIdx = slot.subIdx
                    currentSectionIdx = slot.sectionIdx

                    val effectiveBpm = bpmState.value
                    val beatMs = 60000L / effectiveBpm
                    val measureMs = beatMs * beatsPerMeasure
                    val baseSubMs = measureMs.toFloat() / subdivs

                    val subsPerBeat = subdivs / beatsPerMeasure.coerceAtLeast(1)
                    val subWithinBeat = if (subsPerBeat > 1) slot.subIdx % subsPerBeat else 0
                    val swingSubMs = if (subsPerBeat == 2) {
                        if (subWithinBeat == 0) (baseSubMs * 2f * swingRatio).toLong()
                        else (baseSubMs * 2f * (1f - swingRatio)).toLong()
                    } else baseSubMs.toLong()

                    val jitterMs = (humanRng.nextGaussian() * 4.0).toLong().coerceIn(-6L, 6L)
                    val finalSubMs = (swingSubMs + jitterMs).coerceAtLeast(30L)

                    // Play strum
                    val dir = when (slot.strumDirection) {
                        "D" -> StrumEngine.Direction.DOWN
                        "U" -> StrumEngine.Direction.UP
                        "x", "X" -> StrumEngine.Direction.MUTE
                        else -> StrumEngine.Direction.REST
                    }
                    if (dir != StrumEngine.Direction.REST) {
                        slot.chordId?.let { id ->
                            val chord = ChordRepository.getChords().firstOrNull { it.id == id }
                            chord?.let {
                                val durationMs = (baseSubMs * 2 + 200).toInt().coerceAtLeast(400)
                                StrumEngine.strum(
                                    frets = it.frets,
                                    direction = dir,
                                    velocity = slot.velocity,
                                    durationMs = durationMs
                                )
                            }
                        }
                    }
                    delay(finalSubMs)
                }
            }
        } finally {
            StrumEngine.mute()
            currentFlatIdx = -1
            currentStrokeIdx = -1
            currentSectionIdx = -1
        }
    }

    // Derive current/next chord info
    val currentSlot = if (currentFlatIdx >= 0) playbackSlots.getOrNull(currentFlatIdx) else null
    val currentChordName = currentSlot?.chordName ?: ""
    val currentChord = currentSlot?.chordId?.let { id ->
        ChordRepository.getChords().firstOrNull { it.id == id }
    }

    // Find next different chord
    val nextChordInfo = remember(currentFlatIdx, playbackSlots) {
        if (currentFlatIdx < 0 || playbackSlots.isEmpty()) return@remember Pair("", 0)
        val curName = playbackSlots[currentFlatIdx].chordName
        var slotsUntilChange = 0
        for (i in (currentFlatIdx + 1) until playbackSlots.size) {
            slotsUntilChange++
            if (playbackSlots[i].chordName != curName) {
                return@remember Pair(playbackSlots[i].chordName, slotsUntilChange)
            }
        }
        Pair("", slotsUntilChange)
    }
    val nextChordName = nextChordInfo.first
    val slotsUntilChange = nextChordInfo.second

    // Get current section's strum pattern for visual
    val currentPattern = remember(currentSong, currentSectionIdx) {
        currentSong?.sections?.getOrNull(currentSectionIdx)?.pattern ?: emptyList()
    }

    // Convert slots to beats for display
    val beatsPerMeasure = currentSong?.meter?.split("/")?.firstOrNull()?.trim()?.toIntOrNull() ?: 4
    val subdivs = currentSong?.subdivisions?.coerceIn(1, 8) ?: 8
    val beatsUntilChange = if (subdivs > 0) {
        val subsPerBeat = (subdivs.toFloat() / beatsPerMeasure).coerceAtLeast(1f)
        (slotsUntilChange / subsPerBeat).toInt().coerceAtLeast(0)
    } else 0

    // UI
    Box(modifier = Modifier.fillMaxSize().background(SP_BG)) {
        if (currentSong == null || showSongPicker) {
            // Song picker as main view
            if (dataLoaded) {
                SongPickerOverlay(
                    songs = SongRepository.getSongs(),
                    onPick = { song ->
                        currentSong = song
                        bpm = song.bpmTarget
                        loopSectionIdx = -1
                        showSongPicker = false
                    },
                    onDismiss = { showSongPicker = false }
                )
            }
        } else {
            val song = currentSong!!
            Column(modifier = Modifier.fillMaxSize()) {
                // Top bar: song info + controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(SP_TOP_BAR)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Song title (clickable to change song)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable { showSongPicker = true }
                    ) {
                        Text(
                            song.title,
                            color = SP_ACCENT,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Text(
                            "${song.artist} · ${song.key}" + if (song.capo > 0) " · Capo ${song.capo}" else "",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 11.sp,
                            maxLines = 1
                        )
                    }

                    // BPM controls
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { bpm = (bpm - 5).coerceAtLeast(30) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("-5", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                    Text(
                        "$bpm",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { bpm = (bpm + 5).coerceAtMost(220) }
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text("+5", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }

                    // Play/Stop
                    IconButton(
                        onClick = { isPlaying = !isPlaying },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) Color(0xFFE53935) else Color(0xFF43A047))
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlaying) "Parar" else "Reproducir",
                            tint = Color.White,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }

                // Section bar
                if (song.sections.size > 1) {
                    val sectionListState = rememberLazyListState()
                    LazyRow(
                        state = sectionListState,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFF1E1E2E))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(song.sections.size) { idx ->
                            val sec = song.sections[idx]
                            val isActive = idx == currentSectionIdx
                            val isLooped = idx == loopSectionIdx
                            val sectionLabel = when (sec.name) {
                                "verso" -> "Verso"
                                "estribillo" -> "Estribillo"
                                "puente" -> "Puente"
                                "final" -> "Final"
                                else -> sec.name.replaceFirstChar { it.uppercase() }
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(
                                        when {
                                            isLooped -> Color(0xFFFF9800).copy(alpha = 0.5f)
                                            isActive -> SP_SECTION_ACTIVE.copy(alpha = 0.5f)
                                            else -> Color.White.copy(alpha = 0.08f)
                                        }
                                    )
                                    .clickable {
                                        loopSectionIdx = if (loopSectionIdx == idx) -1 else idx
                                    }
                                    .padding(horizontal = 12.dp, vertical = 5.dp)
                            ) {
                                Text(
                                    sectionLabel + if (isLooped) " ↻" else "",
                                    color = when {
                                        isLooped -> Color(0xFFFFCC80)
                                        isActive -> Color(0xFFCE93D8)
                                        else -> Color.White.copy(alpha = 0.6f)
                                    },
                                    fontSize = 12.sp,
                                    fontWeight = if (isActive || isLooped) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Main content: chord display + strum pattern
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Left: Current chord large + diagram
                    Column(
                        modifier = Modifier
                            .weight(1.2f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            currentChordName.ifEmpty { song.chordsUsed.firstOrNull() ?: "—" },
                            color = Color.White,
                            fontSize = 42.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        // Compact fretboard diagram
                        val displayChord = currentChord ?: run {
                            val firstName = song.chordsUsed.firstOrNull() ?: ""
                            val chordId = findOpenChordIdByName(firstName, ChordRepository.getChords())
                            chordId?.let { id -> ChordRepository.getChords().firstOrNull { it.id == id } }
                        }
                        if (displayChord != null) {
                            Canvas(
                                modifier = Modifier
                                    .fillMaxWidth(0.9f)
                                    .height(120.dp)
                            ) {
                                drawCompactChord(displayChord)
                            }
                        }
                    }

                    // Center: Next chord + beats countdown
                    Column(
                        modifier = Modifier
                            .weight(0.8f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Siguiente",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            nextChordName.ifEmpty { "—" },
                            color = Color(0xFF80CBC4),
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        if (nextChordName.isNotEmpty() && isPlaying) {
                            Text(
                                "en $beatsUntilChange ♩",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 16.sp
                            )
                        }
                    }

                    // Right: Strum pattern visual (animated)
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "Rasgueo",
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        StrumPatternVisual(
                            pattern = currentPattern,
                            currentStrokeIdx = if (isPlaying) currentStrokeIdx else -1,
                            subdivisions = subdivs
                        )
                    }
                }
            }
        }
    }
}

// Internal data for playback
private data class PlaybackSlot(
    val chordName: String,
    val chordId: String?,
    val strumDirection: String,
    val sectionIdx: Int,
    val measureInSection: Int,
    val subIdx: Int,
    val velocity: Float
)

/**
 * Visual strum pattern — row of arrows that illuminate in sync with playback
 */
@Composable
private fun StrumPatternVisual(
    pattern: List<StrokeInfo>,
    currentStrokeIdx: Int,
    subdivisions: Int
) {
    if (pattern.isEmpty()) return
    val displayPattern = pattern.take(subdivisions)

    // Show in rows of 4 for readability
    val chunked = displayPattern.chunked(4)
    Column(
        verticalArrangement = Arrangement.spacedBy(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        var globalIdx = 0
        for (row in chunked) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (stroke in row) {
                    val isCurrent = globalIdx == currentStrokeIdx
                    val symbol = when (stroke.type) {
                        "down" -> "↓"
                        "up" -> "↑"
                        "mute" -> "x"
                        else -> "·"
                    }
                    val color = when {
                        isCurrent && stroke.type == "down" -> Color(0xFF4CAF50)
                        isCurrent && stroke.type == "up" -> Color(0xFF42A5F5)
                        isCurrent -> Color(0xFFFF9800)
                        stroke.type == "rest" -> Color.White.copy(alpha = 0.15f)
                        else -> Color.White.copy(alpha = 0.4f)
                    }
                    val bgColor = if (isCurrent) Color.White.copy(alpha = 0.15f) else Color.Transparent

                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(bgColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            symbol,
                            color = color,
                            fontSize = if (isCurrent) 20.sp else 16.sp,
                            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                    globalIdx++
                }
            }
        }
    }
}

// Compact chord diagram (no title text, just fretboard)
private fun DrawScope.drawCompactChord(chord: ChordShape) {
    val w = size.width
    val h = size.height
    val frets = chord.frets
    if (frets.size < 6) return

    val minFret = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0
    val maxFret = frets.filterNotNull().filter { it > 0 }.maxOrNull() ?: 0
    val startFret = if (maxFret <= 4) 0 else (minFret - 1).coerceAtLeast(0)
    val fretsToShow = 5.coerceAtLeast(maxFret - startFret + 1)

    val topPad = h * 0.08f
    val bottomPad = h * 0.05f
    val leftPad = w * 0.08f
    val rightPad = w * 0.04f
    val fbTop = topPad
    val fbBottom = h - bottomPad
    val fbHeight = fbBottom - fbTop
    val stringSpacing = fbHeight / 7f
    val fbLeft = leftPad
    val fbRight = w - rightPad
    val fretWidth = (fbRight - fbLeft) / fretsToShow

    // Fretboard background
    drawRoundRect(
        color = Color(0xFF3E2415),
        topLeft = Offset(fbLeft, fbTop - 4f),
        size = Size(fbRight - fbLeft, fbHeight + 8f),
        cornerRadius = CornerRadius(4f)
    )

    // Nut
    if (startFret == 0) {
        drawRect(color = Color(0xFFF0EAD6), topLeft = Offset(fbLeft, fbTop - 6f), size = Size(8f, fbHeight + 12f))
    }

    // Fret lines
    for (fret in 1..fretsToShow) {
        val x = fbLeft + fret * fretWidth
        drawLine(Color(0xFFBBBBBB), Offset(x, fbTop - 2f), Offset(x, fbBottom + 2f), strokeWidth = 1.5f)
    }

    // String lines
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawLine(Color(0xFFD0C4B0), Offset(fbLeft, y), Offset(fbRight, y), strokeWidth = 1.5f)
    }

    val chordColor = Color(0xFF7B1FA2)
    val noteRadius = (stringSpacing * 0.35f).coerceIn(8f, 20f)
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 20f
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
                drawContext.canvas.nativeCanvas.drawText("X", fbLeft * 0.5f, y + 7f, labelPaint)
                labelPaint.color = android.graphics.Color.WHITE
            }
            fretVal == 0 -> {
                val cx = fbLeft * 0.5f
                drawCircle(Color(0xFF43A047), noteRadius, Offset(cx, y))
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

    // Start fret indicator
    if (startFret > 0) {
        val fretPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 255, 255, 255)
            textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText("${startFret + 1}fr", fbLeft + fretWidth * 0.5f, fbBottom + bottomPad * 0.8f, fretPaint)
    }
}

// Chord name matching helpers
private fun findOpenChordIdByName(name: String, chords: List<ChordShape>): String? {
    val normalized = name.trim()
    if (normalized.isEmpty()) return null
    val matches = chords.filter { matchesChordName(it, normalized) }
    if (matches.isEmpty()) return null
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
    return when (quality.lowercase()) {
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
