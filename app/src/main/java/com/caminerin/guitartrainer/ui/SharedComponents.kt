package com.caminerin.guitartrainer.ui

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

// ===== SHARED COLORS =====
val SHARED_BG = Color(0xFF1A1A1A)
val SHARED_TOOLBAR = Color(0xFF1E1E1E)
val SHARED_ACCENT = Color(0xFF7B1FA2)

// ===== COLOR PALETTE for degree/interval color picker =====
val COLOR_PALETTE = listOf(
    Color(0xFFE53935) to "Rojo",
    Color(0xFF1E88E5) to "Azul",
    Color(0xFF43A047) to "Verde",
    Color(0xFF26A69A) to "Teal",
    Color(0xFF7B1FA2) to "Morado",
    Color(0xFFFF9800) to "Naranja",
    Color(0xFFFFC107) to "Amarillo",
    Color(0xFFEC407A) to "Rosa",
    Color(0xFF00BCD4) to "Cian",
    Color(0xFF8D6E63) to "Marr\u00f3n",
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
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        positions.forEachIndexed { i, pos ->
            val isCurrent = i == currentIndex
            val color = SHARED_POSITION_COLORS.getOrElse(i) { Color.Gray }
            val label = "Pos ${i + 1} (${pos.startFret}-${pos.endFret})"
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (isCurrent) color else color.copy(alpha = 0.2f))
                    .clickable { onSelect(i) }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            ) {
                Text(
                    label,
                    color = if (isCurrent) Color.White else color,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
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
                        .background(if (selected) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.06f))
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
                    .background(if (isTonalityMode) Color(0xFF7B1FA2) else Color.White.copy(alpha = 0.06f))
                    .clickable { onSelectTonality(); onDismiss() }
                    .padding(horizontal = 16.dp, vertical = 14.dp)
            ) {
                Column {
                    Text("Por tonalidad", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("Solo acordes de la escala seleccionada", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (!isTonalityMode) Color(0xFF7B1FA2) else Color.White.copy(alpha = 0.06f))
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
        4 to "4 acordes por compás"
    )
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
                        .background(if (selected) Color(0xFF7B1FA2) else Color.White.copy(alpha = 0.06f))
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
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF7C4DFF).copy(alpha = 0.25f))
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
                        .background(if (selected) Color(0xFF7C4DFF) else Color.White.copy(alpha = 0.06f))
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
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.92f)
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
                            .background(if (!isEnabled) Color.White.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.06f))
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
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF26A69A).copy(alpha = 0.25f))
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(label, color = Color(0xFF80CBC4), fontSize = 13.sp, fontWeight = FontWeight.Bold)
    }
}
