package com.labeltruth.app.core

import java.text.Normalizer

/**
 * Turning messy label text into something matchable is the hardest part of a
 * scanner app. OCR gives us things like "SODIUM  BENZQATE (E211 )" and every
 * brand writes the same ingredient a different way.
 */
object TextNormalizer {

    private val diacritics = Regex("\\p{InCombiningDiacriticalMarks}+")
    private val nonAlphaNum = Regex("[^a-z0-9 ]")
    private val multiSpace = Regex("\\s+")

    /** Lowercase, strip accents and punctuation, collapse whitespace. */
    fun normalize(input: String): String {
        val decomposed = Normalizer.normalize(input.lowercase().trim(), Normalizer.Form.NFD)
        return diacritics.replace(decomposed, "")
            .replace('-', ' ')
            .replace('_', ' ')
            .let { nonAlphaNum.replace(it, " ") }
            .let { multiSpace.replace(it, " ") }
            .trim()
    }

    private val eNumberPattern = Regex("\\be\\s?-?\\s?(\\d{3}[a-z]{0,2})\\b", RegexOption.IGNORE_CASE)

    /** Pulls "E211" out of "preservative (e 211)" and similar. Returns e.g. "E211". */
    fun extractENumber(input: String): String? =
        eNumberPattern.find(input)?.groupValues?.get(1)?.let { "E${it.uppercase()}" }

    /** Optimised Levenshtein for short strings, with an early bail-out. */
    fun levenshtein(a: String, b: String, max: Int): Int {
        if (a == b) return 0
        if (kotlin.math.abs(a.length - b.length) > max) return max + 1
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length

        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)

        for (i in 1..a.length) {
            current[0] = i
            var rowMin = current[0]
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(
                    current[j - 1] + 1,
                    previous[j] + 1,
                    previous[j - 1] + cost
                )
                if (current[j] < rowMin) rowMin = current[j]
            }
            // Whole row already worse than our tolerance: stop early.
            if (rowMin > max) return max + 1
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
