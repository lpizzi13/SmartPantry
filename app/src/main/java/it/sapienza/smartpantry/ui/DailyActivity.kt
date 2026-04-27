package it.sapienza.smartpantry.ui

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.DailyViewModel
import it.sapienza.smartpantry.model.PantryItem
import it.sapienza.smartpantry.model.resolvedCarbs
import it.sapienza.smartpantry.model.resolvedFat
import it.sapienza.smartpantry.model.resolvedGrams
import it.sapienza.smartpantry.model.resolvedKcal
import it.sapienza.smartpantry.model.resolvedProt
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.flow.collectLatest

class DailyActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val uid = intent.getStringExtra(EXTRA_UID).orEmpty()
        val homeDateKey = intent.getStringExtra(EXTRA_HOME_DATE_KEY).orEmpty()
        val homeMealType = intent.getStringExtra(EXTRA_HOME_MEAL_TYPE).orEmpty()

        setContent {
            MaterialTheme {
                DailyActivityContent(
                    uid = uid,
                    homeDateKey = homeDateKey,
                    homeMealType = homeMealType,
                    onClose = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_UID = "daily_uid"
        const val EXTRA_HOME_DATE_KEY = "daily_home_date_key"
        const val EXTRA_HOME_MEAL_TYPE = "daily_home_meal_type"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DailyActivityContent(
    uid: String,
    homeDateKey: String,
    homeMealType: String,
    onClose: () -> Unit,
    dailyViewModel: DailyViewModel = viewModel()
) {
    val context = LocalContext.current
    val uiState by dailyViewModel.uiState.collectAsState()
    var selectedItem by remember { mutableStateOf<PantryItem?>(null) }
    var gramsInput by rememberSaveable { mutableStateOf("100") }

    LaunchedEffect(uid, homeDateKey, homeMealType) {
        dailyViewModel.bindSession(uid, homeDateKey, homeMealType)
    }
    LaunchedEffect(Unit) {
        dailyViewModel.events.collectLatest { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(Unit) {
        dailyViewModel.saveCompleted.collectLatest { onClose() }
    }

    if (uid.isBlank()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DailyBackgroundColor)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("User not authenticated. Please log in again.", color = DailyTextPrimary)
        }
        return
    }

    val filteredItems = remember(uiState.pantryItems, uiState.searchQuery) {
        val query = uiState.searchQuery.trim().lowercase(Locale.ROOT)
        if (query.isBlank()) {
            uiState.pantryItems
        } else {
            uiState.pantryItems.filter { item ->
                item.productName.trim().lowercase(Locale.ROOT).contains(query)
            }
        }
    }

    Scaffold(
        containerColor = DailyBackgroundColor,
        topBar = {
            TopAppBar(
                title = { Text("Add to ${mealTypeLabel(homeMealType)}") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DailyBackgroundColor,
                    titleContentColor = DailyTextPrimary,
                    navigationIconContentColor = DailyTextPrimary
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
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = dailyViewModel::onSearchQueryChange,
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("Search in pantry") },
                shape = RoundedCornerShape(16.dp),
                colors = dailySearchFieldColors(),
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = null
                    )
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions.Default
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (uiState.isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(color = DailyAccentColor)
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            uiState.errorMessage?.let {
                Text(
                    text = it,
                    color = Color(0xFFFF8A80),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            PantrySelectionList(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true),
                items = filteredItems,
                hasQuery = uiState.searchQuery.isNotBlank(),
                isSaving = uiState.isSaving,
                onAddRequested = { item ->
                    selectedItem = item
                    gramsInput = defaultDailyGramsInput(item)
                }
            )

            Spacer(modifier = Modifier.height(10.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        val intent = Intent(context, SearchFoodActivity::class.java).apply {
                            putExtra(SearchFoodActivity.EXTRA_UID, uid)
                            putExtra(SearchFoodActivity.EXTRA_SOURCE, SearchFoodActivity.SOURCE_HOME)
                            putExtra(SearchFoodActivity.EXTRA_HOME_DATE_KEY, homeDateKey)
                            putExtra(SearchFoodActivity.EXTRA_HOME_MEAL_TYPE, homeMealType)
                        }
                        context.startActivity(intent)
                        onClose()
                    },
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, DailyAccentColor),
                colors = CardDefaults.cardColors(containerColor = DailyCardColor)
            ) {
                Text(
                    "FIND MORE FOODS",
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 14.dp, horizontal = 8.dp),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = DailyAccentColor,
                    textAlign = TextAlign.Center
                )
            }
        }
    }

    selectedItem?.let { item ->
        AlertDialog(
            onDismissRequest = {
                if (!uiState.isSaving) selectedItem = null
            },
            containerColor = DailyCardColor,
            titleContentColor = DailyTextPrimary,
            textContentColor = DailyTextSecondary,
            title = { Text("Add ${displayDailyName(item)}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item.resolvedGrams()?.takeIf { it > 0.0 }?.let {
                        Text("Available in pantry: ${formatDailyNumber(it)}g")
                    }
                    OutlinedTextField(
                        value = gramsInput,
                        onValueChange = { gramsInput = it },
                        label = { Text("Grams") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = RoundedCornerShape(14.dp),
                        colors = dailySearchFieldColors()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val accepted = dailyViewModel.addPantryItemToHome(item, gramsInput)
                        if (accepted) selectedItem = null
                    },
                    enabled = !uiState.isSaving,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = DailyAccentColor,
                        contentColor = Color.Black
                    )
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { selectedItem = null },
                    enabled = !uiState.isSaving,
                    colors = ButtonDefaults.textButtonColors(contentColor = DailyTextSecondary)
                ) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun PantrySelectionList(
    modifier: Modifier = Modifier,
    items: List<PantryItem>,
    hasQuery: Boolean,
    isSaving: Boolean,
    onAddRequested: (PantryItem) -> Unit
) {
    Box(modifier = modifier) {
        if (items.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = if (hasQuery) "No items found for this search." else "Your pantry is empty.",
                    color = DailyTextSecondary
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(
                    items = items,
                    key = { "${it.openFoodFactsId}_${it.productName}" }
                ) { item ->
                    DailyPantryItemCard(
                        item = item,
                        isSaving = isSaving,
                        onAddRequested = { onAddRequested(item) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DailyPantryItemCard(
    item: PantryItem,
    isSaving: Boolean,
    onAddRequested: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = DailyCardColor)
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
                color = DailyBackgroundColor
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = formatDailyGramsBadge(item.resolvedGrams()),
                        color = DailyAccentColor,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayDailyName(item),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = DailyTextPrimary
                )
                Text(
                    text = "kcal ${formatDailyNumber(item.resolvedKcal() ?: 0.0)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = DailyTextSecondary
                )
                Text(
                    text = "C: ${formatDailyNumber(item.resolvedCarbs() ?: 0.0)}g | P: ${formatDailyNumber(item.resolvedProt() ?: 0.0)}g | F: ${formatDailyNumber(item.resolvedFat() ?: 0.0)}g",
                    style = MaterialTheme.typography.labelSmall,
                    color = DailyTextSecondary
                )
            }

            Column(
                verticalArrangement = Arrangement.spacedBy(6.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                IconButton(
                    onClick = onAddRequested,
                    enabled = !isSaving,
                    modifier = Modifier
                        .background(DailyBackgroundColor, RoundedCornerShape(10.dp))
                        .size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Add item",
                        tint = DailyAccentColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun dailySearchFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = DailyTextPrimary,
    unfocusedTextColor = DailyTextPrimary,
    focusedContainerColor = DailyCardColor,
    unfocusedContainerColor = DailyCardColor,
    focusedBorderColor = DailyAccentColor,
    unfocusedBorderColor = DailyBorderColor,
    cursorColor = DailyAccentColor,
    focusedLabelColor = DailyAccentColor,
    unfocusedLabelColor = DailyTextSecondary,
    focusedPlaceholderColor = DailyTextSecondary,
    unfocusedPlaceholderColor = DailyTextSecondary,
    focusedTrailingIconColor = DailyAccentColor,
    unfocusedTrailingIconColor = DailyTextSecondary
)

private fun formatDailyGramsBadge(value: Double?): String = "${formatDailyNumber(value ?: 0.0)}g"

private fun displayDailyName(item: PantryItem): String {
    val value = item.productName.trim()
    return if (value.isBlank()) "Unnamed product" else value
}

private fun defaultDailyGramsInput(item: PantryItem): String {
    val available = item.resolvedGrams()?.takeIf { it.isFinite() && it > 0.0 }
    val fallback = 100.0
    val value = if (available == null) fallback else available.coerceAtMost(fallback)
    return formatDailyNumber(value)
}

private fun formatDailyNumber(value: Double): String {
    val safe = when {
        !value.isFinite() -> 0.0
        value < 0.0 -> 0.0
        else -> value
    }
    return if (abs(safe - safe.toLong().toDouble()) < 1e-9) {
        safe.toLong().toString()
    } else {
        String.format(Locale.US, "%.1f", safe).trimEnd('0').trimEnd('.')
    }
}

private fun mealTypeLabel(value: String): String {
    val normalized = value.trim().lowercase(Locale.ROOT)
    return when (normalized) {
        "breakfast" -> "Breakfast"
        "lunch" -> "Lunch"
        "dinner" -> "Dinner"
        "snacks" -> "Snacks"
        else -> "Meal"
    }
}

private val DailyBackgroundColor = Color(0xFF0A120E)
private val DailyCardColor = Color(0xFF1A2421)
private val DailyAccentColor = Color(0xFF00E676)
private val DailyTextPrimary = Color.White
private val DailyTextSecondary = Color.Gray
private val DailyBorderColor = Color(0xFF2C2E33)

