package it.sapienza.smartpantry.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// --- DATA MODELS ---

data class DailyMacroStats(
    val date: String,
    val kcal: Int,
    val proteins: Int,
    val carbs: Int,
    val fats: Int
)

data class WeeklyMacroStats(
    val week: String,
    val kcal: Int,
    val proteins: Int,
    val carbs: Int,
    val fats: Int
)

data class MonthlyMacroStats(
    val week: String,
    val kcal: Int,
    val proteins: Int,
    val carbs: Int,
    val fats: Int
)

// --- API REQUEST/RESPONSE MODELS (Placeholder) ---

data class StatsRequest(val uid: String, val startDate: String, val endDate: String)
data class StatsResponse(val status: String, val stats: List<DailyMacroStats>)

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

    fun loadStats(uid: String) {
        // TODO: Implement API call to fetch stats
        _uiState.value = _uiState.value.copy(isLoading = true)
        
        // Mock data for Weekly
        val mockWeeklyStats = listOf(
            DailyMacroStats("Mon", 1800, 120, 200, 60),
            DailyMacroStats("Tue", 2100, 130, 250, 70),
            DailyMacroStats("Wed", 1950, 115, 220, 65),
            DailyMacroStats("Thu", 2200, 140, 280, 75),
            DailyMacroStats("Fri", 2000, 125, 230, 68),
            DailyMacroStats("Sat", 2500, 150, 300, 90),
            DailyMacroStats("Sun", 2300, 145, 270, 85)
        )

        // Mock data for Monthly (4 weeks)
        val mockMonthlyStats = listOf(
            WeeklyMacroStats("Week 1", 2100, 140, 200, 70),
            WeeklyMacroStats("Week 2", 2300, 150, 230, 80),
            WeeklyMacroStats("Week 3", 2200, 130, 240, 60),
            WeeklyMacroStats("Week 4", 2350, 120, 235, 130)
        )

        val mockYearlyStats = listOf(
            MonthlyMacroStats("Jan", 1950, 130, 210, 65),
            MonthlyMacroStats("Feb", 2050, 135, 240, 70),
            MonthlyMacroStats("Mar", 1900, 125, 215, 62),
            MonthlyMacroStats("Apr", 2100, 140, 250, 75),
            MonthlyMacroStats("May", 2000, 128, 230, 68),
            MonthlyMacroStats("Jun", 2300, 145, 280, 85),
            MonthlyMacroStats("Jul", 2450, 150, 300, 90),
            MonthlyMacroStats("Aug", 2200, 138, 260, 78),
            MonthlyMacroStats("Sep", 2100, 132, 245, 72),
            MonthlyMacroStats("Oct", 2250, 142, 270, 80),
            MonthlyMacroStats("Nov", 1950, 120, 220, 66),
            MonthlyMacroStats("Dec", 2350, 148, 285, 88)
        )

        _uiState.value = StatsUiState(
            weeklyStats = mockWeeklyStats,
            monthlyStats = mockMonthlyStats,
            yearlyStats = mockYearlyStats,
            isLoading = false
        )
    }
}
