package io.github.abhimanuearya1.expensetracker.core

/** Minimal CSV reader/writer (RFC 4180): quoted fields, doubled quotes, line breaks inside quotes. */
object Csv {

    fun escape(field: String): String {
        val needsQuotes = field.any { it == ',' || it == ';' || it == '"' || it == '\n' || it == '\r' } ||
            field.startsWith(' ') || field.endsWith(' ')
        return if (needsQuotes) "\"" + field.replace("\"", "\"\"") + "\"" else field
    }

    fun line(fields: List<String>): String = fields.joinToString(",") { escape(it) }

    /**
     * Spreadsheet programs save "CSV" with a semicolon or a tab in some regions,
     * so the separator is whichever of the three appears most in the first line.
     */
    fun detectDelimiter(text: String): Char {
        var commas = 0
        var semicolons = 0
        var tabs = 0
        var inQuotes = false
        for (c in text) {
            if (c == '"') {
                inQuotes = !inQuotes
            } else if (!inQuotes) {
                when (c) {
                    ',' -> commas++
                    ';' -> semicolons++
                    '\t' -> tabs++
                    '\n', '\r' -> if (commas + semicolons + tabs > 0) break
                }
            }
        }
        return when {
            semicolons > commas && semicolons >= tabs -> ';'
            tabs > commas && tabs > semicolons -> '\t'
            else -> ','
        }
    }

    /** Parses [text] into rows of fields. Blank lines are dropped. */
    fun parse(text: String, delimiter: Char = detectDelimiter(text)): List<List<String>> {
        val rows = ArrayList<List<String>>()
        var row = ArrayList<String>()
        val field = StringBuilder()
        var inQuotes = false
        var i = if (text.startsWith('﻿')) 1 else 0

        fun endField() {
            row.add(field.toString())
            field.setLength(0)
        }

        fun endRow() {
            endField()
            if (!(row.size == 1 && row[0].isBlank())) rows.add(row)
            row = ArrayList()
        }

        while (i < text.length) {
            val c = text[i]
            if (inQuotes) {
                if (c == '"') {
                    if (i + 1 < text.length && text[i + 1] == '"') {
                        field.append('"')
                        i++
                    } else {
                        inQuotes = false
                    }
                } else {
                    field.append(c)
                }
            } else {
                when (c) {
                    '"' -> inQuotes = true
                    delimiter -> endField()
                    '\r' -> {
                        if (i + 1 < text.length && text[i + 1] == '\n') i++
                        endRow()
                    }
                    '\n' -> endRow()
                    else -> field.append(c)
                }
            }
            i++
        }
        if (field.isNotEmpty() || row.isNotEmpty()) endRow()
        return rows
    }
}
