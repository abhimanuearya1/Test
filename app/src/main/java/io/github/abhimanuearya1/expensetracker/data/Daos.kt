package io.github.abhimanuearya1.expensetracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import io.github.abhimanuearya1.expensetracker.core.ExpenseItem
import kotlinx.coroutines.flow.Flow

private const val ITEM_COLUMNS =
    "SELECT e.id, e.amountCents, e.epochDay, e.categoryId, e.details, " +
        "c.name AS categoryName, c.color AS categoryColor, " +
        "e.paymentMethodId, p.name AS paymentMethodName, p.color AS paymentMethodColor, " +
        "(SELECT COUNT(*) FROM attachments a WHERE a.expenseId = e.id) AS attachmentCount " +
        "FROM expenses e " +
        "LEFT JOIN categories c ON c.id = e.categoryId " +
        "LEFT JOIN payment_methods p ON p.id = e.paymentMethodId"

private const val NEWEST_FIRST = " ORDER BY e.epochDay DESC, e.id DESC"

@Dao
interface ExpenseDao {

    /** Expenses dated within [fromDay]..[toDay], newest first. Uses the date index. */
    @Query(ITEM_COLUMNS + " WHERE e.epochDay BETWEEN :fromDay AND :toDay" + NEWEST_FIRST)
    fun observeBetween(fromDay: Long, toDay: Long): Flow<List<ExpenseItem>>

    @Query(ITEM_COLUMNS + NEWEST_FIRST)
    fun observeAll(): Flow<List<ExpenseItem>>

    @Query(ITEM_COLUMNS + NEWEST_FIRST)
    suspend fun getAll(): List<ExpenseItem>

    @Insert
    suspend fun insert(expense: Expense): Long

    @Insert
    suspend fun insertAll(expenses: List<Expense>)

    @Update
    suspend fun update(expense: Expense)

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface CategoryDao {

    @Query(
        "SELECT c.id, c.name, c.color, COUNT(e.id) AS expenseCount " +
            "FROM categories c LEFT JOIN expenses e ON e.categoryId = c.id " +
            "GROUP BY c.id ORDER BY c.name COLLATE NOCASE"
    )
    fun observeWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM categories ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<Category>

    @Insert
    suspend fun insert(category: Category): Long

    @Query("UPDATE categories SET name = :name, color = :color WHERE id = :id")
    suspend fun update(id: Long, name: String, color: Int)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface PaymentMethodDao {

    @Query(
        "SELECT p.id, p.name, p.color, COUNT(e.id) AS expenseCount " +
            "FROM payment_methods p LEFT JOIN expenses e ON e.paymentMethodId = p.id " +
            "GROUP BY p.id ORDER BY p.name COLLATE NOCASE"
    )
    fun observeWithCounts(): Flow<List<CategoryWithCount>>

    @Query("SELECT * FROM payment_methods ORDER BY name COLLATE NOCASE")
    suspend fun getAll(): List<PaymentMethod>

    @Insert
    suspend fun insert(method: PaymentMethod): Long

    @Query("UPDATE payment_methods SET name = :name, color = :color WHERE id = :id")
    suspend fun update(id: Long, name: String, color: Int)

    @Query("DELETE FROM payment_methods WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface AttachmentDao {

    @Query("SELECT * FROM attachments WHERE expenseId = :expenseId ORDER BY id")
    suspend fun forExpense(expenseId: Long): List<Attachment>

    @Query("SELECT fileName FROM attachments")
    suspend fun allFileNames(): List<String>

    @Insert
    suspend fun insert(attachment: Attachment): Long

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun delete(id: Long)
}
