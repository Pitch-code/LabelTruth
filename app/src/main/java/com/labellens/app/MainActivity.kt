package com.labellens.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.labellens.app.ui.DisclaimerDialog
import com.labellens.app.ui.about.AboutScreen
import com.labellens.app.ui.detail.IngredientDetailSheet
import com.labellens.app.ui.history.HistoryScreen
import com.labellens.app.ui.profile.ProfileScreen
import com.labellens.app.ui.result.ResultSheet
import com.labellens.app.ui.scanner.ScannerScreen
import com.labellens.app.ui.scanner.ScannerViewModel
import com.labellens.app.ui.theme.LabelLensTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val container = (application as LabelLensApp).container

        setContent {
            LabelLensTheme {
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

                NavHost(navController = navController, startDestination = Routes.SCANNER) {
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
                            onDismissMessage = viewModel::dismissMessage
                        )
                    }
                    composable(Routes.HISTORY) {
                        HistoryScreen(
                            scans = history,
                            onBack = { navController.popBackStack() },
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
                            onBack = { navController.popBackStack() },
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

                // Result and detail are sheets layered over whatever screen is showing,
                // so a scan never loses the camera behind it.
                val resultSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
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
                        onDismiss = { viewModel.selectIngredient(null) }
                    )
                }
            }
        }
    }
}

private object Routes {
    const val SCANNER = "scanner"
    const val HISTORY = "history"
    const val PROFILE = "profile"
    const val ABOUT = "about"
}
