package com.labeltruth.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Viewfinder overlay: four rounded corner brackets plus a sweeping scan line.
 *
 * This reuses the app icon's visual language, and it does real work - it shows
 * the user exactly where to point the camera without needing any instructions.
 */
@Composable
fun ScanFrame(
    modifier: Modifier = Modifier,
    accent: Color,
    active: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "scanSweep")
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200),
            repeatMode = RepeatMode.Reverse
        ),
        label = "sweep"
    )

    Canvas(modifier = modifier) {
        val strokeWidth = 5.dp.toPx()
        val cornerLength = size.minDimension * 0.14f
        val radius = 14.dp.toPx()

        fun corner(x: Float, y: Float, dx: Float, dy: Float): Path = Path().apply {
            moveTo(x, y + dy * cornerLength)
            lineTo(x, y + dy * radius)
            quadraticTo(x, y, x + dx * radius, y)
            lineTo(x + dx * cornerLength, y)
        }

        listOf(
            corner(0f, 0f, 1f, 1f),
            corner(size.width, 0f, -1f, 1f),
            corner(0f, size.height, 1f, -1f),
            corner(size.width, size.height, -1f, -1f)
        ).forEach { path ->
            drawPath(
                path = path,
                color = Color.White.copy(alpha = 0.92f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )
        }

        if (active) {
            val lineY = size.height * (0.08f + 0.84f * sweep)
            drawLine(
                brush = Brush.horizontalGradient(
                    0f to Color.Transparent,
                    0.15f to accent.copy(alpha = 0.85f),
                    0.85f to accent.copy(alpha = 0.85f),
                    1f to Color.Transparent
                ),
                start = Offset(0f, lineY),
                end = Offset(size.width, lineY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}
