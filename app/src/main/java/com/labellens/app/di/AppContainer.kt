package com.labellens.app.di

import android.content.Context
import com.labellens.app.data.local.LabelLensDatabase
import com.labellens.app.data.prefs.ProfileStore
import com.labellens.app.data.remote.OpenFoodFactsClient
import com.labellens.app.data.repo.AnalysisRepository
import com.labellens.app.data.seed.SeedLoader
import com.labellens.app.domain.IngredientMatcher

/**
 * Hand-rolled dependency container.
 *
 * A DI framework would be overkill here and adds build complexity for no real
 * benefit at this size. If the app grows several more feature modules, revisit.
 */
class AppContainer(context: Context) {

    private val database = LabelLensDatabase.get(context)
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
