package it.sapienza.smartpantry.model

import com.google.gson.annotations.SerializedName

data class SearchFood(
    val productName: String,
    val brandName: String,
    val alreadyInPantryQuantity: Long? = null,
    val isCertified: Boolean = false
)

fun OpenFoodFactsProduct.toSearchFood(
    pantryById: Map<String, PantryItem>
): SearchFood {
    val id = resolvedOpenFoodFactsId().orEmpty()
    val isCertifiedByRule = certified == true

    return SearchFood(
        productName = productName?.trim().orEmpty().ifBlank { "Unnamed product" },
        brandName = brands?.takeIf { it.isNotBlank() } ?: "Unknown brand",
        alreadyInPantryQuantity = pantryById[id]?.quantity,
        isCertified = isCertifiedByRule
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
    @SerializedName(value = "openFoodFactsId", alternate = ["code"]) val openFoodFactsId: String? = null,
    @SerializedName(value = "productName", alternate = ["product_name"]) val productName: String? = null,
    @SerializedName("brands") val brands: String? = null,
    @SerializedName(
        value = "kcal",
        alternate = ["energy-kcal_100g", "energy_kcal_100g", "energyKcal"]
    ) val kcal: Double? = null,
    @SerializedName(
        value = "protein",
        alternate = ["prot", "proteins_100g", "proteins"]
    ) val protein: Double? = null,
    @SerializedName(value = "fat", alternate = ["fat_100g"]) val fat: Double? = null,
    @SerializedName(
        value = "carbs",
        alternate = ["carbohydrates_100g", "carbohydrates"]
    ) val carbs: Double? = null,
    @SerializedName(
        value = "packageWeightGrams",
        alternate = ["package_weight_grams", "product_quantity"]
    ) val packageWeightGrams: Double? = null,
    @SerializedName("nutrients") val nutrients: OpenFoodFactsNutrients? = null,
    @SerializedName("nutriments") val nutriments: OpenFoodFactsNutriments? = null,
    @SerializedName("certified") val certified: Boolean? = null,
    @SerializedName(value = "likelyOriginal", alternate = ["likely_original"]) val likelyOriginal: Boolean? = null,
    @SerializedName("certification") val certification: OpenFoodFactsCertification? = null,
    @SerializedName(value = "barcodeVerified", alternate = ["barcode_verified"]) val barcodeVerified: Boolean? = null,
    @SerializedName("imageUrl") val imageUrl: String? = null
)

data class OpenFoodFactsCertification(
    @SerializedName(value = "isCertified", alternate = ["is_certified"]) val isCertified: Boolean? = null,
    @SerializedName(value = "likelyOriginal", alternate = ["likely_original"]) val likelyOriginal: Boolean? = null,
    @SerializedName(value = "brandMatched", alternate = ["brand_matched"]) val brandMatched: Boolean? = null,
    @SerializedName(value = "ownerMatched", alternate = ["owner_matched"]) val ownerMatched: Boolean? = null,
    @SerializedName(
        value = "producerSourceMatched",
        alternate = ["producer_source_matched"]
    ) val producerSourceMatched: Boolean? = null,
    @SerializedName(
        value = "viaBarcodeLookup",
        alternate = ["via_barcode_lookup"]
    ) val viaBarcodeLookup: Boolean? = null,
    @SerializedName(
        value = "stateReviewedMatched",
        alternate = ["state_reviewed_matched"]
    ) val stateReviewedMatched: Boolean? = null,
    @SerializedName(
        value = "completenessMatched",
        alternate = ["completeness_matched"]
    ) val completenessMatched: Boolean? = null,
    @SerializedName("score") val score: Double? = null,
    @SerializedName("confidence") val confidence: String? = null,
    @SerializedName("reasons") val reasons: List<String> = emptyList()
)

data class OpenFoodFactsNutrients(
    @SerializedName("kcal") val kcal: Double? = null,
    @SerializedName("carbs") val carbs: Double? = null,
    @SerializedName("fat") val fat: Double? = null,
    @SerializedName(value = "protein", alternate = ["prot"]) val protein: Double? = null
)

data class OpenFoodFactsNutriments(
    @SerializedName(
        value = "kcal",
        alternate = ["energy-kcal_100g", "energy_kcal_100g", "energyKcal"]
    ) val kcal: Double? = null,
    @SerializedName(value = "protein", alternate = ["prot", "proteins_100g", "proteins"]) val protein: Double? = null,
    @SerializedName(value = "fat", alternate = ["fat_100g"]) val fat: Double? = null,
    @SerializedName(
        value = "carbs",
        alternate = ["carbohydrates_100g", "carbohydrates"]
    ) val carbs: Double? = null
)

fun OpenFoodFactsProduct.resolvedKcal(): Double? = firstNonNegative(kcal, nutrients?.kcal, nutriments?.kcal)
fun OpenFoodFactsProduct.resolvedProt(): Double? = firstNonNegative(protein, nutrients?.protein, nutriments?.protein)
fun OpenFoodFactsProduct.resolvedFat(): Double? = firstNonNegative(fat, nutrients?.fat, nutriments?.fat)
fun OpenFoodFactsProduct.resolvedCarbs(): Double? = firstNonNegative(carbs, nutrients?.carbs, nutriments?.carbs)
fun OpenFoodFactsProduct.resolvedPackageWeightGrams(): Double? = firstNonNegative(packageWeightGrams)
fun OpenFoodFactsProduct.resolvedOpenFoodFactsId(): String? = openFoodFactsId?.trim()?.takeIf { it.isNotEmpty() }

private fun firstNonNegative(vararg values: Double?): Double? {
    for (value in values) {
        if (value != null && value.isFinite() && value >= 0.0) return value
    }
    return null
}
