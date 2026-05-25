package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val TOTAL_FRETS = 22
private val COLOR_TONIC = Color(0xFFE53935)
private val COLOR_THIRD = Color(0xFF1E88E5)
private val COLOR_FIFTH = Color(0xFF43A047)
private val COLOR_OTHER = Color(0xFF757575)
private val COLOR_POSITION_DIM = Color(0x44757575)

@Composable
fun ScaleFretboardScreen(onBack: () -> Unit) {
    var selectedKey by remember { mutableIntStateOf(0) }
    var selectedScaleIndex by remember { mutableIntStateOf(0) }
    var noteDisplay by remember { mutableStateOf(NoteDisplay.NOTE) }
    var positionsEnabled by remember { mutableStateOf(false) }
    var currentPosition by remember { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1.5f) }
    var scrollOffset by remember { mutableFloatStateOf(0f) }

    var keyMenuExpanded by remember { mutableStateOf(false) }
    var scaleMenuExpanded by remember { mutableStateOf(false) }
    var displayMenuExpanded by remember { mutableStateOf(false) }

    val scale = ALL_SCALES[selectedScaleIndex]

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
            }
            Text(
                "Escalas",
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
        }

        // Controls row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Key selector
            Box {
                OutlinedButton(onClick = { keyMenuExpanded = true }) {
                    Text(ALL_NOTES[selectedKey], color = Color.White, fontWeight = FontWeight.Bold)
                }
                DropdownMenu(expanded = keyMenuExpanded, onDismissRequest = { keyMenuExpanded = false }) {
                    ALL_NOTES.forEachIndexed { index, note ->
                        DropdownMenuItem(
                            text = { Text(note) },
                            onClick = { selectedKey = index; keyMenuExpanded = false }
                        )
                    }
                }
            }

            // Scale selector
            Box {
                OutlinedButton(onClick = { scaleMenuExpanded = true }) {
                    Text(
                        scale.name.take(12),
                        color = Color.White,
                        fontSize = 11.sp
                    )
                }
                DropdownMenu(expanded = scaleMenuExpanded, onDismissRequest = { scaleMenuExpanded = false }) {
                    ALL_SCALES.forEachIndexed { index, s ->
                        DropdownMenuItem(
                            text = { Text(s.name) },
                            onClick = { selectedScaleIndex = index; scaleMenuExpanded = false; currentPosition = 0 }
                        )
                    }
                }
            }

            // Display selector
            Box {
                OutlinedButton(onClick = { displayMenuExpanded = true }) {
                    val label = when (noteDisplay) {
                        NoteDisplay.NOTE -> "Nota"
                        NoteDisplay.DEGREE -> "Grado"
                        NoteDisplay.BOTH -> "Ambos"
                        NoteDisplay.NONE -> "—"
                    }
                    Text(label, color = Color.White, fontSize = 12.sp)
                }
                DropdownMenu(expanded = displayMenuExpanded, onDismissRequest = { displayMenuExpanded = false }) {
                    listOf(
                        NoteDisplay.NOTE to "Nota",
                        NoteDisplay.DEGREE to "Grado",
                        NoteDisplay.BOTH to "Ambos",
                        NoteDisplay.NONE to "Nada"
                    ).forEach { (display, label) ->
                        DropdownMenuItem(
                            text = { Text(label) },
                            onClick = { noteDisplay = display; displayMenuExpanded = false }
                        )
                    }
                }
            }
        }

        // Position controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text("Posiciones", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
            Spacer(modifier = Modifier.width(8.dp))
            Switch(
                checked = positionsEnabled,
                onCheckedChange = { positionsEnabled = it }
            )

            if (positionsEnabled && scale.positions.isNotEmpty()) {
                Spacer(modifier = Modifier.width(12.dp))
                IconButton(
                    onClick = { currentPosition = (currentPosition - 1 + scale.positions.size) % scale.positions.size },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ChevronLeft, "Anterior", tint = Color.White)
                }
                Text(
                    scale.positions[currentPosition].name,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
                IconButton(
                    onClick = { currentPosition = (currentPosition + 1) % scale.positions.size },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(Icons.Default.ChevronRight, "Siguiente", tint = Color.White)
                }
            }
        }

        // Fretboard (takes remaining space)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, gestureZoom, _ ->
                        zoom = (zoom * gestureZoom).coerceIn(1f, 3f)
                        scrollOffset = (scrollOffset + pan.x).coerceIn(
                            -(TOTAL_FRETS * 60f * zoom - size.width),
                            0f
                        )
                    }
                }
        ) {
            FretboardCanvas(
                rootNote = selectedKey,
                scale = scale,
                noteDisplay = noteDisplay,
                positionsEnabled = positionsEnabled,
                currentPosition = currentPosition,
                zoom = zoom,
                scrollOffset = scrollOffset
            )
        }
    }
}

@Composable
private fun FretboardCanvas(
    rootNote: Int,
    scale: Scale,
    noteDisplay: NoteDisplay,
    positionsEnabled: Boolean,
    currentPosition: Int,
    zoom: Float,
    scrollOffset: Float
) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val canvasWidth = size.width
        val canvasHeight = size.height
        val stringSpacing = canvasHeight / 7f
        val fretWidth = 60f * zoom
        val nutWidth = 12f

        // Draw nut
        drawRect(
            color = Color(0xFFE0E0E0),
            topLeft = Offset(scrollOffset, stringSpacing * 0.5f),
            size = Size(nutWidth, stringSpacing * 6f)
        )

        // Draw frets
        for (fret in 0..TOTAL_FRETS) {
            val x = nutWidth + fret * fretWidth + scrollOffset
            if (x < -fretWidth || x > canvasWidth + fretWidth) continue
            drawLine(
                color = Color(0xFF666666),
                start = Offset(x, stringSpacing * 0.5f),
                end = Offset(x, stringSpacing * 6.5f),
                strokeWidth = 2f
            )
        }

        // Draw fret markers (dots)
        val markerFrets = listOf(3, 5, 7, 9, 12, 15, 17, 19, 21)
        val doubleMarkerFrets = listOf(12)
        for (fret in markerFrets) {
            val x = nutWidth + (fret - 0.5f) * fretWidth + scrollOffset
            if (x < -fretWidth || x > canvasWidth + fretWidth) continue
            if (fret in doubleMarkerFrets) {
                drawCircle(Color(0xFF444444), radius = 6f, center = Offset(x, stringSpacing * 2.5f))
                drawCircle(Color(0xFF444444), radius = 6f, center = Offset(x, stringSpacing * 4.5f))
            } else {
                drawCircle(Color(0xFF444444), radius = 6f, center = Offset(x, stringSpacing * 3.5f))
            }
        }

        // Draw strings
        for (string in 0 until 6) {
            val y = stringSpacing * (string + 1)
            val thickness = 1f + (5 - string) * 0.4f
            drawLine(
                color = Color(0xFFBDBDBD),
                start = Offset(scrollOffset.coerceAtLeast(0f), y),
                end = Offset(canvasWidth, y),
                strokeWidth = thickness
            )
        }

        // Draw fret numbers
        for (fret in 1..TOTAL_FRETS) {
            val x = nutWidth + (fret - 0.5f) * fretWidth + scrollOffset
            if (x < -fretWidth || x > canvasWidth + fretWidth) continue
            drawContext.canvas.nativeCanvas.drawText(
                "$fret",
                x,
                stringSpacing * 0.35f,
                android.graphics.Paint().apply {
                    color = android.graphics.Color.argb(150, 200, 200, 200)
                    textSize = 22f
                    textAlign = android.graphics.Paint.Align.CENTER
                }
            )
        }

        // Determine position range
        val posStart = if (positionsEnabled && scale.positions.isNotEmpty()) {
            scale.positions[currentPosition].startFret
        } else 0
        val posEnd = if (positionsEnabled && scale.positions.isNotEmpty()) {
            scale.positions[currentPosition].endFret
        } else TOTAL_FRETS

        // Draw scale notes
        for (string in 0 until 6) {
            val openNote = STANDARD_TUNING[string]
            val y = stringSpacing * (string + 1)

            for (fret in 0..TOTAL_FRETS) {
                val noteIndex = (openNote + fret) % 12
                if (!isNoteInScale(noteIndex, rootNote, scale.intervals)) continue

                val x = if (fret == 0) {
                    nutWidth * 0.5f + scrollOffset
                } else {
                    nutWidth + (fret - 0.5f) * fretWidth + scrollOffset
                }

                if (x < -30f || x > canvasWidth + 30f) continue

                val degree = getDegreeInScale(noteIndex, rootNote, scale.intervals) ?: continue
                val isInPosition = fret in posStart..posEnd

                // If positions enabled, dim notes outside position
                if (positionsEnabled && !isInPosition) {
                    drawCircle(COLOR_POSITION_DIM, radius = 12f, center = Offset(x, y))
                    continue
                }

                val noteColor = when (degree) {
                    1 -> COLOR_TONIC
                    3 -> COLOR_THIRD
                    5 -> COLOR_FIFTH
                    else -> COLOR_OTHER
                }

                val radius = if (degree == 1) 14f else 12f
                drawCircle(noteColor, radius = radius, center = Offset(x, y))

                // Draw text label
                if (noteDisplay != NoteDisplay.NONE) {
                    val label = when (noteDisplay) {
                        NoteDisplay.NOTE -> getNoteName(noteIndex)
                        NoteDisplay.DEGREE -> getDegreeLabel(degree)
                        NoteDisplay.BOTH -> "${getNoteName(noteIndex)}\n${getDegreeLabel(degree)}"
                        NoteDisplay.NONE -> ""
                    }
                    drawNoteLabel(label, x, y, noteDisplay == NoteDisplay.BOTH)
                }
            }
        }
    }
}

private fun DrawScope.drawNoteLabel(label: String, x: Float, y: Float, isDouble: Boolean) {
    val paint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = if (isDouble) 16f else 18f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    if (isDouble && label.contains("\n")) {
        val parts = label.split("\n")
        drawContext.canvas.nativeCanvas.drawText(parts[0], x, y - 3f, paint)
        paint.textSize = 14f
        drawContext.canvas.nativeCanvas.drawText(parts[1], x, y + 11f, paint)
    } else {
        drawContext.canvas.nativeCanvas.drawText(label, x, y + 5f, paint)
    }
}
