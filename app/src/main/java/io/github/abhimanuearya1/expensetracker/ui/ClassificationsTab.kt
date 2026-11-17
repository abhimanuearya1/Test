package io.github.abhimanuearya1.expensetracker.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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

/** The list of classifications, where they are added, renamed, recoloured and deleted. */
@Composable
fun ClassificationsTab(
    categories: List<CategoryWithCount>?,
    onEdit: (CategoryWithCount) -> Unit,
    onDelete: (CategoryWithCount) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp),
    ) {
        item(key = "intro") {
            Text(
                text = "Every expense is filed under one classification. Add your own, or tap one to rename it or change its colour.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp),
            )
        }
        if (categories != null) {
            if (categories.isEmpty()) {
                item(key = "empty") {
                    EmptyMessage("No classifications yet", "Tap “New classification” to create one.")
                }
            } else {
                items(categories, key = { it.id }) { category ->
                    ClassificationRow(
                        category = category,
                        onEdit = { onEdit(category) },
                        onDelete = { onDelete(category) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ClassificationRow(category: CategoryWithCount, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClickLabel = "Edit", onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 6.dp, bottom = 6.dp)
            .testTag("classificationRow"),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryBadge(name = category.name, color = category.color)
        Spacer(Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = category.name,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (category.expenseCount == 0) "Not used yet" else TextTools.plural(category.expenseCount, "expense"),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(AppIcons.Edit, contentDescription = "Edit ${category.name}")
        }
        IconButton(onClick = onDelete) {
            Icon(AppIcons.Delete, contentDescription = "Delete ${category.name}")
        }
    }
}
