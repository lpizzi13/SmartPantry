package it.sapienza.smartpantry.ui.screens

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import it.sapienza.smartpantry.model.DietViewModel
import it.sapienza.smartpantry.model.ShoppingListItem
import it.sapienza.smartpantry.service.*
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

private val ShoppingListBgColor = Color(0xFF0A120E)
private val ShoppingListCardColor = Color(0xFF1A2421)
private val ShoppingListAccentColor = Color(0xFF00E676)

@Composable
fun ShoppingListScreen(
    uid: String,
    dietViewModel: DietViewModel = viewModel(),
    pantryViewModel: PantryViewModel = viewModel()
) {
    val dietState by dietViewModel.uiState.collectAsState()
    val pantryState by pantryViewModel.uiState.collectAsState()
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    val scanner = remember { GmsBarcodeScanning.getClient(context) }

    var items by remember { mutableStateOf<List<ShoppingListItem>>(emptyList()) }
    var itemToMarkAsScanned by remember { mutableStateOf<Int?>(null) }
    var newItemName by remember { mutableStateOf("") }
    var isGenerating by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var showClearDialog by remember { mutableStateOf(false) }

    // Shake detector logic
    DisposableEffect(context) {
        val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

        val shakeListener = object : SensorEventListener {
            private var lastShakeTime: Long = 0
            override fun onSensorChanged(event: SensorEvent?) {
                if (event == null) return
                val x = event.values[0]
                val y = event.values[1]
                val z = event.values[2]

                val acceleration = kotlin.math.sqrt((x * x + y * y + z * z).toDouble()) - SensorManager.GRAVITY_EARTH
                if (acceleration > 20) {
                    val currentTime = System.currentTimeMillis()
                    if (currentTime - lastShakeTime > 1500) {
                        lastShakeTime = currentTime
                        showClearDialog = true
                    }
                }
            }
            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
        }

        sensorManager.registerListener(shakeListener, accelerometer, SensorManager.SENSOR_DELAY_UI)
        onDispose {
            sensorManager.unregisterListener(shakeListener)
        }
    }

    // Initialize PantryViewModel and collect events
    LaunchedEffect(uid) {
        pantryViewModel.bindToUser(uid)
    }

    fun syncList(newItems: List<ShoppingListItem>, itemToAdd: ShoppingListItem? = null, replace: Boolean = true) {
        items = newItems
        val request = if (itemToAdd != null && !replace) {
            UpdateShoppingListRequest(uid, item = itemToAdd, replace = false)
        } else {
            UpdateShoppingListRequest(uid, shoppingList = newItems, replace = true)
        }

        RetrofitClient.instance.updateShoppingList(request)
            .enqueue(object : Callback<UpdateShoppingListResponse> {
                override fun onResponse(call: Call<UpdateShoppingListResponse>, response: Response<UpdateShoppingListResponse>) {}
                override fun onFailure(call: Call<UpdateShoppingListResponse>, t: Throwable) {}
            })
    }

    LaunchedEffect(pantryViewModel) {
        pantryViewModel.events.collect { message ->
            if (message == "Item added to pantry.") {
                itemToMarkAsScanned?.let { index ->
                    if (index < items.size) {
                        val newList = items.filterIndexed { i, _ -> i != index }
                        syncList(newList, replace = true)
                    }
                }
                itemToMarkAsScanned = null
            }
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    // Load initial list
    LaunchedEffect(uid) {
        RetrofitClient.instance.getShoppingList(GetShoppingListRequest(uid)).enqueue(object : Callback<GetShoppingListResponse> {
            override fun onResponse(call: Call<GetShoppingListResponse>, response: Response<GetShoppingListResponse>) {
                isLoading = false
                if (response.isSuccessful) {
                    items = response.body()?.shoppingList ?: emptyList()
                }
            }
            override fun onFailure(call: Call<GetShoppingListResponse>, t: Throwable) {
                isLoading = false
            }
        })
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(ShoppingListBgColor)
            .padding(horizontal = 16.dp)
    ) {
        Spacer(modifier = Modifier.height(0.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
                Text(
                    text = "SHOPPING LIST",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = ShoppingListAccentColor
                )

                if (isGenerating) {
                    CircularProgressIndicator(
                        color = ShoppingListAccentColor,
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    TextButton(
                        onClick = {
                            val dietId = dietState.selectedDietId
                            if (dietId.isNullOrEmpty()) {
                                Toast.makeText(context, "Please create a diet first", Toast.LENGTH_SHORT).show()
                                return@TextButton
                            }
                            isGenerating = true
                            val request = GenerateShoppingListRequest(uid, dietId)
                            RetrofitClient.instance.generateShoppingList(request).enqueue(object : Callback<List<ShoppingListItem>> {
                                override fun onResponse(call: Call<List<ShoppingListItem>>, response: Response<List<ShoppingListItem>>) {
                                    isGenerating = false
                                    if (response.isSuccessful) {
                                        response.body()?.let { generatedItems ->
                                            syncList(generatedItems, replace = true)
                                        }
                                    }
                                }
                                override fun onFailure(call: Call<List<ShoppingListItem>>, t: Throwable) {
                                    isGenerating = false
                                }
                            })
                        },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        colors = ButtonDefaults.textButtonColors(contentColor = ShoppingListAccentColor)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("GENERATE", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(0.dp))

            if (isLoading) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ShoppingListAccentColor)
                }
            } else {
                NewItemInput(
                    value = newItemName,
                    onValueChange = { newItemName = it },
                    onAdd = {
                        if (newItemName.isNotBlank()) {
                            val trimmed = newItemName.trim()
                            val parts = trimmed.split("\\s+".toRegex())
                            val (name, quantity) = if (parts.size > 1 && parts.last().any { it.isDigit() }) {
                                Pair(parts.dropLast(1).joinToString(" "), parts.last())
                            } else {
                                Pair(trimmed, "")
                            }
                            val newItem = ShoppingListItem(name = name, quantity = quantity)
                            val newList = items + newItem
                            syncList(newList, itemToAdd = newItem, replace = false)
                            newItemName = ""
                            focusManager.clearFocus()
                        }
                    }
                )

                Spacer(modifier = Modifier.height(16.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(items.size) { index ->
                        val item = items[index]
                        ShoppingListItemCard(
                            item = item,
                            onToggleChecked = { checked ->
                                val newList = items.toMutableList().apply {
                                    this[index] = item.copy(isChecked = checked)
                                }
                                syncList(newList, replace = true)
                            },
                            onDelete = {
                                val newList = items.filterIndexed { i, _ -> i != index }
                                syncList(newList, replace = true)
                            },
                            onScan = {
                                itemToMarkAsScanned = index
                                scanner.startScan()
                                    .addOnSuccessListener { barcode ->
                                        barcode.rawValue?.let { code ->
                                            pantryViewModel.handleScannedBarcode(code, overridingName = item.name)
                                        }
                                    }
                                    .addOnFailureListener {
                                        itemToMarkAsScanned = null
                                    }
                                    .addOnCanceledListener {
                                        itemToMarkAsScanned = null
                                    }
                            }
                        )
                    }
                }
            }
        }

    if (pantryState.isEditorVisible) {
        ShoppingFoodEditorDialog(
            state = pantryState,
            isSaving = pantryState.isSaving,
            onDismiss = { pantryViewModel.dismissEditor() },
            onNameChange = { pantryViewModel.onEditorNameChange(it) },
            onQuantityChange = { pantryViewModel.onEditorQuantityChange(it) },
            onKcalChange = { pantryViewModel.onEditorKcalChange(it) },
            onCarbsChange = { pantryViewModel.onEditorCarbsChange(it) },
            onProtChange = { pantryViewModel.onEditorProtChange(it) },
            onFatChange = { pantryViewModel.onEditorFatChange(it) },
            onPackageWeightGramsChange = { pantryViewModel.onEditorPackageWeightGramsChange(it) },
            onSave = { pantryViewModel.saveEditor() }
        )
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = ShoppingListCardColor,
            titleContentColor = Color.White,
            textContentColor = Color.Gray,
            title = { Text("Empty Shopping List?", fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to empty the shopping list?") },
            confirmButton = {
                TextButton(onClick = {
                    syncList(emptyList(), replace = true)
                    showClearDialog = false
                }) {
                    Text("YES", color = ShoppingListAccentColor, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text("NO", color = Color.Gray)
                }
            }
        )
    }
}

@Composable
fun ShoppingFoodEditorDialog(
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
        containerColor = ShoppingListCardColor,
        titleContentColor = Color.White,
        textContentColor = Color.Gray,
        title = { Text("Food details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ShoppingListTextField(value = state.editorNameInput, onValueChange = onNameChange, label = "Name")
                ShoppingListTextField(value = state.editorKcalInput, onValueChange = onKcalChange, label = "Kcal", keyboardType = KeyboardType.Decimal)
                ShoppingListTextField(value = state.editorCarbsInput, onValueChange = onCarbsChange, label = "Carbs", keyboardType = KeyboardType.Decimal)
                ShoppingListTextField(value = state.editorProtInput, onValueChange = onProtChange, label = "Protein", keyboardType = KeyboardType.Decimal)
                ShoppingListTextField(value = state.editorFatInput, onValueChange = onFatChange, label = "Fat", keyboardType = KeyboardType.Decimal)
                ShoppingListTextField(value = state.editorQuantityInput, onValueChange = onQuantityChange, label = "Quantity (pieces)", keyboardType = KeyboardType.Number)
                ShoppingListTextField(value = state.editorPackageWeightGramsInput, onValueChange = onPackageWeightGramsChange, label = "Package weight (g)", keyboardType = KeyboardType.Decimal)
            }
        },
        confirmButton = {
            Button(
                onClick = onSave,
                enabled = !isSaving,
                colors = ButtonDefaults.buttonColors(containerColor = ShoppingListAccentColor, contentColor = Color.Black),
                shape = RoundedCornerShape(12.dp)
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp, color = Color.Black)
                } else {
                    Text("Save to Pantry", fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancel", color = Color.Gray)
            }
        }
    )
}

@Composable
fun ShoppingListTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = ShoppingListAccentColor,
            unfocusedBorderColor = Color.Gray.copy(alpha = 0.5f),
            focusedLabelColor = ShoppingListAccentColor,
            unfocusedLabelColor = Color.Gray,
            cursorColor = ShoppingListAccentColor
        )
    )
}

@Composable
fun NewItemInput(
    value: String,
    onValueChange: (String) -> Unit,
    onAdd: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ShoppingListCardColor.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Add,
                contentDescription = "Add item",
                tint = ShoppingListAccentColor,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { onAdd() }
            )

            Spacer(modifier = Modifier.width(12.dp))

            Box(modifier = Modifier.weight(1f)) {
                if (value.isEmpty()) {
                    Text("Add item (e.g. Avocado 150)", color = Color.Gray)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    ),
                    cursorBrush = SolidColor(ShoppingListAccentColor),
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onAdd() }),
                    singleLine = true
                )
            }
        }
    }
}

@Composable
fun ShoppingListItemCard(
    item: ShoppingListItem,
    onToggleChecked: (Boolean) -> Unit,
    onDelete: () -> Unit,
    onScan: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = ShoppingListCardColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = item.isChecked,
                onCheckedChange = onToggleChecked,
                colors = CheckboxDefaults.colors(
                    checkedColor = ShoppingListAccentColor,
                    uncheckedColor = Color.Gray,
                    checkmarkColor = Color.Black
                )
            )

            Spacer(modifier = Modifier.width(8.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = if (item.isChecked) Color.Gray else Color.White,
                    textDecoration = if (item.isChecked) TextDecoration.LineThrough else null
                )
                if (item.quantity.isNotBlank()) {
                    Text(
                        text = item.quantity,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray
                    )
                }
            }

            IconButton(
                onClick = onScan,
                modifier = Modifier
                    .background(ShoppingListBgColor, RoundedCornerShape(10.dp))
                    .size(36.dp)
            ) {
                Icon(
                    Icons.Default.QrCodeScanner,
                    contentDescription = "Scan barcode",
                    tint = ShoppingListAccentColor,
                    modifier = Modifier.size(20.dp)
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .background(ShoppingListBgColor, RoundedCornerShape(10.dp))
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
