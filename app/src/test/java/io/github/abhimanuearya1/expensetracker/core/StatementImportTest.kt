package io.github.abhimanuearya1.expensetracker.core

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/** Importing a PhonePe transaction statement saved as CSV. The data here is made up. */
class StatementImportTest {

    private fun day(iso: String) = LocalDate.parse(iso).toEpochDay()

    private val statement = """
        Transaction Statement for 9000000000
        Duration,"18 Oct, 2026 - 17 Nov, 2026"

        Date,Time,Transaction Details,Transaction ID,UTR,Transaction Type,Credit/debit instrument,Amount
        "Nov 16, 2026","08:01 am","Payment to Example Store","T001","111","DEBIT","Paid by XXXXXXXXXX1234","689"
        "Nov 15, 2026","07:05 pm","Paid to City hospital","T002","222","DEBIT","Paid by 111122XXXXXXXX05","60"
        "Nov 15, 2026","06:00 pm","Received from A Friend","T003","333","CREDIT","Credited to XXXXXXXXXX1234","500"
        "Nov 06, 2026","05:21 pm","Paid to Super Market","T004","444","DEBIT","Paid by 111122XXXXXXXX05","2245.76"
        "Oct 18, 2026","01:52 pm","Paid to SOME   PERSON","T005","555","DEBIT","Paid by XX9999","50,000"

        This is an automatically generated statement. Customer(s) are requested to immediately notify PhonePe in case
        of any errors in the statement at https://example.com/statement and visit https://example.com/
        terms-conditions/ for Terms & Conditions and Privacy Policy.

        Disclaimer : Do not fall prey to fictitious offers of winning prizes, money circulation schemes and cheap funds,
        etc. through SMS, emails and calls. The contents of this email and document are confidential and intended for
    """.trimIndent()

    @Test
    fun readsDateAmountDetailsAndInstrument() {
        val parsed = CsvBackup.parse(statement)
        assertEquals(
            listOf(
                BackupRow(day("2026-11-16"), "", 68900, "Payment to Example Store", "XXXXXXXXXX1234"),
                BackupRow(day("2026-11-15"), "", 6000, "Paid to City hospital", "111122XXXXXXXX05"),
                BackupRow(day("2026-11-06"), "", 224576, "Paid to Super Market", "111122XXXXXXXX05"),
                BackupRow(day("2026-10-18"), "", 5000000, "Paid to SOME   PERSON", "XX9999"),
            ),
            parsed.rows,
        )
    }

    @Test
    fun titleAndFooterLinesAreNotCountedAsErrorsAndMoneyReceivedIsLeftOut() {
        val parsed = CsvBackup.parse(statement)
        assertEquals(0, parsed.unreadable)
        assertEquals(0, parsed.rows.count { it.details.contains("Received") })
    }

    @Test
    fun everythingArrivesWithoutAClassification() {
        assertEquals(setOf(""), CsvBackup.parse(statement).rows.map { it.classification }.toSet())
    }

    @Test
    fun importingAgainAfterClassifyingAddsNothing() {
        val imported = CsvBackup.parse(statement).rows
        // In the app the user has since filed them and renamed the payment method.
        val inApp = imported.map { it.copy(classification = "Shopping", paymentMethod = "Savings account") }
        assertEquals(emptyList<BackupRow>(), CsvBackup.onlyNew(existing = inApp, incoming = imported))
    }

    @Test
    fun datesWithMonthNames() {
        assertEquals(day("2026-11-16"), CsvBackup.parseDate("Nov 16, 2026"))
        assertEquals(day("2026-11-06"), CsvBackup.parseDate("Nov 06, 2026"))
        assertEquals(day("2026-11-16"), CsvBackup.parseDate("November 16 2026"))
        assertEquals(day("2026-11-16"), CsvBackup.parseDate("16 Nov, 2026"))
        assertEquals(day("2026-11-16"), CsvBackup.parseDate("16-Nov-2026"))
        assertEquals(null, CsvBackup.parseDate("Nov 31, 2026"))
        assertEquals(null, CsvBackup.parseDate("Foo 16, 2026"))
    }

    @Test
    fun ownFormatKeepsThePaymentMethod() {
        val rows = listOf(
            BackupRow(day("2026-11-16"), "Food", 25000, "Lunch", "UPI"),
            BackupRow(day("2026-11-15"), "", 100, "", ""),
        )
        assertEquals(rows, CsvBackup.parse(CsvBackup.export(rows)).rows)
    }

    @Test
    fun paymentMethodTotals() {
        fun item(id: Long, cents: Long, methodId: Long?, method: String?) =
            ExpenseItem(id, cents, day("2026-11-16"), 1, "", "Food", 1, methodId, method, 7)
        val totals = Summary.totalsByPaymentMethod(
            listOf(item(1, 300, 1, "UPI"), item(2, 100, 2, "Cash"), item(3, 600, 1, "UPI"), item(4, 50, null, null)),
        )
        assertEquals(listOf("UPI", "Cash", null), totals.map { it.name })
        assertEquals(listOf(900L, 100L, 50L), totals.map { it.totalCents })
        assertEquals(listOf(1L, 2L, null), totals.map { it.categoryId })
    }

    @Test
    fun searchFindsPaymentMethod() {
        val items = listOf(
            ExpenseItem(1, 100, day("2026-11-16"), null, "tea", null, null, 1, "UPI", 7),
            ExpenseItem(2, 100, day("2026-11-16"), null, "coffee", null, null, 2, "Cash", 7),
        )
        assertEquals(listOf(2L), SearchIndex(items, { it.toString() }).search("cash").map { it.id })
    }
}
