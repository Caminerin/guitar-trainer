package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import com.caminerin.guitartrainer.audio.RiffSynth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Sort state: column name -> SortDirection
enum class SortDirection { NONE, ASC, DESC }
data class SortKey(val column: String, val direction: SortDirection)

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
    var searchQuery by remember { mutableStateOf("") }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var showFilterOverlay by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<CatalogEntry?>(null) }
    var bpmRange by remember { mutableStateOf(30f..300f) }
    var sortKeys by remember { mutableStateOf(listOf<SortKey>()) }
    var minGuitars by remember { mutableIntStateOf(0) }
    var minBass by remember { mutableIntStateOf(0) }

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

    if (showFilterOverlay) {
        FilterOverlay(
            artists = TabRepository.getArtists(),
            selectedArtist = selectedArtist,
            bpmRange = bpmRange,
            minGuitars = minGuitars,
            minBass = minBass,
            onArtistSelected = { selectedArtist = it },
            onBpmRangeChanged = { bpmRange = it },
            onMinGuitarsChanged = { minGuitars = it },
            onMinBassChanged = { minBass = it },
            onClear = { selectedArtist = null; bpmRange = 30f..300f; minGuitars = 0; minBass = 0 },
            onDismiss = { showFilterOverlay = false }
        )
        return
    }

    fun toggleSort(column: String) {
        val existing = sortKeys.find { it.column == column }
        sortKeys = if (existing == null) {
            sortKeys + SortKey(column, SortDirection.ASC)
        } else if (existing.direction == SortDirection.ASC) {
            sortKeys.map { if (it.column == column) it.copy(direction = SortDirection.DESC) else it }
        } else {
            sortKeys.filter { it.column != column }
        }
    }

    fun applySorting(entries: List<CatalogEntry>): List<CatalogEntry> {
        if (sortKeys.isEmpty()) return entries
        return entries.sortedWith(Comparator { a, b ->
            for (key in sortKeys) {
                val cmp = when (key.column) {
                    "song" -> a.song.compareTo(b.song, ignoreCase = true)
                    "artist" -> a.artist.compareTo(b.artist, ignoreCase = true)
                    "gtr" -> a.guitarTracks.compareTo(b.guitarTracks)
                    "bass" -> a.bassTracks.compareTo(b.bassTracks)
                    "bpm" -> a.tempo.compareTo(b.tempo)
                    "tracks" -> a.tracks.compareTo(b.tracks)
                    else -> 0
                }
                if (cmp != 0) {
                    return@Comparator if (key.direction == SortDirection.DESC) -cmp else cmp
                }
            }
            0
        })
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
            if (showBackButton) {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, "Volver", tint = AppColors.text)
                }
            }
            Text(
                "Tabs",
                color = AppColors.text,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = if (!showBackButton) 12.dp else 0.dp)
            )
            Spacer(Modifier.weight(1f))
            if (!loading) {
                val bpmActive = bpmRange.start > 30f || bpmRange.endInclusive < 300f
                val filtersActive = selectedArtist != null || bpmActive
                Text(
                    "${TabRepository.filter(searchQuery, selectedArtist,
                        if (bpmActive) bpmRange.start.toInt() else null,
                        if (bpmActive) bpmRange.endInclusive.toInt() else null
                    ).size} canciones",
                    color = AppColors.textSecondary,
                    fontSize = 12.sp
                )
                if (filtersActive) {
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(AppColors.tertiary)
                    )
                }
            }
        }

        // Search bar + filter button
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar canción o artista...", color = AppColors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.textSecondary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppColors.surface,
                    unfocusedContainerColor = AppColors.surface,
                    focusedTextColor = AppColors.text,
                    unfocusedTextColor = AppColors.text
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Icon(
                Icons.Default.FilterList, "Filtros",
                tint = AppColors.tertiary,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .size(24.dp)
                    .clickable { showFilterOverlay = true }
            )
        }

        // Active filter chips
        val bpmActive = bpmRange.start > 30f || bpmRange.endInclusive < 300f
        if (selectedArtist != null || bpmActive) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (selectedArtist != null) {
                    FilterChip(
                        text = selectedArtist!!,
                        onRemove = { selectedArtist = null }
                    )
                }
                if (bpmActive) {
                    FilterChip(
                        text = "${bpmRange.start.toInt()}-${bpmRange.endInclusive.toInt()} BPM",
                        onRemove = { bpmRange = 30f..300f }
                    )
                }
            }
        }

        if (loading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text("Cargando catálogo...", color = AppColors.textSecondary, fontSize = 16.sp)
            }
        } else if (loadError != null) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("Error cargando catálogo", color = AppColors.error, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(loadError!!, color = AppColors.textSecondary, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(AppColors.tertiary)
                            .clickable {
                                loading = true; loadError = null
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
        } else {
            val filteredEntries = TabRepository.filter(
                searchQuery, selectedArtist,
                if (bpmActive) bpmRange.start.toInt() else null,
                if (bpmActive) bpmRange.endInclusive.toInt() else null
            ).filter { entry ->
                (minGuitars == 0 || entry.guitarTracks >= minGuitars) &&
                (minBass == 0 || entry.bassTracks >= minBass)
            }
            val sortedEntries = applySorting(filteredEntries)
            val listState = rememberLazyListState()

            // Column headers
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.surface.copy(alpha = 0.7f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SortableHeader("Canción", "song", sortKeys, Modifier.weight(1.4f)) { toggleSort("song") }
                SortableHeader("Artista", "artist", sortKeys, Modifier.weight(1f)) { toggleSort("artist") }
                SortableHeader("Gtr", "gtr", sortKeys, Modifier.width(36.dp)) { toggleSort("gtr") }
                SortableHeader("Bajo", "bass", sortKeys, Modifier.width(36.dp)) { toggleSort("bass") }
                SortableHeader("BPM", "bpm", sortKeys, Modifier.width(44.dp)) { toggleSort("bpm") }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize()
            ) {
                items(sortedEntries) { entry ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { selectedEntry = entry }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            entry.song,
                            color = AppColors.text,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1.4f)
                        )
                        Text(
                            entry.artist,
                            color = AppColors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            "${entry.guitarTracks}",
                            color = Color(0xFF4CAF50),
                            fontSize = 13.sp,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            "${entry.bassTracks}",
                            color = Color(0xFFFF9800),
                            fontSize = 13.sp,
                            modifier = Modifier.width(36.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Text(
                            "${entry.tempo}",
                            color = AppColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.width(44.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SortableHeader(
    label: String,
    column: String,
    sortKeys: List<SortKey>,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val sortKey = sortKeys.find { it.column == column }
    val sortIndex = sortKeys.indexOfFirst { it.column == column }
    val arrow = when (sortKey?.direction) {
        SortDirection.ASC -> " ▲"
        SortDirection.DESC -> " ▼"
        else -> ""
    }
    val indexLabel = if (sortIndex >= 0 && sortKeys.size > 1) "${sortIndex + 1}" else ""

    Box(
        modifier = modifier
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            "$label$arrow$indexLabel",
            color = if (sortKey != null) AppColors.tertiary else AppColors.textSecondary,
            fontSize = 11.sp,
            fontWeight = if (sortKey != null) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

@Composable
private fun FilterChip(text: String, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(AppColors.tertiary)
            .clickable(onClick = onRemove)
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text("$text  ✕", color = Color.White, fontSize = 12.sp)
    }
}

// ===================== FILTER OVERLAY =====================
@Composable
private fun FilterOverlay(
    artists: List<String>,
    selectedArtist: String?,
    bpmRange: ClosedFloatingPointRange<Float>,
    minGuitars: Int,
    minBass: Int,
    onArtistSelected: (String?) -> Unit,
    onBpmRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onMinGuitarsChanged: (Int) -> Unit,
    onMinBassChanged: (Int) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    var artistSearch by remember { mutableStateOf("") }
    var localBpmRange by remember { mutableStateOf(bpmRange) }
    var localMinGuitars by remember { mutableIntStateOf(minGuitars) }
    var localMinBass by remember { mutableIntStateOf(minBass) }

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
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, "Cerrar", tint = AppColors.text)
            }
            Text("Filtros", color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.error.copy(alpha = 0.2f))
                    .clickable { onClear(); onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Limpiar", color = AppColors.error, fontSize = 12.sp)
            }
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(AppColors.tertiary)
                    .clickable { onBpmRangeChanged(localBpmRange); onMinGuitarsChanged(localMinGuitars); onMinBassChanged(localMinBass); onDismiss() }
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            ) {
                Text("Aplicar", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            // BPM range
            Text("Rango de BPM", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Text(
                "${localBpmRange.start.toInt()} – ${localBpmRange.endInclusive.toInt()} BPM",
                color = AppColors.tertiary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            RangeSlider(
                value = localBpmRange,
                onValueChange = { localBpmRange = it },
                valueRange = 30f..300f,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(20.dp))

            // Min guitars
            Text("Mínimo guitarras", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 1, 2, 3, 4).forEach { n ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (localMinGuitars == n) Color(0xFF4CAF50) else AppColors.surface)
                            .clickable { localMinGuitars = n }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (n == 0) "Todas" else "$n+",
                            color = if (localMinGuitars == n) Color.White else AppColors.text,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(16.dp))

            // Min bass
            Text("Mínimo bajos", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(4.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                listOf(0, 1, 2).forEach { n ->
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (localMinBass == n) Color(0xFFFF9800) else AppColors.surface)
                            .clickable { localMinBass = n }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            if (n == 0) "Todas" else "$n+",
                            color = if (localMinBass == n) Color.White else AppColors.text,
                            fontSize = 13.sp
                        )
                    }
                }
            }
            Spacer(Modifier.height(20.dp))

            // Artist filter
            Text("Artista", color = AppColors.text, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            TextField(
                value = artistSearch,
                onValueChange = { artistSearch = it },
                placeholder = { Text("Buscar artista...", color = AppColors.textSecondary) },
                leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.textSecondary) },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = AppColors.surface,
                    unfocusedContainerColor = AppColors.surface,
                    focusedTextColor = AppColors.text,
                    unfocusedTextColor = AppColors.text
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))

            if (selectedArtist != null) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(AppColors.tertiary.copy(alpha = 0.2f))
                        .clickable { onArtistSelected(null) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(selectedArtist, color = AppColors.tertiary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    Text("✕", color = AppColors.tertiary, fontSize = 14.sp)
                }
                Spacer(Modifier.height(4.dp))
            }

            val filteredArtists = if (artistSearch.isBlank()) artists
                else artists.filter { it.contains(artistSearch, ignoreCase = true) }

            filteredArtists.take(100).forEach { artist ->
                val isSelected = artist == selectedArtist
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onArtistSelected(if (isSelected) null else artist) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        artist,
                        color = if (isSelected) AppColors.tertiary else AppColors.text,
                        fontSize = 13.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }
            if (filteredArtists.size > 100) {
                Text(
                    "... y ${filteredArtists.size - 100} más (usa el buscador)",
                    color = AppColors.textMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(12.dp)
                )
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
    var playJob by remember { mutableStateOf<Job?>(null) }

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
            RiffSynth.release()
        }
    }

    fun stopPlayback() {
        playJob?.cancel()
        isPlaying = false
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
            withContext(Dispatchers.Default) {
                RiffSynth.init(context)

                do {
                    val baseTempo = (entry.tempo * bpmFactor).toInt().coerceIn(30, 300)
                    val startMeasure = if (fromMeasure != null && !loopEnabled) fromMeasure
                        else if (loopEnabled && loopStart >= 0) loopStart
                        else currentMeasure
                    val endMeasure = if (loopEnabled && loopEnd >= 0) (loopEnd + 1).coerceAtMost(track.measures.size)
                        else track.measures.size

                    if (startMeasure >= endMeasure) break

                    val (notes, measureOffsets, totalDurationMs) = buildContinuousSequence(
                        track, startMeasure, endMeasure, baseTempo
                    )

                    if (notes.isNotEmpty()) {
                        RiffSynth.playSequence(notes, "crunch")
                    }

                    val playStartTime = System.currentTimeMillis()
                    val beatDurationMs = 60_000.0 / baseTempo

                    for (mi in startMeasure until endMeasure) {
                        if (!isActive || !isPlaying) break
                        currentMeasure = mi

                        val measureBeats = track.measures[mi]
                        for ((bi, beat) in measureBeats.withIndex()) {
                            if (!isActive || !isPlaying) break
                            currentBeatInMeasure = bi
                            val dur = beatDurationMs * (4.0 / beat.duration)
                            val waitMs = if (beat.isDotted) (dur * 1.5).toLong() else dur.toLong()
                            delay(waitMs.coerceAtLeast(20))
                        }
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
            }

            // Single-line controls: BPM slider left, 4 buttons right
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(AppColors.surface)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // BPM label + slider (left half)
                Text(
                    "${(entry.tempo * bpmFactor).toInt()}",
                    color = AppColors.text,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.width(30.dp)
                )
                Slider(
                    value = bpmFactor,
                    onValueChange = { bpmFactor = it },
                    valueRange = 0.25f..1.5f,
                    colors = SliderDefaults.colors(
                        thumbColor = AppColors.tertiary,
                        activeTrackColor = AppColors.tertiary
                    ),
                    modifier = Modifier.weight(1f)
                )

                // 4 control buttons (right, equidistant)
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.width(200.dp)
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
                                if (!loopEnabled) { loopStart = -1; loopEnd = -1 }
                            }
                    )

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
                }
            }

            // Loop info (only when active, very compact)
            if (loopEnabled && loopStart >= 0) {
                Text(
                    "Loop: ${loopStart + 1}-${if (loopEnd >= 0) "${loopEnd + 1}" else "?"}",
                    color = AppColors.tertiary,
                    fontSize = 9.sp,
                    modifier = Modifier.padding(start = 8.dp)
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

    val lineSpacing = 32.dp
    val beatWidth = 48.dp
    val measurePadding = 24.dp
    val headerWidth = 28.dp

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
            val topOffset = 30f
            val noteFontSize = 36f
            val labelFontSize = 30f

            for (i in 0 until numStrings) {
                val y = topOffset + i * stringSpacing
                drawContext.canvas.nativeCanvas.drawText(
                    stringNames.getOrElse(i) { "" },
                    4f, y + 5f,
                    android.graphics.Paint().apply {
                        color = 0xFF888888.toInt()
                        textSize = labelFontSize
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
                                "harmonic" in note.effects -> Color(0xFFE91E63)
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
