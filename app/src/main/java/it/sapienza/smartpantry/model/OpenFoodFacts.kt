package it.sapienza.smartpantry.model

import com.google.gson.annotations.SerializedName

data class OpenFoodFactsSearchResponse(
    @SerializedName("query") val query: String? = null,
    @SerializedName("products") val products: List<OpenFoodFactsProduct> = emptyList(),
    @SerializedName("recommended") val recommended: List<OpenFoodFactsProduct> = emptyList()
)

data class OpenFoodFactsProductResponse(
    @SerializedName("status") val status: Int = 0,
    @SerializedName("product") val product: OpenFoodFactsProduct? = null
)

data class OpenFoodFactsProduct(
    @SerializedName(value = "code", alternate = ["openFoodFactsId"]) val code: String? = null,
    @SerializedName(value = "product_name", alternate = ["productName"]) val productName: String? = null,
    @SerializedName("brands") val brands: String? = null
)
