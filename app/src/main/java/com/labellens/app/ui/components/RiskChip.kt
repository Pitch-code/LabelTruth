package com.labellens.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.labellens.app.domain.model.RiskTier
import com.labellens.app.ui.theme.RiskAvoid
import com.labellens.app.ui.theme.RiskCaution
import com.labellens.app.ui.theme.RiskModerate
import com.labellens.app.ui.theme.RiskSafe
import com.labellens.app.ui.theme.RiskUnknown

fun riskColor(tier: RiskTier): Color = when (tier) {
    RiskTier.SAFE -> RiskSafe
    RiskTier.CAUTION -> RiskCaution
    RiskTier.MODERATE -> RiskModerate
    RiskTier.AVOID -> RiskAvoid
    RiskTier.UNKNOWN -> RiskUnknown
}

/**
 * One ingredient, colour coded. A dot carries the risk level rather than tinting
 * the whole chip, which keeps a long list readable instead of alarming.
 */
@Composable
fun RiskChip(
    label: String,
    tier: RiskTier,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null
) {
    val color = riskColor(tier)
    Row(
        modifier = modifier
            .background(
                color = MaterialTheme.colorScheme.surfaceVariant,
                shape = RoundedCornerShape(50)
            )
            .border(
                width = 1.dp,
                color = color.copy(alpha = 0.45f),
                shape = RoundedCornerShape(50)
            )
            .then(if (onClick != null) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(9.dp)
                .background(color, CircleShape)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
