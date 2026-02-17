package it.sapienza.smartpantry.data.openfoodfacts

import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName

data class OpenFoodFactsSearchResponse(
    @SerializedName("products")
    val products: List<OpenFoodFactsProductDto> = emptyList()
)

data class OpenFoodFactsProductResponse(
    @SerializedName("status")
    val status: Int?,
    @SerializedName("product")
    val product: OpenFoodFactsProductDto?
)

data class OpenFoodFactsProductDto(
    @SerializedName("product_name")
    val productName: String?,
    @SerializedName("product_name_it")
    val productNameIt: String?,
    @SerializedName("generic_name")
    val genericName: String?,
    @SerializedName("generic_name_it")
    val genericNameIt: String?,
    @SerializedName("brands")
    val brands: String?,
    @SerializedName("nutriments")
    val nutriments: JsonObject?
)
