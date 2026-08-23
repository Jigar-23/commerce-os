package com.commerceos.android.model

data class CustomerOrderTrackingDto(
    val orderId: String,
    val deliveryId: String? = null,
    val state: String = "ASSIGNED",
    val stage: String? = null,
    val riderName: String? = null,
    val riderPhone: String? = null,
    val riderVehicle: String? = null,
    val merchantLat: Double = 28.202218,
    val merchantLng: Double = 76.615403,
    val customerLat: Double = 28.1970,
    val customerLng: Double = 76.6190,
    val liveRiderTelemetry: LiveRiderTelemetryDto? = null,
    val trackingStatusText: String? = null,
    val estimatedArrivalMins: Int? = null,
    val remainingDistanceKm: Double? = null,
    val isStale: Boolean = false,
    val lastUpdatedTimestamp: Long? = null,
    val waypoints: List<TrackingWaypointDto> = emptyList()
)

data class TrackingWaypointDto(
    val lat: Double,
    val lng: Double
)

data class LiveRiderTelemetryDto(
    val latitude: Double,
    val longitude: Double,
    val rawLatitude: Double? = null,
    val rawLongitude: Double? = null,
    val speedKmh: Float = 0f,
    val heading: Float = 0f,
    val sequenceNumber: Long = 0,
    val serverTimestamp: Long = 0,
    val routeProgressPct: Float? = null,
    val remainingDistanceKm: Double? = null,
    val isSnapped: Boolean = false,
    val isStale: Boolean = false
)
