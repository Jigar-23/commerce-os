package com.commerceos.rider.service

import android.content.Intent
import android.util.Log
import com.commerceos.rider.repository.RiderDeliveryRepository
import com.commerceos.rider.session.RiderSessionManager
import com.commerceos.rider.util.RiderOfferEventPipeline
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class CommerceOsFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received [redacted]")
        val sessionMgr = RiderSessionManager.getInstance(applicationContext)
        sessionMgr.saveFcmToken(token)

        val assignedStoreId = sessionMgr.getStoreId().trim()
        sessionMgr.reconcileStoreTopic(assignedStoreId, sessionMgr.isShiftOnline())

        // Strictly purge fleet-wide broadcast subscription
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_broadcast")
        } catch (_: Exception) {}

        val baseUrl = sessionMgr.getBaseUrl()
        val authToken = sessionMgr.getAuthToken()

        if (baseUrl.isNotBlank() && authToken.isNotBlank() && sessionMgr.isLoggedIn() && sessionMgr.isShiftOnline()) {
            val repository = RiderDeliveryRepository(
                baseUrlProvider = { baseUrl },
                authTokenProvider = { authToken }
            )
            serviceScope.launch {
                try {
                    repository.registerDeviceToken(token)
                    Log.d(TAG, "Successfully registered FCM token with backend dispatch engine")
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to register FCM token with backend", e)
                }
            }
        }
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Message received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isEmpty()) return

        val sessionMgr = RiderSessionManager.getInstance(applicationContext)
        val isLoggedIn = sessionMgr.isLoggedIn()
        val isOnline = sessionMgr.isShiftOnline()
        val authenticatedRiderId = sessionMgr.getRiderId().takeIf { it.isNotBlank() }

        Log.d(TAG, "Rider Background Check: isLoggedIn=$isLoggedIn, isOnline=$isOnline, riderId=$authenticatedRiderId")

        // Handle Silent Watchdog PING_LOCATION Wakeups
        val action = data["action"] ?: data["type"] ?: ""
        if (action.equals("PING_LOCATION", ignoreCase = true) || action.equals("WAKEUP_LOCATION", ignoreCase = true)) {
            if (!isLoggedIn || (!isOnline && sessionMgr.getActiveDeliveryId().isBlank())) {
                Log.i(TAG, "RECEIVED_FCM_PING_LOCATION: Ignored because rider is offline or logged out.")
                return
            }
            Log.i(TAG, "RECEIVED_FCM_PING_LOCATION: Watchdog requested location wakeup.")
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = powerManager?.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CommerceOsRider:FCMPingWakeLock"
            )
            wakeLock?.acquire(5000L)
            try {
                val serviceIntent = Intent(applicationContext, RiderForegroundLocationService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    startForegroundService(serviceIntent)
                } else {
                    startService(serviceIntent)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to start RiderForegroundLocationService on FCM ping", e)
            } finally {
                if (wakeLock?.isHeld == true) {
                    try { wakeLock.release() } catch (_: Exception) {}
                }
            }
            return
        }

        // Strictly enforce: Notifications are shown ONLY if the rider is logged in AND status is ONLINE
        if (!isLoggedIn) {
            Log.w(TAG, "DROPPED_FCM_NOTIFICATION: Rider is logged out. Suppressing all alerts and discarding message.")
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_broadcast")
                val notifManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notifManager.cancelAll()
            } catch (_: Exception) {}
            return
        }

        if (!isOnline) {
            Log.w(TAG, "DROPPED_FCM_NOTIFICATION: Rider is logged in but status is OFFLINE. Suppressing delivery notification.")
            try {
                val notifManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notifManager.cancelAll()
            } catch (_: Exception) {}
            return
        }

        val targetRiderId = data["riderId"] ?: data["rider_id"] ?: ""
        val isBroadcastOffer = targetRiderId.equals("ALL", ignoreCase = true) ||
            targetRiderId.equals("broadcast", ignoreCase = true) ||
            targetRiderId.equals("pool", ignoreCase = true) ||
            targetRiderId.isBlank()

        val offerStoreId = (data["storeId"] ?: data["store_id"] ?: "").trim()
        val riderStoreId = sessionMgr.getStoreId().trim()

        if (isBroadcastOffer) {
            // Strictly require non-blank matching storeId for broadcast offers
            if (offerStoreId.isBlank() || riderStoreId.isBlank() || !offerStoreId.equals(riderStoreId, ignoreCase = true)) {
                Log.w(TAG, "DROPPED_FCM_OFFER: Store mismatch or unassigned rider. Offer store: '$offerStoreId', Rider store: '$riderStoreId'")
                return
            }
        } else if (authenticatedRiderId != null && !targetRiderId.equals(authenticatedRiderId, ignoreCase = true)) {
            Log.w(TAG, "DROPPED_FCM_OFFER: Target rider $targetRiderId does not match current rider $authenticatedRiderId.")
            return
        }

        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "CommerceOsRider:FCMWakeLock"
        )
        wakeLock?.acquire(10000L)

        try {
            // Single Canonical Pipeline Ingestion (handles single validation, dedup, DEVICE_RECEIVED ack, and notification posting)
            RiderOfferEventPipeline.processIncomingFcmOffer(
                context = applicationContext,
                data = data,
                authenticatedRiderId = authenticatedRiderId
            )
        } finally {
            if (wakeLock?.isHeld == true) {
                try {
                    wakeLock.release()
                } catch (_: Exception) {}
            }
        }
    }

    companion object {
        private const val TAG = "CommerceOsFCM"
    }
}
