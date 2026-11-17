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
    /** Payment method name; empty means none. */
    val paymentMethod: String = "",
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
 * Reading and writing expenses as CSV.
 *
 * The app's own format, one line per expense:
 *
 *     Date,Classification,Amount,Details,Payment Method
 *     2026-11-16,Food,250.00,Lunch at cafe,UPI
 *
 * It opens directly in Excel / Google Sheets and can be imported back, so it
 * doubles as the backup format (attached documents are not part of it).
 *
 * Import also understands a PhonePe transaction statement saved as CSV: the
 * amount, date and details are taken from it, the "Credit/debit instrument"
 * becomes the payment method, and the expenses arrive without a classification.
 * Money received (CREDIT lines) is left out.
 */
object CsvBackup {

    val HEADER = listOf("Date", "Classification", "Amount", "Details", "Payment Method")

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
                        r.paymentMethod,
                    )
                )
            ).append("\r\n")
        }
        return sb.toString()
    }

    private val CLASSIFICATION_HEADERS = setOf("classification", "category")
    private val DETAILS_HEADERS = setOf("details", "description", "notes", "note", "transaction details")
    private val PAYMENT_HEADERS = setOf("payment method", "credit/debit instrument", "instrument", "paid by")
    private val TYPE_HEADERS = setOf("transaction type", "type")

    /** Bank and wallet statements start with a few title lines, so the column names are looked for this far down. */
    private const val HEADER_SEARCH_ROWS = 20

    fun parse(text: String): ParsedBackup {
        val table = Csv.parse(text)
        if (table.isEmpty()) return ParsedBackup(emptyList(), 0)

        // Without column names the app's own order is assumed.
        var dateCol = 0
        var classCol = 1
        var amountCol = 2
        var detailsCol = 3
        var paymentCol = 4
        var typeCol = -1
        var firstDataRow = 0

        // If a line names its columns, follow those names so that files with
        // re-ordered or extra columns still import.
        for (i in 0 until minOf(table.size, HEADER_SEARCH_ROWS)) {
            val header = table[i].map { it.trim().lowercase() }
            val namedDate = header.indexOf("date")
            val namedAmount = header.indexOf("amount")
            if (namedDate >= 0 && namedAmount >= 0) {
                dateCol = namedDate
                amountCol = namedAmount
                classCol = header.indexOfFirst { it in CLASSIFICATION_HEADERS }
                detailsCol = header.indexOfFirst { it in DETAILS_HEADERS }
                paymentCol = header.indexOfFirst { it in PAYMENT_HEADERS }
                typeCol = header.indexOfFirst { it in TYPE_HEADERS }
                firstDataRow = i + 1
                break
            }
        }
        // Shorter lines are titles or footnotes, not transactions.
        val cellsNeeded = maxOf(dateCol, amountCol) + 1

        val rows = ArrayList<BackupRow>()
        var unreadable = 0
        for (i in firstDataRow until table.size) {
            val line = table[i]
            if (line.size < cellsNeeded) continue
            // Statements list money received as well; only money spent is an expense.
            val type = line.getOrNull(typeCol)?.trim()?.lowercase().orEmpty()
            if (type == "credit" || type == "cr") continue

            val day = parseDate(line.getOrNull(dateCol))
            val cents = parseAmount(line.getOrNull(amountCol))
            if (day == null || cents == null || cents <= 0) {
                unreadable++
                continue
            }
            var classification = cleanName(line.getOrNull(classCol))
            if (classification.equals(Categories.UNCATEGORIZED, ignoreCase = true)) classification = ""
            rows.add(
                BackupRow(
                    epochDay = day,
                    classification = classification,
                    amountCents = cents,
                    details = line.getOrNull(detailsCol).orEmpty().trim(),
                    paymentMethod = cleanName(line.getOrNull(paymentCol)?.trim()?.removePrefixIgnoreCase("Paid by")),
                )
            )
        }
        return ParsedBackup(rows, unreadable)
    }

    private fun cleanName(raw: String?): String =
        Categories.cleanName(raw.orEmpty()).take(Categories.MAX_NAME_LENGTH).trim()

    private fun String.removePrefixIgnoreCase(prefix: String): String =
        if (startsWith(prefix, ignoreCase = true)) substring(prefix.length) else this

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

    // Classification and payment method are left out on purpose: they are what
    // people change after importing, and that must not make the same
    // transaction import a second time.
    private fun key(r: BackupRow) = r.copy(classification = "", paymentMethod = "", details = r.details.trim())

    private val ISO_DATE = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})(\D.*)?$""")
    private val DAY_FIRST_DATE = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{4})(\D.*)?$""")
    private val MONTH_NAME_FIRST = Regex("""^([A-Za-z]{3,9})\.?\s+(\d{1,2}),?\s+(\d{4})(\D.*)?$""")
    private val DAY_THEN_MONTH_NAME = Regex("""^(\d{1,2})[\s-]+([A-Za-z]{3,9})\.?,?[\s-]+(\d{4})(\D.*)?$""")
    private val MONTHS = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec")

    /**
     * Reads 2026-11-16 (what the app writes), 16-11-2026 / 16/11/2026 (what a
     * spreadsheet may turn it into), and dates with a month name such as
     * "Nov 16, 2026" or "16 Nov 2026" (statements). Returns the epoch day, or null.
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
        MONTH_NAME_FIRST.find(s)?.let { m ->
            val month = monthNumber(m.groupValues[1]) ?: return null
            return toEpochDay(m.groupValues[3].toInt(), month, m.groupValues[2].toInt())
        }
        DAY_THEN_MONTH_NAME.find(s)?.let { m ->
            val month = monthNumber(m.groupValues[2]) ?: return null
            return toEpochDay(m.groupValues[3].toInt(), month, m.groupValues[1].toInt())
        }
        return null
    }

    private fun monthNumber(name: String): Int? {
        val index = MONTHS.indexOf(name.take(3).lowercase())
        return if (index >= 0) index + 1 else null
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
