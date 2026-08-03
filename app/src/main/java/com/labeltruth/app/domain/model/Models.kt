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

    /**
     * Not recognised at all. We could not identify what this ingredient is.
     *
     * Carries no penalty, and that is a deliberate correction. It used to cost
     * a point, which meant a product's score was partly a measure of how
     * incomplete *our* dictionary is. Every point deducted has to trace to a
     * published finding, and "we have never heard of this" is not a finding.
     *
     * Unrecognised text cannot quietly inflate a score either, because
     * [ScoreEngine] refuses to produce a score at all when too little of the
     * list was recognised.
     */
    UNKNOWN("Not yet in our database", 0);

    companion object {
        fun from(raw: String?): RiskTier =
            entries.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: UNKNOWN
    }
}

data class SourceRef(val title: String, val url: String?)

/**
 * A nutrition panel, per 100 g or 100 ml.
 *
 * Every field is nullable because labels declare different subsets and Open Food
 * Facts records whatever a contributor entered. A missing value must read as
 * "not declared", never as zero: claiming a product contains no trans fat
 * because nobody typed the number in would be exactly the kind of confident
 * wrongness this app exists to avoid.
 */
data class Nutrition(
    val energyKcal100g: Double? = null,
    val fat100g: Double? = null,
    val saturatedFat100g: Double? = null,
    val transFat100g: Double? = null,
    val carbohydrates100g: Double? = null,
    val sugars100g: Double? = null,
    val fibre100g: Double? = null,
    val proteins100g: Double? = null,
    val salt100g: Double? = null,
    val sodium100g: Double? = null
) {
    /** True when there is nothing worth showing a panel for. */
    val isEmpty: Boolean
        get() = listOf(
            energyKcal100g, fat100g, saturatedFat100g, transFat100g,
            carbohydrates100g, sugars100g, fibre100g, proteins100g,
            salt100g, sodium100g
        ).all { it == null }
}

/**
 * Something notable in the nutrition panel, with the published limit it was
 * measured against.
 *
 * Reports the amount and the guidance, never a prediction about the reader.
 * "68 g of saturated fat per 100 g, and WHO suggests about 22 g a day" is
 * checkable. "This will raise your cholesterol" is not ours to say.
 */
data class NutritionFinding(
    val title: String,
    val detail: String,
    val tier: RiskTier,
    val source: SourceRef
)

/**
 * How heavily a food has been industrially processed, on the NOVA scale.
 *
 * Computed by Open Food Facts from the ingredient list using the published NOVA
 * classification, so it is a citable derivation rather than an opinion.
 */
enum class NovaGroup(val label: String, val summary: String) {
    UNPROCESSED("Unprocessed or minimally processed", "Group 1 on the NOVA scale"),
    CULINARY_INGREDIENT("Processed culinary ingredient", "Group 2 on the NOVA scale"),
    PROCESSED("Processed food", "Group 3 on the NOVA scale"),
    ULTRA_PROCESSED("Ultra-processed food", "Group 4 on the NOVA scale");

    companion object {
        fun of(group: Int?): NovaGroup? = when (group) {
            1 -> UNPROCESSED
            2 -> CULINARY_INGREDIENT
            3 -> PROCESSED
            4 -> ULTRA_PROCESSED
            else -> null
        }
    }
}

/**
 * What kind of product is being scanned.
 *
 * This is not cosmetic detail, it changes the answer. Titanium dioxide is
 * banned in EU food since 2022 but is a permitted UV filter in cosmetics, so
 * looking a substance up without knowing the route of exposure can produce a
 * verdict that is precisely wrong.
 */
enum class ProductCategory(val key: String, val label: String) {
    FOOD("food", "Food & drink"),
    COSMETIC("cosmetic", "Cosmetic & personal care");

    companion object {
        fun from(key: String?): ProductCategory =
            entries.firstOrNull { it.key.equals(key, ignoreCase = true) } ?: FOOD
    }
}

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

/**
 * What the score says, phrased as a finding rather than a verdict.
 *
 * The labels used to be "Excellent", "Good", "Fair", "Poor" and "Avoid", which
 * are judgements about the product as a whole. The score cannot support that:
 * it starts at 100 and deducts for published concerns, so a high score means
 * "we found little to flag among the ingredients we hold assessments for", not
 * "this product is excellent".
 *
 * A hand wash containing two EU-restricted contact sensitisers was graded
 * "Excellent" on 88 points, which is precisely the overclaim this app exists to
 * argue against. Describing the finding instead makes the same number honest.
 */
enum class Grade(val label: String) {
    EXCELLENT("Minor concerns only"),
    GOOD("Some concerns"),
    FAIR("Several concerns"),
    POOR("Many concerns"),
    BAD("Best avoided");

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
    /**
     * Null when we recognised too little of the list to score it honestly.
     *
     * A photograph that caught the front of the pack instead of the ingredient
     * panel used to come back as "97, Excellent" purely because unrecognised
     * text was cheap. Refusing to answer is the correct response.
     */
    val score: Int?,
    val grade: Grade?,
    val ingredients: List<AnalyzedIngredient>,
    val alerts: List<PersonalAlert>,
    val rawIngredientsText: String,
    val category: ProductCategory = ProductCategory.FOOD
) {
    val unmatchedCount: Int get() = ingredients.count { it.matched == null }

    val recognisedCount: Int get() = ingredients.size - unmatchedCount

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
