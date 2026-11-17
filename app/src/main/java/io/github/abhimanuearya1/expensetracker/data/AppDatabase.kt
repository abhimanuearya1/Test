package io.github.abhimanuearya1.expensetracker.data

import android.content.Context
import androidx.annotation.VisibleForTesting
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.github.abhimanuearya1.expensetracker.core.Categories

/**
 * The app's SQLite database, stored only on the phone.
 *
 * When the tables change in a later version, raise [version] and add a Room
 * Migration. Do not use fallbackToDestructiveMigration: it would erase the
 * user's expenses.
 */
@Database(entities = [Expense::class, Category::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun expenseDao(): ExpenseDao

    abstract fun categoryDao(): CategoryDao

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
            builder.addCallback(AddDefaultCategories).build()

        /** Runs once, when the database file is first created. */
        private object AddDefaultCategories : RoomDatabase.Callback() {
            override fun onCreate(db: SupportSQLiteDatabase) {
                for (category in Categories.DEFAULTS) {
                    db.execSQL(
                        "INSERT INTO categories (name, color) VALUES (?, ?)",
                        arrayOf<Any>(category.name, category.color),
                    )
                }
            }
        }
    }
}
