package io.github.abhimanuearya1.expensetracker

import android.content.Context
import android.os.ParcelFileDescriptor
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.abhimanuearya1.expensetracker.core.MoneyFormatter
import io.github.abhimanuearya1.expensetracker.data.AppDatabase
import io.github.abhimanuearya1.expensetracker.data.ExpenseRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.ExternalResource
import org.junit.runner.RunWith
import java.time.LocalDate
import java.time.YearMonth

/**
 * Drives the real app the way a person would. Screenshots taken along the way
 * are saved on the device under Download/expense-tracker-screenshots.
 */
@RunWith(AndroidJUnit4::class)
class AppFlowTest {

    private val context: Context = InstrumentationRegistry.getInstrumentation().targetContext

    /** Every test starts from a freshly installed state. Runs before the app is launched. */
    @get:Rule(order = 0)
    val freshData = object : ExternalResource() {
        override fun before() {
            AppDatabase.reset(context)
            context.getSharedPreferences("settings", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }

    @get:Rule(order = 1)
    val compose = createAndroidComposeRule<MainActivity>()

    private val money by lazy { MoneyFormatter(null, context.resources.configuration.locales[0]) }
    private val today: LocalDate = LocalDate.now()

    // ---- helpers ----------------------------------------------------------------

    private fun waitForText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForNoText(text: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithText(text).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
    }

    private fun waitForNoTag(tag: String) {
        compose.waitUntil(15_000) { compose.onAllNodesWithTag(tag).fetchSemanticsNodes().isEmpty() }
    }

    private fun waitForTotal(cents: Long) {
        val expected = money.format(cents)
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("monthTotal") and hasText(expected)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun shell(command: String) {
        val output = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(command)
        ParcelFileDescriptor.AutoCloseInputStream(output).use { it.readBytes() }
    }

    private fun screenshot(name: String) {
        compose.waitForIdle()
        Thread.sleep(700)
        shell("mkdir -p $SCREENSHOT_DIR")
        shell("screencap -p $SCREENSHOT_DIR/$name.png")
    }

    private fun categoryIds(): Map<String, Long> = runBlocking {
        AppDatabase.get(context).categoryDao().getAll().associate { it.name to it.id }
    }

    private fun paymentMethodNames(): Set<String> = runBlocking {
        AppDatabase.get(context).paymentMethodDao().getAll().map { it.name }.toSet()
    }

    private fun seed(vararg expenses: Triple<Long, String?, String>, daysAgo: (Int) -> Long = { 0 }) {
        val ids = categoryIds()
        val repo = ExpenseRepository(AppDatabase.get(context))
        runBlocking {
            expenses.forEachIndexed { index, (cents, category, details) ->
                repo.saveExpense(null, cents, today.minusDays(daysAgo(index)).toEpochDay(), category?.let { ids.getValue(it) }, details)
            }
        }
    }

    /** Scrolls the classification list until the icon with this description is on screen, then taps it. */
    private fun tapInClassificationList(description: String) {
        compose.onNodeWithTag("classificationList").performScrollToNode(hasContentDescription(description))
        compose.onNodeWithContentDescription(description).performClick()
    }

    private fun addExpense(
        amount: String,
        classification: String,
        details: String,
        yesterday: Boolean = false,
        paymentMethod: String? = null,
    ) {
        compose.onNodeWithTag("addExpense").performClick()
        waitForTag("amount")
        compose.onNodeWithTag("amount").performTextInput(amount)
        compose.onNodeWithTag("details").performScrollTo().performTextInput(details)
        // "Done" on the amount keyboard closes the keyboard, as it does for a person.
        compose.onNodeWithTag("amount").performScrollTo().performImeAction()
        if (yesterday) compose.onNodeWithText("Yesterday").performScrollTo().performClick()
        // Classification and payment method are each chosen from a list.
        compose.onNodeWithTag("pickClassification").performScrollTo().performClick()
        waitForTag("option-$classification")
        compose.onNodeWithTag("option-$classification").performScrollTo().performClick()
        waitForNoTag("option-new")
        if (paymentMethod != null) {
            compose.onNodeWithTag("pickPaymentMethod").performScrollTo().performClick()
            waitForTag("option-$paymentMethod")
            compose.onNodeWithTag("option-$paymentMethod").performScrollTo().performClick()
            waitForNoTag("option-new")
        }
    }

    // ---- tests ------------------------------------------------------------------

    @Test
    fun addSearchEditAndDeleteAnExpense() {
        waitForText("Tap “Add expense” to record one.")
        screenshot("01-empty")

        // Saving without an amount or classification explains what is missing.
        compose.onNodeWithTag("addExpense").performClick()
        waitForTag("saveExpense")
        compose.onNodeWithTag("saveExpense").performClick()
        waitForText("Enter the amount")
        waitForText("Choose a classification")
        compose.onNodeWithContentDescription("Close").performClick()
        waitForTag("addExpense")

        // Add: amount, classification from the list, details. The date defaults to today.
        addExpense("250", "Food", "Lunch at cafe", paymentMethod = "UPI")
        compose.onNodeWithTag("amount").performScrollTo()
        screenshot("02-add-expense")
        compose.onNodeWithTag("attachFile").performScrollTo()
        screenshot("02b-add-expense-lower")
        compose.onNodeWithTag("saveExpense").performClick()
        waitForText("Lunch at cafe")
        waitForTotal(25000)
        compose.onNode(hasTestTag("expenseRow") and hasText("Lunch at cafe") and hasText("UPI")).assertExists()

        // A second one, dated yesterday.
        addExpense("1200.5", "Transport", "Cab to airport", yesterday = true)
        compose.onNodeWithTag("saveExpense").performClick()
        waitForText("Cab to airport")

        // Search covers all months and matches details, classification and amount.
        compose.onNodeWithContentDescription("Search").performClick()
        waitForTag("search")
        waitForText("Lunch at cafe")
        waitForText("Cab to airport")
        compose.onNodeWithTag("search").performTextInput("lunch")
        waitForNoText("Cab to airport")
        compose.onNodeWithTag("searchSummary").assertTextEquals("1 expense · " + money.format(25000))
        screenshot("05-search")
        compose.onNodeWithTag("search").performTextReplacement("transport 1200")
        waitForText("Cab to airport")
        waitForNoText("Lunch at cafe")
        compose.onNodeWithTag("search").performTextReplacement("pizza")
        waitForText("Nothing found")
        compose.onNodeWithContentDescription("Close search").performClick()

        // Edit the first expense: tap it, change the amount, save.
        waitForTag("monthLabel")
        compose.onNodeWithTag("monthLabel").performClick()
        waitForText("Lunch at cafe")
        compose.onNodeWithText("Lunch at cafe").performClick()
        waitForText("Edit expense")
        compose.onNodeWithTag("amount").performTextReplacement("300")
        compose.onNodeWithTag("saveExpense").performClick()
        val yesterdayInSameMonth = YearMonth.from(today) == YearMonth.from(today.minusDays(1))
        waitForTotal(30000 + if (yesterdayInSameMonth) 120050L else 0L)

        // Delete it from the edit screen, after confirming.
        compose.onNodeWithText("Lunch at cafe").performClick()
        waitForText("Edit expense")
        compose.onNodeWithContentDescription("Delete expense").performClick()
        waitForText("Delete this expense?")
        compose.onNodeWithText("Delete").performClick()
        waitForNoText("Lunch at cafe")
        waitForTotal(if (yesterdayInSameMonth) 120050L else 0L)
    }

    @Test
    fun manageClassifications() {
        waitForTag("tab-Lists")
        compose.onNodeWithTag("tab-Lists").performClick()
        waitForText("Food")

        // Add a new one.
        compose.onNodeWithTag("addClassification").performClick()
        waitForTag("classificationName")
        compose.onNodeWithTag("classificationName").performTextInput("Pets")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { "Pets" in categoryIds().keys }

        // A name that already exists (in any letter case) is refused.
        compose.onNodeWithTag("addClassification").performClick()
        waitForTag("classificationName")
        compose.onNodeWithTag("classificationName").performTextInput("food")
        compose.onNodeWithText("Save").performClick()
        waitForText("“food” already exists")
        compose.onNodeWithText("Cancel").performClick()

        // Rename and recolour.
        tapInClassificationList("Edit Pets")
        waitForText("Edit classification")
        compose.onNodeWithTag("classificationName").assertTextContains("Pets")
        compose.onNodeWithTag("classificationName").performTextReplacement("Pet care")
        compose.onNodeWithContentDescription("Teal").performClick()
        screenshot("08-edit-classification")
        compose.onNodeWithText("Save").performClick()
        waitForText("Pet care")
        assertTrue("Pet care" in categoryIds().keys)
        assertFalse("Pets" in categoryIds().keys)
        compose.onNodeWithTag("classificationList").performScrollToNode(hasTestTag("addClassification"))
        screenshot("07-classifications")

        // Delete an unused one.
        tapInClassificationList("Delete Pet care")
        waitForText("Delete “Pet care”?")
        waitForText("No expenses use this classification.")
        compose.onNodeWithText("Delete").performClick()
        waitForNoText("Delete “Pet care”?")
        compose.waitUntil(15_000) { "Pet care" !in categoryIds().keys }

        // Deleting one that is in use keeps the expense, now shown as Uncategorized.
        seed(Triple(5000L, "Food", "Breakfast"))
        compose.onNodeWithTag("classificationList").performScrollToNode(hasContentDescription("Delete Food"))
        waitForText("1 expense")
        tapInClassificationList("Delete Food")
        waitForText("Delete “Food”?")
        waitForText("1 expense uses this classification. It will be kept, shown as “Uncategorized”.")
        compose.onNodeWithText("Delete").performClick()
        waitForNoText("Delete “Food”?")
        compose.waitUntil(15_000) { "Food" !in categoryIds().keys }
        compose.onNodeWithTag("tab-Expenses").performClick()
        waitForText("Breakfast")
        compose.onNode(hasTestTag("expenseRow") and hasText("Uncategorized")).assertExists()

        // Payment methods are a second list managed the same way.
        compose.onNodeWithTag("tab-Lists").performClick()
        waitForTag("toggle-Payment methods")
        compose.onNodeWithTag("toggle-Payment methods").performClick()
        waitForText("UPI")
        compose.onNodeWithTag("addClassification").performClick()
        waitForTag("classificationName")
        compose.onNodeWithTag("classificationName").performTextInput("Wallet")
        compose.onNodeWithText("Save").performClick()
        compose.waitUntil(15_000) { "Wallet" in paymentMethodNames() }
        compose.onNodeWithTag("classificationList").performScrollToNode(hasTestTag("addClassification"))
        screenshot("12-payment-methods")
        tapInClassificationList("Delete Wallet")
        waitForText("No expenses use this payment method.")
        compose.onNodeWithText("Delete").performClick()
        compose.waitUntil(15_000) { "Wallet" !in paymentMethodNames() }
    }

    @Test
    fun summaryAndClassificationFilter() {
        seed(
            Triple(25000L, "Food", "Lunch"),
            Triple(10000L, "Food", "Snacks"),
            Triple(40000L, "Transport", "Taxi"),
        )
        waitForTotal(75000)

        compose.onNodeWithTag("tab-Summary").performClick()
        waitForText(money.format(35000))
        compose.onNode(hasTestTag("summaryRow") and hasText("Transport") and hasText(money.format(40000))).assertExists()
        compose.onNode(hasTestTag("summaryRow") and hasText("Food") and hasText("47% · 2 expenses")).assertExists()

        // The same totals as percentages, by payment method, and for a custom range of dates.
        compose.onNodeWithTag("toggle-Percentage").performClick()
        compose.onNode(hasTestTag("summaryRow") and hasText("Food") and hasText("47%")).assertExists()
        compose.onNode(hasTestTag("summaryRow") and hasText("Food") and hasText(money.format(35000) + " · 2 expenses")).assertExists()
        compose.onNodeWithTag("toggle-By payment method").performClick()
        compose.waitUntil(15_000) {
            compose.onAllNodes(hasTestTag("summaryRow") and hasText("Not set") and hasText("100%")).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithTag("toggle-Custom range").performClick()
        waitForTag("rangeFrom")
        waitForTotal(75000)
        compose.onNodeWithTag("toggle-Month").performClick()
        waitForTag("monthLabel")
        compose.onNodeWithTag("toggle-By classification").performClick()
        compose.onNodeWithTag("toggle-Amount").performClick()
        waitForText("47% · 2 expenses")

        // Tapping a classification shows just its expenses.
        compose.onNode(hasTestTag("summaryRow") and hasText("Transport")).performClick()
        waitForTotal(40000)
        waitForNoText("Lunch")
        // The chosen filter is scrolled into view; tapping it again clears it.
        waitForTag("filter-Transport")
        compose.onNodeWithTag("filter-Transport").assertIsSelected()
        compose.onNodeWithTag("filter-Transport").performClick()
        waitForTotal(75000)
    }

    @Test
    fun screenshotsWithSampleData() {
        seed(
            Triple(32000L, "Food", "Lunch with team"),
            Triple(4500L, "Transport", "Metro card top-up"),
            Triple(184950L, "Groceries", "Monthly groceries"),
            Triple(89900L, "Bills", "Electricity bill"),
            Triple(1500000L, "Rent", "November rent"),
            Triple(64900L, "Entertainment", ""),
            Triple(120000L, "Health", "Dentist"),
            Triple(249900L, "Shopping", "Winter jacket"),
            daysAgo = { index -> (index / 3).toLong() },
        )
        waitForText("Lunch with team")
        screenshot("04-expenses")

        compose.onNodeWithTag("tab-Summary").performClick()
        waitForTag("toggle-By classification")
        waitForTag("summaryRow")
        screenshot("06-summary")
        compose.onNodeWithTag("toggle-Custom range").performClick()
        waitForTag("rangeFrom")
        compose.onNodeWithTag("toggle-Percentage").performClick()
        screenshot("11-summary-range-percent")
        compose.onNodeWithTag("toggle-Month").performClick()

        compose.onNodeWithTag("tab-Expenses").performClick()
        waitForTag("addExpense")
        compose.onNodeWithTag("addExpense").performClick()
        waitForTag("dateButton")
        compose.onNodeWithTag("amount").performImeAction()
        compose.onNodeWithTag("dateButton").performClick()
        waitForText("OK")
        screenshot("03-date-picker")
        compose.onNodeWithText("Cancel").performClick()
        compose.onNodeWithContentDescription("Close").performClick()
        waitForText("Lunch with team")

        // The same list in the dark theme.
        try {
            shell("cmd uimode night yes")
            Thread.sleep(2500)
            waitForText("Lunch with team")
            screenshot("09-dark")
        } finally {
            shell("cmd uimode night no")
            Thread.sleep(1500)
        }
    }

    private companion object {
        const val SCREENSHOT_DIR = "/sdcard/Download/expense-tracker-screenshots"
    }
}
