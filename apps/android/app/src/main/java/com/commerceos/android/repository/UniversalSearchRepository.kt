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
        val result = try {
            Api.run {
                NetworkClient.searchApi.search(
                    query = text,
                    vertical = query.vertical,
                    intent = query.intent,
                    addressId = query.locationAddressId
                )
            }
        } catch (e: Exception) {
            null
        }

        if (result is ApiResult.Success && result.data.allResults.isNotEmpty()) {
            return@withContext ApiResult.Success(result.data.allResults)
        }

        // Fallback to catalog medicines: match against therapeutic category, name, brand, etc.
        val medicinesResult = appRepository.getMedicines("")
        if (medicinesResult is ApiResult.Success) {
            val queryLower = text.lowercase()
            val queryTokens = queryLower.split(Regex("[^a-zA-Z0-9]+")).filter { it.isNotBlank() }

            val matched = medicinesResult.data.filter { med ->
                val name = (med.name ?: "").lowercase()
                val brand = (med.brandName ?: "").lowercase()
                val cat = (med.therapeuticCategory ?: "").lowercase()
                val sku = (med.sku ?: "").lowercase()
                val mfg = (med.manufacturer ?: "").lowercase()

                if (queryTokens.isEmpty()) {
                    true
                } else {
                    queryTokens.any { token ->
                        name.contains(token) || brand.contains(token) || cat.contains(token) || sku.contains(token) || mfg.contains(token)
                    } || (queryLower.contains("fever") && (cat.contains("fever") || name.contains("dolo") || name.contains("paracetamol") || name.contains("paracip")))
                      || (queryLower.contains("pain") && (cat.contains("pain") || name.contains("dolo") || name.contains("paracetamol") || name.contains("paracip")))
                      || (queryLower.contains("cold") && (cat.contains("cold") || cat.contains("cough") || name.contains("cold") || name.contains("cetrizet")))
                      || (queryLower.contains("cough") && (cat.contains("cough") || cat.contains("cold") || name.contains("relief") || name.contains("cetrizet")))
                      || (queryLower.contains("diabet") && (cat.contains("diabet") || name.contains("glycomet") || name.contains("insulin")))
                      || (queryLower.contains("antibiotic") && (cat.contains("antibiotic") || name.contains("amox") || name.contains("azithral")))
                      || (queryLower.contains("vitamin") && (cat.contains("vitamin") || name.contains("becosules") || name.contains("vitamin")))
                      || (queryLower.contains("acid") && (cat.contains("acid") || cat.contains("gas") || name.contains("digene")))
                      || (queryLower.contains("gas") && (cat.contains("gas") || cat.contains("acid") || name.contains("digene")))
                }
            }

            if (matched.isNotEmpty()) {
                val searchResults = matched.map { med ->
                    SearchResult(
                        entityId = med.id ?: med.sku ?: java.util.UUID.randomUUID().toString(),
                        entityType = SearchEntityType.PRODUCT,
                        title = med.name ?: "Medicine",
                        subtitle = med.brandName ?: med.therapeuticCategory ?: "Pharmacy",
                        vertical = "pharmacy",
                        price = med.discountedPrice.takeIf { it > 0 } ?: med.price,
                        rating = med.rating,
                        sku = med.sku,
                        isExpressEligible = med.expressDeliverySlaMins > 0,
                        image = if (!med.image.isNullOrBlank()) med.image else com.commerceos.android.model.MedicineImageResolver.resolve(med.name ?: "", med.therapeuticCategory ?: "")
                    )
                }
                return@withContext ApiResult.Success(searchResults)
            }
        }

        ApiResult.Success(emptyList())
    }

    open suspend fun autocomplete(query: String, vertical: String? = null): ApiResult<List<SearchSuggestion>> = withContext(ioDispatcher) {
        if (query.isBlank()) return@withContext ApiResult.Success(emptyList())
        Api.run { NetworkClient.searchApi.autocomplete(query, vertical) }
    }
}
