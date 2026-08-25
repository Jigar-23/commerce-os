package com.commerceos.android.rider

/**
 * Legacy Adapter — The Rider Location Service is housed natively inside the standalone Rider App (com.commerceos.rider).
 */
object RiderLocationService {
    fun getPendingOfflineQueueCount(): Int = 0
    fun updateDeliverySession(deliveryId: String, riderId: String) {}
    fun getOfflineQueueCount(): Int = 0
    fun clearDeliverySession() {}
}

typealias RiderForegroundLocationService = RiderLocationService

