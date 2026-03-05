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
import it.sapienza.smartpantry.model.DietDefaults
import it.sapienza.smartpantry.model.DietViewModel

@Composable
fun DietScreen(uid: String = "", dietViewModel: DietViewModel = viewModel()) {
    // Osserva lo stato globale dal ViewModel
    val uiState by dietViewModel.uiState.collectAsState()

    // STATO LOCALE: Gestisce l'apertura/chiusura del menu a tendina delle diete
    var menuExpanded by remember { mutableStateOf(false) }

    // STATO LOCALE: Se non è null, indica quale oggetto 'Diet' stiamo rinominando e apre il Dialog
    var renameDialogOpen by remember { mutableStateOf<Diet?>(null) }

    // STATO LOCALE: Buffer temporaneo per il testo che l'utente scrive nel campo "Rinomina"
    var renameDraft by remember { mutableStateOf("") }

    // STATO LOCALE: Gestisce il warning per il cambio di modalità Weekly/Custom
    var showWeeklyWarning by remember { mutableStateOf(false) }
    var pendingWeeklyToggle by remember { mutableStateOf(false) }

    // Inizializza il ViewModel con l'ID utente
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
        // --- SELETTORE DIETA, FAVORITE E SWITCH WEEKLY ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Box per il selettore della dieta
            Box {
                Row(
                    modifier = Modifier
                        .clickable { menuExpanded = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = uiState.selectedDiet?.name ?: "Seleziona Dieta",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = "Seleziona dieta"
                    )
                }

                // Menu a tendina per cambiare dieta
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

            // Colonna destra: Favorite e Switch Weekly
            Row(verticalAlignment = Alignment.CenterVertically) {
                uiState.selectedDiet?.let { diet ->
                    // Tasto Cuore per la Dieta Preferita
                    IconButton(onClick = { dietViewModel.toggleFavorite(diet.id) }) {
                        Icon(
                            imageVector = if (diet.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Favorite",
                            tint = if (diet.isFavorite) Color.Red else LocalContentColor.current
                        )
                    }

                    Spacer(modifier = Modifier.width(4.dp))

                    // Switch per convertire in Weekly Diet (visibile solo se la dieta è modificabile)
                    if (diet.isEditable) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Weekly", style = MaterialTheme.typography.labelMedium)
                            Spacer(modifier = Modifier.width(8.dp))
                            Switch(
                                checked = diet.isWeekly,
                                onCheckedChange = { isChecked ->
                                    // Mostra il warning solo se ci sono alimenti inseriti
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
        }

        // --- DIALOGO DI WARNING PER CAMBIO MODALITÀ ---
        if (showWeeklyWarning) {
            AlertDialog(
                onDismissRequest = { showWeeklyWarning = false },
                title = { Text("Attenzione") },
                text = { 
                    Text("Cambiando lo stato della dieta perderai tutti gli alimenti attualmente inseriti. Vuoi procedere?") 
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            uiState.selectedDiet?.let { diet ->
                                dietViewModel.onDietWeeklyToggled(diet.id, pendingWeeklyToggle)
                            }
                            showWeeklyWarning = false
                        }
                    ) {
                        Text("Procedi")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWeeklyWarning = false }) {
                        Text("Annulla")
                    }
                }
            )
        }

        // Dialogo per rinominare la dieta
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

        // Visualizzazione del contenuto in base alla dieta selezionata
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
        }
    }
}

/**
 * Visualizza i giorni della dieta settimanale (Lun-Dom).
 */
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

/**
 * Visualizza i giorni di una dieta personalizzata e permette di aggiungerne di nuovi.
 */
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
            // Messaggio se la dieta è vuota
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
                            text = "Crea i giorni per questa dieta.",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Usa il tasto + in basso per aggiungere un nuovo giorno.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Lista dei giorni
            diet.days.forEachIndexed { index, dayPlan ->
                DayCard(
                    dayPlan = dayPlan,
                    isExpanded = diet.expandedDayIndices.contains(index),
                    onDayClicked = { onDayClicked(index) },
                    onAddFood = { foodName -> onAddFood(index, foodName) }
                )
            }
        }

        // Bottone fluttuante per aggiungere giorni
        FloatingActionButton(
            onClick = {
                val nextDayNumber = diet.days.count { it.name.startsWith("Giorno ") } + 1
                newDayDraft = "Giorno $nextDayNumber"
                addDayDialogOpen = true
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Aggiungi giorno"
            )
        }
    }

    // Dialogo per l'aggiunta di un nuovo giorno
    if (addDayDialogOpen) {
        AlertDialog(
            onDismissRequest = { addDayDialogOpen = false },
            title = { Text("Aggiungi giorno") },
            text = {
                OutlinedTextField(
                    value = newDayDraft,
                    onValueChange = { newDayDraft = it },
                    singleLine = true,
                    label = { Text("Nome giorno") },
                    placeholder = { Text("Giorno ${diet.days.count { it.name.startsWith("Giorno ") } + 1}") }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val fallbackDayName = "Giorno ${diet.days.count { it.name.startsWith("Giorno ") } + 1}"
                        val dayName = newDayDraft.trim().ifBlank { fallbackDayName }
                        onAddDay(dayName)
                        addDayDialogOpen = false
                    }
                ) {
                    Text("Aggiungi")
                }
            },
            dismissButton = {
                TextButton(onClick = { addDayDialogOpen = false }) {
                    Text("Annulla")
                }
            }
        )
    }
}

/**
 * Rappresenta la scheda di un singolo giorno con la lista degli alimenti.
 */
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
            // Header: Nome giorno + Tasto Aggiungi Cibo + Tasto Show/Hide
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(dayPlan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { addFoodDialogOpen = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Aggiungi alimento", modifier = Modifier.size(20.dp))
                    }
                    TextButton(onClick = onDayClicked) {
                        Text(if (isExpanded) "Nascondi" else "Mostra")
                    }
                }
            }
            
            // Corpo della scheda: lista alimenti (visibile solo se isExpanded è true)
            if (isExpanded) {
                HorizontalDivider(modifier = Modifier.padding(vertical = 6.dp))
                if (dayPlan.foods.isEmpty()) {
                    Text(
                        text = "Nessun alimento aggiunto.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    dayPlan.foods.forEach { food ->
                        Text(
                            text = "• $food",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    }

    // Dialogo per l'aggiunta di un nuovo alimento
    if (addFoodDialogOpen) {
        AlertDialog(
            onDismissRequest = { addFoodDialogOpen = false },
            title = { Text("Aggiungi Alimento a ${dayPlan.name}") },
            text = {
                OutlinedTextField(
                    value = foodDraft,
                    onValueChange = { foodDraft = it },
                    singleLine = true,
                    label = { Text("Nome alimento") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onAddFood(foodDraft)
                    foodDraft = ""
                    addFoodDialogOpen = false
                }) { Text("Aggiungi") }
            },
            dismissButton = {
                TextButton(onClick = { addFoodDialogOpen = false }) { Text("Annulla") }
            }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun DietScreenPreview() {
    DietScreen()
}
