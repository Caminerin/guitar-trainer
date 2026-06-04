package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.caminerin.guitartrainer.audio.DrumEngine
import com.caminerin.guitartrainer.audio.DrumStyle
import com.caminerin.guitartrainer.audio.RiffSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Category metadata (icon + color)
private data class CategoryInfo(val icon: String, val color: Color, val description: String)
private val CATEGORY_MAP = mapOf(
    "Técnica" to CategoryInfo("🎯", Color(0xFFD4960A), "Ejercicios de técnica pura"),
    "Escalas" to CategoryInfo("🎼", Color(0xFF8BC34A), "Escalas, arpegios y patrones"),
    "Riffs" to CategoryInfo("🎸", Color(0xFFE67E00), "Riffs y licks por estilo"),
    "Acordes" to CategoryInfo("🤘", Color(0xFF9C6ADE), "Cambios y progresiones"),
    "Piezas" to CategoryInfo("🎵", Color(0xFF4A90D9), "Piezas clásicas de dominio público"),
    "Mis tabs" to CategoryInfo("📂", Color(0xFF35C89A), "Tabs importados por ti"),
)

private val LEVEL_COLORS = mapOf(
    "Principiante" to Color(0xFF8BC34A),
    "Intermedio" to Color(0xFFD4960A),
    "Avanzado" to Color(0xFFD84315),
)

// ===================== CATALOG SCREEN =====================
@Composable
fun TabPracticeScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var loadError by remember { mutableStateOf<String?>(null) }
    var selectedCategory by remember { mutableStateOf<String?>(null) }
    var selectedLevel by remember { mutableStateOf<String?>(null) }
    var selectedEntry by remember { mutableStateOf<CatalogEntry?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var catalogRefresh by remember { mutableIntStateOf(0) }

    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                try {
                    val contentResolver = context.contentResolver
                    val fileName = run {
                        val cursor = contentResolver.query(uri, null, null, null, null)
                        val name = cursor?.use {
                            val idx = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                            if (it.moveToFirst() && idx >= 0) it.getString(idx) else null
                        }
                        name ?: uri.lastPathSegment ?: "tab_file"
                    }
                    val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        withContext(Dispatchers.IO) {
                            TabRepository.importUserTab(context, fileName, bytes)
                        }
                        TabRepository.reset()
                        TabRepository.loadCatalog(context)
                        catalogRefresh++
                    }
                } catch (e: Exception) {
                    android.util.Log.e("TabPractice", "Error importing tab", e)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        try {
            TabRepository.loadCatalog(context)
            loadError = TabRepository.loadError
        } catch (e: Throwable) {
            loadError = "${e.javaClass.simpleName}: ${e.message}"
        }
        loading = false
    }

    if (selectedEntry != null) {
        TabPlayerScreen(
            entry = selectedEntry!!,
            onBack = { selectedEntry = null }
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface)
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showBackButton || selectedCategory != null) {
                IconButton(onClick = {
                    if (selectedCategory != null) {
                        selectedCategory = null
                        selectedLevel = null
                        searchQuery = ""
                    } else {
                        onBack()
                    }
                }) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = AppColors.text)
                }
            }
            Text(
                if (selectedCategory != null) selectedCategory!! else "Biblioteca",
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (!showBackButton && selectedCategory == null) 12.dp else 0.dp)
            )
            Spacer(Modifier.weight(1f))
            if (!loading && selectedCategory != null) {
                val count = TabRepository.filter(searchQuery, category = selectedCategory, level = selectedLevel).size
                Text("$count", color = AppColors.textSecondary, fontSize = 12.sp)
            }
            // Import button
            Icon(
                Icons.Default.Add, "Importar tab",
                tint = AppColors.success,
                modifier = Modifier
                    .padding(start = 8.dp, end = 8.dp)
                    .size(24.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { filePickerLauncher.launch(arrayOf("*/*")) }
            )
        }

        if (loading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Cargando catálogo...", color = AppColors.textSecondary, fontSize = 16.sp)
            }
        } else if (loadError != null) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error", color = AppColors.error, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(loadError ?: "", color = AppColors.textSecondary, fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 32.dp))
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.tertiary)
                            .clickable {
                                loading = true
                                scope.launch {
                                    try { TabRepository.reset(); TabRepository.loadCatalog(context); loadError = TabRepository.loadError }
                                    catch (e: Throwable) { loadError = "${e.javaClass.simpleName}: ${e.message}" }
                                    loading = false
                                }
                            }
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        Text("Reintentar", color = Color.White, fontSize = 14.sp)
                    }
                }
            }
        } else if (selectedCategory == null) {
            // ===== CATEGORY GRID =====
            CategoryGridView(onCategorySelected = { selectedCategory = it })
        } else {
            // ===== ENTRIES LIST FOR SELECTED CATEGORY =====
            CategoryDetailView(
                category = selectedCategory!!,
                selectedLevel = selectedLevel,
                searchQuery = searchQuery,
                onLevelSelected = { selectedLevel = if (selectedLevel == it) null else it },
                onSearchQueryChanged = { searchQuery = it },
                onEntrySelected = { selectedEntry = it },
                onDeleteUserTab = { tabId ->
                    TabRepository.deleteUserTab(context, tabId)
                    scope.launch {
                        TabRepository.reset()
                        TabRepository.loadCatalog(context)
                        catalogRefresh++
                    }
                }
            )
        }
    }
}

@Composable
private fun CategoryGridView(onCategorySelected: (String) -> Unit) {
    val categories = TabRepository.getCategories()
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        items(categories) { category ->
            val info = CATEGORY_MAP[category] ?: CategoryInfo("📄", AppColors.textSecondary, "")
            val count = TabRepository.filter(category = category).size
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(AppColors.surface)
                    .clickable { onCategorySelected(category) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(info.icon, fontSize = 28.sp)
                Spacer(Modifier.width(14.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(category, color = info.color, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    if (info.description.isNotBlank()) {
                        Text(info.description, color = AppColors.textSecondary, fontSize = 12.sp)
                    }
                }
                Text("$count", color = AppColors.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CategoryDetailView(
    category: String,
    selectedLevel: String?,
    searchQuery: String,
    onLevelSelected: (String) -> Unit,
    onSearchQueryChanged: (String) -> Unit,
    onEntrySelected: (CatalogEntry) -> Unit,
    onDeleteUserTab: (String) -> Unit
) {
    val levels = listOf("Principiante", "Intermedio", "Avanzado")

    Column(modifier = Modifier.fillMaxSize()) {
        // Level filter chips (only for exercise categories)
        if (category != "Mis tabs" && category != "Piezas") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                levels.forEach { level ->
                    val isSelected = selectedLevel == level
                    val levelColor = LEVEL_COLORS[level] ?: AppColors.textSecondary
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) levelColor.copy(alpha = 0.2f) else AppColors.surface)
                            .clickable { onLevelSelected(level) }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Text(level, color = if (isSelected) levelColor else AppColors.textSecondary,
                            fontSize = 13.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
                    }
                }
                if (selectedLevel != null) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(AppColors.error.copy(alpha = 0.15f))
                            .clickable { onLevelSelected(selectedLevel) }
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        Text("✕", color = AppColors.error, fontSize = 13.sp)
                    }
                }
            }
        }

        // Search field for "Piezas" category (many entries)
        if (category == "Piezas") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(AppColors.surface)
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.FilterList, "Buscar", tint = AppColors.textSecondary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                androidx.compose.foundation.text.BasicTextField(
                    value = searchQuery,
                    onValueChange = onSearchQueryChanged,
                    textStyle = androidx.compose.ui.text.TextStyle(color = AppColors.text, fontSize = 14.sp),
                    modifier = Modifier.weight(1f),
                    decorationBox = { inner ->
                        if (searchQuery.isEmpty()) {
                            Text("Buscar por nombre o compositor...", color = AppColors.textSecondary, fontSize = 14.sp)
                        }
                        inner()
                    }
                )
                if (searchQuery.isNotEmpty()) {
                    Icon(Icons.Default.Close, "Limpiar", tint = AppColors.textSecondary,
                        modifier = Modifier.size(18.dp).clickable { onSearchQueryChanged("") })
                }
            }
        }

        // Entries list grouped by subcategory
        val filteredEntries = TabRepository.filter(searchQuery = searchQuery, category = category, level = selectedLevel)
        val grouped = filteredEntries.groupBy { it.subcategory.ifBlank { it.artist } }
        val listState = rememberLazyListState()

        LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
            grouped.forEach { (group, entries) ->
                // Group header
                item(key = "header_$group") {
                    Text(
                        group,
                        color = CATEGORY_MAP[category]?.color ?: AppColors.tertiary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.surface.copy(alpha = 0.5f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }
                items(entries, key = { it.path }) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEntrySelected(entry) }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(entry.song, color = AppColors.text, fontSize = 14.sp,
                                fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (category == "Piezas" && entry.artist.isNotBlank()) {
                                Text(entry.artist, color = AppColors.textSecondary, fontSize = 12.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                        // Level badge
                        if (entry.level.isNotBlank() && category != "Piezas") {
                            val badgeColor = LEVEL_COLORS[entry.level] ?: AppColors.textSecondary
                            Text(
                                entry.level.take(4),
                                color = badgeColor,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp)
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(badgeColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        // BPM
                        Text("${entry.tempo}", color = AppColors.textSecondary, fontSize = 12.sp,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center)
                        // Delete button for user tabs
                        if (entry.isUserTab) {
                            Icon(Icons.Default.Delete, "Eliminar", tint = AppColors.error.copy(alpha = 0.6f),
                                modifier = Modifier.padding(start = 6.dp).size(20.dp).clickable {
                                    onDeleteUserTab(entry.path.removePrefix("user://"))
                                })
                        }
                    }
                }
            }
        }
    }
}



// ===================== TAB PLAYER SCREEN =====================
@Composable
fun TabPlayerScreen(
    entry: CatalogEntry,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var song by remember { mutableStateOf<TabSong?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var selectedTrackIndex by remember { mutableIntStateOf(0) }
    var isPlaying by remember { mutableStateOf(false) }
    var currentMeasure by remember { mutableIntStateOf(0) }
    var currentBeatInMeasure by remember { mutableIntStateOf(0) }
    var bpmFactor by remember { mutableFloatStateOf(1f) }
    var loopStart by remember { mutableIntStateOf(-1) }
    var loopEnd by remember { mutableIntStateOf(-1) }
    var loopEnabled by remember { mutableStateOf(false) }
    var countdownEnabled by remember { mutableStateOf(false) }
    var countdownText by remember { mutableStateOf<String?>(null) }
    var playJob by remember { mutableStateOf<Job?>(null) }
    var selectedDrumStyle by remember { mutableStateOf<DrumStyle?>(null) }
    var drumJob by remember { mutableStateOf<Job?>(null) }
    var subdivision by remember { mutableIntStateOf(1) }
    var showSubdivisionMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        try {
            song = TabRepository.downloadSong(context, entry)
            if (song == null) {
                error = "Error descargando la canción"
            } else {
                val playable = song!!.playableTracks()
                if (playable.isNotEmpty()) selectedTrackIndex = song!!.tracks.indexOf(playable[0])
            }
        } catch (e: Throwable) {
            error = "${e.javaClass.simpleName}: ${e.message}"
        }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            playJob?.cancel()
            drumJob?.cancel()
            RiffSynth.release()
            DrumEngine.release()
        }
    }

    // Drum engine sync
    LaunchedEffect(isPlaying, selectedDrumStyle) {
        drumJob?.cancel()
        DrumEngine.stop()
        if (isPlaying && selectedDrumStyle != null) {
            drumJob = scope.launch {
                DrumEngine.playLoop(
                    context = context,
                    style = selectedDrumStyle!!,
                    bpm = (entry.tempo * bpmFactor).toInt().coerceIn(30, 300),
                    beatsPerMeasure = 4
                )
            }
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        isPlaying = false
        countdownText = null
        RiffSynth.stop()
    }

    fun measureDurationMs(track: TabTrack, measureIdx: Int, baseTempo: Int): Long {
        val beatDurationMs = 60_000.0 / baseTempo
        var totalMs = 0L
        for (beat in track.measures[measureIdx]) {
            val dur = beatDurationMs * (4.0 / beat.duration)
            totalMs += if (beat.isDotted) (dur * 1.5).toLong() else dur.toLong()
        }
        return totalMs
    }

    fun buildContinuousSequence(
        track: TabTrack,
        startMeasure: Int,
        endMeasure: Int,
        baseTempo: Int
    ): Triple<List<RiffSynth.NoteEvent>, List<Long>, Long> {
        val beatDurationMs = 60_000.0 / baseTempo
        val allEvents = mutableListOf<RiffSynth.NoteEvent>()
        val measureOffsets = mutableListOf<Long>()
        var globalTimeMs = 0L

        for (mi in startMeasure until endMeasure) {
            measureOffsets.add(globalTimeMs)
            val measure = track.measures[mi]
            var localTimeMs = 0L

            for (beat in measure) {
                val durationBeat = beatDurationMs * (4.0 / beat.duration)
                val dur = if (beat.isDotted) (durationBeat * 1.5).toLong() else durationBeat.toLong()

                if (!beat.isRest) {
                    for (note in beat.notes) {
                        if (note.string in 1..6 && note.fret >= 0) {
                            allEvents.add(RiffSynth.NoteEvent(
                                string = note.string,
                                fret = note.fret,
                                startMs = globalTimeMs + localTimeMs,
                                durationMs = dur.toInt().coerceAtLeast(50),
                                technique = note.effects.firstOrNull() ?: ""
                            ))
                        }
                    }
                }
                localTimeMs += dur
            }
            globalTimeMs += localTimeMs
        }
        return Triple(allEvents, measureOffsets, globalTimeMs)
    }

    fun startPlayback(fromMeasure: Int? = null) {
        if (song == null) return
        val track = song!!.tracks[selectedTrackIndex]
        isPlaying = true
        if (fromMeasure != null) {
            currentMeasure = fromMeasure
            currentBeatInMeasure = 0
        }

        playJob = scope.launch {
            // Countdown before playing
            if (countdownEnabled) {
                val baseTempo = (entry.tempo * bpmFactor).toInt().coerceIn(30, 300)
                val beatMs = 60_000L / baseTempo
                val startIdx = fromMeasure ?: currentMeasure
                val beatsInMeasure = if (startIdx < track.measures.size)
                    track.measures[startIdx].size.coerceIn(2, 8) else 4
                for (i in beatsInMeasure downTo 1) {
                    if (!isActive || !isPlaying) { countdownText = null; return@launch }
                    countdownText = "$i"
                    delay(beatMs)
                }
                countdownText = null
            }

            withContext(Dispatchers.Default) {
                RiffSynth.init(context)

                do {
                    val playStart = if (fromMeasure != null && !loopEnabled) fromMeasure
                        else if (loopEnabled && loopStart >= 0) loopStart
                        else currentMeasure
                    val playEnd = if (loopEnabled && loopEnd >= 0) (loopEnd + 1).coerceAtMost(track.measures.size)
                        else track.measures.size

                    if (playStart >= playEnd) break

                    // Play measure by measure so BPM changes apply in real-time
                    var measureIdx = playStart

                    while (measureIdx < playEnd && isActive && isPlaying) {
                        // Re-read bpmFactor each measure for real-time BPM changes
                        val liveTempo = (entry.tempo * bpmFactor).toInt().coerceIn(30, 300)
                        val beatDurationMs = 60_000.0 / liveTempo

                        val (notes, _, _) = buildContinuousSequence(
                            track, measureIdx, measureIdx + 1, liveTempo
                        )

                        if (notes.isNotEmpty()) {
                            RiffSynth.playSequence(notes, "clean")
                        }

                        currentMeasure = measureIdx
                        val measureBeats = track.measures[measureIdx]
                        val measureStartNanos = System.nanoTime()
                        var elapsedTargetMs = 0.0

                        for ((bi, beat) in measureBeats.withIndex()) {
                            if (!isActive || !isPlaying) break
                            currentBeatInMeasure = bi
                            val dur = beatDurationMs * (4.0 / beat.duration)
                            val beatMs = if (beat.isDotted) dur * 1.5 else dur
                            elapsedTargetMs += beatMs
                            val targetNanos = measureStartNanos + (elapsedTargetMs * 1_000_000).toLong()
                            val nowNanos = System.nanoTime()
                            val waitMs = ((targetNanos - nowNanos) / 1_000_000L).coerceAtLeast(1)
                            delay(waitMs)
                        }

                        measureIdx++
                    }

                    if (!isActive || !isPlaying) break
                } while (loopEnabled)

                withContext(Dispatchers.Main) {
                    isPlaying = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(AppColors.surface)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { stopPlayback(); onBack() }) {
                Icon(Icons.Default.ArrowBack, "Volver", tint = AppColors.text)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.song,
                    color = AppColors.text,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(entry.artist, color = AppColors.textSecondary, fontSize = 12.sp, maxLines = 1)
            }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Descargando tab...", color = AppColors.textSecondary, fontSize = 14.sp)
            }
        } else if (error != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(error!!, color = AppColors.error, fontSize = 14.sp)
            }
        } else if (song != null) {
            val track = song!!.tracks[selectedTrackIndex]

            // Track selector (scrollable)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Pista:", color = AppColors.textSecondary, fontSize = 12.sp)
                song!!.tracks.forEachIndexed { idx, t ->
                    val isSelected = idx == selectedTrackIndex
                    val trackColor = when (t.type) {
                        "guitar" -> Color(0xFF4CAF50)
                        "bass" -> Color(0xFFFF9800)
                        "drums" -> Color(0xFF9C27B0)
                        "keys" -> Color(0xFF2196F3)
                        else -> AppColors.textSecondary
                    }
                    val typeLabel = when (t.type) {
                        "guitar" -> "🎸"
                        "bass" -> "🎸"
                        "drums" -> "🥁"
                        "keys" -> "🎹"
                        else -> ""
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isSelected) trackColor else trackColor.copy(alpha = 0.15f))
                            .clickable {
                                stopPlayback()
                                selectedTrackIndex = idx
                            }
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            "$typeLabel ${t.name.take(20)}",
                            color = if (isSelected) Color.White else trackColor,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            maxLines = 1
                        )
                    }
                }
            }

            // Drum style selector
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("\uD83E\uDD41", fontSize = 11.sp,
                    modifier = Modifier.align(Alignment.CenterVertically))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (selectedDrumStyle == null) AppColors.tertiary else AppColors.surface)
                        .clickable { selectedDrumStyle = null; drumJob?.cancel(); DrumEngine.stop() }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Off", fontSize = 10.sp, color = if (selectedDrumStyle == null) Color.Black else AppColors.textSecondary)
                }
                DrumStyle.entries.forEach { style ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (selectedDrumStyle == style) AppColors.tertiary else AppColors.surface)
                            .clickable { selectedDrumStyle = style }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(style.displayName, fontSize = 10.sp,
                            color = if (selectedDrumStyle == style) Color.Black else AppColors.textSecondary)
                    }
                }
            }

            // Tab viewer (takes remaining space)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                TabViewer(
                    track = track,
                    currentMeasure = currentMeasure,
                    currentBeat = currentBeatInMeasure,
                    loopStart = loopStart,
                    loopEnd = loopEnd,
                    onMeasureTap = { measure ->
                        if (loopEnabled) {
                            if (loopStart < 0 || (loopEnd >= 0)) {
                                loopStart = measure
                                loopEnd = -1
                            } else {
                                loopEnd = measure.coerceAtLeast(loopStart)
                            }
                        } else {
                            stopPlayback()
                            currentMeasure = measure
                            currentBeatInMeasure = 0
                            startPlayback(fromMeasure = measure)
                        }
                    },
                    tempo = (entry.tempo * bpmFactor).toInt()
                )
                // Countdown overlay
                if (countdownText != null) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.6f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            countdownText!!,
                            color = AppColors.tertiary,
                            fontSize = 72.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Loop range slider (when loop active)
            if (loopEnabled && track.measures.isNotEmpty()) {
                val totalMeasures = track.measures.size
                val rangeStart = if (loopStart >= 0) loopStart.toFloat() else 0f
                val rangeEnd = if (loopEnd >= 0) loopEnd.toFloat() else (totalMeasures - 1).toFloat()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(AppColors.surface)
                        .padding(horizontal = 12.dp, vertical = 2.dp)
                ) {
                    Text(
                        "Loop: compás ${loopStart + 1} → ${if (loopEnd >= 0) "${loopEnd + 1}" else "?"}",
                        color = AppColors.tertiary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                    RangeSlider(
                        value = rangeStart..rangeEnd,
                        onValueChange = { range ->
                            loopStart = range.start.toInt().coerceIn(0, totalMeasures - 1)
                            loopEnd = range.endInclusive.toInt().coerceIn(loopStart, totalMeasures - 1)
                        },
                        valueRange = 0f..(totalMeasures - 1).toFloat(),
                        steps = (totalMeasures - 2).coerceAtLeast(0),
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.tertiary,
                            activeTrackColor = AppColors.tertiary
                        ),
                        modifier = Modifier.fillMaxWidth().height(28.dp)
                    )
                }
            }

            // Controls: tempo slider (1/3) + buttons equidistant (2/3)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.surface)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // BPM: -5 button + value + slider + +5 button
                Row(
                    modifier = Modifier.weight(1.3f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // -5 button
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.surface)
                            .clickable {
                                val newBpm = ((entry.tempo * bpmFactor).toInt() - 5)
                                    .coerceIn(30, 300)
                                bpmFactor = newBpm.toFloat() / entry.tempo
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("-5", color = AppColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(2.dp))
                    // -1 button
                    Icon(
                        Icons.Default.Remove, "-1",
                        tint = AppColors.textSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                val newBpm = ((entry.tempo * bpmFactor).toInt() - 1)
                                    .coerceIn(30, 300)
                                bpmFactor = newBpm.toFloat() / entry.tempo
                            }
                    )
                    Text(
                        "${(entry.tempo * bpmFactor).toInt()}",
                        color = AppColors.text,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.width(28.dp)
                    )
                    Slider(
                        value = bpmFactor,
                        onValueChange = { bpmFactor = it },
                        valueRange = (30f / entry.tempo)..(300f / entry.tempo),
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.tertiary,
                            activeTrackColor = AppColors.tertiary
                        ),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                    // +1 button
                    Icon(
                        Icons.Default.Add, "+1",
                        tint = AppColors.textSecondary,
                        modifier = Modifier
                            .size(18.dp)
                            .clickable {
                                val newBpm = ((entry.tempo * bpmFactor).toInt() + 1)
                                    .coerceIn(30, 300)
                                bpmFactor = newBpm.toFloat() / entry.tempo
                            }
                    )
                    Spacer(Modifier.width(2.dp))
                    // +5 button
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(AppColors.surface)
                            .clickable {
                                val newBpm = ((entry.tempo * bpmFactor).toInt() + 5)
                                    .coerceIn(30, 300)
                                bpmFactor = newBpm.toFloat() / entry.tempo
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("+5", color = AppColors.textSecondary, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Control buttons
                Row(
                    modifier = Modifier.weight(1.7f),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Measure counter
                    Text(
                        "${currentMeasure + 1}/${track.measures.size}",
                        color = AppColors.textSecondary,
                        fontSize = 10.sp,
                        modifier = Modifier
                            .clickable {
                                stopPlayback()
                                currentMeasure = 0
                                currentBeatInMeasure = 0
                            }
                    )

                    // Loop
                    Icon(
                        Icons.Default.Repeat, "Loop",
                        tint = if (loopEnabled) AppColors.tertiary else AppColors.textSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                loopEnabled = !loopEnabled
                                if (loopEnabled) {
                                    if (loopStart < 0) {
                                        loopStart = 0
                                        loopEnd = track.measures.size - 1
                                    }
                                } else {
                                    loopStart = -1; loopEnd = -1
                                }
                            }
                    )

                    // Countdown toggle
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (countdownEnabled) AppColors.tertiary else Color.Transparent)
                            .clickable { countdownEnabled = !countdownEnabled },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "3…",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (countdownEnabled) Color.White else AppColors.textSecondary
                        )
                    }

                    // Play/Stop
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(if (isPlaying) AppColors.error else AppColors.tertiary)
                            .clickable {
                                if (isPlaying) stopPlayback() else startPlayback()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                            "Play",
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset
                    Icon(
                        Icons.Default.Refresh, "Reiniciar",
                        tint = AppColors.textSecondary,
                        modifier = Modifier
                            .size(24.dp)
                            .clickable {
                                stopPlayback()
                                currentMeasure = if (loopEnabled && loopStart >= 0) loopStart else 0
                                currentBeatInMeasure = 0
                            }
                    )

                    // Subdivision selector
                    val subLabel = when (subdivision) {
                        1 -> "\u2669"; 2 -> "\u266a\u266a"; 3 -> "\u266a\u266a\u266a"; 4 -> "\u266c"; else -> "$subdivision"
                    }
                    Box(
                        modifier = Modifier
                            .size(24.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (subdivision > 1) AppColors.tertiary else Color.Transparent)
                            .clickable { showSubdivisionMenu = true },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            subLabel,
                            fontSize = 12.sp,
                            color = if (subdivision > 1) Color.White else AppColors.textSecondary
                        )
                    }
                }
            }

            // Subdivision overlay
            if (showSubdivisionMenu) {
                SubdivisionSelectorOverlay(
                    current = subdivision,
                    onSelect = { subdivision = it },
                    onDismiss = { showSubdivisionMenu = false }
                )
            }


        }
    }
}

// ===================== TAB VIEWER =====================
@Composable
fun TabViewer(
    track: TabTrack,
    currentMeasure: Int,
    currentBeat: Int,
    loopStart: Int,
    loopEnd: Int,
    onMeasureTap: (Int) -> Unit,
    tempo: Int
) {
    val scrollState = rememberScrollState()
    val numStrings = track.tuning.size.coerceIn(4, 8)
    val stringNames = if (numStrings == 6) listOf("e", "B", "G", "D", "A", "E")
        else if (numStrings == 4) listOf("G", "D", "A", "E")
        else (1..numStrings).map { "S$it" }

    val lineSpacing = 34.dp
    val beatWidth = 48.dp
    val measurePadding = 24.dp
    val headerWidth = 44.dp

    var totalWidth = headerWidth.value
    for (measure in track.measures) {
        totalWidth += measure.size * beatWidth.value + measurePadding.value
    }

    LaunchedEffect(currentMeasure) {
        var offsetPx = headerWidth.value
        for (i in 0 until currentMeasure.coerceAtMost(track.measures.size - 1)) {
            offsetPx += track.measures[i].size * beatWidth.value + measurePadding.value
        }
        val density = 2.75f
        scrollState.animateScrollTo((offsetPx * density).toInt().coerceAtLeast(0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val density = 2.75f
                    val headerW = headerWidth.value * density
                    val beatW = beatWidth.value * density
                    val measPad = measurePadding.value * density
                    var xOffset = headerW
                    for (mi in track.measures.indices) {
                        val measureWidth = track.measures[mi].size * beatW + measPad
                        if (offset.x >= xOffset && offset.x < xOffset + measureWidth) {
                            onMeasureTap(mi)
                            break
                        }
                        xOffset += measureWidth
                    }
                }
            }
    ) {
        Canvas(
            modifier = Modifier
                .width(totalWidth.dp)
                .fillMaxSize()
        ) {
            val stringSpacing = lineSpacing.toPx()
            val beatW = beatWidth.toPx()
            val measPad = measurePadding.toPx()
            val headerW = headerWidth.toPx()
            val topOffset = 10f
            val noteFontSize = 42f
            val labelFontSize = 48f

            for (i in 0 until numStrings) {
                val y = topOffset + i * stringSpacing
                drawContext.canvas.nativeCanvas.drawText(
                    stringNames.getOrElse(i) { "" },
                    4f, y + 14f,
                    android.graphics.Paint().apply {
                        color = 0xFFBBBBBB.toInt()
                        textSize = labelFontSize
                        isFakeBoldText = true
                        isAntiAlias = true
                    }
                )
            }

            var xOffset = headerW
            for (mi in track.measures.indices) {
                val measure = track.measures[mi]
                val isCurrentMeasure = mi == currentMeasure
                val isInLoop = loopStart >= 0 && mi >= loopStart && (loopEnd < 0 || mi <= loopEnd)
                val measureWidth = measure.size * beatW + measPad

                if (isInLoop) {
                    drawRect(
                        color = Color(0x1500BCD4),
                        topLeft = Offset(xOffset, topOffset - 15f),
                        size = androidx.compose.ui.geometry.Size(measureWidth, numStrings * stringSpacing + 15f)
                    )
                }

                if (isCurrentMeasure) {
                    drawRect(
                        color = Color(0x20FFAB40),
                        topLeft = Offset(xOffset, topOffset - 15f),
                        size = androidx.compose.ui.geometry.Size(measureWidth, numStrings * stringSpacing + 15f)
                    )
                }

                drawContext.canvas.nativeCanvas.drawText(
                    "${mi + 1}",
                    xOffset + 2f, topOffset - 4f,
                    android.graphics.Paint().apply {
                        color = if (isCurrentMeasure) 0xFFFFAB40.toInt() else 0xFF666666.toInt()
                        textSize = 24f
                        isAntiAlias = true
                    }
                )

                for (si in 0 until numStrings) {
                    val y = topOffset + si * stringSpacing
                    drawLine(
                        color = Color(0xFF444444),
                        start = Offset(xOffset, y),
                        end = Offset(xOffset + measureWidth, y),
                        strokeWidth = 1f
                    )
                }

                for ((bi, beat) in measure.withIndex()) {
                    val bx = xOffset + bi * beatW + measPad / 2

                    if (isCurrentMeasure && bi == currentBeat) {
                        drawRect(
                            color = Color(0x30FF6D00),
                            topLeft = Offset(bx - beatW / 4, topOffset - 10f),
                            size = androidx.compose.ui.geometry.Size(beatW, numStrings * stringSpacing + 10f)
                        )
                    }

                    if (beat.isRest) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "–",
                            bx, topOffset + (numStrings / 2) * stringSpacing + 5f,
                            android.graphics.Paint().apply {
                                color = 0xFF555555.toInt()
                                textSize = 36f
                                textAlign = android.graphics.Paint.Align.CENTER
                                isAntiAlias = true
                            }
                        )
                    } else {
                        for (note in beat.notes) {
                            val si = note.string - 1
                            if (si < 0 || si >= numStrings) continue
                            val y = topOffset + si * stringSpacing

                            val noteColor = when {
                                "bend" in note.effects -> Color(0xFF4CAF50)
                                "hammer" in note.effects -> Color(0xFF00BCD4)
                                "slide" in note.effects -> Color(0xFFFFC107)
                                "palm_mute" in note.effects -> Color(0xFFFF5722)
                                "vibrato" in note.effects -> Color(0xFF9C27B0)
                                "harmonic" in note.effects -> Color(0xFFE67E00)
                                "let_ring" in note.effects -> Color(0xFF3F51B5)
                                else -> Color.White
                            }

                            drawContext.canvas.nativeCanvas.drawText(
                                "${note.fret}",
                                bx, y + 8f,
                                android.graphics.Paint().apply {
                                    color = noteColor.toArgb()
                                    textSize = noteFontSize
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                    isFakeBoldText = true
                                }
                            )

                            val effectText = when {
                                "bend" in note.effects -> "b"
                                "hammer" in note.effects -> "h"
                                "slide" in note.effects -> "/"
                                "palm_mute" in note.effects -> "."
                                "vibrato" in note.effects -> "~"
                                "harmonic" in note.effects -> "◇"
                                else -> null
                            }
                            if (effectText != null) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    effectText,
                                    bx + 16f, y - 4f,
                                    android.graphics.Paint().apply {
                                        color = noteColor.toArgb()
                                        textSize = 20f
                                        isAntiAlias = true
                                    }
                                )
                            }
                        }
                    }
                }

                drawLine(
                    color = Color(0xFF666666),
                    start = Offset(xOffset + measureWidth, topOffset - 5f),
                    end = Offset(xOffset + measureWidth, topOffset + (numStrings - 1) * stringSpacing + 5f),
                    strokeWidth = 2f
                )

                xOffset += measureWidth
            }
        }
    }
}
