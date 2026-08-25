package com.commerceos.android.viewmodel

import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.HomeFeedResponse
import com.commerceos.android.model.HomeHeroDto
import com.commerceos.android.model.HomeVertical
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * P3-99 & P3-100: Unit tests for HomeFeed mapping and generic Commerce entities.
 * Verifies that HomeFeedResponse supports multiple verticals (health, grocery, food, fashion, electronics)
 * without hardcoded pharmacy assumptions.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class HomeFeedMappingTest {

    private val testDispatcher = StandardTestDispatcher()

    private class MultiVerticalFakeRepository : AppRepository() {
        override suspend fun getHomeFeed(customerId: String, addressId: String?): ApiResult<HomeFeedResponse> {
            val groceryProd = CommerceProduct(
                id = "g_01",
                sku = "SKU-GROC-1",
                name = "Fresh Organic Milk 1L",
                price = 60.0,
                sellingPrice = 55.0,
                verticalId = "grocery"
            )
            val fashionProd = CommerceProduct(
                id = "f_01",
                sku = "SKU-FASH-1",
                name = "Cotton Denim Jacket",
                price = 2499.0,
                sellingPrice = 1999.0,
                verticalId = "fashion"
            )
            val electronicsProd = CommerceProduct(
                id = "e_01",
                sku = "SKU-ELEC-1",
                name = "Wireless Noise Cancelling Earbuds",
                price = 4999.0,
                sellingPrice = 3999.0,
                verticalId = "electronics"
            )

            val feed = HomeFeedResponse(
                hero = HomeHeroDto(
                    campaignId = "camp_001",
                    title = "Grand Multi-Vertical Sale",
                    subtitle = "Groceries, Fashion & Tech",
                    badge = "Mega Sale",
                    ctaText = "Shop Now"
                ),
                verticals = listOf(
                    HomeVertical("health", "Health", "Pharmacy & Wellness", "health", isLive = true),
                    HomeVertical("grocery", "Grocery", "Instant Supermarket", "grocery", isLive = true),
                    HomeVertical("fashion", "Fashion", "Trending Apparel", "fashion", isLive = true)
                ),
                buyAgain = listOf(groceryProd),
                topDeals = listOf(fashionProd),
                popular = listOf(electronicsProd),
                feed = listOf(groceryProd, fashionProd, electronicsProd)
            )

            return ApiResult.Success(feed)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testHomeFeedMapping_mapsMultiVerticalProducts() = runTest {
        val repo = MultiVerticalFakeRepository()
        val viewModel = HomeViewModel(repo)

        viewModel.loadHomeData("cust_100", "addr_200")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, viewModel.verticals.size)
        assertTrue(viewModel.sections.isNotEmpty())

        val buyAgainSection = viewModel.sections.find { it.id == "buy_again" }
        assertNotNull(buyAgainSection)

        val buyAgainEntity = buyAgainSection!!.entities.first() as CommerceEntity.ProductItem
        assertEquals("grocery", buyAgainEntity.product.verticalId)
        assertEquals("Fresh Organic Milk 1L", buyAgainEntity.product.name)

        val dealsSection = viewModel.sections.find { it.id == "top_deals" }
        assertNotNull(dealsSection)
        val dealsEntity = dealsSection!!.entities.first() as CommerceEntity.ProductItem
        assertEquals("fashion", dealsEntity.product.verticalId)

        val popularSection = viewModel.sections.find { it.id == "popular_picks" }
        assertNotNull(popularSection)
        val popularEntity = popularSection!!.entities.first() as CommerceEntity.ProductItem
        assertEquals("electronics", popularEntity.product.verticalId)
    }

    @Test
    fun testHomeFeedMapping_whenRestaurantsAndServicesEmpty_doesNotManufactureFakeEntities() = runTest {
        val repo = MultiVerticalFakeRepository()
        val viewModel = HomeViewModel(repo)

        viewModel.loadHomeData("cust_100", "addr_200")
        testDispatcher.scheduler.advanceUntilIdle()

        val restaurantSection = viewModel.sections.find { it.id == "restaurant_shelf" }
        assertTrue(restaurantSection == null || restaurantSection.entities.isEmpty())

        val serviceSection = viewModel.sections.find { it.id == "service_shelf" }
        assertTrue(serviceSection == null || serviceSection.entities.isEmpty())
    }
}
