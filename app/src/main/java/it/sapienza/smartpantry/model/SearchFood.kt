package it.sapienza.smartpantry.model

data class SearchFood(
    val openFoodFactsId: String,
    val productName: String,
    val brandName: String,
    val alreadyInPantryQuantity: Long? = null
)

fun OpenFoodFactsProduct.toSearchFood(
    pantryById: Map<String, PantryItem>
): SearchFood {
    val id = code?.trim().orEmpty()
    return SearchFood(
        openFoodFactsId = id,
        productName = productName?.trim().orEmpty().ifBlank { "Unnamed product" },
        brandName = brands?.takeIf { it.isNotBlank() } ?: "Unknown brand",
        alreadyInPantryQuantity = pantryById[id]?.quantity
    )
}
