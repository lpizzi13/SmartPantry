package it.sapienza.smartpantry.ui

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

class FoodQuantityActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val foodName = intent.getStringExtra(EXTRA_FOOD_NAME).orEmpty()
        val kcalPer100g = intent
            .getDoubleExtra(EXTRA_FOOD_KCAL_100G, FoodSelectionActivity.INVALID_KCAL)
            .takeUnless { it == FoodSelectionActivity.INVALID_KCAL }

        setContent {
            MaterialTheme {
                FoodQuantityScreen(
                    foodName = foodName,
                    kcalPer100g = kcalPer100g,
                    onConfirm = { grams ->
                        val resultIntent = Intent().apply {
                            putExtra(EXTRA_FOOD_NAME, foodName)
                            putExtra(
                                EXTRA_FOOD_KCAL_100G,
                                kcalPer100g ?: FoodSelectionActivity.INVALID_KCAL
                            )
                            putExtra(EXTRA_FOOD_GRAMS, grams)
                        }
                        setResult(Activity.RESULT_OK, resultIntent)
                        finish()
                    }
                )
            }
        }
    }

    companion object {
        const val EXTRA_FOOD_NAME = "extra_food_name"
        const val EXTRA_FOOD_KCAL_100G = "extra_food_kcal_100g"
        const val EXTRA_FOOD_GRAMS = "extra_food_grams"
    }
}

@Composable
private fun FoodQuantityScreen(
    foodName: String,
    kcalPer100g: Double?,
    onConfirm: (Int) -> Unit
) {
    var gramsInput by rememberSaveable { mutableStateOf("100") }
    val grams = gramsInput.toIntOrNull() ?: 0
    val estimatedKcal = kcalPer100g?.let { (it * grams / 100.0).roundToInt() }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Quantità alimento",
                style = MaterialTheme.typography.titleLarge
            )

            Text(
                text = foodName,
                style = MaterialTheme.typography.titleMedium
            )

            if (kcalPer100g != null) {
                Text(
                    text = "${kcalPer100g.roundToInt()} kcal/100g",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B6B6B)
                )
            }

            OutlinedTextField(
                value = gramsInput,
                onValueChange = { gramsInput = it.filter(Char::isDigit).take(5) },
                label = { Text("Grammi") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                suffix = { Text("g") }
            )

            estimatedKcal?.let {
                Text(
                    text = "Calorie stimate: $it kcal",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B6B6B)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = { onConfirm(grams) },
                enabled = grams > 0,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Conferma")
            }
        }
    }
}
