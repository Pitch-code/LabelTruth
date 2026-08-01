package com.labeltruth.app.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
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

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                // Clears the floating bottom bar.
                .padding(bottom = 110.dp)
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
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface
    )
    Text(
        text = subtitle,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Spacer(Modifier.height(12.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            SelectableChip(
                label = HealthProfile.label(option),
                selected = option in selected,
                onClick = { onToggle(option) }
            )
        }
    }
    Spacer(Modifier.height(28.dp))
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val fg = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Text(
        text = label,
        style = MaterialTheme.typography.bodyMedium,
        color = fg,
        modifier = Modifier
            .padding(bottom = 8.dp)
            .background(bg, RoundedCornerShape(50))
            .border(
                width = 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp)
    )
}
