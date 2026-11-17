package io.github.abhimanuearya1.expensetracker.ui

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.abhimanuearya1.expensetracker.core.TextTools
import io.github.abhimanuearya1.expensetracker.data.CategoryWithCount

/**
 * The two lists expenses are filed by — classifications and payment methods —
 * where their entries are added, renamed, recoloured and deleted.
 */
@Composable
fun ListsTab(
    showPaymentMethods: Boolean,
    onShowPaymentMethods: (Boolean) -> Unit,
    entries: List<CategoryWithCount>?,
    onAdd: () -> Unit,
    onEdit: (CategoryWithCount) -> Unit,
    onDelete: (CategoryWithCount) -> Unit,
    modifier: Modifier = Modifier,
) {
    val noun = if (showPaymentMethods) "payment method" else "classification"
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .testTag("classificationList"),
        contentPadding = PaddingValues(bottom = 24.dp),
    ) {
        item(key = "which") {
            ChoiceToggle(
                options = listOf("Classifications", "Payment methods"),
                selectedIndex = if (showPaymentMethods) 1 else 0,
                onSelect = { onShowPaymentMethods(it == 1) },
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
            )
        }
        item(key = "intro") {
            Text(
                text = if (showPaymentMethods) {
                    "How an expense was paid: cash, UPI, a particular card or account. Add your own, or tap one to rename it or change its colour."
                } else {
                    "Every expense is filed under one classification. Add your own, or tap one to rename it or change its colour."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        // A button in the list rather than a floating one, so nothing covers the
        // edit and delete icons of the rows below.
        item(key = "add") {
            FilledTonalButton(
                onClick = onAdd,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("addClassification"),
            ) {
                Icon(AppIcons.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("New $noun")
            }
        }
        if (entries != null) {
            if (entries.isEmpty()) {
                item(key = "empty") {
                    EmptyMessage("Nothing here yet", "Tap “New $noun” above to create one.")
                }
            } else {
                items(entries, key = { it.id }) { entry ->
                    EntryRow(
                        entry = entry,
                        onEdit = { onEdit(entry) },
                        onDelete = { onDelete(entry) },
                    )
                }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: CategoryWithCount, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit", onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("classificationRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(name = entry.name, color = entry.color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (entry.expenseCount == 0) "Not used yet" else TextTools.plural(entry.expenseCount, "expense"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(AppIcons.Edit, contentDescription = "Edit ${entry.name}")
        }
        IconButton(onClick = onDelete) {
            Icon(AppIcons.Delete, contentDescription = "Delete ${entry.name}")
        }
    }
}
