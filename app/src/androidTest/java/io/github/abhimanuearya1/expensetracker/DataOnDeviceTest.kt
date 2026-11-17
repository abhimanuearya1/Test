package io.github.abhimanuearya1.expensetracker

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.MoneyFormatter
import io.github.abhimanuearya1.expensetracker.data.AppDatabase
import io.github.abhimanuearya1.expensetracker.data.AttachmentStore
import io.github.abhimanuearya1.expensetracker.data.ExpenseRepository
import io.github.abhimanuearya1.expensetracker.ui.ExpenseViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.util.Locale

private fun day(iso: String) = LocalDate.parse(iso).toEpochDay()

/** The real SQLite database, running on a phone or emulator. */
@RunWith(AndroidJUnit4::class)
class DatabaseTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: ExpenseRepository
    private lateinit var ids: Map<String, Long>

    @Before
    fun setUp() {
        val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
        db = AppDatabase.inMemory(context)
        repo = ExpenseRepository(db)
        ids = runBlocking { db.categoryDao().getAll().associate { it.name to it.id } }
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun startsWithTheDefaultClassificationsInAlphabeticalOrder() {
        runBlocking {
            val names = db.categoryDao().getAll().map { it.name }
            assertEquals(Categories.DEFAULTS.map { it.name }.sortedBy { it.lowercase() }, names)
        }
    }

    @Test
    fun monthListHasOnlyThatMonthNewestFirst() {
        runBlocking {
            repo.saveExpense(null, 100, day("2026-10-31"), ids["Food"], "october")
            repo.saveExpense(null, 200, day("2026-11-01"), ids["Food"], "first")
            repo.saveExpense(null, 300, day("2026-11-30"), ids["Transport"], "last")
            repo.saveExpense(null, 400, day("2026-11-30"), null, "later the same day")
            repo.saveExpense(null, 500, day("2026-12-01"), ids["Food"], "december")

            val november = repo.observeMonth(YearMonth.of(2026, 11)).first()
            assertEquals(listOf("later the same day", "last", "first"), november.map { it.details })
            assertEquals(listOf(null, "Transport", "Food"), november.map { it.categoryName })
            assertEquals(5, repo.observeAll().first().size)
        }
    }

    @Test
    fun savingWithAnIdChangesThatExpense() {
        runBlocking {
            repo.saveExpense(null, 100, day("2026-11-01"), ids["Food"], " tea ")
            val saved = repo.observeAll().first().single()
            assertEquals("tea", saved.details)

            repo.saveExpense(saved.id, 250, day("2026-11-02"), ids["Travel"], "train")
            val changed = repo.observeAll().first().single()
            assertEquals(saved.id, changed.id)
            assertEquals(250L, changed.amountCents)
            assertEquals(day("2026-11-02"), changed.epochDay)
            assertEquals("Travel", changed.categoryName)

            repo.deleteExpense(saved.id)
            assertTrue(repo.observeAll().first().isEmpty())
        }
    }

    @Test
    fun deletingAClassificationKeepsItsExpensesAsUncategorised() {
        runBlocking {
            repo.saveExpense(null, 100, day("2026-11-01"), ids["Food"], "kept")
            repo.deleteCategory(ids.getValue("Food"))

            val item = repo.observeAll().first().single()
            assertEquals("kept", item.details)
            assertNull(item.categoryId)
            assertNull(item.categoryName)
            assertTrue(db.categoryDao().getAll().none { it.name == "Food" })
        }
    }

    @Test
    fun classificationsCanBeAddedRenamedAndCounted() {
        runBlocking {
            val pets = repo.addCategory("  Pets  ", Categories.PALETTE[0])
            repo.saveExpense(null, 100, day("2026-11-01"), pets, "")
            repo.saveExpense(null, 100, day("2026-11-02"), pets, "")
            repo.updateCategory(pets, "Pet care", Categories.PALETTE[1])

            val row = repo.observeCategories().first().single { it.id == pets }
            assertEquals("Pet care", row.name)
            assertEquals(Categories.PALETTE[1], row.color)
            assertEquals(2, row.expenseCount)
            assertEquals(0, repo.observeCategories().first().single { it.name == "Food" }.expenseCount)
        }
    }

    @Test
    fun classificationNamesAreUniqueWhateverTheCase() {
        runBlocking {
            try {
                repo.addCategory("FOOD", Categories.PALETTE[0])
                fail("A second “Food” was accepted")
            } catch (expected: SQLiteConstraintException) {
                // good
            }
        }
    }

    @Test
    fun importAddsNewRowsCreatesClassificationsAndSkipsDuplicates() {
        runBlocking {
            repo.saveExpense(null, 25000, day("2026-11-16"), ids["Food"], "Lunch")
            val csv = "Date,Classification,Amount,Details\n" +
                "2026-11-16,food,250.00,Lunch\n" + // already in the app
                "2026-11-15,Pets,99.50,Vet\n" + // new classification
                "2026-11-14,,10,\n" + // uncategorised
                "bad line,x,y,z\n"

            val first = repo.importCsv(csv)
            assertEquals(2, first.added)
            assertEquals(1, first.alreadyPresent)
            assertEquals(1, first.unreadable)

            val all = repo.observeAll().first()
            assertEquals(3, all.size)
            assertEquals("Pets", all.single { it.details == "Vet" }.categoryName)
            assertNull(all.single { it.amountCents == 1000L }.categoryId)

            val second = repo.importCsv(csv)
            assertEquals(0, second.added)
            assertEquals(3, second.alreadyPresent)
            assertEquals(3, repo.observeAll().first().size)
        }
    }
}

/** Things added in app 2.1: payment methods, attachments, statement import, and upgrading a 2.0 database. */
@RunWith(AndroidJUnit4::class)
class Version21Test {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    @Before
    fun setUp() {
        AppDatabase.reset(context)
    }

    @After
    fun tearDown() {
        AppDatabase.reset(context)
    }

    @Test
    fun aDatabaseFromVersion20IsUpgradedWithoutLosingAnything() {
        // Build the database exactly as app 2.0 created it, with one classification and two expenses.
        val path = context.getDatabasePath(AppDatabase.FILE_NAME)
        path.parentFile?.mkdirs()
        SQLiteDatabase.openOrCreateDatabase(path, null).use { old ->
            old.execSQL("CREATE TABLE `categories` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `name` TEXT NOT NULL COLLATE NOCASE, `color` INTEGER NOT NULL)")
            old.execSQL("CREATE UNIQUE INDEX `index_categories_name` ON `categories` (`name`)")
            old.execSQL(
                "CREATE TABLE `expenses` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `amountCents` INTEGER NOT NULL, " +
                    "`epochDay` INTEGER NOT NULL, `categoryId` INTEGER, `details` TEXT NOT NULL, " +
                    "FOREIGN KEY(`categoryId`) REFERENCES `categories`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )"
            )
            old.execSQL("CREATE INDEX `index_expenses_categoryId` ON `expenses` (`categoryId`)")
            old.execSQL("CREATE INDEX `index_expenses_epochDay` ON `expenses` (`epochDay`)")
            old.execSQL("INSERT INTO categories (id, name, color) VALUES (1, 'Food', -1)")
            old.execSQL("INSERT INTO expenses (amountCents, epochDay, categoryId, details) VALUES (25000, ${day("2026-11-16")}, 1, 'Lunch')")
            old.execSQL("INSERT INTO expenses (amountCents, epochDay, categoryId, details) VALUES (4000, ${day("2026-11-15")}, NULL, 'Bus')")
            old.version = 1
        }

        // Opening it with the current app runs the upgrade; Room then checks every table matches.
        runBlocking {
            val repo = ExpenseRepository(AppDatabase.get(context))
            val all = repo.observeAll().first()
            assertEquals(listOf("Lunch", "Bus"), all.map { it.details })
            assertEquals(listOf(25000L, 4000L), all.map { it.amountCents })
            assertEquals(listOf("Food", null), all.map { it.categoryName })
            assertEquals(listOf(null, null), all.map { it.paymentMethodName })
            assertEquals(listOf(0, 0), all.map { it.attachmentCount })
            // The new payment-method list arrives with its defaults and is usable straight away.
            val methods = AppDatabase.get(context).paymentMethodDao().getAll()
            assertEquals(Categories.DEFAULT_PAYMENT_METHODS.map { it.name }.sortedBy { it.lowercase() }, methods.map { it.name })
            repo.saveExpense(all[0].id, 25000, all[0].epochDay, 1, "Lunch", methods.first { it.name == "UPI" }.id)
            assertEquals("UPI", repo.observeAll().first()[0].paymentMethodName)
        }
    }

    @Test
    fun paymentMethodIsStoredAndSurvivesItsDeletionAsNotSet() {
        runBlocking {
            val db = AppDatabase.get(context)
            val repo = ExpenseRepository(db)
            val cash = db.paymentMethodDao().getAll().single { it.name == "Cash" }.id
            repo.saveExpense(null, 500, day("2026-11-16"), null, "Tea", cash)
            assertEquals("Cash", repo.observeAll().first().single().paymentMethodName)
            assertEquals(1, repo.observePaymentMethods().first().single { it.id == cash }.expenseCount)

            repo.deletePaymentMethod(cash)
            val item = repo.observeAll().first().single()
            assertEquals("Tea", item.details)
            assertNull(item.paymentMethodId)
        }
    }

    @Test
    fun documentsCanBeAttachedReplacedAndAreRemovedWithTheExpense() {
        runBlocking {
            val repo = ExpenseRepository(AppDatabase.get(context))
            val store = AttachmentStore(context)
            val source = File(context.cacheDir, "receipt.pdf").apply { writeText("pretend this is a PDF") }

            // Attach a copy of a file to a new expense.
            val first = store.copyIn(Uri.fromFile(source))
            assertEquals("receipt.pdf", first.displayName)
            assertTrue(first.fileName.endsWith(".pdf"))
            assertTrue(store.exists(first.fileName))
            source.delete() // the app keeps its own copy
            repo.saveExpense(null, 100, day("2026-11-16"), null, "With receipt", null, listOf(first))

            val expense = repo.observeAll().first().single()
            assertEquals(1, expense.attachmentCount)
            val saved = repo.attachmentsOf(expense.id)
            assertEquals(listOf("receipt.pdf"), saved.map { it.displayName })
            assertEquals("pretend this is a PDF", context.contentResolver.openInputStream(store.uriFor(saved[0].fileName))!!.use { it.readBytes().decodeToString() })

            // Saving with an empty list detaches it and reports the file to delete.
            val removed = repo.saveExpense(expense.id, 100, expense.epochDay, null, "With receipt", null, emptyList())
            assertEquals(listOf(first.fileName), removed)
            assertEquals(0, repo.observeAll().first().single().attachmentCount)

            // Saving without mentioning attachments leaves them alone; deleting the expense reports its files.
            val second = store.copyIn(Uri.fromFile(File(context.cacheDir, "photo.jpg").apply { writeText("jpg") }))
            repo.saveExpense(expense.id, 100, expense.epochDay, null, "With photo", null, listOf(second))
            repo.saveExpense(expense.id, 200, expense.epochDay, null, "With photo")
            assertEquals(1, repo.observeAll().first().single().attachmentCount)
            assertEquals(listOf(second.fileName), repo.deleteExpense(expense.id))
            assertTrue(repo.allAttachmentFileNames().isEmpty())
            store.delete(first.fileName)
            store.delete(second.fileName)
        }
    }

    @Test
    fun phonePeStatementImportsAsUnclassifiedWithPaymentMethodFromInstrument() {
        val statement = "Transaction Statement for 9000000000\n" +
            "Duration,\"18 Oct, 2026 - 17 Nov, 2026\"\n\n" +
            "Date,Time,Transaction Details,Transaction ID,UTR,Transaction Type,Credit/debit instrument,Amount\n" +
            "\"Nov 16, 2026\",\"08:01 am\",\"Payment to Example Store\",\"T1\",\"1\",\"DEBIT\",\"Paid by XXXXXXXXXX1234\",\"689\"\n" +
            "\"Nov 15, 2026\",\"07:05 pm\",\"Paid to City hospital\",\"T2\",\"2\",\"DEBIT\",\"Paid by XX9999\",\"2245.76\"\n" +
            "\"Nov 15, 2026\",\"06:00 pm\",\"Received from A Friend\",\"T3\",\"3\",\"CREDIT\",\"Credited to XXXXXXXXXX1234\",\"500\"\n" +
            "\"Nov 14, 2026\",\"06:00 pm\",\"Paid to Cafe\",\"T4\",\"4\",\"DEBIT\",\"Paid by XXXXXXXXXX1234\",\"190\"\n\n" +
            "This is an automatically generated statement. Customer(s) are requested to immediately notify PhonePe in case\n"
        runBlocking {
            val repo = ExpenseRepository(AppDatabase.get(context))
            val outcome = repo.importCsv(statement)
            assertEquals(3, outcome.added)
            assertEquals(0, outcome.unreadable)

            val all = repo.observeAll().first()
            assertEquals(listOf(68900L, 224576L, 19000L), all.map { it.amountCents })
            assertEquals(listOf("2026-11-16", "2026-11-15", "2026-11-14").map(::day), all.map { it.epochDay })
            assertEquals(listOf("XXXXXXXXXX1234", "XX9999", "XXXXXXXXXX1234"), all.map { it.paymentMethodName })
            assertTrue(all.all { it.categoryId == null })
            assertEquals(2, repo.observePaymentMethods().first().single { it.name == "XXXXXXXXXX1234" }.expenseCount)

            // Classify one, then import the statement again: nothing is duplicated.
            val food = AppDatabase.get(context).categoryDao().getAll().single { it.name == "Food" }.id
            repo.saveExpense(all[2].id, all[2].amountCents, all[2].epochDay, food, all[2].details, all[2].paymentMethodId)
            assertEquals(0, repo.importCsv(statement).added)
            assertEquals(3, repo.observeAll().first().size)
        }
    }
}

/** Export and import through the same code the menu uses, with real files. */
@RunWith(AndroidJUnit4::class)
class ExportImportTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext
    private val file = File(context.cacheDir, "export-test.csv")

    @Before
    fun setUp() {
        AppDatabase.reset(context)
        file.delete()
    }

    @After
    fun tearDown() {
        AppDatabase.reset(context)
        file.delete()
    }

    @Test
    fun exportWritesAFileThatImportsBackIntoAnEmptyApp() {
        runBlocking {
            val repo = ExpenseRepository(AppDatabase.get(context))
            val food = AppDatabase.get(context).categoryDao().getAll().single { it.name == "Food" }.id
            repo.saveExpense(null, 25050, day("2026-11-16"), food, "Lunch, with \"quotes\" and ₹")
            repo.saveExpense(null, 4000, day("2026-11-15"), null, "")

            val viewModel = ExpenseViewModel(context.applicationContext as Application)
            viewModel.exportTo(Uri.fromFile(file))
            assertEquals("Saved 2 expenses to the file", withTimeout(15_000) { viewModel.messages.first() })

            val text = file.readText(Charsets.UTF_8)
            assertTrue("File should start with a byte-order mark", text.startsWith("﻿"))
            assertTrue(text.contains("Date,Classification,Amount,Details"))
            assertTrue(text.contains("2026-11-16,Food,250.50,"))

            // Importing into the same data adds nothing ...
            viewModel.importFrom(Uri.fromFile(file))
            assertEquals(
                "Nothing new: all 2 expenses are already in the app",
                withTimeout(15_000) { viewModel.messages.first() },
            )
        }

        // ... and importing into a fresh install restores everything.
        AppDatabase.reset(context)
        runBlocking {
            val viewModel = ExpenseViewModel(context.applicationContext as Application)
            viewModel.importFrom(Uri.fromFile(file))
            assertEquals("Imported 2 expenses", withTimeout(15_000) { viewModel.messages.first() })

            val restored = ExpenseRepository(AppDatabase.get(context)).observeAll().first()
            assertEquals(listOf(25050L, 4000L), restored.map { it.amountCents })
            assertEquals(listOf("Food", null), restored.map { it.categoryName })
            assertEquals("Lunch, with \"quotes\" and ₹", restored[0].details)
        }
    }
}

/** Number formatting differs between Android and a desktop JVM, so this part is checked on the device. */
@RunWith(AndroidJUnit4::class)
class FormattingOnDeviceTest {

    @Test
    fun rupeesUseLakhGrouping() {
        val us = Locale.Builder().setLanguage("en").setRegion("US").build()
        val india = Locale.Builder().setLanguage("en").setRegion("IN").build()
        assertEquals("₹1,23,456.50", MoneyFormatter("INR", us).format(12345650))
        assertEquals("₹1,23,456.50", MoneyFormatter(null, india).format(12345650))
        assertEquals("₹", MoneyFormatter(null, india).symbol)
        assertEquals("$1,234.50", MoneyFormatter(null, us).format(123450))
    }
}
