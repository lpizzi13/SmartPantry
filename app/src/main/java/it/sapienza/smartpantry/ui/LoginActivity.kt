package it.sapienza.smartpantry.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.smartpantry.R
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.UserRequest
import it.sapienza.smartpantry.model.UserResponse
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth
    private var errorMessage by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()

        setContent {
            SmartPantryTheme {
                LoginScreen(
                    onLoginClick = { email, password -> loginUser(email, password) },
                    onRegisterClick = { /* Implement register navigation */ },
                    onForgotPasswordClick = { /* Implement forgot password */ }
                )

                // Popup di errore credenziali
                if (errorMessage != null) {
                    AlertDialog(
                        onDismissRequest = { errorMessage = null },
                        title = { Text("Login Failed", fontWeight = FontWeight.Bold) },
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

        // Logo
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

        // Email Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Email Address",
                color = Color.White,
                fontSize = 14.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )
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
                    focusedBorderColor = MaterialTheme.colorScheme.primary
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email, imeAction = ImeAction.Next)
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Password Field
        Column(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = "Password",
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Text(
                    text = "Forgot Password?",
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(bottom = 8.dp)
                        .clickable { onForgotPasswordClick() }
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
                        Icon(
                            imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = null,
                            tint = Color.Gray
                        )
                    }
                },
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    unfocusedBorderColor = Color.DarkGray,
                    focusedBorderColor = MaterialTheme.colorScheme.primary
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

        // Footer
        Row(
            modifier = Modifier.padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
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
