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
import androidx.compose.foundation.verticalScroll
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
    var selectedArtist by remember { mutableStateOf<String?>(null) }
    var showFilterOverlay by remember { mutableStateOf(false) }
    var selectedEntry by remember { mutableStateOf<CatalogEntry?>(null) }
    var bpmRange by remember { mutableStateOf(30f..300f) }
    var sortKeys by remember { mutableStateOf(listOf<SortKey>()) }
    var selectedInstruments by remember { mutableStateOf(setOf<String>()) }
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

    if (showFilterOverlay) {
        FilterOverlay(
            artists = TabRepository.getArtists(),
            selectedArtist = selectedArtist,
            bpmRange = bpmRange,
            selectedInstruments = selectedInstruments,
            onArtistSelected = { selectedArtist = it },
            onBpmRangeChanged = { bpmRange = it },
            onInstrumentsChanged = { selectedInstruments = it },
            onClear = { selectedArtist = null; bpmRange = 30f..300f; selectedInstruments = emptySet() },
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
                val filtersActive = selectedArtist != null || bpmActive || selectedInstruments.isNotEmpty()
                Text(
                    "${TabRepository.filter("", selectedArtist,
                        if (bpmActive) bpmRange.start.toInt() else null,
                        if (bpmActive) bpmRange.endInclusive.toInt() else null
                    ).size} tabs",
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

        // Import + filter buttons
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.End
        ) {
            Icon(
                Icons.Default.Add, "Importar tab",
                tint = AppColors.success,
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(26.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) {
                        filePickerLauncher.launch(arrayOf(
                            "*/*"
                        ))
                    }
            )
            Icon(
                Icons.Default.FilterList, "Filtros",
                tint = AppColors.tertiary,
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(24.dp)
                    .clickable(
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ) { showFilterOverlay = true }
            )
        }

        // Active filter chips
        val bpmActive = bpmRange.start > 30f || bpmRange.endInclusive < 300f
        if (selectedArtist != null || bpmActive || selectedInstruments.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .horizontalScroll(rememberScrollState()),
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
                if (selectedInstruments.isNotEmpty()) {
                    FilterChip(
                        text = selectedInstruments.joinToString(", "),
                        onRemove = { selectedInstruments = emptySet() }
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
                "", selectedArtist,
                if (bpmActive) bpmRange.start.toInt() else null,
                if (bpmActive) bpmRange.endInclusive.toInt() else null
            ).filter { entry ->
                if (selectedInstruments.isEmpty()) true
                else selectedInstruments.all { inst ->
                    when (inst) {
                        "Guitarra" -> entry.guitarTracks > 0
                        "Bajo" -> entry.bassTracks > 0
                        "Teclado/Piano" -> entry.otherTracks > 0
                        else -> true
                    }
                }
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
                SortableHeader("Nombre", "song", sortKeys, Modifier.weight(1.2f)) { toggleSort("song") }
                SortableHeader("Categoría", "artist", sortKeys, Modifier.weight(0.8f)) { toggleSort("artist") }
                SortableHeader("Instrumentos", "tracks", sortKeys, Modifier.weight(0.8f)) { toggleSort("tracks") }
                SortableHeader("BPM", "bpm", sortKeys, Modifier.width(40.dp)) { toggleSort("bpm") }
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
                            modifier = Modifier.weight(1.2f)
                        )
                        Text(
                            entry.artist,
                            color = if (entry.isUserTab) AppColors.success else AppColors.textSecondary,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.8f)
                        )
                        Text(
                            instrumentsLabel(entry),
                            color = AppColors.textSecondary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(0.8f)
                        )
                        Text(
                            "${entry.tempo}",
                            color = AppColors.textSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.width(40.dp),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        if (entry.isUserTab) {
                            Icon(
                                Icons.Default.Delete, "Eliminar",
                                tint = AppColors.error.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .clickable {
                                        val tabId = entry.path.removePrefix("user://")
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
            }
        }
    }
}

private fun instrumentsLabel(entry: CatalogEntry): String {
    val parts = mutableListOf<String>()
    if (entry.guitarTracks > 0) parts.add("Guitarra")
    if (entry.bassTracks > 0) parts.add("Bajo")
    if (entry.otherTracks > 0) parts.add("Teclado/Piano")
    return if (parts.isEmpty()) "-" else parts.joinToString(", ")
}

private val ALL_INSTRUMENTS = listOf("Guitarra", "Bajo", "Teclado/Piano")

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
    selectedInstruments: Set<String>,
    onArtistSelected: (String?) -> Unit,
    onBpmRangeChanged: (ClosedFloatingPointRange<Float>) -> Unit,
    onInstrumentsChanged: (Set<String>) -> Unit,
    onClear: () -> Unit,
    onDismiss: () -> Unit
) {
    // 0 = main menu, 1 = BPM, 2 = Categoría, 3 = Instrumentos
    var filterPage by remember { mutableIntStateOf(0) }
    var localBpmRange by remember { mutableStateOf(bpmRange) }
    var localInstruments by remember { mutableStateOf(selectedInstruments) }

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
            IconButton(onClick = {
                if (filterPage == 0) onDismiss()
                else filterPage = 0
            }) {
                Icon(
                    if (filterPage == 0) Icons.Default.Close else Icons.Default.ArrowBack,
                    "Volver",
                    tint = AppColors.text
                )
            }
            Text(
                when (filterPage) {
                    1 -> "BPM"
                    2 -> "Categoría"
                    3 -> "Instrumentos"
                    else -> "Filtros"
                },
                color = AppColors.text, fontSize = 20.sp, fontWeight = FontWeight.Bold
            )
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
        }

        when (filterPage) {
            0 -> {
                // Main menu with 3 filter type buttons
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    FilterMenuButton(
                        title = "BPM",
                        subtitle = if (bpmRange.start > 30f || bpmRange.endInclusive < 300f)
                            "${bpmRange.start.toInt()} – ${bpmRange.endInclusive.toInt()}" else "Todos",
                        onClick = { filterPage = 1 }
                    )
                    FilterMenuButton(
                        title = "Categoría",
                        subtitle = selectedArtist ?: "Todas",
                        onClick = { filterPage = 2 }
                    )
                    FilterMenuButton(
                        title = "Instrumentos",
                        subtitle = if (selectedInstruments.isEmpty()) "Todos"
                            else selectedInstruments.joinToString(", "),
                        onClick = { filterPage = 3 }
                    )
                }
            }
            1 -> {
                // BPM filter page
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${localBpmRange.start.toInt()} – ${localBpmRange.endInclusive.toInt()} BPM",
                        color = AppColors.tertiary,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Spacer(Modifier.height(8.dp))
                    RangeSlider(
                        value = localBpmRange,
                        onValueChange = { localBpmRange = it },
                        valueRange = 30f..300f,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(24.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.tertiary)
                            .clickable { onBpmRangeChanged(localBpmRange); filterPage = 0 }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aplicar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
            2 -> {
                // Category filter page
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    artists.forEach { category ->
                        val isSelected = category == selectedArtist
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AppColors.tertiary.copy(alpha = 0.2f) else Color.Transparent)
                                .clickable {
                                    onArtistSelected(if (isSelected) null else category)
                                    filterPage = 0
                                }
                                .padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                category,
                                color = if (isSelected) AppColors.tertiary else AppColors.text,
                                fontSize = 15.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            if (isSelected) {
                                Spacer(Modifier.weight(1f))
                                Text("✕", color = AppColors.tertiary, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
            3 -> {
                // Instruments filter page (multi-select)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ALL_INSTRUMENTS.forEach { instrument ->
                        val isSelected = instrument in localInstruments
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) AppColors.tertiary.copy(alpha = 0.2f) else AppColors.surface)
                                .clickable {
                                    localInstruments = if (isSelected) localInstruments - instrument
                                        else localInstruments + instrument
                                }
                                .padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                instrument,
                                color = if (isSelected) AppColors.tertiary else AppColors.text,
                                fontSize = 16.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Spacer(Modifier.weight(1f))
                            if (isSelected) {
                                Text("✓", color = AppColors.tertiary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(AppColors.tertiary)
                            .clickable { onInstrumentsChanged(localInstruments); filterPage = 0 }
                            .padding(vertical = 14.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("Aplicar", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun FilterMenuButton(title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = AppColors.text, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = AppColors.textSecondary, fontSize = 13.sp)
        }
        Text("›", color = AppColors.textSecondary, fontSize = 22.sp)
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
                    val baseTempo = (entry.tempo * bpmFactor).toInt().coerceIn(30, 300)
                    val playStart = if (fromMeasure != null && !loopEnabled) fromMeasure
                        else if (loopEnabled && loopStart >= 0) loopStart
                        else currentMeasure
                    val playEnd = if (loopEnabled && loopEnd >= 0) (loopEnd + 1).coerceAtMost(track.measures.size)
                        else track.measures.size

                    if (playStart >= playEnd) break

                    val chunkSize = 8
                    val beatDurationMs = 60_000.0 / baseTempo
                    var chunkStart = playStart

                    while (chunkStart < playEnd && isActive && isPlaying) {
                        val chunkEnd = (chunkStart + chunkSize).coerceAtMost(playEnd)
                        val (notes, _, _) = buildContinuousSequence(
                            track, chunkStart, chunkEnd, baseTempo
                        )

                        if (notes.isNotEmpty()) {
                            RiffSynth.playSequence(notes, "crunch")
                        }

                        for (mi in chunkStart until chunkEnd) {
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

                        chunkStart = chunkEnd
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
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Remove, "-5",
                        tint = AppColors.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                val newBpm = ((entry.tempo * bpmFactor).toInt() - 5)
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
                        valueRange = 0.25f..1.5f,
                        colors = SliderDefaults.colors(
                            thumbColor = AppColors.tertiary,
                            activeTrackColor = AppColors.tertiary
                        ),
                        modifier = Modifier.weight(1f).height(24.dp)
                    )
                    Icon(
                        Icons.Default.Add, "+5",
                        tint = AppColors.textSecondary,
                        modifier = Modifier
                            .size(20.dp)
                            .clickable {
                                val newBpm = ((entry.tempo * bpmFactor).toInt() + 5)
                                    .coerceIn(30, 300)
                                bpmFactor = newBpm.toFloat() / entry.tempo
                            }
                    )
                }

                // Control buttons — 2/3 of width, equidistant
                Row(
                    modifier = Modifier.weight(2f),
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
