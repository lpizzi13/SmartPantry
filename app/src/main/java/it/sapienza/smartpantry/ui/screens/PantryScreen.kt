package it.sapienza.smartpantry.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.*
import it.sapienza.smartpantry.service.PantryAddNutrients
import it.sapienza.smartpantry.service.PantryAddRequest
import it.sapienza.smartpantry.service.PantryQuantityRequest
import it.sapienza.smartpantry.service.RetrofitClient
import java.util.Locale
import kotlinx.coroutines.Dispatchers
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

    private var currentUid = ""
    private var currentEditorTarget: EditorTarget? = null

    fun bindToUser(uid: String) {
        if (uid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        currentUid = uid
        refreshPantry()
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
        _uiState.update { it.copy(isSearching = true, hasSearched = true) }
        viewModelScope.launch {
            try {
                val results = searchProductsFromBackend(
                    query = query,
                    similar = true,
                    limit = 15,
                    lang = "en"
                )
                _uiState.update { it.copy(searchResults = results) }
            } catch (error: Throwable) {
                emitEvent("Search failed: ${error.localizedMessage ?: "unknown error"}")
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun openEditorFromSearchResult(product: OpenFoodFactsProduct) {
        val openFoodFactsId = product.code?.trim().orEmpty()
        if (openFoodFactsId.isBlank()) {
            emitEvent("Invalid product code.")
            return
        }
        currentEditorTarget = EditorTarget.AddWithId(openFoodFactsId)
        openEditorWithValues(
            name = displayName(product),
            quantity = 1L,
            kcal = product.resolvedKcal(),
            carbs = product.resolvedCarbs(),
            prot = product.resolvedProt(),
            fat = product.resolvedFat(),
            packageWeightGrams = product.resolvedPackageWeightGrams()
        )
    }

    fun openEditorFromManualEntry() {
        currentEditorTarget = EditorTarget.AddManual
        openEditorWithValues(
            name = _uiState.value.searchQuery.trim(),
            quantity = 1L,
            kcal = 0.0,
            carbs = 0.0,
            prot = 0.0,
            fat = 0.0,
            packageWeightGrams = 0.0
        )
    }

    fun deleteItem(item: PantryItem) {
        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = updateItemQuantity(currentUid, item.openFoodFactsId, 0L)
                if (result.isSuccess) {
                    _uiState.update { state ->
                        state.copy(
                            pantryItems = state.pantryItems.filterNot {
                                it.openFoodFactsId == item.openFoodFactsId
                            }
                        )
                    }
                    emitEvent("Item removed from pantry.")
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
        val quantity = parseQuantityOrNull(state.editorQuantityInput) ?: return
        val kcal = parseMacroOrNull(state.editorKcalInput, "Kcal") ?: return
        val carbs = parseMacroOrNull(state.editorCarbsInput, "Carbs") ?: return
        val prot = parseMacroOrNull(state.editorProtInput, "Protein") ?: return
        val fat = parseMacroOrNull(state.editorFatInput, "Fat") ?: return
        val packageWeightGrams = parseMacroOrNull(
            state.editorPackageWeightGramsInput,
            "Package weight (g)"
        ) ?: return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = when (target) {
                    is EditorTarget.AddWithId -> addPantryItem(
                        currentUid,
                        target.openFoodFactsId,
                        name,
                        quantity,
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
                        quantity,
                        kcal,
                        prot,
                        fat,
                        carbs,
                        packageWeightGrams
                    )
                }
                if (result.isSuccess) {
                    val refresh = refreshPantryFromBackend(currentUid)
                    if (refresh.isFailure) {
                        emitEvent(
                            "Unable to load pantry: ${
                                refresh.exceptionOrNull()?.localizedMessage ?: "unknown error"
                            }"
                        )
                    }
                    _uiState.update { it.copy(isEditorVisible = false) }
                    currentEditorTarget = null
                    emitEvent("Item added to pantry.")
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

    private fun parseMacroOrNull(value: String, fieldName: String): Double? {
        val parsed = value.trim().replace(',', '.').toDoubleOrNull()
        if (parsed == null || parsed < 0.0) {
            emitEvent("$fieldName must be a number greater than or equal to 0.")
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
        lang: String = "en"
    ): List<OpenFoodFactsProduct> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchProducts(
                query = query,
                similar = similar,
                limit = limit,
                lang = lang
            ).execute()
            if (response.isSuccessful) {
                val body = response.body()
                val ranked = if (similar && !body?.recommended.isNullOrEmpty()) {
                    body?.recommended.orEmpty()
                } else {
                    body?.products.orEmpty()
                }

                ranked
                    .filter { !it.code.isNullOrBlank() }
                    .distinctBy { it.code }
                    .take(limit)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
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

    private suspend fun updateItemQuantity(
        uid: String,
        openFoodFactsId: String,
        quantity: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val quantityValue = quantity.toIntOrNullChecked(allowZero = true)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Quantity must be 0 or greater.")
            )

        try {
            val response = api.updateItemQuantity(
                PantryQuantityRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    quantity = quantityValue
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to update quantity."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to update quantity."))
        }
    }

    private fun parseBarcodeSuccess(
        body: OpenFoodFactsProductResponse?,
        fallbackBarcode: String
    ): BarcodeResolution {
        val product = body?.product
        val resolvedId = product?.code?.trim().orEmpty().ifBlank { fallbackBarcode }
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
    var isProteinExpanded by rememberSaveable { mutableStateOf(false) }
    var isCarbsExpanded by rememberSaveable { mutableStateOf(false) }
    var isFatExpanded by rememberSaveable { mutableStateOf(false) }
    var isOtherExpanded by rememberSaveable { mutableStateOf(false) }
    val categorizedItems = remember(uiState.pantryItems) {
        groupPantryItemsByCategory(uiState.pantryItems)
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
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("User not authenticated. Please log in again.")
        }
        return
    }

    Scaffold(
        floatingActionButton = {
            FloatingActionButton(onClick = onOpenSearchFood) {
                Icon(Icons.Default.Add, contentDescription = "Add item")
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${uiState.pantryItems.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            if (uiState.pantryItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your pantry is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 88.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    PantryCategory.entries.forEach { category ->
                        val sectionItems = categorizedItems[category].orEmpty()
                        val isExpanded = when (category) {
                            PantryCategory.PROTEIN -> isProteinExpanded
                            PantryCategory.CARBS -> isCarbsExpanded
                            PantryCategory.FAT -> isFatExpanded
                            PantryCategory.OTHER -> isOtherExpanded
                        }

                        item(key = "header_${category.name}") {
                            PantryCategoryHeader(
                                category = category,
                                itemCount = sectionItems.size,
                                isExpanded = isExpanded,
                                onToggleExpanded = {
                                    when (category) {
                                        PantryCategory.PROTEIN -> isProteinExpanded = !isProteinExpanded
                                        PantryCategory.CARBS -> isCarbsExpanded = !isCarbsExpanded
                                        PantryCategory.FAT -> isFatExpanded = !isFatExpanded
                                        PantryCategory.OTHER -> isOtherExpanded = !isOtherExpanded
                                    }
                                }
                            )
                        }

                        if (isExpanded) {
                            if (sectionItems.isEmpty()) {
                                item(key = "empty_${category.name}") {
                                    Text(
                                        text = "No items in this category.",
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(
                                    sectionItems,
                                    key = { "${category.name}_${it.openFoodFactsId}" }
                                ) { item ->
                                    PantryItemCard(
                                        item = item,
                                        isSaving = uiState.isSaving,
                                        onDeleteRequested = { pendingDeletionItem = item }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    pendingDeletionItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSaving) pendingDeletionItem = null
            },
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
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { pendingDeletionItem = null },
                    enabled = !uiState.isSaving
                ) {
                    Text("Cancel")
                }
            }
        )
    }

}

private enum class PantryCategory(val label: String) {
    PROTEIN("Protein"),
    CARBS("Carbs"),
    FAT("Fat"),
    OTHER("Other")
}

@Composable
private fun PantryCategoryHeader(
    category: PantryCategory,
    itemCount: Int,
    isExpanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onToggleExpanded),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${category.label} ($itemCount)",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (isExpanded) {
                    Icons.Default.KeyboardArrowUp
                } else {
                    Icons.Default.KeyboardArrowDown
                },
                contentDescription = if (isExpanded) {
                    "Collapse ${category.label}"
                } else {
                    "Expand ${category.label}"
                }
            )
        }
    }
}

@Composable
private fun PantryItemCard(
    item: PantryItem,
    isSaving: Boolean,
    onDeleteRequested: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (item.productName.isBlank()) "Unnamed product" else item.productName,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "kcal ${formatDecimalInput(item.resolvedKcal())} • carbs ${formatDecimalInput(item.resolvedCarbs())} • protein ${formatDecimalInput(item.resolvedProt())} • fat ${formatDecimalInput(item.resolvedFat())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = "Quantity: ${item.quantity} • Weight package: ${formatDecimalInput(item.resolvedPackageWeightGrams())} g",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(
                onClick = onDeleteRequested,
                enabled = !isSaving,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = "Remove item")
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
    else String.format(Locale.US, "%.2f", safe).trimEnd('0').trimEnd('.')
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

