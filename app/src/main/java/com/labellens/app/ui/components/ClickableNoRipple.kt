package com.labellens.app.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * Clickable without a ripple, for list rows and links that already communicate
 * their own state. A ripple on every row in a long ingredient list is noisy.
 */
@Composable
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interaction = remember { MutableInteractionSource() }
    return clickable(interactionSource = interaction, indication = null, onClick = onClick)
}
