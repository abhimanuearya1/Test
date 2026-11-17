package io.github.abhimanuearya1.expensetracker.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.abhimanuearya1.expensetracker.core.Categories

/**
 * The app's SQLite database, stored only on the phone.
 *
 * When the tables change in a later version, raise [version] and add a Room
 * Migration to [MIGRATIONS]. Do not use fallbackToDestructiveMigration: it would
 * erase the user's expenses.
 *
 * Version history:
 *  1 – expenses, categories (app 2.0)
 *  2 – payment methods, attachments (app 2.1)
 */
@Database(
    entities = [Expense::class, Category::class, PaymentMethod::class, Attachment::class],
    version = 2,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

    abstract fun paymentMethodDao(): PaymentMethodDao

    abstract fun attachmentDao(): AttachmentDao

    companion object {
        const val FILE_NAME = "expenses.db"

        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: configure(
                    Room.databaseBuilder(context.applicationContext, AppDatabase::class.java, FILE_NAME)
                ).also { instance = it }
            }

        /** A throw-away database with the same starting data, for tests. */
        fun inMemory(context: Context): AppDatabase =
            configure(Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java))

        /** Closes and deletes the on-phone database so the next [get] starts empty. Tests only. */
        @VisibleForTesting
        fun reset(context: Context) {
            synchronized(this) {
                instance?.close()
                instance = null
                context.applicationContext.deleteDatabase(FILE_NAME)
            }
        }

        private fun configure(builder: RoomDatabase.Builder<AppDatabase>): AppDatabase =
            builder.addCallback(AddDefaults).addMigrations(*MIGRATIONS).build()

        /** Runs once, when the database file is first created. */
        private object AddDefaults : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                for (category in Categories.DEFAULTS) {
                    db.execSQL(
                        "INSERT INTO categories (name, color) VALUES (?, ?)",
                        arrayOf<Any>(category.name, category.color),
                    )
                }
                addDefaultPaymentMethods(db)
            }
        }

        private fun addDefaultPaymentMethods(db: SupportSQLiteDatabase) {
            for (method in Categories.DEFAULT_PAYMENT_METHODS) {
                db.execSQL(
                    "INSERT INTO payment_methods (name, color) VALUES (?, ?)",
                    arrayOf<Any>(method.name, method.color),
                )
            }
        }

        /** Adds payment methods and attachments to a database created by app 2.0. Existing expenses are untouched. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `payment_methods` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL COLLATE NOCASE, " +
                        "`color` INTEGER NOT NULL)"
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payment_methods_name` ON `payment_methods` (`name`)")
                db.execSQL(
                    "ALTER TABLE `expenses` ADD COLUMN `paymentMethodId` INTEGER " +
                        "REFERENCES `payment_methods`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_paymentMethodId` ON `expenses` (`paymentMethodId`)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `attachments` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`expenseId` INTEGER NOT NULL, " +
                        "`fileName` TEXT NOT NULL, " +
                        "`displayName` TEXT NOT NULL, " +
                        "`mimeType` TEXT NOT NULL, " +
                        "FOREIGN KEY(`expenseId`) REFERENCES `expenses`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_attachments_expenseId` ON `attachments` (`expenseId`)")
                addDefaultPaymentMethods(db)
            }
        }

        private val MIGRATIONS = arrayOf<Migration>(MIGRATION_1_2)
    }
}
