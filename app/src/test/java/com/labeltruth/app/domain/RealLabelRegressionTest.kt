package com.labeltruth.app.domain

import com.labeltruth.app.core.TextNormalizer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Regression tests taken from labels photographed on a real phone.
 *
 * Every case here is a defect that shipped and was found by a person holding
 * the app in front of a product, which is the only way most of these surface.
 */
class RealLabelRegressionTest {

    /**
     * Puvi groundnut oil, photographed in India. The printed label is one
     * ingredient followed by a marketing block.
     *
     * The parser previously ran past the end of the declaration and returned
     * five "ingredients": Groundnut Oil, deep frying, and three fragments of
     * the FEATURES sentence. Because only two of five matched the dictionary,
     * the app refused to score the product at all.
     */
    @Test
    fun `stops at the FEATURES heading and keeps only the real ingredient`() {
        val label = """
            INGREDIENTS:
            Groundnut Oil.
            FEATURES: Unrefined and nutrients intact without additives or
            blending. Suitable for daily cooking, deep frying, substitute for
            refined oils.
        """.trimIndent()

        assertEquals(listOf("Groundnut Oil"), IngredientListParser.parse(label))
    }

    /** The same label with the sections run together on one line. */
    @Test
    fun `stops at a heading even when the label has no line breaks`() {
        val label = "Ingredients: Groundnut Oil. FEATURES: Unrefined and " +
            "nutrients intact. Suitable for daily cooking, deep frying."

        assertEquals(listOf("Groundnut Oil"), IngredientListParser.parse(label))
    }

    /**
     * Indian labels put a great deal of text beside the ingredient list. None
     * of it should reach the results.
     */
    @Test
    fun `drops Indian regulatory and marketing sections`() {
        val label = """
            INGREDIENTS: Wheat Flour, Sugar, Palm Oil, Salt, Raising Agent (INS 500(ii)).
            NUTRITIONAL INFORMATION: Energy 450 kcal
            NET WEIGHT: 100 g
            FSSAI Lic No: 10012345678901
            MFG: 01/2026
            BEST BEFORE: 9 months from packaging
            CUSTOMER CARE: care@example.com
        """.trimIndent()

        val parsed = IngredientListParser.parse(label)

        assertTrue("expected wheat flour, got $parsed", parsed.any { it.contains("Wheat Flour") })
        assertTrue("expected the INS additive, got $parsed", parsed.any { it.contains("INS 500") })
        listOf("Energy", "450", "100 g", "10012345678901", "01/2026", "care@example.com")
            .forEach { junk ->
                assertTrue(
                    "\"$junk\" leaked into the ingredient list: $parsed",
                    parsed.none { it.contains(junk) }
                )
            }
    }

    /**
     * Open Food Facts returns only the declaration, with no "Ingredients:"
     * marker. Truncation must not fire on ordinary words in that case.
     */
    @Test
    fun `does not truncate a bare declaration from a barcode lookup`() {
        val parsed = IngredientListParser.parse(
            "Water, Sugar, Citric Acid, Preservative (E211), Flavouring"
        )

        assertEquals(
            listOf("Water", "Sugar", "Citric Acid", "E211", "Flavouring"),
            parsed
        )
    }

    /**
     * FSSAI requires additives to be declared by INS number. INS and E numbers
     * are the same Codex numbering, so INS 330 is E330 and the app must read
     * it. Without this, additives on most Indian packaged food are invisible.
     */
    @Test
    fun `reads Indian INS numbers as their E number equivalent`() {
        assertEquals("E330", TextNormalizer.extractENumber("Acidity Regulator (INS 330)"))
        assertEquals("E322", TextNormalizer.extractENumber("Emulsifier INS 322"))
        assertEquals("E500", TextNormalizer.extractENumber("Raising Agent (INS 500(ii))"))
    }

    /** Four digit additive numbers exist and never matched before. */
    @Test
    fun `reads four digit additive numbers`() {
        assertEquals("E1442", TextNormalizer.extractENumber("Modified Starch (E1442)"))
        assertEquals("E1422", TextNormalizer.extractENumber("Thickener (INS 1422)"))
    }

    /** A word merely starting with "ins" is not an INS number. */
    @Test
    fun `does not mistake ordinary words for INS numbers`() {
        assertEquals(null, TextNormalizer.extractENumber("Instant Coffee"))
        assertEquals(null, TextNormalizer.extractENumber("Insoluble Fibre"))
    }
}


/**
 * The Dettol hand wash that has been driving this round of testing.
 *
 * The declaration below is the real `ingredients_text` Open Beauty Facts holds
 * for barcode 8901396324584, kept verbatim including the trailing "Directions:"
 * block that the printed label runs straight into.
 */
class DettolHandWashTest {

    private val declaration =
        "No TCC & Triclosan Plant derived cleansers Ingredients: Aqua, " +
            "Ammonium Lauryl Sulfate, Sodium Laureth Sulfate, Glycerin, Parfum, " +
            "Glycol Distearate, Cocamide MEA, Glycol Stearate, Propylene Glycol, " +
            "Sodium Chloride, Citric acid, Tetrasodium EDTA, Salicylic Acid, " +
            "Sodium Citrate, Citral, Magnesium Nitrate, " +
            "Methylchloroisothiazolinone, Methylisothiazolinone, CI 11710, " +
            "CI 12085 Directions: Press nozzle nently to net Dettol"

    @Test
    fun `starts at the marketing-prefixed Ingredients header and stops at Directions`() {
        val parsed = IngredientListParser.parse(declaration)

        // The claim before the header is not an ingredient.
        assertTrue(
            "marketing text leaked in: $parsed",
            parsed.none { it.contains("Triclosan") || it.contains("cleansers") }
        )
        // Neither is the usage block after it, OCR typos included.
        assertTrue(
            "usage text leaked in: $parsed",
            parsed.none { it.contains("nozzle") || it.contains("Press") }
        )
        assertEquals("Aqua", parsed.first())
    }

    /** Both preservatives must survive parsing, since they carry EU restrictions. */
    @Test
    fun `keeps the isothiazolinone preservatives`() {
        val parsed = IngredientListParser.parse(declaration)

        listOf("Methylchloroisothiazolinone", "Methylisothiazolinone").forEach { name ->
            assertTrue("$name missing from $parsed", parsed.any { it.equals(name, true) })
        }
    }
}
