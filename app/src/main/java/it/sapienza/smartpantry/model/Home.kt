package it.sapienza.smartpantry.model

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.annotations.SerializedName
import it.sapienza.smartpantry.service.HomeAddNutrients
import it.sapienza.smartpantry.service.HomeDeleteRequest
import it.sapienza.smartpantry.service.PantryAddNutrients
import it.sapienza.smartpantry.service.PantryAddRequest
import it.sapienza.smartpantry.service.PantryGramsRequest
import it.sapienza.smartpantry.service.HomeUpdateRequest
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
    private companion object {
        private const val ENTRY_PREFIX_PANTRY_OFF = "hp_off::"
        private const val ENTRY_PREFIX_PANTRY_NAME = "hp_name::"
        private const val ENTRY_PREFIX_SEARCH_OFF = "he_off::"
        private const val ENTRY_PREFIX_SEARCH_NAME = "he_name::"
    }

    private data class PantryReference(
        val openFoodFactsId: String? = null,
        val productName: String? = null
    )

    private val api = RetrofitClient.instance
    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState = _uiState.asStateFlow()
    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val events = _events.asSharedFlow()
    private var latestLoadToken: Long = 0L

    fun loadDay(uid: String, dateKey: String, silent: Boolean = false) {
        if (uid.isBlank() || dateKey.isBlank()) return
        
        // Se stiamo già caricando esattamente questa data, non fare nulla
        if (_uiState.value.isLoading && _uiState.value.dateKey == dateKey) return

        val loadToken = ++latestLoadToken
        
        // Mostriamo il caricamento solo se non è silent O se la data è cambiata
        if (!silent || _uiState.value.dateKey != dateKey) {
            _uiState.update { it.copy(isLoading = true, dateKey = dateKey, errorMessage = null) }
        }

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
                val message = result.exceptionOrNull()?.localizedMessage
                    ?: "Unable to load home data."
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totals = HomeTotals(),
                        entriesCount = 0,
                        meals = emptyMap(),
                        errorMessage = message
                    )
                }
                emitEvent(message)
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
                val message = result.exceptionOrNull()?.localizedMessage
                    ?: "Unable to update item."
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = message
                    )
                }
                emitEvent(message)
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
                val message = result.exceptionOrNull()?.localizedMessage
                    ?: "Unable to delete item."
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        errorMessage = message
                    )
                }
                emitEvent(message)
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
        val normalizedMealType = mealType.trim().lowercase(Locale.ROOT)
        if (normalizedMealType !in setOf("breakfast", "lunch", "dinner", "snacks")) {
            return@withContext Result.failure(
                IllegalArgumentException("Invalid meal type.")
            )
        }

        val originalGrams = sanitizeMacro(originalEntry.grams)
        val ratio = if (originalGrams > 0.0) normalizedGrams / originalGrams else 0.0
        val normalizedSource = originalEntry.source.trim().lowercase(Locale.ROOT).let { sourceValue ->
            if (sourceValue == "openfoodfacts" || sourceValue == "manual") sourceValue else "manual"
        }
        val gramsDelta = normalizedGrams - originalGrams
        val pantryReference = resolvePantryReference(originalEntry)
        val shouldSyncPantry = pantryReference != null && abs(gramsDelta) > 1e-9

        if (shouldSyncPantry) {
            val pantryResult = applyPantryDelta(
                uid = uid,
                reference = pantryReference!!,
                gramsDelta = gramsDelta,
                fallbackProductName = originalEntry.productName,
                fallbackNutrients = originalEntry.nutrients
            )
            if (pantryResult.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException(
                        pantryResult.exceptionOrNull()?.localizedMessage
                            ?: "Unable to update pantry quantity."
                    )
                )
            }
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
                if (shouldSyncPantry) {
                    applyPantryDelta(
                        uid = uid,
                        reference = pantryReference!!,
                        gramsDelta = -gramsDelta,
                        fallbackProductName = originalEntry.productName,
                        fallbackNutrients = originalEntry.nutrients
                    )
                }
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to update item."))
                )
            }
        } catch (_: Exception) {
            if (shouldSyncPantry) {
                applyPantryDelta(
                    uid = uid,
                    reference = pantryReference!!,
                    gramsDelta = -gramsDelta,
                    fallbackProductName = originalEntry.productName,
                    fallbackNutrients = originalEntry.nutrients
                )
            }
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
        val gramsToRestore = sanitizeMacro(entry.grams)
        val pantryReference = resolvePantryReference(entry)
        val shouldSyncPantry = pantryReference != null && gramsToRestore > 0.0

        if (shouldSyncPantry) {
            val pantryResult = applyPantryDelta(
                uid = uid,
                reference = pantryReference!!,
                gramsDelta = -gramsToRestore,
                fallbackProductName = entry.productName,
                fallbackNutrients = entry.nutrients
            )
            if (pantryResult.isFailure) {
                return@withContext Result.failure(
                    IllegalStateException(
                        pantryResult.exceptionOrNull()?.localizedMessage
                            ?: "Unable to restore pantry quantity."
                    )
                )
            }
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
                if (shouldSyncPantry) {
                    applyPantryDelta(
                        uid = uid,
                        reference = pantryReference!!,
                        gramsDelta = gramsToRestore,
                        fallbackProductName = entry.productName,
                        fallbackNutrients = entry.nutrients
                    )
                }
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to delete item."))
                )
            }
        } catch (_: Exception) {
            if (shouldSyncPantry) {
                applyPantryDelta(
                    uid = uid,
                    reference = pantryReference!!,
                    gramsDelta = gramsToRestore,
                    fallbackProductName = entry.productName,
                    fallbackNutrients = entry.nutrients
                )
            }
            Result.failure(IllegalStateException("Unable to delete item."))
        }
    }

    private fun resolvePantryReference(entry: HomeEntry): PantryReference? {
        val entryId = entry.openFoodFactsId.trim()
        if (entryId.isBlank()) return null

        if (entryId.startsWith(ENTRY_PREFIX_SEARCH_OFF) || entryId.startsWith(ENTRY_PREFIX_SEARCH_NAME)) {
            return null
        }

        decodeEntryIdPayload(entryId, ENTRY_PREFIX_PANTRY_OFF)?.let { decodedId ->
            return PantryReference(openFoodFactsId = decodedId.takeIf { it.isNotBlank() })
        }
        decodeEntryIdPayload(entryId, ENTRY_PREFIX_PANTRY_NAME)?.let { decodedName ->
            return PantryReference(productName = decodedName.takeIf { it.isNotBlank() })
        }

        if (entryId.startsWith("home_")) {
            val fallbackName = entry.productName.trim().takeIf { it.isNotBlank() } ?: return null
            return PantryReference(productName = fallbackName)
        }

        return null
    }

    private fun decodeEntryIdPayload(entryId: String, prefix: String): String? {
        if (!entryId.startsWith(prefix)) return null
        val payload = entryId.removePrefix(prefix).substringBefore("::").trim()
        if (payload.isBlank()) return null
        return try {
            val decoded = Base64.decode(payload, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            String(decoded, Charsets.UTF_8).trim().takeIf { it.isNotBlank() }
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    private suspend fun applyPantryDelta(
        uid: String,
        reference: PantryReference,
        gramsDelta: Double,
        fallbackProductName: String,
        fallbackNutrients: HomeTotals
    ): Result<Unit> = withContext(Dispatchers.IO) {
        if (abs(gramsDelta) < 1e-9) {
            return@withContext Result.success(Unit)
        }
        if (uid.isBlank()) {
            return@withContext Result.failure(IllegalArgumentException("Missing user identifier."))
        }

        val lookupResult = resolvePantryItem(uid, reference)
        if (lookupResult.isFailure) {
            return@withContext Result.failure(
                IllegalStateException(
                    lookupResult.exceptionOrNull()?.localizedMessage ?: "Unable to load pantry."
                )
            )
        }

        var pantryItem = lookupResult.getOrNull()
        var createdForRestore = false
        if (pantryItem == null) {
            if (gramsDelta < 0.0) {
                val createResult = createPantryItemForRestore(
                    uid = uid,
                    reference = reference,
                    fallbackProductName = fallbackProductName,
                    fallbackNutrients = fallbackNutrients,
                    restoredGrams = sanitizeMacro(-gramsDelta)
                )
                if (createResult.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            createResult.exceptionOrNull()?.localizedMessage
                                ?: "Unable to restore pantry quantity."
                        )
                    )
                }
                createdForRestore = true
                val reloadResult = resolvePantryItem(uid, reference)
                if (reloadResult.isFailure) {
                    return@withContext Result.failure(
                        IllegalStateException(
                            reloadResult.exceptionOrNull()?.localizedMessage ?: "Unable to load pantry."
                        )
                    )
                }
                pantryItem = reloadResult.getOrNull()
            } else {
                return@withContext Result.failure(
                    IllegalStateException("Pantry item not found.")
                )
            }
        }
        if (pantryItem == null) {
            return@withContext Result.failure(
                IllegalStateException("Pantry item not found.")
            )
        }

        val currentGrams = sanitizeMacro(pantryItem.resolvedGrams() ?: 0.0)
        val targetGrams = if (createdForRestore && gramsDelta < 0.0) {
            sanitizeMacro(-gramsDelta)
        } else {
            currentGrams - gramsDelta
        }
        if (targetGrams < -1e-9) {
            return@withContext Result.failure(
                IllegalStateException("Not enough grams available in pantry.")
            )
        }

        val openFoodFactsId = pantryItem.openFoodFactsId.trim().takeIf { it.isNotBlank() }
        val productName = pantryItem.productName.trim().takeIf { it.isNotBlank() }
        if (openFoodFactsId.isNullOrBlank() && productName.isNullOrBlank()) {
            return@withContext Result.failure(
                IllegalStateException("Missing pantry item identifier.")
            )
        }

        try {
            val response = api.updateItemGrams(
                PantryGramsRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    productName = productName,
                    grams = sanitizeMacro(targetGrams.coerceAtLeast(0.0))
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to update pantry grams."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to update pantry grams."))
        }
    }

    private suspend fun resolvePantryItem(
        uid: String,
        reference: PantryReference
    ): Result<PantryItem?> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPantry(uid).execute()
            if (!response.isSuccessful) {
                return@withContext Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to load pantry."))
                )
            }

            val items = response.body()?.items.orEmpty()
            val resolvedItem = when {
                !reference.openFoodFactsId.isNullOrBlank() -> {
                    items.firstOrNull { candidate ->
                        candidate.openFoodFactsId.trim() == reference.openFoodFactsId
                    }
                }
                !reference.productName.isNullOrBlank() -> {
                    items.firstOrNull { candidate ->
                        candidate.productName.trim().equals(reference.productName, ignoreCase = true)
                    }
                }
                else -> null
            }

            Result.success(resolvedItem)
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to load pantry."))
        }
    }

    private suspend fun createPantryItemForRestore(
        uid: String,
        reference: PantryReference,
        fallbackProductName: String,
        fallbackNutrients: HomeTotals,
        restoredGrams: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val normalizedName = reference.productName
            ?.takeIf { it.isNotBlank() }
            ?: fallbackProductName.trim().ifBlank { "Unnamed product" }
        val packageWeightGrams = sanitizeMacro(restoredGrams).coerceAtLeast(1.0)

        try {
            val response = api.addToPantry(
                PantryAddRequest(
                    uid = uid,
                    openFoodFactsId = reference.openFoodFactsId?.takeIf { it.isNotBlank() },
                    productName = normalizedName,
                    quantity = 1,
                    nutrients = PantryAddNutrients(
                        kcal = sanitizeMacro(fallbackNutrients.kcal),
                        carbs = sanitizeMacro(fallbackNutrients.carbs),
                        fat = sanitizeMacro(fallbackNutrients.fat),
                        protein = sanitizeMacro(fallbackNutrients.protein)
                    ),
                    packageWeightGrams = packageWeightGrams
                )
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to recreate pantry item."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to recreate pantry item."))
        }
    }

    private fun sanitizeMacro(value: Double): Double =
        if (!value.isFinite() || value < 0.0) 0.0 else value

    private fun emitEvent(message: String) {
        _events.tryEmit(message)
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
}
