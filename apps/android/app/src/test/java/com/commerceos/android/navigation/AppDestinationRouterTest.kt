package com.commerceos.android.navigation

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.model.HomeDestination
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.ui.navigation.Screen
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3-96: Unit tests for AppDestinationRouter.
 * Verifies mapping of every HomeDestination to concrete Screen destination without UI coupling.
 */
class AppDestinationRouterTest {

    private val router = AppDestinationRouter()

    @Test
    fun testOrdersDestination_mapsToOrderHistory() {
        val screen = router.resolve(HomeDestination.Orders)
        assertEquals(Screen.OrderHistory, screen)
    }

    @Test
    fun testCartDestination_mapsToCart() {
        val screen = router.resolve(HomeDestination.Cart)
        assertEquals(Screen.Cart, screen)
    }

    @Test
    fun testPrescriptionsDestination_mapsToPrescriptions() {
        val screen = router.resolve(HomeDestination.Prescriptions, ClientConfiguration.PharmacyClient)
        assertEquals(Screen.Prescriptions, screen)
    }

    @Test
    fun testPrescriptionsDestination_whenDisabled_mapsToFeatureDisabled() {
        val screen = router.resolve(HomeDestination.Prescriptions, ClientConfiguration.DefaultGeneric)
        assertTrue(screen is Screen.FeatureDisabled)
    }

    @Test
    fun testCategoriesDestination_mapsToCategories() {
        val screen = router.resolve(HomeDestination.Categories)
        assertEquals(Screen.Categories, screen)
    }

    @Test
    fun testProductDestination_mapsToProductDetail() {
        val screen = router.resolve(HomeDestination.Product("prod_101", "health"))
        assertTrue(screen is Screen.ProductDetail)
        assertEquals("prod_101", (screen as Screen.ProductDetail).productId)
    }

    @Test
    fun testCategoryDestination_mapsToCatalog() {
        val screen = router.resolve(HomeDestination.Category("cat_wellness", "health"))
        assertTrue(screen is Screen.Catalog)
        assertEquals("cat_wellness", (screen as Screen.Catalog).query.categoryId)
        assertEquals("health", screen.query.vertical)
    }

    @Test
    fun testBrandDestination_mapsToBrandScreen() {
        val screen = router.resolve(HomeDestination.Brand("brand_himalaya", "health"))
        assertTrue(screen is Screen.Brand)
        assertEquals("brand_himalaya", (screen as Screen.Brand).brandId)
    }

    @Test
    fun testVerticalDestination_mapsToVerticalHome() {
        val screen = router.resolve(HomeDestination.Vertical("grocery"))
        assertTrue(screen is Screen.VerticalHome)
        assertEquals("grocery", (screen as Screen.VerticalHome).verticalId)
    }

    @Test
    fun testStoreDestination_mapsToStoreScreen() {
        val screen = router.resolve(HomeDestination.Store("store_123"))
        assertTrue(screen is Screen.Store)
        assertEquals("store_123", (screen as Screen.Store).storeId)
    }

    @Test
    fun testRestaurantDestination_mapsToRestaurantScreen() {
        val screen = router.resolve(HomeDestination.Restaurant("rest_456"))
        assertTrue(screen is Screen.Restaurant)
        assertEquals("rest_456", (screen as Screen.Restaurant).restaurantId)
    }

    @Test
    fun testServiceDestination_mapsToServiceScreen() {
        val screen = router.resolve(HomeDestination.Service("service_789"), com.commerceos.android.config.ClientConfiguration.ServicesClient)
        assertTrue(screen is Screen.Service)
        assertEquals("service_789", (screen as Screen.Service).serviceId)
    }

    @Test
    fun testServiceDestination_whenDisabled_mapsToFeatureDisabled() {
        val screen = router.resolve(HomeDestination.Service("service_789"), ClientConfiguration.DefaultGeneric)
        assertTrue(screen is Screen.FeatureDisabled)
    }

    @Test
    fun testCampaignDestination_mapsToCampaignScreen() {
        val screen = router.resolve(HomeDestination.Campaign("camp_summer"))
        assertTrue(screen is Screen.Campaign)
        assertEquals("camp_summer", (screen as Screen.Campaign).campaignId)
    }

    @Test
    fun testCollectionDestination_mapsToCollectionScreen() {
        val screen = router.resolve(HomeDestination.Collection("coll_top20"))
        assertTrue(screen is Screen.Collection)
        assertEquals("coll_top20", (screen as Screen.Collection).collectionId)
    }

    @Test
    fun testOfferDestination_mapsToOfferScreen() {
        val screen = router.resolve(HomeDestination.Offer("offer_50off"))
        assertTrue(screen is Screen.Offer)
        assertEquals("offer_50off", (screen as Screen.Offer).offerId)
    }

    @Test
    fun testSearchDestination_mapsToSearchScreen() {
        val searchQuery = UniversalSearchQuery(text = "paracetamol", vertical = "health")
        val screen = router.resolveSearch(searchQuery)
        assertTrue(screen is Screen.Search)
        assertEquals("paracetamol", (screen as Screen.Search).query.text)
    }
}
