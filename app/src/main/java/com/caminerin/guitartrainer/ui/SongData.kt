package com.caminerin.guitartrainer.ui

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class Song(
    val ranking: Int,
    val title: String,
    val artist: String,
    val language: String,
    val style: String,
    val level: Int,
    val bpmStart: Int,
    val bpmTarget: Int,
    val key: String,
    val capo: Int,
    val chordsUsed: List<String>,
    val measuresUsed: Int,
    val subdivisionsPerMeasure: Int,
    val strumPattern: String,
    val strumLegend: String,
    val measures: List<String>, // chord name for each measure (up to 12)
    val tips: String
)

object SongRepository {
    private var songs: List<Song> = emptyList()
    private var allStyles: List<String> = emptyList()
    private var allLanguages: List<String> = emptyList()
    private var allLevels: List<Int> = emptyList()

    fun load(context: Context) {
        if (songs.isNotEmpty()) return
        val result = mutableListOf<Song>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("songs.csv")))
            reader.readLine() // skip header (with BOM)
            var line = reader.readLine()
            while (line != null) {
                parseSongLine(line)?.let { result.add(it) }
                line = reader.readLine()
            }
            reader.close()
        } catch (_: Exception) {}
        songs = result
        allStyles = songs.map { it.style }.distinct().sorted()
        allLanguages = songs.map { it.language }.distinct().sorted()
        allLevels = songs.map { it.level }.distinct().sorted()
    }

    fun getSongs(): List<Song> = songs
    fun getStyles(): List<String> = allStyles
    fun getLanguages(): List<String> = allLanguages
    fun getLevels(): List<Int> = allLevels

    fun filter(
        level: Int? = null,
        style: String? = null,
        language: String? = null,
        searchQuery: String = ""
    ): List<Song> {
        return songs.filter { song ->
            (level == null || song.level == level) &&
            (style == null || song.style == style) &&
            (language == null || song.language == language) &&
            (searchQuery.isBlank() ||
                song.title.contains(searchQuery, ignoreCase = true) ||
                song.artist.contains(searchQuery, ignoreCase = true))
        }
    }

    private fun parseSongLine(line: String): Song? {
        try {
            val parts = smartSplit(line)
            if (parts.size < 30) return null

            val ranking = parts[0].trim().toIntOrNull() ?: return null
            val title = parts[1].trim()
            val artist = parts[2].trim()
            val language = parts[3].trim()
            val style = parts[4].trim()
            val level = parts[5].trim().toIntOrNull() ?: 1
            val bpmStart = parts[6].trim().toIntOrNull() ?: 60
            val bpmTarget = parts[7].trim().toIntOrNull() ?: 80
            val key = parts[9].trim()
            val capo = parts[10].trim().toIntOrNull() ?: 0
            val chordsUsed = parts[12].trim().split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val measuresUsed = parts[14].trim().toIntOrNull() ?: 4
            val subdivisionsPerMeasure = parts[15].trim().toIntOrNull() ?: 4
            val strumPattern = parts[17].trim()
            val strumLegend = parts[18].trim()

            // Parse measures (compas_01 to compas_12 are at indices 24..35)
            val measures = mutableListOf<String>()
            for (i in 24..35) {
                val m = parts.getOrElse(i) { "" }.trim()
                if (m.isNotEmpty()) {
                    // Extract chord name: "Em [D - D U]" -> "Em"
                    val chordName = m.split("[").firstOrNull()?.trim() ?: m
                    measures.add(chordName)
                }
            }

            val tips = parts.getOrElse(37) { "" }.trim()

            return Song(
                ranking = ranking,
                title = title,
                artist = artist,
                language = language,
                style = style,
                level = level,
                bpmStart = bpmStart,
                bpmTarget = bpmTarget,
                key = key,
                capo = capo,
                chordsUsed = chordsUsed,
                measuresUsed = measuresUsed,
                subdivisionsPerMeasure = subdivisionsPerMeasure,
                strumPattern = strumPattern,
                strumLegend = strumLegend,
                measures = measures,
                tips = tips
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun smartSplit(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    parts.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        parts.add(current.toString())
        return parts
    }
}
