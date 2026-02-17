package it.sapienza.smartpantry.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import it.sapienza.smartpantry.ui.model.FoodSearchItemUi
import it.sapienza.smartpantry.ui.viewmodel.FoodSelectionViewModel
import kotlin.math.roundToInt

class FoodSelectionActivity : ComponentActivity() {
    private val viewModel: FoodSelectionViewModel by viewModels()
    private var mealName: String = ""

    private val quantityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult

        val data = result.data ?: return@registerForActivityResult
        val foodName = data.getStringExtra(FoodQuantityActivity.EXTRA_FOOD_NAME).orEmpty()
        val kcal = data.getDoubleExtra(
            FoodQuantityActivity.EXTRA_FOOD_KCAL_100G,
            INVALID_KCAL
        ).takeUnless { it == INVALID_KCAL }
        val grams = data.getIntExtra(FoodQuantityActivity.EXTRA_FOOD_GRAMS, 0)

        if (foodName.isBlank() || grams <= 0) return@registerForActivityResult

        val resultIntent = Intent().apply {
            putExtra(EXTRA_MEAL_NAME, mealName)
            putExtra(EXTRA_SELECTED_FOOD_NAME, foodName)
            putExtra(EXTRA_SELECTED_FOOD_KCAL_100G, kcal ?: INVALID_KCAL)
            putExtra(EXTRA_SELECTED_FOOD_GRAMS, grams)
        }

        setResult(Activity.RESULT_OK, resultIntent)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        mealName = intent.getStringExtra(EXTRA_MEAL_NAME).orEmpty()

        setContent {
            MaterialTheme {
                FoodSelectionScreen(
                    viewModel = viewModel,
                    mealName = mealName,
                    onFoodSelected = ::openFoodQuantityScreen
                )
            }
        }
    }

    private fun openFoodQuantityScreen(item: FoodSearchItemUi) {
        val intent = Intent(this, FoodQuantityActivity::class.java).apply {
            putExtra(FoodQuantityActivity.EXTRA_FOOD_NAME, item.name)
            putExtra(
                FoodQuantityActivity.EXTRA_FOOD_KCAL_100G,
                item.caloriesPer100g ?: INVALID_KCAL
            )
        }
        quantityLauncher.launch(intent)
    }

    companion object {
        const val EXTRA_MEAL_NAME = "extra_meal_name"
        const val EXTRA_SELECTED_FOOD_NAME = "extra_selected_food_name"
        const val EXTRA_SELECTED_FOOD_KCAL_100G = "extra_selected_food_kcal_100g"
        const val EXTRA_SELECTED_FOOD_GRAMS = "extra_selected_food_grams"
        const val INVALID_KCAL = -1.0
    }
}

@Composable
private fun FoodSelectionScreen(
    viewModel: FoodSelectionViewModel,
    mealName: String,
    onFoodSelected: (FoodSearchItemUi) -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val scannerOptions = remember {
        GmsBarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_EAN_13,
                Barcode.FORMAT_EAN_8,
                Barcode.FORMAT_UPC_A,
                Barcode.FORMAT_UPC_E
            )
            .enableAutoZoom()
            .build()
    }

    LaunchedEffect(mealName) {
        viewModel.initialize(mealName)
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp)
        ) {
            Text(
                text = "Aggiungi alimento a $mealName",
                style = MaterialTheme.typography.titleLarge
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.query,
                    onValueChange = viewModel::onQueryChanged,
                    label = { Text("Cerca alimento") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = viewModel::searchFoods,
                    enabled = !uiState.isLoading
                ) {
                    Text("Cerca")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                Button(
                    onClick = {
                        val activity = context as? Activity
                        if (activity == null) {
                            viewModel.onScannerError("Scanner non disponibile in questa schermata.")
                        } else {
                            val scanner = GmsBarcodeScanning.getClient(activity, scannerOptions)
                            scanner.startScan()
                                .addOnSuccessListener { barcode ->
                                    viewModel.searchFoodByCode(barcode.rawValue.orEmpty())
                                }
                                .addOnFailureListener { throwable ->
                                    val isUserCanceled = (throwable as? ApiException)
                                        ?.statusCode == CommonStatusCodes.CANCELED
                                    if (!isUserCanceled) {
                                        viewModel.onScannerError("Scansione non riuscita. Riprova.")
                                    }
                                }
                        }
                    },
                    enabled = !uiState.isLoading
                ) {
                    Text("Scansiona")
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when {
                uiState.isLoading -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }

                uiState.errorMessage != null -> {
                    Text(
                        text = uiState.errorMessage.orEmpty(),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                uiState.foods.isEmpty() -> {
                    Text(
                        text = "Nessun alimento trovato.",
                        color = Color(0xFF6B6B6B),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.foods, key = { "${it.name}|${it.brand.orEmpty()}" }) { item ->
                            FoodItemCard(item = item, onFoodSelected = onFoodSelected)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodItemCard(
    item: FoodSearchItemUi,
    onFoodSelected: (FoodSearchItemUi) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onFoodSelected(item) },
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = item.name,
                style = MaterialTheme.typography.titleMedium
            )
            item.brand?.let { brand ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = brand,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF6B6B6B)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = item.caloriesPer100g
                    ?.let { "${it.roundToInt()} kcal/100g" }
                    ?: "kcal non disponibili",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B6B6B)
            )
        }
    }
}
