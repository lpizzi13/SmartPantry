package it.sapienza.smartpantry.model

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import it.sapienza.smartpantry.service.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response

data class ShoppingListItem(
    val name: String,
    val quantity: String,
    val isChecked: Boolean = false
)

data class ShoppingListUiState(
    val items: List<ShoppingListItem> = emptyList(),
    val isLoading: Boolean = false,
    val isGenerating: Boolean = false,
    val hasLoaded: Boolean = false,
    val error: String? = null
)

class ShoppingListViewModel : ViewModel() {
    private val _uiState = MutableStateFlow(ShoppingListUiState())
    val uiState: StateFlow<ShoppingListUiState> = _uiState.asStateFlow()
    private var currentUid: String? = null

    fun initialize(uid: String) {
        if (uid.isBlank() || uid == currentUid) return
        currentUid = uid
        loadList(silent = false)
    }

    fun loadList(silent: Boolean = false) {
        val uid = currentUid ?: return
        
        // Se stiamo già caricando, evitiamo di far ripartire la rotella o sovrapporre chiamate
        if (_uiState.value.isLoading) return

        // Se è silent e abbiamo già caricato almeno una volta, non mostriamo il loading overlay
        if (!silent || !_uiState.value.hasLoaded) {
            _uiState.update { it.copy(isLoading = true) }
        }
        RetrofitClient.instance.getShoppingList(GetShoppingListRequest(uid)).enqueue(object : Callback<GetShoppingListResponse> {
            override fun onResponse(call: Call<GetShoppingListResponse>, response: Response<GetShoppingListResponse>) {
                if (response.isSuccessful) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false, 
                            hasLoaded = true,
                            items = response.body()?.shoppingList ?: emptyList()
                        ) 
                    }
                } else {
                    _uiState.update { it.copy(isLoading = false, hasLoaded = true) }
                }
            }
            override fun onFailure(call: Call<GetShoppingListResponse>, t: Throwable) {
                _uiState.update { it.copy(isLoading = false, hasLoaded = true, error = t.message) }
            }
        })
    }

    fun addItem(name: String, quantity: String) {
        val newItem = ShoppingListItem(name, quantity)
        val newList = _uiState.value.items + newItem
        syncList(newList, itemToAdd = newItem, replace = false)
    }

    fun toggleItem(index: Int, isChecked: Boolean) {
        val newList = _uiState.value.items.toMutableList().apply {
            val item = this[index]
            this[index] = item.copy(isChecked = isChecked)
        }
        syncList(newList, replace = true)
    }

    fun deleteItem(index: Int) {
        val newList = _uiState.value.items.filterIndexed { i, _ -> i != index }
        syncList(newList, replace = true)
    }

    fun clearList() {
        syncList(emptyList(), replace = true)
    }

    fun generateList(dietId: String, onComplete: (Boolean) -> Unit = {}) {
        val uid = currentUid ?: return
        _uiState.update { it.copy(isGenerating = true) }
        val request = GenerateShoppingListRequest(uid, dietId)
        RetrofitClient.instance.generateShoppingList(request).enqueue(object : Callback<List<ShoppingListItem>> {
            override fun onResponse(call: Call<List<ShoppingListItem>>, response: Response<List<ShoppingListItem>>) {
                _uiState.update { it.copy(isGenerating = false) }
                if (response.isSuccessful) {
                    val generatedItems = response.body() ?: emptyList()
                    syncList(generatedItems, replace = true)
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
            override fun onFailure(call: Call<List<ShoppingListItem>>, t: Throwable) {
                _uiState.update { it.copy(isGenerating = false) }
                onComplete(false)
            }
        })
    }

    private fun syncList(newItems: List<ShoppingListItem>, itemToAdd: ShoppingListItem? = null, replace: Boolean = true) {
        val uid = currentUid ?: return
        // Update local state immediately for responsiveness
        _uiState.update { it.copy(items = newItems) }

        val request = if (itemToAdd != null && !replace) {
            UpdateShoppingListRequest(uid, item = itemToAdd, replace = false)
        } else {
            UpdateShoppingListRequest(uid, shoppingList = newItems, replace = true)
        }

        RetrofitClient.instance.updateShoppingList(request)
            .enqueue(object : Callback<UpdateShoppingListResponse> {
                override fun onResponse(call: Call<UpdateShoppingListResponse>, response: Response<UpdateShoppingListResponse>) {
                    if (!response.isSuccessful) {
                        Log.e("SHOP_SYNC", "Failed to sync shopping list")
                    }
                }
                override fun onFailure(call: Call<UpdateShoppingListResponse>, t: Throwable) {
                    Log.e("SHOP_SYNC", "Failure syncing list: ${t.message}")
                }
            })
    }
}
