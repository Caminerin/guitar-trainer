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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.VolumeUp
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
import androidx.compose.ui.zIndex
import com.caminerin.guitartrainer.audio.ChordSynth

private val CHORD_BG = SHARED_BG
private val CHORD_TOOLBAR = SHARED_TOOLBAR
private val CHORD_WOOD = Color(0xFFFAFAF5)       // cream paper background
private val CHORD_NUT = Color(0xFF333333)         // dark nut (tab style)
private val CHORD_FRET_WIRE = Color(0xFFCCCCCC)    // subtle fret lines
private val CHORD_STRING_COLORS = listOf(
    Color(0xFF999999), Color(0xFF999999), Color(0xFF999999),
    Color(0xFF999999), Color(0xFF999999), Color(0xFF999999)
)
private val CHORD_STRING_WIDTHS = listOf(1.5f, 1.5f, 1.5f, 1.5f, 1.5f, 1.5f)

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
    "half_diminished7" to Color(0xFF455A64),
    "augmented" to Color(0xFFD81B60),
    "add9" to Color(0xFF1565C0),
    "dominant9" to Color(0xFFEF6C00),
    "maj9" to Color(0xFF2E7D32),
    "minor9" to Color(0xFF00838F),
    "minor_major7" to Color(0xFFC62828),
    "minor_major9" to Color(0xFFAD1457),
    "sixth" to Color(0xFF6A1B9A),
    "six_nine" to Color(0xFF4A148C),
    "minor6" to Color(0xFF880E4F),
    "minor_six_nine" to Color(0xFF4E342E),
    "power5" to Color(0xFF37474F),
    "dominant7sus4" to Color(0xFFE65100),
    "nine_sus4" to Color(0xFFBF360C),
    "minor_add9" to Color(0xFF00695C),
    "minor11" to Color(0xFF004D40),
    "minor13" to Color(0xFF006064),
    "minor7_flat9" to Color(0xFF3E2723)
)

private val LEVEL_COLORS = mapOf(
    "all" to Color(0xFF5C6BC0),
    "beginner_core" to Color(0xFF4CAF50),
    "intermediate_core" to Color(0xFFFF9800),
    "advanced_reference" to Color(0xFFE53935)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordVisualizerScreen(onBack: () -> Unit, onGoToPractice: (() -> Unit)? = null, showBackButton: Boolean = true, onOverlayChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        ChordRepository.loadChords(context)
        ScaleChordRepository.load(context)
        AppPreferences.loadChordState(context)
        ChordSynth.init(context)
    }

    var selectedRoot by rememberSaveable { mutableIntStateOf(AppPreferences.chordRoot) }
    var selectedQuality by rememberSaveable { mutableStateOf(AppPreferences.chordQuality) }
    var selectedLevel by rememberSaveable { mutableStateOf(AppPreferences.chordLevel) }
    var selectedChordIndex by rememberSaveable { mutableIntStateOf(0) }
    val hasSelectedRoot = selectedRoot >= 0

    var noteDisplay by rememberSaveable { mutableStateOf(NoteDisplay.NOTE) }

    var showRootSelector by remember { mutableStateOf(false) }
    var showQualitySelector by remember { mutableStateOf(false) }
    var showLevelSelector by remember { mutableStateOf(false) }
    var showDisplaySelector by remember { mutableStateOf(false) }
    var showColorSelector by remember { mutableStateOf(false) }
    var showScaleSelector by remember { mutableStateOf(false) }
    var showShapeSelector by remember { mutableStateOf(false) }
    var scaleFilterEnabled by rememberSaveable { mutableStateOf(AppPreferences.chordScaleEnabled) }
    var selectedScaleName by rememberSaveable { mutableStateOf(AppPreferences.chordScaleName) }

    val anyOverlayOpen = showRootSelector || showQualitySelector || showLevelSelector || showDisplaySelector || showColorSelector || showScaleSelector || showShapeSelector
    LaunchedEffect(anyOverlayOpen) { onOverlayChanged(anyOverlayOpen) }

    // Save state whenever it changes
    LaunchedEffect(selectedRoot, selectedQuality, selectedLevel, scaleFilterEnabled, selectedScaleName) {
        AppPreferences.saveChordState(selectedRoot, selectedQuality, selectedLevel, scaleFilterEnabled, selectedScaleName, context)
    }

    // Load color preferences
    LaunchedEffect(Unit) { DegreeColorPrefs.load(context) }

    val quality = ChordQuality.entries.find { it.csvValue == selectedQuality } ?: ChordQuality.MAJOR
    val level = ChordLevel.entries.find { it.csvValue == selectedLevel } ?: ChordLevel.BEGINNER

    val scaleOffset = if (scaleFilterEnabled) getRelativeMajorOffset(selectedScaleName) else 0

    val filteredChords = if (hasSelectedRoot) {
        if (scaleFilterEnabled) {
            val scaleChords = ScaleChordRepository.getChordsForScale(selectedScaleName, selectedRoot, scaleOffset)
            val scaleChordNames = scaleChords.map { it.chordName.lowercase() }.toSet()
            val rootName = AMERICAN_NOTE_NAMES[selectedRoot]
            ChordRepository.getChordsByRootLevelQuality(rootName, level, quality)
                .filter { chord -> chord.getDisplayName(selectedRoot, scaleOffset).lowercase() in scaleChordNames }
                .sortedBy { it.priority }
        } else {
            val rootName = AMERICAN_NOTE_NAMES[selectedRoot]
            ChordRepository.getChordsByRootLevelQuality(rootName, level, quality)
                .sortedBy { it.priority }
        }
    } else emptyList()

    val safeIndex = if (filteredChords.isEmpty()) 0 else selectedChordIndex.coerceIn(0, filteredChords.size - 1)
    val currentChord = filteredChords.getOrNull(safeIndex)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CHORD_BG)
    ) {
        // Toolbar
        ScrollableToolbar(bgColor = CHORD_TOOLBAR) {
            // Back button
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Root selector (chromatic color to identify root)
            ToolbarChip(
                text = if (hasSelectedRoot) getChromaticNames(selectedRoot)[selectedRoot] else "Raíz",
                onClick = { showRootSelector = true },
                backgroundColor = if (hasSelectedRoot) CHROMATIC_COLORS[selectedRoot].copy(alpha = 0.5f) else TOOLBAR_CHIP_BG
            )

            // Quality selector
            ToolbarChip(text = quality.displayName, onClick = { showQualitySelector = true })

            // Level selector
            ToolbarChip(text = "Nivel: ${level.displayName}", onClick = { showLevelSelector = true })

            // Scale filter
            val scaleLabel = if (scaleFilterEnabled) {
                selectedScaleName.replace(" (Jónica)", "").replace(" (Eólica)", "")
            } else "Escala"
            ToolbarChip(
                text = scaleLabel,
                onClick = { if (hasSelectedRoot) showScaleSelector = true },
                backgroundColor = if (scaleFilterEnabled) SHARED_ACCENT else TOOLBAR_CHIP_BG
            )

            // Display mode selector
            NoteDisplayToolbarButton(noteDisplay) { showDisplaySelector = true }

            // Colors button
            ToolbarChip(text = "Colores", onClick = { showColorSelector = true })
        }

        // Navigation row (separate from toolbar for clarity)
        if (filteredChords.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CHORD_TOOLBAR)
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.End
            ) {
                IconButton(
                    onClick = {
                        selectedChordIndex = (safeIndex - 1 + filteredChords.size) % filteredChords.size
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowLeft, "Anterior", tint = Color.White)
                }
                Text(
                    "${safeIndex + 1}/${filteredChords.size}",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 12.sp
                )
                IconButton(
                    onClick = {
                        selectedChordIndex = (safeIndex + 1) % filteredChords.size
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(Icons.Default.KeyboardArrowRight, "Siguiente", tint = Color.White)
                }
            }
        }

        // Shape selector button (replaces old horizontal bar)
        if (filteredChords.size > 1) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF252525))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ToolbarChip(
                    text = currentChord?.shortLabel ?: "Posición",
                    onClick = { showShapeSelector = true }
                )
                Text(
                    "${safeIndex + 1}/${filteredChords.size} posiciones",
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 12.sp
                )
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
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        currentChord.displayName,
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(
                        onClick = { ChordSynth.playChord(currentChord.frets, 1500, root = currentChord.root, quality = currentChord.quality) },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.VolumeUp, "Escuchar", tint = Color(0xFF4CAF50), modifier = Modifier.size(22.dp))
                    }
                }
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
                    drawChordDiagram(currentChord, noteDisplay, selectedRoot, currentChord.fingering)
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
        val rootForCircle = if (hasSelectedRoot) selectedRoot else 0
        ChromaticCircleOverlay(
            selectedNote = rootForCircle,
            rootNote = rootForCircle,
            scaleIntervals = listOf(0, 2, 4, 5, 7, 9, 11),
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
                .zIndex(10f)
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
                horizontalAlignment = Alignment.Start
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
                QualityGroup.entries.forEach { group ->
                    val groupQualities = ChordQuality.entries.filter { it.group == group }
                    if (groupQualities.isNotEmpty()) {
                        Text(group.displayName, color = Color.White.copy(alpha = 0.5f), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(6.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            groupQualities.forEach { q ->
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
                                        .padding(horizontal = 16.dp, vertical = 10.dp)
                                ) {
                                    Text(q.displayName, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
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
                .zIndex(10f)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { showLevelSelector = false },
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
                            ChordRepository.getChordsByRootLevelQuality(AMERICAN_NOTE_NAMES[selectedRoot], l, quality).size
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
            onDismiss = { showDisplaySelector = false },
            showFingering = true
        )
    }

    // Color selector overlay
    if (showColorSelector) {
        ChordColorSelectorOverlay(
            context = context,
            onDismiss = { showColorSelector = false }
        )
    }

    // Scale selector overlay
    if (showScaleSelector) {
        ScaleNameSelectorOverlay(
            currentName = selectedScaleName,
            showDisableOption = scaleFilterEnabled,
            onSelected = { name ->
                selectedScaleName = name
                scaleFilterEnabled = true
                selectedChordIndex = 0
                showScaleSelector = false
            },
            onDisable = {
                scaleFilterEnabled = false
                showScaleSelector = false
            },
            onDismiss = { showScaleSelector = false }
        )
    }

    // Shape/position selector overlay
    if (showShapeSelector && filteredChords.isNotEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { showShapeSelector = false },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF2A2A2A))
                    .clickable(enabled = false) {}
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Posiciones", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    IconButton(onClick = { showShapeSelector = false }) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    itemsIndexed(filteredChords) { idx, chord ->
                        val isSelected = idx == safeIndex
                        val color = QUALITY_COLORS[selectedQuality] ?: Color.Gray
                        val minFret = chord.frets.filterNotNull().filter { it > 0 }.minOrNull()
                        val maxFretVal = chord.frets.filterNotNull().filter { it > 0 }.maxOrNull()
                        val fretRange = if (minFret != null && maxFretVal != null) "Trastes $minFret-$maxFretVal" else "Abierto"
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) color else color.copy(alpha = 0.15f))
                                .clickable {
                                    selectedChordIndex = idx
                                    showShapeSelector = false
                                }
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    chord.shortLabel,
                                    color = Color.White,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    fretRange,
                                    color = Color.White.copy(alpha = 0.5f),
                                    fontSize = 11.sp
                                )
                            }
                            Text(
                                "${idx + 1}/${filteredChords.size}",
                                color = Color.White.copy(alpha = 0.4f),
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

private val CHORD_COLOR_ROOT = Color(0xFFE53935)
private val CHORD_COLOR_THIRD = Color(0xFF1E88E5)
private val CHORD_COLOR_FIFTH = Color(0xFF43A047)
private val CHORD_COLOR_OTHER = Color(0xFF26A69A)

private fun getChordNoteColor(interval: String): Color {
    return DegreeColorPrefs.getChordColor(interval)
}

private fun getIntervalCategory(interval: String): String {
    return when (interval) {
        "1" -> "1"; "3", "b3" -> "3"; "5", "b5", "#5" -> "5"; else -> "other"
    }
}

private fun DrawScope.drawChordDiagram(chord: ChordShape, noteDisplay: NoteDisplay, rootNoteIdx: Int, fingering: List<String> = emptyList()) {
    val w = size.width
    val h = size.height
    val frets = chord.frets
    if (frets.size < 6) return

    val minFret = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0
    val maxFret = frets.filterNotNull().filter { it > 0 }.maxOrNull() ?: 0
    val startFret = if (maxFret <= 4) 0 else (minFret - 1).coerceAtLeast(0)
    val fretsToShow = 5.coerceAtLeast(maxFret - startFret + 1)

    val topPad = h * 0.08f
    val bottomPad = h * 0.18f
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

    // Paper/cream background
    drawRoundRect(
        color = CHORD_WOOD,
        topLeft = Offset(fbLeft, fbTop - 4f),
        size = Size(fbRight - fbLeft, fbHeight + 8f),
        cornerRadius = CornerRadius(6f)
    )

    // Nut (dark, tab style — only if showing from fret 0)
    if (startFret == 0) {
        val nutWidth = 14f
        drawRect(color = CHORD_NUT, topLeft = Offset(fbLeft, fbTop - 6f), size = Size(nutWidth, fbHeight + 12f))
    }

    // Fret lines — subtle
    for (fret in 1..fretsToShow) {
        val x = fbLeft + fret * fretWidth
        drawLine(CHORD_FRET_WIRE, Offset(x, fbTop), Offset(x, fbBottom), strokeWidth = 1f)
    }

    // Fret numbers
    val fretNumPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(150, 100, 100, 100)
        textSize = 36f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    for (fret in 1..fretsToShow) {
        val displayFret = startFret + fret
        val x = fbLeft + (fret - 0.5f) * fretWidth
        drawContext.canvas.nativeCanvas.drawText("$displayFret", x, fbTop - 12f, fretNumPaint)
    }

    // Staff lines (strings) — thin uniform like tab notation
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
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
                val isFiltered = DegreeColorPrefs.isChordEnabled(interval)
                val noteColor = if (isFiltered) {
                    if (interval != "None" && interval.isNotEmpty()) getChordNoteColor(interval) else DegreeColorPrefs.chordOtherColor
                } else {
                    COLOR_OFF.copy(alpha = 0.35f)
                }

                val cx = fbLeft * 0.5f
                val r = if (isFiltered) noteRadius else noteRadius * 0.7f
                drawCircle(CHORD_WOOD, r + 2f, Offset(cx, y))
                drawCircle(noteColor, r, Offset(cx, y))
                drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(1.5f))

                if (noteDisplay != NoteDisplay.NONE && isFiltered) {
                    val lbl = if (noteDisplay == NoteDisplay.FINGERING && fingering.size > s) {
                        val f = fingering[s]; if (f == "0") "" else f
                    } else buildChordNoteLabel(noteName, interval, noteDisplay)
                    if (lbl.isNotEmpty()) {
                        val paint = if (lbl.length > 3) notePaintSmall else notePaintBig
                        drawContext.canvas.nativeCanvas.drawText(lbl, cx, y + paint.textSize * 0.35f, paint)
                    }
                }
            }
            else -> {
                val displayPos = fretVal - startFret
                if (displayPos in 1..fretsToShow) {
                    val cx = fbLeft + (displayPos - 0.5f) * fretWidth
                    val noteIdx = (STANDARD_TUNING_MIDI[s] + fretVal) % 12
                    val noteName = getNoteName(noteIdx, rootNoteIdx)
                    val isFiltered2 = DegreeColorPrefs.isChordEnabled(interval)
                    val noteColor = if (isFiltered2) {
                        if (interval != "None" && interval.isNotEmpty()) getChordNoteColor(interval) else DegreeColorPrefs.chordOtherColor
                    } else {
                        COLOR_OFF.copy(alpha = 0.35f)
                    }
                    val r = if (interval == "1" && isFiltered2) noteRadius * 1.1f else if (!isFiltered2) noteRadius * 0.7f else noteRadius

                    drawCircle(CHORD_WOOD, r + 2f, Offset(cx, y))
                    drawCircle(noteColor, r, Offset(cx, y))
                    drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(1.5f))

                    if (noteDisplay != NoteDisplay.NONE && isFiltered2) {
                        val lbl = if (noteDisplay == NoteDisplay.FINGERING && fingering.size > s) {
                            fingering[s]
                        } else buildChordNoteLabel(noteName, interval, noteDisplay)
                        if (lbl != "x" && lbl.isNotEmpty()) {
                            val paint = if (lbl.length > 3) notePaintSmall else notePaintBig
                            drawContext.canvas.nativeCanvas.drawText(lbl, cx, y + paint.textSize * 0.35f, paint)
                        }
                    }
                }
            }
        }
    }

    // Open string labels on right side
    val openStringPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(180, 80, 80, 80)
        textSize = 48f
        textAlign = android.graphics.Paint.Align.LEFT
        isAntiAlias = true
    }
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawContext.canvas.nativeCanvas.drawText(getOpenStringNames()[s], fbRight + 8f, y + 12f, openStringPaint)
    }

    // Chord name (top of bottom area)
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

    // Notes annotation at bottom (context-aware: use flats when appropriate)
    val chordNoteNames = mutableListOf<String>()
    for (s in 0 until 6) {
        val fretVal = frets.getOrNull(s)
        if (fretVal != null) {
            val noteIdx = (STANDARD_TUNING_MIDI[s] + fretVal) % 12
            chordNoteNames.add(getNoteName(noteIdx, rootNoteIdx))
        }
    }
    if (chordNoteNames.isNotEmpty()) {
        val notePaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(180, 200, 200, 200)
            textSize = 36f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        val notesText = chordNoteNames.joinToString(" - ")
        drawContext.canvas.nativeCanvas.drawText(notesText, w / 2f, h - bottomPad * 0.12f, notePaint)
    }
}

private fun buildChordNoteLabel(noteName: String, interval: String, display: NoteDisplay): String {
    val intervalClean = if (interval == "None" || interval.isEmpty()) "" else interval
    return when (display) {
        NoteDisplay.NOTE -> noteName
        NoteDisplay.DEGREE -> intervalClean
        NoteDisplay.BOTH -> if (intervalClean.isNotEmpty()) "$intervalClean $noteName" else noteName
        NoteDisplay.FINGERING -> ""
        NoteDisplay.NONE -> ""
    }
}

@Composable
private fun ChordColorPreviewChip(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
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
