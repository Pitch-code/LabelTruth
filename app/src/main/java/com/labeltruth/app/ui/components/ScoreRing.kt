package com.labeltruth.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.labeltruth.app.domain.model.Grade
import com.labeltruth.app.ui.theme.RiskAvoid
import com.labeltruth.app.ui.theme.RiskCaution
import com.labeltruth.app.ui.theme.RiskModerate
import com.labeltruth.app.ui.theme.RiskSafe

fun gradeColor(grade: Grade): Color = when (grade) {
    Grade.EXCELLENT -> RiskSafe
    Grade.GOOD -> RiskCaution
    Grade.FAIR -> RiskModerate
    Grade.POOR -> Color(0xFFF97316)
    Grade.BAD -> RiskAvoid
}

/**
 * The single element that has to communicate a verdict in under a second.
 * Animated fill gives the user a moment of anticipation, then the answer.
 */
@Composable
fun ScoreRing(
    score: Int,
    grade: Grade,
    modifier: Modifier = Modifier,
    diameter: Dp = 132.dp,
    strokeWidth: Dp = 12.dp
) {
    val target = (score.coerceIn(0, 100)) / 100f
    val progress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 750),
        label = "scoreRing"
    )
    val color = gradeColor(grade)
    val trackColor = MaterialTheme.colorScheme.surfaceVariant

    Box(modifier = modifier.size(diameter), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(diameter)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2f
            val arcSize = Size(size.width - stroke, size.height - stroke)

            drawArc(
                color = trackColor,
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * progress,
                useCenter = false,
                topLeft = Offset(inset, inset),
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "$score",
                fontSize = 38.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = grade.label,
                style = MaterialTheme.typography.labelLarge,
                color = color
            )
        }
    }
}
