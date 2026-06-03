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
import com.caminerin.guitartrainer.audio.AudioProcessor
import com.caminerin.guitartrainer.audio.FretboardConstraint
import com.caminerin.guitartrainer.audio.NoteEvent
import com.caminerin.guitartrainer.audio.NoteRecognizer
import com.caminerin.guitartrainer.audio.PitchDetector
import com.caminerin.guitartrainer.audio.ScaleEvaluation
import com.caminerin.guitartrainer.audio.ScaleJudgement
import com.caminerin.guitartrainer.audio.ScalePracticeContext
import com.caminerin.guitartrainer.audio.TickPlayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

private val COLOR_BG = SHARED_BG
private val COLOR_TOOLBAR = SHARED_TOOLBAR



@Composable
fun CagedPracticeScreen(
    onBack: () -> Unit,
    pitchResult: PitchDetector.PitchResult? = null,
    noteEvent: NoteEvent? = null,
    noteRecognizer: NoteRecognizer? = null,
    audioProcessor: AudioProcessor? = null,
    scaleEvaluation: ScaleEvaluation? = null,
    onOverlayChanged: (Boolean) -> Unit = {}
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var selectedKey by rememberSaveable { mutableIntStateOf(AppPreferences.lastKey) }
    var selectedScaleIndex by rememberSaveable { mutableIntStateOf(AppPreferences.lastScaleIndex.coerceIn(0, ALL_SCALES.size - 1)) }
    var noteDisplay by rememberSaveable { mutableStateOf(NoteDisplay.BOTH) }
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
    var showDisplaySelector by remember { mutableStateOf(false) }

    val anyOverlayOpen = showKeyCircle || showScaleSelector || showInfo || showSubdivisionMenu || showColorSelector || showDisplaySelector
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
    val totalWidthDp = openStringWidth + (FRETBOARD_TOTAL_FRETS * 60 * zoom + 30).dp

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

    // Reset engines when entering/leaving guitar mode
    LaunchedEffect(guitarMode) {
        if (guitarMode) {
            noteRecognizer?.reset()
            audioProcessor?.scalePracticeEngine?.reset()
        }
    }

    // Build ScalePracticeContext and push it to AudioProcessor
    LaunchedEffect(selectedKey, selectedScaleIndex, currentPositionIndex, currentNoteIndex, guitarMode) {
        if (!guitarMode) {
            audioProcessor?.practiceContext = null
            return@LaunchedEffect
        }
        val scaleNotes = scale.intervals.map { (selectedKey + it) % 12 }.toSet()
        val expectedIdx = currentNote?.noteIndex ?: 0
        val prevSeqIdx = currentNoteIndex - 1
        val prevNote = if (prevSeqIdx >= 0) noteSequence.getOrNull(prevSeqIdx)?.noteIndex else null
        val nextSeqIdx = currentNoteIndex + 1
        val nextNote = if (nextSeqIdx < noteSequence.size) noteSequence.getOrNull(nextSeqIdx)?.noteIndex else null

        val allowedMidi = FretboardConstraint.allowedMidiNotes(
            currentPosition.startFret, currentPosition.endFret, scaleNotes
        )
        val midiRange = FretboardConstraint.midiRange(
            currentPosition.startFret, currentPosition.endFret
        )

        audioProcessor?.practiceContext = ScalePracticeContext(
            rootNoteIndex = selectedKey,
            scaleNoteIndices = scaleNotes,
            expectedNoteIndex = expectedIdx,
            previousNoteIndex = prevNote,
            nextNoteIndex = nextNote,
            allowedMidiNotes = allowedMidi,
            allowedMidiRange = midiRange
        )
    }

    // Guitar evaluation: consume ScaleEvaluation from the new pipeline
    LaunchedEffect(guitarMode, scaleEvaluation) {
        if (!guitarMode) return@LaunchedEffect
        val eval = scaleEvaluation ?: return@LaunchedEffect
        val expected = currentNote?.noteIndex ?: return@LaunchedEffect
        val detectedNote = eval.noteEvent.noteIndex
        val now = System.currentTimeMillis()

        when (eval.judgement) {
            ScaleJudgement.EXPECTED -> {
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
            ScaleJudgement.PREVIOUS_STILL_RINGING -> {
                // Ignore — still hearing the previous note
            }
            ScaleJudgement.NEXT_NOTE_EARLY -> {
                // Player is ahead — treat as correct for next note
                if (lastEvalNoteIdx != currentNoteIndex) {
                    correctCount++
                    lastEvalNoteIdx = currentNoteIndex
                }
                evalFeedback = "✓ (adelantada)"
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
            ScaleJudgement.UNCERTAIN -> {
                // Not confident enough — ignore
            }
            else -> {
                // WRONG_SCALE_NOTE or OUT_OF_SCALE_NOTE
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
            ScrollableToolbar(bgColor = COLOR_TOOLBAR) {
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

                // Display mode (same as Biblioteca)
                NoteDisplayToolbarButton(noteDisplay) { showDisplaySelector = true }

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
                    drawSharedFretboard(
                        rootNote = selectedKey,
                        scale = scale,
                        noteDisplay = noteDisplay,
                        positionsEnabled = positionsEnabled,
                        posStart = currentPosition.startFret,
                        posEnd = currentPosition.endFret,
                        fretWidthPx = with(density) { fretWidthDp.toPx() },
                        openStringWidthPx = with(density) { openStringWidth.toPx() },
                        currentNote = currentNote
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

        if (showDisplaySelector) {
            NoteDisplaySelectorOverlay(
                current = noteDisplay,
                onSelect = { noteDisplay = it },
                onDismiss = { showDisplaySelector = false }
            )
        }
    }
}


