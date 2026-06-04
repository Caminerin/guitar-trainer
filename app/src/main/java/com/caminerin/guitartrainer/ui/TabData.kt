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
    val isUserTab: Boolean = false,
    val category: String = "",
    val subcategory: String = "",
    val level: String = "",
    val technique: String = "",
    val style: String = ""
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
    private const val LIBRARY_BASE_URL = "https://raw.githubusercontent.com/Caminerin/guitar-tabs-library/main/"

    @Volatile private var catalog: List<CatalogEntry> = emptyList()
    @Volatile private var allArtists: List<String> = emptyList()
    @Volatile private var allCategories: List<String> = emptyList()
    @Volatile private var allLevels: List<String> = emptyList()
    @Volatile private var catalogLoaded = false
    @Volatile var loadError: String? = null
        private set

    @Volatile private var userTabs: List<UserTabFile> = emptyList()

    fun isLoaded() = catalogLoaded
    fun getCatalog() = catalog
    fun getArtists() = allArtists
    fun getCategories() = allCategories
    fun getLevels() = allLevels
    fun getUserTabs() = userTabs

    fun reset() {
        catalog = emptyList()
        allArtists = emptyList()
        allCategories = emptyList()
        allLevels = emptyList()
        catalogLoaded = false
        loadError = null
    }

    fun filter(
        searchQuery: String = "",
        artist: String? = null,
        bpmMin: Int? = null,
        bpmMax: Int? = null,
        category: String? = null,
        level: String? = null
    ): List<CatalogEntry> {
        return catalog.filter { entry ->
            (artist == null || entry.artist.equals(artist, ignoreCase = true)) &&
            (bpmMin == null || entry.tempo >= bpmMin) &&
            (bpmMax == null || entry.tempo <= bpmMax) &&
            (category == null || entry.category.equals(category, ignoreCase = true)) &&
            (level == null || entry.level.equals(level, ignoreCase = true)) &&
            (searchQuery.isBlank() ||
                entry.song.contains(searchQuery, ignoreCase = true) ||
                entry.artist.contains(searchQuery, ignoreCase = true) ||
                entry.category.contains(searchQuery, ignoreCase = true) ||
                entry.subcategory.contains(searchQuery, ignoreCase = true))
        }
    }

    private fun loadExercisesFromAssets(context: Context): List<CatalogEntry> {
        return try {
            val indexJson = context.assets.open("exercises/index.json").bufferedReader().readText()
            val arr = org.json.JSONArray(indexJson)
            (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                CatalogEntry(
                    artist = obj.optString("subcategory", ""),
                    song = obj.getString("title"),
                    tempo = obj.optInt("tempo", 80),
                    tracks = 1,
                    guitarTracks = 1,
                    bassTracks = 0,
                    path = "exercise://${obj.getString("file")}",
                    category = obj.optString("category", ""),
                    subcategory = obj.optString("subcategory", ""),
                    level = obj.optString("level", ""),
                    technique = obj.optString("technique", ""),
                    style = obj.optString("style", "")
                )
            }
        } catch (e: Exception) {
            android.util.Log.w("TabData", "Error loading exercises index", e)
            emptyList()
        }
    }

    private fun libraryCatalog(): List<CatalogEntry> {
        return listOf(
            CatalogEntry("Agustín Barrios", "Abri la Puerta Mi China", 96, 2, 2, 0, "A/Agustin Barrios/Abri La Puerta Mi China.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Armonias de America", 112, 2, 1, 1, "A/Agustin Barrios/Armonias De America.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Barcarole", 77, 3, 3, 0, "A/Agustin Barrios/Barcarole.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Barrios Mangore Leyenda Espana", 100, 3, 1, 2, "A/Agustin Barrios/Leyenda España.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Barrios Mangore Minueto la", 120, 2, 1, 1, "A/Agustin Barrios/Minueto En La.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Caazapa", 78, 3, 1, 2, "A/Agustin Barrios/Caazapa.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Cathedral (3) Allegro", 95, 1, 1, 0, "A/Agustin Barrios/La Cathedral.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Choro Da Saudade", 57, 3, 1, 2, "A/Agustin Barrios/Choro Da Saudade.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Confesion", 90, 3, 1, 2, "A/Agustin Barrios/Confesion.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Contemplacion (vals Et Tremolo)", 140, 1, 1, 0, "A/Agustin Barrios/Contemplacion (Vals et Tremolo).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Danza Paraguaya", 100, 1, 1, 0, "A/Agustin Barrios/Danza paraguaya.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "El Ultimo Tremolo", 150, 1, 1, 0, "A/Agustin Barrios/El Ultimo Tremolo.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Estudio de Concierto", 120, 1, 1, 0, "A/Augustin Barrios Mangore/Estudio De Concierto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Julia Florida - Barcarola", 86, 1, 1, 0, "A/Augustin Barrios Mangore/Julia Florida - Barcarola.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "La Catedral", 92, 1, 1, 0, "A/Agustin Barrios/La Catedral.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "La Catedral (allegro)", 120, 1, 1, 0, "A/Agustin Barrios/La Catedral (Allegro).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "La Catedral - Allegro Solemne", 120, 2, 1, 1, "A/Agustin Barrios/La Catedral - Allegro Solemne.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Maxixe", 100, 1, 1, 0, "A/Agustin Barrios/Maxixe.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Prélude", 60, 1, 1, 0, "A/Agustin Barrios/Prélude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Prélude op. 5, No. 1", 85, 1, 1, 0, "A/Agustin Barrios/Prelude Op 5 No 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Sueno en la Floresta by Agustin Barrios Mangore", 102, 1, 1, 0, "A/Agustin Barrios/Sueno en la Floresta.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "The Bees (las Abejas)", 140, 1, 1, 0, "A/Agustin Barrios/The Bees (Las Abejas).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "The Bees (speed Metal Version)", 110, 5, 2, 3, "A/Augustin Barrios Mangore/The Bees.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Vals No. 3 op. 8", 195, 1, 1, 0, "A/Agustin Barrios/Waltz No.3 Op.8.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Agustín Barrios", "Waltz op 8 nr 4", 220, 1, 1, 0, "A/Agustin Barrios/Waltz opus 8 number 4.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Albert Cano", "Estudio Cano", 130, 1, 1, 0, "A/Albert Cano/Estudio Cano.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Alonso Mudarra", "Fantasia", 358, 1, 1, 0, "A/Alonso Mudarra/Fantasia.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Alonso Mudarra", "Fantasía Que Contrahaze el Harpa en la Manera de Ludovico", 140, 1, 1, 0, "A/Alonso Mudarra/Fantasía Que Contrahaze El Harpa En La Manera De Ludovico.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Andres Segovia", "Allemande - Cello Suite No. 3", 110, 1, 1, 0, "A/Andres Segovia/Allemande - Cello Suite No. 3 (bwv 1009).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Andres Segovia", "Estudio Remembranza", 155, 1, 1, 0, "A/Andres Segovia/Estudio Remembranza.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Alecrim", 120, 1, 1, 0, "A/Anonymous/Alecrim.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Caballeria Song", 120, 2, 1, 1, "A/Anonymous/Caballeria Song.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Cancao", 120, 3, 1, 2, "A/Anonymous/Cancao.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Czech Medley", 100, 1, 1, 0, "A/Anonymous/Czech Medley.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Duelling Banjos", 130, 2, 1, 1, "A/Anonymous/Duelling banjos.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Flamenco Study", 95, 1, 1, 0, "A/Anonymous/Flamenco Study.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Greensleeves", 70, 2, 1, 1, "A/Anonymous/Greensleeves.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "If Your Happy and You Know It", 120, 1, 1, 0, "A/Anonymous/If Your Happy and You Know It.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Lamento Di Tristan", 100, 1, 1, 0, "A/Anonymous/Lamento di Tristan.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Le Petit Train D'interlude", 120, 4, 3, 1, "A/Anonymous/Le Petit Train d'Interlude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Mi Favorita", 140, 1, 1, 0, "A/Anonymous/Mi Favorita.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Minuet in G", 120, 2, 1, 1, "A/Anonymous/Baroque Minuet In G.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Nihavend Longa", 140, 1, 1, 0, "A/Anonymous/Nihavend Longa.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Packington's Pound", 120, 1, 1, 0, "A/Anonymous/Packington_'s Pound.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Packington´s Pound", 120, 1, 1, 0, "A/Anonymous/Packingtonns Pound.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Romance", 145, 1, 1, 0, "A/Anonymous/Romance.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Romanza - Anonimo, Spanish Ballad", 102, 1, 1, 0, "A/Anonymous/Romance (Romanza).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Saltarello", 110, 1, 1, 0, "A/Anonymous/Saltarello.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Spanish Ballad", 100, 1, 1, 0, "A/Anonymous/spanish ballad.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Spanish Study", 200, 1, 1, 0, "A/Anonymous/Spanish Study.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Tarrantella", 180, 1, 1, 0, "A/Anonymous/Tarrantella.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Turkey in the Straw", 126, 2, 1, 1, "A/Anonymous/Turkey in the Straw.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Anonymous", "Valse en Sol", 100, 1, 1, 0, "A/Anonymous/Valse In SOL.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonin Dvorak", "Humoresque", 120, 1, 1, 0, "A/Antonin Dvorak/Humoresque.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonin Dvorak", "O Sanctissima", 110, 4, 1, 3, "A/Antonin Dvorak/O Sanctissima.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "4 Seasons - Winter - 3rd Mov.", 160, 2, 1, 1, "A/Antonio Vivaldi/Winter.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Alegro op 4", 116, 5, 1, 4, "A/Antonio Vivaldi/Alegro op 4.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Autumn", 100, 4, 1, 3, "A/Antonio Vivaldi/Autumn.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Concert in D for Guitar", 120, 1, 1, 0, "A/Antonio Vivaldi/Consert in D for Guitar.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Concerto Baroque", 85, 1, 1, 0, "A/Antonio Vivaldi/Concerto Baroque.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Concerto en Sol", 112, 2, 2, 0, "A/Antonio Vivaldi/Concerto En Sol (1er Mouvement).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Concerto en Ut Pour Mandoline", 80, 1, 1, 0, "A/Antonio Vivaldi/Concerto En Ut Pour Mandoline.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "El Choclo", 88, 1, 1, 0, "A/Antonio Vivaldi/El Chocle.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Hiver Part Ii", 35, 3, 1, 2, "A/Antonio Vivaldi/Hiver Part II (Largo).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "La Primavera (allegro 1)", 91, 8, 1, 7, "A/Antonio Vivaldi/La Primavera (Allegro 1).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "La Primavera (guint'e la Primavera)", 104, 3, 1, 2, "A/Antonio Vivaldi/La Primavera (Guint_'e la Primavera).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Largo From Concerto in D", 50, 1, 1, 0, "A/Antonio Vivaldi/Largo from Concerto in D.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Sonata de Violin no 2", 90, 1, 1, 0, "A/Antonio Vivaldi/Sonata de Violín 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Spring 1-3 Parts", 150, 5, 4, 1, "A/Antonio Vivaldi/Spring.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "String Quartet in Gmin", 126, 4, 1, 3, "A/Antonio Vivaldi/String Quartet in Gmin.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Summer", 132, 5, 1, 4, "A/Antonio Vivaldi/Summer.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Summer - Presto", 180, 1, 1, 0, "A/Antonio Vivaldi/Summer - Presto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "The Four Seasons - Summer", 120, 1, 1, 0, "A/Antonio Vivaldi/The Four Seasons - Summer Theme.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Antonio Vivaldi", "Vivaldi Four Seasons", 180, 1, 1, 0, "A/Antonio Vivaldi/The Four Seasons.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "4ème Symphonie en B Majeur", 150, 13, 1, 12, "B/Beethoven/4th Symphony In B Major.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "5th Simphony", 200, 18, 1, 17, "B/Beethoven/5th Simphony.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Andante", 65, 4, 1, 3, "B/Beethoven/Andante (Acoustic).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Chanson de Chepard", 120, 4, 1, 3, "B/Beethoven/Shepard_'s Song - Symphonie Pastorale.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Fifth Symphony", 140, 5, 2, 3, "B/Beethoven/Fifth Symphony.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Fur Elise", 62, 1, 1, 0, "B/Beethoven/Fur Elise full version.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Fur Elise (bagatelle in a Minor)", 120, 1, 1, 0, "B/Beethoven/Fur Elise (Bagatelle in A minor).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Moonlight Sonata", 48, 2, 1, 1, "B/Beethoven/Moonlight Sonata (Movement 1).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Moonlight Sonata (third Movement)", 190, 2, 1, 1, "B/Beethoven/Moonlight Sonata (Third Movement).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Ode to Joy", 200, 8, 1, 7, "B/Beethoven/Ode to Joy.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Osudová", 160, 9, 1, 8, "B/Beethoven/Pátá symfonie-Osudová.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Pathetique Sonata 2nd Movement", 55, 1, 1, 0, "B/Beethoven/Pathetique Sonata 2nd movement.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Rage Over a Lost Penny", 120, 2, 2, 0, "B/Beethoven/Rage Over A Lost Penny.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Rondo in C", 97, 2, 2, 0, "B/Beethoven/Rondo In C.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Sinfonia 9 (rock-ballad)", 120, 4, 1, 3, "B/Beethoven/Sinfonia 9 (Rock-Ballad).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Sonata 0p27", 379, 2, 1, 1, "B/Beethoven/Sonata 0p27.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Sonata N. 6 in Fa Maggiore - Presto", 165, 2, 2, 0, "B/Beethoven/Sonata N. 6 En Fa Maggiore - Presto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Sonata Pathetique", 90, 2, 1, 1, "B/Beethoven/Sonata Pathetique 2nd Move.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Sonata Quasi Una Fantasia (moonlight) op. 27, No. 2", 176, 1, 1, 0, "B/Beethoven/Moonlight Sonata  Op. 27 No. 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Sonate Pathetique", 109, 2, 2, 0, "B/Beethoven/Sonata Pathetique.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Symphony No. 5 in Cm, 1st Movement", 120, 12, 1, 11, "B/Beethoven/Symphony No.5 in Cm 1st movement.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Symphony No7 Allegretto", 50, 3, 3, 0, "B/Beethoven/Symphony No7 allegretto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Beethoven", "Violin Concerto", 100, 13, 1, 12, "B/Beethoven/Violin Concerto_ 1st Movement.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Claude Debussy", "Clair de Lune", 90, 2, 2, 0, "C/Claude Debussy/Clair de Lune.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Daniel Fortea", "Estudio", 60, 1, 1, 0, "D/Daniel Fortea/Estudio.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Daniel Fortea", "Mi Favourita", 120, 1, 1, 0, "D/Daniel Fortea/Mi Favourita.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Andante", 120, 1, 1, 0, "D/Dionso Aguado/Andante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Andante nº 18", 160, 1, 1, 0, "D/Dionso Aguado/Andante nº 18.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Brillante", 65, 1, 1, 0, "D/Dionso Aguado/Brilliante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Estudio", 120, 1, 1, 0, "D/Dionso Aguado/Estudio.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Etüde in A-moll", 120, 1, 1, 0, "D/Dionso Aguado/Etüde in A-Moll.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Minuet", 100, 1, 1, 0, "D/Dionso Aguado/Minuet.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Moderato", 200, 1, 1, 0, "D/Dionso Aguado/Moderato.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Rondo", 94, 1, 1, 0, "D/Dionso Aguado/Rondo.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Study in a Minor", 100, 1, 1, 0, "D/Dionso Aguado/Study in A minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Study in Andante", 120, 1, 1, 0, "D/Dionso Aguado/Study in Andante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Two Pieces in G", 100, 1, 1, 0, "D/Dionso Aguado/Two Pieces in G.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Wals", 80, 1, 1, 0, "D/Dionso Aguado/Wals.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Dionisio Aguado", "Walzer", 115, 1, 1, 0, "D/Dionso Aguado/Walzer.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Edvard Grieg", "Atmosphère Matinale", 63, 3, 1, 2, "G/Grieg/Atmosphère Matinale.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Edvard Grieg", "Dans L'antre Du Roi de la Montagne", 300, 5, 1, 4, "G/Grieg/Dans l'Antre du Roi de la Montagne.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Edvard Grieg", "Danse D'anitra", 160, 2, 1, 1, "G/Grieg/Danse d_'Anitra.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Edvard Grieg", "En la Mansión del Rey de la Montaña", 120, 9, 6, 3, "G/Grieg/En la Mansion del Rey de la Montaña.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Edvard Grieg", "Solveig Song", 400, 2, 1, 1, "G/Grieg/Solveig Song.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Edvard Grieg", "The Hall of the Mountain King (black Metal Version)", 151, 3, 1, 2, "G/Grieg/The Hall of the Mountain King (Black Metal version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Edward Elgar", "Pomp and Circumstance", 90, 1, 1, 0, "E/Edward Elgar/Pomp And Circumstance.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Enrique Granados", "Dedicatoria", 75, 1, 1, 0, "E/Enrique Granados/Dedicatoria.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Enrique Granados", "La Maja de Goya (tonadilla no 7)", 85, 1, 1, 0, "E/Enrique Granados/La Maja De Goya (Tonadilla Number 7).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Enrique Granados", "Oriental", 90, 1, 1, 0, "E/Enrique Granados/Spanish Dance No 2. (Oriental).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Enrique Granados", "Orientale", 104, 2, 1, 1, "E/Enrique Granados/Orientale danse n 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Enrique Granados", "Spanish Dance No. 2", 91, 2, 2, 0, "E/Enrique Granados/Spanish Dance No. 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Enrique Granados", "Spanish Dance No. 5 (andalusa)", 67, 1, 1, 0, "E/Enrique Granados/Spanish Dance No 5 (Andalusa).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Erik Satie", "Gnossienne No. 2", 90, 1, 1, 0, "E/Erik Satie/Gnossienne No. 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Erik Satie", "Gnossienne No. 3", 85, 1, 1, 0, "E/Erik Satie/Gnossienne No. 3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Erik Satie", "Gymnopedie 2", 91, 2, 1, 1, "E/Erik Satie/Gymnopedie 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Erik Satie", "Gymnopedie No. 1", 88, 1, 1, 0, "E/Erik Satie/Gymnopedie No. 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Erik Satie", "Gymnopedie N° 1", 89, 2, 1, 1, "E/Erik Satie/Gymnopedie 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Allegretto", 120, 1, 1, 0, "F/Ferdinando Carulli/Allegretto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Allegretto nº 15", 120, 1, 1, 0, "F/Ferdinando Carulli/Allegretto nº 15.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Andante", 112, 1, 1, 0, "F/Ferdinando Carulli/Andante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Andantino", 52, 1, 1, 0, "F/Ferdinando Carulli/Andantino.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Andantino #4", 120, 1, 1, 0, "F/Ferdinando Carulli/Andantino 4.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Andantino (siciliana), Exercice", 120, 1, 1, 0, "F/Ferdinando Carulli/Andantino (Siciliana).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Appendice No. 1", 120, 1, 1, 0, "F/Ferdinando Carulli/Opus 241 Appendice No1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Bagatelle", 100, 1, 1, 0, "F/Ferdinando Carulli/Bagatelle.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Contredanse", 100, 2, 1, 1, "F/Ferdinando Carulli/Contredanse.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Duo in G-dur", 144, 2, 1, 1, "F/Ferdinando Carulli/Duet in G durian.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Estudio en a Menor", 130, 1, 1, 0, "F/Ferdinando Carulli/Estudio En A Menor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Etude", 120, 1, 1, 0, "F/Ferdinando Carulli/Etude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "No 2", 100, 1, 1, 0, "F/Ferdinando Carulli/Opus 241 Appendice No2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Overture", 168, 1, 1, 0, "F/Ferdinando Carulli/Overture.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Preludio N°1", 116, 1, 1, 0, "F/Ferdinando Carulli/Capriccio N1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Romanza", 70, 1, 1, 0, "F/Ferdinando Carulli/Romanza.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Sicilienne", 78, 1, 1, 0, "F/Ferdinando Carulli/Sicilienne.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Walse", 120, 1, 1, 0, "F/Ferdinando Carulli/Walse.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Ferdinando Carulli", "Waltz", 126, 1, 1, 0, "F/Ferdinando Carulli/Warltz.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Adagio", 115, 1, 1, 0, "F/Fernando Sor/Adagio.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Andante", 92, 2, 1, 1, "F/Fernando Sor/Andante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Andante Op. 44 No1", 96, 1, 1, 0, "F/Fernando Sor/Andante (Op.44).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Andantino", 58, 1, 1, 0, "F/Fernando Sor/andantino.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Andantino Op. 60", 110, 1, 1, 0, "F/Fernando Sor/Andantino (op.60).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Bolero", 83, 1, 1, 0, "F/Fernando Sor/Bolero.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio 1", 120, 1, 1, 0, "F/Fernando Sor/Estudio 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio 2", 80, 1, 1, 0, "F/Fernando Sor/Estudio 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio Ii", 91, 1, 1, 0, "F/Fernando Sor/Estudio No. 2 (op. 35 No. 13).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio Iii", 112, 1, 1, 0, "F/Fernando Sor/Estudio 3 (Op. 6 No. 2).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio Iv", 154, 1, 1, 0, "F/Fernando Sor/Estudio No. IV.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio No. 10", 70, 1, 1, 0, "F/Fernando Sor/Estudio No. 10.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio No. 11", 45, 1, 1, 0, "F/Fernando Sor/Estudio No. 11.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio No. 15", 126, 1, 1, 0, "F/Fernando Sor/Estudio No. 15.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio No. 3", 120, 1, 1, 0, "F/Fernando Sor/Estudio No. 3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio nº 12 en Sol M", 120, 1, 1, 0, "F/Fernando Sor/Estudio nº 12 en Sol M.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudio V", 110, 1, 1, 0, "F/Fernando Sor/Estudio No. V.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Estudo Em Si Menor", 140, 1, 1, 0, "F/Fernando Sor/Estudio em Si menor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Etude de Fernando Sor", 104, 1, 1, 0, "F/Fernando Sor/Etude in D.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Etude en Mi Majeur", 117, 1, 1, 0, "F/Fernando Sor/Etude en Mi Majeur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Etude No. 13", 30, 1, 1, 0, "F/Fernando Sor/Etude No. 13.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Etude No. 9 Opus 35 - Fernando Sor", 120, 1, 1, 0, "F/Fernando Sor/Etude No. 9 Opus 35.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Etude N°6 en Ré Majeur", 120, 1, 1, 0, "F/Fernando Sor/Etude N6 En Re Majeur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Etude N°9, Opus31 N°20", 132, 1, 1, 0, "F/Fernando Sor/Etude No 9 Opus 31.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Etude Xvi, Opus 29 #23", 80, 1, 1, 0, "F/Fernando Sor/Etude 16 No. 23.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Grand Sonata: Minuet and Trio (iii)", 152, 1, 1, 0, "F/Fernando Sor/Grand Sonata.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Moderato for 1st Through 6th Position", 130, 1, 1, 0, "F/Fernando Sor/Moderato dalla I alla VI Posizione.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Op. 31 - No. 1 (study in C Major)", 100, 1, 1, 0, "F/Fernando Sor/Op. 31 - No 1 (Study In C Major).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Op. 44 - no 22 (study in a Minor)", 60, 1, 1, 0, "F/Fernando Sor/Op. 44 - No 22 (study In A Minor).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Op. 60 - no 1 (study in C Major)", 100, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 1 (study In C Major).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Op. 60 - no 5 (study in a Minor)", 66, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 5 (study In A Minor).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Op. 60 - No. 2 (study in C Major)", 104, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 2 (study In C Major).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Op. 60 - No. 6 (study in a Minor)", 63, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 6 (study In A Minor).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Sonate in C (op.15b)", 128, 1, 1, 0, "F/Fernando Sor/Sonate In C (Op.15B).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Study in Bm", 117, 1, 1, 0, "F/Fernando Sor/Study in Bm.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Study in C", 80, 1, 1, 0, "F/Fernando Sor/Study in C.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Study in D Minor (poco Allegretto)", 72, 1, 1, 0, "F/Fernando Sor/Study In D Minor (poco Allegretto).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Study No. 16", 80, 1, 1, 0, "F/Fernando Sor/Study No. 16.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Study No. 18", 70, 1, 1, 0, "F/Fernando Sor/Estudio No. 18.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Study No. 19 - Op. 29, No. 13", 35, 1, 1, 0, "F/Fernando Sor/Study No. 19.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Tarantella", 150, 2, 1, 1, "F/Fernando Sor/Tarantella.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Variations / Over a Theme From the Magic Flute by Mozart", 60, 1, 1, 0, "F/Fernando Sor/Variations from the Magic Flute.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Variations in Tremolo", 46, 1, 1, 0, "F/Fernando Sor/Variations in Tremolo From Op.21.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Fernando Sor", "Walzer Nr.1", 120, 2, 1, 1, "F/Fernando Sor/Nr.1 (Six Valses).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Folk", "Learn Rythm Feu de Camp Guitar", 74, 2, 1, 1, "F/Folk/Learn Rythm Feu De Camp Guitar.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Adagio in a", 110, 1, 1, 0, "F/Francisco Tarrega/Adagio in A.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Adelita", 96, 1, 1, 0, "F/Francisco Tarrega/Adelita.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Cajita de Musica (music Box)", 76, 1, 1, 0, "F/Francisco Tarrega/Cajita De Musica (Music Box).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Capricho Arabe", 80, 1, 1, 0, "F/Francisco Tarrega/Capricho Arabe.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "D+i Sa(n", 145, 1, 1, 0, "F/Francisco Tarrega/Di San.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Danza Mora", 100, 1, 1, 0, "F/Francisco Tarrega/Danza Mora.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Eleven Teaching Preludes", 100, 1, 1, 0, "F/Francisco Tarrega/11 Teaching Preludes.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Endecha - Oremus", 75, 1, 1, 0, "F/Francisco Tarrega/Endecha - Oremus (Preludios).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Estudio Brilliante", 69, 1, 1, 0, "F/Francisco Tarrega/Estudio Brilliante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Estudio de Velocidad", 90, 1, 1, 0, "F/Francisco Tarrega/Estudio De Velocidad.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Estudio Sobre Un Tema de Mendelssohn", 120, 1, 1, 0, "F/Francisco Tarrega/Estudio Sobre Un Fragmento De Mendelssohn.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Estudo Em Dó Maior", 100, 1, 1, 0, "F/Francisco Tarrega/Estudo Em Dó Maior.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Etude in C", 72, 1, 1, 0, "F/Francisco Tarrega/Etude In C.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Etude in e Minor", 72, 2, 1, 1, "F/Francisco Tarrega/Etude In E Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Etude-scherzo", 116, 1, 1, 0, "F/Francisco Tarrega/Etude-Scherzo.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Etude-sonatine", 80, 1, 1, 0, "F/Francisco Tarrega/Etude - Sonatine.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Gran Vals", 205, 1, 1, 0, "F/Francisco Tarrega/Gran Vals.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Gran Vals en la (grand Waltz in A)", 150, 1, 1, 0, "F/Francisco Tarrega/Gran Vals en La (Grand Waltz in A).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "La Paloma", 71, 1, 1, 0, "F/Francisco Tarrega/La Paloma.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Lagrima", 80, 1, 1, 0, "F/Francisco Tarrega/Lagrima Teardrops.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Maria", 89, 1, 1, 0, "F/Francisco Tarrega/Maria (Gavotte).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Marieta", 94, 1, 1, 0, "F/Francisco Tarrega/Marieta.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Mariposa", 120, 1, 1, 0, "F/Francisco Tarrega/Mariposa.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Mazurka", 104, 1, 1, 0, "F/Francisco Tarrega/Mazurka.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Pavana", 100, 1, 1, 0, "F/Francisco Tarrega/Pavana.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Preludio 1", 122, 1, 1, 0, "F/Francisco Tarrega/Preludio 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Preludio nr. 10", 90, 1, 1, 0, "F/Francisco Tarrega/Preludio No. 10 (G Major).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Recuerdos de la Alhambra", 82, 1, 1, 0, "F/Francisco Tarrega/Recuerdos de la Alhambra (No Tremolo).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Recuerdos del de la Alhambra (palacio)", 79, 2, 1, 1, "F/Francisco Tarrega/Recuerdos de la Alhambra (Arrange).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Rosita", 112, 1, 1, 0, "F/Francisco Tarrega/Rosita.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Scherzo en la Majeur", 75, 1, 1, 0, "F/Francisco Tarrega/Scherzo en La Majeur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Study in a", 120, 1, 1, 0, "F/Francisco Tarrega/Study in A.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Study in D", 120, 1, 1, 0, "F/Francisco Tarrega/Study in D.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Study in G", 90, 1, 1, 0, "F/Francisco Tarrega/Study in G.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Study nº 10", 130, 1, 1, 0, "F/Francisco Tarrega/Study nº 10.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Sueno", 100, 1, 1, 0, "F/Francisco Tarrega/Sueno.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Tango", 67, 1, 1, 0, "F/Francisco Tarrega/Tango.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "The Carnival of Venice", 100, 1, 1, 0, "F/Francisco Tarrega/Carnival of Venice.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Francisco Tárrega", "Valse", 140, 1, 1, 0, "F/Francisco Tarrega/Valse.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Andante Con Moto", 80, 4, 1, 3, "F/Franz Schubert/Andante Con Moto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Ave Maria", 100, 4, 3, 1, "F/Franz Schubert/Ave Maria.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Das Fischermädchen", 120, 2, 1, 1, "F/Franz Schubert/Das Fischermädchen.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Momento Musicale Op. 94 No. 2", 108, 1, 1, 0, "F/Franz Schubert/Momento Musicale Op.94 No.2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Serenade", 54, 1, 1, 0, "F/Franz Schubert/Serenade.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Serenade (trio)", 64, 3, 1, 2, "F/Franz Schubert/Serenade (trio).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Standchen", 64, 2, 1, 1, "F/Franz Schubert/Standchen.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Franz Schubert", "Tränenregen", 50, 2, 1, 1, "F/Franz Schubert/Traenenregen.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Ballade no 1 in G Minor, op. 23", 60, 1, 1, 0, "F/Frederic Chopin/Ballade No 1 In G Minor Op. 23.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Etude No. 2, Opus 25", 110, 2, 1, 1, "F/Frederic Chopin/Etude No. 2 Opus 25.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Fantaisie Impromptu Op66", 140, 1, 1, 0, "F/Frederic Chopin/Fantaisie Impromptu Op66.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Fantaisie Impromptue Opus 66 (c# Mineur)", 180, 2, 1, 1, "F/Frederic Chopin/Fantaisie impromptu in C sharp minor op.66.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Mazurek Op68 No3", 132, 2, 2, 0, "F/Frederic Chopin/Mazurek Op68 No3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Mazurka Op52 No2", 100, 2, 2, 0, "F/Frederic Chopin/Mazurka Op52 No2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Nocturne in C-sharp Minor", 72, 2, 1, 1, "F/Frederic Chopin/Nocturne in C-Sharp Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Prelude No. 15 in D Flat Major 'raindrop Prelude'", 75, 2, 1, 1, "F/Frederic Chopin/Prelude No.15 In D Flat Major (Raindrop Prelude).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Prelude No. 4", 66, 4, 4, 0, "F/Frederic Chopin/Prelude No.4.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Prelude No. 6, Opus 28", 50, 2, 1, 1, "F/Frederic Chopin/Prelude  No. 6 Opus 28.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Prelude No20", 32, 3, 3, 0, "F/Frederic Chopin/prelude No20.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Prelude Opus 28 No. 4", 58, 2, 2, 0, "F/Frederic Chopin/Opus 28 No.3 - Prelude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Preludio N. 1 in Do Maggiore", 60, 1, 1, 0, "F/Frederic Chopin/Preludio n.1 in Do maggiore.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Preludio N.2 in la Minore", 60, 1, 1, 0, "F/Frederic Chopin/Preludio n.2 in La minore.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Revolutionary Study", 180, 1, 1, 0, "F/Frederic Chopin/Revolutionary Study.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Tristesse", 43, 1, 1, 0, "F/Frederic Chopin/Tristesse.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Valse No6 Op64 No1", 202, 2, 2, 0, "F/Frederic Chopin/Valse No6 Op64 No1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Valse No7 Op64 No2", 109, 1, 1, 0, "F/Frederic Chopin/Valse No. 7 Op64 No. 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Valse Op. 64 No. 1 (petit Chien)", 240, 3, 1, 2, "F/Frederic Chopin/Valse Op.64 No.1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Valse Op34 No2", 100, 2, 2, 0, "F/Frederic Chopin/Valse Op34 No2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Frédéric Chopin", "Valse Op69 No2", 152, 3, 3, 0, "F/Frederic Chopin/Valse Op69 No2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Canarios", 180, 1, 1, 0, "G/Gaspar Sanz/CANARIOS.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Corranda", 144, 1, 1, 0, "G/Gaspar Sanz/Corranda.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "En Ré", 120, 1, 1, 0, "G/Gaspar Sanz/PARADETAS  FROM 5 DANCES.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Espagnoletta", 160, 1, 1, 0, "G/Gaspar Sanz/Espagnoletta.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Españoletas", 120, 1, 1, 0, "G/Gaspar Sanz/Españoletas 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Fanfarra", 140, 1, 1, 0, "G/Gaspar Sanz/Fanfarra (from 5 dances).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Folias", 120, 2, 2, 0, "G/Gaspar Sanz/Folias.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Matachin", 120, 1, 1, 0, "G/Gaspar Sanz/MATACHIN.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Paradetas", 105, 2, 2, 0, "G/Gaspar Sanz/Paradetas.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Pavana", 140, 1, 1, 0, "G/Gaspar Sanz/Pavana (from 5 dances).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Pavanas", 112, 1, 1, 0, "G/Gaspar Sanz/Pavanas.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Rujero", 120, 1, 1, 0, "G/Gaspar Sanz/Rujero  (from 5 dances).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Sarabande", 56, 1, 1, 0, "G/Gaspar Sanz/SARABANDE.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Sesquialtera", 100, 1, 1, 0, "G/Gaspar Sanz/Sesquialtera.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Suite Española: No. 9 Canarios", 210, 1, 1, 0, "G/Gaspar Sanz/Suite Española_ No. 9 Canarios.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Gaspar Sanz", "Ïðåëþäèÿ", 120, 1, 1, 0, "G/Gaspar Sanz/Prelude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("George Frideric Handel", "Concerto en Si Minor", 120, 1, 1, 0, "G/George Frideric Handel/Concerto En Si Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("George Frideric Handel", "Sarabande", 80, 3, 1, 2, "G/George Frideric Handel/Sarabande_MetalVersion.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("George Frideric Handel", "Water Music", 220, 3, 3, 0, "G/George Frideric Handel/Water Music Finale.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Alborada op 71", 89, 2, 2, 0, "I/Isaac Albeniz/Alborada Op 71.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Asturias", 120, 1, 1, 0, "I/Isaac Albeniz/Asturias.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Bajo la Palmera", 100, 2, 2, 0, "I/Isaac Albeniz/Bajo La Palmera.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Cadiz", 100, 1, 1, 0, "I/Isaac Albeniz/Cadiz.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Capricho Catalan", 70, 1, 1, 0, "I/Isaac Albeniz/Capricho Catalan.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Castilla", 100, 2, 2, 0, "I/Isaac Albeniz/Castilla (duet).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Córdoba", 90, 1, 1, 0, "I/Isaac Albeniz/C_rdoba.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "En la Playa (on the Beach).", 88, 2, 1, 1, "I/Isaac Albeniz/En la Playa (On the Beach)..json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Granada", 50, 1, 1, 0, "I/Isaac Albeniz/Granada.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Leyenda (asturias)", 107, 1, 1, 0, "I/Isaac Albeniz/Leyenda.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Rumores de la Caleta", 82, 1, 1, 0, "I/Isaac Albeniz/Rumores De La Caleta (Malaguena - De _'Requerdos De Viaje_').json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Sevilla", 112, 1, 1, 0, "I/Isaac Albeniz/Sevilla.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Spanish Tango, Op. 164 No. 2", 80, 2, 1, 1, "I/Isaac Albeniz/Spanish Tango Op.164 No.2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Isaac Albéniz", "Tango", 70, 1, 1, 0, "I/Isaac Albeniz/Tango (No.2 De La Suite _'Espana_').json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Air", 40, 3, 1, 2, "J/Johann Sebastian Bach/Air.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Air (electic Guitar Version)", 35, 3, 1, 2, "J/Johann Sebastian Bach/Air On G (Electric Guitar Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Air on a G String", 73, 1, 1, 0, "J/Johann Sebastian Bach/Air On a G String.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Air on a G String for a Metal Band", 120, 5, 3, 2, "J/Johann Sebastian Bach/Air on a G string (metal arrangement).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Air on the 4th String (metal Version)", 120, 4, 2, 2, "J/Johann Sebastian Bach/Air on the G string.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Alegretto", 92, 2, 2, 0, "J/Johann Sebastian Bach/Alegretto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Allegro", 100, 2, 2, 0, "J/Johann Sebastian Bach/Allegro (Acoustic Guitar Version ).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Allegro Third Movement", 120, 3, 1, 2, "J/Johann Sebastian Bach/Allegro Third Movement.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Allemande From Partita No. 2 in D Minor", 60, 1, 1, 0, "J/Johann Sebastian Bach/Allemande From Partita No. 2 In D Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Allemande in a Minor", 120, 1, 1, 0, "J/Johann Sebastian Bach/Allemande In A Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Andante", 100, 2, 1, 1, "J/Johann Sebastian Bach/ANDANTE.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Aria", 53, 1, 1, 0, "J/Johann Sebastian Bach/Aria (Goldberg Variations).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Aria From Cantata 41", 100, 4, 4, 0, "J/Johann Sebastian Bach/Aria From Cantata 41.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Ave Maria", 120, 1, 1, 0, "B/Bach/Ave Maria.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Avemaria (acoustic)", 120, 1, 1, 0, "J/Johann Sebastian Bach/Ave Maria (Acoustic).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Ayre", 50, 4, 1, 3, "J/Johann Sebastian Bach/Ayre suite no 3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bach Suite no 1 bwv 1007", 68, 1, 1, 0, "J/Johann Sebastian Bach/Bach Suite N0 1 BWV 1007Doig.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Badinerie", 120, 3, 1, 2, "J/Johann Sebastian Bach/Badinerie.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bouree (from Lute Suite No. 1 in e Minor)", 120, 2, 1, 1, "J/Johann Sebastian Bach/Bouree (From Lute suite no. 1 in E minor).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bouree Cello Suite Iii", 120, 1, 1, 0, "J/Johann Sebastian Bach/Bouree Cello Suite III.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bourre", 120, 1, 1, 0, "J/Johann Sebastian Bach/Bourre.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bourree", 132, 1, 1, 0, "J/Johann Sebastian Bach/Bouree.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bourree (suite Pour Luth en Mi Mineur bwv 996)", 116, 1, 1, 0, "J/Johann Sebastian Bach/Bourree en mi mineur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bwv 515", 140, 2, 1, 1, "J/Johann Sebastian Bach/Bwv 515.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bwv 625", 89, 3, 1, 2, "J/Johann Sebastian Bach/BWV 625.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bwv 996 Suite Prélude/presto", 70, 1, 1, 0, "J/Johann Sebastian Bach/Bwv 996 N1 Suite Prédule Presto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Bwv1007 - Prelude (cello Suite 1)", 140, 1, 1, 0, "J/Johann Sebastian Bach/Bwv1007 - Prelude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "C Minor Prelude", 120, 1, 1, 0, "J/Johann Sebastian Bach/C Minor Prelude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Cantate Ich Ruf", 57, 2, 2, 0, "J/Johann Sebastian Bach/Cantate Ich Ruf.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Chaccone (from Violin Partita No. 2)", 60, 1, 1, 0, "J/Johann Sebastian Bach/Chaccone (From Violin Partita No.2).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Chello 1", 80, 2, 1, 1, "J/Johann Sebastian Bach/Chello 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Chorale", 120, 1, 1, 0, "J/Johann Sebastian Bach/Gieb dass ich thu_' mit Fleiss.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Chromatic Fugue", 100, 3, 3, 0, "J/Johann Sebastian Bach/Chromatic Fugue.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Concerto in a Minor", 100, 1, 1, 0, "J/Johann Sebastian Bach/Concerto In A Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Courante", 150, 1, 1, 0, "J/Johann Sebastian Bach/Courante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Double", 100, 1, 1, 0, "J/Johann Sebastian Bach/Double.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fuga 1 Para 4 Voces", 53, 4, 3, 1, "J/Johann Sebastian Bach/Fuga 1 Para 4 Voces.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fuga in Do Minore", 80, 3, 2, 1, "J/Johann Sebastian Bach/Fuga in do minore.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fuga À Trois", 112, 3, 1, 2, "J/Johann Sebastian Bach/Fuga A 3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fuge (orig.: G-moll)", 90, 1, 1, 0, "J/Johann Sebastian Bach/Fugue (G-Moll) BWV 1000.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fughetta", 100, 2, 2, 0, "J/Johann Sebastian Bach/Fughetta.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fugue", 105, 4, 1, 3, "J/Johann Sebastian Bach/Fugue In D-.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fugue in D Minor", 120, 1, 1, 0, "J/Johann Sebastian Bach/Fugue In D Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Fugue in Gm (little Fugue)", 127, 1, 1, 0, "J/Johann Sebastian Bach/Fugue In Gm (Little Fugue).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gavote", 60, 1, 1, 0, "J/Johann Sebastian Bach/Gavote.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gavotte", 120, 1, 1, 0, "J/Johann Sebastian Bach/BWV1006.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gavotte 1 & 2", 160, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte 1 & 2.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gavotte en Rondeau", 137, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte en Rondeau (from lute suite 4) BWV 1006a.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gavotte en Rondeau (from bwv 995)", 200, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte En Rondeau (From BWV 995).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gavotte in a Minor (j.s. Bach) by Leo V.d..ketterij", 60, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte in A minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gigue", 170, 1, 1, 0, "J/Johann Sebastian Bach/Gigue (Acoustic Guitar Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Gigue (from Lute Suite No. 2)", 104, 1, 1, 0, "J/Johann Sebastian Bach/Gigue (From Lute Suite No. 2).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Inventio 3", 120, 1, 1, 0, "J/Johann Sebastian Bach/Inventio 3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Invention 01", 110, 2, 2, 0, "J/Johann Sebastian Bach/Invention 01.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Invention 13", 100, 2, 1, 1, "J/Johann Sebastian Bach/Invention 13.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Invention 13 in a Minor. B. W. Iii", 80, 2, 1, 1, "J/Johann Sebastian Bach/Invention in Am.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Invention en D Mineur", 60, 2, 2, 0, "J/Johann Sebastian Bach/Invention En D mineur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Invention No. 1", 80, 2, 1, 1, "J/Johann Sebastian Bach/Invention No. 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Invention nº 13", 60, 1, 1, 0, "J/Johann Sebastian Bach/Invention No.13.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Invenzione a Due Voci - for 1st Through 7th Position", 75, 1, 1, 0, "J/Johann Sebastian Bach/Invenzione a Due Voci - For 1st through 7th position..json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Italian Concerto", 127, 2, 1, 1, "J/Johann Sebastian Bach/Italian Concerto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Jesu Joy of Man's Desiring", 100, 1, 1, 0, "J/Johann Sebastian Bach/Jesu Joy Of Man_'s Desiring BWV 147.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Jesu Meine Zuversicht", 128, 1, 1, 0, "J/Johann Sebastian Bach/Jesu meine Zuversicht.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Jesu, Joy of Man's Desiring", 69, 2, 1, 1, "J/Johann Sebastian Bach/Jesu Joy Of Man's Desiring.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Jesus Alegría del Hombre", 110, 1, 1, 0, "B/Bach/Jesus Alegria Del Hombre.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Joke", 120, 1, 1, 0, "J/Johann Sebastian Bach/Joke.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Joke B-moll", 140, 5, 2, 3, "J/Johann Sebastian Bach/Joke B-moll.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Joy of Mans Desiring", 110, 1, 1, 0, "J/Johann Sebastian Bach/Jesu Joy Of Man_'s Desiring.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Jésus Que Ma Joie Demeure", 99, 1, 1, 0, "J/Johann Sebastian Bach/Jesus que ma joie demeure.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Largo e Dolce", 75, 3, 3, 0, "J/Johann Sebastian Bach/Largo e Dolce.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Largo Es-dur", 45, 3, 1, 2, "J/Johann Sebastian Bach/Largo Trio Es-dur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "March", 130, 1, 1, 0, "J/Johann Sebastian Bach/March.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Menuet", 120, 2, 1, 1, "J/Johann Sebastian Bach/Menuet.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Menuet (1ère Suite Violoncelle Ré Mineur)", 98, 1, 1, 0, "J/Johann Sebastian Bach/Menuet 1ère suite violoncelle ré mineur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Menuet in a Min", 120, 1, 1, 0, "J/Johann Sebastian Bach/Menuet In A Min.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Menuet in D Major", 88, 4, 1, 3, "J/Johann Sebastian Bach/Menuet In D Major.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Menuet in G Min", 110, 1, 1, 0, "J/Johann Sebastian Bach/Menuet In G Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Menuett", 146, 1, 1, 0, "J/Johann Sebastian Bach/Menuett.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Menuetti G-duuri", 120, 3, 1, 2, "J/Johann Sebastian Bach/Menuetti.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Minuet #2, Cello Suite #1", 120, 1, 1, 0, "J/Johann Sebastian Bach/Minuet 2 Cellosuite 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Minuet in G", 140, 2, 1, 1, "J/Johann Sebastian Bach/Minuet in G.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Minuet in G (duet)", 120, 2, 1, 1, "J/Johann Sebastian Bach/Minuet In G (Duet).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Minuet in Mi Menor", 180, 2, 1, 1, "J/Johann Sebastian Bach/Minuet in Mi menor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Minueto Em la Menor", 100, 1, 1, 0, "J/Johann Sebastian Bach/Minueto Em La Menor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Minueto Em Sol Maior", 140, 1, 1, 0, "J/Johann Sebastian Bach/Menuet In G Major.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Minueto en G+", 120, 1, 1, 0, "J/Johann Sebastian Bach/Minueto En G Anna Magdelene Bach Notebook.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Musette in D Major", 75, 1, 1, 0, "J/Johann Sebastian Bach/Musette in D Major.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Number 3", 120, 1, 1, 0, "J/Johann Sebastian Bach/Ach wie fluchtig.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Number 5", 120, 1, 1, 0, "J/Johann Sebastian Bach/Jesu nimm dich deiner Glieder.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Number 7", 120, 1, 1, 0, "J/Johann Sebastian Bach/Es ist genug so nimm. Herr meinen Geist.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Partita for Lute: Sarabande", 48, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande - Bwv 997.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Partita No. 1 for Solo Violin: Sarabande (v)", 56, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande - Bwv 1002.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Polonaise", 110, 1, 1, 0, "J/Johann Sebastian Bach/Polonaise.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Praludium", 95, 1, 1, 0, "J/Johann Sebastian Bach/Praludium 4.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude", 120, 2, 1, 1, "J/Johann Sebastian Bach/Prelude (Electric Guitar Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude - From Suite in e", 100, 1, 1, 0, "J/Johann Sebastian Bach/Prelude In E From 4th Lute Suite.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude C Mineur", 160, 3, 1, 2, "J/Johann Sebastian Bach/Prelude C mineur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude From Suite No. 1 for Cello - Bwv1007", 70, 1, 1, 0, "J/Johann Sebastian Bach/Cello Suite 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude in C Minor", 160, 1, 1, 0, "J/Johann Sebastian Bach/Prelude In C Minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude in Cm", 120, 2, 1, 1, "J/Johann Sebastian Bach/Prelude in Cm.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude in D", 90, 1, 1, 0, "J/Johann Sebastian Bach/Prelude In D.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude in Dminor", 120, 1, 1, 0, "J/Johann Sebastian Bach/prelude in D minor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude N. 1", 60, 1, 1, 0, "J/Johann Sebastian Bach/Prelude N 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prelude No. 1 in C Major", 120, 1, 1, 0, "J/Johann Sebastian Bach/Prelude No.1 In C Major.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Preludio in Mi Minore", 144, 2, 2, 0, "J/Johann Sebastian Bach/Preludio in mi minore.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Preludium No. 1", 92, 3, 1, 2, "J/Johann Sebastian Bach/Preludium no.1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prélude en Ré Mineur", 90, 1, 1, 0, "J/Johann Sebastian Bach/Prelude en Re Mineur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prélude N°1", 60, 1, 1, 0, "J/Johann Sebastian Bach/Prlude no1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Prélude N°1 en Do Majeur Du Clavier Bien Tempéré", 70, 1, 1, 0, "J/Johann Sebastian Bach/Prelude N1 En Do Majeur Du Clavier Bien Tempere.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Pélude en C", 80, 1, 1, 0, "J/Johann Sebastian Bach/C Prelude From The Well-Tempered Clavier.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Sarabande", 72, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande from Partita in Bm for Violin.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Scerzzo H-moll From Sonata #4", 100, 3, 1, 2, "J/Johann Sebastian Bach/Badinerie (Joke In H-Moll From Sonata 4) - Rearranged For (Art) Rock Band.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Second Violin Sonata in A-minor. bwv 1003 - Allegro", 160, 1, 1, 0, "J/Johann Sebastian Bach/Second Violin Sonata in a-minor. BWV 1003 - allegro.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Sinfonia#12", 50, 1, 1, 0, "J/Johann Sebastian Bach/Sinfonia 12.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Srabande From Cello Suite I (bwv 1007)", 35, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande (From Cello Suite I BWV 1007).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Suite #1 in D Major", 70, 1, 1, 0, "J/Johann Sebastian Bach/Prelude from Suite 1 in D Major.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Suite de Bach", 120, 3, 1, 2, "J/Johann Sebastian Bach/Suite De Bach (Metal Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Suite de Bach With Heavy Metal Arrangement", 120, 6, 1, 5, "J/Johann Sebastian Bach/Suite De Bach.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Suite for Lute: Prelude (i)", 92, 1, 1, 0, "J/Johann Sebastian Bach/Prelude - Bwv 999.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Suite nr. 1", 120, 2, 1, 1, "J/Johann Sebastian Bach/Préludía from Suit N1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Suiten Für Violoncello N°1", 77, 1, 1, 0, "J/Johann Sebastian Bach/Suiten Fur Violoncello N1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Toccata & Fugue", 30, 1, 1, 0, "J/Johann Sebastian Bach/Toccata And Fugue (Bwv 565).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Toccata and Fugue", 110, 1, 1, 0, "J/Johann Sebastian Bach/Toccata and Fugue.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Toccata e Fugue in Dm bwv 565", 100, 3, 1, 2, "J/Johann Sebastian Bach/Toccata & Fugue In Dm Bwv 565 (Metal Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Toccata Et Fugue en Ré Mineur", 50, 2, 2, 0, "J/Johann Sebastian Bach/Toccata et fugue en ré mineur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Two Part Invention #4", 130, 2, 1, 1, "J/Johann Sebastian Bach/Invention 04.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Two Part Invention #8", 120, 2, 1, 1, "J/Johann Sebastian Bach/Invention 08.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Variation Goldberg Numéro 1", 100, 2, 1, 1, "J/Johann Sebastian Bach/Goldberg Variations 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Variations Goldberg, Variations 29", 69, 2, 1, 1, "J/Johann Sebastian Bach/Golberg Variation 29.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Violin Concerto (2nd Movement)", 80, 5, 4, 1, "J/Johann Sebastian Bach/Violin Concerto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Violin Sonata no 1 in Gm Fugue", 100, 1, 1, 0, "J/Johann Sebastian Bach/Violin Sonata No 1 In Gm  Fugue Bwv 1001.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("J.S. Bach", "Vivace", 91, 1, 1, 0, "J/Johann Sebastian Bach/Vivace.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Johann Pachelbel", "Cannon in C", 120, 1, 1, 0, "J/Johann Pachelbel/Cannon In C.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Johann Pachelbel", "Cannon in D", 120, 3, 1, 2, "J/Johann Pachelbel/Cannon In D.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Johann Pachelbel", "Canon en Ré Majeur", 48, 4, 1, 3, "J/Johann Pachelbel/Canon De Pachelbel.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Johann Pachelbel", "Canon in D", 72, 1, 1, 0, "J/Johann Pachelbel/Canon In D Variation.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Johann Pachelbel", "Canon in D Major (2 Guitars)", 48, 2, 2, 0, "J/Johann Pachelbel/Canon in D Major.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Johann Pachelbel", "Fantasie", 45, 6, 6, 0, "J/Johann Pachelbel/Fantasie (Acoustic).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Johann Pachelbel", "Fugue", 64, 2, 2, 0, "J/Johann Pachelbel/Fugue Duo.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "A Fancy (p-73)", 64, 1, 1, 0, "J/John Dowland/A Fancy P.73.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "A Fancy P-6", 72, 1, 1, 0, "J/John Dowland/A Fancy P-6.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "An Air", 66, 1, 1, 0, "J/John Dowland/An air.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Andante", 70, 1, 1, 0, "J/John Dowland/Andante.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Awake Sweet Love", 112, 1, 1, 0, "J/John Dowland/Awake Sweet Love.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Can She Excuse", 108, 1, 1, 0, "J/John Dowland/Can She Excuse.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Come Again", 100, 1, 1, 0, "J/John Dowland/Come Again.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Fantasia", 100, 1, 1, 0, "J/John Dowland/Fantasia.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Forlorn Hope Fancy", 55, 1, 1, 0, "J/John Dowland/Forlorn Hope Fancy.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Fortune My Foe", 87, 3, 1, 2, "J/John Dowland/Fortune My Foe.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Go From My Window", 72, 1, 1, 0, "J/John Dowland/Go From My Window.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Lachrimae Pavan", 46, 1, 1, 0, "J/John Dowland/Lachrimae Pavan.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Lady Hunsdon's Puffe", 76, 1, 1, 0, "J/John Dowland/Lady Hunsdon's Puffe.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Lady Laiton's Almain", 108, 1, 1, 0, "J/John Dowland/Lady Laiton's Almain.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Melancholy Gaillard", 76, 1, 1, 0, "J/John Dowland/Melancholy Gaillard.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Mistress Winter's Jump", 120, 1, 1, 0, "J/John Dowland/Mistress Winter_'s Jump.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Mrs. Winter`s Jump", 120, 1, 1, 0, "J/John Dowland/Mrs Winter_'s Jump.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Queen Elizabeth's", 88, 1, 1, 0, "J/John Dowland/Queen Elizabeths.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Queen Elizabeth's Galliard", 94, 1, 1, 0, "J/John Dowland/Queen Elizabeth's Galliard.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "Sir John Smith, His Almain", 112, 1, 1, 0, "J/John Dowland/Sir John Smith His Almain.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "The Frog Galliard", 90, 1, 1, 0, "J/John Dowland/The Frog Galliard.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "The Most Sacred Queen Elisabeth, Her Galliard", 95, 1, 1, 0, "J/John Dowland/The Most Sacred Queen Elisabeth Her Galliard.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "The Shoemaker's Wife. a Toy", 76, 1, 1, 0, "J/John Dowland/The Shoemaker_'s Wife A Toy.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "The Sick Tune", 66, 2, 1, 1, "J/John Dowland/The Sick Tune.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("John Dowland", "What If a Day", 82, 1, 1, 0, "J/John Dowland/What If A Day.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Julio Sagreras", "Etude (tremolo)", 79, 1, 1, 0, "J/Julio Sagreras/Etude (tremolo).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Manuel de Falla", "Cancion del Fuego Fatuo", 130, 1, 1, 0, "M/Manuel De Falla/Will O' The Wisp (Cancion Del Fuego Fatuo).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Manuel de Falla", "Miller's Dance", 120, 1, 1, 0, "M/Manuel De Falla/Dance Of The Miller.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Matteo Carcassi", "Etude Opus 60 N°7", 120, 4, 1, 3, "M/Mateo Carcassi/Etude op 60 no 7.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Maurice Ravel", "Bolero", 80, 1, 1, 0, "M/Maurice Ravel/Bolero.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Maurice Ravel", "Le Boléro", 120, 4, 1, 3, "M/Maurice Ravel/Le Bolero.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "32 Easy Pieces for Guitar op. 30", 120, 1, 1, 0, "M/Mauro Giuliani/32 Easy Pieces For Guitar Opus 30.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Allegretto", 70, 1, 1, 0, "M/Mauro Giuliani/Allegretto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Allegretto in C", 116, 1, 1, 0, "M/Mauro Giuliani/Allegretto in C.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Allegro", 120, 5, 2, 3, "M/Mauro Giuliani/Allegro.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Andante in C", 75, 1, 1, 0, "M/Mauro Giuliani/Andante in C.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Caprice", 130, 1, 1, 0, "M/Mauro Giuliani/Caprice.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Dance Rondo", 100, 1, 1, 0, "M/Mauro Giuliani/Dance Rondo.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Etude", 90, 1, 1, 0, "M/Mauro Giuliani/Etude.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Grazioso in G", 120, 1, 1, 0, "M/Mauro Giuliani/Grazioso in G.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Maestoso", 69, 1, 1, 0, "M/Mauro Giuliani/Maestoso Opus 48.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Op. 50 No. 1", 120, 1, 1, 0, "M/Mauro Giuliani/Op. 50 No. 1.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Op. 50 No. 3", 120, 1, 1, 0, "M/Mauro Giuliani/Op. 50 No.3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Opera 100 N. 11", 120, 1, 1, 0, "M/Mauro Giuliani/Opera 100 N. 11.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Opus 48 ¹5", 71, 1, 1, 0, "M/Mauro Giuliani/Opus 48 Ç5.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Study No. 3 / Etude No. 3", 92, 1, 1, 0, "M/Mauro Giuliani/Study No. 3.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "The Last Rose of Summer (traditional Irish Melody)", 69, 1, 1, 0, "M/Mauro Giuliani/The Last Rose Of Summer.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mauro Giuliani", "Tirolienne", 108, 1, 1, 0, "M/Mauro Giuliani/Tirolienne.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "40th Symphony", 110, 3, 1, 2, "M/Mozart/40th Symphony (Metal Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Alla Turca", 200, 3, 3, 0, "M/Mozart/Alla Turca (Acoustic Guitar Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Ave Verum Corpus", 120, 4, 1, 3, "M/Mozart/Ave Verum Corpus.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Bourée", 120, 1, 1, 0, "M/Mozart/Bourée.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Concerto", 69, 3, 1, 2, "M/Mozart/Concerto For Clarinet.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Confutatis (heavy Version)", 60, 2, 1, 1, "M/Mozart/Confutatis (Heavy Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Dies Irae", 150, 7, 3, 4, "M/Mozart/Dies Irae (Metal Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Eine Kleine Nachtmusik", 116, 1, 1, 0, "M/Mozart/Eine Kleine NachtMusik (bass).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Eine Kleine Nachtsmusik", 120, 3, 2, 1, "M/Mozart/Eine Kleine Nachtmusik.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Eine Kliene Nachtmusik (!punk Version!)", 120, 2, 1, 1, "M/Mozart/Eine Kliene Nachtmusik (Punk Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "La Flûte Enchantée", 154, 15, 1, 14, "M/Mozart/La Flûte Enchantée.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Menuett", 100, 2, 2, 0, "M/Mozart/Menuett.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Piano Sonata K.545 1st Movement", 130, 2, 1, 1, "M/Mozart/K.545 1st movement.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Piccola Musica Notturna", 120, 2, 1, 1, "M/Mozart/Piccola Musica Notturna.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Presto", 138, 2, 2, 0, "M/Mozart/Sonata n. 5 in Sol Maggiore - Presto.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Rondo Alla Turca", 120, 2, 1, 1, "M/Mozart/Rondo Alla Torca.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Rondo Alla Turca (electric Guitar Version)", 200, 2, 1, 1, "M/Mozart/Rondo Alla Turca (Electic Guitar Version).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Rondo Alla Turca (tabbed by Vinther)", 132, 3, 3, 0, "M/Mozart/Rondo Alla Turca.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Rondo Alla Turka", 125, 1, 1, 0, "M/Mozart/Rondo Alla Turka.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Rondo Allegro Vivo", 128, 2, 2, 0, "M/Mozart/Rondo Allegro Vivo.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Sinfonia 40", 250, 1, 1, 0, "M/Mozart/Sinfonia 40(1st Mov).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Sinfonia No. 40", 200, 10, 4, 6, "M/Mozart/Sinfonia No. 40.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Sonate", 135, 4, 3, 1, "M/Mozart/Sonate.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Sonate en C", 120, 2, 1, 1, "M/Mozart/Sonate en C Majeur.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Symphonia No. 40", 200, 5, 1, 4, "M/Mozart/Symphonia No. 40.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "The Magic Flute - Der Hölle Rache (night Queen)", 150, 7, 1, 6, "M/Mozart/The Magic Flute - Der Hölle Rache (Night Queen).json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "The Marriage of Figaro", 144, 8, 6, 2, "M/Mozart/The Marriage of Figaro.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Turkish Delight", 120, 2, 1, 1, "M/Mozart/Turkish Delight.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Mozart", "Tyrkisk March", 115, 3, 1, 2, "M/Mozart/Turkish March.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "A Casinha Pequenina", 60, 1, 1, 0, "T/Traditional/A Casinha Pequenina.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "A Londonderry Air", 72, 2, 1, 1, "T/Traditional/A Londonderry Air.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "A Summer Breeze", 140, 1, 1, 0, "T/Traditional/A Summer Breeze.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Alabama Jubilee", 250, 2, 1, 1, "T/Traditional/Alabama Jubilee.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Amazing Grace", 93, 1, 1, 0, "T/Traditional/Amazing Grace.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Auld Lang Syne", 120, 1, 1, 0, "T/Traditional/Auld Lang Syne.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Backwater Blues", 90, 3, 2, 1, "T/Traditional/Backwater Blues.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Blackberry Blossom", 240, 3, 1, 2, "T/Traditional/Blackberry Blossom.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Ca la Breaza", 120, 2, 1, 1, "T/Traditional/Ca la Breaza.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Caffee-kanon", 120, 3, 1, 2, "T/Traditional/Caffee.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Caravan", 120, 6, 2, 4, "T/Traditional/Caravan.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Dixie Hoedown Bluegrass Traditional", 250, 2, 1, 1, "T/Traditional/Dixie Hoedown.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Drunken Sailor", 120, 6, 1, 5, "T/Traditional/Drunken Sailor.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "El Condor Pasa", 78, 2, 1, 1, "T/Traditional/El Condor Pasa.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Figuri", 300, 2, 1, 1, "T/Traditional/Figuri.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Greensleeves", 120, 5, 3, 2, "T/Traditional/Greensleeves.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Indifférence", 200, 2, 1, 1, "T/Traditional/Indifférence.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Irish Washerwoman", 120, 4, 3, 1, "T/Traditional/Irish Washerwoman.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Jesse James", 195, 2, 1, 1, "T/Traditional/Jesse James.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Kuckuck", 88, 3, 1, 2, "T/Traditional/Kuckuck.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "La Cucaracha", 160, 1, 1, 0, "T/Traditional/La cucaracha.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Le Vin", 170, 2, 1, 1, "T/Traditional/Le Vin.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Les Yeux Noirs", 200, 2, 1, 1, "T/Traditional/Les Yeux Noirs.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Little Drummer Boy", 120, 2, 1, 1, "T/Traditional/Little Drummer Boy.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Marsch", 120, 3, 1, 2, "T/Traditional/March.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Nashville Blues", 180, 1, 1, 0, "T/Traditional/Nashville Blues.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Nobody Knows the Trouble I've Seen", 120, 1, 1, 0, "T/Traditional/Nobody Knows The Trouble I've Seen.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Och Jungfrun Hon Gar I Dansen", 126, 2, 1, 1, "T/Traditional/Och jungfrun hon gar i dansen.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Oh When the Saints", 200, 1, 1, 0, "T/Traditional/Oh When The Saints.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Polka Russe", 250, 2, 1, 1, "T/Traditional/Polka Russe.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Scotland the Brave", 200, 2, 2, 0, "T/Traditional/Scotland the brave.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Silent Night", 120, 2, 1, 1, "T/Traditional/Silent night.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Soldier's Joy", 120, 2, 1, 1, "T/Traditional/Soldier's Joy.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Staccato Hora", 160, 2, 2, 0, "T/Traditional/Staccato Hora.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Standard Acoustic Blues", 101, 2, 1, 1, "T/Traditional/Standard acoustic blues.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "The King of the Fairies", 72, 1, 1, 0, "T/Traditional/The King Of The Fairies.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "The Wild Rover", 170, 2, 1, 1, "T/Traditional/The Wild Rover.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Tico Tico", 180, 3, 1, 2, "T/Traditional/Tico Tico.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Two Guitars", 100, 1, 1, 0, "T/Traditional/Two Guitars.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Vidalita", 105, 1, 1, 0, "T/Traditional/Vidalita.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Vom Pastor Seiner Kuh", 80, 3, 1, 2, "T/Traditional/Vom Pastor seiner Kuh.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "When the Saints Go Marching in", 204, 1, 1, 0, "T/Traditional/When The Saints Go Marching In.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Will the Circle Be Unbroken", 190, 2, 1, 1, "T/Traditional/Will the circle be unbroken.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Wilwood Flowers", 120, 1, 1, 0, "T/Traditional/Wilwood Flowers.json", category = "Piezas", level = "Intermedio", style = "clásico"),
            CatalogEntry("Traditional", "Yankee Doodle Dixie", 160, 2, 1, 1, "T/Traditional/Yankee doodle dixie.json", category = "Piezas", level = "Intermedio", style = "clásico")
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
                val entries = (loadExercisesFromAssets(context) + libraryCatalog()).toMutableList()
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
                        isUserTab = true,
                        category = "Mis tabs"
                    ))
                }
                catalog = entries.sortedWith(compareBy({ it.category }, { it.subcategory }, { it.song.lowercase() }))
                allArtists = catalog.map { it.artist }.distinct().sorted()
                allCategories = catalog.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
                allLevels = listOf("Principiante", "Intermedio", "Avanzado")
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
                if (entry.path.startsWith("exercise://")) {
                    val fileName = entry.path.removePrefix("exercise://")
                    val jsonStr = context.assets.open("exercises/$fileName").bufferedReader().readText()
                    return@withContext parseSongJson(jsonStr)
                }
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
                // Library tabs - download from remote
                val encodedPath = entry.path.split("/").joinToString("/") { part ->
                    URLEncoder.encode(part, "UTF-8").replace("+", "%20")
                }
                val url = URL("$LIBRARY_BASE_URL$encodedPath")
                val cacheFile = File(context.cacheDir, "tabs/${entry.path.replace("/", "_")}")
                if (cacheFile.exists()) {
                    return@withContext parseSongJson(cacheFile.readText())
                }
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 8_000
                conn.readTimeout = 10_000
                conn.setRequestProperty("User-Agent", "GuitarTrainer/1.0")
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    conn.disconnect()
                    android.util.Log.w("TabData", "HTTP $responseCode for $url")
                    return@withContext null
                }
                val jsonStr = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                if (jsonStr.isBlank() || !jsonStr.trimStart().startsWith("{")) {
                    android.util.Log.w("TabData", "Invalid JSON response for ${entry.path}")
                    return@withContext null
                }
                cacheFile.parentFile?.mkdirs()
                cacheFile.writeText(jsonStr)
                return@withContext parseSongJson(jsonStr)
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
