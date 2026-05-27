package com.caminerin.guitartrainer.ui

data class NoteInfo(
    val name: String,
    val spanishName: String,
    val altName: String = name,
    val altSpanishName: String = spanishName,
    val noteIndex: Int = 0
)

val ALL_NOTES = listOf(
    NoteInfo("C", "Do", noteIndex = 0),
    NoteInfo("C#", "Do#", altName = "Db", altSpanishName = "Reb", noteIndex = 1),
    NoteInfo("D", "Re", noteIndex = 2),
    NoteInfo("D#", "Re#", altName = "Eb", altSpanishName = "Mib", noteIndex = 3),
    NoteInfo("E", "Mi", noteIndex = 4),
    NoteInfo("F", "Fa", noteIndex = 5),
    NoteInfo("F#", "Fa#", altName = "Gb", altSpanishName = "Solb", noteIndex = 6),
    NoteInfo("G", "Sol", noteIndex = 7),
    NoteInfo("G#", "Sol#", altName = "Ab", altSpanishName = "Lab", noteIndex = 8),
    NoteInfo("A", "La", noteIndex = 9),
    NoteInfo("A#", "La#", altName = "Bb", altSpanishName = "Sib", noteIndex = 10),
    NoteInfo("B", "Si", noteIndex = 11),
)

fun findNoteByIndex(noteIndex: Int): NoteInfo =
    ALL_NOTES.find { it.noteIndex == noteIndex } ?: ALL_NOTES[0]

data class GuitarString(
    val number: Int,
    val noteName: String,
    val spanishName: String,
    val octave: Int,
    val frequency: Float
)

data class GuitarTuning(
    val name: String,
    val strings: List<GuitarString>
)

val STANDARD_TUNING = listOf(
    GuitarString(6, "E", "Mi", 2, 82.41f),
    GuitarString(5, "A", "La", 2, 110.00f),
    GuitarString(4, "D", "Re", 3, 146.83f),
    GuitarString(3, "G", "Sol", 3, 196.00f),
    GuitarString(2, "B", "Si", 3, 246.94f),
    GuitarString(1, "E", "Mi", 4, 329.63f),
)

val ALL_TUNINGS = listOf(
    GuitarTuning(
        name = "Estándar (EADGBE)",
        strings = STANDARD_TUNING
    ),
    GuitarTuning(
        name = "Drop D (DADGBE)",
        strings = listOf(
            GuitarString(6, "D", "Re", 2, 73.42f),
            GuitarString(5, "A", "La", 2, 110.00f),
            GuitarString(4, "D", "Re", 3, 146.83f),
            GuitarString(3, "G", "Sol", 3, 196.00f),
            GuitarString(2, "B", "Si", 3, 246.94f),
            GuitarString(1, "E", "Mi", 4, 329.63f),
        )
    ),
    GuitarTuning(
        name = "Open G (DGDGBD)",
        strings = listOf(
            GuitarString(6, "D", "Re", 2, 73.42f),
            GuitarString(5, "G", "Sol", 2, 98.00f),
            GuitarString(4, "D", "Re", 3, 146.83f),
            GuitarString(3, "G", "Sol", 3, 196.00f),
            GuitarString(2, "B", "Si", 3, 246.94f),
            GuitarString(1, "D", "Re", 4, 293.66f),
        )
    ),
    GuitarTuning(
        name = "Open D (DADF#AD)",
        strings = listOf(
            GuitarString(6, "D", "Re", 2, 73.42f),
            GuitarString(5, "A", "La", 2, 110.00f),
            GuitarString(4, "D", "Re", 3, 146.83f),
            GuitarString(3, "F#", "Fa#", 3, 185.00f),
            GuitarString(2, "A", "La", 3, 220.00f),
            GuitarString(1, "D", "Re", 4, 293.66f),
        )
    ),
    GuitarTuning(
        name = "DADGAD",
        strings = listOf(
            GuitarString(6, "D", "Re", 2, 73.42f),
            GuitarString(5, "A", "La", 2, 110.00f),
            GuitarString(4, "D", "Re", 3, 146.83f),
            GuitarString(3, "G", "Sol", 3, 196.00f),
            GuitarString(2, "A", "La", 3, 220.00f),
            GuitarString(1, "D", "Re", 4, 293.66f),
        )
    ),
    GuitarTuning(
        name = "½ tono abajo (Eb Ab Db Gb Bb Eb)",
        strings = listOf(
            GuitarString(6, "Eb", "Mib", 2, 77.78f),
            GuitarString(5, "Ab", "Lab", 2, 103.83f),
            GuitarString(4, "Db", "Reb", 3, 138.59f),
            GuitarString(3, "Gb", "Solb", 3, 185.00f),
            GuitarString(2, "Bb", "Sib", 3, 233.08f),
            GuitarString(1, "Eb", "Mib", 4, 311.13f),
        )
    ),
)
