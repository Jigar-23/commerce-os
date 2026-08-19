package com.commerceos.android.config

import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchFilterGroup
import com.commerceos.android.model.SearchFilterOption
import com.commerceos.android.registry.CardVariant

/** Search History retention policy. */
data class SearchHistoryBehavior(
    val enabled: Boolean = true,
    val maxHistoryItems: Int = 10,
    val persistToDisk: Boolean = true,
    val allowIndividualClear: Boolean = true,
    val allowClearAll: Boolean = true
)

/** Search ranking weight configuration. */
data class RankingConfiguration(
    val textRelevanceWeight: Double = 0.4,
    val popularityWeight: Double = 0.2,
    val ratingWeight: Double = 0.2,
    val inventoryWeight: Double = 0.1,
    val distanceWeight: Double = 0.1
)

/** Client Search Action intent definition. */
data class SearchAction(
    val actionId: String,
    val label: String,
    val iconKey: String = "search",
    val deeplink: String? = null
)

/** Suggestion provider type enum. */
enum class SuggestionProvider {
    RECENT_SEARCHES,
    TRENDING_QUERIES,
    CATEGORY_TREE,
    BRAND_INDEX,
    PRODUCT_TITLES,
    RESTAURANT_NAMES,
    SERVICE_CATALOG,
    AI_SEMANTIC
}

/**
 * Universal Client Search Configuration governing search capabilities, filters, placeholders,
 * entity mappings, history rules, ranking weights, voice/image/barcode features, and suggestion providers.
 */
data class ClientSearchConfiguration(
    val enabledEntityTypes: Set<SearchEntityType> = SearchEntityType.entries.toSet(),
    val searchPlaceholder: String = "Search products, brands, categories...",
    val quickFilters: List<SearchFilterOption> = defaultQuickFilters(),
    val filterGroups: List<SearchFilterGroup> = emptyList(),
    val sortOptions: List<SortOption> = defaultSortOptions(),
    val resultCardMappings: Map<SearchEntityType, CardVariant> = defaultCardMappings(),
    val searchActions: List<SearchAction> = emptyList(),
    val suggestionProviders: List<SuggestionProvider> = listOf(
        SuggestionProvider.RECENT_SEARCHES,
        SuggestionProvider.TRENDING_QUERIES,
        SuggestionProvider.CATEGORY_TREE,
        SuggestionProvider.BRAND_INDEX,
        SuggestionProvider.PRODUCT_TITLES
    ),
    val searchHistoryBehavior: SearchHistoryBehavior = SearchHistoryBehavior(),
    val voiceCapabilityEnabled: Boolean = true,
    val imageCapabilityEnabled: Boolean = true,
    val barcodeCapabilityEnabled: Boolean = true,
    val rankingConfig: RankingConfiguration = RankingConfiguration(),
    val domainScope: String = "general",
    val customSearchConfigJson: String? = null
) {
    fun isEntityEnabled(type: SearchEntityType): Boolean = type in enabledEntityTypes

    fun cardVariantFor(type: SearchEntityType): CardVariant {
        return resultCardMappings[type] ?: when (type) {
            SearchEntityType.PRODUCT -> CardVariant.GENERIC_PRODUCT
            SearchEntityType.RESTAURANT -> CardVariant.RESTAURANT_CARD
            SearchEntityType.SERVICE -> CardVariant.SERVICE_CARD
            SearchEntityType.STORE -> CardVariant.STORE_CARD
            SearchEntityType.BRAND -> CardVariant.BRAND_CARD
            SearchEntityType.CATEGORY -> CardVariant.CATEGORY_CARD
            SearchEntityType.CAMPAIGN -> CardVariant.CAMPAIGN_CARD
            SearchEntityType.OFFER -> CardVariant.OFFER_CARD
            SearchEntityType.COLLECTION -> CardVariant.COLLECTION_CARD
            else -> CardVariant.GENERIC_PRODUCT
        }
    }
}

private fun defaultQuickFilters(): List<SearchFilterOption> = listOf(
    SearchFilterOption("express_only", "⚡ Express"),
    SearchFilterOption("in_stock", "In Stock"),
    SearchFilterOption("rating_4_plus", "★ 4.0+"),
    SearchFilterOption("deals_only", "🏷️ On Sale")
)

private fun defaultSortOptions(): List<SortOption> = listOf(
    SortOption("relevance", "Relevance", isDefault = true),
    SortOption("price_low_high", "Price: Low to High"),
    SortOption("price_high_low", "Price: High to Low"),
    SortOption("rating", "Top Rated")
)

private fun defaultCardMappings(): Map<SearchEntityType, CardVariant> = mapOf(
    SearchEntityType.PRODUCT to CardVariant.GENERIC_PRODUCT,
    SearchEntityType.RESTAURANT to CardVariant.RESTAURANT_CARD,
    SearchEntityType.SERVICE to CardVariant.SERVICE_CARD,
    SearchEntityType.STORE to CardVariant.STORE_CARD,
    SearchEntityType.BRAND to CardVariant.BRAND_CARD,
    SearchEntityType.CATEGORY to CardVariant.CATEGORY_CARD,
    SearchEntityType.CAMPAIGN to CardVariant.CAMPAIGN_CARD,
    SearchEntityType.OFFER to CardVariant.OFFER_CARD,
    SearchEntityType.COLLECTION to CardVariant.COLLECTION_CARD
)
