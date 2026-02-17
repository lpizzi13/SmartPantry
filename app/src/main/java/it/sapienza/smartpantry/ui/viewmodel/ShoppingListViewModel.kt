package it.sapienza.smartpantry.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.sapienza.smartpantry.data.openfoodfacts.OpenFoodFactsRepository
import it.sapienza.smartpantry.ui.model.ShoppingItem
import it.sapienza.smartpantry.ui.model.ShoppingSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EditingShoppingItemUiState(
    val sectionId: Int,
    val itemIndex: Int,
    val name: String,
    val grams: String
)

data class PendingProductLookupUiState(
    val code: String,
    val name: String,
    val brand: String?,
    val grams: String,
    val sectionId: Int
)

data class ShoppingListUiState(
    val sections: List<ShoppingSection> = listOf(
        ShoppingSection(id = 0, name = "Diario", items = emptyList())
    ),
    val nextSectionId: Int = 1,
    val newSectionName: String = "",
    val productCodeInput: String = "",
    val isProductLookupLoading: Boolean = false,
    val productLookupErrorMessage: String? = null,
    val pendingProduct: PendingProductLookupUiState? = null,
    val editingItem: EditingShoppingItemUiState? = null
)

class ShoppingListViewModel : ViewModel() {
    private val repository = OpenFoodFactsRepository()

    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()

    fun onNewSectionNameChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(newSectionName = value)
        }
    }

    fun addSection() {
        _uiState.update { currentState ->
            val name = currentState.newSectionName.trim()
            if (name.isEmpty()) {
                currentState
            } else {
                currentState.copy(
                    sections = currentState.sections + ShoppingSection(
                        id = currentState.nextSectionId,
                        name = name,
                        items = emptyList()
                    ),
                    nextSectionId = currentState.nextSectionId + 1,
                    newSectionName = ""
                )
            }
        }
    }

    fun addItem(sectionId: Int, name: String, grams: String) {
        val itemName = name.trim()
        if (itemName.isEmpty()) {
            return
        }

        val itemGrams = grams.trim()
        _uiState.update { currentState ->
            currentState.copy(
                sections = addOrMergeItem(
                    sections = currentState.sections,
                    sectionId = sectionId,
                    itemName = itemName,
                    itemGrams = itemGrams
                )
            )
        }
    }

    fun onProductCodeChanged(value: String) {
        _uiState.update { currentState ->
            currentState.copy(
                productCodeInput = value,
                productLookupErrorMessage = null
            )
        }
    }

    fun onProductCodeScanned(value: String) {
        val scannedCode = value.trim()
        if (scannedCode.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(productLookupErrorMessage = "Codice scansionato non valido.")
            }
            return
        }

        _uiState.update { currentState ->
            currentState.copy(
                productCodeInput = scannedCode,
                productLookupErrorMessage = null
            )
        }
        lookupProductByCode()
    }

    fun onProductLookupError(message: String) {
        _uiState.update { currentState ->
            currentState.copy(productLookupErrorMessage = message)
        }
    }

    fun lookupProductByCode() {
        val code = _uiState.value.productCodeInput.trim()
        if (code.isEmpty()) {
            _uiState.update { currentState ->
                currentState.copy(productLookupErrorMessage = "Inserisci un codice prodotto.")
            }
            return
        }

        viewModelScope.launch {
            _uiState.update { currentState ->
                currentState.copy(
                    isProductLookupLoading = true,
                    productLookupErrorMessage = null
                )
            }

            runCatching {
                repository.findProductByCode(code)
            }.onSuccess { product ->
                _uiState.update { currentState ->
                    if (product == null) {
                        currentState.copy(
                            isProductLookupLoading = false,
                            productLookupErrorMessage = "Nessun prodotto trovato per il codice inserito."
                        )
                    } else {
                        val defaultSectionId = currentState.sections.firstOrNull()?.id
                        if (defaultSectionId == null) {
                            currentState.copy(
                                isProductLookupLoading = false,
                                productLookupErrorMessage = "Aggiungi prima una sezione alla lista."
                            )
                        } else {
                            currentState.copy(
                                isProductLookupLoading = false,
                                productLookupErrorMessage = null,
                                pendingProduct = PendingProductLookupUiState(
                                    code = product.code,
                                    name = product.name,
                                    brand = product.brand,
                                    grams = "",
                                    sectionId = defaultSectionId
                                )
                            )
                        }
                    }
                }
            }.onFailure {
                _uiState.update { currentState ->
                    currentState.copy(
                        isProductLookupLoading = false,
                        productLookupErrorMessage = "Errore nel recupero prodotto. Riprova."
                    )
                }
            }
        }
    }

    fun onPendingProductNameChanged(value: String) {
        _uiState.update { currentState ->
            val pendingProduct = currentState.pendingProduct ?: return@update currentState
            currentState.copy(
                pendingProduct = pendingProduct.copy(name = value)
            )
        }
    }

    fun onPendingProductGramsChanged(value: String) {
        _uiState.update { currentState ->
            val pendingProduct = currentState.pendingProduct ?: return@update currentState
            currentState.copy(
                pendingProduct = pendingProduct.copy(grams = value)
            )
        }
    }

    fun onPendingProductSectionChanged(sectionId: Int) {
        _uiState.update { currentState ->
            val pendingProduct = currentState.pendingProduct ?: return@update currentState
            if (currentState.sections.none { it.id == sectionId }) {
                currentState
            } else {
                currentState.copy(
                    pendingProduct = pendingProduct.copy(sectionId = sectionId)
                )
            }
        }
    }

    fun confirmPendingProduct() {
        _uiState.update { currentState ->
            val pendingProduct = currentState.pendingProduct ?: return@update currentState
            val itemName = pendingProduct.name.trim()
            val itemGrams = pendingProduct.grams.trim()
            if (itemName.isEmpty()) {
                return@update currentState.copy(
                    productLookupErrorMessage = "Inserisci un nome articolo valido."
                )
            }

            val targetSectionId = currentState.sections
                .firstOrNull { it.id == pendingProduct.sectionId }
                ?.id
                ?: currentState.sections.firstOrNull()?.id

            if (targetSectionId == null) {
                currentState.copy(
                    pendingProduct = null,
                    productLookupErrorMessage = "Aggiungi prima una sezione alla lista."
                )
            } else {
                currentState.copy(
                    sections = addOrMergeItem(
                        sections = currentState.sections,
                        sectionId = targetSectionId,
                        itemName = itemName,
                        itemGrams = itemGrams
                    ),
                    productCodeInput = "",
                    pendingProduct = null,
                    productLookupErrorMessage = null
                )
            }
        }
    }

    fun dismissPendingProduct() {
        _uiState.update { currentState ->
            currentState.copy(pendingProduct = null)
        }
    }

    fun toggleItem(sectionId: Int, itemIndex: Int, isChecked: Boolean) {
        _uiState.update { currentState ->
            currentState.copy(
                sections = currentState.sections.map { section ->
                    if (section.id == sectionId && itemIndex in section.items.indices) {
                        section.copy(
                            items = section.items.mapIndexed { index, item ->
                                if (index == itemIndex) item.copy(isChecked = isChecked) else item
                            }
                        )
                    } else {
                        section
                    }
                }
            )
        }
    }

    fun startEditingItem(sectionId: Int, itemIndex: Int) {
        _uiState.update { currentState ->
            val section = currentState.sections.firstOrNull { it.id == sectionId } ?: return@update currentState
            val item = section.items.getOrNull(itemIndex) ?: return@update currentState

            currentState.copy(
                editingItem = EditingShoppingItemUiState(
                    sectionId = sectionId,
                    itemIndex = itemIndex,
                    name = item.name,
                    grams = item.grams
                )
            )
        }
    }

    fun onEditingItemNameChanged(value: String) {
        _uiState.update { currentState ->
            val editingItem = currentState.editingItem ?: return@update currentState
            currentState.copy(editingItem = editingItem.copy(name = value))
        }
    }

    fun onEditingItemGramsChanged(value: String) {
        _uiState.update { currentState ->
            val editingItem = currentState.editingItem ?: return@update currentState
            currentState.copy(editingItem = editingItem.copy(grams = value))
        }
    }

    fun saveEditedItem() {
        _uiState.update { currentState ->
            val editingItem = currentState.editingItem ?: return@update currentState
            val updatedName = editingItem.name.trim()
            val updatedGrams = editingItem.grams.trim()

            val updatedSections = currentState.sections.map { section ->
                if (section.id == editingItem.sectionId && editingItem.itemIndex in section.items.indices) {
                    if (updatedName.isNotEmpty()) {
                        section.copy(
                            items = section.items.mapIndexed { index, item ->
                                if (index == editingItem.itemIndex) {
                                    item.copy(name = updatedName, grams = updatedGrams)
                                } else {
                                    item
                                }
                            }
                        )
                    } else {
                        section
                    }
                } else {
                    section
                }
            }

            currentState.copy(
                sections = updatedSections,
                editingItem = null
            )
        }
    }

    fun dismissEditingItem() {
        _uiState.update { currentState ->
            currentState.copy(editingItem = null)
        }
    }

    fun deleteItem(sectionId: Int, itemIndex: Int) {
        _uiState.update { currentState ->
            currentState.copy(
                sections = currentState.sections.map { section ->
                    if (section.id == sectionId && itemIndex in section.items.indices) {
                        section.copy(
                            items = section.items.filterIndexed { index, _ -> index != itemIndex }
                        )
                    } else {
                        section
                    }
                }
            )
        }
    }

    fun deleteSection(sectionId: Int) {
        _uiState.update { currentState ->
            val updatedSections = currentState.sections.filterNot { it.id == sectionId }
            val updatedPendingProduct = currentState.pendingProduct?.let { pending ->
                when {
                    pending.sectionId != sectionId -> pending
                    updatedSections.isEmpty() -> null
                    else -> pending.copy(sectionId = updatedSections.first().id)
                }
            }
            currentState.copy(
                sections = updatedSections,
                pendingProduct = updatedPendingProduct,
                editingItem = currentState.editingItem?.takeUnless { it.sectionId == sectionId }
            )
        }
    }

    private fun addOrMergeItem(
        sections: List<ShoppingSection>,
        sectionId: Int,
        itemName: String,
        itemGrams: String
    ): List<ShoppingSection> {
        return sections.map { section ->
            if (section.id != sectionId) {
                section
            } else {
                val existingIndex = section.items.indexOfFirst { it.name.equals(itemName, ignoreCase = true) }
                if (existingIndex == -1) {
                    section.copy(
                        items = section.items + ShoppingItem(
                            name = itemName,
                            grams = itemGrams,
                            isChecked = false
                        )
                    )
                } else {
                    val currentItem = section.items[existingIndex]
                    val mergedItem = currentItem.copy(
                        name = itemName,
                        grams = mergeGrams(currentItem.grams, itemGrams),
                        isChecked = false
                    )
                    section.copy(
                        items = section.items.mapIndexed { index, item ->
                            if (index == existingIndex) mergedItem else item
                        }
                    )
                }
            }
        }
    }

    private fun mergeGrams(existingGrams: String, newGrams: String): String {
        val normalizedExisting = existingGrams.trim()
        val normalizedNew = newGrams.trim()
        if (normalizedNew.isEmpty()) {
            return normalizedExisting
        }
        if (normalizedExisting.isEmpty()) {
            return normalizedNew
        }

        val existingAsNumber = normalizedExisting.toIntOrNull()
        val newAsNumber = normalizedNew.toIntOrNull()
        return when {
            existingAsNumber != null && newAsNumber != null -> (existingAsNumber + newAsNumber).toString()
            normalizedExisting.equals(normalizedNew, ignoreCase = true) -> normalizedExisting
            else -> "$normalizedExisting + $normalizedNew"
        }
    }
}
