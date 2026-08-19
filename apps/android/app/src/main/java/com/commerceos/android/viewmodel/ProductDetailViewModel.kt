package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.MedicineDetail
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.launch

import com.commerceos.android.model.CommerceProduct

/** Product detail is fetched from the catalog by id — never carried stale through navigation. */
sealed interface ProductDetailUiState {
    data object Loading : ProductDetailUiState
    data class Content(val product: CommerceProduct) : ProductDetailUiState
    data class Error(val message: String) : ProductDetailUiState
}

class ProductDetailViewModel(private val repository: AppRepository) : ViewModel() {

    var uiState by mutableStateOf<ProductDetailUiState>(ProductDetailUiState.Loading)
        private set

    // The requested id keys the state: a slow response for an earlier tap must not
    // overwrite the state of a later tap (rapid Product A -> Product B).
    private var requestedProductId: String? = null

    fun load(productId: String, verticalId: String? = null) {
        requestedProductId = productId
        uiState = ProductDetailUiState.Loading
        viewModelScope.launch {
            when (val result = repository.getProductDetail(productId, verticalId)) {
                is ApiResult.Success -> {
                    if (requestedProductId == productId) {
                        uiState = ProductDetailUiState.Content(result.data)
                    }
                }
                is ApiResult.Failure -> {
                    if (requestedProductId == productId) {
                        uiState = ProductDetailUiState.Error(result.error.message)
                    }
                }
            }
        }
    }

    fun reset() {
        requestedProductId = null
        uiState = ProductDetailUiState.Loading
    }
}
