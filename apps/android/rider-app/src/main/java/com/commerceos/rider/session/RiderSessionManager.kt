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
        if (!saved.isNullOrBlank()) return saved
        val buildConfigUrl = com.commerceos.rider.BuildConfig.API_BASE_URL
        if (buildConfigUrl.isNotBlank()) return buildConfigUrl
        return ""
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

    fun clearSession() {
        secureStore.clear(KEY_AUTH_TOKEN)
        secureStore.clear(KEY_REFRESH_TOKEN)
        prefs.edit().clear().apply()
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

    fun getCachedDeviceToken(): String {
        return getFcmToken()
    }

    companion object {
        private const val PREFS_NAME = "commerce_rider_session_config"
        private const val KEY_AUTH_TOKEN = "rider_jwt_access_token_secure"
        private const val KEY_REFRESH_TOKEN = "rider_jwt_refresh_token_secure"
        private const val KEY_BASE_URL = "rider_api_base_url"
        private const val KEY_FCM_TOKEN = "rider_fcm_device_token"
        private const val KEY_RIDER_ID = "rider_authenticated_id"

        @Volatile
        private var INSTANCE: RiderSessionManager? = null

        fun getInstance(context: Context): RiderSessionManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: RiderSessionManager(context).also { INSTANCE = it }
            }
        }
    }
}
