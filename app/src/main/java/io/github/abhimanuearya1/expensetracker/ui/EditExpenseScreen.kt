@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.abhimanuearya1.expensetracker.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.Dates
import io.github.abhimanuearya1.expensetracker.core.Money
import io.github.abhimanuearya1.expensetracker.data.CategoryWithCount

private const val MILLIS_PER_DAY = 86_400_000L
private const val MAX_DETAILS_LENGTH = 200

/**
 * Full-screen form to add an expense or change an existing one:
 * amount, date, classification (picked from the classification list) and details.
 */
@Composable
fun EditExpenseScreen(
    editing: Editing,
    categories: List<CategoryWithCount>,
    onSave: (id: Long?, amountCents: Long, epochDay: Long, categoryId: Long?, details: String) -> Unit,
    onDelete: (id: Long) -> Unit,
    onClose: () -> Unit,
    onAddCategory: (name: String, color: Int, onAdded: (Long) -> Unit) -> Unit,
) {
    val existing = (editing as? Editing.Existing)?.item
    val money = LocalMoney.current
    val today = Dates.today()

    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toInputString(it.amountCents) } ?: "") }
    var epochDay by rememberSaveable { mutableStateOf(existing?.epochDay ?: today) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var details by rememberSaveable { mutableStateOf(existing?.details ?: "") }

    var showProblems by rememberSaveable { mutableStateOf(false) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var showDelete by rememberSaveable { mutableStateOf(false) }
    var showDiscard by rememberSaveable { mutableStateOf(false) }
    var showNewCategory by rememberSaveable { mutableStateOf(false) }

    val cents = Money.parseToCents(amountText)
    val amountProblem = when {
        amountText.isBlank() -> "Enter the amount"
        cents == null -> "Enter an amount like 250 or 250.50"
        cents == 0L -> "The amount must be more than zero"
        else -> null
    }
    // An expense whose classification was deleted may stay uncategorised; anything else needs one.
    val mustClassify = categories.isNotEmpty() && (existing == null || existing.categoryId != null)
    val classificationProblem = if (mustClassify && categoryId == null) "Choose a classification" else null

    val changed = if (existing == null) {
        amountText.isNotBlank() || details.isNotBlank() || categoryId != null
    } else {
        amountText != Money.toInputString(existing.amountCents) ||
            epochDay != existing.epochDay ||
            categoryId != existing.categoryId ||
            details != existing.details
    }

    fun requestClose() {
        if (changed) showDiscard = true else onClose()
    }

    fun save() {
        if (cents != null && amountProblem == null && classificationProblem == null) {
            onSave(existing?.id, cents, epochDay, categoryId, details)
        } else {
            showProblems = true
        }
    }

    BackHandler { requestClose() }

    val amountFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        // Typing the amount is always the first step of a new expense.
        if (existing == null) amountFocus.requestFocus()
    }

    Scaffold(
        // Keeps the Save button above the keyboard.
        modifier = Modifier.imePadding(),
        topBar = {
            TopAppBar(
                title = { Text(if (existing == null) "Add expense" else "Edit expense") },
                navigationIcon = {
                    IconButton(onClick = { requestClose() }) {
                        Icon(AppIcons.Close, contentDescription = "Close")
                    }
                },
                actions = {
                    if (existing != null) {
                        IconButton(onClick = { showDelete = true }) {
                            Icon(AppIcons.Delete, contentDescription = "Delete expense")
                        }
                    }
                },
            )
        },
        bottomBar = {
            Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
                Button(
                    onClick = { save() },
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .height(52.dp)
                        .testTag("saveExpense"),
                ) {
                    Text("Save", style = MaterialTheme.typography.titleMedium)
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
        ) {
            val visibleAmountProblem = if (showProblems) amountProblem else null
            OutlinedTextField(
                value = amountText,
                onValueChange = { text ->
                    if (text.length <= 14 && text.all { it in '0'..'9' || it == '.' || it == ',' }) amountText = text
                },
                label = { Text("Amount") },
                prefix = if (money.symbol.isNotEmpty()) {
                    { Text(money.symbol + " ", style = MaterialTheme.typography.headlineSmall) }
                } else {
                    null
                },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                isError = visibleAmountProblem != null,
                supportingText = if (visibleAmountProblem != null) {
                    { Text(visibleAmountProblem) }
                } else {
                    null
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(amountFocus)
                    .testTag("amount"),
            )

            SectionLabel("Date")
            OutlinedButton(
                onClick = { showDatePicker = true },
                shape = MaterialTheme.shapes.extraSmall,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("dateButton"),
            ) {
                Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = Dates.full(epochDay),
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f),
                )
                Text("Change")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = epochDay == today,
                    onClick = { epochDay = today },
                    label = { Text("Today") },
                )
                FilterChip(
                    selected = epochDay == today - 1,
                    onClick = { epochDay = today - 1 },
                    label = { Text("Yesterday") },
                )
            }

            SectionLabel("Classification")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (category in categories) {
                    key(category.id) {
                        FilterChip(
                            selected = categoryId == category.id,
                            onClick = { categoryId = category.id },
                            label = { Text(category.name) },
                            leadingIcon = { ColorDot(category.color) },
                            modifier = Modifier.testTag("pick-" + category.name),
                        )
                    }
                }
                AssistChip(
                    onClick = { showNewCategory = true },
                    label = { Text("New") },
                    leadingIcon = { Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("pick-new"),
                )
            }
            if (showProblems && classificationProblem != null) {
                Text(
                    text = classificationProblem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }

            Spacer(Modifier.height(20.dp))
            OutlinedTextField(
                value = details,
                onValueChange = { details = it.take(MAX_DETAILS_LENGTH) },
                label = { Text("Details (optional)") },
                placeholder = { Text("What was it for?") },
                minLines = 2,
                maxLines = 4,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("details"),
            )
            Spacer(Modifier.height(16.dp))
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = epochDay * MILLIS_PER_DAY)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        val picked = pickerState.selectedDateMillis
                        if (picked != null) epochDay = Math.floorDiv(picked, MILLIS_PER_DAY)
                        showDatePicker = false
                    },
                ) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showDatePicker = false }) { Text("Cancel") } },
        ) {
            DatePicker(state = pickerState)
        }
    }

    if (showDelete && existing != null) {
        ConfirmDialog(
            title = "Delete this expense?",
            text = money.format(existing.amountCents) + " · " +
                (existing.categoryName ?: Categories.UNCATEGORIZED) + " · " + Dates.full(existing.epochDay),
            confirmLabel = "Delete",
            onConfirm = { onDelete(existing.id) },
            onDismiss = { showDelete = false },
        )
    }

    if (showDiscard) {
        ConfirmDialog(
            title = if (existing == null) "Discard this expense?" else "Discard changes?",
            text = "What you entered will not be saved.",
            confirmLabel = "Discard",
            onConfirm = onClose,
            onDismiss = { showDiscard = false },
            dismissLabel = "Keep editing",
        )
    }

    if (showNewCategory) {
        CategoryDialog(
            title = "New classification",
            initialName = "",
            initialColor = Categories.nextColor(categories.map { it.color }),
            existingNames = categories.map { it.name },
            renamingFrom = null,
            onDismiss = { showNewCategory = false },
            onSave = { name, color ->
                showNewCategory = false
                onAddCategory(name, color) { newId -> categoryId = newId }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 20.dp, bottom = 8.dp),
    )
}
