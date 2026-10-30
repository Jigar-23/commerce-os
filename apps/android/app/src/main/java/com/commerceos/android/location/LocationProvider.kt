package com.commerceos.android.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume

/**
 * Provider-independent interface for hardware location services.
 */
interface LocationProvider {
    fun hasPermission(): Boolean
    fun isLocationEnabled(): Boolean
    suspend fun getCurrentLocation(timeoutMs: Long = 10000L): LocationResult
}

/**
 * Android system implementation of [LocationProvider] using LocationManager
 * with fine/coarse permission verification, provider availability checks,
 * and graceful fallback. Does NOT invent synthetic locations.
 */
class DefaultLocationProvider(private val context: Context) : LocationProvider {

    private val locationManager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    override fun hasPermission(): Boolean {
        val fineGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarseGranted = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return fineGranted || coarseGranted
    }

    override fun isLocationEnabled(): Boolean {
        val lm = locationManager ?: return false
        val gpsEnabled = try { lm.isProviderEnabled(LocationManager.GPS_PROVIDER) } catch (_: Exception) { false }
        val networkEnabled = try { lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { false }
        return gpsEnabled || networkEnabled
    }

    @SuppressLint("MissingPermission")
    override suspend fun getCurrentLocation(timeoutMs: Long): LocationResult = withContext(Dispatchers.IO) {
        if (!hasPermission()) {
            return@withContext LocationResult.Failure("Location permission required", isPermissionError = true)
        }

        if (!isLocationEnabled()) {
            return@withContext LocationResult.Failure("Location services disabled on device")
        }

        // 1. First attempt: High-accuracy Google Play Services FusedLocationProviderClient
        try {
            val fusedClient = LocationServices.getFusedLocationProviderClient(context)
            val fusedFix = withTimeoutOrNull(timeoutMs.coerceAtMost(6000L)) {
                suspendCancellableCoroutine<Location?> { cont ->
                    val cts = CancellationTokenSource()
                    fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.token)
                        .addOnSuccessListener { loc ->
                            if (cont.isActive) cont.resume(loc)
                        }
                        .addOnFailureListener {
                            if (cont.isActive) cont.resume(null)
                        }
                    cont.invokeOnCancellation { cts.cancel() }
                }
            }

            if (fusedFix != null) {
                return@withContext LocationResult.Success(
                    GeoPoint(
                        latitude = fusedFix.latitude,
                        longitude = fusedFix.longitude,
                        accuracyMeters = if (fusedFix.hasAccuracy()) fusedFix.accuracy else null,
                        timestamp = fusedFix.time,
                        provider = fusedFix.provider ?: "fused"
                    )
                )
            }

            // Check fusedClient.lastLocation
            val lastFusedClient = suspendCancellableCoroutine<Location?> { cont ->
                fusedClient.lastLocation
                    .addOnSuccessListener { loc -> if (cont.isActive) cont.resume(loc) }
                    .addOnFailureListener { if (cont.isActive) cont.resume(null) }
            }
            if (lastFusedClient != null && (System.currentTimeMillis() - lastFusedClient.time) < 300_000) {
                return@withContext LocationResult.Success(
                    GeoPoint(
                        latitude = lastFusedClient.latitude,
                        longitude = lastFusedClient.longitude,
                        accuracyMeters = if (lastFusedClient.hasAccuracy()) lastFusedClient.accuracy else null,
                        timestamp = lastFusedClient.time,
                        provider = lastFusedClient.provider ?: "fused_last"
                    )
                )
            }
        } catch (_: Exception) {}

        val lm = locationManager ?: return@withContext LocationResult.Failure("Location manager unavailable")

        // 2. Collect all available last-known locations across providers
        val lastGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
        val lastFused = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
            } else null
        } catch (_: Exception) { null }
        val lastNetwork = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }

        val allLastKnown = listOfNotNull(lastGps, lastFused, lastNetwork)
            .sortedWith(
                compareBy<Location> { if (it.hasAccuracy()) it.accuracy else 9999f }
                    .thenByDescending { it.time }
            )

        val bestRecentLastKnown = allLastKnown.firstOrNull { (System.currentTimeMillis() - it.time) < 180_000 }
        if (bestRecentLastKnown != null && bestRecentLastKnown.hasAccuracy() && bestRecentLastKnown.accuracy <= 30f) {
            return@withContext LocationResult.Success(
                GeoPoint(
                    latitude = bestRecentLastKnown.latitude,
                    longitude = bestRecentLastKnown.longitude,
                    accuracyMeters = bestRecentLastKnown.accuracy,
                    timestamp = bestRecentLastKnown.time,
                    provider = bestRecentLastKnown.provider
                )
            )
        }

        // 3. Fallback: Android system LocationManager updates with explicit Looper.getMainLooper()
        val freshFix = withTimeoutOrNull(timeoutMs) {
            suspendCancellableCoroutine<Location?> { continuation ->
                val listener = object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        try { lm.removeUpdates(this) } catch (_: Exception) {}
                        if (continuation.isActive) continuation.resume(location)
                    }
                    override fun onProviderDisabled(provider: String) {}
                    override fun onProviderEnabled(provider: String) {}
                }

                try {
                    val enabledProviders = listOf(
                        LocationManager.GPS_PROVIDER,
                        LocationManager.NETWORK_PROVIDER
                    ).filter { lm.isProviderEnabled(it) }

                    if (enabledProviders.isNotEmpty()) {
                        for (prov in enabledProviders) {
                            lm.requestLocationUpdates(prov, 500L, 0f, listener, Looper.getMainLooper())
                        }
                    } else {
                        lm.requestSingleUpdate(LocationManager.PASSIVE_PROVIDER, listener, Looper.getMainLooper())
                    }
                } catch (e: Exception) {
                    if (continuation.isActive) continuation.resume(null)
                }

                continuation.invokeOnCancellation {
                    try { lm.removeUpdates(listener) } catch (_: Exception) {}
                }
            }
        }

        if (freshFix != null) {
            val accuracy = if (freshFix.hasAccuracy()) freshFix.accuracy else null
            LocationResult.Success(
                GeoPoint(
                    latitude = freshFix.latitude,
                    longitude = freshFix.longitude,
                    accuracyMeters = accuracy,
                    timestamp = freshFix.time,
                    provider = freshFix.provider
                )
            )
        } else if (allLastKnown.isNotEmpty()) {
            val bestKnown = allLastKnown.first()
            val accuracy = if (bestKnown.hasAccuracy()) bestKnown.accuracy else null
            LocationResult.Success(
                GeoPoint(
                    latitude = bestKnown.latitude,
                    longitude = bestKnown.longitude,
                    accuracyMeters = accuracy,
                    timestamp = bestKnown.time,
                    provider = bestKnown.provider
                )
            )
        } else {
            LocationResult.Failure("Unable to acquire GPS fix. Please select location on map.")
        }
    }
}
