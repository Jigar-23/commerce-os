package com.commerceos.rider.security

import android.content.Context
import android.util.Log
import com.commerceos.rider.session.RiderSessionManager
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlayIntegrityService private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val integrityManager = IntegrityManagerFactory.create(appContext)
    private val sessionManager = RiderSessionManager.getInstance(appContext)

    companion object {
        private const val TAG = "PlayIntegrityService"
        @Volatile
        private var instance: PlayIntegrityService? = null

        fun getInstance(context: Context): PlayIntegrityService {
            return instance ?: synchronized(this) {
                instance ?: PlayIntegrityService(context).also { instance = it }
            }
        }
    }

    suspend fun requestToken(requestHash: String): String? {
        val cached = sessionManager.getPlayIntegrityToken()
        return try {
            suspendCancellableCoroutine { continuation ->
                integrityManager.requestIntegrityToken(
                    IntegrityTokenRequest.builder()
                        .setNonce(requestHash)
                        .build()
                ).addOnSuccessListener { response ->
                    val token = response.token()
                    if (!token.isNullOrBlank()) {
                        sessionManager.savePlayIntegrityToken(token)
                    }
                    if (continuation.isActive) {
                        continuation.resume(token)
                    }
                }.addOnFailureListener { err ->
                    Log.w(TAG, "Live Play Integrity token request failed: ${err.message}; falling back to cached token")
                    if (continuation.isActive) {
                        continuation.resume(cached)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception requesting Play Integrity token: ${e.message}")
            cached
        }
    }
}
