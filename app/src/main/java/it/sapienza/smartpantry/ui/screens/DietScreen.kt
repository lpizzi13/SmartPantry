package it.sapienza.smartpantry.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.DayPlan
import it.sapienza.smartpantry.model.Diet
import it.sapienza.smartpantry.model.DietViewModel

@Composable
fun DietScreen(uid: String = "", dietViewModel: DietViewModel = viewModel()) {
    // Observe global state from ViewModel
    val uiState by dietViewModel.uiState.collectAsState()

    // Lifecycle observer to refresh data when screen is resumed (navigated back to)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                if (uid.isNotBlank()) {
                    dietViewModel.initialize(uid)
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // LOCAL STATE: Manages opening/closing of the diet dropdown menu
    var menuExpanded by remember { mutableStateOf(false) }

    // LOCAL STATE: Dialog for renaming diet
    var renameDialogOpen by remember { mutableStateOf<Diet?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    // LOCAL STATE: Dialog for creating new diet
    var addDietDialogOpen by remember { mutableStateOf(false) }
    var newDietNameDraft by remember { mutableStateOf("") }

    // LOCAL STATE: Manages warning for Weekly/Custom mode change
    var showWeeklyWarning by remember { mutableStateOf(false) }
    var pendingWeeklyToggle by remember { mutableStateOf(false) }

    // LOCAL STATE: Dialog for deleting diet
    var dietToDelete by remember { mutableStateOf<Diet?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // --- DIET SELECTOR, FAVORITE AND WEEKLY SWITCH ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Box for diet selector
                Box {
                    Row(
                        modifier = Modifier
                            .clickable { menuExpanded = true }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = uiState.selectedDiet?.name ?: "Select Diet",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold
                        )
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Select diet"
                        )
                    }

                    // Dropdown menu to change diet
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false }
                    ) {
                        uiState.diets.forEach { diet ->
                            DropdownMenuItem(
                                text = { Text(diet.name) },
                                leadingIcon = {
                                    if (diet.isFavorite) {
                                        Icon(
                                            imageVector = Icons.Default.Favorite,
                                            contentDescription = null,
                                            tint = Color.Red,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                },
                                onClick = {
                                    dietViewModel.onDietSelected(diet.duid)
                                    menuExpanded = false
                                },
                                trailingIcon = {
                                    Row {
                                        IconButton(
                                            onClick = {
                                                renameDraft = diet.name
                                                renameDialogOpen = diet
                                                menuExpanded = false
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = "Rename diet",
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                dietToDelete = diet
                                                menuExpanded = false
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Delete,
                                                contentDescription = "Delete diet",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }
                                }
                            )
                        }
                    }
                }

                // Right column: Favorite and Weekly Switch
                Row(verticalAlignment = Alignment.CenterVertically) {
                    uiState.selectedDiet?.let { diet ->
                        // Heart button for Favorite Diet
                        IconButton(onClick = { dietViewModel.toggleFavorite(diet.duid) }) {
                            Icon(
                                imageVector = if (diet.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                contentDescription = "Favorite",
                                tint = if (diet.isFavorite) Color.Red else LocalContentColor.current
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        // Switch to convert to Weekly Diet
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Weekly", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = diet.isWeekly,
                                onCheckedChange = { isChecked ->
                                    val hasFoods = diet.days.any { it.breakfast.isNotEmpty() || it.lunch.isNotEmpty() || it.dinner.isNotEmpty() || it.snacks.isNotEmpty() }
                                    if (hasFoods) {
                                        pendingWeeklyToggle = isChecked
                                        showWeeklyWarning = true
                                    } else {
                                        dietViewModel.onDietWeeklyToggled(diet.duid, isChecked)
                                    }
                                }
                            )
                        }
                    }
                }
            }

            // Content display based on selected diet
            uiState.selectedDiet?.let { diet ->
                if (diet.isWeekly) {
                    WeeklyDietPlanContent(
                        diet = diet,
                        onDayClicked = { dayIndex -> dietViewModel.onDayClicked(diet.duid, dayIndex) },
                        onAddFood = { dayIndex, foodName, mealType -> dietViewModel.addFoodToDay(diet.duid, dayIndex, foodName, mealType) },
                        onEditFood = { dayIndex, mealType, foodIndex, newName -> dietViewModel.editFoodInDay(diet.duid, dayIndex, mealType, foodIndex, newName) },
                        onRemoveFood = { dayIndex, mealType, foodIndex -> dietViewModel.removeFoodFromDay(diet.duid, dayIndex, mealType, foodIndex) }
                    )
                } else {
                    NewDietContent(
                        diet = diet,
                        onDayClicked = { dayIndex -> dietViewModel.onDayClicked(diet.duid, dayIndex) },
                        onAddDay = { dayName -> dietViewModel.addDayToDiet(diet.duid, dayName) },
                        onAddFood = { dayIndex, foodName, mealType -> dietViewModel.addFoodToDay(diet.duid, dayIndex, foodName, mealType) },
                        onEditFood = { dayIndex, mealType, foodIndex, newName -> dietViewModel.editFoodInDay(diet.duid, dayIndex, mealType, foodIndex, newName) },
                        onRemoveFood = { dayIndex, mealType, foodIndex -> dietViewModel.removeFoodFromDay(diet.duid, dayIndex, mealType, foodIndex) }
                    )
                }
            } ?: run {
                // Message if no diets are present
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No diets found. Create one using the + button", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }

        // --- FAB FOR NEW DIET (BOTTOM RIGHT) ---
        FloatingActionButton(
            onClick = {
                newDietNameDraft = "New Diet ${uiState.diets.size + 1}"
                addDietDialogOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ) {
            Icon(Icons.Default.PostAdd, contentDescription = "Add Diet")
        }
    }

    // --- DIALOGS ---

    if (showWeeklyWarning) {
        AlertDialog(
            onDismissRequest = { showWeeklyWarning = false },
            title = { Text("Warning") },
            text = { Text("Changing the diet mode will clear all currently added foods. Do you want to proceed?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        uiState.selectedDiet?.let { diet ->
                            dietViewModel.onDietWeeklyToggled(diet.duid, pendingWeeklyToggle)
                        }
                        showWeeklyWarning = false
                    }
                ) { Text("Proceed") }
            },
            dismissButton = {
                TextButton(onClick = { showWeeklyWarning = false }) { Text("Cancel") }
            }
        )
    }

    if (renameDialogOpen != null) {
        AlertDialog(
            onDismissRequest = { renameDialogOpen = null },
            title = { Text("Rename diet") },
            text = {
                OutlinedTextField(
                    value = renameDraft,
                    onValueChange = { renameDraft = it },
                    singleLine = true,
                    label = { Text("Diet name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        renameDialogOpen?.let { diet ->
                            dietViewModel.onDietNameChanged(diet.duid, renameDraft)
                        }
                        renameDialogOpen = null
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogOpen = null }) { Text("Cancel") }
            }
        )
    }

    if (addDietDialogOpen) {
        AlertDialog(
            onDismissRequest = { addDietDialogOpen = false },
            title = { Text("New Diet") },
            text = {
                OutlinedTextField(
                    value = newDietNameDraft,
                    onValueChange = { newDietNameDraft = it },
                    singleLine = true,
                    label = { Text("Diet name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        dietViewModel.addNewDiet(newDietNameDraft)
                        addDietDialogOpen = false
                    }
                ) { Text("Create") }
            },
            dismissButton = {
                TextButton(onClick = { addDietDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (dietToDelete != null) {
        AlertDialog(
            onDismissRequest = { dietToDelete = null },
            title = { Text("Delete Diet") },
            text = { Text("Are you sure you want to delete '${dietToDelete?.name}'? This action cannot be undone.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        dietToDelete?.let { diet ->
                            dietViewModel.deleteDiet(diet.duid)
                        }
                        dietToDelete = null
                    },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { dietToDelete = null }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun WeeklyDietPlanContent(
    diet: Diet,
    onDayClicked: (Int) -> Unit,
    onAddFood: (Int, String, String) -> Unit,
    onEditFood: (Int, String, Int, String) -> Unit,
    onRemoveFood: (Int, String, Int) -> Unit
) {
    diet.days.forEachIndexed { index, dayPlan ->
        DayPlanItem(
            dayPlan = dayPlan,
            isExpanded = diet.expandedDayIndices.contains(index),
            onToggleExpand = { onDayClicked(index) },
            onAddFood = { food, meal -> onAddFood(index, food, meal) },
            onEditFood = { meal, fIdx, newName -> onEditFood(index, meal, fIdx, newName) },
            onRemoveFood = { meal, fIdx -> onRemoveFood(index, meal, fIdx) }
        )
    }
}

@Composable
private fun NewDietContent(
    diet: Diet,
    onDayClicked: (Int) -> Unit,
    onAddDay: (String) -> Unit,
    onAddFood: (Int, String, String) -> Unit,
    onEditFood: (Int, String, Int, String) -> Unit,
    onRemoveFood: (Int, String, Int) -> Unit
) {
    var showAddDayDialog by remember { mutableStateOf(false) }
    var newDayName by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        diet.days.forEachIndexed { index, dayPlan ->
            DayPlanItem(
                dayPlan = dayPlan,
                isExpanded = diet.expandedDayIndices.contains(index),
                onToggleExpand = { onDayClicked(index) },
                onAddFood = { food, meal -> onAddFood(index, food, meal) },
                onEditFood = { meal, fIdx, newName -> onEditFood(index, meal, fIdx, newName) },
                onRemoveFood = { meal, fIdx -> onRemoveFood(index, meal, fIdx) }
            )
        }

        Button(
            onClick = { showAddDayDialog = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Add custom day")
        }
    }

    if (showAddDayDialog) {
        AlertDialog(
            onDismissRequest = { showAddDayDialog = false },
            title = { Text("Add Day") },
            text = {
                OutlinedTextField(
                    value = newDayName,
                    onValueChange = { newDayName = it },
                    label = { Text("Day Name (e.g., Day 1, Travel Day)") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddDay(newDayName)
                    newDayName = ""
                    showAddDayDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDayDialog = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun DayPlanItem(
    dayPlan: DayPlan,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onAddFood: (String, String) -> Unit,
    onEditFood: (String, Int, String) -> Unit,
    onRemoveFood: (String, Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(dayPlan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Icon(
                    imageVector = Icons.Default.ArrowDropDown,
                    contentDescription = null,
                    modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                )
            }

            if (isExpanded) {
                Spacer(modifier = Modifier.height(8.dp))
                MealSection("Breakfast", dayPlan.breakfast, { onAddFood(it, "Breakfast") }, { idx, name -> onEditFood("Breakfast", idx, name) }, { idx -> onRemoveFood("Breakfast", idx) })
                MealSection("Lunch", dayPlan.lunch, { onAddFood(it, "Lunch") }, { idx, name -> onEditFood("Lunch", idx, name) }, { idx -> onRemoveFood("Lunch", idx) })
                MealSection("Dinner", dayPlan.dinner, { onAddFood(it, "Dinner") }, { idx, name -> onEditFood("Dinner", idx, name) }, { idx -> onRemoveFood("Dinner", idx) })
                MealSection("Snacks", dayPlan.snacks, { onAddFood(it, "Snacks") }, { idx, name -> onEditFood("Snacks", idx, name) }, { idx -> onRemoveFood("Snacks", idx) })
            }
        }
    }
}

@Composable
fun MealSection(
    title: String,
    foods: List<String>,
    onAddFood: (String) -> Unit,
    onEditFood: (Int, String) -> Unit,
    onRemoveFood: (Int) -> Unit
) {
    var showAddDialog by remember { mutableStateOf(false) }
    var foodDraft by remember { mutableStateOf("") }
    
    var editingFoodIndex by remember { mutableStateOf<Int?>(null) }
    var editFoodDraft by remember { mutableStateOf("") }

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            IconButton(onClick = { showAddDialog = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.Add, contentDescription = "Add food", tint = MaterialTheme.colorScheme.primary)
            }
        }

        if (foods.isEmpty()) {
            Text("No food added", style = MaterialTheme.typography.bodySmall, color = Color.Gray)
        } else {
            foods.forEachIndexed { index, food ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("• $food", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                    IconButton(onClick = {
                        editFoodDraft = food
                        editingFoodIndex = index
                    }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                    }
                    IconButton(onClick = { onRemoveFood(index) }, modifier = Modifier.size(24.dp)) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
    }

    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text("Add to $title") },
            text = {
                OutlinedTextField(value = foodDraft, onValueChange = { foodDraft = it }, label = { Text("Food name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddFood(foodDraft)
                    foodDraft = ""
                    showAddDialog = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) { Text("Cancel") }
            }
        )
    }
    
    if (editingFoodIndex != null) {
        AlertDialog(
            onDismissRequest = { editingFoodIndex = null },
            title = { Text("Edit Food") },
            text = {
                OutlinedTextField(value = editFoodDraft, onValueChange = { editFoodDraft = it }, label = { Text("Food name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    editingFoodIndex?.let { index ->
                        onEditFood(index, editFoodDraft)
                    }
                    editingFoodIndex = null
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingFoodIndex = null }) { Text("Cancel") }
            }
        )
    }
}
