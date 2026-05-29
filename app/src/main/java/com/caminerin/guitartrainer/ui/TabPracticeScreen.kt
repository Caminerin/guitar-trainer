package com.caminerin.guitartrainer.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.runtime.derivedStateOf
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

// ===================== CATALOG SCREEN =====================
@Composable
fun TabPracticeScreen(
    onBack: () -> Unit,
    showBackButton: Boolean = true
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var loading by remember { mutableStateOf(true) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var showArtistFilter by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<CatalogEntry?>(null) }

    LaunchedEffect(Unit) {
        try {
            TabRepository.loadCatalog(context)
            errorMsg = TabRepository.loadError
        } catch (e: Exception) {
            errorMsg = "Error: ${e.message}"
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

    BackHandler { onBack() }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background)
    ) {
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
            Text(
                "${TabRepository.getCatalog().size} canciones",
                color = AppColors.textSecondary,
                fontSize = 12.sp
            )
        }

        TextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Buscar canción o artista...", color = AppColors.textSecondary) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = AppColors.textSecondary) },
            trailingIcon = {
                IconButton(onClick = { showArtistFilter = true }) {
                    Icon(Icons.Default.FilterList, "Filtrar", tint = AppColors.textSecondary)
                }
                DropdownMenu(
                    expanded = showArtistFilter,
                    onDismissRequest = { showArtistFilter = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Todos los artistas") },
                        onClick = { selectedArtist = null; showArtistFilter = false }
                    )
                    TabRepository.getArtists().take(50).forEach { artist ->
                        DropdownMenuItem(
                            text = { Text(artist) },
                            onClick = { selectedArtist = artist; showArtistFilter = false }
                        )
                    }
                }
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = AppColors.surface,
                unfocusedContainerColor = AppColors.surface,
                focusedTextColor = AppColors.text,
                unfocusedTextColor = AppColors.text
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (selectedArtist != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(AppColors.tertiary)
                        .clickable { selectedArtist = null }
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text("$selectedArtist  ✕", color = Color.White, fontSize = 12.sp)
                }
            }
        }

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.tertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("Cargando catálogo...", color = AppColors.textSecondary, fontSize = 14.sp)
                    }
                }
            }
            errorMsg != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            errorMsg ?: "Error desconocido",
                            color = Color(0xFFFF5252),
                            fontSize = 14.sp,
                            modifier = Modifier.padding(16.dp)
                        )
                        Spacer(Modifier.height(12.dp))
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(AppColors.tertiary)
                                .clickable {
                                    loading = true
                                    errorMsg = null
                                    scope.launch {
                                        TabRepository.loadCatalog(context)
                                        errorMsg = TabRepository.loadError
                                        loading = false
                                    }
                                }
                                .padding(horizontal = 16.dp, vertical = 8.dp)
                        ) {
                            Text("Reintentar", color = Color.White, fontSize = 14.sp)
                        }
                    }
                }
            }
            else -> {
                val filteredEntries by remember(searchQuery, selectedArtist) {
                    derivedStateOf { TabRepository.filter(searchQuery, selectedArtist) }
                }
                val listState = rememberLazyListState()

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(filteredEntries) { entry ->
                        SongListItem(
                            entry = entry,
                            onClick = { selectedEntry = entry }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SongListItem(entry: CatalogEntry, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                entry.song,
                color = AppColors.text,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                entry.artist,
                color = AppColors.textSecondary,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Column(horizontalAlignment = Alignment.End) {
            Text("${entry.tempo} BPM", color = AppColors.textSecondary, fontSize = 11.sp)
            Text(
                "${entry.guitarTracks} gtr",
                color = Color(0xFF4CAF50),
                fontSize = 11.sp
            )
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
                if (playable.isNotEmpty()) {
                    selectedTrackIndex = song!!.tracks.indexOf(playable[0])
                }
            }
        } catch (e: Exception) {
            error = "Error: ${e.message}"
        }
        loading = false
    }

    DisposableEffect(Unit) {
        onDispose {
            playJob?.cancel()
            RiffSynth.release()
        }
    }

    BackHandler {
        playJob?.cancel()
        isPlaying = false
        onBack()
    }

    fun stopPlayback() {
        playJob?.cancel()
        isPlaying = false
    }

    fun startPlayback() {
        val currentSong = song ?: return
        if (selectedTrackIndex >= currentSong.tracks.size) return
        val track = currentSong.tracks[selectedTrackIndex]
        if (track.measures.isEmpty()) return
        isPlaying = true

        playJob = scope.launch {
            withContext(Dispatchers.Default) {
                RiffSynth.init(context)
                val baseTempo = (entry.tempo * bpmFactor).toInt().coerceIn(30, 300)
                val beatDurationMs = 60_000.0 / baseTempo

                val startMeasure = if (loopEnabled && loopStart >= 0) loopStart else currentMeasure
                val endMeasure = if (loopEnabled && loopEnd >= 0) (loopEnd + 1).coerceAtMost(track.measures.size) else track.measures.size

                var mi = startMeasure.coerceIn(0, track.measures.size - 1)
                while (isActive && isPlaying) {
                    if (mi >= endMeasure) {
                        if (loopEnabled) {
                            mi = startMeasure.coerceIn(0, track.measures.size - 1)
                        } else {
                            break
                        }
                    }

                    currentMeasure = mi
                    val measure = track.measures[mi]

                    val events = mutableListOf<RiffSynth.NoteEvent>()
                    var timeMs = 0L
                    for (beat in measure) {
                        val dur = beatDurationMs * (4.0 / beat.duration.coerceAtLeast(1))
                        val actualDur = if (beat.isDotted) (dur * 1.5).toLong() else dur.toLong()
                        if (!beat.isRest) {
                            for (note in beat.notes) {
                                val stringIdx = note.string - 1
                                if (stringIdx in 0..5 && note.fret >= 0) {
                                    events.add(RiffSynth.NoteEvent(
                                        string = stringIdx,
                                        fret = note.fret,
                                        startMs = timeMs,
                                        durationMs = actualDur.toInt().coerceAtLeast(50),
                                        technique = note.effects.firstOrNull() ?: ""
                                    ))
                                }
                            }
                        }
                        timeMs += actualDur
                    }

                    if (events.isNotEmpty()) {
                        RiffSynth.playSequence(events, "crunch")
                    }

                    for ((bi, beat) in measure.withIndex()) {
                        if (!isActive || !isPlaying) break
                        currentBeatInMeasure = bi
                        val dur = beatDurationMs * (4.0 / beat.duration.coerceAtLeast(1))
                        val waitMs = if (beat.isDotted) (dur * 1.5).toLong() else dur.toLong()
                        delay(waitMs.coerceAtLeast(30))
                    }

                    mi++
                }

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

        when {
            loading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = AppColors.tertiary)
                        Spacer(Modifier.height(8.dp))
                        Text("Descargando tab...", color = AppColors.textSecondary, fontSize = 14.sp)
                    }
                }
            }
            error != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(error!!, color = Color(0xFFFF5252), fontSize = 14.sp)
                }
            }
            song != null -> {
                val currentSong = song!!
                val safeTrackIndex = selectedTrackIndex.coerceIn(0, (currentSong.tracks.size - 1).coerceAtLeast(0))
                if (currentSong.tracks.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Esta canción no tiene pistas", color = AppColors.textSecondary, fontSize = 14.sp)
                    }
                } else {
                    val track = currentSong.tracks[safeTrackIndex]

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Pista:", color = AppColors.textSecondary, fontSize = 12.sp)
                        currentSong.tracks.forEachIndexed { idx, t ->
                            val isSelected = idx == safeTrackIndex
                            val trackColor = when (t.type) {
                                "guitar" -> Color(0xFF4CAF50)
                                "bass" -> Color(0xFFFF9800)
                                "drums" -> Color(0xFF9C27B0)
                                "keys" -> Color(0xFF2196F3)
                                else -> AppColors.textSecondary
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
                                    t.name.take(15),
                                    color = if (isSelected) Color.White else trackColor,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                    ) {
                        if (track.measures.isNotEmpty()) {
                            TabViewer(
                                track = track,
                                currentMeasure = currentMeasure,
                                currentBeat = currentBeatInMeasure,
                                loopStart = loopStart,
                                loopEnd = loopEnd,
                                onMeasureTap = { measure ->
                                    if (loopEnabled) {
                                        if (loopStart < 0 || loopEnd >= 0) {
                                            loopStart = measure
                                            loopEnd = -1
                                        } else {
                                            loopEnd = measure.coerceAtLeast(loopStart)
                                        }
                                    }
                                },
                                tempo = (entry.tempo * bpmFactor).toInt()
                            )
                        } else {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text("Sin compases", color = AppColors.textSecondary)
                            }
                        }
                    }

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(AppColors.surface)
                            .padding(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${(entry.tempo * bpmFactor).toInt()} BPM",
                                color = AppColors.text,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(75.dp)
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
                            listOf("50%" to 0.5f, "75%" to 0.75f, "100%" to 1f).forEach { (label, factor) ->
                                Box(
                                    modifier = Modifier
                                        .padding(horizontal = 2.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(
                                            if (bpmFactor == factor) AppColors.tertiary
                                            else Color.White.copy(alpha = 0.08f)
                                        )
                                        .clickable { bpmFactor = factor }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(label, fontSize = 11.sp, color = AppColors.text)
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Compás ${currentMeasure + 1}/${track.measures.size}",
                                color = AppColors.textSecondary,
                                fontSize = 12.sp
                            )

                            IconButton(onClick = {
                                loopEnabled = !loopEnabled
                                if (!loopEnabled) { loopStart = -1; loopEnd = -1 }
                            }) {
                                Icon(
                                    Icons.Default.Repeat, "Loop",
                                    tint = if (loopEnabled) AppColors.tertiary else AppColors.textSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            if (loopEnabled && loopStart >= 0) {
                                Text(
                                    "Loop: ${loopStart + 1}-${if (loopEnd >= 0) "${loopEnd + 1}" else "?"}",
                                    color = AppColors.tertiary,
                                    fontSize = 11.sp
                                )
                            }

                            IconButton(
                                onClick = {
                                    if (isPlaying) stopPlayback() else startPlayback()
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isPlaying) Color(0xFFFF5252) else AppColors.tertiary)
                            ) {
                                Icon(
                                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                                    "Play",
                                    tint = Color.White,
                                    modifier = Modifier.size(28.dp)
                                )
                            }

                            IconButton(onClick = {
                                stopPlayback()
                                currentMeasure = if (loopEnabled && loopStart >= 0) loopStart else 0
                                currentBeatInMeasure = 0
                            }) {
                                Icon(Icons.Default.Refresh, "Reiniciar", tint = AppColors.textSecondary, modifier = Modifier.size(24.dp))
                            }
                        }
                    }
                }
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
    val stringNames = when (numStrings) {
        6 -> listOf("e", "B", "G", "D", "A", "E")
        4 -> listOf("G", "D", "A", "E")
        7 -> listOf("e", "B", "G", "D", "A", "E", "B")
        else -> (1..numStrings).map { "S$it" }
    }

    val lineSpacing = 28.dp
    val beatWidth = 40.dp
    val measurePadding = 20.dp
    val headerWidth = 24.dp

    var totalWidth = headerWidth.value
    for (measure in track.measures) {
        totalWidth += measure.size * beatWidth.value + measurePadding.value
    }
    totalWidth = totalWidth.coerceAtLeast(200f)

    LaunchedEffect(currentMeasure) {
        if (track.measures.isEmpty()) return@LaunchedEffect
        var offsetPx = headerWidth.value
        val safeIdx = currentMeasure.coerceIn(0, track.measures.size - 1)
        for (i in 0 until safeIdx) {
            offsetPx += track.measures[i].size * beatWidth.value + measurePadding.value
        }
        val density = 2.75f
        scrollState.animateScrollTo((offsetPx * density).toInt().coerceAtLeast(0))
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .horizontalScroll(scrollState)
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

            val labelPaint = android.graphics.Paint().apply {
                color = 0xFF888888.toInt()
                textSize = 28f
                isAntiAlias = true
            }

            for (i in 0 until numStrings) {
                val y = topOffset + i * stringSpacing
                drawContext.canvas.nativeCanvas.drawText(
                    stringNames.getOrElse(i) { "" },
                    4f, y + 5f,
                    labelPaint
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
                        textSize = 22f
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

                    if (beat.isRest || beat.notes.isEmpty()) {
                        drawContext.canvas.nativeCanvas.drawText(
                            "–",
                            bx, topOffset + (numStrings / 2) * stringSpacing + 5f,
                            android.graphics.Paint().apply {
                                color = 0xFF555555.toInt()
                                textSize = 32f
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
                                note.effects.any { "bend" in it } -> Color(0xFF4CAF50)
                                note.effects.any { "hammer" in it } -> Color(0xFF00BCD4)
                                note.effects.any { "slide" in it } -> Color(0xFFFFC107)
                                note.effects.any { "palm" in it } -> Color(0xFFFF5722)
                                note.effects.any { "vibrato" in it } -> Color(0xFF9C27B0)
                                note.effects.any { "harmonic" in it } -> Color(0xFFE91E63)
                                note.effects.any { "ring" in it } -> Color(0xFF3F51B5)
                                else -> Color.White
                            }

                            drawContext.canvas.nativeCanvas.drawText(
                                "${note.fret}",
                                bx, y + 8f,
                                android.graphics.Paint().apply {
                                    color = noteColor.toArgb()
                                    textSize = 30f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                    isFakeBoldText = true
                                }
                            )

                            val effectText = when {
                                note.effects.any { "bend" in it } -> "b"
                                note.effects.any { "hammer" in it } -> "h"
                                note.effects.any { "slide" in it } -> "/"
                                note.effects.any { "palm" in it } -> "."
                                note.effects.any { "vibrato" in it } -> "~"
                                note.effects.any { "harmonic" in it } -> "◇"
                                else -> null
                            }
                            if (effectText != null) {
                                drawContext.canvas.nativeCanvas.drawText(
                                    effectText,
                                    bx + 14f, y - 4f,
                                    android.graphics.Paint().apply {
                                        color = noteColor.toArgb()
                                        textSize = 18f
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
