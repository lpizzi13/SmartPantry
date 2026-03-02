package it.sapienza.smartpantry.model

import com.google.gson.annotations.SerializedName

data class PantryResponse(
    @SerializedName("status") val status: String = "",
    @SerializedName("items") val items: List<PantryItem> = emptyList()
)

data class PantryItem(
    val openFoodFactsId: String = "",
    val productName: String = "",
    val quantity: Long = 0L
)

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
