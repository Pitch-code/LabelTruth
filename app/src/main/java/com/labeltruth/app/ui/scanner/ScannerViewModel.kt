package com.labeltruth.app.ui.scanner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.labeltruth.app.data.prefs.ProfileStore
import com.labeltruth.app.data.repo.AnalysisRepository
import com.labeltruth.app.data.repo.BarcodeOutcome
import com.labeltruth.app.domain.model.Analysis
import com.labeltruth.app.domain.model.HealthProfile
import com.labeltruth.app.domain.model.Ingredient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

data class ScannerUiState(
    val mode: ScanMode = ScanMode.BARCODE,
    val isProcessing: Boolean = false,
    val torchOn: Boolean = false,
    val analysis: Analysis? = null,
    val selectedIngredient: Ingredient? = null,
    val message: String? = null,
    val profile: HealthProfile = HealthProfile(),
    val searchQuery: String = "",
    val searchResults: List<Ingredient> = emptyList()
)

class ScannerViewModel(
    private val repository: AnalysisRepository,
    private val profileStore: ProfileStore
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    /** Stops the same barcode being looked up over and over as frames stream in. */
    private var lastHandledBarcode: String? = null

    init {
        profileStore.profile
            .onEach { profile -> _state.value = _state.value.copy(profile = profile) }
            .launchIn(viewModelScope)
    }

    fun setMode(mode: ScanMode) {
        lastHandledBarcode = null
        _state.value = _state.value.copy(mode = mode, message = null)
    }

    fun toggleTorch() {
        _state.value = _state.value.copy(torchOn = !_state.value.torchOn)
    }

    fun onBarcodeDetected(barcode: String) {
        val current = _state.value
        if (current.isProcessing || current.analysis != null) return
        if (barcode == lastHandledBarcode) return
        lastHandledBarcode = barcode

        _state.value = current.copy(isProcessing = true, message = null)
        viewModelScope.launch {
            when (val outcome = repository.analyzeBarcode(barcode, _state.value.profile)) {
                is BarcodeOutcome.Success ->
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        analysis = outcome.analysis
                    )

                BarcodeOutcome.NotFound -> fail(
                    "This product is not in the Open Food Facts database yet. " +
                        "Switch to Label mode and scan the printed ingredient list instead."
                )

                is BarcodeOutcome.NoIngredients -> fail(
                    "Found \"${outcome.productName}\" but it has no ingredient list on record. " +
                        "Try Label mode."
                )

                is BarcodeOutcome.Error -> fail(outcome.message)
            }
        }
    }

    fun onLabelTextCaptured(text: String) {
        val current = _state.value
        if (current.isProcessing || current.analysis != null) return

        _state.value = current.copy(isProcessing = true, message = null)
        viewModelScope.launch {
            val analysis = repository.analyzeScannedText(text, _state.value.profile)
            if (analysis.ingredients.isEmpty()) {
                fail("Could not read any ingredients. Hold steady and fill the frame with the ingredient list.")
            } else {
                _state.value = _state.value.copy(isProcessing = false, analysis = analysis)
            }
        }
    }

    private fun fail(message: String) {
        lastHandledBarcode = null
        _state.value = _state.value.copy(isProcessing = false, message = message)
    }

    fun dismissResult() {
        lastHandledBarcode = null
        _state.value = _state.value.copy(analysis = null, selectedIngredient = null)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    fun selectIngredient(ingredient: Ingredient?) {
        _state.value = _state.value.copy(selectedIngredient = ingredient)
    }

    private var searchJob: Job? = null

    /**
     * Debounced dictionary search. Each keystroke cancels the previous query so
     * we do not run a LIKE scan over thousands of rows per character typed.
     */
    fun onSearchQueryChange(query: String) {
        _state.value = _state.value.copy(searchQuery = query)
        searchJob?.cancel()

        if (query.isBlank()) {
            _state.value = _state.value.copy(searchResults = emptyList())
            return
        }
        searchJob = viewModelScope.launch {
            delay(180)
            val results = repository.searchIngredients(query)
            _state.value = _state.value.copy(searchResults = results)
        }
    }

    fun clearSearch() {
        searchJob?.cancel()
        _state.value = _state.value.copy(searchQuery = "", searchResults = emptyList())
    }

    companion object {
        fun factory(
            repository: AnalysisRepository,
            profileStore: ProfileStore
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                ScannerViewModel(repository, profileStore) as T
        }
    }
}
