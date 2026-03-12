package it.sapienza.smartpantry.model

import com.google.gson.annotations.SerializedName

data class SearchFood(
    val productName: String,
    val brandName: String,
    val alreadyInPantryQuantity: Long? = null
)

fun OpenFoodFactsProduct.toSearchFood(
    pantryById: Map<String, PantryItem>
): SearchFood {
    val id = code?.trim().orEmpty()
    return SearchFood(
        productName = productName?.trim().orEmpty().ifBlank { "Unnamed product" },
        brandName = brands?.takeIf { it.isNotBlank() } ?: "Unknown brand",
        alreadyInPantryQuantity = pantryById[id]?.quantity
    )
}

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
    @SerializedName("brands") val brands: String? = null,
    @SerializedName(
        value = "kcal",
        alternate = ["energy-kcal_100g", "energy_kcal_100g", "energyKcal"]
    ) val kcal: Double? = null,
    @SerializedName(value = "prot", alternate = ["proteins_100g", "proteins"]) val prot: Double? = null,
    @SerializedName(value = "fat", alternate = ["fat_100g"]) val fat: Double? = null,
    @SerializedName(
        value = "carbs",
        alternate = ["carbohydrates_100g", "carbohydrates"]
    ) val carbs: Double? = null,
    @SerializedName(
        value = "packageWeightGrams",
        alternate = ["package_weight_grams", "product_quantity"]
    ) val packageWeightGrams: Double? = null,
    @SerializedName("nutriments") val nutriments: OpenFoodFactsNutriments? = null
)

data class OpenFoodFactsNutriments(
    @SerializedName(
        value = "kcal",
        alternate = ["energy-kcal_100g", "energy_kcal_100g", "energyKcal"]
    ) val kcal: Double? = null,
    @SerializedName(value = "prot", alternate = ["proteins_100g", "proteins"]) val prot: Double? = null,
    @SerializedName(value = "fat", alternate = ["fat_100g"]) val fat: Double? = null,
    @SerializedName(
        value = "carbs",
        alternate = ["carbohydrates_100g", "carbohydrates"]
    ) val carbs: Double? = null
)

fun OpenFoodFactsProduct.resolvedKcal(): Double? = firstNonNegative(kcal, nutriments?.kcal)
fun OpenFoodFactsProduct.resolvedProt(): Double? = firstNonNegative(prot, nutriments?.prot)
fun OpenFoodFactsProduct.resolvedFat(): Double? = firstNonNegative(fat, nutriments?.fat)
fun OpenFoodFactsProduct.resolvedCarbs(): Double? = firstNonNegative(carbs, nutriments?.carbs)
fun OpenFoodFactsProduct.resolvedPackageWeightGrams(): Double? = firstNonNegative(packageWeightGrams)

private fun firstNonNegative(vararg values: Double?): Double? {
    for (value in values) {
        if (value != null && value.isFinite() && value >= 0.0) return value
    }
    return null
}
