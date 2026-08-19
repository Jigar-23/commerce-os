package com.commerceos.android.model

/** Sub-category / destination chip filter model. */
data class Destination(
    val id: String = "",
    val name: String = "",
    val slug: String = ""
)

/** Explicit price band filter option for PLP refinement. */
data class PriceBand(
    val id: String = "",
    val min: Double? = null,
    val max: Double? = null,
    val label: String = ""
)

/** Structured catalog filters payload. */
data class CatalogFilters(
    val minPrice: Double? = null,
    val maxPrice: Double? = null,
    val inStockOnly: Boolean = false,
    val fastFulfillmentOnly: Boolean = false
)

/** Standard catalog sorting options. */
enum class CatalogSort {
    RELEVANCE,
    PRICE_LOW_TO_HIGH,
    PRICE_HIGH_TO_LOW,
    POPULARITY,
    NEWEST
}

/**
 * Immutable structured query for catalog listing (PLP).
 * All filter & destination parameters travel to the server — nothing is sliced client-side.
 */
data class CatalogQuery(
    val text: String? = null,
    val vertical: String? = null,
    val categoryId: String? = null,
    val brandId: String? = null,
    val storeId: String? = null,
    val collectionId: String? = null,
    val campaignId: String? = null,
    val offerId: String? = null,
    val locationAddressId: String? = null,
    val query: String = text ?: "",
    val categoryName: String? = null,
    val destinationId: String? = null,
    val priceBand: PriceBand? = null,
    val filters: CatalogFilters = CatalogFilters(),
    val sort: CatalogSort? = null
)