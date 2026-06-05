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
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp


private val COLOR_BG = SHARED_BG
private val COLOR_TOOLBAR = SHARED_TOOLBAR



@Composable
fun ScaleFretboardScreen(onBack: () -> Unit, showBackButton: Boolean = true, onOverlayChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    var selectedKey by rememberSaveable { mutableIntStateOf(AppPreferences.lastKey) }
    var selectedScaleIndex by rememberSaveable { mutableIntStateOf(AppPreferences.lastScaleIndex.coerceIn(0, ALL_SCALES.size - 1)) }
    var noteDisplay by rememberSaveable { mutableStateOf(NoteDisplay.BOTH) }
    var positionsEnabled by rememberSaveable { mutableStateOf(false) }
    var currentPosition by rememberSaveable { mutableIntStateOf(0) }
    var currentLocation by rememberSaveable { mutableIntStateOf(0) }
    var zoom by remember { mutableFloatStateOf(1.5f) }

    var showScaleSelector by remember { mutableStateOf(false) }
    var showDisplaySelector by remember { mutableStateOf(false) }
    var showColorSelector by remember { mutableStateOf(false) }
    var showInfo by remember { mutableStateOf(false) }

    // State for chromatic circle overlay on key selection
    var showChromaticCircle by remember { mutableStateOf(false) }

    val anyOverlayOpen = showScaleSelector || showDisplaySelector || showColorSelector || showInfo || showChromaticCircle
    LaunchedEffect(anyOverlayOpen) { onOverlayChanged(anyOverlayOpen) }

    // Load color preferences
    LaunchedEffect(Unit) { DegreeColorPrefs.load(context) }

    val scale = ALL_SCALES[selectedScaleIndex]
    val positions = if (scale.hasCaged) computeCagedPositions(selectedKey) else scale.positions
    val density = LocalDensity.current
    val fretWidthDp = (60f * zoom).dp
    val locationOffset = currentLocation * 12
    val posStart = if (positionsEnabled && positions.isNotEmpty()) positions[currentPosition].startFret + locationOffset else 0
    val posEnd = if (positionsEnabled && positions.isNotEmpty()) positions[currentPosition].endFret + locationOffset else FRETBOARD_TOTAL_FRETS

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(COLOR_BG)
    ) {
        // ===== TOOLBAR =====
        ScrollableToolbar(bgColor = COLOR_TOOLBAR) {
            // Back button
            if (showBackButton) {
                IconButton(onClick = onBack, modifier = Modifier.size(34.dp)) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            // Key selector (only one with chromatic color to identify the root)
            ToolbarChip(
                text = getChromaticNames(selectedKey, scale.relativeMajorOffset)[selectedKey],
                onClick = { showChromaticCircle = true },
                backgroundColor = CHROMATIC_COLORS[selectedKey].copy(alpha = 0.5f)
            )

            // Scale selector
            ToolbarChip(text = scale.name, onClick = { showScaleSelector = true })

            // Display mode
            NoteDisplayToolbarButton(noteDisplay) { showDisplaySelector = true }

            // Colors
            ToolbarChip(text = "Colores", onClick = { showColorSelector = true })

            // Info
            ToolbarChip(text = "Info", onClick = { showInfo = true })

            // Posiciones toggle
            ToolbarChip(
                text = "Pos",
                onClick = { positionsEnabled = !positionsEnabled; if (!positionsEnabled) currentLocation = 0 },
                backgroundColor = if (positionsEnabled) SHARED_ACCENT else TOOLBAR_CHIP_BG
            )

            // Position chips - ALWAYS visible, dimmed when positions disabled
            if (positions.isNotEmpty()) {
                CagedPositionBar(
                    positions = positions,
                    currentIndex = currentPosition,
                    onSelect = { idx ->
                        if (idx == currentPosition) {
                            currentLocation = 1 - currentLocation
                        } else {
                            currentPosition = idx
                            currentLocation = 0
                        }
                    },
                    enabled = positionsEnabled,
                    currentLocation = currentLocation
                )
            }
        }

        // ===== FRETBOARD =====
        val openStringWidth = 48.dp
        val maxFret = if (positionsEnabled && positions.isNotEmpty()) maxOf(FRETBOARD_TOTAL_FRETS, posEnd) else FRETBOARD_TOTAL_FRETS
        val totalWidthDp = openStringWidth + (maxFret * 60 * zoom + 30).dp
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
                    drawSharedFretboard(
                        rootNote = selectedKey,
                        scale = scale,
                        noteDisplay = noteDisplay,
                        positionsEnabled = positionsEnabled,
                        posStart = posStart,
                        posEnd = posEnd,
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

    // Scale selector overlay (same component as Acordes for consistency)
    if (showScaleSelector) {
        ScaleNameSelectorOverlay(
            currentName = scale.name,
            onSelected = { name ->
                val idx = ALL_SCALES.indexOfFirst { it.name == name }
                if (idx >= 0) {
                    selectedScaleIndex = idx
                    currentPosition = 0
                    currentLocation = 0
                    AppPreferences.saveScale(idx, context)
                }
                showScaleSelector = false
            },
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
