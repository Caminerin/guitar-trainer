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
    val meter: String,
    val key: String,
    val capo: Int,
    val tuning: String,
    val chordsUsed: List<String>,
    val measuresUsed: Int,
    val subdivisionsPerMeasure: Int,
    val strumPattern: String,
    val strumLegend: String,
    val defaultStrums: List<String>,
    val measures: List<SongMeasure>,
    val arrangementType: String,
    val practiceFocus: String,
    val sourceUrl: String,
    val notes: String
)

data class MeasureChord(
    val symbol: String,
    val startBeat: Int,
    val endBeat: Int
)

data class SongMeasure(
    val index: Int,
    val chords: List<MeasureChord>,
    val strumPattern: List<String>,
    val raw: String
) {
    val chordSymbol: String get() = chords.firstOrNull()?.symbol.orEmpty()
}

private val BRACKET_REGEX = Regex("\\[(.+?)]")
private val MULTI_CHORD_REGEX = Regex("(\\S+?)\\((\\d+)-(\\d+)\\)")

private fun parseMeasureCell(raw: String, defaultStrums: List<String>): SongMeasure? {
    if (raw.isBlank()) return null

    val bracketMatch = BRACKET_REGEX.find(raw)
    val strumPattern = if (bracketMatch != null) {
        bracketMatch.groupValues[1].trim().split("\\s+".toRegex())
    } else {
        defaultStrums.ifEmpty { listOf("D", "D", "D", "D") }
    }

    val chordPart = BRACKET_REGEX.replace(raw, "").trim()
    if (chordPart.isBlank()) return null

    val chords = if (chordPart.contains("/") && chordPart.contains("(")) {
        chordPart.split("/").mapNotNull { segment ->
            val m = MULTI_CHORD_REGEX.find(segment.trim())
            if (m != null) {
                MeasureChord(
                    symbol = m.groupValues[1],
                    startBeat = m.groupValues[2].toIntOrNull() ?: 1,
                    endBeat = m.groupValues[3].toIntOrNull() ?: 4
                )
            } else {
                val trimmed = segment.trim()
                if (trimmed.isNotBlank()) MeasureChord(trimmed, 1, 4) else null
            }
        }
    } else {
        listOf(MeasureChord(chordPart, 1, 4))
    }

    if (chords.isEmpty()) return null
    return SongMeasure(index = 0, chords = chords, strumPattern = strumPattern, raw = raw)
}

object SongRepository {
    private var songs: List<Song> = emptyList()
    private var allStyles: List<String> = emptyList()
    private var allLanguages: List<String> = emptyList()
    private var allLevels: List<Int> = emptyList()
    private var headerIndex: Map<String, Int> = emptyMap()

    fun load(context: Context) {
        if (songs.isNotEmpty()) return
        val result = mutableListOf<Song>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("songs.csv")))
            val headerLine = reader.readLine()?.removePrefix("\uFEFF") ?: return
            val headerParts = smartSplit(headerLine)
            headerIndex = headerParts.withIndex().associate { (i, v) -> v.trim() to i }

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

    private fun col(parts: List<String>, name: String): String {
        val idx = headerIndex[name] ?: return ""
        return parts.getOrElse(idx) { "" }.trim()
    }

    private val KEY_REGEX = Regex("^[A-G][#b]?m?$")

    private fun normalizeKey(raw: String): String {
        val trimmed = raw.trim()
        if (KEY_REGEX.matches(trimmed)) return trimmed
        val match = Regex("[A-G][#b]?m?").find(trimmed)
        return match?.value ?: trimmed
    }

    private fun parseSongLine(line: String): Song? {
        try {
            val parts = smartSplit(line)
            if (parts.size < 20) return null

            val ranking = col(parts, "ranking_aprox").toIntOrNull() ?: return null
            val title = col(parts, "titulo")
            val artist = col(parts, "artista")
            val language = col(parts, "idioma")
            val style = col(parts, "estilo")
            val level = col(parts, "nivel_1_5").toIntOrNull() ?: 1
            val bpmStart = col(parts, "bpm_practica_inicio").toIntOrNull() ?: 60
            val bpmTarget = col(parts, "bpm_practica_objetivo").toIntOrNull() ?: 80
            val meter = col(parts, "metro_adaptado")
            val rawKey = col(parts, "tonalidad_sugerida")
            val key = normalizeKey(rawKey)
            val capo = col(parts, "capo_traste").toIntOrNull() ?: 0
            val tuning = col(parts, "afinacion")
            val chordsUsed = col(parts, "acordes_sugeridos")
                .split(";").map { it.trim() }.filter { it.isNotEmpty() }
            val measuresUsed = col(parts, "compases_usados").toIntOrNull() ?: 4
            val subdivisionsPerMeasure = col(parts, "subdivisiones_por_compas").toIntOrNull() ?: 4
            val strumPattern = col(parts, "patron_golpes_4_subdiv")
            val strumLegend = col(parts, "leyenda_golpes")

            val defaultStrums = listOf(
                col(parts, "golpe_1"),
                col(parts, "golpe_2"),
                col(parts, "golpe_3"),
                col(parts, "golpe_4")
            ).filter { it.isNotBlank() }

            val measures = (1..12).mapNotNull { n ->
                val raw = col(parts, "compas_%02d".format(n))
                parseMeasureCell(raw, defaultStrums)?.copy(index = n)
            }

            val arrangementType = col(parts, "tipo_arreglo")
            val practiceFocus = col(parts, "foco_practica")
            val sourceUrl = col(parts, "fuente_url")
            val notes = col(parts, "notas")

            return Song(
                ranking = ranking,
                title = title,
                artist = artist,
                language = language,
                style = style,
                level = level,
                bpmStart = bpmStart,
                bpmTarget = bpmTarget,
                meter = meter,
                key = key,
                capo = capo,
                tuning = tuning,
                chordsUsed = chordsUsed,
                measuresUsed = measuresUsed,
                subdivisionsPerMeasure = subdivisionsPerMeasure,
                strumPattern = strumPattern,
                strumLegend = strumLegend,
                defaultStrums = defaultStrums,
                measures = measures,
                arrangementType = arrangementType,
                practiceFocus = practiceFocus,
                sourceUrl = sourceUrl,
                notes = notes
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
