package it.sapienza.smartpantry.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
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
                                    val hasFoods = diet.days.any { it.foods.isNotEmpty() }
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
                        onAddFood = { dayIndex, foodName -> dietViewModel.addFoodToDay(diet.id, dayIndex, foodName) }
                    )
                } else {
                    NewDietContent(
                        diet = diet,
                        onDayClicked = { dayIndex -> dietViewModel.onDayClicked(diet.id, dayIndex) },
                        onAddDay = { dayName -> dietViewModel.addDayToDiet(diet.id, dayName) },
                        onAddFood = { dayIndex, foodName -> dietViewModel.addFoodToDay(diet.id, dayIndex, foodName) }
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
    onAddFood: (Int, String) -> Unit
) {
    diet.days.forEachIndexed { index, dayPlan ->
        DayCard(
            dayPlan = dayPlan,
            isExpanded = diet.expandedDayIndices.contains(index),
            onDayClicked = { onDayClicked(index) },
            onAddFood = { foodName -> onAddFood(index, foodName) }
        )
    }
}

@Composable
private fun NewDietContent(
    diet: Diet,
    onDayClicked: (Int) -> Unit,
    onAddDay: (String) -> Unit,
    onAddFood: (Int, String) -> Unit
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
                    onAddFood = { foodName -> onAddFood(index, foodName) }
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

@Composable
private fun DayCard(
    dayPlan: DayPlan,
    isExpanded: Boolean,
    onDayClicked: () -> Unit,
    onAddFood: (String) -> Unit
) {
    var addFoodDialogOpen by remember { mutableStateOf(false) }
    var foodDraft by remember { mutableStateOf("") }

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
                if (dayPlan.foods.isEmpty()) {
                    Text("No foods added.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    dayPlan.foods.forEach { food ->
                        Text("• $food", modifier = Modifier.padding(vertical = 2.dp))
                    }
                }
            }
        }
    }

    if (addFoodDialogOpen) {
        AlertDialog(
            onDismissRequest = { addFoodDialogOpen = false },
            title = { Text("Add Food to ${dayPlan.name}") },
            text = {
                OutlinedTextField(value = foodDraft, onValueChange = { foodDraft = it }, singleLine = true, label = { Text("Food name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddFood(foodDraft)
                    foodDraft = ""
                    addFoodDialogOpen = false
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { addFoodDialogOpen = false }) { Text("Cancel") }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DietScreenPreview() {
    DietScreen()
}
