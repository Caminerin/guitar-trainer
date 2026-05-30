package com.caminerin.guitartrainer.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class TabBeat(
    val duration: Int,
    val isDotted: Boolean = false,
    val isRest: Boolean = false,
    val notes: List<TabNote> = emptyList()
)

data class TabNote(
    val string: Int,
    val fret: Int,
    val velocity: Int = 100,
    val effects: List<String> = emptyList()
)

data class TabTrack(
    val name: String,
    val type: String,
    val tuning: List<Int>,
    val totalNotes: Int,
    val measures: List<List<TabBeat>>
)

data class TabSong(
    val title: String,
    val artist: String,
    val tempo: Int,
    val tracks: List<TabTrack>
) {
    fun playableTracks(): List<TabTrack> = tracks.filter { it.type in listOf("guitar", "bass") }
}

data class CatalogEntry(
    val artist: String,
    val song: String,
    val tempo: Int,
    val tracks: Int,
    val guitarTracks: Int,
    val bassTracks: Int,
    val path: String,
    val isUserTab: Boolean = false
) {
    val otherTracks: Int get() = (tracks - guitarTracks - bassTracks).coerceAtLeast(0)
}

// User-imported tab file metadata
data class UserTabFile(
    val id: String,
    val fileName: String,
    val displayName: String,
    val format: String, // gp3, gp4, gp5, gpx, pdf, txt
    val importedAt: Long
)

object TabRepository {
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000
    private const val USER_TABS_DIR = "user_tabs"
    private const val USER_TABS_INDEX = "user_tabs_index.json"

    @Volatile private var catalog: List<CatalogEntry> = emptyList()
    @Volatile private var allArtists: List<String> = emptyList()
    @Volatile private var catalogLoaded = false
    @Volatile var loadError: String? = null
        private set

    @Volatile private var userTabs: List<UserTabFile> = emptyList()

    fun isLoaded() = catalogLoaded
    fun getCatalog() = catalog
    fun getArtists() = allArtists
    fun getUserTabs() = userTabs

    fun reset() {
        catalog = emptyList()
        allArtists = emptyList()
        catalogLoaded = false
        loadError = null
    }

    fun filter(
        searchQuery: String = "",
        artist: String? = null,
        bpmMin: Int? = null,
        bpmMax: Int? = null
    ): List<CatalogEntry> {
        return catalog.filter { entry ->
            (artist == null || entry.artist.equals(artist, ignoreCase = true)) &&
            (bpmMin == null || entry.tempo >= bpmMin) &&
            (bpmMax == null || entry.tempo <= bpmMax) &&
            (searchQuery.isBlank() ||
                entry.song.contains(searchQuery, ignoreCase = true) ||
                entry.artist.contains(searchQuery, ignoreCase = true))
        }
    }

    private fun builtInExercises(): List<CatalogEntry> {
        return listOf(
            CatalogEntry("Ejercicios", "Cromático 1-2-3-4", 80, 1, 1, 0, "builtin://chromatic_1234"),
            CatalogEntry("Ejercicios", "Cromático 4-3-2-1", 80, 1, 1, 0, "builtin://chromatic_4321"),
            CatalogEntry("Ejercicios", "Cromático 1-3-2-4", 80, 1, 1, 0, "builtin://chromatic_1324"),
            CatalogEntry("Ejercicios", "Alternate picking básico", 100, 1, 1, 0, "builtin://alt_picking_basic"),
            CatalogEntry("Ejercicios", "Hammer-on / Pull-off", 90, 1, 1, 0, "builtin://hammer_pulloff"),
            CatalogEntry("Ejercicios", "String skipping", 80, 1, 1, 0, "builtin://string_skipping"),
            CatalogEntry("Ejercicios", "Arpegios triada mayor", 70, 1, 1, 0, "builtin://arpeggio_major"),
            CatalogEntry("Ejercicios", "Arpegios triada menor", 70, 1, 1, 0, "builtin://arpeggio_minor"),
            CatalogEntry("Ejercicios", "Spider exercise", 60, 1, 1, 0, "builtin://spider"),
            CatalogEntry("Ejercicios", "Escala mayor (patrón 1)", 80, 1, 1, 0, "builtin://scale_major_p1"),
            CatalogEntry("Ejercicios", "Escala menor natural (patrón 1)", 80, 1, 1, 0, "builtin://scale_minor_p1"),
            CatalogEntry("Ejercicios", "Escala pentatónica menor", 90, 1, 1, 0, "builtin://scale_penta_minor"),
            CatalogEntry("Ejercicios", "Escala pentatónica mayor", 90, 1, 1, 0, "builtin://scale_penta_major"),
            CatalogEntry("Ejercicios", "Escala blues", 85, 1, 1, 0, "builtin://scale_blues"),
            CatalogEntry("Ejercicios", "Fingerpicking patrón Travis", 75, 1, 1, 0, "builtin://fingerpick_travis"),
            CatalogEntry("Ejercicios", "Fingerpicking clásico", 60, 1, 1, 0, "builtin://fingerpick_classic"),
            CatalogEntry("Ejercicios", "Power chords progresión", 110, 1, 1, 0, "builtin://power_chords"),
            CatalogEntry("Ejercicios", "Bending y vibrato", 80, 1, 1, 0, "builtin://bending_vibrato"),
            CatalogEntry("Ejercicios", "Slide exercise", 90, 1, 1, 0, "builtin://slide_exercise"),
            CatalogEntry("Ejercicios", "Palm mute gallop", 120, 1, 1, 0, "builtin://palm_mute_gallop"),
        )
    }

    private fun generateExerciseJson(exerciseId: String): String {
        val measures = when (exerciseId) {
            "chromatic_1234" -> buildChromatic(listOf(1,2,3,4))
            "chromatic_4321" -> buildChromatic(listOf(4,3,2,1))
            "chromatic_1324" -> buildChromatic(listOf(1,3,2,4))
            "alt_picking_basic" -> buildAlternatePicking()
            "hammer_pulloff" -> buildHammerPulloff()
            "string_skipping" -> buildStringSkipping()
            "arpeggio_major" -> buildArpeggio(listOf(0,4,7))
            "arpeggio_minor" -> buildArpeggio(listOf(0,3,7))
            "spider" -> buildSpider()
            "scale_major_p1" -> buildScale(listOf(0,2,4,5,7,9,11,12))
            "scale_minor_p1" -> buildScale(listOf(0,2,3,5,7,8,10,12))
            "scale_penta_minor" -> buildScale(listOf(0,3,5,7,10,12))
            "scale_penta_major" -> buildScale(listOf(0,2,4,7,9,12))
            "scale_blues" -> buildScale(listOf(0,3,5,6,7,10,12))
            "fingerpick_travis" -> buildFingerpickTravis()
            "fingerpick_classic" -> buildFingerpickClassic()
            "power_chords" -> buildPowerChords()
            "bending_vibrato" -> buildBendingVibrato()
            "slide_exercise" -> buildSlideExercise()
            "palm_mute_gallop" -> buildPalmMuteGallop()
            else -> "[]"
        }
        return """{"title":"Ejercicio","artist":"Ejercicios","tempo":80,"tracks":[{"name":"Guitar","type":"guitar","tuning":[64,59,55,50,45,40],"notes":0,"measures":$measures}]}"""
    }

    private fun buildChromatic(pattern: List<Int>): String {
        val measures = mutableListOf<String>()
        for (s in 5 downTo 0) {
            val beats = pattern.map { f -> """ {"d":4,"n":[{"s":$s,"f":$f}]}""" }
            measures.add("[${beats.joinToString(",")}]")
        }
        for (s in 0..5) {
            val beats = pattern.reversed().map { f -> """ {"d":4,"n":[{"s":$s,"f":$f}]}""" }
            measures.add("[${beats.joinToString(",")}]")
        }
        return "[${measures.joinToString(",")}]"
    }

    private fun buildAlternatePicking(): String {
        val measures = mutableListOf<String>()
        for (s in 5 downTo 0) {
            val beats = (1..8).map { f -> """ {"d":8,"n":[{"s":$s,"f":${if(f%2==1) 0 else 2}}]}""" }
            measures.add("[${beats.joinToString(",")}]")
        }
        return "[${measures.joinToString(",")}]"
    }

    private fun buildHammerPulloff(): String {
        val measures = mutableListOf<String>()
        for (s in 5 downTo 3) {
            val beats = listOf(
                """{"d":4,"n":[{"s":$s,"f":0}]}""",
                """{"d":4,"n":[{"s":$s,"f":2,"fx":["hammer"]}]}""",
                """{"d":4,"n":[{"s":$s,"f":2}]}""",
                """{"d":4,"n":[{"s":$s,"f":0,"fx":["pulloff"]}]}"""
            )
            measures.add("[${beats.joinToString(",")}]")
        }
        return "[${measures.joinToString(",")}]"
    }

    private fun buildStringSkipping(): String {
        val pairs = listOf(5 to 3, 4 to 2, 3 to 1, 4 to 2, 5 to 3)
        val measures = pairs.map { (s1, s2) ->
            val beats = listOf(
                """{"d":4,"n":[{"s":$s1,"f":0}]}""", """{"d":4,"n":[{"s":$s2,"f":0}]}""",
                """{"d":4,"n":[{"s":$s1,"f":2}]}""", """{"d":4,"n":[{"s":$s2,"f":2}]}"""
            )
            "[${beats.joinToString(",")}]"
        }
        return "[${measures.joinToString(",")}]"
    }

    private fun buildArpeggio(intervals: List<Int>): String {
        val frets = intervals.map { it % 12 }
        val beats = frets.map { f -> """{"d":4,"n":[{"s":4,"f":$f}]}""" }
        return "[" + (1..4).joinToString(",") { "[${beats.joinToString(",")}]" } + "]"
    }

    private fun buildSpider(): String {
        val measures = mutableListOf<String>()
        for (s in 5 downTo 0) {
            val beats = listOf(1,2,3,4).map { f -> """{"d":4,"n":[{"s":$s,"f":$f}]}""" }
            measures.add("[${beats.joinToString(",")}]")
        }
        for (s in 5 downTo 0) {
            val beats = listOf(2,3,4,5).map { f -> """{"d":4,"n":[{"s":$s,"f":$f}]}""" }
            measures.add("[${beats.joinToString(",")}]")
        }
        return "[${measures.joinToString(",")}]"
    }

    private fun buildScale(intervals: List<Int>): String {
        val notes = intervals.map { it + 5 }
        val beats = notes.map { f -> """{"d":4,"n":[{"s":5,"f":$f}]}""" }
        val beatsDown = notes.reversed().map { f -> """{"d":4,"n":[{"s":5,"f":$f}]}""" }
        val m1 = "[${beats.take(4).joinToString(",")}]"
        val m2 = "[${beats.drop(4).joinToString(",")}]"
        val m3 = "[${beatsDown.take(4).joinToString(",")}]"
        val m4 = "[${beatsDown.drop(4).joinToString(",")}]"
        return "[$m1,$m2,$m3,$m4]"
    }

    private fun buildFingerpickTravis(): String {
        val beats = listOf(
            """{"d":8,"n":[{"s":4,"f":3}]}""", """{"d":8,"n":[{"s":1,"f":0}]}""",
            """{"d":8,"n":[{"s":3,"f":2}]}""", """{"d":8,"n":[{"s":1,"f":0}]}""",
            """{"d":8,"n":[{"s":4,"f":3}]}""", """{"d":8,"n":[{"s":2,"f":0}]}""",
            """{"d":8,"n":[{"s":3,"f":2}]}""", """{"d":8,"n":[{"s":1,"f":0}]}"""
        )
        return "[" + (1..4).joinToString(",") { "[${beats.joinToString(",")}]" } + "]"
    }

    private fun buildFingerpickClassic(): String {
        val beats = listOf(
            """{"d":8,"n":[{"s":5,"f":0}]}""", """{"d":8,"n":[{"s":2,"f":1}]}""",
            """{"d":8,"n":[{"s":1,"f":0}]}""", """{"d":8,"n":[{"s":2,"f":1}]}""",
            """{"d":8,"n":[{"s":4,"f":2}]}""", """{"d":8,"n":[{"s":2,"f":1}]}""",
            """{"d":8,"n":[{"s":1,"f":0}]}""", """{"d":8,"n":[{"s":2,"f":1}]}"""
        )
        return "[" + (1..4).joinToString(",") { "[${beats.joinToString(",")}]" } + "]"
    }

    private fun buildPowerChords(): String {
        val chords = listOf(0,5,7,5)
        val measures = chords.map { root ->
            val beats = (1..4).map { """{"d":4,"n":[{"s":5,"f":$root},{"s":4,"f":${root+2}}]}""" }
            "[${beats.joinToString(",")}]"
        }
        return "[${measures.joinToString(",")}]"
    }

    private fun buildBendingVibrato(): String {
        val beats = listOf(
            """{"d":2,"n":[{"s":2,"f":7,"fx":["bend"]}]}""",
            """{"d":2,"n":[{"s":2,"f":5,"fx":["vibrato"]}]}""",
            """{"d":2,"n":[{"s":1,"f":8,"fx":["bend"]}]}""",
            """{"d":2,"n":[{"s":1,"f":5,"fx":["vibrato"]}]}"""
        )
        return "[" + (1..3).joinToString(",") { "[${beats.joinToString(",")}]" } + "]"
    }

    private fun buildSlideExercise(): String {
        val beats = listOf(
            """{"d":4,"n":[{"s":3,"f":5,"fx":["slide"]}]}""",
            """{"d":4,"n":[{"s":3,"f":7}]}""",
            """{"d":4,"n":[{"s":3,"f":7,"fx":["slide"]}]}""",
            """{"d":4,"n":[{"s":3,"f":9}]}"""
        )
        return "[" + (1..4).joinToString(",") { "[${beats.joinToString(",")}]" } + "]"
    }

    private fun buildPalmMuteGallop(): String {
        val beats = listOf(
            """{"d":8,"n":[{"s":5,"f":0,"fx":["palm_mute"]}]}""",
            """{"d":16,"n":[{"s":5,"f":0,"fx":["palm_mute"]}]}""",
            """{"d":16,"n":[{"s":5,"f":0,"fx":["palm_mute"]}]}""",
            """{"d":8,"n":[{"s":5,"f":0,"fx":["palm_mute"]}]}""",
            """{"d":16,"n":[{"s":5,"f":0,"fx":["palm_mute"]}]}""",
            """{"d":16,"n":[{"s":5,"f":0,"fx":["palm_mute"]}]}"""
        )
        return "[" + (1..4).joinToString(",") { "[${beats.joinToString(",")}]" } + "]"
    }

    suspend fun loadCatalog(context: Context) {
        if (catalogLoaded) return
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                val entries = builtInExercises().toMutableList()
                // Load user tabs from index
                loadUserTabsIndex(context)
                userTabs.forEach { ut ->
                    entries.add(CatalogEntry(
                        artist = "Mis tabs",
                        song = ut.displayName,
                        tempo = 120,
                        tracks = 1,
                        guitarTracks = 1,
                        bassTracks = 0,
                        path = "user://${ut.id}",
                        isUserTab = true
                    ))
                }
                catalog = entries.sortedBy { it.artist.lowercase() }
                allArtists = catalog.map { it.artist }.distinct().sorted()
                catalogLoaded = true
            } catch (e: Throwable) {
                android.util.Log.e("TabData", "Error loading catalog", e)
                loadError = "Error cargando catálogo: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    fun importUserTab(context: Context, fileName: String, data: ByteArray): UserTabFile {
        val tabsDir = File(context.filesDir, USER_TABS_DIR)
        tabsDir.mkdirs()
        val ext = fileName.substringAfterLast(".", "txt").lowercase()
        val id = "tab_${System.currentTimeMillis()}_${(0..9999).random()}"
        val destFile = File(tabsDir, "$id.$ext")
        destFile.writeBytes(data)
        val displayName = fileName.substringBeforeLast(".")
        val userTab = UserTabFile(
            id = id,
            fileName = fileName,
            displayName = displayName,
            format = ext,
            importedAt = System.currentTimeMillis()
        )
        userTabs = userTabs + userTab
        saveUserTabsIndex(context)
        // Reload catalog
        catalogLoaded = false
        return userTab
    }

    fun deleteUserTab(context: Context, tabId: String) {
        val tab = userTabs.find { it.id == tabId } ?: return
        val tabsDir = File(context.filesDir, USER_TABS_DIR)
        val file = File(tabsDir, "${tab.id}.${tab.format}")
        file.delete()
        userTabs = userTabs.filter { it.id != tabId }
        saveUserTabsIndex(context)
        catalogLoaded = false
    }

    private fun loadUserTabsIndex(context: Context) {
        try {
            val indexFile = File(context.filesDir, USER_TABS_INDEX)
            if (!indexFile.exists()) { userTabs = emptyList(); return }
            val json = JSONObject(indexFile.readText())
            val arr = json.getJSONArray("tabs")
            val tabs = mutableListOf<UserTabFile>()
            for (i in 0 until arr.length()) {
                val t = arr.getJSONObject(i)
                tabs.add(UserTabFile(
                    id = t.getString("id"),
                    fileName = t.getString("fileName"),
                    displayName = t.getString("displayName"),
                    format = t.getString("format"),
                    importedAt = t.optLong("importedAt", 0)
                ))
            }
            userTabs = tabs
        } catch (e: Exception) {
            android.util.Log.e("TabData", "Error loading user tabs index", e)
            userTabs = emptyList()
        }
    }

    private fun saveUserTabsIndex(context: Context) {
        try {
            val json = JSONObject()
            val arr = org.json.JSONArray()
            userTabs.forEach { t ->
                val obj = JSONObject()
                obj.put("id", t.id)
                obj.put("fileName", t.fileName)
                obj.put("displayName", t.displayName)
                obj.put("format", t.format)
                obj.put("importedAt", t.importedAt)
                arr.put(obj)
            }
            json.put("tabs", arr)
            File(context.filesDir, USER_TABS_INDEX).writeText(json.toString())
        } catch (e: Exception) {
            android.util.Log.e("TabData", "Error saving user tabs index", e)
        }
    }

    suspend fun downloadSong(context: Context, entry: CatalogEntry): TabSong? {
        return withContext(Dispatchers.IO) {
            try {
                if (entry.path.startsWith("builtin://")) {
                    val exerciseId = entry.path.removePrefix("builtin://")
                    val jsonStr = generateExerciseJson(exerciseId)
                    return@withContext parseSongJson(jsonStr)
                }
                if (entry.path.startsWith("user://")) {
                    // User tabs are raw files - show info only
                    val tabId = entry.path.removePrefix("user://")
                    val tab = userTabs.find { it.id == tabId }
                    if (tab != null) {
                        val tabsDir = File(context.filesDir, USER_TABS_DIR)
                        val file = File(tabsDir, "${tab.id}.${tab.format}")
                        if (file.exists() && tab.format in listOf("json")) {
                            return@withContext parseSongJson(file.readText())
                        }
                        // For non-json formats return a placeholder
                        return@withContext TabSong(
                            title = tab.displayName,
                            artist = "Mis tabs",
                            tempo = 120,
                            tracks = listOf(TabTrack(
                                name = "${tab.fileName} (${tab.format.uppercase()})",
                                type = "guitar",
                                tuning = listOf(64, 59, 55, 50, 45, 40),
                                totalNotes = 0,
                                measures = listOf(listOf(TabBeat(duration = 1, isRest = true)))
                            ))
                        )
                    }
                    return@withContext null
                }
                null
            } catch (e: Exception) {
                android.util.Log.e("TabData", "Error loading tab", e)
                null
            }
        }
    }

    private fun parseSongJson(jsonStr: String): TabSong {
        val json = JSONObject(jsonStr)
        val tracks = mutableListOf<TabTrack>()
        val tracksArr = json.getJSONArray("tracks")

        for (t in 0 until tracksArr.length()) {
            val trackJson = tracksArr.getJSONObject(t)
            val measuresArr = trackJson.getJSONArray("measures")
            val measures = mutableListOf<List<TabBeat>>()

            for (m in 0 until measuresArr.length()) {
                val beatsArr = measuresArr.getJSONArray(m)
                val beats = mutableListOf<TabBeat>()
                for (b in 0 until beatsArr.length()) {
                    val beatJson = beatsArr.getJSONObject(b)
                    val notesArr = beatJson.optJSONArray("n")
                    val notes = mutableListOf<TabNote>()
                    if (notesArr != null) {
                        for (n in 0 until notesArr.length()) {
                            val noteJson = notesArr.getJSONObject(n)
                            val fxArr = noteJson.optJSONArray("fx")
                            val fx = mutableListOf<String>()
                            if (fxArr != null) {
                                for (f in 0 until fxArr.length()) fx.add(fxArr.getString(f))
                            }
                            notes.add(TabNote(
                                string = noteJson.getInt("s"),
                                fret = noteJson.getInt("f"),
                                velocity = noteJson.optInt("v", 100),
                                effects = fx
                            ))
                        }
                    }
                    beats.add(TabBeat(
                        duration = beatJson.getInt("d"),
                        isDotted = beatJson.optBoolean("dot", false),
                        isRest = notesArr == null || notesArr.length() == 0,
                        notes = notes
                    ))
                }
                measures.add(beats)
            }

            val tuningArr = trackJson.optJSONArray("tuning")
            val tuning = if (tuningArr != null) {
                (0 until tuningArr.length()).map { tuningArr.getInt(it) }
            } else {
                listOf(64, 59, 55, 50, 45, 40)
            }

            tracks.add(TabTrack(
                name = trackJson.optString("name", "Track ${t + 1}"),
                type = trackJson.optString("type", "other"),
                tuning = tuning,
                totalNotes = trackJson.optInt("notes", 0),
                measures = measures
            ))
        }

        return TabSong(
            title = json.optString("title", ""),
            artist = json.optString("artist", ""),
            tempo = json.optInt("tempo", 120),
            tracks = tracks
        )
    }
}
