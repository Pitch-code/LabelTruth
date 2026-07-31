package com.labeltruth.app.domain

import com.labeltruth.app.domain.model.AlertSeverity
import com.labeltruth.app.domain.model.AnalyzedIngredient
import com.labeltruth.app.domain.model.Grade
import com.labeltruth.app.domain.model.HealthProfile
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.domain.model.MatchConfidence
import com.labeltruth.app.domain.model.RiskTier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScoreEngineTest {

    private fun ingredient(
        id: String,
        tier: RiskTier,
        allergens: List<String> = emptyList(),
        diets: List<String> = emptyList(),
        cautions: List<String> = emptyList()
    ) = Ingredient(
        id = id,
        name = id,
        eNumber = null,
        category = "food",
        whatItIs = "",
        whyUsed = "",
        riskTier = tier,
        riskReason = "",
        allergens = allergens,
        dietaryFlags = diets,
        cautionGroups = cautions,
        adi = null,
        sources = emptyList()
    )

    private fun analyzed(
        id: String,
        tier: RiskTier,
        allergens: List<String> = emptyList(),
        diets: List<String> = emptyList(),
        cautions: List<String> = emptyList()
    ) = AnalyzedIngredient(
        rawText = id,
        matched = ingredient(id, tier, allergens, diets, cautions),
        matchConfidence = MatchConfidence.EXACT
    )

    private fun unmatched(text: String) =
        AnalyzedIngredient(text, null, MatchConfidence.NONE)

    // ------------------------------------------------------------------- score

    @Test
    fun `a clean list scores full marks`() {
        val score = ScoreEngine.score(listOf(analyzed("water", RiskTier.SAFE)))
        assertEquals(100, score)
    }

    @Test
    fun `an empty list scores zero rather than a misleading hundred`() {
        assertEquals(0, ScoreEngine.score(emptyList()))
    }

    /**
     * NOT_ASSESSED means "we hold no published assessment", which is not
     * evidence of a concern and must never be scored as one. Most of the
     * dictionary sits in this state, so a penalty here would drag almost every
     * product down for no reason.
     */
    @Test
    fun `NOT_ASSESSED carries no penalty`() {
        val score = ScoreEngine.score(
            List(10) { analyzed("unknown$it", RiskTier.NOT_ASSESSED) }
        )
        assertEquals(100, score)
    }

    @Test
    fun `an unrecognised ingredient costs only a token amount`() {
        val score = ScoreEngine.score(List(5) { unmatched("mystery$it") })
        assertTrue("expected a small deduction, got $score", score in 95..99)
    }

    @Test
    fun `worse tiers deduct more`() {
        val safe = ScoreEngine.score(listOf(analyzed("a", RiskTier.SAFE)))
        val caution = ScoreEngine.score(listOf(analyzed("a", RiskTier.CAUTION)))
        val moderate = ScoreEngine.score(listOf(analyzed("a", RiskTier.MODERATE)))
        val avoid = ScoreEngine.score(listOf(analyzed("a", RiskTier.AVOID)))
        assertTrue("$safe > $caution > $moderate > $avoid",
            safe > caution && caution > moderate && moderate > avoid)
    }

    @Test
    fun `score never leaves the zero to one hundred range`() {
        val awful = ScoreEngine.score(List(40) { analyzed("bad$it", RiskTier.AVOID) })
        assertTrue("expected clamping to 0, got $awful", awful == 0)
    }

    @Test
    fun `ingredients listed first weigh more, since they are present in more quantity`() {
        val first = ScoreEngine.score(
            listOf(analyzed("bad", RiskTier.AVOID)) + List(8) { analyzed("ok$it", RiskTier.SAFE) }
        )
        val last = ScoreEngine.score(
            List(8) { analyzed("ok$it", RiskTier.SAFE) } + listOf(analyzed("bad", RiskTier.AVOID))
        )
        assertTrue("leading position should cost more: first=$first last=$last", first < last)
    }

    // ------------------------------------------------------------------- grade

    @Test
    fun `grade boundaries are exact`() {
        assertEquals(Grade.EXCELLENT, Grade.of(100))
        assertEquals(Grade.EXCELLENT, Grade.of(80))
        assertEquals(Grade.GOOD, Grade.of(79))
        assertEquals(Grade.GOOD, Grade.of(60))
        assertEquals(Grade.FAIR, Grade.of(59))
        assertEquals(Grade.FAIR, Grade.of(40))
        assertEquals(Grade.POOR, Grade.of(39))
        assertEquals(Grade.POOR, Grade.of(20))
        assertEquals(Grade.BAD, Grade.of(19))
        assertEquals(Grade.BAD, Grade.of(0))
    }

    // ----------------------------------------------------------------- summary

    /**
     * The important honesty case. If nothing carries a published assessment we
     * must not imply we checked and found nothing: "no concerns found" and
     * "we have no assessments" are very different claims.
     */
    @Test
    fun `summary does not claim no concerns when nothing was assessed`() {
        val summary = ScoreEngine.summary(
            List(4) { analyzed("x$it", RiskTier.NOT_ASSESSED) }
        )
        assertFalse(
            "must not imply a clean bill of health: \"$summary\"",
            summary.contains("no concern", ignoreCase = true)
        )
        assertTrue(
            "should say we hold no assessment: \"$summary\"",
            summary.contains("no published assessment", ignoreCase = true)
        )
    }

    @Test
    fun `summary reports no concerns only when something really was assessed`() {
        val summary = ScoreEngine.summary(listOf(analyzed("water", RiskTier.SAFE)))
        assertTrue(summary, summary.contains("No concerns published", ignoreCase = true))
    }

    @Test
    fun `summary leads with the worst finding`() {
        val ingredients = listOf(
            analyzed("safe", RiskTier.SAFE),
            analyzed("avoid", RiskTier.AVOID)
        )
        assertTrue(ScoreEngine.summary(ingredients).contains("best avoided", ignoreCase = true))
    }

    @Test
    fun `summary says so plainly when nothing could be read`() {
        assertTrue(ScoreEngine.summary(emptyList()).contains("No ingredients", ignoreCase = true))
    }

    // ------------------------------------------------------------------ alerts

    @Test
    fun `no profile means no personal alerts`() {
        val alerts = ScoreEngine.alerts(
            listOf(analyzed("milk", RiskTier.SAFE, allergens = listOf("milk"))),
            HealthProfile()
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `a declared allergen raises a high severity alert`() {
        val alerts = ScoreEngine.alerts(
            listOf(analyzed("milk", RiskTier.SAFE, allergens = listOf("milk"))),
            HealthProfile(allergens = setOf("milk"))
        )
        assertEquals(1, alerts.size)
        assertEquals(AlertSeverity.HIGH, alerts.first().severity)
    }

    @Test
    fun `an allergen the user did not declare is not flagged`() {
        val alerts = ScoreEngine.alerts(
            listOf(analyzed("milk", RiskTier.SAFE, allergens = listOf("milk"))),
            HealthProfile(allergens = setOf("peanuts"))
        )
        assertTrue(alerts.isEmpty())
    }

    @Test
    fun `a diet conflict is flagged, and a compatible ingredient is not`() {
        val gelatine = analyzed("gelatine", RiskTier.SAFE, diets = listOf("gluten_free"))
        val conflict = ScoreEngine.alerts(listOf(gelatine), HealthProfile(diets = setOf("vegan")))
        assertEquals(1, conflict.size)

        val pectin = analyzed("pectin", RiskTier.SAFE, diets = listOf("vegan", "vegetarian"))
        val fine = ScoreEngine.alerts(listOf(pectin), HealthProfile(diets = setOf("vegan")))
        assertTrue(fine.isEmpty())
    }

    @Test
    fun `an ingredient with no dietary data makes no dietary claim either way`() {
        val unknownDiet = analyzed("mystery", RiskTier.SAFE, diets = emptyList())
        val alerts = ScoreEngine.alerts(listOf(unknownDiet), HealthProfile(diets = setOf("vegan")))
        assertTrue("absence of data must not become a conflict", alerts.isEmpty())
    }

    @Test
    fun `a matching condition raises a medium severity caution`() {
        val alerts = ScoreEngine.alerts(
            listOf(analyzed("caffeine", RiskTier.CAUTION, cautions = listOf("pregnancy"))),
            HealthProfile(conditions = setOf("pregnancy"))
        )
        assertEquals(1, alerts.size)
        assertEquals(AlertSeverity.MEDIUM, alerts.first().severity)
    }

    @Test
    fun `high severity alerts are ordered before medium ones`() {
        val alerts = ScoreEngine.alerts(
            listOf(
                analyzed("caffeine", RiskTier.CAUTION, cautions = listOf("pregnancy")),
                analyzed("milk", RiskTier.SAFE, allergens = listOf("milk"))
            ),
            HealthProfile(allergens = setOf("milk"), conditions = setOf("pregnancy"))
        )
        assertEquals(2, alerts.size)
        assertEquals(AlertSeverity.HIGH, alerts.first().severity)
    }

    @Test
    fun `identical alerts are not repeated`() {
        val alerts = ScoreEngine.alerts(
            listOf(
                analyzed("milk", RiskTier.SAFE, allergens = listOf("milk")),
                analyzed("milk", RiskTier.SAFE, allergens = listOf("milk"))
            ),
            HealthProfile(allergens = setOf("milk"))
        )
        assertEquals(1, alerts.size)
    }
}
