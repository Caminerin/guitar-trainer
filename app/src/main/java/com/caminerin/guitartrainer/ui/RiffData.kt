package com.caminerin.guitartrainer.ui

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class RiffNote(
    val string: Int,   // 1-6 (1=high E, 6=low E)
    val fret: Int,
    val startSub: Int, // 1-based subdivision within measure
    val endSub: Int,
    val technique: String = "" // palm_mute, staccato, tremolo, bend, hammer_on, pull_off
)

data class RiffMeasure(
    val index: Int,
    val notes: List<RiffNote>
)

data class Riff(
    val id: String,
    val title: String,
    val artist: String,
    val style: String,
    val level: Int,
    val bpmStart: Int,
    val bpmTarget: Int,
    val meter: String,
    val subdivisionsPerMeasure: Int,
    val key: String,
    val tuning: String,
    val sound: String,
    val technique: String,
    val practiceComment: String,
    val usefulMeasures: Int,
    val tags: List<String>,
    val measures: List<RiffMeasure>
)

// Matches: string.fret(start-end) or string.fret(start-end,technique)
private val NOTE_REGEX = Regex("(\\d+)\\.(\\d+)\\((\\d+)-(\\d+)(?:,([a-z_]+))?\\)")

private fun parseMeasureNotes(raw: String): List<RiffNote> {
    if (raw.isBlank()) return emptyList()
    return raw.split(";").mapNotNull { token ->
        val m = NOTE_REGEX.find(token.trim()) ?: return@mapNotNull null
        RiffNote(
            string = m.groupValues[1].toInt(),
            fret = m.groupValues[2].toInt(),
            startSub = m.groupValues[3].toInt(),
            endSub = m.groupValues[4].toInt(),
            technique = m.groupValues.getOrElse(5) { "" }
        )
    }
}

object RiffRepository {
    private var riffs: List<Riff> = emptyList()
    private var allStyles: List<String> = emptyList()
    private var allLevels: List<Int> = emptyList()
    private var headerIndex: Map<String, Int> = emptyMap()

    fun load(context: Context) {
        if (riffs.isNotEmpty()) return
        val result = mutableListOf<Riff>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("riffs.csv")))
            val headerLine = reader.readLine()?.removePrefix("\uFEFF") ?: return
            val headerParts = smartSplit(headerLine)
            headerIndex = headerParts.withIndex().associate { (i, v) -> v.trim() to i }

            var line = reader.readLine()
            while (line != null) {
                val riff = parseRiffLine(line)
                if (riff != null) result.add(riff)
                line = reader.readLine()
            }
            reader.close()
        } catch (e: Exception) {
            android.util.Log.e("RiffData", "Error loading riffs.csv", e)
        }
        riffs = result
        allStyles = riffs.map { it.style }.distinct().sorted()
        allLevels = riffs.map { it.level }.distinct().sorted()
    }

    fun getRiffs(): List<Riff> = riffs
    fun getStyles(): List<String> = allStyles
    fun getLevels(): List<Int> = allLevels

    fun filter(
        level: Int? = null,
        style: String? = null,
        searchQuery: String = ""
    ): List<Riff> {
        return riffs.filter { riff ->
            (level == null || riff.level == level) &&
            (style == null || riff.style == style) &&
            (searchQuery.isBlank() ||
                riff.title.contains(searchQuery, ignoreCase = true) ||
                riff.artist.contains(searchQuery, ignoreCase = true))
        }
    }

    private fun col(parts: List<String>, name: String): String {
        val idx = headerIndex[name] ?: return ""
        return parts.getOrElse(idx) { "" }.trim()
    }

    private fun parseRiffLine(line: String): Riff? {
        try {
            val parts = smartSplit(line)
            if (parts.size < 10) return null

            val id = col(parts, "riff_id")
            if (id.isBlank()) return null

            val title = col(parts, "titulo")
            val artist = col(parts, "artista")
            val style = col(parts, "estilo")
            val level = col(parts, "nivel_1_5").toIntOrNull() ?: 1
            val bpmStart = col(parts, "bpm_practica_inicio").toIntOrNull() ?: 60
            val bpmTarget = col(parts, "bpm_practica_objetivo").toIntOrNull() ?: 120
            val meter = col(parts, "metrica").ifBlank { "4/4" }
            val subdivisions = col(parts, "subdivisiones_por_compas").toIntOrNull() ?: 8
            val key = col(parts, "tonalidad")
            val tuning = col(parts, "afinacion").ifBlank { "EADGBE" }
            val sound = col(parts, "sonido")
            val technique = col(parts, "tecnica_principal")
            val comment = col(parts, "comentario_practica")
            val usefulMeasures = col(parts, "compases_utiles").toIntOrNull() ?: 2
            val tags = col(parts, "tags").split(",").map { it.trim() }.filter { it.isNotEmpty() }

            val measures = (1..16).mapNotNull { n ->
                val raw = col(parts, "compas_%02d".format(n))
                if (raw.isBlank()) return@mapNotNull null
                val notes = parseMeasureNotes(raw)
                if (notes.isEmpty()) return@mapNotNull null
                RiffMeasure(index = n, notes = notes)
            }

            if (measures.isEmpty()) return null

            return Riff(
                id = id, title = title, artist = artist, style = style,
                level = level, bpmStart = bpmStart, bpmTarget = bpmTarget,
                meter = meter, subdivisionsPerMeasure = subdivisions,
                key = key, tuning = tuning, sound = sound,
                technique = technique, practiceComment = comment,
                usefulMeasures = usefulMeasures, tags = tags,
                measures = measures
            )
        } catch (_: Exception) {
            return null
        }
    }
}
