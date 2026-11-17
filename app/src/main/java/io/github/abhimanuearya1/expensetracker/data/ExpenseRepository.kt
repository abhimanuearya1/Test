package io.github.abhimanuearya1.expensetracker.data

import androidx.room.withTransaction
import io.github.abhimanuearya1.expensetracker.core.BackupRow
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.CsvBackup
import io.github.abhimanuearya1.expensetracker.core.Dates
import io.github.abhimanuearya1.expensetracker.core.ExpenseItem
import io.github.abhimanuearya1.expensetracker.core.ImportOutcome
import kotlinx.coroutines.flow.Flow
import java.time.YearMonth

/** Everything the screens need from the database. */
class ExpenseRepository(private val db: AppDatabase) {

    private val expenses = db.expenseDao()
    private val categories = db.categoryDao()

    fun observeMonth(month: YearMonth): Flow<List<ExpenseItem>> =
        expenses.observeBetween(Dates.firstDay(month), Dates.lastDay(month))

    fun observeAll(): Flow<List<ExpenseItem>> = expenses.observeAll()

    fun observeCategories(): Flow<List<CategoryWithCount>> = categories.observeWithCounts()

    /** Adds a new expense when [id] is null, otherwise updates that expense. */
    suspend fun saveExpense(id: Long?, amountCents: Long, epochDay: Long, categoryId: Long?, details: String) {
        val row = Expense(
            id = id ?: 0,
            amountCents = amountCents,
            epochDay = epochDay,
            categoryId = categoryId,
            details = details.trim(),
        )
        if (id == null) expenses.insert(row) else expenses.update(row)
    }

    suspend fun deleteExpense(id: Long) = expenses.delete(id)

    /** Returns the id of the new classification. */
    suspend fun addCategory(name: String, color: Int): Long =
        categories.insert(Category(name = Categories.cleanName(name), color = color))

    suspend fun updateCategory(id: Long, name: String, color: Int) =
        categories.update(id, Categories.cleanName(name), color)

    /** Expenses that used the classification are kept and become uncategorised. */
    suspend fun deleteCategory(id: Long) = categories.delete(id)

    /** All expenses in the form written to a CSV file, newest first. */
    suspend fun exportRows(): List<BackupRow> =
        expenses.getAll().map { BackupRow(it.epochDay, it.categoryName.orEmpty(), it.amountCents, it.details) }

    /**
     * Adds the expenses from a CSV file that are not already in the app, creating
     * any classifications the file mentions that do not exist yet. Either
     * everything is added or, if something fails, nothing is.
     */
    suspend fun importCsv(text: String): ImportOutcome {
        val parsed = CsvBackup.parse(text)
        return db.withTransaction {
            val fresh = CsvBackup.onlyNew(existing = exportRows(), incoming = parsed.rows)
            val existing = categories.getAll()
            val idByName = HashMap<String, Long>()
            for (c in existing) idByName[c.name.lowercase()] = c.id
            val usedColors = existing.map { it.color }.toMutableList()

            val rows = ArrayList<Expense>(fresh.size)
            for (r in fresh) {
                var categoryId: Long? = null
                if (r.classification.isNotEmpty()) {
                    val key = r.classification.lowercase()
                    categoryId = idByName[key]
                    if (categoryId == null) {
                        val color = Categories.nextColor(usedColors)
                        usedColors.add(color)
                        categoryId = categories.insert(Category(name = r.classification, color = color))
                        idByName[key] = categoryId
                    }
                }
                rows.add(
                    Expense(
                        amountCents = r.amountCents,
                        epochDay = r.epochDay,
                        categoryId = categoryId,
                        details = r.details,
                    )
                )
            }
            expenses.insertAll(rows)
            ImportOutcome(
                added = rows.size,
                alreadyPresent = parsed.rows.size - rows.size,
                unreadable = parsed.unreadable,
            )
        }
    }
}
