package io.github.abhimanuearya1.expensetracker.core

/** The expenses of one calendar day, with their total. */
data class DayGroup(
    val epochDay: Long,
    val totalCents: Long,
    val items: List<ExpenseItem>,
)

/** Spending for one classification within a set of expenses. */
data class CategoryTotal(
    /** Null for uncategorised expenses. */
    val categoryId: Long?,
    val name: String?,
    val color: Int?,
    val totalCents: Long,
    val count: Int,
    /** Part of the overall total, 0..1. */
    val share: Float,
)

object Summary {

    /** Groups expenses by day, newest day first. The order inside a day is kept. */
    fun groupByDay(items: List<ExpenseItem>): List<DayGroup> {
        val byDay = LinkedHashMap<Long, MutableList<ExpenseItem>>()
        for (item in items) byDay.getOrPut(item.epochDay) { ArrayList() }.add(item)
        return byDay.entries
            .sortedByDescending { it.key }
            .map { (day, list) -> DayGroup(day, list.sumOf { it.amountCents }, list) }
    }

    /** Totals per classification, largest first. */
    fun totalsByCategory(items: List<ExpenseItem>): List<CategoryTotal> {
        val grandTotal = items.sumOf { it.amountCents }
        return items.groupBy { it.categoryId }
            .map { (id, list) ->
                val total = list.sumOf { it.amountCents }
                CategoryTotal(
                    categoryId = id,
                    name = list.first().categoryName,
                    color = list.first().categoryColor,
                    totalCents = total,
                    count = list.size,
                    share = if (grandTotal > 0) total.toFloat() / grandTotal else 0f,
                )
            }
            .sortedWith(compareByDescending<CategoryTotal> { it.totalCents }.thenBy { it.name ?: "￿" })
    }

    /** Totals per payment method, largest first. Expenses without one are grouped under a null name. */
    fun totalsByPaymentMethod(items: List<ExpenseItem>): List<CategoryTotal> =
        totalsByCategory(
            items.map {
                it.copy(
                    categoryId = it.paymentMethodId,
                    categoryName = it.paymentMethodName,
                    categoryColor = it.paymentMethodColor,
                )
            }
        )

    /** "45%", "<1%" for small non-zero shares. */
    fun percentText(share: Float): String {
        val percent = Math.round(share * 100)
        return if (percent == 0 && share > 0f) "<1%" else "$percent%"
    }
}
