// NOTA: Este modo está desactivado en la navegación actual de la app.
// Se conserva como código reservado para una futura versión.
// No se muestra al usuario ni se accede desde ningún menú.
package com.caminerin.guitartrainer.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector

private const val CENTS_TOLERANCE = 25f

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun VerifyMode(
    pitchResult: PitchDetector.PitchResult?,
    modifier: Modifier = Modifier
) {
    var selectedNote by remember { mutableStateOf<NoteInfo?>(null) }

    val isCorrect = selectedNote != null && pitchResult != null &&
        pitchResult.noteIndex == selectedNote!!.noteIndex &&
        kotlin.math.abs(pitchResult.centsOff) < CENTS_TOLERANCE

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Selecciona una nota y tócala",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(16.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            maxItemsInEachRow = 4
        ) {
            ALL_NOTES.forEach { note ->
                val isSelected = selectedNote == note
                NoteButton(
                    note = note,
                    isSelected = isSelected,
                    onClick = { selectedNote = note }
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (selectedNote != null) {
            val targetNote = selectedNote!!
            val targetLabel = if (targetNote.name != targetNote.altName)
                "${targetNote.spanishName} / ${targetNote.altSpanishName} (${targetNote.name} / ${targetNote.altName})"
            else
                "${targetNote.spanishName} (${targetNote.name})"
            Text(
                text = "Objetivo: $targetLabel",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(16.dp))

            if (pitchResult != null) {
                PitchIndicator(centsOff = pitchResult.centsOff)

                Spacer(modifier = Modifier.height(16.dp))

                val detectedNote = findNoteByIndex(pitchResult.noteIndex)
                val detectedLabel = getNoteName(pitchResult.noteIndex)
                Text(
                    text = "Detectado: $detectedLabel${pitchResult.octave}",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Text(
                    text = "${pitchResult.frequency.toInt()} Hz  •  ${"%+.0f".format(pitchResult.centsOff)} cents",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(16.dp))

                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    ResultBadge(isCorrect = isCorrect)
                }
            } else {
                Text(
                    text = "Toca la nota...",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.4f)
                )
            }
        }
    }
}

@Composable
private fun NoteButton(
    note: NoteInfo,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surface
    }
    val textColor = if (isSelected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (isSelected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
    }

    val hasAlt = note.name != note.altName
    Box(
        modifier = Modifier
            .padding(4.dp)
            .size(72.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(backgroundColor)
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = if (hasAlt) "${note.name}/${note.altName}" else note.name,
                fontSize = if (hasAlt) 14.sp else 18.sp,
                fontWeight = FontWeight.Bold,
                color = textColor
            )
            Text(
                text = note.spanishName,
                fontSize = 11.sp,
                color = textColor.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
private fun ResultBadge(isCorrect: Boolean) {
    val backgroundColor = if (isCorrect) Color(0xFF4CAF50) else Color(0xFFFF5252)
    val text = if (isCorrect) "✓ Correcto" else "✗ Incorrecto"

    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(backgroundColor)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            fontSize = 20.sp,
            textAlign = TextAlign.Center
        )
    }
}
