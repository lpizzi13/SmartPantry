package it.sapienza.smartpantry.data.openfoodfacts

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class OpenFoodFactsSearchResponse(
    @SerializedName("products")
    val products: List<OpenFoodFactsProductDto> = emptyList()
)

data class OpenFoodFactsProductDto(
    @SerializedName("product_name")
    val productName: String?,
    @SerializedName("brands")
    val brands: String?,
    @SerializedName("nutriments")
    val nutriments: JsonObject?
)
