package it.sapienza.smartpantry.data.openfoodfacts

import com.google.gson.JsonObject
import it.sapienza.smartpantry.ui.model.FoodSearchItemUi
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
    }
}
