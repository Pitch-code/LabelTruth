package com.labeltruth.app.di

import android.content.Context
import com.labeltruth.app.data.local.LabelTruthDatabase
import com.labeltruth.app.data.prefs.ProfileStore
import com.labeltruth.app.data.remote.OpenFoodFactsClient
import com.labeltruth.app.data.repo.AnalysisRepository
import com.labeltruth.app.data.seed.SeedLoader
import com.labeltruth.app.domain.IngredientMatcher

/**
 * Hand-rolled dependency container.
 *
 * A DI framework would be overkill here and adds build complexity for no real
 * benefit at this size. If the app grows several more feature modules, revisit.
 */
class AppContainer(context: Context) {

    private val database = LabelTruthDatabase.get(context)
    private val ingredientDao = database.ingredientDao()
    private val scanDao = database.scanDao()

    val profileStore = ProfileStore(context)

    val repository = AnalysisRepository(
        ingredientDao = ingredientDao,
        scanDao = scanDao,
        matcher = IngredientMatcher(ingredientDao),
        remote = OpenFoodFactsClient(context),
        seedLoader = SeedLoader(context, ingredientDao)
    )
}
