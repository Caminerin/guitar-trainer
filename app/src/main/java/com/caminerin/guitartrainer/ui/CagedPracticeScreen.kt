package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import com.caminerin.guitartrainer.audio.TickPlayer
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private const val TOTAL_FRETS = 22

private val COLOR_BG = Color(0xFF1A1A1A)
private val COLOR_TOOLBAR = Color(0xFF1E1E1E)
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
fun CagedPracticeScreen(onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedScaleIndex by rememberSaveable { mutableIntStateOf(0) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var subdivision by rememberSaveable { mutableIntStateOf(1) }
    var positionsEnabled by rememberSaveable { mutableStateOf(true) }
    var currentPositionIndex by remember { mutableIntStateOf(0) }
    var currentNoteIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1.5f) }

    var showKeyCircle by remember { mutableStateOf(false) }
    var showScaleSelector by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }
    var showSubdivisionMenu by remember { mutableStateOf(false) }
    var showColorSelector by remember { mutableStateOf(false) }

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

    // Audio-driven BPM loop: playBeat blocks for exactly one beat duration
    LaunchedEffect(isPlaying, isPaused) {
        if (!isPlaying || isPaused) return@LaunchedEffect
        withContext(Dispatchers.IO) {
            while (isActive && isPlaying && !isPaused) {
                tickPlayer.playBeat(currentBpm, currentSubdivision)
                // Advance note on main thread
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

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(COLOR_BG)
        ) {
            // Toolbar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(COLOR_TOOLBAR)
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                IconButton(onClick = { isPlaying = false; onBack() }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Key selector -> chromatic circle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(CHROMATIC_COLORS[selectedKey].copy(alpha = 0.4f))
                        .clickable { showKeyCircle = true }
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(getChromaticNames(selectedKey)[selectedKey], color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }

                // Scale selector -> overlay
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF5C6BC0).copy(alpha = 0.25f))
                        .clickable { showScaleSelector = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(scale.name, color = Color(0xFFB0BEC5), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                // BPM control
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { bpm = (bpm - 5).coerceAtLeast(20) }
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    ) { Text("-", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                    Text(" $bpm ", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { bpm = (bpm + 5).coerceAtMost(240) }
                            .padding(horizontal = 7.dp, vertical = 5.dp)
                    ) { Text("+", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold) }
                }

                // Subdivision selector
                val subLabel = when (subdivision) {
                    1 -> "\u2669"; 2 -> "\u266a\u266a"; 3 -> "\u266a\u266a\u266a"; 4 -> "\u266c"; else -> "$subdivision"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF7C4DFF).copy(alpha = 0.3f))
                        .clickable { showSubdivisionMenu = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(subLabel, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                }

                // Colors button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFFE91E63).copy(alpha = 0.3f))
                        .clickable { showColorSelector = true }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Colores", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

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
                    modifier = Modifier.size(36.dp)
                ) {
                    val icon = if (isPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow
                    val tint = if (isPlaying && !isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50)
                    Icon(icon, "Play/Pause", tint = tint, modifier = Modifier.size(24.dp))
                }
                if (isPlaying) {
                    IconButton(
                        onClick = { isPlaying = false; isPaused = false; currentNoteIndex = 0; currentPositionIndex = 0 },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(Icons.Default.Stop, "Stop", tint = Color(0xFFF44336), modifier = Modifier.size(20.dp))
                    }
                }

                // Info button
                IconButton(onClick = { showInfo = true }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Info, "Info", tint = Color(0xFF90CAF9), modifier = Modifier.size(20.dp))
                }

                // Zoom controls
                IconButton(onClick = { zoom = (zoom - 0.3f).coerceAtLeast(0.5f) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ZoomOut, "Alejar", tint = Color.White, modifier = Modifier.size(20.dp))
                }
                IconButton(onClick = { zoom = (zoom + 0.3f).coerceAtMost(3f) }, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.ZoomIn, "Acercar", tint = Color.White, modifier = Modifier.size(20.dp))
                }

                // Positions toggle
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (positionsEnabled) COLOR_TONIC.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                        .clickable { positionsEnabled = !positionsEnabled }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text("Posiciones", color = if (positionsEnabled) Color.White else Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                if (positionsEnabled && positions.isNotEmpty()) {
                    CagedPositionBar(
                        positions = positions,
                        currentIndex = currentPositionIndex,
                        onSelect = { currentPositionIndex = it; currentNoteIndex = 0 }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))
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
                    currentNoteIndex = 0; currentPositionIndex = 0
                },
                onDismiss = { showKeyCircle = false }
            )
        }
        if (showScaleSelector) {
            ScaleSelectorOverlay(
                currentIndex = selectedScaleIndex,
                onSelected = {
                    selectedScaleIndex = it; showScaleSelector = false
                    currentNoteIndex = 0; currentPositionIndex = 0
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
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawContext.canvas.nativeCanvas.drawText(OPEN_STRING_NAMES[s], nutX * 0.5f, y + 24f, openPaint)
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

    val posStart = position.startFret
    val posEnd = position.endFret
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
                val dimR = noteRadius * 0.65f
                drawCircle(COLOR_DIM.copy(alpha = 0.35f), dimR, Offset(cx, y))
                val lbl = getSpanishNoteName(noteIdx, rootNote)
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
                val noteName = getSpanishNoteName(noteIdx, rootNote)
                val label = "$degreeStr $noteName"
                val paint = if (label.length > 3) notePaint else notePaintBig
                paint.color = if (isCurrentNote) android.graphics.Color.WHITE
                else android.graphics.Color.argb(220, 255, 255, 255)
                drawContext.canvas.nativeCanvas.drawText(label, cx, y + paint.textSize * 0.35f, paint)
            }
        }
    }
}
