package it.sapienza.smartpantry.ui

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import coil.compose.AsyncImage
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import it.sapienza.smartpantry.R
import it.sapienza.smartpantry.model.*
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var errorMessage by mutableStateOf<String?>(null)
    private var successMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()

        setContent {
            SmartPantryTheme {
                val navController = rememberNavController()
                var showForgotDialog by remember { mutableStateOf(false) }

                NavHost(navController = navController, startDestination = "login") {
                    composable("login") {
                        LoginScreen(
                            onLoginClick = { email, password -> loginUser(email, password) },
                            onRegisterClick = { navController.navigate("signup") },
                            onForgotPasswordClick = { showForgotDialog = true }
                        )
                    }
                    composable("signup") {
                        SignUpScreen(
                            onSignUpClick = { request -> performSignUp(request) },
                            onLoginClick = { navController.popBackStack() },
                            onBackClick = { navController.popBackStack() }
                        )
                    }
                }

                if (showForgotDialog) {
                    ForgotPasswordDialog(
                        onDismiss = { showForgotDialog = false },
                        onSendClick = { email -> resetPassword(email) }
                    )
                }

                if (errorMessage != null) {
                    AlertDialog(
                        onDismissRequest = { errorMessage = null },
                        title = { Text("Error", fontWeight = FontWeight.Bold) },
                        text = { Text(errorMessage!!) },
                        confirmButton = {
                            TextButton(onClick = { errorMessage = null }) {
                                Text("OK", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        containerColor = Color(0xFF1A2421),
                        titleContentColor = Color.White,
                        textContentColor = Color.LightGray
                    )
                }

                if (successMessage != null) {
                    AlertDialog(
                        onDismissRequest = { successMessage = null },
                        title = { Text("Success", fontWeight = FontWeight.Bold) },
                        text = { Text(successMessage!!) },
                        confirmButton = {
                            TextButton(onClick = { successMessage = null }) {
                                Text("OK", color = MaterialTheme.colorScheme.primary)
                            }
                        },
                        containerColor = Color(0xFF1A2421),
                        titleContentColor = Color.White,
                        textContentColor = Color.LightGray
                    )
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (auth.currentUser != null) {
            syncWithBackend(auth.currentUser!!.uid, auth.currentUser!!.email ?: "")
        }
    }

    private fun loginUser(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            errorMessage = "Please fill all fields."
            return
        }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    syncWithBackend(auth.currentUser!!.uid, auth.currentUser!!.email ?: "")
                } else {
                    errorMessage = "Incorrect credentials. Please try again."
                }
            }
    }

    private fun resetPassword(email: String) {
        if (email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            errorMessage = "Please enter a valid email address."
            return
        }
        auth.sendPasswordResetEmail(email)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    successMessage = "Password reset email sent to $email."
                } else {
                    errorMessage = "Failed to send reset email: ${task.exception?.message}"
                }
            }
    }

    private fun performSignUp(request: RegisterRequest) {
        RetrofitClient.instance.registerUser(request).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (response.isSuccessful) {
                    auth.signInWithEmailAndPassword(request.email, request.password)
                        .addOnCompleteListener { task ->
                            if (task.isSuccessful) {
                                navigateToMain(response.body()?.userData)
                            } else {
                                errorMessage = "Account created, but login failed. Please try logging in manually."
                            }
                        }
                } else {
                    errorMessage = "Registration failed: ${response.message()}"
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                errorMessage = "Network error during registration: ${t.message}"
            }
        })
    }

    private fun syncWithBackend(uid: String, email: String) {
        val request = UserRequest(uid, email)
        RetrofitClient.instance.getUserData(request).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (response.isSuccessful) {
                    navigateToMain(response.body()?.userData)
                } else {
                    navigateToMain(User(uid = uid, email = email))
                }
            }
            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                navigateToMain(User(uid = uid, email = email))
            }
        })
    }

    private fun navigateToMain(user: User?) {
        val intent = Intent(this, MainActivity::class.java)
        user?.let { intent.putExtra("user_extra", it) }
        startActivity(intent)
        finish()
    }
}

@Composable
fun SmartPantryTheme(content: @Composable () -> Unit) {
    val darkGreen = Color(0xFF0A120E)
    val neonGreen = Color(0xFF00E676)
    
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = darkGreen,
            surface = Color(0xFF1A2421),
            primary = neonGreen,
            onBackground = Color.White,
            onSurface = Color.White
        ),
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ForgotPasswordDialog(onDismiss: () -> Unit, onSendClick: (String) -> Unit) {
    var email by remember { mutableStateOf("") }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset Password", fontWeight = FontWeight.Bold, color = Color.White) },
        text = {
            Column {
                Text("Enter your email address to receive a password reset link.", color = Color.LightGray, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    placeholder = { Text("name@example.com", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        containerColor = Color(0xFF0A120E),
                        unfocusedBorderColor = Color.DarkGray,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email)
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    onSendClick(email)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary, contentColor = Color.Black),
                shape = RoundedCornerShape(8.dp)
            ) {
                Text("Send Link", fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = Color.Gray)
            }
        },
        containerColor = Color(0xFF1A2421)
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onRegisterClick: () -> Unit,
    onForgotPasswordClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(60.dp))

        Box(
            modifier = Modifier
                .size(100.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(Color(0xFFA3C15A)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painter = painterResource(id = R.drawable.briefcase_meal_24dp_e3e3e3_fill0_wght400_grad0_opsz24),
                contentDescription = null,
                modifier = Modifier.size(60.dp),
                tint = Color(0xFF0A120E)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Smart Pantry",
            color = MaterialTheme.colorScheme.primary,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(48.dp))

        Text(
            text = "Welcome Back",
            color = Color.White,
            fontSize = 32.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Manage your nutrition and pantry with ease.",
            color = Color.Gray,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 8.dp)
        )

        Spacer(modifier = Modifier.height(40.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Text(text = "Email Address", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                placeholder = { Text("name@example.com", color = Color.DarkGray) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Email, contentDescription = null, tint = Color.Gray) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(text = "Password", color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                Text(
                    text = "Forgot Password?",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp).clickable { onForgotPasswordClick() }
                )
            }
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                placeholder = { Text("••••••••", color = Color.DarkGray) },
                modifier = Modifier.fillMaxWidth(),
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = Color.Gray) },
                trailingIcon = {
                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = Color.Gray)
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done)
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Login Button
        Button(
            onClick = { onLoginClick(email, password) },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            )
        ) {
            Text(text = "Login", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.weight(1f))

        Row(modifier = Modifier.padding(bottom = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Don't have an account? ", color = Color.Gray)
            Text(
                text = "Sign Up",
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onRegisterClick() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpScreen(
    onSignUpClick: (RegisterRequest) -> Unit,
    onLoginClick: () -> Unit,
    onBackClick: () -> Unit
) {
    var step by remember { mutableIntStateOf(1) }
    
    // Step 1 states
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var confirmPasswordVisible by remember { mutableStateOf(false) }

    // Step 2 states
    var fullName by remember { mutableStateOf("") }
    var age by remember { mutableStateOf("") }
    var gender by remember { mutableStateOf("") }
    var height by remember { mutableStateOf("") }
    var weight by remember { mutableStateOf("") }
    var activityLevel by remember { mutableStateOf("") }
    var fitnessGoal by remember { mutableStateOf("") }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isUploading by remember { mutableStateOf(false) }

    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { selectedImageUri = it }

    val activityLevels = listOf(
        "Sedentary (Office, little exercise)" to "1.2",
        "Light (Exercise 1-3 times/week)" to "1.375",
        "Moderate (Exercise 3-5 times/week)" to "1.55",
        "Active (Physical work or daily sport)" to "1.725",
        "Very Active (Athlete or very heavy work)" to "1.9"
    )
    val genders = listOf("Male", "Female", "Other")
    val fitnessGoals = listOf("Deficit", "Maintenance", "Surplus")

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { if (step > 1) step-- else onBackClick() }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
            }
            Text(text = "Registration", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), textAlign = TextAlign.Center)
            Box(modifier = Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (step == 1) {
                // Step 1: Credentials
                val isStep1Valid = email.isNotEmpty() && password.isNotEmpty() && confirmPassword.isNotEmpty() && password == confirmPassword && Patterns.EMAIL_ADDRESS.matcher(email).matches()

                Box(
                    modifier = Modifier.size(80.dp).clip(RoundedCornerShape(22.dp)).background(Color(0xFF00E676).copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(painter = painterResource(id = R.drawable.briefcase_meal_24dp_e3e3e3_fill0_wght400_grad0_opsz24), contentDescription = null, modifier = Modifier.size(50.dp), tint = Color(0xFF00E676))
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Create Account", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Step 1: Your credentials", color = Color.Gray, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp)
                
                Spacer(modifier = Modifier.height(32.dp))

                SignUpField(label = "Email Address", value = email, placeholder = "Enter your email", icon = Icons.Default.Email, onValueChange = { email = it })
                Spacer(modifier = Modifier.height(16.dp))
                SignUpField(
                    label = "Password", value = password, placeholder = "Create a password", icon = Icons.Default.Lock, isPassword = true,
                    passwordVisible = passwordVisible, onVisibilityChange = { passwordVisible = !passwordVisible }, onValueChange = { password = it }
                )
                Spacer(modifier = Modifier.height(16.dp))
                SignUpField(
                    label = "Confirm Password", value = confirmPassword, placeholder = "Repeat your password", icon = Icons.Default.Lock, isPassword = true,
                    passwordVisible = confirmPasswordVisible, onVisibilityChange = { confirmPasswordVisible = !confirmPasswordVisible }, onValueChange = { confirmPassword = it }
                )

                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = { if (isStep1Valid) step = 2 },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStep1Valid) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f), 
                        contentColor = Color.Black
                    ),
                    enabled = isStep1Valid
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Next", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowForward, contentDescription = null)
                    }
                }
            } else {
                // Step 2: Profile Setup
                val isStep2Valid = fullName.isNotBlank() && age.isNotBlank() && gender.isNotBlank() && height.isNotBlank() && weight.isNotBlank() && activityLevel.isNotBlank() && fitnessGoal.isNotBlank()

                Box(
                    modifier = Modifier.size(100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFF0E68C).copy(alpha = 0.5f))
                            .clickable { galleryLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (selectedImageUri != null) {
                            AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize().clip(CircleShape), contentScale = ContentScale.Crop)
                        } else {
                            Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(60.dp), tint = Color.White)
                        }
                    }
                    Icon(
                        Icons.Default.Add, contentDescription = null, tint = Color.Black,
                        modifier = Modifier.align(Alignment.BottomEnd).size(28.dp).background(MaterialTheme.colorScheme.primary, CircleShape).padding(4.dp)
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text("Set up your profile", style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold)
                Text("Step 2: Biometrics", color = Color.Gray, modifier = Modifier.padding(top = 8.dp), fontSize = 14.sp)

                Spacer(modifier = Modifier.height(24.dp))

                SignUpField(label = "Full Name", value = fullName, placeholder = "Enter your full name", onValueChange = { fullName = it })
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SignUpField(
                        label = "Age", value = age, placeholder = "25", modifier = Modifier.weight(1f), 
                        keyboardType = KeyboardType.Number, onValueChange = { if (it.all { char -> char.isDigit() }) age = it }
                    )
                    SignUpDropdown(label = "Gender", selected = gender, options = genders, modifier = Modifier.weight(1f)) { gender = it }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    SignUpField(
                        label = "Height (cm)", value = height, placeholder = "180", modifier = Modifier.weight(1f), 
                        keyboardType = KeyboardType.Number, onValueChange = { if (it.all { char -> char.isDigit() }) height = it }
                    )
                    SignUpField(
                        label = "Weight (kg)", value = weight, placeholder = "75", modifier = Modifier.weight(1f), 
                        keyboardType = KeyboardType.Number, onValueChange = { if (it.all { char -> char.isDigit() || char == '.' }) weight = it }
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                SignUpDropdown(label = "Activity Level", selected = activityLevel, options = activityLevels.map { it.first }) { activityLevel = it }
                Spacer(modifier = Modifier.height(12.dp))
                SignUpDropdown(label = "Fitness Goal", selected = fitnessGoal, options = fitnessGoals) { fitnessGoal = it }

                Spacer(modifier = Modifier.weight(1f))
                
                Button(
                    onClick = {
                        isUploading = true
                        val biometrics = Biometrics(
                            age = age.toIntOrNull() ?: 0,
                            height = height.toDoubleOrNull() ?: 0.0,
                            weight = weight.toDoubleOrNull() ?: 0.0,
                            gender = gender.lowercase(),
                            activityLevel = activityLevels.find { it.first == activityLevel }?.second ?: "1.2"
                        )
                        val goals = Goals(fitnessGoal = fitnessGoal.lowercase())
                        
                        val registerRequest = RegisterRequest(
                            email = email,
                            password = password,
                            name = fullName,
                            biometrics = biometrics,
                            goals = goals,
                            profileImageUrl = "",
                            manualOverride = false
                        )

                        if (selectedImageUri != null) {
                            val storageRef = FirebaseStorage.getInstance().reference.child("profile_images/${UUID.randomUUID()}.jpg")
                            storageRef.putFile(selectedImageUri!!).addOnSuccessListener {
                                storageRef.downloadUrl.addOnSuccessListener { uri ->
                                    onSignUpClick(registerRequest.copy(profileImageUrl = uri.toString()))
                                }
                            }.addOnFailureListener { onSignUpClick(registerRequest) }
                        } else {
                            onSignUpClick(registerRequest)
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isStep2Valid) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.5f), 
                        contentColor = Color.Black
                    ),
                    enabled = isStep2Valid && !isUploading
                ) {
                    if (isUploading) CircularProgressIndicator(color = Color.Black)
                    else Text("Complete Profile", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            StepIndicator(currentStep = step)
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpField(
    label: String, value: String, placeholder: String, icon: ImageVector? = null,
    isPassword: Boolean = false, passwordVisible: Boolean = false,
    modifier: Modifier = Modifier, keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit, onVisibilityChange: (() -> Unit)? = null
) {
    Column(modifier = modifier) {
        Text(text = label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        OutlinedTextField(
            value = value, onValueChange = onValueChange, placeholder = { Text(placeholder, color = Color.DarkGray) },
            modifier = Modifier.fillMaxWidth(),
            leadingIcon = icon?.let { { Icon(it, contentDescription = null, tint = Color.Gray) } },
            trailingIcon = if (isPassword) {
                {
                    IconButton(onClick = { onVisibilityChange?.invoke() }) {
                        Icon(imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff, contentDescription = null, tint = Color.Gray)
                    }
                }
            } else null,
            visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
            shape = RoundedCornerShape(12.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                containerColor = MaterialTheme.colorScheme.surface,
                unfocusedBorderColor = Color.DarkGray,
                focusedBorderColor = MaterialTheme.colorScheme.primary,
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            ),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType)
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SignUpDropdown(label: String, selected: String, options: List<String>, modifier: Modifier = Modifier, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = modifier) {
        Text(text = label, color = Color.White, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
        ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = !expanded }) {
            OutlinedTextField(
                value = if (selected.isEmpty()) "Select" else selected, onValueChange = {}, readOnly = true,
                modifier = Modifier.menuAnchor().fillMaxWidth(),
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    focusedTextColor = if (selected.isEmpty()) Color.Gray else Color.White,
                    unfocusedTextColor = if (selected.isEmpty()) Color.Gray else Color.White
                )
            )
            ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
                }
            }
        }
    }
}

@Composable
fun StepIndicator(currentStep: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(2) { index ->
            Box(
                modifier = Modifier
                    .width(32.dp)
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(if (index + 1 == currentStep) Color(0xFF00E676) else Color.DarkGray)
            )
        }
    }
}
