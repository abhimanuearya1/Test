package io.github.abhimanuearya1.expensetracker.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class CsvTest {

    @Test
    fun plainFieldsAreNotQuoted() {
        assertEquals("a,b,c", Csv.line(listOf("a", "b", "c")))
    }

    @Test
    fun specialCharactersAreQuoted() {
        assertEquals("\"a,b\",\"say \"\"hi\"\"\",\"two\nlines\"", Csv.line(listOf("a,b", "say \"hi\"", "two\nlines")))
    }

    @Test
    fun parseHandlesQuotesAndLineBreaks() {
        val text = "a,b\r\n\"x,1\",\"he said \"\"ok\"\"\"\n\"line1\nline2\",z\n"
        assertEquals(
            listOf(listOf("a", "b"), listOf("x,1", "he said \"ok\""), listOf("line1\nline2", "z")),
            Csv.parse(text),
        )
    }

    @Test
    fun parseSkipsBlankLinesAndByteOrderMark() {
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), Csv.parse("﻿a,b\n\n\nc,d"))
    }

    @Test
    fun parseKeepsEmptyFields() {
        assertEquals(listOf(listOf("a", "", "c"), listOf("", "", "")), Csv.parse("a,,c\n,,\n"))
    }

    @Test
    fun semicolonAndTabFilesAreDetected() {
        assertEquals(listOf(listOf("Date", "Amount"), listOf("2026-01-02", "1,5")), Csv.parse("Date;Amount\n2026-01-02;1,5\n"))
        assertEquals(listOf(listOf("Date", "Amount"), listOf("2026-01-02", "15")), Csv.parse("Date\tAmount\n2026-01-02\t15\n"))
    }

    @Test
    fun writeThenParseGivesTheSameFields() {
        val rows = listOf(
            listOf("plain", "with,comma", "with \"quotes\""),
            listOf(" padded ", "multi\r\nline", ""),
            listOf("₹ rupee", "हिंदी", "semi;colon"),
        )
        val text = rows.joinToString("\r\n") { Csv.line(it) }
        assertEquals(rows, Csv.parse(text))
    }
}

class CsvBackupTest {

    private fun day(iso: String) = LocalDate.parse(iso).toEpochDay()

    private val sample = listOf(
        BackupRow(day("2026-11-16"), "Food", 25000, "Lunch at cafe"),
        BackupRow(day("2026-11-15"), "Transport", 4050, "Auto, to office"),
        BackupRow(day("2026-10-01"), "", 1200000, "Paid \"in cash\"\nsecond line"),
    )

    @Test
    fun exportHasHeaderAndPlainValues() {
        val lines = CsvBackup.export(sample.take(1)).split("\r\n")
        assertEquals("Date,Classification,Amount,Details", lines[0])
        assertEquals("2026-11-16,Food,250.00,Lunch at cafe", lines[1])
    }

    @Test
    fun exportThenImportGivesTheSameRows() {
        val parsed = CsvBackup.parse(CsvBackup.export(sample))
        assertEquals(sample, parsed.rows)
        assertEquals(0, parsed.unreadable)
    }

    @Test
    fun importFollowsColumnNames() {
        val text = "Amount,Notes,Category,Date,Extra\n99.5,Tea,Food,2026-01-05,x\n"
        assertEquals(listOf(BackupRow(day("2026-01-05"), "Food", 9950, "Tea")), CsvBackup.parse(text).rows)
    }

    @Test
    fun importWorksWithoutHeader() {
        val text = "2026-01-05,Food,99.50,Tea\n2026-01-06,,10,\n"
        assertEquals(
            listOf(BackupRow(day("2026-01-05"), "Food", 9950, "Tea"), BackupRow(day("2026-01-06"), "", 1000, "")),
            CsvBackup.parse(text).rows,
        )
    }

    @Test
    fun importReadsSpreadsheetStyleValues() {
        val text = "Date,Classification,Amount,Details\n" +
            "16-11-2026,Food,\"1,234.50\",a\n" +
            "16/11/2026,Food,₹ 250,b\n" +
            "11/16/2026,Food,250.5,c\n" +
            "2026-11-16 00:00:00,Uncategorized,7,d\n"
        val rows = CsvBackup.parse(text).rows
        assertEquals(listOf(123450L, 25000L, 25050L, 700L), rows.map { it.amountCents })
        assertEquals(List(4) { day("2026-11-16") }, rows.map { it.epochDay })
        assertEquals("", rows[3].classification)
    }

    @Test
    fun unreadableLinesAreCountedAndSkipped() {
        val text = "Date,Classification,Amount,Details\n" +
            "not a date,Food,10,a\n" +
            "2026-02-30,Food,10,impossible date\n" +
            "2026-01-05,Food,abc,bad amount\n" +
            "2026-01-05,Food,-5,negative\n" +
            "2026-01-05,Food,0,zero\n" +
            "2026-01-05,Food,10,good\n"
        val parsed = CsvBackup.parse(text)
        assertEquals(1, parsed.rows.size)
        assertEquals("good", parsed.rows[0].details)
        assertEquals(5, parsed.unreadable)
    }

    @Test
    fun emptyOrUnrelatedFilesGiveNoRows() {
        assertEquals(ParsedBackup(emptyList(), 0), CsvBackup.parse(""))
        assertEquals(0, CsvBackup.parse("hello world\nthis is not a csv\n").rows.size)
    }

    @Test
    fun dayFirstDatesUnlessImpossible() {
        assertEquals(day("2026-03-04"), CsvBackup.parseDate("04/03/2026"))
        assertEquals(day("2026-11-16"), CsvBackup.parseDate("11/16/2026"))
        assertEquals(day("2026-11-16"), CsvBackup.parseDate("16.11.2026"))
        assertEquals(null, CsvBackup.parseDate("16-11-26"))
        assertEquals(null, CsvBackup.parseDate("32/01/2026"))
    }

    @Test
    fun importingTheSameFileTwiceAddsNothing() {
        assertEquals(emptyList<BackupRow>(), CsvBackup.onlyNew(existing = sample, incoming = sample))
    }

    @Test
    fun onlyMissingRowsAreAdded() {
        val incoming = sample + BackupRow(day("2026-11-17"), "Food", 100, "new")
        assertEquals(listOf(incoming.last()), CsvBackup.onlyNew(existing = sample, incoming = incoming))
    }

    @Test
    fun repeatedIdenticalRowsAreCounted() {
        val ticket = BackupRow(day("2026-11-16"), "Transport", 2000, "Bus")
        // File has three bus tickets, the app has one: two are new.
        assertEquals(listOf(ticket, ticket), CsvBackup.onlyNew(existing = listOf(ticket), incoming = listOf(ticket, ticket, ticket)))
    }

    @Test
    fun classificationNamesCompareWithoutCase() {
        val inApp = BackupRow(day("2026-11-16"), "Food", 100, "x")
        val inFile = BackupRow(day("2026-11-16"), "food", 100, "x")
        assertEquals(emptyList<BackupRow>(), CsvBackup.onlyNew(listOf(inApp), listOf(inFile)))
    }
}

class CsvBackupNamesTest {

    @Test
    fun longClassificationNamesAreShortenedTheSameWayEveryTime() {
        val longName = "A very long classification name that goes on"
        val text = "2026-01-05,$longName,10,a\n"
        val row = CsvBackup.parse(text).rows.single()
        assertEquals(Categories.MAX_NAME_LENGTH, row.classification.length)
        // Exporting what was imported and importing the original file again adds nothing.
        assertEquals(emptyList<BackupRow>(), CsvBackup.onlyNew(listOf(row), CsvBackup.parse(text).rows))
    }
}
