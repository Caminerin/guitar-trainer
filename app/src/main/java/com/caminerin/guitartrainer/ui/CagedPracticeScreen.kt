package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

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

private val STRING_COLORS = listOf(
    Color(0xFFB0A080), Color(0xFFB8A888), Color(0xFFC0B090),
    Color(0xFFD0C4B0), Color(0xFFD8D0C0), Color(0xFFE0D8C8)
)
private val STRING_WIDTHS = listOf(5.0f, 4.2f, 3.5f, 2.4f, 1.8f, 1.3f)

private val CAGED_COLORS = mapOf(
    'C' to Color(0xFFE53935),
    'A' to Color(0xFFFF9800),
    'G' to Color(0xFF4CAF50),
    'E' to Color(0xFF2196F3),
    'D' to Color(0xFF9C27B0)
)

@Composable
fun CagedPracticeScreen(onBack: () -> Unit) {
    var selectedKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedScaleIndex by rememberSaveable { mutableIntStateOf(0) }
    var bpm by rememberSaveable { mutableIntStateOf(60) }
    var currentPositionIndex by remember { mutableIntStateOf(0) }
    var currentNoteIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var zoom by remember { mutableFloatStateOf(1.5f) }

    var keyMenuExpanded by remember { mutableStateOf(false) }
    var scaleMenuExpanded by remember { mutableStateOf(false) }

    val scale = ALL_SCALES[selectedScaleIndex]
    val positions = scale.positions
    val currentPosition = positions.getOrElse(currentPositionIndex) { positions.first() }
    val noteSequence = remember(selectedKey, selectedScaleIndex, currentPositionIndex) {
        getPositionNoteSequence(selectedKey, scale.intervals, currentPosition)
    }
    val currentNote = noteSequence.getOrNull(currentNoteIndex)

    val density = LocalDensity.current
    val fretWidthDp = (60f * zoom).dp
    val openStringWidth = 48.dp
    val totalWidthDp = openStringWidth + (TOTAL_FRETS * 60 * zoom + 30).dp

    // BPM tick
    LaunchedEffect(isPlaying, isPaused, bpm, noteSequence) {
        if (!isPlaying || isPaused || noteSequence.isEmpty()) return@LaunchedEffect
        val intervalMs = 60_000L / bpm
        while (isPlaying && !isPaused) {
            delay(intervalMs)
            val nextIdx = currentNoteIndex + 1
            if (nextIdx >= noteSequence.size) {
                // Move to next CAGED position
                val nextPos = (currentPositionIndex + 1) % positions.size
                currentPositionIndex = nextPos
                currentNoteIndex = 0
            } else {
                currentNoteIndex = nextIdx
            }
        }
    }

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
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = { isPlaying = false; onBack() }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            // Key selector
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(COLOR_TONIC.copy(alpha = 0.3f))
                        .clickable { keyMenuExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(SCALE_NOTE_NAMES[selectedKey], color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = keyMenuExpanded, onDismissRequest = { keyMenuExpanded = false }) {
                    SCALE_NOTE_NAMES.forEachIndexed { i, n ->
                        DropdownMenuItem(text = { Text(n) }, onClick = {
                            selectedKey = i; keyMenuExpanded = false; currentNoteIndex = 0; currentPositionIndex = 0
                        })
                    }
                }
            }

            // Scale selector
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF5C6BC0).copy(alpha = 0.25f))
                        .clickable { scaleMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(scale.name, color = Color(0xFFB0BEC5), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = scaleMenuExpanded, onDismissRequest = { scaleMenuExpanded = false }) {
                    ALL_SCALES.forEachIndexed { i, s ->
                        DropdownMenuItem(text = { Text(s.name) }, onClick = {
                            selectedScaleIndex = i; scaleMenuExpanded = false; currentNoteIndex = 0; currentPositionIndex = 0
                        })
                    }
                }
            }

            // BPM control
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { bpm = (bpm - 5).coerceAtLeast(20) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) { Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                Text(" $bpm BPM ", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { bpm = (bpm + 5).coerceAtMost(240) }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) { Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
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
                modifier = Modifier.size(40.dp)
            ) {
                val icon = if (isPlaying && !isPaused) Icons.Default.Pause else Icons.Default.PlayArrow
                val tint = if (isPlaying && !isPaused) Color(0xFFFF9800) else Color(0xFF4CAF50)
                Icon(icon, "Play/Pause", tint = tint, modifier = Modifier.size(26.dp))
            }
            if (isPlaying) {
                IconButton(
                    onClick = { isPlaying = false; isPaused = false; currentNoteIndex = 0; currentPositionIndex = 0 },
                    modifier = Modifier.size(40.dp)
                ) {
                    Icon(Icons.Default.Stop, "Stop", tint = Color(0xFFF44336), modifier = Modifier.size(22.dp))
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            // CAGED position indicators
            positions.forEachIndexed { i, pos ->
                val isCurrent = i == currentPositionIndex
                val color = CAGED_COLORS[pos.cagedLetter] ?: Color.Gray
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isCurrent) color else color.copy(alpha = 0.2f))
                        .clickable {
                            currentPositionIndex = i; currentNoteIndex = 0
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        "${pos.cagedLetter}",
                        color = if (isCurrent) Color.White else color.copy(alpha = 0.6f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Info bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252525))
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val posColor = CAGED_COLORS[currentPosition.cagedLetter] ?: Color.Gray
            Text(
                "CAGED: ${currentPosition.name} (trastes ${currentPosition.startFret}-${currentPosition.endFret})",
                color = posColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )
            if (currentNote != null) {
                Text(
                    "Nota: ${getSpanishNoteName(currentNote.noteIndex)} | Cuerda ${6 - currentNote.string} | Traste ${currentNote.fret}",
                    color = Color(0xFFFFD600),
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            Text(
                "${currentNoteIndex + 1}/${noteSequence.size}",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
        }

        // Fretboard
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
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
                    openStringWidthPx = with(density) { openStringWidth.toPx() }
                )
            }
        }
    }
}

private fun DrawScope.drawCagedFretboard(
    rootNote: Int,
    scale: Scale,
    position: ScalePosition,
    currentNote: FretboardNote?,
    fretWidthPx: Float,
    openStringWidthPx: Float
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

    // Wood background
    drawRoundRect(
        color = COLOR_WOOD,
        topLeft = Offset(nutX, fbTop - 4f),
        size = Size(size.width - nutX, fbHeight + 8f),
        cornerRadius = CornerRadius(3f)
    )

    // Position highlight
    val startX = if (position.startFret == 0) nutX else nutX + nutWidth + (position.startFret - 1) * fretWidthPx
    val endX = nutX + nutWidth + position.endFret * fretWidthPx
    drawRect(
        color = Color(0x22FFD54F),
        topLeft = Offset(startX, fbTop - 4f),
        size = Size(endX - startX, fbHeight + 8f)
    )

    // Nut
    drawRect(color = COLOR_NUT, topLeft = Offset(nutX, fbTop - 6f), size = Size(nutWidth, fbHeight + 12f))

    // Fret wires
    for (fret in 1..TOTAL_FRETS) {
        val x = nutX + nutWidth + fret * fretWidthPx
        drawLine(COLOR_FRET_WIRE, Offset(x, fbTop - 2f), Offset(x, fbBottom + 2f), strokeWidth = 2.5f)
    }

    // Inlay markers
    val singleDots = listOf(3, 5, 7, 9, 15, 17, 19, 21)
    val doubleDots = listOf(12)
    val dotRadius = (fretWidthPx * 0.08f).coerceIn(4f, 12f)
    for (fret in singleDots) {
        if (fret > TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        val cy = fbBottom + bottomPad * 0.5f
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, cy))
    }
    for (fret in doubleDots) {
        if (fret > TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.3f))
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.7f))
    }

    // Fret numbers
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

    // Strings
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (s + 1)
        drawLine(STRING_COLORS[s], Offset(nutX, y), Offset(size.width, y), strokeWidth = STRING_WIDTHS[s])
    }

    // Open string labels
    val openPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 240, 240, 240)
        textSize = 36f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (s + 1)
        drawContext.canvas.nativeCanvas.drawText(OPEN_STRING_NAMES[s], nutX * 0.5f, y + 12f, openPaint)
    }

    val noteRadius = (stringSpacing * 0.44f).coerceIn(36f, 80f)
    val notePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    // Draw scale notes in position
    for (s in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val y = fbTop + stringSpacing * (s + 1)

        for (fret in position.startFret..position.endFret) {
            val noteIdx = (openNote + fret) % 12
            if (!isNoteInScale(noteIdx, rootNote, scale.intervals)) continue

            val degree = getDegreeInScale(noteIdx, rootNote, scale.intervals) ?: continue
            val cx = if (fret == 0) nutX * 0.5f else nutX + nutWidth + (fret - 0.5f) * fretWidthPx

            val isCurrentNote = currentNote != null && currentNote.string == s && currentNote.fret == fret

            val noteColor = when (degree) {
                1 -> COLOR_TONIC
                3 -> COLOR_THIRD
                5 -> COLOR_FIFTH
                else -> COLOR_OTHER
            }

            val r = if (isCurrentNote) noteRadius * 1.3f else noteRadius
            val alpha = if (isCurrentNote) 1f else 0.5f

            // Shadow
            drawCircle(Color(0x55000000), r + 3f, Offset(cx + 1.5f, y + 2f))
            // Main circle
            drawCircle(noteColor.copy(alpha = alpha), r, Offset(cx, y))
            // Border
            drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(2f))

            // Highlight ring for current note
            if (isCurrentNote) {
                drawCircle(COLOR_HIGHLIGHT, r + 8f, Offset(cx, y), style = Stroke(5f))
                drawCircle(COLOR_HIGHLIGHT.copy(alpha = 0.3f), r + 16f, Offset(cx, y), style = Stroke(3f))
            }

            // Label
            val label = getSpanishNoteName(noteIdx)
            notePaint.color = if (isCurrentNote) android.graphics.Color.WHITE
            else android.graphics.Color.argb(180, 255, 255, 255)
            drawContext.canvas.nativeCanvas.drawText(label, cx, y + notePaint.textSize * 0.35f, notePaint)
        }
    }
}
