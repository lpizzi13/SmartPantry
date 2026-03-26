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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.DailyMacroStats
import it.sapienza.smartpantry.model.StatsViewModel

enum class ChartType {
    CALORIES, MACRONUTRIENTS
}

@Composable
fun StatsScreen(uid: String = "", statsViewModel: StatsViewModel = viewModel()) {
    val uiState by statsViewModel.uiState.collectAsState()
    var selectedKcalIndex by remember { mutableStateOf<Int?>(null) }
    var selectedChart by remember { mutableStateOf(ChartType.CALORIES) }
    var expanded by remember { mutableStateOf(false) }

    // Load stats when the screen is opened
    LaunchedEffect(uid) {
        if (uid.isNotBlank()) {
            statsViewModel.loadStats(uid)
        }
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
        Text(
            text = "WEEKLY STATISTICS",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00E676),
            modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp)
        )

        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Color(0xFF00E676))
            }
        } else {
            // Summary Card
            StatsSummaryCard(uiState.weeklyStats)

            Spacer(modifier = Modifier.height(24.dp))

            // Chart Card
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(320.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { expanded = true }
                        ) {
                            Text(
                                if (selectedChart == ChartType.CALORIES) "CALORIES TIMELINE" else "MACRONUTRIENTS",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                            Icon(
                                Icons.Default.ArrowDropDown,
                                contentDescription = "Select Chart",
                                tint = Color.White
                            )
                        }
                        DropdownMenu(
                            expanded = expanded,
                            onDismissRequest = { expanded = false },
                            modifier = Modifier.background(Color(0xFF2A3431))
                        ) {
                            DropdownMenuItem(
                                text = { Text("Calories Timeline", color = Color.White) },
                                onClick = {
                                    selectedChart = ChartType.CALORIES
                                    expanded = false
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Macronutrients", color = Color.White) },
                                onClick = {
                                    selectedChart = ChartType.MACRONUTRIENTS
                                    expanded = false
                                }
                            )
                        }
                    }
                    
                    Spacer(modifier = Modifier.height(16.dp))
                    
                    AnimatedContent(
                        targetState = selectedChart,
                        transitionSpec = {
                            fadeIn() togetherWith fadeOut()
                        },
                        label = "ChartTransition"
                    ) { chart ->
                        when (chart) {
                            ChartType.CALORIES -> {
                                KcalLineChart(
                                    stats = uiState.weeklyStats,
                                    selectedIndex = selectedKcalIndex,
                                    onSelectedIndexChange = { selectedKcalIndex = it }
                                )
                            }
                            ChartType.MACRONUTRIENTS -> {
                                MacrosBarChart(uiState.weeklyStats)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MacrosBarChart(stats: List<DailyMacroStats>) {
    if (stats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available", color = Color.Gray)
        }
        return
    }

    val avgPro = stats.map { it.proteins }.average().toFloat()
    val avgCarb = stats.map { it.carbs }.average().toFloat()
    val avgFat = stats.map { it.fats }.average().toFloat()
    
    val macros = listOf(
        Triple("Proteins", avgPro, Color(0xFFFF5252)),
        Triple("Carbs", avgCarb, Color(0xFFFFD740)),
        Triple("Fats", avgFat, Color(0xFF448AFF))
    )
    
    val maxVal = macros.maxOf { it.second }.coerceAtLeast(1f)

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.SpaceEvenly
    ) {
        macros.forEach { (label, value, color) ->
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(label, color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("${value.toInt()}g", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(20.dp)
                ) {
                    val barWidth = (value / maxVal) * size.width
                    drawRoundRect(
                        color = color.copy(alpha = 0.2f),
                        size = Size(size.width, size.height),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                    drawRoundRect(
                        color = color,
                        size = Size(barWidth, size.height),
                        cornerRadius = CornerRadius(10f, 10f)
                    )
                }
            }
        }
    }
}

@Composable
fun KcalLineChart(
    stats: List<DailyMacroStats>,
    selectedIndex: Int?,
    onSelectedIndexChange: (Int?) -> Unit
) {
    if (stats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available", color = Color.Gray)
        }
        return
    }

    val maxKcal = stats.maxOfOrNull { it.kcal }?.coerceAtLeast(1) ?: 2000
    val yMax = (maxKcal * 1.2f)

    Row(modifier = Modifier.fillMaxSize()) {
        // Y-Axis Labels
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(bottom = 24.dp, top = 8.dp, end = 8.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.End
        ) {
            val steps = 4
            for (i in steps downTo 0) {
                val label = (yMax * i / steps).toInt()
                Text(
                    text = label.toString(),
                    color = Color.Gray.copy(alpha = 0.7f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Box(modifier = Modifier.weight(1f)) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = 24.dp, top = 8.dp)
                    .pointerInput(stats) {
                        detectTapGestures(
                            onTap = { offset ->
                                val width = size.width.toFloat()
                                val spacing = if (stats.size > 1) width / (stats.size - 1) else 0f
                                
                                var closestIndex = -1
                                var minDistance = Float.MAX_VALUE
                                
                                stats.forEachIndexed { index, _ ->
                                    val x = index * spacing
                                    val distance = kotlin.math.abs(offset.x - x)
                                    if (distance < minDistance && distance < spacing / 2) {
                                        minDistance = distance
                                        closestIndex = index
                                    }
                                }
                                val newIndex = if (closestIndex != -1 && selectedIndex != closestIndex) closestIndex else null
                                onSelectedIndexChange(newIndex)
                            }
                        )
                    }
            ) {
                val width = size.width
                val height = size.height
                val spacing = if (stats.size > 1) width / (stats.size - 1) else 0f

                val points = stats.mapIndexed { index, stat ->
                    val x = index * spacing
                    val y = height - (stat.kcal.toFloat() / yMax) * height
                    Offset(x, y)
                }

                // Grid Lines
                for (i in 0..4) {
                    val y = height - (i.toFloat() / 4) * height
                    drawLine(
                        color = Color.Gray.copy(alpha = 0.1f),
                        start = Offset(0f, y),
                        end = Offset(width, y),
                        strokeWidth = 1.dp.toPx()
                    )
                }

                // Line Path
                val path = Path().apply {
                    if (points.isNotEmpty()) {
                        moveTo(points.first().x, points.first().y)
                        for (i in 1 until points.size) {
                            lineTo(points[i].x, points[i].y)
                        }
                    }
                }

                // Gradient Fill
                if (points.isNotEmpty()) {
                    val fillPath = Path().apply {
                        addPath(path)
                        lineTo(points.last().x, height)
                        lineTo(points.first().x, height)
                        close()
                    }
                    drawPath(
                        path = fillPath,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color(0xFF00E676).copy(alpha = 0.2f), Color.Transparent)
                        )
                    )
                }

                // Main Line
                drawPath(
                    path = path,
                    color = Color(0xFF00E676),
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round)
                )

                // Selection Highlight Line
                selectedIndex?.let { index ->
                    if (index < points.size) {
                        val x = points[index].x
                        drawLine(
                            color = Color(0xFF00E676).copy(alpha = 0.3f),
                            start = Offset(x, 0f),
                            end = Offset(x, height),
                            strokeWidth = 2.dp.toPx(),
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                        )
                    }
                }

                // Points
                points.forEachIndexed { index, point ->
                    val isSelected = selectedIndex == index
                    drawCircle(
                        color = if (isSelected) Color.White else Color(0xFF00E676),
                        radius = (if (isSelected) 6.dp else 4.dp).toPx(),
                        center = point
                    )
                    drawCircle(
                        color = Color(0xFF1A2421),
                        radius = (if (isSelected) 3.dp else 2.dp).toPx(),
                        center = point
                    )
                }
            }

            // X-Axis Labels
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.BottomCenter),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                stats.forEachIndexed { index, stat ->
                    Text(
                        text = stat.date,
                        color = if (selectedIndex == index) Color.White else Color.Gray,
                        fontSize = 10.sp,
                        fontWeight = if (selectedIndex == index) FontWeight.Bold else FontWeight.Normal
                    )
                }
            }

            // Tooltip Overlay
            selectedIndex?.let { index ->
                if (index < stats.size) {
                    val stat = stats[index]
                    ChartTooltip(
                        stat = stat,
                        modifier = Modifier
                            .align(if (index > stats.size / 2) Alignment.TopStart else Alignment.TopEnd)
                            .padding(8.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ChartTooltip(stat: DailyMacroStats, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFF2A3431),
        tonalElevation = 8.dp,
        shadowElevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                text = stat.date.uppercase(),
                color = Color.Gray,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${stat.kcal} kcal",
                color = Color(0xFF00E676),
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold
            )
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 4.dp),
                thickness = DividerDefaults.Thickness,
                color = Color.White.copy(alpha = 0.1f)
            )
            TooltipMacroRow("Pro", "${stat.proteins}g")
            TooltipMacroRow("Carb", "${stat.carbs}g")
            TooltipMacroRow("Fat", "${stat.fats}g")
        }
    }
}

@Composable
fun TooltipMacroRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(0.3f),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, color = Color.Gray, fontSize = 11.sp)
        Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StatsSummaryCard(stats: List<DailyMacroStats>) {
    val avgKcal = if (stats.isNotEmpty()) stats.map { it.kcal }.average().toInt() else 0
    val avgPro = if (stats.isNotEmpty()) stats.map { it.proteins }.average().toInt() else 0
    val avgCarb = if (stats.isNotEmpty()) stats.map { it.carbs }.average().toInt() else 0
    val avgFat = if (stats.isNotEmpty()) stats.map { it.fats }.average().toInt() else 0

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("WEEKLY AVERAGE", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "$avgKcal",
                    color = Color(0xFF00E676),
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 32.sp
                )
                Text(
                    text = " kcal / day",
                    color = Color.Gray,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 6.dp, start = 4.dp)
                )
            }
            
            Spacer(modifier = Modifier.height(16.dp))
            
            // Macro averages
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroStatSmall("PRO", "${avgPro}g", "🥩")
                MacroStatSmall("CARB", "${avgCarb}g", "🍞")
                MacroStatSmall("FAT", "${avgFat}g", "🥑")
            }
        }
    }
}

@Composable
fun MacroStatSmall(label: String, value: String, emoji: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Surface(
            shape = RoundedCornerShape(8.dp),
            color = Color(0xFF2A3431),
            modifier = Modifier.size(32.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(emoji, fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.width(8.dp))
        Column {
            Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }
    }
}
