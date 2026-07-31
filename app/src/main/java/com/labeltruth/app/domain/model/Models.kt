package com.labeltruth.app.domain.model

/**
 * How much concern an ingredient carries. [penalty] feeds the product score.
 *
 * Tiers are assigned from published regulatory or scientific positions only
 * (EFSA, FDA, WHO/IARC, national bans). They are never invented.
 */
enum class RiskTier(val label: String, val penalty: Int) {
    SAFE("No known concern", 0),
    CAUTION("Minor concern", 4),
    MODERATE("Moderate concern", 12),
    AVOID("Best avoided", 28),
    UNKNOWN("Not yet in our database", 1);

    companion object {
        fun from(raw: String?): RiskTier =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

data class SourceRef(val title: String, val url: String?)

data class Ingredient(
    val id: String,
    val name: String,
    val eNumber: String?,
    val category: String,
    val whatItIs: String,
    val whyUsed: String,
    val riskTier: RiskTier,
    val riskReason: String,
    val allergens: List<String>,
    val dietaryFlags: List<String>,
    val cautionGroups: List<String>,
    val adi: String?,
    val sources: List<SourceRef>
)

/** One token from a product's ingredient list, with whatever we could match it to. */
data class AnalyzedIngredient(
    val rawText: String,
    val matched: Ingredient?,
    val matchConfidence: MatchConfidence
) {
    val displayName: String get() = matched?.name ?: rawText
    val riskTier: RiskTier get() = matched?.riskTier ?: RiskTier.UNKNOWN
}

enum class MatchConfidence { EXACT, SYNONYM, E_NUMBER, FUZZY, NONE }

enum class AlertSeverity { HIGH, MEDIUM }

/** A warning that applies to *this* user, based on their saved profile. */
data class PersonalAlert(
    val ingredientName: String,
    val reason: String,
    val severity: AlertSeverity
)

enum class Grade(val label: String) {
    EXCELLENT("Excellent"),
    GOOD("Good"),
    FAIR("Fair"),
    POOR("Poor"),
    BAD("Avoid");

    companion object {
        fun of(score: Int): Grade = when {
            score >= 80 -> EXCELLENT
            score >= 60 -> GOOD
            score >= 40 -> FAIR
            score >= 20 -> POOR
            else -> BAD
        }
    }
}

data class Analysis(
    val productName: String,
    val brand: String?,
    val barcode: String?,
    val score: Int,
    val grade: Grade,
    val ingredients: List<AnalyzedIngredient>,
    val alerts: List<PersonalAlert>,
    val rawIngredientsText: String
) {
    val unmatchedCount: Int get() = ingredients.count { it.matched == null }
    val coveragePercent: Int
        get() = if (ingredients.isEmpty()) 0
        else ((ingredients.size - unmatchedCount) * 100) / ingredients.size
}

/**
 * Stored on device only. Never uploaded, never synced.
 * This is what turns a generic rating into a personal one.
 */
data class HealthProfile(
    val allergens: Set<String> = emptySet(),
    val diets: Set<String> = emptySet(),
    val conditions: Set<String> = emptySet()
) {
    val isEmpty: Boolean get() = allergens.isEmpty() && diets.isEmpty() && conditions.isEmpty()

    companion object {
        /** The 14 allergens that must be declared on food labels in the EU/UK. */
        val ALL_ALLERGENS = listOf(
            "gluten", "crustaceans", "eggs", "fish", "peanuts", "soybeans", "milk",
            "nuts", "celery", "mustard", "sesame", "sulphites", "lupin", "molluscs"
        )
        val ALL_DIETS = listOf("vegan", "vegetarian", "halal", "kosher", "gluten_free")
        val ALL_CONDITIONS = listOf(
            "pregnancy", "breastfeeding", "children", "hypertension", "diabetes",
            "kidney_disease", "coeliac", "ibs", "migraine", "asthma", "phenylketonuria"
        )
    }
}
