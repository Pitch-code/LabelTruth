package com.labeltruth.app.ui.home

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labeltruth.app.R
import com.labeltruth.app.data.local.ScanEntity
import com.labeltruth.app.domain.model.Grade
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.ui.components.gradeColor
import com.labeltruth.app.ui.components.riskColor
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * The app's front door.
 *
 * Deliberately absent, and recorded here so it does not creep back in: there is
 * no remaining-scans counter, no plan badge and no upgrade prompt. Scanning is
 * the core function of an app about trust, and metering it teaches distrust.
 * Nothing on this screen advertises a purchase, which is also the only honest
 * option while no purchase exists.
 */
@Composable
fun HomeScreen(
    greetingName: String,
    scanCount: Int,
    distribution: Map<Grade, Int>,
    recent: List<ScanEntity>,
    spotlight: Ingredient?,
    onOpenScanner: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenScan: (Long) -> Unit,
    onOpenIngredient: (Ingredient) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 20.dp)
    ) {
        Spacer(Modifier.height(12.dp))
        Greeting(greetingName)

        Spacer(Modifier.height(18.dp))
        SearchEntry(onOpenSearch)

        Spacer(Modifier.height(16.dp))
        ScanCard(onOpenScanner)

        Spacer(Modifier.height(24.dp))
        ScanTally(scanCount = scanCount, distribution = distribution)

        if (spotlight != null) {
            Spacer(Modifier.height(24.dp))
            SectionHeader("Ingredient spotlight")
            Spacer(Modifier.height(10.dp))
            SpotlightCard(spotlight, onClick = { onOpenIngredient(spotlight) })
        }

        Spacer(Modifier.height(24.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            SectionHeader("Recent scans")
            if (recent.isNotEmpty()) {
                Text(
                    text = "View all",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.clickable(onClick = onOpenHistory)
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        if (recent.isEmpty()) {
            EmptyRecent()
        } else {
            recent.forEach { scan ->
                RecentRow(scan = scan, onClick = { onOpenScan(scan.id) })
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)
                )
            }
        }

        // Clears the bottom navigation bar, which floats over this content.
        Spacer(Modifier.height(110.dp))
    }
}

@Composable
private fun Greeting(name: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = name.take(1).uppercase().ifBlank { "L" },
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.size(12.dp))
        Column {
            Text(
                text = if (name.isBlank()) "Welcome" else "Hello, $name",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onBackground
            )
            Text(
                text = "Know what is really in it",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/**
 * Looks like a search field but is a button.
 *
 * Search has its own screen with debouncing and a keyboard-aware layout, and
 * duplicating that here would mean two code paths for one feature.
 */
@Composable
private fun SearchEntry(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_search),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = "Search an ingredient or barcode",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ScanCard(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Scan a product",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onPrimary
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = "Barcode or ingredient label. Every rating cites its source.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f)
            )
        }
        Spacer(Modifier.size(12.dp))
        Box(
            modifier = Modifier
                .size(52.dp)
                .background(Color.White.copy(alpha = 0.18f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_scan),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(26.dp)
            )
        }
    }
}

/**
 * Counts of scans by score band.
 *
 * Bands, not ingredient tallies. Per-ingredient verdicts are not stored, so
 * "12 safe ingredients" is a number we could not stand behind; the score of
 * each scan is something we did record.
 */
@Composable
private fun ScanTally(scanCount: Int, distribution: Map<Grade, Int>) {
    Text(
        text = if (scanCount == 1) "1 product scanned" else "$scanCount products scanned",
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground
    )
    Spacer(Modifier.height(12.dp))

    if (scanCount == 0) {
        Text(
            text = "Nothing scanned yet. Your results will appear here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(8.dp)
            .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(50))
    ) {
        distribution.forEach { (grade, count) ->
            if (count > 0) {
                Box(
                    modifier = Modifier
                        .weight(count.toFloat())
                        .fillMaxSize()
                        .background(gradeColor(grade))
                )
            }
        }
    }

    Spacer(Modifier.height(12.dp))
    Row(modifier = Modifier.fillMaxWidth()) {
        distribution.entries.filter { it.value > 0 }.forEach { (grade, count) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(end = 14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(gradeColor(grade), CircleShape)
                )
                Spacer(Modifier.size(6.dp))
                Text(
                    text = "$count ${grade.label}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun SpotlightCard(ingredient: Ingredient, onClick: () -> Unit) {
    val tint = riskColor(ingredient.riskTier)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(tint, CircleShape)
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = ingredient.riskTier.label,
                style = MaterialTheme.typography.labelLarge,
                color = tint
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = ingredient.name,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = ingredient.whatItIs,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(Modifier.height(10.dp))
        Text(
            // Every spotlight entry carries a citation by construction: the
            // query that selects it requires one.
            text = "Sourced · tap to read the evidence",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

@Composable
private fun RecentRow(scan: ScanEntity, onClick: () -> Unit) {
    val grade = Grade.of(scan.score)
    val formatter = remember { SimpleDateFormat("d MMM, HH:mm", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .background(gradeColor(grade).copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = scan.score.toString(),
                style = MaterialTheme.typography.labelLarge,
                color = gradeColor(grade)
            )
        }
        Spacer(Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scan.productName,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = listOfNotNull(scan.brand, formatter.format(scan.timestamp))
                    .joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun EmptyRecent() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(18.dp))
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            painter = painterResource(R.drawable.ic_scan),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(32.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text(
            text = "Start scanning",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = "Products you check will line up here.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
