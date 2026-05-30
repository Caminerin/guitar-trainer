package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

val CHROMATIC_COLORS = listOf(
    Color(0xFFC8B400), Color(0xFFB8A020), Color(0xFFD4842A), Color(0xFFCC6644),
    Color(0xFFBB4444), Color(0xFFCC3388), Color(0xFFAA33AA), Color(0xFF8844BB),
    Color(0xFF6655CC), Color(0xFF4477AA), Color(0xFF338888), Color(0xFF559944)
)

@Composable
fun ChromaticCircleOverlay(
    selectedNote: Int,
    rootNote: Int,
    scaleIntervals: List<Int>,
    onNoteSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
    relativeMajorOffset: Int = 0
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(10f)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        val circleSize = 500.dp
        Canvas(
            modifier = Modifier
                .size(circleSize)
                .pointerInput(Unit) {
                    detectTapGestures { tapOffset ->
                        val cx = size.width / 2f
                        val cy = size.height / 2f
                        val dx = tapOffset.x - cx
                        val dy = tapOffset.y - cy
                        val dist = sqrt(dx * dx + dy * dy)
                        val outerR = min(size.width, size.height) / 2f * 0.95f
                        val innerR = min(size.width, size.height) / 2f * 0.35f

                        if (dist in innerR..outerR) {
                            var angle = atan2(dy, dx).toDouble()
                            val segAngle = 2.0 * PI / 12.0
                            angle += PI / 2.0 + segAngle / 2.0
                            if (angle < 0) angle += 2.0 * PI
                            if (angle >= 2.0 * PI) angle -= 2.0 * PI
                            val segmentIndex = (angle / segAngle).toInt() % 12
                            onNoteSelected(segmentIndex)
                        } else if (dist < innerR) {
                            onDismiss()
                        }
                    }
                }
        ) {
            drawChromaticCircleShared(
                center = Offset(size.width / 2f, size.height / 2f),
                maxRadius = min(size.width, size.height) / 2f,
                selectedNote = selectedNote,
                alpha = 1f,
                rootNote = rootNote,
                scaleIntervals = scaleIntervals,
                relativeMajorOffset = relativeMajorOffset
            )
        }
    }
}

fun DrawScope.drawChromaticCircleShared(
    center: Offset,
    maxRadius: Float,
    selectedNote: Int,
    alpha: Float,
    rootNote: Int,
    scaleIntervals: List<Int>,
    relativeMajorOffset: Int = 0
) {
    val outerRadius = maxRadius * 0.95f
    val innerRadius = maxRadius * 0.35f
    val midRadius = (outerRadius + innerRadius) / 2f

    val segmentAngle = (2 * PI / 12).toFloat()
    val startAngleOffset = (-PI / 2 - segmentAngle / 2).toFloat()

    for (i in 0 until 12) {
        val angleStart = startAngleOffset + i * segmentAngle
        val angleEnd = angleStart + segmentAngle
        val isSelected = i == selectedNote
        val isInScale = scaleIntervals.contains((i - rootNote + 12) % 12)

        val segColor = if (isSelected) {
            CHROMATIC_COLORS[i]
        } else if (isInScale) {
            CHROMATIC_COLORS[i].copy(alpha = 0.7f * alpha)
        } else {
            CHROMATIC_COLORS[i].copy(alpha = 0.25f * alpha)
        }

        val segOuter = if (isSelected) outerRadius + 6f else outerRadius
        val segInner = if (isSelected) innerRadius - 4f else innerRadius

        val path = Path().apply {
            moveTo(center.x + segInner * cos(angleStart), center.y + segInner * sin(angleStart))
            lineTo(center.x + segOuter * cos(angleStart), center.y + segOuter * sin(angleStart))
            val steps = 16
            for (step in 1..steps) {
                val a = angleStart + (angleEnd - angleStart) * step / steps
                lineTo(center.x + segOuter * cos(a), center.y + segOuter * sin(a))
            }
            lineTo(center.x + segInner * cos(angleEnd), center.y + segInner * sin(angleEnd))
            for (step in (steps - 1) downTo 0) {
                val a = angleStart + (angleEnd - angleStart) * step / steps
                lineTo(center.x + segInner * cos(a), center.y + segInner * sin(a))
            }
            close()
        }

        drawPath(path, segColor, style = Fill)
        drawPath(path, Color(0x33000000).copy(alpha = alpha * 0.3f), style = Stroke(1.5f))

        val labelAngle = angleStart + segmentAngle / 2f
        val labelX = center.x + midRadius * cos(labelAngle)
        val labelY = center.y + midRadius * sin(labelAngle)

        val label = getChromaticNames(rootNote, relativeMajorOffset)[i]
        val hasSlash = label.contains("/")
        val textSize = if (hasSlash) {
            if (isSelected) 72f else 54f
        } else {
            if (isSelected) 126f else 90f
        }
        val labelPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb((255 * alpha).toInt(), 255, 255, 255)
            this.textSize = textSize
            textAlign = android.graphics.Paint.Align.CENTER
            isFakeBoldText = isSelected
            isAntiAlias = true
            setShadowLayer(4f, 1f, 1f, android.graphics.Color.argb(180, 0, 0, 0))
        }
        if (hasSlash) {
            val parts = label.split("/")
            drawContext.canvas.nativeCanvas.drawText(parts[0], labelX, labelY - textSize * 0.1f, labelPaint)
            drawContext.canvas.nativeCanvas.drawText(parts[1], labelX, labelY + textSize * 0.9f, labelPaint)
        } else {
            drawContext.canvas.nativeCanvas.drawText(label, labelX, labelY + textSize * 0.35f, labelPaint)
        }
    }

    drawCircle(Color(0xFF111111).copy(alpha = alpha), innerRadius, center)
    drawCircle(Color(0x33FFFFFF).copy(alpha = alpha * 0.3f), innerRadius, center, style = Stroke(2f))

    val centerLabel = getChromaticNames(rootNote, relativeMajorOffset)[selectedNote]
    val centerHasSlash = centerLabel.contains("/")
    val centerTextSize = if (centerHasSlash) 96f else 168f
    val centerPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.argb((255 * alpha).toInt(), 255, 255, 255)
        textSize = centerTextSize
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }
    if (centerHasSlash) {
        val parts = centerLabel.split("/")
        drawContext.canvas.nativeCanvas.drawText(parts[0], center.x, center.y - 10f, centerPaint)
        drawContext.canvas.nativeCanvas.drawText(parts[1], center.x, center.y + centerTextSize, centerPaint)
    } else {
        drawContext.canvas.nativeCanvas.drawText(centerLabel, center.x, center.y + 60f, centerPaint)
    }
}

@Composable
fun ChromaticCircleOverlay(
    selectedNote: Int,
    onNoteSelected: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    ChromaticCircleOverlay(
        selectedNote = selectedNote,
        rootNote = selectedNote,
        scaleIntervals = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
        onNoteSelected = onNoteSelected,
        onDismiss = onDismiss
    )
}
