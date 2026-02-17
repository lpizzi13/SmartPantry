package it.sapienza.smartpantry.ui

import android.Manifest
import android.app.Activity
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import it.sapienza.smartpantry.reminder.ShoppingProximityReminderManager
import it.sapienza.smartpantry.ui.model.MealFoodUi
import it.sapienza.smartpantry.ui.model.MealUi
import it.sapienza.smartpantry.ui.model.ShoppingItem
import it.sapienza.smartpantry.ui.model.ShoppingSection
import it.sapienza.smartpantry.ui.viewmodel.FoodJournalViewModel
import it.sapienza.smartpantry.ui.viewmodel.ProfileViewModel
import it.sapienza.smartpantry.ui.viewmodel.ShoppingListViewModel
import java.util.Calendar

class MainActivity : ComponentActivity() {
    private val foodJournalViewModel: FoodJournalViewModel by viewModels()
    private val shoppingListViewModel: ShoppingListViewModel by viewModels()
    private val profileViewModel: ProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen(
                    foodJournalViewModel = foodJournalViewModel,
                    shoppingListViewModel = shoppingListViewModel,
                    profileViewModel = profileViewModel
                )
            }
        }
    }
}

sealed class Screen(val route: String, val label: String, val icon: ImageVector) {
    object Home : Screen("home", "Home", Icons.Default.Home)
    object FoodJournal : Screen("journal", "Diario", Icons.Default.DateRange)
    object ShoppingList : Screen("shopping", "Spesa", Icons.Default.ShoppingCart)
    object Profile : Screen("profile", "Profilo", Icons.Default.Person)
}

private val navigationItems = listOf(
    Screen.Home,
    Screen.FoodJournal,
    Screen.ShoppingList,
    Screen.Profile
)

@Composable
fun MainScreen(
    foodJournalViewModel: FoodJournalViewModel,
    shoppingListViewModel: ShoppingListViewModel,
    profileViewModel: ProfileViewModel
) {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                navigationItems.forEach { screen ->
                    NavigationBarItem(
                        icon = { Icon(screen.icon, contentDescription = null) },
                        label = { Text(screen.label) },
                        selected = currentDestination?.hierarchy?.any { it.route == screen.route } == true,
                        onClick = {
                            navController.navigate(screen.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(navController, startDestination = Screen.Home.route, Modifier.padding(innerPadding)) {
            composable(Screen.Home.route) { HomeScreen() }
            composable(Screen.FoodJournal.route) {
                FoodJournalScreen(
                    viewModel = foodJournalViewModel,
                    profileViewModel = profileViewModel
                )
            }
            composable(Screen.ShoppingList.route) {
                ShoppingListScreen(viewModel = shoppingListViewModel)
            }
            composable(Screen.Profile.route) {
                ProfileScreen(viewModel = profileViewModel)
            }
        }
    }
}

@Composable
fun HomeScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Home Screen")
    }
}

@Composable
fun FoodJournalScreen(
    viewModel: FoodJournalViewModel,
    profileViewModel: ProfileViewModel
) {
    val uiState by viewModel.uiState.collectAsState()
    val profileState by profileViewModel.uiState.collectAsState()
    val context = LocalContext.current
    val addFoodLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val data = result.data ?: return@rememberLauncherForActivityResult
            val mealName = data.getStringExtra(FoodSelectionActivity.EXTRA_MEAL_NAME).orEmpty()
            val foodName = data.getStringExtra(FoodSelectionActivity.EXTRA_SELECTED_FOOD_NAME).orEmpty()
            val kcal = data.getDoubleExtra(
                FoodSelectionActivity.EXTRA_SELECTED_FOOD_KCAL_100G,
                FoodSelectionActivity.INVALID_KCAL
            ).takeUnless { it == FoodSelectionActivity.INVALID_KCAL }
            val grams = data.getIntExtra(FoodSelectionActivity.EXTRA_SELECTED_FOOD_GRAMS, 0)

            viewModel.onFoodAdded(
                mealName = mealName,
                foodName = foodName,
                caloriesPer100g = kcal,
                grams = grams
            )
        }
    }

    LaunchedEffect(profileState) {
        viewModel.onProfileUpdated(profileState)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            JournalHeader(
                weekNumber = uiState.selectedWeekNumber,
                onOpenCalendar = {
                    val currentDate = Calendar.getInstance().apply {
                        timeInMillis = uiState.selectedDateMillis
                    }
                    DatePickerDialog(
                        context,
                        { _, year, month, dayOfMonth ->
                            viewModel.onDateSelected(year, month, dayOfMonth)
                        },
                        currentDate.get(Calendar.YEAR),
                        currentDate.get(Calendar.MONTH),
                        currentDate.get(Calendar.DAY_OF_MONTH)
                    ).show()
                }
            )
        }
        item {
            SummarySection(
                basalMetabolicRate = uiState.basalMetabolicRate,
                sportCalories = uiState.sportCalories,
                recommendedCalories = uiState.recommendedCalories
            )
        }
        item {
            MealsSection(
                meals = uiState.meals,
                mealFoods = uiState.mealFoods,
                onAddFoodClick = { meal ->
                    val intent = Intent(context, FoodSelectionActivity::class.java).apply {
                        putExtra(FoodSelectionActivity.EXTRA_MEAL_NAME, meal.name)
                    }
                    addFoodLauncher.launch(intent)
                }
            )
        }
    }
}

@Composable
private fun JournalHeader(
    weekNumber: Int,
    onOpenCalendar: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "Oggi",
                style = MaterialTheme.typography.headlineMedium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Settimana $weekNumber",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF6B6B6B)
            )
        }
        FilledTonalIconButton(onClick = onOpenCalendar) {
            Icon(Icons.Default.DateRange, contentDescription = "Apri calendario")
        }
    }
}

@Composable
private fun SummarySection(
    basalMetabolicRate: Int?,
    sportCalories: Int,
    recommendedCalories: Int?
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Riepilogo",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Dettagli",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                if (recommendedCalories == null) {
                    Text(
                        text = "Completa sesso, età, altezza e peso nel profilo per calcolare le calorie giornaliere.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B6B6B)
                    )
                } else {
                    val bmrValue = basalMetabolicRate ?: 0
                    Text(
                        text = "Calorie consigliate: $recommendedCalories kcal",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "BMR: $bmrValue kcal + 300 kcal + sport: $sportCalories kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B6B6B)
                    )
                }
            }
        }
    }
}

@Composable
private fun MealsSection(
    meals: List<MealUi>,
    mealFoods: Map<String, List<MealFoodUi>>,
    onAddFoodClick: (MealUi) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Alimentazione",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "Altro",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Card(
            shape = RoundedCornerShape(16.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                meals.forEachIndexed { index, meal ->
                    val foodsForMeal = mealFoods[meal.name].orEmpty()
                    val subtitle = when {
                        foodsForMeal.isEmpty() -> "Aggiungi alimenti"
                        foodsForMeal.size == 1 -> "${foodsForMeal.first().name} (${foodsForMeal.first().grams} g)"
                        else -> "${foodsForMeal.size} alimenti aggiunti"
                    }
                    MealRow(
                        meal = meal,
                        subtitle = subtitle,
                        onAddFoodClick = { onAddFoodClick(meal) }
                    )
                    if (index != meals.lastIndex) {
                        Divider(color = Color(0xFFE5E5E5))
                    }
                }
            }
        }
    }
}

@Composable
private fun MealRow(
    meal: MealUi,
    subtitle: String,
    onAddFoodClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = CircleShape,
            color = Color(0xFFEFEFEF),
            modifier = Modifier.size(40.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = meal.badge,
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${meal.name} >",
                style = MaterialTheme.typography.titleMedium
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B6B6B)
            )
        }

        FilledIconButton(
            onClick = onAddFoodClick,
            colors = IconButtonDefaults.filledIconButtonColors(
                containerColor = Color(0xFF111111),
                contentColor = Color.White
            )
        ) {
            Icon(Icons.Default.Add, contentDescription = "Aggiungi alimento")
        }
    }
}

@Composable
fun ShoppingListScreen(viewModel: ShoppingListViewModel) {
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
    var remindersEnabled by remember {
        mutableStateOf(ShoppingProximityReminderManager.isEnabled(context))
    }
    var reminderFeedbackMessage by remember { mutableStateOf<String?>(null) }
    val pendingShoppingItemsCount = uiState.sections.sumOf { section ->
        section.items.count { item -> !item.isChecked }
    }
    val activateReminders = {
        ShoppingProximityReminderManager.setEnabled(context, true)
        ShoppingProximityReminderManager.syncShoppingItems(context, uiState.sections)
        remindersEnabled = true
        reminderFeedbackMessage = if (pendingShoppingItemsCount == 0) {
            "Promemoria attivati. Aggiungi articoli alla lista per ricevere notifiche."
        } else {
            "Promemoria supermercato attivati."
        }
    }
    val backgroundLocationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            activateReminders()
        } else {
            reminderFeedbackMessage = "Permesso posizione in background negato."
        }
    }
    val foregroundLocationPermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val hasForegroundLocationPermission = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
        if (!hasForegroundLocationPermission) {
            reminderFeedbackMessage = "Permesso posizione negato."
            return@rememberLauncherForActivityResult
        }

        val needsBackgroundLocationPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_BACKGROUND_LOCATION
            ) != PackageManager.PERMISSION_GRANTED

        if (needsBackgroundLocationPermission) {
            backgroundLocationPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        } else {
            activateReminders()
        }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            foregroundLocationPermissionsLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        } else {
            reminderFeedbackMessage = "Permesso notifiche negato."
        }
    }

    LaunchedEffect(uiState.sections) {
        ShoppingProximityReminderManager.syncShoppingItems(context, uiState.sections)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Aggiungi da codice prodotto",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = uiState.productCodeInput,
                        onValueChange = viewModel::onProductCodeChanged,
                        label = { Text("Codice (EAN/UPC/QR)") },
                        modifier = Modifier.weight(1f),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = viewModel::lookupProductByCode,
                        enabled = !uiState.isProductLookupLoading && uiState.productCodeInput.isNotBlank()
                    ) {
                        Text(if (uiState.isProductLookupLoading) "Ricerca..." else "Cerca")
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
                                viewModel.onProductLookupError("Scanner non disponibile in questa schermata.")
                            } else {
                                val scanner = GmsBarcodeScanning.getClient(activity, scannerOptions)
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        viewModel.onProductCodeScanned(barcode.rawValue.orEmpty())
                                    }
                                    .addOnFailureListener { throwable ->
                                        val isUserCanceled = (throwable as? ApiException)
                                            ?.statusCode == CommonStatusCodes.CANCELED
                                        if (!isUserCanceled) {
                                            viewModel.onProductLookupError("Scansione non riuscita. Riprova.")
                                        }
                                    }
                            }
                        },
                        enabled = !uiState.isProductLookupLoading
                    ) {
                        Text("Scansiona")
                    }
                }

                val lookupError = uiState.productLookupErrorMessage
                if (!lookupError.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = lookupError,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Text(
                    text = "Promemoria supermercato",
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Raggio: 300 m. Notifica inviata solo se hai articoli non spuntati.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF6B6B6B)
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Articoli da comprare: $pendingShoppingItemsCount",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    Button(
                        onClick = {
                            reminderFeedbackMessage = null
                            if (remindersEnabled) {
                                ShoppingProximityReminderManager.setEnabled(context, false)
                                remindersEnabled = false
                                reminderFeedbackMessage = "Promemoria supermercato disattivati."
                            } else {
                                val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                if (notificationPermissionRequired) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    foregroundLocationPermissionsLauncher.launch(
                                        arrayOf(
                                            Manifest.permission.ACCESS_FINE_LOCATION,
                                            Manifest.permission.ACCESS_COARSE_LOCATION
                                        )
                                    )
                                }
                            }
                        }
                    ) {
                        Text(if (remindersEnabled) "Disattiva" else "Attiva")
                    }
                }
                if (!reminderFeedbackMessage.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = reminderFeedbackMessage.orEmpty(),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (remindersEnabled) Color(0xFF2E7D32) else MaterialTheme.colorScheme.error
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = uiState.newSectionName,
                onValueChange = viewModel::onNewSectionNameChanged,
                label = { Text("Nuova sezione") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = viewModel::addSection) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi sezione")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(uiState.sections, key = { it.id }) { section ->
                ShoppingSectionCard(
                    section = section,
                    onAddItem = { name, grams ->
                        viewModel.addItem(section.id, name, grams)
                    },
                    onToggleItem = { index, isChecked ->
                        viewModel.toggleItem(section.id, index, isChecked)
                    },
                    onEditItem = { index ->
                        viewModel.startEditingItem(section.id, index)
                    },
                    onDeleteItem = { index ->
                        viewModel.deleteItem(section.id, index)
                    },
                    onDeleteSection = {
                        viewModel.deleteSection(section.id)
                    }
                )
            }
        }
    }

    val pendingProduct = uiState.pendingProduct
    if (pendingProduct != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissPendingProduct,
            title = { Text("Conferma articolo") },
            text = {
                Column {
                    Text(
                        text = "Codice: ${pendingProduct.code}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF6B6B6B)
                    )
                    if (!pendingProduct.brand.isNullOrBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Marca: ${pendingProduct.brand}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF6B6B6B)
                        )
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pendingProduct.name,
                        onValueChange = viewModel::onPendingProductNameChanged,
                        label = { Text("Nome articolo") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = pendingProduct.grams,
                        onValueChange = viewModel::onPendingProductGramsChanged,
                        label = { Text("Grammi (opzionale)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Sezione",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    uiState.sections.forEach { section ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.onPendingProductSectionChanged(section.id) },
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = pendingProduct.sectionId == section.id,
                                onClick = { viewModel.onPendingProductSectionChanged(section.id) }
                            )
                            Text(text = section.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmPendingProduct,
                    enabled = pendingProduct.name.isNotBlank()
                ) {
                    Text("Aggiungi")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissPendingProduct) {
                    Text("Annulla")
                }
            }
        )
    }

    val editingItem = uiState.editingItem
    if (editingItem != null) {
        AlertDialog(
            onDismissRequest = viewModel::dismissEditingItem,
            title = { Text("Modifica articolo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingItem.name,
                        onValueChange = viewModel::onEditingItemNameChanged,
                        label = { Text("Nome articolo") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingItem.grams,
                        onValueChange = viewModel::onEditingItemGramsChanged,
                        label = { Text("Grammi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::saveEditedItem) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissEditingItem) {
                    Text("Annulla")
                }
            }
        )
    }
}

@Composable
private fun ShoppingSectionCard(
    section: ShoppingSection,
    onAddItem: (String, String) -> Unit,
    onToggleItem: (Int, Boolean) -> Unit,
    onEditItem: (Int) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onDeleteSection: () -> Unit
) {
    var newItemText by remember(section.id) { mutableStateOf("") }
    var newItemGrams by remember(section.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = section.name,
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDeleteSection) {
                    Icon(Icons.Default.Delete, contentDescription = "Elimina sezione")
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newItemText,
                    onValueChange = { newItemText = it },
                    label = { Text("Aggiungi articolo") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(
                    value = newItemGrams,
                    onValueChange = { newItemGrams = it },
                    label = { Text("Grammi") },
                    modifier = Modifier.width(110.dp),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(onClick = {
                    val name = newItemText.trim()
                    val grams = newItemGrams.trim()
                    if (name.isNotEmpty()) {
                        onAddItem(name, grams)
                        newItemText = ""
                        newItemGrams = ""
                    }
                }) {
                    Icon(Icons.Default.Add, contentDescription = "Aggiungi")
                }
            }

            if (section.items.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
            }

            section.items.forEachIndexed { index, item ->
                ShoppingItemRow(
                    item = item,
                    onToggle = { isChecked -> onToggleItem(index, isChecked) },
                    onEdit = { onEditItem(index) },
                    onDelete = { onDeleteItem(index) }
                )
            }
        }
    }
}

@Composable
private fun ShoppingItemRow(
    item: ShoppingItem,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle(!item.isChecked) }
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(
            checked = item.isChecked,
            onCheckedChange = onToggle
        )
        Spacer(modifier = Modifier.width(16.dp))
        Text(
            text = item.name,
            style = if (item.isChecked) {
                TextStyle(textDecoration = TextDecoration.LineThrough, color = Color.Gray)
            } else {
                MaterialTheme.typography.bodyLarge
            },
            modifier = Modifier.weight(1f)
        )
        if (item.grams.isNotBlank()) {
            Text(
                text = "${item.grams} g",
                color = Color(0xFF9E9E9E),
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(end = 8.dp)
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Default.Edit, contentDescription = "Modifica articolo")
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Default.Delete, contentDescription = "Elimina articolo")
        }
    }
}

@Composable
fun ProfileScreen(viewModel: ProfileViewModel) {
    val uiState by viewModel.uiState.collectAsState()

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text(
                text = "Profilo Utente",
                style = MaterialTheme.typography.headlineSmall
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Questi dati verranno usati per calcolare il fabbisogno calorico del diario.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6B6B6B)
            )
        }

        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = uiState.firstName,
                        onValueChange = viewModel::onFirstNameChanged,
                        label = { Text("Nome") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedTextField(
                        value = uiState.lastName,
                        onValueChange = viewModel::onLastNameChanged,
                        label = { Text("Cognome") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Text(
                        text = "Sesso",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        viewModel.sexOptions.forEach { option ->
                            FilterChip(
                                selected = uiState.sex == option,
                                onClick = { viewModel.onSexChanged(option) },
                                label = { Text(option) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = uiState.age,
                        onValueChange = viewModel::onAgeChanged,
                        label = { Text("Età") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )

                    OutlinedTextField(
                        value = uiState.heightCm,
                        onValueChange = viewModel::onHeightChanged,
                        label = { Text("Altezza") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text("cm") }
                    )

                    OutlinedTextField(
                        value = uiState.weightKg,
                        onValueChange = viewModel::onWeightChanged,
                        label = { Text("Peso") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        suffix = { Text("kg") }
                    )

                    Text(
                        text = "Calorie bruciate con sport",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = uiState.sportCaloriesInput,
                            onValueChange = viewModel::onSportCaloriesChanged,
                            label = { Text("kcal") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(onClick = viewModel::addSportCalories) {
                            Text("Aggiungi")
                        }
                    }
                    Text(
                        text = "Totale sport: ${uiState.sportCaloriesTotal} kcal",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6B6B6B)
                    )

                    Button(
                        onClick = viewModel::saveProfile,
                        enabled = uiState.canSave,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Salva dati profilo")
                    }

                    if (uiState.showSavedMessage) {
                        Text(
                            text = "Dati profilo salvati correttamente.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF2E7D32)
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        HomeScreen()
    }
}
