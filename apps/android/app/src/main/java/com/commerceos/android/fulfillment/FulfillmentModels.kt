package com.commerceos.android.fulfillment

import java.math.BigDecimal

/** Canonical fulfillment modes supported across Commerce OS tenants. */
enum class FulfillmentMode {
    QUICK,
    STANDARD,
    SCHEDULED,
    PICKUP,
    SERVICE_BOOKING
}

/** State machine for inventory reservation lifecycle. */
enum class ReservationState {
    RESERVED,
    ALLOCATED,
    PICKED,
    PACKED,
    HANDED_OFF,
    DELIVERED,
    CANCELLED,
    RELEASED
}

/** Physical or virtual fulfillment node (Warehouse, Store, Dark Store, Merchant Outlet). */
data class FulfillmentNode(
    val nodeId: String,
    val name: String,
    val merchantId: String,
    val latitude: Double,
    val longitude: Double,
    val supportedModes: Set<FulfillmentMode> = setOf(FulfillmentMode.STANDARD, FulfillmentMode.QUICK),
    val isActive: Boolean = true
)

/** Item inventory reservation record. */
data class InventoryReservation(
    val reservationId: String,
    val orderId: String,
    val sku: String,
    val quantity: Int,
    val nodeId: String,
    val state: ReservationState = ReservationState.RESERVED,
    val createdAtTimestamp: Long = System.currentTimeMillis()
)

/** Serviceability check result for a given fulfillment node and customer location. */
data class ServiceabilityCheckResult(
    val isEligible: Boolean,
    val fulfillmentMode: FulfillmentMode,
    val nodeId: String,
    val nodeName: String,
    val estimatedSlaMinutes: Int,
    val deliveryFee: BigDecimal,
    val coldChainFee: BigDecimal = BigDecimal.ZERO,
    val distanceKm: Double = 0.0
)

/** Proof of delivery record captured upon customer handoff. */
data class ProofOfDelivery(
    val orderId: String,
    val handoffTimestamp: Long = System.currentTimeMillis(),
    val otpVerified: Boolean = false,
    val photoUrl: String? = null,
    val customerSignatureUrl: String? = null,
    val gpsLatitude: Double? = null,
    val gpsLongitude: Double? = null,
    val recipientName: String? = null,
    val collectorRiderId: String? = null
)
