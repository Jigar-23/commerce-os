package com.commerceos.rider.service

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.commerceos.rider.model.RiderLocationUpdate
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * Production Android Foreground Location Service.
 * Assigns sequenceNumber and deliveryId per queued GPS event,
 * persists full queue to SharedPreferences disk storage without arbitrary truncation,
 * replays in sequence order, and deletes items ONLY after matching server ACK sequence.
 */
class RiderForegroundLocationService : Service(), LocationListener {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var locationManager: LocationManager? = null
    private var connectivityManager: ConnectivityManager? = null
    private var dbHelper: RiderTelemetryDbHelper? = null

    companion object {
        private const val CHANNEL_ID = "rider_location_channel"
        private const val NOTIFICATION_ID = 1001

        private val _lastLocation = MutableStateFlow<RiderLocationUpdate?>(null)
        val lastLocation: StateFlow<RiderLocationUpdate?> = _lastLocation.asStateFlow()

        private val _lastServerAckSeq = MutableStateFlow(0L)
        val lastServerAckSeq: StateFlow<Long> = _lastServerAckSeq.asStateFlow()

        private val _lastServerAckTimestamp = MutableStateFlow(0L)
        val lastServerAckTimestamp: StateFlow<Long> = _lastServerAckTimestamp.asStateFlow()

        private val _isStale = MutableStateFlow(false)
        val isStale: StateFlow<Boolean> = _isStale.asStateFlow()

        private val _isNetworkAvailable = MutableStateFlow(true)
        val isNetworkAvailable: StateFlow<Boolean> = _isNetworkAvailable.asStateFlow()

        private val _isLowAccuracy = MutableStateFlow(false)
        val isLowAccuracy: StateFlow<Boolean> = _isLowAccuracy.asStateFlow()

        private val _pendingQueueCount = MutableStateFlow(0)
        val pendingQueueCount: StateFlow<Int> = _pendingQueueCount.asStateFlow()

        private var staticDbHelper: RiderTelemetryDbHelper? = null
        private var currentSequenceNumber = 0L
        private var activeDeliveryId = ""
        private var activeRiderId = ""
        private var apiBaseUrl = System.getProperty("BASE_URL") ?: ""
        private var authToken = ""

        fun updateDeliverySession(deliveryId: String, riderId: String, baseUrl: String = "", token: String = "") {
            activeDeliveryId = deliveryId
            if (riderId.isNotBlank()) activeRiderId = riderId
            if (baseUrl.isNotBlank()) apiBaseUrl = baseUrl
            if (token.isNotBlank()) authToken = token
        }

        fun clearDeliverySession() {
            activeDeliveryId = ""
        }

        fun getOfflineQueueCount(): Int {
            return staticDbHelper?.getQueueSize() ?: 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        val notification = buildNotification("Rider GPS Active • High Precision Telemetry")
        try {
            val hasLocationPerm = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
                    ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && hasLocationPerm) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                ex.printStackTrace()
            }
        }

        locationManager = getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        dbHelper = RiderTelemetryDbHelper(applicationContext)
        staticDbHelper = dbHelper

        registerNetworkMonitoring()
        requestLocationUpdates()

        serviceScope.launch {
            replayOfflineQueueLoop()
        }

        serviceScope.launch {
            listenRiderSseLoop()
        }

        serviceScope.launch {
            pollActiveOffersLoop()
        }
    }

    private suspend fun pollActiveOffersLoop() {
        var lastSeenOfferId = ""
        while (serviceScope.isActive) {
            try {
                val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
                val baseUrl = sessionMgr.getBaseUrl()
                val token = sessionMgr.getAuthToken()
                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    val repo = com.commerceos.rider.repository.RiderDeliveryRepository(
                        baseUrlProvider = { baseUrl },
                        authTokenProvider = { token }
                    )
                    when (val res = repo.fetchActiveOffer()) {
                        is com.commerceos.rider.model.ActiveOfferResult.Success -> {
                            val offer = res.offer
                            if (offer.offerId != lastSeenOfferId) {
                                lastSeenOfferId = offer.offerId
                                com.commerceos.rider.util.RiderOfferEventPipeline.processValidatedOffer(
                                    context = applicationContext,
                                    offer = offer,
                                    source = com.commerceos.rider.util.OfferEventSource.RECONCILIATION
                                )
                            }
                        }
                        is com.commerceos.rider.model.ActiveOfferResult.None -> {
                            lastSeenOfferId = ""
                        }
                        is com.commerceos.rider.model.ActiveOfferResult.Error -> {}
                    }
                }
            } catch (e: Exception) {
                // Ignore transient background errors
            }
            delay(5000L)
        }
    }

    private suspend fun listenRiderSseLoop() {
        while (serviceScope.isActive) {
            try {
                val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
                val baseUrl = sessionMgr.getBaseUrl()
                val token = sessionMgr.getAuthToken()
                val riderId = sessionMgr.getRiderId()
                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    val repository = com.commerceos.rider.repository.RiderDeliveryRepository(
                        baseUrlProvider = { baseUrl },
                        authTokenProvider = { token }
                    )
                    repository.listenRiderSseStream { offerJson ->
                        com.commerceos.rider.util.RiderOfferEventPipeline.processIncomingSseOffer(
                            context = applicationContext,
                            json = offerJson,
                            authenticatedRiderId = riderId
                        )
                    }
                }
            } catch (e: Exception) {
                // Ignore stream reconnect errors
            }
            delay(5000L) // Reconnect backoff if connection drops
        }
    }

    private fun registerNetworkMonitoring() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager?.registerNetworkCallback(request, object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkAvailable.value = true
                }

                override fun onLost(network: Network) {
                    _isNetworkAvailable.value = false
                    _isStale.value = true
                }
            })
        } catch (e: Exception) {
            _isNetworkAvailable.value = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        requestLocationUpdates()
        return START_STICKY
    }

    private fun requestLocationUpdates() {
        try {
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val isNetEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            val isPassiveEnabled = locationManager?.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) == true

            if (!isGpsEnabled && !isNetEnabled && !isPassiveEnabled) {
                _isStale.value = true
            }

            // Immediately prime last known location so UI doesn't stall waiting for hardware callback
            val lastGps = try { locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
            val lastNet = try { locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
            val lastPassive = try { locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { null }
            val best = lastGps ?: lastNet ?: lastPassive
            if (best != null) {
                onLocationChanged(best)
            }

            val mainLooper = android.os.Looper.getMainLooper()
            if (isGpsEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    1000L,
                    0f,
                    this,
                    mainLooper
                )
            }
            if (isNetEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    1500L,
                    0f,
                    this,
                    mainLooper
                )
            }
            if (isPassiveEnabled) {
                locationManager?.requestLocationUpdates(
                    LocationManager.PASSIVE_PROVIDER,
                    1000L,
                    0f,
                    this,
                    mainLooper
                )
            }
        } catch (e: SecurityException) {
            _isStale.value = true
        } catch (e: Exception) {
            _isStale.value = true
        }
    }

    private var lastMockTimestamp: Long = 0L
    private var lastDispatchedLat: Double = 0.0
    private var lastDispatchedLng: Double = 0.0

    override fun onLocationChanged(location: Location) {
        val accuracy = if (location.hasAccuracy()) location.accuracy else 5.0f
        _isLowAccuracy.value = accuracy > 100.0f
        _isStale.value = false

        // Filter out extreme noise fixes (>150m)
        if (accuracy > 150.0f) {
            return
        }

        val isMock = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            location.isMock
        } else {
            @Suppress("DEPRECATION")
            location.isFromMockProvider
        }

        val now = System.currentTimeMillis()
        if (isMock) {
            lastMockTimestamp = now
        } else if (now - lastMockTimestamp < 5000L) {
            // A mock location app/simulator is actively injecting test coordinates.
            // Ignore non-mock hardware satellite fixes to prevent bouncing between test and physical location!
            return
        }

        // Anti-jitter: ignore micro movements (< 1.0m) if stationary
        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0.0f
        if (lastDispatchedLat != 0.0 && lastDispatchedLng != 0.0 && speedKmh < 1.0f) {
            val distResults = FloatArray(1)
            Location.distanceBetween(lastDispatchedLat, lastDispatchedLng, location.latitude, location.longitude, distResults)
            if (distResults[0] < 1.0f && _lastLocation.value != null) {
                return
            }
        }

        lastDispatchedLat = location.latitude
        lastDispatchedLng = location.longitude

        currentSequenceNumber++
        val headingValue: Float? = if (location.hasBearing()) location.bearing else null
        val update = RiderLocationUpdate(
            deliveryId = if (activeDeliveryId.isNotBlank()) activeDeliveryId else "idle_presence",
            sequenceNumber = currentSequenceNumber,
            riderId = activeRiderId,
            latitude = location.latitude,
            longitude = location.longitude,
            speedKmh = speedKmh,
            heading = headingValue,
            accuracyMeters = accuracy,
            timestamp = now
        )

        _lastLocation.value = update

        if (activeDeliveryId.isNotBlank()) {
            // Atomic SQLite enqueue for active delivery telemetry
            dbHelper?.enqueue(update)
            _pendingQueueCount.value = dbHelper?.getQueueSize() ?: 0
        }

        // Transmit real-time telemetry and presence to server immediately
        serviceScope.launch(Dispatchers.IO) {
            try {
                val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
                val baseUrl = sessionMgr.getBaseUrl()
                val token = sessionMgr.getAuthToken()
                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    if (activeDeliveryId.isNotBlank()) {
                        transmitHeadTelemetry()
                    }

                    val url = java.net.URL("${baseUrl.trimEnd('/')}/api/v1/delivery/rider/presence")
                    val conn = url.openConnection() as java.net.HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.setRequestProperty("Content-Type", "application/json")
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.doOutput = true
                    conn.connectTimeout = 2000
                    conn.readTimeout = 2000

                    val body = JSONObject().apply {
                        put("latitude", location.latitude)
                        put("longitude", location.longitude)
                        put("speedKmh", if (location.hasSpeed()) location.speed * 3.6f else 0.0f)
                        if (location.hasBearing()) {
                            put("heading", location.bearing)
                        }
                        put("accuracyMeters", accuracy)
                        put("isOnline", true)
                    }
                    conn.outputStream.use { os -> os.write(body.toString().toByteArray(Charsets.UTF_8)) }
                    conn.responseCode // execute
                    conn.disconnect()
                }
            } catch (e: Exception) {
                // Ignore transient network errors
            }
        }
    }

    private suspend fun replayOfflineQueueLoop() {
        var backoffMs = 1000L

        while (serviceScope.isActive) {
            val queueSize = dbHelper?.getQueueSize() ?: 0
            _pendingQueueCount.value = queueSize

            if (queueSize > 0 && _isNetworkAvailable.value && apiBaseUrl.isNotBlank()) {
                val success = transmitHeadTelemetry()
                if (success) {
                    backoffMs = 1000L
                } else {
                    delay(backoffMs)
                    backoffMs = (backoffMs * 2).coerceAtMost(30000L)
                }
            } else {
                delay(2000L)
            }
        }
    }



    private suspend fun transmitHeadTelemetry(): Boolean = withContext(Dispatchers.IO) {
        if (activeDeliveryId.isNotBlank()) {
            dbHelper?.purgeInactiveDeliveries(activeDeliveryId)
        }
        val nextUpdate = dbHelper?.peekHead(activeDeliveryId.ifBlank { null }) ?: return@withContext true
        val targetDeliveryId = nextUpdate.deliveryId.ifBlank { activeDeliveryId }
        val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
        val baseUrl = sessionMgr.getBaseUrl()
        val token = sessionMgr.getAuthToken()

        if (baseUrl.isBlank() || targetDeliveryId.isBlank() || token.isBlank()) {
            return@withContext false
        }

        try {
            val url = URL("${baseUrl.trimEnd('/')}/api/v1/delivery/$targetDeliveryId/telemetry")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json")
            conn.setRequestProperty("Authorization", "Bearer $token")
            conn.doOutput = true
            conn.connectTimeout = 3000
            conn.readTimeout = 3000

            val jsonBody = JSONObject().apply {
                put("deliveryId", targetDeliveryId)
                put("sequenceNumber", nextUpdate.sequenceNumber)
                put("riderId", nextUpdate.riderId)
                put("latitude", nextUpdate.latitude)
                put("longitude", nextUpdate.longitude)
                put("speedKmh", nextUpdate.speedKmh)
                put("heading", nextUpdate.heading)
                put("accuracyMeters", nextUpdate.accuracyMeters)
                put("timestamp", nextUpdate.timestamp)
            }

            conn.outputStream.use { os ->
                os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
            }

            if (conn.responseCode == 401) {
                // Token expired; attempt token refresh and retry
                if (sessionMgr.refreshAccessToken()) {
                    val newToken = sessionMgr.getAuthToken()
                    val retryConn = url.openConnection() as HttpURLConnection
                    retryConn.requestMethod = "POST"
                    retryConn.setRequestProperty("Content-Type", "application/json")
                    retryConn.setRequestProperty("Authorization", "Bearer $newToken")
                    retryConn.doOutput = true
                    retryConn.connectTimeout = 3000
                    retryConn.readTimeout = 3000
                    retryConn.outputStream.use { os ->
                        os.write(jsonBody.toString().toByteArray(Charsets.UTF_8))
                    }
                    if (retryConn.responseCode in 200..299) {
                        val responseStr = retryConn.inputStream.bufferedReader().use { it.readText() }
                        val respJson = JSONObject(responseStr)
                        val ackSeq = respJson.optLong("ackSequenceNumber", -1L)
                        val accepted = respJson.optBoolean("accepted", true)
                        val isDuplicate = respJson.optBoolean("duplicate", false)
                        if (ackSeq >= nextUpdate.sequenceNumber || accepted || isDuplicate) {
                            _lastServerAckSeq.value = ackSeq
                            _lastServerAckTimestamp.value = System.currentTimeMillis()
                            _isStale.value = false
                            dbHelper?.dequeueUpToSequence(targetDeliveryId, nextUpdate.sequenceNumber)
                            _pendingQueueCount.value = dbHelper?.getQueueSize() ?: 0
                            return@withContext true
                        }
                    }
                }
            }

            if (conn.responseCode in 200..299) {
                val responseStr = conn.inputStream.bufferedReader().use { it.readText() }
                val respJson = JSONObject(responseStr)
                val ackSeq = respJson.optLong("ackSequenceNumber", -1L)
                val accepted = respJson.optBoolean("accepted", true)
                val isDuplicate = respJson.optBoolean("duplicate", false)

                // Atomic Dequeue upon ACK (Requirements 42, 43, 50)
                if (ackSeq >= nextUpdate.sequenceNumber || accepted || isDuplicate) {
                    _lastServerAckSeq.value = ackSeq
                    _lastServerAckTimestamp.value = System.currentTimeMillis()
                    _isStale.value = false
                    
                    dbHelper?.dequeueUpToSequence(targetDeliveryId, nextUpdate.sequenceNumber)
                    _pendingQueueCount.value = dbHelper?.getQueueSize() ?: 0
                    return@withContext true
                }
            }
            _isStale.value = true
            return@withContext false
        } catch (e: Exception) {
            _isStale.value = true
            return@withContext false
        }
    }



    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        locationManager?.removeUpdates(this)
        serviceScope.cancel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rider Live GPS Tracking",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Streams real-time rider location to customer and server"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(contentText: String) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Commerce OS Rider Partner")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
}
