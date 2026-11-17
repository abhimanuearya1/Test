package io.github.abhimanuearya1.expensetracker.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private fun day(iso: String) = LocalDate.parse(iso).toEpochDay()

private val FOOD = 1L
private val TRANSPORT = 2L

private fun item(
    id: Long,
    date: String,
    cents: Long,
    categoryId: Long?,
    details: String,
): ExpenseItem {
    val name = when (categoryId) {
        FOOD -> "Food"
        TRANSPORT -> "Transport"
        else -> null
    }
    return ExpenseItem(id, cents, day(date), categoryId, details, name, categoryId?.toInt())
}

private val ITEMS = listOf(
    item(5, "2026-11-16", 25000, FOOD, "Lunch at Blue Cafe"),
    item(4, "2026-11-16", 4000, TRANSPORT, "Auto to office"),
    item(3, "2026-11-15", 125000, FOOD, "Dinner with family"),
    item(2, "2026-10-02", 9950, null, "Gift wrap"),
    item(1, "2025-12-31", 250000, TRANSPORT, ""),
)

class SearchTest {

    private val english = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val formatter = MoneyFormatter("INR", english)
    private val index = SearchIndex(ITEMS, formatter::format, english)

    private fun ids(query: String) = index.search(query).map { it.id }

    @Test
    fun blankQueryReturnsEverythingInOrder() {
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), ids(""))
        assertEquals(listOf(5L, 4L, 3L, 2L, 1L), ids("   "))
    }

    @Test
    fun matchesDetailsIgnoringCase() {
        assertEquals(listOf(5L), ids("lunch"))
        assertEquals(listOf(5L), ids("BLUE cafe"))
        assertEquals(listOf(4L), ids("office"))
    }

    @Test
    fun matchesClassificationName() {
        assertEquals(listOf(5L, 3L), ids("food"))
        assertEquals(listOf(4L, 1L), ids("transport"))
        assertEquals(listOf(2L), ids("uncategorized"))
    }

    @Test
    fun everyWordMustMatch() {
        assertEquals(listOf(3L), ids("food dinner"))
        assertEquals(listOf(3L), ids("dinner food"))
        assertEquals(emptyList<Long>(), ids("food office"))
    }

    @Test
    fun matchesAmounts() {
        assertEquals(listOf(5L, 3L, 1L), ids("250"))
        assertEquals(listOf(3L), ids("1250"))
        assertEquals(listOf(3L), ids("1,250"))
        assertEquals(listOf(2L), ids("99.5"))
        assertEquals(listOf(4L), ids("40.00"))
    }

    @Test
    fun matchesDates() {
        assertEquals(listOf(5L, 4L, 3L), ids("nov"))
        assertEquals(listOf(5L, 4L, 3L), ids("november 2026"))
        assertEquals(listOf(5L, 4L), ids("16 nov"))
        assertEquals(listOf(2L), ids("2026-10-02"))
        assertEquals(listOf(1L), ids("31/12/2025"))
        assertEquals(listOf(1L), ids("2025"))
    }

    @Test
    fun noMatch() {
        assertEquals(emptyList<Long>(), ids("pizza"))
    }
}

class SummaryTest {

    @Test
    fun groupsByDayNewestFirstWithTotals() {
        val groups = Summary.groupByDay(ITEMS)
        assertEquals(listOf("2026-11-16", "2026-11-15", "2026-10-02", "2025-12-31").map(::day), groups.map { it.epochDay })
        assertEquals(listOf(29000L, 125000L, 9950L, 250000L), groups.map { it.totalCents })
        assertEquals(listOf(5L, 4L), groups[0].items.map { it.id })
    }

    @Test
    fun groupingSortsDaysEvenIfInputIsNot() {
        val shuffled = listOf(ITEMS[3], ITEMS[0], ITEMS[4], ITEMS[1])
        assertEquals(
            listOf("2026-11-16", "2026-10-02", "2025-12-31").map(::day),
            Summary.groupByDay(shuffled).map { it.epochDay },
        )
    }

    @Test
    fun totalsPerClassificationLargestFirst() {
        val november = ITEMS.filter { it.epochDay >= day("2026-11-01") }
        val totals = Summary.totalsByCategory(november)
        assertEquals(listOf("Food", "Transport"), totals.map { it.name })
        assertEquals(listOf(150000L, 4000L), totals.map { it.totalCents })
        assertEquals(listOf(2, 1), totals.map { it.count })
        assertEquals(150000f / 154000f, totals[0].share, 0.0001f)
    }

    @Test
    fun uncategorisedExpensesGetTheirOwnRow() {
        val totals = Summary.totalsByCategory(ITEMS)
        val uncategorised = totals.single { it.categoryId == null }
        assertNull(uncategorised.name)
        assertEquals(9950L, uncategorised.totalCents)
    }

    @Test
    fun emptyInput() {
        assertEquals(emptyList<DayGroup>(), Summary.groupByDay(emptyList()))
        assertEquals(emptyList<CategoryTotal>(), Summary.totalsByCategory(emptyList()))
    }

    @Test
    fun percentText() {
        assertEquals("45%", Summary.percentText(0.451f))
        assertEquals("100%", Summary.percentText(1f))
        assertEquals("<1%", Summary.percentText(0.001f))
        assertEquals("0%", Summary.percentText(0f))
    }
}

class DatesTest {

    private val english = Locale.Builder().setLanguage("en").setRegion("IN").build()
    private val today = day("2026-11-16")

    @Test
    fun dayHeaders() {
        assertEquals("Today · Mon, 16 Nov", Dates.dayHeader(today, today, english))
        assertEquals("Yesterday · Sun, 15 Nov", Dates.dayHeader(today - 1, today, english))
        assertEquals("Sat, 14 Nov", Dates.dayHeader(today - 2, today, english))
        assertEquals("Wed, 31 Dec 2025", Dates.dayHeader(day("2025-12-31"), today, english))
    }

    @Test
    fun fullDateAndMonth() {
        assertEquals("Mon, 16 Nov 2026", Dates.full(today, english))
        assertEquals("November 2026", Dates.month(YearMonth.of(2026, 11), english))
    }

    @Test
    fun monthBoundaries() {
        val february = YearMonth.of(2028, 2)
        assertEquals(day("2028-02-01"), Dates.firstDay(february))
        assertEquals(day("2028-02-29"), Dates.lastDay(february))
        assertEquals(february, Dates.monthOf(day("2028-02-15")))
    }
}

class CategoriesTest {

    private val existing = listOf("Food", "Transport")

    @Test
    fun cleansNames() {
        assertEquals("Eating out", Categories.cleanName("  Eating   out "))
        assertEquals("", Categories.cleanName("   "))
    }

    @Test
    fun acceptsNewName() {
        assertNull(Categories.nameProblem("Pets", existing))
    }

    @Test
    fun rejectsEmptyLongReservedAndDuplicateNames() {
        assertEquals("Enter a name", Categories.nameProblem("  ", existing))
        assertEquals("Use 30 characters or fewer", Categories.nameProblem("x".repeat(31), existing))
        assertEquals("This name is reserved", Categories.nameProblem("uncategorized", existing))
        assertEquals("“food” already exists", Categories.nameProblem(" food ", existing))
    }

    @Test
    fun renamingToItselfOrChangingCaseIsAllowed() {
        assertNull(Categories.nameProblem("Food", existing, renamingFrom = "Food"))
        assertNull(Categories.nameProblem("FOOD", existing, renamingFrom = "Food"))
        assertEquals("“Transport” already exists", Categories.nameProblem("Transport", existing, renamingFrom = "Food"))
    }

    @Test
    fun nextColourPrefersUnused() {
        assertEquals(Categories.PALETTE[0], Categories.nextColor(emptyList()))
        assertEquals(Categories.PALETTE[2], Categories.nextColor(Categories.PALETTE.take(2)))
        // All used: still returns a palette colour.
        assert(Categories.nextColor(Categories.PALETTE) in Categories.PALETTE)
    }

    @Test
    fun everyPaletteColourHasAName() {
        assertEquals(Categories.PALETTE.size, Categories.PALETTE_NAMES.size)
        assertEquals(Categories.PALETTE.size, Categories.PALETTE.toSet().size)
    }

    @Test
    fun defaultsAreValidAndDistinct() {
        val names = Categories.DEFAULTS.map { it.name }
        assertEquals(names.size, names.map { it.lowercase() }.toSet().size)
        for (d in Categories.DEFAULTS) {
            assertNull(Categories.nameProblem(d.name, emptyList()))
            assert(d.color in Categories.PALETTE) { "${d.name} uses a colour outside the palette" }
        }
    }
}
