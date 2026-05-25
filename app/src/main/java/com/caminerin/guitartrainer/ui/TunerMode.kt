package com.caminerin.guitartrainer.ui

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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

    val currentTuning = ALL_TUNINGS[selectedTuningIndex]
    val activeString = resolveActiveString(
        isAutoMode = isAutoMode,
        selectedStringIndex = selectedStringIndex,
        tuningStrings = currentTuning.strings,
        pitchResult = pitchResult
    )

    val centsFromTarget = if (activeString != null && pitchResult != null) {
        centsFromTarget(pitchResult.frequency, activeString.frequency)
    } else {
        0f
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Tuning selector dropdown
        Box {
            OutlinedButton(onClick = { tuningMenuExpanded = true }) {
                Text(text = currentTuning.name)
            }
            DropdownMenu(
                expanded = tuningMenuExpanded,
                onDismissRequest = { tuningMenuExpanded = false }
            ) {
                ALL_TUNINGS.forEachIndexed { index, tuning ->
                    DropdownMenuItem(
                        text = { Text(tuning.name) },
                        onClick = {
                            selectedTuningIndex = index
                            selectedStringIndex = null
                            tuningMenuExpanded = false
                        }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Auto / Manual toggle
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            ToggleButton(
                text = "Auto",
                isSelected = isAutoMode,
                onClick = { isAutoMode = true }
            )
            ToggleButton(
                text = "Manual",
                isSelected = !isAutoMode,
                onClick = { isAutoMode = false }
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // String selection (only in manual mode, but always show which string is active)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            currentTuning.strings.forEachIndexed { index, string ->
                val isActive = activeString == string
                StringChip(
                    guitarString = string,
                    isActive = isActive,
                    isManual = !isAutoMode,
                    onClick = {
                        if (!isAutoMode) {
                            selectedStringIndex = index
                        }
                    }
                )
                if (index < currentTuning.strings.size - 1) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Semicircular gauge
        TunerGauge(
            cents = if (pitchResult != null && activeString != null) centsFromTarget else 0f,
            isActive = pitchResult != null && activeString != null
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Note name and frequency display
        if (activeString != null && pitchResult != null) {
            Text(
                text = "${activeString.noteName}${activeString.octave}",
                fontSize = 48.sp,
                fontWeight = FontWeight.Bold,
                color = statusColor(centsFromTarget)
            )
            Text(
                text = "${activeString.spanishName} · Cuerda ${activeString.number}",
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

            // Status badge
            val status = tuningStatus(centsFromTarget)
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
        } else {
            Text(
                text = if (isAutoMode) "Toca una cuerda..." else "Selecciona y toca una cuerda...",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
            )
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
            .height(160.dp)
            .padding(horizontal = 24.dp)
    ) {
        val centerX = size.width / 2f
        val bottomY = size.height - 10f
        val radius = size.width * 0.42f

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

        // Colored zones on the arc
        val zoneRadius = radius
        // Green zone (center ±10 cents = ±36°)
        drawArc(
            color = COLOR_TUNED.copy(alpha = 0.3f),
            startAngle = 180f + 90f - 18f,
            sweepAngle = 36f,
            useCenter = false,
            topLeft = Offset(centerX - zoneRadius, bottomY - zoneRadius),
            size = androidx.compose.ui.geometry.Size(zoneRadius * 2, zoneRadius * 2),
            style = Stroke(width = 12f, cap = StrokeCap.Round)
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

        // Needle pivot circle
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
            .clip(RoundedCornerShape(10.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(10.dp))
            .then(if (isManual) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 10.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${guitarString.number}",
                fontSize = 10.sp,
                color = textColor.copy(alpha = 0.5f)
            )
            Text(
                text = guitarString.noteName,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
        }
    }
}

private fun resolveActiveString(
    isAutoMode: Boolean,
    selectedStringIndex: Int?,
    tuningStrings: List<GuitarString>,
    pitchResult: PitchDetector.PitchResult?
): GuitarString? {
    if (!isAutoMode) {
        return selectedStringIndex?.let { tuningStrings.getOrNull(it) }
    }
    if (pitchResult == null) return null
    return tuningStrings.minByOrNull { abs(centsFromTarget(pitchResult.frequency, it.frequency)) }
}

private data class TuningStatus(val text: String, val color: Color)

private fun tuningStatus(cents: Float): TuningStatus {
    val absCents = abs(cents)
    return when {
        absCents <= TUNED_TOLERANCE -> TuningStatus("✓ Afinado", COLOR_TUNED)
        absCents <= CLOSE_TOLERANCE -> {
            if (cents > 0) TuningStatus("↓ Baja un poco", COLOR_CLOSE)
            else TuningStatus("↑ Sube un poco", COLOR_CLOSE)
        }
        else -> {
            if (cents > 0) TuningStatus("↓ Baja bastante", COLOR_FAR)
            else TuningStatus("↑ Sube bastante", COLOR_FAR)
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

private fun centsFromTarget(detected: Float, target: Float): Float {
    return (1200f * kotlin.math.log2(detected / target))
}
