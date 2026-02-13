package it.sapienza.smartpantry.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                MainScreen()
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

data class ShoppingItem(val name: String, val grams: String, val isChecked: Boolean)
data class ShoppingSection(val id: Int, val name: String, val items: SnapshotStateList<ShoppingItem>)

private val ShoppingSectionsSaver: Saver<SnapshotStateList<ShoppingSection>, Any> = listSaver(
    save = { sections ->
        sections.flatMap { section ->
            val itemsFlat = section.items.flatMap { listOf(it.name, it.grams, it.isChecked) }
            listOf(section.id, section.name, section.items.size) + itemsFlat
        }
    },
    restore = { restored ->
        val sections = mutableStateListOf<ShoppingSection>()
        var index = 0
        while (index < restored.size) {
            val id = restored[index++] as Int
            val name = restored[index++] as String
            val itemCount = restored[index++] as Int
            val items = mutableStateListOf<ShoppingItem>()
            repeat(itemCount) {
                val itemName = restored[index++] as String
                val grams = restored[index++] as String
                val isChecked = restored[index++] as Boolean
                items.add(ShoppingItem(itemName, grams, isChecked))
            }
            sections.add(ShoppingSection(id = id, name = name, items = items))
        }
        sections
    }
)

val items = listOf(
    Screen.Home,
    Screen.FoodJournal,
    Screen.ShoppingList,
    Screen.Profile
)

@Composable
fun MainScreen() {
    val navController = rememberNavController()
    Scaffold(
        bottomBar = {
            NavigationBar {
                val navBackStackEntry by navController.currentBackStackEntryAsState()
                val currentDestination = navBackStackEntry?.destination
                items.forEach { screen ->
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
            composable(Screen.FoodJournal.route) { FoodJournalScreen() }
            composable(Screen.ShoppingList.route) { ShoppingListScreen() }
            composable(Screen.Profile.route) { ProfileScreen() }
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
fun FoodJournalScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Food Journal Screen")
    }
}

@Composable
fun ShoppingListScreen() {
    val sections = rememberSaveable(saver = ShoppingSectionsSaver) {
        mutableStateListOf(
            ShoppingSection(
                id = 0,
                name = "Diario",
                items = mutableStateListOf()
            )
        )
    }
    var nextSectionId by rememberSaveable {
        mutableStateOf((sections.maxOfOrNull { it.id } ?: 0) + 1)
    }
    var newSectionName by remember { mutableStateOf("") }
    var editingSectionId by remember { mutableStateOf<Int?>(null) }
    var editingItemIndex by remember { mutableStateOf<Int?>(null) }
    var editingText by remember { mutableStateOf("") }
    var editingGrams by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = newSectionName,
                onValueChange = { newSectionName = it },
                label = { Text("Nuova sezione") },
                modifier = Modifier.weight(1f),
                singleLine = true
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                val name = newSectionName.trim()
                if (name.isNotEmpty()) {
                    sections.add(
                        ShoppingSection(
                            id = nextSectionId,
                            name = name,
                            items = mutableStateListOf()
                        )
                    )
                    nextSectionId += 1
                    newSectionName = ""
                }
            }) {
                Icon(Icons.Default.Add, contentDescription = "Aggiungi sezione")
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(sections, key = { it.id }) { section ->
                ShoppingSectionCard(
                    section = section,
                    onAddItem = { name, grams ->
                        section.items.add(ShoppingItem(name = name, grams = grams, isChecked = false))
                    },
                    onToggleItem = { index, isChecked ->
                        val item = section.items[index]
                        section.items[index] = item.copy(isChecked = isChecked)
                    },
                    onEditItem = { index, item ->
                        editingSectionId = section.id
                        editingItemIndex = index
                        editingText = item.name
                        editingGrams = item.grams
                    },
                    onDeleteItem = { index ->
                        section.items.removeAt(index)
                    },
                    onDeleteSection = {
                        sections.remove(section)
                    }
                )
            }
        }
    }

    if (editingSectionId != null && editingItemIndex != null) {
        AlertDialog(
            onDismissRequest = {
                editingSectionId = null
                editingItemIndex = null
            },
            title = { Text("Modifica articolo") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editingText,
                        onValueChange = { editingText = it },
                        label = { Text("Nome articolo") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editingGrams,
                        onValueChange = { editingGrams = it },
                        label = { Text("Grammi") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val sectionId = editingSectionId
                    val itemIndex = editingItemIndex
                    if (sectionId != null && itemIndex != null) {
                        val sectionIndex = sections.indexOfFirst { it.id == sectionId }
                        if (sectionIndex != -1 && itemIndex in sections[sectionIndex].items.indices) {
                            val name = editingText.trim()
                            val grams = editingGrams.trim()
                            if (name.isNotEmpty()) {
                                val items = sections[sectionIndex].items
                                items[itemIndex] = items[itemIndex].copy(name = name, grams = grams)
                            }
                        }
                    }
                    editingSectionId = null
                    editingItemIndex = null
                }) {
                    Text("Salva")
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    editingSectionId = null
                    editingItemIndex = null
                }) {
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
    onEditItem: (Int, ShoppingItem) -> Unit,
    onDeleteItem: (Int) -> Unit,
    onDeleteSection: () -> Unit
) {
    var newItemText by remember(section.id) { mutableStateOf("") }
    var newItemGrams by remember(section.id) { mutableStateOf("") }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(12.dp)) {
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
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onToggleItem(index, !item.isChecked) }
                        .padding(vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = item.isChecked,
                        onCheckedChange = { isChecked -> onToggleItem(index, isChecked) }
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
                    IconButton(onClick = { onEditItem(index, item) }) {
                        Icon(Icons.Default.Edit, contentDescription = "Modifica articolo")
                    }
                    IconButton(onClick = { onDeleteItem(index) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Elimina articolo")
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileScreen() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text = "Profile Screen")
    }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MaterialTheme {
        MainScreen()
    }
}
