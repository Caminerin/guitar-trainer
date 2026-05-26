package com.caminerin.guitartrainer.ui

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class ChordShape(
    val id: String,
    val root: String,
    val quality: String,
    val qualityLabel: String,
    val formula: String,
    val level: String,
    val category: String,
    val shapeName: String,
    val frets: List<Int?>, // null = muted, 0 = open, 1+ = fret
    val inversion: String,
    val priority: Int,
    val maxFret: Int,
    val notesSharp: String,
    val intervals: String
) {
    val displayName: String
        get() {
            val rootIdx = AMERICAN_NOTE_NAMES.indexOf(root)
            val rootDisplay = getNoteName(rootIdx, rootIdx)
            val qualDisplay = when (qualityLabel) {
                "major" -> ""
                "minor" -> "m"
                "7" -> "7"
                "maj7" -> "maj7"
                "m7" -> "m7"
                "dim" -> "dim"
                "dim7" -> "dim7"
                "sus2" -> "sus2"
                "sus4" -> "sus4"
                "m7b5" -> "m7b5"
                else -> qualityLabel
            }
            return "$rootDisplay$qualDisplay"
        }

    val shortLabel: String
        get() {
            val inv = when {
                inversion.contains("first") -> "1ª inv."
                inversion.contains("second") -> "2ª inv."
                inversion.contains("third") -> "3ª inv."
                inversion == "root_position" -> "raíz"
                else -> ""
            }
            val shape = shapeName
                .replace(Regex("_shape.*"), "")
                .replace("common_", "")
                .replace("compact_", "")
                .replace("caged_", "")
                .replace("barre_", "")
                .replace("open_", "")
                .replace("_", " ")
                .trim()
            val cagedLetter = when {
                shapeName.contains("C_shape") -> "Forma C"
                shapeName.contains("A_shape") -> "Forma A"
                shapeName.contains("G_shape") -> "Forma G"
                shapeName.contains("E_shape") -> "Forma E"
                shapeName.contains("D_shape") -> "Forma D"
                else -> ""
            }
            return when {
                cagedLetter.isNotEmpty() && inv.isNotEmpty() -> "$cagedLetter $inv"
                cagedLetter.isNotEmpty() -> cagedLetter
                inv.isNotEmpty() -> inv
                shape.length <= 20 -> shape
                else -> "Pos ${priority}"
            }
        }
}

enum class ChordLevel(val displayName: String, val csvValue: String) {
    BEGINNER("Principiante", "beginner_core"),
    INTERMEDIATE("Intermedio", "intermediate_core"),
    ADVANCED("Avanzado", "advanced_reference")
}

enum class ChordQuality(val displayName: String, val csvValue: String) {
    MAJOR("Mayor", "major"),
    MINOR("Menor", "minor"),
    DOMINANT7("7\u00aa", "dominant7"),
    MAJ7("Maj7", "maj7"),
    MINOR7("m7", "m7"),
    SUS2("Sus2", "sus2"),
    SUS4("Sus4", "sus4"),
    DIMINISHED("Dim", "diminished"),
    DIMINISHED7("Dim7", "diminished7"),
    HALF_DIM7("m7b5", "half_diminished7")
}

object ChordRepository {
    private var allChords: List<ChordShape> = emptyList()

    fun loadChords(context: Context) {
        if (allChords.isNotEmpty()) return
        val chords = mutableListOf<ChordShape>()
        try {
            val inputStream = context.assets.open("chords.csv")
            val reader = BufferedReader(InputStreamReader(inputStream))
            reader.readLine() // skip header
            var line = reader.readLine()
            while (line != null) {
                val chord = parseCsvLine(line)
                if (chord != null) chords.add(chord)
                line = reader.readLine()
            }
            reader.close()
        } catch (_: Exception) { }
        allChords = chords
    }

    fun getChords(): List<ChordShape> = allChords

    fun getChordsByLevel(level: ChordLevel): List<ChordShape> =
        allChords.filter { it.level == level.csvValue }

    fun getChordsByQuality(quality: ChordQuality): List<ChordShape> =
        allChords.filter { it.quality == quality.csvValue }

    fun getChordsByRoot(root: String): List<ChordShape> =
        allChords.filter { it.root == root }

    fun getChordsByRootAndQuality(root: String, quality: ChordQuality): List<ChordShape> =
        allChords.filter { it.root == root && it.quality == quality.csvValue }

    fun getChordsByLevelAndQuality(level: ChordLevel, quality: ChordQuality): List<ChordShape> =
        allChords.filter { it.level == level.csvValue && it.quality == quality.csvValue }

    fun getChordsByRootLevelQuality(root: String, level: ChordLevel, quality: ChordQuality): List<ChordShape> =
        allChords.filter { it.root == root && it.level == level.csvValue && it.quality == quality.csvValue }

    fun getAvailableRoots(): List<String> =
        allChords.map { it.root }.distinct().sortedBy {
            SCALE_NOTE_NAMES.indexOf(it)
        }

    fun getAvailableQualities(): List<ChordQuality> =
        ChordQuality.entries.filter { q -> allChords.any { it.quality == q.csvValue } }

    private fun parseCsvLine(line: String): ChordShape? {
        try {
            // Parse CSV carefully (frets field contains commas inside brackets)
            val parts = smartSplit(line)
            if (parts.size < 18) return null

            val fretsStr = parts[9] // e.g. "[null, 3, 2, 0, 1, 0]"
            val frets = parseFrets(fretsStr)

            val priority = parts[13].toIntOrNull() ?: 50
            val maxFret = parts[14].toIntOrNull() ?: 0

            return ChordShape(
                id = parts[0],
                root = parts[1],
                quality = parts[2],
                qualityLabel = parts[3],
                formula = parts[4],
                level = parts[5],
                category = parts[6],
                shapeName = parts[7],
                frets = frets,
                inversion = parts[11],
                priority = priority,
                maxFret = maxFret,
                notesSharp = parts[16],
                intervals = parts[17]
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun smartSplit(line: String): List<String> {
        val parts = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> {
                    parts.add(current.toString())
                    current.clear()
                }
                else -> current.append(ch)
            }
        }
        parts.add(current.toString())
        return parts
    }

    private fun parseFrets(fretsStr: String): List<Int?> {
        val cleaned = fretsStr.replace("[", "").replace("]", "").trim()
        return cleaned.split(",").map { s ->
            val trimmed = s.trim()
            if (trimmed == "null" || trimmed.isEmpty()) null
            else trimmed.toIntOrNull()
        }
    }
}
