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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAPhoto
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.storage.FirebaseStorage
import it.sapienza.smartpantry.model.UpdateUserResponse
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(user: User, onUserUpdate: (User) -> Unit) {
    var isEditing by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current
    val context = LocalContext.current
    
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
    
    // manualOverride is true when the checkbox is NOT checked
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
    val genders = listOf("Male", "Female", "Other")
    val fitnessGoalsList = listOf("Deficit", "Maintenance", "Surplus")

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(2.dp, MaterialTheme.colorScheme.primary, CircleShape)
                    .clickable(enabled = isEditing) { galleryLauncher.launch("image/*") },
                contentAlignment = Alignment.Center
            ) {
                if (selectedImageUri != null) {
                    AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else if (user.profileImageUrl.isNotEmpty()) {
                    AsyncImage(model = user.profileImageUrl, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                } else {
                    Icon(imageVector = if (isEditing) Icons.Default.AddAPhoto else Icons.Default.Person, contentDescription = null, modifier = Modifier.size(50.dp), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(text = user.name, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Text(text = user.email, style = MaterialTheme.typography.titleMedium, color = Color.Gray, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(12.dp))

            Card(
                modifier = Modifier.fillMaxWidth(),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    if (isEditing) {
                        OutlinedTextField(
                            value = name, 
                            onValueChange = { name = it }, 
                            label = { Text("Name") }, 
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = age, onValueChange = { age = it }, label = { Text("Age") }, modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                            )
                            OutlinedTextField(
                                value = height, onValueChange = { height = it }, label = { Text("Height") }, modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) })
                            )
                            OutlinedTextField(
                                value = weight, onValueChange = { weight = it }, label = { Text("Weight") }, modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) })
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        DropdownSelector(label = "Gender", options = genders, selected = gender.replaceFirstChar { it.uppercase() }) { gender = it.lowercase() }
                        Spacer(modifier = Modifier.height(8.dp))
                        DropdownSelector(label = "Activity", options = activityLevels.map { it.first }, selected = activityLevels.find { it.second == activityLevel }?.first ?: "Select") { lbl ->
                            activityLevel = activityLevels.find { it.first == lbl }?.second ?: activityLevel
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        DropdownSelector(label = "Fitness Goal", options = fitnessGoalsList, selected = fitnessGoal.replaceFirstChar { it.uppercase() }) { fitnessGoal = it }
                        
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        Row(
                            verticalAlignment = Alignment.CenterVertically, 
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Target Goals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { autoCalculate = !autoCalculate }) {
                                Checkbox(
                                    checked = autoCalculate, 
                                    onCheckedChange = { autoCalculate = it },
                                    modifier = Modifier.size(32.dp)
                                )
                                Text("Auto-calculate", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = dailyKcal, 
                            onValueChange = { dailyKcal = it }, 
                            label = { Text("Daily Kcal") }, 
                            modifier = Modifier.fillMaxWidth(), 
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                            keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Down) }),
                            enabled = !autoCalculate
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = carbs, onValueChange = { carbs = it }, label = { Text("Carbs (g)") }, modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                enabled = !autoCalculate
                            )
                            OutlinedTextField(
                                value = protein, onValueChange = { protein = it }, label = { Text("Prot (g)") }, modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                                keyboardActions = KeyboardActions(onNext = { focusManager.moveFocus(FocusDirection.Next) }),
                                enabled = !autoCalculate
                            )
                            OutlinedTextField(
                                value = fat, onValueChange = { fat = it }, label = { Text("Fat (g)") }, modifier = Modifier.weight(1f),
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                                keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                                enabled = !autoCalculate
                            )
                        }
                    } else {
                        ProfileInfoRow(label = "Age", value = user.biometrics.age.toString())
                        ProfileInfoRow(label = "Gender", value = user.biometrics.gender.replaceFirstChar { it.uppercase() })
                        ProfileInfoRow(label = "Height", value = "${user.biometrics.height} cm")
                        ProfileInfoRow(label = "Weight", value = "${user.biometrics.weight} kg")
                        
                        val currentActivityLabel = activityLevels.find { it.second == user.biometrics.activityLevel }?.first ?: user.biometrics.activityLevel
                        ProfileInfoRow(label = "Activity", value = currentActivityLabel)
                        
                        ProfileInfoRow(label = "Fitness Goal", value = user.goals.fitnessGoal.replaceFirstChar { it.uppercase() })
                        
                        Spacer(modifier = Modifier.height(12.dp))
                        Box(modifier = Modifier.align(Alignment.CenterHorizontally).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer).padding(horizontal = 20.dp, vertical = 4.dp)) {
                            Text(text = "Daily Goals", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer, style = MaterialTheme.typography.labelMedium)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        ProfileInfoRow(label = "Daily Kcal", value = "${user.goals.dailyKcal} kcal")
                        user.goals.macrosTarget.forEach { (macro, value) -> ProfileInfoRow(label = macro.replaceFirstChar { it.uppercase() }, value = "$value g") }
                    }
                }
            }
            Spacer(modifier = Modifier.height(80.dp))
        }

        ExtendedFloatingActionButton(
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
            icon = { Icon(if (isEditing) Icons.Default.Save else Icons.Default.Edit, contentDescription = null) },
            text = { Text(if (isEditing) "Save" else "Edit Profile") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        )
    }
}

private fun saveUserToBackend(updatedUser: User, onUserUpdate: (User) -> Unit) {
    RetrofitClient.instance.updateUser(updatedUser).enqueue(object : Callback<UpdateUserResponse> {
        override fun onResponse(call: Call<UpdateUserResponse>, response: Response<UpdateUserResponse>) {
            if (response.isSuccessful) {
                response.body()?.let { 
                    val finalGoals = if (updatedUser.manualOverride) {
                        updatedUser.goals
                    } else {
                        updatedUser.goals.copy(dailyKcal = it.dailyKcal, macrosTarget = it.macros)
                    }
                    onUserUpdate(updatedUser.copy(goals = finalGoals, manualOverride = updatedUser.manualOverride))
                }
            }
        }
        override fun onFailure(call: Call<UpdateUserResponse>, t: Throwable) { Log.e("API", t.message ?: "") }
    })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(label: String, options: List<String>, selected: String, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
        OutlinedTextField(value = selected, onValueChange = {}, readOnly = true, label = { Text(label) }, trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }, modifier = Modifier.menuAnchor().fillMaxWidth())
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
        }
    }
}

@Composable
fun ProfileInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontWeight = FontWeight.SemiBold, color = Color.Gray, style = MaterialTheme.typography.bodyLarge)
        Text(text = value, style = MaterialTheme.typography.bodyLarge)
    }
}
