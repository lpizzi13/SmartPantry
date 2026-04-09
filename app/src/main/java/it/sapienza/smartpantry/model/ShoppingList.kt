package it.sapienza.smartpantry.model

data class ShoppingListItem(
    val name: String,
    val quantity: String,
    val isChecked: Boolean = false
)
