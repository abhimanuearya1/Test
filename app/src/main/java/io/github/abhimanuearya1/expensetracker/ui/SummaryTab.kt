package io.github.abhimanuearya1.expensetracker.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/** Where the month's money went: one row per classification, largest first. */
@Composable
fun SummaryTab(
    state: SummaryUiState,
    onPreviousMonth: () -> Unit,
    onNextMonth: () -> Unit,
    onCurrentMonth: () -> Unit,
    onOpenCategory: (Long?) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
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
        if (!state.loading) {
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    EmptyMessage(
                        "Nothing spent in ${Dates.month(state.month)}",
                        "Expenses you add are totalled here by classification.",
                    )
                }
            } else {
                item(key = "heading") {
                    Text(
                        text = "By classification",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 6.dp),
                    )
                }
                items(state.rows, key = { it.categoryId ?: 0L }) { row ->
                    CategoryTotalRow(row = row, onClick = { onOpenCategory(row.categoryId) })
                }
            }
        }
    }
}

@Composable
private fun CategoryTotalRow(row: CategoryTotal, onClick: () -> Unit) {
    val money = LocalMoney.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Show these expenses", onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("summaryRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(name = row.name, color = row.color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.name ?: Categories.UNCATEGORIZED,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = money.format(row.totalCents),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            Spacer(Modifier.height(6.dp))
            ShareBar(share = row.share, color = Color(row.color ?: Categories.UNCATEGORIZED_COLOR))
            Spacer(Modifier.height(4.dp))
            Text(
                text = Summary.percentText(row.share) + " · " + TextTools.plural(row.count, "expense"),
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
