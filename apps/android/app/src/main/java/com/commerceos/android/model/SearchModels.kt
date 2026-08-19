package com.commerceos.android.model

/** Strongly-typed search entity domains supported by Commerce OS Universal Search pipeline. */
enum class SearchEntityType {
    PRODUCT,
    STORE,
    RESTAURANT,
    SERVICE,
    BRAND,
    CATEGORY,
    COLLECTION,
    CAMPAIGN,
    OFFER
}

/** Typed Search Intent categories. */
enum class SearchIntent {
    PRODUCT_SEARCH,
    CATEGORY_DISCOVERY,
    RESTAURANT_DISCOVERY,
    STORE_DISCOVERY,
    SERVICE_DISCOVERY,
    REORDER,
    NEAR_ME,
    DEAL_DISCOVERY
}

/** Strongly-typed search result entry returned by Universal Search pipeline. */
data class SearchResult(
    val entityType: SearchEntityType,
    val entityId: String,
    val title: String,
    val subtitle: String = "",
    val image: String? = null,
    val price: Double? = null,
    val vertical: String = "general",
    val relevance: Float? = null,
    val globalRank: Int? = null,
    val score: Float? = null,
    val section: String? = null,
    val destination: HomeDestination? = null,
    // Rich entity metadata
    val sku: String? = null,
    val merchantId: String? = null,
    val storeType: String? = null,
    val location: String? = null,
    val cuisine: String? = null,
    val rating: Double? = null,
    val etaLabel: String? = null,
    val providerId: String? = null,
    val availabilityStatus: String? = null,
    val isExpressEligible: Boolean? = null
) {
    /**
     * Centralized conversion of search results into typed application navigation intents.
     * Prevents inline string comparisons like `entityType == "restaurant"` in UI composables.
     */
    fun toDestination(): HomeDestination = destination ?: when (entityType) {
        SearchEntityType.PRODUCT -> HomeDestination.Product(entityId, vertical)
        SearchEntityType.STORE -> HomeDestination.Store(entityId)
        SearchEntityType.RESTAURANT -> HomeDestination.Restaurant(entityId)
        SearchEntityType.SERVICE -> HomeDestination.Service(entityId)
        SearchEntityType.BRAND -> HomeDestination.Brand(entityId, vertical)
        SearchEntityType.CATEGORY -> HomeDestination.Category(entityId, vertical)
        SearchEntityType.CAMPAIGN -> HomeDestination.Campaign(entityId)
        SearchEntityType.COLLECTION -> HomeDestination.Collection(entityId)
        SearchEntityType.OFFER -> HomeDestination.Offer(entityId)
    }
}

/** Server/domain-driven filter metadata for search queries. */
data class SearchFilterOption(val id: String, val label: String)

data class SearchFilterGroup(
    val id: String,
    val title: String,
    val options: List<SearchFilterOption>
)

data class SearchFilterConfig(
    val vertical: String? = null,
    val filterGroups: List<SearchFilterGroup> = emptyList()
) {
    companion object {
        fun defaultConfigForVertical(
            vertical: String?,
            categories: List<CategoryGroup> = emptyList()
        ): SearchFilterConfig {
            val base = listOf(
                SearchFilterGroup("fulfillment", "Fulfillment", listOf(SearchFilterOption("express_only", "⚡ Express Delivery"))),
                SearchFilterGroup("rating", "Rating", listOf(SearchFilterOption("4_plus", "★ 4.0 & Above"), SearchFilterOption("4.5_plus", "★ 4.5 & Above"))),
                SearchFilterGroup("price", "Price", listOf(SearchFilterOption("under_500", "Under ₹500"), SearchFilterOption("500_2000", "₹500 - ₹2,000"), SearchFilterOption("above_2000", "Above ₹2,000")))
            )
            val dynamicCategoryGroup = if (categories.isNotEmpty()) {
                listOf(
                    SearchFilterGroup(
                        id = "category",
                        title = "Category",
                        options = categories.map { SearchFilterOption(it.id, it.title) }
                    )
                )
            } else emptyList()

            return SearchFilterConfig(vertical, base + dynamicCategoryGroup)
        }
    }
}

/** Server-authored multi-domain search response. */
data class SearchResponse(
    val query: String,
    val totalCount: Int = 0,
    val orderedResults: List<SearchResult>? = null,
    val products: List<SearchResult> = emptyList(),
    val stores: List<SearchResult> = emptyList(),
    val restaurants: List<SearchResult> = emptyList(),
    val services: List<SearchResult> = emptyList(),
    val brands: List<SearchResult> = emptyList(),
    val categories: List<SearchResult> = emptyList(),
    val collections: List<SearchResult> = emptyList(),
    val campaigns: List<SearchResult> = emptyList(),
    val offers: List<SearchResult> = emptyList()
) {
    val allResults: List<SearchResult>
        get() = orderedResults ?: (products + stores + restaurants + services + brands + categories + collections + campaigns + offers)
            .sortedBy { it.globalRank ?: Int.MAX_VALUE }
}

/** Strongly-typed discovery suggestion. */
data class SearchSuggestion(
    val id: String,
    val type: SearchEntityType,
    val title: String,
    val subtitle: String = "",
    val vertical: String = "general",
    val image: String? = null
)
