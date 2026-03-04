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

data class Diet(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val days: List<String> = emptyList(),
    val isEditable: Boolean = false,
    val expandedDayIndex: Int? = null
)

object DietDefaults {
    const val WEEKLY_DIET_NAME = "Weekly Diet Plan"
    const val NEW_DIET_NAME = "New Diet"
    val weekDays = listOf(
        "Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"
    )

    fun initialDiets(): List<Diet> = listOf(
        Diet(name = WEEKLY_DIET_NAME, days = weekDays),
        Diet(name = NEW_DIET_NAME, isEditable = true)
    )
}

data class DietUiState(
    val diets: List<Diet> = DietDefaults.initialDiets(),
    val selectedDietId: String? = null
) {
    val selectedDiet: Diet?
        get() = diets.find { it.id == selectedDietId }
}

data class DietRequest(
    @SerializedName("uid") val uid: String
)

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

data class SaveDietResponse(
    @SerializedName("status") val status: String = ""
)

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
                if (!response.isSuccessful) {
                    Log.e("DIET_LOAD", "HTTP ${response.code()}")
                    return
                }

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
        val hasWeekly = normalized.any { it.name == DietDefaults.WEEKLY_DIET_NAME }
        val hasPlaceholder = normalized.any { it.name == DietDefaults.NEW_DIET_NAME && it.isEditable }

        if (!hasWeekly) {
            normalized.add(0, Diet(name = DietDefaults.WEEKLY_DIET_NAME, days = DietDefaults.weekDays))
        }

        if (!hasPlaceholder) {
            normalized.add(Diet(name = DietDefaults.NEW_DIET_NAME, isEditable = true))
        }

        return normalized
    }

    private fun persistDietState() {
        val uid = currentUid ?: return
        val state = _uiState.value
        val request = SaveDietRequest(
            uid = uid,
            dietData = DietPayload(
                diets = state.diets,
                selectedDietId = state.selectedDietId
            )
        )

        RetrofitClient.instance.saveDiet(request).enqueue(object : Callback<SaveDietResponse> {
            override fun onResponse(call: Call<SaveDietResponse>, response: Response<SaveDietResponse>) {
                if (!response.isSuccessful) {
                    Log.e("DIET_SAVE", "HTTP ${response.code()}")
                }
            }

            override fun onFailure(call: Call<SaveDietResponse>, t: Throwable) {
                Log.e("DIET_SAVE", "Failure: ${t.message}")
            }
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
        updatePersistentState { state -> state.copy(selectedDietId = dietId) }
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
        val trimmedName = newName.trim()
        if (trimmedName.isBlank() || trimmedName == DietDefaults.WEEKLY_DIET_NAME) return

        updatePersistentState { state ->
            val dietToUpdate = state.diets.find { it.id == dietId }
            if (dietToUpdate?.name == DietDefaults.NEW_DIET_NAME && trimmedName != DietDefaults.NEW_DIET_NAME) {
                val newDiet = dietToUpdate.copy(name = trimmedName)
                val newDiets = state.diets.map { if (it.id == dietId) newDiet else it } +
                        Diet(name = DietDefaults.NEW_DIET_NAME, isEditable = true)
                state.copy(diets = newDiets, selectedDietId = newDiet.id)
            } else {
                val updatedDiets = state.diets.map {
                    if (it.id == dietId) it.copy(name = trimmedName) else it
                }
                state.copy(diets = updatedDiets)
            }
        }
    }

    fun addDayToDiet(dietId: String, dayName: String) {
        val trimmedDayName = dayName.trim()
        if (trimmedDayName.isBlank()) return

        updatePersistentState { state ->
            val dietToUpdate = state.diets.find { it.id == dietId }
            if (dietToUpdate?.name == DietDefaults.NEW_DIET_NAME) {
                val newDietName = "My Diet ${state.diets.count { it.name.startsWith("My Diet") } + 1}"
                val newDiet = dietToUpdate.copy(name = newDietName, days = dietToUpdate.days + trimmedDayName)
                val newDiets = state.diets.map { if (it.id == dietId) newDiet else it } +
                        Diet(name = DietDefaults.NEW_DIET_NAME, isEditable = true)
                state.copy(diets = newDiets, selectedDietId = newDiet.id)
            } else {
                val updatedDiets = state.diets.map {
                    if (it.id == dietId) it.copy(days = it.days + trimmedDayName) else it
                }
                state.copy(diets = updatedDiets)
            }
        }
    }
}
