package com.labeltruth.app.domain

import com.labeltruth.app.domain.model.Nutrition
import com.labeltruth.app.domain.model.RiskTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NutritionAssessorTest {

    /**
     * Milky Mist Cow Ghee, from the nutrition panel of the real product.
     *
     * This is the case that exposed the gap: one ingredient, milk fat, so
     * ingredient analysis had nothing to say and the app reported "no score"
     * while a competitor showed a full breakdown. Everything below comes from
     * the panel, not from an opinion about ghee.
     */
    private val ghee = Nutrition(
        energyKcal100g = 898.0,
        fat100g = 99.8,
        saturatedFat100g = 68.0,
        transFat100g = 1.4,
        carbohydrates100g = 0.0,
        sugars100g = 0.0,
        proteins100g = 0.0,
        sodium100g = 0.006
    )

    @Test
    fun `flags the saturated fat in ghee against the WHO guideline`() {
        val findings = NutritionAssessor.assess(ghee)
        val satFat = findings.single { it.title == "High in saturated fat" }

        assertEquals(RiskTier.MODERATE, satFat.tier)
        assertTrue("should state the amount: ${satFat.detail}", satFat.detail.contains("68"))
        // 2000 kcal * 10% / 9 kcal per gram is about 22 g.
        assertTrue("should state the guideline: ${satFat.detail}", satFat.detail.contains("22"))
        assertTrue(satFat.source.title.contains("WHO"))
    }

    /**
     * The accuracy point. 1.4 g of trans fat in 99.8 g of total fat is 1.4%,
     * which is *below* the FSSAI cap of 2%. Reporting its presence is right;
     * claiming it breaches an Indian regulation would be wrong.
     */
    @Test
    fun `notes trans fat in ghee without claiming it breaks the FSSAI limit`() {
        val findings = NutritionAssessor.assess(ghee)

        assertTrue(findings.any { it.title == "Contains trans fat" })
        assertFalse(
            "1.4% of total fat is under the 2% cap: ${findings.map { it.title }}",
            findings.any { it.title.contains("regulatory limit") }
        )
    }

    /** Above 2% of total fat, the Indian cap is exceeded and that is worth saying. */
    @Test
    fun `flags trans fat above the FSSAI two percent cap`() {
        val findings = NutritionAssessor.assess(
            Nutrition(fat100g = 100.0, transFat100g = 5.0)
        )
        val breach = findings.single { it.title.contains("regulatory limit") }

        assertEquals(RiskTier.AVOID, breach.tier)
        assertTrue(breach.source.title.contains("FSSAI"))
        assertTrue("should show the share: ${breach.detail}", breach.detail.contains("5%"))
        // Naturally occurring trans fat in dairy is not covered by the rule, and
        // the wording must not imply the manufacturer broke the law.
        assertTrue(breach.detail.contains("naturally"))
    }

    /**
     * A missing value means "not declared", never zero. Inventing an absence is
     * how someone avoiding an ingredient gets hurt.
     */
    @Test
    fun `says nothing when nothing was declared`() {
        assertTrue(NutritionAssessor.assess(Nutrition()).isEmpty())
        assertTrue(Nutrition().isEmpty)
    }

    /** Zero really is zero, and deserves no warning. */
    @Test
    fun `does not flag a product that is genuinely low`() {
        val findings = NutritionAssessor.assess(
            Nutrition(
                energyKcal100g = 40.0,
                fat100g = 0.2,
                saturatedFat100g = 0.1,
                sugars100g = 1.0,
                salt100g = 0.05
            )
        )
        assertTrue("expected no findings, got ${findings.map { it.title }}", findings.isEmpty())
    }

    @Test
    fun `flags high sugars and high salt against WHO guidance`() {
        val findings = NutritionAssessor.assess(
            Nutrition(sugars100g = 60.0, salt100g = 6.0)
        )

        val sugars = findings.single { it.title == "High in sugars" }
        assertEquals(RiskTier.MODERATE, sugars.tier)
        // Labels cannot separate free sugars from naturally present ones, and the
        // guideline is about free sugars, so that limitation has to be stated.
        assertTrue(sugars.detail.contains("free sugars"))

        val salt = findings.single { it.title == "High in salt" }
        assertEquals(RiskTier.MODERATE, salt.tier)
        assertTrue(salt.detail.contains("5"))
    }

    /** Half a day's worth is worth mentioning, but not at the same weight. */
    @Test
    fun `scales severity with how much of the daily guidance is present`() {
        val moderate = NutritionAssessor.assess(Nutrition(saturatedFat100g = 30.0))
        val caution = NutritionAssessor.assess(Nutrition(saturatedFat100g = 13.0))

        assertEquals(RiskTier.MODERATE, moderate.single().tier)
        assertEquals(RiskTier.CAUTION, caution.single().tier)
    }

    /** Every finding must carry something the reader can go and check. */
    @Test
    fun `every finding cites a source`() {
        val findings = NutritionAssessor.assess(
            Nutrition(
                fat100g = 100.0, saturatedFat100g = 68.0, transFat100g = 5.0,
                sugars100g = 60.0, salt100g = 6.0
            )
        )

        assertTrue(findings.size >= 4)
        findings.forEach { finding ->
            assertTrue("${finding.title} has no source", finding.source.title.isNotBlank())
            assertTrue("${finding.title} has no url", !finding.source.url.isNullOrBlank())
        }
    }
}
