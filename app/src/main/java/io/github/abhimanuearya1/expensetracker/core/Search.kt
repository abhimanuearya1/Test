package io.github.abhimanuearya1.expensetracker.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Text search over expenses.
 *
 * Every word typed must appear somewhere in the expense: its details, its
 * classification, its amount ("250", "1,250.00") or its date ("16 nov",
 * "november", "2026-11-16"). Upper/lower case does not matter.
 *
 * The searchable text of each expense is prepared once, so each keystroke only
 * does cheap substring checks.
 */
class SearchIndex(
    items: List<ExpenseItem>,
    formatAmount: (Long) -> String,
    locale: Locale = Locale.getDefault(),
) {
    private class Entry(val item: ExpenseItem, val text: String)

    private val entries: List<Entry>

    init {
        val shortDate = DateTimeFormatter.ofPattern("EEE d MMM yyyy", locale)
        val longDate = DateTimeFormatter.ofPattern("EEEE d MMMM yyyy", locale)
        val numericDate = DateTimeFormatter.ofPattern("dd/MM/yyyy", locale)
        entries = items.map { item ->
            val date = LocalDate.ofEpochDay(item.epochDay)
            val text = listOf(
                item.details,
                item.categoryName ?: Categories.UNCATEGORIZED,
                Money.toPlainString(item.amountCents),
                formatAmount(item.amountCents),
                date.toString(),
                date.format(shortDate),
                date.format(longDate),
                date.format(numericDate),
            ).joinToString("\n").lowercase(Locale.ROOT)
            Entry(item, text)
        }
    }

    val size: Int get() = entries.size

    /** Matching expenses in their original order. A blank query matches everything. */
    fun search(query: String): List<ExpenseItem> {
        val words = query.lowercase(Locale.ROOT).split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return entries.map { it.item }
        return entries.filter { entry -> words.all { entry.text.contains(it) } }.map { it.item }
    }
}
