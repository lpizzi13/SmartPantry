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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.DayPlan
import it.sapienza.smartpantry.model.Diet
import it.sapienza.smartpantry.model.DietViewModel

@Composable
fun DietScreen(uid: String = "", dietViewModel: DietViewModel = viewModel()) {
    // Observe global state from ViewModel
    val uiState by dietViewModel.uiState.collectAsState()

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

    // Initialize ViewModel with user ID
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            dietViewModel.initialize(uid)
        }
    }

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
                                    dietViewModel.onDietSelected(diet.id)
                                    menuExpanded = false
                                },
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            renameDraft = diet.name
                                            renameDialogOpen = diet
                                            menuExpanded = false
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Edit,
                                            contentDescription = "Rename diet"
                                        )
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
                        IconButton(onClick = { dietViewModel.toggleFavorite(diet.id) }) {
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
                                        dietViewModel.onDietWeeklyToggled(diet.id, isChecked)
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
                        onDayClicked = { dayIndex -> dietViewModel.onDayClicked(diet.id, dayIndex) },
                        onAddFood = { dayIndex, foodName, mealType -> dietViewModel.addFoodToDay(diet.id, dayIndex, foodName, mealType) },
                        onEditFood = { dayIndex, mealType, foodIndex, newName -> dietViewModel.editFoodInDay(diet.id, dayIndex, mealType, foodIndex, newName) },
                        onRemoveFood = { dayIndex, mealType, foodIndex -> dietViewModel.removeFoodFromDay(diet.id, dayIndex, mealType, foodIndex) }
                    )
                } else {
                    NewDietContent(
                        diet = diet,
                        onDayClicked = { dayIndex -> dietViewModel.onDayClicked(diet.id, dayIndex) },
                        onAddDay = { dayName -> dietViewModel.addDayToDiet(diet.id, dayName) },
                        onAddFood = { dayIndex, foodName, mealType -> dietViewModel.addFoodToDay(diet.id, dayIndex, foodName, mealType) },
                        onEditFood = { dayIndex, mealType, foodIndex, newName -> dietViewModel.editFoodInDay(diet.id, dayIndex, mealType, foodIndex, newName) },
                        onRemoveFood = { dayIndex, mealType, foodIndex -> dietViewModel.removeFoodFromDay(diet.id, dayIndex, mealType, foodIndex) }
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
                            dietViewModel.onDietWeeklyToggled(diet.id, pendingWeeklyToggle)
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

    renameDialogOpen?.let { diet ->
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
                        dietViewModel.onDietNameChanged(diet.id, renameDraft)
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
        DayCard(
            dayPlan = dayPlan,
            isExpanded = diet.expandedDayIndices.contains(index),
            onDayClicked = { onDayClicked(index) },
            onAddFood = { foodName, mealType -> onAddFood(index, foodName, mealType) },
            onEditFood = { mealType, fIdx, newName -> onEditFood(index, mealType, fIdx, newName) },
            onRemoveFood = { mealType, fIdx -> onRemoveFood(index, mealType, fIdx) }
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
    var addDayDialogOpen by remember { mutableStateOf(false) }
    var newDayDraft by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            diet.days.forEachIndexed { index, dayPlan ->
                DayCard(
                    dayPlan = dayPlan,
                    isExpanded = diet.expandedDayIndices.contains(index),
                    onDayClicked = { onDayClicked(index) },
                    onAddFood = { foodName, mealType -> onAddFood(index, foodName, mealType) },
                    onEditFood = { mealType, fIdx, newName -> onEditFood(index, mealType, fIdx, newName) },
                    onRemoveFood = { mealType, fIdx -> onRemoveFood(index, mealType, fIdx) }
                )
            }
        }

        // Floating button to add days - MOVED TO LEFT
        FloatingActionButton(
            onClick = {
                val nextDayNumber = diet.days.count { it.name.startsWith("Day ") } + 1
                newDayDraft = "Day $nextDayNumber"
                addDayDialogOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(16.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Add day")
        }
    }

    if (addDayDialogOpen) {
        AlertDialog(
            onDismissRequest = { addDayDialogOpen = false },
            title = { Text("Add day") },
            text = {
                OutlinedTextField(
                    value = newDayDraft,
                    onValueChange = { newDayDraft = it },
                    singleLine = true,
                    label = { Text("Day name") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAddDay(newDayDraft.trim().ifBlank { "Day ${diet.days.size + 1}" })
                        addDayDialogOpen = false
                    }
                ) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { addDayDialogOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DayCard(
    dayPlan: DayPlan,
    isExpanded: Boolean,
    onDayClicked: () -> Unit,
    onAddFood: (String, String) -> Unit,
    onEditFood: (String, Int, String) -> Unit,
    onRemoveFood: (String, Int) -> Unit
) {
    var addFoodDialogOpen by remember { mutableStateOf(false) }
    var foodDraft by remember { mutableStateOf("") }
    var selectedMealType by remember { mutableStateOf("Colazione") }
    val mealTypes = listOf("Colazione", "Pranzo", "Cena", "Snacks")
    var mealTypeExpanded by remember { mutableStateOf(false) }

    // State for editing food
    var editFoodDialogOpen by remember { mutableStateOf(false) }
    var foodToEdit by remember { mutableStateOf<Triple<String, Int, String>?>(null) } // MealType, Index, CurrentName
    var editFoodDraft by remember { mutableStateOf("") }

    Card(modifier = Modifier.fillMaxWidth(), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayPlan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row {
                    IconButton(onClick = { addFoodDialogOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add food")
                    }
                    TextButton(onClick = onDayClicked) {
                        Text(if (isExpanded) "Hide" else "Show")
                    }
                }
            }
            
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                
                MealSection("Colazione", dayPlan.breakfast, onEdit = { idx, name -> 
                    foodToEdit = Triple("Colazione", idx, name)
                    editFoodDraft = name
                    editFoodDialogOpen = true
                }, onRemove = { idx -> onRemoveFood("Colazione", idx) })
                
                MealSection("Pranzo", dayPlan.lunch, onEdit = { idx, name -> 
                    foodToEdit = Triple("Pranzo", idx, name)
                    editFoodDraft = name
                    editFoodDialogOpen = true
                }, onRemove = { idx -> onRemoveFood("Pranzo", idx) })
                
                MealSection("Cena", dayPlan.dinner, onEdit = { idx, name -> 
                    foodToEdit = Triple("Cena", idx, name)
                    editFoodDraft = name
                    editFoodDialogOpen = true
                }, onRemove = { idx -> onRemoveFood("Cena", idx) })
                
                MealSection("Snacks", dayPlan.snacks, onEdit = { idx, name -> 
                    foodToEdit = Triple("Snacks", idx, name)
                    editFoodDraft = name
                    editFoodDialogOpen = true
                }, onRemove = { idx -> onRemoveFood("Snacks", idx) })
                
                if (dayPlan.breakfast.isEmpty() && dayPlan.lunch.isEmpty() && dayPlan.dinner.isEmpty() && dayPlan.snacks.isEmpty()) {
                    Text("No foods added.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }

    if (addFoodDialogOpen) {
        AlertDialog(
            onDismissRequest = { addFoodDialogOpen = false },
            title = { Text("Add Food to ${dayPlan.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    ExposedDropdownMenuBox(
                        expanded = mealTypeExpanded,
                        onExpandedChange = { mealTypeExpanded = !mealTypeExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedMealType,
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Meal") },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mealTypeExpanded) },
                            modifier = Modifier.menuAnchor().fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = mealTypeExpanded,
                            onDismissRequest = { mealTypeExpanded = false }
                        ) {
                            mealTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type) },
                                    onClick = {
                                        selectedMealType = type
                                        mealTypeExpanded = false
                                    }
                                )
                            }
                        }
                    }
                    OutlinedTextField(
                        value = foodDraft,
                        onValueChange = { foodDraft = it },
                        singleLine = true,
                        label = { Text("Food name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddFood(foodDraft, selectedMealType)
                    foodDraft = ""
                    addFoodDialogOpen = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { addFoodDialogOpen = false }) { Text("Cancel") }
            }
        )
    }

    if (editFoodDialogOpen) {
        AlertDialog(
            onDismissRequest = { editFoodDialogOpen = false },
            title = { Text("Edit Food") },
            text = {
                OutlinedTextField(
                    value = editFoodDraft,
                    onValueChange = { editFoodDraft = it },
                    singleLine = true,
                    label = { Text("Food name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    foodToEdit?.let { (mealType, idx, _) ->
                        onEditFood(mealType, idx, editFoodDraft)
                    }
                    editFoodDialogOpen = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editFoodDialogOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun MealSection(title: String, foods: List<String>, onEdit: (Int, String) -> Unit, onRemove: (Int) -> Unit) {
    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (foods.isEmpty()) {
            Text(
                text = "Empty",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 8.dp)
            )
        } else {
            foods.forEachIndexed { index, food ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 8.dp, bottom = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "• $food",
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row {
                        IconButton(onClick = { onEdit(index, food) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit", modifier = Modifier.size(16.dp))
                        }
                        IconButton(onClick = { onRemove(index) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Default.Delete, contentDescription = "Remove", modifier = Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun DietScreenPreview() {
    DietScreen()
}
