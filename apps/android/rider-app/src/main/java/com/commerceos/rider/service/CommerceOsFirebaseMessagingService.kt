package com.commerceos.rider.service

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
        Log.d(TAG, "New FCM Token received: $token")
        val sessionMgr = RiderSessionManager.getInstance(applicationContext)
        sessionMgr.saveFcmToken(token)

        val baseUrl = sessionMgr.getBaseUrl()
        val authToken = sessionMgr.getAuthToken()

        if (baseUrl.isNotBlank() && authToken.isNotBlank()) {
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
        val authenticatedRiderId = sessionMgr.getRiderId().takeIf { it.isNotBlank() }
        val authToken = sessionMgr.getAuthToken().takeIf { it.isNotBlank() }

        if (authenticatedRiderId == null || authToken == null) {
            Log.w(TAG, "DROPPED_FCM_OFFER: Device is logged out or unauthenticated. Discarding incoming delivery push.")
            return
        }

        // Single Canonical Pipeline Ingestion (handles single validation, dedup, DEVICE_RECEIVED ack, and notification posting)
        RiderOfferEventPipeline.processIncomingFcmOffer(
            context = applicationContext,
            data = data,
            authenticatedRiderId = authenticatedRiderId
        )
    }

    companion object {
        private const val TAG = "CommerceOsFCM"
    }
}
