package com.commerceos.android.session

import android.content.Context
import com.commerceos.android.BuildConfig
import com.commerceos.android.network.NetworkClient
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionState(
    val authenticatedCustomerId: String = "",
    val phone: String = "",
    val authToken: String = "",
    val refreshToken: String = "",
    val isAuthenticated: Boolean = false
)

/**
 * Single owner of the auth session. Tokens are persisted AES/GCM-encrypted in
 * AndroidKeyStore (never plaintext SharedPreferences); [refreshToken] is retained
 * so a signed-in session can recover from an expired access token.
 *
 * This object is the ONLY writer of session credentials and installs the token /
 * refresh providers on [NetworkClient] at construction. UI and ViewModels react to
 * the exposed [StateFlow]; they never mutate the session or touch storage.
 */
class SessionManager(context: Context) {
    private val secureStore = SecureCredentialStore(context)
    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _session = MutableStateFlow(
        SessionState(
            authenticatedCustomerId = prefs.getString(KEY_CUSTOMER_ID, "") ?: "",
            phone = prefs.getString(KEY_PHONE, "") ?: "",
            authToken = secureStore.read(KEY_TOKEN) ?: "",
            refreshToken = secureStore.read(KEY_REFRESH_TOKEN) ?: "",
            isAuthenticated = !(secureStore.read(KEY_TOKEN) ?: "").isBlank()
        )
    )
    val session: StateFlow<SessionState> = _session.asStateFlow()

    init {
        val savedUrl = prefs.getString(KEY_BASE_URL, null)
        if (!savedUrl.isNullOrBlank()) {
            if (savedUrl.contains("192.168.") || savedUrl.contains("10.0.2.2") || savedUrl.contains("127.0.0.1") || savedUrl.contains("localhost")) {
                NetworkClient.baseUrl = BuildConfig.API_BASE_URL
                prefs.edit().putString(KEY_BASE_URL, BuildConfig.API_BASE_URL).apply()
            } else {
                NetworkClient.baseUrl = savedUrl
            }
        } else {
            NetworkClient.baseUrl = BuildConfig.API_BASE_URL
        }
        NetworkClient.authTokenProvider = { _session.value.authToken }
        NetworkClient.refreshTokenProvider = { _session.value.refreshToken }
        NetworkClient.onSessionRefreshed = { access: String, refresh: String -> updateTokens(access, refresh) }
        NetworkClient.onSessionExpired = { logout() }
    }

    fun setBaseUrl(url: String) {
        val clean = url.trim().trimEnd('/')
        prefs.edit().putString(KEY_BASE_URL, clean).apply()
        NetworkClient.baseUrl = clean
    }

    fun login(customerId: String, phone: String, accessToken: String, refreshToken: String) {
        prefs.edit()
            .putString(KEY_CUSTOMER_ID, customerId)
            .putString(KEY_PHONE, phone)
            .apply()
        secureStore.save(KEY_TOKEN, accessToken.orEmpty())
        secureStore.save(KEY_REFRESH_TOKEN, refreshToken.orEmpty())
        _session.value = SessionState(
            authenticatedCustomerId = customerId,
            phone = phone,
            authToken = accessToken.orEmpty(),
            refreshToken = refreshToken.orEmpty(),
            isAuthenticated = true
        )
    }

    private fun updateTokens(accessToken: String, refreshToken: String) {
        secureStore.save(KEY_TOKEN, accessToken.orEmpty())
        secureStore.save(KEY_REFRESH_TOKEN, refreshToken.orEmpty())
        _session.value = _session.value.copy(
            authToken = accessToken.orEmpty(),
            refreshToken = refreshToken.orEmpty()
        )
    }

    /** Clears session keys and their Keystore entries only — never prefs.clear(). */
    fun logout() {
        prefs.edit().remove(KEY_CUSTOMER_ID).remove(KEY_PHONE).apply()
        secureStore.deleteKeyAndValue(KEY_TOKEN)
        secureStore.deleteKeyAndValue(KEY_REFRESH_TOKEN)
        _session.value = SessionState()
    }

    companion object {
        private const val PREFS_NAME = "commerce_os_prefs"
        private const val KEY_CUSTOMER_ID = "auth_customer_id"
        private const val KEY_PHONE = "auth_phone"
        private const val KEY_TOKEN = "auth_access_token"
        private const val KEY_REFRESH_TOKEN = "auth_refresh_token"
        private const val KEY_BASE_URL = "custom_api_base_url"
    }
}
