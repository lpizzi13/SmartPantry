package it.sapienza.smartpantry.ui.screens

import android.content.Intent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.HomeEntry
import it.sapienza.smartpantry.model.HomeViewModel
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.ui.SearchFoodActivity
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

private enum class HomeMealType(val apiValue: String, val title: String, val icon: ImageVector) {
    BREAKFAST("breakfast", "Breakfast", Icons.Default.WbTwilight),
    LUNCH("lunch", "Lunch", Icons.Default.WbSunny),
    DINNER("dinner", "Dinner", Icons.Default.NightsStay),
    SNACKS("snacks", "Snacks", Icons.Default.Icecream)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(user: User, homeViewModel: HomeViewModel = viewModel()) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scrollState = rememberScrollState()
    var selectedDate by remember { mutableStateOf(startOfDay(Calendar.getInstance())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateLabel = remember(selectedDate.timeInMillis) { formatHomeDateLabel(selectedDate) }
    val dateKey = remember(selectedDate.timeInMillis) { formatDateKey(selectedDate) }
    val homeState by homeViewModel.uiState.collectAsState()

    LaunchedEffect(user.uid, dateKey) {
        if (user.uid.isNotBlank()) homeViewModel.loadDay(user.uid, dateKey)
    }
    DisposableEffect(lifecycleOwner, user.uid, dateKey) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && user.uid.isNotBlank()) {
                homeViewModel.loadDay(user.uid, dateKey)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val consumedKcal = homeState.totals.kcal
    val consumedProteins = homeState.totals.protein
    val consumedCarbs = homeState.totals.carbs
    val consumedFats = homeState.totals.fat

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A120E))
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState)
    ) {
        HomeCalendarHeader(
            dateLabel = dateLabel,
            onPreviousDay = { selectedDate = shiftSelectedDate(selectedDate, -1) },
            onNextDay = { selectedDate = shiftSelectedDate(selectedDate, 1) },
            onOpenCalendar = { showDatePicker = true }
        )

        Spacer(modifier = Modifier.height(20.dp))

        SectionHeader("Daily Nutrition")
        if (homeState.isLoading) {
            LinearProgressIndicator(
                modifier = Modifier.fillMaxWidth(),
                color = Color(0xFF00E676),
                trackColor = Color(0xFF1A2421)
            )
            Spacer(modifier = Modifier.height(10.dp))
        }
        homeState.errorMessage?.let {
            Text(
                text = it,
                color = Color(0xFFFF8A80),
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
        NutritionCard(
            user = user,
            currentKcal = consumedKcal,
            currentProteins = consumedProteins,
            currentCarbs = consumedCarbs,
            currentFats = consumedFats
        )

        Spacer(modifier = Modifier.height(24.dp))

        SectionHeader("Meals")
        HomeMealType.entries.forEach { mealType ->
            MealItem(
                title = mealType.title,
                entries = homeState.meals[mealType.apiValue].orEmpty(),
                icon = mealType.icon,
                onAddClick = {
                    if (user.uid.isBlank()) return@MealItem
                    val intent = Intent(context, SearchFoodActivity::class.java).apply {
                        putExtra(SearchFoodActivity.EXTRA_UID, user.uid)
                        putExtra(SearchFoodActivity.EXTRA_SOURCE, SearchFoodActivity.SOURCE_HOME)
                        putExtra(SearchFoodActivity.EXTRA_HOME_DATE_KEY, dateKey)
                        putExtra(SearchFoodActivity.EXTRA_HOME_MEAL_TYPE, mealType.apiValue)
                    }
                    context.startActivity(intent)
                },
                onEditEntry = { entry, updatedName, updatedGrams ->
                    if (user.uid.isBlank()) return@MealItem
                    homeViewModel.updateEntry(
                        uid = user.uid,
                        dateKey = dateKey,
                        mealType = mealType.apiValue,
                        originalEntry = entry,
                        updatedProductName = updatedName,
                        updatedGrams = updatedGrams
                    )
                },
                onDeleteEntry = { entry ->
                    if (user.uid.isBlank()) return@MealItem
                    homeViewModel.deleteEntry(
                        uid = user.uid,
                        dateKey = dateKey,
                        entry = entry
                    )
                }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = localDateToUtcMillis(selectedDate)
        )

        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { selectedMillis ->
                            selectedDate = utcMillisToLocalDate(selectedMillis)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("Select")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(
                state = datePickerState,
                showModeToggle = false
            )
        }
    }
}

@Composable
private fun HomeCalendarHeader(
    dateLabel: String,
    onPreviousDay: () -> Unit,
    onNextDay: () -> Unit,
    onOpenCalendar: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF1A2421)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onPreviousDay) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Previous day",
                    tint = Color(0xFF00E676)
                )
            }
            Text(
                text = dateLabel,
                color = Color.White,
                fontWeight = FontWeight.Bold,
                fontSize = 17.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .weight(1f)
                    .clickable(onClick = onNextDay)
                    .padding(vertical = 12.dp)
            )
            IconButton(onClick = onNextDay) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Next day",
                    tint = Color(0xFF00E676)
                )
            }
            IconButton(onClick = onOpenCalendar) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = "Open calendar",
                    tint = Color(0xFF00E676)
                )
            }
        }
    }
}

private fun startOfDay(calendar: Calendar): Calendar =
    (calendar.clone() as Calendar).apply {
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }

private fun shiftSelectedDate(date: Calendar, days: Int): Calendar =
    startOfDay((date.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, days) })

private fun isSameDay(first: Calendar, second: Calendar): Boolean =
    first.get(Calendar.YEAR) == second.get(Calendar.YEAR) &&
        first.get(Calendar.DAY_OF_YEAR) == second.get(Calendar.DAY_OF_YEAR)

private fun formatDateKey(selectedDate: Calendar): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(startOfDay(selectedDate).time)

private fun formatHomeDateLabel(selectedDate: Calendar): String {
    val locale = Locale.ENGLISH
    val normalizedSelectedDate = startOfDay(selectedDate)
    val today = startOfDay(Calendar.getInstance())
    val yesterday = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    val tomorrow = (today.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 1) }

    val dayPrefix = when {
        isSameDay(normalizedSelectedDate, yesterday) -> "YESTERDAY"
        isSameDay(normalizedSelectedDate, today) -> "TODAY"
        isSameDay(normalizedSelectedDate, tomorrow) -> "TOMORROW"
        else -> SimpleDateFormat("EEEE", locale).format(normalizedSelectedDate.time).uppercase(locale)
    }
    val dayMonth = SimpleDateFormat("d MMM", locale).format(normalizedSelectedDate.time).lowercase(locale)
    return "$dayPrefix, $dayMonth"
}

private fun localDateToUtcMillis(localDate: Calendar): Long =
    Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(
            localDate.get(Calendar.YEAR),
            localDate.get(Calendar.MONTH),
            localDate.get(Calendar.DAY_OF_MONTH)
        )
    }.timeInMillis

private fun utcMillisToLocalDate(utcMillis: Long): Calendar {
    val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        timeInMillis = utcMillis
    }
    return Calendar.getInstance().apply {
        clear()
        set(
            utcCalendar.get(Calendar.YEAR),
            utcCalendar.get(Calendar.MONTH),
            utcCalendar.get(Calendar.DAY_OF_MONTH)
        )
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = Color(0xFF00E676),
        modifier = Modifier.padding(bottom = 12.dp, start = 4.dp)
    )
}

@Composable
fun NutritionCard(
    user: User,
    currentKcal: Double,
    currentProteins: Double,
    currentCarbs: Double,
    currentFats: Double
) {
    val targetKcal = user.goals.dailyKcal
    val progress = if (targetKcal > 0) currentKcal.toFloat() / targetKcal else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("CALORIES", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatHomeNumber(currentKcal),
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 28.sp
                    )
                    Text(
                        text = " / $targetKcal kcal",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 4.dp, start = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            LinearProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp),
                color = Color(0xFF00E676),
                trackColor = Color(0xFF0A120E),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val targetProteins = user.goals.macrosTarget["protein"]
                    ?: user.goals.macrosTarget["prot"]
                    ?: 0
                val targetCarbs = user.goals.macrosTarget["carbs"] ?: 0
                val targetFats = user.goals.macrosTarget["fat"] ?: 0

                MacroProgress(
                    "PROTEIN",
                    "${formatHomeNumber(currentProteins)}g",
                    "${targetProteins}g",
                    if (targetProteins > 0) currentProteins.toFloat() / targetProteins else 0f
                )
                MacroProgress(
                    "CARBS",
                    "${formatHomeNumber(currentCarbs)}g",
                    "${targetCarbs}g",
                    if (targetCarbs > 0) currentCarbs.toFloat() / targetCarbs else 0f
                )
                MacroProgress(
                    "FAT",
                    "${formatHomeNumber(currentFats)}g",
                    "${targetFats}g",
                    if (targetFats > 0) currentFats.toFloat() / targetFats else 0f
                )
            }
        }
    }
}

@Composable
fun MacroProgress(label: String, current: String, target: String, progress: Float) {
    Column(modifier = Modifier.width(95.dp)) {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(current, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(
                " / $target",
                color = Color.Gray,
                fontSize = 10.sp,
                modifier = Modifier.padding(bottom = 1.dp, start = 2.dp)
            )
        }
        Spacer(modifier = Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { progress.coerceAtMost(1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp),
            color = Color(0xFF00E676),
            trackColor = Color(0xFF0A120E),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun MealItem(
    title: String,
    entries: List<HomeEntry>,
    icon: ImageVector,
    onAddClick: () -> Unit,
    onEditEntry: (HomeEntry, String, Double) -> Unit,
    onDeleteEntry: (HomeEntry) -> Unit
) {
    var isExpanded by remember(title) { mutableStateOf(false) }
    var editingEntry by remember(title) { mutableStateOf<HomeEntry?>(null) }
    var editNameDraft by remember(title) { mutableStateOf("") }
    var editGramsDraft by remember(title) { mutableStateOf("") }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                modifier = Modifier.size(44.dp),
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFF0A120E)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.padding(10.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { isExpanded = !isExpanded },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        title,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = if (isExpanded) "Collapse $title" else "Expand $title",
                        tint = Color.White,
                        modifier = Modifier.rotate(if (isExpanded) 180f else 0f)
                    )
                }

                if (entries.isEmpty()) {
                    Text("Not consumed yet", color = Color.Gray, fontSize = 12.sp)
                } else {
                    Text(
                        text = "${entries.size} food ${if (entries.size == 1) "item" else "items"}",
                        color = Color.Gray,
                        fontSize = 12.sp
                    )
                }

                if (isExpanded && entries.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    entries.forEach { entry ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            val displayText =
                                "${displayHomeProductName(entry)} (${formatHomeNumber(entry.grams)}g - ${formatHomeNumber(entry.nutrients.kcal)} kcal)"
                            Text(
                                text = displayText,
                                color = Color.White,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    editingEntry = entry
                                    editNameDraft = displayHomeProductName(entry)
                                    editGramsDraft = formatHomeNumber(entry.grams)
                                },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Edit,
                                    contentDescription = "Edit item",
                                    tint = Color.Gray,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDeleteEntry(entry) },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete item",
                                    tint = Color.Red,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .background(Color(0xFF00E676), CircleShape)
                    .size(32.dp)
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = Color.Black,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    if (editingEntry != null) {
        AlertDialog(
            onDismissRequest = { editingEntry = null },
            title = { Text("Edit item") },
            text = {
                Column {
                    OutlinedTextField(
                        value = editNameDraft,
                        onValueChange = { editNameDraft = it },
                        singleLine = true,
                        label = { Text("Food name") }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editGramsDraft,
                        onValueChange = { editGramsDraft = it },
                        singleLine = true,
                        label = { Text("Grams") }
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val currentEntry = editingEntry ?: return@TextButton
                        val grams = editGramsDraft.replace(',', '.').toDoubleOrNull()
                        if (grams != null && grams > 0.0) {
                            onEditEntry(
                                currentEntry,
                                editNameDraft.trim().ifBlank { displayHomeProductName(currentEntry) },
                                grams
                            )
                            editingEntry = null
                        }
                    }
                ) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { editingEntry = null }) { Text("Cancel") }
            }
        )
    }
}

private fun displayHomeProductName(entry: HomeEntry): String {
    val value = entry.productName.trim()
    return if (value.isBlank()) "Unnamed product" else value
}

private fun formatHomeNumber(value: Double): String {
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

