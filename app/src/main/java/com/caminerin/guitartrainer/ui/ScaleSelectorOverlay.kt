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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
@Suppress("unused")
import androidx.compose.ui.unit.sp

private data class ScaleCategory(
    val name: String,
    val color: Color,
    val indices: List<Int>
)

private val SCALE_CATEGORIES = listOf(
    ScaleCategory("Mayores", Color(0xFF2196F3), listOf(0, 2)),      // Mayor, Pent mayor
    ScaleCategory("Menores", Color(0xFFE53935), listOf(1, 3)),      // Menor, Pent menor
    ScaleCategory("Blues", Color(0xFF9C27B0), listOf(4, 5)),        // Blues menor, Blues mayor
    ScaleCategory("Modos", Color(0xFF4CAF50), listOf(6, 7, 8, 9, 10)), // Dórica..Locria
    ScaleCategory("Armónicas", Color(0xFFFF9800), listOf(11, 12)),  // Menor arm, Menor mel
    ScaleCategory("Exóticas", Color(0xFF00BCD4), listOf(13, 14, 15, 16)) // Frigia esp, Húngara, Tonos, Cromática
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScaleSelectorOverlay(
    currentIndex: Int,
    onSelected: (Int) -> Unit,
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
                .background(Color(0xFF1E1E1E))
                .clickable(enabled = false) {}
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Selecciona escala",
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            SCALE_CATEGORIES.forEach { category ->
                Text(
                    category.name,
                    color = category.color,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    category.indices.forEach { idx ->
                        if (idx < ALL_SCALES.size) {
                            val scale = ALL_SCALES[idx]
                            val isSelected = idx == currentIndex
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(
                                        if (isSelected) category.color
                                        else category.color.copy(alpha = 0.15f)
                                    )
                                    .clickable { onSelected(idx) }
                                    .padding(horizontal = 16.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    scale.name,
                                    color = if (isSelected) Color.White else category.color,
                                    fontSize = 14.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}
