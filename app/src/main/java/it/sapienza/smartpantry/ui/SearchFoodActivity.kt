package it.sapienza.smartpantry.ui

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import it.sapienza.smartpantry.model.OpenFoodFactsProduct
import it.sapienza.smartpantry.model.PantryItem
import it.sapienza.smartpantry.model.toSearchFood
import it.sapienza.smartpantry.ui.screens.PantryUiState
import it.sapienza.smartpantry.ui.screens.PantryViewModel
import kotlinx.coroutines.flow.collectLatest

class SearchFoodActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uid = intent.getStringExtra(EXTRA_UID).orEmpty()
        setContent {
            MaterialTheme {
                SearchFoodActivityContent(
                    uid = uid,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_UID = "search_food_uid"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchFoodActivityContent(
    uid: String,
    onClose: () -> Unit,
    pantryViewModel: PantryViewModel = viewModel()
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val uiState by pantryViewModel.uiState.collectAsState()

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) pantryViewModel.bindToUser(uid)
    }
    LaunchedEffect(Unit) {
        pantryViewModel.events.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    if (uid.isBlank()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("User not authenticated. Please log in again.", color = SearchTextPrimary)
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
    val scanner = remember(activity) { activity?.let { GmsBarcodeScanning.getClient(it, scannerOptions) } }
    val pantryById = remember(uiState.pantryItems) { uiState.pantryItems.associateBy { it.openFoodFactsId } }

    Scaffold(
        containerColor = SearchBackgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Search food") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SearchBackgroundColor,
                    titleContentColor = SearchTextPrimary,
                    navigationIconContentColor = SearchTextPrimary
                ),
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 10.dp)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = pantryViewModel::onSearchQueryChange,
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text("Search food") },
                    shape = RoundedCornerShape(16.dp),
                    colors = searchFieldColors(),
                    trailingIcon = {
                        IconButton(
                            onClick = pantryViewModel::searchProducts,
                            enabled = !uiState.isSearching
                        ) {
                            Icon(Icons.Default.Search, contentDescription = "Search")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { pantryViewModel.searchProducts() })
                )
                Spacer(modifier = Modifier.width(8.dp))
                FilledTonalIconButton(
                    onClick = {
                        val scannerClient = scanner ?: run {
                            Toast.makeText(
                                context,
                                "Scanner is not available in this context.",
                                Toast.LENGTH_SHORT
                            ).show()
                            return@FilledTonalIconButton
                        }
                        pantryViewModel.onScanStateChanged(true)
                        scannerClient.startScan()
                            .addOnSuccessListener {
                                pantryViewModel.handleScannedBarcode(it.rawValue?.trim().orEmpty())
                            }
                            .addOnCanceledListener { pantryViewModel.onScanStateChanged(false) }
                            .addOnFailureListener { pantryViewModel.onScanStateChanged(false) }
                    },
                    enabled = !uiState.isScanning,
                    colors = IconButtonDefaults.filledTonalIconButtonColors(
                        containerColor = SearchCardColor,
                        contentColor = SearchAccentColor
                    )
                ) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = "Barcode scanner")
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Box(modifier = Modifier.fillMaxWidth().height(40.dp), contentAlignment = Alignment.Center) {
                if (uiState.isSearching) {
                    CircularProgressIndicator(color = SearchAccentColor)
                }
            }

            ManualEntrySection(onManualEntry = pantryViewModel::openEditorFromManualEntry)

            if (uiState.searchResults.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                SearchResultsSection(
                    state = uiState,
                    pantryById = pantryById,
                    onSelectProduct = pantryViewModel::openEditorFromSearchResult
                )
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
            onPackageWeightGramsChange = pantryViewModel::onEditorPackageWeightGramsChange,
            onSave = pantryViewModel::saveEditor
        )
    }
}

@Composable
private fun ManualEntrySection(
    onManualEntry: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onManualEntry),
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, Color.Black),
        colors = CardDefaults.cardColors(containerColor = SearchBorderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Manual entry",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = SearchTextPrimary
                )
                Text(
                    "Create a custom pantry item",
                    style = MaterialTheme.typography.bodySmall,
                    color = SearchTextSecondary
                )
            }
            Icon(
                Icons.Default.Add,
                contentDescription = "Manual entry",
                tint = SearchAccentColor
            )
        }
    }
}

@Composable
private fun SearchResultsSection(
    state: PantryUiState,
    pantryById: Map<String, PantryItem>,
    onSelectProduct: (OpenFoodFactsProduct) -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = SearchCardColor)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp)) {
            Text(
                "Search results",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = SearchTextPrimary
            )
            Spacer(modifier = Modifier.height(6.dp))
            HorizontalDivider(color = SearchBorderColor)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 420.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(state.searchResults, key = { it.code ?: it.hashCode().toString() }) { product ->
                    val uiModel = product.toSearchFood(pantryById)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectProduct(product) },
                        shape = RoundedCornerShape(14.dp),
                        colors = CardDefaults.cardColors(containerColor = SearchItemColor)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    uiModel.productName,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = SearchTextPrimary
                                )
                                Text(
                                    uiModel.brandName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SearchTextSecondary
                                )
                                uiModel.alreadyInPantryQuantity?.let {
                                    Text(
                                        "Already in pantry: $it",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = SearchAccentColor
                                    )
                                }
                            }
                            Icon(
                                Icons.Default.Add,
                                contentDescription = "Add",
                                tint = SearchAccentColor
                            )
                        }
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
    onPackageWeightGramsChange: (String) -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = { if (!isSaving) onDismiss() },
        containerColor = SearchCardColor,
        titleContentColor = SearchTextPrimary,
        textContentColor = SearchTextSecondary,
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
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = searchFieldColors()
                )
                OutlinedTextField(
                    value = state.editorQuantityInput,
                    onValueChange = onQuantityChange,
                    label = { Text("Quantity") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    shape = RoundedCornerShape(14.dp),
                    colors = searchFieldColors()
                )
                OutlinedTextField(
                    value = state.editorKcalInput,
                    onValueChange = onKcalChange,
                    label = { Text("Kcal") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = searchFieldColors()
                )
                OutlinedTextField(
                    value = state.editorCarbsInput,
                    onValueChange = onCarbsChange,
                    label = { Text("Carbs") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = searchFieldColors()
                )
                OutlinedTextField(
                    value = state.editorProtInput,
                    onValueChange = onProtChange,
                    label = { Text("Protein") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = searchFieldColors()
                )
                OutlinedTextField(
                    value = state.editorFatInput,
                    onValueChange = onFatChange,
                    label = { Text("Fat") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = searchFieldColors()
                )
                OutlinedTextField(
                    value = state.editorPackageWeightGramsInput,
                    onValueChange = onPackageWeightGramsChange,
                    label = { Text("Package weight (g)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(14.dp),
                    colors = searchFieldColors()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SearchAccentColor,
                    contentColor = Color.Black
                )
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !isSaving,
                colors = ButtonDefaults.textButtonColors(contentColor = SearchTextSecondary)
            ) {
                Text("Cancel")
            }
        }
    )
}

private val SearchBackgroundColor = Color(0xFF0A120E)
private val SearchCardColor = Color(0xFF1A2421)
private val SearchItemColor = Color(0xFF1E2124)
private val SearchAccentColor = Color(0xFF00E676)
private val SearchTextPrimary = Color.White
private val SearchTextSecondary = Color.Gray
private val SearchBorderColor = Color(0xFF2C2E33)

@Composable
private fun searchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = SearchTextPrimary,
    unfocusedTextColor = SearchTextPrimary,
    focusedContainerColor = SearchCardColor,
    unfocusedContainerColor = SearchCardColor,
    focusedBorderColor = SearchAccentColor,
    unfocusedBorderColor = SearchBorderColor,
    cursorColor = SearchAccentColor,
    focusedLabelColor = SearchAccentColor,
    unfocusedLabelColor = SearchTextSecondary,
    focusedPlaceholderColor = SearchTextSecondary,
    unfocusedPlaceholderColor = SearchTextSecondary,
    focusedTrailingIconColor = SearchAccentColor,
    unfocusedTrailingIconColor = SearchTextSecondary
)
