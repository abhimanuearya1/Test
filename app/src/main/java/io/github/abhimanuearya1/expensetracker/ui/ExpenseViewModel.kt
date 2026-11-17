package io.github.abhimanuearya1.expensetracker.ui

import android.app.Application
import android.database.sqlite.SQLiteConstraintException
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.abhimanuearya1.expensetracker.core.CategoryTotal
import io.github.abhimanuearya1.expensetracker.core.CsvBackup
import io.github.abhimanuearya1.expensetracker.core.Dates
import io.github.abhimanuearya1.expensetracker.core.DayGroup
import io.github.abhimanuearya1.expensetracker.core.ExpenseItem
import io.github.abhimanuearya1.expensetracker.core.MoneyFormatter
import io.github.abhimanuearya1.expensetracker.core.SearchIndex
import io.github.abhimanuearya1.expensetracker.core.Summary
import io.github.abhimanuearya1.expensetracker.core.TextTools
import io.github.abhimanuearya1.expensetracker.data.AppDatabase
import io.github.abhimanuearya1.expensetracker.data.CategoryWithCount
import io.github.abhimanuearya1.expensetracker.data.ExpenseRepository
import io.github.abhimanuearya1.expensetracker.data.Settings
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.time.YearMonth

/** What the Expenses tab shows. */
data class ExpensesUiState(
    /** True until the first database result arrives, so "no expenses" is not flashed. */
    val loading: Boolean = true,
    val month: YearMonth,
    val searching: Boolean = false,
    /** Classification filter: null = all, [ExpenseViewModel.FILTER_UNCATEGORIZED], or a classification id. */
    val filter: Long? = null,
    val days: List<DayGroup> = emptyList(),
    val totalCents: Long = 0,
    val count: Int = 0,
    /** Whether the list (before filtering) has expenses without a classification. */
    val hasUncategorized: Boolean = false,
)

/** What the Summary tab shows. */
data class SummaryUiState(
    val loading: Boolean = true,
    val month: YearMonth,
    val totalCents: Long = 0,
    val count: Int = 0,
    val rows: List<CategoryTotal> = emptyList(),
)

/** The expense form is open: either for a new expense or to change an existing one. */
sealed interface Editing {
    data object New : Editing
    data class Existing(val item: ExpenseItem) : Editing
}

@OptIn(ExperimentalCoroutinesApi::class)
class ExpenseViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = ExpenseRepository(AppDatabase.get(application))
    private val settings = Settings(application)

    // ---- Currency -----------------------------------------------------------------

    private val _currencyCode = MutableStateFlow(settings.currencyCode)

    /** ISO code picked by the user, or null to follow the phone's region. */
    val currencyCode: StateFlow<String?> = _currencyCode.asStateFlow()

    fun setCurrency(code: String?) {
        settings.currencyCode = code
        _currencyCode.value = code
    }

    // ---- Classifications ----------------------------------------------------------

    /** All classifications with their expense counts; null while loading. */
    val categories: StateFlow<List<CategoryWithCount>?> = repo.observeCategories()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addCategory(name: String, color: Int, onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                onAdded(repo.addCategory(name, color))
            } catch (e: SQLiteConstraintException) {
                _messages.send("That classification already exists")
            }
        }
    }

    fun updateCategory(id: Long, name: String, color: Int) {
        viewModelScope.launch {
            try {
                repo.updateCategory(id, name, color)
            } catch (e: SQLiteConstraintException) {
                _messages.send("That classification already exists")
            }
        }
    }

    fun deleteCategory(category: CategoryWithCount) {
        viewModelScope.launch {
            repo.deleteCategory(category.id)
            if (_filter.value == category.id) _filter.value = null
            _messages.send("Deleted “${category.name}”")
        }
    }

    // ---- Month, search and filter -------------------------------------------------

    private val _month = MutableStateFlow(YearMonth.now())

    fun previousMonth() {
        _month.value = _month.value.minusMonths(1)
    }

    fun nextMonth() {
        _month.value = _month.value.plusMonths(1)
    }

    fun goToCurrentMonth() {
        _month.value = YearMonth.now()
    }

    /** True while the search box is open; the list then covers all months. */
    var searching by mutableStateOf(false)
        private set

    /** Kept as Compose state so typing is never delayed. */
    var query by mutableStateOf("")
        private set

    /**
     * Changes whenever the list should jump back to its top: after adding an
     * expense, and when search is opened or closed.
     */
    var listResetKey by mutableIntStateOf(0)
        private set

    fun openSearch() {
        if (!searching) listResetKey++
        searching = true
    }

    fun closeSearch() {
        if (searching) listResetKey++
        searching = false
        query = ""
    }

    fun onQueryChange(text: String) {
        query = text
    }

    private val _filter = MutableStateFlow<Long?>(null)

    fun setFilter(categoryId: Long?) {
        _filter.value = categoryId
    }

    /** From the Summary tab: show the month's expenses of one classification. */
    fun showCategory(categoryId: Long?) {
        closeSearch()
        _filter.value = categoryId ?: FILTER_UNCATEGORIZED
        listResetKey++
    }

    // ---- Lists --------------------------------------------------------------------

    private class MonthData(val month: YearMonth, val items: List<ExpenseItem>)

    /** The selected month's expenses, loaded with one indexed query. Null while loading. */
    private val monthData: StateFlow<MonthData?> = _month
        .flatMapLatest { month -> repo.observeMonth(month).map { MonthData(month, it) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** All expenses prepared for searching. Only loaded while the search box is open. */
    private val searchIndex: StateFlow<SearchIndex?> = snapshotFlow { searching }
        .flatMapLatest { open -> if (open) buildSearchIndex() else flowOf(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private fun buildSearchIndex(): Flow<SearchIndex?> =
        combine(repo.observeAll(), _currencyCode) { items, code ->
            SearchIndex(items, MoneyFormatter(code)::format)
        }.flowOn(Dispatchers.Default)

    val expensesUi: StateFlow<ExpensesUiState> = combine(
        monthData,
        snapshotFlow { searching },
        snapshotFlow { query },
        _filter,
        searchIndex,
    ) { data, searching, query, filter, index ->
        val source: List<ExpenseItem>? = if (searching) index?.search(query) else data?.items
        val shown = source?.filter { matchesFilter(it, filter) }
        ExpensesUiState(
            loading = shown == null,
            month = data?.month ?: _month.value,
            searching = searching,
            filter = filter,
            days = Summary.groupByDay(shown.orEmpty()),
            totalCents = shown.orEmpty().sumOf { it.amountCents },
            count = shown?.size ?: 0,
            hasUncategorized = source.orEmpty().any { it.categoryId == null },
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ExpensesUiState(month = _month.value))

    val summaryUi: StateFlow<SummaryUiState> = monthData
        .map { data ->
            if (data == null) {
                SummaryUiState(month = _month.value)
            } else {
                SummaryUiState(
                    loading = false,
                    month = data.month,
                    totalCents = data.items.sumOf { it.amountCents },
                    count = data.items.size,
                    rows = Summary.totalsByCategory(data.items),
                )
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState(month = _month.value))

    private fun matchesFilter(item: ExpenseItem, filter: Long?): Boolean = when (filter) {
        null -> true
        FILTER_UNCATEGORIZED -> item.categoryId == null
        else -> item.categoryId == filter
    }

    // ---- Adding, changing and deleting expenses -----------------------------------

    /** Non-null while the expense form is open. Survives screen rotation. */
    var editing by mutableStateOf<Editing?>(null)
        private set

    fun startNewExpense() {
        editing = Editing.New
    }

    fun startEditing(item: ExpenseItem) {
        editing = Editing.Existing(item)
    }

    fun closeEditor() {
        editing = null
    }

    /** Adds a new expense when [id] is null, otherwise updates that expense. */
    fun saveExpense(id: Long?, amountCents: Long, epochDay: Long, categoryId: Long?, details: String) {
        if (id == null) listResetKey++
        editing = null
        viewModelScope.launch {
            try {
                repo.saveExpense(id, amountCents, epochDay, categoryId, details)
                // Leave the list showing the expense that was just saved.
                if (id == null) closeSearch()
                if (!searching) _month.value = Dates.monthOf(epochDay)
                val filter = _filter.value
                if (filter != null && filter != (categoryId ?: FILTER_UNCATEGORIZED)) _filter.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _messages.send("Could not save the expense")
            }
        }
    }

    fun deleteExpense(id: Long) {
        editing = null
        viewModelScope.launch {
            repo.deleteExpense(id)
            _messages.send("Expense deleted")
        }
    }

    // ---- Export / import ----------------------------------------------------------

    private val _messages = Channel<String>(Channel.BUFFERED)

    /** Short confirmations and errors to show at the bottom of the screen. */
    val messages: Flow<String> = _messages.receiveAsFlow()

    fun showMessage(text: String) {
        _messages.trySend(text)
    }

    /** Writes every expense to the file the user picked, as CSV that Excel can open. */
    fun exportTo(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                val count = withContext(Dispatchers.IO) {
                    val rows = repo.exportRows()
                    // The byte-order mark makes Excel read the file as UTF-8 (₹, non-English text).
                    val bytes = ("﻿" + CsvBackup.export(rows)).toByteArray(Charsets.UTF_8)
                    openForWriting(uri).use { it.write(bytes) }
                    rows.size
                }
                "Saved ${TextTools.plural(count, "expense")} to the file"
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Could not save the file"
            }
            _messages.send(message)
        }
    }

    /** Adds the expenses from a CSV file that are not already in the app. */
    fun importFrom(uri: Uri) {
        viewModelScope.launch {
            val message = try {
                withContext(Dispatchers.IO) {
                    val bytes = readLimited(uri)
                    if (bytes == null) {
                        "That file is too large to be an expenses file"
                    } else {
                        repo.importCsv(TextTools.decode(bytes)).message()
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "Could not read the file"
            }
            _messages.send(message)
        }
    }

    private fun openForWriting(uri: Uri): OutputStream {
        val resolver = getApplication<Application>().contentResolver
        // "wt" replaces any existing content; a few file providers only accept plain "w".
        val stream = try {
            resolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            null
        }
        return stream ?: resolver.openOutputStream(uri) ?: throw IOException("Cannot open the file")
    }

    /** Reads the whole file, or returns null if it is larger than [MAX_IMPORT_BYTES]. */
    private fun readLimited(uri: Uri): ByteArray? {
        val input = getApplication<Application>().contentResolver.openInputStream(uri)
            ?: throw IOException("Cannot open the file")
        return input.use { stream ->
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(64 * 1024)
            var tooLarge = false
            while (true) {
                val read = stream.read(chunk)
                if (read < 0) break
                if (buffer.size() + read > MAX_IMPORT_BYTES) {
                    tooLarge = true
                    break
                }
                buffer.write(chunk, 0, read)
            }
            if (tooLarge) null else buffer.toByteArray()
        }
    }

    companion object {
        /** Filter value meaning "expenses without a classification". Real ids start at 1. */
        const val FILTER_UNCATEGORIZED = 0L

        /** Far more than any expenses file needs; stops a mistakenly picked video from being read. */
        private const val MAX_IMPORT_BYTES = 5 * 1024 * 1024
    }
}
