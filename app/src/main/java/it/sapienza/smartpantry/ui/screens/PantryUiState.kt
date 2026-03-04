package it.sapienza.smartpantry.ui.screens

import it.sapienza.smartpantry.model.OpenFoodFactsProduct
import it.sapienza.smartpantry.model.PantryItem

data class PantryUiState(
    val searchQuery: String = "",
    val hasSearched: Boolean = false,
    val searchResults: List<OpenFoodFactsProduct> = emptyList(),
    val pantryItems: List<PantryItem> = emptyList(),
    val isSearching: Boolean = false,
    val isSaving: Boolean = false,
    val isScanning: Boolean = false,
    val isPantryEditMode: Boolean = false,
    val isEditorVisible: Boolean = false,
    val editorNameInput: String = "",
    val editorQuantityInput: String = "1",
    val editorKcalInput: String = "0",
    val editorCarbsInput: String = "0",
    val editorProtInput: String = "0",
    val editorFatInput: String = "0"
)
