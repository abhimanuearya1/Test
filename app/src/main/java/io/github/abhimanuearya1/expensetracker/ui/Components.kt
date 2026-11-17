@file:OptIn(ExperimentalLayoutApi::class)

package io.github.abhimanuearya1.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.Dates
import io.github.abhimanuearya1.expensetracker.core.MoneyFormatter
import io.github.abhimanuearya1.expensetracker.core.TextTools
import java.time.YearMonth
import java.util.Currency

/** Round coloured badge with the first letter of a classification. */
@Composable
fun CategoryBadge(name: String?, color: Int?, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    val letter = if (name == null) "?" else name.firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "•"
    // Sized in dp so the letter always fits the circle, whatever the font-size setting.
    val fontSize = with(LocalDensity.current) { (size * 0.44f).toSp() }
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color ?: Categories.UNCATEGORIZED_COLOR))
            .clearAndSetSemantics { },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = letter, color = Color.White, fontSize = fontSize, fontWeight = FontWeight.Bold)
    }
}

/** Small filled circle in a classification's colour. */
@Composable
fun ColorDot(color: Int?, modifier: Modifier = Modifier, size: Dp = 12.dp) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(Color(color ?: Categories.UNCATEGORIZED_COLOR))
    )
}

/** Card at the top of the Expenses and Summary tabs: month switcher and the month's total. */
@Composable
fun MonthHeader(
    month: YearMonth,
    totalCents: Long,
    count: Int,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onCurrentMonth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoney.current
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 4.dp, end = 4.dp, top = 4.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onPrevious) {
                    Icon(AppIcons.ChevronLeft, contentDescription = "Previous month")
                }
                Text(
                    text = Dates.month(month),
                    style = MaterialTheme.typography.titleMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .clickable(onClickLabel = "Go to the current month", onClick = onCurrentMonth)
                        .padding(vertical = 10.dp)
                        .testTag("monthLabel"),
                )
                IconButton(onClick = onNext) {
                    Icon(AppIcons.ChevronRight, contentDescription = "Next month")
                }
            }
            Text(
                text = money.format(totalCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .testTag("monthTotal"),
            )
            Text(
                text = "Total spent · " + TextTools.plural(count, "expense"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/** Centered two-line message for empty lists. */
@Composable
fun EmptyMessage(title: String, subtitle: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    text: String,
    confirmLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    dismissLabel: String = "Cancel",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(text) },
        confirmButton = { TextButton(onClick = onConfirm) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(dismissLabel) } },
    )
}

/**
 * Dialog to create or change a classification: its name and colour.
 *
 * @param renamingFrom the current name when editing (so it is not reported as a duplicate), null when creating.
 */
@Composable
fun CategoryDialog(
    title: String,
    initialName: String,
    initialColor: Int,
    existingNames: List<String>,
    renamingFrom: String?,
    onDismiss: () -> Unit,
    onSave: (name: String, color: Int) -> Unit,
) {
    var name by rememberSaveable { mutableStateOf(initialName) }
    var color by rememberSaveable { mutableStateOf(initialColor) }
    var showProblem by rememberSaveable { mutableStateOf(false) }
    val problem = Categories.nameProblem(name, existingNames, renamingFrom)
    val visibleProblem = if (showProblem) problem else null

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= Categories.MAX_NAME_LENGTH) name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    isError = visibleProblem != null,
                    supportingText = if (visibleProblem != null) {
                        { Text(visibleProblem) }
                    } else {
                        null
                    },
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Words,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("classificationName"),
                )
                Spacer(Modifier.height(16.dp))
                Text("Colour", style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Categories.PALETTE.forEachIndexed { index, choice ->
                        ColorChoice(
                            color = choice,
                            name = Categories.PALETTE_NAMES[index],
                            selected = choice == color,
                            onClick = { color = choice },
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (problem == null) onSave(Categories.cleanName(name), color) else showProblem = true
                },
            ) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun ColorChoice(color: Int, name: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(color))
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                } else {
                    Modifier
                }
            )
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = name },
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(AppIcons.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        }
    }
}

private val CURRENCY_CODES = listOf(
    "INR", "USD", "EUR", "GBP", "AED", "SAR", "AUD", "CAD", "SGD", "JPY", "CNY", "BDT", "LKR", "NPR", "PKR",
)

/** Lets the user pick the currency used to show amounts. Stored amounts are not changed. */
@Composable
fun CurrencyDialog(current: String?, onPick: (String?) -> Unit, onDismiss: () -> Unit) {
    val locale = LocalConfiguration.current.locales[0]
    val choices = remember(locale) {
        CURRENCY_CODES.mapNotNull { code ->
            runCatching { Currency.getInstance(code) }.getOrNull()?.let { currency ->
                code to "${currency.getDisplayName(locale)} (${currency.getSymbol(locale)})"
            }
        }
    }
    val automaticSymbol = remember(locale) { MoneyFormatter(null, locale).symbol }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Currency") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                CurrencyRow(
                    label = if (automaticSymbol.isEmpty()) "Same as phone" else "Same as phone ($automaticSymbol)",
                    selected = current == null,
                    onClick = { onPick(null) },
                )
                for ((code, label) in choices) {
                    CurrencyRow(label = label, selected = current == code, onClick = { onPick(code) })
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun CurrencyRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null)
        Text(text = label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp))
    }
}
