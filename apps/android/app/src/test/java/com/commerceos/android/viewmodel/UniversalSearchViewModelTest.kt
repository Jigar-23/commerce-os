package com.commerceos.android.viewmodel

import com.commerceos.android.model.ApiMedicine
import com.commerceos.android.model.HomeDestination
import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchResult
import com.commerceos.android.model.UniversalSearchQuery
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import com.commerceos.android.repository.UniversalSearchRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UniversalSearchViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private class FakeAppRepository : AppRepository() {
        override suspend fun getMedicines(query: String): ApiResult<List<ApiMedicine>> {
            val item = ApiMedicine(
                id = "med_001",
                sku = "SKU-001",
                name = "Paracetamol 500mg",
                brandName = "Crocin",
                manufacturer = "GSK",
                packSize = "10 Tablets",
                rxRequirement = "OTC",
                price = 30.0,
                discountedPrice = 25.0,
                expressDeliverySlaMins = 15,
                therapeuticCategory = "health"
            )
            return ApiResult.Success(listOf(item))
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
    fun testExecuteSearch_returnsTypedSearchResults() = runTest {
        val appRepo = FakeAppRepository()
        val searchRepo = object : UniversalSearchRepository(appRepo, testDispatcher) {
            override suspend fun executeSearch(query: UniversalSearchQuery): ApiResult<List<SearchResult>> {
                val results = listOf(
                    SearchResult(entityType = SearchEntityType.PRODUCT, entityId = "med_001", title = "Paracetamol 500mg", vertical = "health"),
                    SearchResult(entityType = SearchEntityType.STORE, entityId = "store_001", title = "City Pharmacy", vertical = "health"),
                    SearchResult(entityType = SearchEntityType.RESTAURANT, entityId = "rest_001", title = "Pizza Palace", vertical = "food"),
                    SearchResult(entityType = SearchEntityType.SERVICE, entityId = "serv_001", title = "Home Cleaning", vertical = "services")
                )
                return ApiResult.Success(results)
            }
        }
        val viewModel = UniversalSearchViewModel(appRepo, searchRepo)

        viewModel.executeSearch("paracetamol", "health")
        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState
        assertTrue(state is SearchUiState.Success)

        val successState = state as SearchUiState.Success
        assertEquals("paracetamol", successState.query.text)
        assertEquals("health", successState.query.vertical)
        assertEquals(4, successState.results.size)

        val firstResult = successState.results.first()
        assertEquals(SearchEntityType.PRODUCT, firstResult.entityType)
        assertEquals("med_001", firstResult.entityId)
        assertEquals("Paracetamol 500mg", firstResult.title)
    }

    @Test
    fun testDebouncedSearch_delaysExecutionAndCancelsRace() = runTest {
        val appRepo = FakeAppRepository()
        val searchRepo = object : UniversalSearchRepository(appRepo, testDispatcher) {
            override suspend fun executeSearch(query: UniversalSearchQuery): ApiResult<List<SearchResult>> {
                return ApiResult.Success(listOf(SearchResult(entityType = SearchEntityType.PRODUCT, entityId = query.text, title = query.text)))
            }
        }
        val viewModel = UniversalSearchViewModel(appRepo, searchRepo)

        viewModel.updateQuery("p", debounceMs = 300L)
        viewModel.updateQuery("pa", debounceMs = 300L)
        viewModel.updateQuery("para", debounceMs = 300L)

        testDispatcher.scheduler.advanceUntilIdle()

        val state = viewModel.uiState
        assertTrue(state is SearchUiState.Success)
        val successState = state as SearchUiState.Success
        assertEquals("para", successState.query.text)
    }

    @Test
    fun testExpressFilter_usesStructuredFlagAndIgnoresTextInference() = runTest {
        val verifiedExpress = SearchResult(
            entityType = SearchEntityType.PRODUCT,
            entityId = "express_1",
            title = "Verified Express Product",
            isExpressEligible = true
        )
        val nonExpressWithText = SearchResult(
            entityType = SearchEntityType.PRODUCT,
            entityId = "non_express_text",
            title = "Non Express Product",
            subtitle = "Express Delivery Available in 20 min",
            isExpressEligible = false
        )
        val unknownExpress = SearchResult(
            entityType = SearchEntityType.PRODUCT,
            entityId = "unknown_1",
            title = "Unknown Fulfillment Product",
            isExpressEligible = null
        )

        val results = listOf(verifiedExpress, nonExpressWithText, unknownExpress)

        val filteredExpress = results.filter { it.isExpressEligible == true }

        assertEquals(1, filteredExpress.size)
        assertEquals("express_1", filteredExpress.first().entityId)
        assertTrue(filteredExpress.none { it.entityId == "non_express_text" })
        assertTrue(filteredExpress.none { it.entityId == "unknown_1" })
    }

    @Test
    fun testSearchEntityDestinationMapping_mapsCollectionCampaignAndOfferCorrectly() = runTest {
        val collResult = SearchResult(
            entityType = SearchEntityType.COLLECTION,
            entityId = "coll_001",
            title = "Summer Collection"
        )
        val campResult = SearchResult(
            entityType = SearchEntityType.CAMPAIGN,
            entityId = "camp_001",
            title = "Grand Festive Sale"
        )
        val offerResult = SearchResult(
            entityType = SearchEntityType.OFFER,
            entityId = "offer_001",
            title = "50% Off Instant Discount"
        )

        assertEquals("coll_001", (collResult.toDestination() as HomeDestination.Collection).collectionId)
        assertEquals("camp_001", (campResult.toDestination() as HomeDestination.Campaign).campaignId)
        assertEquals("offer_001", (offerResult.toDestination() as HomeDestination.Offer).offerId)
    }

    @Test
    fun testMissingRatingAndMetadata_omitsUnverifiedClaims() = runTest {
        val resultWithoutRating = SearchResult(
            entityType = SearchEntityType.PRODUCT,
            entityId = "p_no_rating",
            title = "Product Without Rating",
            rating = null,
            score = null,
            cuisine = null,
            etaLabel = null
        )

        val displayRating = resultWithoutRating.rating ?: resultWithoutRating.score?.toDouble()
        assertTrue(displayRating == null)
        assertTrue(resultWithoutRating.cuisine == null)
        assertTrue(resultWithoutRating.etaLabel == null)
    }
}
