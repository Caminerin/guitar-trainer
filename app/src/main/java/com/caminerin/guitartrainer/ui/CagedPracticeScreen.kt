package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop

import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.caminerin.guitartrainer.audio.ExerciseContext
import com.caminerin.guitartrainer.audio.NoteEvent
import com.caminerin.guitartrainer.audio.NoteRecognizer
import com.caminerin.guitartrainer.audio.PitchDetector
import com.caminerin.guitartrainer.audio.RecognitionResult
import com.caminerin.guitartrainer.audio.TickPlayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val TOTAL_FRETS = 22

private val COLOR_BG = SHARED_BG
private val COLOR_TOOLBAR = SHARED_TOOLBAR
private val COLOR_WOOD = Color(0xFF3E2415)
private val COLOR_NUT = Color(0xFFF0EAD6)
private val COLOR_FRET_WIRE = Color(0xFFBBBBBB)
private val COLOR_INLAY = Color(0xFFCCC4B0)
private val COLOR_TONIC = Color(0xFFE53935)
private val COLOR_THIRD = Color(0xFF1E88E5)
private val COLOR_FIFTH = Color(0xFF43A047)
private val COLOR_OTHER = Color(0xFF26A69A)
private val COLOR_HIGHLIGHT = Color(0xFFFFD600)
private val COLOR_DIM = Color(0xFF78909C)

private val STRING_COLORS = listOf(
    Color(0xFFB0A080), Color(0xFFB8A888), Color(0xFFC0B090),
    Color(0xFFD0C4B0), Color(0xFFD8D0C0), Color(0xFFE0D8C8)
)
private val STRING_WIDTHS = listOf(5.0f, 4.2f, 3.5f, 2.4f, 1.8f, 1.3f)



@Composable
fun CagedPracticeScreen(
    onBack: () -> Unit,
    pitchResult: PitchDetector.PitchResult? = null,
    noteEvent: NoteEvent? = null,
    noteRecognizer: NoteRecognizer? = null,
    onOverlayChanged: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedKey by rememberSaveable { mutableIntStateOf(AppPreferences.lastKey) }
    var selectedScaleIndex by rememberSaveable { mutableIntStateOf(AppPreferences.lastScaleIndex.coerceIn(0, ALL_SCALES.size - 1)) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var subdivision by rememberSaveable { mutableIntStateOf(1) }
    var positionsEnabled by rememberSaveable { mutableStateOf(true) }
    var currentPositionIndex by remember { mutableIntStateOf(0) }
    var currentNoteIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1.5f) }
    var guitarMode by rememberSaveable { mutableStateOf(false) }
    var evalFeedback by remember { mutableStateOf<String?>(null) }
    var evalFeedbackColor by remember { mutableStateOf(Color.Transparent) }
    var correctCount by remember { mutableIntStateOf(0) }
    var wrongCount by remember { mutableIntStateOf(0) }

    var showKeyCircle by remember { mutableStateOf(false) }
    var showScaleSelector by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showSubdivisionMenu by remember { mutableStateOf(false) }
    var showColorSelector by remember { mutableStateOf(false) }

    val anyOverlayOpen = showKeyCircle || showScaleSelector || showInfo || showSubdivisionMenu || showColorSelector
    LaunchedEffect(anyOverlayOpen) { onOverlayChanged(anyOverlayOpen) }

    val scale = ALL_SCALES[selectedScaleIndex]
    val positions = if (scale.hasCaged) computeCagedPositions(selectedKey) else scale.positions
    val currentPosition = positions.getOrElse(currentPositionIndex) { positions.first() }
    val noteSequence = remember(selectedKey, selectedScaleIndex, currentPositionIndex) {
        getPositionNoteSequence(selectedKey, scale.intervals, currentPosition)
    }
    val currentNote = noteSequence.getOrNull(currentNoteIndex)

    val density = LocalDensity.current
    val fretWidthDp = (60f * zoom).dp
    val openStringWidth = 48.dp
    val totalWidthDp = openStringWidth + (TOTAL_FRETS * 60 * zoom + 30).dp

    // Metronome tick player
    val tickPlayer = remember { TickPlayer() }
    DisposableEffect(Unit) {
        onDispose { tickPlayer.release() }
    }

    // Real-time references for audio loop
    val currentBpm by rememberUpdatedState(bpm)
    val currentSubdivision by rememberUpdatedState(subdivision)
    val currentPositions by rememberUpdatedState(positions)

    val currentGuitarMode by rememberUpdatedState(guitarMode)

    // Audio-driven BPM loop: playBeat blocks for exactly one beat duration
    LaunchedEffect(isPlaying, isPaused) {
        if (!isPlaying || isPaused) return@LaunchedEffect
        if (currentGuitarMode) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            while (isActive && isPlaying && !isPaused && !currentGuitarMode) {
                tickPlayer.playBeat(currentBpm, currentSubdivision)
                withContext(Dispatchers.Main) {
                    val seq = getPositionNoteSequence(
                        selectedKey, scale.intervals,
                        currentPositions.getOrElse(currentPositionIndex) { currentPositions.first() }
                    )
                    val nextIdx = currentNoteIndex + 1
                    if (nextIdx >= seq.size) {
                        val nextPos = (currentPositionIndex + 1) % currentPositions.size
                        currentPositionIndex = nextPos
                        currentNoteIndex = 0
                    } else {
                        currentNoteIndex = nextIdx
                    }
                }
            }
        }
    }

    var lastEvalNoteIdx by remember { mutableIntStateOf(-1) }
    var lastWrongNote by remember { mutableIntStateOf(-1) }
    var lastWrongTime by remember { mutableStateOf(0L) }

    // Reset NoteRecognizer when entering/leaving guitar mode
    LaunchedEffect(guitarMode) {
        if (guitarMode) noteRecognizer?.reset()
    }

    // Build exercise context from current scale + position
    val exerciseContext = remember(selectedKey, selectedScaleIndex, currentPositionIndex, currentNoteIndex) {
        val scaleNotes = scale.intervals.map { (selectedKey + it) % 12 }.toSet()
        val expectedIdx = currentNote?.noteIndex ?: 0
        val prevSeqIdx = currentNoteIndex - 1
        val prevNote = if (prevSeqIdx >= 0) noteSequence.getOrNull(prevSeqIdx)?.noteIndex ?: -1 else -1
        // Constrain MIDI range to current position on fretboard
        val posMinMidi = (0 until 6).minOf { s -> STANDARD_TUNING_MIDI[s] + currentPosition.startFret }
        val posMaxMidi = (0 until 6).maxOf { s -> STANDARD_TUNING_MIDI[s] + currentPosition.endFret }
        ExerciseContext(
            scaleNoteIndices = scaleNotes,
            expectedNoteIndex = expectedIdx,
            previousNoteIndex = prevNote,
            minMidi = posMinMidi,
            maxMidi = posMaxMidi
        )
    }

    // Guitar evaluation mode: use NoteRecognizer for smart note detection
    LaunchedEffect(guitarMode, noteEvent) {
        if (!guitarMode) return@LaunchedEffect
        val event = noteEvent ?: return@LaunchedEffect
        val recognizer = noteRecognizer ?: return@LaunchedEffect

        val evaluated = recognizer.evaluate(event, exerciseContext)
        val detectedNote = event.noteIndex
        val expected = currentNote?.noteIndex ?: return@LaunchedEffect
        val now = System.currentTimeMillis()

        when (evaluated.result) {
            RecognitionResult.EXPECTED_NOTE -> {
                if (lastEvalNoteIdx != currentNoteIndex) {
                    correctCount++
                    lastEvalNoteIdx = currentNoteIndex
                    lastWrongNote = -1
                }
                evalFeedback = "✓"
                evalFeedbackColor = Color(0xFF4CAF50)
                val seq = getPositionNoteSequence(
                    selectedKey, scale.intervals,
                    positions.getOrElse(currentPositionIndex) { positions.first() }
                )
                val nextIdx = currentNoteIndex + 1
                if (nextIdx >= seq.size) {
                    val nextPos = (currentPositionIndex + 1) % positions.size
                    currentPositionIndex = nextPos
                    currentNoteIndex = 0
                    lastEvalNoteIdx = -1
                } else {
                    currentNoteIndex = nextIdx
                    lastEvalNoteIdx = -1
                }
            }
            RecognitionResult.PREVIOUS_NOTE -> {
                // Ignore — still hearing the previous note ringing
            }
            RecognitionResult.NOISE, RecognitionResult.UNCERTAIN -> {
                // Ignore noise and uncertain detections
            }
            else -> {
                // Wrong note (in-scale or out-of-scale)
                if (detectedNote != lastWrongNote || (now - lastWrongTime) > 1500L) {
                    wrongCount++
                    lastWrongNote = detectedNote
                    lastWrongTime = now
                }
                val detectedName = getNoteName(detectedNote, selectedKey, scale.relativeMajorOffset)
                val expectedName = getNoteName(expected, selectedKey, scale.relativeMajorOffset)
                evalFeedback = "✗ $detectedName (esperada: $expectedName)"
                evalFeedbackColor = Color(0xFFF44336)
            }
        }
    }

    // Clear eval feedback after a short delay
    LaunchedEffect(evalFeedback) {
        if (evalFeedback != null) {
            kotlinx.coroutines.delay(1200L)
            evalFeedback = null
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(COLOR_BG)
        ) {
            // Toolbar — all buttons same height via ToolbarChip, single scrollable row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(COLOR_TOOLBAR)
                    .padding(horizontal = 6.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { isPlaying = false; onBack() }, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Key selector (chromatic color — same as Biblioteca)
                ToolbarChip(
                    text = getChromaticNames(selectedKey, scale.relativeMajorOffset)[selectedKey],
                    onClick = { showKeyCircle = true },
                    backgroundColor = CHROMATIC_COLORS[selectedKey].copy(alpha = 0.5f)
                )

                // Scale selector (same as Biblioteca)
                ToolbarChip(text = scale.name, onClick = { showScaleSelector = true })

                // BPM control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ToolbarChip(text = "-", onClick = { bpm = (bpm - 5).coerceAtLeast(20) })
                    Text(" $bpm ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    ToolbarChip(text = "+", onClick = { bpm = (bpm + 5).coerceAtMost(240) })
                }

                // Subdivision selector
                val subLabel = when (subdivision) {
                    1 -> "\u2669"; 2 -> "\u266a\u266a"; 3 -> "\u266a\u266a\u266a"; 4 -> "\u266c"; else -> "$subdivision"
                }
                ToolbarChip(text = subLabel, onClick = { showSubdivisionMenu = true })

                // Colores (same name as Biblioteca)
                ToolbarChip(text = "Colores", onClick = { showColorSelector = true })

                // Info (same name as Biblioteca)
                ToolbarChip(text = "Info", onClick = { showInfo = true })

                // Play/Pause/Stop
                IconButton(
                    onClick = {
                        if (isPlaying && !isPaused) {
                            isPaused = true
                        } else if (isPlaying && isPaused) {
                            isPaused = false
                        } else {
                            currentNoteIndex = 0
                            currentPositionIndex = 0
                            isPlaying = true
                            isPaused = false
                        }
                    },
                    modifier = Modifier.size(34.dp)
                ) {
                    val icon = if (isPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow
                    val tint = if (isPlaying && !isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50)
                    Icon(icon, "Play/Pause", tint = tint, modifier = Modifier.size(24.dp))
                }
                if (isPlaying) {
                    IconButton(
                        onClick = { isPlaying = false; isPaused = false; currentNoteIndex = 0; currentPositionIndex = 0 },
                        modifier = Modifier.size(34.dp)
                    ) {
                        Icon(Icons.Default.Stop, "Stop", tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                    }
                }

                // Pos toggle (same name as Biblioteca: "Pos")
                ToolbarChip(
                    text = "Pos",
                    onClick = { positionsEnabled = !positionsEnabled },
                    backgroundColor = if (positionsEnabled) SHARED_ACCENT else TOOLBAR_CHIP_BG
                )

                // Position chips — ALWAYS visible, dimmed when disabled
                if (positions.isNotEmpty()) {
                    CagedPositionBar(
                        positions = positions,
                        currentIndex = currentPositionIndex,
                        onSelect = { currentPositionIndex = it; currentNoteIndex = 0 },
                        enabled = positionsEnabled
                    )
                }

                // Guitar evaluation toggle
                ToolbarChip(
                    text = "\uD83C\uDFB8",
                    onClick = {
                        guitarMode = !guitarMode
                        if (!guitarMode) { evalFeedback = null; correctCount = 0; wrongCount = 0 }
                    },
                    backgroundColor = if (guitarMode) Color(0xFF4CAF50).copy(alpha = 0.4f) else TOOLBAR_CHIP_BG
                )
            }

            // Guitar evaluation status bar
            if (guitarMode) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(32.dp)
                        .background(Color(0xFF263238))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        evalFeedback ?: "\uD83C\uDFB8 Toca la nota resaltada",
                        color = if (evalFeedback != null) evalFeedbackColor else Color(0xFF80CBC4),
                        fontSize = 13.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "\u2714 $correctCount",
                            color = Color(0xFF4CAF50), fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                        Text(
                            "\u2718 $wrongCount",
                            color = Color(0xFFF44336), fontSize = 13.sp, fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Fretboard
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var prevSpan = 0f
                            do {
                                val event = awaitPointerEvent()
                                val pressed = event.changes.filter { it.pressed }
                                if (pressed.size >= 2) {
                                    val dx = pressed[0].position.x - pressed[1].position.x
                                    val dy = pressed[0].position.y - pressed[1].position.y
                                    val span = kotlin.math.sqrt(dx * dx + dy * dy)
                                    if (prevSpan > 10f && span > 10f) {
                                        zoom = (zoom * (span / prevSpan)).coerceIn(0.5f, 3f)
                                    }
                                    prevSpan = span
                                    pressed.forEach { it.consume() }
                                } else {
                                    prevSpan = 0f
                                }
                            } while (event.changes.any { it.pressed })
                        }
                    }
            ) {
              Box(
                modifier = Modifier
                    .fillMaxSize()
                    .horizontalScroll(rememberScrollState())
              ) {
                Canvas(
                    modifier = Modifier
                        .width(totalWidthDp)
                        .fillMaxHeight()
                ) {
                    drawCagedFretboard(
                        rootNote = selectedKey,
                        scale = scale,
                        position = currentPosition,
                        currentNote = currentNote,
                        fretWidthPx = with(density) { fretWidthDp.toPx() },
                        openStringWidthPx = with(density) { openStringWidth.toPx() },
                        positionsEnabled = positionsEnabled
                    )
                }
              }
            }
        }

        // Overlays
        if (showKeyCircle) {
            ChromaticCircleOverlay(
                selectedNote = selectedKey,
                rootNote = selectedKey,
                scaleIntervals = scale.intervals,
                onNoteSelected = {
                    selectedKey = it; showKeyCircle = false
                    AppPreferences.saveKey(it, context)
                    currentNoteIndex = 0; currentPositionIndex = 0
                },
                onDismiss = { showKeyCircle = false },
                relativeMajorOffset = scale.relativeMajorOffset
            )
        }
        if (showScaleSelector) {
            ScaleNameSelectorOverlay(
                currentName = scale.name,
                onSelected = { name ->
                    val idx = ALL_SCALES.indexOfFirst { it.name == name }
                    if (idx >= 0) {
                        selectedScaleIndex = idx
                        AppPreferences.saveScale(idx, context)
                        currentNoteIndex = 0; currentPositionIndex = 0
                    }
                    showScaleSelector = false
                },
                onDismiss = { showScaleSelector = false }
            )
        }
        if (showInfo) {
            ScaleInfoSheet(
                rootNote = selectedKey,
                scale = scale,
                onDismiss = { showInfo = false }
            )
        }
        if (showSubdivisionMenu) {
            SubdivisionSelectorOverlay(
                current = subdivision,
                onSelect = { subdivision = it },
                onDismiss = { showSubdivisionMenu = false }
            )
        }

        if (showColorSelector) {
            ScaleColorSelectorOverlay(
                context = context,
                onDismiss = { showColorSelector = false }
            )
        }
    }
}

private fun DrawScope.drawCagedFretboard(
    rootNote: Int,
    scale: Scale,
    position: ScalePosition,
    currentNote: FretboardNote?,
    fretWidthPx: Float,
    openStringWidthPx: Float,
    positionsEnabled: Boolean = true
) {
    val h = size.height
    val topPad = h * 0.08f
    val bottomPad = h * 0.08f
    val fbTop = topPad
    val fbBottom = h - bottomPad
    val fbHeight = fbBottom - fbTop
    val stringSpacing = fbHeight / 7f
    val nutX = openStringWidthPx
    val nutWidth = 12f

    drawRoundRect(
        color = COLOR_WOOD,
        topLeft = Offset(nutX, fbTop - 4f),
        size = Size(size.width - nutX, fbHeight + 8f),
        cornerRadius = CornerRadius(3f)
    )

    if (positionsEnabled) {
        val startX = if (position.startFret == 0) nutX else nutX + nutWidth + (position.startFret - 1) * fretWidthPx
        val endX = nutX + nutWidth + position.endFret * fretWidthPx
        drawRect(
            color = Color(0x22FFD54F),
            topLeft = Offset(startX, fbTop - 4f),
            size = Size(endX - startX, fbHeight + 8f)
        )
    }

    drawRect(color = COLOR_NUT, topLeft = Offset(nutX, fbTop - 6f), size = Size(nutWidth, fbHeight + 12f))

    for (fret in 1..TOTAL_FRETS) {
        val x = nutX + nutWidth + fret * fretWidthPx
        drawLine(COLOR_FRET_WIRE, Offset(x, fbTop - 2f), Offset(x, fbBottom + 2f), strokeWidth = 2.5f)
    }

    val singleDots = listOf(3, 5, 7, 9, 15, 17, 19, 21)
    val doubleDots = listOf(12)
    val dotRadius = (fretWidthPx * 0.08f).coerceIn(4f, 12f)
    for (fret in singleDots) {
        if (fret > TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.5f))
    }
    for (fret in doubleDots) {
        if (fret > TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.3f))
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.7f))
    }

    val fretNumPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(200, 200, 200, 200)
        textSize = 66f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    for (fret in 1..TOTAL_FRETS) {
        val x = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawContext.canvas.nativeCanvas.drawText("$fret", x, fbTop - 10f, fretNumPaint)
    }

    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawLine(STRING_COLORS[s], Offset(nutX, y), Offset(size.width, y), strokeWidth = STRING_WIDTHS[s])
    }

    val openPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 240, 240, 240)
        textSize = 72f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    val posStart = position.startFret
    val posEnd = position.endFret
    val fret0InPos = 0 in posStart..posEnd
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        val openNote = STANDARD_TUNING_MIDI[s]
        val noteIdx = openNote % 12
        val hasScaleNote = isNoteInScale(noteIdx, rootNote, scale.intervals)
        val willDrawCircle = hasScaleNote && (!positionsEnabled || fret0InPos)
        if (!willDrawCircle) {
            drawContext.canvas.nativeCanvas.drawText(OPEN_STRING_NAMES[s], nutX * 0.5f, y + 24f, openPaint)
        }
    }

    val noteRadius = (stringSpacing * 0.44f).coerceIn(36f, 80f)
    val notePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    val notePaintBig = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    val notePaintDim = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(100, 255, 255, 255)
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    for (s in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val y = fbTop + stringSpacing * (6 - s)

        for (fret in 0..TOTAL_FRETS) {
            val noteIdx = (openNote + fret) % 12
            if (!isNoteInScale(noteIdx, rootNote, scale.intervals)) continue

            val degree = getDegreeInScale(noteIdx, rootNote, scale.intervals) ?: continue
            val cx = if (fret == 0) nutX * 0.5f else nutX + nutWidth + (fret - 0.5f) * fretWidthPx

            val isInPos = fret in posStart..posEnd
            val isCurrentNote = currentNote != null && currentNote.string == s && currentNote.fret == fret

            if (positionsEnabled && !isInPos) {
                if (fret == 0) continue
                val dimR = noteRadius * 0.65f
                drawCircle(COLOR_DIM.copy(alpha = 0.35f), dimR, Offset(cx, y))
                val lbl = getSpanishNoteName(noteIdx, rootNote, scale.relativeMajorOffset)
                notePaintDim.color = android.graphics.Color.argb(100, 255, 255, 255)
                drawContext.canvas.nativeCanvas.drawText(lbl, cx, y + 10f, notePaintDim)
                continue
            }

            val isFiltered = DegreeColorPrefs.isScaleEnabled(degree)
            val noteColor = DegreeColorPrefs.getScaleColor(degree)

            val baseR = if (degree == 1 && isFiltered) noteRadius * 1.1f else if (!isFiltered) noteRadius * 0.7f else noteRadius
            val r = if (isCurrentNote) baseR * 1.3f else baseR

            drawCircle(Color(0x55000000), r + 3f, Offset(cx + 1.5f, y + 2f))
            drawCircle(noteColor, r, Offset(cx, y))
            drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(2f))

            if (isCurrentNote) {
                drawCircle(COLOR_HIGHLIGHT, r + 8f, Offset(cx, y), style = Stroke(5f))
                drawCircle(COLOR_HIGHLIGHT.copy(alpha = 0.3f), r + 16f, Offset(cx, y), style = Stroke(3f))
            }

            if (isFiltered) {
                val degreeStr = getDegreeLabel(degree)
                val noteName = getSpanishNoteName(noteIdx, rootNote, scale.relativeMajorOffset)
                val label = "$degreeStr $noteName"
                val paint = if (label.length > 3) notePaint else notePaintBig
                paint.color = if (isCurrentNote) android.graphics.Color.WHITE
                else android.graphics.Color.argb(220, 255, 255, 255)
                drawContext.canvas.nativeCanvas.drawText(label, cx, y + paint.textSize * 0.35f, paint)
            }
        }
    }
}
