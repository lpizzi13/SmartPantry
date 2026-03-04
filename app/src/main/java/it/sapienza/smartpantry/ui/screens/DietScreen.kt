package it.sapienza.smartpantry.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.Diet
import it.sapienza.smartpantry.model.DietViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DietScreen(uid: String = "", dietViewModel: DietViewModel = viewModel()) {
    val uiState by dietViewModel.uiState.collectAsState()
    var menuExpanded by remember { mutableStateOf(false) }
    var renameDialogOpen by remember { mutableStateOf<Diet?>(null) }
    var renameDraft by remember { mutableStateOf("") }

    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            dietViewModel.initialize(uid)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ExposedDropdownMenuBox(
            expanded = menuExpanded,
            onExpandedChange = { menuExpanded = !menuExpanded }
        ) {
            OutlinedTextField(
                value = uiState.selectedDiet?.name ?: "",
                onValueChange = {},
                readOnly = true,
                trailingIcon = {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuExpanded)
                },
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth()
            )

            ExposedDropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                uiState.diets.forEach { diet ->
                    DropdownMenuItem(
                        text = { Text(diet.name) },
                        onClick = {
                            dietViewModel.onDietSelected(diet.id)
                            menuExpanded = false
                        },
                        trailingIcon = if (diet.isEditable) {
                            {
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
                        } else {
                            null
                        }
                    )
                }
            }
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
                        label = { Text("Diet name") },
                        placeholder = { Text("New Diet") }
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            dietViewModel.onDietNameChanged(diet.id, renameDraft)
                            renameDialogOpen = null
                        }
                    ) {
                        Text("Save")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { renameDialogOpen = null }) {
                        Text("Cancel")
                    }
                }
            )
        }

        uiState.selectedDiet?.let { diet ->
            if (diet.name == "Weekly Diet Plan") {
                WeeklyDietPlanContent(
                    diet = diet,
                    onDayClicked = { dayIndex -> dietViewModel.onDayClicked(diet.id, dayIndex) }
                )
            } else {
                NewDietContent(
                    diet = diet,
                    onDayClicked = { dayIndex -> dietViewModel.onDayClicked(diet.id, dayIndex) },
                    onAddDay = { dayName -> dietViewModel.addDayToDiet(diet.id, dayName) }
                )
            }
        }
    }
}

@Composable
private fun WeeklyDietPlanContent(
    diet: Diet,
    onDayClicked: (Int) -> Unit
) {
    diet.days.forEachIndexed { index, day ->
        val isExpanded = diet.expandedDayIndex == index

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = day,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )

                    TextButton(
                        onClick = { onDayClicked(index) }
                    ) {
                        Text(if (isExpanded) "Hide" else "Show")
                    }
                }

                if (isExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                    Text(
                        text = "Section for $day",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NewDietContent(
    diet: Diet,
    onDayClicked: (Int) -> Unit,
    onAddDay: (String) -> Unit
) {
    var addDayDialogOpen by remember { mutableStateOf(false) }
    var newDayDraft by remember { mutableStateOf("") }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 80.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (diet.days.isEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Create days for this diet.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Use the + button to add a new day.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            diet.days.forEachIndexed { index, day ->
                val isExpanded = diet.expandedDayIndex == index

                Card(
                    modifier = Modifier.fillMaxWidth(),
                    elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = day,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )

                            TextButton(
                                onClick = { onDayClicked(index) }
                            ) {
                                Text(if (isExpanded) "Hide" else "Show")
                            }
                        }

                        if (isExpanded) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                            Text(
                                text = "Section for $day",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = {
                val nextDayNumber = diet.days.count { it.startsWith("Day ") } + 1
                newDayDraft = "Day $nextDayNumber"
                addDayDialogOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add day"
            )
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
                    label = { Text("Day name") },
                    placeholder = { Text("Day ${diet.days.count { it.startsWith("Day ") } + 1}") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fallbackDayName = "Day ${diet.days.count { it.startsWith("Day ") } + 1}"
                        val dayName = newDayDraft.trim().ifBlank { fallbackDayName }
                        onAddDay(dayName)
                        addDayDialogOpen = false
                    }
                ) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { addDayDialogOpen = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
