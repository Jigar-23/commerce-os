package com.commerceos.android.registry

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchResult
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 🔴 P0 — CLIENT CARD REGISTRY TEST SUITE
 * Verifies card variant mapping for all entity types, custom overrides, and unknown-card fallback.
 */
class ClientCardRegistryTest {

    @Before
    fun setup() {
        ClientCardRegistry.clearOverrides()
    }

    @Test
    fun testProductCardMapping_ResolvesPerVerticalAndDomain() {
        val fashionProduct = CommerceEntity.ProductItem(
            product = CommerceProduct(id = "p1", sku = "s1", name = "Shirt", price = 100.0, sellingPrice = 90.0),
            vertical = "fashion"
        )
        val pharmacyProduct = CommerceEntity.ProductItem(
            product = CommerceProduct(id = "p2", sku = "s2", name = "Aspirin", price = 20.0, sellingPrice = 18.0),
            vertical = "pharmacy"
        )

        assertEquals(CardVariant.FASHION_PRODUCT, ClientCardRegistry.resolveCardVariant(fashionProduct, ClientConfiguration.FashionClient))
        assertEquals(CardVariant.PHARMACY_PRODUCT, ClientCardRegistry.resolveCardVariant(pharmacyProduct, ClientConfiguration.PharmacyClient))
    }

    @Test
    fun testRestaurantDishServiceStoreBrandCardMappings() {
        val rest = CommerceEntity.RestaurantItem("r1", "Bistro", null, "Italian", 4.5, "20m", "₹500")
        val dish = CommerceEntity.DishItem("d1", "r1", "Pasta", 200.0)
        val service = CommerceEntity.ServiceItem("s1", "AC Repair", "Fixer", null, 4.8, 300.0, "1 hr")
        val unknown = CommerceEntity.UnknownEntity("u1", "custom_type", "Special Entity")

        assertEquals(CardVariant.RESTAURANT_CARD, ClientCardRegistry.resolveCardVariant(rest, ClientConfiguration.DefaultGeneric))
        assertEquals(CardVariant.DISH_CARD, ClientCardRegistry.resolveCardVariant(dish, ClientConfiguration.DefaultGeneric))
        assertEquals(CardVariant.SERVICE_CARD, ClientCardRegistry.resolveCardVariant(service, ClientConfiguration.ServicesClient))
        assertEquals(CardVariant.FALLBACK_GENERIC_CARD, ClientCardRegistry.resolveCardVariant(unknown, ClientConfiguration.DefaultGeneric))
    }

    @Test
    fun testCustomCardOverride_OverridesDefaultVariant() {
        ClientCardRegistry.registerCardOverride("productitem", CardVariant.ELECTRONICS_PRODUCT)
        val product = CommerceEntity.ProductItem(
            product = CommerceProduct(id = "p1", sku = "s1", name = "Item", price = 10.0, sellingPrice = 10.0)
        )
        assertEquals(CardVariant.ELECTRONICS_PRODUCT, ClientCardRegistry.resolveCardVariant(product, ClientConfiguration.DefaultGeneric))
    }

    @Test
    fun testSafeFallbackCardVariant_ReturnsFallbackGeneric() {
        assertEquals(CardVariant.FALLBACK_GENERIC_CARD, ClientCardRegistry.safeFallbackCardVariant())
    }

    @Test
    fun testSearchResultCardMapping() {
        val result = SearchResult(
            entityId = "e1",
            title = "Special Offer",
            entityType = SearchEntityType.OFFER
        )
        assertEquals(CardVariant.OFFER_CARD, ClientCardRegistry.resolveSearchResultCardVariant(result, ClientConfiguration.DefaultGeneric))
    }
}
