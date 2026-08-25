package com.commerceos.android.config

import com.commerceos.android.model.BrandItem
import com.commerceos.android.model.CategoryGroup
import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchFilterGroup

/** Taxonomy origin source. */
enum class TaxonomySource {
    REMOTE_CMS,
    CLIENT_CONFIG,
    INLINE_OVERRIDE
}

/** Subcategory structure within Taxonomy. */
data class SubcategoryGroup(
    val id: String,
    val parentCategoryId: String,
    val title: String,
    val subtitle: String = "",
    val imageUrl: String? = null,
    val itemCount: Int? = null
)

/** Dynamic Taxonomy Attribute definition. */
data class TaxonomyAttribute(
    val id: String,
    val key: String,
    val label: String,
    val values: List<String> = emptyList(),
    val isFilterable: Boolean = true,
    val isSearchable: Boolean = true
)

/** Taxonomy Collection metadata. */
data class TaxonomyCollection(
    val id: String,
    val title: String,
    val description: String? = null,
    val bannerUrl: String? = null,
    val categoryIds: List<String> = emptyList(),
    val isFeatured: Boolean = false
)

/** Taxonomy Campaign promotion metadata. */
data class TaxonomyCampaign(
    val id: String,
    val title: String,
    val subtitle: String = "",
    val badge: String = "",
    val ctaText: String = "Explore",
    val imageUrl: String? = null,
    val targetDestination: String? = null
)

/** Taxonomy Offer/Deal metadata. */
data class TaxonomyOffer(
    val id: String,
    val title: String,
    val code: String = "",
    val discountText: String = "",
    val minOrderAmount: Double? = null,
    val terms: String? = null
)

/** Taxonomy Sort Option. */
data class SortOption(
    val id: String,
    val label: String,
    val field: String = "relevance",
    val isAscending: Boolean = false,
    val isDefault: Boolean = false
)

/**
 * Universal Client Taxonomy Configuration governing category hierarchy, subcategories,
 * attributes, brands, collections, campaigns, offers, filters, sorts, and supported entities.
 */
data class ClientTaxonomyConfiguration(
    val verticalId: String = "general",
    val defaultCategories: List<CategoryGroup> = emptyList(),
    val subcategories: List<SubcategoryGroup> = emptyList(),
    val attributes: List<TaxonomyAttribute> = emptyList(),
    val attributeValues: Map<String, List<String>> = emptyMap(),
    val brands: List<BrandItem> = emptyList(),
    val collections: List<TaxonomyCollection> = emptyList(),
    val campaigns: List<TaxonomyCampaign> = emptyList(),
    val offers: List<TaxonomyOffer> = emptyList(),
    val filters: List<SearchFilterGroup> = emptyList(),
    val sorts: List<SortOption> = defaultSortOptions(),
    val supportedEntityTypes: Set<SearchEntityType> = SearchEntityType.entries.toSet(),
    val taxonomyVersion: String = "1.0",
    val taxonomySource: TaxonomySource = TaxonomySource.CLIENT_CONFIG,
    val isActive: Boolean = true
) {
    val categories: List<CategoryGroup> get() = defaultCategories

    fun subcategoriesFor(categoryId: String): List<SubcategoryGroup> {
        return subcategories.filter { it.parentCategoryId == categoryId }
    }

    fun isEntitySupported(type: SearchEntityType): Boolean = isActive && type in supportedEntityTypes
}

private fun defaultSortOptions(): List<SortOption> = listOf(
    SortOption("relevance", "Relevance", "relevance", false, true),
    SortOption("price_low_high", "Price: Low to High", "price", true),
    SortOption("price_high_low", "Price: High to Low", "price", false),
    SortOption("rating", "Customer Rating", "rating", false),
    SortOption("newest", "Newest Arrivals", "createdAt", false)
)
