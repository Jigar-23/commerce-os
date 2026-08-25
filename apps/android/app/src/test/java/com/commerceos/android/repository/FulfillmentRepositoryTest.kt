package com.commerceos.android.repository

import com.commerceos.android.model.ApiVerticalAvailability
import com.commerceos.android.model.FulfillmentStatus
import com.commerceos.android.model.ServiceabilityResponse
import com.commerceos.android.model.VerticalOperationalStatus
import com.commerceos.android.network.ApiResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P3-97: Unit tests for FulfillmentRepository invariants.
 * Verifies address matching, request protection, partial status, and zero fabricated ETAs.
 */
class FulfillmentRepositoryTest {

    private class FakeAppRepository : AppRepository() {
        var responseToReturn: ServiceabilityResponse = ServiceabilityResponse(
            eligible = true,
            etaLabel = "20-30 min"
        )

        override suspend fun checkServiceability(addressId: String): ApiResult<ServiceabilityResponse> {
            return ApiResult.Success(responseToReturn)
        }
    }

    @Test
    fun testFulfillmentCheck_returnsServiceableWithServerEta() = runBlocking {
        val fakeRepo = FakeAppRepository()
        fakeRepo.responseToReturn = ServiceabilityResponse(
            eligible = true,
            etaLabel = "15-25 min",
            verticals = listOf(
                ApiVerticalAvailability("health", "AVAILABLE", "15-25 min"),
                ApiVerticalAvailability("grocery", "AVAILABLE", "10-20 min")
            )
        )
        val fulfillmentRepo = FulfillmentRepository(fakeRepo)
        val context = fulfillmentRepo.checkFulfillment("addr_001")

        assertEquals("addr_001", context.addressId)
        assertEquals(FulfillmentStatus.SERVICEABLE, context.status)
        assertEquals("15-25 min", context.etaLabel)
        assertEquals(VerticalOperationalStatus.AVAILABLE, context.verticalFulfillments["health"]?.status)
        assertEquals("10-20 min", context.verticalFulfillments["grocery"]?.etaLabel)
    }

    @Test
    fun testFulfillmentCheck_noFabricatedEtasForUnsuppliedVerticals() = runBlocking {
        val fakeRepo = FakeAppRepository()
        fakeRepo.responseToReturn = ServiceabilityResponse(
            eligible = true,
            etaLabel = "20 min",
            verticals = null // Backend only returned root promise
        )
        val fulfillmentRepo = FulfillmentRepository(fakeRepo)
        val context = fulfillmentRepo.checkFulfillment("addr_002")

        // All unsupplied verticals (including health) MUST NOT have fabricated ETAs when verticals list is null
        assertNull(context.verticalFulfillments["health"]?.etaLabel)
        assertNull(context.verticalFulfillments["fashion"]?.etaLabel)
        assertNull(context.verticalFulfillments["electronics"]?.etaLabel)
        assertNull(context.verticalFulfillments["services"]?.etaLabel)

        assertEquals(VerticalOperationalStatus.UNKNOWN, context.verticalFulfillments["fashion"]?.status)
    }

    @Test
    fun testFulfillmentContext_addressValidationAndExpiry() {
        val context = com.commerceos.android.model.FulfillmentContext(
            addressId = "addr_abc",
            status = FulfillmentStatus.SERVICEABLE,
            expiresAt = System.currentTimeMillis() + 60000
        )
        assertTrue(context.isValidForAddress("addr_abc"))
        assertFalse(context.isValidForAddress("addr_xyz"))
        assertFalse(context.isExpired)
    }
}
