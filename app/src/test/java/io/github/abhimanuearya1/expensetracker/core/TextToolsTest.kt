package io.github.abhimanuearya1.expensetracker.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class TextToolsTest {

    @Test
    fun plural() {
        assertEquals("1 expense", TextTools.plural(1, "expense"))
        assertEquals("0 expenses", TextTools.plural(0, "expense"))
        assertEquals("12 expenses", TextTools.plural(12, "expense"))
    }

    @Test
    fun decodesUtf8WithAndWithoutMark() {
        val text = "Date,Details\n2026-01-01,₹ चाय café"
        assertEquals(text, TextTools.decode(text.toByteArray(Charsets.UTF_8)))
        // The mark is left in place here; the CSV reader removes it.
        assertEquals("﻿" + text, TextTools.decode(byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + text.toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun decodesUtf16() {
        val text = "Date\tDetails\n2026-01-01\tचाय"
        assertEquals(text, TextTools.decode(byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + text.toByteArray(Charsets.UTF_16LE)))
        assertEquals(text, TextTools.decode(byteArrayOf(0xFE.toByte(), 0xFF.toByte()) + text.toByteArray(Charsets.UTF_16BE)))
    }

    @Test
    fun fallsBackToWindows1252ForExcelFiles() {
        val text = "2026-01-01,Food,5.00,café £5"
        assertEquals(text, TextTools.decode(text.toByteArray(Charset.forName("windows-1252"))))
    }

    @Test
    fun importMessages() {
        assertEquals("Imported 3 expenses", ImportOutcome(3, 0, 0).message())
        assertEquals("Imported 1 expense · 2 already in the app", ImportOutcome(1, 2, 0).message())
        assertEquals("Imported 3 expenses · 1 line could not be read", ImportOutcome(3, 0, 1).message())
        assertEquals("Nothing new: all 4 expenses are already in the app", ImportOutcome(0, 4, 0).message())
        assertEquals("No expenses found in that file. It needs to be a CSV file.", ImportOutcome(0, 0, 7).message())
    }
}
