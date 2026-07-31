package com.labeltruth.app.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.domain.model.RiskTier
import com.labeltruth.app.ui.components.clickableNoRipple
import com.labeltruth.app.ui.components.riskColor
import com.labeltruth.app.ui.history.TopBar

/**
 * Look up an ingredient directly, without scanning anything.
 *
 * The repository already supported this; there was simply no way to reach it.
 * With thousands of entries, a dictionary you cannot browse is a wasted asset,
 * and "what is E471?" is a question people genuinely type.
 */
@Composable
fun SearchScreen(
    query: String,
    results: List<Ingredient>,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSelect: (Ingredient) -> Unit,
    onLookupBarcode: (String) -> Unit
) {
    // A typed barcode is the fallback for a scratched or badly lit one, and for
    // checking a product before you buy it.
    val typed = query.trim()
    val looksLikeBarcode = typed.length in 6..14 && typed.all { it.isDigit() }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        TopBar(title = "Look up an ingredient", onBack = onBack)

        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
            placeholder = { Text("Ingredient, E-number, or a barcode") },
            singleLine = true,
            shape = RoundedCornerShape(14.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
        )

        Spacer(Modifier.height(12.dp))

        if (looksLikeBarcode) {
            BarcodeLookupRow(barcode = typed, onClick = { onLookupBarcode(typed) })
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }

        when {
            query.isBlank() -> Hint(
                "Search ingredients by name or E-number, or type a product " +
                    "barcode. Ingredient lookup works offline; barcodes need " +
                    "a connection."
            )

            results.isEmpty() && !looksLikeBarcode -> Hint(
                "Nothing matched \"$query\". We would rather say so than guess."
            )

            results.isEmpty() -> Unit

            else -> LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(results, key = { it.id }) { ingredient ->
                    ResultRow(ingredient = ingredient, onClick = { onSelect(ingredient) })
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                    )
                }
            }
        }
    }
}

@Composable
private fun BarcodeLookupRow(barcode: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Look up barcode $barcode",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Searches Open Food Facts and Open Beauty Facts",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ResultRow(ingredient: Ingredient, onClick: () -> Unit) {
    val color = riskColor(ingredient.riskTier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickableNoRipple(onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.size(10.dp).background(color, CircleShape))
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = ingredient.name,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2
            )
            Text(
                text = ingredient.riskTier.label,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
        if (ingredient.riskTier != RiskTier.NOT_ASSESSED && ingredient.sources.isNotEmpty()) {
            SourcedBadge()
        }
    }
}

/**
 * Marks entries whose rating traces to a published source.
 *
 * Competitors badge their AI-written text. We badge the opposite, because the
 * presence of a citation is the thing worth advertising here.
 */
@Composable
fun SourcedBadge() {
    Text(
        text = "SOURCED",
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier
            .background(
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 7.dp, vertical = 3.dp)
    )
}

@Composable
private fun Hint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 36.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}
