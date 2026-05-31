package com.caminerin.guitartrainer.ui

import android.content.res.Configuration
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TUNED_TOLERANCE = 10f
private const val CLOSE_TOLERANCE = 25f

private val COLOR_TUNED = Color(0xFF4CAF50)
private val COLOR_CLOSE = Color(0xFFFF9800)
private val COLOR_FAR = Color(0xFFF44336)
private val COLOR_INACTIVE = Color(0xFF9E9E9E)

@Composable
fun TunerMode(
    pitchResult: PitchDetector.PitchResult?,
    modifier: Modifier = Modifier
) {
    var selectedTuningIndex by remember { mutableIntStateOf(0) }
    var isAutoMode by remember { mutableStateOf(true) }
    var selectedStringIndex by remember { mutableStateOf<Int?>(null) }
    var tuningMenuExpanded by remember { mutableStateOf(false) }
    var stableString by remember { mutableStateOf<GuitarString?>(null) }
    var candidateString by remember { mutableStateOf<GuitarString?>(null) }
    var candidateCount by remember { mutableIntStateOf(0) }

    val currentTuning = ALL_TUNINGS[selectedTuningIndex]

    val rawClosest = if (!isAutoMode) {
        selectedStringIndex?.let { currentTuning.strings.getOrNull(it) }
    } else if (pitchResult == null) {
        null
    } else {
        val freq = pitchResult.frequency
        val closest = currentTuning.strings.minByOrNull { abs(centsFromTarget(freq, it.frequency)) }
        if (stableString != null && closest != null && closest != stableString) {
            val closestCents = abs(centsFromTarget(freq, closest.frequency))
            if (closestCents < 30f) {
                closest
            } else {
                stableString
            }
        } else {
            closest
        }
    }

    val activeString = if (!isAutoMode) {
        rawClosest
    } else if (rawClosest == null) {
        stableString
    } else if (rawClosest == stableString) {
        candidateString = null
        candidateCount = 0
        stableString
    } else if (rawClosest == candidateString) {
        candidateCount++
        if (candidateCount >= 6) {
            stableString = rawClosest
            candidateString = null
            candidateCount = 0
            rawClosest
        } else {
            stableString
        }
    } else {
        candidateString = rawClosest
        candidateCount = 1
        stableString ?: rawClosest
    }

    val centsFromTarget = if (activeString != null && pitchResult != null) {
        centsFromTarget(pitchResult.frequency, activeString.frequency)
    } else {
        0f
    }

    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    if (isLandscape) {
        TunerLandscapeLayout(
            currentTuning = currentTuning,
            tuningMenuExpanded = tuningMenuExpanded,
            onTuningMenuToggle = { tuningMenuExpanded = it },
            onTuningSelected = { selectedTuningIndex = it; selectedStringIndex = null; tuningMenuExpanded = false },
            isAutoMode = isAutoMode,
            onAutoModeChanged = { isAutoMode = it },
            activeString = activeString,
            selectedStringIndex = selectedStringIndex,
            onStringSelected = { selectedStringIndex = it },
            pitchResult = pitchResult,
            centsFromTarget = centsFromTarget,
            modifier = modifier
        )
    } else {
        TunerPortraitLayout(
            currentTuning = currentTuning,
            tuningMenuExpanded = tuningMenuExpanded,
            onTuningMenuToggle = { tuningMenuExpanded = it },
            onTuningSelected = { selectedTuningIndex = it; selectedStringIndex = null; tuningMenuExpanded = false },
            isAutoMode = isAutoMode,
            onAutoModeChanged = { isAutoMode = it },
            activeString = activeString,
            selectedStringIndex = selectedStringIndex,
            onStringSelected = { selectedStringIndex = it },
            pitchResult = pitchResult,
            centsFromTarget = centsFromTarget,
            modifier = modifier
        )
    }
}

@Composable
private fun TunerPortraitLayout(
    currentTuning: GuitarTuning,
    tuningMenuExpanded: Boolean,
    onTuningMenuToggle: (Boolean) -> Unit,
    onTuningSelected: (Int) -> Unit,
    isAutoMode: Boolean,
    onAutoModeChanged: (Boolean) -> Unit,
    activeString: GuitarString?,
    selectedStringIndex: Int?,
    onStringSelected: (Int) -> Unit,
    pitchResult: PitchDetector.PitchResult?,
    centsFromTarget: Float,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        TuningSelector(currentTuning, tuningMenuExpanded, onTuningMenuToggle, onTuningSelected)
        Spacer(modifier = Modifier.height(12.dp))
        AutoManualToggle(isAutoMode, onAutoModeChanged)
        Spacer(modifier = Modifier.height(12.dp))
        StringRow(currentTuning, activeString, !isAutoMode, onStringSelected)
        Spacer(modifier = Modifier.height(16.dp))
        TunerGauge(
            cents = if (pitchResult != null && activeString != null) centsFromTarget else 0f,
            isActive = pitchResult != null && activeString != null,
            modifier = Modifier.fillMaxWidth().height(160.dp)
        )
        Spacer(modifier = Modifier.height(8.dp))
        TunerInfo(activeString, pitchResult, centsFromTarget, isAutoMode)
    }
}

@Composable
private fun TunerLandscapeLayout(
    currentTuning: GuitarTuning,
    tuningMenuExpanded: Boolean,
    onTuningMenuToggle: (Boolean) -> Unit,
    onTuningSelected: (Int) -> Unit,
    isAutoMode: Boolean,
    onAutoModeChanged: (Boolean) -> Unit,
    activeString: GuitarString?,
    selectedStringIndex: Int?,
    onStringSelected: (Int) -> Unit,
    pitchResult: PitchDetector.PitchResult?,
    centsFromTarget: Float,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left: strings + controls
        Column(
            modifier = Modifier
                .weight(0.35f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TuningSelector(currentTuning, tuningMenuExpanded, onTuningMenuToggle, onTuningSelected)
            Spacer(modifier = Modifier.height(8.dp))
            AutoManualToggle(isAutoMode, onAutoModeChanged)
            Spacer(modifier = Modifier.height(8.dp))
            StringRow(currentTuning, activeString, !isAutoMode, onStringSelected)
        }

        Spacer(modifier = Modifier.width(16.dp))

        // Right: gauge + info (constrained height to avoid overflow)
        Column(
            modifier = Modifier
                .weight(0.65f)
                .fillMaxHeight()
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            TunerGauge(
                cents = if (pitchResult != null && activeString != null) centsFromTarget else 0f,
                isActive = pitchResult != null && activeString != null,
                modifier = Modifier.fillMaxWidth().height(120.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            TunerInfo(activeString, pitchResult, centsFromTarget, isAutoMode)
        }
    }
}

@Composable
private fun TuningSelector(
    currentTuning: GuitarTuning,
    expanded: Boolean,
    onToggle: (Boolean) -> Unit,
    onSelected: (Int) -> Unit
) {
    Box {
        OutlinedButton(onClick = { onToggle(true) }) {
            Text(text = currentTuning.name)
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { onToggle(false) }) {
            ALL_TUNINGS.forEachIndexed { index, tuning ->
                DropdownMenuItem(
                    text = { Text(tuning.name) },
                    onClick = { onSelected(index) }
                )
            }
        }
    }
}

@Composable
private fun AutoManualToggle(isAutoMode: Boolean, onChanged: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        ToggleButton("Auto", isAutoMode) { onChanged(true) }
        ToggleButton("Manual", !isAutoMode) { onChanged(false) }
    }
}

@Composable
private fun StringRow(
    currentTuning: GuitarTuning,
    activeString: GuitarString?,
    isManual: Boolean,
    onStringSelected: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        currentTuning.strings.forEachIndexed { index, string ->
            StringChip(
                guitarString = string,
                isActive = activeString == string,
                isManual = isManual,
                onClick = { if (isManual) onStringSelected(index) }
            )
            if (index < currentTuning.strings.size - 1) {
                Spacer(modifier = Modifier.width(6.dp))
            }
        }
    }
}

@Composable
private fun TunerInfo(
    activeString: GuitarString?,
    pitchResult: PitchDetector.PitchResult?,
    centsFromTarget: Float,
    isAutoMode: Boolean
) {
    if (activeString != null && pitchResult != null) {
        Text(
            text = "${if (NoteFormatPreference.current == NoteFormat.EUROPEAN) activeString.spanishName else activeString.noteName}${activeString.octave}",
            fontSize = 48.sp,
            fontWeight = FontWeight.Bold,
            color = statusColor(centsFromTarget)
        )
        Text(
            text = "Cuerda ${activeString.number}",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = "${"%.1f".format(pitchResult.frequency)} Hz",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
        )
        Text(
            text = "${"%+.0f".format(centsFromTarget)} cents",
            fontSize = 14.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
        )
        Spacer(modifier = Modifier.height(12.dp))

        val status = tuningStatus(centsFromTarget)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(status.color.copy(alpha = 0.15f))
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = status.text,
                    color = status.color,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF2196F3).copy(alpha = 0.15f))
                    .clickable { playReferenceTone(activeString.frequency) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "\uD83D\uDD0A Referencia",
                    color = Color(0xFF2196F3),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    } else {
        Text(
            text = if (isAutoMode) "Toca una cuerda..." else "Selecciona y toca una cuerda...",
            fontSize = 18.sp,
            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(COLOR_INACTIVE.copy(alpha = 0.15f))
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                Text(
                    text = "— Sin señal",
                    color = COLOR_INACTIVE,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

@Composable
private fun TunerGauge(
    cents: Float,
    isActive: Boolean,
    modifier: Modifier = Modifier
) {
    val maxCents = 50f
    val animatedCents by animateFloatAsState(
        targetValue = cents.coerceIn(-maxCents, maxCents),
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 200f),
        label = "gauge"
    )

    val needleColor by animateColorAsState(
        targetValue = if (!isActive) COLOR_INACTIVE else statusColor(animatedCents),
        label = "needleColor"
    )

    val arcBackground = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp)
    ) {
        val centerX = size.width / 2f
        val bottomY = size.height - 10f
        val radius = minOf(size.width * 0.42f, (size.height - 20f) * 0.9f)

        // Arc background
        drawArc(
            color = arcBackground,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(centerX - radius, bottomY - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 12f, cap = StrokeCap.Round)
        )

        // Green zone — wider and more visible
        drawArc(
            color = COLOR_TUNED.copy(alpha = 0.6f),
            startAngle = 180f + 90f - 18f,
            sweepAngle = 36f,
            useCenter = false,
            topLeft = Offset(centerX - radius, bottomY - radius),
            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
            style = Stroke(width = 20f, cap = StrokeCap.Round)
        )

        // Tick marks
        val tickCount = 11
        for (i in 0..tickCount - 1) {
            val fraction = i.toFloat() / (tickCount - 1)
            val angle = PI.toFloat() * (1f - fraction)
            val innerR = radius - 20f
            val outerR = radius + 10f
            val isCenterTick = i == tickCount / 2

            drawLine(
                color = if (isCenterTick) COLOR_TUNED else tickColor,
                start = Offset(
                    centerX + innerR * cos(angle),
                    bottomY - innerR * sin(angle)
                ),
                end = Offset(
                    centerX + outerR * cos(angle),
                    bottomY - outerR * sin(angle)
                ),
                strokeWidth = if (isCenterTick) 3f else 2f,
                cap = StrokeCap.Round
            )
        }

        // Needle
        val needleAngle = PI.toFloat() * (1f - (animatedCents + maxCents) / (2f * maxCents))
        val needleLength = radius - 30f

        drawLine(
            color = needleColor,
            start = Offset(centerX, bottomY),
            end = Offset(
                centerX + needleLength * cos(needleAngle),
                bottomY - needleLength * sin(needleAngle)
            ),
            strokeWidth = 4f,
            cap = StrokeCap.Round
        )

        // Needle pivot
        drawCircle(
            color = needleColor,
            radius = 8f,
            center = Offset(centerX, bottomY)
        )
    }
}

@Composable
private fun ToggleButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bg = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
    val fg = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = fg,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun StringChip(
    guitarString: GuitarString,
    isActive: Boolean,
    isManual: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.surface
    }
    val textColor = when {
        isActive -> MaterialTheme.colorScheme.onPrimary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val borderColor = when {
        isActive -> MaterialTheme.colorScheme.primary
        !isManual -> MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)
        else -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    Box(
        modifier = Modifier
            .widthIn(min = 44.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (isManual) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${guitarString.number}",
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.5f)
            )
            Text(
                text = if (NoteFormatPreference.current == NoteFormat.EUROPEAN) guitarString.spanishName else guitarString.noteName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private data class TuningStatus(val text: String, val color: Color)

private fun tuningStatus(cents: Float): TuningStatus {
    val absCents = abs(cents)
    return when {
        absCents <= TUNED_TOLERANCE -> TuningStatus("\u2713 Afinado", COLOR_TUNED)
        absCents <= CLOSE_TOLERANCE -> {
            if (cents > 0) TuningStatus("\u2193 Baja un poco", COLOR_CLOSE)
            else TuningStatus("\u2191 Sube un poco", COLOR_CLOSE)
        }
        else -> {
            if (cents > 0) TuningStatus("\u2193 Baja bastante", COLOR_FAR)
            else TuningStatus("\u2191 Sube bastante", COLOR_FAR)
        }
    }
}

private fun statusColor(cents: Float): Color {
    val absCents = abs(cents)
    return when {
        absCents <= TUNED_TOLERANCE -> COLOR_TUNED
        absCents <= CLOSE_TOLERANCE -> COLOR_CLOSE
        else -> COLOR_FAR
    }
}

private fun centsFromTarget(freq: Float, target: Float): Float {
    if (freq <= 0f || target <= 0f) return 0f
    return (1200f * kotlin.math.log2(freq / target))
}

private fun isHarmonicRelated(detectedFreq: Float, fundamentalFreq: Float): Boolean {
    val harmonics = floatArrayOf(0.5f, 1f, 2f, 3f, 4f, 5f, 6f)
    val toleranceCents = 250f
    for (mult in harmonics) {
        val harmonicFreq = fundamentalFreq * mult
        val cents = abs(1200f * kotlin.math.log2(detectedFreq / harmonicFreq))
        if (cents < toleranceCents) return true
    }
    return false
}

private fun playReferenceTone(frequency: Float, durationMs: Int = 2000) {
    val sampleRate = 44100
    val numSamples = sampleRate * durationMs / 1000
    val fadeLen = (sampleRate * 0.05).toInt()
    val buffer = ShortArray(numSamples)
    for (i in 0 until numSamples) {
        var sample = sin(2.0 * PI * frequency * i / sampleRate)
        if (i < fadeLen) sample *= i.toDouble() / fadeLen
        if (i > numSamples - fadeLen) sample *= (numSamples - i).toDouble() / fadeLen
        buffer[i] = (sample * Short.MAX_VALUE * 0.5).toInt().toShort()
    }
    val track = android.media.AudioTrack(
        android.media.AudioManager.STREAM_MUSIC,
        sampleRate,
        android.media.AudioFormat.CHANNEL_OUT_MONO,
        android.media.AudioFormat.ENCODING_PCM_16BIT,
        buffer.size * 2,
        android.media.AudioTrack.MODE_STATIC
    )
    track.write(buffer, 0, buffer.size)
    track.play()
}
