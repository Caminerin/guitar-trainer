package com.caminerin.guitartrainer.ui

import android.content.Context
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.caminerin.guitartrainer.audio.MetronomeEngine

// ===== SHARED CSV PARSING =====
fun smartSplit(line: String): List<String> {
    val parts = mutableListOf<String>()
    val current = StringBuilder()
    var inQuotes = false
    for (ch in line) {
        when {
            ch == '"' -> inQuotes = !inQuotes
            ch == ',' && !inQuotes -> {
                parts.add(current.toString())
                current.clear()
            }
            else -> current.append(ch)
        }
    }
    parts.add(current.toString())
    return parts
}

// ===== SHARED COLORS (Warm Dark style) =====
val SHARED_BG = Color(0xFF0F0D0A)
val SHARED_TOOLBAR = Color(0xFF12100C)
val SHARED_ACCENT = Color(0xFFD4960A)
val TOOLBAR_CHIP_BG = Color(0xFF251E15) // Warm dark toolbar button background

// ===== SHARED FRETBOARD CONSTANTS (Warm Dark — rosewood style) =====
val FRETBOARD_WOOD = Color(0xFF2C1E10)       // dark rosewood background
val FRETBOARD_NUT = Color(0xFFF5E6C8)         // bone/ivory nut
val FRETBOARD_FRET_WIRE = Color(0xFF8B7355)   // warm bronze fret lines
val FRETBOARD_INLAY = Color(0xFFD4C4A0)        // warm ivory dot markers
val FRETBOARD_DIM = Color(0xFF5A4A3A)          // muted warm for out-of-position
val FRETBOARD_HIGHLIGHT = Color(0xFFD4960A)    // amber highlight (practice mode)

val FRETBOARD_STRING_COLORS = listOf(
    Color(0xFFC8B090), Color(0xFFC8B090), Color(0xFFC8B090),
    Color(0xFFB0A080), Color(0xFFB0A080), Color(0xFFB0A080)
)
val FRETBOARD_STRING_WIDTHS = listOf(1.2f, 1.4f, 1.6f, 1.8f, 2.0f, 2.2f)

const val FRETBOARD_TOTAL_FRETS = 22

// ===== SHARED FRETBOARD DRAWING (single source of truth) =====

/**
 * Draws a tablatura-style guitar fretboard used by both Biblioteca/Escalas
 * and Practicar/Escalas screens. Having a single function prevents
 * recurring bugs caused by code duplication.
 *
 * @param rootNote        index (0-11) of the scale root
 * @param scale           the Scale object with intervals, etc.
 * @param noteDisplay     how to show notes (NOTE, DEGREE, BOTH, etc.)
 * @param positionsEnabled  whether position filtering is active
 * @param posStart        start fret of the active position (0 if all frets)
 * @param posEnd          end fret of the active position (TOTAL_FRETS if all frets)
 * @param fretWidthPx     pixel width of each fret cell
 * @param openStringWidthPx  pixel width reserved for open string labels
 * @param currentNote     optional highlight for practice mode (null in library mode)
 */
fun DrawScope.drawSharedFretboard(
    rootNote: Int,
    scale: Scale,
    noteDisplay: NoteDisplay,
    positionsEnabled: Boolean,
    posStart: Int,
    posEnd: Int,
    fretWidthPx: Float,
    openStringWidthPx: Float,
    currentNote: FretboardNote? = null
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

    // Paper/cream background
    drawRoundRect(
        color = FRETBOARD_WOOD,
        topLeft = Offset(nutX, fbTop - 4f),
        size = Size(size.width - nutX, fbHeight + 8f),
        cornerRadius = CornerRadius(6f)
    )

    // Position bracket with golden border
    if (positionsEnabled) {
        val startX = if (posStart == 0) nutX else nutX + nutWidth + (posStart - 1) * fretWidthPx
        val endX = nutX + nutWidth + posEnd * fretWidthPx
        drawRect(
            color = Color(0x1AD4960A),
            topLeft = Offset(startX, fbTop - 4f),
            size = Size(endX - startX, fbHeight + 8f)
        )
        drawRect(
            color = Color(0xFFD4960A),
            topLeft = Offset(startX, fbTop - 4f),
            size = Size(endX - startX, fbHeight + 8f),
            style = Stroke(2f)
        )
    }

    // Nut (dark, tab style)
    drawRect(color = FRETBOARD_NUT, topLeft = Offset(nutX, fbTop - 6f), size = Size(nutWidth, fbHeight + 12f))

    // Fret lines — subtle
    for (fret in 1..FRETBOARD_TOTAL_FRETS) {
        val x = nutX + nutWidth + fret * fretWidthPx
        drawLine(FRETBOARD_FRET_WIRE, Offset(x, fbTop), Offset(x, fbBottom), strokeWidth = 1f)
    }

    // Fret numbers below the fretboard
    val fretNumPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(180, 196, 176, 144)
        textSize = 36f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    for (fret in 1..FRETBOARD_TOTAL_FRETS) {
        val x = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawContext.canvas.nativeCanvas.drawText("$fret", x, fbBottom + bottomPad * 0.7f, fretNumPaint)
    }

    // Dot markers (subtle, below fretboard)
    val singleDots = listOf(3, 5, 7, 9, 15, 17, 19, 21)
    val doubleDots = listOf(12)
    val dotRadius = (fretWidthPx * 0.05f).coerceIn(3f, 8f)
    for (fret in singleDots) {
        if (fret > FRETBOARD_TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawCircle(FRETBOARD_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.35f))
    }
    for (fret in doubleDots) {
        if (fret > FRETBOARD_TOTAL_FRETS) continue
        val cx = nutX + nutWidth + (fret - 0.5f) * fretWidthPx
        drawCircle(FRETBOARD_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.2f))
        drawCircle(FRETBOARD_INLAY, dotRadius, Offset(cx, fbBottom + bottomPad * 0.5f))
    }

    // Staff lines (strings) — thin uniform like tab notation
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        drawLine(FRETBOARD_STRING_COLORS[s], Offset(nutX, y), Offset(size.width, y), strokeWidth = FRETBOARD_STRING_WIDTHS[s])
    }

    // Open string labels
    val openPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(180, 80, 80, 80)
        textSize = 48f
        textAlign = android.graphics.Paint.Align.CENTER
        isAntiAlias = true
    }
    val fret0InPos = 0 in posStart..posEnd
    for (s in 0 until 6) {
        val y = fbTop + stringSpacing * (6 - s)
        val openNote = STANDARD_TUNING_MIDI[s]
        val noteIdx = openNote % 12
        val hasScaleNote = isNoteInScale(noteIdx, rootNote, scale.intervals)
        val willDrawCircle = hasScaleNote && (!positionsEnabled || fret0InPos)
        if (!willDrawCircle) {
            drawContext.canvas.nativeCanvas.drawText(getOpenStringNames()[s], nutX * 0.5f, y + 24f, openPaint)
        }
    }

    // Note text paints
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
    val notePaintDim = android.graphics.Paint().apply {
        color = android.graphics.Color.argb(100, 100, 100, 100)
        textSize = 28f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    val noteRadius = (stringSpacing * 0.44f).coerceIn(36f, 80f)

    // Precompute scale membership + degree for all 12 pitch classes (cached per draw call)
    val scaleDegreeMap = IntArray(12) { -1 }  // noteIdx -> degree (1-based), -1 = not in scale
    for (interval in scale.intervals) {
        val noteIdx = (rootNote + interval) % 12
        val degree = getDegreeInScale(noteIdx, rootNote, scale.intervals)
        if (degree != null) scaleDegreeMap[noteIdx] = degree
    }

    for (s in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val y = fbTop + stringSpacing * (6 - s)

        for (fret in 0..FRETBOARD_TOTAL_FRETS) {
            val noteIdx = (openNote + fret) % 12
            val degree = scaleDegreeMap[noteIdx]
            if (degree < 0) continue // not in scale
            val isInPos = fret in posStart..posEnd

            val cx = if (fret == 0) {
                nutX * 0.5f
            } else {
                nutX + nutWidth + (fret - 0.5f) * fretWidthPx
            }

            // Out-of-position: dimmed
            if (positionsEnabled && !isInPos) {
                // Skip dimmed notes at fret 0 — open string labels are already drawn there
                if (fret == 0) continue
                val dimR = noteRadius * 0.65f
                drawCircle(FRETBOARD_WOOD, dimR + 1f, Offset(cx, y))
                drawCircle(FRETBOARD_DIM.copy(alpha = 0.25f), dimR, Offset(cx, y))
                if (noteDisplay != NoteDisplay.NONE) {
                    val lbl = buildSharedNoteLabel(noteIdx, degree, noteDisplay, rootNote, scale.relativeMajorOffset)
                    notePaintDim.color = android.graphics.Color.argb(100, 100, 100, 100)
                    drawContext.canvas.nativeCanvas.drawText(lbl, cx, y + 10f, notePaintDim)
                }
                continue
            }

            // In-position: full color
            val isFiltered = DegreeColorPrefs.isScaleEnabled(degree)
            val noteColor = DegreeColorPrefs.getScaleColor(degree)
            val isCurrentNote = currentNote != null && currentNote.string == s && currentNote.fret == fret

            val baseR = if (degree == 1 && isFiltered) noteRadius * 1.1f else if (!isFiltered) noteRadius * 0.7f else noteRadius
            val r = if (isCurrentNote) baseR * 1.3f else baseR

            // White background to break the staff line
            drawCircle(FRETBOARD_WOOD, r + 2f, Offset(cx, y))
            drawCircle(noteColor, r, Offset(cx, y))
            drawCircle(Color(0x44000000), r, Offset(cx, y), style = Stroke(1.5f))

            // Yellow highlight ring for current note (practice mode)
            if (isCurrentNote) {
                drawCircle(FRETBOARD_HIGHLIGHT, r + 8f, Offset(cx, y), style = Stroke(5f))
                drawCircle(FRETBOARD_HIGHLIGHT.copy(alpha = 0.3f), r + 16f, Offset(cx, y), style = Stroke(3f))
            }

            // Note label
            if (noteDisplay != NoteDisplay.NONE && isFiltered) {
                val label = buildSharedNoteLabel(noteIdx, degree, noteDisplay, rootNote, scale.relativeMajorOffset)
                val paint = if (label.length > 4) notePaintSmall else notePaintBig
                if (isCurrentNote) {
                    paint.color = android.graphics.Color.WHITE
                } else {
                    paint.color = android.graphics.Color.argb(220, 255, 255, 255)
                }
                drawContext.canvas.nativeCanvas.drawText(label, cx, y + paint.textSize * 0.35f, paint)
                // Reset to white for next iteration
                paint.color = android.graphics.Color.WHITE
            }
        }
    }
}

private fun buildSharedNoteLabel(noteIdx: Int, degree: Int, display: NoteDisplay, rootNote: Int = -1, relativeMajorOffset: Int = 0): String {
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

// ===== SCROLLABLE TOOLBAR WITH EDGE INDICATORS =====
@Composable
fun ScrollableToolbar(
    bgColor: Color = SHARED_TOOLBAR,
    content: @Composable RowScope.() -> Unit
) {
    val scrollState = rememberScrollState()
    Box(modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min).background(bgColor)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp)
                .horizontalScroll(scrollState),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            content = content
        )
        // Left fade + chevron when scrolled right
        if (scrollState.value > 0) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .fillMaxHeight()
                    .width(28.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(bgColor, Color.Transparent)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "\u2039",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
        // Right fade + chevron when more content to scroll
        if (scrollState.maxValue > 0 && scrollState.value < scrollState.maxValue) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(28.dp)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(Color.Transparent, bgColor)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "\u203A",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ===== STANDARD TOOLBAR CHIP =====
@Composable
fun ToolbarChip(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = TOOLBAR_CHIP_BG,
    textColor: Color = Color.White,
    isActive: Boolean = true
) {
    Box(
        modifier = modifier
            .height(34.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(if (isActive) backgroundColor else backgroundColor.copy(alpha = 0.4f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text,
            color = if (isActive) textColor else textColor.copy(alpha = 0.5f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1
        )
    }
}

// ===== CENTRALIZED APP COLOR SYSTEM (Warm Dark style) =====
object AppColors {
    val background = Color(0xFF0F0D0A)
    val surface = Color(0xFF1A1714)
    val surfaceVariant = Color(0xFF201C16)
    val surfaceBright = Color(0xFF2A2420)
    val primary = Color(0xFFD4960A)         // Amber/gold accent
    val onPrimary = Color(0xFF0F0D0A)
    val secondary = Color(0xFFE67E00)       // Orange-amber accent
    val tertiary = Color(0xFF8BC34A)        // Warm green for interactive elements
    val text = Color(0xFFF0E8D8)
    val textSecondary = Color(0xFF8B7D6B)
    val textMuted = Color(0xFF5A5040)
    val success = Color(0xFF8BC34A)
    val warning = Color(0xFFE6A000)
    val error = Color(0xFFD84315)
    val divider = Color(0xFF2A2420)
    val navBar = Color(0xFF0A0908)
    val navSelected = primary
    val navUnselected = Color(0xFF5A5040)
    val overlay = Color(0xFF0A0908).copy(alpha = 0.85f)
    val cardBg = Color(0xFF1A1714)
}

// ===== COLOR PALETTE for degree/interval color picker =====
val COLOR_PALETTE = listOf(
    Color(0xFFE53935) to "Rojo",
    Color(0xFFD81B60) to "Fucsia",
    Color(0xFFff6b9d) to "Coral",
    Color(0xFF8E24AA) to "Púrpura",
    Color(0xFF5E35B1) to "Violeta",
    Color(0xFF3949AB) to "Índigo",
    Color(0xFF1E88E5) to "Azul",
    Color(0xFF00ACC1) to "Cian",
    Color(0xFF00897B) to "Teal",
    Color(0xFF43A047) to "Verde",
    Color(0xFF7CB342) to "Lima",
    Color(0xFFFDD835) to "Amarillo",
    Color(0xFFFF8F00) to "Ámbar",
    Color(0xFFFF6D00) to "Naranja",
    Color(0xFF6D4C41) to "Marrón",
    Color(0xFF546E7A) to "Gris",
)
val COLOR_OFF = Color(0xFF78909C) // "Apagado"

// ===== DEGREE/INTERVAL COLOR PREFERENCES =====
object DegreeColorPrefs {
    private const val PREFS = "guitar_prefs"

    // Scale degree colors (keys: "scale_tonic", "scale_third", "scale_fifth", "scale_other")
    var tonicColor by mutableStateOf(Color(0xFFE53935))
        private set
    var thirdColor by mutableStateOf(Color(0xFF1E88E5))
        private set
    var fifthColor by mutableStateOf(Color(0xFF43A047))
        private set
    var otherColor by mutableStateOf(Color(0xFF26A69A))
        private set
    var tonicEnabled by mutableStateOf(true)
        private set
    var thirdEnabled by mutableStateOf(true)
        private set
    var fifthEnabled by mutableStateOf(true)
        private set
    var otherEnabled by mutableStateOf(true)
        private set

    // Chord interval colors
    var chordRootColor by mutableStateOf(Color(0xFFE53935))
        private set
    var chordThirdColor by mutableStateOf(Color(0xFF1E88E5))
        private set
    var chordFifthColor by mutableStateOf(Color(0xFF43A047))
        private set
    var chordOtherColor by mutableStateOf(Color(0xFF26A69A))
        private set
    var chordRootEnabled by mutableStateOf(true)
        private set
    var chordThirdEnabled by mutableStateOf(true)
        private set
    var chordFifthEnabled by mutableStateOf(true)
        private set
    var chordOtherEnabled by mutableStateOf(true)
        private set

    fun load(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        tonicColor = Color(p.getInt("scale_tonic_color", 0xFFE53935.toInt()))
        thirdColor = Color(p.getInt("scale_third_color", 0xFF1E88E5.toInt()))
        fifthColor = Color(p.getInt("scale_fifth_color", 0xFF43A047.toInt()))
        otherColor = Color(p.getInt("scale_other_color", 0xFF26A69A.toInt()))
        tonicEnabled = p.getBoolean("scale_tonic_on", true)
        thirdEnabled = p.getBoolean("scale_third_on", true)
        fifthEnabled = p.getBoolean("scale_fifth_on", true)
        otherEnabled = p.getBoolean("scale_other_on", true)
        chordRootColor = Color(p.getInt("chord_root_color", 0xFFE53935.toInt()))
        chordThirdColor = Color(p.getInt("chord_third_color", 0xFF1E88E5.toInt()))
        chordFifthColor = Color(p.getInt("chord_fifth_color", 0xFF43A047.toInt()))
        chordOtherColor = Color(p.getInt("chord_other_color", 0xFF26A69A.toInt()))
        chordRootEnabled = p.getBoolean("chord_root_on", true)
        chordThirdEnabled = p.getBoolean("chord_third_on", true)
        chordFifthEnabled = p.getBoolean("chord_fifth_on", true)
        chordOtherEnabled = p.getBoolean("chord_other_on", true)
    }

    fun setScaleColor(degree: String, color: Color, enabled: Boolean, context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        when (degree) {
            "tonic" -> { tonicColor = color; tonicEnabled = enabled; p.putInt("scale_tonic_color", color.toArgb()); p.putBoolean("scale_tonic_on", enabled) }
            "third" -> { thirdColor = color; thirdEnabled = enabled; p.putInt("scale_third_color", color.toArgb()); p.putBoolean("scale_third_on", enabled) }
            "fifth" -> { fifthColor = color; fifthEnabled = enabled; p.putInt("scale_fifth_color", color.toArgb()); p.putBoolean("scale_fifth_on", enabled) }
            "other" -> { otherColor = color; otherEnabled = enabled; p.putInt("scale_other_color", color.toArgb()); p.putBoolean("scale_other_on", enabled) }
        }
        p.apply()
    }

    fun setChordColor(interval: String, color: Color, enabled: Boolean, context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        when (interval) {
            "root" -> { chordRootColor = color; chordRootEnabled = enabled; p.putInt("chord_root_color", color.toArgb()); p.putBoolean("chord_root_on", enabled) }
            "third" -> { chordThirdColor = color; chordThirdEnabled = enabled; p.putInt("chord_third_color", color.toArgb()); p.putBoolean("chord_third_on", enabled) }
            "fifth" -> { chordFifthColor = color; chordFifthEnabled = enabled; p.putInt("chord_fifth_color", color.toArgb()); p.putBoolean("chord_fifth_on", enabled) }
            "other" -> { chordOtherColor = color; chordOtherEnabled = enabled; p.putInt("chord_other_color", color.toArgb()); p.putBoolean("chord_other_on", enabled) }
        }
        p.apply()
    }

    fun getScaleColor(degree: Int): Color = when (degree) {
        1 -> if (tonicEnabled) tonicColor else COLOR_OFF.copy(alpha = 0.35f)
        3 -> if (thirdEnabled) thirdColor else COLOR_OFF.copy(alpha = 0.35f)
        5 -> if (fifthEnabled) fifthColor else COLOR_OFF.copy(alpha = 0.35f)
        else -> if (otherEnabled) otherColor else COLOR_OFF.copy(alpha = 0.35f)
    }

    fun isScaleEnabled(degree: Int): Boolean = when (degree) {
        1 -> tonicEnabled; 3 -> thirdEnabled; 5 -> fifthEnabled; else -> otherEnabled
    }

    fun getChordColor(interval: String): Color {
        val cat = when (interval) {
            "1" -> "root"; "3", "b3" -> "third"; "5", "b5", "#5" -> "fifth"; else -> "other"
        }
        return when (cat) {
            "root" -> if (chordRootEnabled) chordRootColor else COLOR_OFF.copy(alpha = 0.35f)
            "third" -> if (chordThirdEnabled) chordThirdColor else COLOR_OFF.copy(alpha = 0.35f)
            "fifth" -> if (chordFifthEnabled) chordFifthColor else COLOR_OFF.copy(alpha = 0.35f)
            else -> if (chordOtherEnabled) chordOtherColor else COLOR_OFF.copy(alpha = 0.35f)
        }
    }

    fun isChordEnabled(interval: String): Boolean {
        return when (interval) {
            "1" -> chordRootEnabled
            "3", "b3" -> chordThirdEnabled
            "5", "b5", "#5" -> chordFifthEnabled
            else -> chordOtherEnabled
        }
    }
}

// ===== BPM SELECTOR OVERLAY =====
@Composable
fun BpmSelectorOverlay(
    bpm: Int,
    onBpmChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("BPM", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Big number
            Text(
                "$bpm",
                color = Color.White,
                fontSize = 56.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Slider
            Slider(
                value = bpm.toFloat(),
                onValueChange = { onBpmChange(it.toInt()) },
                valueRange = 20f..240f,
                modifier = Modifier.fillMaxWidth(),
                colors = SliderDefaults.colors(
                    thumbColor = Color(0xFFD4960A),
                    activeTrackColor = Color(0xFFD4960A)
                )
            )

            Spacer(modifier = Modifier.height(12.dp))

            // +-1 and +-5 buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BpmButton("-5") { onBpmChange((bpm - 5).coerceAtLeast(20)) }
                BpmButton("-1") { onBpmChange((bpm - 1).coerceAtLeast(20)) }
                Spacer(modifier = Modifier.width(16.dp))
                BpmButton("+1") { onBpmChange((bpm + 1).coerceAtMost(240)) }
                BpmButton("+5") { onBpmChange((bpm + 5).coerceAtMost(240)) }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Preset buttons
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(40, 60, 80, 100, 120, 160).forEach { v ->
                    val selected = bpm == v
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.08f))
                            .clickable { onBpmChange(v) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("$v", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun BpmButton(label: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color.White.copy(alpha = 0.1f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Text(label, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// ===== MEASURES COUNT SELECTOR OVERLAY =====
@Composable
fun MeasuresSelectorOverlay(
    count: Int,
    onCountChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Compases", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                (1..16).forEach { v ->
                    val selected = count == v
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.08f))
                            .clickable { onCountChange(v); onDismiss() },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$v", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ===== FRET LIMIT SELECTOR OVERLAY =====
@Composable
fun FretSelectorOverlay(
    maxFret: Int,
    onFretChange: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Hasta traste", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(5, 7, 9, 12, 15, 17, 22).forEach { f ->
                    val selected = maxFret == f
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selected) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.08f))
                            .clickable { onFretChange(f); onDismiss() }
                            .padding(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Text("$f", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

// ===== UNIFIED SCALE SELECTOR (replaces both DropdownMenu and ScaleSelectorOverlay) =====
private val SCALE_CATEGORIES = listOf(
    "Mayores" to listOf("Mayor (Jónica)", "Pentatónica mayor"),
    "Menores" to listOf("Menor natural (Eólica)", "Pentatónica menor", "Menor armónica", "Menor melódica"),
    "Blues" to listOf("Blues menor", "Blues mayor"),
    "Modos" to listOf("Dórica", "Frigia", "Lidia", "Mixolidia", "Locria"),
    "Exóticas" to listOf("Frigia española", "Húngara menor", "Tonos enteros", "Cromática")
)

private val CATEGORY_COLORS = listOf(
    Color(0xFF4CAF50), Color(0xFFE53935), Color(0xFF2196F3),
    Color(0xFFFF9800), Color(0xFF9C27B0)
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun UnifiedScaleSelectorOverlay(
    selectedScaleName: String,
    onScaleSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Escala", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            SCALE_CATEGORIES.forEachIndexed { catIdx, (catName, scaleNames) ->
                val catColor = CATEGORY_COLORS[catIdx]
                Text(
                    catName,
                    color = catColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 6.dp, top = if (catIdx > 0) 12.dp else 0.dp)
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    scaleNames.forEach { scaleName ->
                        val scaleIdx = ALL_SCALES.indexOfFirst { it.name == scaleName }
                        if (scaleIdx >= 0) {
                            val isSelected = scaleName == selectedScaleName
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) catColor else catColor.copy(alpha = 0.15f))
                                    .clickable { onScaleSelected(scaleIdx); onDismiss() }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    scaleName,
                                    color = if (isSelected) Color.White else catColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ===== UNIFIED POSITION SELECTOR =====
val SHARED_POSITION_COLORS = listOf(
    Color(0xFFE53935),
    Color(0xFFFF9800),
    Color(0xFF4CAF50),
    Color(0xFF2196F3),
    Color(0xFF9C27B0)
)

@Composable
fun CagedPositionBar(
    positions: List<ScalePosition>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    currentLocation: Int = 0
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        positions.forEachIndexed { i, pos ->
            val isCurrent = i == currentIndex && enabled
            val isLetter = pos.name.length == 1 && pos.name[0].isLetter()
            val locationSuffix = if (isCurrent && currentLocation == 1) "\u00B72" else ""
            val label = if (isLetter) "P${i + 1}(${pos.name})$locationSuffix" else "P${pos.name}$locationSuffix"
            ToolbarChip(
                text = label,
                onClick = { if (enabled) onSelect(i) },
                isActive = if (isCurrent) true else enabled,
                backgroundColor = if (isCurrent) SHARED_POSITION_COLORS.getOrElse(i) { Color.Gray } else TOOLBAR_CHIP_BG
            )
        }
    }
}

// ===== SUBDIVISION SELECTOR OVERLAY =====
@Composable
fun SubdivisionSelectorOverlay(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        1 to "\u2669 Negras",
        2 to "\u266a\u266a Corcheas",
        3 to "\u266a\u266a\u266a Tresillos",
        4 to "\u266c Semicorcheas"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Subdivisión", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            options.forEach { (sub, label) ->
                val selected = sub == current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.06f))
                        .clickable { onSelect(sub); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 18.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ===== CHORD MODE SELECTOR OVERLAY (tonality vs free) =====
@Composable
fun ChordModeSelectorOverlay(
    isTonalityMode: Boolean,
    onSelectTonality: () -> Unit,
    onSelectFree: () -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Modo de acordes", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (isTonalityMode) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.06f))
                    .clickable { onSelectTonality(); onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text("Catálogo", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Solo acordes de la escala seleccionada", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isTonalityMode) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.06f))
                    .clickable { onSelectFree(); onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text("Todos los acordes", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Sin restricción de tonalidad", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
        }
    }
}

// ===== MEASURE SUBDIVISION SELECTOR OVERLAY =====
@Composable
fun MeasureSubdivisionOverlay(
    current: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val options = listOf(
        1 to "1 acorde por compás",
        2 to "2 acordes por compás",
        3 to "3 acordes por compás",
        4 to "4 acordes por compás",
        5 to "5 acordes por compás",
        6 to "6 acordes por compás",
        7 to "7 acordes por compás",
        8 to "8 acordes por compás"
    )
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Divisiones del compás", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            options.forEach { (count, label) ->
                val selected = count == current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.06f))
                        .clickable { onSelect(count) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ===== BPM TOOLBAR BUTTON (compact, opens overlay) =====
@Composable
fun BpmToolbarButton(bpm: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFD4960A).copy(alpha = 0.25f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("$bpm BPM", color = Color(0xFFB39DDB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}

// ===== NOTE DISPLAY MODE SELECTOR OVERLAY =====
@Composable
fun NoteDisplaySelectorOverlay(
    current: NoteDisplay,
    onSelect: (NoteDisplay) -> Unit,
    onDismiss: () -> Unit,
    showFingering: Boolean = false
) {
    val baseOptions = listOf(
        NoteDisplay.NOTE to "Nota",
        NoteDisplay.DEGREE to "Grado / Intervalo",
        NoteDisplay.BOTH to "Nota + Grado",
    )
    val fingeringOption = if (showFingering) listOf(NoteDisplay.FINGERING to "Digitaci\u00f3n") else emptyList()
    val options = baseOptions + fingeringOption + listOf(NoteDisplay.NONE to "Nada")
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Mostrar en notas", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            options.forEach { (display, label) ->
                val selected = display == current
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selected) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.06f))
                        .clickable { onSelect(display); onDismiss() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(label, color = Color.White, fontSize = 16.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ===== DEGREE COLOR SELECTOR OVERLAY (Scale) =====
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleColorSelectorOverlay(context: Context, onDismiss: () -> Unit) {
    val degrees = listOf(
        Triple("tonic", "T\u00f3nica (1\u00aa)", DegreeColorPrefs.tonicColor to DegreeColorPrefs.tonicEnabled),
        Triple("third", "Tercera (3\u00aa)", DegreeColorPrefs.thirdColor to DegreeColorPrefs.thirdEnabled),
        Triple("fifth", "Quinta (5\u00aa)", DegreeColorPrefs.fifthColor to DegreeColorPrefs.fifthEnabled),
        Triple("other", "Otras notas", DegreeColorPrefs.otherColor to DegreeColorPrefs.otherEnabled),
    )
    ColorSelectorOverlayBody(
        title = "Colores por grado",
        items = degrees,
        onColorChange = { key, color, enabled -> DegreeColorPrefs.setScaleColor(key, color, enabled, context) },
        onDismiss = onDismiss
    )
}

// ===== INTERVAL COLOR SELECTOR OVERLAY (Chord) =====
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChordColorSelectorOverlay(context: Context, onDismiss: () -> Unit) {
    val intervals = listOf(
        Triple("root", "Fundamental (1\u00aa)", DegreeColorPrefs.chordRootColor to DegreeColorPrefs.chordRootEnabled),
        Triple("third", "Tercera (3\u00aa)", DegreeColorPrefs.chordThirdColor to DegreeColorPrefs.chordThirdEnabled),
        Triple("fifth", "Quinta (5\u00aa)", DegreeColorPrefs.chordFifthColor to DegreeColorPrefs.chordFifthEnabled),
        Triple("other", "Otras notas", DegreeColorPrefs.chordOtherColor to DegreeColorPrefs.chordOtherEnabled),
    )
    ColorSelectorOverlayBody(
        title = "Colores por intervalo",
        items = intervals,
        onColorChange = { key, color, enabled -> DegreeColorPrefs.setChordColor(key, color, enabled, context) },
        onDismiss = onDismiss
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColorSelectorOverlayBody(
    title: String,
    items: List<Triple<String, String, Pair<Color, Boolean>>>,
    onColorChange: (String, Color, Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            items.forEach { (key, label, state) ->
                val (currentColor, isEnabled) = state
                Text(label, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    COLOR_PALETTE.forEach { (color, _) ->
                        val selected = isEnabled && currentColor == color
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(color)
                                .then(
                                    if (selected) Modifier.border(3.dp, Color.White, CircleShape)
                                    else Modifier
                                )
                                .clickable { onColorChange(key, color, true) }
                        )
                    }
                    // "Apagado" option
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (!isEnabled) Color.White.copy(alpha = 0.2f) else Color(0xFFC8B090).copy(alpha = 0.06f))
                            .then(
                                if (!isEnabled) Modifier.border(2.dp, Color.White, RoundedCornerShape(8.dp))
                                else Modifier
                            )
                            .clickable { onColorChange(key, currentColor, false) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("Apagado", color = if (!isEnabled) Color.White else Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
            }
        }
    }
}

// ===== NOTE DISPLAY TOOLBAR BUTTON =====
@Composable
fun NoteDisplayToolbarButton(noteDisplay: NoteDisplay, onClick: () -> Unit) {
    val label = when (noteDisplay) {
        NoteDisplay.NOTE -> "Nota"
        NoteDisplay.DEGREE -> "Grado"
        NoteDisplay.BOTH -> "N+G"
        NoteDisplay.FINGERING -> "Digit."
        NoteDisplay.NONE -> "\u2205"
    }
    ToolbarChip(text = label, onClick = onClick)
}

@Composable
fun ScaleNameSelectorOverlay(
    currentName: String,
    showDisableOption: Boolean = false,
    onSelected: (String) -> Unit,
    onDisable: () -> Unit = {},
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF201C16))
                .clickable(enabled = false) {}
                .padding(24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Escala", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            if (showDisableOption) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFFE53935).copy(alpha = 0.3f))
                        .clickable { onDisable() }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text("Desactivar filtro", color = Color(0xFFEF9A9A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
            ALL_SCALES.forEach { scaleEntry ->
                val name = scaleEntry.name
                val normalizedName = name.lowercase().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u")
                val normalizedCurrent = currentName.lowercase().replace("á","a").replace("é","e").replace("í","i").replace("ó","o").replace("ú","u")
                val isSelected = name == currentName || normalizedName == normalizedCurrent
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isSelected) Color(0xFFD4960A) else Color(0xFFC8B090).copy(alpha = 0.06f))
                        .clickable { onSelected(name) }
                        .padding(horizontal = 16.dp, vertical = 14.dp)
                ) {
                    Text(name, color = if (isSelected) Color.White else Color.White.copy(alpha = 0.7f),
                        fontSize = 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        }
    }
}

// ===== FLOATING METRONOME FAB =====
@Composable
fun FloatingMetronomeFab(
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(120) }
    var isPlaying by remember { mutableStateOf(false) }
    val engine = remember { MetronomeEngine() }
    @Suppress("UNUSED_VARIABLE")
    val beat by engine.currentBeat.collectAsState()
    val playing by engine.isPlaying.collectAsState()

    val fabColor by animateColorAsState(
        targetValue = if (playing) AppColors.primary else AppColors.surfaceBright,
        animationSpec = tween(200),
        label = "fab_color"
    )

    DisposableEffect(Unit) {
        onDispose {
            engine.stop()
        }
    }

    LaunchedEffect(isPlaying, bpm) {
        if (isPlaying) {
            engine.liveBpm = bpm
            engine.start(
                com.caminerin.guitartrainer.audio.MetronomeConfig(bpm = bpm)
            )
        } else {
            engine.stop()
        }
    }

    Box(modifier = modifier) {
        if (expanded) {
            Column(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(AppColors.surface)
                    .padding(12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text("$bpm BPM", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(-5, -1, 1, 5).forEach { delta ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(AppColors.surfaceVariant)
                                .clickable {
                                    bpm = (bpm + delta).coerceIn(40, 240)
                                    engine.liveBpm = bpm
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                if (delta > 0) "+$delta" else "$delta",
                                color = AppColors.text,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isPlaying) AppColors.error else AppColors.success)
                        .clickable { isPlaying = !isPlaying }
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Text(
                        if (isPlaying) "Stop" else "Play",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(AppColors.surfaceVariant)
                        .clickable { expanded = false }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("Cerrar", color = AppColors.textSecondary, fontSize = 10.sp)
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(fabColor)
                    .clickable { expanded = true },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Speed,
                    contentDescription = "Metrónomo",
                    tint = if (playing) AppColors.onPrimary else AppColors.text,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}
