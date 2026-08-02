package com.labeltruth.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.labeltruth.app.domain.ScanStats
import com.labeltruth.app.domain.model.Ingredient
import com.labeltruth.app.ui.DisclaimerDialog
import com.labeltruth.app.ui.about.AboutScreen
import com.labeltruth.app.ui.bookmarks.BookmarksScreen
import com.labeltruth.app.ui.components.BottomBar
import com.labeltruth.app.ui.components.BottomTab
import com.labeltruth.app.ui.detail.IngredientDetailSheet
import com.labeltruth.app.ui.history.HistoryScreen
import com.labeltruth.app.ui.home.HomeScreen
import com.labeltruth.app.ui.onboarding.OnboardingScreen
import com.labeltruth.app.ui.profile.ProfileScreen
import com.labeltruth.app.ui.result.ResultSheet
import com.labeltruth.app.ui.search.SearchScreen
import com.labeltruth.app.ui.scanner.ScannerScreen
import com.labeltruth.app.ui.scanner.ScannerViewModel
import com.labeltruth.app.ui.theme.LabelTruthTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as LabelTruthApp).container

        setContent {
            LabelTruthTheme {
                val scope = rememberCoroutineScope()
                val navController = rememberNavController()

                val viewModel: ScannerViewModel = viewModel(
                    factory = ScannerViewModel.factory(
                        container.repository,
                        container.profileStore
                    )
                )
                val state by viewModel.state.collectAsStateWithLifecycle()
                val history by container.repository.history
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val disclaimerAccepted by container.profileStore.disclaimerAccepted
                    .collectAsState(initial = true)

                if (!disclaimerAccepted) {
                    DisclaimerDialog(
                        onAccept = { scope.launch { container.profileStore.acceptDisclaimer() } }
                    )
                }

                // Ask for the profile up front, once the disclaimer is out of the
                // way. Personalised alerts are the differentiator, and they need
                // a profile that nobody would otherwise go and fill in.
                val onboarded by container.profileStore.onboardingComplete
                    .collectAsState(initial = true)

                val firstName by container.profileStore.firstName
                    .collectAsState(initial = "")

                if (disclaimerAccepted && !onboarded) {
                    OnboardingScreen(
                        profile = state.profile,
                        firstName = firstName,
                        onFirstNameChange = { value ->
                            scope.launch { container.profileStore.setFirstName(value) }
                        },
                        onToggleAllergen = { value ->
                            scope.launch { container.profileStore.toggleAllergen(value) }
                        },
                        onToggleIntolerance = { value ->
                            scope.launch { container.profileStore.toggleCondition(value) }
                        },
                        onToggleDiet = { value ->
                            scope.launch { container.profileStore.toggleDiet(value) }
                        },
                        onToggleCondition = { value ->
                            scope.launch { container.profileStore.toggleCondition(value) }
                        },
                        onFinish = {
                            scope.launch { container.profileStore.completeOnboarding() }
                        }
                    )
                    return@LabelTruthTheme
                }

                val bookmarks by container.repository.bookmarks
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val bookmarkedIds by container.repository.bookmarkedIds
                    .collectAsStateWithLifecycle(initialValue = emptySet())
                val scanScores by container.repository.scanScores
                    .collectAsStateWithLifecycle(initialValue = emptyList())
                val scanCount by container.repository.scanCount
                    .collectAsStateWithLifecycle(initialValue = 0)

                // Imports the dictionary at launch instead of on the first scan,
                // then picks the spotlight entry. Doing it here means the first
                // scan of a fresh install is not the one that waits.
                var spotlight by remember { mutableStateOf<Ingredient?>(null) }
                LaunchedEffect(Unit) {
                    container.repository.ensureDictionaryReady()
                    // A day number, so the card is stable for the whole day
                    // rather than reshuffling on every launch.
                    val today = (System.currentTimeMillis() / 86_400_000L).toInt()
                    spotlight = container.repository.spotlightIngredient(today)
                }

                val backStackEntry by navController.currentBackStackEntryAsState()
                val currentRoute = backStackEntry?.destination?.route
                val bottomBarRoutes = setOf(
                    Routes.HOME, Routes.HISTORY, Routes.BOOKMARKS, Routes.PROFILE
                )

                Box(modifier = Modifier.fillMaxSize()) {
                NavHost(navController = navController, startDestination = Routes.HOME) {
                    composable(Routes.HOME) {
                        HomeScreen(
                            greetingName = firstName,
                            scanCount = scanCount,
                            distribution = ScanStats.distribution(scanScores),
                            recent = history.take(3),
                            spotlight = spotlight,
                            onOpenScanner = { navController.navigate(Routes.SCANNER) },
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onOpenHistory = { navController.navigate(Routes.HISTORY) },
                            onOpenScan = viewModel::reopenScan,
                            onOpenIngredient = viewModel::selectIngredient
                        )
                    }
                    composable(Routes.BOOKMARKS) {
                        BookmarksScreen(
                            bookmarks = bookmarks,
                            onOpenIngredient = viewModel::selectIngredient
                        )
                    }
                    composable(Routes.SCANNER) {
                        ScannerScreen(
                            state = state,
                            onModeChange = viewModel::setMode,
                            onToggleTorch = viewModel::toggleTorch,
                            onBarcode = viewModel::onBarcodeDetected,
                            onLabelText = viewModel::onLabelTextCaptured,
                            onOpenHistory = { navController.navigate(Routes.HISTORY) },
                            onOpenProfile = { navController.navigate(Routes.PROFILE) },
                            onOpenAbout = { navController.navigate(Routes.ABOUT) },
                            onOpenSearch = { navController.navigate(Routes.SEARCH) },
                            onCategoryChange = viewModel::setScanCategory,
                            onDismissMessage = viewModel::dismissMessage,
                            greeting = firstName.takeIf { it.isNotBlank() }?.let { "Hi, $it" },
                            // A sheet is covering the screen, so there is
                            // nothing to scan. Release the camera.
                            cameraActive = state.analysis == null &&
                                state.selectedIngredient == null
                        )
                    }
                    composable(Routes.SEARCH) {
                        SearchScreen(
                            query = state.searchQuery,
                            results = state.searchResults,
                            onQueryChange = viewModel::onSearchQueryChange,
                            onBack = {
                                viewModel.clearSearch()
                                navController.popBackStack()
                            },
                            onSelect = viewModel::selectIngredient,
                            onLookupBarcode = { barcode ->
                                viewModel.lookupTypedBarcode(barcode)
                                navController.popBackStack()
                            }
                        )
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            scans = history,
                            // Reached from the bottom bar, so no back arrow.
                            onBack = null,
                            onOpen = viewModel::reopenScan,
                            onDelete = { id ->
                                scope.launch { container.repository.deleteScan(id) }
                            },
                            onClearAll = {
                                scope.launch { container.repository.clearHistory() }
                            }
                        )
                    }
                    composable(Routes.PROFILE) {
                        ProfileScreen(
                            profile = state.profile,
                            onBack = null,
                            onToggleAllergen = { value ->
                                scope.launch { container.profileStore.toggleAllergen(value) }
                            },
                            onToggleDiet = { value ->
                                scope.launch { container.profileStore.toggleDiet(value) }
                            },
                            onToggleCondition = { value ->
                                scope.launch { container.profileStore.toggleCondition(value) }
                            }
                        )
                    }
                    composable(Routes.ABOUT) {
                        AboutScreen(onBack = { navController.popBackStack() })
                    }
                }

                if (currentRoute in bottomBarRoutes) {
                    BottomBar(
                        tabs = BOTTOM_TABS,
                        currentRoute = currentRoute,
                        onSelect = { route ->
                            navController.navigate(route) {
                                // Tabs are siblings, not a stack. Without this,
                                // tapping between them piles up destinations and
                                // back becomes a tour of everywhere you have been.
                                popUpTo(Routes.HOME) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onScan = { navController.navigate(Routes.SCANNER) },
                        modifier = Modifier.align(Alignment.BottomCenter)
                    )
                }
                }

                // Result and detail are sheets layered over whatever screen is showing,
                // so a scan never loses the camera behind it.
                // Opens at full height rather than half, so a result never has to
                // be dragged up to be read. A scan result is the whole point of
                // the screen, not a preview of it, and leaving the scanner
                // visible behind a half sheet also made the app look like it was
                // still scanning after it had finished.
                val resultSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
                val detailSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

                state.analysis?.let { analysis ->
                    ResultSheet(
                        analysis = analysis,
                        sheetState = resultSheetState,
                        onDismiss = viewModel::dismissResult,
                        onIngredientClick = viewModel::selectIngredient
                    )
                }

                state.selectedIngredient?.let { ingredient ->
                    IngredientDetailSheet(
                        ingredient = ingredient,
                        sheetState = detailSheetState,
                        isBookmarked = ingredient.id in bookmarkedIds,
                        onToggleBookmark = {
                            scope.launch { container.repository.toggleBookmark(ingredient.id) }
                        },
                        onDismiss = { viewModel.selectIngredient(null) }
                    )
                }
            }
        }
    }
}

private object Routes {
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val SEARCH = "search"
    const val HISTORY = "history"
    const val BOOKMARKS = "bookmarks"
    const val PROFILE = "profile"
    const val ABOUT = "about"
}

/**
 * Bottom bar destinations, in display order.
 *
 * The scan action is not in this list: it is the raised centre button, and it
 * pushes a full-screen destination rather than switching tab.
 */
private val BOTTOM_TABS = listOf(
    BottomTab(Routes.HOME, "Home", R.drawable.ic_home),
    BottomTab(Routes.HISTORY, "History", R.drawable.ic_history),
    BottomTab(Routes.BOOKMARKS, "Saved", R.drawable.ic_bookmark),
    BottomTab(Routes.PROFILE, "Profile", R.drawable.ic_person)
)
