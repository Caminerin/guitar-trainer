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
    private const val LIBRARY_BASE_URL = "https://raw.githubusercontent.com/Caminerin/guitar-tabs-library/main/tabs/"

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

    private fun libraryCatalog(): List<CatalogEntry> {
        return listOf(
            CatalogEntry("Agustin Barrios", "Abri la Puerta Mi China", 96, 2, 2, 0, "A/Agustin Barrios/Abri La Puerta Mi China.json"),
            CatalogEntry("Agustin Barrios", "Armonias de America", 112, 2, 1, 1, "A/Agustin Barrios/Armonias De America.json"),
            CatalogEntry("Agustin Barrios", "Barcarole", 77, 3, 3, 0, "A/Agustin Barrios/Barcarole.json"),
            CatalogEntry("Agustin Barrios", "Barrios Mangore Leyenda Espana", 100, 3, 1, 2, "A/Agustin Barrios/Leyenda España.json"),
            CatalogEntry("Agustin Barrios", "Barrios Mangore Minueto la", 120, 2, 1, 1, "A/Agustin Barrios/Minueto En La.json"),
            CatalogEntry("Agustin Barrios", "Caazapa", 78, 3, 1, 2, "A/Agustin Barrios/Caazapa.json"),
            CatalogEntry("Agustin Barrios", "Cathedral (3) Allegro", 95, 1, 1, 0, "A/Agustin Barrios/La Cathedral.json"),
            CatalogEntry("Agustin Barrios", "Choro Da Saudade", 57, 3, 1, 2, "A/Agustin Barrios/Choro Da Saudade.json"),
            CatalogEntry("Agustin Barrios", "Confesion", 90, 3, 1, 2, "A/Agustin Barrios/Confesion.json"),
            CatalogEntry("Agustin Barrios", "Contemplacion (vals Et Tremolo)", 140, 1, 1, 0, "A/Agustin Barrios/Contemplacion (Vals et Tremolo).json"),
            CatalogEntry("Agustin Barrios", "Danza Paraguaya", 100, 1, 1, 0, "A/Agustin Barrios/Danza paraguaya.json"),
            CatalogEntry("Agustin Barrios", "El Ultimo Tremolo", 150, 1, 1, 0, "A/Agustin Barrios/El Ultimo Tremolo.json"),
            CatalogEntry("Agustin Barrios", "La Catedral", 92, 1, 1, 0, "A/Agustin Barrios/La Catedral.json"),
            CatalogEntry("Agustin Barrios", "La Catedral (allegro)", 120, 1, 1, 0, "A/Agustin Barrios/La Catedral (Allegro).json"),
            CatalogEntry("Agustin Barrios", "La Catedral - Allegro Solemne", 120, 2, 1, 1, "A/Agustin Barrios/La Catedral - Allegro Solemne.json"),
            CatalogEntry("Agustin Barrios", "Maxixe", 100, 1, 1, 0, "A/Agustin Barrios/Maxixe.json"),
            CatalogEntry("Agustin Barrios", "Prélude", 60, 1, 1, 0, "A/Agustin Barrios/Prélude.json"),
            CatalogEntry("Agustin Barrios", "Prélude op. 5, No. 1", 85, 1, 1, 0, "A/Agustin Barrios/Prelude Op 5 No 1.json"),
            CatalogEntry("Agustin Barrios", "Sueno en la Floresta by Agustin Barrios Mangore", 102, 1, 1, 0, "A/Agustin Barrios/Sueno en la Floresta.json"),
            CatalogEntry("Agustin Barrios", "The Bees (las Abejas)", 140, 1, 1, 0, "A/Agustin Barrios/The Bees (Las Abejas).json"),
            CatalogEntry("Agustin Barrios", "Vals No. 3 op. 8", 195, 1, 1, 0, "A/Agustin Barrios/Waltz No.3 Op.8.json"),
            CatalogEntry("Agustin Barrios", "Waltz op 8 nr 4", 220, 1, 1, 0, "A/Agustin Barrios/Waltz opus 8 number 4.json"),
            CatalogEntry("Alonso Mudarra", "Fantasia", 358, 1, 1, 0, "A/Alonso Mudarra/Fantasia.json"),
            CatalogEntry("Alonso Mudarra", "Fantasía Que Contrahaze el Harpa en la Manera de Ludovico", 140, 1, 1, 0, "A/Alonso Mudarra/Fantasía Que Contrahaze El Harpa En La Manera De Ludovico.json"),
            CatalogEntry("Andres Segovia", "Allemande - Cello Suite No. 3", 110, 1, 1, 0, "A/Andres Segovia/Allemande - Cello Suite No. 3 (bwv 1009).json"),
            CatalogEntry("Andres Segovia", "Estudio Remembranza", 155, 1, 1, 0, "A/Andres Segovia/Estudio Remembranza.json"),
            CatalogEntry("Antonin Dvorak", "Humoresque", 120, 1, 1, 0, "A/Antonin Dvorak/Humoresque.json"),
            CatalogEntry("Antonin Dvorak", "O Sanctissima", 110, 4, 1, 3, "A/Antonin Dvorak/O Sanctissima.json"),
            CatalogEntry("Antonio Lauro", "Carora - Valse Venezolano", 160, 1, 1, 0, "A/Antonio Lauro/Carora (Valse Venezolano).json"),
            CatalogEntry("Antonio Lauro", "El Marabino", 156, 1, 1, 0, "A/Antonio Lauro/El Marabino.json"),
            CatalogEntry("Antonio Lauro", "El Negrito", 136, 1, 1, 0, "A/Antonio Lauro/El Negrito.json"),
            CatalogEntry("Antonio Lauro", "La Gatica", 155, 1, 1, 0, "A/Antonio Lauro/La Gatica.json"),
            CatalogEntry("Antonio Lauro", "Suite Venezolana: I Registro (preludio)", 80, 1, 1, 0, "A/Antonio Lauro/Registro (Preludio) from Suite Venezolana.json"),
            CatalogEntry("Antonio Lauro", "Vals Venezolano 3", 165, 1, 1, 0, "A/Antonio Lauro/Vals Venezolano no. 3.json"),
            CatalogEntry("Antonio Lauro", "Vals Venezolano No. 2", 145, 1, 1, 0, "A/Antonio Lauro/Andreina - Vals No 2.json"),
            CatalogEntry("Antonio Lauro", "Valse Venezolano No. 3", 144, 1, 1, 0, "A/Antonio Lauro/Valse Venezolano No.3.json"),
            CatalogEntry("Antonio Lauro", "Valse Venezuelienne", 150, 2, 1, 1, "A/Antonio Lauro/Valse Vénézuelienne.json"),
            CatalogEntry("Antonio Lauro", "Valse Vénezuelienne", 112, 2, 1, 1, "A/Antonio Lauro/Valse Vnezuelienne.json"),
            CatalogEntry("Antonio Vivaldi", "4 Seasons - Winter - 3rd Mov.", 160, 2, 1, 1, "A/Antonio Vivaldi/Winter.json"),
            CatalogEntry("Antonio Vivaldi", "Alegro op 4", 116, 5, 1, 4, "A/Antonio Vivaldi/Alegro op 4.json"),
            CatalogEntry("Antonio Vivaldi", "Autumn", 100, 4, 1, 3, "A/Antonio Vivaldi/Autumn.json"),
            CatalogEntry("Antonio Vivaldi", "Concert in D for Guitar", 120, 1, 1, 0, "A/Antonio Vivaldi/Consert in D for Guitar.json"),
            CatalogEntry("Antonio Vivaldi", "Concerto Baroque", 85, 1, 1, 0, "A/Antonio Vivaldi/Concerto Baroque.json"),
            CatalogEntry("Antonio Vivaldi", "Concerto en Sol", 112, 2, 2, 0, "A/Antonio Vivaldi/Concerto En Sol (1er Mouvement).json"),
            CatalogEntry("Antonio Vivaldi", "Concerto en Ut Pour Mandoline", 80, 1, 1, 0, "A/Antonio Vivaldi/Concerto En Ut Pour Mandoline.json"),
            CatalogEntry("Antonio Vivaldi", "El Choclo", 88, 1, 1, 0, "A/Antonio Vivaldi/El Chocle.json"),
            CatalogEntry("Antonio Vivaldi", "Hiver Part Ii", 35, 3, 1, 2, "A/Antonio Vivaldi/Hiver Part II (Largo).json"),
            CatalogEntry("Antonio Vivaldi", "La Primavera (allegro 1)", 91, 8, 1, 7, "A/Antonio Vivaldi/La Primavera (Allegro 1).json"),
            CatalogEntry("Antonio Vivaldi", "La Primavera (guint'e la Primavera)", 104, 3, 1, 2, "A/Antonio Vivaldi/La Primavera (Guint_'e la Primavera).json"),
            CatalogEntry("Antonio Vivaldi", "Largo From Concerto in D", 50, 1, 1, 0, "A/Antonio Vivaldi/Largo from Concerto in D.json"),
            CatalogEntry("Antonio Vivaldi", "Sonata de Violin no 2", 90, 1, 1, 0, "A/Antonio Vivaldi/Sonata de Violín 2.json"),
            CatalogEntry("Antonio Vivaldi", "Spring 1-3 Parts", 150, 5, 4, 1, "A/Antonio Vivaldi/Spring.json"),
            CatalogEntry("Antonio Vivaldi", "String Quartet in Gmin", 126, 4, 1, 3, "A/Antonio Vivaldi/String Quartet in Gmin.json"),
            CatalogEntry("Antonio Vivaldi", "Summer", 132, 5, 1, 4, "A/Antonio Vivaldi/Summer.json"),
            CatalogEntry("Antonio Vivaldi", "Summer - Presto", 180, 1, 1, 0, "A/Antonio Vivaldi/Summer - Presto.json"),
            CatalogEntry("Antonio Vivaldi", "The Four Seasons - Summer", 120, 1, 1, 0, "A/Antonio Vivaldi/The Four Seasons - Summer Theme.json"),
            CatalogEntry("Antonio Vivaldi", "Vivaldi Four Seasons", 180, 1, 1, 0, "A/Antonio Vivaldi/The Four Seasons.json"),
            CatalogEntry("Augustin Barrios Mangore", "Choro Da Saudade", 57, 1, 1, 0, "A/Augustin Barrios Mangore/Choro Da Saudade.json"),
            CatalogEntry("Augustin Barrios Mangore", "Estudio de Concierto", 120, 1, 1, 0, "A/Augustin Barrios Mangore/Estudio De Concierto.json"),
            CatalogEntry("Augustin Barrios Mangore", "Julia Florida - Barcarola", 86, 1, 1, 0, "A/Augustin Barrios Mangore/Julia Florida - Barcarola.json"),
            CatalogEntry("Augustin Barrios Mangore", "The Bees (speed Metal Version)", 110, 5, 2, 3, "A/Augustin Barrios Mangore/The Bees.json"),
            CatalogEntry("Bach", "Ave Maria", 120, 1, 1, 0, "B/Bach/Ave Maria.json"),
            CatalogEntry("Bach", "Jesus Alegría del Hombre", 110, 1, 1, 0, "B/Bach/Jesus Alegria Del Hombre.json"),
            CatalogEntry("Beethoven", "4ème Symphonie en B Majeur", 150, 13, 1, 12, "B/Beethoven/4th Symphony In B Major.json"),
            CatalogEntry("Beethoven", "5th Simphony", 200, 18, 1, 17, "B/Beethoven/5th Simphony.json"),
            CatalogEntry("Beethoven", "Andante", 65, 4, 1, 3, "B/Beethoven/Andante (Acoustic).json"),
            CatalogEntry("Beethoven", "Chanson de Chepard", 120, 4, 1, 3, "B/Beethoven/Shepard_'s Song - Symphonie Pastorale.json"),
            CatalogEntry("Beethoven", "Fifth Symphony", 140, 5, 2, 3, "B/Beethoven/Fifth Symphony.json"),
            CatalogEntry("Beethoven", "Fur Elise", 62, 1, 1, 0, "B/Beethoven/Fur Elise full version.json"),
            CatalogEntry("Beethoven", "Fur Elise (bagatelle in a Minor)", 120, 1, 1, 0, "B/Beethoven/Fur Elise (Bagatelle in A minor).json"),
            CatalogEntry("Beethoven", "Moonlight Sonata", 48, 2, 1, 1, "B/Beethoven/Moonlight Sonata (Movement 1).json"),
            CatalogEntry("Beethoven", "Moonlight Sonata (third Movement)", 190, 2, 1, 1, "B/Beethoven/Moonlight Sonata (Third Movement).json"),
            CatalogEntry("Beethoven", "Moonlight Sonata (third Movement) (metal Version)", 190, 3, 1, 2, "B/Beethoven/Moonlight Sonata (Metal Version).json"),
            CatalogEntry("Beethoven", "Ode to Joy", 200, 8, 1, 7, "B/Beethoven/Ode to Joy.json"),
            CatalogEntry("Beethoven", "Osudová", 160, 9, 1, 8, "B/Beethoven/Pátá symfonie-Osudová.json"),
            CatalogEntry("Beethoven", "Pathetique Sonata 2nd Movement", 55, 1, 1, 0, "B/Beethoven/Pathetique Sonata 2nd movement.json"),
            CatalogEntry("Beethoven", "Piano Sonata #29 (\"hammerklavier\")", 76, 1, 1, 0, "B/Beethoven/Piano Sonata 29 (Hammerklavier).json"),
            CatalogEntry("Beethoven", "Rage Over a Lost Penny", 120, 2, 2, 0, "B/Beethoven/Rage Over A Lost Penny.json"),
            CatalogEntry("Beethoven", "Rondo in C", 97, 2, 2, 0, "B/Beethoven/Rondo In C.json"),
            CatalogEntry("Beethoven", "Sinfonia 9 (rock-ballad)", 120, 4, 1, 3, "B/Beethoven/Sinfonia 9 (Rock-Ballad).json"),
            CatalogEntry("Beethoven", "Sonata 0p27", 379, 2, 1, 1, "B/Beethoven/Sonata 0p27.json"),
            CatalogEntry("Beethoven", "Sonata N. 6 in Fa Maggiore - Presto", 165, 2, 2, 0, "B/Beethoven/Sonata N. 6 En Fa Maggiore - Presto.json"),
            CatalogEntry("Beethoven", "Sonata Pathetique", 90, 2, 1, 1, "B/Beethoven/Sonata Pathetique 2nd Move.json"),
            CatalogEntry("Beethoven", "Sonata Quasi Una Fantasia (moonlight) op. 27, No. 2", 176, 1, 1, 0, "B/Beethoven/Moonlight Sonata  Op. 27 No. 2.json"),
            CatalogEntry("Beethoven", "Sonate Pathetique", 109, 2, 2, 0, "B/Beethoven/Sonata Pathetique.json"),
            CatalogEntry("Beethoven", "Symphony No. 5 in Cm, 1st Movement", 120, 12, 1, 11, "B/Beethoven/Symphony No.5 in Cm 1st movement.json"),
            CatalogEntry("Beethoven", "Symphony No7 Allegretto", 50, 3, 3, 0, "B/Beethoven/Symphony No7 allegretto.json"),
            CatalogEntry("Beethoven", "Violin Concerto", 100, 13, 1, 12, "B/Beethoven/Violin Concerto_ 1st Movement.json"),
            CatalogEntry("Claude Debussy", "Clair de Lune", 90, 2, 2, 0, "C/Claude Debussy/Clair de Lune.json"),
            CatalogEntry("Daniel Fortea", "Estudio", 60, 1, 1, 0, "D/Daniel Fortea/Estudio.json"),
            CatalogEntry("Daniel Fortea", "Mi Favourita", 120, 1, 1, 0, "D/Daniel Fortea/Mi Favourita.json"),
            CatalogEntry("Dionso Aguado", "Andante", 120, 1, 1, 0, "D/Dionso Aguado/Andante.json"),
            CatalogEntry("Dionso Aguado", "Andante nº 18", 160, 1, 1, 0, "D/Dionso Aguado/Andante nº 18.json"),
            CatalogEntry("Dionso Aguado", "Brillante", 65, 1, 1, 0, "D/Dionso Aguado/Brilliante.json"),
            CatalogEntry("Dionso Aguado", "Estudio", 120, 1, 1, 0, "D/Dionso Aguado/Estudio.json"),
            CatalogEntry("Dionso Aguado", "Etüde in A-moll", 120, 1, 1, 0, "D/Dionso Aguado/Etüde in A-Moll.json"),
            CatalogEntry("Dionso Aguado", "Minuet", 100, 1, 1, 0, "D/Dionso Aguado/Minuet.json"),
            CatalogEntry("Dionso Aguado", "Moderato", 200, 1, 1, 0, "D/Dionso Aguado/Moderato.json"),
            CatalogEntry("Dionso Aguado", "Rondo", 94, 1, 1, 0, "D/Dionso Aguado/Rondo.json"),
            CatalogEntry("Dionso Aguado", "Study in a Minor", 100, 1, 1, 0, "D/Dionso Aguado/Study in A minor.json"),
            CatalogEntry("Dionso Aguado", "Study in Andante", 120, 1, 1, 0, "D/Dionso Aguado/Study in Andante.json"),
            CatalogEntry("Dionso Aguado", "Two Pieces in G", 100, 1, 1, 0, "D/Dionso Aguado/Two Pieces in G.json"),
            CatalogEntry("Dionso Aguado", "Wals", 80, 1, 1, 0, "D/Dionso Aguado/Wals.json"),
            CatalogEntry("Dionso Aguado", "Walzer", 115, 1, 1, 0, "D/Dionso Aguado/Walzer.json"),
            CatalogEntry("Edward Elgar", "Pomp and Circumstance", 90, 1, 1, 0, "E/Edward Elgar/Pomp And Circumstance.json"),
            CatalogEntry("Emilio Pujol", "El Abejorro ( the Bumblebee )", 76, 1, 1, 0, "E/Emilio Pujol/El Abejorro.json"),
            CatalogEntry("Emilio Pujol", "Etude", 120, 1, 1, 0, "E/Emilio Pujol/Etude.json"),
            CatalogEntry("Emilio Pujol", "Untitled", 120, 1, 1, 0, "E/Emilio Pujol/Untitled.json"),
            CatalogEntry("Enrique Granados", "Dedicatoria", 75, 1, 1, 0, "E/Enrique Granados/Dedicatoria.json"),
            CatalogEntry("Enrique Granados", "La Maja de Goya (tonadilla no 7)", 85, 1, 1, 0, "E/Enrique Granados/La Maja De Goya (Tonadilla Number 7).json"),
            CatalogEntry("Enrique Granados", "Oriental", 90, 1, 1, 0, "E/Enrique Granados/Spanish Dance No 2. (Oriental).json"),
            CatalogEntry("Enrique Granados", "Orientale", 104, 2, 1, 1, "E/Enrique Granados/Orientale danse n 2.json"),
            CatalogEntry("Enrique Granados", "Spanish Dance No. 2", 91, 2, 2, 0, "E/Enrique Granados/Spanish Dance No. 2.json"),
            CatalogEntry("Enrique Granados", "Spanish Dance No. 5 (andalusa)", 67, 1, 1, 0, "E/Enrique Granados/Spanish Dance No 5 (Andalusa).json"),
            CatalogEntry("Erik Satie", "Gnossienne No. 2", 90, 1, 1, 0, "E/Erik Satie/Gnossienne No. 2.json"),
            CatalogEntry("Erik Satie", "Gnossienne No. 3", 85, 1, 1, 0, "E/Erik Satie/Gnossienne No. 3.json"),
            CatalogEntry("Erik Satie", "Gymnopedie 2", 91, 2, 1, 1, "E/Erik Satie/Gymnopedie 2.json"),
            CatalogEntry("Erik Satie", "Gymnopedie No. 1", 88, 1, 1, 0, "E/Erik Satie/Gymnopedie No. 1.json"),
            CatalogEntry("Erik Satie", "Gymnopedie N° 1", 89, 2, 1, 1, "E/Erik Satie/Gymnopedie 1.json"),
            CatalogEntry("Federico Moreno Torroba", "Romance de los Pinos", 120, 1, 1, 0, "F/Federico Moreno Torroba/Romance De Los Pinos.json"),
            CatalogEntry("Federico Moreno Torroba", "Torija (elegia)", 62, 1, 1, 0, "F/Federico Moreno Torroba/Torija (Elegia).json"),
            CatalogEntry("Ferdinando Carulli", "Allegretto", 120, 1, 1, 0, "F/Ferdinando Carulli/Allegretto.json"),
            CatalogEntry("Ferdinando Carulli", "Allegretto nº 15", 120, 1, 1, 0, "F/Ferdinando Carulli/Allegretto nº 15.json"),
            CatalogEntry("Ferdinando Carulli", "Andante", 112, 1, 1, 0, "F/Ferdinando Carulli/Andante.json"),
            CatalogEntry("Ferdinando Carulli", "Andantino", 52, 1, 1, 0, "F/Ferdinando Carulli/Andantino.json"),
            CatalogEntry("Ferdinando Carulli", "Andantino #4", 120, 1, 1, 0, "F/Ferdinando Carulli/Andantino 4.json"),
            CatalogEntry("Ferdinando Carulli", "Andantino (siciliana), Exercice", 120, 1, 1, 0, "F/Ferdinando Carulli/Andantino (Siciliana).json"),
            CatalogEntry("Ferdinando Carulli", "Appendice No. 1", 120, 1, 1, 0, "F/Ferdinando Carulli/Opus 241 Appendice No1.json"),
            CatalogEntry("Ferdinando Carulli", "Bagatelle", 100, 1, 1, 0, "F/Ferdinando Carulli/Bagatelle.json"),
            CatalogEntry("Ferdinando Carulli", "Contredanse", 100, 2, 1, 1, "F/Ferdinando Carulli/Contredanse.json"),
            CatalogEntry("Ferdinando Carulli", "Duo in G-dur", 144, 2, 1, 1, "F/Ferdinando Carulli/Duet in G durian.json"),
            CatalogEntry("Ferdinando Carulli", "Estudio en a Menor", 130, 1, 1, 0, "F/Ferdinando Carulli/Estudio En A Menor.json"),
            CatalogEntry("Ferdinando Carulli", "Etude", 120, 1, 1, 0, "F/Ferdinando Carulli/Etude.json"),
            CatalogEntry("Ferdinando Carulli", "No 2", 100, 1, 1, 0, "F/Ferdinando Carulli/Opus 241 Appendice No2.json"),
            CatalogEntry("Ferdinando Carulli", "Overture", 168, 1, 1, 0, "F/Ferdinando Carulli/Overture.json"),
            CatalogEntry("Ferdinando Carulli", "Preludio N°1", 116, 1, 1, 0, "F/Ferdinando Carulli/Capriccio N1.json"),
            CatalogEntry("Ferdinando Carulli", "Romanza", 70, 1, 1, 0, "F/Ferdinando Carulli/Romanza.json"),
            CatalogEntry("Ferdinando Carulli", "Sicilienne", 78, 1, 1, 0, "F/Ferdinando Carulli/Sicilienne.json"),
            CatalogEntry("Ferdinando Carulli", "Walse", 120, 1, 1, 0, "F/Ferdinando Carulli/Walse.json"),
            CatalogEntry("Ferdinando Carulli", "Waltz", 126, 1, 1, 0, "F/Ferdinando Carulli/Warltz.json"),
            CatalogEntry("Fernando Sor", "Adagio", 115, 1, 1, 0, "F/Fernando Sor/Adagio.json"),
            CatalogEntry("Fernando Sor", "Andante", 92, 2, 1, 1, "F/Fernando Sor/Andante.json"),
            CatalogEntry("Fernando Sor", "Andante Op. 44 No1", 96, 1, 1, 0, "F/Fernando Sor/Andante (Op.44).json"),
            CatalogEntry("Fernando Sor", "Andantino", 58, 1, 1, 0, "F/Fernando Sor/andantino.json"),
            CatalogEntry("Fernando Sor", "Andantino Op. 60", 110, 1, 1, 0, "F/Fernando Sor/Andantino (op.60).json"),
            CatalogEntry("Fernando Sor", "Bolero", 83, 1, 1, 0, "F/Fernando Sor/Bolero.json"),
            CatalogEntry("Fernando Sor", "Estudio 1", 120, 1, 1, 0, "F/Fernando Sor/Estudio 1.json"),
            CatalogEntry("Fernando Sor", "Estudio 2", 80, 1, 1, 0, "F/Fernando Sor/Estudio 2.json"),
            CatalogEntry("Fernando Sor", "Estudio Ii", 91, 1, 1, 0, "F/Fernando Sor/Estudio No. 2 (op. 35 No. 13).json"),
            CatalogEntry("Fernando Sor", "Estudio Iii", 112, 1, 1, 0, "F/Fernando Sor/Estudio 3 (Op. 6 No. 2).json"),
            CatalogEntry("Fernando Sor", "Estudio Iv", 154, 1, 1, 0, "F/Fernando Sor/Estudio No. IV.json"),
            CatalogEntry("Fernando Sor", "Estudio No. 10", 70, 1, 1, 0, "F/Fernando Sor/Estudio No. 10.json"),
            CatalogEntry("Fernando Sor", "Estudio No. 11", 45, 1, 1, 0, "F/Fernando Sor/Estudio No. 11.json"),
            CatalogEntry("Fernando Sor", "Estudio No. 15", 126, 1, 1, 0, "F/Fernando Sor/Estudio No. 15.json"),
            CatalogEntry("Fernando Sor", "Estudio No. 3", 120, 1, 1, 0, "F/Fernando Sor/Estudio No. 3.json"),
            CatalogEntry("Fernando Sor", "Estudio nº 12 en Sol M", 120, 1, 1, 0, "F/Fernando Sor/Estudio nº 12 en Sol M.json"),
            CatalogEntry("Fernando Sor", "Estudio V", 110, 1, 1, 0, "F/Fernando Sor/Estudio No. V.json"),
            CatalogEntry("Fernando Sor", "Estudo Em Si Menor", 140, 1, 1, 0, "F/Fernando Sor/Estudio em Si menor.json"),
            CatalogEntry("Fernando Sor", "Etude de Fernando Sor", 104, 1, 1, 0, "F/Fernando Sor/Etude in D.json"),
            CatalogEntry("Fernando Sor", "Etude en Mi Majeur", 117, 1, 1, 0, "F/Fernando Sor/Etude en Mi Majeur.json"),
            CatalogEntry("Fernando Sor", "Etude No. 13", 30, 1, 1, 0, "F/Fernando Sor/Etude No. 13.json"),
            CatalogEntry("Fernando Sor", "Etude No. 9 Opus 35 - Fernando Sor", 120, 1, 1, 0, "F/Fernando Sor/Etude No. 9 Opus 35.json"),
            CatalogEntry("Fernando Sor", "Etude N°6 en Ré Majeur", 120, 1, 1, 0, "F/Fernando Sor/Etude N6 En Re Majeur.json"),
            CatalogEntry("Fernando Sor", "Etude N°9, Opus31 N°20", 132, 1, 1, 0, "F/Fernando Sor/Etude No 9 Opus 31.json"),
            CatalogEntry("Fernando Sor", "Etude Xvi, Opus 29 #23", 80, 1, 1, 0, "F/Fernando Sor/Etude 16 No. 23.json"),
            CatalogEntry("Fernando Sor", "Grand Sonata: Minuet and Trio (iii)", 152, 1, 1, 0, "F/Fernando Sor/Grand Sonata.json"),
            CatalogEntry("Fernando Sor", "Moderato for 1st Through 6th Position", 130, 1, 1, 0, "F/Fernando Sor/Moderato dalla I alla VI Posizione.json"),
            CatalogEntry("Fernando Sor", "Op. 31 - No. 1 (study in C Major)", 100, 1, 1, 0, "F/Fernando Sor/Op. 31 - No 1 (Study In C Major).json"),
            CatalogEntry("Fernando Sor", "Op. 44 - no 22 (study in a Minor)", 60, 1, 1, 0, "F/Fernando Sor/Op. 44 - No 22 (study In A Minor).json"),
            CatalogEntry("Fernando Sor", "Op. 60 - no 1 (study in C Major)", 100, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 1 (study In C Major).json"),
            CatalogEntry("Fernando Sor", "Op. 60 - no 5 (study in a Minor)", 66, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 5 (study In A Minor).json"),
            CatalogEntry("Fernando Sor", "Op. 60 - No. 2 (study in C Major)", 104, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 2 (study In C Major).json"),
            CatalogEntry("Fernando Sor", "Op. 60 - No. 6 (study in a Minor)", 63, 1, 1, 0, "F/Fernando Sor/Op. 60 - No 6 (study In A Minor).json"),
            CatalogEntry("Fernando Sor", "Sonate in C (op.15b)", 128, 1, 1, 0, "F/Fernando Sor/Sonate In C (Op.15B).json"),
            CatalogEntry("Fernando Sor", "Study in Bm", 117, 1, 1, 0, "F/Fernando Sor/Study in Bm.json"),
            CatalogEntry("Fernando Sor", "Study in C", 80, 1, 1, 0, "F/Fernando Sor/Study in C.json"),
            CatalogEntry("Fernando Sor", "Study in D Minor (poco Allegretto)", 72, 1, 1, 0, "F/Fernando Sor/Study In D Minor (poco Allegretto).json"),
            CatalogEntry("Fernando Sor", "Study No. 16", 80, 1, 1, 0, "F/Fernando Sor/Study No. 16.json"),
            CatalogEntry("Fernando Sor", "Study No. 18", 70, 1, 1, 0, "F/Fernando Sor/Estudio No. 18.json"),
            CatalogEntry("Fernando Sor", "Study No. 19 - Op. 29, No. 13", 35, 1, 1, 0, "F/Fernando Sor/Study No. 19.json"),
            CatalogEntry("Fernando Sor", "Tarantella", 150, 2, 1, 1, "F/Fernando Sor/Tarantella.json"),
            CatalogEntry("Fernando Sor", "Variations / Over a Theme From the Magic Flute by Mozart", 60, 1, 1, 0, "F/Fernando Sor/Variations from the Magic Flute.json"),
            CatalogEntry("Fernando Sor", "Variations in Tremolo", 46, 1, 1, 0, "F/Fernando Sor/Variations in Tremolo From Op.21.json"),
            CatalogEntry("Fernando Sor", "Walzer Nr.1", 120, 2, 1, 1, "F/Fernando Sor/Nr.1 (Six Valses).json"),
            CatalogEntry("Francisco Tarrega", "Adagio in a", 110, 1, 1, 0, "F/Francisco Tarrega/Adagio in A.json"),
            CatalogEntry("Francisco Tarrega", "Adelita", 96, 1, 1, 0, "F/Francisco Tarrega/Adelita.json"),
            CatalogEntry("Francisco Tarrega", "Cajita de Musica (music Box)", 76, 1, 1, 0, "F/Francisco Tarrega/Cajita De Musica (Music Box).json"),
            CatalogEntry("Francisco Tarrega", "Capricho Arabe", 80, 1, 1, 0, "F/Francisco Tarrega/Capricho Arabe.json"),
            CatalogEntry("Francisco Tarrega", "D+i Sa(n", 145, 1, 1, 0, "F/Francisco Tarrega/Di San.json"),
            CatalogEntry("Francisco Tarrega", "Danza Mora", 100, 1, 1, 0, "F/Francisco Tarrega/Danza Mora.json"),
            CatalogEntry("Francisco Tarrega", "Eleven Teaching Preludes", 100, 1, 1, 0, "F/Francisco Tarrega/11 Teaching Preludes.json"),
            CatalogEntry("Francisco Tarrega", "Endecha - Oremus", 75, 1, 1, 0, "F/Francisco Tarrega/Endecha - Oremus (Preludios).json"),
            CatalogEntry("Francisco Tarrega", "Estudio Brilliante", 69, 1, 1, 0, "F/Francisco Tarrega/Estudio Brilliante.json"),
            CatalogEntry("Francisco Tarrega", "Estudio de Velocidad", 90, 1, 1, 0, "F/Francisco Tarrega/Estudio De Velocidad.json"),
            CatalogEntry("Francisco Tarrega", "Estudio Sobre Un Tema de Mendelssohn", 120, 1, 1, 0, "F/Francisco Tarrega/Estudio Sobre Un Fragmento De Mendelssohn.json"),
            CatalogEntry("Francisco Tarrega", "Estudo Em Dó Maior", 100, 1, 1, 0, "F/Francisco Tarrega/Estudo Em Dó Maior.json"),
            CatalogEntry("Francisco Tarrega", "Etude in C", 72, 1, 1, 0, "F/Francisco Tarrega/Etude In C.json"),
            CatalogEntry("Francisco Tarrega", "Etude in e Minor", 72, 2, 1, 1, "F/Francisco Tarrega/Etude In E Minor.json"),
            CatalogEntry("Francisco Tarrega", "Etude-scherzo", 116, 1, 1, 0, "F/Francisco Tarrega/Etude-Scherzo.json"),
            CatalogEntry("Francisco Tarrega", "Etude-sonatine", 80, 1, 1, 0, "F/Francisco Tarrega/Etude - Sonatine.json"),
            CatalogEntry("Francisco Tarrega", "Gran Vals", 205, 1, 1, 0, "F/Francisco Tarrega/Gran Vals.json"),
            CatalogEntry("Francisco Tarrega", "Gran Vals en la (grand Waltz in A)", 150, 1, 1, 0, "F/Francisco Tarrega/Gran Vals en La (Grand Waltz in A).json"),
            CatalogEntry("Francisco Tarrega", "La Paloma", 71, 1, 1, 0, "F/Francisco Tarrega/La Paloma.json"),
            CatalogEntry("Francisco Tarrega", "Lagrima", 80, 1, 1, 0, "F/Francisco Tarrega/Lagrima Teardrops.json"),
            CatalogEntry("Francisco Tarrega", "Maria", 89, 1, 1, 0, "F/Francisco Tarrega/Maria (Gavotte).json"),
            CatalogEntry("Francisco Tarrega", "Marieta", 94, 1, 1, 0, "F/Francisco Tarrega/Marieta.json"),
            CatalogEntry("Francisco Tarrega", "Mariposa", 120, 1, 1, 0, "F/Francisco Tarrega/Mariposa.json"),
            CatalogEntry("Francisco Tarrega", "Mazurka", 104, 1, 1, 0, "F/Francisco Tarrega/Mazurka.json"),
            CatalogEntry("Francisco Tarrega", "Pavana", 100, 1, 1, 0, "F/Francisco Tarrega/Pavana.json"),
            CatalogEntry("Francisco Tarrega", "Preludio 1", 122, 1, 1, 0, "F/Francisco Tarrega/Preludio 1.json"),
            CatalogEntry("Francisco Tarrega", "Preludio nr. 10", 90, 1, 1, 0, "F/Francisco Tarrega/Preludio No. 10 (G Major).json"),
            CatalogEntry("Francisco Tarrega", "Recuerdos de la Alhambra", 82, 1, 1, 0, "F/Francisco Tarrega/Recuerdos de la Alhambra (No Tremolo).json"),
            CatalogEntry("Francisco Tarrega", "Recuerdos del de la Alhambra (palacio)", 79, 2, 1, 1, "F/Francisco Tarrega/Recuerdos de la Alhambra (Arrange).json"),
            CatalogEntry("Francisco Tarrega", "Rosita", 112, 1, 1, 0, "F/Francisco Tarrega/Rosita.json"),
            CatalogEntry("Francisco Tarrega", "Scherzo en la Majeur", 75, 1, 1, 0, "F/Francisco Tarrega/Scherzo en La Majeur.json"),
            CatalogEntry("Francisco Tarrega", "Study in a", 120, 1, 1, 0, "F/Francisco Tarrega/Study in A.json"),
            CatalogEntry("Francisco Tarrega", "Study in D", 120, 1, 1, 0, "F/Francisco Tarrega/Study in D.json"),
            CatalogEntry("Francisco Tarrega", "Study in G", 90, 1, 1, 0, "F/Francisco Tarrega/Study in G.json"),
            CatalogEntry("Francisco Tarrega", "Study nº 10", 130, 1, 1, 0, "F/Francisco Tarrega/Study nº 10.json"),
            CatalogEntry("Francisco Tarrega", "Sueno", 100, 1, 1, 0, "F/Francisco Tarrega/Sueno.json"),
            CatalogEntry("Francisco Tarrega", "Tango", 67, 1, 1, 0, "F/Francisco Tarrega/Tango.json"),
            CatalogEntry("Francisco Tarrega", "The Carnival of Venice", 100, 1, 1, 0, "F/Francisco Tarrega/Carnival of Venice.json"),
            CatalogEntry("Francisco Tarrega", "Valse", 140, 1, 1, 0, "F/Francisco Tarrega/Valse.json"),
            CatalogEntry("Franz Schubert", "Andante Con Moto", 80, 4, 1, 3, "F/Franz Schubert/Andante Con Moto.json"),
            CatalogEntry("Franz Schubert", "Ave Maria", 100, 4, 3, 1, "F/Franz Schubert/Ave Maria.json"),
            CatalogEntry("Franz Schubert", "Das Fischermädchen", 120, 2, 1, 1, "F/Franz Schubert/Das Fischermädchen.json"),
            CatalogEntry("Franz Schubert", "Momento Musicale Op. 94 No. 2", 108, 1, 1, 0, "F/Franz Schubert/Momento Musicale Op.94 No.2.json"),
            CatalogEntry("Franz Schubert", "Serenade", 54, 1, 1, 0, "F/Franz Schubert/Serenade.json"),
            CatalogEntry("Franz Schubert", "Serenade (trio)", 64, 3, 1, 2, "F/Franz Schubert/Serenade (trio).json"),
            CatalogEntry("Franz Schubert", "Standchen", 64, 2, 1, 1, "F/Franz Schubert/Standchen.json"),
            CatalogEntry("Franz Schubert", "Tränenregen", 50, 2, 1, 1, "F/Franz Schubert/Traenenregen.json"),
            CatalogEntry("Frederic Chopin", "Ballade no 1 in G Minor, op. 23", 60, 1, 1, 0, "F/Frederic Chopin/Ballade No 1 In G Minor Op. 23.json"),
            CatalogEntry("Frederic Chopin", "Etude No. 2, Opus 25", 110, 2, 1, 1, "F/Frederic Chopin/Etude No. 2 Opus 25.json"),
            CatalogEntry("Frederic Chopin", "Fantaisie Impromptu Op66", 140, 1, 1, 0, "F/Frederic Chopin/Fantaisie Impromptu Op66.json"),
            CatalogEntry("Frederic Chopin", "Fantaisie Impromptue Opus 66 (c# Mineur)", 180, 2, 1, 1, "F/Frederic Chopin/Fantaisie impromptu in C sharp minor op.66.json"),
            CatalogEntry("Frederic Chopin", "Mazurek Op68 No3", 132, 2, 2, 0, "F/Frederic Chopin/Mazurek Op68 No3.json"),
            CatalogEntry("Frederic Chopin", "Mazurka Op52 No2", 100, 2, 2, 0, "F/Frederic Chopin/Mazurka Op52 No2.json"),
            CatalogEntry("Frederic Chopin", "Nocturne in C-sharp Minor", 72, 2, 1, 1, "F/Frederic Chopin/Nocturne in C-Sharp Minor.json"),
            CatalogEntry("Frederic Chopin", "Prelude No. 15 in D Flat Major 'raindrop Prelude'", 75, 2, 1, 1, "F/Frederic Chopin/Prelude No.15 In D Flat Major (Raindrop Prelude).json"),
            CatalogEntry("Frederic Chopin", "Prelude No. 4", 66, 4, 4, 0, "F/Frederic Chopin/Prelude No.4.json"),
            CatalogEntry("Frederic Chopin", "Prelude No. 6, Opus 28", 50, 2, 1, 1, "F/Frederic Chopin/Prelude  No. 6 Opus 28.json"),
            CatalogEntry("Frederic Chopin", "Prelude No20", 32, 3, 3, 0, "F/Frederic Chopin/prelude No20.json"),
            CatalogEntry("Frederic Chopin", "Prelude Opus 28 No. 4", 58, 2, 2, 0, "F/Frederic Chopin/Opus 28 No.3 - Prelude.json"),
            CatalogEntry("Frederic Chopin", "Preludio N. 1 in Do Maggiore", 60, 1, 1, 0, "F/Frederic Chopin/Preludio n.1 in Do maggiore.json"),
            CatalogEntry("Frederic Chopin", "Preludio N.2 in la Minore", 60, 1, 1, 0, "F/Frederic Chopin/Preludio n.2 in La minore.json"),
            CatalogEntry("Frederic Chopin", "Revolutionary Study", 180, 1, 1, 0, "F/Frederic Chopin/Revolutionary Study.json"),
            CatalogEntry("Frederic Chopin", "Tristesse", 43, 1, 1, 0, "F/Frederic Chopin/Tristesse.json"),
            CatalogEntry("Frederic Chopin", "Valse No6 Op64 No1", 202, 2, 2, 0, "F/Frederic Chopin/Valse No6 Op64 No1.json"),
            CatalogEntry("Frederic Chopin", "Valse No7 Op64 No2", 109, 1, 1, 0, "F/Frederic Chopin/Valse No. 7 Op64 No. 2.json"),
            CatalogEntry("Frederic Chopin", "Valse Op. 64 No. 1 (petit Chien)", 240, 3, 1, 2, "F/Frederic Chopin/Valse Op.64 No.1.json"),
            CatalogEntry("Frederic Chopin", "Valse Op34 No2", 100, 2, 2, 0, "F/Frederic Chopin/Valse Op34 No2.json"),
            CatalogEntry("Frederic Chopin", "Valse Op69 No2", 152, 3, 3, 0, "F/Frederic Chopin/Valse Op69 No2.json"),
            CatalogEntry("Gaspar Sanz", "Canarios", 180, 1, 1, 0, "G/Gaspar Sanz/CANARIOS.json"),
            CatalogEntry("Gaspar Sanz", "Corranda", 144, 1, 1, 0, "G/Gaspar Sanz/Corranda.json"),
            CatalogEntry("Gaspar Sanz", "En Ré", 120, 1, 1, 0, "G/Gaspar Sanz/PARADETAS  FROM 5 DANCES.json"),
            CatalogEntry("Gaspar Sanz", "Espagnoletta", 160, 1, 1, 0, "G/Gaspar Sanz/Espagnoletta.json"),
            CatalogEntry("Gaspar Sanz", "Españoletas", 120, 1, 1, 0, "G/Gaspar Sanz/Españoletas 2.json"),
            CatalogEntry("Gaspar Sanz", "Fanfarra", 140, 1, 1, 0, "G/Gaspar Sanz/Fanfarra (from 5 dances).json"),
            CatalogEntry("Gaspar Sanz", "Folias", 120, 2, 2, 0, "G/Gaspar Sanz/Folias.json"),
            CatalogEntry("Gaspar Sanz", "Matachin", 120, 1, 1, 0, "G/Gaspar Sanz/MATACHIN.json"),
            CatalogEntry("Gaspar Sanz", "Paradetas", 105, 2, 2, 0, "G/Gaspar Sanz/Paradetas.json"),
            CatalogEntry("Gaspar Sanz", "Pavana", 140, 1, 1, 0, "G/Gaspar Sanz/Pavana (from 5 dances).json"),
            CatalogEntry("Gaspar Sanz", "Pavanas", 112, 1, 1, 0, "G/Gaspar Sanz/Pavanas.json"),
            CatalogEntry("Gaspar Sanz", "Rujero", 120, 1, 1, 0, "G/Gaspar Sanz/Rujero  (from 5 dances).json"),
            CatalogEntry("Gaspar Sanz", "Sarabande", 56, 1, 1, 0, "G/Gaspar Sanz/SARABANDE.json"),
            CatalogEntry("Gaspar Sanz", "Sesquialtera", 100, 1, 1, 0, "G/Gaspar Sanz/Sesquialtera.json"),
            CatalogEntry("Gaspar Sanz", "Suite Española: No. 9 Canarios", 210, 1, 1, 0, "G/Gaspar Sanz/Suite Española_ No. 9 Canarios.json"),
            CatalogEntry("Gaspar Sanz", "Ïðåëþäèÿ", 120, 1, 1, 0, "G/Gaspar Sanz/Prelude.json"),
            CatalogEntry("George Frideric Handel", "\"aylesford\" Gavotte", 126, 2, 1, 1, "G/George Frideric Handel/''Aylesford'' Gavotte.json"),
            CatalogEntry("George Frideric Handel", "Concerto en Si Minor", 120, 1, 1, 0, "G/George Frideric Handel/Concerto En Si Minor.json"),
            CatalogEntry("George Frideric Handel", "Sarabande", 80, 3, 1, 2, "G/George Frideric Handel/Sarabande_MetalVersion.json"),
            CatalogEntry("George Frideric Handel", "Water Music", 220, 3, 3, 0, "G/George Frideric Handel/Water Music Finale.json"),
            CatalogEntry("Grieg", "Atmosphère Matinale", 63, 3, 1, 2, "G/Grieg/Atmosphère Matinale.json"),
            CatalogEntry("Grieg", "Dans L'antre Du Roi de la Montagne", 300, 5, 1, 4, "G/Grieg/Dans l'Antre du Roi de la Montagne.json"),
            CatalogEntry("Grieg", "Danse D'anitra", 160, 2, 1, 1, "G/Grieg/Danse d_'Anitra.json"),
            CatalogEntry("Grieg", "En la Mansión del Rey de la Montaña", 120, 9, 6, 3, "G/Grieg/En la Mansion del Rey de la Montaña.json"),
            CatalogEntry("Grieg", "Solveig Song", 400, 2, 1, 1, "G/Grieg/Solveig Song.json"),
            CatalogEntry("Grieg", "The Hall of the Mountain King (black Metal Version)", 151, 3, 1, 2, "G/Grieg/The Hall of the Mountain King (Black Metal version).json"),
            CatalogEntry("Heitor Villa-lobos", "Bachianas Brasileiras 5 ( Aria )", 60, 2, 1, 1, "H/Heitor Villa-Lobos/Bachianas Brasileiras 5 (Aria).json"),
            CatalogEntry("Heitor Villa-lobos", "Besame Mucho", 120, 1, 1, 0, "H/Heitor Villa-Lobos/Besame Mucho.json"),
            CatalogEntry("Heitor Villa-lobos", "Choros", 62, 1, 1, 0, "H/Heitor Villa-Lobos/Choros.json"),
            CatalogEntry("Heitor Villa-lobos", "Etude no 1", 120, 1, 1, 0, "H/Heitor Villa-Lobos/Etude No 1.json"),
            CatalogEntry("Heitor Villa-lobos", "Etude No. 1", 160, 1, 1, 0, "H/Heitor Villa-Lobos/Etude No. 1.json"),
            CatalogEntry("Heitor Villa-lobos", "Etude No. 12", 176, 1, 1, 0, "H/Heitor Villa-Lobos/Etude no. 12.json"),
            CatalogEntry("Heitor Villa-lobos", "Etude N°6", 90, 1, 1, 0, "H/Heitor Villa-Lobos/Etude No. 6.json"),
            CatalogEntry("Heitor Villa-lobos", "Gavotta-chôro", 120, 1, 1, 0, "H/Heitor Villa-Lobos/Suite Populaire Bresilienne - No. 4_ Gavotta-choro.json"),
            CatalogEntry("Heitor Villa-lobos", "Prelude N°1", 120, 1, 1, 0, "H/Heitor Villa-Lobos/Prelude No. 1.json"),
            CatalogEntry("Heitor Villa-lobos", "Prelude N°4", 60, 1, 1, 0, "H/Heitor Villa-Lobos/Prelude n4 in Em.json"),
            CatalogEntry("Heitor Villa-lobos", "Sentimental Melody", 92, 1, 1, 0, "H/Heitor Villa-Lobos/Sentimental Melody.json"),
            CatalogEntry("Isaac Albeniz", "Alborada op 71", 89, 2, 2, 0, "I/Isaac Albeniz/Alborada Op 71.json"),
            CatalogEntry("Isaac Albeniz", "Asturias", 120, 1, 1, 0, "I/Isaac Albeniz/Asturias.json"),
            CatalogEntry("Isaac Albeniz", "Bajo la Palmera", 100, 2, 2, 0, "I/Isaac Albeniz/Bajo La Palmera.json"),
            CatalogEntry("Isaac Albeniz", "Cadiz", 100, 1, 1, 0, "I/Isaac Albeniz/Cadiz.json"),
            CatalogEntry("Isaac Albeniz", "Capricho Catalan", 70, 1, 1, 0, "I/Isaac Albeniz/Capricho Catalan.json"),
            CatalogEntry("Isaac Albeniz", "Castilla", 100, 2, 2, 0, "I/Isaac Albeniz/Castilla (duet).json"),
            CatalogEntry("Isaac Albeniz", "Córdoba", 90, 1, 1, 0, "I/Isaac Albeniz/C_rdoba.json"),
            CatalogEntry("Isaac Albeniz", "En la Playa (on the Beach).", 88, 2, 1, 1, "I/Isaac Albeniz/En la Playa (On the Beach)..json"),
            CatalogEntry("Isaac Albeniz", "Granada", 50, 1, 1, 0, "I/Isaac Albeniz/Granada.json"),
            CatalogEntry("Isaac Albeniz", "Leyenda (asturias)", 107, 1, 1, 0, "I/Isaac Albeniz/Leyenda.json"),
            CatalogEntry("Isaac Albeniz", "Rumores de la Caleta", 82, 1, 1, 0, "I/Isaac Albeniz/Rumores De La Caleta (Malaguena - De _'Requerdos De Viaje_').json"),
            CatalogEntry("Isaac Albeniz", "Sevilla", 112, 1, 1, 0, "I/Isaac Albeniz/Sevilla.json"),
            CatalogEntry("Isaac Albeniz", "Spanish Tango, Op. 164 No. 2", 80, 2, 1, 1, "I/Isaac Albeniz/Spanish Tango Op.164 No.2.json"),
            CatalogEntry("Isaac Albeniz", "Tango", 70, 1, 1, 0, "I/Isaac Albeniz/Tango (No.2 De La Suite _'Espana_').json"),
            CatalogEntry("Johann Pachelbel", "Cannon in C", 120, 1, 1, 0, "J/Johann Pachelbel/Cannon In C.json"),
            CatalogEntry("Johann Pachelbel", "Cannon in D", 120, 3, 1, 2, "J/Johann Pachelbel/Cannon In D.json"),
            CatalogEntry("Johann Pachelbel", "Canon en Ré Majeur", 48, 4, 1, 3, "J/Johann Pachelbel/Canon De Pachelbel.json"),
            CatalogEntry("Johann Pachelbel", "Canon in D", 72, 1, 1, 0, "J/Johann Pachelbel/Canon In D Variation.json"),
            CatalogEntry("Johann Pachelbel", "Canon in D Major (2 Guitars)", 48, 2, 2, 0, "J/Johann Pachelbel/Canon in D Major.json"),
            CatalogEntry("Johann Pachelbel", "Fantasie", 45, 6, 6, 0, "J/Johann Pachelbel/Fantasie (Acoustic).json"),
            CatalogEntry("Johann Pachelbel", "Fugue", 64, 2, 2, 0, "J/Johann Pachelbel/Fugue Duo.json"),
            CatalogEntry("Johann Sebastian Bach", "\"fugue\" From Toccata", 120, 1, 1, 0, "J/Johann Sebastian Bach/Fugue from Toccata.json"),
            CatalogEntry("Johann Sebastian Bach", "Air", 40, 3, 1, 2, "J/Johann Sebastian Bach/Air.json"),
            CatalogEntry("Johann Sebastian Bach", "Air (electic Guitar Version)", 35, 3, 1, 2, "J/Johann Sebastian Bach/Air On G (Electric Guitar Version).json"),
            CatalogEntry("Johann Sebastian Bach", "Air From \"suite no 3\"", 93, 2, 1, 1, "J/Johann Sebastian Bach/Air From Suite 3.json"),
            CatalogEntry("Johann Sebastian Bach", "Air on a G String", 73, 1, 1, 0, "J/Johann Sebastian Bach/Air On a G String.json"),
            CatalogEntry("Johann Sebastian Bach", "Air on a G String for a Metal Band", 120, 5, 3, 2, "J/Johann Sebastian Bach/Air on a G string (metal arrangement).json"),
            CatalogEntry("Johann Sebastian Bach", "Air on the 4th String (metal Version)", 120, 4, 2, 2, "J/Johann Sebastian Bach/Air on the G string.json"),
            CatalogEntry("Johann Sebastian Bach", "Alegretto", 92, 2, 2, 0, "J/Johann Sebastian Bach/Alegretto.json"),
            CatalogEntry("Johann Sebastian Bach", "Allegro", 100, 2, 2, 0, "J/Johann Sebastian Bach/Allegro (Acoustic Guitar Version ).json"),
            CatalogEntry("Johann Sebastian Bach", "Allegro Third Movement", 120, 3, 1, 2, "J/Johann Sebastian Bach/Allegro Third Movement.json"),
            CatalogEntry("Johann Sebastian Bach", "Allemande From Partita No. 2 in D Minor", 60, 1, 1, 0, "J/Johann Sebastian Bach/Allemande From Partita No. 2 In D Minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Allemande in a Minor", 120, 1, 1, 0, "J/Johann Sebastian Bach/Allemande In A Minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Andante", 100, 2, 1, 1, "J/Johann Sebastian Bach/ANDANTE.json"),
            CatalogEntry("Johann Sebastian Bach", "Aria", 53, 1, 1, 0, "J/Johann Sebastian Bach/Aria (Goldberg Variations).json"),
            CatalogEntry("Johann Sebastian Bach", "Aria From Cantata 41", 100, 4, 4, 0, "J/Johann Sebastian Bach/Aria From Cantata 41.json"),
            CatalogEntry("Johann Sebastian Bach", "Avemaria (acoustic)", 120, 1, 1, 0, "J/Johann Sebastian Bach/Ave Maria (Acoustic).json"),
            CatalogEntry("Johann Sebastian Bach", "Ayre", 50, 4, 1, 3, "J/Johann Sebastian Bach/Ayre suite no 3.json"),
            CatalogEntry("Johann Sebastian Bach", "Bach Suite no 1 bwv 1007", 68, 1, 1, 0, "J/Johann Sebastian Bach/Bach Suite N0 1 BWV 1007Doig.json"),
            CatalogEntry("Johann Sebastian Bach", "Badinerie", 120, 3, 1, 2, "J/Johann Sebastian Bach/Badinerie.json"),
            CatalogEntry("Johann Sebastian Bach", "Bouree (from Lute Suite No. 1 in e Minor)", 120, 2, 1, 1, "J/Johann Sebastian Bach/Bouree (From Lute suite no. 1 in E minor).json"),
            CatalogEntry("Johann Sebastian Bach", "Bouree Cello Suite Iii", 120, 1, 1, 0, "J/Johann Sebastian Bach/Bouree Cello Suite III.json"),
            CatalogEntry("Johann Sebastian Bach", "Bourre", 120, 1, 1, 0, "J/Johann Sebastian Bach/Bourre.json"),
            CatalogEntry("Johann Sebastian Bach", "Bourree", 132, 1, 1, 0, "J/Johann Sebastian Bach/Bouree.json"),
            CatalogEntry("Johann Sebastian Bach", "Bourree (suite Pour Luth en Mi Mineur bwv 996)", 116, 1, 1, 0, "J/Johann Sebastian Bach/Bourree en mi mineur.json"),
            CatalogEntry("Johann Sebastian Bach", "Bwv 515", 140, 2, 1, 1, "J/Johann Sebastian Bach/Bwv 515.json"),
            CatalogEntry("Johann Sebastian Bach", "Bwv 625", 89, 3, 1, 2, "J/Johann Sebastian Bach/BWV 625.json"),
            CatalogEntry("Johann Sebastian Bach", "Bwv 996 Suite Prélude/presto", 70, 1, 1, 0, "J/Johann Sebastian Bach/Bwv 996 N1 Suite Prédule Presto.json"),
            CatalogEntry("Johann Sebastian Bach", "Bwv1007 - Prelude (cello Suite 1)", 140, 1, 1, 0, "J/Johann Sebastian Bach/Bwv1007 - Prelude.json"),
            CatalogEntry("Johann Sebastian Bach", "C Minor Prelude", 120, 1, 1, 0, "J/Johann Sebastian Bach/C Minor Prelude.json"),
            CatalogEntry("Johann Sebastian Bach", "Cantate Ich Ruf", 57, 2, 2, 0, "J/Johann Sebastian Bach/Cantate Ich Ruf.json"),
            CatalogEntry("Johann Sebastian Bach", "Chaccone (from Violin Partita No. 2)", 60, 1, 1, 0, "J/Johann Sebastian Bach/Chaccone (From Violin Partita No.2).json"),
            CatalogEntry("Johann Sebastian Bach", "Chello 1", 80, 2, 1, 1, "J/Johann Sebastian Bach/Chello 1.json"),
            CatalogEntry("Johann Sebastian Bach", "Chorale", 120, 1, 1, 0, "J/Johann Sebastian Bach/Gieb dass ich thu_' mit Fleiss.json"),
            CatalogEntry("Johann Sebastian Bach", "Chromatic Fugue", 100, 3, 3, 0, "J/Johann Sebastian Bach/Chromatic Fugue.json"),
            CatalogEntry("Johann Sebastian Bach", "Concerto in a Minor", 100, 1, 1, 0, "J/Johann Sebastian Bach/Concerto In A Minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Courante", 150, 1, 1, 0, "J/Johann Sebastian Bach/Courante.json"),
            CatalogEntry("Johann Sebastian Bach", "Double", 100, 1, 1, 0, "J/Johann Sebastian Bach/Double.json"),
            CatalogEntry("Johann Sebastian Bach", "Fuga 1 Para 4 Voces", 53, 4, 3, 1, "J/Johann Sebastian Bach/Fuga 1 Para 4 Voces.json"),
            CatalogEntry("Johann Sebastian Bach", "Fuga in Do Minore", 80, 3, 2, 1, "J/Johann Sebastian Bach/Fuga in do minore.json"),
            CatalogEntry("Johann Sebastian Bach", "Fuga À Trois", 112, 3, 1, 2, "J/Johann Sebastian Bach/Fuga A 3.json"),
            CatalogEntry("Johann Sebastian Bach", "Fuge (orig.: G-moll)", 90, 1, 1, 0, "J/Johann Sebastian Bach/Fugue (G-Moll) BWV 1000.json"),
            CatalogEntry("Johann Sebastian Bach", "Fughetta", 100, 2, 2, 0, "J/Johann Sebastian Bach/Fughetta.json"),
            CatalogEntry("Johann Sebastian Bach", "Fugue", 105, 4, 1, 3, "J/Johann Sebastian Bach/Fugue In D-.json"),
            CatalogEntry("Johann Sebastian Bach", "Fugue in D Minor", 120, 1, 1, 0, "J/Johann Sebastian Bach/Fugue In D Minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Fugue in Gm (little Fugue)", 127, 1, 1, 0, "J/Johann Sebastian Bach/Fugue In Gm (Little Fugue).json"),
            CatalogEntry("Johann Sebastian Bach", "Gavote", 60, 1, 1, 0, "J/Johann Sebastian Bach/Gavote.json"),
            CatalogEntry("Johann Sebastian Bach", "Gavotte", 120, 1, 1, 0, "J/Johann Sebastian Bach/BWV1006.json"),
            CatalogEntry("Johann Sebastian Bach", "Gavotte 1 & 2", 160, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte 1 & 2.json"),
            CatalogEntry("Johann Sebastian Bach", "Gavotte en Rondeau", 137, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte en Rondeau (from lute suite 4) BWV 1006a.json"),
            CatalogEntry("Johann Sebastian Bach", "Gavotte en Rondeau (from bwv 995)", 200, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte En Rondeau (From BWV 995).json"),
            CatalogEntry("Johann Sebastian Bach", "Gavotte in a Minor (j.s. Bach) by Leo V.d..ketterij", 60, 1, 1, 0, "J/Johann Sebastian Bach/Gavotte in A minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Gigue", 170, 1, 1, 0, "J/Johann Sebastian Bach/Gigue (Acoustic Guitar Version).json"),
            CatalogEntry("Johann Sebastian Bach", "Gigue (from Lute Suite No. 2)", 104, 1, 1, 0, "J/Johann Sebastian Bach/Gigue (From Lute Suite No. 2).json"),
            CatalogEntry("Johann Sebastian Bach", "Inventio 3", 120, 1, 1, 0, "J/Johann Sebastian Bach/Inventio 3.json"),
            CatalogEntry("Johann Sebastian Bach", "Invention 01", 110, 2, 2, 0, "J/Johann Sebastian Bach/Invention 01.json"),
            CatalogEntry("Johann Sebastian Bach", "Invention 13", 100, 2, 1, 1, "J/Johann Sebastian Bach/Invention 13.json"),
            CatalogEntry("Johann Sebastian Bach", "Invention 13 in a Minor. B. W. Iii", 80, 2, 1, 1, "J/Johann Sebastian Bach/Invention in Am.json"),
            CatalogEntry("Johann Sebastian Bach", "Invention en D Mineur", 60, 2, 2, 0, "J/Johann Sebastian Bach/Invention En D mineur.json"),
            CatalogEntry("Johann Sebastian Bach", "Invention No. 1", 80, 2, 1, 1, "J/Johann Sebastian Bach/Invention No. 1.json"),
            CatalogEntry("Johann Sebastian Bach", "Invention nº 13", 60, 1, 1, 0, "J/Johann Sebastian Bach/Invention No.13.json"),
            CatalogEntry("Johann Sebastian Bach", "Invenzione a Due Voci - for 1st Through 7th Position", 75, 1, 1, 0, "J/Johann Sebastian Bach/Invenzione a Due Voci - For 1st through 7th position..json"),
            CatalogEntry("Johann Sebastian Bach", "Italian Concerto", 127, 2, 1, 1, "J/Johann Sebastian Bach/Italian Concerto.json"),
            CatalogEntry("Johann Sebastian Bach", "Jesu Joy of Man's Desiring", 100, 1, 1, 0, "J/Johann Sebastian Bach/Jesu Joy Of Man_'s Desiring BWV 147.json"),
            CatalogEntry("Johann Sebastian Bach", "Jesu Meine Zuversicht", 128, 1, 1, 0, "J/Johann Sebastian Bach/Jesu meine Zuversicht.json"),
            CatalogEntry("Johann Sebastian Bach", "Jesu, Joy of Man's Desiring", 69, 2, 1, 1, "J/Johann Sebastian Bach/Jesu Joy Of Man's Desiring.json"),
            CatalogEntry("Johann Sebastian Bach", "Joke", 120, 1, 1, 0, "J/Johann Sebastian Bach/Joke.json"),
            CatalogEntry("Johann Sebastian Bach", "Joke B-moll", 140, 5, 2, 3, "J/Johann Sebastian Bach/Joke B-moll.json"),
            CatalogEntry("Johann Sebastian Bach", "Joy of Mans Desiring", 110, 1, 1, 0, "J/Johann Sebastian Bach/Jesu Joy Of Man_'s Desiring.json"),
            CatalogEntry("Johann Sebastian Bach", "Jésus Que Ma Joie Demeure", 99, 1, 1, 0, "J/Johann Sebastian Bach/Jesus que ma joie demeure.json"),
            CatalogEntry("Johann Sebastian Bach", "Largo e Dolce", 75, 3, 3, 0, "J/Johann Sebastian Bach/Largo e Dolce.json"),
            CatalogEntry("Johann Sebastian Bach", "Largo Es-dur", 45, 3, 1, 2, "J/Johann Sebastian Bach/Largo Trio Es-dur.json"),
            CatalogEntry("Johann Sebastian Bach", "March", 130, 1, 1, 0, "J/Johann Sebastian Bach/March.json"),
            CatalogEntry("Johann Sebastian Bach", "Menuet", 120, 2, 1, 1, "J/Johann Sebastian Bach/Menuet.json"),
            CatalogEntry("Johann Sebastian Bach", "Menuet (1ère Suite Violoncelle Ré Mineur)", 98, 1, 1, 0, "J/Johann Sebastian Bach/Menuet 1ère suite violoncelle ré mineur.json"),
            CatalogEntry("Johann Sebastian Bach", "Menuet in a Min", 120, 1, 1, 0, "J/Johann Sebastian Bach/Menuet In A Min.json"),
            CatalogEntry("Johann Sebastian Bach", "Menuet in D Major", 88, 4, 1, 3, "J/Johann Sebastian Bach/Menuet In D Major.json"),
            CatalogEntry("Johann Sebastian Bach", "Menuet in G Min", 110, 1, 1, 0, "J/Johann Sebastian Bach/Menuet In G Minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Menuett", 146, 1, 1, 0, "J/Johann Sebastian Bach/Menuett.json"),
            CatalogEntry("Johann Sebastian Bach", "Menuetti G-duuri", 120, 3, 1, 2, "J/Johann Sebastian Bach/Menuetti.json"),
            CatalogEntry("Johann Sebastian Bach", "Minuet #2, Cello Suite #1", 120, 1, 1, 0, "J/Johann Sebastian Bach/Minuet 2 Cellosuite 1.json"),
            CatalogEntry("Johann Sebastian Bach", "Minuet in G", 140, 2, 1, 1, "J/Johann Sebastian Bach/Minuet in G.json"),
            CatalogEntry("Johann Sebastian Bach", "Minuet in G (duet)", 120, 2, 1, 1, "J/Johann Sebastian Bach/Minuet In G (Duet).json"),
            CatalogEntry("Johann Sebastian Bach", "Minuet in Mi Menor", 180, 2, 1, 1, "J/Johann Sebastian Bach/Minuet in Mi menor.json"),
            CatalogEntry("Johann Sebastian Bach", "Minueto Em la Menor", 100, 1, 1, 0, "J/Johann Sebastian Bach/Minueto Em La Menor.json"),
            CatalogEntry("Johann Sebastian Bach", "Minueto Em Sol Maior", 140, 1, 1, 0, "J/Johann Sebastian Bach/Menuet In G Major.json"),
            CatalogEntry("Johann Sebastian Bach", "Minueto en G+", 120, 1, 1, 0, "J/Johann Sebastian Bach/Minueto En G Anna Magdelene Bach Notebook.json"),
            CatalogEntry("Johann Sebastian Bach", "Musette in D Major", 75, 1, 1, 0, "J/Johann Sebastian Bach/Musette in D Major.json"),
            CatalogEntry("Johann Sebastian Bach", "Number 3", 120, 1, 1, 0, "J/Johann Sebastian Bach/Ach wie fluchtig.json"),
            CatalogEntry("Johann Sebastian Bach", "Number 5", 120, 1, 1, 0, "J/Johann Sebastian Bach/Jesu nimm dich deiner Glieder.json"),
            CatalogEntry("Johann Sebastian Bach", "Number 7", 120, 1, 1, 0, "J/Johann Sebastian Bach/Es ist genug so nimm. Herr meinen Geist.json"),
            CatalogEntry("Johann Sebastian Bach", "Partita for Lute: Sarabande", 48, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande - Bwv 997.json"),
            CatalogEntry("Johann Sebastian Bach", "Partita No. 1 for Solo Violin: Sarabande (v)", 56, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande - Bwv 1002.json"),
            CatalogEntry("Johann Sebastian Bach", "Polonaise", 110, 1, 1, 0, "J/Johann Sebastian Bach/Polonaise.json"),
            CatalogEntry("Johann Sebastian Bach", "Praludium", 95, 1, 1, 0, "J/Johann Sebastian Bach/Praludium 4.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude", 120, 2, 1, 1, "J/Johann Sebastian Bach/Prelude (Electric Guitar Version).json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude - From Suite in e", 100, 1, 1, 0, "J/Johann Sebastian Bach/Prelude In E From 4th Lute Suite.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude C Mineur", 160, 3, 1, 2, "J/Johann Sebastian Bach/Prelude C mineur.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude From Suite No. 1 for Cello - Bwv1007", 70, 1, 1, 0, "J/Johann Sebastian Bach/Cello Suite 1.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude in C Minor", 160, 1, 1, 0, "J/Johann Sebastian Bach/Prelude In C Minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude in Cm", 120, 2, 1, 1, "J/Johann Sebastian Bach/Prelude in Cm.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude in D", 90, 1, 1, 0, "J/Johann Sebastian Bach/Prelude In D.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude in Dminor", 120, 1, 1, 0, "J/Johann Sebastian Bach/prelude in D minor.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude N. 1", 60, 1, 1, 0, "J/Johann Sebastian Bach/Prelude N 1.json"),
            CatalogEntry("Johann Sebastian Bach", "Prelude No. 1 in C Major", 120, 1, 1, 0, "J/Johann Sebastian Bach/Prelude No.1 In C Major.json"),
            CatalogEntry("Johann Sebastian Bach", "Preludio in Mi Minore", 144, 2, 2, 0, "J/Johann Sebastian Bach/Preludio in mi minore.json"),
            CatalogEntry("Johann Sebastian Bach", "Preludium No. 1", 92, 3, 1, 2, "J/Johann Sebastian Bach/Preludium no.1.json"),
            CatalogEntry("Johann Sebastian Bach", "Prélude en Ré Mineur", 90, 1, 1, 0, "J/Johann Sebastian Bach/Prelude en Re Mineur.json"),
            CatalogEntry("Johann Sebastian Bach", "Prélude N°1", 60, 1, 1, 0, "J/Johann Sebastian Bach/Prlude no1.json"),
            CatalogEntry("Johann Sebastian Bach", "Prélude N°1 en Do Majeur Du Clavier Bien Tempéré", 70, 1, 1, 0, "J/Johann Sebastian Bach/Prelude N1 En Do Majeur Du Clavier Bien Tempere.json"),
            CatalogEntry("Johann Sebastian Bach", "Pélude en C", 80, 1, 1, 0, "J/Johann Sebastian Bach/C Prelude From The Well-Tempered Clavier.json"),
            CatalogEntry("Johann Sebastian Bach", "Sarabande", 72, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande from Partita in Bm for Violin.json"),
            CatalogEntry("Johann Sebastian Bach", "Scerzzo H-moll From Sonata #4", 100, 3, 1, 2, "J/Johann Sebastian Bach/Badinerie (Joke In H-Moll From Sonata 4) - Rearranged For (Art) Rock Band.json"),
            CatalogEntry("Johann Sebastian Bach", "Second Violin Sonata in A-minor. bwv 1003 - Allegro", 160, 1, 1, 0, "J/Johann Sebastian Bach/Second Violin Sonata in a-minor. BWV 1003 - allegro.json"),
            CatalogEntry("Johann Sebastian Bach", "Sinfonia#12", 50, 1, 1, 0, "J/Johann Sebastian Bach/Sinfonia 12.json"),
            CatalogEntry("Johann Sebastian Bach", "Srabande From Cello Suite I (bwv 1007)", 35, 1, 1, 0, "J/Johann Sebastian Bach/Sarabande (From Cello Suite I BWV 1007).json"),
            CatalogEntry("Johann Sebastian Bach", "Suite #1 in D Major", 70, 1, 1, 0, "J/Johann Sebastian Bach/Prelude from Suite 1 in D Major.json"),
            CatalogEntry("Johann Sebastian Bach", "Suite de Bach", 120, 3, 1, 2, "J/Johann Sebastian Bach/Suite De Bach (Metal Version).json"),
            CatalogEntry("Johann Sebastian Bach", "Suite de Bach With Heavy Metal Arrangement", 120, 6, 1, 5, "J/Johann Sebastian Bach/Suite De Bach.json"),
            CatalogEntry("Johann Sebastian Bach", "Suite for Lute: Prelude (i)", 92, 1, 1, 0, "J/Johann Sebastian Bach/Prelude - Bwv 999.json"),
            CatalogEntry("Johann Sebastian Bach", "Suite nr. 1", 120, 2, 1, 1, "J/Johann Sebastian Bach/Préludía from Suit N1.json"),
            CatalogEntry("Johann Sebastian Bach", "Suiten Für Violoncello N°1", 77, 1, 1, 0, "J/Johann Sebastian Bach/Suiten Fur Violoncello N1.json"),
            CatalogEntry("Johann Sebastian Bach", "Toccata & Fugue", 30, 1, 1, 0, "J/Johann Sebastian Bach/Toccata And Fugue (Bwv 565).json"),
            CatalogEntry("Johann Sebastian Bach", "Toccata and Fugue", 110, 1, 1, 0, "J/Johann Sebastian Bach/Toccata and Fugue.json"),
            CatalogEntry("Johann Sebastian Bach", "Toccata e Fugue in Dm bwv 565", 100, 3, 1, 2, "J/Johann Sebastian Bach/Toccata & Fugue In Dm Bwv 565 (Metal Version).json"),
            CatalogEntry("Johann Sebastian Bach", "Toccata Et Fugue en Ré Mineur", 50, 2, 2, 0, "J/Johann Sebastian Bach/Toccata et fugue en ré mineur.json"),
            CatalogEntry("Johann Sebastian Bach", "Two Part Invention #4", 130, 2, 1, 1, "J/Johann Sebastian Bach/Invention 04.json"),
            CatalogEntry("Johann Sebastian Bach", "Two Part Invention #8", 120, 2, 1, 1, "J/Johann Sebastian Bach/Invention 08.json"),
            CatalogEntry("Johann Sebastian Bach", "Variation Goldberg Numéro 1", 100, 2, 1, 1, "J/Johann Sebastian Bach/Goldberg Variations 1.json"),
            CatalogEntry("Johann Sebastian Bach", "Variations Goldberg, Variations 29", 69, 2, 1, 1, "J/Johann Sebastian Bach/Golberg Variation 29.json"),
            CatalogEntry("Johann Sebastian Bach", "Violin Concerto (2nd Movement)", 80, 5, 4, 1, "J/Johann Sebastian Bach/Violin Concerto.json"),
            CatalogEntry("Johann Sebastian Bach", "Violin Sonata no 1 in Gm Fugue", 100, 1, 1, 0, "J/Johann Sebastian Bach/Violin Sonata No 1 In Gm  Fugue Bwv 1001.json"),
            CatalogEntry("Johann Sebastian Bach", "Vivace", 91, 1, 1, 0, "J/Johann Sebastian Bach/Vivace.json"),
            CatalogEntry("John Dowland", "A Fancy (p-73)", 64, 1, 1, 0, "J/John Dowland/A Fancy P.73.json"),
            CatalogEntry("John Dowland", "A Fancy P-6", 72, 1, 1, 0, "J/John Dowland/A Fancy P-6.json"),
            CatalogEntry("John Dowland", "An Air", 66, 1, 1, 0, "J/John Dowland/An air.json"),
            CatalogEntry("John Dowland", "Andante", 70, 1, 1, 0, "J/John Dowland/Andante.json"),
            CatalogEntry("John Dowland", "Awake Sweet Love", 112, 1, 1, 0, "J/John Dowland/Awake Sweet Love.json"),
            CatalogEntry("John Dowland", "Can She Excuse", 108, 1, 1, 0, "J/John Dowland/Can She Excuse.json"),
            CatalogEntry("John Dowland", "Come Again", 100, 1, 1, 0, "J/John Dowland/Come Again.json"),
            CatalogEntry("John Dowland", "Fantasia", 100, 1, 1, 0, "J/John Dowland/Fantasia.json"),
            CatalogEntry("John Dowland", "Forlorn Hope Fancy", 55, 1, 1, 0, "J/John Dowland/Forlorn Hope Fancy.json"),
            CatalogEntry("John Dowland", "Fortune My Foe", 87, 3, 1, 2, "J/John Dowland/Fortune My Foe.json"),
            CatalogEntry("John Dowland", "Go From My Window", 72, 1, 1, 0, "J/John Dowland/Go From My Window.json"),
            CatalogEntry("John Dowland", "Lachrimae Pavan", 46, 1, 1, 0, "J/John Dowland/Lachrimae Pavan.json"),
            CatalogEntry("John Dowland", "Lady Hunsdon's Puffe", 76, 1, 1, 0, "J/John Dowland/Lady Hunsdon's Puffe.json"),
            CatalogEntry("John Dowland", "Lady Laiton's Almain", 108, 1, 1, 0, "J/John Dowland/Lady Laiton's Almain.json"),
            CatalogEntry("John Dowland", "Melancholy Gaillard", 76, 1, 1, 0, "J/John Dowland/Melancholy Gaillard.json"),
            CatalogEntry("John Dowland", "Mistress Winter's Jump", 120, 1, 1, 0, "J/John Dowland/Mistress Winter_'s Jump.json"),
            CatalogEntry("John Dowland", "Mrs. Winter`s Jump", 120, 1, 1, 0, "J/John Dowland/Mrs Winter_'s Jump.json"),
            CatalogEntry("John Dowland", "Queen Elizabeth's", 88, 1, 1, 0, "J/John Dowland/Queen Elizabeths.json"),
            CatalogEntry("John Dowland", "Queen Elizabeth's Galliard", 94, 1, 1, 0, "J/John Dowland/Queen Elizabeth's Galliard.json"),
            CatalogEntry("John Dowland", "Sir John Smith, His Almain", 112, 1, 1, 0, "J/John Dowland/Sir John Smith His Almain.json"),
            CatalogEntry("John Dowland", "The Frog Galliard", 90, 1, 1, 0, "J/John Dowland/The Frog Galliard.json"),
            CatalogEntry("John Dowland", "The Most Sacred Queen Elisabeth, Her Galliard", 95, 1, 1, 0, "J/John Dowland/The Most Sacred Queen Elisabeth Her Galliard.json"),
            CatalogEntry("John Dowland", "The Shoemaker's Wife. a Toy", 76, 1, 1, 0, "J/John Dowland/The Shoemaker_'s Wife A Toy.json"),
            CatalogEntry("John Dowland", "The Sick Tune", 66, 2, 1, 1, "J/John Dowland/The Sick Tune.json"),
            CatalogEntry("John Dowland", "What If a Day", 82, 1, 1, 0, "J/John Dowland/What If A Day.json"),
            CatalogEntry("John Williams", "Across the Stars", 76, 1, 1, 0, "J/John Williams/Across The Stars.json"),
            CatalogEntry("John Williams", "Battle of the Heroes", 99, 7, 1, 6, "J/John Williams/Battle Of The Heroes.json"),
            CatalogEntry("John Williams", "Cantina Band", 260, 6, 1, 5, "J/John Williams/Cantina Band (Star Wars).json"),
            CatalogEntry("John Williams", "Cavatina", 90, 1, 1, 0, "J/John Williams/Cavatina.json"),
            CatalogEntry("John Williams", "Duel of the Fates", 60, 14, 1, 13, "J/John Williams/Duel Of The Fates.json"),
            CatalogEntry("John Williams", "Imperial March", 80, 5, 2, 3, "J/John Williams/Star Wars Imperial March (Heavy Metal version).json"),
            CatalogEntry("John Williams", "Jurassic Park", 55, 5, 3, 2, "J/John Williams/Jurassic Parc.json"),
            CatalogEntry("John Williams", "May the Force Be With You", 120, 2, 1, 1, "J/John Williams/May The Force Be With You.json"),
            CatalogEntry("John Williams", "Requiem for a Dream", 135, 6, 1, 5, "J/John Williams/Requiem For A Dream.json"),
            CatalogEntry("John Williams", "Schindler's List", 75, 1, 1, 0, "J/John Williams/Schindler's List.json"),
            CatalogEntry("John Williams", "Star Wars", 110, 4, 1, 3, "J/John Williams/Star Wars.json"),
            CatalogEntry("John Williams", "Star Wars Main Theme", 110, 2, 1, 1, "J/John Williams/Star Wars Main Theme.json"),
            CatalogEntry("John Williams", "Star Wars Theme (bass)", 120, 1, 1, 0, "J/John Williams/Star Wars Theme (bass).json"),
            CatalogEntry("John Williams", "Star Wars: May the Force Be With You", 100, 1, 1, 0, "J/John Williams/May the Force Be With You (bass).json"),
            CatalogEntry("John Williams", "Symphonic Suite From Far and Away", 137, 14, 1, 13, "J/John Williams/Symphonic Suite From Far And Away.json"),
            CatalogEntry("John Williams", "The Imperial March", 120, 4, 2, 2, "J/John Williams/The Imperial March (Darth Vader_'s Theme).json"),
            CatalogEntry("John Williams", "The Imperial March (star Wars)", 93, 2, 2, 0, "J/John Williams/The Imperial March (Star Wars).json"),
            CatalogEntry("John Williams", "The Rodrigo Guitar Concerto 2nd Movement (mon Amour)", 60, 1, 1, 0, "J/John Williams/The Rodrigo Guitar Concerto 2nd Movement (Mon Amour).json"),
            CatalogEntry("John Williams", "Welcome to Jurassic Park", 53, 17, 1, 16, "J/John Williams/Jurassic Park Theme.json"),
            CatalogEntry("Leo Brouwer", "Canticum", 120, 2, 1, 1, "L/Leo Brouwer/Canticum.json"),
            CatalogEntry("Leo Brouwer", "El Decameron Negro", 200, 1, 1, 0, "L/Leo Brouwer/El Decameron Negro.json"),
            CatalogEntry("Leo Brouwer", "Eligio de la Danza", 49, 2, 1, 1, "L/Leo Brouwer/Eligio De La Danza.json"),
            CatalogEntry("Leo Brouwer", "Estudio 13", 180, 1, 1, 0, "L/Leo Brouwer/Estudios Sencillos - Estudio 13.json"),
            CatalogEntry("Leo Brouwer", "Hika: in Memoriam Toru Takemitsu", 120, 1, 1, 0, "L/Leo Brouwer/Hika - In Memoriam Toru Takemitsu.json"),
            CatalogEntry("Leo Brouwer", "Music Incidental Campesina (danza)", 110, 2, 2, 0, "L/Leo Brouwer/Music Incidental Campesina (Danza).json"),
            CatalogEntry("Leo Brouwer", "Music Incidental Campesina (finale)", 104, 2, 2, 0, "L/Leo Brouwer/Music Incidental Campesina (Finale).json"),
            CatalogEntry("Leo Brouwer", "Music Incidental Campesina (interludio)", 84, 2, 2, 0, "L/Leo Brouwer/Music Incidental Campesina (Interludio).json"),
            CatalogEntry("Leo Brouwer", "Music Incidental Campesina (preludio)", 104, 2, 2, 0, "L/Leo Brouwer/Music Incidental Campesina (Preludio).json"),
            CatalogEntry("Leo Brouwer", "Ojos Brujos", 120, 1, 1, 0, "L/Leo Brouwer/Ojos Brujos.json"),
            CatalogEntry("Leo Brouwer", "Parabola", 80, 1, 1, 0, "L/Leo Brouwer/Parabola.json"),
            CatalogEntry("Leo Brouwer", "Pieza Sin Titulo No. 3", 100, 1, 1, 0, "L/Leo Brouwer/Pieza Sin Titulo No.3.json"),
            CatalogEntry("Leo Brouwer", "Sonata", 60, 3, 1, 2, "L/Leo Brouwer/Sonata.json"),
            CatalogEntry("Leo Brouwer", "Tarantos", 80, 1, 1, 0, "L/Leo Brouwer/Tarantos.json"),
            CatalogEntry("Leo Brouwer", "Tres Apuntes", 180, 3, 1, 2, "L/Leo Brouwer/Tres Apuntes.json"),
            CatalogEntry("Leo Brouwer", "Um Dia de Noviembro", 78, 1, 1, 0, "L/Leo Brouwer/Um Dia de Noviembro.json"),
            CatalogEntry("Manuel de Falla", "Cancion del Fuego Fatuo", 130, 1, 1, 0, "M/Manuel De Falla/Will O' The Wisp (Cancion Del Fuego Fatuo).json"),
            CatalogEntry("Manuel de Falla", "Miller's Dance", 120, 1, 1, 0, "M/Manuel De Falla/Dance Of The Miller.json"),
            CatalogEntry("Maurice Ravel", "Bolero", 80, 1, 1, 0, "M/Maurice Ravel/Bolero.json"),
            CatalogEntry("Maurice Ravel", "Le Boléro", 120, 4, 1, 3, "M/Maurice Ravel/Le Bolero.json"),
            CatalogEntry("Mauro Giuliani", "32 Easy Pieces for Guitar op. 30", 120, 1, 1, 0, "M/Mauro Giuliani/32 Easy Pieces For Guitar Opus 30.json"),
            CatalogEntry("Mauro Giuliani", "Allegretto", 70, 1, 1, 0, "M/Mauro Giuliani/Allegretto.json"),
            CatalogEntry("Mauro Giuliani", "Allegretto in C", 116, 1, 1, 0, "M/Mauro Giuliani/Allegretto in C.json"),
            CatalogEntry("Mauro Giuliani", "Allegro", 120, 5, 2, 3, "M/Mauro Giuliani/Allegro.json"),
            CatalogEntry("Mauro Giuliani", "Andante in C", 75, 1, 1, 0, "M/Mauro Giuliani/Andante in C.json"),
            CatalogEntry("Mauro Giuliani", "Caprice", 130, 1, 1, 0, "M/Mauro Giuliani/Caprice.json"),
            CatalogEntry("Mauro Giuliani", "Dance Rondo", 100, 1, 1, 0, "M/Mauro Giuliani/Dance Rondo.json"),
            CatalogEntry("Mauro Giuliani", "Etude", 90, 1, 1, 0, "M/Mauro Giuliani/Etude.json"),
            CatalogEntry("Mauro Giuliani", "Grazioso in G", 120, 1, 1, 0, "M/Mauro Giuliani/Grazioso in G.json"),
            CatalogEntry("Mauro Giuliani", "Maestoso", 69, 1, 1, 0, "M/Mauro Giuliani/Maestoso Opus 48.json"),
            CatalogEntry("Mauro Giuliani", "Op. 50 No. 1", 120, 1, 1, 0, "M/Mauro Giuliani/Op. 50 No. 1.json"),
            CatalogEntry("Mauro Giuliani", "Op. 50 No. 3", 120, 1, 1, 0, "M/Mauro Giuliani/Op. 50 No.3.json"),
            CatalogEntry("Mauro Giuliani", "Opera 100 N. 11", 120, 1, 1, 0, "M/Mauro Giuliani/Opera 100 N. 11.json"),
            CatalogEntry("Mauro Giuliani", "Opus 48 ¹5", 71, 1, 1, 0, "M/Mauro Giuliani/Opus 48 Ç5.json"),
            CatalogEntry("Mauro Giuliani", "Study No. 3 / Etude No. 3", 92, 1, 1, 0, "M/Mauro Giuliani/Study No. 3.json"),
            CatalogEntry("Mauro Giuliani", "The Last Rose of Summer (traditional Irish Melody)", 69, 1, 1, 0, "M/Mauro Giuliani/The Last Rose Of Summer.json"),
            CatalogEntry("Mauro Giuliani", "Tirolienne", 108, 1, 1, 0, "M/Mauro Giuliani/Tirolienne.json"),
            CatalogEntry("Mozart", "40th Symphony", 110, 3, 1, 2, "M/Mozart/40th Symphony (Metal Version).json"),
            CatalogEntry("Mozart", "Alla Turca", 200, 3, 3, 0, "M/Mozart/Alla Turca (Acoustic Guitar Version).json"),
            CatalogEntry("Mozart", "Ave Verum Corpus", 120, 4, 1, 3, "M/Mozart/Ave Verum Corpus.json"),
            CatalogEntry("Mozart", "Bourée", 120, 1, 1, 0, "M/Mozart/Bourée.json"),
            CatalogEntry("Mozart", "Concerto", 69, 3, 1, 2, "M/Mozart/Concerto For Clarinet.json"),
            CatalogEntry("Mozart", "Confutatis (heavy Version)", 60, 2, 1, 1, "M/Mozart/Confutatis (Heavy Version).json"),
            CatalogEntry("Mozart", "Dies Irae", 150, 7, 3, 4, "M/Mozart/Dies Irae (Metal Version).json"),
            CatalogEntry("Mozart", "Eine Kleine Nachtmusik", 116, 1, 1, 0, "M/Mozart/Eine Kleine NachtMusik (bass).json"),
            CatalogEntry("Mozart", "Eine Kleine Nachtsmusik", 120, 3, 2, 1, "M/Mozart/Eine Kleine Nachtmusik.json"),
            CatalogEntry("Mozart", "Eine Kliene Nachtmusik (!punk Version!)", 120, 2, 1, 1, "M/Mozart/Eine Kliene Nachtmusik (Punk Version).json"),
            CatalogEntry("Mozart", "La Flûte Enchantée", 154, 15, 1, 14, "M/Mozart/La Flûte Enchantée.json"),
            CatalogEntry("Mozart", "Menuett", 100, 2, 2, 0, "M/Mozart/Menuett.json"),
            CatalogEntry("Mozart", "Mozart's \"alla Turca\"", 127, 2, 1, 1, "M/Mozart/Mozart Alla Turca.json"),
            CatalogEntry("Mozart", "Piano Sonata K.545 1st Movement", 130, 2, 1, 1, "M/Mozart/K.545 1st movement.json"),
            CatalogEntry("Mozart", "Piccola Musica Notturna", 120, 2, 1, 1, "M/Mozart/Piccola Musica Notturna.json"),
            CatalogEntry("Mozart", "Presto", 138, 2, 2, 0, "M/Mozart/Sonata n. 5 in Sol Maggiore - Presto.json"),
            CatalogEntry("Mozart", "Rondo Alla Turca", 120, 2, 1, 1, "M/Mozart/Rondo Alla Torca.json"),
            CatalogEntry("Mozart", "Rondo Alla Turca (electric Guitar Version)", 200, 2, 1, 1, "M/Mozart/Rondo Alla Turca (Electic Guitar Version).json"),
            CatalogEntry("Mozart", "Rondo Alla Turca (tabbed by Vinther)", 132, 3, 3, 0, "M/Mozart/Rondo Alla Turca.json"),
            CatalogEntry("Mozart", "Rondo Alla Turka", 125, 1, 1, 0, "M/Mozart/Rondo Alla Turka.json"),
            CatalogEntry("Mozart", "Rondo Allegro Vivo", 128, 2, 2, 0, "M/Mozart/Rondo Allegro Vivo.json"),
            CatalogEntry("Mozart", "Sinfonia 40", 250, 1, 1, 0, "M/Mozart/Sinfonia 40(1st Mov).json"),
            CatalogEntry("Mozart", "Sinfonia No. 40", 200, 10, 4, 6, "M/Mozart/Sinfonia No. 40.json"),
            CatalogEntry("Mozart", "Sonate", 135, 4, 3, 1, "M/Mozart/Sonate.json"),
            CatalogEntry("Mozart", "Sonate en C", 120, 2, 1, 1, "M/Mozart/Sonate en C Majeur.json"),
            CatalogEntry("Mozart", "Symphonia No. 40", 200, 5, 1, 4, "M/Mozart/Symphonia No. 40.json"),
            CatalogEntry("Mozart", "The Magic Flute - Der Hölle Rache (night Queen)", 150, 7, 1, 6, "M/Mozart/The Magic Flute - Der Hölle Rache (Night Queen).json"),
            CatalogEntry("Mozart", "The Marriage of Figaro", 144, 8, 6, 2, "M/Mozart/The Marriage of Figaro.json"),
            CatalogEntry("Mozart", "Turkish Delight", 120, 2, 1, 1, "M/Mozart/Turkish Delight.json"),
            CatalogEntry("Mozart", "Tyrkisk March", 115, 3, 1, 2, "M/Mozart/Turkish March.json"),
            CatalogEntry("Narciso Yepes", "Jeux Interdits", 100, 4, 1, 3, "N/Narciso Yepes/Jeux Interdits.json"),
            CatalogEntry("Narciso Yepes", "Romance (jeux Interdits)", 120, 1, 1, 0, "N/Narciso Yepes/Romance.json"),
            CatalogEntry("Rodrigo", "Aranjuez", 40, 2, 2, 0, "R/Rodrigo/ARANJUEZ.json"),
            CatalogEntry("Rodrigo", "Aranjuez Guitar Concerto (2nd Movement)", 44, 3, 1, 2, "R/Rodrigo/Aranjuez Guitar Concerto 2nd movement.json"),
            CatalogEntry("Rodrigo", "Cadenza (from Concerto de Aranjuez, Adagio)", 39, 1, 1, 0, "R/Rodrigo/Cadenza (From Concerto De Aranjuez Adagio).json"),
            CatalogEntry("Rodrigo", "Concerto de Aranjuez (adagio)", 39, 1, 1, 0, "R/Rodrigo/Concerto De Aranjuez (Adagio).json"),
            CatalogEntry("Rodrigo", "Concierto de Aranjuez", 120, 1, 1, 0, "R/Rodrigo/Cocierto de aranjuez Allegro con spirito.json"),
            CatalogEntry("Rodrigo", "Concierto de Aranjuez 2 Movimiento", 44, 12, 1, 11, "R/Rodrigo/2do movimiento Concierto de Aranjuez  Adagio.json"),
            CatalogEntry("Rodrigo", "Concierto de Aranjuez Part 2", 44, 13, 2, 11, "R/Rodrigo/Cncierto de Aranjuez Adagio.json"),
            CatalogEntry("Rodrigo", "Concierto de Aranjuez, Adagio", 44, 2, 1, 1, "R/Rodrigo/Concierto de Aranjuez Adagio.json"),
            CatalogEntry("Vivaldi", "4 Saisons \"hiver\" (op.8, N°4)", 88, 5, 4, 1, "V/Vivaldi/4 saisons (hiver op.8 n4).json"),
            CatalogEntry("Ab Irato", "Bass Lydian Chromatic Flunk", 120, 2, 1, 1, "A/Ab Irato/Bass Lydian Chromatic Flunk (true kick).json"),
            CatalogEntry("Advanced Technique Exercises", "Linear Scalar Sequences", 150, 1, 1, 0, "A/Advanced Technique Exercises/Linear Scalar Sequences.json"),
            CatalogEntry("Advanced Technique Exercises", "Two-string Sequences", 150, 1, 1, 0, "A/Advanced Technique Exercises/Two-String Sequences.json"),
            CatalogEntry("Agustin Barrios Mangore", "Estudio de Concierto", 120, 1, 1, 0, "A/Agustin Barrios Mangore/Estudio De Concierto.json"),
            CatalogEntry("Albert Cano", "Estudio Cano", 130, 1, 1, 0, "A/Albert Cano/Estudio Cano.json"),
            CatalogEntry("Aldo Bruno Marchand", "Chromatic Heaven", 120, 2, 1, 1, "A/Aldo Bruno Marchand/Chromatic Heaven.json"),
            CatalogEntry("Andrés Carapia", "Ejercicio de Tapping en Am", 180, 1, 1, 0, "A/Andrés Carapia/Ejercicio de Tapping en Am.json"),
            CatalogEntry("Antonio Rubira", "Estudio", 120, 1, 1, 0, "A/Antonio Rubira/Estudio.json"),
            CatalogEntry("Bass Exercises", "Alexis Slarevski-s Bass Playing Techniques", 120, 1, 1, 0, "B/Bass Exercises/Alexis Slarevski's Bass Playing Techniques Chapter 2.json"),
            CatalogEntry("Bass Exercises", "Bass Playing Techniques", 108, 1, 1, 0, "B/Bass Exercises/Alexis Slarevski's Bass Playing Techniques Chapter 1.json"),
            CatalogEntry("Bass Exercises", "Basse Makossa", 100, 1, 1, 0, "B/Bass Exercises/BASS MAKOSSA (5 exercices).json"),
            CatalogEntry("Bass Exercises", "Cold Waves", 120, 1, 1, 0, "B/Bass Exercises/Cold Waves.json"),
            CatalogEntry("Bass Exercises", "Hardcore Slap Bass", 120, 2, 1, 1, "B/Bass Exercises/Hardcore Slape Bass.json"),
            CatalogEntry("Bass Exercises", "James' Brown Bassists", 120, 1, 1, 0, "B/Bass Exercises/James' Brown Bassists.json"),
            CatalogEntry("Bass Exercises", "Jesienne Liœcie", 130, 1, 1, 0, "B/Bass Exercises/jesienne liscie.json"),
            CatalogEntry("Bass Exercises", "Left Hand Practising Excercises", 120, 1, 1, 0, "B/Bass Exercises/Left Hand Practising Excercises.json"),
            CatalogEntry("Bass Exercises", "Muted G Pops", 120, 1, 1, 0, "B/Bass Exercises/Muted G Pops.json"),
            CatalogEntry("Bass Exercises", "Muting: Ghost Notes", 120, 1, 1, 0, "B/Bass Exercises/Ghost Notes.json"),
            CatalogEntry("Bass Exercises", "Slap Bass With Acoustic Guitar", 120, 3, 1, 2, "B/Bass Exercises/Slap Bass With Acoustic Guitar.json"),
            CatalogEntry("Bass Exercises", "Slap Exercise Am", 93, 1, 1, 0, "B/Bass Exercises/Slap in Am.json"),
            CatalogEntry("Bass Exercises", "Tarentula (8-finger-tapping )", 80, 1, 1, 0, "B/Bass Exercises/Tarentula (8-finger-tapping ).json"),
            CatalogEntry("Bass Exercises", "The Mood", 120, 5, 2, 3, "B/Bass Exercises/The Mood.json"),
            CatalogEntry("Blues Exercises", "A Blues Shuffle", 120, 2, 1, 1, "B/Blues Exercises/A Blues Shuffle.json"),
            CatalogEntry("Blues Exercises", "A Rigging of Blues", 160, 2, 1, 1, "B/Blues Exercises/A Rigging Of Blues.json"),
            CatalogEntry("Blues Exercises", "A Slow Blues in A7", 69, 2, 2, 0, "B/Blues Exercises/A Slow Blues in A7.json"),
            CatalogEntry("Blues Exercises", "Blues 1", 90, 2, 1, 1, "B/Blues Exercises/Blues 1.json"),
            CatalogEntry("Blues Exercises", "Blues 10", 90, 1, 1, 0, "B/Blues Exercises/Blues 10.json"),
            CatalogEntry("Blues Exercises", "Blues 11- Swinging the Blues", 100, 2, 1, 1, "B/Blues Exercises/Blues 11.json"),
            CatalogEntry("Blues Exercises", "Blues 4", 100, 2, 2, 0, "B/Blues Exercises/Blues 4.json"),
            CatalogEntry("Blues Exercises", "Blues 5", 130, 3, 1, 2, "B/Blues Exercises/Blues 5.json"),
            CatalogEntry("Blues Exercises", "Blues 6", 100, 1, 1, 0, "B/Blues Exercises/Blues 6.json"),
            CatalogEntry("Blues Exercises", "Blues 7", 120, 2, 1, 1, "B/Blues Exercises/Blues 7.json"),
            CatalogEntry("Blues Exercises", "Blues 8", 110, 1, 1, 0, "B/Blues Exercises/Blues 8.json"),
            CatalogEntry("Blues Exercises", "Blues 9", 120, 1, 1, 0, "B/Blues Exercises/Blues 9.json"),
            CatalogEntry("Blues Exercises", "Blues Exercises (#1)", 120, 2, 1, 1, "B/Blues Exercises/Easy small.json"),
            CatalogEntry("Blues Exercises", "Blues in a", 76, 2, 1, 1, "B/Blues Exercises/Blues in A.json"),
            CatalogEntry("Blues Exercises", "Blues in G 2", 153, 2, 2, 0, "B/Blues Exercises/Blues in G (other).json"),
            CatalogEntry("Blues Exercises", "Blues Shuffle N Boogie 4/4 E7", 120, 2, 2, 0, "B/Blues Exercises/A Blues Shuffle n Booggie.json"),
            CatalogEntry("Blues Exercises", "Bluse in G", 120, 2, 1, 1, "B/Blues Exercises/Blues In G.json"),
            CatalogEntry("Blues Exercises", "C Minor Blues", 120, 4, 1, 3, "B/Blues Exercises/C Minor 12 Bar Vamp.json"),
            CatalogEntry("Blues Exercises", "Exercício de Escala Pentatônica", 145, 1, 1, 0, "B/Blues Exercises/Exercício de escala pentatônica.json"),
            CatalogEntry("Blues Exercises", "Just-a-easy Loop Blues Accomp in e", 150, 4, 1, 3, "B/Blues Exercises/Blues Loop Accompanniament In E.json"),
            CatalogEntry("Blues Exercises", "Latin Blue (rhythm Guitar)", 100, 1, 1, 0, "B/Blues Exercises/Latin Blue (Rhythm Guitar).json"),
            CatalogEntry("Blues Exercises", "Mirza", 120, 3, 1, 2, "B/Blues Exercises/Mirza - Blues Standart.json"),
            CatalogEntry("Blues Exercises", "Patrones de Blues -- Blues Paterns", 98, 1, 1, 0, "B/Blues Exercises/Blues Paterns.json"),
            CatalogEntry("Blues Exercises", "Shuffle Ride in G", 72, 1, 1, 0, "B/Blues Exercises/Shuffle ride in G.json"),
            CatalogEntry("Blues Exercises", "Trip", 120, 1, 1, 0, "B/Blues Exercises/Trip.json"),
            CatalogEntry("Classic (the)", "Etude in a Minor (napoleon Coste)", 80, 1, 1, 0, "C/Classic (The)/Etude in A minor.json"),
            CatalogEntry("Classical Competition", "Birth of the Star", 130, 1, 1, 0, "C/Classical Competition/Birth Of The Star.json"),
            CatalogEntry("Classical Competition", "Bless", 109, 3, 1, 2, "C/Classical Competition/Bless.json"),
            CatalogEntry("Classical Competition", "Blue Turn to Gray", 120, 3, 3, 0, "C/Classical Competition/Blue Turn To Gray.json"),
            CatalogEntry("Classical Competition", "Cloud Fantasy", 165, 3, 3, 0, "C/Classical Competition/A Cloud Fantasy.json"),
            CatalogEntry("Classical Competition", "Days of Insanity", 90, 1, 1, 0, "C/Classical Competition/Days Of Insanity.json"),
            CatalogEntry("Classical Competition", "Days of Sanity", 80, 1, 1, 0, "C/Classical Competition/Days Of Sanity.json"),
            CatalogEntry("Classical Competition", "Estudio Et Varioso", 96, 2, 1, 1, "C/Classical Competition/Estudio.json"),
            CatalogEntry("Classical Competition", "Ethereal Sojourn", 116, 7, 1, 6, "C/Classical Competition/Ethereal Sojourn.json"),
            CatalogEntry("Classical Competition", "Fuckin' Around in a Minor and Something Else", 120, 4, 1, 3, "C/Classical Competition/Fuckin' Around In A Minor And Something More.json"),
            CatalogEntry("Classical Competition", "Gigue", 140, 1, 1, 0, "C/Classical Competition/Gigue.json"),
            CatalogEntry("Classical Competition", "Howl at Midnight", 80, 9, 1, 8, "C/Classical Competition/Howl At Midnight.json"),
            CatalogEntry("Classical Competition", "Jenjal", 120, 1, 1, 0, "C/Classical Competition/Jenjal.json"),
            CatalogEntry("Classical Competition", "Lost in Silence", 120, 4, 2, 2, "C/Classical Competition/Lost In Silence.json"),
            CatalogEntry("Classical Competition", "Menace", 70, 5, 2, 3, "C/Classical Competition/Menace.json"),
            CatalogEntry("Classical Competition", "Milonga", 120, 2, 2, 0, "C/Classical Competition/Milonga.json"),
            CatalogEntry("Classical Competition", "Mina de Luz", 130, 1, 1, 0, "C/Classical Competition/Mina De Luz.json"),
            CatalogEntry("Classical Competition", "One Last Wish", 90, 7, 1, 6, "C/Classical Competition/One Last Wish.json"),
            CatalogEntry("Classical Competition", "One Love Forever", 190, 4, 1, 3, "C/Classical Competition/1 Love Forever.json"),
            CatalogEntry("Classical Competition", "Orphan", 160, 1, 1, 0, "C/Classical Competition/Orphan.json"),
            CatalogEntry("Classical Competition", "Spiritu Sancti", 120, 5, 4, 1, "C/Classical Competition/Spiritu Sancti.json"),
            CatalogEntry("Classical Competition", "The Jasmine Blooms in the Hands of the Carter", 85, 4, 2, 2, "C/Classical Competition/The Jasmine Blooms In The Hands Of The Carter.json"),
            CatalogEntry("Classical Competition", "Twisted E's", 140, 1, 1, 0, "C/Classical Competition/Twisted E's.json"),
            CatalogEntry("Classical Competition", "Un Simple Pas", 105, 4, 1, 3, "C/Classical Competition/Un Simple Pas.json"),
            CatalogEntry("Classical Competition", "Zorina", 200, 6, 2, 4, "C/Classical Competition/Zorina.json"),
            CatalogEntry("Composers of Msb", "\"the Desolation...", 120, 12, 3, 9, "C/Composers Of MSB/The Desolation.json"),
            CatalogEntry("Composers of Msb", "# Ein Endloser Tag", 120, 5, 2, 3, "C/Composers Of MSB/Ein Endloser Tag.json"),
            CatalogEntry("Composers of Msb", "#1 Stuff", 90, 3, 1, 2, "C/Composers of MSB/Number 1 Stuff.json"),
            CatalogEntry("Composers of Msb", "#1005678", 120, 5, 1, 4, "C/Composers Of MSB/1005678.json"),
            CatalogEntry("Composers of Msb", "#3 My Third Song of Emotion", 89, 6, 3, 3, "C/Composers of MSB/3_ My Third Song Of Emotion.json"),
            CatalogEntry("Composers of Msb", "#67", 145, 6, 2, 4, "C/Composers of MSB/67.json"),
            CatalogEntry("Composers of Msb", "#the Final Simphony", 140, 4, 1, 3, "C/Composers Of MSB/The Final Symphony.json"),
            CatalogEntry("Composers of Msb", "'till Down", 180, 6, 1, 5, "C/Composers of MSB/'Till Down.json"),
            CatalogEntry("Composers of Msb", "24 Bars", 180, 1, 1, 0, "C/Composers Of MSB/24 Bars Of A Minor.json"),
            CatalogEntry("Composers of Msb", "7th February", 120, 5, 4, 1, "C/Composers Of MSB/7th February.json"),
            CatalogEntry("Composers of Msb", "A Black Ligth in the Dark", 180, 5, 3, 2, "C/Composers of MSB/A Black Light In The Dark.json"),
            CatalogEntry("Composers of Msb", "A Blue Dream", 100, 6, 1, 5, "C/Composers Of MSB/A Blue Dream.json"),
            CatalogEntry("Composers of Msb", "A Bright Morning Slide", 140, 6, 1, 5, "C/Composers Of MSB/A Bright Morning Slide.json"),
            CatalogEntry("Composers of Msb", "A Calm Day in the Woods", 120, 2, 1, 1, "C/Composers Of MSB/A Calm Day In The Woods.json"),
            CatalogEntry("Composers of Msb", "A Canção Do Duende Feliz", 130, 8, 1, 7, "C/Composers Of MSB/A Can_o Do Duende Feliz.json"),
            CatalogEntry("Composers of Msb", "A Death Metal Song", 95, 5, 1, 4, "C/Composers Of MSB/A Death Metal Song.json"),
            CatalogEntry("Composers of Msb", "A Grain of Time - the Rule of Lruht: Viking Klingon", 190, 3, 2, 1, "C/Composers Of MSB/A Grain Of Time - The Rule Of Lruht_ Viking Klingon.json"),
            CatalogEntry("Composers of Msb", "A House on the Haunted Hill", 100, 3, 1, 2, "C/Composers Of MSB/A House On The Haunted Hill.json"),
            CatalogEntry("Composers of Msb", "A Laughter at Midnight", 120, 5, 5, 0, "C/Composers Of MSB/A Laughter At Midnight.json"),
            CatalogEntry("Composers of Msb", "A Long Way", 120, 4, 3, 1, "C/Composers Of MSB/A Long Way.json"),
            CatalogEntry("Composers of Msb", "A Lost Soul", 90, 2, 1, 1, "C/Composers Of MSB/A Lost Soul.json"),
            CatalogEntry("Composers of Msb", "A New Beginning", 100, 5, 1, 4, "C/Composers Of MSB/a New Beginning.json"),
            CatalogEntry("Composers of Msb", "A Night on the Sun", 144, 2, 1, 1, "C/Composers Of MSB/A Night On The Sun.json"),
            CatalogEntry("Composers of Msb", "A Recollection", 120, 3, 2, 1, "C/Composers Of MSB/A Recollection.json"),
            CatalogEntry("Composers of Msb", "A Short Song", 150, 3, 2, 1, "C/Composers Of MSB/A Short Song.json"),
            CatalogEntry("Composers of Msb", "A Short Story", 110, 6, 3, 3, "C/Composers Of MSB/A Short Story.json"),
            CatalogEntry("Composers of Msb", "A Song for Anyone", 120, 5, 2, 3, "C/Composers of MSB/A Little Rock Song.json"),
            CatalogEntry("Composers of Msb", "Absent", 140, 3, 1, 2, "C/Composers Of MSB/Absent.json"),
            CatalogEntry("Composers of Msb", "Accordionic Groove Sewing (patches of the Minimalistic)", 205, 5, 2, 3, "C/Composers of MSB/Accordionic Groove Sewing (Patches Of The Minimalistic).json"),
            CatalogEntry("Composers of Msb", "Accordiorganistic 5634", 120, 4, 2, 2, "C/Composers of MSB/Accordiorganistic 5634.json"),
            CatalogEntry("Composers of Msb", "Acoustic", 120, 4, 1, 3, "C/Composers Of MSB/Acoustic.json"),
            CatalogEntry("Composers of Msb", "Acoustic Intermede", 130, 7, 2, 5, "C/Composers Of MSB/Acoustic Intermede.json"),
            CatalogEntry("Composers of Msb", "Acoustic Medlay (nergal)", 100, 3, 1, 2, "C/Composers of MSB/Acoustic Medlay (Nergal).json"),
            CatalogEntry("Composers of Msb", "Acoustic Minor Song", 60, 1, 1, 0, "C/Composers Of MSB/Acoustic Minor Song.json"),
            CatalogEntry("Composers of Msb", "Acoustic Requiem", 100, 4, 4, 0, "C/Composers of MSB/Acoustic Requiem.json"),
            CatalogEntry("Composers of Msb", "Adrenalin", 160, 4, 3, 1, "C/Composers Of MSB/Adrenalin.json"),
            CatalogEntry("Composers of Msb", "Aeingeru's Quest", 80, 12, 1, 11, "C/Composers Of MSB/Aeingeru's Quest.json"),
            CatalogEntry("Composers of Msb", "After the Show - Purgatory", 140, 4, 2, 2, "C/Composers of MSB/After The Show - Purgatory.json"),
            CatalogEntry("Composers of Msb", "Against All", 130, 2, 1, 1, "C/Composers Of MSB/Against All.json"),
            CatalogEntry("Composers of Msb", "Alitaz de Colorez", 200, 3, 2, 1, "C/Composers Of MSB/Alitaz De Colorez.json"),
            CatalogEntry("Composers of Msb", "All Around Me They Gather", 112, 3, 1, 2, "C/Composers of MSB/All Around Me They Gather (But I Know).json"),
            CatalogEntry("Composers of Msb", "Amertume D'une Nuit Sans Vie", 140, 3, 1, 2, "C/Composers Of MSB/Amertume D'une Nuit Sans Vie.json"),
            CatalogEntry("Composers of Msb", "Amplified", 120, 2, 2, 0, "C/Composers Of MSB/Amplified.json"),
            CatalogEntry("Composers of Msb", "An Ode to Aunt Harriet Cooper", 120, 3, 1, 2, "C/Composers Of MSB/An Ode To Aunt Harriet Cooper.json"),
            CatalogEntry("Composers of Msb", "Animal Primacy", 134, 3, 3, 0, "C/Composers Of MSB/Animal Primacy.json"),
            CatalogEntry("Composers of Msb", "Apokaliptyk Saung", 65, 7, 1, 6, "C/Composers Of MSB/Apokaliptyk Saung.json"),
            CatalogEntry("Composers of Msb", "Apokaliptyk Saung Tuu", 130, 7, 1, 6, "C/Composers Of MSB/Apokaliptyk Saung Two.json"),
            CatalogEntry("Composers of Msb", "Arc", 75, 9, 1, 8, "C/Composers Of MSB/ARC.json"),
            CatalogEntry("Composers of Msb", "Atypical (ojnabotokratis)", 110, 3, 1, 2, "C/Composers Of MSB/Atypical (Ojnabotokratis).json"),
            CatalogEntry("Composers of Msb", "Bandito", 120, 4, 2, 2, "C/Composers Of MSB/Bandito.json"),
            CatalogEntry("Composers of Msb", "Be Asdf", 90, 3, 1, 2, "C/Composers Of MSB/Be Asdf.json"),
            CatalogEntry("Composers of Msb", "Beautiful Rain", 125, 4, 3, 1, "C/Composers Of MSB/Beatiful Rain.json"),
            CatalogEntry("Composers of Msb", "Behind the Door", 160, 3, 2, 1, "C/Composers Of MSB/Behind The Door 2.json"),
            CatalogEntry("Composers of Msb", "Betrayal", 120, 5, 3, 2, "C/Composers Of MSB/Betrayal.json"),
            CatalogEntry("Composers of Msb", "Beyond Haedus I", 150, 3, 2, 1, "C/Composers Of MSB/Beyond Haedus I.json"),
            CatalogEntry("Composers of Msb", "Binary Groove", 120, 3, 1, 2, "C/Composers Of MSB/Binary Groove.json"),
            CatalogEntry("Composers of Msb", "Black Angels", 137, 8, 2, 6, "C/Composers of MSB/Red Falcon.json"),
            CatalogEntry("Composers of Msb", "Black Light", 170, 6, 3, 3, "C/Composers Of MSB/Black Light.json"),
            CatalogEntry("Composers of Msb", "Black Rose", 120, 4, 2, 2, "C/Composers of MSB/Black Rose.json"),
            CatalogEntry("Composers of Msb", "Bleeding Heart", 120, 4, 1, 3, "C/Composers Of MSB/Bleeding Heart.json"),
            CatalogEntry("Composers of Msb", "Blinding Darkness", 150, 8, 4, 4, "C/Composers Of MSB/Blinding Darkness.json"),
            CatalogEntry("Composers of Msb", "Braves Fall First", 115, 9, 1, 8, "C/Composers Of MSB/Braves Fall First.json"),
            CatalogEntry("Composers of Msb", "Breathe", 70, 4, 3, 1, "C/Composers of MSB/Breathe.json"),
            CatalogEntry("Composers of Msb", "Brokind", 120, 4, 3, 1, "C/Composers Of MSB/Brokind.json"),
            CatalogEntry("Composers of Msb", "Bull Horn", 120, 3, 1, 2, "C/Composers Of MSB/Bull Horn.json"),
            CatalogEntry("Composers of Msb", "Burning", 78, 5, 1, 4, "C/Composers Of MSB/Burning.json"),
            CatalogEntry("Composers of Msb", "Bush", 116, 5, 1, 4, "C/Composers of MSB/Bush.json"),
            CatalogEntry("Composers of Msb", "Can't Be", 130, 4, 2, 2, "C/Composers Of MSB/Can't Be.json"),
            CatalogEntry("Composers of Msb", "Caramel Honey", 120, 4, 1, 3, "C/Composers of MSB/Caramel Honey.json"),
            CatalogEntry("Composers of Msb", "Celeste", 150, 4, 2, 2, "C/Composers Of MSB/Celeste.json"),
            CatalogEntry("Composers of Msb", "Celestial Culmination", 160, 9, 3, 6, "C/Composers Of MSB/Celestial Culmination.json"),
            CatalogEntry("Composers of Msb", "Christina Aguilera in the Land of the Inabilitatians", 110, 4, 2, 2, "C/Composers Of MSB/Christina Aguilera In The Land Of The Inabilitatians.json"),
            CatalogEntry("Composers of Msb", "Ciprian's Song ; )", 130, 5, 1, 4, "C/Composers Of MSB/Ciprian's Song.json"),
            CatalogEntry("Composers of Msb", "Cocika", 120, 2, 1, 1, "C/Composers Of MSB/Cocika.json"),
            CatalogEntry("Composers of Msb", "Cold Obession", 150, 6, 2, 4, "C/Composers Of MSB/Cold Obsession.json"),
            CatalogEntry("Composers of Msb", "Coming Home", 130, 5, 1, 4, "C/Composers of MSB/Come Home.json"),
            CatalogEntry("Composers of Msb", "Competition Technique", 120, 1, 1, 0, "C/Composers Of MSB/Competition Technique.json"),
            CatalogEntry("Composers of Msb", "Damned Kind", 140, 6, 3, 3, "C/Composers of MSB/Damned Kind.json"),
            CatalogEntry("Composers of Msb", "Dance of Innocence", 104, 5, 2, 3, "C/Composers Of MSB/Dance Of Innocence.json"),
            CatalogEntry("Composers of Msb", "Dark Angel", 101, 7, 1, 6, "C/Composers of MSB/Dark Angel.json"),
            CatalogEntry("Composers of Msb", "Death", 105, 3, 2, 1, "C/Composers Of MSB/Death.json"),
            CatalogEntry("Composers of Msb", "Death I", 105, 3, 2, 1, "C/Composers Of MSB/Death I.json"),
            CatalogEntry("Composers of Msb", "Deep Inside Your Brain", 70, 5, 4, 1, "C/Composers Of MSB/Deep Inside Your Brain.json"),
            CatalogEntry("Composers of Msb", "Demon Accordions", 125, 7, 2, 5, "C/Composers Of MSB/Demon Accordions.json"),
            CatalogEntry("Composers of Msb", "Denogginizer", 120, 2, 1, 1, "C/Composers Of MSB/Denogginizer.json"),
            CatalogEntry("Composers of Msb", "Desde o Início", 120, 3, 2, 1, "C/Composers of MSB/Desde O Início.json"),
            CatalogEntry("Composers of Msb", "Destroy", 120, 4, 2, 2, "C/Composers Of MSB/Destroy.json"),
            CatalogEntry("Composers of Msb", "Destroyer", 160, 3, 1, 2, "C/Composers Of MSB/Destroyer.json"),
            CatalogEntry("Composers of Msb", "Devil's House", 190, 4, 3, 1, "C/Composers of MSB/Devil's House.json"),
            CatalogEntry("Composers of Msb", "Dflat Minor Preludium", 131, 5, 1, 4, "C/Composers Of MSB/D Flat Minor Preludium.json"),
            CatalogEntry("Composers of Msb", "Die !", 180, 4, 3, 1, "C/Composers Of MSB/Die.json"),
            CatalogEntry("Composers of Msb", "Ditching School", 170, 3, 2, 1, "C/Composers Of MSB/Ditching School.json"),
            CatalogEntry("Composers of Msb", "Dodo", 140, 5, 1, 4, "C/Composers Of MSB/Dodo.json"),
            CatalogEntry("Composers of Msb", "Dog, Which Thought It Was Easy to Be a Secret Agent, Broke His Bones.", 120, 5, 2, 3, "C/Composers of MSB/Dog Which Thought It Was Easy To Be A Secret Agent Broke His Bones.json"),
            CatalogEntry("Composers of Msb", "Down There", 212, 7, 2, 5, "C/Composers Of MSB/Down There.json"),
            CatalogEntry("Composers of Msb", "Drag", 120, 5, 2, 3, "C/Composers Of MSB/The Drag.json"),
            CatalogEntry("Composers of Msb", "Drink to Piss", 100, 5, 1, 4, "C/Composers of MSB/Drink To Piss.json"),
            CatalogEntry("Composers of Msb", "Drinking Salt Water", 115, 4, 2, 2, "C/Composers Of MSB/Drinking Salt Water.json"),
            CatalogEntry("Composers of Msb", "Du", 100, 7, 3, 4, "C/Composers Of MSB/Du.json"),
            CatalogEntry("Composers of Msb", "During Darkness", 200, 3, 1, 2, "C/Composers Of MSB/During Darkness.json"),
            CatalogEntry("Composers of Msb", "Egypt", 120, 4, 2, 2, "C/Composers Of MSB/Egypt.json"),
            CatalogEntry("Composers of Msb", "Einsamkeit", 120, 4, 1, 3, "C/Composers Of MSB/Einsamkeit.json"),
            CatalogEntry("Composers of Msb", "Eleminimalisticavation", 105, 5, 4, 1, "C/Composers of MSB/Eleminimalisticavation.json"),
            CatalogEntry("Composers of Msb", "Epic", 120, 5, 2, 3, "C/Composers Of MSB/Epic.json"),
            CatalogEntry("Composers of Msb", "Erwachen", 170, 1, 1, 0, "C/Composers of MSB/Erwachen (10 Finger Tapping).json"),
            CatalogEntry("Composers of Msb", "Et Si?", 82, 6, 1, 5, "C/Composers Of MSB/Et Si_.json"),
            CatalogEntry("Composers of Msb", "Eternal Descent", 120, 6, 2, 4, "C/Composers Of MSB/Eternal Descent.json"),
            CatalogEntry("Composers of Msb", "Etude 1", 120, 4, 2, 2, "C/Composers Of MSB/Etude 1.json"),
            CatalogEntry("Composers of Msb", "Euphoria", 107, 3, 2, 1, "C/Composers of MSB/(Short) Euphoria.json"),
            CatalogEntry("Composers of Msb", "Evora", 125, 12, 1, 11, "C/Composers Of MSB/Evora.json"),
            CatalogEntry("Composers of Msb", "Extreme Crusher", 140, 4, 3, 1, "C/Composers Of MSB/Extreme Crusher.json"),
            CatalogEntry("Composers of Msb", "F.q.w.", 80, 4, 1, 3, "C/Composers of MSB/F.Q.W..json"),
            CatalogEntry("Composers of Msb", "Fabio2", 90, 2, 1, 1, "C/Composers of MSB/Blue.json"),
            CatalogEntry("Composers of Msb", "Far Beyond the Moon", 190, 6, 2, 4, "C/Composers Of MSB/Far Beyond The Moon.json"),
            CatalogEntry("Composers of Msb", "Farewell", 80, 11, 4, 7, "C/Composers Of MSB/Farewell.json"),
            CatalogEntry("Composers of Msb", "Fatal Loss", 110, 4, 1, 3, "C/Composers of MSB/Fatal Loss.json"),
            CatalogEntry("Composers of Msb", "Feel the Flame", 160, 3, 1, 2, "C/Composers Of MSB/Feel The Flame.json"),
            CatalogEntry("Composers of Msb", "Final Night", 126, 3, 1, 2, "C/Composers Of MSB/Final Night.json"),
            CatalogEntry("Composers of Msb", "Fire and Water", 140, 4, 2, 2, "C/Composers Of MSB/Fire And Water.json"),
            CatalogEntry("Composers of Msb", "Fire Extinguisher", 120, 4, 3, 1, "C/Composers of MSB/Fire Extinguisher.json"),
            CatalogEntry("Composers of Msb", "Fire of Death", 125, 3, 2, 1, "C/Composers of MSB/Fire Of Death.json"),
            CatalogEntry("Composers of Msb", "Flaming Water", 140, 3, 2, 1, "C/Composers of MSB/Flaming Water.json"),
            CatalogEntry("Composers of Msb", "Fly Away", 80, 8, 4, 4, "C/Composers Of MSB/Fly Away.json"),
            CatalogEntry("Composers of Msb", "For Betrayer", 69, 6, 3, 3, "C/Composers of MSB/For Betrayer.json"),
            CatalogEntry("Composers of Msb", "Fortune Comes in Vain", 120, 4, 2, 2, "C/Composers of MSB/Fortune Comes In Vain.json"),
            CatalogEntry("Composers of Msb", "Freedom", 95, 6, 3, 3, "C/Composers Of MSB/Liberty 2.json"),
            CatalogEntry("Composers of Msb", "Friday's Sarcasm", 130, 4, 3, 1, "C/Composers Of MSB/Friday's Sarcasm.json"),
            CatalogEntry("Composers of Msb", "From the Bottom of the Sky", 130, 10, 4, 6, "C/Composers Of MSB/From The Bottom Of The Sky.json"),
            CatalogEntry("Composers of Msb", "Fuck Off", 155, 3, 1, 2, "C/Composers Of MSB/Fuck Off.json"),
            CatalogEntry("Composers of Msb", "Fuck the White House", 125, 3, 2, 1, "C/Composers Of MSB/Fuck The White House.json"),
            CatalogEntry("Composers of Msb", "Funky Distraction", 120, 3, 1, 2, "C/Composers of MSB/Funky Distraction.json"),
            CatalogEntry("Composers of Msb", "Fühle Es", 140, 5, 3, 2, "C/Composers Of MSB/Fühle Es.json"),
            CatalogEntry("Composers of Msb", "Gamesbr Theme I", 118, 7, 1, 6, "C/Composers Of MSB/Gamesbr Theme.json"),
            CatalogEntry("Composers of Msb", "Gib Niemals Auf", 150, 3, 1, 2, "C/Composers Of MSB/Gib Niemals Auf.json"),
            CatalogEntry("Composers of Msb", "Girlfriends", 130, 7, 3, 4, "C/Composers Of MSB/Girlfriends.json"),
            CatalogEntry("Composers of Msb", "Greetings Mister President", 180, 4, 1, 3, "C/Composers Of MSB/Greetings Mister President (Let Me Kick You In The Nuts).json"),
            CatalogEntry("Composers of Msb", "H.e.l.g.a.", 105, 3, 2, 1, "C/Composers Of MSB/H.E.L.G.A..json"),
            CatalogEntry("Composers of Msb", "Haloween in Heaven", 120, 2, 1, 1, "C/Composers Of MSB/Halloween In Heaven.json"),
            CatalogEntry("Composers of Msb", "Hammond", 105, 4, 4, 0, "C/Composers Of MSB/Hammond.json"),
            CatalogEntry("Composers of Msb", "Harmonic Misery", 80, 3, 2, 1, "C/Composers Of MSB/Harmonic Misery.json"),
            CatalogEntry("Composers of Msb", "Harpor", 100, 4, 1, 3, "C/Composers Of MSB/Harpor.json"),
            CatalogEntry("Composers of Msb", "Hate Fuss", 120, 4, 3, 1, "C/Composers Of MSB/Hate Fuss.json"),
            CatalogEntry("Composers of Msb", "Hate V 2.5", 93, 6, 2, 4, "C/Composers of MSB/Hate V 2.5.json"),
            CatalogEntry("Composers of Msb", "Having a Bad Day", 120, 3, 2, 1, "C/Composers Of MSB/Having A Bad Day.json"),
            CatalogEntry("Composers of Msb", "Heaven", 125, 4, 3, 1, "C/Composers of MSB/Heaven.json"),
            CatalogEntry("Composers of Msb", "Heavy Stuff", 110, 4, 1, 3, "C/Composers Of MSB/Heavy Stuff.json"),
            CatalogEntry("Composers of Msb", "Hellcome to the Jungle", 180, 3, 2, 1, "C/Composers of MSB/Hellcome To The Jungle.json"),
            CatalogEntry("Composers of Msb", "Hey! Dónde Estás?", 200, 4, 2, 2, "C/Composers Of MSB/Hey D_nde Est_s_.json"),
            CatalogEntry("Composers of Msb", "Hi Diddle Riddle/smack in the Middle", 118, 3, 1, 2, "C/Composers Of MSB/Hi Diddle Riddle - Smack In The Middle.json"),
            CatalogEntry("Composers of Msb", "How Can You Feel?", 115, 5, 1, 4, "C/Composers Of MSB/How Can You Feel_.json"),
            CatalogEntry("Composers of Msb", "I Can't Find My Blues", 100, 4, 2, 2, "C/Composers of MSB/I Can't Find My Blues.json"),
            CatalogEntry("Composers of Msb", "I Don't Know Who I Am", 120, 4, 2, 2, "C/Composers Of MSB/I Don't Know Who I Am.json"),
            CatalogEntry("Composers of Msb", "I Don't Want to Live Forever", 120, 6, 4, 2, "C/Composers Of MSB/I'm Looking For Darkness.json"),
            CatalogEntry("Composers of Msb", "I Hate That Damn William Shatner Mask!", 210, 3, 1, 2, "C/Composers of MSB/I Hate That Damn William Shatner Mask.json"),
            CatalogEntry("Composers of Msb", "I Like It", 132, 5, 3, 2, "C/Composers Of MSB/I Like It.json"),
            CatalogEntry("Composers of Msb", "I Love Msb", 150, 4, 1, 3, "C/Composers Of MSB/MSB Team Are The Kings.json"),
            CatalogEntry("Composers of Msb", "I Wanna My Hair Like Plant's Hair", 85, 4, 3, 1, "C/Composers of MSB/I Wanna My Hair Like Plant's Hair.json"),
            CatalogEntry("Composers of Msb", "I Was Running by the Sea", 75, 6, 4, 2, "C/Composers Of MSB/I Was Running By The Sea.json"),
            CatalogEntry("Composers of Msb", "I Will Survive (solos)", 105, 2, 1, 1, "C/Composers Of MSB/I Will Survive.json"),
            CatalogEntry("Composers of Msb", "I'm Feeling Cool", 130, 6, 1, 5, "C/Composers Of MSB/I'm Feeling Cool.json"),
            CatalogEntry("Composers of Msb", "Immortality Beckons", 144, 6, 1, 5, "C/Composers Of MSB/Immortality Beckons.json"),
            CatalogEntry("Composers of Msb", "In a Stone Casket", 200, 3, 1, 2, "C/Composers Of MSB/In A Stone Casket.json"),
            CatalogEntry("Composers of Msb", "In Front of Reality", 190, 5, 1, 4, "C/Composers of MSB/In Front Of Reality.json"),
            CatalogEntry("Composers of Msb", "In the Mist", 120, 4, 2, 2, "C/Composers Of MSB/In The Mist.json"),
            CatalogEntry("Composers of Msb", "It Is...", 135, 7, 1, 6, "C/Composers Of MSB/It Is.json"),
            CatalogEntry("Composers of Msb", "It's Just a Fight", 120, 4, 2, 2, "C/Composers Of MSB/It's Just A Fight.json"),
            CatalogEntry("Composers of Msb", "Jam Song", 120, 5, 2, 3, "C/Composers Of MSB/Jam Song.json"),
            CatalogEntry("Composers of Msb", "Jammin' Blue", 90, 5, 1, 4, "C/Composers of MSB/Jammim_' Blue.json"),
            CatalogEntry("Composers of Msb", "Jingle", 217, 4, 1, 3, "C/Composers Of MSB/Jingle.json"),
            CatalogEntry("Composers of Msb", "K.n.u.p.", 170, 4, 1, 3, "C/Composers Of MSB/K.N.U.P..json"),
            CatalogEntry("Composers of Msb", "Kali Can Walk Too", 86, 11, 1, 10, "C/Composers Of MSB/Kali Can Walk Too.json"),
            CatalogEntry("Composers of Msb", "Karamelo de Limón", 240, 4, 1, 3, "C/Composers Of MSB/Karamelo De Lim_n.json"),
            CatalogEntry("Composers of Msb", "Kill or Be Killed", 200, 4, 2, 2, "C/Composers Of MSB/Kill Or Be Killed.json"),
            CatalogEntry("Composers of Msb", "Knights of Rouds", 100, 2, 1, 1, "C/Composers Of MSB/Knights Of Round.json"),
            CatalogEntry("Composers of Msb", "Kristina", 120, 5, 1, 4, "C/Composers Of MSB/Kristina.json"),
            CatalogEntry("Composers of Msb", "Let Me Go Away (the Waterfall)", 120, 5, 3, 2, "C/Composers Of MSB/Let Me Go Away (The Waterfall).json"),
            CatalogEntry("Composers of Msb", "Lex Talionis", 100, 5, 1, 4, "C/Composers Of MSB/Lex Talionis.json"),
            CatalogEntry("Composers of Msb", "Liberty", 105, 4, 2, 2, "C/Composers Of MSB/Liberty.json"),
            CatalogEntry("Composers of Msb", "Light", 120, 4, 3, 1, "C/Composers of MSB/Light.json"),
            CatalogEntry("Composers of Msb", "Like a Led Zeppelin", 100, 3, 2, 1, "C/Composers Of MSB/Down Like A Led Zeppelin.json"),
            CatalogEntry("Composers of Msb", "Liquid Life", 87, 3, 2, 1, "C/Composers Of MSB/Liquid Life.json"),
            CatalogEntry("Composers of Msb", "Lost Idol", 70, 4, 1, 3, "C/Composers of MSB/Lost Idol.json"),
            CatalogEntry("Composers of Msb", "Luring in E-moll", 70, 1, 1, 0, "C/Composers of MSB/Luring In Emoll (At The Tavern).json"),
            CatalogEntry("Composers of Msb", "Luz en el Vacio", 160, 3, 2, 1, "C/Composers Of MSB/Luz En El Vacio.json"),
            CatalogEntry("Composers of Msb", "Majesty Murshrooms", 130, 3, 3, 0, "C/Composers of MSB/Majesty Mushrooms.json"),
            CatalogEntry("Composers of Msb", "Make It Rock", 174, 3, 2, 1, "C/Composers Of MSB/Make It Rock.json"),
            CatalogEntry("Composers of Msb", "March of Madness", 120, 3, 1, 2, "C/Composers Of MSB/The March Of Madness.json"),
            CatalogEntry("Composers of Msb", "Marie's Odyssey", 120, 8, 3, 5, "C/Composers Of MSB/Marie's Odyssey.json"),
            CatalogEntry("Composers of Msb", "Master of Dreams", 130, 5, 3, 2, "C/Composers Of MSB/Act I Master Of Dreams.json"),
            CatalogEntry("Composers of Msb", "Melancholy", 90, 4, 2, 2, "C/Composers Of MSB/Melancholy.json"),
            CatalogEntry("Composers of Msb", "Melodic", 190, 3, 2, 1, "C/Composers Of MSB/Melodic.json"),
            CatalogEntry("Composers of Msb", "Melodic Ii", 190, 4, 2, 2, "C/Composers Of MSB/Melodic Ii.json"),
            CatalogEntry("Composers of Msb", "Melodic Iii", 200, 4, 2, 2, "C/Composers Of MSB/Melodic III.json"),
            CatalogEntry("Composers of Msb", "Monolith", 120, 3, 1, 2, "C/Composers Of MSB/Monolith.json"),
            CatalogEntry("Composers of Msb", "Moonlight Shadow", 80, 5, 1, 4, "C/Composers of MSB/Moonlight Shadow.json"),
            CatalogEntry("Composers of Msb", "Muadros", 100, 6, 3, 3, "C/Composers of MSB/Muadros.json"),
            CatalogEntry("Composers of Msb", "Nada Mas Que Otra Cosa", 150, 3, 2, 1, "C/Composers Of MSB/Nada Mas Que Otra Cosa.json"),
            CatalogEntry("Composers of Msb", "Narrata Reffero", 130, 8, 3, 5, "C/Composers Of MSB/Narrata Reffero (I Pass Told).json"),
            CatalogEntry("Composers of Msb", "Narthex", 120, 5, 3, 2, "C/Composers of MSB/Narthex Variation.json"),
            CatalogEntry("Composers of Msb", "Navarone", 120, 6, 1, 5, "C/Composers Of MSB/Navarone.json"),
            CatalogEntry("Composers of Msb", "Neurosis", 100, 5, 2, 3, "C/Composers Of MSB/Neurosis.json"),
            CatalogEntry("Composers of Msb", "Never Knowing", 160, 4, 1, 3, "C/Composers of MSB/Never Knowing.json"),
            CatalogEntry("Composers of Msb", "No Name", 110, 3, 3, 0, "C/Composers Of MSB/No Name.json"),
            CatalogEntry("Composers of Msb", "Nothing Is Left", 85, 4, 3, 1, "C/Composers of MSB/Nothing Is Left.json"),
            CatalogEntry("Composers of Msb", "Number 4", 190, 10, 3, 7, "C/Composers Of MSB/Number 4.json"),
            CatalogEntry("Composers of Msb", "Number the Stars", 120, 5, 2, 3, "C/Composers Of MSB/The Stars.json"),
            CatalogEntry("Composers of Msb", "Nunca Mas", 160, 3, 1, 2, "C/Composers Of MSB/Nunca Mas.json"),
            CatalogEntry("Composers of Msb", "Ohp", 85, 4, 3, 1, "C/Composers Of MSB/Ohp.json"),
            CatalogEntry("Composers of Msb", "One Day in Hell", 150, 4, 3, 1, "C/Composers Of MSB/One Day In Hell.json"),
            CatalogEntry("Composers of Msb", "One Love Forever", 190, 4, 1, 3, "C/Composers of MSB/1 Love Forever.json"),
            CatalogEntry("Composers of Msb", "Open Sky", 140, 4, 2, 2, "C/Composers Of MSB/Open Sky.json"),
            CatalogEntry("Composers of Msb", "Otacon", 115, 4, 3, 1, "C/Composers Of MSB/Otacon.json"),
            CatalogEntry("Composers of Msb", "Pan Con Palta (y Bien Chorreada)", 190, 8, 2, 6, "C/Composers Of MSB/Pan Con Palta (Y Bien Chorreada).json"),
            CatalogEntry("Composers of Msb", "Panic", 120, 3, 2, 1, "C/Composers Of MSB/Panic.json"),
            CatalogEntry("Composers of Msb", "Paranoid(live)", 160, 4, 2, 2, "C/Composers of MSB/Paranoid.json"),
            CatalogEntry("Composers of Msb", "Paws of Steel", 144, 5, 4, 1, "C/Composers Of MSB/Paws Of Steel.json"),
            CatalogEntry("Composers of Msb", "Pig Corpse Is Left to Rot", 120, 6, 4, 2, "C/Composers of MSB/Pig Corpse Is Left To Rot.json"),
            CatalogEntry("Composers of Msb", "Pink Marshmallows", 230, 3, 2, 1, "C/Composers Of MSB/Pink Marshmallows.json"),
            CatalogEntry("Composers of Msb", "Pink Panther", 120, 2, 1, 1, "C/Composers of MSB/The Pink Panther (Ska Version).json"),
            CatalogEntry("Composers of Msb", "Podoboo", 150, 4, 1, 3, "C/Composers Of MSB/Podoboo.json"),
            CatalogEntry("Composers of Msb", "Poison", 150, 7, 5, 2, "C/Composers Of MSB/Poison.json"),
            CatalogEntry("Composers of Msb", "Power", 190, 4, 2, 2, "C/Composers Of MSB/Power.json"),
            CatalogEntry("Composers of Msb", "Pravda", 102, 4, 3, 1, "C/Composers Of MSB/Pravda.json"),
            CatalogEntry("Composers of Msb", "Prelude and Tango for Piano", 80, 4, 1, 3, "C/Composers Of MSB/Prelude And Tango For Piano.json"),
            CatalogEntry("Composers of Msb", "Project 2", 122, 4, 1, 3, "C/Composers Of MSB/Project 2.json"),
            CatalogEntry("Composers of Msb", "Psychedelic Girl", 120, 3, 2, 1, "C/Composers of MSB/Psychedelic Girl.json"),
            CatalogEntry("Composers of Msb", "Punk Song", 200, 3, 2, 1, "C/Composers of MSB/Punk Song.json"),
            CatalogEntry("Composers of Msb", "Quand Je Marche", 100, 7, 2, 5, "C/Composers Of MSB/Quand Je Marche.json"),
            CatalogEntry("Composers of Msb", "Quizas Podriamos....", 160, 3, 1, 2, "C/Composers Of MSB/Quizas Podriamos.json"),
            CatalogEntry("Composers of Msb", "Razor", 115, 7, 2, 5, "C/Composers Of MSB/Razor.json"),
            CatalogEntry("Composers of Msb", "Ready to Kill", 170, 6, 5, 1, "C/Composers Of MSB/Ready To Kill.json"),
            CatalogEntry("Composers of Msb", "Recrudescing", 160, 8, 5, 3, "C/Composers of MSB/Recrudescing.json"),
            CatalogEntry("Composers of Msb", "Reggae-punk", 120, 6, 2, 4, "C/Composers Of MSB/Reagge.json"),
            CatalogEntry("Composers of Msb", "Remembrance/awakening Anew", 70, 7, 2, 5, "C/Composers of MSB/Rememberance_Awakening Anew.json"),
            CatalogEntry("Composers of Msb", "Rest Your Head on Me", 50, 5, 2, 3, "C/Composers of MSB/Rest Your Head On Me.json"),
            CatalogEntry("Composers of Msb", "Rytmic Hallucination", 85, 9, 2, 7, "C/Composers of MSB/Rythmic Hallucination.json"),
            CatalogEntry("Composers of Msb", "Savior Sephiroth", 120, 2, 1, 1, "C/Composers Of MSB/Savior Sephiroth.json"),
            CatalogEntry("Composers of Msb", "Seven Steps of Darkness (ñåìü Ñòóïåíåé Òüìû)", 194, 5, 1, 4, "C/Composers Of MSB/Seven Steps Of Darkness.json"),
            CatalogEntry("Composers of Msb", "Sherry", 99, 8, 2, 6, "C/Composers Of MSB/Cherry.json"),
            CatalogEntry("Composers of Msb", "Shorty", 120, 3, 2, 1, "C/Composers Of MSB/Shorty.json"),
            CatalogEntry("Composers of Msb", "Silence", 73, 4, 4, 0, "C/Composers of MSB/Silence.json"),
            CatalogEntry("Composers of Msb", "Sirens", 120, 7, 1, 6, "C/Composers Of MSB/Sirens.json"),
            CatalogEntry("Composers of Msb", "Snow in November", 110, 7, 5, 2, "C/Composers Of MSB/Snow In November.json"),
            CatalogEntry("Composers of Msb", "Soberr 82 4", 90, 3, 1, 2, "C/Composers Of MSB/Soberr82_4.json"),
            CatalogEntry("Composers of Msb", "Soberr 92 2", 160, 3, 1, 2, "C/Composers Of MSB/Soberr92_2.json"),
            CatalogEntry("Composers of Msb", "Softly", 125, 4, 4, 0, "C/Composers Of MSB/Softly.json"),
            CatalogEntry("Composers of Msb", "Spirits Around Me", 125, 7, 3, 4, "C/Composers Of MSB/Spirits Around Me.json"),
            CatalogEntry("Composers of Msb", "Spockaholic", 154, 2, 1, 1, "C/Composers Of MSB/Spockaholic.json"),
            CatalogEntry("Composers of Msb", "Stefan", 150, 4, 2, 2, "C/Composers Of MSB/Stefan.json"),
            CatalogEntry("Composers of Msb", "Study #14", 110, 7, 1, 6, "C/Composers of MSB/Study 14.json"),
            CatalogEntry("Composers of Msb", "Sudden Trap", 166, 4, 2, 2, "C/Composers Of MSB/Sudden Trap.json"),
            CatalogEntry("Composers of Msb", "Sunrise", 90, 7, 1, 6, "C/Composers Of MSB/Sunrise.json"),
            CatalogEntry("Composers of Msb", "Suspended", 100, 7, 1, 6, "C/Composers of MSB/Suspended.json"),
            CatalogEntry("Composers of Msb", "Sustain", 165, 4, 3, 1, "C/Composers Of MSB/Sustain.json"),
            CatalogEntry("Composers of Msb", "Sux", 120, 2, 1, 1, "C/Composers of MSB/Untitled Song.json"),
            CatalogEntry("Composers of Msb", "Symphozik", 115, 7, 2, 5, "C/Composers Of MSB/Symphozik.json"),
            CatalogEntry("Composers of Msb", "Talk Trash, Get Smacked", 150, 4, 2, 2, "C/Composers Of MSB/Talk Trash Get Smacked.json"),
            CatalogEntry("Composers of Msb", "Temple of Forest", 120, 10, 1, 9, "C/Composers Of MSB/Temple Of Forest.json"),
            CatalogEntry("Composers of Msb", "Temple of Ice", 120, 8, 2, 6, "C/Composers Of MSB/Temple Of Ice.json"),
            CatalogEntry("Composers of Msb", "Terminal", 120, 3, 1, 2, "C/Composers Of MSB/Terminal.json"),
            CatalogEntry("Composers of Msb", "The 11th War of Morrrrdor", 93, 9, 1, 8, "C/Composers of MSB/The 11th War of Morrrrdor.json"),
            CatalogEntry("Composers of Msb", "The Asdf Song", 170, 3, 2, 1, "C/Composers Of MSB/Asdf.json"),
            CatalogEntry("Composers of Msb", "The Birthday of the Clown", 120, 4, 1, 3, "C/Composers Of MSB/The Birthday Of The Clown.json"),
            CatalogEntry("Composers of Msb", "The Book of Struggles", 95, 9, 2, 7, "C/Composers Of MSB/The Book Of Struggles.json"),
            CatalogEntry("Composers of Msb", "The Breath of the Soul", 95, 4, 2, 2, "C/Composers Of MSB/Breath Of The Soul.json"),
            CatalogEntry("Composers of Msb", "The Dancing Song", 95, 1, 1, 0, "C/Composers Of MSB/The Dancing Song.json"),
            CatalogEntry("Composers of Msb", "The Death of the Deer", 95, 6, 3, 3, "C/Composers Of MSB/The Death Of The Deer.json"),
            CatalogEntry("Composers of Msb", "The Ethereal Declaration", 160, 10, 3, 7, "C/Composers Of MSB/The Ethereal Declaration.json"),
            CatalogEntry("Composers of Msb", "The Great Journey", 120, 3, 2, 1, "C/Composers Of MSB/The Great Journey.json"),
            CatalogEntry("Composers of Msb", "The Great Maiden Voyage", 120, 3, 2, 1, "C/Composers Of MSB/The Great Maiden Voyage.json"),
            CatalogEntry("Composers of Msb", "The Hole of Nightmares", 120, 7, 1, 6, "C/Composers Of MSB/The Hole OF Nightmares.json"),
            CatalogEntry("Composers of Msb", "The Hunt of Black Swan", 150, 6, 3, 3, "C/Composers of MSB/Hunt Of The Black Swan.json"),
            CatalogEntry("Composers of Msb", "The Jasmine Blooms in the Hands of the Carter", 85, 4, 2, 2, "C/Composers of MSB/A Jasmine Blooms In The Hands Of The Carter.json"),
            CatalogEntry("Composers of Msb", "The Joke", 80, 4, 1, 3, "C/Composers Of MSB/The Joke.json"),
            CatalogEntry("Composers of Msb", "The Lopper", 120, 2, 1, 1, "C/Composers Of MSB/The Lopper.json"),
            CatalogEntry("Composers of Msb", "The Man of Steel and Stone", 116, 4, 4, 0, "C/Composers Of MSB/The Man Of Steel And Stone.json"),
            CatalogEntry("Composers of Msb", "The Rider", 120, 3, 1, 2, "C/Composers of MSB/The Rider.json"),
            CatalogEntry("Composers of Msb", "The Small City Remais the Same", 150, 3, 3, 0, "C/Composers of MSB/The Small City Remais The Same.json"),
            CatalogEntry("Composers of Msb", "The Tritrooks of the Lobanza", 120, 3, 2, 1, "C/Composers of MSB/The Tri-trooks Of The Lobanza.json"),
            CatalogEntry("Composers of Msb", "This Beautiful Damage", 60, 7, 2, 5, "C/Composers Of MSB/This Beautiful Damage.json"),
            CatalogEntry("Composers of Msb", "Thrash", 110, 2, 1, 1, "C/Composers Of MSB/Thrash.json"),
            CatalogEntry("Composers of Msb", "Thrash I", 110, 2, 1, 1, "C/Composers Of MSB/Thrash I.json"),
            CatalogEntry("Composers of Msb", "Thrash Ii", 200, 3, 2, 1, "C/Composers Of MSB/Thrash II.json"),
            CatalogEntry("Composers of Msb", "Thrash Punk", 105, 3, 1, 2, "C/Composers Of MSB/Thrash Punk.json"),
            CatalogEntry("Composers of Msb", "Thrazz", 120, 4, 1, 3, "C/Composers of MSB/Thrazzy.json"),
            CatalogEntry("Composers of Msb", "Through the Valley", 135, 9, 4, 5, "C/Composers Of MSB/Through The Valley.json"),
            CatalogEntry("Composers of Msb", "Throwing the Blackrose Into Nightmares", 150, 4, 1, 3, "C/Composers Of MSB/Throwing The Blackrose Into N&305ghtmares.json"),
            CatalogEntry("Composers of Msb", "Top Secret", 120, 4, 2, 2, "C/Composers Of MSB/(Very) Top Secret.json"),
            CatalogEntry("Composers of Msb", "Transcience", 80, 9, 2, 7, "C/Composers Of MSB/Transcience.json"),
            CatalogEntry("Composers of Msb", "Tristesse", 120, 4, 1, 3, "C/Composers Of MSB/La Tristesse.json"),
            CatalogEntry("Composers of Msb", "Tropico", 136, 2, 2, 0, "C/Composers Of MSB/Tropico.json"),
            CatalogEntry("Composers of Msb", "Tupperwar", 120, 5, 1, 4, "C/Composers of MSB/Tupperwar.json"),
            CatalogEntry("Composers of Msb", "Tyromidias Melody", 130, 2, 1, 1, "C/Composers Of MSB/Tyromidias Melody.json"),
            CatalogEntry("Composers of Msb", "Ultraviolence", 220, 1, 1, 0, "C/Composers Of MSB/Ultraviolence.json"),
            CatalogEntry("Composers of Msb", "Un Mundo de Colores", 200, 3, 1, 2, "C/Composers Of MSB/Un Mundo De Colores.json"),
            CatalogEntry("Composers of Msb", "Undefined", 160, 6, 4, 2, "C/Composers Of MSB/Undefined.json"),
            CatalogEntry("Composers of Msb", "Under My Starlight", 87, 2, 1, 1, "C/Composers Of MSB/Under My Starlight.json"),
            CatalogEntry("Composers of Msb", "Underinfluenced", 160, 4, 4, 0, "C/Composers of MSB/Underinfluenced.json"),
            CatalogEntry("Composers of Msb", "Until It Die's", 120, 4, 1, 3, "C/Composers Of MSB/Until It Die's.json"),
            CatalogEntry("Composers of Msb", "Van Slooten", 100, 7, 3, 4, "C/Composers Of MSB/Van Slooten.json"),
            CatalogEntry("Composers of Msb", "Vera, Nadezhda, Lyubov'", 80, 5, 2, 3, "C/Composers Of MSB/Vera Nadezhda Lyubov'.json"),
            CatalogEntry("Composers of Msb", "W.a.r. (warriors Are Ready)", 115, 4, 2, 2, "C/Composers of MSB/W.A.R. (Warriors Are Ready).json"),
            CatalogEntry("Composers of Msb", "Waiting for You", 120, 6, 6, 0, "C/Composers of MSB/Waiting For You.json"),
            CatalogEntry("Composers of Msb", "Wann ?", 80, 5, 1, 4, "C/Composers Of MSB/Wann_.json"),
            CatalogEntry("Composers of Msb", "War of Shadows", 150, 4, 2, 2, "C/Composers of MSB/War Of Shadows.json"),
            CatalogEntry("Composers of Msb", "Waves", 56, 3, 1, 2, "C/Composers Of MSB/Waves.json"),
            CatalogEntry("Composers of Msb", "What It's Like?", 80, 5, 1, 4, "C/Composers Of MSB/What It's Like _.json"),
            CatalogEntry("Composers of Msb", "When the Tears Never Dry", 160, 4, 1, 3, "C/Composers Of MSB/When The Tears Never Dry.json"),
            CatalogEntry("Composers of Msb", "Whiskey Sour's and Minuature Macanudo's", 140, 3, 1, 2, "C/Composers Of MSB/Whiskey Sour's And Miniature Macanudo's.json"),
            CatalogEntry("Composers of Msb", "Why", 80, 4, 2, 2, "C/Composers Of MSB/Why.json"),
            CatalogEntry("Composers of Msb", "Why Children Have to Live Under the Bullet Sky ?", 100, 5, 3, 2, "C/Composers Of MSB/Why Children Have To Live Under The Bullet Sky.json"),
            CatalogEntry("Composers of Msb", "Wilderness", 138, 7, 2, 5, "C/Composers of MSB/Wilderness.json"),
            CatalogEntry("Composers of Msb", "Windmill", 110, 4, 1, 3, "C/Composers of MSB/Windmill.json"),
            CatalogEntry("Composers of Msb", "Wine of Hope", 120, 5, 3, 2, "C/Composers Of MSB/A Promise Of Hope.json"),
            CatalogEntry("Composers of Msb", "Wishes for the Rainbow", 110, 4, 1, 3, "C/Composers of MSB/Wishes For The Rainbow.json"),
            CatalogEntry("Composers of Msb", "Work in Progress", 109, 4, 1, 3, "C/Composers Of MSB/Work In Progress.json"),
            CatalogEntry("Composers of Msb", "Wtwstl", 140, 6, 1, 5, "C/Composers Of MSB/WTWSTL.json"),
            CatalogEntry("Composers of Msb", "Xchichox", 120, 1, 1, 0, "C/Composers of MSB/Sweet Song.json"),
            CatalogEntry("Composers of Msb", "Yao", 120, 9, 1, 8, "C/Composers Of MSB/Yao.json"),
            CatalogEntry("Composers of Msb", "Yat", 130, 4, 1, 3, "C/Composers Of MSB/Yat.json"),
            CatalogEntry("Composers of Msb", "You and Me", 120, 4, 3, 1, "C/Composers Of MSB/You And Me.json"),
            CatalogEntry("Composers of Msb", "Za Oknami Tvoih Sten", 75, 8, 4, 4, "C/Composers Of MSB/Za Oknami Tvoih Sten.json"),
            CatalogEntry("Composers of Msb", "Zapuseala (stifling Heat)", 114, 3, 1, 2, "C/Composers of MSB/Zapuseala (Stifling Heat).json"),
            CatalogEntry("Cool Exercise", "\"play Arpegios With Two Guitars\"", 80, 2, 2, 0, "C/Cool Exercise/Two guitars arpegios.json"),
            CatalogEntry("Cool Exercise", "Hot Exerice", 100, 1, 1, 0, "C/Cool Exercise/Freak speed.json"),
            CatalogEntry("Cool Exercise", "Learning", 120, 1, 1, 0, "C/Cool Exercise/learning.json"),
            CatalogEntry("Cool Exercise", "Out of the Dark", 200, 1, 1, 0, "C/Cool Exercise/Out Of The Dark.json"),
            CatalogEntry("Cool Exercise", "Sweeping, Weird Timing and Pick Technique", 150, 1, 1, 0, "C/Cool Exercise/Sweeping Weird Timing and Pick Technique.json"),
            CatalogEntry("Cool Exercise", "Tapping", 240, 1, 1, 0, "C/Cool Exercise/tapping.json"),
            CatalogEntry("Demo", "Solnishko (heavy Version)", 140, 4, 2, 2, "D/Demo/Solnishko.json"),
            CatalogEntry("Drum Exercises", "Drum Lesson 1", 118, 0, 1, 0, "D/Drum Exercises/Demonstration 1.json"),
            CatalogEntry("Drum Exercises", "Drum Lesson 4", 170, 0, 1, 0, "D/Drum Exercises/Demonstration 4.json"),
            CatalogEntry("Eric Genevois", "Estudio À Louer", 120, 2, 1, 1, "E/eric genevois/Estudio à louer.json"),
            CatalogEntry("Eric Genevois", "Etude Pour Un Notaire", 120, 1, 1, 0, "E/eric genevois/Etude pour un notaire.json"),
            CatalogEntry("Finger and Picking Exercise", "Fingerstyle Basics", 120, 1, 1, 0, "F/Finger And Picking Exercise/Fingerstyle Basics.json"),
            CatalogEntry("Gerardo Nunez", "Estudio Para Pulgar e Indice", 228, 1, 1, 0, "G/Gerardo Nunez/Estudio para pulgar E indice.json"),
            CatalogEntry("Guitar Classique", "Arpegio C", 80, 1, 1, 0, "G/Guitar Classique/arpegio_c.json"),
            CatalogEntry("Guitar Pro", "Guitar Pro Heavy Metal Verision (2)", 150, 5, 2, 3, "G/Guitar Pro/Guitar Pro Heavy Metal Version.json"),
            CatalogEntry("Heitor Villa Lobos", "Etude No. 2", 120, 1, 1, 0, "H/Heitor Villa Lobos/Etude Nº2.json"),
            CatalogEntry("Ivanov-kramskoy", "Etude", 120, 1, 1, 0, "I/Ivanov-Kramskoy/Etude.json"),
            CatalogEntry("Jaco Pastorius", "Chromatic Fantasy", 76, 1, 1, 0, "J/Jaco Pastorius/Chromatic Fantasy (bass).json"),
            CatalogEntry("John Petrucci", "Etude in a Minor, Opus 10, No. 2", 155, 2, 1, 1, "J/John Petrucci/Etude in A minor Opus 10 no. 2.json"),
            CatalogEntry("John Petrucci", "Rock Discipline String Skipping Exercise", 110, 1, 1, 0, "J/John Petrucci/Rock Discipline Left And Right Hand.json"),
            CatalogEntry("John Petrucci", "Speed Lesson", 120, 1, 1, 0, "J/John Petrucci/speed and tehnique.json"),
            CatalogEntry("Julio Sagreras", "Etude (tremolo)", 79, 1, 1, 0, "J/Julio Sagreras/Etude (tremolo).json"),
            CatalogEntry("Leo Brower", "Estudio Sencillos", 120, 1, 1, 0, "L/Leo Brower/estudio_sencillos.json"),
            CatalogEntry("Leo Brower", "Etude 6", 130, 1, 1, 0, "L/Leo Brower/Etude 6.json"),
            CatalogEntry("Leo Brower", "Etude 9", 110, 1, 1, 0, "L/Leo Brower/Etude 9.json"),
            CatalogEntry("Mamo Mimi", "Estudio Ii", 91, 1, 1, 0, "M/mamo mimi/Flamenco Study.json"),
            CatalogEntry("Mamo Mimi", "Estudio Iv", 154, 1, 1, 0, "M/mamo mimi/flamenco study4.json"),
            CatalogEntry("Mamo Mimi", "Estudio V", 69, 1, 1, 0, "M/mamo mimi/study5.json"),
            CatalogEntry("Manuel Granados", "Estudio Trémolo", 120, 1, 1, 0, "M/Manuel Granados/Estudio Tremolo.json"),
            CatalogEntry("Mateo Carcassi", "Etude Opus 60 N°7", 120, 4, 1, 3, "M/Mateo Carcassi/Etude op 60 no 7.json"),
            CatalogEntry("N.e.r.d", "Drill Sargent", 140, 3, 1, 2, "N/N.E.R.D/Drill Sargent.json"),
            CatalogEntry("Rock Licks", "Legato Speed Picking Lesson 3", 120, 1, 1, 0, "R/Rock Licks/Legato Speed Techniques.json"),
            CatalogEntry("Shredding Exercises", "String Skipping and Picking Exercise", 210, 1, 1, 0, "S/Shredding Exercises/String Skipping And Picking.json"),
            CatalogEntry("Sokol", "Northern Etude", 130, 6, 2, 4, "S/Sokol/Northern Etude.json"),
            CatalogEntry("Stephen White", "The White Etude", 120, 1, 1, 0, "S/Stephen White/The White Etude.json"),
            CatalogEntry("Supercrado3000", "Etude N1", 120, 2, 1, 1, "S/Supercrado3000/Etude n1.json"),
            CatalogEntry("Supercrado3000", "Etude N2", 120, 3, 1, 2, "S/Supercrado3000/Etude n2.json"),
            CatalogEntry("Sweep Excercises", "Sweep Exercise", 140, 1, 1, 0, "S/Sweep Excercises/Sweep Excercise.json"),
            CatalogEntry("Sweep Excercises", "Sweep Exercise. 3 Strings. V1.1", 100, 1, 1, 0, "S/Sweep Excercises/3 String Sweeping.json"),
            CatalogEntry("Triumph", "Petite Etude", 110, 1, 1, 0, "T/Triumph/Petite Etude.json"),
            CatalogEntry("Violin Lesson", "Advanced Bowing Exercise", 100, 2, 1, 1, "V/Violin Lesson/Advanced Bowing 1.json"),
            CatalogEntry("Yngwie Malmsteen", "Echo Etude", 150, 1, 1, 0, "Y/Yngwie Malmsteen/Echo Etude.json"),
            CatalogEntry("Anonymous", "Alecrim", 120, 1, 1, 0, "A/Anonymous/Alecrim.json"),
            CatalogEntry("Anonymous", "Caballeria Song", 120, 2, 1, 1, "A/Anonymous/Caballeria Song.json"),
            CatalogEntry("Anonymous", "Cancao", 120, 3, 1, 2, "A/Anonymous/Cancao.json"),
            CatalogEntry("Anonymous", "Czech Medley", 100, 1, 1, 0, "A/Anonymous/Czech Medley.json"),
            CatalogEntry("Anonymous", "Duelling Banjos", 130, 2, 1, 1, "A/Anonymous/Duelling banjos.json"),
            CatalogEntry("Anonymous", "Flamenco Study", 95, 1, 1, 0, "A/Anonymous/Flamenco Study.json"),
            CatalogEntry("Anonymous", "Greensleeves", 70, 2, 1, 1, "A/Anonymous/Greensleeves.json"),
            CatalogEntry("Anonymous", "If Your Happy and You Know It", 120, 1, 1, 0, "A/Anonymous/If Your Happy and You Know It.json"),
            CatalogEntry("Anonymous", "Lamento Di Tristan", 100, 1, 1, 0, "A/Anonymous/Lamento di Tristan.json"),
            CatalogEntry("Anonymous", "Le Petit Train D'interlude", 120, 4, 3, 1, "A/Anonymous/Le Petit Train d'Interlude.json"),
            CatalogEntry("Anonymous", "Mi Favorita", 140, 1, 1, 0, "A/Anonymous/Mi Favorita.json"),
            CatalogEntry("Anonymous", "Minuet in G", 120, 2, 1, 1, "A/Anonymous/Baroque Minuet In G.json"),
            CatalogEntry("Anonymous", "Nihavend Longa", 140, 1, 1, 0, "A/Anonymous/Nihavend Longa.json"),
            CatalogEntry("Anonymous", "Packington's Pound", 120, 1, 1, 0, "A/Anonymous/Packington_'s Pound.json"),
            CatalogEntry("Anonymous", "Packington´s Pound", 120, 1, 1, 0, "A/Anonymous/Packingtonns Pound.json"),
            CatalogEntry("Anonymous", "Romance", 145, 1, 1, 0, "A/Anonymous/Romance.json"),
            CatalogEntry("Anonymous", "Romanza - Anonimo, Spanish Ballad", 102, 1, 1, 0, "A/Anonymous/Romance (Romanza).json"),
            CatalogEntry("Anonymous", "Saltarello", 110, 1, 1, 0, "A/Anonymous/Saltarello.json"),
            CatalogEntry("Anonymous", "Spanish Ballad", 100, 1, 1, 0, "A/Anonymous/spanish ballad.json"),
            CatalogEntry("Anonymous", "Spanish Study", 200, 1, 1, 0, "A/Anonymous/Spanish Study.json"),
            CatalogEntry("Anonymous", "Tarrantella", 180, 1, 1, 0, "A/Anonymous/Tarrantella.json"),
            CatalogEntry("Anonymous", "Turkey in the Straw", 126, 2, 1, 1, "A/Anonymous/Turkey in the Straw.json"),
            CatalogEntry("Anonymous", "Valse en Sol", 100, 1, 1, 0, "A/Anonymous/Valse In SOL.json"),
            CatalogEntry("Folk", "Learn Rythm Feu de Camp Guitar", 74, 2, 1, 1, "F/Folk/Learn Rythm Feu De Camp Guitar.json"),
            CatalogEntry("Traditional", "A Casinha Pequenina", 60, 1, 1, 0, "T/Traditional/A Casinha Pequenina.json"),
            CatalogEntry("Traditional", "A Londonderry Air", 72, 2, 1, 1, "T/Traditional/A Londonderry Air.json"),
            CatalogEntry("Traditional", "A Summer Breeze", 140, 1, 1, 0, "T/Traditional/A Summer Breeze.json"),
            CatalogEntry("Traditional", "Alabama Jubilee", 250, 2, 1, 1, "T/Traditional/Alabama Jubilee.json"),
            CatalogEntry("Traditional", "Amazing Grace", 93, 1, 1, 0, "T/Traditional/Amazing Grace.json"),
            CatalogEntry("Traditional", "Auld Lang Syne", 120, 1, 1, 0, "T/Traditional/Auld Lang Syne.json"),
            CatalogEntry("Traditional", "Backwater Blues", 90, 3, 2, 1, "T/Traditional/Backwater Blues.json"),
            CatalogEntry("Traditional", "Blackberry Blossom", 240, 3, 1, 2, "T/Traditional/Blackberry Blossom.json"),
            CatalogEntry("Traditional", "Ca la Breaza", 120, 2, 1, 1, "T/Traditional/Ca la Breaza.json"),
            CatalogEntry("Traditional", "Caffee-kanon", 120, 3, 1, 2, "T/Traditional/Caffee.json"),
            CatalogEntry("Traditional", "Caravan", 120, 6, 2, 4, "T/Traditional/Caravan.json"),
            CatalogEntry("Traditional", "Dixie Hoedown Bluegrass Traditional", 250, 2, 1, 1, "T/Traditional/Dixie Hoedown.json"),
            CatalogEntry("Traditional", "Drunken Sailor", 120, 6, 1, 5, "T/Traditional/Drunken Sailor.json"),
            CatalogEntry("Traditional", "El Condor Pasa", 78, 2, 1, 1, "T/Traditional/El Condor Pasa.json"),
            CatalogEntry("Traditional", "Figuri", 300, 2, 1, 1, "T/Traditional/Figuri.json"),
            CatalogEntry("Traditional", "Greensleeves", 120, 5, 3, 2, "T/Traditional/Greensleeves.json"),
            CatalogEntry("Traditional", "Indifférence", 200, 2, 1, 1, "T/Traditional/Indifférence.json"),
            CatalogEntry("Traditional", "Irish Washerwoman", 120, 4, 3, 1, "T/Traditional/Irish Washerwoman.json"),
            CatalogEntry("Traditional", "Jesse James", 195, 2, 1, 1, "T/Traditional/Jesse James.json"),
            CatalogEntry("Traditional", "Kuckuck", 88, 3, 1, 2, "T/Traditional/Kuckuck.json"),
            CatalogEntry("Traditional", "La Cucaracha", 160, 1, 1, 0, "T/Traditional/La cucaracha.json"),
            CatalogEntry("Traditional", "Le Vin", 170, 2, 1, 1, "T/Traditional/Le Vin.json"),
            CatalogEntry("Traditional", "Les Yeux Noirs", 200, 2, 1, 1, "T/Traditional/Les Yeux Noirs.json"),
            CatalogEntry("Traditional", "Little Drummer Boy", 120, 2, 1, 1, "T/Traditional/Little Drummer Boy.json"),
            CatalogEntry("Traditional", "Marsch", 120, 3, 1, 2, "T/Traditional/March.json"),
            CatalogEntry("Traditional", "Nashville Blues", 180, 1, 1, 0, "T/Traditional/Nashville Blues.json"),
            CatalogEntry("Traditional", "Nobody Knows the Trouble I've Seen", 120, 1, 1, 0, "T/Traditional/Nobody Knows The Trouble I've Seen.json"),
            CatalogEntry("Traditional", "Och Jungfrun Hon Gar I Dansen", 126, 2, 1, 1, "T/Traditional/Och jungfrun hon gar i dansen.json"),
            CatalogEntry("Traditional", "Oh When the Saints", 200, 1, 1, 0, "T/Traditional/Oh When The Saints.json"),
            CatalogEntry("Traditional", "Polka Russe", 250, 2, 1, 1, "T/Traditional/Polka Russe.json"),
            CatalogEntry("Traditional", "Scotland the Brave", 200, 2, 2, 0, "T/Traditional/Scotland the brave.json"),
            CatalogEntry("Traditional", "Silent Night", 120, 2, 1, 1, "T/Traditional/Silent night.json"),
            CatalogEntry("Traditional", "Soldier's Joy", 120, 2, 1, 1, "T/Traditional/Soldier's Joy.json"),
            CatalogEntry("Traditional", "Staccato Hora", 160, 2, 2, 0, "T/Traditional/Staccato Hora.json"),
            CatalogEntry("Traditional", "Standard Acoustic Blues", 101, 2, 1, 1, "T/Traditional/Standard acoustic blues.json"),
            CatalogEntry("Traditional", "The King of the Fairies", 72, 1, 1, 0, "T/Traditional/The King Of The Fairies.json"),
            CatalogEntry("Traditional", "The Wild Rover", 170, 2, 1, 1, "T/Traditional/The Wild Rover.json"),
            CatalogEntry("Traditional", "Tico Tico", 180, 3, 1, 2, "T/Traditional/Tico Tico.json"),
            CatalogEntry("Traditional", "Two Guitars", 100, 1, 1, 0, "T/Traditional/Two Guitars.json"),
            CatalogEntry("Traditional", "Vidalita", 105, 1, 1, 0, "T/Traditional/Vidalita.json"),
            CatalogEntry("Traditional", "Vom Pastor Seiner Kuh", 80, 3, 1, 2, "T/Traditional/Vom Pastor seiner Kuh.json"),
            CatalogEntry("Traditional", "When the Saints Go Marching in", 204, 1, 1, 0, "T/Traditional/When The Saints Go Marching In.json"),
            CatalogEntry("Traditional", "Will the Circle Be Unbroken", 190, 2, 1, 1, "T/Traditional/Will the circle be unbroken.json"),
            CatalogEntry("Traditional", "Wilwood Flowers", 120, 1, 1, 0, "T/Traditional/Wilwood Flowers.json"),
            CatalogEntry("Traditional", "Yankee Doodle Dixie", 160, 2, 1, 1, "T/Traditional/Yankee doodle dixie.json"),
            CatalogEntry("Unknown", "Bloody Tears (castelvania 4 Gears Stage)", 113, 8, 4, 4, "U/Unknown/Bloody Tears (Castelvania 4 Gears Stage).json"),
            CatalogEntry("Unknown", "Brazil at World Cup Theme", 150, 2, 1, 1, "U/Unknown/Brazil at World Cup Theme.json"),
            CatalogEntry("Unknown", "Brazilian National Hymn(hino Do Brasil)", 112, 1, 1, 0, "U/Unknown/Brazil National Anthem (hino do Brasil).json"),
            CatalogEntry("Unknown", "Capitaine Flam", 152, 1, 1, 0, "U/Unknown/Capitaine Flam.json"),
            CatalogEntry("Unknown", "Czech Medley", 100, 1, 1, 0, "U/Unknown/Czech Medley.json"),
            CatalogEntry("Unknown", "Farruca", 170, 1, 1, 0, "U/Unknown/Farruca.json"),
            CatalogEntry("Unknown", "Feelings", 80, 1, 1, 0, "U/Unknown/Feelings.json"),
            CatalogEntry("Unknown", "Flight of the Bumble Bee", 133, 2, 1, 1, "U/Unknown/Flight of the Bumble Bee.json"),
            CatalogEntry("Unknown", "German National Anthem", 120, 4, 1, 3, "U/Unknown/Deutsche Nationalhymne.json"),
            CatalogEntry("Unknown", "Ghost Riders on the Sky", 140, 2, 1, 1, "U/Unknown/Ghost riders on the sky.json"),
            CatalogEntry("Unknown", "Gipsy Jazz", 202, 3, 2, 1, "U/Unknown/Traditionnal - Gipsy Jazz.json"),
            CatalogEntry("Unknown", "Goldorack", 130, 5, 3, 2, "U/Unknown/Goldorack.json"),
            CatalogEntry("Unknown", "Hail to the Chief", 202, 3, 1, 2, "U/Unknown/Hail To The Chief.json"),
            CatalogEntry("Unknown", "Hall of Mountain King", 120, 1, 1, 0, "U/Unknown/Hall of Mountain King.json"),
            CatalogEntry("Unknown", "Harmonic Minor Jam in a", 118, 2, 1, 1, "U/Unknown/- Jam (Harmonic Minor).json"),
            CatalogEntry("Unknown", "Hatikva", 110, 1, 1, 0, "U/Unknown/Hatikva - Israel national anthem.json"),
            CatalogEntry("Unknown", "Het Wilhelmus", 120, 3, 1, 2, "U/Unknown/Het Wilhelmus (Dutch Anthem).json"),
            CatalogEntry("Unknown", "Infinite Dreams", 160, 4, 2, 2, "U/Unknown/Infinite Dreams.json"),
            CatalogEntry("Unknown", "Instrumental", 207, 6, 4, 2, "U/Unknown/Instrumental.json"),
            CatalogEntry("Unknown", "Isolated", 138, 1, 1, 0, "U/Unknown/- Isolated.json"),
            CatalogEntry("Unknown", "James Bond Theme", 115, 1, 1, 0, "U/Unknown/James Bond Theme.json"),
            CatalogEntry("Unknown", "Lotus Esprit Turbo Challenge", 180, 3, 2, 1, "U/Unknown/Lotus Esprit Turbo Challenge.json"),
            CatalogEntry("Unknown", "Magnum", 130, 4, 2, 2, "U/Unknown/Magnum.json"),
            CatalogEntry("Unknown", "Marche de L'air", 180, 0, 1, 0, "U/Unknown/Marche De L_'air.json"),
            CatalogEntry("Unknown", "Mighty Morphin Power Rangers Theme Song", 185, 3, 3, 0, "U/Unknown/Mighty Morphin Power Rangers Theme Song.json"),
            CatalogEntry("Unknown", "Pagan´s Song", 112, 4, 1, 3, "U/Unknown/- Pagan_'s Song.json"),
            CatalogEntry("Unknown", "Popeye Theme", 200, 2, 1, 1, "U/Unknown/Popeye the Sailor Man.json"),
            CatalogEntry("Unknown", "Resident Evil 2", 65, 3, 1, 2, "U/Unknown/Resident Evil 2.json"),
            CatalogEntry("Unknown", "Romance", 120, 2, 1, 1, "U/Unknown/Romance.json"),
            CatalogEntry("Unknown", "Romance de Amor", 90, 1, 1, 0, "U/Unknown/Romance De Amor.json"),
            CatalogEntry("Unknown", "Romance in D Major", 100, 1, 1, 0, "U/Unknown/Beautiful Music.json"),
            CatalogEntry("Unknown", "Romeo and Juliette", 70, 1, 1, 0, "U/Unknown/Romeo and Juliette.json"),
            CatalogEntry("Unknown", "Super Mario Bross (theme Song)", 105, 3, 1, 2, "U/Unknown/Super Mario Bross (Theme Song).json"),
            CatalogEntry("Unknown", "Tetris", 140, 1, 1, 0, "U/Unknown/Tetris.json"),
            CatalogEntry("Unknown", "The Addams Family", 140, 5, 1, 4, "U/Unknown/The Addams Family.json"),
            CatalogEntry("Unknown", "The Dueling Banjos", 200, 2, 1, 1, "U/Unknown/The Dueling Banjos.json"),
            CatalogEntry("Unknown", "The Green Leaves of Summer", 85, 1, 1, 0, "U/Unknown/The Green Leaves of Summer.json"),
            CatalogEntry("Unknown", "The Last Post", 152, 1, 1, 0, "U/Unknown/The Last Post.json"),
            CatalogEntry("Unknown", "The Oompa Loompa Song", 120, 1, 1, 0, "U/Unknown/Willy Wonka - The Oompa Loompa Song.json"),
            CatalogEntry("Unknown", "The Simpsons", 171, 1, 1, 0, "U/Unknown/The Simpsons.json"),
            CatalogEntry("Unknown", "The Star Spangled Banner", 90, 1, 1, 0, "U/Unknown/The Star Spangled Banner.json"),
            CatalogEntry("Unknown", "Transformers", 150, 3, 1, 2, "U/Unknown/TransFormers.json"),
            CatalogEntry("Unknown", "Tudo Que Vai", 210, 2, 1, 1, "U/Unknown/- Tudo Que Vai.json"),
            CatalogEntry("Unknown", "Wedding March", 120, 1, 1, 0, "U/Unknown/Wedding march.json"),
            CatalogEntry("Unknown", "Wilson's Wilde", 152, 1, 1, 0, "U/Unknown/Wilson_'s Wilde.json"),
            CatalogEntry("Unknown", "Áåç Íàçâàíèÿ", 120, 5, 3, 2, "U/Unknown/Rock Composition.json"),
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
                val entries = (builtInExercises() + libraryCatalog()).toMutableList()
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
                conn.connectTimeout = CONNECT_TIMEOUT
                conn.readTimeout = READ_TIMEOUT
                conn.setRequestProperty("User-Agent", "GuitarTrainer/1.0")
                val jsonStr = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
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
