package com.caminerin.guitartrainer.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.unit.dp

@Composable
fun PitchIndicator(
    centsOff: Float,
    modifier: Modifier = Modifier,
    maxCents: Float = 50f
) {
    val animatedCents by animateFloatAsState(
        targetValue = centsOff.coerceIn(-maxCents, maxCents),
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 300f),
        label = "cents"
    )

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceVariant = MaterialTheme.colorScheme.surfaceVariant
    val inTuneColor = Color(0xFF4CAF50)
    val outOfTuneColor = MaterialTheme.colorScheme.error

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(60.dp)
            .padding(horizontal = 32.dp)
    ) {
        val centerX = size.width / 2f
        val centerY = size.height / 2f
        val trackWidth = size.width * 0.8f
        val trackStartX = centerX - trackWidth / 2f
        val trackEndX = centerX + trackWidth / 2f

        // Track background
        drawLine(
            color = surfaceVariant,
            start = Offset(trackStartX, centerY),
            end = Offset(trackEndX, centerY),
            strokeWidth = 8f,
            cap = StrokeCap.Round
        )

        // Center tick
        drawLine(
            color = inTuneColor,
            start = Offset(centerX, centerY - 20f),
            end = Offset(centerX, centerY + 20f),
            strokeWidth = 3f,
            cap = StrokeCap.Round
        )

        // Side ticks at -25, +25 cents
        for (offset in listOf(-0.5f, 0.5f)) {
            val tickX = centerX + offset * trackWidth
            drawLine(
                color = surfaceVariant,
                start = Offset(tickX, centerY - 10f),
                end = Offset(tickX, centerY + 10f),
                strokeWidth = 2f,
                cap = StrokeCap.Round
            )
        }

        // Needle position
        val needlePosition = centerX + (animatedCents / maxCents) * (trackWidth / 2f)
        val needleColor = if (kotlin.math.abs(animatedCents) < 10f) {
            inTuneColor
        } else {
            outOfTuneColor
        }

        drawCircle(
            color = needleColor,
            radius = 14f,
            center = Offset(needlePosition, centerY)
        )
    }
}
