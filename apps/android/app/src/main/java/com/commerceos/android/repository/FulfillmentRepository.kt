package com.commerceos.android.repository

import com.commerceos.android.model.FulfillmentContext
import com.commerceos.android.model.FulfillmentStatus
import com.commerceos.android.model.ServiceabilityItem
import com.commerceos.android.model.ServiceabilityResponse
import com.commerceos.android.model.VerticalAvailability
import com.commerceos.android.model.VerticalOperationalStatus
import com.commerceos.android.network.ApiResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.util.UUID
import java.util.concurrent.atomic.AtomicReference

/**
 * Shared Fulfillment Domain Repository.
 * Owns location-based serviceability & fulfillment promises across Commerce OS.
 * Guarantees request-ID validation and latest-request-wins ordering: stale async
 * responses for previous addresses or older request IDs are automatically discarded.
 * ZERO fabricated availability: ETAs and serviceability come strictly from the server.
 */
class FulfillmentRepository(private val appRepository: AppRepository) {

    private val _fulfillmentContext = MutableStateFlow(FulfillmentContext())
    val fulfillmentContext: StateFlow<FulfillmentContext> = _fulfillmentContext.asStateFlow()

    private val activeRequestId = AtomicReference<String>("")

    /**
     * Issues an address, customer, and cart-aware fulfillment check with a new UUID request ID.
     * Guarantees latest-request-wins order.
     */
    suspend fun checkFulfillment(
        addressId: String,
        customerId: String = "guest",
        items: List<ServiceabilityItem> = emptyList()
    ): FulfillmentContext = withContext(Dispatchers.IO) {
        val newRequestId = UUID.randomUUID().toString()
        activeRequestId.set(newRequestId)

        _fulfillmentContext.value = FulfillmentContext(
            addressId = addressId,
            status = FulfillmentStatus.CHECKING,
            requestId = newRequestId
        )

        val result = if (items.isNotEmpty() || customerId != "guest") {
            appRepository.getServiceability(customerId, addressId, items)
        } else {
            appRepository.checkServiceability(addressId)
        }

        // Request protection: discard if address changed or a newer check was launched
        if (activeRequestId.get() != newRequestId) {
            return@withContext _fulfillmentContext.value
        }

        val updatedContext = when (result) {
            is ApiResult.Success -> {
                val resp = result.data
                val verticals = buildVerticalAvailabilityMap(addressId, resp)

                val status = when {
                    !resp.eligible -> FulfillmentStatus.UNSERVICEABLE
                    verticals.values.any { it.status == VerticalOperationalStatus.AVAILABLE } &&
                        verticals.values.any { it.status == VerticalOperationalStatus.OUT_OF_ZONE } -> FulfillmentStatus.PARTIAL
                    else -> FulfillmentStatus.SERVICEABLE
                }

                FulfillmentContext(
                    addressId = addressId,
                    status = status,
                    etaLabel = resp.etaLabel,
                    verticalFulfillments = verticals,
                    generatedAt = System.currentTimeMillis(),
                    requestId = newRequestId
                )
            }
            is ApiResult.Failure -> {
                FulfillmentContext(
                    addressId = addressId,
                    status = FulfillmentStatus.ERROR,
                    requestId = newRequestId
                )
            }
        }

        if (activeRequestId.get() == newRequestId) {
            _fulfillmentContext.value = updatedContext
        }

        updatedContext
    }

    /**
     * Address-only serviceability preview (for location bar / anonymous browsing).
     */
    suspend fun checkAddressServiceability(addressId: String): FulfillmentContext =
        checkFulfillment(addressId = addressId, customerId = "anonymous", items = emptyList())

    /**
     * Customer & Cart-aware fulfillment eligibility check (for cart checkout).
     */
    suspend fun checkCartFulfillmentEligibility(
        customerId: String,
        addressId: String,
        items: List<ServiceabilityItem>
    ): FulfillmentContext =
        checkFulfillment(addressId = addressId, customerId = customerId, items = items)

    private fun buildVerticalAvailabilityMap(
        addressId: String,
        resp: ServiceabilityResponse
    ): Map<String, VerticalAvailability> {
        // Server-authoritative vertical availability.
        // If the backend explicitly provides vertical status, map it.
        if (!resp.verticals.isNullOrEmpty()) {
            return resp.verticals.associate { apiVert ->
                val status = when (apiVert.status.uppercase()) {
                    "AVAILABLE", "SERVICEABLE" -> VerticalOperationalStatus.AVAILABLE
                    "COMING_SOON" -> VerticalOperationalStatus.COMING_SOON
                    "OUT_OF_ZONE", "UNSERVICEABLE" -> VerticalOperationalStatus.OUT_OF_ZONE
                    "DEGRADED" -> VerticalOperationalStatus.DEGRADED
                    "TEMPORARILY_UNAVAILABLE" -> VerticalOperationalStatus.TEMPORARILY_UNAVAILABLE
                    else -> VerticalOperationalStatus.UNKNOWN
                }
                apiVert.verticalId to VerticalAvailability(
                    verticalId = apiVert.verticalId,
                    addressId = addressId,
                    status = status,
                    etaLabel = apiVert.eta,
                    fulfillmentMode = apiVert.deliveryMode ?: "standard"
                )
            }
        }

        // When no vertical list is provided by server, non-supplied verticals default to UNKNOWN with null ETAs.
        val verticalsList = listOf("health", "grocery", "food", "fashion", "electronics", "services")
        return verticalsList.associateWith { vId ->
            VerticalAvailability(vId, addressId, VerticalOperationalStatus.UNKNOWN, null)
        }
    }

    fun invalidate() {
        activeRequestId.set("")
        _fulfillmentContext.value = FulfillmentContext()
    }
}
