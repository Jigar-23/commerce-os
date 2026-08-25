package com.commerceos.rider.model

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
    DECLINED,
    CANCELLED,
    FAILED,
    RETURNED
}

data class ServerDeliverySession(
    val deliveryId: String,
    val orderId: String,
    val riderId: String,
    val riderName: String,
    val riderPhone: String,
    val riderVehicle: String,
    val customerId: String,
    val customerName: String,
    val customerPhone: String,
    val customerAddress: String,
    val customerLat: Double?,
    val customerLng: Double?,
    val merchantName: String,
    val merchantAddress: String,
    val merchantLat: Double?,
    val merchantLng: Double?,
    val payoutFormatted: String? = null,
    val distanceKm: Double? = null,
    val estimatedTimeMins: Int? = null,
    val state: String,
    val otpAttemptsLeft: Int,
    val otpVerified: Boolean,
    val isCod: Boolean,
    val codAmount: Double? = null,
    val codCollectedAmount: Double? = null,
    val codReconciled: Boolean,
    val telemetry: TelemetryState?,
    val history: List<StateHistoryItem>
) {
    val canonicalState: CanonicalDeliveryState
        get() = try {
            CanonicalDeliveryState.valueOf(state.uppercase())
        } catch (e: Exception) {
            CanonicalDeliveryState.ASSIGNED
        }

    val maskedCustomerPhone: String
        get() {
            if (customerPhone.length > 5) {
                return customerPhone.take(3) + "****" + customerPhone.takeLast(3)
            }
            return "*******"
        }
}

data class TelemetryState(
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val heading: Float? = null,
    val accuracyMeters: Float,
    val sequenceNumber: Long,
    val serverTimestamp: Long,
    val clientTimestamp: Long,
    val isStale: Boolean
)

data class StateHistoryItem(
    val state: String,
    val timestamp: String
)

data class RiderProfile(
    val riderId: String,
    val name: String,
    val phone: String,
    val vehicleNumber: String,
    val rating: Double? = null,
    val completedToday: Int? = null,
    val earningsTodayFormatted: String? = null,
    val shiftStatus: String? = null,
    val assignedHub: String? = null,
    val tier: String? = null
)

sealed class ActiveOfferResult {
    data class Success(val offer: ServerOffer) : ActiveOfferResult()
    object None : ActiveOfferResult()
    data class Error(val message: String, val throwable: Throwable? = null) : ActiveOfferResult()
}

data class RiderLocationUpdate(
    val deliveryId: String,
    val sequenceNumber: Long,
    val riderId: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val heading: Float? = null,
    val accuracyMeters: Float,
    val timestamp: Long = System.currentTimeMillis()
)

data class ServerOffer(
    val offerId: String,
    val eventId: String,
    val notificationId: String,
    val deliveryId: String,
    val orderId: String,
    val riderId: String? = null,
    val status: String = "CREATED",
    val earningsAmount: Double,
    val pickupDistanceKm: Double? = null,
    val deliveryDistanceKm: Double,
    val totalDistanceKm: Double,
    val estimatedDurationMins: Int,
    val isCod: Boolean = false,
    val codAmount: Double? = null,
    val customerName: String,
    val customerAddress: String,
    val customerLat: Double,
    val customerLng: Double,
    val merchantName: String,
    val merchantAddress: String,
    val merchantLat: Double,
    val merchantLng: Double,
    val offerCreatedAt: Long,
    val offerExpiresAt: Long,
    val serverTime: Long
) {
    val remainingSeconds: Int
        get() = maxOf(0, ((offerExpiresAt - serverTime) / 1000).toInt())
}

data class RiderNotificationItem(
    val notificationId: String,
    val eventId: String,
    val type: String,
    val category: String,
    val priority: String,
    val riderId: String,
    val orderId: String?,
    val deliveryId: String?,
    val offerId: String?,
    val title: String,
    val body: String,
    val deepLink: String?,
    val createdAt: String,
    val expiresAt: Long?,
    val readAt: String?
)

data class RoutePoint(
    val lat: Double,
    val lng: Double
)

data class DeliveryRouteResult(
    val distanceKm: Double,
    val durationMins: Int,
    val waypoints: List<RoutePoint>,
    val provider: String
)
