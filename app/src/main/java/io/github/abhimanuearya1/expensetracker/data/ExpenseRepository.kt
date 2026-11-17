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
    private val paymentMethods = db.paymentMethodDao()
    private val attachments = db.attachmentDao()

    fun observeMonth(month: YearMonth): Flow<List<ExpenseItem>> =
        expenses.observeBetween(Dates.firstDay(month), Dates.lastDay(month))

    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<ExpenseItem>> =
        expenses.observeBetween(fromDay, toDay)

    fun observeAll(): Flow<List<ExpenseItem>> = expenses.observeAll()

    fun observeCategories(): Flow<List<CategoryWithCount>> = categories.observeWithCounts()

    fun observePaymentMethods(): Flow<List<CategoryWithCount>> = paymentMethods.observeWithCounts()

    /**
     * Adds a new expense when [id] is null, otherwise updates that expense.
     *
     * @param attachmentDrafts the complete list of attachments the expense should
     *   have afterwards, or null to leave its attachments as they are.
     * @return file names of attachments that were removed and can now be deleted.
     */
    suspend fun saveExpense(
        id: Long?,
        amountCents: Long,
        epochDay: Long,
        categoryId: Long?,
        details: String,
        paymentMethodId: Long? = null,
        attachmentDrafts: List<AttachmentDraft>? = null,
    ): List<String> = db.withTransaction {
        val row = Expense(
            id = id ?: 0,
            amountCents = amountCents,
            epochDay = epochDay,
            categoryId = categoryId,
            details = details.trim(),
            paymentMethodId = paymentMethodId,
        )
        val expenseId = if (id == null) {
            expenses.insert(row)
        } else {
            expenses.update(row)
            id
        }
        val removedFiles = ArrayList<String>()
        if (attachmentDrafts != null) {
            val keptIds = attachmentDrafts.mapNotNull { it.id }.toSet()
            for (existing in attachments.forExpense(expenseId)) {
                if (existing.id !in keptIds) {
                    attachments.delete(existing.id)
                    removedFiles.add(existing.fileName)
                }
            }
            for (draft in attachmentDrafts) {
                if (draft.id == null) {
                    attachments.insert(
                        Attachment(
                            expenseId = expenseId,
                            fileName = draft.fileName,
                            displayName = draft.displayName,
                            mimeType = draft.mimeType,
                        )
                    )
                }
            }
        }
        removedFiles
    }

    suspend fun attachmentsOf(expenseId: Long): List<AttachmentDraft> =
        attachments.forExpense(expenseId).map { AttachmentDraft(it.id, it.fileName, it.displayName, it.mimeType) }

    suspend fun allAttachmentFileNames(): Set<String> = attachments.allFileNames().toSet()

    /** Deletes the expense and its attachment rows. Returns the attachment files that can now be deleted. */
    suspend fun deleteExpense(id: Long): List<String> = db.withTransaction {
        val files = attachments.forExpense(id).map { it.fileName }
        expenses.delete(id)
        files
    }

    /** Returns the id of the new classification. */
    suspend fun addCategory(name: String, color: Int): Long =
        categories.insert(Category(name = Categories.cleanName(name), color = color))

    suspend fun updateCategory(id: Long, name: String, color: Int) =
        categories.update(id, Categories.cleanName(name), color)

    /** Expenses that used the classification are kept and become uncategorised. */
    suspend fun deleteCategory(id: Long) = categories.delete(id)

    /** Returns the id of the new payment method. */
    suspend fun addPaymentMethod(name: String, color: Int): Long =
        paymentMethods.insert(PaymentMethod(name = Categories.cleanName(name), color = color))

    suspend fun updatePaymentMethod(id: Long, name: String, color: Int) =
        paymentMethods.update(id, Categories.cleanName(name), color)

    /** Expenses that used the payment method are kept, without one. */
    suspend fun deletePaymentMethod(id: Long) = paymentMethods.delete(id)

    /** All expenses in the form written to a CSV file, newest first. */
    suspend fun exportRows(): List<BackupRow> =
        expenses.getAll().map {
            BackupRow(it.epochDay, it.categoryName.orEmpty(), it.amountCents, it.details, it.paymentMethodName.orEmpty())
        }

    /**
     * Adds the expenses from a CSV file (the app's own export or a PhonePe
     * statement) that are not already in the app, creating any classifications
     * and payment methods the file mentions that do not exist yet. Either
     * everything is added or, if something fails, nothing is.
     */
    suspend fun importCsv(text: String): ImportOutcome {
        val parsed = CsvBackup.parse(text)
        return db.withTransaction {
            val fresh = CsvBackup.onlyNew(existing = exportRows(), incoming = parsed.rows)

            val existingCategories = categories.getAll()
            val categoryIds = HashMap<String, Long>()
            for (c in existingCategories) categoryIds[c.name.lowercase()] = c.id
            val categoryColors = existingCategories.map { it.color }.toMutableList()

            val existingMethods = paymentMethods.getAll()
            val methodIds = HashMap<String, Long>()
            for (m in existingMethods) methodIds[m.name.lowercase()] = m.id
            val methodColors = existingMethods.map { it.color }.toMutableList()

            val rows = ArrayList<Expense>(fresh.size)
            for (r in fresh) {
                var categoryId: Long? = null
                if (r.classification.isNotEmpty()) {
                    val key = r.classification.lowercase()
                    categoryId = categoryIds[key]
                    if (categoryId == null) {
                        val color = Categories.nextColor(categoryColors)
                        categoryColors.add(color)
                        categoryId = categories.insert(Category(name = r.classification, color = color))
                        categoryIds[key] = categoryId
                    }
                }
                var methodId: Long? = null
                if (r.paymentMethod.isNotEmpty()) {
                    val key = r.paymentMethod.lowercase()
                    methodId = methodIds[key]
                    if (methodId == null) {
                        val color = Categories.nextColor(methodColors)
                        methodColors.add(color)
                        methodId = paymentMethods.insert(PaymentMethod(name = r.paymentMethod, color = color))
                        methodIds[key] = methodId
                    }
                }
                rows.add(
                    Expense(
                        amountCents = r.amountCents,
                        epochDay = r.epochDay,
                        categoryId = categoryId,
                        details = r.details,
                        paymentMethodId = methodId,
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
