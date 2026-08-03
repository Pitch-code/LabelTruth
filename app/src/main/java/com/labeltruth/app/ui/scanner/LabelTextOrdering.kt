package com.labeltruth.app.ui.scanner

import android.graphics.Rect
import com.google.mlkit.vision.text.Text

/**
 * Fraction of the median line height within which two lines count as the same
 * row. Generous enough for a hand-held, slightly rotated photo, tight enough
 * not to merge adjacent lines of a dense ingredient list.
 */
private const val ROW_TOLERANCE_FRACTION = 0.6

/**
 * Rebuilds recognised text in the order a person would read it.
 *
 * [Text.getText] concatenates blocks in the order the recogniser happened to
 * emit them, which is not reading order. On a groundnut oil bottle that fused
 * two unrelated lines into "FEATURES: nutrients intact without Suitable for
 * daily cooking", so the parser saw a sentence that was never printed and
 * produced ingredients that do not exist.
 *
 * Lines rather than blocks are the unit here, because a block can span columns.
 * Lines are grouped into rows using a fraction of the median line height as the
 * tolerance, so gently tilted labels still group correctly, then ordered left to
 * right within each row.
 *
 * Shared by both capture paths: the live camera frame and the flattened image
 * returned by the document scanner. They need identical treatment, and having
 * two copies of this would guarantee they eventually drifted apart.
 */
internal fun Text.inVisualOrder(): String {
    val lines = textBlocks.flatMap { it.lines }
    // Without geometry there is nothing to sort by, so keep the original.
    if (lines.isEmpty() || lines.any { it.boundingBox == null }) return text

    val boxes = lines.mapNotNull { line -> line.boundingBox?.let { line to it } }
    val heights = boxes.map { it.second.height() }.sorted()
    val medianHeight = heights[heights.size / 2].coerceAtLeast(1)
    val rowTolerance = (medianHeight * ROW_TOLERANCE_FRACTION).toInt().coerceAtLeast(1)

    val byVertical = boxes.sortedBy { it.second.centerY() }
    val rows = mutableListOf<MutableList<Pair<Text.Line, Rect>>>()
    for (entry in byVertical) {
        val lastRow = rows.lastOrNull()
        val sameRow = lastRow != null &&
            entry.second.centerY() - lastRow.last().second.centerY() <= rowTolerance
        if (sameRow) lastRow.add(entry) else rows.add(mutableListOf(entry))
    }

    return rows.joinToString("\n") { row ->
        row.sortedBy { it.second.left }.joinToString(" ") { it.first.text.trim() }
    }
}
