package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LEVEL_COLORS = listOf(
    Color(0xFF4CAF50),
    Color(0xFF8BC34A),
    Color(0xFFFFC107),
    Color(0xFFFF9800),
    Color(0xFFF44336),
)

private data class SongColumn(
    val header: String,
    val width: Dp,
    val extract: (Song) -> String,
    val sortKey: String
)

private val SONG_COLUMNS = listOf(
    SongColumn("Canción", 180.dp, { it.title }, "title"),
    SongColumn("Artista", 140.dp, { it.artist }, "artist"),
    SongColumn("Dif.", 50.dp, { "${it.level}" }, "level"),
    SongColumn("Compás", 60.dp, { it.meter.ifBlank { "4/4" } }, "meter"),
    SongColumn("Nº Acord.", 65.dp, { "${it.chordsUsed.size}" }, "chords_count"),
    SongColumn("Tonalidad", 80.dp, { it.key }, "key"),
    SongColumn("BPM Ini.", 65.dp, { "${it.bpmStart}" }, "bpm_start"),
    SongColumn("BPM Obj.", 65.dp, { "${it.bpmTarget}" }, "bpm_target"),
    SongColumn("Capo", 50.dp, { if (it.capo > 0) "Sí (${it.capo})" else "No" }, "capo"),
    SongColumn("Acordes", 200.dp, { it.chordsUsed.joinToString(", ") }, "chords"),
    SongColumn("Foco práctica", 200.dp, { it.practiceFocus }, "focus")
)

private val TABLE_TOTAL_WIDTH = SONG_COLUMNS.sumOf { it.width.value.toInt() }.dp

private enum class SortDir { ASC, DESC }

private data class SortEntry(val columnKey: String, val dir: SortDir)

private enum class SearchField(val label: String) {
    ALL("Todo"), TITLE("Canci\u00f3n"), ARTIST("Artista")
}

@Composable
fun SongPickerOverlay(
    songs: List<Song>,
    onPick: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var searchField by remember { mutableStateOf(SearchField.ALL) }
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    var selectedCapo by remember { mutableStateOf<Boolean?>(null) }
    var sortStack by remember { mutableStateOf(listOf<SortEntry>()) }
    var showDifficultyFilter by remember { mutableStateOf(false) }
    var showKeyFilter by remember { mutableStateOf(false) }
    var showCapoFilter by remember { mutableStateOf(false) }
    var showMeterFilter by remember { mutableStateOf(false) }
    var showSearchFieldFilter by remember { mutableStateOf(false) }
    var selectedMeter by remember { mutableStateOf<String?>(null) }

    val allKeys = remember(songs) { songs.map { it.key }.distinct().sorted() }
    val allMeters = remember(songs) { songs.map { it.meter.ifBlank { "4/4" } }.distinct().sorted() }

    val filtered = remember(searchQuery, searchField, selectedLevel, selectedKey, selectedCapo, selectedMeter) {
        songs.filter { song ->
            (selectedLevel == null || song.level == selectedLevel) &&
            (selectedKey == null || song.key == selectedKey) &&
            (selectedCapo == null || (selectedCapo == true && song.capo > 0) || (selectedCapo == false && song.capo == 0)) &&
            (selectedMeter == null || song.meter.ifBlank { "4/4" } == selectedMeter) &&
            (searchQuery.isBlank() || when (searchField) {
                SearchField.TITLE -> song.title.contains(searchQuery, ignoreCase = true)
                SearchField.ARTIST -> song.artist.contains(searchQuery, ignoreCase = true)
                SearchField.ALL -> song.title.contains(searchQuery, ignoreCase = true) ||
                    song.artist.contains(searchQuery, ignoreCase = true)
            })
        }
    }

    val sorted = remember(filtered, sortStack) {
        if (sortStack.isEmpty()) {
            filtered.sortedBy { it.level }
        } else {
            filtered.sortedWith(
                sortStack.reversed().fold(compareBy<Song> { 0 }) { comparator, entry ->
                    val cmp: Comparator<Song> = when (entry.columnKey) {
                        "title" -> compareBy { it.title.lowercase() }
                        "artist" -> compareBy { it.artist.lowercase() }
                        "level" -> compareBy { it.level }
                        "chords_count" -> compareBy { it.chordsUsed.size }
                        "key" -> compareBy { it.key }
                        "bpm_start" -> compareBy { it.bpmStart }
                        "bpm_target" -> compareBy { it.bpmTarget }
                        "capo" -> compareBy { it.capo }
                        "chords" -> compareBy { it.chordsUsed.joinToString() }
                        "focus" -> compareBy { it.practiceFocus }
                        else -> compareBy { 0 }
                    }
                    val directed = if (entry.dir == SortDir.DESC) cmp.reversed() else cmp
                    comparator.then(directed)
                }
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.97f)
                .fillMaxHeight(0.94f)
                .clip(RoundedCornerShape(16.dp))
                .background(Color(0xFF1A1A1A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = true
                ) {}
                .padding(12.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color(0xFFFFC107),
                    modifier = Modifier.size(24.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Canciones", color = Color.White, fontSize = 20.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.width(8.dp))
                Text("${sorted.size}", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Filter buttons row (above search bar)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Search field button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF7B1FA2).copy(alpha = 0.3f))
                        .clickable { showSearchFieldFilter = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text("Buscar: ${searchField.label}", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Difficulty button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedLevel != null) LEVEL_COLORS.getOrElse((selectedLevel ?: 1) - 1) { Color.Gray }.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.08f))
                        .clickable { showDifficultyFilter = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(if (selectedLevel != null) "Dif: $selectedLevel" else "Dificultad", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Key button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedKey != null) Color(0xFFFFD600).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                        .clickable { showKeyFilter = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(if (selectedKey != null) "Ton: $selectedKey" else "Tonalidad", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Capo button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedCapo != null) Color(0xFFFFC107).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                        .clickable { showCapoFilter = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    val capoLabel = when (selectedCapo) { true -> "Capo: Sí"; false -> "Capo: No"; else -> "Capo" }
                    Text(capoLabel, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }

                // Meter filter button
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedMeter != null) Color(0xFF7C4DFF).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                        .clickable { showMeterFilter = true }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                ) {
                    Text(selectedMeter ?: "Compás", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Search bar
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar...", fontSize = 13.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(18.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Close, "Limpiar", tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(16.dp))
                        }
                    }
                },
                singleLine = true,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.White.copy(alpha = 0.08f),
                    unfocusedContainerColor = Color.White.copy(alpha = 0.06f),
                    cursorColor = Color.White,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
                    unfocusedPlaceholderColor = Color.White.copy(alpha = 0.3f),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().height(48.dp)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Table
            if (sorted.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay canciones con estos filtros",
                        color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                }
            } else {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    Column(modifier = Modifier.width(TABLE_TOTAL_WIDTH).fillMaxHeight()) {
                        // Header row with sort indicators
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2A2A2A))
                                .padding(vertical = 6.dp)
                        ) {
                            SONG_COLUMNS.forEach { col ->
                                val sortEntry = sortStack.find { it.columnKey == col.sortKey }
                                val sortIndex = sortStack.indexOfFirst { it.columnKey == col.sortKey }
                                Row(
                                    modifier = Modifier
                                        .width(col.width)
                                        .clip(RoundedCornerShape(4.dp))
                                        .clickable {
                                            sortStack = when {
                                                sortEntry == null -> sortStack + SortEntry(col.sortKey, SortDir.ASC)
                                                sortEntry.dir == SortDir.ASC -> sortStack.map {
                                                    if (it.columnKey == col.sortKey) it.copy(dir = SortDir.DESC) else it
                                                }
                                                else -> sortStack.filter { it.columnKey != col.sortKey }
                                            }
                                        }
                                        .padding(horizontal = 4.dp, vertical = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        col.header,
                                        color = if (sortEntry != null) Color(0xFF90CAF9) else Color(0xFFB0BEC5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (sortEntry != null) {
                                        Icon(
                                            if (sortEntry.dir == SortDir.ASC) Icons.Default.ArrowUpward else Icons.Default.ArrowDownward,
                                            null,
                                            tint = Color(0xFF90CAF9),
                                            modifier = Modifier.size(12.dp)
                                        )
                                        if (sortStack.size > 1) {
                                            Text("${sortIndex + 1}", color = Color(0xFF90CAF9).copy(alpha = 0.6f),
                                                fontSize = 8.sp)
                                        }
                                    }
                                }
                            }
                        }

                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(sorted, key = { "${it.title}-${it.artist}" }) { song ->
                                SongTableRow(song = song, onClick = { onPick(song) })
                            }
                        }
                    }
                }
            }
        }

        // Filter overlay dialogs (on top of everything)
        if (showSearchFieldFilter) {
            FilterOverlayDialog(
                title = "Buscar en",
                onDismiss = { showSearchFieldFilter = false }
            ) {
                SearchField.entries.forEach { field ->
                    val sel = searchField == field
                    FilterOptionRow(field.label, sel) { searchField = field; showSearchFieldFilter = false }
                }
            }
        }

        if (showDifficultyFilter) {
            FilterOverlayDialog(
                title = "Dificultad",
                onDismiss = { showDifficultyFilter = false }
            ) {
                FilterOptionRow("Todas", selectedLevel == null) { selectedLevel = null; showDifficultyFilter = false }
                (1..5).forEach { lvl ->
                    FilterOptionRow("Nivel $lvl", selectedLevel == lvl) { selectedLevel = lvl; showDifficultyFilter = false }
                }
            }
        }

        if (showKeyFilter) {
            FilterOverlayDialog(
                title = "Tonalidad",
                onDismiss = { showKeyFilter = false }
            ) {
                FilterOptionRow("Todas", selectedKey == null) { selectedKey = null; showKeyFilter = false }
                allKeys.forEach { key ->
                    FilterOptionRow(key, selectedKey == key) { selectedKey = key; showKeyFilter = false }
                }
            }
        }

        if (showCapoFilter) {
            FilterOverlayDialog(
                title = "Capo",
                onDismiss = { showCapoFilter = false }
            ) {
                FilterOptionRow("Todos", selectedCapo == null) { selectedCapo = null; showCapoFilter = false }
                FilterOptionRow("Con capo", selectedCapo == true) { selectedCapo = true; showCapoFilter = false }
                FilterOptionRow("Sin capo", selectedCapo == false) { selectedCapo = false; showCapoFilter = false }
            }
        }
        if (showMeterFilter) {
            FilterOverlayDialog(
                title = "Compás",
                onDismiss = { showMeterFilter = false }
            ) {
                FilterOptionRow("Todos", selectedMeter == null) { selectedMeter = null; showMeterFilter = false }
                allMeters.forEach { meter ->
                    FilterOptionRow(meter, selectedMeter == meter) { selectedMeter = meter; showMeterFilter = false }
                }
            }
        }
    }
}

@Composable
private fun SongTableRow(song: Song, onClick: () -> Unit) {
    val levelColor = LEVEL_COLORS.getOrElse(song.level - 1) { Color.Gray }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .background(Color.White.copy(alpha = 0.03f))
                .padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            SONG_COLUMNS.forEachIndexed { idx, col ->
                val value = col.extract(song)
                Box(modifier = Modifier.width(col.width).padding(horizontal = 6.dp)) {
                    when (idx) {
                        0 -> Text(value, color = Color.White, fontSize = 13.sp,
                            fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        1 -> Text(value, color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp,
                            maxLines = 1, overflow = TextOverflow.Ellipsis)
                        2 -> Box(
                            modifier = Modifier.size(24.dp).clip(CircleShape)
                                .background(levelColor.copy(alpha = 0.3f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(value, color = levelColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        3 -> Text(value, color = Color(0xFF7C4DFF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        4 -> Text(value, color = Color(0xFF90CAF9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        5 -> Text(value, color = Color(0xFFFFD600), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        6 -> Text(value, color = Color(0xFF81C784), fontSize = 12.sp)
                        7 -> Text(value, color = Color(0xFFFF8A65), fontSize = 12.sp)
                        8 -> Text(value,
                            color = if (song.capo > 0) Color(0xFFFFC107) else Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp)
                        9 -> Text(value, color = Color(0xFFCE93D8), fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        10 -> Text(value, color = Color(0xFF80DEEA), fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        else -> Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}

@Composable
private fun FilterOverlayDialog(
    title: String,
    onDismiss: () -> Unit,
    content: @Composable () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.75f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() },
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.7f)
                .fillMaxHeight(0.7f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF2A2A2A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = true
                ) {}
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
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
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                content()
            }
        }
    }
}

@Composable
private fun FilterOptionRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(if (selected) Color(0xFF7B1FA2).copy(alpha = 0.4f) else Color.White.copy(alpha = 0.06f))
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(label, color = if (selected) Color.White else Color.White.copy(alpha = 0.7f),
            fontSize = 15.sp, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal)
    }
    Spacer(modifier = Modifier.height(4.dp))
}
