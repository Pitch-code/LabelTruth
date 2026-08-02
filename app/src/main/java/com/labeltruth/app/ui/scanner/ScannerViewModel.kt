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
import com.labeltruth.app.domain.model.ProductCategory
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
    val searchResults: List<Ingredient> = emptyList(),
    /**
     * For label scanning only. A barcode tells us the category by which
     * database answered, but a photograph of an ingredient list does not, and
     * guessing would risk showing a food verdict for a shampoo.
     */
    val scanCategory: ProductCategory = ProductCategory.FOOD
)

class ScannerViewModel(
    private val repository: AnalysisRepository,
    private val profileStore: ProfileStore
) : ViewModel() {

    private val _state = MutableStateFlow(ScannerUiState())
    val state: StateFlow<ScannerUiState> = _state.asStateFlow()

    /** Stops the same barcode being looked up over and over as frames stream in. */
    private var lastHandledBarcode: String? = null

    /**
     * Earliest time each barcode may be looked up again.
     *
     * The camera reports the same barcode many times a second, so suppressing
     * repeats by identity alone is not enough: any state reset re-opens the
     * floodgates. On a real phone this went wrong exactly that way. A product
     * missing from Open Food Facts cleared the guard, the next frame re-ran the
     * lookup, and each attempt made two HTTP requests (food, then beauty).
     * Within a minute Open Food Facts returned 429 Too Many Requests.
     *
     * A failure is remembered for much longer than a success, because a barcode
     * absent from the database will still be absent a second later, whereas
     * re-scanning a product you just looked at is a reasonable thing to do.
     */
    private val barcodeCooldown = mutableMapOf<String, Long>()

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

    fun setScanCategory(category: ProductCategory) {
        _state.value = _state.value.copy(scanCategory = category)
    }

    fun onBarcodeDetected(barcode: String) {
        val current = _state.value
        // An unread message means the previous attempt failed. Carrying on
        // scanning would repeat that failure at the camera frame rate, which is
        // precisely how this ended up rate-limited by Open Food Facts.
        if (current.isProcessing || current.analysis != null || current.message != null) return
        if (barcode == lastHandledBarcode) return
        if (isCoolingDown(barcode)) return
        lastHandledBarcode = barcode

        _state.value = current.copy(isProcessing = true, message = null)
        viewModelScope.launch {
            val outcome = repository.analyzeBarcode(barcode, _state.value.profile)
            holdOff(
                barcode,
                if (outcome is BarcodeOutcome.Success) SUCCESS_COOLDOWN_MS else FAILURE_COOLDOWN_MS
            )
            when (outcome) {
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
            val analysis = repository.analyzeScannedText(
                text = text,
                profile = _state.value.profile,
                category = _state.value.scanCategory
            )
            if (analysis.ingredients.isEmpty()) {
                fail("Could not read any ingredients. Hold steady and fill the frame with the ingredient list.")
            } else {
                _state.value = _state.value.copy(isProcessing = false, analysis = analysis)
            }
        }
    }

    /**
     * Reports a failure without re-arming the scanner.
     *
     * [lastHandledBarcode] is deliberately left set. Clearing it here was the
     * bug that caused runaway lookups: it made every failure immediately
     * retryable, and the camera obliges several times a second.
     */
    private fun fail(message: String) {
        _state.value = _state.value.copy(isProcessing = false, message = message)
    }

    private fun isCoolingDown(barcode: String): Boolean {
        val readyAt = barcodeCooldown[barcode] ?: return false
        if (System.currentTimeMillis() >= readyAt) {
            barcodeCooldown.remove(barcode)
            return false
        }
        return true
    }

    private fun holdOff(barcode: String, forMillis: Long) {
        // Bounded so a long shopping trip cannot grow this without limit.
        if (barcodeCooldown.size > MAX_REMEMBERED_BARCODES) {
            val now = System.currentTimeMillis()
            barcodeCooldown.entries.removeAll { it.value <= now }
            if (barcodeCooldown.size > MAX_REMEMBERED_BARCODES) barcodeCooldown.clear()
        }
        barcodeCooldown[barcode] = System.currentTimeMillis() + forMillis
    }

    fun dismissResult() {
        lastHandledBarcode = null
        _state.value = _state.value.copy(analysis = null, selectedIngredient = null)
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    /**
     * Reopens a saved scan from history.
     *
     * Recomputed rather than replayed from storage, so the result reflects the
     * current dictionary and the user's current profile.
     */
    fun reopenScan(id: Long) {
        if (_state.value.isProcessing) return
        _state.value = _state.value.copy(isProcessing = true, message = null)

        viewModelScope.launch {
            val analysis = repository.replayScan(id, _state.value.profile)
            if (analysis == null) {
                fail("That scan could not be opened. It may have been deleted.")
            } else {
                _state.value = _state.value.copy(isProcessing = false, analysis = analysis)
            }
        }
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

    /**
     * Looks up a barcode the user typed in rather than scanned.
     *
     * Worth having for a scratched or badly lit barcode, and for checking a
     * product before buying it. Unlike the camera path this has no
     * already-handled guard, because repeating a deliberate action should work.
     */
    fun lookupTypedBarcode(barcode: String) {
        if (_state.value.isProcessing) return
        lastHandledBarcode = null
        _state.value = _state.value.copy(isProcessing = true, message = null)

        viewModelScope.launch {
            when (val outcome = repository.analyzeBarcode(barcode, _state.value.profile)) {
                is BarcodeOutcome.Success ->
                    _state.value = _state.value.copy(
                        isProcessing = false,
                        analysis = outcome.analysis,
                        searchQuery = "",
                        searchResults = emptyList()
                    )

                BarcodeOutcome.NotFound -> fail(
                    "Barcode $barcode is not in the Open Food Facts or Open Beauty " +
                        "Facts databases yet. Try Label mode and scan the printed " +
                        "ingredient list instead."
                )

                is BarcodeOutcome.NoIngredients -> fail(
                    "Found \"${outcome.productName}\" but it has no ingredient list " +
                        "on record. Try Label mode."
                )

                is BarcodeOutcome.Error -> fail(outcome.message)
            }
        }
    }

    companion object {
        /** Re-scanning a product you just looked at is reasonable, so this is short. */
        private const val SUCCESS_COOLDOWN_MS = 10_000L

        /**
         * A barcode that is not in the database will still not be in it a moment
         * later, so there is nothing to gain from asking again quickly.
         */
        private const val FAILURE_COOLDOWN_MS = 5 * 60_000L

        private const val MAX_REMEMBERED_BARCODES = 256

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
