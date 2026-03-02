package it.sapienza.smartpantry.data.repository

import it.sapienza.smartpantry.model.OpenFoodFactsProduct
import it.sapienza.smartpantry.model.OpenFoodFactsProductResponse
import it.sapienza.smartpantry.model.PantryItem
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
        val errorMessage: String? = null
    ) {
        val isSuccess: Boolean
            get() = errorMessage == null && !openFoodFactsId.isNullOrBlank()
    }

    suspend fun searchProducts(
        query: String,
        similar: Boolean = true,
        limit: Int = 15,
        lang: String = "it"
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
        } catch (e: Exception) {
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
                    errorMessage = "Prodotto non trovato su OpenFoodFacts"
                )
                response.code() == 502 || response.code() >= 500 -> BarcodeResolution(
                    errorMessage = "Servizio OpenFoodFacts temporaneamente non disponibile"
                )
                else -> BarcodeResolution(
                    errorMessage = parseBackendError(response, "Prodotto non trovato su OpenFoodFacts")
                )
            }
        } catch (_: Exception) {
            BarcodeResolution(errorMessage = "Errore scansione")
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
                        parseBackendError(response, "Errore caricamento dispensa")
                    )
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Errore caricamento dispensa"))
        }
    }

    suspend fun addOrUpdateItem(
        uid: String,
        openFoodFactsId: String,
        productName: String,
        quantityToAdd: Long
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val quantityDelta = quantityToAdd.toIntOrNull()
        if (quantityDelta == null || quantityDelta <= 0) {
            return@withContext Result.failure(IllegalArgumentException("Quantita non valida"))
        }

        try {
            val response = api.addToPantry(
                it.sapienza.smartpantry.service.PantryRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    productName = productName,
                    quantityDelta = quantityDelta
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Operazione fallita"))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Operazione fallita"))
        }
    }

    suspend fun incrementItem(uid: String, openFoodFactsId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.addToPantry(
                it.sapienza.smartpantry.service.PantryRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId,
                    quantityDelta = 1
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Operazione fallita"))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Operazione fallita"))
        }
    }

    suspend fun decrementItem(uid: String, openFoodFactsId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.decrementItem(
                it.sapienza.smartpantry.service.PantryRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Operazione fallita"))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Operazione fallita"))
        }
    }

    suspend fun deleteItem(uid: String, openFoodFactsId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val response = api.deleteItem(
                it.sapienza.smartpantry.service.PantryRequest(
                    uid = uid,
                    openFoodFactsId = openFoodFactsId
                )
            ).execute()
            if (response.isSuccessful) {
                Result.success(Unit)
            } else {
                Result.failure(
                    IllegalStateException(parseBackendError(response, "Operazione fallita"))
                )
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Operazione fallita"))
        }
    }

    private fun parseBarcodeSuccess(
        body: OpenFoodFactsProductResponse?,
        fallbackBarcode: String
    ): BarcodeResolution {
        val product = body?.product
        val resolvedId = product?.code?.trim().orEmpty().ifBlank { fallbackBarcode }
        val resolvedName = product?.productName?.trim().orEmpty().ifBlank { "Prodotto sconosciuto" }

        return if (body?.status == 1 && resolvedId.isNotBlank()) {
            BarcodeResolution(openFoodFactsId = resolvedId, productName = resolvedName)
        } else {
            BarcodeResolution(errorMessage = "Prodotto non trovato su OpenFoodFacts")
        }
    }

    private fun parseBackendError(response: Response<*>, fallback: String): String {
        return try {
            val raw = response.errorBody()?.string().orEmpty()
            if (raw.isBlank()) return fallback
            JSONObject(raw).optString("error", fallback).ifBlank { fallback }
        } catch (_: Exception) {
            fallback
        }
    }

    private fun Long.toIntOrNull(): Int? {
        if (this < Int.MIN_VALUE.toLong() || this > Int.MAX_VALUE.toLong()) return null
        return this.toInt()
    }
}
