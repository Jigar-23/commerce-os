package com.commerceos.rider

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.commerceos.rider.theme.RiderTheme
import com.commerceos.rider.ui.RiderMainScreen
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        // Permissions granted callback - ensure service starts
        startLocationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        requestRequiredPermissions()
        startLocationService()
        initFirebaseMessagingToken()
        checkIntentOfferAck(intent)

        setContent {
            RiderTheme {
                val sessionManager = androidx.compose.runtime.remember { com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext) }
                var isAuthenticated by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf(sessionManager.getAuthToken().isNotBlank()) }
                var loggedProfile by androidx.compose.runtime.remember { androidx.compose.runtime.mutableStateOf<com.commerceos.rider.model.RiderProfile?>(null) }

                if (isAuthenticated) {
                    com.commerceos.rider.ui.RiderMainScreen(
                        profile = loggedProfile,
                        onLogout = {
                            isAuthenticated = false
                            loggedProfile = null
                        }
                    )
                } else {
                    com.commerceos.rider.ui.RiderAuthScreen(
                        onLoginSuccess = { profile ->
                            loggedProfile = profile
                            isAuthenticated = true
                            startLocationService()
                            initFirebaseMessagingToken()
                        }
                    )
                }
            }
        }
    }

    private fun startLocationService() {
        try {
            val intent = android.content.Intent(this, com.commerceos.rider.service.RiderForegroundLocationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun onNewIntent(intent: android.content.Intent?) {
        super.onNewIntent(intent)
        checkIntentOfferAck(intent)
    }

    private fun checkIntentOfferAck(intent: android.content.Intent?) {
        val offerId = intent?.getStringExtra("offerId")
        if (!offerId.isNullOrBlank()) {
            com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(applicationContext, offerId)
            val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
            val baseUrl = sessionMgr.getBaseUrl()
            val authToken = sessionMgr.getAuthToken()
            if (baseUrl.isNotBlank() && authToken.isNotBlank()) {
                val repo = com.commerceos.rider.repository.RiderDeliveryRepository(
                    baseUrlProvider = { baseUrl },
                    authTokenProvider = { authToken }
                )
                kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                    repo.ackOffer(offerId, "RIDER_OPENED")
                }
            }
        }
    }

    private fun initFirebaseMessagingToken() {
        try {
            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val token = task.result
                        android.util.Log.d("MainActivity", "FCM Token retrieved on launch: $token")
                        val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
                        sessionMgr.saveFcmToken(token)

                        val baseUrl = sessionMgr.getBaseUrl()
                        val authToken = sessionMgr.getAuthToken()

                        if (baseUrl.isNotBlank() && authToken.isNotBlank()) {
                            val repo = com.commerceos.rider.repository.RiderDeliveryRepository(
                                baseUrlProvider = { baseUrl },
                                authTokenProvider = { authToken }
                            )
                            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.IO).launch {
                                try {
                                    repo.registerDeviceToken(token)
                                } catch (e: Exception) {
                                    android.util.Log.e("MainActivity", "Failed to register FCM token", e)
                                }
                            }
                        }
                    }
                }
        } catch (e: Exception) {
            android.util.Log.w("MainActivity", "FirebaseMessaging not initialized yet", e)
        }
    }

    private fun requestRequiredPermissions() {
        val permissionsToRequest = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        val missingPermissions = permissionsToRequest.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missingPermissions.isNotEmpty()) {
            permissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
