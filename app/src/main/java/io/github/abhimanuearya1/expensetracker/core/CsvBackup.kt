package io.github.abhimanuearya1.expensetracker.core

import java.time.DateTimeException
import java.time.LocalDate

/** One expense as written to / read from a CSV file. */
data class BackupRow(
    val epochDay: Long,
    /** Classification name; empty means uncategorised. */
    val classification: String,
    val amountCents: Long,
    val details: String,
)

data class ParsedBackup(
    val rows: List<BackupRow>,
    /** Lines that could not be understood (bad date or amount). */
    val unreadable: Int,
)

/** What happened when a file was imported. */
data class ImportOutcome(
    val added: Int,
    /** Expenses in the file that the app already had. */
    val alreadyPresent: Int,
    val unreadable: Int,
) {
    /** One line to show the user. */
    fun message(): String {
        val skipped = if (unreadable > 0) " · ${TextTools.plural(unreadable, "line")} could not be read" else ""
        return when {
            added > 0 && alreadyPresent > 0 ->
                "Imported ${TextTools.plural(added, "expense")} · $alreadyPresent already in the app$skipped"
            added > 0 -> "Imported ${TextTools.plural(added, "expense")}$skipped"
            alreadyPresent > 0 -> "Nothing new: all ${TextTools.plural(alreadyPresent, "expense")} are already in the app$skipped"
            else -> "No expenses found in that file. It needs to be a CSV file."
        }
    }
}

/**
 * The export/import file format. One line per expense:
 *
 *     Date,Classification,Amount,Details
 *     2026-11-16,Food,250.00,Lunch at cafe
 *
 * The same file opens directly in Excel / Google Sheets and can be imported back
 * into the app, so it doubles as the backup format.
 */
object CsvBackup {

    val HEADER = listOf("Date", "Classification", "Amount", "Details")

    fun export(rows: List<BackupRow>): String {
        val sb = StringBuilder()
        sb.append(Csv.line(HEADER)).append("\r\n")
        for (r in rows) {
            sb.append(
                Csv.line(
                    listOf(
                        LocalDate.ofEpochDay(r.epochDay).toString(),
                        r.classification,
                        Money.toPlainString(r.amountCents),
                        r.details,
                    )
                )
            ).append("\r\n")
        }
        return sb.toString()
    }

    fun parse(text: String): ParsedBackup {
        val table = Csv.parse(text)
        if (table.isEmpty()) return ParsedBackup(emptyList(), 0)

        var dateCol = 0
        var classCol = 1
        var amountCol = 2
        var detailsCol = 3
        var firstDataRow = 0

        // If the first line names its columns, follow those names so that files
        // with re-ordered or extra columns still import.
        val header = table[0].map { it.trim().lowercase() }
        val namedDate = header.indexOf("date")
        val namedAmount = header.indexOf("amount")
        if (namedDate >= 0 && namedAmount >= 0) {
            dateCol = namedDate
            amountCol = namedAmount
            classCol = header.indexOfFirst { it == "classification" || it == "category" }
            detailsCol = header.indexOfFirst { it == "details" || it == "description" || it == "notes" || it == "note" }
            firstDataRow = 1
        }

        val rows = ArrayList<BackupRow>()
        var unreadable = 0
        for (i in firstDataRow until table.size) {
            val line = table[i]
            val day = parseDate(line.getOrNull(dateCol))
            val cents = parseAmount(line.getOrNull(amountCol))
            if (day == null || cents == null || cents <= 0) {
                unreadable++
                continue
            }
            var classification = Categories.cleanName(line.getOrNull(classCol).orEmpty())
                .take(Categories.MAX_NAME_LENGTH).trim()
            if (classification.equals(Categories.UNCATEGORIZED, ignoreCase = true)) classification = ""
            rows.add(BackupRow(day, classification, cents, line.getOrNull(detailsCol).orEmpty().trim()))
        }
        return ParsedBackup(rows, unreadable)
    }

    /**
     * Rows of [incoming] that are not already in [existing], so importing the same
     * file twice does not duplicate anything. Counts are respected: if the file
     * has three identical lines and the app already has one, two are added.
     */
    fun onlyNew(existing: List<BackupRow>, incoming: List<BackupRow>): List<BackupRow> {
        val counts = HashMap<BackupRow, Int>()
        for (e in existing) {
            val k = key(e)
            counts[k] = (counts[k] ?: 0) + 1
        }
        val result = ArrayList<BackupRow>()
        for (r in incoming) {
            val k = key(r)
            val have = counts[k] ?: 0
            if (have > 0) counts[k] = have - 1 else result.add(r)
        }
        return result
    }

    private fun key(r: BackupRow) =
        r.copy(classification = r.classification.trim().lowercase(), details = r.details.trim())

    private val ISO_DATE = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(\D.*)?$""")
    private val DAY_FIRST_DATE = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})(\D.*)?$""")

    /**
     * Reads 2026-11-16 (what the app writes) and also 16-11-2026 / 16/11/2026,
     * which is what a spreadsheet may turn the date into when the file is edited
     * and saved again. Returns the epoch day, or null.
     */
    fun parseDate(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        if (s.isEmpty()) return null
        ISO_DATE.find(s)?.let { m ->
            return toEpochDay(m.groupValues[1].toInt(), m.groupValues[2].toInt(), m.groupValues[3].toInt())
        }
        DAY_FIRST_DATE.find(s)?.let { m ->
            val a = m.groupValues[1].toInt()
            val b = m.groupValues[2].toInt()
            val year = m.groupValues[3].toInt()
            // Day first, unless that is impossible and month-first works (11/16/2026).
            return if (b > 12 && a <= 12) toEpochDay(year, a, b) else toEpochDay(year, b, a)
        }
        return null
    }

    private fun toEpochDay(year: Int, month: Int, day: Int): Long? =
        try {
            LocalDate.of(year, month, day).toEpochDay()
        } catch (e: DateTimeException) {
            null
        }

    private fun parseAmount(raw: String?): Long? {
        val s = raw?.trim().orEmpty()
        // Negative amounts (refunds) are not something the app can store.
        if (s.isEmpty() || s.contains('-') || s.contains('(')) return null
        // Drop a currency symbol or code a spreadsheet may have added around the number.
        val number = s.trim { it !in '0'..'9' }
        return Money.parseToCents(number, decimalSeparator = '.')
    }
}
