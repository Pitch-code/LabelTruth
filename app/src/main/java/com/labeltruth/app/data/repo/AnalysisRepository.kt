package com.labeltruth.app.data.repo

import com.labeltruth.app.data.local.BookmarkDao
import com.labeltruth.app.data.local.BookmarkEntity
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AnalysisRepository(
    private val ingredientDao: IngredientDao,
    private val scanDao: ScanDao,
    private val bookmarkDao: BookmarkDao,
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

    /** Scans the user pinned, newest first. */
    val savedScans: Flow<List<ScanEntity>> = scanDao.observeSaved()

    suspend fun toggleScanSaved(id: Long) = scanDao.toggleSaved(id)

    suspend fun deleteScan(id: Long) = scanDao.delete(id)
    suspend fun clearHistory() = scanDao.clearAll()

    /** Saved ingredients, re-read from the dictionary so they stay current. */
    val bookmarks: Flow<List<Ingredient>> =
        bookmarkDao.observeBookmarked().map { rows -> rows.map { it.toDomain() } }

    /** Ids only, for deciding whether the bookmark button reads as saved. */
    val bookmarkedIds: Flow<Set<String>> =
        bookmarkDao.observeIds().map { it.toSet() }

    suspend fun toggleBookmark(ingredientId: String) = withContext(Dispatchers.IO) {
        if (bookmarkDao.exists(ingredientId)) {
            bookmarkDao.delete(ingredientId)
        } else {
            bookmarkDao.insert(
                BookmarkEntity(ingredientId = ingredientId, savedAt = System.currentTimeMillis())
            )
        }
    }

    /** Scores of the scans that could be scored, for the grade breakdown. */
    val scanScores: Flow<List<Int>> = scanDao.observeScores()

    /**
     * How many scans have been made, including ones that could not be scored.
     *
     * Counted separately from [scanScores] on purpose: a scan the user made is a
     * scan, and telling them "0 products scanned" straight after they scanned
     * something is the kind of small dishonesty this app cannot afford.
     */
    val scanCount: Flow<Int> = scanDao.observeCount()

    /**
     * One assessed, cited ingredient to feature on the home screen.
     *
     * [seed] is expected to be a day number, so the choice is stable for a whole
     * day rather than changing on every launch. A card that reshuffles each time
     * the user opens the app is noise, not a reading suggestion.
     *
     * Returns null before the dictionary has been imported.
     */
    suspend fun spotlightIngredient(seed: Int): Ingredient? = withContext(Dispatchers.IO) {
        val total = ingredientDao.assessedWithSourcesCount()
        if (total == 0) return@withContext null
        val offset = ((seed % total) + total) % total
        ingredientDao.assessedWithSourcesAt(offset)?.toDomain()
    }

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

        // Unscored scans are kept too, with a null score rather than a made-up
        // one. Dropping them meant the app could show a result and then show an
        // empty history seconds later, which is indistinguishable from being
        // broken. Anything we managed to read is worth being able to return to.
        if (persist) {
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
