package com.labeltruth.app.data.seed

import android.content.Context
import com.labeltruth.app.core.TextNormalizer
import com.labeltruth.app.data.local.IngredientDao
import com.labeltruth.app.data.local.IngredientEntity
import com.labeltruth.app.data.local.LIST_DELIMITER
import com.labeltruth.app.data.local.SOURCE_DELIMITER
import com.labeltruth.app.data.local.SOURCE_FIELD_DELIMITER
import com.labeltruth.app.data.local.SynonymEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Imports the bundled dictionary into Room on first launch.
 *
 * At the current seed size this takes well under a second. When the dictionary
 * reaches tens of thousands of rows this should be replaced with a pre-built
 * SQLite file loaded via Room's createFromAsset, so there is no import at all.
 */
class SeedLoader(
    private val context: Context,
    private val dao: IngredientDao
) {
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun seedIfNeeded(): Int = withContext(Dispatchers.IO) {
        val existing = dao.count()
        if (existing > 0) return@withContext existing

        val raw = context.assets.open(ASSET_NAME).bufferedReader().use { it.readText() }
        val seed = json.decodeFromString<SeedFile>(raw)

        val ingredients = seed.ingredients.map { it.toEntity() }

        // Deduplicate synonyms; a synonym is a primary key so a collision would throw.
        val synonyms = LinkedHashMap<String, SynonymEntity>()
        for (item in seed.ingredients) {
            val canonicalNormalized = TextNormalizer.normalize(item.name)
            val candidates = item.synonyms + listOfNotNull(item.eNumber)
            for (synonym in candidates) {
                val normalized = TextNormalizer.normalize(synonym)
                if (normalized.isBlank() || normalized == canonicalNormalized) continue
                synonyms.putIfAbsent(normalized, SynonymEntity(normalized, item.id))
            }
        }

        dao.insertIngredients(ingredients)
        dao.insertSynonyms(synonyms.values.toList())
        ingredients.size
    }

    private fun SeedIngredient.toEntity() = IngredientEntity(
        id = id,
        canonicalName = name,
        normalizedName = TextNormalizer.normalize(name),
        eNumber = eNumber,
        category = category,
        whatItIs = whatItIs,
        whyUsed = whyUsed,
        riskTier = riskTier,
        riskReason = riskReason,
        allergens = allergens.joinToString(LIST_DELIMITER),
        dietaryFlags = dietary.joinToString(LIST_DELIMITER),
        cautionGroups = cautionGroups.joinToString(LIST_DELIMITER),
        adi = adi,
        sources = sources.joinToString(SOURCE_DELIMITER) {
            "${it.title}$SOURCE_FIELD_DELIMITER${it.url ?: ""}"
        }
    )

    companion object {
        const val ASSET_NAME = "ingredients_seed.json"
    }
}
