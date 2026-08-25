package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.launch

/**
 * Home feed is a SINGLE server-authored payload (GET /api/v1/catalog/home-feed).
 * Every section — hero campaign, verticals, buy-again (from real order history),
 * honest popular (from purchase frequency), top deals (real discount math),
 * categories, brands and the compact recommended feed — is composed server-side.
 *
 * This ViewModel maps the payload to [HomeSection]s and renders nothing by
 * itself; it never decides what is popular, featured or a top deal. The feed is
 * keyed on (customer, delivery address) so a location change invalidates stale
 * inventory/ETA/promotions instead of showing an outdated Home.
 */
class HomeViewModel(private val repository: AppRepository) : ViewModel() {

    var isLoading by mutableStateOf(false)
        private set

    /** Server-authored verticals (availability included) rendered in the rail. */
    var verticals by mutableStateOf<List<HomeVertical>>(emptyList())
        private set

    var sections by mutableStateOf<List<HomeSection>>(emptyList())
        private set

    /** Real search suggestions derived from the server feed (categories + brands). */
    var storefrontSuggestions by mutableStateOf<List<String>>(emptyList())
        private set

    /** Non-null when the server could not be reached — distinct from an empty feed. */
    var errorMessage by mutableStateOf<String?>(null)
        private set

    private var loadedCustomerId: String? = null
    private var loadedAddressId: String? = null
    private var activeHomeRequestId: String = ""
    var generatedAt by mutableStateOf<Long?>(null)
        private set

    fun loadHomeData(customerId: String, addressId: String? = null, forceRefresh: Boolean = false) {
        if (!forceRefresh && loadedCustomerId == customerId && loadedAddressId == addressId && sections.isNotEmpty() && !isLoading) {
            return
        }

        val requestId = java.util.UUID.randomUUID().toString()
        activeHomeRequestId = requestId
        android.util.Log.d("HomeViewModel", "loadHomeData called: customerId=$customerId, addressId=$addressId, requestId=$requestId")

        viewModelScope.launch {
            isLoading = true
            errorMessage = null
            when (val result = repository.getHomeFeed(customerId, addressId)) {
                is ApiResult.Success -> {
                    android.util.Log.d("HomeViewModel", "getHomeFeed SUCCESS: hero=${result.data.hero?.title}, sectionsCount=${result.data.sections?.size}, feedCount=${result.data.feed.size}")
                    // Latest-request-wins protection: discard if address changed or newer request initiated
                    if (activeHomeRequestId == requestId) {
                        sections = mapFeedToSections(result.data)
                        verticals = result.data.verticals
                        storefrontSuggestions = buildStorefrontSuggestions(result.data)
                        loadedCustomerId = customerId
                        loadedAddressId = addressId
                        generatedAt = result.data.generatedAt ?: System.currentTimeMillis()
                        android.util.Log.d("HomeViewModel", "Mapped into ${sections.size} UI sections successfully")
                    }
                }
                is ApiResult.Failure -> {
                    android.util.Log.e("HomeViewModel", "getHomeFeed FAILURE: ${result.error.message}, errorType=${result.error.javaClass.simpleName}")
                    if (activeHomeRequestId == requestId) {
                        // Non-destructive error handling: if content already exists, keep it & expose error message
                        if (sections.isEmpty()) {
                            sections = emptyList()
                            verticals = emptyList()
                            storefrontSuggestions = emptyList()
                        }
                        errorMessage = result.error.message
                    }
                }
            }
            if (activeHomeRequestId == requestId) {
                isLoading = false
            }
        }
    }

    private fun mapFeedToSections(feed: HomeFeedResponse): List<HomeSection> {
        return HomeFeedMapper.mapFeedToSections(feed)
    }

    private fun buildStorefrontSuggestions(feed: HomeFeedResponse): List<String> {
        val fromCategories = feed.categories.mapNotNull { it.title.takeIf { it.isNotBlank() } }
        val fromBrands = feed.brands.mapNotNull { it.name.takeIf { it.isNotBlank() } }
        return (fromCategories + fromBrands).distinct().take(12)
    }

    fun reset() {
        isLoading = false
        verticals = emptyList()
        sections = emptyList()
        storefrontSuggestions = emptyList()
        errorMessage = null
        loadedAddressId = null
    }
}