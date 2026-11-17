package io.github.abhimanuearya1.expensetracker.core

import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Date text used across the app. Dates are plain calendar days (no time zone). */
object Dates {

    private val formatters = ConcurrentHashMap<Pair<String, Locale>, DateTimeFormatter>()

    private fun formatter(pattern: String, locale: Locale): DateTimeFormatter =
        formatters.getOrPut(pattern to locale) { DateTimeFormatter.ofPattern(pattern, locale) }

    fun today(): Long = LocalDate.now().toEpochDay()

    /** "Mon, 16 Nov 2026" */
    fun full(epochDay: Long, locale: Locale = Locale.getDefault()): String =
        LocalDate.ofEpochDay(epochDay).format(formatter("EEE, d MMM yyyy", locale))

    /** "16 Nov 2026" */
    fun short(epochDay: Long, locale: Locale = Locale.getDefault()): String =
        LocalDate.ofEpochDay(epochDay).format(formatter("d MMM yyyy", locale))

    /**
     * Heading for one day in a list: "Today · Mon, 16 Nov", "Yesterday · Sun, 15 Nov",
     * otherwise just the date. The year is added when it is not the current year.
     */
    fun dayHeader(epochDay: Long, today: Long, locale: Locale = Locale.getDefault()): String {
        val date = LocalDate.ofEpochDay(epochDay)
        val sameYear = date.year == LocalDate.ofEpochDay(today).year
        val text = date.format(formatter(if (sameYear) "EEE, d MMM" else "EEE, d MMM yyyy", locale))
        return when (epochDay) {
            today -> "Today · $text"
            today - 1 -> "Yesterday · $text"
            else -> text
        }
    }

    /** "November 2026" */
    fun month(month: YearMonth, locale: Locale = Locale.getDefault()): String =
        month.format(formatter("MMMM yyyy", locale))

    fun firstDay(month: YearMonth): Long = month.atDay(1).toEpochDay()

    fun lastDay(month: YearMonth): Long = month.atEndOfMonth().toEpochDay()

    fun monthOf(epochDay: Long): YearMonth = YearMonth.from(LocalDate.ofEpochDay(epochDay))
}
