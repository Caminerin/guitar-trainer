package com.caminerin.guitartrainer.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val LEVEL_COLORS = listOf(
    Color(0xFF4CAF50), // 1 - easy
    Color(0xFF8BC34A), // 2
    Color(0xFFFFC107), // 3
    Color(0xFFFF9800), // 4
    Color(0xFFF44336), // 5 - hard
)

private val STYLE_COLORS = mapOf(
    "Folk" to Color(0xFF795548),
    "Folk rock" to Color(0xFF8D6E63),
    "Country rock" to Color(0xFFD4A574),
    "Country / folk" to Color(0xFFA1887F),
    "Pop rock" to Color(0xFF42A5F5),
    "Pop acústico / reggae" to Color(0xFF26C6DA),
    "Rock / pop clásico" to Color(0xFF5C6BC0),
    "Rock alternativo" to Color(0xFF7E57C2),
    "Rock sureño" to Color(0xFFAB47BC),
    "Reggae" to Color(0xFF66BB6A),
    "Soul / R&B" to Color(0xFFEF5350),
    "Indie folk" to Color(0xFF26A69A),
    "Britpop / acústica" to Color(0xFF29B6F6),
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SongPickerOverlay(
    songs: List<Song>,
    onPick: (Song) -> Unit,
    onDismiss: () -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedLevel by remember { mutableStateOf<Int?>(null) }
    var selectedStyle by remember { mutableStateOf<String?>(null) }

    val styles = remember { songs.map { it.style }.distinct().sorted() }

    val filtered = remember(searchQuery, selectedLevel, selectedStyle) {
        songs.filter { song ->
            (selectedLevel == null || song.level == selectedLevel) &&
            (selectedStyle == null || song.style == selectedStyle) &&
            (searchQuery.isBlank() ||
                song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true) ||
                song.chordsUsed.any { it.contains(searchQuery, ignoreCase = true) })
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
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF1A1A1A))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    enabled = true
                ) { /* block dismiss */ }
                .padding(16.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.MusicNote, null, tint = Color(0xFFFFC107),
                    modifier = Modifier.size(28.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Canciones", color = Color.White, fontSize = 22.sp,
                    fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.weight(1f))
                Text("${filtered.size}", color = Color.White.copy(alpha = 0.5f),
                    fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, "Cerrar", tint = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Search bar (Material3 TextField for reliable input)
            TextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar canción o artista...", fontSize = 15.sp) },
                leadingIcon = {
                    Icon(Icons.Default.Search, null, tint = Color.White.copy(alpha = 0.4f),
                        modifier = Modifier.size(20.dp))
                },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Default.Close, "Limpiar", tint = Color.White.copy(alpha = 0.5f),
                                modifier = Modifier.size(18.dp))
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
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(10.dp))

            // Level filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Nivel:", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedLevel == null) Color(0xFF7B1FA2) else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedLevel = null }
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text("Todos", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                (1..3).forEach { lvl ->
                    val selected = selectedLevel == lvl
                    val color = LEVEL_COLORS.getOrElse(lvl - 1) { Color.Gray }
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(if (selected) color else color.copy(alpha = 0.2f))
                            .clickable { selectedLevel = if (selected) null else lvl },
                        contentAlignment = Alignment.Center
                    ) {
                        Text("$lvl", color = if (selected) Color.White else color,
                            fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Style filter
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (selectedStyle == null) Color(0xFF7B1FA2) else Color.White.copy(alpha = 0.08f))
                        .clickable { selectedStyle = null }
                        .padding(horizontal = 8.dp, vertical = 3.dp)
                ) {
                    Text("Todos", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
                styles.forEach { st ->
                    val selected = selectedStyle == st
                    val color = STYLE_COLORS[st] ?: Color(0xFF607D8B)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (selected) color else color.copy(alpha = 0.15f))
                            .clickable { selectedStyle = if (selected) null else st }
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(st, color = if (selected) Color.White else color,
                            fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(Color.White.copy(alpha = 0.1f)))
            Spacer(modifier = Modifier.height(6.dp))

            // Song list (LazyColumn for better performance)
            if (filtered.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("No hay canciones con estos filtros",
                        color = Color.White.copy(alpha = 0.4f), fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(filtered, key = { "${it.title}-${it.artist}" }) { song ->
                        SongCard(song = song, onClick = { onPick(song) })
                    }
                }
            }
        }
    }
}

@Composable
private fun SongCard(song: Song, onClick: () -> Unit) {
    val levelColor = LEVEL_COLORS.getOrElse(song.level - 1) { Color.Gray }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color.White.copy(alpha = 0.05f))
            .clickable { onClick() }
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Level indicator
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(levelColor.copy(alpha = 0.2f)),
            contentAlignment = Alignment.Center
        ) {
            Text("${song.level}", color = levelColor, fontSize = 14.sp,
                fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.width(10.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, color = Color.White, fontSize = 15.sp,
                fontWeight = FontWeight.Bold, maxLines = 1)
            Text(song.artist, color = Color.White.copy(alpha = 0.5f),
                fontSize = 12.sp, maxLines = 1)
        }

        Spacer(modifier = Modifier.width(6.dp))

        Column(horizontalAlignment = Alignment.End) {
            Text("${song.bpmStart}-${song.bpmTarget}", color = Color(0xFF90A4AE),
                fontSize = 11.sp)
            Text("${song.measuresUsed} comp.", color = Color(0xFF90A4AE),
                fontSize = 11.sp)
            Row {
                song.chordsUsed.take(4).forEach { chord ->
                    Box(
                        modifier = Modifier
                            .padding(start = 2.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(Color(0xFF7B1FA2).copy(alpha = 0.3f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(chord, color = Color.White, fontSize = 9.sp)
                    }
                }
                if (song.chordsUsed.size > 4) {
                    Text("+${song.chordsUsed.size - 4}", color = Color.White.copy(alpha = 0.3f),
                        fontSize = 9.sp, modifier = Modifier.padding(start = 2.dp))
                }
            }
        }
    }
}
