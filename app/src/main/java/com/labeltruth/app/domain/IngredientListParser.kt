package com.labeltruth.app.domain

/**
 * Splits a raw ingredient declaration into individual ingredient tokens.
 *
 * Real labels look like:
 *   "Ingredients: Water, Sugar, Vegetable Oil (Palm, Rapeseed), Emulsifier
 *    (E471, Soy Lecithin), Colour: Curcumin, Salt 1.2%, Flavouring."
 *
 * Rules applied:
 *  - drop a leading "Ingredients:" style prefix
 *  - split on commas and semicolons, but only at bracket depth zero
 *  - additionally emit the contents of brackets as their own tokens, because
 *    that is where additives usually hide
 *  - strip percentages, footnote markers and category prefixes
 */
object IngredientListParser {

    private val leadingLabel = Regex(
        "^\\s*(ingredients?|ingrédients?|composition|contains)\\s*[:\\-]\\s*",
        RegexOption.IGNORE_CASE
    )
    private val percentage = Regex("\\d+([.,]\\d+)?\\s*%")
    /**
     * Functional-class prefixes, as in "Emulsifier: Soy Lecithin".
     *
     * Plurals are an optional suffix rather than separate alternatives, because
     * ordered alternation would otherwise match the singular and leave a
     * stray "s" behind. Longer phrases are listed before shorter ones that
     * prefix them, so "acidity regulator" wins over "acid".
     */
    private const val FUNCTIONAL_CLASSES =
        "acidity regulator(s)?|" +
            "flavour enhancer(s)?|flavor enhancer(s)?|" +
            "anti[ -]?caking agent(s)?|" +
            "flour treatment agent(s)?|" +
            "raising agent(s)?|glazing agent(s)?|gelling agent(s)?|" +
            "bulking agent(s)?|firming agent(s)?|" +
            "colour(s)?|color(s)?|colouring(s)?|coloring(s)?|" +
            "preservative(s)?|emulsifier(s)?|antioxidant(s)?|" +
            "stabiliser(s)?|stabilizer(s)?|" +
            "thickener(s)?|thickening agent(s)?|" +
            "sweetener(s)?|humectant(s)?|carrier(s)?|propellant(s)?|" +
            "acid"

    /**
     * Strips a functional-class prefix, as in "Emulsifier: Soy Lecithin".
     *
     * The separator is required. Without it, "acid" would also match the start
     * of "acidified milk" and mangle it. Tokens that are *only* a class name,
     * such as the "Colours" left behind by "Colours (E102, E133)", are dropped
     * by [classOnly] instead, since the real ingredients were already pulled
     * out of the brackets.
     */
    private val categoryPrefix = Regex(
        "^($FUNCTIONAL_CLASSES)\\s*[:\\-]\\s*",
        RegexOption.IGNORE_CASE
    )

    private val classOnly = Regex("^($FUNCTIONAL_CLASSES)$", RegexOption.IGNORE_CASE)
    private val noiseChars = Regex("[*†‡•·]")

    /** Matches an innermost bracket group, round or square. */
    private val innerBracketGroup = Regex("[(\\[][^()\\[\\]]*[)\\]]")

    /**
     * Label text that sits alongside the ingredient list but is not an
     * ingredient. Without this, phrases like "Gluten free" count as
     * unrecognised ingredients and unfairly drag the score down.
     */
    private val boilerplate = Regex(
        "^(" +
            // Food label claims and statements
            "gluten free|sans gluten|dairy free|sugar free|fat free|" +
            "contains a source of.*|contains .* in bold|" +
            "may contain.*|allergy advice.*|for allergen(s)?.*|" +
            "suitable for.*|not suitable for.*|" +
            "store .*|keep .*|best before.*|use by.*|once opened.*|" +
            "produced in.*|packed in.*|made in.*|manufactured .*|" +
            "average values.*|nutrition.*|typical values.*|" +
            "percentage(s)? .*|all percentages.*|" +
            "e numbers|ingredients|composition|" +
            // Cosmetic, hygiene and household labels put usage and safety text
            // right next to the ingredient list. A competitor's app displayed
            // "For external use only" and "Keep out of" as if they were
            // ingredients, which is exactly the failure to avoid.
            "for external use only|external use only|" +
            "direction(s)?.*|instruction(s)?.*|how to use.*|usage.*|" +
            "warning(s)?.*|caution.*|precaution(s)?.*|" +
            "avoid contact.*|in case of.*|if swallowed.*|if irritation.*|" +
            "discontinue use.*|rinse.*|wet hands.*|apply .*|" +
            "not to be taken.*|for best results.*|" +
            "batch no.*|mfg.*|mfd.*|exp.*|lot no.*|net (wt|weight).*|" +
            "marketed by.*|manufactured by.*|imported by.*|customer care.*|" +
            "consumer complaint.*|shelf life.*|" +
            "recyclable|please recycle.*|dispose of.*" +
            ")$",
        RegexOption.IGNORE_CASE
    )

    fun parse(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()

        val cleaned = raw
            .replace('\n', ' ')
            .replace(leadingLabel, "")
            .replace(noiseChars, " ")

        val tokens = mutableListOf<String>()
        val topLevel = splitAtDepthZero(cleaned)

        for (segment in topLevel) {
            val bracketBodies = extractBracketBodies(segment)
            val outer = segment.replace(innerBracketGroup, " ")

            addToken(tokens, outer)
            // Bracket contents can themselves be a comma-separated list.
            for (body in bracketBodies) {
                splitAtDepthZero(body).forEach { addToken(tokens, it) }
            }
        }

        return tokens.distinctBy { it.lowercase() }
    }

    private fun addToken(sink: MutableList<String>, candidate: String) {
        val token = tidy(candidate)
        if (token.isNotEmpty()) sink.add(token)
    }

    private fun tidy(input: String): String {
        var s = input
            .replace(percentage, " ")
            .replace(Regex("\\s+"), " ")
            .trim()
            // Trim first: the category prefix pattern is anchored, so a leading
            // space from splitting would stop it matching.
            .replace(categoryPrefix, "")
            .trim()
            .trim(',', '.', ';', ':', '-', '–', '—', ' ')
            .replace(Regex("\\s+"), " ")

        // Leading conjunctions left behind by splitting.
        s = s.replace(Regex("^(and|or|with|from|of)\\s+", RegexOption.IGNORE_CASE), "")

        // Drop pure noise and over-long fragments that are clearly not ingredients.
        if (s.length < 2 || s.length > 80) return ""
        if (!s.any { it.isLetter() }) return ""
        if (boilerplate.containsMatchIn(s)) return ""
        if (classOnly.matches(s)) return ""
        return s
    }

    /**
     * Split on , ; and . but only at bracket depth zero.
     *
     * Full stops matter because labels routinely end the list with a sentence,
     * as in "...; vanillin. Gluten free". A full stop between two digits is a
     * decimal point, not a separator, so "8.7%" stays intact.
     */
    private fun splitAtDepthZero(input: String): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var depth = 0
        for (index in input.indices) {
            val ch = input[index]
            when {
                ch == '(' || ch == '[' || ch == '{' -> { depth++; sb.append(ch) }
                ch == ')' || ch == ']' || ch == '}' -> { if (depth > 0) depth--; sb.append(ch) }
                depth == 0 && (ch == ',' || ch == ';') -> { out.add(sb.toString()); sb.clear() }
                depth == 0 && ch == '.' && !isDecimalPoint(input, index) -> {
                    out.add(sb.toString()); sb.clear()
                }
                else -> sb.append(ch)
            }
        }
        if (sb.isNotBlank()) out.add(sb.toString())
        return out
    }

    private fun isDecimalPoint(text: String, index: Int): Boolean =
        text.getOrNull(index - 1)?.isDigit() == true &&
            text.getOrNull(index + 1)?.isDigit() == true

    /** Returns the text inside each outermost bracket pair. */
    private fun extractBracketBodies(input: String): List<String> {
        val out = mutableListOf<String>()
        var depth = 0
        val sb = StringBuilder()
        for (ch in input) {
            when (ch) {
                '(', '[' -> { if (depth > 0) sb.append(ch); depth++ }
                ')', ']' -> {
                    depth--
                    if (depth == 0) { if (sb.isNotBlank()) out.add(sb.toString()); sb.clear() }
                    else if (depth > 0) sb.append(ch)
                }
                else -> if (depth > 0) sb.append(ch)
            }
        }
        return out
    }
}
