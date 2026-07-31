package com.labellens.app.data.repo

import com.labellens.app.data.local.ScanDao
import com.labellens.app.data.local.ScanEntity
import com.labellens.app.data.local.toDomain
import com.labellens.app.data.local.IngredientDao
import com.labellens.app.data.remote.LookupResult
import com.labellens.app.data.remote.OpenFoodFactsClient
import com.labellens.app.data.seed.SeedLoader
import com.labellens.app.domain.IngredientListParser
import com.labellens.app.domain.IngredientMatcher
import com.labellens.app.domain.ScoreEngine
import com.labellens.app.domain.model.AnalyzedIngredient
import com.labellens.app.domain.model.Analysis
import com.labellens.app.domain.model.Grade
import com.labellens.app.domain.model.HealthProfile
import com.labellens.app.domain.model.Ingredient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AnalysisRepository(
    private val ingredientDao: IngredientDao,
    private val scanDao: ScanDao,
    private val matcher: IngredientMatcher,
    private val remote: OpenFoodFactsClient,
    private val seedLoader: SeedLoader
) {
    suspend fun ensureDictionaryReady(): Int = seedLoader.seedIfNeeded()

    val history: Flow<List<ScanEntity>> = scanDao.observeRecent()

    suspend fun deleteScan(id: Long) = scanDao.delete(id)
    suspend fun clearHistory() = scanDao.clearAll()

    suspend fun ingredientById(id: String): Ingredient? =
        withContext(Dispatchers.IO) { ingredientDao.byId(id)?.toDomain() }

    suspend fun searchIngredients(query: String): List<Ingredient> =
        withContext(Dispatchers.IO) {
            ingredientDao.search(query.lowercase().trim()).map { it.toDomain() }
        }

    /** Barcode path: look the product up, then analyse its declared ingredients. */
    suspend fun analyzeBarcode(barcode: String, profile: HealthProfile): BarcodeOutcome =
        when (val result = remote.lookup(barcode)) {
            is LookupResult.Found -> BarcodeOutcome.Success(
                analyze(
                    productName = result.product.name,
                    brand = result.product.brand,
                    barcode = barcode,
                    ingredientsText = result.product.ingredientsText,
                    profile = profile
                )
            )
            is LookupResult.NoIngredients -> BarcodeOutcome.NoIngredients(result.name)
            LookupResult.NotFound -> BarcodeOutcome.NotFound
            is LookupResult.Error -> BarcodeOutcome.Error(result.message)
        }

    /** OCR path: the user photographed the printed ingredient list. */
    suspend fun analyzeScannedText(text: String, profile: HealthProfile): Analysis =
        analyze(
            productName = "Scanned label",
            brand = null,
            barcode = null,
            ingredientsText = text,
            profile = profile
        )

    private suspend fun analyze(
        productName: String,
        brand: String?,
        barcode: String?,
        ingredientsText: String,
        profile: HealthProfile
    ): Analysis = withContext(Dispatchers.Default) {
        val tokens = IngredientListParser.parse(ingredientsText)
        val matched = tokens.map { token ->
            val match = matcher.match(token)
            AnalyzedIngredient(
                rawText = token,
                matched = match.ingredient,
                matchConfidence = match.confidence
            )
        }

        // Labels frequently name the same additive twice, for example
        // "Acid: Citric Acid (E330)" yields both "Citric Acid" and "E330".
        // Without collapsing these, the ingredient is listed twice and its
        // penalty is counted twice.
        val analyzed = mutableListOf<AnalyzedIngredient>()
        val seenIds = mutableSetOf<String>()
        val seenRaw = mutableSetOf<String>()
        for (item in matched) {
            val id = item.matched?.id
            if (id != null) {
                if (!seenIds.add(id)) continue
            } else if (!seenRaw.add(item.rawText.lowercase())) {
                continue
            }
            analyzed.add(item)
        }

        val score = ScoreEngine.score(analyzed)
        val analysis = Analysis(
            productName = productName,
            brand = brand,
            barcode = barcode,
            score = score,
            grade = Grade.of(score),
            ingredients = analyzed,
            alerts = ScoreEngine.alerts(analyzed, profile),
            rawIngredientsText = ingredientsText
        )

        if (analyzed.isNotEmpty()) {
            scanDao.insert(
                ScanEntity(
                    productName = productName,
                    brand = brand,
                    barcode = barcode,
                    score = score,
                    timestamp = System.currentTimeMillis(),
                    ingredientsRaw = ingredientsText
                )
            )
        }

        analysis
    }
}

sealed interface BarcodeOutcome {
    data class Success(val analysis: Analysis) : BarcodeOutcome
    data object NotFound : BarcodeOutcome
    data class NoIngredients(val productName: String) : BarcodeOutcome
    data class Error(val message: String) : BarcodeOutcome
}
