package com.caminerin.guitartrainer.audio

/**
 * A single stroke inside a strum pattern.
 *
 * @param direction  how the pick moves
 * @param velocity   relative loudness 0.0–1.0 (scaled by the overall pattern velocity)
 * @param accent     whether this beat carries extra emphasis
 * @param ghost      ghost stroke — hand moves but barely touches strings (very light)
 */
data class PatternStroke(
    val direction: StrumEngine.Direction,
    val velocity: Float,
    val accent: Boolean = false,
    val ghost: Boolean = false
)

/**
 * A repeatable strum pattern — one full measure of rhythmic strokes.
 *
 * @param id             machine-readable key
 * @param name           display name (English)
 * @param nameEs         display name (Spanish)
 * @param genre          genre category for grouping
 * @param timeSignature  e.g. "4/4", "3/4", "6/8"
 * @param subdivisions   number of strokes per measure
 * @param strokes        the strokes — must have [subdivisions] elements
 * @param description    short human-readable description (Spanish)
 */
data class StrumPattern(
    val id: String,
    val name: String,
    val nameEs: String,
    val genre: String,
    val timeSignature: String,
    val subdivisions: Int,
    val strokes: List<PatternStroke>,
    val description: String = ""
)

/**
 * Built-in library of common strum patterns, covering the genres a guitar
 * student is most likely to practise.  Each pattern is expressed as one
 * measure of strokes at the song's subdivision rate.
 *
 * Legend used in comments:
 *   D = down-strum, U = up-strum, x = dead/mute, M = palm-mute, - = rest/skip
 *   Accent marked with ^
 */
object StrumPatternLibrary {

    private val D   = StrumEngine.Direction.DOWN
    private val U   = StrumEngine.Direction.UP
    private val X   = StrumEngine.Direction.DEAD
    private val M   = StrumEngine.Direction.MUTE
    private val R   = StrumEngine.Direction.REST

    private fun s(dir: StrumEngine.Direction, vel: Float, accent: Boolean = false, ghost: Boolean = false) =
        PatternStroke(dir, vel, accent, ghost)

    val ALL: List<StrumPattern> = listOf(

        /* ── 4/4 Pop / Rock ──────────────────────────────────────── */

        // ^D - D U - U D U      Classic strumming 101
        StrumPattern(
            id = "pop_standard", name = "Pop Standard", nameEs = "Pop Estándar",
            genre = "Pop", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),    // 1
                s(R, 0.0f),                    // &
                s(D, 0.75f),                   // 2
                s(U, 0.50f),                   // &
                s(R, 0.0f),                    // 3
                s(U, 0.50f),                   // &
                s(D, 0.70f),                   // 4
                s(U, 0.40f)                    // &
            ),
            description = "El patrón más universal. D-DU-UDU"
        ),

        // ^D D D D               Power downstrokes
        StrumPattern(
            id = "rock_power", name = "Rock Power", nameEs = "Rock Poderoso",
            genre = "Rock", timeSignature = "4/4", subdivisions = 4,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(D, 0.80f),
                s(D, 0.85f),
                s(D, 0.75f)
            ),
            description = "Todos golpes abajo, estilo punk/rock. D D D D"
        ),

        // ^D - ^D U - U ^D U    Island strum — used in thousands of songs
        StrumPattern(
            id = "island", name = "Island Strum", nameEs = "Rasgueo Isla",
            genre = "Pop", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(R, 0.0f),
                s(D, 0.90f, accent = true),
                s(U, 0.55f),
                s(R, 0.0f),
                s(U, 0.50f),
                s(D, 0.85f, accent = true),
                s(U, 0.45f)
            ),
            description = "Patrón 'isla': D-DU-UDU con acentos alternos"
        ),

        // ^D D U U D U          Basic folk
        StrumPattern(
            id = "folk_basic", name = "Folk Basic", nameEs = "Folk Básico",
            genre = "Folk", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(R, 0.0f),
                s(D, 0.70f),
                s(U, 0.45f),
                s(U, 0.50f),
                s(R, 0.0f),
                s(D, 0.65f),
                s(U, 0.40f)
            ),
            description = "Rasgueo folk suave. D-DU U-DU"
        ),

        /* ── Rock / Punk ─────────────────────────────────────────── */

        // ^D D U ^D D U         Driving rock 8ths
        StrumPattern(
            id = "rock_driving", name = "Driving Rock", nameEs = "Rock Agresivo",
            genre = "Rock", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(D, 0.65f),
                s(U, 0.50f),
                s(D, 0.90f, accent = true),
                s(D, 0.60f),
                s(U, 0.45f),
                s(D, 0.80f),
                s(U, 0.50f)
            ),
            description = "Rock con octavos continuos. DDU DDU DU"
        ),

        // ^M M D U ^M M D U    Palm-mute + open strum (Green Day style)
        StrumPattern(
            id = "punk_mute", name = "Punk Mute-Open", nameEs = "Punk Mute-Abierto",
            genre = "Rock", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(M, 0.85f, accent = true),
                s(M, 0.60f),
                s(D, 0.95f),
                s(U, 0.55f),
                s(M, 0.80f, accent = true),
                s(M, 0.55f),
                s(D, 0.90f),
                s(U, 0.50f)
            ),
            description = "Palm-mute y abierto alternado. xM xM DU xM xM DU"
        ),

        /* ── Funk ────────────────────────────────────────────────── */

        // ^D x D U x U D x     Funky 16th-note feel
        StrumPattern(
            id = "funk_16", name = "Funk 16ths", nameEs = "Funk Dieciseisavos",
            genre = "Funk", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(X, 0.70f),
                s(D, 0.65f),
                s(U, 0.50f),
                s(X, 0.75f),
                s(U, 0.55f),
                s(D, 0.70f),
                s(X, 0.65f)
            ),
            description = "Funk con golpes muertos rítmicos. Dx DU xU Dx"
        ),

        /* ── Reggae ──────────────────────────────────────────────── */

        // - ^D - ^D - ^D - ^D   Off-beat skank
        StrumPattern(
            id = "reggae_skank", name = "Reggae Skank", nameEs = "Reggae Skank",
            genre = "Reggae", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(R, 0.0f),
                s(D, 0.90f, accent = true),
                s(R, 0.0f),
                s(D, 0.85f, accent = true),
                s(R, 0.0f),
                s(D, 0.88f, accent = true),
                s(R, 0.0f),
                s(D, 0.80f, accent = true)
            ),
            description = "Rasgueo reggae en el offbeat. -D -D -D -D"
        ),

        /* ── Ballad / Slow ───────────────────────────────────────── */

        // ^D - U - ^D - U -     Slow ballad
        StrumPattern(
            id = "ballad_slow", name = "Slow Ballad", nameEs = "Balada Lenta",
            genre = "Balada", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(R, 0.0f),
                s(U, 0.45f, ghost = true),
                s(R, 0.0f),
                s(D, 0.80f, accent = true),
                s(R, 0.0f),
                s(U, 0.40f, ghost = true),
                s(R, 0.0f)
            ),
            description = "Balada suave con ghost strokes. D-u-D-u-"
        ),

        /* ── 3/4 Waltz ──────────────────────────────────────────── */

        // ^D D U D U U          Waltz / 3/4 pattern
        StrumPattern(
            id = "waltz_34", name = "Waltz 3/4", nameEs = "Vals 3/4",
            genre = "Vals", timeSignature = "3/4", subdivisions = 6,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(D, 0.55f),
                s(U, 0.40f),
                s(D, 0.65f),
                s(U, 0.45f),
                s(U, 0.35f)
            ),
            description = "Vals clásico en 3/4. D DU DUU"
        ),

        /* ── 6/8 ─────────────────────────────────────────────────── */

        // ^D - - U - U          6/8 slow feel
        StrumPattern(
            id = "68_slow", name = "6/8 Ballad", nameEs = "6/8 Balada",
            genre = "Balada", timeSignature = "6/8", subdivisions = 6,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(R, 0.0f),
                s(R, 0.0f),
                s(U, 0.55f),
                s(R, 0.0f),
                s(U, 0.45f)
            ),
            description = "Rasgueo 6/8 para baladas. D--U-U"
        ),

        /* ── Latin ───────────────────────────────────────────────── */

        // ^D U - U D U - U      Bossa / Latin syncopation
        StrumPattern(
            id = "latin_bossa", name = "Latin Bossa", nameEs = "Latin Bossa",
            genre = "Latin", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 0.95f, accent = true),
                s(U, 0.45f),
                s(R, 0.0f),
                s(U, 0.55f),
                s(D, 0.80f, accent = true),
                s(U, 0.50f),
                s(R, 0.0f),
                s(U, 0.45f)
            ),
            description = "Patrón bossa nova sincopado. DU-UDU-U"
        ),

        /* ── Country ─────────────────────────────────────────────── */

        // ^D - D U D U D U      Country boom-chicka
        StrumPattern(
            id = "country_boom", name = "Country Boom-Chicka", nameEs = "Country Boom-Chicka",
            genre = "Country", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(R, 0.0f),
                s(D, 0.60f),
                s(U, 0.45f),
                s(D, 0.70f),
                s(U, 0.40f),
                s(D, 0.65f),
                s(U, 0.40f)
            ),
            description = "Country alternado. D-DUDUDU"
        ),

        /* ── Flamenco ────────────────────────────────────────────── */

        // Simplified Rumba strum (really needs rasgueo but approximated)
        StrumPattern(
            id = "flamenco_rumba", name = "Flamenco Rumba", nameEs = "Rumba Flamenca",
            genre = "Flamenco", timeSignature = "4/4", subdivisions = 8,
            strokes = listOf(
                s(D, 1.0f, accent = true),
                s(U, 0.40f, ghost = true),
                s(D, 0.85f),
                s(U, 0.50f),
                s(X, 0.70f),
                s(U, 0.55f),
                s(D, 0.80f),
                s(U, 0.35f, ghost = true)
            ),
            description = "Rumba flamenca simplificada. DuDUxUDu"
        )
    )

    /** All unique genre strings, sorted. */
    val genres: List<String> get() = ALL.map { it.genre }.distinct().sorted()

    /** Find pattern by id. */
    fun byId(id: String): StrumPattern? = ALL.firstOrNull { it.id == id }

    /** Default pattern when none is selected. */
    val default: StrumPattern get() = ALL.first { it.id == "pop_standard" }
}
