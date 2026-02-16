package it.sapienza.smartpantry.ui.viewmodel

import androidx.lifecycle.ViewModel
import it.sapienza.smartpantry.ui.model.ShoppingItem
import it.sapienza.smartpantry.ui.model.ShoppingSection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class EditingShoppingItemUiState(
    val sectionId: Int,
    val itemIndex: Int,
    val name: String,
    val grams: String
)

data class ShoppingListUiState(
    val sections: List<ShoppingSection> = listOf(
        ShoppingSection(id = 0, name = "Diario", items = emptyList())
    ),
    val nextSectionId: Int = 1,
    val newSectionName: String = "",
    val editingItem: EditingShoppingItemUiState? = null
)

class ShoppingListViewModel : ViewModel() {
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
                sections = currentState.sections.map { section ->
                    if (section.id == sectionId) {
                        section.copy(
                            items = section.items + ShoppingItem(
                                name = itemName,
                                grams = itemGrams,
                                isChecked = false
                            )
                        )
                    } else {
                        section
                    }
                }
            )
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
            currentState.copy(
                sections = currentState.sections.filterNot { it.id == sectionId },
                editingItem = currentState.editingItem?.takeUnless { it.sectionId == sectionId }
            )
        }
    }
}
