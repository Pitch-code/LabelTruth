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

    /**
     * Matches both EU "E numbers" and Indian "INS numbers".
     *
     * Indian labels are required by FSSAI to declare additives by INS number,
     * as in "Acidity Regulator (INS 330)". INS and E numbers share the Codex
     * International Numbering System, so INS 330 and E330 are the same
     * additive, and mapping one onto the other is a renaming rather than an
     * assumption about safety. Without this the app cannot read the additives
     * on most Indian packaged food.
     *
     * Four digits are accepted as well as three: the modified starches and
     * enzymes live in the E1000s, so E1442 previously failed to match at all.
     */
    private val additiveNumberPattern =
        Regex("\\b(?:e|ins)\\s?-?\\s?(\\d{3,4})([a-z]{0,3})\\b", RegexOption.IGNORE_CASE)

    /**
     * Pulls "E211" out of "preservative (e 211)", and "E330" out of
     * "acidity regulator (INS 330)".
     *
     * The canonical form is an uppercase E, the digits, then any letter suffix
     * in lowercase, which is the EU convention: E150d, not E150D. This has to
     * match how the dictionary stores eNumber exactly, because Room compares
     * text with BINARY collation, so "E150D" would not find "E150d".
     */
    fun extractENumber(input: String): String? {
        val match = additiveNumberPattern.find(input) ?: return null
        val digits = match.groupValues[1]
        val suffix = match.groupValues[2].lowercase()
        return "E$digits$suffix"
    }

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
