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
            currentState.copy(query = value, errorMessage = null)
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

    fun searchFoodByCode(code: String) {
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(errorMessage = "Codice scansionato non valido.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    query = normalizedCode,
                    isLoading = true,
                    errorMessage = null
                )
            }

            runCatching {
                repository.findProductByCode(normalizedCode)
            }.onSuccess { product ->
                _uiState.update { currentState ->
                    if (product == null) {
                        currentState.copy(
                            foods = emptyList(),
                            isLoading = false,
                            errorMessage = "Nessun alimento trovato per il codice inserito."
                        )
                    } else {
                        currentState.copy(
                            foods = listOf(
                                FoodSearchItemUi(
                                    name = product.name,
                                    brand = product.brand,
                                    caloriesPer100g = product.caloriesPer100g
                                )
                            ),
                            isLoading = false,
                            errorMessage = null
                        )
                    }
                }
            }.onFailure {
                _uiState.update { currentState ->
                    currentState.copy(
                        foods = emptyList(),
                        isLoading = false,
                        errorMessage = "Errore nel recupero alimento. Riprova."
                    )
                }
            }
        }
    }

    fun onScannerError(message: String) {
        _uiState.update { currentState ->
            currentState.copy(errorMessage = message)
        }
    }
}
