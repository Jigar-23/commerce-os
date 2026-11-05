package com.commerceos.rider.session

import android.content.Context
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RiderSessionManager private constructor(context: Context) {

    private val secureStore = RiderSecureCredentialStore(context.applicationContext)
    private val prefs = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getAuthToken(): String {
        return secureStore.read(KEY_AUTH_TOKEN) ?: ""
    }

    fun saveAuthToken(token: String) {
        if (token.isBlank()) {
            secureStore.clear(KEY_AUTH_TOKEN)
        } else {
            secureStore.save(KEY_AUTH_TOKEN, token)
        }
    }

    fun getRefreshToken(): String {
        return secureStore.read(KEY_REFRESH_TOKEN) ?: ""
    }

    fun saveRefreshToken(token: String) {
        if (token.isBlank()) {
            secureStore.clear(KEY_REFRESH_TOKEN)
        } else {
            secureStore.save(KEY_REFRESH_TOKEN, token)
        }
    }

    fun getBaseUrl(): String {
        val saved = prefs.getString(KEY_BASE_URL, null)
        if (!saved.isNullOrBlank() && !saved.contains("127.0.0.1") && !saved.contains("localhost")) {
            return saved.trimEnd('/')
        }
        val buildConfigUrl = com.commerceos.rider.BuildConfig.API_BASE_URL
        if (buildConfigUrl.isNotBlank() && !buildConfigUrl.contains("127.0.0.1") && !buildConfigUrl.contains("localhost")) {
            return buildConfigUrl.trimEnd('/')
        }
        return "https://commerce-os-api.onrender.com"
    }

    fun saveBaseUrl(url: String) {
        prefs.edit().putString(KEY_BASE_URL, url).apply()
    }

    fun refreshAccessToken(): Boolean {
        val refreshToken = getRefreshToken()
        val baseUrl = getBaseUrl()
        if (refreshToken.isBlank() || baseUrl.isBlank()) return false
        return try {
            val url = URL("${baseUrl.trimEnd('/')}/api/v1/auth/refresh")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            val body = JSONObject().put("refreshToken", refreshToken)
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            if (conn.responseCode in 200..299) {
                val resp = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                val newAccessToken = resp.optString("accessToken", "")
                if (newAccessToken.isNotBlank()) {
                    saveAuthToken(newAccessToken)
                    val newRefreshToken = resp.optString("refreshToken", "")
                    if (newRefreshToken.isNotBlank()) saveRefreshToken(newRefreshToken)
                    true
                } else {
                    false
                }
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun isLoggedIn(): Boolean {
        return getAuthToken().isNotBlank() && getRiderId().isNotBlank()
    }

    fun clearSession(context: Context? = null) {
        val currentStore = getStoreId().trim()
        if (currentStore.isNotBlank()) {
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_store_$currentStore")
            } catch (_: Exception) {}
        }
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_broadcast")
        } catch (_: Exception) {}
        saveShiftOnline(false)
        secureStore.deleteKeyAndValue(KEY_AUTH_TOKEN)
        secureStore.deleteKeyAndValue(KEY_REFRESH_TOKEN)
        saveRiderId("")
        prefs.edit().clear().apply()
        context?.let { ctx ->
            try {
                val notifManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                notifManager.cancelAll()
            } catch (_: Exception) {}
        }
    }

    fun reconcileStoreTopic(newStoreId: String?, isOnline: Boolean) {
        val oldStore = getStoreId().trim()
        val newStore = if (newStoreId.equals("null", ignoreCase = true)) "" else (newStoreId?.trim() ?: "")

        if (newStore.isBlank()) {
            // Rider is unassigned or store assignment was removed: clear saved store and unsubscribe
            saveStoreId("")
            if (oldStore.isNotBlank()) {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_store_$oldStore")
                } catch (_: Exception) {}
            }
        } else if (newStore != oldStore) {
            // Store changed to a new store: unsubscribe from old store topic before registering new store
            if (oldStore.isNotBlank()) {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_store_$oldStore")
                        .addOnCompleteListener {
                            saveStoreId(newStore)
                            if (isOnline) {
                                try {
                                    com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("riders_store_$newStore")
                                } catch (_: Exception) {}
                            }
                        }
                } catch (_: Exception) {
                    saveStoreId(newStore)
                    if (isOnline) {
                        try {
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("riders_store_$newStore")
                        } catch (_: Exception) {}
                    }
                }
            } else {
                saveStoreId(newStore)
                if (isOnline) {
                    try {
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("riders_store_$newStore")
                    } catch (_: Exception) {}
                }
            }
        } else {
            // Store confirmed unchanged: presence-gated topic subscription
            saveStoreId(newStore)
            if (!isOnline) {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_store_$newStore")
                } catch (_: Exception) {}
            } else {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("riders_store_$newStore")
                } catch (_: Exception) {}
            }
        }

        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_broadcast")
        } catch (_: Exception) {}
    }

    fun getFcmToken(): String {
        return prefs.getString(KEY_FCM_TOKEN, "") ?: ""
    }

    fun saveFcmToken(token: String) {
        prefs.edit().putString(KEY_FCM_TOKEN, token).apply()
    }

    fun getRiderId(): String {
        return prefs.getString(KEY_RIDER_ID, "") ?: ""
    }

    fun saveRiderId(riderId: String) {
        prefs.edit().putString(KEY_RIDER_ID, riderId).apply()
    }

    fun getStoreId(): String {
        return prefs.getString(KEY_STORE_ID, "") ?: ""
    }

    fun saveStoreId(storeId: String) {
        prefs.edit().putString(KEY_STORE_ID, storeId).apply()
    }

    fun getCachedDeviceToken(): String {
        return getFcmToken()
    }

    fun isShiftOnline(): Boolean {
        if (!isLoggedIn()) return false
        return prefs.getBoolean(KEY_IS_ONLINE, false)
    }

    fun saveShiftOnline(online: Boolean) {
        prefs.edit().putBoolean(KEY_IS_ONLINE, online).apply()
    }

    fun getPendingOfferId(): String? {
        val id = prefs.getString(KEY_PENDING_OFFER_ID, null)
        return if (!id.isNullOrBlank()) id else null
    }

    fun savePendingOfferId(offerId: String?) {
        if (offerId.isNullOrBlank()) {
            prefs.edit().remove(KEY_PENDING_OFFER_ID).apply()
        } else {
            prefs.edit().putString(KEY_PENDING_OFFER_ID, offerId).apply()
        }
    }

    fun getActiveDeliveryId(): String {
        return prefs.getString(KEY_ACTIVE_DELIVERY_ID, "") ?: ""
    }

    fun saveActiveDeliveryId(deliveryId: String) {
        prefs.edit().putString(KEY_ACTIVE_DELIVERY_ID, deliveryId).apply()
    }

    companion object {
        private const val PREFS_NAME = "commerce_rider_session_config"
        private const val KEY_AUTH_TOKEN = "rider_jwt_access_token_secure"
        private const val KEY_REFRESH_TOKEN = "rider_jwt_refresh_token_secure"
        private const val KEY_BASE_URL = "rider_api_base_url"
        private const val KEY_FCM_TOKEN = "rider_fcm_device_token"
        private const val KEY_RIDER_ID = "rider_authenticated_id"
        private const val KEY_STORE_ID = "rider_assigned_store_id"
        private const val KEY_IS_ONLINE = "rider_shift_is_online"
        private const val KEY_PENDING_OFFER_ID = "rider_pending_intent_offer_id"
        private const val KEY_ACTIVE_DELIVERY_ID = "rider_active_delivery_id"

        @Volatile
        private var INSTANCE: RiderSessionManager? = null

        fun getInstance(context: Context): RiderSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RiderSessionManager(context).also { INSTANCE = it }
            }
        }
    }
}
