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
    val path: String
) {
    val otherTracks: Int get() = (tracks - guitarTracks - bassTracks).coerceAtLeast(0)
}

object TabRepository {
    private const val REPO_BASE = "https://raw.githubusercontent.com/Caminerin/guitar-tabs-library/main/"
    private const val CATALOG_URL = "${REPO_BASE}catalog.json"
    private const val CONNECT_TIMEOUT = 15_000
    private const val READ_TIMEOUT = 30_000

    @Volatile private var catalog: List<CatalogEntry> = emptyList()
    @Volatile private var allArtists: List<String> = emptyList()
    @Volatile private var catalogLoaded = false
    @Volatile var loadError: String? = null
        private set

    fun isLoaded() = catalogLoaded
    fun getCatalog() = catalog
    fun getArtists() = allArtists

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

    private fun encodePath(path: String): String {
        return path.split("/").joinToString("/") { segment ->
            URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }
    }

    private fun fetchUrl(urlStr: String): String {
        val conn = URL(urlStr).openConnection() as HttpURLConnection
        conn.connectTimeout = CONNECT_TIMEOUT
        conn.readTimeout = READ_TIMEOUT
        conn.requestMethod = "GET"
        try {
            if (conn.responseCode != 200) {
                throw Exception("HTTP ${conn.responseCode}")
            }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun normalizeTitle(title: String): String {
        return title.lowercase()
            .replace(Regex("\\(.*?\\)"), "")
            .replace(Regex("\\[.*?]"), "")
            .replace(Regex("[^a-z0-9]"), "")
            .trim()
    }

    private fun deduplicateCatalog(entries: List<CatalogEntry>): List<CatalogEntry> {
        val grouped = entries.groupBy {
            "${it.artist.lowercase().trim()}|||${normalizeTitle(it.song)}"
        }
        return grouped.values.map { versions ->
            versions.sortedByDescending { it.guitarTracks + it.bassTracks }.first()
        }
    }

    suspend fun loadCatalog(context: Context) {
        if (catalogLoaded) return
        loadError = null
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "tabs_catalog.json")
                val jsonStr = if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 86400000) {
                    cacheFile.readText()
                } else {
                    val text = fetchUrl(CATALOG_URL)
                    cacheFile.writeText(text)
                    text
                }
                val json = JSONObject(jsonStr)
                val songs = json.getJSONArray("songs")
                val rawEntries = mutableListOf<CatalogEntry>()
                for (i in 0 until songs.length()) {
                    val s = songs.getJSONObject(i)
                    rawEntries.add(CatalogEntry(
                        artist = s.getString("artist"),
                        song = s.getString("song"),
                        tempo = s.optInt("tempo", 120),
                        tracks = s.optInt("tracks", 1),
                        guitarTracks = s.optInt("guitar_tracks", 1),
                        bassTracks = s.optInt("bass_tracks", 0),
                        path = s.getString("path")
                    ))
                }
                // Test entry: JSON generated directly from user's .gp3 file
                rawEntries.add(0, CatalogEntry(
                    artist = "Metallica",
                    song = "Nothing Else Matters (prueba JSON)",
                    tempo = 74,
                    tracks = 10,
                    guitarTracks = 6,
                    bassTracks = 1,
                    path = "test/Metallica/Nothing Else Matters (prueba JSON).json"
                ))
                catalog = deduplicateCatalog(rawEntries).sortedBy { it.artist.lowercase() }
                allArtists = catalog.map { it.artist }.distinct().sorted()
                catalogLoaded = true
            } catch (e: Throwable) {
                android.util.Log.e("TabData", "Error loading catalog", e)
                loadError = "Error cargando catálogo: ${e.javaClass.simpleName}: ${e.message}"
            }
        }
    }

    suspend fun downloadSong(context: Context, entry: CatalogEntry): TabSong? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "tabs/${entry.path}")
                val cacheValid = cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 3600000
                val jsonStr = if (cacheValid) {
                    cacheFile.readText()
                } else {
                    val url = REPO_BASE + encodePath(entry.path)
                    val text = fetchUrl(url)
                    cacheFile.parentFile?.mkdirs()
                    cacheFile.writeText(text)
                    text
                }
                parseSongJson(jsonStr)
            } catch (e: Exception) {
                android.util.Log.e("TabData", "Error downloading song", e)
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
