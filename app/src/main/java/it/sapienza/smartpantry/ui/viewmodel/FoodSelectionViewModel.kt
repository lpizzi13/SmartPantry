package it.sapienza.smartpantry.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.sapienza.smartpantry.data.openfoodfacts.OpenFoodFactsRepository
import it.sapienza.smartpantry.ui.model.FoodSearchItemUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class FoodSelectionUiState(
    val mealName: String = "",
    val query: String = "",
    val foods: List<FoodSearchItemUi> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class FoodSelectionViewModel : ViewModel() {
    private val repository = OpenFoodFactsRepository()

    private val _uiState = MutableStateFlow(FoodSelectionUiState())
    val uiState: StateFlow<FoodSelectionUiState> = _uiState.asStateFlow()

    fun initialize(mealName: String) {
        _uiState.update { currentState ->
            if (currentState.mealName == mealName && currentState.foods.isNotEmpty()) {
                currentState
            } else {
                currentState.copy(mealName = mealName)
            }
        }

        if (_uiState.value.foods.isEmpty()) {
            searchFoods()
        }
    }

    fun onQueryChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(query = value)
        }
    }

    fun searchFoods() {
        val query = _uiState.value.query.trim()

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(isLoading = true, errorMessage = null)
            }

            runCatching {
                repository.searchFoods(query)
            }.onSuccess { foods ->
                _uiState.update { currentState ->
                    currentState.copy(
                        foods = foods,
                        isLoading = false,
                        errorMessage = null
                    )
                }
            }.onFailure {
                _uiState.update { currentState ->
                    currentState.copy(
                        isLoading = false,
                        errorMessage = "Errore nel caricamento alimenti. Riprova."
                    )
                }
            }
        }
    }
}
