package it.sapienza.smartpantry.ui

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.UserRequest
import it.sapienza.smartpantry.model.UserResponse
import it.sapienza.smartpantry.service.RetrofitClient
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

class LoginActivity : ComponentActivity() {

    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        auth = FirebaseAuth.getInstance()

        setContent {
            MaterialTheme {
                LoginScreen(
                    onLoginClick = { email, password ->
                        loginUser(email, password)
                    },
                    onRegisterClick = {
                        Toast.makeText(this, "Registration function to be implemented", Toast.LENGTH_SHORT).show()
                    }
                )
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
            Toast.makeText(this, "Please enter email and password!", Toast.LENGTH_SHORT).show()
            return
        }

        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        syncWithBackend(user.uid, user.email ?: "")
                    }
                } else {
                    Toast.makeText(
                        this,
                        "Error: ${task.exception?.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
    }

    private fun syncWithBackend(uid: String, email: String) {
        val request = UserRequest(uid, email)

        RetrofitClient.instance.getUserData(request).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                if (response.isSuccessful) {
                    val userResponse = response.body()
                    val userProfile = userResponse?.userData
                    
                    userProfile?.let {
                        if (it.biometrics.weight == 0.0) {
                            navigateToSetup(it)
                        } else {
                            navigateToMain(it)
                        }
                    }
                } else {
                    Log.e("API_ERROR", "Error code: ${response.code()}")
                    // Fallback in caso di errore API
                    navigateToMain(User(uid = uid, email = email))
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                Log.e("NETWORK_ERROR", "Error: ${t.message}")
                // Fallback in caso di errore di rete
                navigateToMain(User(uid = uid, email = email))
            }
        })
    }

    private fun navigateToMain(user: User? = null) {
        val intent = Intent(this, MainActivity::class.java)
        user?.let { intent.putExtra("user_extra", it) }
        startActivity(intent)
        finish()
    }

    private fun navigateToSetup(user: User) {
        // Implementazione futura per onboarding/setup dati mancanti
        navigateToMain(user)
    }
}

@Composable
fun LoginScreen(
    onLoginClick: (String, String) -> Unit,
    onRegisterClick: () -> Unit
) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Welcome to SmartPantry",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 32.dp)
            )

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("Email") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Password") },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            Button(
                onClick = { onLoginClick(email, password) },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Login")
            }

            TextButton(
                onClick = onRegisterClick,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Don't have an account? Register")
            }
        }
    }
}