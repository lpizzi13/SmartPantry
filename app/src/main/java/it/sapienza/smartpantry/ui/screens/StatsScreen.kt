package it.sapienza.smartpantry.ui.screens

import androidx.compose.animation.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.StatsViewModel
import it.sapienza.smartpantry.model.User
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

enum class ChartType {
    CALORIES, MACRONUTRIENTS
}

enum class TimeRange {
    WEEKLY, MONTHLY, YEARLY
}

// Common UI model to unify Daily, Weekly and Monthly stats
data class StatPoint(
    val label: String,
    val kcal: Float,
    val proteins: Float,
    val carbs: Float,
    val fats: Float
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatsScreen(user: User, statsViewModel: StatsViewModel = viewModel()) {
    val uiState by statsViewModel.uiState.collectAsState()
    var selectedPeriod by remember { mutableStateOf(TimeRange.WEEKLY) }
    var selectedKcalIndex by remember { mutableStateOf<Int?>(null) }
    var selectedChart by remember { mutableStateOf(ChartType.CALORIES) }
    var expandedChartMenu by remember { mutableStateOf(false) }

    var selectedDate by remember { mutableStateOf(startOfStatsDay(Calendar.getInstance())) }
    var showDatePicker by remember { mutableStateOf(false) }
    val dateKey = remember(selectedDate.timeInMillis) { formatStatsDateKey(selectedDate) }

    LaunchedEffect(user.uid, dateKey) {
        if (user.uid.isNotBlank()) {
            statsViewModel.loadStats(user.uid, dateKey)
        }
    }

    val currentStats = remember(uiState, selectedPeriod) {
        when (selectedPeriod) {
            TimeRange.WEEKLY -> uiState.weeklyStats.map { 
                StatPoint(formatChartLabel(it.date, TimeRange.WEEKLY), it.kcal, it.proteins, it.carbs, it.fats) 
            }
            TimeRange.MONTHLY -> uiState.monthlyStats.mapIndexed { index, it -> 
                StatPoint("W${index + 1}", it.kcal, it.proteins, it.carbs, it.fats) 
            }
            TimeRange.YEARLY -> uiState.yearlyStats.map { 
                StatPoint(formatChartLabel(it.week, TimeRange.YEARLY), it.kcal, it.proteins, it.carbs, it.fats) 
            }
        }
    }

    LaunchedEffect(selectedPeriod) {
        selectedKcalIndex = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A120E))
            .pointerInput(Unit) {
                detectTapGestures(onTap = { selectedKcalIndex = null })
            }
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${selectedPeriod.name} STATISTICS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E676)
            )
            
            IconButton(onClick = { showDatePicker = true }, modifier = Modifier.size(24.dp)) {
                Icon(Icons.Default.CalendarMonth, contentDescription = null, tint = Color(0xFF00E676))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Time Range Selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .background(Color(0xFF1A2421), RoundedCornerShape(12.dp))
                .padding(4.dp)
        ) {
            TimeRange.entries.forEach { range ->
                val isSelected = selectedPeriod == range
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isSelected) Color(0xFF00E676) else Color.Transparent,
                            RoundedCornerShape(8.dp)
                        )
                        .clickable { selectedPeriod = range }
                        .padding(vertical = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = range.name,
                        color = if (isSelected) Color.Black else Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
            }
        }

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676))
            }
        } else if (uiState.errorMessage != null) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Text(uiState.errorMessage!!, color = Color.Red, textAlign = TextAlign.Center)
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = { statsViewModel.loadStats(user.uid, dateKey) }) {
                    Icon(Icons.Default.Refresh, contentDescription = null)
                    Text("Retry")
                }
            }
        } else {
            StatsSummaryCard(currentStats, user, selectedPeriod)

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                modifier = Modifier.fillMaxWidth().weight(1f),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { expandedChartMenu = true }
                        ) {
                            Text(
                                if (selectedChart == ChartType.CALORIES) "CALORIES TIMELINE" else "MACRONUTRIENTS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }
                        DropdownMenu(
                            expanded = expandedChartMenu,
                            onDismissRequest = { expandedChartMenu = false },
                            modifier = Modifier.background(Color(0xFF2A3431))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Calories Timeline", color = Color.White) },
                                onClick = { selectedChart = ChartType.CALORIES; expandedChartMenu = false }
                            )
                            DropdownMenuItem(
                                text = { Text("Macronutrients", color = Color.White) },
                                onClick = { selectedChart = ChartType.MACRONUTRIENTS; expandedChartMenu = false }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(24.dp))
                    
                    if (currentStats.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No data available", color = Color.Gray)
                        }
                    } else {
                        when (selectedChart) {
                            ChartType.CALORIES -> KcalLineChart(currentStats, user.goals.dailyKcal, selectedKcalIndex) { selectedKcalIndex = it }
                            ChartType.MACRONUTRIENTS -> MacrosBarChart(currentStats, user)
                        }
                    }
                }
            }
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(initialSelectedDateMillis = statsLocalDateToUtcMillis(selectedDate))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { selectedDate = statsUtcMillisToLocalDate(it) }
                    showDatePicker = false
                }) { Text("Select") }
            }
        ) { DatePicker(state = datePickerState) }
    }
}

@Composable
fun StatsSummaryCard(stats: List<StatPoint>, user: User, period: TimeRange) {
    val avgKcal = if (stats.isNotEmpty()) stats.map { it.kcal }.average().toInt() else 0
    val avgPro = if (stats.isNotEmpty()) stats.map { it.proteins }.average().toInt() else 0
    val avgCarb = if (stats.isNotEmpty()) stats.map { it.carbs }.average().toInt() else 0
    val avgFat = if (stats.isNotEmpty()) stats.map { it.fats }.average().toInt() else 0

    val targetKcal = user.goals.dailyKcal
    val targetPro = user.goals.macrosTarget["protein"] ?: 0
    val targetCarb = user.goals.macrosTarget["carbs"] ?: 0
    val targetFat = user.goals.macrosTarget["fat"] ?: 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                text = "${period.name} AVERAGE",
                color = Color.Gray,
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp
            )

            Row(verticalAlignment = Alignment.Bottom, modifier = Modifier.padding(vertical = 8.dp)) {
                Text(text = avgKcal.toString(), color = Color(0xFF00E676), fontWeight = FontWeight.ExtraBold, fontSize = 36.sp)
                Text(text = " / $targetKcal kcal avg", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp, start = 8.dp))
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroSummaryItem("🥩", "PRO", avgPro, targetPro)
                MacroSummaryItem("🍞", "CARB", avgCarb, targetCarb)
                MacroSummaryItem("🥑", "FAT", avgFat, targetFat)
            }
        }
    }
}

@Composable
fun MacroSummaryItem(icon: String, label: String, value: Int, target: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = icon, fontSize = 20.sp)
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(text = label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.Bottom) {
                Text(text = "${value}g", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(text = " / ${target}g", color = Color.DarkGray, fontSize = 10.sp, modifier = Modifier.padding(start = 4.dp))
            }
        }
    }
}

@Composable
fun KcalLineChart(stats: List<StatPoint>, targetKcal: Int, selectedIndex: Int?, onSelectedIndexChange: (Int?) -> Unit) {
    val maxKcal = (maxOf(stats.maxOf { it.kcal }, targetKcal.toFloat()) * 1.3f).coerceAtLeast(1f)
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column {
            Canvas(modifier = Modifier.weight(1f).fillMaxWidth().pointerInput(stats) {
                detectTapGestures { offset ->
                    val stepX = size.width / (stats.size - 1).coerceAtLeast(1)
                    onSelectedIndexChange((offset.x / stepX + 0.5f).toInt().coerceIn(0, stats.size - 1))
                }
            }) {
                val width = size.width
                val height = size.height
                val stepX = width / (stats.size - 1).coerceAtLeast(1)

                // Grid lines
                val gridSteps = 4
                for (i in 0..gridSteps) {
                    val y = height - (i.toFloat() / gridSteps) * height
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.1f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Target Line
                val targetY = height - (targetKcal / maxKcal) * height
                drawLine(
                    color = Color.White.copy(alpha = 0.2f),
                    start = Offset(0f, targetY),
                    end = Offset(width, targetY),
                    strokeWidth = 1.dp.toPx(),
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                )

                val points = stats.mapIndexed { i, s -> Offset(i * stepX, height - (s.kcal / maxKcal) * height) }
                
                // Area
                val areaPath = Path().apply {
                    moveTo(0f, height)
                    points.forEach { lineTo(it.x, it.y) }
                    lineTo(width, height); close()
                }
                drawPath(areaPath, Brush.verticalGradient(listOf(Color(0xFF00E676).copy(alpha = 0.2f), Color.Transparent)))

                // Line
                val linePath = Path().apply { points.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) } }
                drawPath(
                    path = linePath,
                    color = Color(0xFF00E676),
                    style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Points
                points.forEachIndexed { i, p ->
                    drawCircle(Color(0xFF00E676), 4.dp.toPx(), p)
                    if (i == selectedIndex) drawCircle(Color.White, 6.dp.toPx(), p, style = Stroke(2.dp.toPx()))
                }
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // X-Axis labels
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                stats.forEach { 
                    Text(it.label, color = Color.Gray, fontSize = 10.sp, textAlign = TextAlign.Center) 
                }
            }
        }

        // Tooltip with bounds check
        if (selectedIndex != null && selectedIndex < stats.size) {
            ChartTooltip(
                stat = stats[selectedIndex],
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 8.dp)
            )
        }
    }
}

@Composable
fun ChartTooltip(stat: StatPoint, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        color = Color(0xFF2A3431),
        shadowElevation = 8.dp
    ) {
        Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = stat.label, color = Color.Gray, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(text = "${stat.kcal.toInt()} kcal", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 4.dp)) {
                TooltipMacroInfo("P", stat.proteins, Color(0xFFFF5252))
                TooltipMacroInfo("C", stat.carbs, Color(0xFFFFD740))
                TooltipMacroInfo("F", stat.fats, Color(0xFF448AFF))
            }
        }
    }
}

@Composable
fun TooltipMacroInfo(label: String, value: Float, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.size(6.dp).background(color, RoundedCornerShape(2.dp)))
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = "$label: ${value.toInt()}g", color = Color.Gray, fontSize = 10.sp)
    }
}

@Composable
fun MacrosBarChart(stats: List<StatPoint>, user: User) {
    val avgPro = stats.map { it.proteins }.average().toFloat()
    val avgCarb = stats.map { it.carbs }.average().toFloat()
    val avgFat = stats.map { it.fats }.average().toFloat()
    
    val targetPro = (user.goals.macrosTarget["protein"] ?: 1).toFloat()
    val targetCarb = (user.goals.macrosTarget["carbs"] ?: 1).toFloat()
    val targetFat = (user.goals.macrosTarget["fat"] ?: 1).toFloat()

    val macros = listOf(
        Triple("Prot", avgPro, Color(0xFFFF5252)),
        Triple("Carb", avgCarb, Color(0xFFFFD740)),
        Triple("Fat", avgFat, Color(0xFF448AFF))
    )
    
    val targets = listOf(targetPro, targetCarb, targetFat)
    val maxVal = (maxOf(macros.maxOf { it.second }, targets.maxOf { it }, 100f) / 50).toInt() * 50f + 50f

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
        macros.forEachIndexed { index, (label, value, color) ->
            val target = targets[index]
            Column {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(text = label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(text = "${value.toInt()}g / ${target.toInt()}g", color = Color.White, fontSize = 10.sp)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Box(modifier = Modifier.fillMaxWidth().height(16.dp).background(color.copy(alpha = 0.1f), RoundedCornerShape(8.dp))) {
                    Box(modifier = Modifier.fillMaxWidth( (value / maxVal).coerceIn(0f, 1f) ).fillMaxHeight().background(color, RoundedCornerShape(8.dp)))
                    
                    // Target Line Indicator
                    Box(modifier = Modifier.fillMaxWidth( (target / maxVal).coerceIn(0f, 1f) ).fillMaxHeight()) {
                        Box(modifier = Modifier.align(Alignment.CenterEnd).width(2.dp).fillMaxHeight().background(Color.White.copy(alpha = 0.7f)))
                    }
                }
            }
        }
        
        // X-Axis Scale
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            for (i in 0..4) {
                Text(text = "${(maxVal * i / 4).toInt()}g", color = Color.DarkGray, fontSize = 10.sp)
            }
        }
    }
}

private fun formatChartLabel(label: String, period: TimeRange): String {
    return when (period) {
        TimeRange.WEEKLY -> {
            try {
                val date = SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(label)
                SimpleDateFormat("EEE", Locale.ENGLISH).format(date!!).uppercase()
            } catch (e: Exception) {
                label.take(3).uppercase()
            }
        }
        TimeRange.YEARLY -> {
            try {
                val date = SimpleDateFormat("yyyy-MM", Locale.US).parse(label)
                SimpleDateFormat("MMM", Locale.ENGLISH).format(date!!).uppercase()
            } catch (e: Exception) {
                label.take(3).uppercase()
            }
        }
        else -> label
    }
}

private fun startOfStatsDay(calendar: Calendar): Calendar = (calendar.clone() as Calendar).apply { set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }
private fun formatStatsDateKey(selectedDate: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(selectedDate.time)
private fun statsLocalDateToUtcMillis(localDate: Calendar): Long = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { clear(); set(localDate.get(Calendar.YEAR), localDate.get(Calendar.MONTH), localDate.get(Calendar.DAY_OF_MONTH)) }.timeInMillis
private fun statsUtcMillisToLocalDate(utcMillis: Long): Calendar { val utcCalendar = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }; return Calendar.getInstance().apply { clear(); set(utcCalendar.get(Calendar.YEAR), utcCalendar.get(Calendar.MONTH), utcCalendar.get(Calendar.DAY_OF_MONTH)) } }
