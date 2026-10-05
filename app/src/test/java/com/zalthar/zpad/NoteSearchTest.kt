package com.zalthar.zpad

import org.junit.Assert.*
import org.junit.Test

class NoteSearchTest {
    @Test fun matchesAcrossChunkBoundariesAndIgnoresCase() {
        val text = "x".repeat(8190) + "Telegram" + "\nFast notes"
        assertTrue(matchesNote(text.reader(), listOf("telegram", "FAST")))
    }
    @Test fun requiresEveryWordEvenWhenInDifferentChunks() {
        val text = "first" + "x".repeat(9000) + "last"
        assertTrue(matchesNote(text.reader(), listOf("first", "last")))
        assertFalse(matchesNote(text.reader(), listOf("first", "missing")))
    }
    @Test fun trimsAndSplitsQuery() {
        assertEquals(listOf("hello", "world"), searchTerms("  hello   world hello "))
        assertTrue(searchTerms(" \n ").isEmpty())
    }
    @Test fun propagatesCancellation() {
        assertThrows(IllegalStateException::class.java) { matchesNote("text".reader(), listOf("text")) { throw IllegalStateException("cancelled") } }
    }
}
