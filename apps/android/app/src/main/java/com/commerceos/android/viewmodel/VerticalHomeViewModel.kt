package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.CategoryGroup
import com.commerceos.android.model.FulfillmentContext
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import com.commerceos.android.repository.FulfillmentRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.util.UUID

import com.commerceos.android.model.VerticalCategory

sealed interface VerticalHomeUiState {
    data object Loading : VerticalHomeUiState
    data class Content(
        val verticalId: String,
        val title: String,
        val subtitle: String,
        val ctaText: String? = null,
        val featuredProducts: List<CommerceProduct>,
        val categories: List<VerticalCategory>,
        val fulfillment: FulfillmentContext
    ) : VerticalHomeUiState
    data class Error(val message: String) : VerticalHomeUiState
}

data class VerticalHomeRequestKey(
    val customerId: String,
    val verticalId: String,
    val addressId: String?
)

/** Domain ViewModel owning state and data loading for VerticalHomeScreen. */
class VerticalHomeViewModel(
    private val repository: AppRepository,
    private val fulfillmentRepository: FulfillmentRepository
) : ViewModel() {

    var uiState by mutableStateOf<VerticalHomeUiState>(VerticalHomeUiState.Loading)
        private set

    var currentVerticalId by mutableStateOf<String?>(null)
        private set

    private var loadedKey: VerticalHomeRequestKey? = null
    private var activeRequestId: String = ""
    private var loadJob: kotlinx.coroutines.Job? = null

    fun loadVertical(verticalId: String, addressId: String? = null, customerId: String = "guest", forceRefresh: Boolean = false) {
        val requestKey = VerticalHomeRequestKey(customerId, verticalId, addressId)
        if (!forceRefresh && loadedKey == requestKey && uiState is VerticalHomeUiState.Content) {
            return
        }

        currentVerticalId = verticalId
        uiState = VerticalHomeUiState.Loading

        loadJob?.cancel()
        val requestId = UUID.randomUUID().toString()
        activeRequestId = requestId

        loadJob = viewModelScope.launch {
            val fulfillmentDeferred = async {
                addressId?.let { fulfillmentRepository.checkFulfillment(addressId = it, customerId = customerId) } ?: FulfillmentContext()
            }
            val feedDeferred = async {
                repository.getVerticalHomeFeed(verticalId, addressId)
            }

            val fulfillment = fulfillmentDeferred.await()
            val result = feedDeferred.await()

            // Latest-request-wins protection: discard if superseded by a newer request
            if (activeRequestId == requestId) {
                when (result) {
                    is ApiResult.Success -> {
                        val feed = result.data
                        loadedKey = requestKey
                        uiState = VerticalHomeUiState.Content(
                            verticalId = verticalId,
                            title = feed.title,
                            subtitle = feed.subtitle,
                            ctaText = feed.ctaText,
                            featuredProducts = feed.featuredProducts,
                            categories = feed.categories,
                            fulfillment = fulfillment
                        )
                    }
                    is ApiResult.Failure -> {
                        uiState = VerticalHomeUiState.Error(result.error.message)
                    }
                }
            }
        }
    }

    fun reset() {
        uiState = VerticalHomeUiState.Loading
        currentVerticalId = null
    }
}
