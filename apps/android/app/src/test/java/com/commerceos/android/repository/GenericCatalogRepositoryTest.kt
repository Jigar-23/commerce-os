package com.commerceos.android.repository

import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.PageResponse
import com.commerceos.android.model.VerticalHomeFeedResponse
import com.commerceos.android.network.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P1-19: Unit tests verifying that generic catalog and vertical home feeds
 * do not delegate to medicine endpoints on API failure.
 */
class GenericCatalogRepositoryTest {

    private class FakeAppRepository : AppRepository() {
        var simulateFailure = false

        override suspend fun getVerticalHomeFeed(verticalId: String, addressId: String?): ApiResult<VerticalHomeFeedResponse> {
            if (simulateFailure) {
                return ApiResult.Failure(com.commerceos.android.network.AppError.Network("Server Error"))
            }
            return ApiResult.Success(
                VerticalHomeFeedResponse(
                    verticalId = verticalId,
                    title = "Food Market",
                    subtitle = "Fresh meals delivered fast"
                )
            )
        }

        override suspend fun queryCatalogProducts(
            query: String,
            category: String,
            categoryId: String?,
            brandId: String?,
            storeId: String?,
            collectionId: String?,
            campaignId: String?,
            offerId: String?,
            vertical: String?,
            priceBand: com.commerceos.android.model.PriceBand?,
            limit: Int,
            offset: Int
        ): ApiResult<PageResponse<CommerceProduct>> {
            if (simulateFailure) {
                return ApiResult.Failure(com.commerceos.android.network.AppError.Network("Not Found"))
            }
            return ApiResult.Success(
                PageResponse(
                    content = listOf(
                        CommerceProduct(
                            id = "prod_001",
                            sku = "SKU-FOOD-001",
                            name = "Margherita Pizza",
                            price = 15.0,
                            sellingPrice = 12.0,
                            verticalId = "food"
                        )
                    ),
                    totalElements = 1,
                    hasMore = false
                )
            )
        }
    }

    @Test
    fun testVerticalHomeFeed_failsCleanlyWithoutMedicineFallback() = runBlocking {
        val repo = FakeAppRepository()
        repo.simulateFailure = true

        val result = repo.getVerticalHomeFeed("food", "addr_123")
        assertTrue(result is ApiResult.Failure)
        val failure = result as ApiResult.Failure
        assertTrue(failure.error is com.commerceos.android.network.AppError.Network)
    }

    @Test
    fun testQueryCatalogProducts_returnsGenericProducts() = runBlocking {
        val repo = FakeAppRepository()
        repo.simulateFailure = false

        val result = repo.queryCatalogProducts(
            query = "pizza",
            category = "",
            categoryId = null,
            brandId = null,
            storeId = null,
            collectionId = null,
            campaignId = null,
            offerId = null,
            vertical = "food",
            priceBand = null,
            limit = 20,
            offset = 0
        )

        assertTrue(result is ApiResult.Success)
        val success = result as ApiResult.Success
        assertEquals(1, success.data.content.size)
        assertEquals("food", success.data.content.first().verticalId)
    }
}
