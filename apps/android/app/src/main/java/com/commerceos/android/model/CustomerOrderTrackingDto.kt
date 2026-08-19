package com.commerceos.android.model

data class CustomerOrderTrackingDto(
    val orderId: String,
    val deliveryId: String? = null,
    val state: String = "ASSIGNED",
    val riderName: String? = null,
    val riderPhone: String? = null,
    val riderVehicle: String? = null,
    val merchantLat: Double = 28.1989,
    val merchantLng: Double = 76.6186,
    val customerLat: Double = 28.1970,
    val customerLng: Double = 76.6190,
    val liveRiderTelemetry: LiveRiderTelemetryDto? = null,
    val trackingStatusText: String? = null,
    val estimatedArrivalMins: Int? = null,
    val isStale: Boolean = false,
    val lastUpdatedTimestamp: Long? = null
)

data class LiveRiderTelemetryDto(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float = 0f,
    val heading: Float = 0f,
    val sequenceNumber: Long = 0,
    val serverTimestamp: Long = 0,
    val isStale: Boolean = false
)
