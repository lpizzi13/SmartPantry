package it.sapienza.smartpantry.model

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class DietSection(val label: String) {
    WEEKLY_DIET_PLAN("Weekly Diet Plan"),
    NEW_DIET("New Diet")
}

data class DietUiState(
    val sections: List<DietSection> = listOf(
        DietSection.WEEKLY_DIET_PLAN,
        DietSection.NEW_DIET
    ),
    val selectedSection: DietSection = DietSection.WEEKLY_DIET_PLAN,
    val daysOfWeek: List<String> = listOf(
        "Monday",
        "Tuesday",
        "Wednesday",
        "Thursday",
        "Friday",
        "Saturday",
        "Sunday"
    ),
    val expandedDayIndex: Int? = null
)

class DietViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(DietUiState())
    val uiState: StateFlow<DietUiState> = _uiState.asStateFlow()

    fun onSectionSelected(section: DietSection) {
        _uiState.update { state ->
            state.copy(
                selectedSection = section,
                expandedDayIndex = null
            )
        }
    }

    fun onDayClicked(dayIndex: Int) {
        _uiState.update { state ->
            state.copy(
                expandedDayIndex = if (state.expandedDayIndex == dayIndex) null else dayIndex
            )
        }
    }
}

