package io.github.abhimanuearya1.expensetracker.core

/**
 * One expense as shown in lists: the stored row plus the name and colour of its
 * classification. Both are null when the expense has no classification.
 *
 * Everything in the `core` package is plain Kotlin with no Android imports, so it
 * can be unit-tested on any JVM.
 */
data class ExpenseItem(
    val id: Long,
    /** Amount in hundredths of the currency unit (see [Money]). */
    val amountCents: Long,
    /** Calendar date as days since 1970-01-01 (LocalDate.toEpochDay). */
    val epochDay: Long,
    val categoryId: Long?,
    val details: String,
    val categoryName: String?,
    val categoryColor: Int?,
)
