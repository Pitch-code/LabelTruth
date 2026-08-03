package com.labeltruth.app.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
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
import com.labeltruth.app.R
import com.labeltruth.app.domain.ScoreEngine
import com.labeltruth.app.domain.model.AlertSeverity
import com.labeltruth.app.domain.model.Analysis
import com.labeltruth.app.domain.model.AnalyzedIngredient
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.domain.model.NovaGroup
import com.labeltruth.app.domain.model.Nutrition
import com.labeltruth.app.domain.model.NutritionFinding
import com.labeltruth.app.domain.model.PersonalAlert
import com.labeltruth.app.domain.model.RiskTier
import com.labeltruth.app.ui.components.ScoreRing
import com.labeltruth.app.ui.components.clickableNoRipple
import com.labeltruth.app.ui.components.riskColor
import com.labeltruth.app.ui.theme.RiskAvoid
import com.labeltruth.app.ui.theme.RiskModerate

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
                // No score rather than a misleading one. A photo that caught the
                // front of a pack instead of the ingredient panel used to show
                // "97, Excellent" here.
                if (analysis.score != null && analysis.grade != null) {
                    ScoreRing(score = analysis.score, grade = analysis.grade)
                } else {
                    NoScoreRing()
                }
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
                    // Pack size and the product's own category. Both were already
                    // being fetched and never shown, and they are the first things
                    // that tell a reader we found the right product.
                    val facts = listOfNotNull(analysis.quantity, analysis.categoryText)
                    if (facts.isNotEmpty()) {
                        Text(
                            text = facts.joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
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

        // Nutrition comes before the ingredient list on purpose. For a
        // single-ingredient product such as ghee, the ingredient list says
        // "milk fat" and nothing else, while the panel carries the whole answer.
        if (analysis.nutritionFindings.isNotEmpty()) {
            item {
                Text(
                    text = "What the nutrition panel shows",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(8.dp))
            }
            items(analysis.nutritionFindings) { finding ->
                NutritionFindingRow(finding)
                Spacer(Modifier.height(8.dp))
            }
            item { Spacer(Modifier.height(12.dp)) }
        }

        if (!analysis.nutrition.isEmpty) {
            item {
                NutritionPanel(analysis.nutrition)
                Spacer(Modifier.height(20.dp))
            }
        }

        analysis.novaGroup?.let { nova ->
            item {
                NovaRow(nova)
                Spacer(Modifier.height(20.dp))
            }
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
                    text = "${analysis.coveragePercent}% identified · " +
                        "${analysis.assessedCount} assessed",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.height(4.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        }

        // Grouped under band headers rather than one long list. A flat list of
        // twenty ingredients is unreadable; the bands let someone stop reading
        // as soon as they have the answer they came for.
        val bands = listOf(
            RiskTier.AVOID to "Best avoided",
            RiskTier.MODERATE to "Moderate concern",
            RiskTier.CAUTION to "Minor concern",
            RiskTier.SAFE to "No known concern",
            RiskTier.NOT_ASSESSED to "No published assessment",
            RiskTier.UNKNOWN to "Not recognised"
        )

        bands.forEach { (tier, heading) ->
            val group = ordered.filter { it.riskTier == tier }
            if (group.isEmpty()) return@forEach

            item {
                BandHeader(heading = heading, count = group.size, tier = tier)
            }
            items(group) { item ->
                IngredientRow(
                    item = item,
                    onClick = { item.matched?.let(onIngredientClick) }
                )
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            }
        }

        item {
            Spacer(Modifier.height(20.dp))
            SourceNote(hasBarcode = analysis.barcode != null)
            Spacer(Modifier.height(16.dp))
            DisclaimerFooter()
        }
    }
}

/**
 * Stands in for the score ring when we recognised too little to score.
 *
 * Kept the same size and shape as the real ring so the layout does not jump,
 * but visibly empty and neutrally coloured, because it must not read as a
 * verdict of any kind.
 */
@Composable
private fun NoScoreRing() {
    Box(
        modifier = Modifier
            .size(132.dp)
            .background(
                MaterialTheme.colorScheme.surfaceVariant,
                RoundedCornerShape(percent = 50)
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "–",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "No score",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
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
            .padding(vertical = 14.dp)
            // Lets the risk bar match the row's own height, so it still lines up
            // when a long INCI name wraps onto a second line.
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // A full-height bar rather than a small dot. The tier is the single most
        // useful thing in the row, and a 10dp dot was easy to miss while
        // scanning a list of twenty ingredients in a shop.
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color, RoundedCornerShape(2.dp))
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
private fun BandHeader(heading: String, count: Int, tier: RiskTier) {
    val color = riskColor(tier)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 20.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(width = 4.dp, height = 16.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = heading.uppercase(),
            style = MaterialTheme.typography.labelLarge,
            color = color
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = "$count",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * Where this result came from, stated on the result itself rather than buried
 * in an About screen.
 *
 * For an app called LabelTruth, being less transparent about our own data than
 * a competitor is not defensible.
 */
@Composable
private fun SourceNote(hasBarcode: Boolean) {
    val body = if (hasBarcode) {
        "Product name and ingredient list came from Open Food Facts, matched to " +
            "the barcode you scanned. It is community-maintained, so it can be " +
            "incomplete or out of date. Check the packaging if something looks wrong.\n\n" +
            "Ingredient assessments come from EFSA, WHO/IARC, FDA and EU " +
            "regulations, cited on each ingredient."
    } else {
        "Ingredients were read from the label on your device, so accuracy depends " +
            "on the photo. Assessments come from EFSA, WHO/IARC, FDA and EU " +
            "regulations, cited on each ingredient."
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(14.dp))
            .padding(16.dp)
    ) {
        Text(
            text = "Where this came from",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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


/**
 * One nutrition finding: the amount, the published limit, and where it came from.
 *
 * The source is shown on the row rather than hidden behind a tap, because a
 * statement like "68 g per 100 g against WHO's 22 g a day" is only worth
 * anything if the reader can see who said 22.
 */
@Composable
private fun NutritionFindingRow(finding: NutritionFinding) {
    val tint = riskColor(finding.tier)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(tint, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = finding.title,
                style = MaterialTheme.typography.titleSmall,
                color = tint
            )
            Text(
                text = finding.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = finding.source.title,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * The declared panel, per 100 g or 100 ml.
 *
 * Only declared values appear. A nutrient nobody entered is omitted rather than
 * shown as zero, because "not declared" and "none" are different claims.
 */
@Composable
private fun NutritionPanel(nutrition: Nutrition) {
    val rows = buildList {
        nutrition.energyKcal100g?.let { add("Energy" to "${trim(it)} kcal") }
        nutrition.fat100g?.let { add("Fat" to "${trim(it)} g") }
        nutrition.saturatedFat100g?.let { add("  of which saturated" to "${trim(it)} g") }
        nutrition.transFat100g?.let { add("  of which trans" to "${trim(it)} g") }
        nutrition.carbohydrates100g?.let { add("Carbohydrate" to "${trim(it)} g") }
        nutrition.sugars100g?.let { add("  of which sugars" to "${trim(it)} g") }
        nutrition.fibre100g?.let { add("Fibre" to "${trim(it)} g") }
        nutrition.proteins100g?.let { add("Protein" to "${trim(it)} g") }
        nutrition.salt100g?.let { add("Salt" to "${trim(it)} g") }
        nutrition.sodium100g?.let { add("Sodium" to "${trim(it)} g") }
    }
    if (rows.isEmpty()) return

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Nutrition, per 100 g or ml",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
        rows.forEach { (label, value) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 9.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (label.startsWith("  ")) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    }
                )
                Text(
                    text = value,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Declared values from Open Food Facts. Blank nutrients were " +
                "not recorded, which is not the same as containing none.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * How heavily the food is processed, on the published NOVA scale.
 *
 * Open Food Facts derives this from the ingredient list using the NOVA
 * classification, so it is a citable derivation rather than our opinion. Shown
 * without a verdict attached: group 4 describes how a food was made, and is not
 * by itself a finding about safety.
 */
@Composable
private fun NovaRow(nova: NovaGroup) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Processing",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = nova.label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "${nova.summary}, as classified by Open Food Facts from the " +
                "ingredient list.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** Drops a pointless trailing ".0" so a panel reads like a label. */
private fun trim(value: Double): String {
    val rounded = Math.round(value * 10) / 10.0
    return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
}
