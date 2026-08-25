package com.commerceos.android.rider

/** Canonical delivery state lifecycle across fulfillment. */
enum class CanonicalDeliveryState {
    ASSIGNED,
    ACCEPTED,
    EN_ROUTE_PICKUP,
    ARRIVED_PICKUP,
    PICKED_UP,
    EN_ROUTE_CUSTOMER,
    ARRIVED_CUSTOMER,
    HANDOFF_STARTED,
    DELIVERED,
    CANCELLED,
    DECLINED,
    RETURNED
}

/** Status lifecycle of a delivery rider. */
enum class RiderShiftStatus {
    OFFLINE,
    ONLINE_AVAILABLE,
    EN_ROUTE_PICKUP,
    ARRIVED_MERCHANT,
    EN_ROUTE_DELIVERY,
    ARRIVED_CUSTOMER,
    DELIVERING
}

/** Status lifecycle of a quick delivery job dispatch. */
enum class JobAcceptanceState {
    PENDING_DISPATCH,
    ACCEPTED,
    DECLINED,
    PICKED_UP,
    COMPLETED,
    CANCELLED
}

/** Profile details of an active delivery rider. */
data class RiderProfile(
    val riderId: String,
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    val rating: Double = 4.8,
    val currentStatus: RiderShiftStatus = RiderShiftStatus.ONLINE_AVAILABLE,
    val currentLat: Double = 19.0760,
    val currentLng: Double = 72.8777,
    val totalDeliveries: Int = 142
)

/** Realtime GPS location payload streamed by rider app to gateway. */
data class RiderLocationUpdate(
    val riderId: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float = 0.0f,
    val heading: Float = 0.0f,
    val accuracyMeters: Float = 5.0f,
    val timestamp: Long = System.currentTimeMillis()
)

/** Quick delivery job assignment dispatch details. */
data class RiderJob(
    val jobId: String,
    val orderId: String,
    val merchantName: String,
    val merchantAddress: String,
    val merchantLat: Double,
    val merchantLng: Double,
    val customerName: String,
    val customerAddress: String,
    val customerLat: Double,
    val customerLng: Double,
    val estimatedPayoutFormatted: String = "₹65",
    val estimatedDistanceKm: Double = 3.2,
    val state: JobAcceptanceState = JobAcceptanceState.PENDING_DISPATCH,
    val isCod: Boolean = false,
    val codAmount: Double = 0.0,
    val requiredOtp: String = "4892"
)

/** COD collection transaction record. */
data class CodTransaction(
    val transactionId: String,
    val orderId: String,
    val riderId: String,
    val expectedAmount: Double,
    val collectedAmount: Double,
    val isSettled: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

/** Incident report logged by rider. */
data class DeliveryIncident(
    val incidentId: String,
    val orderId: String,
    val riderId: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)
