package io.github.abhimanuearya1.expensetracker

import android.app.Application
import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.MoneyFormatter
import io.github.abhimanuearya1.expensetracker.data.AppDatabase
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
