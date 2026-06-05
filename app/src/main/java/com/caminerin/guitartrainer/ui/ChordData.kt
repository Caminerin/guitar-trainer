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
    val intervals: String,
    val fingering: List<String> = emptyList()
) {
    val displayName: String
        get() = getDisplayName()

    fun getDisplayName(tonalRoot: Int = -1, relativeMajorOffset: Int = 0): String {
        val rootIdx = AMERICAN_NOTE_NAMES.indexOf(root)
        val effectiveRoot = if (tonalRoot >= 0) tonalRoot else rootIdx
        val rootDisplay = getNoteName(rootIdx, effectiveRoot, relativeMajorOffset)
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
            "aug" -> "aug"
            "add9" -> "add9"
            "9" -> "9"
            "maj9" -> "maj9"
            "m9" -> "m9"
            "mMaj7" -> "mMaj7"
            "mMaj9" -> "mMaj9"
            "6" -> "6"
            "6/9" -> "6/9"
            "m6" -> "m6"
            "m6/9" -> "m6/9"
            "5" -> "5"
            "7sus4" -> "7sus4"
            "9sus4" -> "9sus4"
            "madd9" -> "madd9"
            "m11" -> "m11"
            "m13" -> "m13"
            "m7b9" -> "m7b9"
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
            val minFret = frets.filterNotNull().filter { it > 0 }.minOrNull()
            val fretTag = if (minFret != null && minFret > 0) "tr.$minFret" else ""
            val base = when {
                cagedLetter.isNotEmpty() && inv.isNotEmpty() -> "$cagedLetter $inv"
                cagedLetter.isNotEmpty() -> cagedLetter
                inv.isNotEmpty() -> inv
                shape.length <= 20 -> shape
                else -> "Pos ${priority}"
            }
            return if (fretTag.isNotEmpty()) "$base ($fretTag)" else base
        }
}

enum class ChordLevel(val displayName: String, val csvValue: String) {
    ALL("Todos", "all"),
    BEGINNER("Principiante", "beginner_core"),
    INTERMEDIATE("Intermedio", "intermediate_core"),
    ADVANCED("Avanzado", "advanced_reference")
}

enum class QualityGroup(val displayName: String) {
    TRIAD("Tríadas"),
    TETRAD("Cuatríadas"),
    EXTENDED("Extensiones"),
    OTHER("Otros")
}

enum class ChordQuality(val displayName: String, val csvValue: String, val group: QualityGroup) {
    MAJOR("Mayor", "major", QualityGroup.TRIAD),
    MINOR("Menor", "minor", QualityGroup.TRIAD),
    DIMINISHED("Dim", "diminished", QualityGroup.TRIAD),
    AUGMENTED("Aug", "augmented", QualityGroup.TRIAD),
    SUS2("Sus2", "sus2", QualityGroup.TRIAD),
    SUS4("Sus4", "sus4", QualityGroup.TRIAD),
    POWER5("5 (Power)", "power5", QualityGroup.TRIAD),
    DOMINANT7("7ª", "dominant7", QualityGroup.TETRAD),
    MAJ7("Maj7", "maj7", QualityGroup.TETRAD),
    MINOR7("m7", "m7", QualityGroup.TETRAD),
    DIMINISHED7("Dim7", "diminished7", QualityGroup.TETRAD),
    HALF_DIM7("m7b5", "half_diminished7", QualityGroup.TETRAD),
    MINOR_MAJOR7("mMaj7", "minor_major7", QualityGroup.TETRAD),
    SIXTH("6ª", "sixth", QualityGroup.TETRAD),
    MINOR6("m6", "minor6", QualityGroup.TETRAD),
    DOM7SUS4("7sus4", "dominant7sus4", QualityGroup.TETRAD),
    ADD9("add9", "add9", QualityGroup.EXTENDED),
    DOMINANT9("9ª", "dominant9", QualityGroup.EXTENDED),
    MAJ9("Maj9", "maj9", QualityGroup.EXTENDED),
    MINOR9("m9", "minor9", QualityGroup.EXTENDED),
    MINOR_MAJOR9("mMaj9", "minor_major9", QualityGroup.EXTENDED),
    SIX_NINE("6/9", "six_nine", QualityGroup.EXTENDED),
    MINOR_SIX_NINE("m6/9", "minor_six_nine", QualityGroup.EXTENDED),
    NINE_SUS4("9sus4", "nine_sus4", QualityGroup.EXTENDED),
    MINOR_ADD9("madd9", "minor_add9", QualityGroup.EXTENDED),
    MINOR11("m11", "minor11", QualityGroup.EXTENDED),
    MINOR13("m13", "minor13", QualityGroup.EXTENDED),
    MINOR7_FLAT9("m7b9", "minor7_flat9", QualityGroup.EXTENDED)
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
            var lineNum = 1
            var skipped = 0
            while (line != null) {
                lineNum++
                val chord = parseCsvLine(line)
                if (chord != null) chords.add(chord)
                else {
                    skipped++
                    android.util.Log.w("ChordData", "Skipped invalid row at line $lineNum")
                }
                line = reader.readLine()
            }
            reader.close()
            android.util.Log.i("ChordData", "Loaded ${chords.size} chords, skipped $skipped rows")
        } catch (e: Exception) {
            android.util.Log.e("ChordData", "Error loading chords CSV", e)
        }
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
        allChords.filter { (level == ChordLevel.ALL || it.level == level.csvValue) && it.quality == quality.csvValue }

    fun getChordsByRootLevelQuality(root: String, level: ChordLevel, quality: ChordQuality): List<ChordShape> =
        allChords.filter { it.root == root &&
            (level == ChordLevel.ALL || it.level == level.csvValue) &&
            it.quality == quality.csvValue
        }

    fun getAvailableRoots(): List<String> =
        allChords.map { it.root }.distinct().sortedBy {
            AMERICAN_NOTE_NAMES.indexOf(it)
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

            val fingeringStr = if (parts.size > 19) parts[19] else ""
            val fingeringRaw = if (fingeringStr.isNotBlank()) {
                fingeringStr.split("-").map { it.trim() }
            } else emptyList()
            val fingeringList = validateFingering(fingeringRaw, frets)

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
                intervals = parts[17],
                fingering = fingeringList
            )
        } catch (e: Exception) {
            android.util.Log.w("ChordData", "Failed to load chord data", e)
            return null
        }
    }

    private fun validateFingering(fingering: List<String>, frets: List<Int?>): List<String> {
        if (fingering.isEmpty()) return emptyList()
        if (fingering.size != 6) return emptyList()
        for (i in 0 until 6) {
            val fret = frets.getOrNull(i)
            val finger = fingering[i]
            when {
                fret == null && finger != "x" -> return emptyList()
                fret == 0 && finger != "0" && finger != "x" -> return emptyList()
                fret != null && fret > 0 && finger !in listOf("1", "2", "3", "4", "T") -> return emptyList()
            }
        }
        return fingering
    }

    private fun smartSplit(line: String): List<String> = com.caminerin.guitartrainer.ui.smartSplit(line)

    private fun parseFrets(fretsStr: String): List<Int?> {
        val cleaned = fretsStr.replace("[", "").replace("]", "").trim()
        return cleaned.split(",").map { s ->
            val trimmed = s.trim()
            if (trimmed == "null" || trimmed.isEmpty()) null
            else trimmed.toIntOrNull()
        }
    }
}

/**
 * Persists the user's preferred (favorite) chord voicing per chord identity
 * (root + qualityLabel). Stored in SharedPreferences so the choice survives
 * across sessions. Used by the progression trainer voicing picker.
 */
object ChordVoicingFavorites {
    private const val PREFS = "guitar_prefs"
    private fun key(root: String, qualityLabel: String) = "fav_voicing_${root}_${qualityLabel}"

    fun getFavoriteId(context: Context, root: String, qualityLabel: String): String? =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(key(root, qualityLabel), null)

    fun setFavoriteId(context: Context, root: String, qualityLabel: String, id: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(key(root, qualityLabel), id).apply()
    }
}
