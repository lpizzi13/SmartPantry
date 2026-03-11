package it.sapienza.smartpantry.ui.screens

import android.net.Uri
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.CachePolicy
import coil.request.ImageRequest
import com.google.firebase.storage.FirebaseStorage
import it.sapienza.smartpantry.model.Biometrics
import it.sapienza.smartpantry.model.Goals
import it.sapienza.smartpantry.model.UpdateUserResponse
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(user: User, onUserUpdate: (User) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val neonGreen = Color(0xFF00E676)
    val darkBg = Color(0xFF0A120E)
    val surfaceColor = Color(0xFF1A2421)

    // Form states - Biometrics
    var name by remember { mutableStateOf(user.name) }
    var age by remember { mutableStateOf(user.biometrics.age.toString()) }
    var height by remember { mutableStateOf(user.biometrics.height.toString()) }
    var weight by remember { mutableStateOf(user.biometrics.weight.toString()) }
    var gender by remember { mutableStateOf(user.biometrics.gender) }
    var activityLevel by remember { mutableStateOf(user.biometrics.activityLevel) }

    // Form states - Goals
    var fitnessGoal by remember { mutableStateOf(user.goals.fitnessGoal) }
    var dailyKcal by remember { mutableStateOf(user.goals.dailyKcal.toString()) }
    var carbs by remember { mutableStateOf(user.goals.macrosTarget["carbs"]?.toString() ?: "0") }
    var protein by remember { mutableStateOf(user.goals.macrosTarget["protein"]?.toString() ?: "0") }
    var fat by remember { mutableStateOf(user.goals.macrosTarget["fat"]?.toString() ?: "0") }

    var autoCalculate by remember { mutableStateOf(!user.manualOverride) }

    LaunchedEffect(user) {
        if (!isEditing) {
            name = user.name
            age = user.biometrics.age.toString()
            height = user.biometrics.height.toString()
            weight = user.biometrics.weight.toString()
            gender = user.biometrics.gender
            activityLevel = user.biometrics.activityLevel
            fitnessGoal = user.goals.fitnessGoal
            dailyKcal = user.goals.dailyKcal.toString()
            carbs = user.goals.macrosTarget["carbs"]?.toString() ?: "0"
            protein = user.goals.macrosTarget["protein"]?.toString() ?: "0"
            fat = user.goals.macrosTarget["fat"]?.toString() ?: "0"
            autoCalculate = !user.manualOverride
        }
    }

    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? -> selectedImageUri = uri }

    val activityLevels = listOf(
        "Sedentary (Office, little exercise)" to "1.2",
        "Light (Exercise 1-3 times/week)" to "1.375",
        "Moderate (Exercise 3-5 times/week)" to "1.55",
        "Active (Physical work or daily sport)" to "1.725",
        "Very Active (Athlete or very heavy work)" to "1.9"
    )

    Box(modifier = Modifier
        .fillMaxSize()
        .background(darkBg)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // --- Avatar with Glow Effect ---
            Box(
                contentAlignment = Alignment.BottomEnd,
                modifier = Modifier.size(100.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(4.dp)
                        .shadow(12.dp, CircleShape, ambientColor = neonGreen, spotColor = neonGreen)
                        .border(2.dp, neonGreen, CircleShape)
                        .clickable(enabled = isEditing) { galleryLauncher.launch("image/*") },
                    contentAlignment = Alignment.Center
                ) {
                    val context = LocalContext.current
                    val model = remember(selectedImageUri, user.profileImageUrl) {
                        if (selectedImageUri != null) {
                            selectedImageUri
                        } else if (user.profileImageUrl.isNotEmpty()) {
                            ImageRequest.Builder(context)
                                .data(user.profileImageUrl)
                                .crossfade(true)
                                .diskCachePolicy(CachePolicy.ENABLED)
                                .memoryCachePolicy(CachePolicy.ENABLED)
                                .build()
                        } else {
                            null
                        }
                    }

                    if (model != null) {
                        AsyncImage(
                            model = model,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxSize()
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(imageVector = if (isEditing) Icons.Default.AddAPhoto else Icons.Default.Person, contentDescription = null, modifier = Modifier.size(45.dp), tint = Color.Gray)
                    }
                }

                if (isEditing) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .background(neonGreen, CircleShape)
                            .border(2.dp, darkBg, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            modifier = Modifier.size(14.dp),
                            tint = Color.Black
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))
            if (isEditing) {
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    textStyle = LocalTextStyle.current.copy(textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 18.sp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(focusedBorderColor = neonGreen, unfocusedBorderColor = Color.Gray)
                )
            } else {
                Text(text = user.name, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            }
            Text(text = user.email, color = neonGreen, fontSize = 13.sp, textAlign = TextAlign.Center)

            Spacer(modifier = Modifier.height(8.dp))

            // --- Section: Personal Stats ---
            SectionLabel("PERSONAL STATS", neonGreen)
            Spacer(modifier = Modifier.height(4.dp))

            if (isEditing) {
                // --- EDIT MODE GRID ---
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditStatField("Age", age, Modifier.weight(1f), isDecimal = false) { age = it }
                    DropdownSelectorCompact("Gender", listOf("Male", "Female", "Other"), gender.replaceFirstChar { it.uppercase() }, Modifier.weight(1f)) {
                        gender = it.lowercase()
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    EditStatField("Height (cm)", height, Modifier.weight(1f)) { height = it }
                    EditStatField("Weight (kg)", weight, Modifier.weight(1f)) { weight = it }
                }

                Spacer(modifier = Modifier.height(6.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DropdownSelectorCompact(
                        label = "Activity",
                        options = activityLevels.map { it.first },
                        selected = activityLevels.find { it.second == activityLevel }?.first ?: activityLevel,
                        modifier = Modifier.weight(1f)
                    ) { selectedFullLabel ->
                        activityLevel = activityLevels.find { it.first == selectedFullLabel }?.second ?: activityLevel
                    }

                    DropdownSelectorCompact(
                        label = "Goal",
                        options = listOf("Deficit", "Maintenance", "Surplus"),
                        selected = fitnessGoal.replaceFirstChar { it.uppercase() },
                        modifier = Modifier.weight(1f)
                    ) { fitnessGoal = it.lowercase() }
                }
            } else {
                // Display Mode Grid
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayStatCard("Age", "${user.biometrics.age} Years", Modifier.weight(1f))
                    DisplayStatCard("Gender", user.biometrics.gender.replaceFirstChar { it.uppercase() }, Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    DisplayStatCard("Height", "${user.biometrics.height} cm", Modifier.weight(1f))
                    DisplayStatCard("Weight", "${user.biometrics.weight} kg", Modifier.weight(1f))
                }
                Spacer(modifier = Modifier.height(6.dp))
                val actLabel = activityLevels.find { it.second == user.biometrics.activityLevel }?.first?.substringBefore(" (") ?: user.biometrics.activityLevel

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    DisplayStatCard(
                        label = "Activity",
                        value = actLabel,
                        icon = Icons.Default.FitnessCenter,
                        modifier = Modifier.weight(1f)
                    )
                    DisplayStatCard(
                        label = "Goal",
                        value = user.goals.fitnessGoal.replaceFirstChar { it.uppercase() },
                        icon = Icons.Default.Flag,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // --- Section: Nutritional Targets ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "DAILY TARGETS", color = neonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                if (isEditing) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Auto-calculate", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                        Spacer(modifier = Modifier.width(6.dp))
                        Switch(
                            checked = autoCalculate,
                            onCheckedChange = { autoCalculate = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Color.Black,
                                checkedTrackColor = neonGreen,
                                uncheckedThumbColor = Color.Gray,
                                uncheckedTrackColor = surfaceColor
                            ),
                            modifier = Modifier.scale(0.7f)
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            NutritionalCardMockup(user, isEditing, dailyKcal, carbs, protein, fat, autoCalculate, onKcalChange = { dailyKcal = it }, onCarbsChange = { carbs = it }, onProtChange = { protein = it }, onFatChange = { fat = it }, onAutoToggle = { autoCalculate = it })
            
            Spacer(modifier = Modifier.height(80.dp)) // Spazio per il FAB
        }

        // --- Floating Action Button ---
        FloatingActionButton(
            onClick = {
                if (isEditing) {
                    val updatedUser = user.copy(
                        name = name,
                        manualOverride = !autoCalculate,
                        biometrics = user.biometrics.copy(
                            age = age.toIntOrNull() ?: user.biometrics.age,
                            height = height.toDoubleOrNull() ?: user.biometrics.height,
                            weight = weight.toDoubleOrNull() ?: user.biometrics.weight,
                            gender = gender,
                            activityLevel = activityLevel
                        ),
                        goals = user.goals.copy(
                            fitnessGoal = fitnessGoal,
                            dailyKcal = dailyKcal.toIntOrNull() ?: user.goals.dailyKcal,
                            macrosTarget = mapOf(
                                "carbs" to (carbs.toIntOrNull() ?: 0),
                                "protein" to (protein.toIntOrNull() ?: 0),
                                "fat" to (fat.toIntOrNull() ?: 0)
                            )
                        )
                    )

                    if (selectedImageUri != null) {
                        val storageRef = FirebaseStorage.getInstance().reference.child("profile_images/${user.uid}.jpg")
                        storageRef.putFile(selectedImageUri!!).addOnSuccessListener {
                            storageRef.downloadUrl.addOnSuccessListener { downloadUri ->
                                saveUserToBackend(updatedUser.copy(profileImageUrl = downloadUri.toString()), onUserUpdate)
                            }
                        }.addOnFailureListener { saveUserToBackend(updatedUser, onUserUpdate) }
                    } else {
                        saveUserToBackend(updatedUser, onUserUpdate)
                    }
                }
                isEditing = !isEditing
            },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .size(56.dp),
            containerColor = neonGreen,
            contentColor = Color.Black,
            shape = CircleShape
        ) {
            Icon(imageVector = if (isEditing) Icons.Default.Save else Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
fun SectionLabel(text: String, color: Color) {
    Text(text = text, color = color, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth())
}

@Composable
fun DisplayStatCard(label: String, value: String, modifier: Modifier = Modifier, icon: ImageVector? = null) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(label.uppercase(), color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Text(value, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold, maxLines = 1)
            }
            icon?.let { Icon(it, null, tint = Color(0xFF00E676), modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun EditStatField(label: String, value: String, modifier: Modifier = Modifier, isDecimal: Boolean = true, onValueChange: (String) -> Unit) {
    OutlinedTextField(
        value = value,
        onValueChange = { newValue ->
            val filtered = if (isDecimal) {
                if (newValue.count { it == '.' } <= 1) {
                    newValue.filter { it.isDigit() || it == '.' }
                } else {
                    value
                }
            } else {
                newValue.filter { it.isDigit() }
            }
            onValueChange(filtered)
        },
        label = { Text(text = label.uppercase(), fontSize = 11.sp, maxLines = 1,  fontWeight = FontWeight.Bold) },
        modifier = modifier.height(52.dp),
        textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium),
        keyboardOptions = KeyboardOptions(keyboardType = if (isDecimal) KeyboardType.Decimal else KeyboardType.Number),
        singleLine = true,
        shape = RoundedCornerShape(10.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = Color(0xFF00E676),
            unfocusedBorderColor = Color.Gray,
            focusedContainerColor = Color(0xFF1A2421),
            unfocusedContainerColor = Color(0xFF1A2421),
            focusedLabelColor = Color(0xFF00E676)
        )
    )
}

@Composable
fun NutritionalCardMockup(user: User, isEditing: Boolean, kcal: String, carbs: String, prot: String, fat: String, auto: Boolean, onKcalChange: (String) -> Unit, onCarbsChange: (String) -> Unit, onProtChange: (String) -> Unit, onFatChange: (String) -> Unit, onAutoToggle: (Boolean) -> Unit) {
    val neonGreen = Color(0xFF00E676)
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2421))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("TARGET CALORIES", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    if (isEditing && !auto) {
                        OutlinedTextField(
                            value = kcal,
                            onValueChange = { onKcalChange(it.filter { char -> char.isDigit() }) },
                            modifier = Modifier.width(90.dp).height(48.dp),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            textStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = neonGreen,
                                unfocusedBorderColor = Color.Gray,
                                focusedContainerColor = Color(0xFF0A120E),
                                unfocusedContainerColor = Color(0xFF0A120E)
                            )
                        )
                    } else {
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(text = if(isEditing) kcal else "${user.goals.dailyKcal}", color = neonGreen, fontSize = 24.sp, fontWeight = FontWeight.ExtraBold)
                            Text(text = " kcal", color = Color.White, fontSize = 13.sp, modifier = Modifier.padding(bottom = 3.dp))
                        }
                    }
                }
                Box(modifier = Modifier
                    .size(40.dp)
                    .background(neonGreen.copy(0.1f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Bolt, null, tint = neonGreen, modifier = Modifier.size(24.dp))
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                MacroStatBox("CARBS", if(isEditing && !auto) carbs else "${user.goals.macrosTarget["carbs"]}", isEditing && !auto, onCarbsChange)
                MacroStatBox("FAT", if(isEditing && !auto) fat else "${user.goals.macrosTarget["fat"]}", isEditing && !auto, onFatChange)
                MacroStatBox("PROT", if(isEditing && !auto) prot else "${user.goals.macrosTarget["protein"]}", isEditing && !auto, onProtChange)
            }
        }
    }
}

@Composable
fun MacroStatBox(label: String, value: String, isEditing: Boolean, onValueChange: (String) -> Unit) {
    Column(modifier = Modifier.width(80.dp)) {
        Text(label, color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
        if (isEditing) {
            OutlinedTextField(
                value = value,
                onValueChange = { onValueChange(it.filter { char -> char.isDigit() }) },
                modifier = Modifier.fillMaxWidth().height(45.dp),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = TextStyle(color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold),
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF00E676),
                    unfocusedBorderColor = Color.Gray,
                    focusedContainerColor = Color(0xFF0A120E),
                    unfocusedContainerColor = Color(0xFF0A120E)
                )
            )
        } else {
            Text("${value}g", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(3.dp))
            LinearProgressIndicator(progress = { 0.7f }, modifier = Modifier
                .fillMaxWidth()
                .height(3.dp)
                .clip(CircleShape), color = Color(0xFF00E676), trackColor = Color(0xFF2C2E33))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelectorCompact(
    label: String,
    options: List<String>,
    selected: String,
    modifier: Modifier = Modifier,
    onSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    val displayText = selected.substringBefore(" (")

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = !expanded },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
            onValueChange = {},
            readOnly = true,
            label = { Text(label.uppercase(), fontSize = 11.sp,  fontWeight = FontWeight.Bold) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
                .height(52.dp),
            textStyle = TextStyle(fontSize = 12.sp, color = Color.White),
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = Color(0xFF1A2421),
                unfocusedContainerColor = Color(0xFF1A2421),
                unfocusedBorderColor = Color.Gray,
                focusedBorderColor = Color(0xFF00E676)
            )
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF1A2421))
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, color = Color.White, fontSize = 13.sp) },
                    onClick = {
                        onSelected(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

private fun saveUserToBackend(updatedUser: User, onUserUpdate: (User) -> Unit) {
    RetrofitClient.instance.updateUser(updatedUser).enqueue(object : Callback<UpdateUserResponse> {
        override fun onResponse(call: Call<UpdateUserResponse>, response: Response<UpdateUserResponse>) {
            if (response.isSuccessful) {
                response.body()?.let {
                    val finalGoals = if (updatedUser.manualOverride) updatedUser.goals else updatedUser.goals.copy(dailyKcal = it.dailyKcal, macrosTarget = it.macros)
                    onUserUpdate(updatedUser.copy(goals = finalGoals, manualOverride = updatedUser.manualOverride))
                }
            }
        }
        override fun onFailure(call: Call<UpdateUserResponse>, t: Throwable) { Log.e("API", t.message ?: "") }
    })
}

fun getActivityLevelLabel(value: String): String = when(value) {
    "1.2" -> "Sedentary"
    "1.375" -> "Light Exercise (1-3x/week)"
    "1.55" -> "Moderate Exercise (3-5x/week)"
    "1.725" -> "Active"
    "1.9" -> "Very Active"
    else -> value
}

@Preview(showBackground = true, backgroundColor = 0xFF0A120E)
@Composable
fun ProfileScreenPreview() {
    val dummyUser = User(
        uid = "123",
        email = "mario.rossi@example.com",
        name = "Mario Rossi",
        biometrics = Biometrics(
            age = 30,
            gender = "male",
            height = 180.0,
            weight = 75.0,
            activityLevel = "1.55"
        ),
        goals = Goals(
            dailyKcal = 2500,
            macrosTarget = mapOf("carbs" to 300, "protein" to 150, "fat" to 70),
            fitnessGoal = "maintenance"
        )
    )
    ProfileScreen(user = dummyUser, onUserUpdate = {})
}
