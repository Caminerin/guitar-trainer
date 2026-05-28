package com.caminerin.guitartrainer.ui

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class StrokeInfo(
    val type: String,   // "down", "up", "rest", "mute"
    val vel: Float
)

data class SongSection(
    val name: String,
    val pattern: List<StrokeInfo>,
    val measures: List<SectionMeasure>
)

data class SectionMeasure(
    val chords: List<String>,
    val chordsPerSub: List<String>
)

// Legacy compat types used by the old flat-measure loader
data class MeasureChord(
    val symbol: String,
    val startBeat: Int,
    val endBeat: Int
)

data class SongMeasure(
    val index: Int,
    val chords: List<MeasureChord>,
    val strumPattern: List<String>,
    val raw: String,
    val perSubdivisionChords: List<String> = emptyList()
) {
    val chordSymbol: String get() = chords.firstOrNull()?.symbol.orEmpty()
}

data class Song(
    val id: String,
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
    val subdivisions: Int,
    val feel: String,
    val swing: Boolean,
    val patternId: String,
    val sections: List<SongSection>,
    val arrangement: List<String>,
    val sectionPatterns: Map<String, List<StrokeInfo>>,
    val sourceUrl: String,
    // Legacy fields kept for SongPickerOverlay compat
    val practiceFocus: String = "",
    val measuresUsed: Int = 0,
    val strumPattern: String = "",
    val strumLegend: String = "",
    val defaultStrums: List<String> = emptyList(),
    val measures: List<SongMeasure> = emptyList(),
    val arrangementType: String = "",
    val notes: String = ""
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
            val jsonStr = context.assets.open("songs.json").bufferedReader().use { it.readText() }
            val arr = JSONArray(jsonStr)
            for (i in 0 until arr.length()) {
                val song = parseSongJson(arr.getJSONObject(i))
                if (song != null) result.add(song)
            }
            android.util.Log.i("SongData", "Loaded ${result.size} songs from JSON")
        } catch (e: Exception) {
            android.util.Log.e("SongData", "Error loading songs.json", e)
        }
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

    private fun parseStrokeArray(arr: JSONArray): List<StrokeInfo> {
        val result = mutableListOf<StrokeInfo>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(StrokeInfo(
                type = obj.optString("type", "rest"),
                vel = obj.optDouble("vel", 0.0).toFloat()
            ))
        }
        return result
    }

    private fun parseSongJson(obj: JSONObject): Song? {
        try {
            val id = obj.optString("id", "")
            val title = obj.optString("title", "")
            val artist = obj.optString("artist", "")
            if (title.isBlank() || artist.isBlank()) return null

            // Parse section patterns
            val patternsObj = obj.optJSONObject("patterns")
            val sectionPatterns = mutableMapOf<String, List<StrokeInfo>>()
            if (patternsObj != null) {
                for (key in patternsObj.keys()) {
                    sectionPatterns[key] = parseStrokeArray(patternsObj.getJSONArray(key))
                }
            }

            // Parse sections
            val sectionsArr = obj.optJSONArray("sections")
            val sections = mutableListOf<SongSection>()
            if (sectionsArr != null) {
                for (i in 0 until sectionsArr.length()) {
                    val secObj = sectionsArr.getJSONObject(i)
                    val secName = secObj.optString("name", "verso")

                    // Parse pattern from the section's strum pattern string
                    val patternStr = secObj.optString("pattern", "")
                    val sectionPattern = sectionPatterns[secName]
                        ?: parseStrumString(patternStr)

                    val measArr = secObj.optJSONArray("measures") ?: continue
                    val sectionMeasures = mutableListOf<SectionMeasure>()
                    for (j in 0 until measArr.length()) {
                        val mObj = measArr.getJSONObject(j)
                        val chords = jsonArrayToStringList(mObj.optJSONArray("chords"))
                        val chordsPerSub = jsonArrayToStringList(mObj.optJSONArray("chords_per_sub"))
                        sectionMeasures.add(SectionMeasure(chords, chordsPerSub))
                    }
                    sections.add(SongSection(secName, sectionPattern, sectionMeasures))
                }
            }

            // Parse arrangement
            val arrangement = jsonArrayToStringList(obj.optJSONArray("arrangement"))

            // Parse chords used
            val chordsUsed = jsonArrayToStringList(obj.optJSONArray("chordsUsed"))

            // Count total measures
            val totalMeasures = sections.sumOf { it.measures.size }

            return Song(
                id = id,
                ranking = obj.optInt("ranking", 999),
                title = title,
                artist = artist,
                language = obj.optString("language", ""),
                style = obj.optString("style", ""),
                level = obj.optInt("level", 1),
                bpmStart = obj.optInt("bpmStart", 60),
                bpmTarget = obj.optInt("bpmTarget", 80),
                meter = obj.optString("meter", "4/4"),
                key = obj.optString("key", ""),
                capo = obj.optInt("capo", 0),
                tuning = obj.optString("tuning", "EADGBE"),
                chordsUsed = chordsUsed,
                subdivisions = obj.optInt("subdivisions", 8),
                feel = obj.optString("feel", ""),
                swing = obj.optBoolean("swing", false),
                patternId = obj.optString("patternId", "pop_rock"),
                sections = sections,
                arrangement = arrangement,
                sectionPatterns = sectionPatterns,
                sourceUrl = obj.optString("sourceUrl", ""),
                measuresUsed = totalMeasures,
                practiceFocus = obj.optString("feel", "")
            )
        } catch (e: Exception) {
            android.util.Log.w("SongData", "Error parsing song JSON", e)
            return null
        }
    }

    private fun jsonArrayToStringList(arr: JSONArray?): List<String> {
        if (arr == null) return emptyList()
        val result = mutableListOf<String>()
        for (i in 0 until arr.length()) {
            result.add(arr.optString(i, ""))
        }
        return result
    }

    private fun parseStrumString(pattern: String): List<StrokeInfo> {
        if (pattern.isBlank()) return emptyList()
        return pattern.trim().split("\\s+".toRegex()).mapIndexed { idx, token ->
            when (token) {
                "D" -> StrokeInfo("down", if (idx == 0) 1.0f else 0.8f)
                "U" -> StrokeInfo("up", 0.5f)
                "x" -> StrokeInfo("mute", 0.6f)
                else -> StrokeInfo("rest", 0.0f)
            }
        }
    }
}
