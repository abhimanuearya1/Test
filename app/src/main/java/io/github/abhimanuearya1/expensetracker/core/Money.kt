package io.github.abhimanuearya1.expensetracker.core

import java.math.BigDecimal
import java.text.DecimalFormatSymbols
import java.text.NumberFormat
import java.util.Currency
import java.util.Locale

/**
 * Amounts are stored as whole hundredths ("cents") in a Long, never as floating
 * point, so totals are always exact.
 */
object Money {

    /** Largest amount the app accepts: 999,999,999.99 */
    const val MAX_CENTS: Long = 99_999_999_999L

    fun defaultDecimalSeparator(): Char =
        DecimalFormatSymbols.getInstance(Locale.getDefault()).decimalSeparator

    /**
     * Turns what the user typed into cents, or null if it is not a valid amount.
     *
     * Accepts plain numbers ("250", "250.5", "250.50") and grouped numbers
     * ("1,234.50", "1,23,456"). The character that is not the decimal separator
     * of the phone's language is still accepted as a decimal point when that is
     * the only sensible reading ("12,5" on an English phone), because some
     * keyboards offer only one of the two keys.
     *
     * More than two decimals is rejected rather than rounded, so a slip of the
     * finger ("12.345") cannot silently become a different amount.
     */
    fun parseToCents(input: String, decimalSeparator: Char = defaultDecimalSeparator()): Long? {
        val s = input.filterNot { it == ' ' || it == ' ' || it == ' ' }
        if (s.isEmpty()) return null
        if (s.any { it !in '0'..'9' && it != '.' && it != ',' }) return null

        var whole = s
        var fraction = ""
        val lastSep = s.lastIndexOfAny(charArrayOf('.', ','))
        if (lastSep >= 0) {
            val ch = s[lastSep]
            val digitsAfter = s.length - lastSep - 1
            val occursOnce = s.indexOf(ch) == lastSep
            val isDecimalPoint = if (ch == decimalSeparator) {
                if (!occursOnce || digitsAfter > 2) return null
                true
            } else {
                occursOnce && digitsAfter <= 2
            }
            if (isDecimalPoint) {
                whole = s.substring(0, lastSep)
                fraction = s.substring(lastSep + 1)
            }
        }

        val wholeDigits = removeGrouping(whole) ?: return null
        if (wholeDigits.isEmpty() && fraction.isEmpty()) return null
        val significant = wholeDigits.trimStart('0')
        if (significant.length > 9) return null

        val wholeValue = if (significant.isEmpty()) 0L else significant.toLong()
        val fractionValue = when (fraction.length) {
            0 -> 0L
            1 -> fraction.toLong() * 10
            else -> fraction.toLong()
        }
        return wholeValue * 100 + fractionValue
    }

    /** "1,23,456" -> "123456". Null when the separators are not valid digit grouping. */
    private fun removeGrouping(whole: String): String? {
        val groups = whole.split('.', ',')
        if (groups.size == 1) return whole
        val separators = whole.filter { it == '.' || it == ',' }
        if (separators.any { it != separators[0] }) return null
        if (groups.first().isEmpty() || groups.first().length > 3) return null
        if (groups.last().length != 3) return null
        for (i in 1 until groups.size - 1) {
            // Two-digit groups are the Indian lakh/crore style: 1,23,456
            if (groups[i].length !in 2..3) return null
        }
        return groups.joinToString("")
    }

    /** "1234.50": always a dot and two decimals. Used in CSV files and for search. */
    fun toPlainString(cents: Long): String {
        val sign = if (cents < 0) "-" else ""
        val abs = Math.abs(cents)
        return sign + (abs / 100) + "." + (abs % 100).toString().padStart(2, '0')
    }

    /** Text used to pre-fill the amount box when editing: "1234" or "1234.50". */
    fun toInputString(cents: Long): String =
        if (cents % 100 == 0L) (cents / 100).toString() else toPlainString(cents)
}

/**
 * Formats cents for display, e.g. "₹1,23,456.50".
 *
 * @param currencyCode ISO code chosen in the app ("INR", "USD", ...), or null to
 *   follow the phone's region setting.
 */
class MoneyFormatter(currencyCode: String?, locale: Locale = Locale.getDefault()) {

    private val numberFormat: NumberFormat

    /** Symbol shown next to the amount box, e.g. "₹". Empty when there is none. */
    val symbol: String

    init {
        val chosen = currencyCode?.let { code -> runCatching { Currency.getInstance(code) }.getOrNull() }
        // Rupee amounts read best with lakh grouping even on a phone set to another region.
        val formatLocale = if (chosen?.currencyCode == "INR" && locale.country != "IN") {
            Locale.Builder().setLanguage("en").setRegion("IN").build()
        } else {
            locale
        }
        val currencyFormat = NumberFormat.getCurrencyInstance(formatLocale)
        if (chosen != null) currencyFormat.currency = chosen
        val currency = currencyFormat.currency
        if (currency == null || currency.currencyCode == "XXX") {
            // The phone has no region set, so there is no currency to show.
            numberFormat = NumberFormat.getNumberInstance(formatLocale).apply {
                minimumFractionDigits = 2
                maximumFractionDigits = 2
            }
            symbol = ""
        } else {
            val digits = currency.defaultFractionDigits.let { if (it < 0) 2 else minOf(it, 2) }
            currencyFormat.minimumFractionDigits = digits
            currencyFormat.maximumFractionDigits = digits
            numberFormat = currencyFormat
            symbol = currency.getSymbol(formatLocale)
        }
    }

    // NumberFormat is not thread-safe.
    @Synchronized
    fun format(cents: Long): String = numberFormat.format(BigDecimal.valueOf(cents, 2))
}
