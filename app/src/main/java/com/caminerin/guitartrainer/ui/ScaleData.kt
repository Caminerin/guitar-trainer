package com.caminerin.guitartrainer.ui

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class NoteFormat(val label: String) {
    AMERICAN("A B C"),
    EUROPEAN("Do Re Mi")
}

object NoteFormatPreference {
    var current by mutableStateOf(NoteFormat.EUROPEAN)
        private set

    fun set(format: NoteFormat, context: Context) {
        current = format
        context.getSharedPreferences("guitar_prefs", Context.MODE_PRIVATE)
            .edit().putString("note_format", format.name).apply()
    }

    fun load(context: Context) {
        val saved = context.getSharedPreferences("guitar_prefs", Context.MODE_PRIVATE)
            .getString("note_format", NoteFormat.EUROPEAN.name)
        current = try { NoteFormat.valueOf(saved ?: NoteFormat.EUROPEAN.name) } catch (_: Exception) { NoteFormat.EUROPEAN }
    }
}

enum class AccidentalStyle(val label: String) {
    SHARP("♯ Sostenidos"),
    FLAT("♭ Bemoles")
}

object AccidentalPreference {
    var current by mutableStateOf(AccidentalStyle.FLAT)
        private set

    fun set(style: AccidentalStyle, context: Context) {
        current = style
        context.getSharedPreferences("guitar_prefs", Context.MODE_PRIVATE)
            .edit().putString("accidental_style", style.name).apply()
    }

    fun load(context: Context) {
        val saved = context.getSharedPreferences("guitar_prefs", Context.MODE_PRIVATE)
            .getString("accidental_style", AccidentalStyle.FLAT.name)
        current = try { AccidentalStyle.valueOf(saved ?: AccidentalStyle.FLAT.name) } catch (_: Exception) { AccidentalStyle.FLAT }
    }
}

object AppPreferences {
    private const val PREFS = "guitar_prefs"

    var lastTab by mutableStateOf(0)
        private set
    var lastKey by mutableStateOf(0)
        private set
    var lastScaleIndex by mutableStateOf(0)
        private set
    var lastBpm by mutableStateOf(60)
        private set

    fun load(context: Context) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        lastTab = prefs.getInt("last_tab", 0)
        lastKey = prefs.getInt("last_key", 0)
        lastScaleIndex = prefs.getInt("last_scale", 0)
        lastBpm = prefs.getInt("last_bpm", 60)
    }

    fun saveTab(tab: Int, context: Context) {
        lastTab = tab
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("last_tab", tab).apply()
    }

    fun saveKey(key: Int, context: Context) {
        lastKey = key
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("last_key", key).apply()
    }

    fun saveScale(index: Int, context: Context) {
        lastScaleIndex = index
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("last_scale", index).apply()
    }

    fun saveBpm(bpm: Int, context: Context) {
        lastBpm = bpm
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putInt("last_bpm", bpm).apply()
    }

    // Chord visualizer state preservation
    var chordRoot by mutableStateOf(-1)
        private set
    var chordQuality by mutableStateOf("major")
        private set
    var chordLevel by mutableStateOf("all")
        private set
    var chordScaleEnabled by mutableStateOf(false)
        private set
    var chordScaleName by mutableStateOf("Mayor (Jónica)")
        private set

    fun saveChordState(root: Int, quality: String, level: String, scaleEnabled: Boolean, scaleName: String, context: Context) {
        chordRoot = root
        chordQuality = quality
        chordLevel = level
        chordScaleEnabled = scaleEnabled
        chordScaleName = scaleName
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("chord_root", root)
            .putString("chord_quality", quality)
            .putString("chord_level", level)
            .putBoolean("chord_scale_on", scaleEnabled)
            .putString("chord_scale_name", scaleName)
            .apply()
    }

    fun loadChordState(context: Context) {
        val p = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        chordRoot = p.getInt("chord_root", -1)
        chordQuality = p.getString("chord_quality", "major") ?: "major"
        chordLevel = p.getString("chord_level", "all") ?: "all"
        chordScaleEnabled = p.getBoolean("chord_scale_on", false)
        chordScaleName = p.getString("chord_scale_name", "Mayor (Jónica)") ?: "Mayor (Jónica)"
    }
}

val AMERICAN_NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")
val EUROPEAN_NOTE_NAMES = listOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")
val AMERICAN_NOTE_NAMES_FLAT = listOf("C", "Db", "D", "Eb", "E", "F", "Gb", "G", "Ab", "A", "Bb", "B")
val EUROPEAN_NOTE_NAMES_FLAT = listOf("Do", "Reb", "Re", "Mib", "Mi", "Fa", "Solb", "Sol", "Lab", "La", "Sib", "Si")
val AMERICAN_NOTE_NAMES_BOTH = listOf("C", "C#/Db", "D", "D#/Eb", "E", "F", "F#/Gb", "G", "G#/Ab", "A", "A#/Bb", "B")
val EUROPEAN_NOTE_NAMES_BOTH = listOf("Do", "Do#/Reb", "Re", "Re#/Mib", "Mi", "Fa", "Fa#/Solb", "Sol", "Sol#/Lab", "La", "La#/Sib", "Si")
val OPEN_STRING_NAMES_AMERICAN = listOf("E", "A", "D", "G", "B", "E")
val OPEN_STRING_NAMES_EUROPEAN = listOf("Mi", "La", "Re", "Sol", "Si", "Mi")

private val FLAT_MAJOR_KEYS = setOf(1, 3, 5, 8, 10)

fun keyUsesFlats(rootNote: Int): Boolean = rootNote in FLAT_MAJOR_KEYS

fun effectiveMajorRoot(rootNote: Int, relativeMajorOffset: Int): Int =
    (rootNote + relativeMajorOffset) % 12

fun keyUsesFlatsForScale(rootNote: Int, relativeMajorOffset: Int = 0): Boolean =
    effectiveMajorRoot(rootNote, relativeMajorOffset) in FLAT_MAJOR_KEYS

fun getNoteName(noteIndex: Int, rootNote: Int = -1, relativeMajorOffset: Int = 0): String {
    val useFlats = rootNote >= 0 && keyUsesFlatsForScale(rootNote, relativeMajorOffset)
    return when (NoteFormatPreference.current) {
        NoteFormat.AMERICAN -> if (useFlats) AMERICAN_NOTE_NAMES_FLAT[noteIndex % 12] else AMERICAN_NOTE_NAMES[noteIndex % 12]
        NoteFormat.EUROPEAN -> if (useFlats) EUROPEAN_NOTE_NAMES_FLAT[noteIndex % 12] else EUROPEAN_NOTE_NAMES[noteIndex % 12]
    }
}

fun getChromaticNames(rootNote: Int = -1, relativeMajorOffset: Int = 0): List<String> {
    return when (NoteFormatPreference.current) {
        NoteFormat.AMERICAN -> AMERICAN_NOTE_NAMES_BOTH
        NoteFormat.EUROPEAN -> EUROPEAN_NOTE_NAMES_BOTH
    }
}

fun getOpenStringNames(): List<String> {
    return when (NoteFormatPreference.current) {
        NoteFormat.AMERICAN -> OPEN_STRING_NAMES_AMERICAN
        NoteFormat.EUROPEAN -> OPEN_STRING_NAMES_EUROPEAN
    }
}

data class Scale(
    val name: String,
    val intervals: List<Int>,
    val positions: List<ScalePosition>,
    val hasCaged: Boolean = true,
    val relativeMajorOffset: Int = 0
)

fun getRelativeMajorOffset(scaleName: String): Int {
    val normalized = scaleName.lowercase()
        .replace("á", "a").replace("é", "e").replace("í", "i")
        .replace("ó", "o").replace("ú", "u")
    return ALL_SCALES.find { s ->
        s.name.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u") == normalized
    }?.relativeMajorOffset ?: 0
}

data class ScalePosition(
    val name: String,
    val startFret: Int,
    val endFret: Int,
    val cagedLetter: Char = name.firstOrNull() ?: 'C'
)

enum class NoteDisplay {
    NOTE, DEGREE, BOTH, FINGERING, NONE
}

private val CAGED_BASE_OFFSETS = listOf(
    'C' to 0, 'A' to 2, 'G' to 4, 'E' to 7, 'D' to 9
)
private const val CAGED_SPAN = 4

fun computeCagedPositions(key: Int): List<ScalePosition> {
    return CAGED_BASE_OFFSETS.map { (letter, baseOffset) ->
        val start = (baseOffset + key) % 12
        ScalePosition("$letter", start, start + CAGED_SPAN, letter)
    }.sortedBy { it.startFret }
}

@Deprecated("Use AMERICAN_NOTE_NAMES instead", replaceWith = ReplaceWith("AMERICAN_NOTE_NAMES"))
val SCALE_NOTE_NAMES = AMERICAN_NOTE_NAMES

val STANDARD_TUNING_MIDI = listOf(
    40, // E2 (6th string) - MIDI note
    45, // A2 (5th string)
    50, // D3 (4th string)
    55, // G3 (3rd string)
    59, // B3 (2nd string)
    64  // E4 (1st string)
)

val ALL_SCALES = listOf(
    Scale(
        name = "Mayor (Jónica)",
        intervals = listOf(0, 2, 4, 5, 7, 9, 11),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 4, 8, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 9, 13, 'D')
        )
    ),
    Scale(
        name = "Menor natural (Eólica)",
        intervals = listOf(0, 2, 3, 5, 7, 8, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 3, 7, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 10, 14, 'D')
        ),
        relativeMajorOffset = 3
    ),
    Scale(
        name = "Pentatónica mayor",
        intervals = listOf(0, 2, 4, 7, 9),
        positions = listOf(
            ScalePosition("1", 0, 3, '1'),
            ScalePosition("2", 2, 5, '2'),
            ScalePosition("3", 4, 8, '3'),
            ScalePosition("4", 7, 10, '4'),
            ScalePosition("5", 9, 12, '5')
        ),
        hasCaged = false
    ),
    Scale(
        name = "Pentatónica menor",
        intervals = listOf(0, 3, 5, 7, 10),
        positions = listOf(
            ScalePosition("1", 0, 3, '1'),
            ScalePosition("2", 3, 6, '2'),
            ScalePosition("3", 5, 9, '3'),
            ScalePosition("4", 7, 10, '4'),
            ScalePosition("5", 10, 13, '5')
        ),
        hasCaged = false,
        relativeMajorOffset = 3
    ),
    Scale(
        name = "Blues menor",
        intervals = listOf(0, 3, 5, 6, 7, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 3, 6, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 10, 14, 'D')
        ),
        relativeMajorOffset = 3
    ),
    Scale(
        name = "Blues mayor",
        intervals = listOf(0, 2, 3, 4, 7, 9),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 4, 8, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 9, 13, 'D')
        )
    ),
    Scale(
        name = "Dórica",
        intervals = listOf(0, 2, 3, 5, 7, 9, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 9, 13, 'D')
        ),
        relativeMajorOffset = 10
    ),
    Scale(
        name = "Frigia",
        intervals = listOf(0, 1, 3, 5, 7, 8, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 3, 7, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 8, 12, 'D')
        ),
        relativeMajorOffset = 8
    ),
    Scale(
        name = "Lidia",
        intervals = listOf(0, 2, 4, 6, 7, 9, 11),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 4, 8, 'G'),
            ScalePosition("E", 6, 10, 'E'),
            ScalePosition("D", 9, 13, 'D')
        ),
        relativeMajorOffset = 7
    ),
    Scale(
        name = "Mixolidia",
        intervals = listOf(0, 2, 4, 5, 7, 9, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 9, 13, 'D')
        ),
        relativeMajorOffset = 5
    ),
    Scale(
        name = "Locria",
        intervals = listOf(0, 1, 3, 5, 6, 8, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 1, 5, 'A'),
            ScalePosition("G", 3, 7, 'G'),
            ScalePosition("E", 6, 10, 'E'),
            ScalePosition("D", 8, 12, 'D')
        ),
        relativeMajorOffset = 1
    ),
    Scale(
        name = "Menor armónica",
        intervals = listOf(0, 2, 3, 5, 7, 8, 11),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 3, 7, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 8, 13, 'D')
        ),
        relativeMajorOffset = 3
    ),
    Scale(
        name = "Menor melódica",
        intervals = listOf(0, 2, 3, 5, 7, 9, 11),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 9, 13, 'D')
        ),
        relativeMajorOffset = 3
    ),
    Scale(
        name = "Frigia española",
        intervals = listOf(0, 1, 4, 5, 7, 8, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 1, 5, 'A'),
            ScalePosition("G", 4, 8, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 8, 12, 'D')
        ),
        relativeMajorOffset = 8
    ),
    Scale(
        name = "Húngara menor",
        intervals = listOf(0, 2, 3, 6, 7, 8, 11),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 3, 7, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 8, 13, 'D')
        ),
        relativeMajorOffset = 3
    ),
    Scale(
        name = "Tonos enteros",
        intervals = listOf(0, 2, 4, 6, 8, 10),
        positions = listOf(
            ScalePosition("1", 0, 4, '1'),
            ScalePosition("2", 4, 8, '2'),
            ScalePosition("3", 8, 12, '3')
        ),
        hasCaged = false
    ),
    Scale(
        name = "Cromática",
        intervals = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
        positions = listOf(
            ScalePosition("1", 0, 4, '1'),
            ScalePosition("2", 4, 8, '2'),
            ScalePosition("3", 8, 12, '3'),
            ScalePosition("4", 12, 16, '4'),
            ScalePosition("5", 16, 20, '5')
        ),
        hasCaged = false
    )
)

fun getNoteAtFret(stringMidi: Int, fret: Int): Int {
    return (stringMidi + fret) % 12
}

fun getAmericanNoteName(midiNote: Int): String {
    return AMERICAN_NOTE_NAMES[midiNote % 12]
}

fun getDegreeInScale(noteIndex: Int, rootIndex: Int, scaleIntervals: List<Int>): Int? {
    val interval = (noteIndex - rootIndex + 12) % 12
    val position = scaleIntervals.indexOf(interval)
    return if (position >= 0) position + 1 else null
}

fun isNoteInScale(noteIndex: Int, rootIndex: Int, scaleIntervals: List<Int>): Boolean {
    val interval = (noteIndex - rootIndex + 12) % 12
    return scaleIntervals.contains(interval)
}

fun findBestFretPosition(midiNote: Int, maxFret: Int): Pair<Int, Int>? {
    var bestString = -1
    var bestFret = -1
    var bestDist = Int.MAX_VALUE
    for (s in 0 until 6) {
        val fret = midiNote - STANDARD_TUNING_MIDI[s]
        if (fret in 0..maxFret) {
            val dist = kotlin.math.abs(fret - maxFret / 2)
            if (dist < bestDist) {
                bestDist = dist
                bestString = s
                bestFret = fret
            }
        }
    }
    return if (bestString >= 0) bestString to bestFret else null
}

fun getDegreeLabel(degree: Int): String {
    return when (degree) {
        1 -> "1"
        2 -> "2"
        3 -> "3"
        4 -> "4"
        5 -> "5"
        6 -> "6"
        7 -> "7"
        else -> "$degree"
    }
}

private val SPANISH_NOTE_NAMES = listOf("Do", "Do#", "Re", "Re#", "Mi", "Fa", "Fa#", "Sol", "Sol#", "La", "La#", "Si")

fun getSpanishNoteName(midiNote: Int, rootNote: Int = -1, relativeMajorOffset: Int = 0): String {
    return getNoteName(midiNote, rootNote, relativeMajorOffset)
}

val OPEN_STRING_NAMES: List<String> get() = getOpenStringNames()

fun getSpanishChromaticNames(rootNote: Int = -1, relativeMajorOffset: Int = 0): List<String> = getChromaticNames(rootNote, relativeMajorOffset)

val SPANISH_CHROMATIC_NAMES: List<String> get() = getChromaticNames()

data class ScaleChordInfo(
    val degree: Int,
    val noteName: String,
    val chordType: String,
    val intervalName: String,
    val romanDegree: String = ""
)

private val MAJOR_INTERVALS = listOf(0, 2, 4, 5, 7, 9, 11)

fun getScaleChords(rootNote: Int, scaleIntervals: List<Int>, relativeMajorOffset: Int = 0): List<ScaleChordInfo> {
    val result = mutableListOf<ScaleChordInfo>()
    for ((degIdx, interval) in scaleIntervals.withIndex()) {
        val noteIdx = (rootNote + interval) % 12
        val noteName = getSpanishChromaticNames(rootNote, relativeMajorOffset)[noteIdx]
        val degree = degIdx + 1

        // Determine chord quality by stacking thirds
        val thirdInterval = findIntervalSteps(scaleIntervals, degIdx, 2)
        val fifthInterval = findIntervalSteps(scaleIntervals, degIdx, 4)

        val chordType = when {
            thirdInterval == 4 && fifthInterval == 7 -> "Mayor"
            thirdInterval == 3 && fifthInterval == 7 -> "menor"
            thirdInterval == 3 && fifthInterval == 6 -> "dim"
            thirdInterval == 4 && fifthInterval == 8 -> "aug"
            thirdInterval == 4 && fifthInterval == null -> "Mayor"
            thirdInterval == 3 && fifthInterval == null -> "menor"
            else -> ""
        }

        val intervalName = when (interval) {
            0 -> "Tónica"
            1 -> "2ª menor"
            2 -> "2ª Mayor"
            3 -> "3ª menor"
            4 -> "3ª Mayor"
            5 -> "4ª Justa"
            6 -> "Tritono"
            7 -> "5ª Justa"
            8 -> "6ª menor"
            9 -> "6ª Mayor"
            10 -> "7ª menor"
            11 -> "7ª Mayor"
            else -> ""
        }

        val baseRoman = when (degree) {
            1 -> "I"; 2 -> "II"; 3 -> "III"; 4 -> "IV"
            5 -> "V"; 6 -> "VI"; 7 -> "VII"; else -> "$degree"
        }
        val majorRef = if (degree <= MAJOR_INTERVALS.size) MAJOR_INTERVALS[degree - 1] else -1
        val isFlat = majorRef >= 0 && interval < majorRef
        val isSharp = majorRef >= 0 && interval > majorRef
        val prefix = when {
            isFlat -> "b"
            isSharp -> "#"
            else -> ""
        }
        val romanDegree = when (chordType) {
            "menor" -> "$prefix${baseRoman.lowercase()}"
            "dim" -> "$prefix${baseRoman.lowercase()}°"
            "aug" -> "$prefix$baseRoman+"
            else -> "$prefix$baseRoman"
        }

        result.add(ScaleChordInfo(degree, noteName, chordType, intervalName, romanDegree))
    }
    return result
}

private fun findIntervalSteps(scaleIntervals: List<Int>, startDeg: Int, steps: Int): Int? {
    if (startDeg + steps >= scaleIntervals.size) {
        val wrappedIdx = (startDeg + steps) % scaleIntervals.size
        return ((scaleIntervals[wrappedIdx] - scaleIntervals[startDeg] + 12) % 12)
    }
    return (scaleIntervals[startDeg + steps] - scaleIntervals[startDeg] + 12) % 12
}

data class FretboardNote(
    val string: Int,  // 0=6th(E2), 5=1st(E4)
    val fret: Int,
    val noteIndex: Int
)

/**
 * Generate the ordered note sequence for a position: string 6→1 ascending, then 1→6 descending.
 * Only includes notes that belong to the scale within the position's fret range.
 */
fun getPositionNoteSequence(
    rootNote: Int,
    scaleIntervals: List<Int>,
    position: ScalePosition
): List<FretboardNote> {
    val ascending = mutableListOf<FretboardNote>()
    // String 6 (index 0) to string 1 (index 5)
    for (s in 0 until 6) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val stringNotes = mutableListOf<FretboardNote>()
        for (fret in position.startFret..position.endFret) {
            val noteIdx = (openNote + fret) % 12
            if (isNoteInScale(noteIdx, rootNote, scaleIntervals)) {
                stringNotes.add(FretboardNote(s, fret, noteIdx))
            }
        }
        ascending.addAll(stringNotes)
    }
    // Descending: string 1 to string 6, reversed frets on each string
    val descending = mutableListOf<FretboardNote>()
    for (s in 5 downTo 0) {
        val openNote = STANDARD_TUNING_MIDI[s]
        val stringNotes = mutableListOf<FretboardNote>()
        for (fret in position.endFret downTo position.startFret) {
            val noteIdx = (openNote + fret) % 12
            if (isNoteInScale(noteIdx, rootNote, scaleIntervals)) {
                stringNotes.add(FretboardNote(s, fret, noteIdx))
            }
        }
        descending.addAll(stringNotes)
    }
    // Remove the duplicate at the top (string 1 last ascending = string 1 first descending)
    if (ascending.isNotEmpty() && descending.isNotEmpty() && ascending.last() == descending.first()) {
        return ascending + descending.drop(1)
    }
    return ascending + descending
}
