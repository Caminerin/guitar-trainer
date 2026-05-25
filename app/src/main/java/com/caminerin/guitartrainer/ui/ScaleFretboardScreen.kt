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
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.ZoomOut
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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

private const val TOTAL_FRETS = 22

private val COLOR_TONIC = Color(0xFFE53935)
private val COLOR_THIRD = Color(0xFF1E88E5)
private val COLOR_FIFTH = Color(0xFF43A047)
private val COLOR_OTHER = Color(0xFF9E9E9E)
private val COLOR_POSITION_DIM = Color(0x44888888)

private val COLOR_BG = Color(0xFF121212)
private val COLOR_NUT = Color(0xFFF5F0E0)
private val COLOR_FRET_WIRE = Color(0xFFB0B0B0)
private val COLOR_INLAY = Color(0xFFD4C8B0)

private val STRING_COLORS = listOf(
    Color(0xFFB0A080), Color(0xFFB8A888), Color(0xFFC0B090),
    Color(0xFFD0C4B0), Color(0xFFD8D0C0), Color(0xFFE0D8C8)
)
private val STRING_WIDTHS = listOf(5.5f, 4.5f, 3.8f, 2.5f, 2.0f, 1.5f)

@Composable
fun ScaleFretboardScreen(onBack: () -> Unit) {
    var selectedKey by remember { mutableIntStateOf(0) }
    var selectedScaleIndex by remember { mutableIntStateOf(0) }
    var noteDisplay by remember { mutableStateOf(NoteDisplay.NOTE) }
    var positionsEnabled by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1.5f) }

    var keyMenuExpanded by remember { mutableStateOf(false) }
    var scaleMenuExpanded by remember { mutableStateOf(false) }
    var displayMenuExpanded by remember { mutableStateOf(false) }

    val scale = ALL_SCALES[selectedScaleIndex]
    val density = LocalDensity.current
    val fretWidthDp = (60f * zoom).dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(COLOR_BG)
    ) {
        // Compact top bar - all controls in one row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E1E1E))
                .padding(horizontal = 2.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(18.dp))
            }

            // Key selector chip
            Box {
                Box(
                    modifier = Modifier
                        .background(COLOR_TONIC.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable { keyMenuExpanded = true }
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(SCALE_NOTE_NAMES[selectedKey], color = COLOR_TONIC, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = keyMenuExpanded, onDismissRequest = { keyMenuExpanded = false }) {
                    SCALE_NOTE_NAMES.forEachIndexed { index, note ->
                        DropdownMenuItem(text = { Text(note) }, onClick = { selectedKey = index; keyMenuExpanded = false })
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Scale selector chip
            Box {
                Box(
                    modifier = Modifier
                        .background(Color(0xFF5C6BC0).copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                        .clickable { scaleMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(scale.name.take(16), color = Color(0xFF7986CB), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = scaleMenuExpanded, onDismissRequest = { scaleMenuExpanded = false }) {
                    ALL_SCALES.forEachIndexed { index, s ->
                        DropdownMenuItem(text = { Text(s.name) }, onClick = { selectedScaleIndex = index; scaleMenuExpanded = false; currentPosition = 0 })
                    }
                }
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Display selector chip
            Box {
                val displayLabel = when (noteDisplay) {
                    NoteDisplay.NOTE -> "Nota"
                    NoteDisplay.DEGREE -> "Grado"
                    NoteDisplay.BOTH -> "N+G"
                    NoteDisplay.NONE -> "\u2205"
                }
                Box(
                    modifier = Modifier
                        .background(Color(0xFF666666).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .clickable { displayMenuExpanded = true }
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(displayLabel, color = Color(0xFFAAAAAA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = displayMenuExpanded, onDismissRequest = { displayMenuExpanded = false }) {
                    listOf(NoteDisplay.NOTE to "Nota", NoteDisplay.DEGREE to "Grado", NoteDisplay.BOTH to "Ambos", NoteDisplay.NONE to "Nada").forEach { (d, l) ->
                        DropdownMenuItem(text = { Text(l) }, onClick = { noteDisplay = d; displayMenuExpanded = false })
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            IconButton(onClick = { zoom = (zoom - 0.3f).coerceAtLeast(1f) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ZoomOut, "Alejar", tint = Color.White, modifier = Modifier.size(16.dp))
            }
            IconButton(onClick = { zoom = (zoom + 0.3f).coerceAtMost(3f) }, modifier = Modifier.size(30.dp)) {
                Icon(Icons.Default.ZoomIn, "Acercar", tint = Color.White, modifier = Modifier.size(16.dp))
            }
        }

        // Position row - ultra compact
        if (scale.positions.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF181818))
                    .padding(horizontal = 8.dp, vertical = 0.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Text("Pos", color = Color.White.copy(alpha = 0.4f), fontSize = 10.sp)
                Spacer(modifier = Modifier.width(4.dp))
                Switch(
                    checked = positionsEnabled,
                    onCheckedChange = { positionsEnabled = it },
                    modifier = Modifier.height(24.dp),
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = COLOR_TONIC,
                        checkedTrackColor = COLOR_TONIC.copy(alpha = 0.3f)
                    )
                )
                if (positionsEnabled) {
                    Spacer(modifier = Modifier.width(6.dp))
                    IconButton(
                        onClick = { currentPosition = (currentPosition - 1 + scale.positions.size) % scale.positions.size },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowLeft, "Anterior", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                    Text(scale.positions[currentPosition].name, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    IconButton(
                        onClick = { currentPosition = (currentPosition + 1) % scale.positions.size },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(Icons.Default.KeyboardArrowRight, "Siguiente", tint = Color.White, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        // Fretboard - fills all remaining space
        val totalWidthDp = (TOTAL_FRETS * 60 * zoom + 30).dp
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
                drawRealisticFretboard(
                    rootNote = selectedKey,
                    scale = scale,
                    noteDisplay = noteDisplay,
                    positionsEnabled = positionsEnabled,
                    currentPosition = currentPosition,
                    fretWidthPx = with(density) { fretWidthDp.toPx() }
                )
            }
        }
    }
}

private fun DrawScope.drawRealisticFretboard(
    rootNote: Int,
    scale: Scale,
    noteDisplay: NoteDisplay,
    positionsEnabled: Boolean,
    currentPosition: Int,
    fretWidthPx: Float
) {
    val h = size.height
    val topMargin = h * 0.06f
    val bottomMargin = h * 0.08f
    val fretboardTop = topMargin
    val fretboardBottom = h - bottomMargin
    val fretboardHeight = fretboardBottom - fretboardTop
    val stringSpacing = fretboardHeight / 5f
    val nutWidth = 18f

    // Wood background
    drawRoundRect(
        color = Color(0xFF3E2415),
        topLeft = Offset(0f, fretboardTop - 4f),
        size = Size(size.width, fretboardHeight + 8f),
        cornerRadius = CornerRadius(4f, 4f)
    )
    // Subtle wood grain overlay
    drawRoundRect(
        color = Color(0xFF4A2D1A),
        topLeft = Offset(0f, fretboardTop + fretboardHeight * 0.3f),
        size = Size(size.width, fretboardHeight * 0.15f),
        cornerRadius = CornerRadius(2f, 2f)
    )
    drawRoundRect(
        color = Color(0xFF35200E),
        topLeft = Offset(0f, fretboardTop + fretboardHeight * 0.6f),
        size = Size(size.width, fretboardHeight * 0.1f),
        cornerRadius = CornerRadius(2f, 2f)
    )

    // Position highlight
    if (positionsEnabled && scale.positions.isNotEmpty()) {
        val pos = scale.positions[currentPosition]
        val startX = if (pos.startFret == 0) 0f else nutWidth + (pos.startFret - 0.5f) * fretWidthPx
        val endX = nutWidth + (pos.endFret + 0.5f) * fretWidthPx
        drawRect(
            color = Color(0x22FFD54F),
            topLeft = Offset(startX, fretboardTop - 4f),
            size = Size(endX - startX, fretboardHeight + 8f)
        )
    }

    // Nut (cejilla) - thick bone-colored bar
    drawRect(
        color = COLOR_NUT,
        topLeft = Offset(nutWidth - 6f, fretboardTop - 6f),
        size = Size(8f, fretboardHeight + 12f)
    )
    // Nut shadow
    drawRect(
        color = Color(0x33000000),
        topLeft = Offset(nutWidth + 2f, fretboardTop - 6f),
        size = Size(3f, fretboardHeight + 12f)
    )

    // Fret wires
    for (fret in 1..TOTAL_FRETS) {
        val x = nutWidth + fret * fretWidthPx
        // Fret shadow
        drawLine(
            color = Color(0x33000000),
            start = Offset(x + 1.5f, fretboardTop - 2f),
            end = Offset(x + 1.5f, fretboardBottom + 2f),
            strokeWidth = 3f
        )
        // Fret wire
        drawLine(
            color = COLOR_FRET_WIRE,
            start = Offset(x, fretboardTop - 2f),
            end = Offset(x, fretboardBottom + 2f),
            strokeWidth = 3f
        )
        // Fret highlight
        drawLine(
            color = Color(0x44FFFFFF),
            start = Offset(x - 0.5f, fretboardTop - 2f),
            end = Offset(x - 0.5f, fretboardBottom + 2f),
            strokeWidth = 1f
        )
    }

    // Fret inlay markers
    val singleMarkers = listOf(3, 5, 7, 9, 15, 17, 19, 21)
    val doubleMarkers = listOf(12)
    val inlayRadius = fretWidthPx.coerceIn(10f, 20f) * 0.3f

    for (fret in singleMarkers) {
        if (fret > TOTAL_FRETS) continue
        val x = nutWidth + (fret - 0.5f) * fretWidthPx
        val y = fretboardTop + fretboardHeight * 0.5f
        // Inlay shadow
        drawCircle(Color(0x33000000), radius = inlayRadius + 1f, center = Offset(x + 1f, y + 1f))
        // Pearl inlay
        drawCircle(COLOR_INLAY, radius = inlayRadius, center = Offset(x, y))
        drawCircle(Color(0x22FFFFFF), radius = inlayRadius * 0.6f, center = Offset(x - inlayRadius * 0.2f, y - inlayRadius * 0.2f))
    }

    for (fret in doubleMarkers) {
        if (fret > TOTAL_FRETS) continue
        val x = nutWidth + (fret - 0.5f) * fretWidthPx
        val y1 = fretboardTop + fretboardHeight * 0.28f
        val y2 = fretboardTop + fretboardHeight * 0.72f
        for (y in listOf(y1, y2)) {
            drawCircle(Color(0x33000000), radius = inlayRadius + 1f, center = Offset(x + 1f, y + 1f))
            drawCircle(COLOR_INLAY, radius = inlayRadius, center = Offset(x, y))
            drawCircle(Color(0x22FFFFFF), radius = inlayRadius * 0.6f, center = Offset(x - inlayRadius * 0.2f, y - inlayRadius * 0.2f))
        }
    }

    // Strings
    for (string in 0 until 6) {
        val y = fretboardTop + string * stringSpacing
        val width = STRING_WIDTHS[string]
        val color = STRING_COLORS[string]
        // String shadow
        drawLine(
            color = Color(0x33000000),
            start = Offset(0f, y + 1f),
            end = Offset(size.width, y + 1f),
            strokeWidth = width + 1f
        )
        // String
        drawLine(
            color = color,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = width
        )
        // String highlight
        drawLine(
            color = Color(0x33FFFFFF),
            start = Offset(0f, y - width * 0.3f),
            end = Offset(size.width, y - width * 0.3f),
            strokeWidth = 0.5f
        )
    }

    // Fret numbers below fretboard
    val fretNumPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(120, 200, 200, 200)
        textSize = 22f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    for (fret in 1..TOTAL_FRETS) {
        val x = nutWidth + (fret - 0.5f) * fretWidthPx
        drawContext.canvas.nativeCanvas.drawText("$fret", x, fretboardBottom + bottomMargin * 0.7f, fretNumPaint)
    }

    // Position range
    val posStart = if (positionsEnabled && scale.positions.isNotEmpty()) scale.positions[currentPosition].startFret else 0
    val posEnd = if (positionsEnabled && scale.positions.isNotEmpty()) scale.positions[currentPosition].endFret else TOTAL_FRETS

    // Note label paint
    val notePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 20f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    val smallNotePaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 14f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    // Scale notes on strings
    val noteRadius = (stringSpacing * 0.32f).coerceIn(10f, 22f)

    for (string in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[string]
        val y = fretboardTop + string * stringSpacing

        for (fret in 0..TOTAL_FRETS) {
            val noteIndex = (openNote + fret) % 12
            if (!isNoteInScale(noteIndex, rootNote, scale.intervals)) continue

            val x = if (fret == 0) nutWidth * 0.4f else nutWidth + (fret - 0.5f) * fretWidthPx
            val degree = getDegreeInScale(noteIndex, rootNote, scale.intervals) ?: continue
            val isInPosition = fret in posStart..posEnd

            if (positionsEnabled && !isInPosition) {
                drawCircle(COLOR_POSITION_DIM, radius = noteRadius * 0.7f, center = Offset(x, y))
                continue
            }

            val noteColor = when (degree) {
                1 -> COLOR_TONIC
                3 -> COLOR_THIRD
                5 -> COLOR_FIFTH
                else -> COLOR_OTHER
            }

            val r = if (degree == 1) noteRadius * 1.1f else noteRadius

            // Note circle shadow
            drawCircle(Color(0x55000000), radius = r + 2f, center = Offset(x + 1f, y + 1f))
            // Note circle
            drawCircle(noteColor, radius = r, center = Offset(x, y))
            // Border
            drawCircle(Color(0x44000000), radius = r, center = Offset(x, y), style = Stroke(width = 1.5f))
            // Highlight
            drawCircle(Color(0x22FFFFFF), radius = r * 0.65f, center = Offset(x - r * 0.15f, y - r * 0.15f))

            // Label
            if (noteDisplay != NoteDisplay.NONE) {
                val label = when (noteDisplay) {
                    NoteDisplay.NOTE -> getNoteName(noteIndex)
                    NoteDisplay.DEGREE -> getDegreeLabel(degree)
                    NoteDisplay.BOTH -> getNoteName(noteIndex)
                    NoteDisplay.NONE -> ""
                }
                val paint = if (label.length > 2) smallNotePaint else notePaint
                drawContext.canvas.nativeCanvas.drawText(label, x, y + paint.textSize * 0.35f, paint)

                if (noteDisplay == NoteDisplay.BOTH) {
                    val degreeTxt = getDegreeLabel(degree)
                    drawContext.canvas.nativeCanvas.drawText(degreeTxt, x, y + r + smallNotePaint.textSize + 2f, smallNotePaint)
                }
            }
        }
    }
}
