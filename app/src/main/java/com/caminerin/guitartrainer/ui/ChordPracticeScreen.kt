package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList

import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.caminerin.guitartrainer.audio.DrumEngine
import com.caminerin.guitartrainer.audio.DrumStyle
import com.caminerin.guitartrainer.audio.TickPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable

// ─── Theme (Warm Dark) — references centralized AppColors ───
private val BG = SHARED_BG
private val TOP_BAR = SHARED_TOOLBAR
private val ACCENT = SHARED_ACCENT
private val CARD_BG = AppColors.cardBg

// ─── Data model ───
private data class ProgressionChord(val semitones: Int, val quality: String)

private data class ChordProgressionDef(
    val category: String,
    val categoryColor: Color,
    val emotion: String,
    val degreeLabel: String,
    val steps: List<ProgressionChord>
)

// Shorthand builders
private fun maj(semitones: Int) = ProgressionChord(semitones, "major")
private fun min(semitones: Int) = ProgressionChord(semitones, "minor")
private fun dom7(semitones: Int) = ProgressionChord(semitones, "7")
private fun min7(semitones: Int) = ProgressionChord(semitones, "m7")

// ─── Category colors ───
private val CAT_TRISTE = Color(0xFF7E57C2)
private val CAT_AGRIDULCE = Color(0xFFD4960A)
private val CAT_BELLA = Color(0xFF26A69A)
private val CAT_FELIZ = Color(0xFF66BB6A)
private val CAT_EPICA = Color(0xFFFF7043)
private val CAT_OSCURA = Color(0xFF546E7A)
private val CAT_MISTERIOSA = Color(0xFFEF5350)
private val CAT_NEUTRAL = Color(0xFF8D6E63)
private val CAT_BUENA = Color(0xFFFFA726)
private val CAT_JAZZ = Color(0xFF42A5F5)
private val CAT_ROMANTICA = Color(0xFFEC407A)
private val CAT_DRAMATICA = Color(0xFFAB47BC)

// ══════════════════════════════════════════════════════════════════
// All chord progressions — loaded from assets/progressions.csv
// ══════════════════════════════════════════════════════════════════
private val CATEGORY_COLORS = mapOf(
    "Triste" to CAT_TRISTE, "Agridulce" to CAT_AGRIDULCE,
    "Bella" to CAT_BELLA, "Romántica" to CAT_ROMANTICA,
    "Feliz" to CAT_FELIZ, "Épica" to CAT_EPICA,
    "Oscura" to CAT_OSCURA, "Misteriosa" to CAT_MISTERIOSA,
    "Dramática" to CAT_DRAMATICA, "Neutral" to CAT_NEUTRAL,
    "Buena" to CAT_BUENA, "Jazz" to CAT_JAZZ
)

private fun parseStep(token: String): ProgressionChord {
    // Format: "quality-semitones" or "qualitySemitones" e.g. "maj0", "min9", "dom7-7", "m7-2"
    return when {
        token.startsWith("dom7-") -> dom7(token.removePrefix("dom7-").toInt())
        token.startsWith("m7-") -> min7(token.removePrefix("m7-").toInt())
        token.startsWith("maj") -> maj(token.removePrefix("maj").toInt())
        token.startsWith("min") -> min(token.removePrefix("min").toInt())
        else -> maj(0)
    }
}

private fun loadProgressionsFromCsv(context: android.content.Context): List<ChordProgressionDef> {
    val result = mutableListOf<ChordProgressionDef>()
    try {
        val reader = context.assets.open("progressions.csv").bufferedReader()
        reader.readLine() // skip header
        reader.forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val parts = line.split(",", limit = 4)
            if (parts.size < 4) return@forEachLine
            val category = parts[0].trim()
            val emotion = parts[1].trim()
            val degreeLabel = parts[2].trim()
            val stepsRaw = parts[3].trim()
            val steps = stepsRaw.split(" ").map { parseStep(it) }
            val color = CATEGORY_COLORS[category] ?: CAT_NEUTRAL
            result.add(ChordProgressionDef(category, color, emotion, degreeLabel, steps))
        }
        reader.close()
    } catch (e: Exception) {
        android.util.Log.w("ChordPractice", "Failed to load progressions.csv", e)
    }
    return result
}

// Lazy-loaded cache — populated on first access per context
private var cachedProgressions: List<ChordProgressionDef>? = null
private fun getAllProgressions(context: android.content.Context): List<ChordProgressionDef> {
    return cachedProgressions ?: loadProgressionsFromCsv(context).also { cachedProgressions = it }
}

// ═══════════════════════════════════════════════════════
// Chord name generation from root index + step
// ═══════════════════════════════════════════════════════
private fun chordNameFor(rootIndex: Int, step: ProgressionChord): String {
    val noteIndex = (rootIndex + step.semitones) % 12
    val noteName = AMERICAN_NOTE_NAMES[noteIndex]
    val suffix = when (step.quality) {
        "minor" -> "m"
        "7" -> "7"
        "m7" -> "m7"
        "maj7" -> "maj7"
        "dim" -> "dim"
        "aug" -> "aug"
        else -> ""
    }
    return noteName + suffix
}

// ═══════════════════════════════════════════════════════
// Main composable — switches between selector and player
// ═══════════════════════════════════════════════════════
@Composable
fun ChordPracticeScreen(
    onBack: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onGoToVisualizer: (() -> Unit)? = null,
    onOverlayChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    var dataLoaded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        ChordRepository.loadChords(context)
        dataLoaded = true
    }

    var selectedProgression by remember { mutableStateOf<ChordProgressionDef?>(null) }

    Box(modifier = Modifier.fillMaxSize().background(BG)) {
        if (!dataLoaded) return@Box

        val progression = selectedProgression
        if (progression == null) {
            ProgressionSelectorScreen(onSelect = { selectedProgression = it }, onOverlayChanged = onOverlayChanged)
        } else {
            ProgressionPlayerScreen(
                progression = progression,
                onBack = { selectedProgression = null }
            )
        }
    }
}

// ═══════════════════════════════════════════════════════
// 1st Screen — Progression Selector (table)
// ═══════════════════════════════════════════════════════
private fun extractDegrees(label: String): Set<String> {
    return label.split("–").map { it.trim() }.filter { it.isNotEmpty() }.toSet()
}

private val ALL_DEGREES = listOf(
    "I", "ii", "iii", "IV", "V", "vi", "vii",
    "i", "II", "III", "VI", "VII",
    "V7", "ii7",
    "bII", "bIII", "bVI", "bVII", "bii", "bvii",
    "#III", "#IV", "#VII", "#iv", "iv", "v"
)

@Composable
private fun ProgressionSelectorScreen(onSelect: (ChordProgressionDef) -> Unit, onOverlayChanged: (Boolean) -> Unit = {}) {
    val context = LocalContext.current
    val allProgressions = remember { getAllProgressions(context) }
    var selectedCategories by remember { mutableStateOf(setOf<String>()) }
    var selectedDegrees by remember { mutableStateOf(setOf<String>()) }
    var showFilterOverlay by remember { mutableStateOf(false) }
    // 0 = main menu, 1 = sentimiento, 2 = intervalos
    var filterPage by remember { mutableIntStateOf(0) }

    LaunchedEffect(showFilterOverlay) { onOverlayChanged(showFilterOverlay) }

    val allCategories = remember {
        allProgressions.map { it.category }.distinct()
    }

    val availableDegrees = remember {
        allProgressions.flatMap { extractDegrees(it.degreeLabel) }.distinct()
            .sortedWith(compareBy { ALL_DEGREES.indexOf(it).let { idx -> if (idx < 0) 999 else idx } })
    }

    val filtered = remember(selectedCategories, selectedDegrees) {
        allProgressions
            .filter { prog ->
                (selectedCategories.isEmpty() || prog.category in selectedCategories) &&
                (selectedDegrees.isEmpty() || selectedDegrees.all { deg -> deg in extractDegrees(prog.degreeLabel) })
            }
    }

    val grouped = remember(filtered) {
        filtered.groupBy { it.category }
            .toList()
            .sortedBy { (cat, _) ->
                allProgressions.indexOfFirst { it.category == cat }
            }
    }

    // Filter overlay
    if (showFilterOverlay) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .zIndex(10f)
                .background(Color.Black.copy(alpha = 0.7f))
                .clickable { showFilterOverlay = false; filterPage = 0 },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF201C16))
                    .clickable(enabled = false) {}
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (filterPage > 0) {
                        IconButton(onClick = { filterPage = 0 }) {
                            Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
                        }
                    }
                    Text(
                        when (filterPage) { 1 -> "Por sentimiento"; 2 -> "Por intervalos"; else -> "Filtrar" },
                        color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { showFilterOverlay = false; filterPage = 0 }) {
                        Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))

                when (filterPage) {
                    0 -> {
                        // Main menu: two buttons
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFD4960A))
                                .clickable { filterPage = 1 }
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("😊", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Filtrar por sentimiento", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(Color(0xFFE67E00))
                                .clickable { filterPage = 2 }
                                .padding(horizontal = 16.dp, vertical = 16.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("🎵", fontSize = 18.sp)
                                Spacer(modifier = Modifier.width(10.dp))
                                Text("Filtrar por intervalos", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        if (selectedCategories.isNotEmpty() || selectedDegrees.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color.White.copy(alpha = 0.1f))
                                    .clickable { selectedCategories = emptySet(); selectedDegrees = emptySet(); showFilterOverlay = false; filterPage = 0 }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Quitar filtros", color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                            }
                        }
                    }
                    1 -> {
                        // Sentimiento grid — multi-select with scroll
                        val catIcons = mapOf(
                            "Triste" to "\uD83D\uDE22", "Agridulce" to "\uD83E\uDE79",
                            "Bella" to "✨", "Romántica" to "\uD83D\uDC96",
                            "Feliz" to "\uD83D\uDE04", "Épica" to "⚔\uFE0F",
                            "Oscura" to "\uD83C\uDF11", "Misteriosa" to "\uD83D\uDD2E",
                            "Dramática" to "\uD83C\uDFAD", "Neutral" to "⚖\uFE0F",
                            "Buena" to "\uD83D\uDC4D", "Jazz" to "\uD83C\uDFB7"
                        )
                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            // 4-column grid — toggle select/deselect
                            val rows = allCategories.chunked(4)
                            for (row in rows) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    for (cat in row) {
                                        val catColor = allProgressions.first { it.category == cat }.categoryColor
                                        val isSelected = cat in selectedCategories
                                        val icon = catIcons[cat] ?: "\uD83C\uDFB5"
                                        Box(
                                            modifier = Modifier
                                                .weight(1f)
                                                .clip(RoundedCornerShape(12.dp))
                                                .background(if (isSelected) catColor else catColor.copy(alpha = 0.15f))
                                                .clickable {
                                                    selectedCategories = if (isSelected) selectedCategories - cat else selectedCategories + cat
                                                }
                                                .padding(horizontal = 4.dp, vertical = 10.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Text(icon, fontSize = 22.sp)
                                                Text(
                                                    cat,
                                                    color = if (isSelected) Color.White else catColor,
                                                    fontSize = 11.sp,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                                )
                                            }
                                        }
                                    }
                                    // Fill empty cells if row has less than 4
                                    repeat(4 - row.size) {
                                        Spacer(modifier = Modifier.weight(1f))
                                    }
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            // "Filtrar" confirm button
                            val matchCount = if (selectedCategories.isEmpty()) allProgressions.size
                                else allProgressions.count { it.category in selectedCategories }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ACCENT)
                                    .clickable { showFilterOverlay = false; filterPage = 0 }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (selectedCategories.isEmpty()) "Filtrar (todos)" else "Filtrar ($matchCount resultados)",
                                    color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    2 -> {
                        // Interval selector split: Mayores / Menores — multi-select with scroll
                        val majorDegrees = availableDegrees.filter { d ->
                            val clean = d.replace("b", "").replace("#", "").replace("7", "")
                            clean.firstOrNull()?.isUpperCase() == true
                        }
                        val minorDegrees = availableDegrees.filter { d ->
                            val clean = d.replace("b", "").replace("#", "").replace("7", "")
                            clean.firstOrNull()?.isLowerCase() == true
                        }

                        @Composable
                        fun DegreeChip(deg: String) {
                            val isSelected = deg in selectedDegrees
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isSelected) ACCENT else Color.White.copy(alpha = 0.1f))
                                    .clickable {
                                        selectedDegrees = if (isSelected) selectedDegrees - deg else selectedDegrees + deg
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Text(
                                    deg,
                                    color = if (isSelected) Color.Black else Color.White,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                            Text("Mayores", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (deg in majorDegrees) { DegreeChip(deg) }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            Text("Menores", color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(6.dp))
                            @OptIn(ExperimentalLayoutApi::class)
                            FlowRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.Start),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                for (deg in minorDegrees) { DegreeChip(deg) }
                            }
                            Spacer(modifier = Modifier.height(14.dp))
                            // "Filtrar" confirm button — always visible
                            val matchCount = if (selectedDegrees.isEmpty()) allProgressions.size
                                else allProgressions.count { prog ->
                                    selectedDegrees.all { deg -> deg in extractDegrees(prog.degreeLabel) }
                                }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(ACCENT)
                                    .clickable { showFilterOverlay = false; filterPage = 0 }
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    if (selectedDegrees.isEmpty()) "Filtrar (todos)" else "Filtrar ($matchCount resultados)",
                                    color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header with filter button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TOP_BAR)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Progresiones de Acordes",
                color = ACCENT,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.weight(1f))
            if (selectedCategories.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ACCENT.copy(alpha = 0.3f))
                        .clickable { selectedCategories = emptySet() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            if (selectedCategories.size == 1) selectedCategories.first()
                            else "${selectedCategories.size} sentimientos",
                            color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("\u2715", color = ACCENT.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            if (selectedDegrees.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(ACCENT.copy(alpha = 0.3f))
                        .clickable { selectedDegrees = emptySet() }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(selectedDegrees.joinToString(", "), color = ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("\u2715", color = ACCENT.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
            }
            Icon(
                Icons.Default.FilterList, "Filtros",
                tint = if (selectedCategories.isNotEmpty() || selectedDegrees.isNotEmpty()) ACCENT else Color.White.copy(alpha = 0.6f),
                modifier = Modifier
                    .size(28.dp)
                    .clickable { showFilterOverlay = true }
            )
        }

        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF201C16))
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Text(
                "Sentimiento",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1f)
            )
            Text(
                "Grados",
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.weight(1.2f)
            )
        }

        // Progression list grouped by category
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            for ((category, progressions) in grouped) {
                val categoryColor = progressions.first().categoryColor

                // Category header
                item(key = "header_$category") {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(categoryColor.copy(alpha = 0.25f))
                            .padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(categoryColor)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            category,
                            color = categoryColor,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Progression rows
                items(
                    items = progressions,
                    key = { "${it.category}_${it.emotion}_${it.degreeLabel}" }
                ) { progression ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(progression) }
                            .background(CARD_BG)
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            progression.emotion,
                            color = Color.White.copy(alpha = 0.85f),
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            progression.degreeLabel,
                            color = ACCENT,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.weight(1.2f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    // Divider
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(0.5.dp)
                            .background(Color.White.copy(alpha = 0.06f))
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// 2nd Screen — Progression Player
// ═══════════════════════════════════════════════════════
@Composable
private fun ProgressionPlayerScreen(
    progression: ChordProgressionDef,
    onBack: () -> Unit
) {
    // rememberSaveable so config survives rotation
    var rootIndex by rememberSaveable { mutableIntStateOf(0) } // C = 0
    var isPlaying by remember { mutableStateOf(false) } // transient — reset on rotation
    var bpm by rememberSaveable { mutableIntStateOf(100) }
    var loopEnabled by rememberSaveable { mutableStateOf(true) }
    var metronomeEnabled by rememberSaveable { mutableStateOf(true) }
    var currentBeatGlobal by remember { mutableIntStateOf(-1) } // transient
    var beatsPerMeasure by rememberSaveable { mutableIntStateOf(4) } // 3=3/4, 4=4/4, 6=6/8
    var selectedDrumStyle by remember { mutableStateOf<DrumStyle?>(null) } // transient — needs reinit
    var drumJob by remember { mutableStateOf<Job?>(null) }
    val drumScope = rememberCoroutineScope()
    val context = LocalContext.current

    val bpmState = rememberUpdatedState(bpm)
    val loopState = rememberUpdatedState(loopEnabled)
    val metronomeState = rememberUpdatedState(metronomeEnabled)
    val beatsState = rememberUpdatedState(beatsPerMeasure)

    val totalBeats = progression.steps.size * beatsPerMeasure
    val currentChordIndex = if (currentBeatGlobal >= 0) currentBeatGlobal / beatsPerMeasure else -1
    val beatInMeasure = if (currentBeatGlobal >= 0) currentBeatGlobal % beatsPerMeasure else -1

    // Chord names for current root
    val chordNames = remember(rootIndex, progression) {
        progression.steps.map { chordNameFor(rootIndex, it) }
    }

    // Current chord shape for diagram
    val allChords = ChordRepository.getChords()
    val currentChordShape = remember(currentChordIndex, rootIndex, progression) {
        val idx = if (currentChordIndex in progression.steps.indices) currentChordIndex else 0
        val name = chordNameFor(rootIndex, progression.steps[idx])
        val chordId = findOpenChordIdByName(name, allChords)
        chordId?.let { id -> allChords.firstOrNull { it.id == id } }
    }

    val displayChordName = if (currentChordIndex in chordNames.indices)
        chordNames[currentChordIndex]
    else
        chordNames.firstOrNull() ?: "—"

    // Pre-init drum engine so first play has no delay (ref-counted)
    LaunchedEffect(Unit) { DrumEngine.addRef(context) }

    // Single TickPlayer instance, reused across play/stop cycles
    val tickPlayer = remember { TickPlayer() }
    DisposableEffect(Unit) { onDispose { tickPlayer.release(); drumJob?.cancel(); DrumEngine.stop(); DrumEngine.releaseRef() } }

    // Keep DrumEngine.liveBpm in sync with UI bpm at all times
    LaunchedEffect(bpm) { DrumEngine.liveBpm = bpm }

    // Drum engine — launch/cancel when play state or style changes
    LaunchedEffect(isPlaying, selectedDrumStyle, beatsPerMeasure) {
        drumJob?.cancel()
        DrumEngine.stop()
        if (isPlaying && selectedDrumStyle != null) {
            drumJob = drumScope.launch {
                DrumEngine.playLoop(
                    context = context,
                    style = selectedDrumStyle!!,
                    bpm = bpm,
                    beatsPerMeasure = beatsPerMeasure
                )
            }
        }
    }

    // Metronome + beat counter loop (timing source of truth)
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            currentBeatGlobal = -1
            return@LaunchedEffect
        }
        var beat = 0
        try {
            while (isActive) {
                currentBeatGlobal = beat
                val currentBpm = bpmState.value
                if (metronomeState.value) {
                    withContext(Dispatchers.IO) {
                        tickPlayer.playBeat(currentBpm)
                    }
                } else {
                    delay(60_000L / currentBpm.toLong())
                }
                beat++
                if (beat >= totalBeats) {
                    if (loopState.value) {
                        beat = 0
                    } else {
                        break
                    }
                }
            }
        } finally {
            currentBeatGlobal = -1
            isPlaying = false
        }
    }

    // Stop playback when root or time signature changes
    LaunchedEffect(rootIndex, beatsPerMeasure) {
        isPlaying = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // ── Top bar: back + info + root selector ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TOP_BAR)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = {
                    DrumEngine.stop()
                    isPlaying = false
                    onBack()
                },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    progression.emotion,
                    color = ACCENT,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    progression.degreeLabel,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }
            // Time signature selector
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                for ((beats, label) in listOf(3 to "3/4", 4 to "4/4", 6 to "6/8")) {
                    val selected = beatsPerMeasure == beats
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(
                                if (selected) ACCENT else Color.White.copy(alpha = 0.08f)
                            )
                            .clickable { beatsPerMeasure = beats }
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            label,
                            color = if (selected) Color.Black else Color.White.copy(alpha = 0.6f),
                            fontSize = 12.sp,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                }
            }
        }

        // ── Root selector row ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1714))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                "Raíz:",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 11.sp,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 4.dp)
            )
            for (noteIdx in AMERICAN_NOTE_NAMES.indices) {
                val isSelected = noteIdx == rootIndex
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (isSelected) ACCENT else Color.White.copy(alpha = 0.08f))
                        .clickable {
                            rootIndex = noteIdx
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        AMERICAN_NOTE_NAMES[noteIdx],
                        color = if (isSelected) Color.Black else Color.White,
                        fontSize = 11.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }

        // ── Drum style selector ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1A1714))
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 8.dp, vertical = 3.dp),
            horizontalArrangement = Arrangement.spacedBy(5.dp)
        ) {
            Text("🥁", fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically).padding(end = 2.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (selectedDrumStyle == null) ACCENT else Color.White.copy(alpha = 0.08f))
                    .clickable { selectedDrumStyle = null; drumJob?.cancel(); DrumEngine.stop() }
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("Off", fontSize = 10.sp, color = if (selectedDrumStyle == null) Color.Black else Color.White)
            }
            DrumStyle.entries.forEach { style ->
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (selectedDrumStyle == style) ACCENT else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedDrumStyle = style }
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(style.displayName, fontSize = 10.sp, color = if (selectedDrumStyle == style) Color.Black else Color.White)
                }
            }
        }

        // ── Main content: chord diagram (left) + measures (right) ──
        Row(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Left: current chord name + fretboard diagram
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    displayChordName,
                    color = Color.White,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(6.dp))
                if (currentChordShape != null) {
                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth(0.95f)
                            .height(110.dp)
                    ) {
                        drawCompactChord(currentChordShape)
                    }
                } else {
                    Text(
                        "Sin diagrama",
                        color = Color.White.copy(alpha = 0.3f),
                        fontSize = 12.sp
                    )
                }


            }

            // Right: all chord measures
            Column(
                modifier = Modifier
                    .weight(1.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.Center
            ) {
                Text(
                    "Compases",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for ((idx, name) in chordNames.withIndex()) {
                        val isActive = idx == currentChordIndex ||
                            (currentChordIndex < 0 && idx == 0)
                        val borderColor = if (isActive) ACCENT else Color.White.copy(alpha = 0.15f)
                        val bgColor = if (isActive) ACCENT.copy(alpha = 0.15f) else CARD_BG

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .width(90.dp)
                                    .height(72.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(bgColor)
                                    .border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        // Jump to this chord
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "${idx + 1}",
                                        color = Color.White.copy(alpha = 0.3f),
                                        fontSize = 10.sp
                                    )
                                    Text(
                                        name,
                                        color = if (isActive) ACCENT else Color.White,
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(5.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                                for (beat in 0 until beatsPerMeasure) {
                                    val active = isPlaying && idx == currentChordIndex &&
                                        beatInMeasure >= 0 && beat <= beatInMeasure
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (active) ACCENT
                                                else Color.White.copy(alpha = 0.15f)
                                            )
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ── Bottom bar: controls ──
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(TOP_BAR)
                .padding(vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Reserve space for Ver/Practicar toggle at left
            Spacer(modifier = Modifier.width(155.dp))

            // All controls centered and equidistant
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                // Loop toggle
                IconButton(
                    onClick = { loopEnabled = !loopEnabled },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (loopEnabled) ACCENT.copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f)
                        )
                ) {
                    Icon(
                        Icons.Default.Repeat,
                        contentDescription = "Bucle",
                        tint = if (loopEnabled) ACCENT else Color.White.copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // Metronome speaker toggle
                IconButton(
                    onClick = { metronomeEnabled = !metronomeEnabled },
                    modifier = Modifier
                        .size(42.dp)
                        .clip(CircleShape)
                        .background(
                            if (metronomeEnabled) Color(0xFF8BC34A).copy(alpha = 0.3f)
                            else Color(0xFFC8B090).copy(alpha = 0.08f)
                        )
                ) {
                    Icon(
                        if (metronomeEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Metrónomo",
                        tint = if (metronomeEnabled) Color(0xFF8BC34A) else Color(0xFFF0E8D8).copy(alpha = 0.5f),
                        modifier = Modifier.size(24.dp)
                    )
                }

                // BPM controls
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { bpm = (bpm - 5).coerceAtLeast(30) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("-5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            "$bpm",
                            color = Color.White,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "BPM",
                            color = Color.White.copy(alpha = 0.4f),
                            fontSize = 10.sp
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .clickable { bpm = (bpm + 5).coerceAtMost(240) }
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text("+5", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Play/Stop
                IconButton(
                    onClick = {
                        if (isPlaying) DrumEngine.stop()
                        isPlaying = !isPlaying
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFFD84315) else Color(0xFF8BC34A))
                ) {
                    Icon(
                        if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Parar" else "Reproducir",
                        tint = Color.White,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
        }
    }
}

// ═══════════════════════════════════════════════════════
// Compact chord fretboard diagram
// ═══════════════════════════════════════════════════════
private fun DrawScope.drawCompactChord(chord: ChordShape) {
    val width = size.width
    val height = size.height
    val frets = chord.frets
    if (frets.size < 6) return

    val minFret = frets.filterNotNull().filter { it > 0 }.minOrNull() ?: 0
    val maxFret = frets.filterNotNull().filter { it > 0 }.maxOrNull() ?: 0
    val startFret = if (maxFret <= 4) 0 else (minFret - 1).coerceAtLeast(0)
    val fretsToShow = 5.coerceAtLeast(maxFret - startFret + 1)

    val topPad = height * 0.08f
    val bottomPad = height * 0.05f
    val leftPad = width * 0.08f
    val rightPad = width * 0.04f
    val fbTop = topPad
    val fbBottom = height - bottomPad
    val fbHeight = fbBottom - fbTop
    val stringSpacing = fbHeight / 7f
    val fbLeft = leftPad
    val fbRight = width - rightPad
    val fretWidth = (fbRight - fbLeft) / fretsToShow

    drawRoundRect(
        color = FRETBOARD_WOOD,
        topLeft = Offset(fbLeft, fbTop - 4f),
        size = Size(fbRight - fbLeft, fbHeight + 8f),
        cornerRadius = CornerRadius(4f)
    )

    if (startFret == 0) {
        drawRect(color = FRETBOARD_NUT, topLeft = Offset(fbLeft, fbTop - 6f), size = Size(8f, fbHeight + 12f))
    }

    for (fret in 1..fretsToShow) {
        val xPos = fbLeft + fret * fretWidth
        drawLine(FRETBOARD_FRET_WIRE, Offset(xPos, fbTop - 2f), Offset(xPos, fbBottom + 2f), strokeWidth = 1.5f)
    }

    for (stringNum in 0 until 6) {
        val yPos = fbTop + stringSpacing * (6 - stringNum)
        drawLine(FRETBOARD_STRING_COLORS[stringNum.coerceIn(0, 5)], Offset(fbLeft, yPos), Offset(fbRight, yPos), strokeWidth = 1.5f)
    }

    val chordColor = SHARED_ACCENT
    val noteRadius = (stringSpacing * 0.35f).coerceIn(8f, 20f)
    val labelPaint = android.graphics.Paint().apply {
        color = android.graphics.Color.WHITE
        textSize = 20f
        textAlign = android.graphics.Paint.Align.CENTER
        isFakeBoldText = true
        isAntiAlias = true
    }

    for (stringNum in 0 until 6) {
        val fretVal = frets[stringNum]
        val yPos = fbTop + stringSpacing * (6 - stringNum)
        when {
            fretVal == null -> {
                labelPaint.color = android.graphics.Color.argb(180, 220, 80, 80)
                drawContext.canvas.nativeCanvas.drawText("X", fbLeft * 0.5f, yPos + 7f, labelPaint)
                labelPaint.color = android.graphics.Color.WHITE
            }
            fretVal == 0 -> {
                val centerX = fbLeft * 0.5f
                drawCircle(AppColors.success, noteRadius, Offset(centerX, yPos))
                drawCircle(Color(0x44000000), noteRadius, Offset(centerX, yPos), style = Stroke(1.5f))
            }
            else -> {
                val displayPos = fretVal - startFret
                if (displayPos in 1..fretsToShow) {
                    val centerX = fbLeft + (displayPos - 0.5f) * fretWidth
                    drawCircle(chordColor, noteRadius, Offset(centerX, yPos))
                    drawCircle(Color(0x44000000), noteRadius, Offset(centerX, yPos), style = Stroke(1.5f))
                }
            }
        }
    }

    if (startFret > 0) {
        val fretPaint = android.graphics.Paint().apply {
            color = android.graphics.Color.argb(200, 255, 255, 255)
            textSize = 18f
            textAlign = android.graphics.Paint.Align.CENTER
            isAntiAlias = true
        }
        drawContext.canvas.nativeCanvas.drawText(
            "${startFret + 1}fr",
            fbLeft + fretWidth * 0.5f,
            fbBottom + bottomPad * 0.8f,
            fretPaint
        )
    }
}

// ═══════════════════════════════════════════════════════
// Chord name matching helpers
// ═══════════════════════════════════════════════════════
private fun findOpenChordIdByName(name: String, chords: List<ChordShape>): String? {
    val normalized = name.trim()
    if (normalized.isEmpty()) return null
    val matches = chords.filter { matchesChordName(it, normalized) }
    if (matches.isEmpty()) return null
    return matches.sortedWith(compareBy(
        { if (it.frets.count { fret -> fret != null } >= 4) 0 else 1 },
        { it.maxFret },
        { -(it.frets.count { fret -> fret != null }) },
        { it.priority }
    )).first().id
}

private fun matchesChordName(chord: ChordShape, name: String): Boolean {
    val root = extractChordRoot(name)
    val quality = name.removePrefix(root).trim()
    if (chord.root != root && chord.root != enharmonicEquivalent(root)) return false
    return when (quality.lowercase()) {
        "", "maj", "major" -> chord.qualityLabel == "major"
        "m", "min", "minor" -> chord.qualityLabel == "minor"
        "7" -> chord.qualityLabel == "7"
        "m7", "min7" -> chord.qualityLabel == "m7"
        "maj7" -> chord.qualityLabel == "maj7"
        "dim" -> chord.qualityLabel == "dim"
        "sus2" -> chord.qualityLabel == "sus2"
        "sus4" -> chord.qualityLabel == "sus4"
        "add9", "add(9)" -> chord.qualityLabel.contains("add9")
        else -> chord.qualityLabel.contains(quality, ignoreCase = true)
    }
}

private fun extractChordRoot(name: String): String {
    if (name.isEmpty()) return ""
    val first = name[0]
    if (name.length >= 2 && (name[1] == '#' || name[1] == 'b')) return "${first}${name[1]}"
    return "$first"
}

private fun enharmonicEquivalent(note: String): String {
    return when (note) {
        "Db" -> "C#"; "C#" -> "Db"
        "Eb" -> "D#"; "D#" -> "Eb"
        "Gb" -> "F#"; "F#" -> "Gb"
        "Ab" -> "G#"; "G#" -> "Ab"
        "Bb" -> "A#"; "A#" -> "Bb"
        else -> note
    }
}
