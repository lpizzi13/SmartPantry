package it.sapienza.smartpantry.model

import android.util.Log
import androidx.lifecycle.ViewModel
import it.sapienza.smartpantry.service.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

// --- DATA MODELS ---

data class FoodItem(
    val name: String,
    val quantity: String = ""
)

data class Diet(
    val duid: String = UUID.randomUUID().toString(),
    val name: String,
    val isWeekly: Boolean = false,
    val isFavorite: Boolean = false,
    val days: List<DayPlan> = emptyList(),
    // NEW: Keeps track of which days are expanded in the UI
    val expandedDayIndices: Set<Int> = emptySet()
)

data class DayPlan(
    val name: String,
    val breakfast: List<FoodItem> = emptyList(),
    val lunch: List<FoodItem> = emptyList(),
    val dinner: List<FoodItem> = emptyList(),
    val snacks: List<FoodItem> = emptyList()
)

// --- API REQUEST/RESPONSE MODELS ---

data class DietRequest(val uid: String)
data class DietResponse(val status: String, val dietData: DietData?)
data class DietData(val diets: List<Diet>, val selectedDietId: String?)

data class SaveDietRequest(val uid: String, val dietData: DietPayload)
data class DietPayload(val diets: List<Diet>, val selectedDietId: String?)
data class SaveDietResponse(val status: String)

data class DeleteDietRequest(val uid: String, val duid: String, val newSelectedId: String?)
data class DeleteDietResponse(val status: String)

// --- VIEWMODEL STATE ---

data class DietUiState(
    val diets: List<Diet> = emptyList(),
    val selectedDietId: String? = null
) {
    val selectedDiet: Diet? get() = diets.find { it.duid == selectedDietId }
}

// --- DEFAULTS ---

object DietDefaults {
    val weekDays = listOf(
        DayPlan("Monday"), DayPlan("Tuesday"), DayPlan("Wednesday"),
        DayPlan("Thursday"), DayPlan("Friday"), DayPlan("Saturday"), DayPlan("Sunday")
    )
}

// --- VIEWMODEL ---

class DietViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DietUiState())
    val uiState: StateFlow<DietUiState> = _uiState.asStateFlow()
    private var currentUid: String? = null

    init {
        _uiState.update { it.copy(selectedDietId = it.diets.firstOrNull()?.duid) }
    }

    fun initialize(uid: String) {
        if (uid.isBlank()) return
        currentUid = uid
        loadDiet(uid)
    }

    fun refreshDiet() {
        currentUid?.let { loadDiet(it) }
    }

    private fun loadDiet(uid: String) {
        RetrofitClient.instance.getDiet(DietRequest(uid)).enqueue(object : Callback<DietResponse> {
            override fun onResponse(call: Call<DietResponse>, response: Response<DietResponse>) {
                if (!response.isSuccessful) return
                val remoteDietData = response.body()?.dietData
                val remoteDiets = remoteDietData?.diets ?: emptyList()

                // Priorità di selezione: 1. Dieta preferita, 2. selectedDietId dal backend, 3. Prima dieta della lista
                val favoriteDietId = remoteDiets.find { it.isFavorite }?.duid
                val selectedId = favoriteDietId
                    ?: remoteDietData?.selectedDietId?.takeIf { requestedId -> remoteDiets.any { it.duid == requestedId } }
                    ?: remoteDiets.firstOrNull()?.duid

                _uiState.update { it.copy(diets = remoteDiets, selectedDietId = selectedId) }

                // Se il backend non ha ancora dati dieta, inizializziamo subito se necessario (ma ora partiamo vuoti)
                if (remoteDiets.isEmpty()) {
                    persistDietState()
                }
            }
            override fun onFailure(call: Call<DietResponse>, t: Throwable) {
                Log.e("DIET_LOAD", "Failure: ${t.message}")
            }
        })
    }

    private fun persistDietState() {
        val uid = currentUid ?: return
        val state = _uiState.value
        val request = SaveDietRequest(uid, DietPayload(state.diets, state.selectedDietId))
        RetrofitClient.instance.saveDiet(request).enqueue(object : Callback<SaveDietResponse> {
            override fun onResponse(call: Call<SaveDietResponse>, response: Response<SaveDietResponse>) {}
            override fun onFailure(call: Call<SaveDietResponse>, t: Throwable) {}
        })
    }

    private fun updateState(shouldPersist: Boolean = false, transform: (DietUiState) -> DietUiState) {
        var hasChanged = false
        _uiState.update { state ->
            val updatedState = transform(state)
            hasChanged = updatedState != state
            updatedState
        }
        if (hasChanged && shouldPersist) persistDietState()
    }

    fun onDietSelected(dietId: String) {
        updateState(shouldPersist = false) { it.copy(selectedDietId = dietId) }
    }

    fun onDayClicked(dietId: String, dayIndex: Int) {
        updateState(shouldPersist = false) { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.duid == dietId) {
                    val newIndices = if (diet.expandedDayIndices.contains(dayIndex)) {
                        diet.expandedDayIndices - dayIndex
                    } else {
                        diet.expandedDayIndices + dayIndex
                    }
                    diet.copy(expandedDayIndices = newIndices)
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun onDietNameChanged(dietId: String, newName: String) {
        val trimmedName = newName.trim()
        if (trimmedName.isBlank()) return
        updateState(shouldPersist = true) { state ->
            val updatedDiets = state.diets.map { if (it.duid == dietId) it.copy(name = trimmedName) else it }
            state.copy(diets = updatedDiets)
        }
    }

    fun deleteDiet(duid: String) {
        val uid = currentUid ?: return
        val currentState = _uiState.value
        val updatedDiets = currentState.diets.filter { it.duid != duid }
        val newSelectedId = if (currentState.selectedDietId == duid) {
            updatedDiets.firstOrNull()?.duid
        } else {
            currentState.selectedDietId
        }

        // Chiamata API esplicita per la cancellazione
        val request = DeleteDietRequest(uid, duid, newSelectedId)
        RetrofitClient.instance.deleteDiet(request).enqueue(object : Callback<DeleteDietResponse> {
            override fun onResponse(call: Call<DeleteDietResponse>, response: Response<DeleteDietResponse>) {
                if (response.isSuccessful) {
                    _uiState.update { it.copy(diets = updatedDiets, selectedDietId = newSelectedId) }
                }
            }
            override fun onFailure(call: Call<DeleteDietResponse>, t: Throwable) {
                Log.e("DIET_DELETE", "Failure: ${t.message}")
            }
        })
    }

    fun addNewDiet(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        updateState(shouldPersist = true) { state ->
            val newDiet = Diet(name = trimmedName)
            val newDiets = state.diets + newDiet
            state.copy(diets = newDiets, selectedDietId = newDiet.duid)
        }
    }

    fun addDayToDiet(dietId: String, dayName: String) {
        val trimmedDayName = dayName.trim()
        if (trimmedDayName.isBlank()) return
        updateState(shouldPersist = true) { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.duid == dietId) {
                    diet.copy(days = diet.days + DayPlan(trimmedDayName))
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun addFoodToDay(dietId: String, dayIndex: Int, foodName: String, quantity: String = "", mealType: String = "Breakfast") {
        val trimmedFood = foodName.trim()
        if (trimmedFood.isBlank()) return
        val foodItem = FoodItem(trimmedFood, quantity.trim())
        updateState(shouldPersist = true) { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.duid == dietId) {
                    val updatedDays = diet.days.mapIndexed { index, dayPlan ->
                        if (index == dayIndex) {
                            when (mealType) {
                                "Breakfast", "Colazione" -> dayPlan.copy(breakfast = dayPlan.breakfast + foodItem)
                                "Lunch", "Pranzo" -> dayPlan.copy(lunch = dayPlan.lunch + foodItem)
                                "Dinner", "Cena" -> dayPlan.copy(dinner = dayPlan.dinner + foodItem)
                                "Snacks", "Spuntini" -> dayPlan.copy(snacks = dayPlan.snacks + foodItem)
                                else -> dayPlan.copy(breakfast = dayPlan.breakfast + foodItem)
                            }
                        } else dayPlan
                    }
                    diet.copy(days = updatedDays, expandedDayIndices = diet.expandedDayIndices + dayIndex)
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun editFoodInDay(dietId: String, dayIndex: Int, mealType: String, foodIndex: Int, newFoodName: String, newQuantity: String) {
        val trimmedFood = newFoodName.trim()
        if (trimmedFood.isBlank()) return
        val foodItem = FoodItem(trimmedFood, newQuantity.trim())
        updateState(shouldPersist = true) { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.duid == dietId) {
                    val updatedDays = diet.days.mapIndexed { dIdx, dayPlan ->
                        if (dIdx == dayIndex) {
                            when (mealType) {
                                "Breakfast", "Colazione" -> dayPlan.copy(breakfast = dayPlan.breakfast.toMutableList().apply { this[foodIndex] = foodItem })
                                "Lunch", "Pranzo" -> dayPlan.copy(lunch = dayPlan.lunch.toMutableList().apply { this[foodIndex] = foodItem })
                                "Dinner", "Cena" -> dayPlan.copy(dinner = dayPlan.dinner.toMutableList().apply { this[foodIndex] = foodItem })
                                "Snacks", "Spuntini" -> dayPlan.copy(snacks = dayPlan.snacks.toMutableList().apply { this[foodIndex] = foodItem })
                                else -> dayPlan
                            }
                        } else dayPlan
                    }
                    diet.copy(days = updatedDays)
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun removeFoodFromDay(dietId: String, dayIndex: Int, mealType: String, foodIndex: Int) {
        updateState(shouldPersist = true) { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.duid == dietId) {
                    val updatedDays = diet.days.mapIndexed { dIdx, dayPlan ->
                        if (dIdx == dayIndex) {
                            when (mealType) {
                                "Breakfast", "Colazione" -> dayPlan.copy(breakfast = dayPlan.breakfast.toMutableList().apply { removeAt(foodIndex) })
                                "Lunch", "Pranzo" -> dayPlan.copy(lunch = dayPlan.lunch.toMutableList().apply { removeAt(foodIndex) })
                                "Dinner", "Cena" -> dayPlan.copy(dinner = dayPlan.dinner.toMutableList().apply { removeAt(foodIndex) })
                                "Snacks", "Spuntini" -> dayPlan.copy(snacks = dayPlan.snacks.toMutableList().apply { removeAt(foodIndex) })
                                else -> dayPlan
                            }
                        } else dayPlan
                    }
                    diet.copy(days = updatedDays)
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun onDietWeeklyToggled(dietId: String, isWeekly: Boolean) {
        updateState(shouldPersist = true) { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.duid == dietId) {
                    diet.copy(
                        isWeekly = isWeekly,
                        days = if (isWeekly) DietDefaults.weekDays else emptyList(),
                        expandedDayIndices = emptySet()
                    )
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun toggleFavorite(dietId: String) {
        updateState(shouldPersist = true) { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.duid == dietId) {
                    val newFavoriteStatus = !diet.isFavorite
                    // Se stiamo impostando questa come preferita, impostiamo selectedDietId a questa
                    diet.copy(isFavorite = newFavoriteStatus)
                } else {
                    diet.copy(isFavorite = false)
                }
            }

            // Trova l'ID della dieta appena segnata come preferita (se presente)
            val newFavoriteId = updatedDiets.find { it.isFavorite }?.duid

            if (newFavoriteId != null) {
                state.copy(diets = updatedDiets, selectedDietId = newFavoriteId)
            } else {
                state.copy(diets = updatedDiets)
            }
        }
    }
}