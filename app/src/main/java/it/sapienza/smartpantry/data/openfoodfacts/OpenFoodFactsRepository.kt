package it.sapienza.smartpantry.data.openfoodfacts

import com.google.gson.JsonObject
import it.sapienza.smartpantry.ui.model.FoodSearchItemUi
import it.sapienza.smartpantry.ui.model.ProductLookupUi
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class OpenFoodFactsRepository {
    private val api: OpenFoodFactsApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(OpenFoodFactsApiService::class.java)
    }

    suspend fun searchFoods(query: String): List<FoodSearchItemUi> {
        val response = api.searchProducts(searchTerms = query)

        return response.products
            .mapNotNull { product ->
                val name = product.productName?.trim().orEmpty()
                if (name.isEmpty()) {
                    null
                } else {
                    FoodSearchItemUi(
                        name = name,
                        brand = product.brands?.trim()?.takeIf { it.isNotEmpty() },
                        caloriesPer100g = parseCaloriesPer100g(product.nutriments)
                    )
                }
            }
            .distinctBy { "${it.name.lowercase()}|${it.brand?.lowercase().orEmpty()}" }
    }

    suspend fun findProductByCode(code: String): ProductLookupUi? {
        val normalizedCode = code.trim()
        if (normalizedCode.isEmpty()) {
            return null
        }

        val response = api.getProductByCode(normalizedCode)
        if (response.status != PRODUCT_FOUND_STATUS) {
            return null
        }

        val product = response.product ?: return null
        val name = firstNotBlank(
            product.productName,
            product.productNameIt,
            product.genericNameIt,
            product.genericName
        ) ?: return null

        return ProductLookupUi(
            code = normalizedCode,
            name = name,
            brand = product.brands?.trim()?.takeIf { it.isNotEmpty() },
            caloriesPer100g = parseCaloriesPer100g(product.nutriments)
        )
    }

    private fun parseCaloriesPer100g(nutriments: JsonObject?): Double? {
        if (nutriments == null) return null

        return nutriments.readDouble("energy-kcal_100g")
            ?: nutriments.readDouble("energy-kcal")
    }

    private fun JsonObject.readDouble(key: String): Double? {
        val element = get(key) ?: return null
        if (element.isJsonNull) return null

        return if (element.isJsonPrimitive) {
            val primitive = element.asJsonPrimitive
            when {
                primitive.isNumber -> primitive.asDouble
                primitive.isString -> primitive.asString
                    .replace(',', '.')
                    .toDoubleOrNull()
                else -> null
            }
        } else {
            null
        }
    }

    private companion object {
        const val BASE_URL = "https://world.openfoodfacts.org/"
        const val PRODUCT_FOUND_STATUS = 1
    }

    private fun firstNotBlank(vararg candidates: String?): String? {
        return candidates
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
    }
}
