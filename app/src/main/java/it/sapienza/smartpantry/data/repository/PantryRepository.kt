package it.sapienza.smartpantry.data.repository

import it.sapienza.smartpantry.model.OpenFoodFactsProduct
import it.sapienza.smartpantry.model.OpenFoodFactsProductResponse
import it.sapienza.smartpantry.model.PantryItem
import it.sapienza.smartpantry.model.resolvedCarbs
import it.sapienza.smartpantry.model.resolvedFat
import it.sapienza.smartpantry.model.resolvedKcal
import it.sapienza.smartpantry.model.resolvedProt
import it.sapienza.smartpantry.service.PantryAddRequest
import it.sapienza.smartpantry.service.PantryItemUpdateRequest
import it.sapienza.smartpantry.service.PantryQuantityRequest
import it.sapienza.smartpantry.service.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import retrofit2.Response

class PantryRepository {
    private val api = RetrofitClient.instance

    data class BarcodeResolution(
        val openFoodFactsId: String? = null,
        val productName: String? = null,
        val kcal: Double? = null,
        val prot: Double? = null,
        val fat: Double? = null,
        val carbs: Double? = null,
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean
            get() = errorMessage == null && !openFoodFactsId.isNullOrBlank()
    }

    suspend fun searchProducts(
        query: String,
        similar: Boolean = true,
        limit: Int = 15,
        lang: String = "en"
    ): List<OpenFoodFactsProduct> = withContext(Dispatchers.IO) {
        try {
            val response = api.searchProducts(
                query = query,
                similar = similar,
                limit = limit,
                lang = lang
            ).execute()
            if (response.isSuccessful) {
                val body = response.body()
                val ranked = if (similar && !body?.recommended.isNullOrEmpty()) {
                    body?.recommended.orEmpty()
                } else {
                    body?.products.orEmpty()
                }

                ranked
                    .filter { !it.code.isNullOrBlank() }
                    .distinctBy { it.code }
                    .take(limit)
            } else {
                emptyList()
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    suspend fun resolveBarcode(barcode: String): BarcodeResolution = withContext(Dispatchers.IO) {
        try {
            val response = api.getProductByBarcode(barcode).execute()
            if (response.isSuccessful) {
                return@withContext parseBarcodeSuccess(response.body(), barcode)
            }

            return@withContext when {
                response.code() == 404 -> BarcodeResolution(
                    errorMessage = "Product not found on OpenFoodFacts."
                )
                response.code() == 502 || response.code() >= 500 -> BarcodeResolution(
                    errorMessage = "OpenFoodFacts is temporarily unavailable."
                )
                else -> BarcodeResolution(
                    errorMessage = parseBackendError(response, "Barcode lookup failed.")
                )
            }
        } catch (_: Exception) {
            BarcodeResolution(errorMessage = "Barcode lookup failed.")
        }
    }

    suspend fun getPantry(uid: String): Result<List<PantryItem>> = withContext(Dispatchers.IO) {
        try {
            val response = api.getPantry(uid).execute()
            if (response.isSuccessful) {
                Result.success(response.body()?.items.orEmpty())
            } else {
                Result.failure(
                    IllegalStateException(
                        parseBackendError(response, "Unable to load pantry.")
                    )
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to load pantry."))
        }
    }

    suspend fun addItem(
        uid: String,
        openFoodFactsId: String?,
        productName: String,
        quantity: Long,
        kcal: Double,
        prot: Double,
        fat: Double,
        carbs: Double
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val quantityValue = quantity.toIntOrNullChecked()
            ?: return@withContext Result.failure(
                IllegalArgumentException("Quantity must be greater than 0.")
            )

        try {
            val response = api.addToPantry(
                PantryAddRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId?.takeIf { it.isNotBlank() },
                    productName = productName,
                    quantity = quantityValue,
                    kcal = sanitizeMacro(kcal),
                    prot = sanitizeMacro(prot),
                    fat = sanitizeMacro(fat),
                    carbs = sanitizeMacro(carbs)
                )
            ).execute()

            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to add item."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to add item."))
        }
    }

    suspend fun updateQuantity(
        uid: String,
        openFoodFactsId: String,
        quantity: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val quantityValue = quantity.toIntOrNullChecked(allowZero = true)
            ?: return@withContext Result.failure(
                IllegalArgumentException("Quantity must be 0 or greater.")
            )

        try {
            val response = api.updateItemQuantity(
                PantryQuantityRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    quantity = quantityValue
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Unable to update quantity."))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Unable to update quantity."))
        }
    }

    suspend fun updateItem(
        uid: String,
        openFoodFactsId: String,
        productName: String? = null,
        quantity: Long? = null,
        kcal: Double? = null,
        prot: Double? = null,
        fat: Double? = null,
        carbs: Double? = null
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val quantityValue = when (quantity) {
            null -> null
            else -> quantity.toIntOrNullChecked()
        }
        if (quantity != null && quantityValue == null) {
            return@withContext Result.failure(
                IllegalArgumentException("Quantity must be greater than 0.")
            )
        }

        try {
            val response = api.updatePantryItem(
                PantryItemUpdateRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    productName = productName,
                    quantity = quantityValue,
                    kcal = kcal?.let(::sanitizeMacro),
                    prot = prot?.let(::sanitizeMacro),
                    fat = fat?.let(::sanitizeMacro),
                    carbs = carbs?.let(::sanitizeMacro)
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

    private fun parseBarcodeSuccess(
        body: OpenFoodFactsProductResponse?,
        fallbackBarcode: String
    ): BarcodeResolution {
        val product = body?.product
        val resolvedId = product?.code?.trim().orEmpty().ifBlank { fallbackBarcode }
        val resolvedName = product?.productName?.trim().orEmpty().ifBlank { "Unnamed product" }
        val resolvedKcal = product?.resolvedKcal()
        val resolvedProt = product?.resolvedProt()
        val resolvedFat = product?.resolvedFat()
        val resolvedCarbs = product?.resolvedCarbs()

        return if (body?.status == 1 && resolvedId.isNotBlank()) {
            BarcodeResolution(
                openFoodFactsId = resolvedId,
                productName = resolvedName,
                kcal = resolvedKcal,
                prot = resolvedProt,
                fat = resolvedFat,
                carbs = resolvedCarbs
            )
        } else {
            BarcodeResolution(errorMessage = "Product not found on OpenFoodFacts.")
        }
    }

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

    private fun sanitizeMacro(value: Double): Double {
        return if (value.isFinite() && value >= 0.0) value else 0.0
    }

    private fun Long.toIntOrNullChecked(allowZero: Boolean = false): Int? {
        if (this < Int.MIN_VALUE.toLong() || this > Int.MAX_VALUE.toLong()) return null
        val intValue = this.toInt()
        return if (allowZero) {
            intValue.takeIf { it >= 0 }
        } else {
            intValue.takeIf { it > 0 }
        }
    }
}
