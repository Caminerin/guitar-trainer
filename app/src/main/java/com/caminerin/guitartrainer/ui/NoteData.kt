package com.caminerin.guitartrainer.ui

data class NoteInfo(
    val name: String,
    val spanishName: String
)

val ALL_NOTES = listOf(
    NoteInfo("C", "Do"),
    NoteInfo("C#", "Do#"),
    NoteInfo("D", "Re"),
    NoteInfo("D#", "Re#"),
    NoteInfo("E", "Mi"),
    NoteInfo("F", "Fa"),
    NoteInfo("F#", "Fa#"),
    NoteInfo("G", "Sol"),
    NoteInfo("G#", "Sol#"),
    NoteInfo("A", "La"),
    NoteInfo("A#", "La#"),
    NoteInfo("B", "Si"),
)

data class GuitarString(
    val number: Int,
    val noteName: String,
    val spanishName: String,
    val octave: Int,
    val frequency: Float
)

val STANDARD_TUNING = listOf(
    GuitarString(6, "E", "Mi", 2, 82.41f),
    GuitarString(5, "A", "La", 2, 110.00f),
    GuitarString(4, "D", "Re", 3, 146.83f),
    GuitarString(3, "G", "Sol", 3, 196.00f),
    GuitarString(2, "B", "Si", 3, 246.94f),
    GuitarString(1, "E", "Mi", 4, 329.63f),
)
