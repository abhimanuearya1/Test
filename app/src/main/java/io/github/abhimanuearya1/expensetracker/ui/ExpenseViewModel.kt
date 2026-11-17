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
import io.github.abhimanuearya1.expensetracker.data.AttachmentDraft
import io.github.abhimanuearya1.expensetracker.data.AttachmentStore
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

/** What the Summary tab shows: either one month or a custom range of dates. */
data class SummaryUiState(
    val loading: Boolean = true,
    val month: YearMonth,
    /** True when [fromDay]..[toDay] was chosen by the user instead of a month. */
    val customRange: Boolean = false,
    val fromDay: Long = 0,
    val toDay: Long = 0,
    val totalCents: Long = 0,
    val count: Int = 0,
    /** Totals per classification. */
    val rows: List<CategoryTotal> = emptyList(),
    /** Totals per payment method. */
    val paymentRows: List<CategoryTotal> = emptyList(),
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
    private val attachmentStore = AttachmentStore(application)

    init {
        // Tidy up copies of files that were added to a form but never saved.
        viewModelScope.launch(Dispatchers.IO) {
            try {
                attachmentStore.deleteUnreferenced(repo.allAttachmentFileNames())
            } catch (e: Exception) {
                // Only housekeeping; try again next start.
            }
        }
    }

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

    // ---- Payment methods ----------------------------------------------------------

    /** All payment methods with their expense counts; null while loading. */
    val paymentMethods: StateFlow<List<CategoryWithCount>?> = repo.observePaymentMethods()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun addPaymentMethod(name: String, color: Int, onAdded: (Long) -> Unit = {}) {
        viewModelScope.launch {
            try {
                onAdded(repo.addPaymentMethod(name, color))
            } catch (e: SQLiteConstraintException) {
                _messages.send("That payment method already exists")
            }
        }
    }

    fun updatePaymentMethod(id: Long, name: String, color: Int) {
        viewModelScope.launch {
            try {
                repo.updatePaymentMethod(id, name, color)
            } catch (e: SQLiteConstraintException) {
                _messages.send("That payment method already exists")
            }
        }
    }

    fun deletePaymentMethod(method: CategoryWithCount) {
        viewModelScope.launch {
            repo.deletePaymentMethod(method.id)
            _messages.send("Deleted “${method.name}”")
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

    /** Custom date range for the Summary tab as first day to last day, or null to follow the month. */
    private val _summaryRange = MutableStateFlow<Pair<Long, Long>?>(null)

    /** Switches the Summary between the selected month and a custom range (this month so far, to begin with). */
    fun setSummaryCustomRange(enabled: Boolean) {
        if (!enabled) {
            _summaryRange.value = null
        } else if (_summaryRange.value == null) {
            _summaryRange.value = Dates.firstDay(_month.value) to
                minOf(Dates.lastDay(_month.value), maxOf(Dates.today(), Dates.firstDay(_month.value)))
        }
    }

    fun setSummaryFrom(day: Long) {
        val current = _summaryRange.value ?: return
        _summaryRange.value = day to maxOf(day, current.second)
    }

    fun setSummaryTo(day: Long) {
        val current = _summaryRange.value ?: return
        _summaryRange.value = minOf(day, current.first) to day
    }

    val summaryUi: StateFlow<SummaryUiState> = _summaryRange
        .flatMapLatest { range ->
            if (range == null) {
                monthData.map { data ->
                    if (data == null) {
                        SummaryUiState(month = _month.value)
                    } else {
                        summaryOf(data.items).copy(
                            month = data.month,
                            fromDay = Dates.firstDay(data.month),
                            toDay = Dates.lastDay(data.month),
                        )
                    }
                }
            } else {
                repo.observeBetween(range.first, range.second).map { items ->
                    summaryOf(items).copy(customRange = true, fromDay = range.first, toDay = range.second)
                }
            }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SummaryUiState(month = _month.value))

    private fun summaryOf(items: List<ExpenseItem>) = SummaryUiState(
        loading = false,
        month = _month.value,
        totalCents = items.sumOf { it.amountCents },
        count = items.size,
        rows = Summary.totalsByCategory(items),
        paymentRows = Summary.totalsByPaymentMethod(items),
    )

    private fun matchesFilter(item: ExpenseItem, filter: Long?): Boolean = when (filter) {
        null -> true
        FILTER_UNCATEGORIZED -> item.categoryId == null
        else -> item.categoryId == filter
    }

    // ---- Adding, changing and deleting expenses -----------------------------------

    /** Non-null while the expense form is open. Survives screen rotation. */
    var editing by mutableStateOf<Editing?>(null)
        private set

    /** Supporting documents of the expense in the form, including ones just added. */
    var editorAttachments by mutableStateOf<List<AttachmentDraft>>(emptyList())
        private set

    /** True once a document was added or removed in the form. */
    var attachmentsChanged by mutableStateOf(false)
        private set

    private var pendingPhoto: String? = null

    fun startNewExpense() {
        editorAttachments = emptyList()
        attachmentsChanged = false
        editing = Editing.New
    }

    fun startEditing(item: ExpenseItem) {
        editorAttachments = emptyList()
        attachmentsChanged = false
        editing = Editing.Existing(item)
        if (item.attachmentCount > 0) {
            viewModelScope.launch {
                val saved = repo.attachmentsOf(item.id)
                val current = editing
                if (current is Editing.Existing && current.item.id == item.id) {
                    editorAttachments = saved + editorAttachments.filter { it.id == null }
                }
            }
        }
    }

    /** Closes the form without saving; files added in it are thrown away. */
    fun closeEditor() {
        val unsaved = editorAttachments.filter { it.id == null }.map { it.fileName }
        editing = null
        editorAttachments = emptyList()
        deleteFiles(unsaved)
    }

    private fun deleteFiles(fileNames: List<String>) {
        if (fileNames.isEmpty()) return
        viewModelScope.launch(Dispatchers.IO) { fileNames.forEach(attachmentStore::delete) }
    }

    /** Copies the chosen files into the app and adds them to the form. */
    fun addAttachments(uris: List<Uri>) {
        if (uris.isEmpty()) return
        viewModelScope.launch {
            var failed = 0
            for (uri in uris) {
                try {
                    val draft = withContext(Dispatchers.IO) { attachmentStore.copyIn(uri) }
                    if (editing != null) {
                        editorAttachments = editorAttachments + draft
                        attachmentsChanged = true
                    } else {
                        deleteFiles(listOf(draft.fileName))
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    failed++
                }
            }
            if (failed > 0) _messages.send("Could not attach ${TextTools.plural(failed, "file")}")
        }
    }

    fun removeAttachment(draft: AttachmentDraft) {
        editorAttachments = editorAttachments - draft
        attachmentsChanged = true
        if (draft.id == null) deleteFiles(listOf(draft.fileName))
    }

    /** Where the camera should save a new photo. Call [finishPhoto] when the camera returns. */
    fun beginPhoto(): Uri {
        val fileName = attachmentStore.newPhotoFileName()
        pendingPhoto = fileName
        return attachmentStore.uriFor(fileName)
    }

    fun finishPhoto(saved: Boolean) {
        val fileName = pendingPhoto ?: return
        pendingPhoto = null
        if (saved && editing != null && attachmentStore.exists(fileName)) {
            val number = editorAttachments.count { it.displayName.startsWith("Photo") } + 1
            editorAttachments = editorAttachments + AttachmentDraft(null, fileName, "Photo $number.jpg", "image/jpeg")
            attachmentsChanged = true
        } else {
            deleteFiles(listOf(fileName))
        }
    }

    /** Address of an attached file that a viewer app can be allowed to read. */
    fun attachmentUri(draft: AttachmentDraft): Uri = attachmentStore.uriFor(draft.fileName)

    /** Adds a new expense when [id] is null, otherwise updates that expense. */
    fun saveExpense(
        id: Long?,
        amountCents: Long,
        epochDay: Long,
        categoryId: Long?,
        details: String,
        paymentMethodId: Long?,
    ) {
        if (id == null) listResetKey++
        val attachments = editorAttachments
        editing = null
        editorAttachments = emptyList()
        viewModelScope.launch {
            try {
                val removed = repo.saveExpense(id, amountCents, epochDay, categoryId, details, paymentMethodId, attachments)
                deleteFiles(removed)
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
        val unsaved = editorAttachments.filter { it.id == null }.map { it.fileName }
        editing = null
        editorAttachments = emptyList()
        viewModelScope.launch {
            deleteFiles(repo.deleteExpense(id) + unsaved)
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
