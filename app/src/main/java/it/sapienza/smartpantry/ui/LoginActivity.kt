package it.sapienza.smartpantry.ui

import android.content.Intent
import android.os.Bundle
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.firebase.auth.FirebaseAuth
import it.sapienza.smartpantry.model.User
import it.sapienza.smartpantry.model.UserRequest
import it.sapienza.smartpantry.model.UserResponse
import it.sapienza.smartpantry.service.RetrofitClient
import it.sapienza.smartpantry.service.SmartPantryApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val userProfile: User? = null,
    val navigateToMain: Boolean = false,
    val navigateToSetup: Boolean = false
)

class LoginViewModel(
    private val api: SmartPantryApi = RetrofitClient.instance,
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
) : ViewModel() {

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<String>()
    val events = _events.asSharedFlow()

    fun onStart() {
        auth.currentUser?.let { user ->
            syncWithBackend(user.uid, user.email ?: "")
        }
    }

    fun login(email: String, password: String) {
        if (email.isEmpty() || password.isEmpty()) {
            _uiState.update { it.copy(error = "Please enter email and password!") }
            return
        }

        _uiState.update { it.copy(isLoading = true, error = null) }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser
                    if (user != null) {
                        syncWithBackend(user.uid, user.email ?: "")
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, error = task.exception?.message) }
                }
            }
    }

    private fun syncWithBackend(uid: String, email: String) {
        _uiState.update { it.copy(isLoading = true) }
        api.getUserData(UserRequest(uid, email)).enqueue(object : Callback<UserResponse> {
            override fun onResponse(call: Call<UserResponse>, response: Response<UserResponse>) {
                _uiState.update { it.copy(isLoading = false) }
                if (response.isSuccessful) {
                    val userProfile = response.body()?.userData
                    if (userProfile != null) {
                        if (userProfile.biometrics.weight == 0.0) {
                            _uiState.update { it.copy(userProfile = userProfile, navigateToSetup = true) }
                        } else {
                            _uiState.update { it.copy(userProfile = userProfile, navigateToMain = true) }
                        }
                    }
                } else {
                    _uiState.update { it.copy(userProfile = User(uid = uid, email = email), navigateToMain = true) }
                }
            }

            override fun onFailure(call: Call<UserResponse>, t: Throwable) {
                _uiState.update { it.copy(isLoading = false, userProfile = User(uid = uid, email = email), navigateToMain = true) }
            }
        })
    }
    
    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateToMain = false, navigateToSetup = false) }
    }
}

class LoginActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            val loginViewModel: LoginViewModel = viewModel()
            val uiState by loginViewModel.uiState.collectAsState()

            LaunchedEffect(uiState.navigateToMain, uiState.navigateToSetup) {
                if (uiState.navigateToMain || uiState.navigateToSetup) {
                    navigateToMain(uiState.userProfile)
                    loginViewModel.onNavigationHandled()
                }
                uiState.error?.let {
                    Toast.makeText(this@LoginActivity, it, Toast.LENGTH_LONG).show()
                }
            }

            MaterialTheme {
                LoginScreen(
                    onLoginClick = { email, password ->
                        loginViewModel.login(email, password)
                    },
                    onRegisterClick = {
                        Toast.makeText(this, "Registration function to be implemented", Toast.LENGTH_SHORT).show()
                    }
                )
            }
        }
    }

    private fun navigateToMain(user: User? = null) {
        val intent = Intent(this, MainActivity::class.java)
        user?.let { intent.putExtra("user_extra", it) }
        startActivity(intent)
        finish()
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

            @OptIn(ExperimentalMaterial3Api::class)
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

            @OptIn(ExperimentalMaterial3Api::class)
            TextButton(
                onClick = onRegisterClick,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Text("Don't have an account? Register")
            }
        }
    }
}
