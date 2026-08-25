package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.Destination
import com.commerceos.android.model.PageResponse
import com.commerceos.android.model.PriceBand
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

sealed interface CatalogUiState {
    data object Loading : CatalogUiState
    data class Content(
        val title: String,
        val results: List<CommerceProduct>,
        val totalElements: Int
    ) : CatalogUiState
    data object Empty : CatalogUiState
    data class Error(val message: String) : CatalogUiState
}

/** Off-the-shelf price bands passed to the server as minPrice/maxPrice. */
val CATALOG_PRICE_BANDS = listOf(
    PriceBand(label = "All Prices"),
    PriceBand(min = 0.0, max = 100.0, label = "Under ₹100"),
    PriceBand(min = 100.0, max = 300.0, label = "₹100 – ₹300"),
    PriceBand(min = 300.0, max = null, label = "Above ₹300")
)

data class CatalogRequestKey(
    val query: String,
    val categoryId: String?,
    val brandId: String?,
    val storeId: String?,
    val collectionId: String?,
    val campaignId: String?,
    val offerId: String?,
    val vertical: String?,
    val minPrice: Double?,
    val maxPrice: Double?
)

/**
 * Unified Product Listing Page (PLP) for free-text search, category browsing and
 * full catalog. All filters (query/category/price band/destination) are applied
 * SERVER-SIDE; pagination walks limit/offset pages with a true hasMore flag.
 */
class CatalogViewModel(private val repository: AppRepository) : ViewModel() {

    var uiState by mutableStateOf<CatalogUiState>(CatalogUiState.Loading)
        private set

    var destinations by mutableStateOf<List<Destination>>(emptyList())
        private set

    var selectedDestinationId by mutableStateOf<String?>(null)
        private set

    var selectedPriceBand by mutableStateOf(CATALOG_PRICE_BANDS.first())
        private set

    var query by mutableStateOf("")
        private set

    var categoryName by mutableStateOf<String?>(null)
        private set

    var categoryId by mutableStateOf<String?>(null)
        private set

    var brandId by mutableStateOf<String?>(null)
        private set

    var storeId by mutableStateOf<String?>(null)
        private set

    var collectionId by mutableStateOf<String?>(null)
        private set

    var campaignId by mutableStateOf<String?>(null)
        private set

    var offerId by mutableStateOf<String?>(null)
        private set

    var vertical by mutableStateOf<String?>(null)
        private set

    var hasMore by mutableStateOf(false)
        private set

    var isLoadingMore by mutableStateOf(false)
        private set

    private var offset = 0
    private var activeGenerationId: String = ""
    private var loadJob: Job? = null

    companion object {
        private const val PAGE_SIZE = 20
        private const val DEBOUNCE_MS = 350L
    }

    val currentRequestKey: CatalogRequestKey
        get() = CatalogRequestKey(
            query = query,
            categoryId = categoryId,
            brandId = brandId,
            storeId = storeId,
            collectionId = collectionId,
            campaignId = campaignId,
            offerId = offerId,
            vertical = vertical,
            minPrice = selectedPriceBand.min,
            maxPrice = selectedPriceBand.max
        )

    fun open(initial: CatalogQuery) {
        query = initial.query
        categoryName = initial.categoryName
        categoryId = initial.categoryId
        brandId = initial.brandId
        storeId = initial.storeId
        collectionId = initial.collectionId
        campaignId = initial.campaignId
        offerId = initial.offerId
        vertical = initial.vertical
        selectedPriceBand = initial.priceBand ?: CATALOG_PRICE_BANDS.first()
        selectedDestinationId = initial.destinationId
        reload()
        if (destinations.isEmpty()) loadDestinations()
    }

    fun setPriceBand(band: PriceBand) {
        if (band == selectedPriceBand) return
        selectedPriceBand = band
        reload()
    }

    fun setDestination(destinationId: String?) {
        if (destinationId == selectedDestinationId) return
        selectedDestinationId = destinationId
        reload()
    }

    /** Debounced live search: re-queries the server without touching filters. */
    fun onQueryChange(newQuery: String) {
        if (query == newQuery) return
        query = newQuery
        queryJob?.cancel()
        queryJob = viewModelScope.launch {
            delay(DEBOUNCE_MS)
            reload()
        }
    }

    fun submitQuery() {
        queryJob?.cancel()
        reload()
    }

    private var queryJob: Job? = null

    fun loadMore() {
        if (!hasMore || isLoadingMore || uiState !is CatalogUiState.Content) return
        val generationId = activeGenerationId
        loadJob?.cancel()
        loadJob = viewModelScope.launch {
            isLoadingMore = true
            when (val result = repository.queryCatalogProducts(query, categoryName ?: "", categoryId, brandId, storeId, collectionId, campaignId, offerId, vertical, selectedPriceBand, PAGE_SIZE, offset)) {
                is ApiResult.Success -> {
                    if (activeGenerationId == generationId) {
                        appendPage(result.data)
                    }
                }
                is ApiResult.Failure -> { /* keep current page; a load-more miss is non-fatal */ }
            }
            isLoadingMore = false
        }
    }

    private fun reload() {
        loadJob?.cancel()
        val generationId = java.util.UUID.randomUUID().toString()
        activeGenerationId = generationId
        loadJob = viewModelScope.launch {
            uiState = CatalogUiState.Loading
            when (val result = repository.queryCatalogProducts(query, categoryName ?: "", categoryId, brandId, storeId, collectionId, campaignId, offerId, vertical, selectedPriceBand, PAGE_SIZE, 0)) {
                is ApiResult.Success -> {
                    if (activeGenerationId == generationId) {
                        applyPage(result.data, replace = true)
                    }
                }
                is ApiResult.Failure -> {
                    if (activeGenerationId == generationId) {
                        uiState = CatalogUiState.Error(result.error.message)
                    }
                }
            }
        }
    }

    private fun appendPage(page: PageResponse<CommerceProduct>) {
        val current = uiState as? CatalogUiState.Content
        val merged = (current?.results ?: emptyList()) + page.content
        applyPage(page, replace = false, merged = merged)
    }

    private fun applyPage(page: PageResponse<CommerceProduct>, replace: Boolean, merged: List<CommerceProduct> = page.content) {
        offset = page.nextOffset ?: (offset + page.content.size)
        hasMore = page.hasMore
        val results = if (replace) page.content else merged
        uiState = when {
            results.isEmpty() -> CatalogUiState.Empty
            else -> CatalogUiState.Content(
                title = titleFor(),
                results = results,
                totalElements = page.totalElements
            )
        }
    }

    private fun titleFor(): String = when {
        !brandId.isNullOrBlank() -> "Brand: $brandId"
        !categoryName.isNullOrBlank() -> categoryName!!
        !categoryId.isNullOrBlank() -> categoryId!!
        !vertical.isNullOrBlank() && vertical != "general" -> "${vertical!!.replaceFirstChar { it.uppercase() }} Catalog"
        query.isNotBlank() -> "Results for '$query'"
        else -> "All Products"
    }

    private fun loadDestinations() {
        viewModelScope.launch {
            when (val result = repository.getDestinations()) {
                is ApiResult.Success -> {
                    destinations = result.data
                    if (selectedDestinationId == null) {
                        selectedDestinationId = result.data.firstOrNull()?.id
                    }
                }
                is ApiResult.Failure -> destinations = emptyList()
            }
        }
    }

    fun reset() {
        loadJob?.cancel()
        queryJob?.cancel()
        uiState = CatalogUiState.Loading
        destinations = emptyList()
        selectedDestinationId = null
        selectedPriceBand = CATALOG_PRICE_BANDS.first()
        query = ""
        categoryName = null
        categoryId = null
        brandId = null
        storeId = null
        collectionId = null
        campaignId = null
        offerId = null
        vertical = null
        hasMore = false
        isLoadingMore = false
        offset = 0
    }
}
