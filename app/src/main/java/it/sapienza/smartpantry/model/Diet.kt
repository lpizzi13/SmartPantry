package it.sapienza.smartpantry.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.UUID

data class Diet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val days: List<String> = emptyList(),
    val isEditable: Boolean = false,
    val expandedDayIndex: Int? = null
)

data class DietUiState(
    val diets: List<Diet> = listOf(
        Diet(name = "Weekly Diet Plan", days = listOf(
            "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
        )),
        Diet(name = "New Diet", isEditable = true)
    ),
    val selectedDietId: String? = null
) {
    val selectedDiet: Diet?
        get() = diets.find { it.id == selectedDietId }
}

class DietViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DietUiState())
    val uiState: StateFlow<DietUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(selectedDietId = it.diets.first().id) }
    }

    fun onDietSelected(dietId: String) {
        _uiState.update { it.copy(selectedDietId = dietId) }
    }

    fun onDayClicked(dietId: String, dayIndex: Int) {
        _uiState.update { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.id == dietId) {
                    diet.copy(expandedDayIndex = if (diet.expandedDayIndex == dayIndex) null else dayIndex)
                } else {
                    diet
                }
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun onDietNameChanged(dietId: String, newName: String) {
        if (newName.isBlank()) return

        _uiState.update { state ->
            val dietToUpdate = state.diets.find { it.id == dietId }
            if (dietToUpdate?.name == "New Diet" && newName != "New Diet") {
                // This is a new diet being named for the first time
                val newDiet = dietToUpdate.copy(name = newName)
                val newDiets = state.diets.map { if (it.id == dietId) newDiet else it } +
                        Diet(name = "New Diet", isEditable = true)
                state.copy(diets = newDiets, selectedDietId = newDiet.id)
            } else {
                // Renaming an existing diet
                val updatedDiets = state.diets.map {
                    if (it.id == dietId) it.copy(name = newName) else it
                }
                state.copy(diets = updatedDiets)
            }
        }
    }

    fun addDayToDiet(dietId: String, dayName: String) {
        if (dayName.isBlank()) return

        _uiState.update { state ->
            val dietToUpdate = state.diets.find { it.id == dietId }
            if (dietToUpdate?.name == "New Diet") {
                // If the user adds a day to the "New Diet" before renaming it,
                // we treat it as creating a new diet with a default name.
                val newDietName = "My Diet" // You can customize this default name
                val newDiet = dietToUpdate.copy(name = newDietName, days = dietToUpdate.days + dayName)
                val newDiets = state.diets.map { if (it.id == dietId) newDiet else it } +
                        Diet(name = "New Diet", isEditable = true)
                state.copy(diets = newDiets, selectedDietId = newDiet.id)
            } else {
                val updatedDiets = state.diets.map {
                    if (it.id == dietId) {
                        it.copy(days = it.days + dayName)
                    } else {
                        it
                    }
                }
                state.copy(diets = updatedDiets)
            }
        }
    }
}
