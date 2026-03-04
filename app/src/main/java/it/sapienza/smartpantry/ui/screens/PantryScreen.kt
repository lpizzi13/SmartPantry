package it.sapienza.smartpantry.ui.screens

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.data.repository.PantryRepository
import it.sapienza.smartpantry.model.OpenFoodFactsProduct
import it.sapienza.smartpantry.model.PantryItem
import it.sapienza.smartpantry.model.resolvedCarbs
import it.sapienza.smartpantry.model.resolvedFat
import it.sapienza.smartpantry.model.resolvedKcal
import it.sapienza.smartpantry.model.resolvedProt
import java.util.Locale
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class PantryViewModel(private val repository: PantryRepository = PantryRepository()) : ViewModel() {
    private sealed interface EditorTarget {
        data class AddWithId(val openFoodFactsId: String) : EditorTarget
        data object AddManual : EditorTarget
        data class EditExisting(val openFoodFactsId: String) : EditorTarget
    }

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
    fun togglePantryEditMode() = _uiState.update { it.copy(isPantryEditMode = !it.isPantryEditMode) }
    fun onEditorNameChange(value: String) = _uiState.update { it.copy(editorNameInput = value) }
    fun onEditorQuantityChange(value: String) = _uiState.update { it.copy(editorQuantityInput = value) }
    fun onEditorKcalChange(value: String) = _uiState.update { it.copy(editorKcalInput = value) }
    fun onEditorCarbsChange(value: String) = _uiState.update { it.copy(editorCarbsInput = value) }
    fun onEditorProtChange(value: String) = _uiState.update { it.copy(editorProtInput = value) }
    fun onEditorFatChange(value: String) = _uiState.update { it.copy(editorFatInput = value) }

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
                val results = repository.searchProducts(
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
            fat = product.resolvedFat()
        )
    }

    fun openEditorFromManualEntry() {
        currentEditorTarget = EditorTarget.AddManual
        openEditorWithValues(_uiState.value.searchQuery.trim(), 1L, 0.0, 0.0, 0.0, 0.0)
    }

    fun openEditorFromPantryItem(item: PantryItem) {
        currentEditorTarget = EditorTarget.EditExisting(item.openFoodFactsId)
        openEditorWithValues(
            item.productName,
            item.quantity,
            item.resolvedKcal(),
            item.resolvedCarbs(),
            item.resolvedProt(),
            item.resolvedFat()
        )
    }

    fun incrementItem(item: PantryItem) = updateItemQuantityFromList(item, item.quantity + 1L)

    fun decrementItem(item: PantryItem) {
        if (item.quantity > 1L) updateItemQuantityFromList(item, item.quantity - 1L)
    }

    fun deleteItem(item: PantryItem) {
        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = repository.updateQuantity(currentUid, item.openFoodFactsId, 0L)
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

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = when (target) {
                    is EditorTarget.AddWithId -> repository.addItem(
                        currentUid,
                        target.openFoodFactsId,
                        name,
                        quantity,
                        kcal,
                        prot,
                        fat,
                        carbs
                    )
                    is EditorTarget.AddManual -> repository.addItem(
                        currentUid,
                        null,
                        name,
                        quantity,
                        kcal,
                        prot,
                        fat,
                        carbs
                    )
                    is EditorTarget.EditExisting -> repository.updateItem(
                        currentUid,
                        target.openFoodFactsId,
                        name,
                        quantity,
                        kcal,
                        prot,
                        fat,
                        carbs
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
                    emitEvent(
                        if (target is EditorTarget.EditExisting) {
                            "Pantry item updated."
                        } else {
                            "Item added to pantry."
                        }
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
                val resolution = repository.resolveBarcode(cleanCode)
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
                    fat = resolution.fat
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
        fat: Double?
    ) {
        _uiState.update {
            it.copy(
                isEditorVisible = true,
                editorNameInput = name,
                editorQuantityInput = quantity.coerceAtLeast(1L).toString(),
                editorKcalInput = formatDecimalInput(kcal),
                editorCarbsInput = formatDecimalInput(carbs),
                editorProtInput = formatDecimalInput(prot),
                editorFatInput = formatDecimalInput(fat)
            )
        }
    }

    private fun updateItemQuantityFromList(item: PantryItem, newQuantity: Long) {
        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        if (newQuantity < 1L) return

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = repository.updateQuantity(currentUid, item.openFoodFactsId, newQuantity)
                if (result.isSuccess) {
                    _uiState.update { state ->
                        state.copy(
                            pantryItems = state.pantryItems.map { current ->
                                if (current.openFoodFactsId == item.openFoodFactsId) {
                                    current.copy(quantity = newQuantity)
                                } else {
                                    current
                                }
                            }
                        )
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
        val result = repository.getPantry(uid)
        return if (result.isSuccess) {
            _uiState.update { it.copy(pantryItems = result.getOrNull().orEmpty()) }
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(result.exceptionOrNull()?.localizedMessage ?: "unknown error")
            )
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
    var pantryFilterQuery by rememberSaveable { mutableStateOf("") }
    var selectedSortFieldName by rememberSaveable { mutableStateOf(PantrySortField.NAME.name) }
    var isSortAscending by rememberSaveable { mutableStateOf(true) }
    var isSortMenuExpanded by remember { mutableStateOf(false) }
    val pantryListState = rememberLazyListState()
    val selectedSortField = PantrySortField.valueOf(selectedSortFieldName)
    val visiblePantryItems = remember(
        uiState.pantryItems,
        pantryFilterQuery,
        selectedSortFieldName,
        isSortAscending
    ) {
        val query = pantryFilterQuery.trim()
        val filtered = if (query.isBlank()) {
            uiState.pantryItems
        } else {
            uiState.pantryItems.filter { item ->
                item.productName.contains(query, ignoreCase = true) ||
                    item.openFoodFactsId.contains(query, ignoreCase = true)
            }
        }
        sortPantryItems(filtered, selectedSortField, isSortAscending)
    }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) pantryViewModel.bindToUser(uid)
    }
    LaunchedEffect(Unit) {
        pantryViewModel.events.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(selectedSortFieldName, isSortAscending) {
        if (visiblePantryItems.isNotEmpty()) {
            pantryListState.scrollToItem(0)
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

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            OutlinedTextField(
                value = pantryFilterQuery,
                onValueChange = { pantryFilterQuery = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                label = { Text("Search pantry items") }
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "${visiblePantryItems.size} items",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onOpenSearchFood) {
                    Icon(Icons.Default.Add, contentDescription = "Add Items")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add Items")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = pantryViewModel::togglePantryEditMode) {
                    Text(if (uiState.isPantryEditMode) "Done" else "Edit")
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { isSortMenuExpanded = true }) {
                        Text("Sort: ${selectedSortField.label}")
                    }
                    DropdownMenu(
                        expanded = isSortMenuExpanded,
                        onDismissRequest = { isSortMenuExpanded = false }
                    ) {
                        PantrySortField.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    selectedSortFieldName = option.name
                                    isSortMenuExpanded = false
                                }
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = { isSortAscending = !isSortAscending }) {
                    Text(if (isSortAscending) "Ascending" else "Descending")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (uiState.pantryItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Your pantry is empty.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else if (visiblePantryItems.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        "No pantry items match your search.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    state = pantryListState,
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(visiblePantryItems, key = { it.openFoodFactsId }) { item ->
                        PantryItemRow(
                            item = item,
                            isEditMode = uiState.isPantryEditMode,
                            isSaving = uiState.isSaving,
                            onEdit = { pantryViewModel.openEditorFromPantryItem(item) },
                            onDelete = { pantryViewModel.deleteItem(item) },
                            onIncrement = { pantryViewModel.incrementItem(item) },
                            onDecrement = { pantryViewModel.decrementItem(item) }
                        )
                    }
                }
            }
        }
    }

    if (uiState.isEditorVisible) {
        FoodEditorDialog(
            state = uiState,
            isSaving = uiState.isSaving,
            onDismiss = pantryViewModel::dismissEditor,
            onNameChange = pantryViewModel::onEditorNameChange,
            onQuantityChange = pantryViewModel::onEditorQuantityChange,
            onKcalChange = pantryViewModel::onEditorKcalChange,
            onCarbsChange = pantryViewModel::onEditorCarbsChange,
            onProtChange = pantryViewModel::onEditorProtChange,
            onFatChange = pantryViewModel::onEditorFatChange,
            onSave = pantryViewModel::saveEditor
        )
    }
}

@Composable
private fun PantryItemRow(
    item: PantryItem,
    isEditMode: Boolean,
    isSaving: Boolean,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(14.dp)) {
            val isManualItem = item.openFoodFactsId.startsWith("manual", ignoreCase = true)
            Text(
                if (item.productName.isBlank()) "Unnamed product" else item.productName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                if (isManualItem) "Manual item" else "OpenFoodFacts ID: ${item.openFoodFactsId}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "kcal ${formatDecimalInput(item.resolvedKcal())} | carbs ${formatDecimalInput(item.resolvedCarbs())} | prot ${formatDecimalInput(item.resolvedProt())} | fat ${formatDecimalInput(item.resolvedFat())}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                "Qty: ${item.quantity}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
            if (isEditMode) {
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onDecrement, enabled = !isSaving && item.quantity > 1L) {
                        Text("-")
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        "Qty ${item.quantity}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    OutlinedButton(onClick = onIncrement, enabled = !isSaving) {
                        Text("+")
                    }
                    Spacer(modifier = Modifier.weight(1f))
                    TextButton(onClick = onEdit, enabled = !isSaving) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit item")
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    IconButton(onClick = onDelete, enabled = !isSaving) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete item")
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodEditorDialog(
    state: PantryUiState,
    isSaving: Boolean,
    onDismiss: () -> Unit,
    onNameChange: (String) -> Unit,
    onQuantityChange: (String) -> Unit,
    onKcalChange: (String) -> Unit,
    onCarbsChange: (String) -> Unit,
    onProtChange: (String) -> Unit,
    onFatChange: (String) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        title = { Text("Food details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.editorNameInput,
                    onValueChange = onNameChange,
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = state.editorQuantityInput,
                    onValueChange = onQuantityChange,
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                OutlinedTextField(
                    value = state.editorKcalInput,
                    onValueChange = onKcalChange,
                    label = { Text("Kcal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = state.editorCarbsInput,
                    onValueChange = onCarbsChange,
                    label = { Text("Carbs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = state.editorProtInput,
                    onValueChange = onProtChange,
                    label = { Text("Prot") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
                OutlinedTextField(
                    value = state.editorFatInput,
                    onValueChange = onFatChange,
                    label = { Text("Fat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                )
            }
        },
        confirmButton = {
            Button(onClick = onSave, enabled = !isSaving) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel")
            }
        }
    )
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

private enum class PantrySortField(val label: String) {
    NAME("Name"),
    QUANTITY("Quantity"),
    KCAL("Kcal"),
    PROTEIN("Protein"),
    CARBS("Carbs"),
    FAT("Fat")
}

private fun sortPantryItems(
    items: List<PantryItem>,
    field: PantrySortField,
    ascending: Boolean
): List<PantryItem> {
    val sorted = when (field) {
        PantrySortField.NAME -> items.sortedBy { it.productName.trim().lowercase(Locale.US) }
        PantrySortField.QUANTITY -> items.sortedBy { it.quantity }
        PantrySortField.KCAL -> items.sortedBy { it.resolvedKcal() ?: 0.0 }
        PantrySortField.PROTEIN -> items.sortedBy { it.resolvedProt() ?: 0.0 }
        PantrySortField.CARBS -> items.sortedBy { it.resolvedCarbs() ?: 0.0 }
        PantrySortField.FAT -> items.sortedBy { it.resolvedFat() ?: 0.0 }
    }
    return if (ascending) sorted else sorted.asReversed()
}
