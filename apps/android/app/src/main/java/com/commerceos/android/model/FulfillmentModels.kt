package com.commerceos.android.model

import java.util.UUID

/** Explicit fulfillment lifecycle state across Commerce OS. */
enum class FulfillmentStatus {
    UNKNOWN,
    CHECKING,
    SERVICEABLE,
    PARTIAL,
    UNSERVICEABLE,
    ERROR
}

/** Operational serviceability status for a vertical at a customer location. */
enum class VerticalOperationalStatus {
    UNKNOWN,
    AVAILABLE,
    COMING_SOON,
    OUT_OF_ZONE,
    TEMPORARILY_UNAVAILABLE,
    DEGRADED
}

/** Address-aware fulfillment promise for a specific Commerce OS vertical. */
data class VerticalAvailability(
    val verticalId: String,
    val addressId: String,
    val status: VerticalOperationalStatus,
    val etaLabel: String? = null,
    val fulfillmentMode: String = "standard"
) {
    val isServiceable: Boolean get() = status == VerticalOperationalStatus.AVAILABLE || status == VerticalOperationalStatus.DEGRADED
}

/** Immutable fulfillment context snapshot owned by FulfillmentRepository. */
data class FulfillmentContext(
    val addressId: String? = null,
    val status: FulfillmentStatus = FulfillmentStatus.UNKNOWN,
    val etaLabel: String? = null,
    val verticalFulfillments: Map<String, VerticalAvailability> = emptyMap(),
    val generatedAt: Long = System.currentTimeMillis(),
    val expiresAt: Long = System.currentTimeMillis() + (15 * 60 * 1000), // 15-minute TTL
    val requestId: String = UUID.randomUUID().toString()
) {
    val isExpired: Boolean get() = System.currentTimeMillis() > expiresAt
    fun isValidForAddress(targetAddressId: String?): Boolean =
        !targetAddressId.isNullOrBlank() && addressId == targetAddressId && !isExpired
}
