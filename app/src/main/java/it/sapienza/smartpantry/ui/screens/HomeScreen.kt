package it.sapienza.smartpantry.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import it.sapienza.smartpantry.model.User

@Composable
fun HomeScreen(user: User) {
    val scrollState = rememberScrollState()

    // I valori iniziali partono tutti da 0
    val consumedKcal = 0
    val consumedProteins = 0
    val consumedCarbs = 0
    val consumedFats = 0

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(scrollState)
    ) {
        SectionHeader("Daily Nutrition")
        NutritionCard(
            user = user,
            currentKcal = consumedKcal,
            currentProteins = consumedProteins,
            currentCarbs = consumedCarbs,
            currentFats = consumedFats
        )

        Spacer(modifier = Modifier.height(16.dp))

        SectionHeader("Today's Meals")
        
        // Tutti i pasti partono con "Not consumed yet" e il tasto + non fa nulla
        MealItem("Breakfast", "Not consumed yet", Icons.Default.WbTwilight) { }
        MealItem("Lunch", "Not consumed yet", Icons.Default.WbSunny) { }
        MealItem("Dinner", "Not consumed yet", Icons.Default.NightsStay) { }
        MealItem("Snacks", "Not consumed yet", Icons.Default.Icecream) { }
        
        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(bottom = 8.dp)
    )
}

@Composable
fun NutritionCard(
    user: User,
    currentKcal: Int,
    currentProteins: Int,
    currentCarbs: Int,
    currentFats: Int
) {
    val targetKcal = user.goals.dailyKcal
    val progress = if (targetKcal > 0) currentKcal.toFloat() / targetKcal else 0f

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121417))
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Calories (Kcal)", color = Color.White, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "$currentKcal",
                        color = Color(0xFF00E676),
                        fontWeight = FontWeight.Bold,
                        fontSize = 24.sp
                    )
                    Text(
                        text = " / $targetKcal",
                        color = Color.Gray,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(bottom = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { progress.coerceAtMost(1f) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp),
                color = Color(0xFF00E676),
                trackColor = Color(0xFF2C2E33),
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
            )

            Spacer(modifier = Modifier.height(20.dp))

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                val targetProteins = user.goals.macrosTarget["protein"] ?: 0
                val targetCarbs = user.goals.macrosTarget["carbs"] ?: 0
                val targetFats = user.goals.macrosTarget["fat"] ?: 0
                
                MacroProgress(
                    "PROTEINS", 
                    "${currentProteins}g", 
                    "${targetProteins}g", 
                    if (targetProteins > 0) currentProteins.toFloat() / targetProteins else 0f
                )
                MacroProgress(
                    "CARBS", 
                    "${currentCarbs}g", 
                    "${targetCarbs}g", 
                    if (targetCarbs > 0) currentCarbs.toFloat() / targetCarbs else 0f
                )
                MacroProgress(
                    "FATS", 
                    "${currentFats}g", 
                    "${targetFats}g", 
                    if (targetFats > 0) currentFats.toFloat() / targetFats else 0f
                )
            }
        }
    }
}

@Composable
fun MacroProgress(label: String, current: String, target: String, progress: Float) {
    Column(modifier = Modifier.width(90.dp)) {
        Text(label, fontSize = 9.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(current, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            Text(" / $target", color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(bottom = 1.dp))
        }
        Spacer(modifier = Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.coerceAtMost(1f) },
            modifier = Modifier.fillMaxWidth().height(4.dp),
            color = Color(0xFF00E676),
            trackColor = Color(0xFF2C2E33),
            strokeCap = androidx.compose.ui.graphics.StrokeCap.Round
        )
    }
}

@Composable
fun MealItem(title: String, subtitle: String, icon: ImageVector, onAddClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121417))
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                modifier = Modifier.size(40.dp),
                shape = RoundedCornerShape(10.dp),
                color = Color(0xFF1E2124)
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = Color(0xFF00E676),
                    modifier = Modifier.padding(10.dp)
                )
            }
            
            Spacer(modifier = Modifier.width(12.dp))
            
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(subtitle, color = Color.Gray, fontSize = 12.sp)
            }
            
            IconButton(
                onClick = onAddClick,
                modifier = Modifier
                    .background(Color(0xFF00E676), CircleShape)
                    .size(32.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, tint = Color.Black, modifier = Modifier.size(20.dp))
            }
        }
    }
}
