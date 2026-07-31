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

    fun score(ingredients: List<AnalyzedIngredient>): Int {
        if (ingredients.isEmpty()) return 0
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

        val worst = ingredients.maxByOrNull { it.riskTier.penalty }?.riskTier ?: RiskTier.UNKNOWN
        val flagged = ingredients.count {
            it.riskTier == RiskTier.MODERATE || it.riskTier == RiskTier.AVOID
        }
        val assessed = ingredients.count {
            it.matched != null && it.riskTier != RiskTier.NOT_ASSESSED
        }

        return when {
            worst == RiskTier.AVOID -> "$flagged ingredient(s) here are best avoided."
            worst == RiskTier.MODERATE -> "$flagged ingredient(s) are worth a closer look."
            worst == RiskTier.CAUTION -> "Nothing serious, a few minor points to note."
            assessed > 0 -> "No concerns published for any of these ingredients."
            else -> "These ingredients are recognised, but we hold no published " +
                "assessment for them yet."
        }
    }
}
