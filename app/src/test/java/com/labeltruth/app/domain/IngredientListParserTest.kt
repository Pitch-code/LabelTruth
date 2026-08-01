package com.labeltruth.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Every case here corresponds to a defect that actually shipped and was fixed.
 * Parsing real ingredient declarations is where this app is most likely to be
 * quietly wrong, so the bugs are pinned down rather than trusted.
 */
class IngredientListParserTest {

    private fun parse(raw: String) = IngredientListParser.parse(raw)

    private fun assertContains(tokens: List<String>, expected: String) {
        assertTrue(
            "expected a token equal to \"$expected\", got $tokens",
            tokens.any { it.equals(expected, ignoreCase = true) }
        )
    }

    private fun assertAbsent(tokens: List<String>, unwanted: String) {
        assertFalse(
            "did not expect a token equal to \"$unwanted\", got $tokens",
            tokens.any { it.equals(unwanted, ignoreCase = true) }
        )
    }

    // ------------------------------------------------------------- real labels

    @Test
    fun `parses the live Open Food Facts record for Nutella`() {
        val tokens = parse(
            "Sugar, palm oil, HAZELNUTS 13%, skimmed MILK powder 8.7%, low-fat cocoa " +
                "7.4%, emulsifiers: lecithin [SOYA]; vanillin. Gluten free"
        )
        assertContains(tokens, "Sugar")
        assertContains(tokens, "palm oil")
        assertContains(tokens, "HAZELNUTS")
        assertContains(tokens, "skimmed MILK powder")
        assertContains(tokens, "vanillin")
    }

    /** Square brackets were being stripped before sub-lists were extracted. */
    @Test
    fun `extracts ingredients from square brackets, not just round ones`() {
        val tokens = parse("emulsifiers: lecithin [SOYA]")
        assertContains(tokens, "lecithin")
        assertContains(tokens, "SOYA")
    }

    /** The list often ends mid-sentence: "...; vanillin. Gluten free". */
    @Test
    fun `splits on a sentence-ending full stop`() {
        val tokens = parse("vanillin. Salt")
        assertContains(tokens, "vanillin")
        assertContains(tokens, "Salt")
    }

    /** But a full stop between digits is a decimal point, not a separator. */
    @Test
    fun `does not split a decimal number`() {
        val tokens = parse("skimmed MILK powder 8.7%, Salt 1.2%")
        assertContains(tokens, "skimmed MILK powder")
        assertContains(tokens, "Salt")
        assertAbsent(tokens, "7")
        assertAbsent(tokens, "2")
    }

    // --------------------------------------------------- functional class names

    /**
     * Ordered alternation matched the singular "emulsifier" first and left a
     * stray "s", producing the token "s: lecithin".
     */
    @Test
    fun `strips a plural functional class prefix without leaving an s behind`() {
        assertContains(parse("Emulsifiers: Soya Lecithin"), "Soya Lecithin")
        assertContains(parse("Emulsifier: Soya Lecithin"), "Soya Lecithin")
        assertContains(parse("Colours: Curcumin"), "Curcumin")
        assertContains(parse("Preservatives: Potassium Sorbate"), "Potassium Sorbate")
        assertAbsent(parse("Emulsifiers: Soya Lecithin"), "s: Soya Lecithin")
    }

    /** "Colours (E102, E133)" leaves a bare class name once brackets are pulled out. */
    @Test
    fun `drops a token that is only a functional class name`() {
        val tokens = parse("Colours (E102, E133), Salt")
        assertContains(tokens, "E102")
        assertContains(tokens, "E133")
        assertContains(tokens, "Salt")
        assertAbsent(tokens, "Colours")
    }

    @Test
    fun `does not mangle an ingredient whose name merely starts with acid`() {
        // A required separator stops the "acid" class prefix eating "Acidified".
        assertContains(parse("Acidified Milk"), "Acidified Milk")
        // With a separator it is a class prefix and should go.
        assertContains(parse("Acid: Citric Acid"), "Citric Acid")
    }

    // ------------------------------------------------------------- boilerplate

    @Test
    fun `drops food label boilerplate that is not an ingredient`() {
        val tokens = parse(
            "Sugar, Aspartame. Contains a source of phenylalanine. Gluten free. " +
                "May contain traces of nuts. Best before end: see cap."
        )
        assertContains(tokens, "Sugar")
        assertContains(tokens, "Aspartame")
        assertAbsent(tokens, "Contains a source of phenylalanine")
        assertAbsent(tokens, "Gluten free")
        assertAbsent(tokens, "May contain traces of nuts")
    }

    /**
     * A competitor's app displayed "For external use only" and "Keep out of" as
     * if they were ingredients. Hygiene and household labels put usage and
     * safety text right next to the ingredient list.
     */
    @Test
    fun `drops hygiene and household label boilerplate`() {
        val tokens = parse(
            "Aqua, Glycerin, Parfum. Directions: Wet hands, apply and rinse. " +
                "For external use only. Keep out of reach of children. " +
                "Warnings: avoid contact with eyes. Mfg by XYZ Ltd. Net wt 200ml."
        )
        assertContains(tokens, "Aqua")
        assertContains(tokens, "Glycerin")
        assertContains(tokens, "Parfum")
        assertAbsent(tokens, "For external use only")
        assertAbsent(tokens, "Keep out of reach of children")
        assertAbsent(tokens, "Net wt 200ml")
        assertTrue(
            "no usage or warning text should survive, got $tokens",
            tokens.none { it.contains("avoid contact", true) || it.contains("Wet hands", true) }
        )
    }

    // ------------------------------------------------------------------ hygiene

    @Test
    fun `removes a leading Ingredients label`() {
        val tokens = parse("INGREDIENTS: WHEAT FLOUR, SALT")
        assertContains(tokens, "WHEAT FLOUR")
        assertAbsent(tokens, "INGREDIENTS: WHEAT FLOUR")
    }

    @Test
    fun `deduplicates case-insensitively`() {
        val tokens = parse("Salt, salt, SALT")
        assertEquals(1, tokens.count { it.equals("salt", ignoreCase = true) })
    }

    @Test
    fun `returns nothing for blank or meaningless input`() {
        assertTrue(parse("").isEmpty())
        assertTrue(parse("   ").isEmpty())
        assertTrue(parse("...,,,;;;").isEmpty())
    }

    @Test
    fun `handles nested brackets without losing the inner ingredients`() {
        val tokens = parse("Vegetable Oils (Palm, Rapeseed), Salt")
        assertContains(tokens, "Palm")
        assertContains(tokens, "Rapeseed")
        assertContains(tokens, "Salt")
    }

    // ------------------------------------------- real OCR captured on a phone

    /**
     * The verbatim output of photographing a Dettol hand wash bottle on a real
     * device. The camera caught the front of the pack, the pack size and a batch
     * code, and all three were reported to the user as ingredients.
     *
     * The header pattern used to be anchored to the start of the string, so it
     * only stripped "Ingredients:" when the OCR happened to begin with it.
     */
    @Test
    fun `rejects front-of-pack marketing text, pack size and batch codes`() {
        val ocr = """
            ORIGINAL LIQUID HANDWASH GERM OEFENcE Dettol sa e g ¥11/24
            #11/26 ^200 ml Net R09/
            0.50/ml 02: 08 ESSZ452
        """.trimIndent()

        val tokens = parse(ocr)
        assertTrue("expected nothing usable, got $tokens", tokens.isEmpty())
    }

    /**
     * The same capture with the ingredient panel actually in frame. Everything
     * before the header and everything after the "Directions" section must go.
     */
    @Test
    fun `reads only the ingredient section when a header is present`() {
        val ocr = """
            DETTOL ORIGINAL LIQUID HANDWASH
            GERM DEFENCE 200 ml
            Ingredients: Aqua, Sodium Laureth Sulfate, Cocamidopropyl Betaine,
            Glycerin, Parfum, Salicylic Acid, Triclosan
            Directions: Wet hands, apply and rinse thoroughly.
            Net Wt 200 ml  Batch No R09  MFG 11/24
        """.trimIndent()

        val tokens = parse(ocr)
        assertContains(tokens, "Aqua")
        assertContains(tokens, "Sodium Laureth Sulfate")
        assertContains(tokens, "Triclosan")
        assertTrue(
            "front-of-pack text leaked in: $tokens",
            tokens.none { it.contains("DETTOL", ignoreCase = true) }
        )
        assertTrue(
            "post-list sections leaked in: $tokens",
            tokens.none { it.contains("Batch", ignoreCase = true) }
        )
        assertTrue(
            "pack size leaked in: $tokens",
            tokens.none { it.contains("200", ignoreCase = true) }
        )
    }

    @Test
    fun `an ingredient list with no header is still parsed in full`() {
        // Text from a barcode lookup arrives without a header.
        val tokens = parse("Sugar, Cocoa Butter, Skimmed Milk Powder")
        assertEquals(3, tokens.size)
    }

    /**
     * The digit-heavy filter must not eat the tokens that are legitimately
     * mostly numbers. E numbers are the whole point of the additive database,
     * and CI numbers are how cosmetic colourants are declared.
     */
    @Test
    fun `keeps E numbers and colour index numbers despite being mostly digits`() {
        assertContains(parse("Ingredients: Sugar, E330, E150d"), "E330")
        assertContains(parse("Ingredients: Sugar, E330, E150d"), "E150d")
        assertContains(parse("Ingredients: Aqua, CI 77491"), "CI 77491")
    }

    @Test
    fun `rejects addresses and other long non-ingredient lines`() {
        val tokens = parse(
            "Ingredients: Aqua, Glycerin. Marketed by Example Consumer Products " +
                "Limited, Plot 42 Industrial Estate, Mumbai 400001"
        )
        assertContains(tokens, "Aqua")
        assertContains(tokens, "Glycerin")
        assertTrue(
            "an address is not an ingredient: $tokens",
            tokens.none { it.contains("Mumbai", ignoreCase = true) }
        )
    }
}
