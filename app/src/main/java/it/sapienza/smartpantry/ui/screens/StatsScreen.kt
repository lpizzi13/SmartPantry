package it.sapienza.smartpantry.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import it.sapienza.smartpantry.model.DailyMacroStats
import it.sapienza.smartpantry.model.StatsViewModel

@Composable
fun StatsScreen(uid: String = "", statsViewModel: StatsViewModel = viewModel()) {
    val uiState by statsViewModel.uiState.collectAsState()

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

            // Calories Chart
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(280.dp),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "CALORIES TIMELINE",
                        color = Color.Gray,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    KcalLineChart(uiState.weeklyStats)
                }
            }
        }
    }
}

@Composable
fun KcalLineChart(stats: List<DailyMacroStats>) {
    if (stats.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No data available", color = Color.Gray)
        }
        return
    }

    // Determine Y axis range
    val maxKcal = stats.maxOfOrNull { it.kcal }?.coerceAtLeast(1) ?: 2000
    val yMax = (maxKcal * 1.2f) // Give some headroom

    Box(modifier = Modifier.fillMaxSize()) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 24.dp, top = 8.dp)
        ) {
            val width = size.width
            val height = size.height
            val spacing = width / (stats.size - 1)

            val points = stats.mapIndexed { index, stat ->
                val x = index * spacing
                val y = height - (stat.kcal.toFloat() / yMax) * height
                Offset(x, y)
            }

            // Draw Background Grid (horizontal lines)
            val gridLines = 4
            for (i in 0..gridLines) {
                val y = height - (i.toFloat() / gridLines) * height
                drawLine(
                    color = Color.Gray.copy(alpha = 0.1f),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1.dp.toPx()
                )
            }

            // Draw the line path
            val path = Path().apply {
                if (points.isNotEmpty()) {
                    moveTo(points.first().x, points.first().y)
                    for (i in 1 until points.size) {
                        lineTo(points[i].x, points[i].y)
                    }
                }
            }

            // Fill area under the line with gradient
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
                        colors = listOf(
                            Color(0xFF00E676).copy(alpha = 0.2f),
                            Color.Transparent
                        )
                    )
                )
            }

            // Draw the main line
            drawPath(
                path = path,
                color = Color(0xFF00E676),
                style = Stroke(
                    width = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round
                )
            )

            // Draw circles at data points
            points.forEach { point ->
                drawCircle(
                    color = Color(0xFF00E676),
                    radius = 4.dp.toPx(),
                    center = point
                )
                drawCircle(
                    color = Color(0xFF1A2421),
                    radius = 2.dp.toPx(),
                    center = point
                )
            }
        }

        // X-Axis Labels (Days)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            stats.forEach { stat ->
                Text(
                    text = stat.date,
                    color = Color.Gray,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun StatsSummaryCard(stats: List<DailyMacroStats>) {
    //Calcolo delle calorie a schermo
    val avgKcal = if (stats.isNotEmpty()) stats.map { it.kcal }.average().toInt() else 0
    
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text("WEEKLY AVERAGE", color = Color.Gray, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
            
            // Macro averages (Mock representation)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroStatSmall("PRO", "125g")
                MacroStatSmall("CARB", "240g")
                MacroStatSmall("FAT", "70g")
            }
        }
    }
}

@Composable
fun MacroStatSmall(label: String, value: String) {
    Column {
        Text(label, fontSize = 10.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Text(value, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
    }
}
