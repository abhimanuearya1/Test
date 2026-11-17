package io.github.abhimanuearya1.expensetracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Locale

class MoneyTest {

    /** Parse as on a phone whose language uses a dot for decimals (India, US, UK). */
    private fun parse(text: String) = Money.parseToCents(text, decimalSeparator = '.')

    /** Parse as on a phone whose language uses a comma for decimals (Germany, France). */
    private fun parseComma(text: String) = Money.parseToCents(text, decimalSeparator = ',')

    @Test
    fun plainAmounts() {
        assertEquals(25000L, parse("250"))
        assertEquals(25050L, parse("250.5"))
        assertEquals(25050L, parse("250.50"))
        assertEquals(5L, parse("0.05"))
        assertEquals(50L, parse(".5"))
        assertEquals(500L, parse("5."))
        assertEquals(0L, parse("0"))
        assertEquals(1200L, parse("00012"))
        assertEquals(25000L, parse("  250 "))
    }

    @Test
    fun groupedAmounts() {
        assertEquals(123400L, parse("1,234"))
        assertEquals(123450L, parse("1,234.50"))
        assertEquals(12345678L, parse("1,23,456.78"))
        assertEquals(123456700L, parse("1,234,567"))
        assertEquals(123450L, parse("1 234.50"))
    }

    @Test
    fun otherSeparatorIsAcceptedAsDecimalPointWhenUnambiguous() {
        assertEquals(1250L, parse("12,5"))
        assertEquals(1250L, parse("12,50"))
        assertEquals(123450L, parse("1.234,5"))
        assertEquals(1250L, parseComma("12.5"))
        assertEquals(1250L, parseComma("12,50"))
        assertEquals(123400L, parseComma("1.234"))
        assertEquals(123450L, parseComma("1.234,50"))
    }

    @Test
    fun tooManyDecimalsIsRejectedNotRounded() {
        assertNull(parse("12.345"))
        assertNull(parse("0.001"))
        assertNull(parseComma("12,345"))
    }

    @Test
    fun invalidInput() {
        assertNull(parse(""))
        assertNull(parse("   "))
        assertNull(parse("."))
        assertNull(parse(","))
        assertNull(parse("abc"))
        assertNull(parse("12a"))
        assertNull(parse("-5"))
        assertNull(parse("1.2.3"))
        assertNull(parse("1,2345"))
        assertNull(parse("12,34,5"))
        assertNull(parse(",123"))
        assertNull(parse("1,234,56"))
        assertNull(parse("1.234.567"))
        assertNull(parse("1,234.567"))
    }

    @Test
    fun sizeLimit() {
        assertEquals(Money.MAX_CENTS, parse("999999999.99"))
        assertNull(parse("1000000000"))
        assertNull(parse("99999999999999999999"))
    }

    @Test
    fun plainAndInputStrings() {
        assertEquals("1234.50", Money.toPlainString(123450))
        assertEquals("0.05", Money.toPlainString(5))
        assertEquals("12.00", Money.toPlainString(1200))
        assertEquals("12", Money.toInputString(1200))
        assertEquals("12.50", Money.toInputString(1250))
        assertEquals("0.05", Money.toInputString(5))
    }

    @Test
    fun whatTheAppPrefillsCanBeParsedAgain() {
        for (cents in listOf(1L, 5L, 99L, 100L, 1250L, 123456L, 100000000L, Money.MAX_CENTS)) {
            assertEquals(cents, parse(Money.toInputString(cents)))
            assertEquals(cents, parseComma(Money.toInputString(cents)))
            assertEquals(cents, parse(Money.toPlainString(cents)))
        }
    }

    private fun locale(language: String, region: String) =
        Locale.Builder().setLanguage(language).setRegion(region).build()

    @Test
    fun formatsWithThePhonesCurrencyByDefault() {
        // Amounts below one lakh are used here on purpose: Android groups Indian
        // amounts as 1,23,456 but a desktop JVM (where these tests run) prints
        // 123,456. The lakh grouping itself is checked in the on-device tests.
        val india = MoneyFormatter(null, locale("en", "IN"))
        assertEquals("₹23,456.50", india.format(2345650))
        assertEquals("₹0.05", india.format(5))
        assertEquals("₹", india.symbol)

        val us = MoneyFormatter(null, locale("en", "US"))
        assertEquals("$1,234.50", us.format(123450))
        assertEquals("$", us.symbol)
    }

    @Test
    fun chosenCurrencyOverridesThePhone() {
        val rupeesOnUsPhone = MoneyFormatter("INR", locale("en", "US"))
        assertEquals("₹23,456.50", rupeesOnUsPhone.format(2345650))
        assertEquals("₹", rupeesOnUsPhone.symbol)

        val dollarsOnIndianPhone = MoneyFormatter("USD", locale("en", "IN"))
        assertEquals("$250.00", dollarsOnIndianPhone.format(25000))
    }

    @Test
    fun currencyWithoutDecimalsShowsNone() {
        assertEquals("¥1,234", MoneyFormatter("JPY", locale("en", "US")).format(123400))
    }

    @Test
    fun noRegionMeansNoSymbol() {
        val noRegion = MoneyFormatter(null, Locale.Builder().setLanguage("en").build())
        assertEquals("1,234.50", noRegion.format(123450))
        assertEquals("", noRegion.symbol)
    }

    @Test
    fun unknownCurrencyCodeFallsBackToThePhone() {
        assertEquals("$1.00", MoneyFormatter("NOPE", locale("en", "US")).format(100))
    }
}
