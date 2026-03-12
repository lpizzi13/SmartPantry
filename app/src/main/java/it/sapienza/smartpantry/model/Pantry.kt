package it.sapienza.smartpantry.model

import com.google.gson.annotations.SerializedName

data class PantryResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("items") val items: List<PantryItem> = emptyList()
)

data class PantryItem(
    @SerializedName("openFoodFactsId") val openFoodFactsId: String = "",
    @SerializedName("productName") val productName: String = "",
    @SerializedName("quantity") val quantity: Long = 0L,
    @SerializedName("kcal") val kcal: Double? = null,
    @SerializedName(value = "prot", alternate = ["protein"]) val prot: Double? = null,
    @SerializedName("fat") val fat: Double? = null,
    @SerializedName("carbs") val carbs: Double? = null,
    @SerializedName("nutrients") val nutrients: PantryNutrients? = null,
    @SerializedName(
        value = "packageWeightGrams",
        alternate = ["package_weight_grams"]
    ) val packageWeightGrams: Double? = null
)

data class PantryNutrients(
    @SerializedName("kcal") val kcal: Double? = null,
    @SerializedName(value = "prot", alternate = ["protein"]) val prot: Double? = null,
    @SerializedName("fat") val fat: Double? = null,
    @SerializedName("carbs") val carbs: Double? = null
)

fun PantryItem.resolvedKcal(): Double? = firstNonNegative(kcal, nutrients?.kcal)
fun PantryItem.resolvedProt(): Double? = firstNonNegative(prot, nutrients?.prot)
fun PantryItem.resolvedFat(): Double? = firstNonNegative(fat, nutrients?.fat)
fun PantryItem.resolvedCarbs(): Double? = firstNonNegative(carbs, nutrients?.carbs)
fun PantryItem.resolvedPackageWeightGrams(): Double? = firstNonNegative(packageWeightGrams)

private fun firstNonNegative(vararg values: Double?): Double? {
    for (value in values) {
        if (value != null && value.isFinite() && value >= 0.0) return value
    }
    return null
}
