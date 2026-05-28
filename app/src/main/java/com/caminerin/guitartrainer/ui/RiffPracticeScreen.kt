package com.caminerin.guitartrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caminerin.guitartrainer.audio.RiffSynth
import com.caminerin.guitartrainer.audio.TickPlayer
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private val RP_BG = Color(0xFF1A1A1A)
private val RP_TOOLBAR = Color(0xFF1E1E1E)
private val RP_CARD = Color(0xFF252525)
private val RP_ACCENT = Color(0xFFFFC107)
private val RP_PRIMARY = Color(0xFFE65100)
private val RP_ACTIVE_NOTE = Color(0xFFFFC107)
private val RP_FRET_LINE = Color(0xFF555555)
private val RP_STRING_COLOR = Color(0xFFBDBDBD)

private val LEVEL_COLORS = listOf(
    Color(0xFF4CAF50), // 1
    Color(0xFF8BC34A), // 2
    Color(0xFFFFC107), // 3
    Color(0xFFFF9800), // 4
    Color(0xFFE53935)  // 5
)

private val SOUND_COLORS = mapOf(
    "clean" to Color(0xFF42A5F5),
    "clean_edge" to Color(0xFF66BB6A),
    "clean_chorus" to Color(0xFF7E57C2),
    "clean_bass" to Color(0xFF5C6BC0),
    "clean_tremolo" to Color(0xFF26A69A),
    "crunch" to Color(0xFFFF7043),
    "distorsion" to Color(0xFFEF5350),
    "fuzz" to Color(0xFFAB47BC),
    "surf_reverb" to Color(0xFF29B6F6),
    "funk_clean" to Color(0xFFFFCA28),
    "acoustic" to Color(0xFF8D6E63)
)

@Composable
fun RiffPracticeScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var dataLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        RiffRepository.load(context)
        dataLoaded = true
    }

    var selectedRiff by remember { mutableStateOf<Riff?>(null) }

    BackHandler {
        if (selectedRiff != null) selectedRiff = null
        else onBack()
    }

    if (selectedRiff != null) {
        RiffPlayerView(
            riff = selectedRiff!!,
            onBack = { selectedRiff = null }
        )
    } else {
        RiffCatalogView(
            onSelectRiff = { selectedRiff = it },
            onBack = onBack,
            dataLoaded = dataLoaded
        )
    }
}

// ==================== CATALOG VIEW ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RiffCatalogView(
    onSelectRiff: (Riff) -> Unit,
    onBack: () -> Unit,
    dataLoaded: Boolean
) {
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var selectedLevel by rememberSaveable { mutableIntStateOf(0) } // 0 = all
    var selectedStyle by rememberSaveable { mutableStateOf<String?>(null) }
    var showFilters by remember { mutableStateOf(false) }

    val filteredRiffs = remember(searchQuery, selectedLevel, selectedStyle, dataLoaded) {
        RiffRepository.filter(
            level = if (selectedLevel == 0) null else selectedLevel,
            style = selectedStyle,
            searchQuery = searchQuery
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RP_BG)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RP_TOOLBAR)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
            }
            Text(
                "Riffs",
                color = RP_ACCENT,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 4.dp)
            )
            Spacer(modifier = Modifier.weight(1f))
            Text(
                "${filteredRiffs.size} riffs",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp
            )
            IconButton(
                onClick = { showFilters = !showFilters },
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    Icons.Default.FilterList, "Filtros",
                    tint = if (showFilters || selectedLevel != 0 || selectedStyle != null)
                        RP_ACCENT else Color.White
                )
            }
        }

        // Search
        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            placeholder = { Text("Buscar riff o artista...", color = Color.White.copy(alpha = 0.4f)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.4f)) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF2A2A2A),
                unfocusedContainerColor = Color(0xFF2A2A2A),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White,
                cursorColor = RP_ACCENT,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            shape = RoundedCornerShape(12.dp),
            singleLine = true
        )

        // Filters panel
        if (showFilters) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF222222))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Level filter
                Text("Nivel", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val levels = listOf(0 to "Todos") + (1..5).map { it to "$it" }
                    levels.forEach { (lv, label) ->
                        val isSelected = selectedLevel == lv
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(
                                    if (isSelected) {
                                        if (lv == 0) RP_ACCENT else LEVEL_COLORS.getOrElse(lv - 1) { RP_ACCENT }
                                    } else Color(0xFF3A3A3A)
                                )
                                .clickable { selectedLevel = lv }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                label,
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Style filter
                Text("Estilo", color = Color.White.copy(alpha = 0.6f), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val allStyles = listOf(null to "Todos") + RiffRepository.getStyles().map { it to it }
                    allStyles.forEach { (st, label) ->
                        val isSelected = selectedStyle == st
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) RP_ACCENT else Color(0xFF3A3A3A))
                                .clickable { selectedStyle = st }
                                .padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Text(
                                label ?: "Todos",
                                color = if (isSelected) Color.Black else Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Riff list
        if (filteredRiffs.isEmpty() && dataLoaded) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    "No se encontraron riffs",
                    color = Color.White.copy(alpha = 0.4f),
                    fontSize = 16.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(filteredRiffs, key = { it.id }) { riff ->
                    RiffCard(riff = riff, onClick = { onSelectRiff(riff) })
                }
            }
        }
    }
}

@Composable
private fun RiffCard(riff: Riff, onClick: () -> Unit) {
    val levelColor = LEVEL_COLORS.getOrElse(riff.level - 1) { Color.Gray }
    val soundColor = SOUND_COLORS[riff.sound] ?: Color(0xFF78909C)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(RP_CARD)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level badge
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(levelColor),
            contentAlignment = Alignment.Center
        ) {
            Text(
                "${riff.level}",
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                riff.title,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                riff.artist,
                color = Color.White.copy(alpha = 0.6f),
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 2.dp)
            ) {
                SmallTag(text = riff.style.replace("_", " "), color = Color(0xFF546E7A))
                SmallTag(text = riff.sound.replace("_", " "), color = soundColor)
                SmallTag(text = "${riff.bpmTarget} bpm", color = Color(0xFF455A64))
                // Technique tags from note data
                val techniques = riff.measures.flatMap { m -> m.notes.map { it.technique } }
                    .filter { it.isNotEmpty() }.distinct()
                for (tech in techniques) {
                    val techColor = when (tech) {
                        "palm_mute" -> Color(0xFFFF7043)
                        "staccato" -> Color(0xFF42A5F5)
                        "tremolo" -> Color(0xFFAB47BC)
                        "bend" -> Color(0xFF66BB6A)
                        "hammer_on", "pull_off" -> Color(0xFF26C6DA)
                        else -> Color(0xFF78909C)
                    }
                    SmallTag(text = tech.replace("_", " "), color = techColor)
                }
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        // Meter badge
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(riff.meter, color = RP_ACCENT, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text(
                "${riff.measures.size} c.",
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 10.sp
            )
        }
    }
}

@Composable
private fun SmallTag(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(color.copy(alpha = 0.25f))
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(text, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
    }
}

// ==================== PLAYER VIEW ====================
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RiffPlayerView(riff: Riff, onBack: () -> Unit) {
    var bpm by rememberSaveable { mutableIntStateOf(riff.bpmStart) }
    var isPlaying by remember { mutableStateOf(false) }
    var loopEnabled by remember { mutableStateOf(true) }
    var metronomeOn by remember { mutableStateOf(true) }
    var currentMeasureIdx by remember { mutableIntStateOf(-1) }
    var currentSubIdx by remember { mutableIntStateOf(-1) }
    var showBpmSlider by remember { mutableStateOf(false) }

    val tickPlayer = remember { TickPlayer() }
    DisposableEffect(Unit) {
        onDispose {
            tickPlayer.release()
            RiffSynth.stop()
        }
    }

    BackHandler {
        if (isPlaying) {
            isPlaying = false
        } else {
            onBack()
        }
    }

    val bpmState = rememberUpdatedState(bpm)
    val loopState = rememberUpdatedState(loopEnabled)
    val metronomeState = rememberUpdatedState(metronomeOn)

    // Playback engine
    LaunchedEffect(isPlaying) {
        if (!isPlaying) {
            currentMeasureIdx = -1
            currentSubIdx = -1
            RiffSynth.stop()
            return@LaunchedEffect
        }
        try {
            val subsPerMeasure = riff.subdivisionsPerMeasure
            do {
                // Pre-render audio for the entire riff at current BPM
                val currentBpm = bpmState.value
                val beatsPerMeasure = riff.meter.split("/").firstOrNull()?.toIntOrNull() ?: 4
                val measureMs = (60000.0 * beatsPerMeasure / currentBpm).toLong()
                val subMs = measureMs / subsPerMeasure

                val noteEvents = mutableListOf<RiffSynth.NoteEvent>()
                var globalOffsetMs = 0L

                for (measure in riff.measures) {
                    for (note in measure.notes) {
                        val noteStartMs = globalOffsetMs + (note.startSub - 1) * subMs
                        val noteDurMs = ((note.endSub - note.startSub + 1) * subMs).toInt()
                            .coerceAtLeast(50)
                        noteEvents.add(
                            RiffSynth.NoteEvent(
                                string = note.string,
                                fret = note.fret,
                                startMs = noteStartMs,
                                durationMs = noteDurMs.coerceAtMost(2000),
                                technique = note.technique
                            )
                        )
                    }
                    globalOffsetMs += measureMs
                }

                // Play the audio
                RiffSynth.playSequence(noteEvents, riff.sound)

                // Visual sync: walk through each subdivision
                for ((mi, measure) in riff.measures.withIndex()) {
                    for (sub in 1..subsPerMeasure) {
                        if (!isActive) break
                        currentMeasureIdx = mi
                        currentSubIdx = sub

                        if (metronomeState.value && sub == 1) {
                            tickPlayer.tick()
                        }

                        delay(subMs)
                    }
                }
                if (!isActive) break
            } while (loopState.value && isActive)

            isPlaying = false
        } finally {
            RiffSynth.stop()
            currentMeasureIdx = -1
            currentSubIdx = -1
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(RP_BG)
    ) {
        // Top toolbar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RP_TOOLBAR)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = {
                isPlaying = false
                onBack()
            }, modifier = Modifier.size(36.dp)) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = Color.White)
            }

            Column(modifier = Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(
                    riff.title,
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    riff.artist,
                    color = Color.White.copy(alpha = 0.5f),
                    fontSize = 11.sp,
                    maxLines = 1
                )
            }

            // Sound badge
            val soundColor = SOUND_COLORS[riff.sound] ?: Color(0xFF78909C)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(soundColor.copy(alpha = 0.3f))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            ) {
                Text(
                    riff.sound.replace("_", " "),
                    color = soundColor,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Info bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            InfoChip("Nivel ${riff.level}", LEVEL_COLORS.getOrElse(riff.level - 1) { Color.Gray })
            InfoChip(riff.meter, Color(0xFF42A5F5))
            InfoChip(riff.key, Color(0xFF66BB6A))
            InfoChip("${riff.measures.size} compases", Color(0xFF78909C))
        }

        // Practice tip
        if (riff.practiceComment.isNotBlank()) {
            Text(
                riff.practiceComment,
                color = RP_ACCENT.copy(alpha = 0.8f),
                fontSize = 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(RP_ACCENT.copy(alpha = 0.08f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }

        // Tab notation display
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            TabNotationView(
                riff = riff,
                currentMeasureIdx = currentMeasureIdx,
                currentSubIdx = currentSubIdx
            )
        }

        // BPM Control
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF222222))
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // BPM display + controls
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Speed, null, tint = RP_ACCENT, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))

                    // Minus button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A3A))
                            .clickable { bpm = (bpm - 5).coerceAtLeast(20) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "$bpm",
                        color = Color.White,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(horizontal = 8.dp)
                            .clickable { showBpmSlider = !showBpmSlider }
                    )

                    // Plus button
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3A3A3A))
                            .clickable { bpm = (bpm + 5).coerceAtMost(300) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }

                    Text(
                        "bpm",
                        color = Color.White.copy(alpha = 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }

                // Target BPM indicator
                val bpmRatio = (bpm.toFloat() - riff.bpmStart) / (riff.bpmTarget - riff.bpmStart).toFloat().coerceAtLeast(1f)
                val bpmColor = when {
                    bpmRatio >= 1f -> Color(0xFF4CAF50)
                    bpmRatio >= 0.5f -> Color(0xFFFFC107)
                    else -> Color(0xFFFF5722)
                }
                Text(
                    "Obj: ${riff.bpmTarget}",
                    color = bpmColor,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            if (showBpmSlider) {
                Slider(
                    value = bpm.toFloat(),
                    onValueChange = { bpm = it.toInt() },
                    valueRange = 20f..300f,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                    colors = SliderDefaults.colors(
                        thumbColor = RP_ACCENT,
                        activeTrackColor = RP_ACCENT
                    )
                )
                // Quick BPM presets
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    listOf(riff.bpmStart to "Inicio", riff.bpmTarget to "Objetivo").forEach { (target, label) ->
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(if (bpm == target) RP_ACCENT else Color(0xFF3A3A3A))
                                .clickable { bpm = target }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "$label: $target",
                                color = if (bpm == target) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // Transport controls
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(RP_TOOLBAR)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Loop toggle
            IconButton(onClick = { loopEnabled = !loopEnabled }) {
                Icon(
                    Icons.Default.Repeat, "Loop",
                    tint = if (loopEnabled) RP_ACCENT else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }

            // Metronome toggle
            IconButton(onClick = { metronomeOn = !metronomeOn }) {
                Icon(
                    if (metronomeOn) Icons.Default.VolumeUp else Icons.Default.VolumeOff,
                    "Metrónomo",
                    tint = if (metronomeOn) Color.White else Color.White.copy(alpha = 0.4f),
                    modifier = Modifier.size(28.dp)
                )
            }

            // PLAY / STOP button
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(if (isPlaying) Color(0xFFE53935) else RP_PRIMARY)
                    .clickable { isPlaying = !isPlaying },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    if (isPlaying) "Parar" else "Reproducir",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // Speed presets
            val speedPresets = listOf(0.5f, 0.75f, 1.0f)
            speedPresets.forEach { factor ->
                val targetBpm = (riff.bpmTarget * factor).toInt()
                val label = when (factor) {
                    0.5f -> "50%"
                    0.75f -> "75%"
                    else -> "100%"
                }
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (bpm == targetBpm) RP_ACCENT.copy(alpha = 0.3f) else Color.Transparent)
                        .clickable { bpm = targetBpm }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        label,
                        color = if (bpm == targetBpm) RP_ACCENT else Color.White.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoChip(text: String, color: Color) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.15f))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

// ==================== TAB NOTATION VIEW ====================
@Composable
private fun TabNotationView(
    riff: Riff,
    currentMeasureIdx: Int,
    currentSubIdx: Int
) {
    val stringNames = listOf("e", "B", "G", "D", "A", "E") // 1-6

    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1E1E1E))
            .padding(4.dp)
    ) {
        val w = size.width
        val h = size.height

        val leftMargin = 30f
        val rightMargin = 16f
        val topMargin = 12f
        val bottomMargin = 12f

        val drawableW = w - leftMargin - rightMargin
        val drawableH = h - topMargin - bottomMargin

        val numStrings = 6
        val stringSpacing = drawableH / (numStrings - 1).toFloat().coerceAtLeast(1f)

        // Draw string labels
        for (s in 0 until numStrings) {
            val y = topMargin + s * stringSpacing
            drawContext.canvas.nativeCanvas.drawText(
                stringNames[s],
                8f,
                y + 5f,
                android.graphics.Paint().apply {
                    color = 0xFFBDBDBD.toInt()
                    textSize = 13f
                    typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                }
            )
        }

        // Draw tab lines (strings)
        for (s in 0 until numStrings) {
            val y = topMargin + s * stringSpacing
            drawLine(
                color = RP_STRING_COLOR.copy(alpha = 0.3f),
                start = Offset(leftMargin, y),
                end = Offset(w - rightMargin, y),
                strokeWidth = 1f
            )
        }

        // Calculate layout: how many subdivisions total
        val totalSubs = riff.measures.sumOf { riff.subdivisionsPerMeasure }
        if (totalSubs == 0) return@Canvas
        val subWidth = drawableW / totalSubs

        // Draw measure dividers
        var subOffset = 0
        for ((mi, measure) in riff.measures.withIndex()) {
            // Measure start line
            val x = leftMargin + subOffset * subWidth
            drawLine(
                color = RP_FRET_LINE,
                start = Offset(x, topMargin),
                end = Offset(x, topMargin + (numStrings - 1) * stringSpacing),
                strokeWidth = if (mi == 0) 2f else 1f
            )

            // Highlight current measure
            if (mi == currentMeasureIdx) {
                drawRect(
                    color = RP_ACCENT.copy(alpha = 0.06f),
                    topLeft = Offset(x, topMargin - 4f),
                    size = Size(
                        riff.subdivisionsPerMeasure * subWidth,
                        (numStrings - 1) * stringSpacing + 8f
                    )
                )
            }

            // Draw notes in this measure
            for (note in measure.notes) {
                val noteSubStart = subOffset + note.startSub - 1
                val noteX = leftMargin + (noteSubStart + 0.5f) * subWidth
                val stringIdx = note.string - 1 // 0-indexed (0=high E)
                val noteY = topMargin + stringIdx * stringSpacing

                val isActive = mi == currentMeasureIdx &&
                    currentSubIdx >= note.startSub && currentSubIdx <= note.endSub

                // Technique-specific colors
                val techniqueColor = when (note.technique) {
                    "palm_mute" -> Color(0xFFFF7043) // orange
                    "staccato" -> Color(0xFF42A5F5)  // blue
                    "tremolo" -> Color(0xFFAB47BC)    // purple
                    "bend" -> Color(0xFF66BB6A)       // green
                    "hammer_on" -> Color(0xFF26C6DA)  // cyan
                    "pull_off" -> Color(0xFF26C6DA)   // cyan
                    else -> null
                }

                // Note duration line
                if (note.endSub > note.startSub) {
                    val endX = leftMargin + (subOffset + note.endSub - 0.5f) * subWidth
                    val lineColor = when {
                        isActive -> RP_ACTIVE_NOTE
                        techniqueColor != null -> techniqueColor.copy(alpha = 0.5f)
                        else -> Color(0xFF666666)
                    }
                    // Palm mute: dashed look (shorter segments)
                    if (note.technique == "palm_mute") {
                        val segLen = subWidth * 0.3f
                        var sx = noteX
                        while (sx < endX) {
                            val ex = (sx + segLen).coerceAtMost(endX)
                            drawLine(lineColor, Offset(sx, noteY), Offset(ex, noteY), 3f)
                            sx += segLen * 2f
                        }
                    } else {
                        drawLine(lineColor, Offset(noteX, noteY), Offset(endX, noteY), 3f)
                    }
                }

                // Note circle background — technique-aware
                val circleRadius = (subWidth * 0.35f).coerceIn(8f, 16f)
                val circleColor = when {
                    isActive -> RP_ACTIVE_NOTE
                    techniqueColor != null -> techniqueColor
                    else -> Color(0xFF333333)
                }
                // Staccato: smaller circle; palm mute: square-ish
                if (note.technique == "staccato") {
                    drawCircle(circleColor, circleRadius * 0.8f, Offset(noteX, noteY))
                } else if (note.technique == "palm_mute") {
                    drawRect(
                        circleColor,
                        Offset(noteX - circleRadius, noteY - circleRadius * 0.8f),
                        Size(circleRadius * 2f, circleRadius * 1.6f)
                    )
                } else {
                    drawCircle(circleColor, circleRadius, Offset(noteX, noteY))
                }

                // Fret number text
                val textSize = (circleRadius * 1.2f).coerceIn(10f, 16f)
                drawContext.canvas.nativeCanvas.drawText(
                    "${note.fret}",
                    noteX,
                    noteY + textSize * 0.35f,
                    android.graphics.Paint().apply {
                        color = if (isActive || techniqueColor != null) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
                        this.textSize = textSize
                        textAlign = android.graphics.Paint.Align.CENTER
                        typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                    }
                )

                // Technique label below note
                if (note.technique.isNotEmpty()) {
                    val label = when (note.technique) {
                        "palm_mute" -> "PM"
                        "staccato" -> "."
                        "tremolo" -> "~~~"
                        "bend" -> "b"
                        "hammer_on" -> "h"
                        "pull_off" -> "p"
                        else -> ""
                    }
                    if (label.isNotEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            label,
                            noteX,
                            noteY + circleRadius + 10f,
                            android.graphics.Paint().apply {
                                color = (techniqueColor ?: Color.White).copy(alpha = 0.7f)
                                    .let { c -> android.graphics.Color.argb(
                                        (c.alpha * 255).toInt(), (c.red * 255).toInt(),
                                        (c.green * 255).toInt(), (c.blue * 255).toInt()
                                    ) }
                                this.textSize = 9f
                                textAlign = android.graphics.Paint.Align.CENTER
                                typeface = android.graphics.Typeface.create(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD)
                            }
                        )
                    }
                }
            }

            // Highlight current subdivision
            if (mi == currentMeasureIdx && currentSubIdx > 0) {
                val curSubX = leftMargin + (subOffset + currentSubIdx - 1) * subWidth
                drawLine(
                    color = RP_ACCENT.copy(alpha = 0.6f),
                    start = Offset(curSubX, topMargin - 6f),
                    end = Offset(curSubX, topMargin + (numStrings - 1) * stringSpacing + 6f),
                    strokeWidth = 2f
                )
            }

            subOffset += riff.subdivisionsPerMeasure
        }

        // Final bar line
        val endX = leftMargin + subOffset * subWidth
        drawLine(
            color = RP_FRET_LINE,
            start = Offset(endX, topMargin),
            end = Offset(endX, topMargin + (numStrings - 1) * stringSpacing),
            strokeWidth = 2f
        )
    }
}
