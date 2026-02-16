package it.sapienza.smartpantry.ui.viewmodel

import androidx.lifecycle.ViewModel
import it.sapienza.smartpantry.ui.model.MealUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar

data class FoodJournalUiState(
    val meals: List<MealUi>,
    val selectedDateMillis: Long,
    val selectedWeekNumber: Int
)

class FoodJournalViewModel : ViewModel() {
    private val initialDateMillis = Calendar.getInstance().let { calendar ->
        calendar.set(Calendar.HOUR_OF_DAY, 0)
        calendar.set(Calendar.MINUTE, 0)
        calendar.set(Calendar.SECOND, 0)
        calendar.set(Calendar.MILLISECOND, 0)
        calendar.timeInMillis
    }

    private val _uiState = MutableStateFlow(
        FoodJournalUiState(
            meals = defaultMeals(),
            selectedDateMillis = initialDateMillis,
            selectedWeekNumber = calculateIsoWeekNumber(initialDateMillis)
        )
    )
    val uiState: StateFlow<FoodJournalUiState> = _uiState.asStateFlow()

    fun onDateSelected(year: Int, month: Int, dayOfMonth: Int) {
        val selectedDateMillis = Calendar.getInstance().let { calendar ->
            calendar.set(Calendar.YEAR, year)
            calendar.set(Calendar.MONTH, month)
            calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
            calendar.set(Calendar.HOUR_OF_DAY, 0)
            calendar.set(Calendar.MINUTE, 0)
            calendar.set(Calendar.SECOND, 0)
            calendar.set(Calendar.MILLISECOND, 0)
            calendar.timeInMillis
        }

        _uiState.update { currentState ->
            currentState.copy(
                selectedDateMillis = selectedDateMillis,
                selectedWeekNumber = calculateIsoWeekNumber(selectedDateMillis)
            )
        }
    }

    private fun defaultMeals(): List<MealUi> {
        return listOf(
            MealUi(name = "Colazione", badge = "C"),
            MealUi(name = "Pranzo", badge = "P"),
            MealUi(name = "Cena", badge = "C"),
            MealUi(name = "Spuntini", badge = "S")
        )
    }

    private fun calculateIsoWeekNumber(timeInMillis: Long): Int {
        return Calendar.getInstance().apply {
            firstDayOfWeek = Calendar.MONDAY
            minimalDaysInFirstWeek = 4
            this.timeInMillis = timeInMillis
        }.get(Calendar.WEEK_OF_YEAR)
    }
}
