package it.sapienza.smartpantry.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import it.sapienza.smartpantry.data.repository.PantryRepository
import it.sapienza.smartpantry.model.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.text.Normalizer

class PantryViewModel(
    private val repository: PantryRepository = PantryRepository()
) : ViewModel() {

    private val _uiState = MutableStateFlow(PantryUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()

    private var currentUid: String = ""

    fun bindToUser(uid: String) {
        if (uid.isBlank()) {
            emitEvent("Utente non autenticato. Effettua di nuovo il login.")
            return
        }
        currentUid = uid
        viewModelScope.launch {
            val result = refreshPantryFromBackend(uid)
            if (result.isFailure) {
                emitEvent(
                    "Errore caricamento dispensa: ${
                        result.exceptionOrNull()?.localizedMessage ?: "sconosciuto"
                    }"
                )
            }
        }
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun onQuantityInputChange(value: String) {
        _uiState.update { it.copy(quantityInput = value) }
    }

    fun onManualNameChange(value: String) {
        _uiState.update { it.copy(manualName = value) }
    }

    fun onShowManualAddChange(value: Boolean) {
        _uiState.update { it.copy(showManualAdd = value) }
    }

    fun onScanStateChanged(value: Boolean) {
        _uiState.update { it.copy(isScanning = value) }
    }

    fun searchProducts() {
        if (currentUid.isBlank()) {
            emitEvent("Utente non autenticato. Effettua di nuovo il login.")
            return
        }

        val query = _uiState.value.searchQuery.trim()
        if (query.length < 2) {
            emitEvent("Inserisci almeno 2 caratteri per la ricerca.")
            return
        }

        _uiState.update { it.copy(isSearching = true) }
        viewModelScope.launch {
            try {
                val results = repository.searchProducts(
                    query = query,
                    similar = true,
                    limit = 15,
                    lang = "it"
                )

                val noResults = results.isEmpty()
                _uiState.update {
                    it.copy(
                        searchResults = results,
                        noApiResults = noResults,
                        showManualAdd = false,
                        manualName = if (noResults) query else it.manualName
                    )
                }
            } catch (error: Throwable) {
                emitEvent("Operazione fallita: ${error.localizedMessage ?: "errore sconosciuto"}")
            } finally {
                _uiState.update { it.copy(isSearching = false) }
            }
        }
    }

    fun addSearchResultToPantry(product: OpenFoodFactsProduct) {
        val uid = currentUid
        if (uid.isBlank()) {
            emitEvent("Utente non autenticato. Effettua di nuovo il login.")
            return
        }

        val openFoodFactsId = product.code?.trim().orEmpty()
        if (openFoodFactsId.isBlank()) {
            emitEvent("Codice prodotto non valido.")
            return
        }

        val quantity = parseQuantityOrNull() ?: return
        addToPantryInternal(
            uid = uid,
            openFoodFactsId = openFoodFactsId,
            productName = displayName(product),
            quantityToAdd = quantity,
            successMessage = "Aggiunto: ${displayName(product)}"
        )
    }

    fun addManualItem() {
        val uid = currentUid
        if (uid.isBlank()) {
            emitEvent("Utente non autenticato. Effettua di nuovo il login.")
            return
        }

        val quantity = parseQuantityOrNull() ?: return
        val name = _uiState.value.manualName.trim()
        if (name.isBlank()) {
            emitEvent("Inserisci il nome dell'alimento.")
            return
        }

        val manualId = manualItemIdFromName(name)
        addToPantryInternal(
            uid = uid,
            openFoodFactsId = manualId,
            productName = name,
            quantityToAdd = quantity,
            successMessage = "Aggiunto manualmente: $name",
            onSuccess = {
                _uiState.update {
                    it.copy(
                        noApiResults = false,
                        showManualAdd = false,
                        searchResults = emptyList()
                    )
                }
            }
        )
    }

    fun handleScannedBarcode(scannedCode: String) {
        val uid = currentUid
        if (uid.isBlank()) {
            onScanStateChanged(false)
            emitEvent("Utente non autenticato. Effettua di nuovo il login.")
            return
        }

        val cleanCode = scannedCode.trim()
        if (cleanCode.isBlank()) {
            onScanStateChanged(false)
            emitEvent("Barcode non valido.")
            return
        }

        val quantity = parseQuantityOrNull()
        if (quantity == null) {
            onScanStateChanged(false)
            return
        }

        viewModelScope.launch {
            try {
                val resolution = repository.resolveBarcode(cleanCode)
                if (!resolution.isSuccess) {
                    onScanStateChanged(false)
                    emitEvent(resolution.errorMessage ?: "Errore scansione")
                    return@launch
                }

                val resolvedId = resolution.openFoodFactsId.orEmpty()
                val resolvedName = resolution.productName ?: "Prodotto sconosciuto"
                addToPantryInternal(
                    uid = uid,
                    openFoodFactsId = resolvedId,
                    productName = resolvedName,
                    quantityToAdd = quantity,
                    successMessage = "Aggiunto: $resolvedName",
                    onFinished = { onScanStateChanged(false) }
                )
            } catch (error: Throwable) {
                onScanStateChanged(false)
                emitEvent("Operazione fallita: ${error.localizedMessage ?: "errore sconosciuto"}")
            }
        }
    }

    fun incrementItem(item: PantryItem) {
        if (currentUid.isBlank()) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = repository.incrementItem(
                    uid = currentUid,
                    openFoodFactsId = item.openFoodFactsId
                )
                if (result.isSuccess) {
                    val refreshResult = refreshPantryFromBackend(currentUid)
                    if (refreshResult.isFailure) {
                        emitEvent(
                            "Errore caricamento dispensa: ${
                                refreshResult.exceptionOrNull()?.localizedMessage ?: "sconosciuto"
                            }"
                        )
                    }
                } else {
                    emitEvent("Operazione fallita: ${result.exceptionOrNull()?.localizedMessage ?: "errore sconosciuto"}")
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun decrementItem(item: PantryItem) {
        if (currentUid.isBlank()) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = repository.decrementItem(
                    uid = currentUid,
                    openFoodFactsId = item.openFoodFactsId
                )
                if (result.isSuccess) {
                    val refreshResult = refreshPantryFromBackend(currentUid)
                    if (refreshResult.isFailure) {
                        emitEvent(
                            "Errore caricamento dispensa: ${
                                refreshResult.exceptionOrNull()?.localizedMessage ?: "sconosciuto"
                            }"
                        )
                    }
                } else {
                    emitEvent("Operazione fallita: ${result.exceptionOrNull()?.localizedMessage ?: "errore sconosciuto"}")
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    fun deleteItem(item: PantryItem) {
        if (currentUid.isBlank()) return
        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = repository.deleteItem(
                    uid = currentUid,
                    openFoodFactsId = item.openFoodFactsId
                )
                if (result.isSuccess) {
                    val refreshResult = refreshPantryFromBackend(currentUid)
                    if (refreshResult.isFailure) {
                        emitEvent(
                            "Errore caricamento dispensa: ${
                                refreshResult.exceptionOrNull()?.localizedMessage ?: "sconosciuto"
                            }"
                        )
                    }
                } else {
                    emitEvent("Operazione fallita: ${result.exceptionOrNull()?.localizedMessage ?: "errore sconosciuto"}")
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
            }
        }
    }

    private fun addToPantryInternal(
        uid: String,
        openFoodFactsId: String,
        productName: String,
        quantityToAdd: Long,
        successMessage: String,
        onSuccess: () -> Unit = {},
        onFinished: () -> Unit = {}
    ) {
        if (openFoodFactsId.isBlank()) {
            emitEvent("Codice prodotto non valido.")
            onFinished()
            return
        }

        _uiState.update { it.copy(isSaving = true) }
        viewModelScope.launch {
            try {
                val result = repository.addOrUpdateItem(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    productName = productName,
                    quantityToAdd = quantityToAdd
                )

                if (result.isSuccess) {
                    val refreshResult = refreshPantryFromBackend(uid)
                    if (refreshResult.isSuccess) {
                        emitEvent(successMessage)
                        onSuccess()
                    } else {
                        emitEvent(
                            "Errore caricamento dispensa: ${
                                refreshResult.exceptionOrNull()?.localizedMessage ?: "sconosciuto"
                            }"
                        )
                    }
                } else {
                    emitEvent("Operazione fallita: ${result.exceptionOrNull()?.localizedMessage ?: "errore sconosciuto"}")
                }
            } finally {
                _uiState.update { it.copy(isSaving = false) }
                onFinished()
            }
        }
    }

    private fun parseQuantityOrNull(): Long? {
        val quantity = _uiState.value.quantityInput.toLongOrNull()
        if (quantity == null || quantity <= 0L) {
            emitEvent("La quantita deve essere maggiore di 0.")
            return null
        }
        return quantity
    }

    private fun emitEvent(message: String) {
        _events.tryEmit(message)
    }

    private suspend fun refreshPantryFromBackend(uid: String): Result<Unit> {
        val result = repository.getPantry(uid)
        return if (result.isSuccess) {
            _uiState.update { it.copy(pantryItems = result.getOrNull().orEmpty()) }
            Result.success(Unit)
        } else {
            Result.failure(
                IllegalStateException(
                    result.exceptionOrNull()?.localizedMessage ?: "sconosciuto"
                )
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
    }
}

private fun displayName(product: OpenFoodFactsProduct): String {
    val productName = product.productName?.trim().orEmpty()
    return if (productName.isNotBlank()) productName else "Prodotto senza nome"
}

private fun normalizeText(value: String): String {
    val normalized = Normalizer.normalize(value.lowercase().trim(), Normalizer.Form.NFD)
    return normalized.replace("\\p{Mn}+".toRegex(), "")
}

private fun tokenize(value: String): List<String> {
    return normalizeText(value)
        .split(Regex("[^a-z0-9]+"))
        .filter { it.isNotBlank() }
}

private fun manualItemIdFromName(name: String): String {
    val slug = tokenize(name).joinToString("_").ifBlank { "item" }
    return "manual_$slug"
}

@Composable
fun PantryScreen(
    uid: String,
    pantryViewModel: PantryViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by pantryViewModel.uiState.collectAsState()

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            pantryViewModel.bindToUser(uid)
        }
    }

    LaunchedEffect(Unit) {
        pantryViewModel.events.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (uid.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Utente non autenticato. Effettua di nuovo il login.")
        }
        return
    }

    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .enableAutoZoom()
            .build()
    }
    val scanner = remember(activity) {
        activity?.let { GmsBarcodeScanning.getClient(it, scannerOptions) }
    }

    val pantryById = remember(uiState.pantryItems) { uiState.pantryItems.associateBy { it.openFoodFactsId } }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 12.dp, vertical = 10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = pantryViewModel::onSearchQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                placeholder = { Text("Cerca alimento") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                trailingIcon = {
                    IconButton(
                        onClick = pantryViewModel::searchProducts,
                        enabled = !uiState.isSearching
                    ) {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Cerca"
                        )
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { pantryViewModel.searchProducts() })
            )
            Spacer(modifier = Modifier.width(8.dp))
            FilledTonalIconButton(
                onClick = {
                    val scannerClient = scanner
                    if (scannerClient == null) {
                        Toast.makeText(
                            context,
                            "Scanner non disponibile in questo contesto.",
                            Toast.LENGTH_SHORT
                        ).show()
                        return@FilledTonalIconButton
                    }

                    pantryViewModel.onScanStateChanged(true)
                    scannerClient.startScan()
                        .addOnSuccessListener { barcode ->
                            val scannedCode = barcode.rawValue?.trim().orEmpty()
                            pantryViewModel.handleScannedBarcode(scannedCode)
                        }
                        .addOnCanceledListener {
                            pantryViewModel.onScanStateChanged(false)
                        }
                        .addOnFailureListener {
                            pantryViewModel.onScanStateChanged(false)
                        }
                },
                enabled = !uiState.isScanning
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = "Scanner barcode"
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.quantityInput,
                onValueChange = pantryViewModel::onQuantityInputChange,
                label = { Text("Qta") },
                modifier = Modifier.width(110.dp),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )
            Spacer(modifier = Modifier.width(10.dp))
            if (uiState.isSearching || uiState.isSaving || uiState.isScanning) {
                LinearProgressIndicator(
                    modifier = Modifier
                        .weight(1f)
                        .height(4.dp)
                )
            } else {
                Text(
                    text = "Ricerca dalla barra in alto",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (uiState.searchResults.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 180.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.searchResults, key = { it.code ?: it.hashCode().toString() }) { product ->
                        SearchResultRow(
                            product = product,
                            existingItem = pantryById[product.code.orEmpty()],
                            onAdd = { pantryViewModel.addSearchResultToPantry(product) }
                        )
                    }
                }
            }
        }

        if (uiState.noApiResults) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Nessun risultato API. Aggiunta manuale",
                            modifier = Modifier.weight(1f),
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Switch(
                            checked = uiState.showManualAdd,
                            onCheckedChange = pantryViewModel::onShowManualAddChange
                        )
                    }
                    if (uiState.showManualAdd) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = uiState.manualName,
                                onValueChange = pantryViewModel::onManualNameChange,
                                modifier = Modifier.weight(1f),
                                singleLine = true,
                                label = { Text("Nome alimento") }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            OutlinedButton(
                                onClick = pantryViewModel::addManualItem,
                                enabled = !uiState.isSaving
                            ) {
                                Text("Aggiungi")
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "In dispensa",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${uiState.pantryItems.size} elementi",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        if (uiState.pantryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Dispensa vuota. Aggiungi il tuo primo prodotto.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(items = uiState.pantryItems, key = { it.openFoodFactsId }) { item ->
                    PantryItemRow(
                        item = item,
                        onIncrement = { pantryViewModel.incrementItem(item) },
                        onDecrement = { pantryViewModel.decrementItem(item) },
                        onDelete = { pantryViewModel.deleteItem(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultRow(
    product: OpenFoodFactsProduct,
    existingItem: PantryItem?,
    onAdd: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName(product),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            val brandText = product.brands?.takeIf { it.isNotBlank() } ?: "Brand non disponibile"
            @OptIn(ExperimentalMaterial3Api::class)
            Text(
                text = brandText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (existingItem != null) {
                Text(
                    text = "Gia in dispensa: ${existingItem.quantity}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(modifier = Modifier.width(6.dp))
        TextButton(onClick = onAdd) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = "Aggiungi"
            )
        }
    }
}

@Composable
private fun PantryItemRow(
    item: PantryItem,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            val isManualItem = item.openFoodFactsId.startsWith("manual_")
            Text(
                text = if (item.productName.isBlank()) "Prodotto senza nome" else item.productName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isManualItem) "Elemento manuale" else "OpenFoodFacts ID",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = item.openFoodFactsId,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedButton(onClick = onDecrement) {
                    Text("-")
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text("Qty: ${item.quantity}", fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedButton(onClick = onIncrement) {
                    Text("+")
                }
                Spacer(modifier = Modifier.weight(1f))
                TextButton(onClick = onDelete) {
                    Text("Elimina")
                }
            }
        }
    }
}
