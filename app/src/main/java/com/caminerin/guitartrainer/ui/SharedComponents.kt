package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== SHARED COLORS =====
val SHARED_BG = Color(0xFF1A1A1A)
val SHARED_TOOLBAR = Color(0xFF1E1E1E)
val SHARED_ACCENT = Color(0xFF7B1FA2)

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
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2A2A2A))
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
                    thumbColor = Color(0xFF7C4DFF),
                    activeTrackColor = Color(0xFF7C4DFF)
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
                            .background(if (selected) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.08f))
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
            .clip(RoundedCornerShape(10.dp))
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
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2A2A2A))
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
                (1..12).forEach { v ->
                    val selected = count == v
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(if (selected) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.08f))
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
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2A2A2A))
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
                            .background(if (selected) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.08f))
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
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2A2A2A))
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
                                    .clip(RoundedCornerShape(10.dp))
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

// ===== UNIFIED CAGED POSITION SELECTOR =====
val SHARED_CAGED_COLORS = mapOf(
    'C' to Color(0xFFE53935),
    'A' to Color(0xFFFF9800),
    'G' to Color(0xFF4CAF50),
    'E' to Color(0xFF2196F3),
    'D' to Color(0xFF9C27B0)
)

@Composable
fun CagedPositionBar(
    positions: List<ScalePosition>,
    currentIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        positions.forEachIndexed { i, pos ->
            val isCurrent = i == currentIndex
            val color = SHARED_CAGED_COLORS[pos.cagedLetter] ?: Color.Gray
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) color else color.copy(alpha = 0.2f))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    "${pos.cagedLetter}",
                    color = if (isCurrent) Color.White else color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

// ===== BPM TOOLBAR BUTTON (compact, opens overlay) =====
@Composable
fun BpmToolbarButton(bpm: Int, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF7C4DFF).copy(alpha = 0.25f))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text("$bpm BPM", color = Color(0xFFB39DDB), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
