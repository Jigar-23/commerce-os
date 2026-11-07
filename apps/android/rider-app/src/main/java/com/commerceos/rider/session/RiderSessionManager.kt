package com.commerceos.rider.session

import android.content.Context
import android.content.SharedPreferences
import com.commerceos.rider.model.ActiveOfferResult
import com.commerceos.rider.repository.RiderDeliveryRepository
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class RiderSessionManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val secureStore = RiderSecureCredentialStore(appContext)
    private val prefs = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

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

    fun getPlayIntegrityToken(): String? {
        return secureStore.read("play_integrity_token")
    }

    fun savePlayIntegrityToken(token: String?) {
        if (token.isNullOrBlank()) {
            secureStore.clear("play_integrity_token")
        } else {
            secureStore.save("play_integrity_token", token)
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

    private val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO + kotlinx.coroutines.SupervisorJob())
    private val topicMutex = kotlinx.coroutines.sync.Mutex()
    private var reconciliationGeneration = 0L

    fun isLoggedIn(): Boolean {
        return getAuthToken().isNotBlank() && getRiderId().isNotBlank()
    }

    private fun getTopicRetryPrefs(): SharedPreferences {
        return appContext.getSharedPreferences(TOPIC_RETRY_PREFS, Context.MODE_PRIVATE)
    }

    suspend fun retryPendingUnsubscriptions() = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val retryPrefs = getTopicRetryPrefs()
        val pending = retryPrefs.getStringSet(KEY_PENDING_UNSUB_SET, emptySet<String>())?.toMutableSet() ?: return@withContext
        if (pending.isEmpty()) return@withContext

        val succeeded = mutableSetOf<String>()
        for (topic in pending) {
            try {
                com.google.android.gms.tasks.Tasks.await(
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(topic),
                    3000,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
                succeeded.add(topic)
            } catch (_: Exception) {}
        }
        if (succeeded.isNotEmpty()) {
            pending.removeAll(succeeded)
            retryPrefs.edit().putStringSet(KEY_PENDING_UNSUB_SET, pending).commit()
        }
    }

    suspend fun coordinateLogout(context: Context? = null) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Invalidate any pending reconciliation
        reconciliationGeneration++

        topicMutex.withLock {
            val currentStore = getStoreId().trim()
            val topicsToUnsub = mutableSetOf<String>()
            if (currentStore.isNotBlank()) {
                topicsToUnsub.add("riders_store_$currentStore")
            }
            topicsToUnsub.add("riders_broadcast")

            // Persist durable unsubscription records into dedicated prefs that survive credentials wipe
            val retryPrefs = getTopicRetryPrefs()
            val existing = retryPrefs.getStringSet(KEY_PENDING_UNSUB_SET, emptySet<String>())?.toMutableSet() ?: mutableSetOf<String>()
            existing.addAll(topicsToUnsub)
            retryPrefs.edit().putStringSet(KEY_PENDING_UNSUB_SET, existing).commit()

            // Best-effort unsubscription with timeout
            val succeeded = mutableSetOf<String>()
            for (topic in existing) {
                try {
                    com.google.android.gms.tasks.Tasks.await(
                        com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(topic),
                        3000,
                        java.util.concurrent.TimeUnit.MILLISECONDS
                    )
                    succeeded.add(topic)
                } catch (_: Exception) {}
            }
            if (succeeded.isNotEmpty()) {
                existing.removeAll(succeeded)
                retryPrefs.edit().putStringSet(KEY_PENDING_UNSUB_SET, existing).commit()
            }

            saveShiftOnline(false)
            secureStore.deleteKeyAndValue(KEY_AUTH_TOKEN)
            secureStore.deleteKeyAndValue(KEY_REFRESH_TOKEN)
            saveRiderId("")
            saveStoreId("")
            prefs.edit().clear().commit()
            context?.let { ctx ->
                try {
                    val notifManager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as android.app.NotificationManager
                    notifManager.cancelAll()
                } catch (_: Exception) {}
            }
        }
    }

    fun queuePendingUnsubscription(topic: String) {
        if (topic.isBlank()) return
        try {
            val retryPrefs = getTopicRetryPrefs()
            val existing = retryPrefs.getStringSet(KEY_PENDING_UNSUB_SET, emptySet<String>())?.toMutableSet() ?: mutableSetOf<String>()
            existing.add(topic)
            retryPrefs.edit().putStringSet(KEY_PENDING_UNSUB_SET, existing).commit()
        } catch (_: Exception) {}
    }

    fun removePendingUnsubscription(topic: String) {
        if (topic.isBlank()) return
        try {
            val retryPrefs = getTopicRetryPrefs()
            val existing = retryPrefs.getStringSet(KEY_PENDING_UNSUB_SET, emptySet<String>())?.toMutableSet() ?: mutableSetOf<String>()
            if (existing.remove(topic)) {
                retryPrefs.edit().putStringSet(KEY_PENDING_UNSUB_SET, existing).commit()
            }
        } catch (_: Exception) {}
    }

    fun clearSession(context: Context? = null) {
        val storeId = getStoreId().trim()
        if (storeId.isNotBlank()) {
            val topic = "riders_store_$storeId"
            queuePendingUnsubscription(topic)
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(topic)
            } catch (_: Exception) {}
        }
        kotlinx.coroutines.runBlocking(kotlinx.coroutines.Dispatchers.IO) {
            try {
                coordinateLogout(context)
            } catch (_: Exception) {}
        }
        saveShiftOnline(false)
        secureStore.deleteKeyAndValue(KEY_AUTH_TOKEN)
        secureStore.deleteKeyAndValue(KEY_REFRESH_TOKEN)
        saveRiderId("")
        saveStoreId("")
        prefs.edit().clear().commit()
    }

    suspend fun coordinateTopicSubscription(newStoreId: String?, isOnline: Boolean) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        topicMutex.withLock {
            try {
                retryPendingUnsubscriptions()
            } catch (_: Exception) {}
            val gen = ++reconciliationGeneration
            val newStore = if (newStoreId == null || newStoreId.equals("null", ignoreCase = true)) "" else newStoreId.trim()
            val targetStore = newStore
            val oldStore = getStoreId().trim()

            if (newStore.isBlank()) {
                if (oldStore.isNotBlank()) {
                    val oldTopic = "riders_store_$oldStore"
                    queuePendingUnsubscription(oldTopic)
                    var unsubOk = false
                    for (attempt in 1..3) {
                        try {
                            com.google.android.gms.tasks.Tasks.await(
                                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_store_$oldStore"),
                                3000,
                                java.util.concurrent.TimeUnit.MILLISECONDS
                            )
                            unsubOk = true
                            removePendingUnsubscription(oldTopic)
                            break
                        } catch (e: Exception) {
                            android.util.Log.w("RiderSessionManager", "Failed to unsubscribe: ${e.message}")
                        }
                    }
                    if (unsubOk) {
                        saveStoreId("")
                    }
                } else {
                    saveStoreId("")
                }
                return@withLock
            }

            if (oldStore.isNotBlank() && (oldStore != targetStore || !isOnline)) {
                val oldTopic = "riders_store_$oldStore"
                queuePendingUnsubscription(oldTopic)
                var unsubOk = false
                for (attempt in 1..3) {
                    try {
                        com.google.android.gms.tasks.Tasks.await(
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic(oldTopic),
                            3000,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        unsubOk = true
                        removePendingUnsubscription(oldTopic)
                        break
                    } catch (e: Exception) {
                        android.util.Log.w("RiderSessionManager", "Failed to unsubscribe from old store topic: ${e.message}")
                    }
                }
                if (!unsubOk) {
                    if (oldStore != targetStore) {
                        android.util.Log.e("RiderSessionManager", "Aborting new store subscription because old store unsubscribe failed")
                        return@withLock
                    }
                }
            }

            if (gen != reconciliationGeneration) return@withLock

            saveStoreId(targetStore)

            if (targetStore.isNotBlank() && isOnline && isLoggedIn()) {
                for (attempt in 1..3) {
                    try {
                        com.google.android.gms.tasks.Tasks.await(
                            com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic("riders_store_$targetStore"),
                            3000,
                            java.util.concurrent.TimeUnit.MILLISECONDS
                        )
                        break
                    } catch (e: Exception) {
                        android.util.Log.w("RiderSessionManager", "Failed to subscribe to store topic: ${e.message}")
                    }
                }
            }

            try {
                com.google.android.gms.tasks.Tasks.await(
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_broadcast"),
                    2000,
                    java.util.concurrent.TimeUnit.MILLISECONDS
                )
            } catch (_: Exception) {}
        }
    }

    fun reconcileStoreTopic(newStoreId: String?, isOnline: Boolean) {
        scope.launch {
            coordinateTopicSubscription(newStoreId, isOnline)
        }
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

    fun createRepository(): RiderDeliveryRepository {
        return RiderDeliveryRepository(
            baseUrlProvider = { getBaseUrl() },
            authTokenProvider = { getAuthToken() }
        )
    }

    suspend fun fetchActiveOffer(): ActiveOfferResult {
        if (!isLoggedIn() || !isShiftOnline()) {
            return ActiveOfferResult.None
        }
        return createRepository().getActiveOffer()
    }

    fun isStoreEligible(targetStoreId: String?): Boolean {
        val currentStore = getStoreId().trim()
        val cleanTarget = targetStoreId?.trim() ?: ""
        if (cleanTarget.isBlank()) return true
        if (currentStore.isBlank()) return false
        return currentStore.equals(cleanTarget, ignoreCase = true)
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
        private const val KEY_PENDING_UNSUB_TOPICS = "rider_pending_unsub_topics"
        private const val TOPIC_RETRY_PREFS = "rider_durable_topic_cleanup_prefs"
        private const val KEY_PENDING_UNSUB_SET = "pending_unsub_topics_set"

        @Volatile
        private var INSTANCE: RiderSessionManager? = null

        fun getInstance(context: Context): RiderSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RiderSessionManager(context).also { INSTANCE = it }
            }
        }
    }
}
