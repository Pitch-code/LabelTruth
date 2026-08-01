package com.labeltruth.app.ui.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.labeltruth.app.R
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.domain.model.RiskTier
import com.labeltruth.app.ui.components.clickableNoRipple
import com.labeltruth.app.ui.components.riskColor
import com.labeltruth.app.ui.result.DisclaimerFooter

/**
 * Everything we know about one ingredient, with its sources visible.
 *
 * Showing the source next to every claim is what separates a trustworthy health
 * app from an app that just asserts things.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IngredientDetailSheet(
    ingredient: Ingredient,
    sheetState: SheetState,
    isBookmarked: Boolean,
    onToggleBookmark: () -> Unit,
    onDismiss: () -> Unit
) {
    val uriHandler = LocalUriHandler.current

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(start = 20.dp, end = 20.dp, bottom = 32.dp)
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = ingredient.name,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    ingredient.eNumber?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onToggleBookmark) {
                    Icon(
                        painter = painterResource(
                            if (isBookmarked) R.drawable.ic_bookmark_filled
                            else R.drawable.ic_bookmark
                        ),
                        contentDescription = if (isBookmarked) {
                            "Remove from saved ingredients"
                        } else {
                            "Save this ingredient"
                        },
                        tint = if (isBookmarked) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            RiskBanner(ingredient)

            Spacer(Modifier.height(20.dp))

            Section("What it is", ingredient.whatItIs)
            Section("Why it is used", ingredient.whyUsed)

            if (ingredient.riskReason.isNotBlank()) {
                Section("Why this rating", ingredient.riskReason)
            }

            // Being explicit here is the point of the whole app. An absence of
            // published concern is not the same as a clean bill of health, and
            // pretending otherwise is what the competition does.
            if (ingredient.riskTier == RiskTier.NOT_ASSESSED) {
                Section(
                    title = "Why there is no rating",
                    body = "We recognise this ingredient, but we have not found a " +
                        "published safety assessment we can cite for it. Rather than " +
                        "guess, we say so. Any allergen and dietary information below " +
                        "still applies."
                )
            }
            ingredient.adi?.let {
                Section("Acceptable daily intake", it)
            }

            if (ingredient.allergens.isNotEmpty()) {
                ChipSection("Allergens", ingredient.allergens.map { it.humanize() })
            }
            if (ingredient.cautionGroups.isNotEmpty()) {
                ChipSection(
                    title = "Extra caution for",
                    values = ingredient.cautionGroups.map { it.humanize() }
                )
            }
            if (ingredient.dietaryFlags.isNotEmpty()) {
                ChipSection(
                    title = "Suitable for",
                    values = ingredient.dietaryFlags.map { it.humanize() }
                )
            }

            if (ingredient.sources.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "Sources",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(6.dp))
                ingredient.sources.forEach { source ->
                    val clickable = source.url != null
                    Text(
                        text = source.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (clickable) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant,
                        textDecoration = if (clickable) TextDecoration.Underline else null,
                        modifier = Modifier
                            .padding(vertical = 4.dp)
                            .then(
                                if (clickable) Modifier.clickableNoRipple {
                                    runCatching { uriHandler.openUri(source.url!!) }
                                } else Modifier
                            )
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            DisclaimerFooter()
        }
    }
}

@Composable
private fun RiskBanner(ingredient: Ingredient) {
    val color = riskColor(ingredient.riskTier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(color.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
            .padding(14.dp)
    ) {
        Text(
            text = ingredient.riskTier.label,
            style = MaterialTheme.typography.titleMedium,
            color = color
        )
    }
}

@Composable
private fun Section(title: String, body: String) {
    if (body.isBlank()) return
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = body,
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(18.dp))
}

@Composable
private fun ChipSection(title: String, values: List<String>) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(Modifier.height(8.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        values.forEach { value ->
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(bottom = 8.dp)
                    .background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(50)
                    )
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
    Spacer(Modifier.height(18.dp))
}

private fun String.humanize(): String =
    replace('_', ' ').replaceFirstChar { it.uppercase() }
