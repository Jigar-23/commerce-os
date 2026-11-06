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

        Log.d(TAG, "Rider Background Check: isLoggedIn=$isLoggedIn, isOnline=$isOnline, hasRiderId=${authenticatedRiderId != null}")

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
                com.commerceos.rider.util.RiderNotificationManager.showLocationSyncRequiredNotification(applicationContext)
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

        val targetRiderId = (data["riderId"] ?: data["rider_id"] ?: "").trim()
        val isBroadcastOffer = targetRiderId.isBlank() ||
            targetRiderId.equals("ALL", ignoreCase = true) ||
            targetRiderId.equals("broadcast", ignoreCase = true) ||
            targetRiderId.equals("pool", ignoreCase = true)

        val offerStoreId = (data["storeId"] ?: data["store_id"] ?: "").trim()
        val riderStoreId = sessionMgr.getStoreId().trim()

        if (isBroadcastOffer) {
            // Strictly require matching storeId for broadcast / topic offers
            if (!sessionMgr.isStoreEligible(offerStoreId)) {
                Log.w(TAG, "DROPPED_FCM_OFFER: Store mismatch. Offer store: '$offerStoreId', Rider store: '$riderStoreId'")
                return
            }
        } else if (authenticatedRiderId != null && !targetRiderId.equals(authenticatedRiderId, ignoreCase = true)) {
            Log.w(TAG, "DROPPED_FCM_OFFER: Target rider '$targetRiderId' does not match current authenticated rider '$authenticatedRiderId'.")
            return
        }

        val rawOfferId = (data["offerId"] ?: data["id"] ?: "").trim()
        val isGenericTopicPing = action.equals("NEW_OFFER_AVAILABLE", ignoreCase = true) ||
            rawOfferId.isBlank() ||
            remoteMessage.from?.contains("riders_store_") == true

        // If this is a direct unicast offer with complete details, parse directly
        val directOffer = if (!isGenericTopicPing && rawOfferId.isNotBlank()) {
            com.commerceos.rider.model.OfferPayloadValidator.parseAndValidate(data, authenticatedRiderId)
        } else {
            null
        }

        if (directOffer != null) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val wakeLock = powerManager?.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
                "CommerceOsRider:FCMWakeLock"
            )
            wakeLock?.acquire(10000L)

            try {
                // Canonical Pipeline Ingestion (handles single validation, dedup, DEVICE_RECEIVED ack, and notification posting)
                RiderOfferEventPipeline.processValidatedOffer(
                    context = applicationContext,
                    offer = directOffer,
                    source = com.commerceos.rider.util.OfferEventSource.FCM
                )
            } finally {
                if (wakeLock?.isHeld == true) {
                    try {
                        wakeLock.release()
                    } catch (_: Exception) {}
                }
            }
            return
        }

        // Generic NEW_OFFER_AVAILABLE or topic message without complete offer details:
        // Treat as lightweight trigger: asynchronously fetch active authenticated offer details from backend
        Log.i(TAG, "GENERIC_FCM_OFFER_PING: Lightweight trigger received. Asynchronously fetching authenticated active offer from backend.")
        val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
        val wakeLock = powerManager?.newWakeLock(
            android.os.PowerManager.PARTIAL_WAKE_LOCK or android.os.PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "CommerceOsRider:FCMGenericWakeLock"
        )
        wakeLock?.acquire(15000L)

        serviceScope.launch {
            try {
                if (!sessionMgr.isLoggedIn() || !sessionMgr.isShiftOnline()) {
                    Log.i(TAG, "GENERIC_FCM_ABORT: Rider went offline or logged out before active offer fetch.")
                    return@launch
                }

                val repository = sessionMgr.createRepository()
                val activeOfferRes = repository.getActiveOffer()

                val verifiedOffer = when (activeOfferRes) {
                    is com.commerceos.rider.model.ActiveOfferResult.Success -> activeOfferRes.offer
                    else -> if (rawOfferId.isNotBlank()) {
                        when (val byIdRes = repository.fetchOfferById(rawOfferId)) {
                            is com.commerceos.rider.model.ActiveOfferResult.Success -> byIdRes.offer
                            else -> null
                        }
                    } else null
                }

                if (verifiedOffer != null) {
                    Log.i(TAG, "GENERIC_FCM_RESOLVED: Active authenticated offer ${verifiedOffer.offerId} resolved for order ${verifiedOffer.orderId}.")
                    RiderOfferEventPipeline.processValidatedOffer(
                        context = applicationContext,
                        offer = verifiedOffer,
                        source = com.commerceos.rider.util.OfferEventSource.FCM
                    )
                } else {
                    Log.d(TAG, "GENERIC_FCM_NO_OFFER: No active offer found for authenticated rider (may have been claimed or expired).")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error resolving active offer from generic FCM ping", e)
            } finally {
                if (wakeLock?.isHeld == true) {
                    try {
                        wakeLock.release()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    companion object {
        private const val TAG = "CommerceOsFCM"
    }
}
