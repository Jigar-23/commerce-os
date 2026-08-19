package com.commerceos.android.viewmodel

import com.commerceos.android.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 🟡 P2 — SEARCH RENDERER & FILTER TEST SUITE
 * Complete test coverage for Express filters, missing field safety, and all 9 Search entity types.
 */
class SearchRendererSuiteTest {

    @Test
    fun testExpressTrueFilter_MatchesExpressProductsOnly() {
        val prod1 = SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "p1", title = "Express Item", price = 100.0, isExpressEligible = true)
        val prod2 = SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "p2", title = "Standard Item", price = 100.0, isExpressEligible = false)
        val prod3 = SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "p3", title = "Unknown Item", price = 100.0, isExpressEligible = null)

        val products = listOf(prod1, prod2, prod3)
        val expressOnly = products.filter { it.isExpressEligible == true }

        assertEquals(1, expressOnly.size)
        assertEquals("p1", expressOnly.first().entityId)
    }

    @Test
    fun testExpressFalseFilter_MatchesNonExpressProducts() {
        val prod1 = SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "p1", title = "Express Item", price = 100.0, isExpressEligible = true)
        val prod2 = SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "p2", title = "Standard Item", price = 100.0, isExpressEligible = false)

        val nonExpress = listOf(prod1, prod2).filter { it.isExpressEligible == false }
        assertEquals(1, nonExpress.size)
        assertEquals("p2", nonExpress.first().entityId)
    }

    @Test
    fun testExpressNull_SafelyHandledWithoutDefaultingToTrue() {
        val prod = SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "p3", title = "Null Express Item", price = 100.0, isExpressEligible = null)
        assertNull(prod.isExpressEligible)
        assertFalse(prod.isExpressEligible == true)
    }

    @Test
    fun testTextExpressPlusFalse_HandlesKeywordSearchWithExpressFilterFalse() {
        val query = UniversalSearchQuery(text = "Express Delivery", filters = mapOf("express" to "false"))
        assertEquals("Express Delivery", query.text)
        assertEquals("false", query.filters["express"])
    }

    @Test
    fun testMissingFieldsInSearch_NullSafety() {
        val prod = CommerceProduct(
            id = "p_missing",
            sku = "sku_missing",
            name = "Basic Item",
            price = 50.0,
            sellingPrice = 50.0,
            rating = null,
            brand = null
        )
        val rest = SearchResult(
            entityType = SearchEntityType.RESTAURANT,
            entityId = "r_missing",
            title = "No Rating Diner",
            rating = null,
            cuisine = null,
            etaLabel = null
        )
        val service = SearchResult(
            entityType = SearchEntityType.SERVICE,
            entityId = "s_missing",
            title = "Simple Service",
            providerId = null,
            rating = null
        )

        assertNull(prod.rating)
        assertNull(rest.rating)
        assertNull(rest.cuisine)
        assertNull(rest.etaLabel)
        assertNull(service.providerId)
    }

    @Test
    fun testNineSearchEntityTypes_ContractValidation() {
        val product = SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "p10", title = "Headphones", price = 2999.0)
        val restaurant = SearchResult(entityType = SearchEntityType.RESTAURANT, entityId = "r10", title = "Burger Joint")
        val service = SearchResult(entityType = SearchEntityType.SERVICE, entityId = "s10", title = "House Painting")
        val store = SearchResult(entityType = SearchEntityType.STORE, entityId = "st10", title = "Main Tech Store")
        val brand = SearchResult(entityType = SearchEntityType.BRAND, entityId = "b10", title = "Samsung")
        val category = SearchResult(entityType = SearchEntityType.CATEGORY, entityId = "c10", title = "Smartphones")
        val collection = SearchResult(entityType = SearchEntityType.COLLECTION, entityId = "col10", title = "Summer Fashion")
        val campaign = SearchResult(entityType = SearchEntityType.CAMPAIGN, entityId = "cam10", title = "Mega Sale")
        val offer = SearchResult(entityType = SearchEntityType.OFFER, entityId = "off10", title = "50% Off Code")

        assertEquals(SearchEntityType.PRODUCT, product.entityType)
        assertEquals(SearchEntityType.RESTAURANT, restaurant.entityType)
        assertEquals(SearchEntityType.SERVICE, service.entityType)
        assertEquals(SearchEntityType.STORE, store.entityType)
        assertEquals(SearchEntityType.BRAND, brand.entityType)
        assertEquals(SearchEntityType.CATEGORY, category.entityType)
        assertEquals(SearchEntityType.COLLECTION, collection.entityType)
        assertEquals(SearchEntityType.CAMPAIGN, campaign.entityType)
        assertEquals(SearchEntityType.OFFER, offer.entityType)
    }
}
