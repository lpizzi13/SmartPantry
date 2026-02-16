package it.sapienza.smartpantry.ui.viewmodel

import androidx.lifecycle.ViewModel
import it.sapienza.smartpantry.ui.model.MealUi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Calendar
import kotlin.math.roundToInt

data class FoodJournalUiState(
    val meals: List<MealUi>,
    val selectedDateMillis: Long,
    val selectedWeekNumber: Int,
    val basalMetabolicRate: Int? = null,
    val sportCalories: Int = 0,
    val recommendedCalories: Int? = null
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

    fun onProfileUpdated(profileState: ProfileUiState) {
        val age = profileState.age.toIntOrNull()
        val heightCm = profileState.heightCm.toDoubleOrNull()
        val weightKg = profileState.weightKg.toDoubleOrNull()
        val sportCalories = profileState.sportCaloriesTotal

        val bmr = if (profileState.sex.isBlank() || age == null || heightCm == null || weightKg == null) {
            null
        } else {
            val sexConstant = when (profileState.sex) {
                "Maschio" -> 5.0
                "Femmina" -> -161.0
                else -> -78.0
            }
            (10.0 * weightKg + 6.25 * heightCm - 5.0 * age + sexConstant).roundToInt()
        }

        _uiState.update { currentState ->
            currentState.copy(
                basalMetabolicRate = bmr,
                sportCalories = sportCalories,
                recommendedCalories = bmr?.plus(300 + sportCalories)
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
