package com.labellens.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.labellens.app.R
import com.labellens.app.domain.ScoreEngine
import com.labellens.app.domain.model.AlertSeverity
import com.labellens.app.domain.model.Analysis
import com.labellens.app.domain.model.AnalyzedIngredient
import com.labellens.app.domain.model.Ingredient
import com.labellens.app.domain.model.PersonalAlert
import com.labellens.app.domain.model.RiskTier
import com.labellens.app.ui.components.ScoreRing
import com.labellens.app.ui.components.clickableNoRipple
import com.labellens.app.ui.components.riskColor
import com.labellens.app.ui.theme.RiskAvoid
import com.labellens.app.ui.theme.RiskModerate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultSheet(
    analysis: Analysis,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    onIngredientClick: (Ingredient) -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        ResultContent(analysis = analysis, onIngredientClick = onIngredientClick)
    }
}

@Composable
private fun ResultContent(
    analysis: Analysis,
    onIngredientClick: (Ingredient) -> Unit
) {
    // Sort so the things that matter appear first. Users scan top-down and stop early.
    val ordered = analysis.ingredients.sortedByDescending { it.riskTier.penalty }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(
            start = 20.dp, end = 20.dp, bottom = 32.dp
        )
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ScoreRing(score = analysis.score, grade = analysis.grade)
                Spacer(Modifier.size(20.dp))
                Column {
                    Text(
                        text = analysis.productName,
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    analysis.brand?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = ScoreEngine.summary(analysis.ingredients),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        if (analysis.alerts.isNotEmpty()) {
            item {
                Text(
                    text = "For you",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
            }
            items(analysis.alerts) { alert ->
                AlertRow(alert)
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Ingredients (${analysis.ingredients.size})",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "${analysis.coveragePercent}% identified",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        items(ordered) { item ->
            IngredientRow(
                item = item,
                onClick = { item.matched?.let(onIngredientClick) }
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }

        item {
            Spacer(Modifier.height(20.dp))
            DisclaimerFooter()
        }
    }
}

@Composable
private fun AlertRow(alert: PersonalAlert) {
    val tint = if (alert.severity == AlertSeverity.HIGH) RiskAvoid else RiskModerate
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(tint.copy(alpha = 0.12f), RoundedCornerShape(12.dp))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_warning),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = alert.ingredientName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = alert.reason,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun IngredientRow(item: AnalyzedIngredient, onClick: () -> Unit) {
    val color = riskColor(item.riskTier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (item.matched != null) Modifier.clickableNoRipple(onClick) else Modifier
            )
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .background(color, RoundedCornerShape(50))
        )
        Spacer(Modifier.size(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.displayName,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (item.riskTier == RiskTier.AVOID) FontWeight.SemiBold
                else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = if (item.matched == null) "Not in our database yet"
                else item.riskTier.label,
                style = MaterialTheme.typography.bodyMedium,
                color = color
            )
        }
        if (item.matched != null) {
            Text(
                text = "Details",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
fun DisclaimerFooter() {
    Text(
        text = "Informational only, not medical advice. Always check the physical " +
            "packaging for allergens, and speak to a healthcare professional about " +
            "your own situation.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
