package io.github.abhimanuearya1.expensetracker.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A classification the user can assign to expenses ("Food", "Transport", ...).
 * Called "classification" on screen and "category" in the code.
 */
@Entity(
    tableName = "categories",
    indices = [Index(value = ["name"], unique = true)],
)
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Unique without regard to upper/lower case. */
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    /** ARGB colour used for this classification's badge and chart bar. */
    val color: Int,
)

/** How an expense was paid ("Cash", "UPI", a particular card or account). An editable list, like classifications. */
@Entity(
    tableName = "payment_methods",
    indices = [Index(value = ["name"], unique = true)],
)
data class PaymentMethod(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(collate = ColumnInfo.NOCASE) val name: String,
    val color: Int,
)

/**
 * A single expense.
 *
 * Deleting a classification or payment method keeps the expenses that used it:
 * the id here becomes null ("Uncategorized" / no payment method).
 */
@Entity(
    tableName = "expenses",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.SET_NULL,
        ),
        ForeignKey(
            entity = PaymentMethod::class,
            parentColumns = ["id"],
            childColumns = ["paymentMethodId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("categoryId"), Index("epochDay"), Index("paymentMethodId")],
)
data class Expense(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Amount in hundredths of the currency unit, so sums are exact. */
    val amountCents: Long,
    /** Calendar date as days since 1970-01-01 (LocalDate.toEpochDay). */
    val epochDay: Long,
    val categoryId: Long?,
    val details: String,
    val paymentMethodId: Long? = null,
)

/**
 * A supporting document (photo, PDF, any file) attached to an expense. The file
 * itself is a private copy kept by [AttachmentStore]; this row points to it.
 * Rows disappear with their expense.
 */
@Entity(
    tableName = "attachments",
    foreignKeys = [
        ForeignKey(
            entity = Expense::class,
            parentColumns = ["id"],
            childColumns = ["expenseId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("expenseId")],
)
data class Attachment(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val expenseId: Long,
    /** Name of the copy inside the app's storage. */
    val fileName: String,
    /** The file's original name, shown to the user. */
    val displayName: String,
    val mimeType: String,
)

/** A classification or payment method together with how many expenses use it. */
data class CategoryWithCount(
    val id: Long,
    val name: String,
    val color: Int,
    val expenseCount: Int,
)

/**
 * An attachment as edited in the expense form. [id] is null for a file that was
 * added in the form and is not saved with the expense yet.
 */
data class AttachmentDraft(
    val id: Long?,
    val fileName: String,
    val displayName: String,
    val mimeType: String,
)
