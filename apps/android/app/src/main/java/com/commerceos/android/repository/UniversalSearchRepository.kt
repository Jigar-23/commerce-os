package com.commerceos.android.repository

import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchResponse
import com.commerceos.android.model.SearchResult
import com.commerceos.android.model.SearchSuggestion
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.network.Api
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.NetworkClient
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Universal Search Repository.
 * Executes server-backed multi-domain search across Products, Stores, Restaurants,
 * Services, Brands, Categories, Collections, Campaigns, and Offers.
 */
open class UniversalSearchRepository(
    private val appRepository: AppRepository,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) {

    open suspend fun executeSearch(query: UniversalSearchQuery): ApiResult<List<SearchResult>> = withContext(ioDispatcher) {
        val text = query.text.trim()
        if (text.isBlank() && query.vertical.isNullOrBlank()) {
            return@withContext ApiResult.Success(emptyList())
        }

        // Real multi-domain search API call (P0-01, P0-02)
        val result = Api.run {
            NetworkClient.searchApi.search(
                query = text,
                vertical = query.vertical,
                intent = query.intent,
                addressId = query.locationAddressId
            )
        }

        when (result) {
            is ApiResult.Success -> ApiResult.Success(result.data.allResults)
            is ApiResult.Failure -> ApiResult.Failure(result.error)
        }
    }

    open suspend fun autocomplete(query: String, vertical: String? = null): ApiResult<List<SearchSuggestion>> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext ApiResult.Success(emptyList())
        Api.run { NetworkClient.searchApi.autocomplete(query, vertical) }
    }
}
