package com.labellens.app.domain

import com.labellens.app.core.TextNormalizer
import com.labellens.app.data.local.IngredientDao
import com.labellens.app.data.local.NameRow
import com.labellens.app.data.local.toDomain
import com.labellens.app.domain.model.Ingredient
import com.labellens.app.domain.model.MatchConfidence
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class MatchResult(val ingredient: Ingredient?, val confidence: MatchConfidence)

/**
 * Resolves a raw ingredient token to a dictionary entry, in order of certainty:
 *
 *  1. exact normalized name
 *  2. known synonym
 *  3. E-number found anywhere in the token
 *  4. fuzzy match, to survive OCR errors like "benzqate" for "benzoate"
 *
 * The fuzzy index is built lazily and cached, because Levenshtein against every
 * row on every token would be far too slow once the dictionary is large.
 */
class IngredientMatcher(private val dao: IngredientDao) {

    private val indexLock = Mutex()
    private var fuzzyIndex: List<NameRow>? = null
    private val cache = HashMap<String, MatchResult>()

    suspend fun match(rawToken: String): MatchResult {
        val normalized = TextNormalizer.normalize(rawToken)
        if (normalized.isEmpty()) return MatchResult(null, MatchConfidence.NONE)

        cache[normalized]?.let { return it }

        val result = resolve(rawToken, normalized)
        cache[normalized] = result
        return result
    }

    private suspend fun resolve(rawToken: String, normalized: String): MatchResult {
        dao.byNormalizedName(normalized)?.let {
            return MatchResult(it.toDomain(), MatchConfidence.EXACT)
        }
        dao.bySynonym(normalized)?.let {
            return MatchResult(it.toDomain(), MatchConfidence.SYNONYM)
        }
        TextNormalizer.extractENumber(rawToken)?.let { eNumber ->
            dao.byENumber(eNumber)?.let {
                return MatchResult(it.toDomain(), MatchConfidence.E_NUMBER)
            }
        }
        containedName(normalized)?.let {
            return MatchResult(it, MatchConfidence.SYNONYM)
        }
        fuzzy(normalized)?.let {
            return MatchResult(it, MatchConfidence.FUZZY)
        }
        return MatchResult(null, MatchConfidence.NONE)
    }

    /**
     * Real labels qualify ingredients: "skimmed milk powder", "low fat cocoa",
     * "refined palm oil". None of those match a dictionary name exactly, but
     * they all contain one.
     *
     * The longest containing name wins, so "palm oil" beats a bare "oil", and
     * matching is on whole words so "soil" never matches "oil".
     */
    private suspend fun containedName(normalized: String): Ingredient? {
        val words = normalized.split(' ').filter { it.isNotEmpty() }
        if (words.size < 2) return null

        var bestId: String? = null
        var bestLength = 0

        for (row in index()) {
            // Single short words are too generic to match on containment alone.
            if (row.name.length < 4) continue
            if (row.name.length <= bestLength) continue
            if (containsWholeWords(words, row.name)) {
                bestLength = row.name.length
                bestId = row.id
            }
        }

        return bestId?.let { dao.byId(it)?.toDomain() }
    }

    private fun containsWholeWords(haystackWords: List<String>, needle: String): Boolean {
        val needleWords = needle.split(' ').filter { it.isNotEmpty() }
        if (needleWords.isEmpty() || needleWords.size > haystackWords.size) return false
        outer@ for (start in 0..haystackWords.size - needleWords.size) {
            for (offset in needleWords.indices) {
                if (haystackWords[start + offset] != needleWords[offset]) continue@outer
            }
            return true
        }
        return false
    }

    private suspend fun fuzzy(normalized: String): Ingredient? {
        // Very short tokens fuzzy-match far too easily ("oil" -> "soil").
        if (normalized.length < 5) return null

        val index = index()
        val tolerance = if (normalized.length <= 8) 1 else 2

        var bestId: String? = null
        var bestDistance = tolerance + 1

        for (row in index) {
            if (kotlin.math.abs(row.name.length - normalized.length) > tolerance) continue
            if (row.name.isEmpty() || row.name[0] != normalized[0]) continue
            val distance = TextNormalizer.levenshtein(normalized, row.name, tolerance)
            if (distance < bestDistance) {
                bestDistance = distance
                bestId = row.id
                if (distance == 1) break
            }
        }

        return bestId?.let { dao.byId(it)?.toDomain() }
    }

    private suspend fun index(): List<NameRow> {
        fuzzyIndex?.let { return it }
        return indexLock.withLock {
            fuzzyIndex ?: (dao.allNormalizedNames() + dao.allSynonymNames())
                .also { fuzzyIndex = it }
        }
    }
}
