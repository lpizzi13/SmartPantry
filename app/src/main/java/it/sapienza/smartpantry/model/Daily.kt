package it.sapienza.smartpantry.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.sapienza.smartpantry.service.HomeAddNutrients
import it.sapienza.smartpantry.service.HomeAddRequest
import it.sapienza.smartpantry.service.HomeDeleteRequest
import it.sapienza.smartpantry.service.PantryGramsRequest
import it.sapienza.smartpantry.service.RetrofitClient
import android.util.Base64
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response
import kotlin.math.abs

data class DailyUiState(
    val pantryItems: List<PantryItem> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null
)

class DailyViewModel : ViewModel() {
    companion object {
        private val HOME_MEAL_TYPES = setOf("breakfast", "lunch", "dinner", "snacks")
    }

    private val api = RetrofitClient.instance
    private val _uiState = MutableStateFlow(DailyUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    private val _saveCompleted = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    val saveCompleted = _saveCompleted.asSharedFlow()

    private var currentUid = ""
    private var activeDateKey = ""
    private var activeMealType = ""

    fun bindSession(uid: String, dateKey: String, mealType: String) {
        currentUid = uid.trim()
        activeDateKey = dateKey.trim()
        activeMealType = mealType.trim().lowercase(Locale.ROOT)

        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return
        }
        if (activeDateKey.isBlank()) {
            emitEvent("Missing date.")
            return
        }
        if (activeMealType !in HOME_MEAL_TYPES) {
            emitEvent("Invalid meal type.")
            return
        }

        refreshPantry()
    }

    fun onSearchQueryChange(value: String) {
        _uiState.update { it.copy(searchQuery = value) }
    }

    fun refreshPantry() {
        if (currentUid.isBlank()) return

        _uiState.update { it.copy(isLoading = true, errorMessage = null) }
        viewModelScope.launch {
            val result = loadPantry(currentUid)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        pantryItems = result.getOrNull().orEmpty().sortedBy { item ->
                            item.productName.trim().lowercase(Locale.ROOT)
                        },
                        errorMessage = null
                    )
                }
            } else {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Unable to load pantry."
                _uiState.update { it.copy(isLoading = false, pantryItems = emptyList(), errorMessage = message) }
                emitEvent("Unable to load pantry: $message")
            }
        }
    }

    fun addPantryItemToHome(item: PantryItem, rawGrams: String): Boolean {
        if (currentUid.isBlank()) {
            emitEvent("User not authenticated. Please log in again.")
            return false
        }
        if (activeDateKey.isBlank() || activeMealType !in HOME_MEAL_TYPES) {
            emitEvent("Missing day or meal.")
            return false
        }

        val grams = parsePositiveDecimalOrNull(rawGrams, "Grams") ?: return false
        val availableGrams = item.resolvedGrams()?.takeIf { it.isFinite() && it > 0.0 }
        if (availableGrams != null && grams - availableGrams > 1e-9) {
            emitEvent("Only ${formatDecimal(availableGrams)}g available in pantry.")
            return false
        }
        val source = if (item.openFoodFactsId.trim().isNotBlank()) "openfoodfacts" else "manual"
        val homeEntryId = buildHomeEntryId(
            originalOpenFoodFactsId = item.openFoodFactsId.trim().takeIf { it.isNotBlank() },
            productName = item.productName,
            dateKey = activeDateKey,
            mealType = activeMealType
        )

        _uiState.update { it.copy(isSaving = true, errorMessage = null) }
        viewModelScope.launch {
            val result = addHomeItem(
                uid = currentUid,
                dateKey = activeDateKey,
                mealType = activeMealType,
                openFoodFactsId = homeEntryId,
                source = source,
                productName = item.productName,
                grams = grams,
                kcal = item.resolvedKcal() ?: 0.0,
                prot = item.resolvedProt() ?: 0.0,
                fat = item.resolvedFat() ?: 0.0,
                carbs = item.resolvedCarbs() ?: 0.0
            )

            if (result.isSuccess) {
                val scaleResult = scalePantryAfterConsumption(
                    uid = currentUid,
                    item = item,
                    consumedGrams = grams
                )
                if (scaleResult.isSuccess) {
                    emitEvent("Item added to $activeMealType.")
                    _saveCompleted.tryEmit(Unit)
                } else {
                    val message = scaleResult.exceptionOrNull()?.localizedMessage
                        ?: "Unable to scale pantry."
                    val rollbackResult = rollbackHomeEntry(
                        uid = currentUid,
                        dateKey = activeDateKey,
                        homeEntryId = homeEntryId
                    )
                    if (rollbackResult.isSuccess) {
                        _uiState.update { it.copy(errorMessage = message) }
                        emitEvent("Unable to update pantry. Add operation has been reverted.")
                    } else {
                        _uiState.update { it.copy(errorMessage = message) }
                        emitEvent("Item added, but pantry was not updated: $message")
                    }
                }
            } else {
                val message = result.exceptionOrNull()?.localizedMessage ?: "Unable to add item."
                _uiState.update { it.copy(errorMessage = message) }
                emitEvent("Operation failed: $message")
            }

            _uiState.update { it.copy(isSaving = false) }
        }

        return true
    }

    private fun parsePositiveDecimalOrNull(value: String, fieldName: String): Double? {
        val parsed = value.trim().replace(',', '.').toDoubleOrNull()
        if (parsed == null || !parsed.isFinite() || parsed <= 0.0) {
            emitEvent("$fieldName must be a number greater than 0.")
            return null
        }
        return parsed
    }

    private suspend fun loadPantry(uid: String): Result<List<PantryItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPantry(uid).execute()
            if (response.isSuccessful) {
                Result.success(response.body()?.items.orEmpty())
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to load pantry."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to load pantry."))
        }
    }

    private suspend fun addHomeItem(
        uid: String,
        dateKey: String,
        mealType: String,
        openFoodFactsId: String?,
        source: String,
        productName: String,
        grams: Double,
        kcal: Double,
        prot: Double,
        fat: Double,
        carbs: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.addHomeEntry(
                HomeAddRequest(
                    uid = uid,
                    dateKey = dateKey,
                    openFoodFactsId = openFoodFactsId?.takeIf { it.isNotBlank() },
                    mealType = mealType,
                    source = source,
                    productName = productName.trim().ifBlank { "Unnamed product" },
                    grams = sanitizeMacro(grams),
                    nutrients = HomeAddNutrients(
                        kcal = sanitizeMacro(kcal),
                        carbs = sanitizeMacro(carbs),
                        protein = sanitizeMacro(prot),
                        fat = sanitizeMacro(fat)
                    )
                )
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to add item to home."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to add item to home."))
        }
    }

    private suspend fun scalePantryAfterConsumption(
        uid: String,
        item: PantryItem,
        consumedGrams: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val openFoodFactsId = item.openFoodFactsId.trim().takeIf { it.isNotBlank() }
        val productName = item.productName.trim().takeIf { it.isNotBlank() }
        if (openFoodFactsId.isNullOrBlank() && productName.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Missing pantry item identifier.")
            )
        }
        val currentGrams = item.resolvedGrams()
        if (currentGrams == null || !currentGrams.isFinite() || currentGrams < 0.0) {
            return@withContext Result.failure(
                IllegalStateException("Unable to determine available pantry grams.")
            )
        }
        val targetGrams = (currentGrams - consumedGrams).coerceAtLeast(0.0)

        try {
            val response = api.updateItemGrams(
                PantryGramsRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    productName = productName,
                    grams = sanitizeMacro(targetGrams)
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to scale pantry item."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to scale pantry item."))
        }
    }

    private suspend fun rollbackHomeEntry(
        uid: String,
        dateKey: String,
        homeEntryId: String
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (uid.isBlank() || dateKey.isBlank() || homeEntryId.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Missing rollback data.")
            )
        }
        try {
            val response = api.deleteHomeEntry(
                HomeDeleteRequest(
                    uid = uid,
                    dateKey = dateKey,
                    openFoodFactsId = homeEntryId
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to rollback home entry."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to rollback home entry."))
        }
    }

    private fun buildHomeEntryId(
        originalOpenFoodFactsId: String?,
        productName: String,
        dateKey: String,
        mealType: String
    ): String {
        val timestamp = System.currentTimeMillis()
        val encodedValue = encodeForEntryId(
            originalOpenFoodFactsId?.takeIf { it.isNotBlank() }
                ?: productName.trim().ifBlank { "manual" }
        )
        return if (!originalOpenFoodFactsId.isNullOrBlank()) {
            "hp_off::$encodedValue::$timestamp"
        } else {
            "hp_name::$encodedValue::$timestamp"
        }
    }

    private fun encodeForEntryId(value: String): String {
        return Base64.encodeToString(
            value.toByteArray(Charsets.UTF_8),
            Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP
        )
    }

    private fun parseBackendError(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return fallback
            val json = JSONObject(raw)
            val errorText = json.optString("error")
            val messageText = json.optString("message")
            val resolved = when {
                errorText.isNotBlank() -> errorText
                messageText.isNotBlank() -> messageText
                else -> fallback
            }
            toEnglishBackendMessage(resolved)
        } catch (_: Exception) {
            fallback
        }
    }

    private fun toEnglishBackendMessage(value: String): String {
        var message = value.trim()
        if (message.isBlank()) return "Unexpected backend error."

        message = message.replace(
            Regex("(?i)packageweightgrams\\s+deve\\s+essere\\s*>\\s*0"),
            "packageWeightGrams must be greater than 0."
        )
        message = message.replace(
            Regex("(?i)deve\\s+essere\\s*>\\s*0"),
            "must be greater than 0"
        )
        message = message.replace(
            Regex("(?i)deve\\s+essere\\s*>=\\s*0"),
            "must be greater than or equal to 0"
        )
        message = message.replace(
            Regex("(?i)deve\\s+essere\\s+maggiore\\s+di\\s+0"),
            "must be greater than 0"
        )
        message = message.replace(
            Regex("(?i)deve\\s+essere\\s+maggiore\\s+o\\s+uguale\\s+a\\s+0"),
            "must be greater than or equal to 0"
        )
        message = message.replace(
            Regex("(?i)deve\\s+essere"),
            "must be"
        )
        message = message.replace(
            Regex("(?i)non\\s+trovat[oa]"),
            "not found"
        )

        return message
    }

    private fun sanitizeMacro(value: Double): Double =
        if (value.isFinite() && value >= 0.0) value else 0.0

    private fun formatDecimal(value: Double): String {
        val safe = if (value.isFinite() && value >= 0.0) value else 0.0
        return if (abs(safe - safe.toLong().toDouble()) < 1e-9) {
            safe.toLong().toString()
        } else {
            String.format(Locale.US, "%.1f", safe).trimEnd('0').trimEnd('.')
        }
    }

    private fun emitEvent(message: String) {
        _events.tryEmit(message)
    }
}
