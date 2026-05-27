package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
    val extract: (Song) -> String
)

private val SONG_COLUMNS = listOf(
    SongColumn("Canci\u00f3n", 180.dp) { it.title },
    SongColumn("Artista", 140.dp) { it.artist },
    SongColumn("Dif.", 50.dp) { "${it.level}" },
    SongColumn("N\u00ba Ac.", 55.dp) { "${it.chordsUsed.size}" },
    SongColumn("Tonalidad", 80.dp) { it.key },
    SongColumn("BPM Ini.", 65.dp) { "${it.bpmStart}" },
    SongColumn("BPM Obj.", 65.dp) { "${it.bpmTarget}" },
    SongColumn("Capo", 50.dp) { if (it.capo > 0) "S\u00ed (${it.capo})" else "No" },
    SongColumn("Acordes", 200.dp) { it.chordsUsed.joinToString(", ") },
    SongColumn("Foco pr\u00e1ctica", 200.dp) { it.practiceFocus }
)

private val TABLE_TOTAL_WIDTH = SONG_COLUMNS.sumOf { it.width.value.toInt() }.dp

@Composable
fun SongPickerOverlay(
    songs: List<Song>,
    onPick: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<Int?>(null) }

    val filtered = remember(searchQuery, selectedLevel) {
        songs.filter { song ->
            (selectedLevel == null || song.level == selectedLevel) &&
            (searchQuery.isBlank() ||
                song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.chordsUsed.any { it.contains(searchQuery, ignoreCase = true) })
        }.sortedBy { it.level }
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
                Text("${filtered.size}", color = Color.White.copy(alpha = 0.4f), fontSize = 13.sp)
                Spacer(modifier = Modifier.weight(1f))
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White, modifier = Modifier.size(20.dp))
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Search + Level filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
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
                    modifier = Modifier.weight(1f).height(48.dp)
                )

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedLevel == null) Color(0xFF7B1FA2) else Color.White.copy(alpha = 0.06f))
                        .clickable { selectedLevel = null }
                        .padding(horizontal = 6.dp, vertical = 6.dp)
                ) {
                    Text("All", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                (1..3).forEach { lvl ->
                    val sel = selectedLevel == lvl
                    val color = LEVEL_COLORS.getOrElse(lvl - 1) { Color.Gray }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (sel) color else color.copy(alpha = 0.2f))
                            .clickable { selectedLevel = if (sel) null else lvl },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$lvl", color = if (sel) Color.White else color,
                            fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Table with horizontal scroll wrapping header + body together
            if (filtered.isEmpty()) {
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
                        // Header row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color(0xFF2A2A2A))
                                .padding(vertical = 8.dp)
                        ) {
                            SONG_COLUMNS.forEach { col ->
                                Box(modifier = Modifier.width(col.width).padding(horizontal = 6.dp)) {
                                    Text(
                                        col.header,
                                        color = Color(0xFFB0BEC5),
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }

                        // Separator
                        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))

                        // Data rows
                        LazyColumn(modifier = Modifier.fillMaxWidth().weight(1f)) {
                            items(filtered, key = { "${it.title}-${it.artist}" }) { song ->
                                SongTableRow(song = song, onClick = { onPick(song) })
                            }
                        }
                    }
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
                        3 -> Text(value, color = Color(0xFF90CAF9), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        4 -> Text(value, color = Color(0xFFFFD600), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        5 -> Text(value, color = Color(0xFF81C784), fontSize = 12.sp)
                        6 -> Text(value, color = Color(0xFFFF8A65), fontSize = 12.sp)
                        7 -> Text(value,
                            color = if (song.capo > 0) Color(0xFFFFC107) else Color.White.copy(alpha = 0.4f),
                            fontSize = 12.sp)
                        8 -> Text(value, color = Color(0xFFCE93D8), fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        9 -> Text(value, color = Color(0xFF80DEEA), fontSize = 11.sp,
                            maxLines = 2, overflow = TextOverflow.Ellipsis)
                        else -> Text(value, color = Color.White.copy(alpha = 0.6f), fontSize = 12.sp)
                    }
                }
            }
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.06f)))
    }
}
