package com.labeltruth.app.ui.bookmarks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labeltruth.app.R
import com.labeltruth.app.data.local.ScanEntity
import com.labeltruth.app.domain.model.Grade
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.ui.components.gradeColor
import com.labeltruth.app.ui.components.riskColor

/**
 * Ingredients the user chose to keep.
 *
 * Unlimited, and it stays that way. Capping saved items is a common way to
 * manufacture an upgrade prompt, and this list costs one row of ids.
 */
@Composable
fun BookmarksScreen(
    savedScans: List<ScanEntity>,
    bookmarks: List<Ingredient>,
    onOpenScan: (Long) -> Unit,
    onOpenIngredient: (Ingredient) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
    ) {
        Text(
            text = "Saved",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 4.dp)
        )
        Text(
            text = listOf(
                savedScans.size to "product",
                bookmarks.size to "ingredient"
            ).filter { it.first > 0 }
                .joinToString(" · ") { (count, noun) ->
                    if (count == 1) "1 $noun" else "$count ${noun}s"
                }
                .ifEmpty { "Nothing saved yet" },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
        )

        if (bookmarks.isEmpty() && savedScans.isEmpty()) {
            EmptyState()
            return@Column
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            // Products first: a saved product is a whole scan the user chose to
            // keep, which is a bigger thing than one ingredient.
            if (savedScans.isNotEmpty()) {
                item { SectionHeader("Products") }
                items(savedScans, key = { "scan-${it.id}" }) { scan ->
                    SavedScanRow(scan, onClick = { onOpenScan(scan.id) })
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }
            if (bookmarks.isNotEmpty()) {
                item { SectionHeader("Ingredients") }
                items(bookmarks, key = { "ingredient-${it.id}" }) { ingredient ->
                    BookmarkRow(ingredient, onClick = { onOpenIngredient(ingredient) })
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }
            item { Spacer(Modifier.height(110.dp)) }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 6.dp)
    )
}

@Composable
private fun SavedScanRow(scan: ScanEntity, onClick: () -> Unit) {
    // Mirrors the history row, including the dash for a scan we could not score
    // honestly, so the same product looks the same in both places.
    val tint = scan.score?.let { gradeColor(Grade.of(it)) }
        ?: MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .background(tint.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = scan.score?.toString() ?: "–",
                style = MaterialTheme.typography.labelLarge,
                color = tint
            )
        }
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scan.productName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (!scan.brand.isNullOrBlank()) {
                Text(
                    text = scan.brand,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun BookmarkRow(ingredient: Ingredient, onClick: () -> Unit) {
    val tint = riskColor(ingredient.riskTier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(tint, CircleShape)
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(ingredient.eNumber, ingredient.riskTier.label)
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_bookmark),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp)
        )
        Spacer(Modifier.height(14.dp))
        Text(
            text = "Nothing saved yet",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Open any ingredient and tap the bookmark to keep it here. " +
                "Saved entries are re-read each time, so they improve as our " +
                "sources do.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(110.dp))
    }
}
