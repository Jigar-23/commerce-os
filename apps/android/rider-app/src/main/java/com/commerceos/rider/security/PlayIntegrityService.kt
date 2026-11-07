package com.commerceos.rider.security

import android.content.Context
import android.util.Log
import com.google.android.play.core.integrity.IntegrityManagerFactory
import com.google.android.play.core.integrity.IntegrityTokenRequest
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

class PlayIntegrityService private constructor(context: Context) {
    private val appContext = context.applicationContext
    private val integrityManager = IntegrityManagerFactory.create(appContext)

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
        return try {
            suspendCancellableCoroutine { continuation ->
                integrityManager.requestIntegrityToken(
                    IntegrityTokenRequest.builder()
                        .setNonce(requestHash)
                        .build()
                ).addOnSuccessListener { response ->
                    val token = response.token()
                    if (continuation.isActive) {
                        continuation.resume(token)
                    }
                }.addOnFailureListener { err ->
                    Log.w(TAG, "Live Play Integrity token request failed: ${err.message}")
                    if (continuation.isActive) {
                        continuation.resume(null)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Exception requesting Play Integrity token: ${e.message}")
            null
        }
    }
}
