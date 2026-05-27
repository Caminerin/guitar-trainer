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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
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
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private const val TOTAL_FRETS = 22

// Note colors by degree in scale
private val COLOR_TONIC = Color(0xFFE53935)      // red - root
private val COLOR_THIRD = Color(0xFF1E88E5)       // blue - 3rd
private val COLOR_FIFTH = Color(0xFF43A047)       // green - 5th
private val COLOR_OTHER = Color(0xFF26A69A)       // teal - other degrees
private val COLOR_DIM = Color(0xFF78909C)         // muted for out-of-position

// Fretboard aesthetic
private val COLOR_BG = Color(0xFF1A1A1A)
private val COLOR_TOOLBAR = Color(0xFF1E1E1E)
private val COLOR_WOOD = Color(0xFF3E2415)
private val COLOR_NUT = Color(0xFFF0EAD6)
private val COLOR_FRET_WIRE = Color(0xFFBBBBBB)
private val COLOR_INLAY = Color(0xFFCCC4B0)

private val STRING_COLORS = listOf(
    Color(0xFFB0A080), Color(0xFFB8A888), Color(0xFFC0B090),
    Color(0xFFD0C4B0), Color(0xFFD8D0C0), Color(0xFFE0D8C8)
)
private val STRING_WIDTHS = listOf(5.0f, 4.2f, 3.5f, 2.4f, 1.8f, 1.3f)



@Composable
fun ScaleFretboardScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var selectedKey by rememberSaveable { mutableIntStateOf(AppPreferences.lastKey) }
    var selectedScaleIndex by rememberSaveable { mutableIntStateOf(AppPreferences.lastScaleIndex.coerceIn(0, ALL_SCALES.size - 1)) }
    var noteDisplay by rememberSaveable { mutableStateOf(NoteDisplay.BOTH) }
    var positionsEnabled by rememberSaveable { mutableStateOf(false) }
    var currentPosition by rememberSaveable { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1.5f) }

    var showScaleSelector by remember { mutableStateOf(false) }
    var showDisplaySelector by remember { mutableStateOf(false) }
    var showColorSelector by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    // State for chromatic circle overlay on key selection
    var showChromaticCircle by remember { mutableStateOf(false) }

    // Load color preferences
    LaunchedEffect(Unit) { DegreeColorPrefs.load(context) }

    val scale = ALL_SCALES[selectedScaleIndex]
    val positions = if (scale.hasCaged) computeCagedPositions(selectedKey) else scale.positions
    val density = LocalDensity.current
    val fretWidthDp = (60f * zoom).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(COLOR_BG)
    ) {
        // ===== REDESIGNED TOOLBAR =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(COLOR_TOOLBAR)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Back button
            IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(22.dp))
            }

            // Key selector - opens chromatic circle
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(CHROMATIC_COLORS[selectedKey].copy(alpha = 0.4f))
                    .clickable { showChromaticCircle = true }
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    getChromaticNames(selectedKey, scale.relativeMajorOffset)[selectedKey],
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Scale selector -> overlay
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(Color(0xFF5C6BC0).copy(alpha = 0.25f))
                    .clickable { showScaleSelector = true }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Text(scale.name, color = Color(0xFFB0BEC5), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            // Display mode
            NoteDisplayToolbarButton(noteDisplay) { showDisplaySelector = true }

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

            // Info button
            IconButton(onClick = { showInfo = true }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.Info, "Info", tint = Color(0xFF90A4AE), modifier = Modifier.size(20.dp))
            }

            // Position toggle + CAGED bar
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .background(if (positionsEnabled) COLOR_TONIC.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                    .clickable { positionsEnabled = !positionsEnabled }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text("Posiciones", color = if (positionsEnabled) Color.White else Color(0xFF90A4AE), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.weight(1f))
        }

        // ===== POSITION BAR (separate row) =====
        if (positionsEnabled && positions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(COLOR_TOOLBAR)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CagedPositionBar(
                    positions = positions,
                    currentIndex = currentPosition,
                    onSelect = { currentPosition = it }
                )
            }
        }

        // ===== DEGREE COLOR LEGEND =====
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF252525))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(modifier = Modifier.weight(1f))

            // Zoom controls
            IconButton(onClick = { zoom = (zoom - 0.3f).coerceAtLeast(0.5f) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ZoomOut, "Alejar", tint = Color.White, modifier = Modifier.size(18.dp))
            }
            IconButton(onClick = { zoom = (zoom + 0.3f).coerceAtMost(3f) }, modifier = Modifier.size(32.dp)) {
                Icon(Icons.Default.ZoomIn, "Acercar", tint = Color.White, modifier = Modifier.size(18.dp))
            }
        }

        // ===== FRETBOARD =====
        val openStringWidth = 48.dp
        val totalWidthDp = openStringWidth + (TOTAL_FRETS * 60 * zoom + 30).dp
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
                    drawGuitarFretboard(
                        rootNote = selectedKey,
                        scale = scale,
                        positions = positions,
                        noteDisplay = noteDisplay,
                        positionsEnabled = positionsEnabled,
                        currentPosition = currentPosition,
                        fretWidthPx = with(density) { fretWidthDp.toPx() },
                        openStringWidthPx = with(density) { openStringWidth.toPx() }
                    )
                }
            }

        }
    }

    // Chromatic circle overlay
    if (showChromaticCircle) {
        ChromaticCircleOverlay(
            selectedNote = selectedKey,
            rootNote = selectedKey,
            scaleIntervals = scale.intervals,
            onNoteSelected = { selectedKey = it; AppPreferences.saveKey(it, context); showChromaticCircle = false },
            onDismiss = { showChromaticCircle = false },
            relativeMajorOffset = scale.relativeMajorOffset
        )
    }

    // Scale selector overlay
    if (showScaleSelector) {
        ScaleSelectorOverlay(
            currentIndex = selectedScaleIndex,
            onSelected = { selectedScaleIndex = it; currentPosition = 0; AppPreferences.saveScale(it, context); showScaleSelector = false },
            onDismiss = { showScaleSelector = false }
        )
    }

    // Display mode overlay
    if (showDisplaySelector) {
        NoteDisplaySelectorOverlay(
            current = noteDisplay,
            onSelect = { noteDisplay = it },
            onDismiss = { showDisplaySelector = false }
        )
    }

    // Color selector overlay
    if (showColorSelector) {
        ScaleColorSelectorOverlay(
            context = context,
            onDismiss = { showColorSelector = false }
        )
    }

    if (showInfo) {
        ScaleInfoSheet(
            rootNote = selectedKey,
            scale = scale,
            onDismiss = { showInfo = false }
        )
    }
}

private fun DrawScope.drawGuitarFretboard(
    rootNote: Int,
    scale: Scale,
    positions: List<ScalePosition>,
    noteDisplay: NoteDisplay,
    positionsEnabled: Boolean,
    currentPosition: Int,
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
    for (i in 0..4) {
        val yOff = fbTop + fbHeight * (0.15f + i * 0.17f)
        drawRect(
            color = Color(0x0CFFFFFF),
            topLeft = Offset(nutX, yOff),
            size = Size(size.width - nutX, fbHeight * 0.04f)
        )
    }

    // Position highlight
    if (positionsEnabled && positions.isNotEmpty()) {
        val pos = positions[currentPosition]
        val startX = if (pos.startFret == 0) nutX else nutX + nutWidth + (pos.startFret - 1) * fretWidthPx
        val endX = nutX + nutWidth + pos.endFret * fretWidthPx
        drawRect(
            color = Color(0x22FFD54F),
            topLeft = Offset(startX, fbTop - 4f),
            size = Size(endX - startX, fbHeight + 8f)
        )
    }

    // Nut
    drawRect(color = COLOR_NUT, topLeft = Offset(nutX, fbTop - 6f), size = Size(nutWidth, fbHeight + 12f))
    drawRect(color = Color(0x33000000), topLeft = Offset(nutX + nutWidth, fbTop - 6f), size = Size(3f, fbHeight + 12f))

    // Fret wires
    for (fret in 1..TOTAL_FRETS) {
        val x = nutX + nutWidth + fret * fretWidthPx
        drawLine(Color(0x33000000), Offset(x + 1.5f, fbTop - 2f), Offset(x + 1.5f, fbBottom + 2f), strokeWidth = 3f)
        drawLine(COLOR_FRET_WIRE, Offset(x, fbTop - 2f), Offset(x, fbBottom + 2f), strokeWidth = 2.5f)
        drawLine(Color(0x33FFFFFF), Offset(x - 0.5f, fbTop), Offset(x - 0.5f, fbBottom), strokeWidth = 0.5f)
    }

    // Inlay markers
    val singleDots = listOf(3, 5, 7, 9, 15, 17, 19, 21)
    val doubleDots = listOf(12)
    val dotRadius = (fretWidthPx * 0.08f).coerceIn(4f, 12f)

    for (fret in singleDots) {
        if (fret > TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        val cy = fbBottom + bottomPad * 0.5f
        drawCircle(Color(0xFF222222), dotRadius + 1f, Offset(cx, cy))
        drawCircle(COLOR_INLAY, dotRadius, Offset(cx, cy))
    }
    for (fret in doubleDots) {
        if (fret > TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        val cy1 = fbBottom + bottomPad * 0.3f
        val cy2 = fbBottom + bottomPad * 0.7f
        for (cy in listOf(cy1, cy2)) {
            drawCircle(Color(0xFF222222), dotRadius + 1f, Offset(cx, cy))
            drawCircle(COLOR_INLAY, dotRadius, Offset(cx, cy))
        }
    }

    // Fret numbers - 3x bigger
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

    // Strings (6th=bottom, 1st=top like tablature)
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawLine(Color(0x33000000), Offset(nutX, y + 1f), Offset(size.width, y + 1f), strokeWidth = STRING_WIDTHS[s] + 1f)
        drawLine(STRING_COLORS[s], Offset(nutX, y), Offset(size.width, y), strokeWidth = STRING_WIDTHS[s])
        drawLine(Color(0x22FFFFFF), Offset(nutX, y - STRING_WIDTHS[s] * 0.3f), Offset(size.width, y - STRING_WIDTHS[s] * 0.3f), strokeWidth = 0.5f)
    }

    // Open string labels
    val openPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(220, 240, 240, 240)
        textSize = 72f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawContext.canvas.nativeCanvas.drawText(getOpenStringNames()[s], nutX * 0.5f, y + 24f, openPaint)
    }

    // Position range
    val posStart = if (positionsEnabled && positions.isNotEmpty()) positions[currentPosition].startFret else 0
    val posEnd = if (positionsEnabled && positions.isNotEmpty()) positions[currentPosition].endFret else TOTAL_FRETS

    // Note text paints - 3x bigger
    val notePaintBig = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    val notePaintSmall = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 36f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    // Note radius - 3x bigger
    val noteRadius = (stringSpacing * 0.44f).coerceIn(36f, 80f)

    for (s in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val y = fbTop + stringSpacing * (6 - s)

        for (fret in 0..TOTAL_FRETS) {
            val noteIdx = (openNote + fret) % 12
            if (!isNoteInScale(noteIdx, rootNote, scale.intervals)) continue

            val degree = getDegreeInScale(noteIdx, rootNote, scale.intervals) ?: continue
            val isInPos = fret in posStart..posEnd

            val cx = if (fret == 0) {
                nutX * 0.5f
            } else {
                nutX + nutWidth + (fret - 0.5f) * fretWidthPx
            }

            if (positionsEnabled && !isInPos) {
                val dimR = noteRadius * 0.65f
                drawCircle(COLOR_DIM.copy(alpha = 0.35f), dimR, Offset(cx, y))
                if (noteDisplay != NoteDisplay.NONE) {
                    val lbl = buildNoteLabel(noteIdx, degree, noteDisplay, rootNote, scale.relativeMajorOffset)
                    notePaintSmall.color = android.graphics.Color.argb(100, 255, 255, 255)
                    notePaintSmall.textSize = 28f
                    drawContext.canvas.nativeCanvas.drawText(lbl, cx, y + 10f, notePaintSmall)
                    notePaintSmall.color = android.graphics.Color.WHITE
                    notePaintSmall.textSize = 36f
                }
                continue
            }

            val isFiltered = DegreeColorPrefs.isScaleEnabled(degree)
            val noteColor = DegreeColorPrefs.getScaleColor(degree)
            val r = if (degree == 1 && isFiltered) noteRadius * 1.1f else if (!isFiltered) noteRadius * 0.7f else noteRadius

            drawCircle(Color(0x55000000), r + 3f, Offset(cx + 1.5f, y + 2f))
            drawCircle(noteColor, r, Offset(cx, y))
            drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(2f))

            if (noteDisplay != NoteDisplay.NONE && isFiltered) {
                val label = buildNoteLabel(noteIdx, degree, noteDisplay, rootNote, scale.relativeMajorOffset)
                val paint = if (label.length > 4) notePaintSmall else notePaintBig
                drawContext.canvas.nativeCanvas.drawText(label, cx, y + paint.textSize * 0.35f, paint)
            }
        }
    }
}

private fun buildNoteLabel(noteIdx: Int, degree: Int, display: NoteDisplay, rootNote: Int = -1, relativeMajorOffset: Int = 0): String {
    val noteName = getSpanishNoteName(noteIdx, rootNote, relativeMajorOffset)
    val degreeStr = getDegreeLabel(degree)
    return when (display) {
        NoteDisplay.NOTE -> noteName
        NoteDisplay.DEGREE -> degreeStr
        NoteDisplay.BOTH -> "$degreeStr $noteName"
        NoteDisplay.FINGERING -> noteName
        NoteDisplay.NONE -> ""
    }
}

@Composable
private fun ColorPreviewChip(label: String, color: Color, active: Boolean, onClick: () -> Unit) {
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
