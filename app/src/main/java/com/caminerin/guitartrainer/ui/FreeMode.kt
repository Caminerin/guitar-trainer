// NOTA: Este modo está desactivado en la navegación actual de la app.
// Se conserva como código reservado para una futura versión.
// No se muestra al usuario ni se accede desde ningún menú.
package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.PitchDetector

@Composable
fun FreeMode(
    pitchResult: PitchDetector.PitchResult?,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        if (pitchResult != null) {
            val displayName = getNoteName(pitchResult.noteIndex)

            Text(
                text = displayName,
                fontSize = 64.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            val noteInfo = findNoteByIndex(pitchResult.noteIndex)
            val hasAlt = noteInfo.name != noteInfo.altName
            val subtitle = if (hasAlt) "${noteInfo.name}/${noteInfo.altName}${pitchResult.octave}" else "${pitchResult.noteName}${pitchResult.octave}"
            Text(
                text = subtitle,
                fontSize = 28.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${pitchResult.frequency.toInt()} Hz",
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f)
            )

            Spacer(modifier = Modifier.height(24.dp))

            PitchIndicator(centsOff = pitchResult.centsOff)

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "${"%+.0f".format(pitchResult.centsOff)} cents",
                fontSize = 14.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.5f)
            )
        } else {
            Text(
                text = "Toca una nota...",
                fontSize = 24.sp,
                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
            )
        }
    }
}
