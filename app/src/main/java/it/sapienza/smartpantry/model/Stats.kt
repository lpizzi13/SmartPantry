package it.sapienza.smartpantry.model

import android.util.Log
import androidx.lifecycle.ViewModel
import com.google.gson.annotations.SerializedName
import it.sapienza.smartpantry.service.RetrofitClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

// --- DATA MODELS ---

data class DailyMacroStats(
    val date: String,
    val kcal: Float,
    val proteins: Float,
    val carbs: Float,
    val fats: Float
)

data class WeeklyMacroStats(
    @SerializedName("week", alternate = ["date", "label"])
    val week: String,
    val kcal: Float,
    val proteins: Float,
    val carbs: Float,
    val fats: Float
)

data class MonthlyMacroStats(
    @SerializedName("week", alternate = ["month", "date", "label"])
    val week: String,
    val kcal: Float,
    val proteins: Float,
    val carbs: Float,
    val fats: Float
)

// --- API REQUEST/RESPONSE MODELS ---

data class StatsRequest(
    val uid: String,
    val date: String
)

data class StatsResponse(
    val status: String,
    val weeklyStats: List<DailyMacroStats> = emptyList(),
    val monthlyStats: List<WeeklyMacroStats> = emptyList(),
    val yearlyStats: List<MonthlyMacroStats> = emptyList()
)

// --- VIEWMODEL STATE ---

data class StatsUiState(
    val weeklyStats: List<DailyMacroStats> = emptyList(),
    val monthlyStats: List<WeeklyMacroStats> = emptyList(),
    val yearlyStats: List<MonthlyMacroStats> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

// --- VIEWMODEL ---

class StatsViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(StatsUiState())
    val uiState: StateFlow<StatsUiState> = _uiState.asStateFlow()

    fun loadStats(uid: String, date: String) {
        if (uid.isBlank() || date.isBlank()) return

        Log.d("StatsViewModel", "Loading stats for uid: $uid, date: $date")
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        
        val request = StatsRequest(uid, date)
        RetrofitClient.instance.getStats(request).enqueue(object : Callback<StatsResponse> {
            override fun onResponse(call: Call<StatsResponse>, response: Response<StatsResponse>) {
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("StatsViewModel", "Response received: ${body?.status}")

                    if (body != null && (body.status == "ok" || body.status == "success")) {
                        _uiState.value = StatsUiState(
                            weeklyStats = body.weeklyStats,
                            monthlyStats = body.monthlyStats,
                            yearlyStats = body.yearlyStats,
                            isLoading = false
                        )
                    } else {
                        val error = "Error: ${body?.status ?: "Unknown status"}"
                        Log.e("StatsViewModel", error)
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = error
                        )
                    }
                } else {
                    val error = "Server error: ${response.code()} ${response.message()}"
                    Log.e("StatsViewModel", error)
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = error
                    )
                }
            }

            override fun onFailure(call: Call<StatsResponse>, t: Throwable) {
                Log.e("StatsViewModel", "Failed to load stats", t)
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = t.message ?: "Network error"
                )
            }
        })
    }
}
