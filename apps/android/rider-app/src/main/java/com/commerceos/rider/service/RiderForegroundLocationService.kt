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
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.commerceos.rider.model.RiderLocationUpdate
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    private var locationWakeLock: android.os.PowerManager.WakeLock? = null
    private var networkCallback: ConnectivityManager.NetworkCallback? = null
    private var lastDispatchedTime: Long = 0L
    private var lastPresencePostTime: Long = 0L
    private val telemetryTransmissionMutex = Mutex()

    companion object {
        private const val TAG = "RiderLocationService"
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
        @Volatile
        private var activeInstance: RiderForegroundLocationService? = null

        fun updateDeliverySession(deliveryId: String, riderId: String, baseUrl: String = "", token: String = "") {
            activeDeliveryId = deliveryId
            if (riderId.isNotBlank()) activeRiderId = riderId
            if (baseUrl.isNotBlank()) apiBaseUrl = baseUrl
            if (token.isNotBlank()) authToken = token
            if (deliveryId.isNotBlank()) {
                val maxSeq = staticDbHelper?.getMaxSequenceNumber(deliveryId) ?: 0L
                if (maxSeq > currentSequenceNumber) {
                    currentSequenceNumber = maxSeq
                }
            }
            activeInstance?.reconfigureLocationTracking()
        }

        fun clearDeliverySession(deliveryIdToPurge: String? = null) {
            val toPurge = deliveryIdToPurge ?: activeDeliveryId
            if (toPurge.isNotBlank()) {
                staticDbHelper?.purgeDelivery(toPurge)
            }
            activeDeliveryId = ""
            activeInstance?.reconfigureLocationTracking()
        }

        fun getOfflineQueueCount(): Int {
            return staticDbHelper?.getQueueSize() ?: 0
        }
    }

    override fun onCreate() {
        super.onCreate()
        activeInstance = this
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

        val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
        val savedDelivery = sessionMgr.getActiveDeliveryId()
        if (savedDelivery.isNotBlank()) {
            activeDeliveryId = savedDelivery
            val maxSeq = dbHelper?.getMaxSequenceNumber(savedDelivery) ?: 0L
            if (maxSeq > currentSequenceNumber) {
                currentSequenceNumber = maxSeq
            }
        }

        registerNetworkMonitoring()
        requestLocationUpdates()

        serviceScope.launch {
            replayOfflineQueueLoop()
        }

        serviceScope.launch {
            listenRiderSseLoop()
        }

        serviceScope.launch {
            presenceProcessingLoop()
        }

        serviceScope.launch {
            stationaryHeartbeatLoop()
        }
    }

    private suspend fun deprecatedPollActiveOffersLoop() {
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

    private data class PresenceUpdate(
        val latitude: Double,
        val longitude: Double,
        val speedKmh: Float,
        val heading: Float?,
        val accuracy: Float,
        val isOnline: Boolean,
        val sequenceNumber: Long,
        val timestamp: Long
    )

    private val presenceChannel = kotlinx.coroutines.channels.Channel<PresenceUpdate>(kotlinx.coroutines.channels.Channel.CONFLATED)
    private var isCurrentlyStationary = false

    private fun reconfigureAdaptiveSampling(stationary: Boolean) {
        if (isCurrentlyStationary == stationary) return
        isCurrentlyStationary = stationary
        reconfigureLocationTracking()
    }

    private suspend fun presenceProcessingLoop() {
        var pendingPresenceRetry: PresenceUpdate? = null
        var retryBackoffMs = 2000L

        while (serviceScope.isActive) {
            try {
                val update = if (pendingPresenceRetry != null) {
                    delay(retryBackoffMs)
                    presenceChannel.tryReceive().getOrNull() ?: pendingPresenceRetry!!
                } else {
                    presenceChannel.receive()
                }

                val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
                val isShiftOnline = sessionMgr.isShiftOnline()
                if (!isShiftOnline && activeDeliveryId.isBlank()) {
                    continue
                }

                val baseUrl = sessionMgr.getBaseUrl()
                val token = sessionMgr.getAuthToken()
                if (baseUrl.isNotBlank() && token.isNotBlank()) {
                    val now = System.currentTimeMillis()
                    if (activeDeliveryId.isNotBlank()) {
                        transmitHeadTelemetry()
                        val minActivePresenceInterval = 10000L
                        if (now - lastPresencePostTime < minActivePresenceInterval && pendingPresenceRetry == null) {
                            continue
                        }
                    } else {
                        if (now - lastPresencePostTime < 30000L && pendingPresenceRetry == null) {
                            continue
                        }
                    }

                    var postSuccessful = false
                    try {
                        val url = java.net.URL("${baseUrl.trimEnd('/')}/api/v1/delivery/rider/presence")
                        val conn = url.openConnection() as java.net.HttpURLConnection
                        conn.requestMethod = "POST"
                        conn.setRequestProperty("Content-Type", "application/json")
                        conn.setRequestProperty("Authorization", "Bearer $token")
                        conn.doOutput = true
                        conn.connectTimeout = 3000
                        conn.readTimeout = 3000

                        val body = JSONObject().apply {
                            put("latitude", update.latitude)
                            put("longitude", update.longitude)
                            put("speedKmh", update.speedKmh)
                            if (update.heading != null) {
                                put("heading", update.heading)
                            }
                            put("accuracyMeters", update.accuracy)
                            put("isOnline", isShiftOnline)
                            put("sequenceNumber", update.sequenceNumber)
                            put("timestamp", update.timestamp)
                        }
                        conn.outputStream.use { os -> os.write(body.toString().toByteArray(Charsets.UTF_8)) }
                        val code = conn.responseCode
                        conn.disconnect()
                        if (code in 200..299) {
                            postSuccessful = true
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Presence update post failed: ${e.message}")
                    }

                    if (postSuccessful) {
                        lastPresencePostTime = System.currentTimeMillis()
                        pendingPresenceRetry = null
                        retryBackoffMs = 2000L
                    } else {
                        pendingPresenceRetry = update
                        retryBackoffMs = (retryBackoffMs * 2).coerceAtMost(16000L)
                    }
                }
            } catch (_: Exception) {}
        }
    }

    private suspend fun stationaryHeartbeatLoop() {
        while (serviceScope.isActive) {
            delay(15000L)
            if (activeDeliveryId.isNotBlank() && isCurrentlyStationary) {
                val lastLoc = _lastLocation.value
                if (lastLoc != null) {
                    currentSequenceNumber++
                    val update = lastLoc.copy(
                        sequenceNumber = currentSequenceNumber,
                        timestamp = System.currentTimeMillis()
                    )
                    dbHelper?.enqueue(update)
                    _lastLocation.value = update
                    presenceChannel.trySend(
                        PresenceUpdate(
                            latitude = update.latitude,
                            longitude = update.longitude,
                            speedKmh = 0.0f,
                            heading = update.heading,
                            accuracy = update.accuracyMeters,
                            isOnline = true,
                            sequenceNumber = currentSequenceNumber,
                            timestamp = update.timestamp
                        )
                    )
                }
            }
        }
    }

    private fun registerNetworkMonitoring() {
        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            val cb = object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    _isNetworkAvailable.value = true
                }

                override fun onLost(network: Network) {
                    _isNetworkAvailable.value = false
                    _isStale.value = true
                }
            }
            networkCallback = cb
            connectivityManager?.registerNetworkCallback(request, cb)
        } catch (e: Exception) {
            _isNetworkAvailable.value = true
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        acquireWakeLock()
        requestLocationUpdates()
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        if (activeDeliveryId.isNotBlank()) {
            try {
                val restartServiceIntent = Intent(applicationContext, RiderForegroundLocationService::class.java).also {
                    it.setPackage(packageName)
                }
                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    android.app.PendingIntent.FLAG_ONE_SHOT or android.app.PendingIntent.FLAG_IMMUTABLE
                } else {
                    android.app.PendingIntent.FLAG_ONE_SHOT
                }
                val restartServicePendingIntent = android.app.PendingIntent.getService(
                    applicationContext, 1002, restartServiceIntent, flags
                )
                val alarmService = applicationContext.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                alarmService?.setExactAndAllowWhileIdle(
                    android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    android.os.SystemClock.elapsedRealtime() + 1000L,
                    restartServicePendingIntent
                )
            } catch (_: Exception) {
                // Fallback to OS START_STICKY resurrection
            }
        }
    }

    private fun acquireWakeLock() {
        if (locationWakeLock == null) {
            val pm = getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
            locationWakeLock = pm?.newWakeLock(
                android.os.PowerManager.PARTIAL_WAKE_LOCK,
                "CommerceOSRider:LocationTrackingWakeLock"
            )?.apply {
                setReferenceCounted(false)
            }
        }
        if (locationWakeLock?.isHeld != true) {
            try {
                locationWakeLock?.acquire(30 * 60 * 1000L) // 30-minute safety cap
            } catch (_: Exception) {}
        }
    }

    private fun releaseWakeLock() {
        if (locationWakeLock?.isHeld == true) {
            try {
                locationWakeLock?.release()
            } catch (_: Exception) {}
        }
    }

    fun reconfigureLocationTracking() {
        try {
            locationManager?.removeUpdates(this)
        } catch (_: Exception) {}
        requestLocationUpdates()
    }

    private fun requestLocationUpdates() {
        try {
            val isGpsEnabled = locationManager?.isProviderEnabled(LocationManager.GPS_PROVIDER) == true
            val isNetEnabled = locationManager?.isProviderEnabled(LocationManager.NETWORK_PROVIDER) == true
            val isPassiveEnabled = locationManager?.isProviderEnabled(LocationManager.PASSIVE_PROVIDER) == true

            if (!isGpsEnabled && !isNetEnabled && !isPassiveEnabled) {
                _isStale.value = true
            }

            // Immediately prime last known location with freshest fix within 60s freshness threshold
            val lastGps = try { locationManager?.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
            val lastNet = try { locationManager?.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }
            val lastPassive = try { locationManager?.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER) } catch (_: Exception) { null }
            val allFixes = listOfNotNull(lastGps, lastNet, lastPassive)
            val now = System.currentTimeMillis()
            val freshFixes = allFixes.filter { (now - it.time) < 60_000L }
            val freshest = freshFixes.maxByOrNull { it.time }
            if (freshest != null) {
                onLocationChanged(freshest)
            }

            val isActiveDelivery = activeDeliveryId.isNotBlank()
            val minTimeMs: Long
            val minDistanceM: Float
            if (isActiveDelivery) {
                if (isCurrentlyStationary) {
                    minTimeMs = 10000L // 10s backoff while stationary
                    minDistanceM = 15.0f
                } else {
                    minTimeMs = 5000L  // 5s adaptive sampling while in motion
                    minDistanceM = 8.0f
                }
            } else {
                minTimeMs = 30000L     // 30s low-power idle
                minDistanceM = 25.0f
            }
            val mainLooper = android.os.Looper.getMainLooper()

            if (isActiveDelivery) {
                if (isGpsEnabled) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        this,
                        mainLooper
                    )
                } else if (isNetEnabled) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        this,
                        mainLooper
                    )
                }
            } else {
                if (isNetEnabled) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.NETWORK_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        this,
                        mainLooper
                    )
                } else if (isGpsEnabled) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        this,
                        mainLooper
                    )
                } else if (isPassiveEnabled) {
                    locationManager?.requestLocationUpdates(
                        LocationManager.PASSIVE_PROVIDER,
                        minTimeMs,
                        minDistanceM,
                        this,
                        mainLooper
                    )
                }
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
        val fixTime = if (location.time > 0L) location.time else now
        if (now - fixTime > 60_000L) {
            return
        }

        if (isMock) {
            lastMockTimestamp = now
        } else if (now - lastMockTimestamp < 5000L) {
            // A mock location app/simulator is actively injecting test coordinates.
            // Ignore non-mock hardware satellite fixes to prevent bouncing between test and physical location!
            return
        }

        val speedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0.0f

        // Adaptive movement tracking
        if (lastDispatchedLat != 0.0 && lastDispatchedLng != 0.0) {
            val distResults = FloatArray(1)
            Location.distanceBetween(lastDispatchedLat, lastDispatchedLng, location.latitude, location.longitude, distResults)
            val moved = distResults[0]
            if (speedKmh < 1.0f && moved < 3.0f) {
                reconfigureAdaptiveSampling(true)
            } else if (speedKmh >= 1.5f || moved >= 5.0f) {
                reconfigureAdaptiveSampling(false)
            }
        }

        // Anti-jitter & Movement Sampling: 3.0m threshold & 15s stationary heartbeat
        var isStationaryHeartbeat = false
        if (lastDispatchedLat != 0.0 && lastDispatchedLng != 0.0 && speedKmh < 1.5f) {
            val distResults = FloatArray(1)
            Location.distanceBetween(lastDispatchedLat, lastDispatchedLng, location.latitude, location.longitude, distResults)
            if (distResults[0] < 3.0f) {
                if (now - lastDispatchedTime >= 15000L) {
                    isStationaryHeartbeat = true
                } else if (_lastLocation.value != null) {
                    return
                }
            }
        }
        if (isStationaryHeartbeat) {
            Log.d(TAG, "Dispatching stationary presence heartbeat for rider=$activeRiderId")
        }

        lastDispatchedLat = location.latitude
        lastDispatchedLng = location.longitude
        lastDispatchedTime = now

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
            timestamp = fixTime
        )

        _lastLocation.value = update

        if (activeDeliveryId.isNotBlank()) {
            // Atomic SQLite enqueue for active delivery telemetry
            val enqueued = dbHelper?.enqueue(update) ?: false
            if (!enqueued) {
                Log.e(TAG, "Failed to persist telemetry fix to SQLite queue for delivery $activeDeliveryId")
                _isStale.value = true
            }
            _pendingQueueCount.value = dbHelper?.getQueueSize() ?: 0
        }

        // Conflated sequential presence pipeline ensures monotonic ordering without race conditions
        val sessionMgr = com.commerceos.rider.session.RiderSessionManager.getInstance(applicationContext)
        val isShiftOnline = sessionMgr.isShiftOnline()

        presenceChannel.trySend(
            PresenceUpdate(
                latitude = location.latitude,
                longitude = location.longitude,
                speedKmh = speedKmh,
                heading = headingValue,
                accuracy = accuracy,
                isOnline = isShiftOnline,
                sequenceNumber = currentSequenceNumber,
                timestamp = now
            )
        )
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



    private suspend fun transmitHeadTelemetry(): Boolean = telemetryTransmissionMutex.withLock {
        withContext(Dispatchers.IO) {
            if (activeDeliveryId.isBlank()) {
                return@withContext true
            }
            dbHelper?.purgeInactiveDeliveries(activeDeliveryId)
            val nextUpdate = dbHelper?.peekHead(activeDeliveryId) ?: return@withContext true
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
                            val accepted = respJson.optBoolean("accepted", respJson.optBoolean("ok", true))
                            val isDuplicate = respJson.optBoolean("duplicate", false) || respJson.optBoolean("duplicateOrOutdated", false)
                            if ((accepted && ackSeq >= nextUpdate.sequenceNumber) || isDuplicate) {
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
                    val accepted = respJson.optBoolean("accepted", respJson.optBoolean("ok", true))
                    val isDuplicate = respJson.optBoolean("duplicate", false) || respJson.optBoolean("duplicateOrOutdated", false)

                    // Atomic Dequeue upon explicit sequence ACK
                    if ((accepted && ackSeq >= nextUpdate.sequenceNumber) || isDuplicate) {
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
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        if (activeInstance == this) {
            activeInstance = null
        }
        releaseWakeLock()
        locationManager?.removeUpdates(this)
        networkCallback?.let {
            try {
                connectivityManager?.unregisterNetworkCallback(it)
            } catch (_: Exception) {}
        }
        networkCallback = null
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
