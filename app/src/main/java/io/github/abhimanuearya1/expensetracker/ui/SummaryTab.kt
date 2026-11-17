package io.github.abhimanuearya1.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.CategoryTotal
import io.github.abhimanuearya1.expensetracker.core.Dates
import io.github.abhimanuearya1.expensetracker.core.Summary
import io.github.abhimanuearya1.expensetracker.core.TextTools

/**
 * Where the money went, largest first.
 *
 * Three choices at the top: the period (a month, or any range of dates), what to
 * group by (classification or payment method), and whether the amount or the
 * percentage is the headline figure of each row. Both are always shown.
 */
@Composable
fun SummaryTab(
    state: SummaryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onCustomRange: (Boolean) -> Unit,
    onFromDay: (Long) -> Unit,
    onToDay: (Long) -> Unit,
    onOpenCategory: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    var byPaymentMethod by rememberSaveable { mutableStateOf(false) }
    var percentFirst by rememberSaveable { mutableStateOf(false) }
    val rows = if (byPaymentMethod) state.paymentRows else state.rows
    val periodName = if (state.customRange) "this period" else Dates.month(state.month)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "period") {
            ChoiceToggle(
                options = listOf("Month", "Custom range"),
                selectedIndex = if (state.customRange) 1 else 0,
                onSelect = { onCustomRange(it == 1) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        item(key = "header") {
            if (state.customRange) {
                RangeHeader(
                    state = state,
                    onFromDay = onFromDay,
                    onToDay = onToDay,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            } else {
                MonthHeader(
                    month = state.month,
                    totalCents = state.totalCents,
                    count = state.count,
                    onPrevious = onPreviousMonth,
                    onNext = onNextMonth,
                    onCurrentMonth = onCurrentMonth,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp),
                )
            }
        }
        item(key = "grouping") {
            ChoiceToggle(
                options = listOf("By classification", "By payment method"),
                selectedIndex = if (byPaymentMethod) 1 else 0,
                onSelect = { byPaymentMethod = it == 1 },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        item(key = "figures") {
            ChoiceToggle(
                options = listOf("Amount", "Percentage"),
                selectedIndex = if (percentFirst) 1 else 0,
                onSelect = { percentFirst = it == 1 },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 4.dp),
            )
        }
        if (!state.loading) {
            if (rows.isEmpty()) {
                item(key = "empty") {
                    EmptyMessage("Nothing spent in $periodName", "Expenses you add are totalled here.")
                }
            } else {
                // Opening the matching expenses is offered where the Expenses tab can show them: one month, one classification.
                val canOpen = !state.customRange && !byPaymentMethod
                items(rows, key = { it.categoryId ?: 0L }) { row ->
                    TotalRow(
                        row = row,
                        nameIfNone = if (byPaymentMethod) "Not set" else Categories.UNCATEGORIZED,
                        percentFirst = percentFirst,
                        onClick = if (canOpen) {
                            { onOpenCategory(row.categoryId) }
                        } else {
                            null
                        },
                    )
                }
            }
        }
    }
}

/** Card for a custom period: its two dates, each opening a calendar, and the total. */
@Composable
private fun RangeHeader(
    state: SummaryUiState,
    onFromDay: (Long) -> Unit,
    onToDay: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    val money = LocalMoney.current
    var picking by rememberSaveable { mutableStateOf(0) } // 0 none, 1 from, 2 to

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        shape = RoundedCornerShape(24.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(
            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DateButton("From", state.fromDay, { picking = 1 }, Modifier.weight(1f).testTag("rangeFrom"))
                DateButton("To", state.toDay, { picking = 2 }, Modifier.weight(1f).testTag("rangeTo"))
            }
            Spacer(Modifier.height(12.dp))
            Text(
                text = money.format(state.totalCents),
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.testTag("monthTotal"),
            )
            Text(
                text = "Total spent · " + TextTools.plural(state.count, "expense"),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }

    if (picking == 1) {
        DayPickerDialog(epochDay = state.fromDay, onPick = onFromDay, onDismiss = { picking = 0 })
    } else if (picking == 2) {
        DayPickerDialog(epochDay = state.toDay, onPick = onToDay, onDismiss = { picking = 0 })
    }
}

@Composable
private fun DateButton(label: String, epochDay: Long, onClick: () -> Unit, modifier: Modifier = Modifier) {
    FilledTonalButton(
        onClick = onClick,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
        modifier = modifier,
    ) {
        Icon(AppIcons.Calendar, contentDescription = null, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(8.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelSmall)
            Text(Dates.short(epochDay), style = MaterialTheme.typography.labelLarge, maxLines = 1)
        }
    }
}

@Composable
private fun TotalRow(row: CategoryTotal, nameIfNone: String, percentFirst: Boolean, onClick: (() -> Unit)?) {
    val money = LocalMoney.current
    val amount = money.format(row.totalCents)
    val percent = Summary.percentText(row.share)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClickLabel = "Show these expenses", onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("summaryRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(name = row.name, color = row.color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name ?: nameIfNone,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (percentFirst) percent else amount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            ShareBar(share = row.share, color = Color(row.color ?: Categories.UNCATEGORIZED_COLOR))
            Spacer(Modifier.height(4.dp))
            Text(
                text = (if (percentFirst) amount else percent) + " · " + TextTools.plural(row.count, "expense"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Horizontal bar filled in proportion to [share] (0..1). */
@Composable
private fun ShareBar(share: Float, color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .clip(RoundedCornerShape(4.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(share.coerceIn(0f, 1f))
                .fillMaxHeight()
                .clip(RoundedCornerShape(4.dp))
                .background(color),
        )
    }
}
