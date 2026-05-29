package com.caminerin.guitartrainer.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URL

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
    fun guitarTracks(): List<TabTrack> = tracks.filter { it.type == "guitar" }
    fun bassTracks(): List<TabTrack> = tracks.filter { it.type == "bass" }
    fun playableTracks(): List<TabTrack> = tracks.filter { it.type in listOf("guitar", "bass") }
}

data class CatalogEntry(
    val artist: String,
    val song: String,
    val tempo: Int,
    val tracks: Int,
    val guitarTracks: Int,
    val path: String
)

object TabRepository {
    private const val REPO_BASE = "https://raw.githubusercontent.com/Caminerin/guitar-tabs-library/main/"
    private const val CATALOG_URL = "${REPO_BASE}catalog.json"

    @Volatile private var catalog: List<CatalogEntry> = emptyList()
    @Volatile private var allArtists: List<String> = emptyList()
    @Volatile private var catalogLoaded = false

    fun isLoaded() = catalogLoaded
    fun getCatalog() = catalog
    fun getArtists() = allArtists

    fun filter(searchQuery: String = "", artist: String? = null): List<CatalogEntry> {
        return catalog.filter { entry ->
            (artist == null || entry.artist.equals(artist, ignoreCase = true)) &&
            (searchQuery.isBlank() ||
                entry.song.contains(searchQuery, ignoreCase = true) ||
                entry.artist.contains(searchQuery, ignoreCase = true))
        }
    }

    suspend fun loadCatalog(context: Context) {
        if (catalogLoaded) return
        withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "tabs_catalog.json")
                val jsonStr = if (cacheFile.exists() && System.currentTimeMillis() - cacheFile.lastModified() < 86400000) {
                    cacheFile.readText()
                } else {
                    val text = URL(CATALOG_URL).readText()
                    cacheFile.writeText(text)
                    text
                }
                val json = JSONObject(jsonStr)
                val songs = json.getJSONArray("songs")
                val entries = mutableListOf<CatalogEntry>()
                for (i in 0 until songs.length()) {
                    val s = songs.getJSONObject(i)
                    entries.add(CatalogEntry(
                        artist = s.getString("artist"),
                        song = s.getString("song"),
                        tempo = s.optInt("tempo", 120),
                        tracks = s.optInt("tracks", 1),
                        guitarTracks = s.optInt("guitar_tracks", 1),
                        path = s.getString("path")
                    ))
                }
                catalog = entries
                allArtists = entries.map { it.artist }.distinct().sorted()
                catalogLoaded = true
            } catch (e: Exception) {
                android.util.Log.e("TabData", "Error loading catalog", e)
            }
        }
    }

    suspend fun downloadSong(context: Context, entry: CatalogEntry): TabSong? {
        return withContext(Dispatchers.IO) {
            try {
                val cacheFile = File(context.cacheDir, "tabs/${entry.path}")
                val jsonStr = if (cacheFile.exists()) {
                    cacheFile.readText()
                } else {
                    val url = REPO_BASE + entry.path
                    val text = URL(url).readText()
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
                        isRest = beatJson.optBoolean("r", false),
                        notes = notes
                    ))
                }
                measures.add(beats)
            }

            tracks.add(TabTrack(
                name = trackJson.getString("name"),
                type = trackJson.optString("type", "other"),
                tuning = (0 until trackJson.getJSONArray("tuning").length()).map {
                    trackJson.getJSONArray("tuning").getInt(it)
                },
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
