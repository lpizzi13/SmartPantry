package it.sapienza.smartpantry.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginUiState(
    val email: String = "",
    val password: String = "",
    val isLoading: Boolean = false
)

sealed interface LoginEvent {
    data class ShowMessage(val message: String) : LoginEvent
    data object NavigateToMain : LoginEvent
}

class LoginViewModel : ViewModel() {
    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(LoginUiState())
    val uiState: StateFlow<LoginUiState> = _uiState.asStateFlow()

    private val _events = MutableSharedFlow<LoginEvent>()
    val events: SharedFlow<LoginEvent> = _events.asSharedFlow()

    fun onEmailChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(email = value)
        }
    }

    fun onPasswordChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(password = value)
        }
    }

    fun login() {
        val currentState = _uiState.value
        val email = currentState.email.trim()
        val password = currentState.password

        if (email.isEmpty() || password.isEmpty()) {
            emitEvent(LoginEvent.ShowMessage("Inserisci email e password!"))
            return
        }

        _uiState.update { state -> state.copy(isLoading = true) }
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener { task ->
                _uiState.update { state -> state.copy(isLoading = false) }
                if (task.isSuccessful) {
                    emitEvent(LoginEvent.NavigateToMain)
                } else {
                    val message = task.exception?.message ?: "Errore di autenticazione"
                    emitEvent(LoginEvent.ShowMessage("Errore: $message"))
                }
            }
    }

    fun register() {
        emitEvent(LoginEvent.ShowMessage("Funzione registrazione da implementare"))
    }

    fun hasLoggedUser(): Boolean {
        return auth.currentUser != null
    }

    private fun emitEvent(event: LoginEvent) {
        viewModelScope.launch {
            _events.emit(event)
        }
    }
}
