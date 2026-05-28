package com.caminerin.guitartrainer.ui

import org.junit.Assert.*
import org.junit.Test

class MusicLogicTest {

    // ===== Bemol / flat key calculation =====

    @Test
    fun `C major does not use flats`() {
        assertFalse(keyUsesFlats(0))
    }

    @Test
    fun `Db major uses flats`() {
        assertTrue(keyUsesFlats(1))
    }

    @Test
    fun `Eb major uses flats`() {
        assertTrue(keyUsesFlats(3))
    }

    @Test
    fun `F major uses flats`() {
        assertTrue(keyUsesFlats(5))
    }

    @Test
    fun `Ab major uses flats`() {
        assertTrue(keyUsesFlats(8))
    }

    @Test
    fun `Bb major uses flats`() {
        assertTrue(keyUsesFlats(10))
    }

    @Test
    fun `G major does not use flats`() {
        assertFalse(keyUsesFlats(7))
    }

    // ===== Effective major root with offset =====

    @Test
    fun `C minor effective major root is Eb`() {
        // C minor = 0, relativeMajorOffset for Eólica = 3
        val offset = getRelativeMajorOffset("Menor natural (Eólica)")
        assertEquals(3, offset)
        val effectiveRoot = effectiveMajorRoot(0, offset)
        assertEquals(3, effectiveRoot) // Eb
    }

    @Test
    fun `G dorian effective major root is F`() {
        val offset = getRelativeMajorOffset("Dórica")
        assertEquals(10, offset)
        val effectiveRoot = effectiveMajorRoot(7, offset)
        assertEquals(5, effectiveRoot) // F
    }

    // ===== keyUsesFlatsForScale =====

    @Test
    fun `C minor uses flats via scale context`() {
        val offset = getRelativeMajorOffset("Menor natural (Eólica)")
        assertTrue(keyUsesFlatsForScale(0, offset))
    }

    @Test
    fun `A minor does not use flats`() {
        val offset = getRelativeMajorOffset("Menor natural (Eólica)")
        assertFalse(keyUsesFlatsForScale(9, offset))
    }

    // ===== getNoteName =====

    @Test
    fun `getNoteName returns Eb for note 3 in C minor context`() {
        val offset = getRelativeMajorOffset("Menor natural (Eólica)")
        val name = getNoteName(3, rootNote = 0, relativeMajorOffset = offset)
        assertEquals("Eb", name)
    }

    @Test
    fun `getNoteName returns D# when no flat context`() {
        val name = getNoteName(3, rootNote = 0, relativeMajorOffset = 0)
        assertEquals("D#", name)
    }

    @Test
    fun `getNoteName returns Bb for note 10 in F major`() {
        val name = getNoteName(10, rootNote = 5, relativeMajorOffset = 0)
        assertEquals("Bb", name)
    }

    @Test
    fun `getNoteName returns natural notes correctly`() {
        assertEquals("C", getNoteName(0))
        assertEquals("D", getNoteName(2))
        assertEquals("E", getNoteName(4))
        assertEquals("F", getNoteName(5))
        assertEquals("G", getNoteName(7))
        assertEquals("A", getNoteName(9))
        assertEquals("B", getNoteName(11))
    }

    // ===== Scale note calculations =====

    @Test
    fun `C major scale notes are correct`() {
        val cMajorIntervals = listOf(0, 2, 4, 5, 7, 9, 11)
        for (interval in cMajorIntervals) {
            assertTrue(isNoteInScale(interval, 0, cMajorIntervals))
        }
        assertFalse(isNoteInScale(1, 0, cMajorIntervals)) // C# not in C major
        assertFalse(isNoteInScale(6, 0, cMajorIntervals)) // F# not in C major
    }

    @Test
    fun `getDegreeInScale returns correct degrees for C major`() {
        val cMajorIntervals = listOf(0, 2, 4, 5, 7, 9, 11)
        assertEquals(1, getDegreeInScale(0, 0, cMajorIntervals))  // C = 1st degree
        assertEquals(2, getDegreeInScale(2, 0, cMajorIntervals))  // D = 2nd degree
        assertEquals(3, getDegreeInScale(4, 0, cMajorIntervals))  // E = 3rd degree
        assertEquals(5, getDegreeInScale(7, 0, cMajorIntervals))  // G = 5th degree
        assertNull(getDegreeInScale(1, 0, cMajorIntervals))       // C# = not in scale
    }

    @Test
    fun `G major transposed scale notes`() {
        val majorIntervals = listOf(0, 2, 4, 5, 7, 9, 11)
        // G major: G A B C D E F#
        assertTrue(isNoteInScale(7, 7, majorIntervals))   // G
        assertTrue(isNoteInScale(9, 7, majorIntervals))   // A
        assertTrue(isNoteInScale(11, 7, majorIntervals))  // B
        assertTrue(isNoteInScale(0, 7, majorIntervals))   // C
        assertTrue(isNoteInScale(2, 7, majorIntervals))   // D
        assertTrue(isNoteInScale(4, 7, majorIntervals))   // E
        assertTrue(isNoteInScale(6, 7, majorIntervals))   // F#
        assertFalse(isNoteInScale(5, 7, majorIntervals))  // F natural not in G major
    }

    // ===== Degree labels =====

    @Test
    fun `getDegreeLabel returns correct labels`() {
        assertEquals("1", getDegreeLabel(1))
        assertEquals("2", getDegreeLabel(2))
        assertEquals("3", getDegreeLabel(3))
        assertEquals("4", getDegreeLabel(4))
        assertEquals("5", getDegreeLabel(5))
        assertEquals("6", getDegreeLabel(6))
        assertEquals("7", getDegreeLabel(7))
    }

    // ===== Fret note calculation =====

    @Test
    fun `getNoteAtFret returns correct note indices`() {
        // 6th string E2 (MIDI 40) open = E = 4
        assertEquals(4, getNoteAtFret(40, 0))
        // 6th string fret 1 = F = 5
        assertEquals(5, getNoteAtFret(40, 1))
        // 6th string fret 12 = E = 4
        assertEquals(4, getNoteAtFret(40, 12))
        // 5th string A2 (MIDI 45) open = A = 9
        assertEquals(9, getNoteAtFret(45, 0))
    }

    @Test
    fun `getAmericanNoteName returns correct names`() {
        assertEquals("C", getAmericanNoteName(0))
        assertEquals("C", getAmericanNoteName(12))
        assertEquals("C", getAmericanNoteName(60)) // Middle C
        assertEquals("A", getAmericanNoteName(69)) // A440
    }

    // ===== ALL_SCALES integrity =====

    @Test
    fun `ALL_SCALES contains major scale`() {
        val major = ALL_SCALES.find { it.name.contains("Mayor") }
        assertNotNull(major)
        assertEquals(listOf(0, 2, 4, 5, 7, 9, 11), major!!.intervals)
    }

    @Test
    fun `ALL_SCALES contains minor scale`() {
        val minor = ALL_SCALES.find { it.name.contains("Menor natural") }
        assertNotNull(minor)
        assertEquals(listOf(0, 2, 3, 5, 7, 8, 10), minor!!.intervals)
    }

    @Test
    fun `ALL_SCALES has correct relative major offsets`() {
        val dorica = ALL_SCALES.find { it.name.contains("Dórica") }
        assertNotNull(dorica)
        assertEquals(10, dorica!!.relativeMajorOffset)

        val frigia = ALL_SCALES.find { it.name.contains("Frigia") }
        assertNotNull(frigia)
        assertEquals(8, frigia!!.relativeMajorOffset)
    }

    // ===== CAGED positions =====

    @Test
    fun `computeCagedPositions returns 5 positions`() {
        val positions = computeCagedPositions(0) // C
        assertEquals(5, positions.size)
    }

    @Test
    fun `computeCagedPositions positions span 4 frets each`() {
        val positions = computeCagedPositions(0)
        for (pos in positions) {
            assertEquals(4, pos.endFret - pos.startFret)
        }
    }

    // ===== Chromatic names =====

    @Test
    fun `getChromaticNames returns 12 names`() {
        val names = getChromaticNames()
        assertEquals(12, names.size)
    }

    @Test
    fun `getChromaticNames with flat key uses flats`() {
        // F major uses flats
        val names = getChromaticNames(rootNote = 5)
        assertTrue(names.contains("Bb"))
        assertFalse(names.contains("A#"))
    }

    @Test
    fun `getChromaticNames with sharp key uses sharps`() {
        // G major uses sharps
        val names = getChromaticNames(rootNote = 7)
        assertTrue(names.contains("F#"))
        assertFalse(names.contains("Gb"))
    }

    // ===== smartSplit =====

    @Test
    fun `smartSplit handles simple CSV`() {
        val result = smartSplit("a,b,c")
        assertEquals(listOf("a", "b", "c"), result)
    }

    @Test
    fun `smartSplit handles quoted fields`() {
        val result = smartSplit("\"a,b\",c,d")
        assertEquals(listOf("a,b", "c", "d"), result)
    }

    @Test
    fun `smartSplit handles empty fields`() {
        val result = smartSplit("a,,c")
        assertEquals(listOf("a", "", "c"), result)
    }
}
