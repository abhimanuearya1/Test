@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.abhimanuearya1.expensetracker.ui

import android.content.ActivityNotFoundException
import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Button
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.Dates
import io.github.abhimanuearya1.expensetracker.core.Money
import io.github.abhimanuearya1.expensetracker.data.AttachmentDraft
import io.github.abhimanuearya1.expensetracker.data.CategoryWithCount

private const val MAX_DETAILS_LENGTH = 200

/** Which of the form's pop-up lists and dialogs is open. Saved as its name. */
private enum class FormDialog { None, Date, Classification, NewClassification, PaymentMethod, NewPaymentMethod, Delete, Discard }

/**
 * Full-screen form to add an expense or change an existing one: amount, date,
 * classification and payment method (each picked from its list), details, and
 * supporting documents.
 */
@Composable
fun EditExpenseScreen(
    editing: Editing,
    categories: List<CategoryWithCount>,
    paymentMethods: List<CategoryWithCount>,
    viewModel: ExpenseViewModel,
) {
    val existing = (editing as? Editing.Existing)?.item
    val money = LocalMoney.current
    val context = LocalContext.current
    val today = Dates.today()
    val attachments = viewModel.editorAttachments

    var amountText by rememberSaveable { mutableStateOf(existing?.let { Money.toInputString(it.amountCents) } ?: "") }
    var epochDay by rememberSaveable { mutableStateOf(existing?.epochDay ?: today) }
    var categoryId by rememberSaveable { mutableStateOf(existing?.categoryId) }
    var paymentMethodId by rememberSaveable { mutableStateOf(existing?.paymentMethodId) }
    var details by rememberSaveable { mutableStateOf(existing?.details ?: "") }
    var showProblems by rememberSaveable { mutableStateOf(false) }
    var dialog by rememberSaveable { mutableStateOf(FormDialog.None) }

    val category = categories.firstOrNull { it.id == categoryId }
    val paymentMethod = paymentMethods.firstOrNull { it.id == paymentMethodId }

    val cents = Money.parseToCents(amountText)
    val amountProblem = when {
        amountText.isBlank() -> "Enter the amount"
        cents == null -> "Enter an amount like 250 or 250.50"
        cents == 0L -> "The amount must be more than zero"
        else -> null
    }
    // An expense that has no classification (imported, or its classification was
    // deleted) may stay that way; a new expense needs one.
    val mustClassify = categories.isNotEmpty() && (existing == null || existing.categoryId != null)
    val classificationProblem = if (mustClassify && categoryId == null) "Choose a classification" else null

    val changed = viewModel.attachmentsChanged || if (existing == null) {
        amountText.isNotBlank() || details.isNotBlank() || categoryId != null || paymentMethodId != null
    } else {
        amountText != Money.toInputString(existing.amountCents) ||
            epochDay != existing.epochDay ||
            categoryId != existing.categoryId ||
            paymentMethodId != existing.paymentMethodId ||
            details != existing.details
    }

    fun requestClose() {
        if (changed) dialog = FormDialog.Discard else viewModel.closeEditor()
    }

    fun save() {
        if (cents != null && amountProblem == null && classificationProblem == null) {
            viewModel.saveExpense(existing?.id, cents, epochDay, categoryId, details, paymentMethodId)
        } else {
            showProblems = true
        }
    }

    fun open(attachment: AttachmentDraft) {
        val intent = Intent(Intent.ACTION_VIEW)
            .setDataAndType(viewModel.attachmentUri(attachment), attachment.mimeType)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        try {
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            viewModel.showMessage("No app on this phone can open ${attachment.displayName}")
        }
    }

    // The system file picker and camera are used, so no permissions are needed.
    val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        viewModel.addAttachments(uris)
    }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { saved ->
        viewModel.finishPhoto(saved)
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
                        IconButton(onClick = { dialog = FormDialog.Delete }) {
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
                onClick = { dialog = FormDialog.Date },
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
            PickerField(
                value = category?.name,
                color = category?.color,
                placeholder = if (existing != null && existing.categoryId == null) Categories.UNCATEGORIZED else "Choose from the list",
                onClick = { dialog = FormDialog.Classification },
                modifier = Modifier.testTag("pickClassification"),
            )
            if (showProblems && classificationProblem != null) {
                Text(
                    text = classificationProblem,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp),
                )
            }

            SectionLabel("Payment method")
            PickerField(
                value = paymentMethod?.name,
                color = paymentMethod?.color,
                placeholder = "Not set",
                onClick = { dialog = FormDialog.PaymentMethod },
                modifier = Modifier.testTag("pickPaymentMethod"),
            )

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

            SectionLabel("Supporting documents")
            for (attachment in attachments) {
                AttachmentRow(
                    attachment = attachment,
                    onOpen = { open(attachment) },
                    onRemove = { viewModel.removeAttachment(attachment) },
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = {
                        try {
                            // Any type: photos, PDFs, documents.
                            filePicker.launch(arrayOf("*/*"))
                        } catch (e: ActivityNotFoundException) {
                            viewModel.showMessage("No app found for choosing files")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("attachFile"),
                ) {
                    Icon(AppIcons.Attach, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Attach file", maxLines = 1)
                }
                OutlinedButton(
                    onClick = {
                        try {
                            camera.launch(viewModel.beginPhoto())
                        } catch (e: ActivityNotFoundException) {
                            viewModel.finishPhoto(false)
                            viewModel.showMessage("No camera app found")
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("takePhoto"),
                ) {
                    Icon(AppIcons.Camera, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Take photo", maxLines = 1)
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    when (dialog) {
        FormDialog.None -> {}

        FormDialog.Date -> DayPickerDialog(
            epochDay = epochDay,
            onPick = { epochDay = it },
            onDismiss = { dialog = FormDialog.None },
        )

        FormDialog.Classification -> ChoiceListDialog(
            title = "Classification",
            options = categories,
            selectedId = categoryId,
            noneLabel = null,
            newLabel = "New classification",
            onPick = {
                categoryId = it
                dialog = FormDialog.None
            },
            onNew = { dialog = FormDialog.NewClassification },
            onDismiss = { dialog = FormDialog.None },
        )

        FormDialog.NewClassification -> CategoryDialog(
            title = "New classification",
            initialName = "",
            initialColor = Categories.nextColor(categories.map { it.color }),
            existingNames = categories.map { it.name },
            renamingFrom = null,
            onDismiss = { dialog = FormDialog.None },
            onSave = { name, color ->
                dialog = FormDialog.None
                viewModel.addCategory(name, color) { newId -> categoryId = newId }
            },
        )

        FormDialog.PaymentMethod -> ChoiceListDialog(
            title = "Payment method",
            options = paymentMethods,
            selectedId = paymentMethodId,
            noneLabel = "Not set",
            newLabel = "New payment method",
            onPick = {
                paymentMethodId = it
                dialog = FormDialog.None
            },
            onNew = { dialog = FormDialog.NewPaymentMethod },
            onDismiss = { dialog = FormDialog.None },
        )

        FormDialog.NewPaymentMethod -> CategoryDialog(
            title = "New payment method",
            initialName = "",
            initialColor = Categories.nextColor(paymentMethods.map { it.color }),
            existingNames = paymentMethods.map { it.name },
            renamingFrom = null,
            onDismiss = { dialog = FormDialog.None },
            onSave = { name, color ->
                dialog = FormDialog.None
                viewModel.addPaymentMethod(name, color) { newId -> paymentMethodId = newId }
            },
        )

        FormDialog.Delete -> if (existing != null) {
            ConfirmDialog(
                title = "Delete this expense?",
                text = money.format(existing.amountCents) + " · " +
                    (existing.categoryName ?: Categories.UNCATEGORIZED) + " · " + Dates.full(existing.epochDay),
                confirmLabel = "Delete",
                onConfirm = { viewModel.deleteExpense(existing.id) },
                onDismiss = { dialog = FormDialog.None },
            )
        }

        FormDialog.Discard -> ConfirmDialog(
            title = if (existing == null) "Discard this expense?" else "Discard changes?",
            text = "What you entered will not be saved.",
            confirmLabel = "Discard",
            onConfirm = { viewModel.closeEditor() },
            onDismiss = { dialog = FormDialog.None },
            dismissLabel = "Keep editing",
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

/** One attached file: tap the name to open it, the cross to remove it. */
@Composable
private fun AttachmentRow(attachment: AttachmentDraft, onOpen: () -> Unit, onRemove: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Open", onClick = onOpen)
            .padding(start = 4.dp)
            .testTag("attachment"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            AppIcons.Attach,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Text(
            text = attachment.displayName,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onRemove) {
            Icon(AppIcons.Close, contentDescription = "Remove ${attachment.displayName}")
        }
    }
}
