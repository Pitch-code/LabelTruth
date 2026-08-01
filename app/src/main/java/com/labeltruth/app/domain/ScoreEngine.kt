package com.labeltruth.app.domain

import com.labeltruth.app.domain.model.AlertSeverity
import com.labeltruth.app.domain.model.AnalyzedIngredient
import com.labeltruth.app.domain.model.HealthProfile
import com.labeltruth.app.domain.model.PersonalAlert
import com.labeltruth.app.domain.model.RiskTier

/**
 * Turns a matched ingredient list into a 0-100 score plus personal alerts.
 *
 * Deliberately simple and explainable: every point deducted traces back to a
 * specific ingredient at a specific tier. A user can always see why. An opaque
 * score would be both less trustworthy and much harder to defend if challenged.
 */
object ScoreEngine {

    /** Ingredients listed first are present in the largest amount, so they weigh more. */
    private fun positionWeight(index: Int, total: Int): Double = when {
        total <= 1 -> 1.0
        index < 3 -> 1.0
        index < total / 2 -> 0.8
        else -> 0.6
    }

    /**
     * Below this share of recognised ingredients we decline to score.
     *
     * A number derived from under half a list describes our dictionary more
     * than it describes the product. The threshold is a judgement call, but
     * having no threshold was not: a photo of the *front* of a bottle scored
     * 97 out of 100 with nothing recognised at all, because unrecognised text
     * cost almost nothing. Saying "we cannot score this" is the honest answer,
     * and it also tells the user to retake the photo.
     */
    const val MIN_RECOGNISED_PERCENT = 50

    /** True when enough of the list was recognised for a score to mean anything. */
    fun isScoreable(ingredients: List<AnalyzedIngredient>): Boolean {
        if (ingredients.isEmpty()) return false
        val recognised = ingredients.count { it.matched != null }
        if (recognised == 0) return false
        return recognised * 100 / ingredients.size >= MIN_RECOGNISED_PERCENT
    }

    /** Returns null when [isScoreable] is false. Callers must handle that. */
    fun score(ingredients: List<AnalyzedIngredient>): Int? {
        if (!isScoreable(ingredients)) return null
        var penalty = 0.0
        ingredients.forEachIndexed { index, item ->
            penalty += item.riskTier.penalty * positionWeight(index, ingredients.size)
        }
        return (100 - penalty).toInt().coerceIn(0, 100)
    }

    fun alerts(
        ingredients: List<AnalyzedIngredient>,
        profile: HealthProfile
    ): List<PersonalAlert> {
        if (profile.isEmpty) return emptyList()

        val alerts = mutableListOf<PersonalAlert>()

        for (item in ingredients) {
            val ingredient = item.matched ?: continue

            // Allergens: highest severity, this is the reason people scan.
            val allergenHits = ingredient.allergens.filter { it in profile.allergens }
            for (hit in allergenHits) {
                alerts += PersonalAlert(
                    ingredientName = ingredient.name,
                    reason = "Contains ${hit.replace('_', ' ')}, which you asked us to flag",
                    severity = AlertSeverity.HIGH
                )
            }

            // Diet conflicts: an ingredient declares which diets it is compatible with.
            for (diet in profile.diets) {
                val incompatible = ingredient.dietaryFlags.isNotEmpty() &&
                    diet !in ingredient.dietaryFlags
                if (incompatible) {
                    alerts += PersonalAlert(
                        ingredientName = ingredient.name,
                        reason = "Not suitable for a ${diet.replace('_', ' ')} diet",
                        severity = AlertSeverity.HIGH
                    )
                }
            }

            // Condition-specific cautions.
            val conditionHits = ingredient.cautionGroups.filter { it in profile.conditions }
            for (hit in conditionHits) {
                alerts += PersonalAlert(
                    ingredientName = ingredient.name,
                    reason = "Extra caution advised for ${hit.replace('_', ' ')}",
                    severity = AlertSeverity.MEDIUM
                )
            }
        }

        return alerts
            .distinctBy { it.ingredientName + it.reason }
            .sortedBy { it.severity.ordinal }
    }

    /**
     * A short, honest headline for the result sheet.
     *
     * The important case is the last one: if nothing carries a published
     * assessment, we must not imply we checked and found nothing. "No concerns
     * found" and "we have no assessments" are very different statements.
     */
    fun summary(ingredients: List<AnalyzedIngredient>): String {
        if (ingredients.isEmpty()) return "No ingredients could be read."

        val recognised = ingredients.count { it.matched != null }

        // Said before anything else, because with nothing recognised we have no
        // basis for any statement about the product at all. The old code fell
        // through to a sentence claiming these ingredients *were* recognised.
        if (recognised == 0) {
            return "We could not match any of this text to an ingredient we know. " +
                "It may not be an ingredient list."
        }

        val worst = ingredients.maxByOrNull { it.riskTier.penalty }?.riskTier ?: RiskTier.UNKNOWN
        val flagged = ingredients.count {
            it.riskTier == RiskTier.MODERATE || it.riskTier == RiskTier.AVOID
        }
        val assessed = ingredients.count {
            it.matched != null && it.riskTier != RiskTier.NOT_ASSESSED
        }
        val unrecognised = ingredients.size - recognised

        // Partial reads get their own sentence. Reporting on half a list as
        // though it were the whole list is the same overclaim in a quieter form.
        if (!isScoreable(ingredients)) {
            return "We only recognised $recognised of ${ingredients.size} items here, " +
                "so we are not scoring this. Try photographing just the ingredient list."
        }

        val tail = if (unrecognised > 0) {
            " $unrecognised item(s) were not recognised."
        } else {
            ""
        }

        return when {
            worst == RiskTier.AVOID -> "$flagged ingredient(s) here are best avoided.$tail"
            worst == RiskTier.MODERATE -> "$flagged ingredient(s) are worth a closer look.$tail"
            worst == RiskTier.CAUTION -> "Nothing serious, a few minor points to note.$tail"
            assessed > 0 -> "No concerns published for any of these ingredients.$tail"
            // Recognised, but we hold no assessment for any of them. Distinct
            // from "no concerns found", which would imply we looked and cleared it.
            else -> "We recognised these ingredients but hold no published " +
                "assessment for any of them yet.$tail"
        }
    }
}
