package it.sapienza.smartpantry.ui

import it.sapienza.smartpantry.model.OpenFoodFactsProduct
import it.sapienza.smartpantry.model.PantryItem

data class PantryUiState(
    val searchQuery: String = "",
    val quantityInput: String = "1",
    val manualName: String = "",
    val showManualAdd: Boolean = false,
    val noApiResults: Boolean = false,
    val searchResults: List<OpenFoodFactsProduct> = emptyList(),
    val pantryItems: List<PantryItem> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val isScanning: Boolean = false
)
