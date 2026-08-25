package com.commerceos.android.fulfillment

/** Normalized tracking status across external shipping carriers. */
enum class CarrierTrackingStatus {
    BOOKED,
    DISPATCHED,
    IN_TRANSIT,
    OUT_FOR_DELIVERY,
    DELIVERED,
    EXCEPTION,
    CANCELLED
}

/** Individual tracking checkpoint along the shipment route. */
data class ShipmentCheckpoint(
    val checkpointId: String,
    val facilityName: String,
    val locationName: String,
    val timestamp: Long,
    val status: CarrierTrackingStatus,
    val description: String
)

/** Shipment creation request sent to carrier adapters. */
data class CreateShipmentRequest(
    val orderId: String,
    val originNodeId: String,
    val recipientName: String,
    val recipientPhone: String,
    val recipientAddress: String,
    val recipientPincode: String,
    val weightKg: Double,
    val isCod: Boolean = false,
    val codAmount: Double = 0.0
)

/** Shipment creation response returned by carrier adapters. */
data class ShipmentResponse(
    val shipmentId: String,
    val consignmentNumber: String,
    val carrierName: String,
    val labelUrl: String? = null,
    val expectedDeliveryTimestamp: Long
)

/** Normalized webhook payload sent by external carriers to Commerce OS gateway. */
data class CarrierWebhookEvent(
    val eventId: String,
    val carrierName: String,
    val consignmentNumber: String,
    val status: CarrierTrackingStatus,
    val location: String,
    val timestamp: Long,
    val rawPayloadJson: String
)

/**
 * Universal Carrier Adapter Abstraction Interface.
 * Implementations wrap specific logistics providers (India Post, Blue Dart, Local Courier).
 * Guarantees zero carrier credentials leak into mobile client apps.
 */
interface CarrierAdapter {
    val carrierId: String
    val carrierName: String

    suspend fun createShipment(request: CreateShipmentRequest): ShipmentResponse
    suspend fun getTrackingStatus(consignmentNumber: String): List<ShipmentCheckpoint>
    suspend fun cancelShipment(consignmentNumber: String): Boolean
}
