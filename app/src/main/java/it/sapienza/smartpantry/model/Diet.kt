package it.sapienza.smartpantry.model

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.gson.annotations.SerializedName
import it.sapienza.smartpantry.service.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import java.util.UUID

/**
 * Rappresenta il piano alimentare di un singolo giorno.
 */
data class DayPlan(
    val name: String,
    val foods: List<String> = emptyList()
)

/**
 * Rappresenta una Dieta.
 */
data class Diet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val days: List<DayPlan> = emptyList(),
    val expandedDayIndices: Set<Int> = emptySet(),
    val isWeekly: Boolean = false,
    val isFavorite: Boolean = false
)

object DietDefaults {
    const val NEW_DIET_NAME = "New Diet"
    val weekDays = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    ).map { DayPlan(it) }

    fun initialDiets(): List<Diet> = listOf(
        Diet(name = NEW_DIET_NAME, isWeekly = false)
    )
}

data class DietUiState(
    val diets: List<Diet> = DietDefaults.initialDiets(),
    val selectedDietId: String? = null
) {
    val selectedDiet: Diet?
        get() = diets.find { it.id == selectedDietId }
}

data class DietRequest(@SerializedName("uid") val uid: String)
data class DietPayload(
    @SerializedName("diets") val diets: List<Diet> = emptyList(),
    @SerializedName("selectedDietId") val selectedDietId: String? = null
)
data class SaveDietRequest(
    @SerializedName("uid") val uid: String,
    @SerializedName("dietData") val dietData: DietPayload
)
data class DietResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("dietData") val dietData: DietPayload? = null
)
data class SaveDietResponse(@SerializedName("status") val status: String = "")

class DietViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DietUiState())
    val uiState: StateFlow<DietUiState> = _uiState.asStateFlow()
    private var currentUid: String? = null

    init {
        _uiState.update { it.copy(selectedDietId = it.diets.firstOrNull()?.id) }
    }

    fun initialize(uid: String) {
        if (uid.isBlank() || currentUid == uid) return
        currentUid = uid
        loadDiet(uid)
    }

    private fun loadDiet(uid: String) {
        RetrofitClient.instance.getDiet(DietRequest(uid)).enqueue(object : Callback<DietResponse> {
            override fun onResponse(call: Call<DietResponse>, response: Response<DietResponse>) {
                if (!response.isSuccessful) return
                val remoteDietData = response.body()?.dietData ?: return
                val normalizedDiets = normalizeDiets(remoteDietData.diets)
                val selectedId = remoteDietData.selectedDietId
                    ?.takeIf { requestedId -> normalizedDiets.any { it.id == requestedId } }
                    ?: normalizedDiets.firstOrNull()?.id
                _uiState.update { it.copy(diets = normalizedDiets, selectedDietId = selectedId) }
            }
            override fun onFailure(call: Call<DietResponse>, t: Throwable) {
                Log.e("DIET_LOAD", "Failure: ${t.message}")
            }
        })
    }

    private fun normalizeDiets(remoteDiets: List<Diet>): List<Diet> {
        if (remoteDiets.isEmpty()) return DietDefaults.initialDiets()
        val normalized = remoteDiets.toMutableList()
        if (!normalized.any { it.name == DietDefaults.NEW_DIET_NAME}) {
            normalized.add(Diet(name = DietDefaults.NEW_DIET_NAME, isWeekly = false))
        }
        return normalized
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

    private fun updatePersistentState(transform: (DietUiState) -> DietUiState) {
        var hasChanged = false
        _uiState.update { state ->
            val updatedState = transform(state)
            hasChanged = updatedState != state
            updatedState
        }
        if (hasChanged) persistDietState()
    }

    fun onDietSelected(dietId: String) {
        updatePersistentState { it.copy(selectedDietId = dietId) }
    }

    fun onDayClicked(dietId: String, dayIndex: Int) {
        updatePersistentState { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.id == dietId) {
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
        updatePersistentState { state ->
            val dietToUpdate = state.diets.find { it.id == dietId }
            if (dietToUpdate?.name == DietDefaults.NEW_DIET_NAME && trimmedName != DietDefaults.NEW_DIET_NAME) {
                val newDiet = dietToUpdate.copy(name = trimmedName)
                val newDiets = state.diets.map { if (it.id == dietId) newDiet else it } +
                        Diet(name = DietDefaults.NEW_DIET_NAME, isWeekly = false)
                state.copy(diets = newDiets, selectedDietId = newDiet.id)
            } else {
                val updatedDiets = state.diets.map { if (it.id == dietId) it.copy(name = trimmedName) else it }
                state.copy(diets = updatedDiets)
            }
        }
    }

    fun addDayToDiet(dietId: String, dayName: String) {
        val trimmedDayName = dayName.trim()
        if (trimmedDayName.isBlank()) return
        updatePersistentState { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.id == dietId) {
                    diet.copy(days = diet.days + DayPlan(trimmedDayName))
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun addFoodToDay(dietId: String, dayIndex: Int, foodName: String) {
        val trimmedFood = foodName.trim()
        if (trimmedFood.isBlank()) return
        updatePersistentState { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.id == dietId) {
                    val updatedDays = diet.days.mapIndexed { index, dayPlan ->
                        if (index == dayIndex) dayPlan.copy(foods = dayPlan.foods + trimmedFood) else dayPlan
                    }
                    diet.copy(days = updatedDays, expandedDayIndices = diet.expandedDayIndices + dayIndex)
                } else diet
            }
            state.copy(diets = updatedDiets)
        }
    }

    fun onDietWeeklyToggled(dietId: String, isWeekly: Boolean) {
        updatePersistentState { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.id == dietId) {
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
        updatePersistentState { state ->
            val updatedDiets = state.diets.map { diet ->
                if (diet.id == dietId) {
                    // Se questa dieta era già preferita, la togliamo. 
                    // Altrimenti la impostiamo come preferita (e togliamo il flag a tutte le altre)
                    diet.copy(isFavorite = !diet.isFavorite)
                } else {
                    // Togliamo il flag di preferita a tutte le altre diete
                    diet.copy(isFavorite = false)
                }
            }
            state.copy(diets = updatedDiets)
        }
    }
}
