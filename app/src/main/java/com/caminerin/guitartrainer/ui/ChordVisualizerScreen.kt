package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

private val CHORD_BG = Color(0xFF1A1A1A)
private val CHORD_TOOLBAR = Color(0xFF1E1E1E)
private val CHORD_WOOD = Color(0xFF3E2415)
private val CHORD_NUT = Color(0xFFF0EAD6)
private val CHORD_FRET_WIRE = Color(0xFFBBBBBB)
private val CHORD_STRING_COLORS = listOf(
    Color(0xFFB0A080), Color(0xFFB8A888), Color(0xFFC0B090),
    Color(0xFFD0C4B0), Color(0xFFD8D0C0), Color(0xFFE0D8C8)
)
private val CHORD_STRING_WIDTHS = listOf(5.0f, 4.2f, 3.5f, 2.4f, 1.8f, 1.3f)

private val QUALITY_COLORS = mapOf(
    "major" to Color(0xFF5C6BC0),
    "minor" to Color(0xFFE53935),
    "dominant7" to Color(0xFFFF9800),
    "maj7" to Color(0xFF43A047),
    "m7" to Color(0xFF26A69A),
    "sus2" to Color(0xFF8E24AA),
    "sus4" to Color(0xFFAB47BC),
    "diminished" to Color(0xFF78909C),
    "diminished7" to Color(0xFF546E7A),
    "half_diminished7" to Color(0xFF455A64)
)

private val LEVEL_COLORS = mapOf(
    "beginner_core" to Color(0xFF4CAF50),
    "intermediate_core" to Color(0xFFFF9800),
    "advanced_reference" to Color(0xFFE53935)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordVisualizerScreen(onBack: () -> Unit) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ChordRepository.loadChords(context)
    }

    var selectedRoot by rememberSaveable { mutableIntStateOf(-1) }
    var selectedQuality by rememberSaveable { mutableStateOf(ChordQuality.MAJOR.csvValue) }
    var selectedLevel by rememberSaveable { mutableStateOf(ChordLevel.BEGINNER.csvValue) }
    var selectedChordIndex by rememberSaveable { mutableIntStateOf(0) }
    val hasSelectedRoot = selectedRoot >= 0

    var noteDisplay by rememberSaveable { mutableStateOf(NoteDisplay.NOTE) }

    var showRootSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showLevelSelector by remember { mutableStateOf(false) }
    var showDisplaySelector by remember { mutableStateOf(false) }

    // Interval color filter: "1"=root, "3"=thirds, "5"=fifths, "other"=other
    var intervalFilter by remember { mutableStateOf(setOf("1", "3", "5", "other")) }

    val quality = ChordQuality.entries.find { it.csvValue == selectedQuality } ?: ChordQuality.MAJOR
    val level = ChordLevel.entries.find { it.csvValue == selectedLevel } ?: ChordLevel.BEGINNER

    val filteredChords = if (hasSelectedRoot) {
        val rootName = SCALE_NOTE_NAMES[selectedRoot]
        ChordRepository.getChordsByRootLevelQuality(rootName, level, quality)
            .sortedBy { it.priority }
    } else emptyList()

    val safeIndex = if (filteredChords.isEmpty()) 0 else selectedChordIndex.coerceIn(0, filteredChords.size - 1)
    val currentChord = filteredChords.getOrNull(safeIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CHORD_BG)
    ) {
        // Toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(CHORD_TOOLBAR)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            // Root selector
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (hasSelectedRoot) CHROMATIC_COLORS[selectedRoot].copy(alpha = 0.4f) else Color.White.copy(alpha = 0.12f))
                    .clickable { showRootSelector = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(
                    if (hasSelectedRoot) getChromaticNames(selectedRoot)[selectedRoot] else "Nota",
                    color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold
                )
            }

            // Quality selector
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background((QUALITY_COLORS[selectedQuality] ?: Color.Gray).copy(alpha = 0.4f))
                    .clickable { showQualitySelector = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(quality.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Level selector
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background((LEVEL_COLORS[selectedLevel] ?: Color.Gray).copy(alpha = 0.4f))
                    .clickable { showLevelSelector = true }
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Text(level.displayName, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            // Display mode selector (N+G)
            NoteDisplayToolbarButton(noteDisplay) { showDisplaySelector = true }

            Spacer(modifier = Modifier.weight(1f))

            // Navigation arrows
            if (filteredChords.size > 1) {
                IconButton(
                    onClick = {
                        selectedChordIndex = (safeIndex - 1 + filteredChords.size) % filteredChords.size
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Anterior", tint = Color.White)
                }
                Text(
                    "${safeIndex + 1}/${filteredChords.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp
                )
                IconButton(
                    onClick = {
                        selectedChordIndex = (safeIndex + 1) % filteredChords.size
                    },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, "Siguiente", tint = Color.White)
                }
            }
        }

        // Shape selector row
        if (filteredChords.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                filteredChords.forEachIndexed { idx, chord ->
                    val isSelected = idx == safeIndex
                    val color = QUALITY_COLORS[selectedQuality] ?: Color.Gray
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isSelected) color else color.copy(alpha = 0.15f))
                            .clickable { selectedChordIndex = idx }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            chord.shortLabel,
                            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // ===== INTERVAL COLOR LEGEND =====
        if (currentChord != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Color:", color = Color.White.copy(alpha = 0.5f), fontSize = 11.sp)
                IntervalChip("Fundamental", CHORD_COLOR_ROOT, "1" in intervalFilter) {
                    intervalFilter = if ("1" in intervalFilter) intervalFilter - "1" else intervalFilter + "1"
                }
                IntervalChip("3\u00aa", CHORD_COLOR_THIRD, "3" in intervalFilter) {
                    intervalFilter = if ("3" in intervalFilter) intervalFilter - "3" else intervalFilter + "3"
                }
                IntervalChip("5\u00aa", CHORD_COLOR_FIFTH, "5" in intervalFilter) {
                    intervalFilter = if ("5" in intervalFilter) intervalFilter - "5" else intervalFilter + "5"
                }
                IntervalChip("Otros", CHORD_COLOR_OTHER, "other" in intervalFilter) {
                    intervalFilter = if ("other" in intervalFilter) intervalFilter - "other" else intervalFilter + "other"
                }
            }
        }

        // Chord diagram
        if (currentChord != null) {
            // Info row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    currentChord.displayName,
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        "F\u00f3rmula: ${currentChord.formula}",
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 11.sp
                    )
                    if (currentChord.inversion.isNotEmpty() && currentChord.inversion != "root_position") {
                        val invText = when {
                            currentChord.inversion.contains("first") -> "1\u00aa inv."
                            currentChord.inversion.contains("second") -> "2\u00aa inv."
                            currentChord.inversion.contains("third") -> "3\u00aa inv."
                            else -> currentChord.inversion.replace("_", " ")
                        }
                        Text(
                            invText,
                            color = Color.White.copy(alpha = 0.5f),
                            fontSize = 10.sp
                        )
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Canvas(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp)
                ) {
                    drawChordDiagram(currentChord, noteDisplay, selectedRoot, intervalFilter)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    if (hasSelectedRoot) "No hay acordes para esta combinaci\u00f3n" else "Selecciona una nota ra\u00edz para ver acordes",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 16.sp
                )
            }
        }
    }

    // Root selector overlay (chromatic circle)
    if (showRootSelector) {
        ChromaticCircleOverlay(
            selectedNote = if (hasSelectedRoot) selectedRoot else 0,
            onNoteSelected = { note ->
                selectedRoot = note
                selectedChordIndex = 0
                showRootSelector = false
            },
            onDismiss = { showRootSelector = false }
        )
    }

    // Quality selector overlay
    if (showQualitySelector) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { showQualitySelector = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2A2A))
                    .padding(24.dp)
                    .verticalScroll(rememberScrollState()),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Tipo de acorde", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showQualitySelector = false }) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChordQuality.entries.forEach { q ->
                        val color = QUALITY_COLORS[q.csvValue] ?: Color.Gray
                        val isSelected = q.csvValue == selectedQuality
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) color else color.copy(alpha = 0.3f))
                                .clickable {
                                    selectedQuality = q.csvValue
                                    selectedChordIndex = 0
                                    showQualitySelector = false
                                }
                                .padding(horizontal = 20.dp, vertical = 14.dp)
                        ) {
                            Text(q.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Level selector overlay
    if (showLevelSelector) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { showLevelSelector = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2A2A))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Nivel", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showLevelSelector = false }) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                ChordLevel.entries.forEach { l ->
                    val color = LEVEL_COLORS[l.csvValue] ?: Color.Gray
                    val isSelected = l.csvValue == selectedLevel
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) color else color.copy(alpha = 0.3f))
                            .clickable {
                                selectedLevel = l.csvValue
                                selectedChordIndex = 0
                                showLevelSelector = false
                            }
                            .padding(horizontal = 20.dp, vertical = 14.dp)
                    ) {
                        val count = if (hasSelectedRoot) {
                            ChordRepository.getChordsByLevelAndQuality(l,
                                ChordQuality.entries.find { it.csvValue == selectedQuality } ?: ChordQuality.MAJOR
                            ).count { it.root == SCALE_NOTE_NAMES[selectedRoot] }
                        } else 0
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(l.displayName, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            Text("$count acordes", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }

    // Display mode overlay
    if (showDisplaySelector) {
        NoteDisplaySelectorOverlay(
            current = noteDisplay,
            onSelect = { noteDisplay = it },
            onDismiss = { showDisplaySelector = false }
        )
    }
}

private val CHORD_COLOR_ROOT = Color(0xFFE53935)
private val CHORD_COLOR_THIRD = Color(0xFF1E88E5)
private val CHORD_COLOR_FIFTH = Color(0xFF43A047)
private val CHORD_COLOR_OTHER = Color(0xFF26A69A)

private fun getChordNoteColor(interval: String): Color {
    return when (interval) {
        "1" -> CHORD_COLOR_ROOT
        "3", "b3" -> CHORD_COLOR_THIRD
        "5", "b5", "#5" -> CHORD_COLOR_FIFTH
        else -> CHORD_COLOR_OTHER
    }
}

private fun DrawScope.drawChordDiagram(chord: ChordShape, noteDisplay: NoteDisplay, rootNoteIdx: Int, intervalFilter: Set<String> = setOf("1", "3", "5", "other")) {
    val w = size.width
    val h = size.height
    val frets = chord.frets
    if (frets.size < 6) return

    val minFret = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0
    val maxFret = frets.filterNotNull().filter { it > 0 }.maxOrNull() ?: 0
    val startFret = if (maxFret <= 4) 0 else (minFret - 1).coerceAtLeast(0)
    val fretsToShow = 5.coerceAtLeast(maxFret - startFret + 1)

    val topPad = h * 0.1f
    val bottomPad = h * 0.1f
    val leftPad = w * 0.12f
    val rightPad = w * 0.08f
    val fbTop = topPad
    val fbBottom = h - bottomPad
    val fbHeight = fbBottom - fbTop
    val stringSpacing = fbHeight / 7f
    val fbLeft = leftPad
    val fbRight = w - rightPad
    val fretWidth = (fbRight - fbLeft) / fretsToShow

    val intervalParts = chord.intervals.split(" ")

    // Wood background
    drawRoundRect(
        color = CHORD_WOOD,
        topLeft = Offset(fbLeft, fbTop - 4f),
        size = Size(fbRight - fbLeft, fbHeight + 8f),
        cornerRadius = CornerRadius(6f)
    )
    for (i in 0..4) {
        val yOff = fbTop + fbHeight * (0.15f + i * 0.17f)
        drawRect(
            color = Color(0x0CFFFFFF),
            topLeft = Offset(fbLeft, yOff),
            size = Size(fbRight - fbLeft, fbHeight * 0.04f)
        )
    }

    // Nut (only if showing from fret 0)
    if (startFret == 0) {
        val nutWidth = 14f
        drawRect(color = CHORD_NUT, topLeft = Offset(fbLeft, fbTop - 6f), size = Size(nutWidth, fbHeight + 12f))
    }

    // Fret wires
    for (fret in 1..fretsToShow) {
        val x = fbLeft + fret * fretWidth
        drawLine(Color(0x33000000), Offset(x + 1.5f, fbTop - 2f), Offset(x + 1.5f, fbBottom + 2f), strokeWidth = 3f)
        drawLine(CHORD_FRET_WIRE, Offset(x, fbTop - 2f), Offset(x, fbBottom + 2f), strokeWidth = 2.5f)
    }

    // Fret numbers
    val fretNumPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(200, 200, 200, 200)
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    for (fret in 1..fretsToShow) {
        val displayFret = startFret + fret
        val x = fbLeft + (fret - 0.5f) * fretWidth
        drawContext.canvas.nativeCanvas.drawText("$displayFret", x, fbTop - 12f, fretNumPaint)
    }

    // Strings (1st at top, 6th at bottom)
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawLine(Color(0x33000000), Offset(fbLeft, y + 1f), Offset(fbRight, y + 1f), strokeWidth = CHORD_STRING_WIDTHS[s] + 1f)
        drawLine(CHORD_STRING_COLORS[s], Offset(fbLeft, y), Offset(fbRight, y), strokeWidth = CHORD_STRING_WIDTHS[s])
    }

    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 56f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    val notePaintBig = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 42f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    val notePaintSmall = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 30f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    val noteRadius = (stringSpacing * 0.44f).coerceIn(36f, 80f)

    for (s in 0 until 6) {
        val fretVal = frets[s]
        val y = fbTop + stringSpacing * (6 - s)
        val interval = intervalParts.getOrNull(s) ?: ""

        when {
            fretVal == null -> {
                labelPaint.color = android.graphics.Color.argb(180, 220, 80, 80)
                drawContext.canvas.nativeCanvas.drawText("X", fbLeft * 0.5f, y + 20f, labelPaint)
                labelPaint.color = android.graphics.Color.WHITE
            }
            fretVal == 0 -> {
                val noteIdx = STANDARD_TUNING_MIDI[s] % 12
                val noteName = getNoteName(noteIdx, rootNoteIdx)
                val intervalCat = getIntervalCategory(interval)
                val isFiltered = intervalCat in intervalFilter
                val noteColor = if (isFiltered) {
                    if (interval != "None" && interval.isNotEmpty()) getChordNoteColor(interval) else Color(0xFF43A047)
                } else {
                    Color(0xFF78909C).copy(alpha = 0.35f)
                }

                val cx = fbLeft * 0.5f
                val r = if (isFiltered) noteRadius else noteRadius * 0.7f
                drawCircle(Color(0x55000000), r + 2f, Offset(cx + 1f, y + 1.5f))
                drawCircle(noteColor, r, Offset(cx, y))
                drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(2f))

                if (noteDisplay != NoteDisplay.NONE && isFiltered) {
                    val lbl = buildChordNoteLabel(noteName, interval, noteDisplay)
                    val paint = if (lbl.length > 3) notePaintSmall else notePaintBig
                    drawContext.canvas.nativeCanvas.drawText(lbl, cx, y + paint.textSize * 0.35f, paint)
                }
            }
            else -> {
                val displayPos = fretVal - startFret
                if (displayPos in 1..fretsToShow) {
                    val cx = fbLeft + (displayPos - 0.5f) * fretWidth
                    val noteIdx = (STANDARD_TUNING_MIDI[s] + fretVal) % 12
                    val noteName = getNoteName(noteIdx, rootNoteIdx)
                    val intervalCat2 = getIntervalCategory(interval)
                    val isFiltered2 = intervalCat2 in intervalFilter
                    val noteColor = if (isFiltered2) {
                        if (interval != "None" && interval.isNotEmpty()) getChordNoteColor(interval) else CHORD_COLOR_OTHER
                    } else {
                        Color(0xFF78909C).copy(alpha = 0.35f)
                    }
                    val r = if (interval == "1" && isFiltered2) noteRadius * 1.1f else if (!isFiltered2) noteRadius * 0.7f else noteRadius

                    drawCircle(Color(0x55000000), r + 3f, Offset(cx + 1.5f, y + 2f))
                    drawCircle(noteColor, r, Offset(cx, y))
                    drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(2f))

                    if (noteDisplay != NoteDisplay.NONE && isFiltered2) {
                        val lbl = buildChordNoteLabel(noteName, interval, noteDisplay)
                        val paint = if (lbl.length > 3) notePaintSmall else notePaintBig
                        drawContext.canvas.nativeCanvas.drawText(lbl, cx, y + paint.textSize * 0.35f, paint)
                    }
                }
            }
        }
    }

    // Open string labels on right side
    val openStringPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 240, 240, 240)
        textSize = 72f
        textAlign = android.graphics.Paint.Align.LEFT
        isAntiAlias = true
    }
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawContext.canvas.nativeCanvas.drawText(getOpenStringNames()[s], fbRight + 8f, y + 12f, openStringPaint)
    }

    // Notes annotation at bottom
    val notesParts = chord.notesSharp.split(" ").filter { it != "None" }
    if (notesParts.isNotEmpty()) {
        val notePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 200, 200, 200)
            textSize = 36f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val notesText = notesParts.joinToString(" - ")
        drawContext.canvas.nativeCanvas.drawText(notesText, w / 2f, h - bottomPad * 0.15f, notePaint)
    }

    // Chord name
    val chordNamePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 64f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    drawContext.canvas.nativeCanvas.drawText(
        chord.displayName,
        w / 2f,
        h - bottomPad * 0.55f,
        chordNamePaint
    )
}

private fun buildChordNoteLabel(noteName: String, interval: String, display: NoteDisplay): String {
    val intervalClean = if (interval == "None" || interval.isEmpty()) "" else interval
    return when (display) {
        NoteDisplay.NOTE -> noteName
        NoteDisplay.DEGREE -> intervalClean
        NoteDisplay.BOTH -> if (intervalClean.isNotEmpty()) "$intervalClean $noteName" else noteName
        NoteDisplay.NONE -> ""
    }
}

private fun getIntervalCategory(interval: String): String {
    return when (interval) {
        "1" -> "1"
        "3", "b3" -> "3"
        "5", "b5", "#5" -> "5"
        else -> "other"
    }
}

@Composable
private fun IntervalChip(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (active) color.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(if (active) color else Color.Gray.copy(alpha = 0.3f))
        )
        Text(
            label,
            color = if (active) Color.White else Color.White.copy(alpha = 0.3f),
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
