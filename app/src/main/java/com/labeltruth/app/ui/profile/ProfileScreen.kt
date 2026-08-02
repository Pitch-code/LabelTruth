package com.labeltruth.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.labeltruth.app.domain.model.HealthProfile
import com.labeltruth.app.ui.history.TopBar

/**
 * Turns generic ratings into personal ones.
 *
 * The copy here is deliberately explicit that nothing leaves the device, because
 * asking someone to disclose allergies and medical conditions requires earning
 * that trust up front.
 */
@Composable
fun ProfileScreen(
    profile: HealthProfile,
    /** Null when shown as a bottom-bar tab. */
    onBack: (() -> Unit)?,
    onToggleAllergen: (String) -> Unit,
    onToggleDiet: (String) -> Unit,
    onToggleCondition: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        TopBar(title = "Your profile", onBack = onBack)

        // The floating bottom bar plus whatever the system reserves for its own
        // navigation. A fixed value was fine on a three-button phone and left the
        // last row of chips half hidden on a gesture-navigation one.
        val systemBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 104.dp + systemBottom)
        ) {
            Text(
                text = "Tell LabelTruth what matters to you and every scan gets " +
                    "checked against it. This stays on your phone. There is no " +
                    "account and nothing is uploaded.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(24.dp))

            ToggleSection(
                title = "Allergies and intolerances",
                subtitle = "The 14 allergens that must be declared on food labels",
                options = HealthProfile.ALL_ALLERGENS,
                selected = profile.allergens,
                onToggle = onToggleAllergen
            )

            ToggleSection(
                title = "Diet",
                subtitle = "We will flag anything incompatible",
                options = HealthProfile.ALL_DIETS,
                selected = profile.diets,
                onToggle = onToggleDiet
            )

            ToggleSection(
                title = "Intolerances",
                subtitle = "Not the same as an allergy: a digestive or metabolic reaction",
                options = HealthProfile.INTOLERANCES,
                selected = profile.conditions,
                onToggle = onToggleCondition
            )

            ToggleSection(
                title = "Health considerations",
                subtitle = "Used only to show extra cautions that apply to you",
                options = HealthProfile.CONDITIONS,
                selected = profile.conditions,
                onToggle = onToggleCondition
            )
        }
    }
}

@Composable
private fun ToggleSection(
    title: String,
    subtitle: String,
    options: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit
) {
    val chosen = options.count { it in selected }

    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        // Immediate feedback that a tap registered, and a way to see at a glance
        // what you have told the app without re-reading every chip.
        if (chosen > 0) {
            Text(
                text = "$chosen selected",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(14.dp))
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        options.forEach { option ->
            SelectableChip(
                label = HealthProfile.label(option),
                selected = option in selected,
                onClick = { onToggle(option) }
            )
        }
    }
    Spacer(Modifier.height(32.dp))
}

/**
 * A chip whose selected state is obvious at a glance.
 *
 * Previously selected and unselected differed only by two very dark greens, so a
 * screen of fourteen allergens looked like fourteen identical grey pills and
 * there was no way to tell what you had chosen. Selected is now a solid brand
 * fill with dark text on it, which is the strongest contrast available and reads
 * instantly even in a shop under bad lighting.
 *
 * The shape stays fully rounded and the label uses the label type scale rather
 * than body, because this is a control, not prose.
 */
@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(50)

    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .clip(shape)
            .background(if (selected) colors.primary else colors.surfaceContainerHigh)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = if (selected) Color.Transparent else colors.outline,
                shape = shape
            )
            // Clipped before clickable so the ripple follows the rounded shape.
            .clickable(onClick = onClick)
            // Comfortably past the 48dp minimum touch target.
            .heightIn(min = 44.dp)
            .padding(horizontal = 16.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) colors.onPrimary else colors.onSurfaceVariant
        )
    }
}
