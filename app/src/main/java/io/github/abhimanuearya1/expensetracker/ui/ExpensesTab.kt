@file:OptIn(ExperimentalFoundationApi::class)

package io.github.abhimanuearya1.expensetracker.ui

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.abhimanuearya1.expensetracker.core.Categories
import io.github.abhimanuearya1.expensetracker.core.Dates
import io.github.abhimanuearya1.expensetracker.core.DayGroup
import io.github.abhimanuearya1.expensetracker.core.ExpenseItem
import io.github.abhimanuearya1.expensetracker.core.TextTools
import io.github.abhimanuearya1.expensetracker.data.CategoryWithCount

/**
 * The main list. Normally one month, grouped by day; while searching, every
 * matching expense from all months.
 */
@Composable
fun ExpensesTab(
    state: ExpensesUiState,
    query: String,
    categories: List<CategoryWithCount>,
    listState: LazyListState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onFilter: (Long?) -> Unit,
    onOpen: (ExpenseItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    val today = Dates.today()
    val showUncategorized = state.hasUncategorized || state.filter == ExpenseViewModel.FILTER_UNCATEGORIZED

    LazyColumn(
        state = listState,
        modifier = modifier.fillMaxSize(),
        // Room for the "Add expense" button so it never hides the last row.
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        if (state.searching) {
            item(key = "searchSummary") { SearchSummary(state) }
        } else {
            item(key = "month") {
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

        if (categories.isNotEmpty() || showUncategorized) {
            item(key = "filters") {
                FilterChips(
                    categories = categories,
                    selected = state.filter,
                    showUncategorized = showUncategorized,
                    onSelect = onFilter,
                )
            }
        }

        if (!state.loading) {
            if (state.days.isEmpty()) {
                item(key = "empty") {
                    val filterLabel = filterName(state.filter, categories)
                    when {
                        state.searching && query.isBlank() && filterLabel == null ->
                            EmptyMessage("No expenses yet", "Expenses you add will all be listed here.")
                        state.searching ->
                            EmptyMessage("Nothing found", "Try another word, an amount such as 250, or a date such as 16 Nov.")
                        filterLabel != null ->
                            EmptyMessage(
                                "No “$filterLabel” expenses in ${Dates.month(state.month)}",
                                "Choose “All” to see every expense of the month.",
                            )
                        else ->
                            EmptyMessage(
                                "No expenses in ${Dates.month(state.month)}",
                                "Tap “Add expense” to record one.",
                            )
                    }
                }
            } else {
                for (day in state.days) {
                    stickyHeader(key = "day" + day.epochDay) { DayHeader(day, today) }
                    items(day.items, key = { it.id }) { item ->
                        ExpenseRow(item = item, onClick = { onOpen(item) })
                    }
                }
            }
        }
    }
}

private fun filterName(filter: Long?, categories: List<CategoryWithCount>): String? = when (filter) {
    null -> null
    ExpenseViewModel.FILTER_UNCATEGORIZED -> Categories.UNCATEGORIZED
    else -> categories.firstOrNull { it.id == filter }?.name
}

@Composable
private fun SearchSummary(state: ExpensesUiState) {
    val money = LocalMoney.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "All months",
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        if (!state.loading) {
            Text(
                text = TextTools.plural(state.count, "expense") + " · " + money.format(state.totalCents),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.testTag("searchSummary"),
            )
        }
    }
}

/** One-tap filter: "All", then every classification. */
@Composable
private fun FilterChips(
    categories: List<CategoryWithCount>,
    selected: Long?,
    showUncategorized: Boolean,
    onSelect: (Long?) -> Unit,
) {
    // Bring the active filter into view, e.g. when it was chosen from the Summary tab.
    val rowState = rememberLazyListState()
    LaunchedEffect(selected, categories.size) {
        val index = when (selected) {
            null -> 0
            ExpenseViewModel.FILTER_UNCATEGORIZED -> categories.size + 1
            else -> categories.indexOfFirst { it.id == selected } + 1
        }
        val visible = rowState.layoutInfo.visibleItemsInfo
        val fullyVisible = visible.any {
            it.index == index && it.offset >= 0 && it.offset + it.size <= rowState.layoutInfo.viewportEndOffset
        }
        if (index > 0 && !fullyVisible) rowState.animateScrollToItem(index)
    }

    LazyRow(
        state = rowState,
        modifier = Modifier.fillMaxWidth(),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "all") {
            FilterChip(
                selected = selected == null,
                onClick = { onSelect(null) },
                label = { Text("All") },
                modifier = Modifier.testTag("filter-All"),
            )
        }
        items(categories, key = { it.id }) { category ->
            FilterChip(
                selected = selected == category.id,
                onClick = { onSelect(if (selected == category.id) null else category.id) },
                label = { Text(category.name) },
                leadingIcon = { ColorDot(category.color) },
                modifier = Modifier.testTag("filter-" + category.name),
            )
        }
        if (showUncategorized) {
            item(key = "uncategorized") {
                val isSelected = selected == ExpenseViewModel.FILTER_UNCATEGORIZED
                FilterChip(
                    selected = isSelected,
                    onClick = { onSelect(if (isSelected) null else ExpenseViewModel.FILTER_UNCATEGORIZED) },
                    label = { Text(Categories.UNCATEGORIZED) },
                    leadingIcon = { ColorDot(null) },
                    modifier = Modifier.testTag("filter-" + Categories.UNCATEGORIZED),
                )
            }
        }
    }
}

@Composable
private fun DayHeader(day: DayGroup, today: Long) {
    val money = LocalMoney.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // Opaque, because rows scroll underneath while this stays pinned.
            .background(MaterialTheme.colorScheme.background)
            .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = Dates.dayHeader(day.epochDay, today),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = money.format(day.totalCents),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Classification first (it is how the data is organised), details underneath, amount on the right. */
@Composable
private fun ExpenseRow(item: ExpenseItem, onClick: () -> Unit) {
    val money = LocalMoney.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("expenseRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(name = item.categoryName, color = item.categoryColor)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.categoryName ?: Categories.UNCATEGORIZED,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (item.details.isNotBlank()) {
                Text(
                    text = item.details,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (item.paymentMethodName != null || item.attachmentCount > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (item.paymentMethodName != null) {
                        Text(
                            text = item.paymentMethodName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false),
                        )
                    }
                    if (item.attachmentCount > 0) {
                        if (item.paymentMethodName != null) Spacer(Modifier.width(8.dp))
                        Icon(
                            AppIcons.Attach,
                            contentDescription = TextTools.plural(item.attachmentCount, "document") + " attached",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp),
                        )
                        Text(
                            text = item.attachmentCount.toString(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
        Spacer(Modifier.width(12.dp))
        Text(
            text = money.format(item.amountCents),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
