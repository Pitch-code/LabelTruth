package com.labeltruth.app.data.repo

import com.labeltruth.app.data.local.ScanDao
import com.labeltruth.app.data.local.ScanEntity
import com.labeltruth.app.data.local.toDomain
import com.labeltruth.app.data.local.IngredientDao
import com.labeltruth.app.data.remote.LookupResult
import com.labeltruth.app.data.remote.OpenFoodFactsClient
import com.labeltruth.app.data.seed.SeedLoader
import com.labeltruth.app.domain.IngredientListParser
import com.labeltruth.app.domain.IngredientMatcher
import com.labeltruth.app.domain.ScoreEngine
import com.labeltruth.app.domain.model.AnalyzedIngredient
import com.labeltruth.app.domain.model.Analysis
import com.labeltruth.app.domain.model.Grade
import com.labeltruth.app.domain.model.HealthProfile
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.domain.model.ProductCategory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AnalysisRepository(
    private val ingredientDao: IngredientDao,
    private val scanDao: ScanDao,
    private val matcher: IngredientMatcher,
    private val remote: OpenFoodFactsClient,
    private val seedLoader: SeedLoader
) {
    private val seedMutex = Mutex()
    private var seeded = false

    /**
     * Importing 27,000 entries takes a moment on first launch. Without this,
     * a scan started during that window would match nothing and the app would
     * confidently report "not in our database" for perfectly ordinary food.
     *
     * Every analysis waits on the same one-time job, so the work happens once
     * and concurrent callers simply queue behind it.
     */
    suspend fun ensureDictionaryReady(): Int {
        if (seeded) return 0
        return seedMutex.withLock {
            if (seeded) 0
            else seedLoader.seedIfNeeded().also { seeded = true }
        }
    }

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
                    profile = profile,
                    // Which database answered tells us the route of exposure.
                    category = result.product.category
                )
            )
            is LookupResult.NoIngredients -> BarcodeOutcome.NoIngredients(result.name)
            LookupResult.NotFound -> BarcodeOutcome.NotFound
            is LookupResult.Error -> BarcodeOutcome.Error(result.message)
        }

    /** OCR path: the user photographed the printed ingredient list. */
    suspend fun analyzeScannedText(
        text: String,
        profile: HealthProfile,
        category: ProductCategory
    ): Analysis = analyze(
        productName = "Scanned label",
        brand = null,
        barcode = null,
        ingredientsText = text,
        profile = profile,
        category = category
    )

    /**
     * Re-runs a saved scan so it can be reopened from history.
     *
     * Only the raw ingredient text is stored, not the per-ingredient verdicts,
     * so the analysis is recomputed. That is the right way round: the dictionary
     * improves over time, and a saved scan should be re-read against what we
     * know now rather than replaying a stale answer. It also means the alerts
     * reflect the user's *current* profile.
     *
     * Does not write a new history row.
     */
    suspend fun replayScan(id: Long, profile: HealthProfile): Analysis? {
        val scan = withContext(Dispatchers.IO) { scanDao.byId(id) } ?: return null
        return analyze(
            productName = scan.productName,
            brand = scan.brand,
            barcode = scan.barcode,
            ingredientsText = scan.ingredientsRaw,
            profile = profile,
            category = ProductCategory.from(scan.category),
            persist = false
        )
    }

    private suspend fun analyze(
        productName: String,
        brand: String?,
        barcode: String?,
        ingredientsText: String,
        profile: HealthProfile,
        category: ProductCategory,
        persist: Boolean = true
    ): Analysis = withContext(Dispatchers.Default) {
        // Never analyse against a half-imported dictionary.
        ensureDictionaryReady()

        val tokens = IngredientListParser.parse(ingredientsText)
        val matched = tokens.map { token ->
            val match = matcher.match(token, category)
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
            grade = score?.let(Grade::of),
            ingredients = analyzed,
            alerts = ScoreEngine.alerts(analyzed, profile),
            rawIngredientsText = ingredientsText,
            category = category
        )

        // Only scans we could actually score are worth keeping. A failed read
        // has nothing to revisit, and storing one would put a fabricated score
        // in the history list where it cannot even be explained.
        if (persist && score != null) {
            scanDao.insert(
                ScanEntity(
                    productName = productName,
                    brand = brand,
                    barcode = barcode,
                    score = score,
                    timestamp = System.currentTimeMillis(),
                    ingredientsRaw = ingredientsText,
                    category = category.key
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
