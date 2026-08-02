package com.labeltruth.app.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.togetherWith
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.labeltruth.app.domain.model.HealthProfile

private data class Step(
    val title: String,
    val subtitle: String,
    val options: List<String> = emptyList(),
    val selected: Set<String> = emptySet(),
    val onToggle: (String) -> Unit = {},
    /** The name step has a text field instead of a row of chips. */
    val isNameStep: Boolean = false
)

/**
 * Asks for the user's profile up front, because otherwise it never gets set.
 *
 * The personalised alerts are the whole reason this app beats a generic score,
 * but they need a profile to work against, and nobody goes hunting through
 * settings to provide one. So we ask once, immediately, and make every step
 * skippable so we never block someone from reaching the scanner.
 */
@Composable
fun OnboardingScreen(
    profile: HealthProfile,
    firstName: String,
    onFirstNameChange: (String) -> Unit,
    onToggleAllergen: (String) -> Unit,
    onToggleIntolerance: (String) -> Unit,
    onToggleDiet: (String) -> Unit,
    onToggleCondition: (String) -> Unit,
    onFinish: () -> Unit
) {
    val steps = listOf(
        Step(
            title = "What should we call you?",
            subtitle = "Optional. Only used to greet you inside the app.",
            isNameStep = true
        ),
        Step(
            title = "Any allergies?",
            subtitle = "These are the 14 allergens that must be declared on food " +
                "labels. We will flag them loudly.",
            options = HealthProfile.ALL_ALLERGENS,
            selected = profile.allergens,
            onToggle = onToggleAllergen
        ),
        Step(
            title = "Any intolerances?",
            subtitle = "Different from an allergy: a digestive or metabolic " +
                "reaction rather than an immune one.",
            options = HealthProfile.INTOLERANCES,
            selected = profile.conditions,
            onToggle = onToggleIntolerance
        ),
        Step(
            title = "Do you follow a diet?",
            subtitle = "We will tell you when something does not fit.",
            options = HealthProfile.ALL_DIETS,
            selected = profile.diets,
            onToggle = onToggleDiet
        ),
        Step(
            title = "Anything else to consider?",
            subtitle = "Used only to surface cautions that actually apply to you.",
            options = HealthProfile.CONDITIONS,
            selected = profile.conditions,
            onToggle = onToggleCondition
        )
    )

    var index by remember { mutableIntStateOf(0) }

    /**
     * The name being typed, held locally and saved only when the step is left.
     *
     * It used to be bound straight to the stored value, with every keystroke
     * launching its own coroutine to write to DataStore. Separate coroutines
     * have no ordering guarantee, so typing "Veera" fired five concurrent
     * writes and the flow emitted them back in whatever order they landed -
     * on a real phone that produced "ErV". Persisting also trimmed the value
     * and fed it back into the field, so a space vanished as it was typed.
     *
     * A text field must be driven by local state for exactly this reason.
     * Seeded once on first composition, which is safe here because onboarding
     * runs once and the stored name is empty at that point.
     */
    var nameDraft by rememberSaveable { mutableStateOf(firstName) }

    // One write per step change instead of one per keystroke.
    val commitName = { onFirstNameChange(nameDraft) }
    val step = steps[index]
    val isLast = index == steps.lastIndex

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            // The name step opens the keyboard, which would otherwise sit on top
            // of the Continue button.
            .imePadding()
            .padding(horizontal = 24.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                steps.indices.forEach { i ->
                    Box(
                        modifier = Modifier
                            .height(6.dp)
                            .width(if (i == index) 26.dp else 6.dp)
                            .background(
                                color = if (i <= index) MaterialTheme.colorScheme.primary
                                else MaterialTheme.colorScheme.surfaceVariant,
                                shape = CircleShape
                            )
                    )
                }
            }
            // Skipping the rest of onboarding should not throw away a name
            // that has already been typed.
            TextButton(onClick = { commitName(); onFinish() }) { Text("Skip") }
        }

        Spacer(Modifier.height(28.dp))

        AnimatedContent(
            targetState = index,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "onboardingStep"
        ) { shown ->
            val current = steps[shown]
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text(
                    text = current.title,
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = current.subtitle,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(24.dp))
                if (current.isNameStep) {
                    OutlinedTextField(
                        value = nameDraft,
                        onValueChange = { nameDraft = it },
                        singleLine = true,
                        placeholder = { Text("First name") },
                        shape = RoundedCornerShape(16.dp),
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Words,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        current.options.forEach { option ->
                            SelectableChip(
                                label = HealthProfile.label(option),
                                selected = option in current.selected,
                                onClick = { current.onToggle(option) }
                            )
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }
        }

        Spacer(Modifier.weight(1f))

        // Deliberately does not say "we do not save your name". We do save it —
        // on this phone. Claiming otherwise would be the same kind of
        // technically-true overclaim this app exists to argue against.
        Text(
            text = "Your name and your answers stay on this phone. There is no " +
                "account and no server to send them to, and every question here " +
                "is optional.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
        )

        Spacer(Modifier.height(14.dp))

        Button(
            onClick = {
                commitName()
                if (isLast) onFinish() else index++
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp),
            shape = RoundedCornerShape(16.dp)
        ) {
            Text(
                text = if (isLast) "Start scanning" else "Continue",
                style = MaterialTheme.typography.labelLarge
            )
        }

        Spacer(Modifier.height(16.dp))
    }
}

@Composable
private fun SelectableChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val background = if (selected) MaterialTheme.colorScheme.primaryContainer
    else MaterialTheme.colorScheme.surfaceVariant
    val foreground = if (selected) MaterialTheme.colorScheme.onPrimaryContainer
    else MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .padding(bottom = 10.dp)
            .background(background, RoundedCornerShape(50))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (selected) {
            Box(
                modifier = Modifier
                    .size(7.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
        }
        Text(text = label, style = MaterialTheme.typography.bodyMedium, color = foreground)
    }
}
