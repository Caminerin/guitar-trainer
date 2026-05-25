package com.caminerin.guitartrainer.ui

data class Scale(
    val name: String,
    val intervals: List<Int>,
    val positions: List<ScalePosition>
)

data class ScalePosition(
    val name: String,
    val startFret: Int,
    val endFret: Int
)

enum class NoteDisplay {
    NOTE, DEGREE, BOTH, NONE
}

val SCALE_NOTE_NAMES = listOf("C", "C#", "D", "Eb", "E", "F", "F#", "G", "Ab", "A", "Bb", "B")

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
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 4, 8),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 9, 13)
        )
    ),
    Scale(
        name = "Menor natural (Eólica)",
        intervals = listOf(0, 2, 3, 5, 7, 8, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 3, 7),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 10, 14)
        )
    ),
    Scale(
        name = "Pentatónica mayor",
        intervals = listOf(0, 2, 4, 7, 9),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 4, 8),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 9, 13)
        )
    ),
    Scale(
        name = "Pentatónica menor",
        intervals = listOf(0, 3, 5, 7, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 3, 6),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 10, 14)
        )
    ),
    Scale(
        name = "Blues menor",
        intervals = listOf(0, 3, 5, 6, 7, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 3, 6),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 10, 14)
        )
    ),
    Scale(
        name = "Blues mayor",
        intervals = listOf(0, 2, 3, 4, 7, 9),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 4, 8),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 9, 13)
        )
    ),
    Scale(
        name = "Dórica",
        intervals = listOf(0, 2, 3, 5, 7, 9, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 9, 13)
        )
    ),
    Scale(
        name = "Frigia",
        intervals = listOf(0, 1, 3, 5, 7, 8, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 3, 7),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 8, 12)
        )
    ),
    Scale(
        name = "Lidia",
        intervals = listOf(0, 2, 4, 6, 7, 9, 11),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 4, 8),
            ScalePosition("Pos 4", 6, 10),
            ScalePosition("Pos 5", 9, 13)
        )
    ),
    Scale(
        name = "Mixolidia",
        intervals = listOf(0, 2, 4, 5, 7, 9, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 9, 13)
        )
    ),
    Scale(
        name = "Locria",
        intervals = listOf(0, 1, 3, 5, 6, 8, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 1, 5),
            ScalePosition("Pos 3", 3, 7),
            ScalePosition("Pos 4", 6, 10),
            ScalePosition("Pos 5", 8, 12)
        )
    ),
    Scale(
        name = "Menor armónica",
        intervals = listOf(0, 2, 3, 5, 7, 8, 11),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 3, 7),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 8, 13)
        )
    ),
    Scale(
        name = "Menor melódica",
        intervals = listOf(0, 2, 3, 5, 7, 9, 11),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 5, 9),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 9, 13)
        )
    ),
    Scale(
        name = "Frigia española",
        intervals = listOf(0, 1, 4, 5, 7, 8, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 1, 5),
            ScalePosition("Pos 3", 4, 8),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 8, 12)
        )
    ),
    Scale(
        name = "Húngara menor",
        intervals = listOf(0, 2, 3, 6, 7, 8, 11),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 3, 7),
            ScalePosition("Pos 4", 7, 11),
            ScalePosition("Pos 5", 8, 13)
        )
    ),
    Scale(
        name = "Tonos enteros",
        intervals = listOf(0, 2, 4, 6, 8, 10),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 2, 6),
            ScalePosition("Pos 3", 4, 8),
            ScalePosition("Pos 4", 6, 10),
            ScalePosition("Pos 5", 8, 12)
        )
    ),
    Scale(
        name = "Cromática",
        intervals = listOf(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11),
        positions = listOf(
            ScalePosition("Pos 1", 0, 4),
            ScalePosition("Pos 2", 4, 8),
            ScalePosition("Pos 3", 8, 12),
            ScalePosition("Pos 4", 12, 16),
            ScalePosition("Pos 5", 16, 20)
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
