package it.sapienza.smartpantry.ui.viewmodel

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ProfileUiState(
    val firstName: String = "",
    val lastName: String = "",
    val sex: String = "",
    val age: String = "",
    val heightCm: String = "",
    val weightKg: String = "",
    val showSavedMessage: Boolean = false
) {
    val canSave: Boolean
        get() = firstName.isNotBlank() &&
            lastName.isNotBlank() &&
            sex.isNotBlank() &&
            age.isNotBlank() &&
            heightCm.isNotBlank() &&
            weightKg.isNotBlank()
}

class ProfileViewModel : ViewModel() {
    val sexOptions = listOf("Maschio", "Femmina", "Altro")

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    fun onFirstNameChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(firstName = value, showSavedMessage = false)
        }
    }

    fun onLastNameChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(lastName = value, showSavedMessage = false)
        }
    }

    fun onSexChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(sex = value, showSavedMessage = false)
        }
    }

    fun onAgeChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                age = value.filter(Char::isDigit).take(3),
                showSavedMessage = false
            )
        }
    }

    fun onHeightChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                heightCm = sanitizeDecimalInput(value),
                showSavedMessage = false
            )
        }
    }

    fun onWeightChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                weightKg = sanitizeDecimalInput(value),
                showSavedMessage = false
            )
        }
    }

    fun saveProfile() {
        _uiState.update { currentState ->
            if (currentState.canSave) {
                currentState.copy(showSavedMessage = true)
            } else {
                currentState
            }
        }
    }

    private fun sanitizeDecimalInput(raw: String): String {
        val normalized = raw.replace(',', '.')
        val result = StringBuilder()
        var hasDot = false

        normalized.forEach { char ->
            when {
                char.isDigit() -> result.append(char)
                char == '.' && !hasDot -> {
                    result.append(char)
                    hasDot = true
                }
            }
        }

        return result.toString()
    }
}
