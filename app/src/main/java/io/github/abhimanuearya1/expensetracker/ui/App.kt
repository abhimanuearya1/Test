@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.abhimanuearya1.expensetracker.ui

import android.content.ActivityNotFoundException
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.MoneyFormatter
import io.github.abhimanuearya1.expensetracker.core.TextTools
import io.github.abhimanuearya1.expensetracker.data.CategoryWithCount
import java.time.LocalDate

/** The three sections reachable from the bar at the bottom. */
enum class MainTab(val title: String) {
    Expenses("Expenses"),
    Summary("Summary"),
    Classifications("Classifications"),
}

private sealed interface CategoryDialogTarget {
    data object New : CategoryDialogTarget
    data class Edit(val category: CategoryWithCount) : CategoryDialogTarget
}

/** Root of the user interface: either the expense form or the three main tabs. */
@Composable
fun ExpenseTrackerApp(viewModel: ExpenseViewModel = viewModel()) {
    val currencyCode by viewModel.currencyCode.collectAsStateWithLifecycle()
    val categories by viewModel.categories.collectAsStateWithLifecycle()
    val locale = LocalConfiguration.current.locales[0]
    val money = remember(currencyCode, locale) { MoneyFormatter(currencyCode, locale) }

    // Kept here, above both screens, so they survive while the expense form is open.
    var tab by rememberSaveable { mutableStateOf(MainTab.Expenses) }
    val expensesListState = rememberSaveable(viewModel.listResetKey, saver = LazyListState.Saver) { LazyListState() }

    CompositionLocalProvider(LocalMoney provides money) {
        val editing = viewModel.editing
        if (editing != null) {
            EditExpenseScreen(
                editing = editing,
                categories = categories.orEmpty(),
                onSave = viewModel::saveExpense,
                onDelete = viewModel::deleteExpense,
                onClose = viewModel::closeEditor,
                onAddCategory = viewModel::addCategory,
            )
        } else {
            MainScreen(
                viewModel = viewModel,
                categories = categories,
                currencyCode = currencyCode,
                tab = tab,
                onTabChange = { tab = it },
                expensesListState = expensesListState,
            )
        }
    }
}

@Composable
private fun MainScreen(
    viewModel: ExpenseViewModel,
    categories: List<CategoryWithCount>?,
    currencyCode: String?,
    tab: MainTab,
    onTabChange: (MainTab) -> Unit,
    expensesListState: LazyListState,
) {
    val expensesUi by viewModel.expensesUi.collectAsStateWithLifecycle()
    val summaryUi by viewModel.summaryUi.collectAsStateWithLifecycle()
    val searching = viewModel.searching && tab == MainTab.Expenses

    val snackbarHostState = remember { SnackbarHostState() }
    LaunchedEffect(Unit) {
        viewModel.messages.collect { snackbarHostState.showSnackbar(it) }
    }

    var menuOpen by remember { mutableStateOf(false) }
    var showCurrency by rememberSaveable { mutableStateOf(false) }
    var categoryDialog by remember { mutableStateOf<CategoryDialogTarget?>(null) }
    var deleteTarget by remember { mutableStateOf<CategoryWithCount?>(null) }

    // The system file picker is used, so the app needs no storage permission.
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) viewModel.exportTo(uri)
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) viewModel.importFrom(uri)
    }

    BackHandler(enabled = searching || tab != MainTab.Expenses) {
        if (searching) viewModel.closeSearch() else onTabChange(MainTab.Expenses)
    }

    Scaffold(
        topBar = {
            if (searching) {
                SearchTopBar(
                    query = viewModel.query,
                    onQueryChange = viewModel::onQueryChange,
                    onClose = viewModel::closeSearch,
                )
            } else {
                TopAppBar(
                    title = { Text(tab.title) },
                    actions = {
                        if (tab == MainTab.Expenses) {
                            IconButton(onClick = viewModel::openSearch) {
                                Icon(AppIcons.Search, contentDescription = "Search")
                            }
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(AppIcons.MoreVert, contentDescription = "More options")
                            }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Export to CSV file") },
                                    onClick = {
                                        menuOpen = false
                                        try {
                                            exportLauncher.launch("expenses-${LocalDate.now()}.csv")
                                        } catch (e: ActivityNotFoundException) {
                                            viewModel.showMessage("No app found for saving files")
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Import from CSV file") },
                                    onClick = {
                                        menuOpen = false
                                        try {
                                            // Any type: file managers label CSV files in many different ways.
                                            importLauncher.launch(arrayOf("*/*"))
                                        } catch (e: ActivityNotFoundException) {
                                            viewModel.showMessage("No app found for choosing files")
                                        }
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text("Currency") },
                                    onClick = {
                                        menuOpen = false
                                        showCurrency = true
                                    },
                                )
                            }
                        }
                    },
                )
            }
        },
        bottomBar = {
            if (!searching) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == MainTab.Expenses,
                        onClick = { onTabChange(MainTab.Expenses) },
                        icon = { Icon(AppIcons.Receipt, contentDescription = null) },
                        label = { Text(MainTab.Expenses.title) },
                        modifier = Modifier.testTag("tab-Expenses"),
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.Summary,
                        onClick = { onTabChange(MainTab.Summary) },
                        icon = { Icon(AppIcons.PieChart, contentDescription = null) },
                        label = { Text(MainTab.Summary.title) },
                        modifier = Modifier.testTag("tab-Summary"),
                    )
                    NavigationBarItem(
                        selected = tab == MainTab.Classifications,
                        onClick = { onTabChange(MainTab.Classifications) },
                        icon = { Icon(AppIcons.Label, contentDescription = null) },
                        label = { Text(MainTab.Classifications.title) },
                        modifier = Modifier.testTag("tab-Classifications"),
                    )
                }
            }
        },
        floatingActionButton = {
            if (!searching && tab != MainTab.Classifications) {
                ExtendedFloatingActionButton(
                    text = { Text("Add expense") },
                    icon = { Icon(AppIcons.Add, contentDescription = null) },
                    onClick = viewModel::startNewExpense,
                    modifier = Modifier.testTag("addExpense"),
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        when (tab) {
            MainTab.Expenses -> ExpensesTab(
                state = expensesUi,
                query = viewModel.query,
                categories = categories.orEmpty(),
                listState = expensesListState,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onCurrentMonth = viewModel::goToCurrentMonth,
                onFilter = viewModel::setFilter,
                onOpen = viewModel::startEditing,
                modifier = Modifier
                    .padding(padding)
                    .consumeWindowInsets(padding)
                    // While searching, keep the results above the keyboard.
                    .imePadding(),
            )

            MainTab.Summary -> SummaryTab(
                state = summaryUi,
                onPreviousMonth = viewModel::previousMonth,
                onNextMonth = viewModel::nextMonth,
                onCurrentMonth = viewModel::goToCurrentMonth,
                onOpenCategory = { categoryId ->
                    viewModel.showCategory(categoryId)
                    onTabChange(MainTab.Expenses)
                },
                modifier = Modifier.padding(padding),
            )

            MainTab.Classifications -> ClassificationsTab(
                categories = categories,
                onAdd = { categoryDialog = CategoryDialogTarget.New },
                onEdit = { categoryDialog = CategoryDialogTarget.Edit(it) },
                onDelete = { deleteTarget = it },
                modifier = Modifier.padding(padding),
            )
        }
    }

    if (showCurrency) {
        CurrencyDialog(
            current = currencyCode,
            onPick = { code ->
                viewModel.setCurrency(code)
                showCurrency = false
            },
            onDismiss = { showCurrency = false },
        )
    }

    val dialogTarget = categoryDialog
    if (dialogTarget != null) {
        val all = categories.orEmpty()
        when (dialogTarget) {
            is CategoryDialogTarget.New -> CategoryDialog(
                title = "New classification",
                initialName = "",
                initialColor = Categories.nextColor(all.map { it.color }),
                existingNames = all.map { it.name },
                renamingFrom = null,
                onDismiss = { categoryDialog = null },
                onSave = { name, color ->
                    viewModel.addCategory(name, color)
                    categoryDialog = null
                },
            )

            is CategoryDialogTarget.Edit -> CategoryDialog(
                title = "Edit classification",
                initialName = dialogTarget.category.name,
                initialColor = dialogTarget.category.color,
                existingNames = all.map { it.name },
                renamingFrom = dialogTarget.category.name,
                onDismiss = { categoryDialog = null },
                onSave = { name, color ->
                    viewModel.updateCategory(dialogTarget.category.id, name, color)
                    categoryDialog = null
                },
            )
        }
    }

    val toDelete = deleteTarget
    if (toDelete != null) {
        ConfirmDialog(
            title = "Delete “${toDelete.name}”?",
            text = when (toDelete.expenseCount) {
                0 -> "No expenses use this classification."
                1 -> "1 expense uses this classification. It will be kept and shown as “${Categories.UNCATEGORIZED}”."
                else -> TextTools.plural(toDelete.expenseCount, "expense") +
                    " use this classification. They will be kept and shown as “${Categories.UNCATEGORIZED}”."
            },
            confirmLabel = "Delete",
            onConfirm = {
                viewModel.deleteCategory(toDelete)
                deleteTarget = null
            },
            onDismiss = { deleteTarget = null },
        )
    }
}

/** Replaces the title bar while searching: back arrow, text box, clear button. */
@Composable
private fun SearchTopBar(query: String, onQueryChange: (String) -> Unit, onClose: () -> Unit) {
    val focusRequester = remember { FocusRequester() }
    val keyboard = LocalSoftwareKeyboardController.current
    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onClose) {
                Icon(AppIcons.ArrowBack, contentDescription = "Close search")
            }
        },
        title = {
            TextField(
                value = query,
                onValueChange = onQueryChange,
                placeholder = { Text("Search expenses") },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge,
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color.Transparent,
                    unfocusedContainerColor = Color.Transparent,
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester)
                    .testTag("search"),
            )
        },
        actions = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(AppIcons.Close, contentDescription = "Clear search")
                }
            }
        },
    )
}
