package com.labeltruth.app.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextNormalizerTest {

    // ---------------------------------------------------------------- normalize

    @Test
    fun `normalize lowercases collapses whitespace and strips punctuation`() {
        assertEquals("sodium benzoate", TextNormalizer.normalize("  SODIUM   BENZOATE. "))
        assertEquals("mono and diglycerides", TextNormalizer.normalize("Mono- and Diglycerides"))
    }

    @Test
    fun `normalize strips accents so accented labels still match`() {
        assertEquals("acide citrique", TextNormalizer.normalize("Acide Citrique"))
        assertEquals("beta carotene", TextNormalizer.normalize("bêta-carotène"))
    }

    @Test
    fun `normalize keeps digits, which E-numbers depend on`() {
        assertEquals("e330", TextNormalizer.normalize("E330"))
        assertEquals("ci 77891", TextNormalizer.normalize("CI 77891"))
    }

    // ------------------------------------------------------------ extractENumber

    /**
     * Regression test for a real bug. This used to uppercase the whole suffix,
     * producing "E150D" while the dictionary stores "E150d". Room compares text
     * with BINARY collation, so the E-number lookup silently failed for every
     * letter-suffixed additive and only worked via the synonym fallback.
     */
    @Test
    fun `extractENumber lowercases the letter suffix, matching the EU convention`() {
        assertEquals("E150d", TextNormalizer.extractENumber("Caramel (E150D)"))
        assertEquals("E150d", TextNormalizer.extractENumber("caramel (e150d)"))
        assertEquals("E160a", TextNormalizer.extractENumber("Colour E160A"))
        assertEquals("E101a", TextNormalizer.extractENumber("e101A"))
    }

    @Test
    fun `extractENumber tolerates the spacing and dashes labels actually use`() {
        assertEquals("E211", TextNormalizer.extractENumber("Preservative (E 211)"))
        assertEquals("E211", TextNormalizer.extractENumber("preservative e-211"))
        assertEquals("E621", TextNormalizer.extractENumber("Flavour enhancer: E621"))
    }

    @Test
    fun `extractENumber ignores things that merely look like E-numbers`() {
        assertNull(TextNormalizer.extractENumber("Vitamin E"))
        assertNull(TextNormalizer.extractENumber("Omega 3"))
        // Two digits is not a valid E-number.
        assertNull(TextNormalizer.extractENumber("E12"))
    }

    // ----------------------------------------------------------- levenshtein

    @Test
    fun `levenshtein measures the OCR errors it exists to absorb`() {
        // The canonical example: OCR reads "benzoate" as "benzqate".
        assertEquals(1, TextNormalizer.levenshtein("sodium benzqate", "sodium benzoate", 2))
        assertEquals(0, TextNormalizer.levenshtein("aspartame", "aspartame", 2))
    }

    @Test
    fun `levenshtein bails out early rather than reporting a real distance`() {
        // Beyond tolerance it only has to prove "too far", not how far.
        assertTrue(TextNormalizer.levenshtein("aspartame", "sucralose", 2) > 2)
        assertTrue(TextNormalizer.levenshtein("salt", "monosodium glutamate", 2) > 2)
    }

    @Test
    fun `levenshtein handles empty input without throwing`() {
        assertEquals(0, TextNormalizer.levenshtein("", "", 2))
        assertEquals(3, TextNormalizer.levenshtein("", "abc", 5))
        assertEquals(3, TextNormalizer.levenshtein("abc", "", 5))
    }
}
