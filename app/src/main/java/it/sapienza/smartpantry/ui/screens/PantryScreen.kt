package it.sapienza.smartpantry.ui.screens

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.*
import it.sapienza.smartpantry.service.HomeAddNutrients
import it.sapienza.smartpantry.service.HomeAddRequest
import it.sapienza.smartpantry.service.PantryAddNutrients
import it.sapienza.smartpantry.service.PantryAddRequest
import it.sapienza.smartpantry.service.PantryGramsRequest
import it.sapienza.smartpantry.service.RetrofitClient
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import retrofit2.Response

data class PantryUiState(
    val searchQuery: String = "",
    val hasSearched: Boolean = false,
    val searchResults: List<OpenFoodFactsProduct> = emptyList(),
    val pantryItems: List<PantryItem> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val isScanning: Boolean = false,
    val isEditorVisible: Boolean = false,
    val editorNameInput: String = "",
    val editorQuantityInput: String = "1",
    val editorKcalInput: String = "0",
    val editorCarbsInput: String = "0",
    val editorProtInput: String = "0",
    val editorFatInput: String = "0",
    val editorPackageWeightGramsInput: String = "0"
)

class PantryViewModel : ViewModel() {
    companion object {
        const val SEARCH_MODE_PANTRY = "pantry"
        const val SEARCH_MODE_HOME = "home"
        private val HOME_MEAL_TYPES = setOf("breakfast", "lunch", "dinner", "snacks")
    }

    data class BarcodeResolution(
        val openFoodFactsId: String? = null,
        val productName: String? = null,
        val kcal: Double? = null,
        val prot: Double? = null,
        val fat: Double? = null,
        val carbs: Double? = null,
        val packageWeightGrams: Double? = null,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean
            get() = errorMessage == null && !openFoodFactsId.isNullOrBlank()
    }

    private sealed interface EditorTarget {
        data class AddWithId(val openFoodFactsId: String) : EditorTarget
        data object AddManual : EditorTarget
    }

    private val api = RetrofitClient.instance
    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    private val _saveCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveCompleted = _saveCompleted.asSharedFlow()

    private var currentUid = ""
    private var currentEditorTarget: EditorTarget? = null
    private var searchJob: Job? = null
    private var latestSearchToken: Long = 0L
    private var activeSearchMode = SEARCH_MODE_PANTRY
    private var activeHomeDateKey: String? = null
    private var activeHomeMealType: String? = null

    fun configureSearchSession(
        mode: String,
        homeDateKey: String? = null,
        homeMealType: String? = null
    ) {
        val normalizedMode = mode.trim().lowercase(Locale.ROOT)
        val normalizedMealType = homeMealType?.trim()?.lowercase(Locale.ROOT)
        val isValidHomeSession = normalizedMode == SEARCH_MODE_HOME &&
            !homeDateKey.isNullOrBlank() &&
            !normalizedMealType.isNullOrBlank() &&
            normalizedMealType in HOME_MEAL_TYPES

        if (isValidHomeSession) {
            activeSearchMode = SEARCH_MODE_HOME
            activeHomeDateKey = homeDateKey
            activeHomeMealType = normalizedMealType
        } else {
            activeSearchMode = SEARCH_MODE_PANTRY
            activeHomeDateKey = null
            activeHomeMealType = null
        }
    }

    fun bindToUser(uid: String, refreshPantry: Boolean = true) {
        if (uid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        currentUid = uid
        if (refreshPantry) {
            refreshPantry()
        } else {
            _uiState.update { it.copy(pantryItems = emptyList()) }
        }
    }

    fun refreshPantry() {
        if (currentUid.isBlank()) return
        viewModelScope.launch {
            val result = refreshPantryFromBackend(currentUid)
            if (result.isFailure) {
                emitEvent(
                    "Unable to load pantry: ${result.exceptionOrNull()?.localizedMessage ?: "unknown error"}"
                )
            }
        }
    }

    fun onSearchQueryChange(value: String) = _uiState.update { it.copy(searchQuery = value) }
    fun onScanStateChanged(value: Boolean) = _uiState.update { it.copy(isScanning = value) }
    fun onEditorNameChange(value: String) = _uiState.update { it.copy(editorNameInput = value) }
    fun onEditorQuantityChange(value: String) = _uiState.update { it.copy(editorQuantityInput = value) }
    fun onEditorKcalChange(value: String) = _uiState.update { it.copy(editorKcalInput = value) }
    fun onEditorCarbsChange(value: String) = _uiState.update { it.copy(editorCarbsInput = value) }
    fun onEditorProtChange(value: String) = _uiState.update { it.copy(editorProtInput = value) }
    fun onEditorFatChange(value: String) = _uiState.update { it.copy(editorFatInput = value) }
    fun onEditorPackageWeightGramsChange(value: String) = _uiState.update {
        it.copy(editorPackageWeightGramsInput = value)
    }

    fun dismissEditor() {
        currentEditorTarget = null
        _uiState.update { it.copy(isEditorVisible = false) }
    }

    private fun isHomeModeActive(): Boolean {
        return activeSearchMode == SEARCH_MODE_HOME &&
            !activeHomeDateKey.isNullOrBlank() &&
            !activeHomeMealType.isNullOrBlank()
    }

    fun searchProducts() {
        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        val query = _uiState.value.searchQuery.trim()
        if (query.length < 2) {
            emitEvent("Enter at least 2 characters to search.")
            return
        }

        searchJob?.cancel()
        val searchToken = ++latestSearchToken
        _uiState.update { it.copy(isSearching = true, hasSearched = true) }
        searchJob = viewModelScope.launch {
            try {
                val result = searchProductsFromBackend(
                    query = query,
                    similar = true,
                    limit = 15,
                    lang = "it"
                )

                if (searchToken != latestSearchToken) return@launch

                if (result.isSuccess) {
                    _uiState.update { it.copy(searchResults = result.getOrNull().orEmpty()) }
                } else {
                    _uiState.update { it.copy(searchResults = emptyList()) }
                    emitEvent(
                        "Search failed: ${result.exceptionOrNull()?.localizedMessage ?: "unknown error"}"
                    )
                }
            } catch (error: Throwable) {
                if (searchToken == latestSearchToken) {
                    _uiState.update { it.copy(searchResults = emptyList()) }
                    emitEvent("Search failed: ${error.localizedMessage ?: "unknown error"}")
                }
            } finally {
                if (searchToken == latestSearchToken) {
                    _uiState.update { it.copy(isSearching = false) }
                }
            }
        }
    }

    fun openEditorFromSearchResult(product: OpenFoodFactsProduct) {
        val openFoodFactsId = product.resolvedOpenFoodFactsId().orEmpty()
        if (openFoodFactsId.isBlank()) {
            emitEvent("Invalid product code.")
            return
        }
        val defaultAmount = if (isHomeModeActive()) 100L else 1L
        currentEditorTarget = EditorTarget.AddWithId(openFoodFactsId)
        openEditorWithValues(
            name = displayName(product),
            quantity = defaultAmount,
            kcal = product.resolvedKcal(),
            carbs = product.resolvedCarbs(),
            prot = product.resolvedProt(),
            fat = product.resolvedFat(),
            packageWeightGrams = product.resolvedPackageWeightGrams()
        )
    }

    fun openEditorFromManualEntry() {
        val defaultAmount = if (isHomeModeActive()) 100L else 1L
        currentEditorTarget = EditorTarget.AddManual
        openEditorWithValues(
            name = _uiState.value.searchQuery.trim(),
            quantity = defaultAmount,
            kcal = 0.0,
            carbs = 0.0,
            prot = 0.0,
            fat = 0.0,
            packageWeightGrams = 0.0
        )
    }

    fun deleteItem(item: PantryItem) {
        submitItemGramsUpdate(
            item = item,
            targetGrams = 0.0,
            successMessage = "Item removed from pantry."
        )
    }

    fun updateItemGramsByDelta(item: PantryItem, rawDelta: String) {
        val delta = parseSignedDecimalOrNull(rawDelta, "Grams delta") ?: return
        val currentGrams = item.resolvedGrams() ?: 0.0
        val targetGrams = (currentGrams + delta).coerceAtLeast(0.0)
        if (abs(targetGrams - currentGrams) < 1e-9) {
            emitEvent("No grams change detected.")
            return
        }

        submitItemGramsUpdate(
            item = item,
            targetGrams = targetGrams,
            successMessage = if (targetGrams <= 0.0) "Item removed from pantry." else "Item grams updated."
        )
    }

    private fun submitItemGramsUpdate(
        item: PantryItem,
        targetGrams: Double,
        successMessage: String
    ) {
        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        val openFoodFactsId = item.openFoodFactsId.takeIf { it.isNotBlank() }
        val productName = item.productName.takeIf { it.isNotBlank() }
        if (openFoodFactsId.isNullOrBlank() && productName.isNullOrBlank()) {
            emitEvent("Missing item identifier.")
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = updateItemGrams(
                    uid = currentUid,
                    openFoodFactsId = openFoodFactsId,
                    productName = productName,
                    grams = targetGrams
                )
                if (result.isSuccess) {
                    val refresh = refreshPantryFromBackend(currentUid)
                    if (refresh.isFailure) {
                        emitEvent(
                            "Unable to load pantry: ${
                                refresh.exceptionOrNull()?.localizedMessage ?: "unknown error"
                            }"
                        )
                    } else {
                        emitEvent(successMessage)
                    }
                } else {
                    emitEvent(
                        "Operation failed: ${result.exceptionOrNull()?.localizedMessage ?: "unknown error"}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun saveEditor() {
        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        val target = currentEditorTarget ?: run {
            emitEvent("No selected action.")
            return
        }
        val state = _uiState.value
        val name = state.editorNameInput.trim()
        if (name.isBlank()) {
            emitEvent("Food name is required.")
            return
        }
        val isHomeMode = isHomeModeActive()
        val grams = if (isHomeMode) {
            parsePositiveDecimalOrNull(state.editorQuantityInput, "Grams") ?: return
        } else {
            null
        }
        val quantity = if (isHomeMode) {
            null
        } else {
            parseQuantityOrNull(state.editorQuantityInput) ?: return
        }
        val kcal = parseMacroOrNull(state.editorKcalInput, "Kcal") ?: return
        val carbs = parseMacroOrNull(state.editorCarbsInput, "Carbs") ?: return
        val prot = parseMacroOrNull(state.editorProtInput, "Protein") ?: return
        val fat = parseMacroOrNull(state.editorFatInput, "Fat") ?: return
        val packageWeightGrams = if (isHomeMode) {
            0.0
        } else {
            parseMacroOrNull(
                state.editorPackageWeightGramsInput,
                "Package weight (g)"
            ) ?: return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = if (isHomeMode) {
                    val dateKey = activeHomeDateKey
                    val mealType = activeHomeMealType
                    if (dateKey.isNullOrBlank() || mealType.isNullOrBlank()) {
                        emitEvent("Missing home date or meal.")
                        return@launch
                    }
                    when (target) {
                        is EditorTarget.AddWithId -> addHomeItem(
                            uid = currentUid,
                            dateKey = dateKey,
                            mealType = mealType,
                            openFoodFactsId = target.openFoodFactsId,
                            source = "openfoodfacts",
                            productName = name,
                            grams = grams ?: 0.0,
                            kcal = kcal,
                            prot = prot,
                            fat = fat,
                            carbs = carbs
                        )
                        is EditorTarget.AddManual -> addHomeItem(
                            uid = currentUid,
                            dateKey = dateKey,
                            mealType = mealType,
                            openFoodFactsId = null,
                            source = "manual",
                            productName = name,
                            grams = grams ?: 0.0,
                            kcal = kcal,
                            prot = prot,
                            fat = fat,
                            carbs = carbs
                        )
                    }
                } else {
                    when (target) {
                        is EditorTarget.AddWithId -> addPantryItem(
                            currentUid,
                            target.openFoodFactsId,
                            name,
                            quantity ?: 1L,
                            kcal,
                            prot,
                            fat,
                            carbs,
                            packageWeightGrams
                        )
                        is EditorTarget.AddManual -> addPantryItem(
                            currentUid,
                            null,
                            name,
                            quantity ?: 1L,
                            kcal,
                            prot,
                            fat,
                            carbs,
                            packageWeightGrams
                        )
                    }
                }
                if (result.isSuccess) {
                    if (!isHomeMode) {
                        val refresh = refreshPantryFromBackend(currentUid)
                        if (refresh.isFailure) {
                            emitEvent(
                                "Unable to load pantry: ${
                                    refresh.exceptionOrNull()?.localizedMessage ?: "unknown error"
                                }"
                            )
                        }
                    } else {
                        _saveCompleted.tryEmit(Unit)
                    }
                    _uiState.update { it.copy(isEditorVisible = false) }
                    currentEditorTarget = null
                    emitEvent(
                        if (isHomeMode) "Item added to ${activeHomeMealType.orEmpty()}."
                        else "Item added to pantry."
                    )
                } else {
                    emitEvent(
                        "Operation failed: ${result.exceptionOrNull()?.localizedMessage ?: "unknown error"}"
                    )
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun handleScannedBarcode(scannedCode: String) {
        if (currentUid.isBlank()) {
            onScanStateChanged(false)
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        val cleanCode = scannedCode.trim()
        if (cleanCode.isBlank()) {
            onScanStateChanged(false)
            emitEvent("Invalid barcode.")
            return
        }
        viewModelScope.launch {
            try {
                val resolution = resolveBarcodeFromBackend(cleanCode)
                if (!resolution.isSuccess) {
                    emitEvent(resolution.errorMessage ?: "Barcode lookup failed.")
                    return@launch
                }
                currentEditorTarget = EditorTarget.AddWithId(resolution.openFoodFactsId.orEmpty())
                openEditorWithValues(
                    name = resolution.productName ?: "Unnamed product",
                    quantity = 1L,
                    kcal = resolution.kcal,
                    carbs = resolution.carbs,
                    prot = resolution.prot,
                    fat = resolution.fat,
                    packageWeightGrams = resolution.packageWeightGrams
                )
            } catch (error: Throwable) {
                emitEvent("Operation failed: ${error.localizedMessage ?: "unknown error"}")
            } finally {
                onScanStateChanged(false)
            }
        }
    }

    private fun openEditorWithValues(
        name: String,
        quantity: Long,
        kcal: Double?,
        carbs: Double?,
        prot: Double?,
        fat: Double?,
        packageWeightGrams: Double?
    ) {
        _uiState.update {
            it.copy(
                isEditorVisible = true,
                editorNameInput = name,
                editorQuantityInput = quantity.coerceAtLeast(1L).toString(),
                editorKcalInput = formatDecimalInput(kcal),
                editorCarbsInput = formatDecimalInput(carbs),
                editorProtInput = formatDecimalInput(prot),
                editorFatInput = formatDecimalInput(fat),
                editorPackageWeightGramsInput = formatDecimalInput(packageWeightGrams)
            )
        }
    }

    private fun parseQuantityOrNull(value: String): Long? {
        val quantity = value.trim().toLongOrNull()
        if (quantity == null || quantity <= 0L) {
            emitEvent("Quantity must be greater than 0.")
            return null
        }
        return quantity
    }

    private fun parsePositiveDecimalOrNull(value: String, fieldName: String): Double? {
        val parsed = value.trim().replace(',', '.').toDoubleOrNull()
        if (parsed == null || !parsed.isFinite() || parsed <= 0.0) {
            emitEvent("$fieldName must be a number greater than 0.")
            return null
        }
        return parsed
    }

    private fun parseMacroOrNull(value: String, fieldName: String): Double? {
        val parsed = value.trim().replace(',', '.').toDoubleOrNull()
        if (parsed == null || parsed < 0.0) {
            emitEvent("$fieldName must be a number greater than or equal to 0.")
            return null
        }
        return parsed
    }

    private fun parseSignedDecimalOrNull(value: String, fieldName: String): Double? {
        val parsed = value.trim().replace(',', '.').toDoubleOrNull()
        if (parsed == null || !parsed.isFinite()) {
            emitEvent("$fieldName must be a valid number.")
            return null
        }
        return parsed
    }

    private fun emitEvent(message: String) = _events.tryEmit(message)

    private suspend fun refreshPantryFromBackend(uid: String): Result<Unit> {
        val result = loadPantry(uid)
        return if (result.isSuccess) {
            _uiState.update { it.copy(pantryItems = result.getOrNull().orEmpty()) }
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(result.exceptionOrNull()?.localizedMessage ?: "unknown error")
            )
        }
    }

    private suspend fun searchProductsFromBackend(
        query: String,
        similar: Boolean = true,
        limit: Int = 15,
        lang: String = "it"
    ): Result<List<OpenFoodFactsProduct>> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchProducts(
                query = query,
                similar = similar,
                limit = limit,
                lang = lang
            ).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException(parseBackendError(response, "Search failed."))
                )
            }

            val body = response.body()
            val ranked = if (!body?.products.isNullOrEmpty()) {
                body?.products.orEmpty()
            } else {
                body?.recommended.orEmpty()
            }

            val sanitized = ranked
                .filter { !it.resolvedOpenFoodFactsId().isNullOrBlank() }
                .distinctBy { it.resolvedOpenFoodFactsId() }
                .take(limit)

            Result.success(sanitized)
        } catch (error: Exception) {
            Result.failure(IllegalStateException(error.localizedMessage ?: "Search failed.", error))
        }
    }

    private suspend fun resolveBarcodeFromBackend(barcode: String): BarcodeResolution = withContext(Dispatchers.IO) {
        try {
            val response = api.getProductByBarcode(barcode).execute()
            if (response.isSuccessful) {
                return@withContext parseBarcodeSuccess(response.body(), barcode)
            }

            return@withContext when {
                response.code() == 404 -> BarcodeResolution(
                    errorMessage = "Product not found on OpenFoodFacts."
                )
                response.code() == 502 || response.code() >= 500 -> BarcodeResolution(
                    errorMessage = "OpenFoodFacts is temporarily unavailable."
                )
                else -> BarcodeResolution(
                    errorMessage = parseBackendError(response, "Barcode lookup failed.")
                )
            }
        } catch (_: Exception) {
            BarcodeResolution(errorMessage = "Barcode lookup failed.")
        }
    }

    private suspend fun loadPantry(uid: String): Result<List<PantryItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPantry(uid).execute()
            if (response.isSuccessful) {
                Result.success(response.body()?.items.orEmpty())
            } else {
                Result.failure(
                    IllegalStateException(
                        parseBackendError(response, "Unable to load pantry.")
                    )
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to load pantry."))
        }
    }

    private suspend fun addPantryItem(
        uid: String,
        openFoodFactsId: String?,
        productName: String,
        quantity: Long,
        kcal: Double,
        prot: Double,
        fat: Double,
        carbs: Double,
        packageWeightGrams: Double?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val quantityValue = quantity.toIntOrNullChecked()
            ?: return@withContext Result.failure(
                IllegalArgumentException("Quantity must be greater than 0.")
            )

        try {
            val normalizedRequest = normalizeAddPayload(
                uid = uid,
                openFoodFactsId = openFoodFactsId,
                productName = productName,
                quantity = quantityValue,
                kcal = kcal,
                prot = prot,
                fat = fat,
                carbs = carbs,
                packageWeightGrams = packageWeightGrams
            )

            val response = api.addToPantry(
                normalizedRequest
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to add item."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to add item."))
        }
    }

    private suspend fun addHomeItem(
        uid: String,
        dateKey: String,
        mealType: String,
        openFoodFactsId: String?,
        source: String,
        productName: String,
        grams: Double,
        kcal: Double,
        prot: Double,
        fat: Double,
        carbs: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (dateKey.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("dateKey is required."))
        }
        if (mealType !in HOME_MEAL_TYPES) {
            return@withContext Result.failure(IllegalArgumentException("Invalid meal type."))
        }
        if (!grams.isFinite() || grams <= 0.0) {
            return@withContext Result.failure(IllegalArgumentException("Grams must be greater than 0."))
        }

        try {
            val response = api.addHomeEntry(
                HomeAddRequest(
                    uid = uid,
                    dateKey = dateKey,
                    openFoodFactsId = openFoodFactsId?.takeIf { it.isNotBlank() },
                    mealType = mealType,
                    source = source,
                    productName = productName.trim().ifBlank { "Unnamed product" },
                    grams = sanitizeMacro(grams),
                    nutrients = HomeAddNutrients(
                        kcal = sanitizeMacro(kcal),
                        carbs = sanitizeMacro(carbs),
                        protein = sanitizeMacro(prot),
                        fat = sanitizeMacro(fat)
                    )
                )
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to add item to home."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to add item to home."))
        }
    }

    private suspend fun updateItemGrams(
        uid: String,
        openFoodFactsId: String?,
        productName: String?,
        grams: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (!grams.isFinite() || grams < 0.0) {
            return@withContext Result.failure(
                IllegalArgumentException("Grams must be 0 or greater.")
            )
        }
        if (openFoodFactsId.isNullOrBlank() && productName.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Missing item identifier.")
            )
        }

        try {
            val response = api.updateItemGrams(
                PantryGramsRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    productName = productName,
                    grams = sanitizeMacro(grams)
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to update grams."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to update grams."))
        }
    }

    private fun parseBarcodeSuccess(
        body: OpenFoodFactsProductResponse?,
        fallbackBarcode: String
    ): BarcodeResolution {
        val product = body?.product
        val resolvedId = product?.resolvedOpenFoodFactsId().orEmpty().ifBlank { fallbackBarcode }
        val resolvedName = product?.productName?.trim().orEmpty().ifBlank { "Unnamed product" }
        val resolvedKcal = product?.resolvedKcal()
        val resolvedProt = product?.resolvedProt()
        val resolvedFat = product?.resolvedFat()
        val resolvedCarbs = product?.resolvedCarbs()
        val resolvedPackageWeightGrams = product?.resolvedPackageWeightGrams()

        return if (body?.status == 1 && resolvedId.isNotBlank()) {
            BarcodeResolution(
                openFoodFactsId = resolvedId,
                productName = resolvedName,
                kcal = resolvedKcal,
                prot = resolvedProt,
                fat = resolvedFat,
                carbs = resolvedCarbs,
                packageWeightGrams = resolvedPackageWeightGrams
            )
        } else {
            BarcodeResolution(errorMessage = "Product not found on OpenFoodFacts.")
        }
    }

    private fun parseBackendError(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return fallback
            val json = JSONObject(raw)
            val errorText = json.optString("error")
            val messageText = json.optString("message")
            when {
                errorText.isNotBlank() -> errorText
                messageText.isNotBlank() -> messageText
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun normalizeAddPayload(
        uid: String,
        openFoodFactsId: String?,
        productName: String,
        quantity: Int,
        kcal: Double,
        prot: Double,
        fat: Double,
        carbs: Double,
        packageWeightGrams: Double?
    ): PantryAddRequest {
        val normalizedName = productName.trim().ifBlank { "Unnamed product" }
        val normalizedNutrients = PantryAddNutrients(
            kcal = sanitizeMacro(kcal),
            carbs = sanitizeMacro(carbs),
            fat = sanitizeMacro(fat),
            protein = sanitizeMacro(prot)
        )

        return PantryAddRequest(
            uid = uid,
            openFoodFactsId = openFoodFactsId?.takeIf { it.isNotBlank() },
            productName = normalizedName,
            quantity = quantity,
            nutrients = normalizedNutrients,
            packageWeightGrams = sanitizeMacro(packageWeightGrams ?: 0.0)
        )
    }

    private fun sanitizeMacro(value: Double): Double {
        return if (value.isFinite() && value >= 0.0) value else 0.0
    }

    private fun Long.toIntOrNullChecked(allowZero: Boolean = false): Int? {
        if (this < Int.MIN_VALUE.toLong() || this > Int.MAX_VALUE.toLong()) return null
        val intValue = this.toInt()
        return if (allowZero) {
            intValue.takeIf { it >= 0 }
        } else {
            intValue.takeIf { it > 0 }
        }
    }
}

@Composable
fun PantryScreen(
    uid: String,
    onOpenSearchFood: () -> Unit,
    pantryViewModel: PantryViewModel = viewModel()
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by pantryViewModel.uiState.collectAsState()
    var pendingDeletionItem by remember { mutableStateOf<PantryItem?>(null) }
    var pendingGramsEditItem by remember { mutableStateOf<PantryItem?>(null) }
    var gramsDeltaInput by rememberSaveable { mutableStateOf("0") }
    var selectedCategory by rememberSaveable { mutableStateOf<PantryCategory?>(null) }
    val categorizedItems = remember(uiState.pantryItems) {
        groupPantryItemsByCategory(uiState.pantryItems)
    }
    val filteredItems = remember(uiState.pantryItems, selectedCategory) {
        selectedCategory?.let { category -> categorizedItems[category].orEmpty() } ?: uiState.pantryItems
    }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) pantryViewModel.bindToUser(uid)
    }
    LaunchedEffect(Unit) {
        pantryViewModel.events.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    DisposableEffect(lifecycleOwner, uid) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && uid.isNotBlank()) {
                pantryViewModel.refreshPantry()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (uid.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(PantryBackgroundColor)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("User not authenticated. Please log in again.", color = Color.White)
        }
        return
    }

    Scaffold(
        containerColor = PantryBackgroundColor,
        floatingActionButton = {
            FloatingActionButton(
                onClick = onOpenSearchFood,
                containerColor = PantryAccentColor,
                contentColor = Color.Black,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            val currentCategory = selectedCategory
            Text(
                text = if (currentCategory == null) {
                    "${uiState.pantryItems.size} ITEMS TOTAL"
                } else {
                    "${currentCategory.label.uppercase()}: ${filteredItems.size} ITEMS"
                },
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = PantryAccentColor
            )

            Spacer(modifier = Modifier.height(12.dp))

            PantryCategoryLabels(
                selectedCategory = selectedCategory,
                categoryCounts = categorizedItems.mapValues { it.value.size },
                onCategorySelected = { selectedCategory = it }
            )

            Spacer(modifier = Modifier.height(16.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                if (uiState.pantryItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Your pantry is empty.", color = Color.Gray)
                    }
                } else if (filteredItems.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No items in this category.", color = Color.Gray)
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(
                            filteredItems,
                            key = { "${it.openFoodFactsId}_${it.productName}" }
                        ) { item ->
                            PantryItemCard(
                                item = item,
                                isSaving = uiState.isSaving,
                                onEditGramsRequested = {
                                    pendingGramsEditItem = item
                                    gramsDeltaInput = "0"
                                },
                                onDeleteRequested = { pendingDeletionItem = item }
                            )
                        }
                    }
                }
            }
        }
    }

    pendingGramsEditItem?.let { item ->
        val currentGrams = item.resolvedGrams() ?: 0.0
        val parsedDelta = parseSignedDecimalInput(gramsDeltaInput)
        val nextGrams = (currentGrams + (parsedDelta ?: 0.0)).coerceAtLeast(0.0)

        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSaving) pendingGramsEditItem = null
            },
            containerColor = PantryCardColor,
            titleContentColor = Color.White,
            textContentColor = Color.Gray,
            title = { Text("Update grams") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Current grams: ${formatGrams(currentGrams)}")
                    OutlinedTextField(
                        value = gramsDeltaInput,
                        onValueChange = { gramsDeltaInput = it },
                        label = { Text("Delta grams (+/-)") },
                        singleLine = true,
                        enabled = !uiState.isSaving,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    )
                    Text("New grams: ${formatGrams(nextGrams)}")
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pantryViewModel.updateItemGramsByDelta(item, gramsDeltaInput)
                        pendingGramsEditItem = null
                    },
                    enabled = !uiState.isSaving && parsedDelta != null
                ) {
                    Text("Save", color = PantryAccentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingGramsEditItem = null },
                    enabled = !uiState.isSaving
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

    pendingDeletionItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSaving) pendingDeletionItem = null
            },
            containerColor = PantryCardColor,
            titleContentColor = Color.White,
            textContentColor = Color.Gray,
            title = { Text("Remove item?") },
            text = { Text("Are you sure you want to remove this item from the pantry?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pantryViewModel.deleteItem(item)
                        pendingDeletionItem = null
                    },
                    enabled = !uiState.isSaving
                ) {
                    Text("Remove", color = PantryAccentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeletionItem = null },
                    enabled = !uiState.isSaving
                ) {
                    Text("Cancel", color = Color.Gray)
                }
            }
        )
    }

}

private val PantryBackgroundColor = Color(0xFF0A120E)
private val PantryCardColor = Color(0xFF1A2421)
private val PantryChipColor = Color(0xFF1A2421)
private val PantryAccentColor = Color(0xFF00E676)
private val PantryBorderColor = Color(0xFF2C2E33)

private enum class PantryCategory(val label: String) {
    PROTEIN("Protein"),
    CARBS("Carbs"),
    FAT("Fat"),
    OTHER("Other")
}

@Composable
private fun PantryCategoryLabels(
    selectedCategory: PantryCategory?,
    categoryCounts: Map<PantryCategory, Int>,
    onCategorySelected: (PantryCategory?) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PantryCategoryChip(
            text = "ALL (${categoryCounts.values.sum()})",
            isSelected = selectedCategory == null,
            onClick = { onCategorySelected(null) }
        )
        PantryCategory.entries.forEach { category ->
            PantryCategoryChip(
                text = "${category.label.uppercase()} (${categoryCounts[category] ?: 0})",
                isSelected = selectedCategory == category,
                onClick = { onCategorySelected(category) }
            )
        }
    }
}

@Composable
private fun PantryCategoryChip(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = if (isSelected) PantryAccentColor else PantryChipColor,
        border = if (isSelected) null else BorderStroke(1.dp, PantryBorderColor)
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
            color = if (isSelected) Color.Black else Color.White
        )
    }
}

@Composable
private fun PantryItemCard(
    item: PantryItem,
    isSaving: Boolean,
    onEditGramsRequested: () -> Unit,
    onDeleteRequested: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = PantryCardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier
                    .width(58.dp)
                    .height(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = PantryBackgroundColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = formatGramsBadge(item.resolvedGrams()),
                        color = PantryAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.productName.isBlank()) "Unnamed product" else item.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = "kcal ${formatDecimalInput(item.resolvedKcal())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray
                )
                Text(
                    text = "C: ${formatDecimalInput(item.resolvedCarbs())}g • P: ${formatDecimalInput(item.resolvedProt())}g • F: ${formatDecimalInput(item.resolvedFat())}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.Gray
                )
            }
            
            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onEditGramsRequested,
                    enabled = !isSaving,
                    modifier = Modifier
                        .background(PantryBackgroundColor, RoundedCornerShape(10.dp))
                        .size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Edit,
                        contentDescription = "Edit grams",
                        tint = PantryAccentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDeleteRequested,
                    enabled = !isSaving,
                    modifier = Modifier
                        .background(PantryBackgroundColor, RoundedCornerShape(10.dp))
                        .size(36.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Remove item",
                        tint = Color.Red.copy(alpha = 0.8f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

private fun displayName(product: OpenFoodFactsProduct): String {
    val value = product.productName?.trim().orEmpty()
    return if (value.isNotBlank()) value else "Unnamed product"
}

private fun formatDecimalInput(value: Double?): String {
    val safe = (value ?: 0.0).let { if (it.isFinite() && it >= 0.0) it else 0.0 }
    return if (safe.toLong().toDouble() == safe) safe.toLong().toString()
    else String.format(Locale.US, "%.1f", safe).trimEnd('0').trimEnd('.')
}

private fun formatGrams(value: Double?): String = "${formatDecimalInput(value)}g"

private fun formatGramsBadge(value: Double?): String = formatGrams(value)

private fun parseSignedDecimalInput(value: String): Double? {
    val normalized = value.trim().replace(',', '.')
    if (normalized.isBlank()) return null
    return normalized.toDoubleOrNull()?.takeIf { it.isFinite() }
}

private fun groupPantryItemsByCategory(items: List<PantryItem>): Map<PantryCategory, List<PantryItem>> {
    val grouped = items.groupBy(::classifyPantryItem)
    return PantryCategory.entries.associateWith { category ->
        grouped[category].orEmpty()
    }
}

private fun classifyPantryItem(item: PantryItem): PantryCategory {
    val protein = item.resolvedProt() ?: 0.0
    val carbs = item.resolvedCarbs() ?: 0.0
    val fat = item.resolvedFat() ?: 0.0
    val maxMacro = maxOf(protein, carbs, fat)

    if (maxMacro <= 0.0) return PantryCategory.OTHER

    val isProteinTop = abs(protein - maxMacro) < 1e-9
    val isCarbsTop = abs(carbs - maxMacro) < 1e-9
    val isFatTop = abs(fat - maxMacro) < 1e-9
    val topCount = listOf(isProteinTop, isCarbsTop, isFatTop).count { it }
    if (topCount != 1) return PantryCategory.OTHER

    return when {
        isProteinTop -> PantryCategory.PROTEIN
        isCarbsTop -> PantryCategory.CARBS
        isFatTop -> PantryCategory.FAT
        else -> PantryCategory.OTHER
    }
}

