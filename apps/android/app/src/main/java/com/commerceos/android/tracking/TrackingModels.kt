package com.commerceos.android.tracking

import com.commerceos.android.fulfillment.ShipmentCheckpoint

/** Modes of unified order tracking experience in Commerce OS. */
enum class TrackingMode {
    LIVE_LOCATION,
    CARRIER_CHECKPOINT,
    PICKUP,
    SERVICE_BOOKING
}

enum class CustomerTrackingSignalState {
    LIVE,
    DELAYED,
    LOCATION_UNAVAILABLE,
    DISCONNECTED
}

/** Unified tracking session state for customer screen rendering. */
data class UnifiedTrackingSession(
    val orderId: String,
    val mode: TrackingMode,
    val title: String,
    val statusText: String,
    val estimatedArrivalFormatted: String,
    val liveRiderLat: Double? = null,
    val liveRiderLng: Double? = null,
    val riderName: String? = null,
    val riderPhone: String? = null,
    val riderPhotoUrl: String? = null,
    val handoffOtp: String? = null,
    val consignmentNumber: String? = null,
    val carrierName: String? = null,
    val checkpoints: List<ShipmentCheckpoint> = emptyList(),
    val pickupStoreAddress: String? = null,
    val pickupQrPassCode: String? = null,
    val serviceProviderName: String? = null,
    val serviceSlotTime: String? = null,
    val isStale: Boolean = false,
    val isSseConnected: Boolean = true,
    val isDelayed: Boolean = false,
    val lastEventId: Long = 0L,
    val lastUpdatedTimestamp: Long = System.currentTimeMillis()
) {
    val signalState: CustomerTrackingSignalState
        get() = when {
            !isSseConnected -> CustomerTrackingSignalState.DISCONNECTED
            liveRiderLat == null || liveRiderLng == null -> CustomerTrackingSignalState.LOCATION_UNAVAILABLE
            isStale || isDelayed -> CustomerTrackingSignalState.DELAYED
            else -> CustomerTrackingSignalState.LIVE
        }
}
