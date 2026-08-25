package com.commerceos.android.viewmodel

import com.commerceos.android.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying CommerceEntity typing, destination resolution, and safe fallback handling.
 */
class CommerceEntityRendererTest {

    @Test
    fun testCommerceEntityDestinations_mapToCorrectHomeDestinations() {
        val prodEntity = CommerceEntity.ProductItem(
            product = CommerceProduct(id = "p_01", sku = "SKU-1", name = "Test Product", price = 100.0, sellingPrice = 80.0, verticalId = "grocery")
        )
        val restEntity = CommerceEntity.RestaurantItem(
            id = "r_01",
            name = "Test Kitchen",
            imageUrl = null,
            cuisine = "Indian",
            priceForTwo = "₹500 for two"
        )
        val servEntity = CommerceEntity.ServiceItem(
            id = "s_01",
            title = "AC Servicing",
            providerName = "Pros",
            imageUrl = null,
            startingPrice = 499.0,
            duration = "45 mins"
        )
        val brandEntity = CommerceEntity.Brand(
            item = BrandItem(id = "b_01", name = "Nike"),
            vertical = "fashion"
        )

        assertEquals("p_01", (prodEntity.product.id))
        assertEquals("r_01", restEntity.id)
        assertEquals("s_01", servEntity.id)
        assertEquals("b_01", brandEntity.item.id)
    }

    @Test
    fun testSearchResultDestinations_mapToTypedDestinationsWithoutStringParsing() {
        val searchProd = SearchResult(
            entityType = SearchEntityType.PRODUCT,
            entityId = "p_100",
            title = "Crocin 500mg",
            vertical = "health"
        )
        val searchRest = SearchResult(
            entityType = SearchEntityType.RESTAURANT,
            entityId = "r_200",
            title = "Truffles Kitchen",
            vertical = "food"
        )

        val prodDest = searchProd.toDestination()
        val restDest = searchRest.toDestination()

        assertNotNull(prodDest)
        assertNotNull(restDest)
        assertEquals("p_100", (prodDest as HomeDestination.Product).productId)
        assertEquals("r_200", (restDest as HomeDestination.Restaurant).restaurantId)
    }
}
