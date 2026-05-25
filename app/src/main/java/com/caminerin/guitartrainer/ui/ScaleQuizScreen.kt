package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlin.math.sqrt

private const val TOTAL_FRETS = 22

private val COLOR_BG = Color(0xFF1A1A1A)
private val COLOR_TOOLBAR = Color(0xFF1E1E1E)
private val COLOR_WOOD = Color(0xFF3E2415)
private val COLOR_NUT = Color(0xFFF0EAD6)
private val COLOR_FRET_WIRE = Color(0xFFBBBBBB)
private val COLOR_INLAY = Color(0xFFCCC4B0)
private val COLOR_CORRECT = Color(0xFF4CAF50)
private val COLOR_ERROR = Color(0xFFF44336)
private val COLOR_TONIC_Q = Color(0xFFE53935)

private val STRING_COLORS_Q = listOf(
    Color(0xFFB0A080), Color(0xFFB8A888), Color(0xFFC0B090),
    Color(0xFFD0C4B0), Color(0xFFD8D0C0), Color(0xFFE0D8C8)
)
private val STRING_WIDTHS_Q = listOf(5.0f, 4.2f, 3.5f, 2.4f, 1.8f, 1.3f)

data class QuizHitTarget(
    val string: Int,
    val fret: Int,
    val noteIndex: Int,
    val cx: Float,
    val cy: Float,
    val radius: Float,
    val isInScale: Boolean
)

data class QuizResult(
    val string: Int,
    val fret: Int,
    val correct: Boolean
)

@Composable
fun ScaleQuizScreen(onBack: () -> Unit) {
    var selectedKey by rememberSaveable { mutableIntStateOf(0) }
    var selectedScaleIndex by rememberSaveable { mutableIntStateOf(0) }
    var maxFret by rememberSaveable { mutableIntStateOf(12) }
    var zoom by remember { mutableFloatStateOf(1.5f) }

    var keyMenuExpanded by remember { mutableStateOf(false) }
    var scaleMenuExpanded by remember { mutableStateOf(false) }
    var fretMenuExpanded by remember { mutableStateOf(false) }

    val revealedNotes = remember { mutableStateListOf<Pair<Int, Int>>() } // (string, fret) correctly found
    val errorFlash = remember { mutableStateOf<Pair<Float, Float>?>(null) } // (cx, cy) of error
    var correctCount by remember { mutableIntStateOf(0) }
    var errorCount by remember { mutableIntStateOf(0) }

    val scale = ALL_SCALES[selectedScaleIndex]
    val density = LocalDensity.current
    val fretWidthDp = (60f * zoom).dp
    val openStringWidth = 48.dp
    val totalWidthDp = openStringWidth + (TOTAL_FRETS * 60 * zoom + 30).dp

    // Count total notes in scale within fret range
    val totalScaleNotes = remember(selectedKey, selectedScaleIndex, maxFret) {
        var count = 0
        for (s in 0 until 6) {
            for (fret in 0..maxFret) {
                val noteIdx = (STANDARD_TUNING_MIDI[s] + fret) % 12
                if (isNoteInScale(noteIdx, selectedKey, scale.intervals)) count++
            }
        }
        count
    }

    // Clear error flash after delay
    val currentError = errorFlash.value
    if (currentError != null) {
        androidx.compose.runtime.LaunchedEffect(currentError) {
            delay(400)
            errorFlash.value = null
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
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            // Key selector
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(COLOR_TONIC_Q.copy(alpha = 0.3f))
                        .clickable { keyMenuExpanded = true }
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                ) {
                    Text(SCALE_NOTE_NAMES[selectedKey], color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = keyMenuExpanded, onDismissRequest = { keyMenuExpanded = false }) {
                    SCALE_NOTE_NAMES.forEachIndexed { i, n ->
                        DropdownMenuItem(text = { Text(n) }, onClick = {
                            selectedKey = i; keyMenuExpanded = false
                            revealedNotes.clear(); correctCount = 0; errorCount = 0
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
                            selectedScaleIndex = i; scaleMenuExpanded = false
                            revealedNotes.clear(); correctCount = 0; errorCount = 0
                        })
                    }
                }
            }

            // Max fret selector
            Box {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color.White.copy(alpha = 0.08f))
                        .clickable { fretMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text("Traste $maxFret", color = Color(0xFF90A4AE), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = fretMenuExpanded, onDismissRequest = { fretMenuExpanded = false }) {
                    listOf(5, 7, 9, 12, 15, 17, 22).forEach { f ->
                        DropdownMenuItem(text = { Text("Hasta traste $f") }, onClick = {
                            maxFret = f; fretMenuExpanded = false
                            revealedNotes.clear(); correctCount = 0; errorCount = 0
                        })
                    }
                }
            }

            // Reset button
            IconButton(
                onClick = { revealedNotes.clear(); correctCount = 0; errorCount = 0 },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.Refresh, "Reset", tint = Color.White, modifier = Modifier.size(20.dp))
            }

            Spacer(modifier = Modifier.weight(1f))

            // Score
            Text(
                "${revealedNotes.size}/$totalScaleNotes",
                color = COLOR_CORRECT,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            if (errorCount > 0) {
                Text(
                    "${errorCount} err",
                    color = COLOR_ERROR,
                    fontSize = 14.sp
                )
            }
        }

        // Fretboard with tap detection
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
        ) {
            val hitTargets = remember { mutableStateListOf<QuizHitTarget>() }

            Canvas(
                modifier = Modifier
                    .width(totalWidthDp)
                    .fillMaxHeight()
                    .pointerInput(selectedKey, selectedScaleIndex, maxFret) {
                        detectTapGestures { tapOffset ->
                            // Find closest fret position to tap
                            var closestDist = Float.MAX_VALUE
                            var closestTarget: QuizHitTarget? = null
                            for (target in hitTargets) {
                                val dx = tapOffset.x - target.cx
                                val dy = tapOffset.y - target.cy
                                val d = sqrt(dx * dx + dy * dy)
                                if (d < target.radius * 1.5f && d < closestDist) {
                                    closestDist = d
                                    closestTarget = target
                                }
                            }
                            if (closestTarget != null) {
                                val key = closestTarget.string to closestTarget.fret
                                if (revealedNotes.contains(key)) return@detectTapGestures
                                if (closestTarget.isInScale) {
                                    revealedNotes.add(key)
                                    correctCount++
                                } else {
                                    errorCount++
                                    errorFlash.value = closestTarget.cx to closestTarget.cy
                                }
                            }
                        }
                    }
            ) {
                hitTargets.clear()
                drawQuizFretboard(
                    rootNote = selectedKey,
                    scale = scale,
                    maxFret = maxFret,
                    revealedNotes = revealedNotes.toSet(),
                    errorFlashPos = errorFlash.value,
                    fretWidthPx = with(density) { fretWidthDp.toPx() },
                    openStringWidthPx = with(density) { openStringWidth.toPx() },
                    hitTargets = hitTargets
                )
            }
        }
    }
}

private fun DrawScope.drawQuizFretboard(
    rootNote: Int,
    scale: Scale,
    maxFret: Int,
    revealedNotes: Set<Pair<Int, Int>>,
    errorFlashPos: Pair<Float, Float>?,
    fretWidthPx: Float,
    openStringWidthPx: Float,
    hitTargets: MutableList<QuizHitTarget>
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

    // Playable area highlight
    val endX = nutX + nutWidth + maxFret * fretWidthPx
    drawRect(
        color = Color(0x11FFFFFF),
        topLeft = Offset(nutX, fbTop - 4f),
        size = Size(endX - nutX, fbHeight + 8f)
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
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.5f))
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
        drawLine(STRING_COLORS_Q[s], Offset(nutX, y), Offset(size.width, y), strokeWidth = STRING_WIDTHS_Q[s])
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

    // Hit targets and revealed notes
    for (s in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val y = fbTop + stringSpacing * (s + 1)

        for (fret in 0..maxFret) {
            val noteIdx = (openNote + fret) % 12
            val inScale = isNoteInScale(noteIdx, rootNote, scale.intervals)
            val cx = if (fret == 0) nutX * 0.5f else nutX + nutWidth + (fret - 0.5f) * fretWidthPx
            val key = s to fret

            hitTargets.add(QuizHitTarget(s, fret, noteIdx, cx, y, noteRadius, inScale))

            if (revealedNotes.contains(key) && inScale) {
                val degree = getDegreeInScale(noteIdx, rootNote, scale.intervals) ?: 1
                val noteColor = when (degree) {
                    1 -> COLOR_TONIC_Q
                    3 -> Color(0xFF1E88E5)
                    5 -> Color(0xFF43A047)
                    else -> Color(0xFF26A69A)
                }
                drawCircle(Color(0x55000000), noteRadius + 3f, Offset(cx + 1.5f, y + 2f))
                drawCircle(noteColor, noteRadius, Offset(cx, y))
                drawCircle(Color(0x44000000), noteRadius, Offset(cx, y), style = Stroke(2f))
                val label = getSpanishNoteName(noteIdx)
                drawContext.canvas.nativeCanvas.drawText(label, cx, y + notePaint.textSize * 0.35f, notePaint)
            } else {
                // Small dot to indicate tappable area
                if (fret <= maxFret && fret > 0) {
                    drawCircle(Color.White.copy(alpha = 0.06f), noteRadius * 0.5f, Offset(cx, y))
                }
            }
        }
    }

    // Error flash
    if (errorFlashPos != null) {
        drawCircle(COLOR_ERROR.copy(alpha = 0.6f), noteRadius * 1.3f, Offset(errorFlashPos.first, errorFlashPos.second))
        drawCircle(COLOR_ERROR, noteRadius * 1.3f, Offset(errorFlashPos.first, errorFlashPos.second), style = Stroke(4f))

        val xPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.WHITE
            textSize = 48f
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = true
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText("\u2717", errorFlashPos.first, errorFlashPos.second + 16f, xPaint)
    }
}
