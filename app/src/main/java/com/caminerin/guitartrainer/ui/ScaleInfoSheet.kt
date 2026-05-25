package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Divider
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val DEGREE_COLORS = listOf(
    Color(0xFFE53935), // 1
    Color(0xFFFF9800), // 2
    Color(0xFF1E88E5), // 3
    Color(0xFF4CAF50), // 4
    Color(0xFF9C27B0), // 5
    Color(0xFF00BCD4), // 6
    Color(0xFFFF5722), // 7
)

@Composable
fun ScaleInfoSheet(
    rootNote: Int,
    scale: Scale,
    onDismiss: () -> Unit
) {
    val chords = getScaleChords(rootNote, scale.intervals)
    val rootName = SPANISH_CHROMATIC_NAMES[rootNote]

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1E1E1E))
                .clickable(enabled = false) {} // block clicks through
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        "$rootName ${scale.name}",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${scale.intervals.size} notas",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 13.sp
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Notes row
            Text("Notas", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                chords.forEach { chord ->
                    val colorIdx = (chord.degree - 1).coerceIn(0, DEGREE_COLORS.size - 1)
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(DEGREE_COLORS[colorIdx]),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            chord.noteName,
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(16.dp))

            // Chords table
            Text("Acordes", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(12.dp))

            // Header row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Grado", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
                    modifier = Modifier.weight(0.15f), textAlign = TextAlign.Center)
                Text("Nota", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
                    modifier = Modifier.weight(0.2f), textAlign = TextAlign.Center)
                Text("Acorde", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
                    modifier = Modifier.weight(0.35f), textAlign = TextAlign.Center)
                Text("Intervalo", color = Color.White.copy(alpha = 0.4f), fontSize = 11.sp,
                    modifier = Modifier.weight(0.3f), textAlign = TextAlign.Center)
            }

            Spacer(modifier = Modifier.height(6.dp))

            chords.forEach { chord ->
                val colorIdx = (chord.degree - 1).coerceIn(0, DEGREE_COLORS.size - 1)
                val degreeRoman = when (chord.degree) {
                    1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"
                    5 -> "V"; 6 -> "VI"; 7 -> "VII"; else -> "${chord.degree}"
                }
                val displayDegree = when (chord.chordType) {
                    "menor" -> degreeRoman.lowercase()
                    "dim" -> degreeRoman.lowercase() + "°"
                    "aug" -> degreeRoman + "+"
                    else -> degreeRoman
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(DEGREE_COLORS[colorIdx].copy(alpha = 0.1f))
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        displayDegree,
                        color = DEGREE_COLORS[colorIdx],
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.15f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        chord.noteName,
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(0.2f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        "${chord.noteName} ${chord.chordType}",
                        color = Color.White.copy(alpha = 0.8f),
                        fontSize = 14.sp,
                        modifier = Modifier.weight(0.35f),
                        textAlign = TextAlign.Center
                    )
                    Text(
                        chord.intervalName,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 12.sp,
                        modifier = Modifier.weight(0.3f),
                        textAlign = TextAlign.Center
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            Spacer(modifier = Modifier.height(16.dp))
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(12.dp))

            // Intervals row
            Text("Intervalos (semitonos)", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                scale.intervals.forEachIndexed { idx, interval ->
                    val nextInterval = if (idx + 1 < scale.intervals.size) scale.intervals[idx + 1] else 12
                    val step = nextInterval - interval
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$interval",
                            color = Color.White.copy(alpha = 0.7f),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        if (idx < scale.intervals.size - 1) {
                            Text(
                                "→$step",
                                color = Color.White.copy(alpha = 0.3f),
                                fontSize = 10.sp
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
