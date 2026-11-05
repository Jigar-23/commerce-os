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

    private var pendingIntentOfferId by mutableStateOf<String?>(null)

    private val backgroundPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        startLocationService()
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val bgGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (!bgGranted) {
                backgroundPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                return@registerForActivityResult
            }
        }
        startLocationService()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        try {
            com.google.android.gms.maps.MapsInitializer.initialize(applicationContext, com.google.android.gms.maps.MapsInitializer.Renderer.LATEST, null)
        } catch (_: Exception) {}

        // Set clean white system status bar and navigation bar with dark icons (Zomato/Blinkit style)
        window.statusBarColor = android.graphics.Color.WHITE
        window.navigationBarColor = android.graphics.Color.WHITE
        val insetsController = androidx.core.view.WindowCompat.getInsetsController(window, window.decorView)
        insetsController.isAppearanceLightStatusBars = true
        insetsController.isAppearanceLightNavigationBars = true

        requestRequiredPermissions()
        checkBatteryOptimization()
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
                        },
                        intentOfferId = pendingIntentOfferId,
                        onClearIntentOfferId = { pendingIntentOfferId = null }
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
        val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
        if (!sessionMgr.isLoggedIn() || (!sessionMgr.isShiftOnline() && sessionMgr.getActiveDeliveryId().isBlank())) {
            android.util.Log.d("MainActivity", "Location service not started: rider offline and no active delivery.")
            return
        }
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
        setIntent(intent)
        checkIntentOfferAck(intent)
    }

    private fun checkIntentOfferAck(intent: android.content.Intent?) {
        val offerId = intent?.getStringExtra("offerId")
            ?: intent?.getStringExtra("orderId")
            ?: intent?.getStringExtra("deliveryId")
        if (!offerId.isNullOrBlank()) {
            pendingIntentOfferId = offerId
            val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
            sessionMgr.savePendingOfferId(offerId)
            com.commerceos.rider.util.RiderNotificationManager.cancelOfferNotification(applicationContext, offerId)
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
            if (com.google.firebase.FirebaseApp.getApps(applicationContext).isEmpty()) {
                try {
                    val options = com.google.firebase.FirebaseOptions.Builder()
                        .setApplicationId("1:586430398048:android:cf343d44537786ec2b0d06")
                        .setApiKey("AIzaSyAhNxuYlXoKy_Zq0yl5j1iJmOW1BCLkm8k")
                        .setProjectId("commerce-os-9f737")
                        .setGcmSenderId("586430398048")
                        .build()
                    com.google.firebase.FirebaseApp.initializeApp(applicationContext, options)
                    android.util.Log.d("MainActivity", "FirebaseApp initialized programmatically with production credentials.")
                } catch (initErr: Exception) {
                    android.util.Log.w("MainActivity", "FirebaseApp init fallback warning: ${initErr.message}")
                }
            }

            val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)

            // Strictly enforce store-scoped server-authorized routing: purge global broadcast subscription
            try {
                com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_broadcast")
            } catch (_: Exception) {}

            val assignedStoreId = sessionMgr.getStoreId()
            if (sessionMgr.isLoggedIn() && sessionMgr.isShiftOnline() && assignedStoreId.isNotBlank()) {
                val storeTopic = "riders_store_$assignedStoreId"
                com.google.firebase.messaging.FirebaseMessaging.getInstance().subscribeToTopic(storeTopic)
                    .addOnCompleteListener { subTask ->
                        if (subTask.isSuccessful) {
                            android.util.Log.d("MainActivity", "Successfully subscribed to authorized store topic: $storeTopic")
                        } else {
                            android.util.Log.w("MainActivity", "Failed to subscribe to store topic $storeTopic: ${subTask.exception?.message}")
                        }
                    }
            } else if (assignedStoreId.isNotBlank()) {
                try {
                    com.google.firebase.messaging.FirebaseMessaging.getInstance().unsubscribeFromTopic("riders_store_$assignedStoreId")
                    val notifManager = getSystemService(android.content.Context.NOTIFICATION_SERVICE) as? android.app.NotificationManager
                    notifManager?.cancelAll()
                } catch (_: Exception) {}
            }

            com.google.firebase.messaging.FirebaseMessaging.getInstance().token
                .addOnCompleteListener { task ->
                    if (task.isSuccessful && task.result != null) {
                        val token = task.result
                        android.util.Log.d("MainActivity", "FCM Token retrieved on launch [redacted]")
                        sessionMgr.saveFcmToken(token)

                        val baseUrl = sessionMgr.getBaseUrl()
                        val authToken = sessionMgr.getAuthToken()

                        if (baseUrl.isNotBlank() && authToken.isNotBlank() && sessionMgr.isLoggedIn() && sessionMgr.isShiftOnline()) {
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

    private fun checkBatteryOptimization() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val powerManager = getSystemService(android.content.Context.POWER_SERVICE) as? android.os.PowerManager
            val pkg = packageName
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(pkg)) {
                try {
                    val intent = android.content.Intent(android.provider.Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = android.net.Uri.parse("package:$pkg")
                    }
                    startActivity(intent)
                } catch (_: Exception) {}
            }
        }
    }
}
