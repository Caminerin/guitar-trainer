package com.caminerin.guitartrainer.ui

data class Scale(
    val name: String,
    val intervals: List<Int>,
    val positions: List<ScalePosition>
)

data class ScalePosition(
    val name: String,
    val startFret: Int,
    val endFret: Int,
    val cagedLetter: Char = name.firstOrNull() ?: 'C'
)

enum class NoteDisplay {
    NOTE, DEGREE, BOTH, NONE
}

val SCALE_NOTE_NAMES = listOf("C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B")

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
        )
    ),
    Scale(
        name = "Pentatónica mayor",
        intervals = listOf(0, 2, 4, 7, 9),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 4, 8, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 9, 13, 'D')
        )
    ),
    Scale(
        name = "Pentatónica menor",
        intervals = listOf(0, 3, 5, 7, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 3, 6, 'A'),
            ScalePosition("G", 5, 9, 'G'),
            ScalePosition("E", 7, 11, 'E'),
            ScalePosition("D", 10, 14, 'D')
        )
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
        )
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
        )
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
        )
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
        )
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
        )
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
        )
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
        )
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
        )
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
        )
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
        )
    ),
    Scale(
        name = "Tonos enteros",
        intervals = listOf(0, 2, 4, 6, 8, 10),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 2, 6, 'A'),
            ScalePosition("G", 4, 8, 'G'),
            ScalePosition("E", 6, 10, 'E'),
            ScalePosition("D", 8, 12, 'D')
        )
    ),
    Scale(
        name = "Cromática",
        intervals = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
        positions = listOf(
            ScalePosition("C", 0, 4, 'C'),
            ScalePosition("A", 4, 8, 'A'),
            ScalePosition("G", 8, 12, 'G'),
            ScalePosition("E", 12, 16, 'E'),
            ScalePosition("D", 16, 20, 'D')
        )
    )
)

fun getNoteAtFret(stringMidi: Int, fret: Int): Int {
    return (stringMidi + fret) % 12
}

fun getNoteName(midiNote: Int): String {
    return SCALE_NOTE_NAMES[midiNote % 12]
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

fun getSpanishNoteName(midiNote: Int): String {
    return SPANISH_NOTE_NAMES[midiNote % 12]
}

val OPEN_STRING_NAMES = listOf("E", "A", "D", "G", "B", "E")

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
