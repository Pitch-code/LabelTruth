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

    /**
     * We recognise the ingredient, and may know its allergens and dietary
     * suitability, but no published safety assessment is attached to it.
     *
     * Deliberately distinct from [UNKNOWN]. Saying "we have no assessment" is
     * honest; guessing would not be. It carries no penalty, because an absence
     * of published concern is not evidence of a concern.
     */
    NOT_ASSESSED("No published assessment", 0),

    /** Not recognised at all. We could not identify what this ingredient is. */
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

    /** Recognised, whether or not we hold a safety assessment for it. */
    val coveragePercent: Int
        get() = if (ingredients.isEmpty()) 0
        else ((ingredients.size - unmatchedCount) * 100) / ingredients.size

    /** Recognised *and* carrying a published assessment. */
    val assessedCount: Int
        get() = ingredients.count {
            it.matched != null && it.riskTier != RiskTier.NOT_ASSESSED
        }
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

        /**
         * Intolerances are not allergies. An allergy is an immune response; an
         * intolerance is a digestive or metabolic one. Users think of them
         * separately, so they are asked separately.
         *
         * Only intolerances we can actually act on are listed. Histamine and
         * amine sensitivity are deliberately omitted: the trigger foods are too
         * poorly defined in the label data to flag without guessing.
         *
         * Stored in the same set as [CONDITIONS], because both are matched
         * against an ingredient's cautionGroups. Splitting them is presentation
         * only, which is why no database change was needed.
         */
        val INTOLERANCES = listOf(
            "lactose", "fructose", "fodmap", "caffeine_sensitivity",
            "alcohol_sensitivity", "salicylate_sensitivity"
        )

        val CONDITIONS = listOf(
            "pregnancy", "breastfeeding", "children", "hypertension", "diabetes",
            "kidney_disease", "coeliac", "ibs", "migraine", "asthma", "phenylketonuria"
        )

        /** Everything persisted to the conditions set. */
        val ALL_CONDITIONS = INTOLERANCES + CONDITIONS

        /** Human-readable labels, since the raw keys are not all self-explanatory. */
        val LABELS = mapOf(
            "fodmap" to "FODMAPs",
            "lactose" to "Lactose",
            "fructose" to "Fructose",
            "caffeine_sensitivity" to "Caffeine",
            "alcohol_sensitivity" to "Alcohol",
            "salicylate_sensitivity" to "Salicylates",
            "phenylketonuria" to "PKU (phenylketonuria)",
            "kidney_disease" to "Kidney disease",
            "ibs" to "IBS",
            "coeliac" to "Coeliac disease",
            "gluten_free" to "Gluten free",
            "soybeans" to "Soya",
            "nuts" to "Tree nuts",
            "sulphites" to "Sulphites"
        )

        fun label(key: String): String =
            LABELS[key] ?: key.replace('_', ' ').replaceFirstChar { it.uppercase() }
    }
}
