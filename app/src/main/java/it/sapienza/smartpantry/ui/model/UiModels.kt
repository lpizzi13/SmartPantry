package it.sapienza.smartpantry.ui.model

data class MealUi(val name: String, val badge: String)

data class ShoppingItem(val name: String, val grams: String, val isChecked: Boolean)

data class ShoppingSection(val id: Int, val name: String, val items: List<ShoppingItem>)
