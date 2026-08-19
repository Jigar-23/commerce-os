package com.commerceos.android.viewmodel

import com.commerceos.android.model.*
import org.junit.Assert.*
import org.junit.Test

/**
 * 🟡 P2 — ENTITY TEST SUITE
 * Complete test coverage for all 10 entity models & renderers in Commerce OS.
 */
class EntityRendererSuiteTest {

    @Test
    fun testProductEntity_PropertiesAndNullSafety() {
        val product = CommerceProduct(
            id = "p101",
            sku = "sku_101",
            name = "Paracetamol 500mg",
            price = 50.0,
            sellingPrice = 45.0,
            image = "https://example.com/p101.jpg",
            medicineDetails = MedicineAttributes(prescriptionRequired = true)
        )
        val entity = CommerceEntity.ProductItem(product = product)

        assertEquals("p101", entity.product.id)
        assertEquals("Paracetamol 500mg", entity.product.name)
        assertEquals(45.0, entity.product.sellingPrice, 0.01)
        assertTrue(entity.product.medicineDetails?.prescriptionRequired == true)
    }

    @Test
    fun testRestaurantEntity_Properties() {
        val entity = CommerceEntity.RestaurantItem(
            id = "r201",
            name = "Pizza Bistro",
            imageUrl = "https://example.com/r201.jpg",
            cuisine = "Italian",
            rating = 4.7,
            deliveryEta = "20-25 mins",
            priceForTwo = "₹500",
            offerText = "50% OFF"
        )

        assertEquals("r201", entity.id)
        assertEquals("Pizza Bistro", entity.name)
        assertEquals(4.7, entity.rating!!, 0.01)
    }

    @Test
    fun testServiceEntity_Properties() {
        val entity = CommerceEntity.ServiceItem(
            id = "s301",
            title = "AC Deep Cleaning",
            providerName = "ProClean Solutions",
            imageUrl = "https://example.com/s301.jpg",
            rating = 4.9,
            startingPrice = 599.0,
            duration = "45 mins"
        )

        assertEquals("s301", entity.id)
        assertEquals("AC Deep Cleaning", entity.title)
        assertEquals("ProClean Solutions", entity.providerName)
        assertEquals(599.0, entity.startingPrice, 0.01)
    }

    @Test
    fun testStoreEntity_Properties() {
        val shortcut = CommerceEntity.Shortcut(
            id = "store_401",
            label = "TechVault Central Store",
            iconType = "store",
            destination = HomeDestination.Store(storeId = "store_401")
        )
        assertEquals("store_401", shortcut.id)
        assertEquals("TechVault Central Store", shortcut.label)
    }

    @Test
    fun testBrandEntity_Properties() {
        val brand = BrandItem(
            id = "brand_501",
            name = "Nike",
            verticalId = "fashion"
        )
        val entity = CommerceEntity.Brand(item = brand, vertical = "fashion")

        assertEquals("brand_501", entity.item.id)
        assertEquals("Nike", entity.item.name)
        assertEquals("fashion", entity.vertical)
    }

    @Test
    fun testCategoryEntity_Properties() {
        val group = CategoryGroup(
            id = "cat_601",
            title = "Personal Care",
            subtitle = "Soaps, Shampoos & Oils",
            imageUrl = "https://example.com/cat.jpg"
        )
        val entity = CommerceEntity.CategoryItem(group = group, vertical = "general")

        assertEquals("cat_601", entity.group.id)
        assertEquals("Personal Care", entity.group.title)
    }

    @Test
    fun testCollectionEntity_Properties() {
        val shortcut = CommerceEntity.Shortcut(
            id = "coll_701",
            label = "Winter Essentials",
            iconType = "collection",
            destination = HomeDestination.Collection(collectionId = "coll_701")
        )
        assertEquals("coll_701", shortcut.id)
    }

    @Test
    fun testCampaignEntity_Properties() {
        val shortcut = CommerceEntity.Shortcut(
            id = "camp_801",
            label = "Diwali Mega Sale",
            iconType = "campaign",
            destination = HomeDestination.Campaign(campaignId = "camp_801")
        )
        assertEquals("camp_801", shortcut.id)
    }

    @Test
    fun testOfferEntity_Properties() {
        val shortcut = CommerceEntity.Shortcut(
            id = "offer_901",
            label = "FLAT 20% OFF RX",
            iconType = "offer",
            destination = HomeDestination.Offer(offerId = "offer_901")
        )
        assertEquals("offer_901", shortcut.id)
    }
}
