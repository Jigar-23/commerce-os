package com.commerceos.android.service

import android.content.Context
import android.util.Log
import com.commerceos.android.model.RegisterDeviceTokenRequest
import com.commerceos.android.network.NetworkClient
import com.commerceos.android.notifications.CustomerNotificationManager
import com.google.firebase.messaging.FirebaseMessaging
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Handles incoming Firebase Cloud Messaging push events and device token refreshes.
 * Subscribes customer devices to broadcast topics ("all_devices" and "customers")
 * and presents rich heads-up notifications with deep linking and inbox sync.
 */
class CustomerFirebaseMessagingService : FirebaseMessagingService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "New FCM Token received for customer device")

        // Persist token locally
        val prefs = applicationContext.getSharedPreferences(PREFS_FCM, Context.MODE_PRIVATE)
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()

        // Subscribe to broadcast topics for zero-cost fanout
        subscribeTopics()

        // Sync with backend if customer session is authenticated
        syncTokenWithBackend(token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "FCM Push Received from: ${remoteMessage.from}")

        val data = remoteMessage.data
        if (data.isEmpty()) {
            remoteMessage.notification?.let { notif ->
                CustomerNotificationManager.postBroadcastNotification(
                    context = applicationContext,
                    broadcastId = UUID.randomUUID().toString(),
                    title = notif.title ?: "Commerce OS",
                    body = notif.body ?: "",
                    category = "ANNOUNCEMENT"
                )
            }
            return
        }

        val title = data["title"] ?: remoteMessage.notification?.title ?: "Commerce OS"
        val body = data["body"] ?: remoteMessage.notification?.body ?: ""
        val broadcastId = data["broadcastId"] ?: data["notificationId"] ?: UUID.randomUUID().toString()
        val category = data["category"] ?: "ANNOUNCEMENT"
        val deepLink = data["deepLink"] ?: data["deeplink"]
        val imageUrl = data["imageUrl"]

        Log.d(TAG, "Processing broadcast notification: title='$title', category='$category', deepLink='$deepLink'")

        CustomerNotificationManager.postBroadcastNotification(
            context = applicationContext,
            broadcastId = broadcastId,
            title = title,
            body = body,
            category = category,
            deepLink = deepLink,
            imageUrl = imageUrl
        )
    }

    companion object {
        private const val TAG = "CustomerFCMService"
        const val PREFS_FCM = "commerce_os_fcm"
        const val KEY_FCM_TOKEN = "fcm_device_token"

        fun subscribeTopics() {
            try {
                FirebaseMessaging.getInstance().subscribeToTopic("all_devices")
                FirebaseMessaging.getInstance().subscribeToTopic("customers")
                Log.d(TAG, "Successfully subscribed to FCM topics [all_devices, customers]")
            } catch (e: Exception) {
                Log.w(TAG, "Failed subscribing to FCM topics", e)
            }
        }

        fun syncTokenWithBackend(token: String) {
            val currentAuthToken = try {
                NetworkClient.authTokenProvider()
            } catch (_: Exception) {
                ""
            }

            if (currentAuthToken.isNotBlank()) {
                CoroutineScope(Dispatchers.IO).launch {
                    try {
                        NetworkClient.customerApi.registerDeviceToken(
                            RegisterDeviceTokenRequest(deviceToken = token, platform = "ANDROID")
                        )
                        Log.d(TAG, "Customer device token synced to backend successfully.")
                    } catch (e: Exception) {
                        Log.w(TAG, "Device token sync deferred or failed: ${e.message}")
                    }
                }
            }
        }
    }
}
