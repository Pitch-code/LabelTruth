package com.labeltruth.app.domain

import com.labeltruth.app.domain.model.Nutrition
import com.labeltruth.app.domain.model.NutritionFinding
import com.labeltruth.app.domain.model.RiskTier
import com.labeltruth.app.domain.model.SourceRef

/**
 * Turns a nutrition panel into findings, each traceable to a published limit.
 *
 * This exists because an ingredient scanner is blind to exactly the products
 * where the answer is simplest. Ghee has one ingredient, milk fat, so ingredient
 * analysis reports "1 item, no published assessment" and tells the reader
 * nothing. The nutrition panel for the same product says 99.8 g fat and 68 g
 * saturated fat per 100 g, which is the entire story.
 *
 * Every finding here is arithmetic against a published threshold, stated with
 * the number, the threshold and the source. Nothing is inferred about the
 * reader's health, and no finding claims what a food will do to a person: it
 * reports what is in it and what the published guidance says about that amount.
 *
 * Values are per 100 g or 100 ml, which is how Open Food Facts holds them and
 * how European and Indian labels declare them. Deliberately *not* converted
 * into servings: a serving size is a manufacturer's choice, and dividing by one
 * would imply a portion nobody agreed to.
 */
object NutritionAssessor {

    /**
     * The reference diet used to turn a percentage-of-energy guideline into
     * grams. 2,000 kcal is the reference intake used on EU labels and in FSSAI's
     * own labelling guidance, so a reader can check the arithmetic.
     */
    const val REFERENCE_KCAL = 2000.0

    // 9 kcal per gram of fat, the standard energy conversion factor.
    private const val KCAL_PER_GRAM_FAT = 9.0

    // 4 kcal per gram of carbohydrate, used for the sugars guideline.
    private const val KCAL_PER_GRAM_CARB = 4.0

    /** WHO: saturated fat no more than 10% of total energy intake. */
    private val saturatedFatDailyGrams =
        REFERENCE_KCAL * 0.10 / KCAL_PER_GRAM_FAT   // ~22 g

    /** WHO: trans fat no more than 1% of total energy intake. */
    private val transFatDailyGrams =
        REFERENCE_KCAL * 0.01 / KCAL_PER_GRAM_FAT   // ~2.2 g

    /** WHO: free sugars below 10% of total energy intake. */
    private val sugarsDailyGrams =
        REFERENCE_KCAL * 0.10 / KCAL_PER_GRAM_CARB  // 50 g

    /** WHO: less than 5 g of salt a day. */
    private const val SALT_DAILY_GRAMS = 5.0

    /**
     * FSSAI caps industrial trans fatty acids at 2% by mass of the total
     * oils and fats present in a food product, from 1 January 2022.
     */
    private const val FSSAI_TRANS_FAT_PERCENT_OF_FAT = 2.0

    private val whoFats = SourceRef(
        title = "WHO guideline: saturated fatty acid and trans-fatty acid intake " +
            "for adults and children (2023)",
        url = "https://www.who.int/publications/i/item/9789240073630"
    )

    private val whoSugars = SourceRef(
        title = "WHO guideline: sugars intake for adults and children",
        url = "https://www.who.int/publications/i/item/9789241549028"
    )

    private val whoSalt = SourceRef(
        title = "WHO guideline: sodium intake for adults and children",
        url = "https://www.who.int/publications/i/item/9789241504836"
    )

    private val fssaiTransFat = SourceRef(
        title = "FSSAI guidance note on trans fat: industrial trans fatty acids " +
            "limited to 2% of total oils and fats from 1 January 2022",
        url = "https://www.fssai.gov.in/upload/uploadfiles/files/" +
            "Guidance_Note_TransFat_03_03_2022.pdf"
    )

    fun assess(nutrition: Nutrition): List<NutritionFinding> {
        val findings = mutableListOf<NutritionFinding>()

        nutrition.transFat100g?.let { grams ->
            // Checked against the Indian regulatory cap first, because exceeding a
            // legal limit is a stronger and more useful statement than exceeding
            // dietary guidance. Needs total fat to express trans fat as a share
            // of it, which is how the rule is written.
            val totalFat = nutrition.fat100g
            if (grams > 0 && totalFat != null && totalFat > 0) {
                val shareOfFat = grams / totalFat * 100
                if (shareOfFat > FSSAI_TRANS_FAT_PERCENT_OF_FAT) {
                    findings += NutritionFinding(
                        title = "Trans fat above India's regulatory limit",
                        detail = "${fmt(grams)} g of trans fat per 100 g, which is " +
                            "${fmt(shareOfFat)}% of the ${fmt(totalFat)} g of total fat. " +
                            "FSSAI limits industrial trans fatty acids to 2% of the " +
                            "oils and fats in a product. Trans fat can also occur " +
                            "naturally in dairy and meat, which this rule does not cover.",
                        tier = RiskTier.AVOID,
                        source = fssaiTransFat
                    )
                }
            }
            if (grams > 0) {
                findings += NutritionFinding(
                    title = "Contains trans fat",
                    detail = "${fmt(grams)} g per 100 g. WHO advises keeping trans fat " +
                        "under 1% of daily energy, about ${fmt(transFatDailyGrams)} g on a " +
                        "${REFERENCE_KCAL.toInt()} kcal diet.",
                    tier = if (grams >= transFatDailyGrams) RiskTier.MODERATE
                    else RiskTier.CAUTION,
                    source = whoFats
                )
            }
        }

        nutrition.saturatedFat100g?.let { grams ->
            val dayShare = grams / saturatedFatDailyGrams
            if (dayShare >= 0.5) {
                findings += NutritionFinding(
                    title = "High in saturated fat",
                    detail = "${fmt(grams)} g per 100 g. WHO advises keeping saturated " +
                        "fat under 10% of daily energy, about " +
                        "${fmt(saturatedFatDailyGrams)} g on a " +
                        "${REFERENCE_KCAL.toInt()} kcal diet, so 100 g of this contains " +
                        "${describeShare(dayShare)}.",
                    tier = if (dayShare >= 1.0) RiskTier.MODERATE else RiskTier.CAUTION,
                    source = whoFats
                )
            }
        }

        nutrition.sugars100g?.let { grams ->
            val dayShare = grams / sugarsDailyGrams
            if (dayShare >= 0.5) {
                findings += NutritionFinding(
                    title = "High in sugars",
                    detail = "${fmt(grams)} g per 100 g. WHO advises keeping free sugars " +
                        "under 10% of daily energy, about ${fmt(sugarsDailyGrams)} g on a " +
                        "${REFERENCE_KCAL.toInt()} kcal diet, so 100 g of this contains " +
                        "${describeShare(dayShare)}. Labels do not separate free sugars " +
                        "from those naturally present, so this figure covers both.",
                    tier = if (dayShare >= 1.0) RiskTier.MODERATE else RiskTier.CAUTION,
                    source = whoSugars
                )
            }
        }

        nutrition.salt100g?.let { grams ->
            val dayShare = grams / SALT_DAILY_GRAMS
            if (dayShare >= 0.5) {
                findings += NutritionFinding(
                    title = "High in salt",
                    detail = "${fmt(grams)} g of salt per 100 g. WHO advises less than " +
                        "${fmt(SALT_DAILY_GRAMS)} g a day, so 100 g of this contains " +
                        "${describeShare(dayShare)}.",
                    tier = if (dayShare >= 1.0) RiskTier.MODERATE else RiskTier.CAUTION,
                    source = whoSalt
                )
            }
        }

        return findings
    }

    /**
     * Plain wording for how much of a day's guidance sits in 100 g.
     *
     * Kept factual and per 100 g. It is not a claim about a portion: nobody eats
     * 100 g of ghee in a sitting, and implying they might would be its own kind
     * of dishonesty.
     */
    private fun describeShare(share: Double): String = when {
        share >= 2.0 -> "about ${fmt(share)} times that whole daily amount"
        share >= 1.0 -> "roughly a whole day's worth or more"
        else -> "about ${(share * 100).toInt()}% of that daily amount"
    }

    /** One decimal place, and no trailing ".0" on whole numbers. */
    private fun fmt(value: Double): String {
        val rounded = Math.round(value * 10) / 10.0
        return if (rounded % 1.0 == 0.0) rounded.toInt().toString() else rounded.toString()
    }
}
