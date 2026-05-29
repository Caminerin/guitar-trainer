package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
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
import com.caminerin.guitartrainer.audio.TickPlayer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

// ─── Theme ───
private val BG = Color(0xFF121212)
private val TOP_BAR = Color(0xFF1A1A2E)
private val ACCENT = Color(0xFFFFC107)
private val CARD_BG = Color(0xFF1E1E2E)

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
private val CAT_AGRIDULCE = Color(0xFF5C6BC0)
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
// All chord progressions — offsets are semitones from key center
// (user picks root = I/i, offsets derive actual chord names)
// ══════════════════════════════════════════════════════════════════
private val ALL_PROGRESSIONS = listOf(

    // ─── TRISTE (Sad) ───
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Clásica pop triste", "i – VI – III – VII",
        listOf(min(0), maj(8), maj(3), maj(10))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Cinematográfica profunda", "i – VII – VI – V",
        listOf(min(0), maj(10), maj(8), maj(7))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Melancólica suave", "I – vi – iii – IV",
        listOf(maj(0), min(9), min(4), maj(5))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Tensión oscura", "i – VII – VI – V7",
        listOf(min(0), maj(10), maj(8), dom7(7))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Narrativa emocional", "i – v – VI – III – VII",
        listOf(min(0), min(7), maj(8), maj(3), maj(10))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Brillo emocional", "i – III – VII – IV",
        listOf(min(0), maj(3), maj(10), maj(5))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Vacía y fría", "i – VII – VI – III",
        listOf(min(0), maj(10), maj(8), maj(3))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Bajo fluido", "I – V – vi – iii – IV",
        listOf(maj(0), maj(7), min(9), min(4), maj(5))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Triste simple", "i – III – i – VII",
        listOf(min(0), maj(3), min(0), maj(10))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Nostálgica", "I – vi – V – II",
        listOf(maj(0), min(9), maj(7), maj(2))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Triste emotiva", "i – VII – VI – iv",
        listOf(min(0), maj(10), maj(8), min(5))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Tristeza pura", "I – iii",
        listOf(maj(0), min(4))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Trágica", "i – iv",
        listOf(min(0), min(5))),
    ChordProgressionDef("Triste", CAT_TRISTE,
        "Trágica (variante)", "i – v",
        listOf(min(0), min(7))),

    // ─── AGRIDULCE (Bittersweet) ───
    ChordProgressionDef("Agridulce", CAT_AGRIDULCE,
        "Menor con dominante", "i – V",
        listOf(min(0), maj(7))),
    ChordProgressionDef("Agridulce", CAT_AGRIDULCE,
        "Menor con séptima natural", "i – VII",
        listOf(min(0), maj(10))),
    ChordProgressionDef("Agridulce", CAT_AGRIDULCE,
        "Mayor con segundo menor", "I – ii",
        listOf(maj(0), min(2))),

    // ─── BELLA (Beautiful) ───
    ChordProgressionDef("Bella", CAT_BELLA,
        "Pop cálida universal", "I – V – vi – IV",
        listOf(maj(0), maj(7), min(9), maj(5))),
    ChordProgressionDef("Bella", CAT_BELLA,
        "Luminosa y elevadora", "I – vi – IV – V",
        listOf(maj(0), min(9), maj(5), maj(7))),
    ChordProgressionDef("Bella", CAT_BELLA,
        "Balada emocional", "vi – IV – I – V",
        listOf(min(9), maj(5), maj(0), maj(7))),
    ChordProgressionDef("Bella", CAT_BELLA,
        "Reflexiva suave", "vi – IV – V – I",
        listOf(min(9), maj(5), maj(7), maj(0))),
    ChordProgressionDef("Bella", CAT_BELLA,
        "Bucle cálido extendido", "I – vi – IV – V – IV – I",
        listOf(maj(0), min(9), maj(5), maj(7), maj(5), maj(0))),

    // ─── ROMÁNTICA (Romantic) ───
    ChordProgressionDef("Romántica", CAT_ROMANTICA,
        "Emotiva en movimiento", "i – v – VI – III",
        listOf(min(0), min(7), maj(8), maj(3))),
    ChordProgressionDef("Romántica", CAT_ROMANTICA,
        "Celestial", "I – vi",
        listOf(maj(0), min(9))),
    ChordProgressionDef("Romántica", CAT_ROMANTICA,
        "Exótica romántica", "I – IV",
        listOf(maj(0), maj(5))),

    // ─── FELIZ (Happy) ───
    ChordProgressionDef("Feliz", CAT_FELIZ,
        "Feliz y estable", "I – IV – V – I",
        listOf(maj(0), maj(5), maj(7), maj(0))),
    ChordProgressionDef("Feliz", CAT_FELIZ,
        "Simple y alegre", "I – I – IV – V",
        listOf(maj(0), maj(0), maj(5), maj(7))),
    ChordProgressionDef("Feliz", CAT_FELIZ,
        "Luminosa positiva", "I – iii – IV – V",
        listOf(maj(0), min(4), maj(5), maj(7))),
    ChordProgressionDef("Feliz", CAT_FELIZ,
        "Rock clásico", "I – V – IV – I",
        listOf(maj(0), maj(7), maj(5), maj(0))),
    ChordProgressionDef("Feliz", CAT_FELIZ,
        "Alegre ascendente", "i – bII – III – IV",
        listOf(min(0), maj(1), maj(3), maj(5))),

    // ─── ÉPICA (Epic) ───
    ChordProgressionDef("Épica", CAT_EPICA,
        "Épica poderosa", "I – bIII – bVII – IV",
        listOf(maj(0), maj(3), maj(10), maj(5))),
    ChordProgressionDef("Épica", CAT_EPICA,
        "Heroica", "I – bVI – bVII",
        listOf(maj(0), maj(8), maj(10))),
    ChordProgressionDef("Épica", CAT_EPICA,
        "Emocional e intensa", "I – ii – bVII – IV",
        listOf(maj(0), min(2), maj(10), maj(5))),

    // ─── OSCURA (Dark) ───
    ChordProgressionDef("Oscura", CAT_OSCURA,
        "Oscura profunda", "I – V – iii – vii",
        listOf(maj(0), maj(7), min(4), min(11))),
    ChordProgressionDef("Oscura", CAT_OSCURA,
        "Misteriosa neutra", "i – bII – VI – vii",
        listOf(min(0), maj(1), maj(8), min(10))),
    ChordProgressionDef("Oscura", CAT_OSCURA,
        "Espeluznante", "i – II – i – VII",
        listOf(min(0), maj(2), min(0), maj(10))),

    // ─── MISTERIOSA (Mysterious / Evil) ───
    ChordProgressionDef("Misteriosa", CAT_MISTERIOSA,
        "Tensa frigia", "i – bii",
        listOf(min(0), min(1))),
    ChordProgressionDef("Misteriosa", CAT_MISTERIOSA,
        "Siniestra cercana", "i – iii",
        listOf(min(0), min(3))),
    ChordProgressionDef("Misteriosa", CAT_MISTERIOSA,
        "Antagónica (tritono)", "i – #iv",
        listOf(min(0), min(6))),
    ChordProgressionDef("Misteriosa", CAT_MISTERIOSA,
        "Ominosa y oscura", "i – vi",
        listOf(min(0), min(8))),

    // ─── DRAMÁTICA (Dramatic) ───
    ChordProgressionDef("Dramática", CAT_DRAMATICA,
        "Tensión y resolución", "I – iv – V – vi",
        listOf(maj(0), min(5), maj(7), min(9))),
    ChordProgressionDef("Dramática", CAT_DRAMATICA,
        "Dramática menor", "i – #VII",
        listOf(min(0), maj(11))),
    ChordProgressionDef("Dramática", CAT_DRAMATICA,
        "Comedia oscura", "I – bvii",
        listOf(maj(0), min(10))),

    // ─── NEUTRAL ───
    ChordProgressionDef("Neutral", CAT_NEUTRAL,
        "Exótica / Western", "I – bII",
        listOf(maj(0), maj(1))),
    ChordProgressionDef("Neutral", CAT_NEUTRAL,
        "Cowboy", "I – VII",
        listOf(maj(0), maj(11))),
    ChordProgressionDef("Neutral", CAT_NEUTRAL,
        "Espacio exterior", "I – #IV",
        listOf(maj(0), maj(6))),
    ChordProgressionDef("Neutral", CAT_NEUTRAL,
        "Acción creciente", "i – III",
        listOf(min(0), maj(3))),
    ChordProgressionDef("Neutral", CAT_NEUTRAL,
        "Poderosa y misteriosa", "i – #III",
        listOf(min(0), maj(4))),
    ChordProgressionDef("Neutral", CAT_NEUTRAL,
        "Resolución natural", "i – VI",
        listOf(min(0), maj(8))),

    // ─── BUENA (Good sounding) ───
    ChordProgressionDef("Buena", CAT_BUENA,
        "Buena energía", "I – V",
        listOf(maj(0), maj(7))),
    ChordProgressionDef("Buena", CAT_BUENA,
        "Protagonista", "I – bVII",
        listOf(maj(0), maj(10))),
    ChordProgressionDef("Buena", CAT_BUENA,
        "Heroica", "I – bIII",
        listOf(maj(0), maj(3))),
    ChordProgressionDef("Buena", CAT_BUENA,
        "Fantástica", "I – III",
        listOf(maj(0), maj(4))),
    ChordProgressionDef("Buena", CAT_BUENA,
        "Fantástica (variante)", "I – bVI",
        listOf(maj(0), maj(8))),
    ChordProgressionDef("Buena", CAT_BUENA,
        "Heroica luminosa", "I – VI",
        listOf(maj(0), maj(9))),

    // ─── JAZZ ───
    ChordProgressionDef("Jazz", CAT_JAZZ,
        "II-V-I clásico", "ii7 – V7 – I",
        listOf(min7(2), dom7(7), maj(0))),
    ChordProgressionDef("Jazz", CAT_JAZZ,
        "Indie / Jazz suave", "I – vi – ii – V",
        listOf(maj(0), min(9), min(2), maj(7))),
    ChordProgressionDef("Jazz", CAT_JAZZ,
        "Circular", "I – V – ii – IV",
        listOf(maj(0), maj(7), min(2), maj(5)))
)

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
    @Suppress("UNUSED_PARAMETER") onGoToVisualizer: (() -> Unit)? = null
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
            ProgressionSelectorScreen(onSelect = { selectedProgression = it })
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
@Composable
private fun ProgressionSelectorScreen(onSelect: (ChordProgressionDef) -> Unit) {
    val grouped = remember {
        ALL_PROGRESSIONS.groupBy { it.category }
            .toList()
            .sortedBy { (cat, _) ->
                ALL_PROGRESSIONS.indexOfFirst { it.category == cat }
            }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
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
        }

        // Table header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF2A2A3A))
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
    var rootIndex by remember { mutableIntStateOf(0) } // C = 0
    var isPlaying by remember { mutableStateOf(false) }
    var bpm by remember { mutableIntStateOf(100) }
    var loopEnabled by remember { mutableStateOf(true) }
    var metronomeEnabled by remember { mutableStateOf(true) }
    var currentBeatGlobal by remember { mutableIntStateOf(-1) }
    var beatsPerMeasure by remember { mutableIntStateOf(4) } // 3=3/4, 4=4/4, 6=6/8

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

    // Single TickPlayer instance, reused across play/stop cycles
    val tickPlayer = remember { TickPlayer() }
    DisposableEffect(Unit) { onDispose { tickPlayer.release() } }

    // Metronome playback loop
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            currentBeatGlobal = -1
            return@LaunchedEffect
        }
        var beat = 0
        try {
            while (isActive) {
                currentBeatGlobal = beat
                if (metronomeState.value) {
                    val currentBpm = bpmState.value
                    withContext(Dispatchers.IO) {
                        tickPlayer.playBeat(currentBpm)
                    }
                } else {
                    delay(60_000L / bpmState.value.toLong())
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
                .background(Color(0xFF1A1A2A))
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
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(bgColor)
                                    .border(
                                        width = if (isActive) 2.dp else 1.dp,
                                        color = borderColor,
                                        shape = RoundedCornerShape(10.dp)
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
                            if (metronomeEnabled) Color(0xFF43A047).copy(alpha = 0.3f)
                            else Color.White.copy(alpha = 0.08f)
                        )
                ) {
                    Icon(
                        if (metronomeEnabled) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                        contentDescription = "Metrónomo",
                        tint = if (metronomeEnabled) Color(0xFF43A047) else Color.White.copy(alpha = 0.5f),
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
                    onClick = { isPlaying = !isPlaying },
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(if (isPlaying) Color(0xFFE53935) else Color(0xFF43A047))
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
        color = Color(0xFF3E2415),
        topLeft = Offset(fbLeft, fbTop - 4f),
        size = Size(fbRight - fbLeft, fbHeight + 8f),
        cornerRadius = CornerRadius(4f)
    )

    if (startFret == 0) {
        drawRect(color = Color(0xFFF0EAD6), topLeft = Offset(fbLeft, fbTop - 6f), size = Size(8f, fbHeight + 12f))
    }

    for (fret in 1..fretsToShow) {
        val xPos = fbLeft + fret * fretWidth
        drawLine(Color(0xFFBBBBBB), Offset(xPos, fbTop - 2f), Offset(xPos, fbBottom + 2f), strokeWidth = 1.5f)
    }

    for (stringNum in 0 until 6) {
        val yPos = fbTop + stringSpacing * (6 - stringNum)
        drawLine(Color(0xFFD0C4B0), Offset(fbLeft, yPos), Offset(fbRight, yPos), strokeWidth = 1.5f)
    }

    val chordColor = Color(0xFF7B1FA2)
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
                drawCircle(Color(0xFF43A047), noteRadius, Offset(centerX, yPos))
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
