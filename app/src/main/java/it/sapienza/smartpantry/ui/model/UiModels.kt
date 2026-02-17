package it.sapienza.smartpantry.ui.model

data class MealUi(val name: String, val badge: String)

data class MealFoodUi(
    val name: String,
    val caloriesPer100g: Double?,
    val grams: Int
)

data class FoodSearchItemUi(
    val name: String,
    val brand: String?,
    val caloriesPer100g: Double?
)

data class ProductLookupUi(
    val code: String,
    val name: String,
    val brand: String?,
    val caloriesPer100g: Double? = null
)

data class ShoppingItem(val name: String, val grams: String, val isChecked: Boolean)

data class ShoppingSection(val id: Int, val name: String, val items: List<ShoppingItem>)
