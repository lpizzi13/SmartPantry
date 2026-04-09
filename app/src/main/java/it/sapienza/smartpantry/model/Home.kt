package it.sapienza.smartpantry.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import it.sapienza.smartpantry.service.HomeAddNutrients
import it.sapienza.smartpantry.service.HomeDeleteRequest
import it.sapienza.smartpantry.service.HomeUpdateRequest
import it.sapienza.smartpantry.service.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

data class HomeDayResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("dateKey") val dateKey: String = "",
    @SerializedName("totals") val totals: HomeTotals = HomeTotals(),
    @SerializedName("entriesCount") val entriesCount: Int = 0,
    @SerializedName("meals") val meals: Map<String, List<HomeEntry>> = emptyMap()
)

data class HomeMutationResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("dateKey") val dateKey: String = "",
    @SerializedName("totals") val totals: HomeTotals = HomeTotals(),
    @SerializedName("entriesCount") val entriesCount: Int = 0
)

data class HomeTotals(
    @SerializedName("kcal") val kcal: Double = 0.0,
    @SerializedName("carbs") val carbs: Double = 0.0,
    @SerializedName(value = "protein", alternate = ["prot"]) val protein: Double = 0.0,
    @SerializedName("fat") val fat: Double = 0.0
)

data class HomeEntry(
    @SerializedName("openFoodFactsId") val openFoodFactsId: String = "",
    @SerializedName("mealType") val mealType: String = "",
    @SerializedName("source") val source: String = "",
    @SerializedName("productName") val productName: String = "",
    @SerializedName("grams") val grams: Double = 0.0,
    @SerializedName("nutrients") val nutrients: HomeTotals = HomeTotals()
)

data class HomeUiState(
    val isLoading: Boolean = false,
    val dateKey: String = "",
    val totals: HomeTotals = HomeTotals(),
    val entriesCount: Int = 0,
    val meals: Map<String, List<HomeEntry>> = emptyMap(),
    val errorMessage: String? = null
)

class HomeViewModel : ViewModel() {
    private val api = RetrofitClient.instance
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private var latestLoadToken: Long = 0L

    fun loadDay(uid: String, dateKey: String) {
        if (uid.isBlank() || dateKey.isBlank()) return
        val loadToken = ++latestLoadToken
        _uiState.update { it.copy(isLoading = true, dateKey = dateKey, errorMessage = null) }

        viewModelScope.launch {
            val result = fetchDay(uid, dateKey)
            if (loadToken != latestLoadToken) return@launch

            if (result.isSuccess) {
                val day = result.getOrNull() ?: HomeDayResponse(dateKey = dateKey)
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        dateKey = day.dateKey.ifBlank { dateKey },
                        totals = day.totals,
                        entriesCount = day.entriesCount,
                        meals = normalizeMeals(day.meals),
                        errorMessage = null
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totals = HomeTotals(),
                        entriesCount = 0,
                        meals = emptyMap(),
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to load home data."
                    )
                }
            }
        }
    }

    fun updateEntry(
        uid: String,
        dateKey: String,
        mealType: String,
        originalEntry: HomeEntry,
        updatedProductName: String,
        updatedGrams: Double
    ) {
        if (uid.isBlank() || dateKey.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = updateHomeEntry(
                uid = uid,
                dateKey = dateKey,
                mealType = mealType,
                originalEntry = originalEntry,
                updatedProductName = updatedProductName,
                updatedGrams = updatedGrams
            )

            if (result.isSuccess) {
                loadDay(uid, dateKey)
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to update item."
                    )
                }
            }
        }
    }

    fun deleteEntry(uid: String, dateKey: String, entry: HomeEntry) {
        if (uid.isBlank() || dateKey.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = deleteHomeEntry(uid = uid, dateKey = dateKey, entry = entry)
            if (result.isSuccess) {
                loadDay(uid, dateKey)
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = result.exceptionOrNull()?.localizedMessage
                            ?: "Unable to delete item."
                    )
                }
            }
        }
    }

    private fun normalizeMeals(rawMeals: Map<String, List<HomeEntry>>): Map<String, List<HomeEntry>> {
        return mapOf(
            "breakfast" to rawMeals["breakfast"].orEmpty(),
            "lunch" to rawMeals["lunch"].orEmpty(),
            "dinner" to rawMeals["dinner"].orEmpty(),
            "snacks" to rawMeals["snacks"].orEmpty()
        )
    }

    private suspend fun fetchDay(uid: String, dateKey: String): Result<HomeDayResponse> =
        withContext(Dispatchers.IO) {
            try {
                val response = api.getHomeDay(uid, dateKey).execute()
                if (response.isSuccessful) {
                    val body = response.body() ?: HomeDayResponse(dateKey = dateKey)
                    Result.success(body)
                } else if (response.code() == 404) {
                    Result.success(
                        HomeDayResponse(
                            status = "ok",
                            dateKey = dateKey,
                            totals = HomeTotals(),
                            entriesCount = 0,
                            meals = emptyMap()
                        )
                    )
                } else {
                    Result.failure(
                        IllegalStateException(parseBackendError(response, "Unable to load home data."))
                    )
                }
            } catch (_: Exception) {
                Result.failure(IllegalStateException("Unable to load home data."))
            }
        }

    private suspend fun updateHomeEntry(
        uid: String,
        dateKey: String,
        mealType: String,
        originalEntry: HomeEntry,
        updatedProductName: String,
        updatedGrams: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val itemId = originalEntry.openFoodFactsId.trim()
        if (itemId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Missing item identifier.")
            )
        }
        val normalizedGrams = sanitizeMacro(updatedGrams)
        if (normalizedGrams <= 0.0) {
            return@withContext Result.failure(
                IllegalArgumentException("Grams must be greater than 0.")
            )
        }
        val normalizedName = updatedProductName.trim().ifBlank { "Unnamed product" }
        val normalizedMealType = mealType.trim().lowercase()
        if (normalizedMealType !in setOf("breakfast", "lunch", "dinner", "snacks")) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid meal type.")
            )
        }

        val originalGrams = sanitizeMacro(originalEntry.grams)
        val ratio = if (originalGrams > 0.0) normalizedGrams / originalGrams else 0.0
        val normalizedSource = originalEntry.source.trim().lowercase().let { sourceValue ->
            if (sourceValue == "openfoodfacts" || sourceValue == "manual") sourceValue else "manual"
        }

        try {
            val response = api.updateHomeEntry(
                HomeUpdateRequest(
                    uid = uid,
                    dateKey = dateKey,
                    openFoodFactsId = itemId,
                    mealType = normalizedMealType,
                    source = normalizedSource,
                    productName = normalizedName,
                    grams = normalizedGrams,
                    nutrients = HomeAddNutrients(
                        kcal = sanitizeMacro(originalEntry.nutrients.kcal * ratio),
                        carbs = sanitizeMacro(originalEntry.nutrients.carbs * ratio),
                        protein = sanitizeMacro(originalEntry.nutrients.protein * ratio),
                        fat = sanitizeMacro(originalEntry.nutrients.fat * ratio)
                    )
                )
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to update item."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to update item."))
        }
    }

    private suspend fun deleteHomeEntry(
        uid: String,
        dateKey: String,
        entry: HomeEntry
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val itemId = entry.openFoodFactsId.trim()
        if (itemId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Missing item identifier.")
            )
        }

        try {
            val response = api.deleteHomeEntry(
                HomeDeleteRequest(
                    uid = uid,
                    dateKey = dateKey,
                    openFoodFactsId = itemId
                )
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to delete item."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to delete item."))
        }
    }

    private fun sanitizeMacro(value: Double): Double =
        if (!value.isFinite() || value < 0.0) 0.0 else value

    private fun parseBackendError(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return fallback
            val json = JSONObject(raw)
            val errorText = json.optString("error")
            val messageText = json.optString("message")
            when {
                errorText.isNotBlank() -> errorText
                messageText.isNotBlank() -> messageText
                else -> fallback
            }
        } catch (_: Exception) {
            fallback
        }
    }
}
