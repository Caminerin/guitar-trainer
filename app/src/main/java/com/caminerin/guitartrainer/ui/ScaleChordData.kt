package com.caminerin.guitartrainer.ui

import android.content.Context
import java.io.BufferedReader
import java.io.InputStreamReader

data class ScaleChordEntry(
    val scaleName: String,
    val degree: String,
    val chordType: String, // "triada" or "cuatriada"
    val chordNameInC: String,
    val rootSemitoneFromTonic: Int,
    val quality: String // "m", "dim", "aug", "7", "maj7", "m7", "m7b5", "dim7", etc.
)

object ScaleChordRepository {
    private var entries: List<ScaleChordEntry> = emptyList()

    fun load(context: Context) {
        if (entries.isNotEmpty()) return
        val result = mutableListOf<ScaleChordEntry>()
        try {
            val reader = BufferedReader(InputStreamReader(context.assets.open("scale_chords_C.csv")))
            reader.readLine() // skip header
            var line = reader.readLine()
            while (line != null) {
                parseLine(line)?.let { result.add(it) }
                line = reader.readLine()
            }
            reader.close()
        } catch (_: Exception) {}
        entries = result
    }

    fun getChordsForScale(scaleName: String, rootNote: Int, relativeMajorOffset: Int = 0): List<TransposedScaleChord> {
        val scaleKey = normalizeScaleName(scaleName)
        return entries
            .filter { normalizeScaleName(it.scaleName) == scaleKey }
            .map { entry ->
                val newRootSemitone = (rootNote + entry.rootSemitoneFromTonic) % 12
                val rootName = getNoteName(newRootSemitone, rootNote, relativeMajorOffset)
                val chordName = "$rootName${entry.quality}"
                TransposedScaleChord(
                    degree = entry.degree,
                    chordType = entry.chordType,
                    chordName = chordName,
                    rootSemitone = newRootSemitone,
                    quality = entry.quality
                )
            }
    }

    private fun normalizeScaleName(name: String): String {
        return name.lowercase()
            .replace("á", "a").replace("é", "e").replace("í", "i")
            .replace("ó", "o").replace("ú", "u")
            .replace("jonica", "jonica").replace("eolica", "eolica")
            .replace("(", "").replace(")", "")
            .replace(" ", "")
            .trim()
    }

    private val NOTE_TO_SEMITONE = mapOf(
        "C" to 0, "C#" to 1, "Db" to 1,
        "D" to 2, "D#" to 3, "Eb" to 3,
        "E" to 4, "Fb" to 4,
        "F" to 5, "F#" to 6, "Gb" to 6,
        "G" to 7, "G#" to 8, "Ab" to 8,
        "A" to 9, "A#" to 10, "Bb" to 10,
        "B" to 11, "Cb" to 11
    )

    private fun parseLine(line: String): ScaleChordEntry? {
        try {
            val parts = smartSplit(line)
            if (parts.size < 9) return null

            val scaleName = parts[0].trim()
            val chordType = parts[5].trim() // triada or cuatriada
            val degree = parts[6].trim()
            val chordName = parts[7].trim()

            if (scaleName == "Cromatica") return null // skip chromatic (it's special)
            if (chordName.contains("no estandar")) return null

            // Extract root note from chord name
            val rootStr = extractRoot(chordName)
            val rootSemitone = NOTE_TO_SEMITONE[rootStr] ?: return null
            // Since tonic is C (semitone 0), rootSemitoneFromTonic = rootSemitone
            val quality = extractQuality(chordName, rootStr)

            return ScaleChordEntry(
                scaleName = scaleName,
                degree = degree,
                chordType = chordType,
                chordNameInC = chordName,
                rootSemitoneFromTonic = rootSemitone,
                quality = quality
            )
        } catch (_: Exception) {
            return null
        }
    }

    private fun extractRoot(chordName: String): String {
        if (chordName.length < 1) return ""
        val first = chordName[0]
        if (chordName.length >= 2) {
            val second = chordName[1]
            if (second == '#' || second == 'b') return "$first$second"
        }
        return "$first"
    }

    private fun extractQuality(chordName: String, root: String): String {
        val after = chordName.removePrefix(root)
        return when {
            after.isEmpty() -> ""
            after == "m" -> "m"
            after == "dim" -> "dim"
            after == "aug" -> "aug"
            after == "7" -> "7"
            after == "m7" -> "m7"
            after == "maj7" -> "maj7"
            after == "m7b5" -> "m7b5"
            after == "dim7" -> "dim7"
            after == "mMaj7" -> "mMaj7"
            after == "6" -> "6"
            after == "m6" -> "m6"
            after == "7#5" -> "7#5"
            after == "maj7#5" -> "maj7#5"
            after == "7b5" -> "7b5"
            after.startsWith("m") && after.length <= 8 -> after
            else -> after
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
}

data class TransposedScaleChord(
    val degree: String,
    val chordType: String,
    val chordName: String,
    val rootSemitone: Int,
    val quality: String
)
