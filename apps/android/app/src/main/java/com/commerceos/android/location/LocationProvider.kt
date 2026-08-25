package com.commerceos.android.location

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import androidx.core.content.ContextCompat
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

        val lm = locationManager ?: return@withContext LocationResult.Failure("Location manager unavailable")

        // Try high-accuracy recent last-known location across Fused, GPS, and Network
        val lastGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
        val lastFused = try {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
                lm.getLastKnownLocation(LocationManager.FUSED_PROVIDER)
            } else null
        } catch (_: Exception) { null }
        val lastNetwork = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }

        val validLastKnown = listOfNotNull(lastGps, lastFused, lastNetwork)
            .filter { (System.currentTimeMillis() - it.time) < 120_000 } // Under 2 mins old
            .sortedBy { if (it.hasAccuracy()) it.accuracy else 9999f } // Prioritize highest accuracy (lowest meters)

        val bestLastKnown = validLastKnown.firstOrNull()

        // If we have an accurate last-known location (< 35m accuracy), return it immediately
        if (bestLastKnown != null && bestLastKnown.hasAccuracy() && bestLastKnown.accuracy <= 35f) {
            return@withContext LocationResult.Success(
                GeoPoint(
                    latitude = bestLastKnown.latitude,
                    longitude = bestLastKnown.longitude,
                    accuracyMeters = bestLastKnown.accuracy,
                    timestamp = bestLastKnown.time,
                    provider = bestLastKnown.provider
                )
            )
        }

        // Fresh high-precision GPS location fix request with timeout
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
                            lm.requestLocationUpdates(prov, 500L, 0f, listener)
                        }
                    } else {
                        lm.requestSingleUpdate(LocationManager.PASSIVE_PROVIDER, listener, null)
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
        } else if (bestLastKnown != null) {
            val accuracy = if (bestLastKnown.hasAccuracy()) bestLastKnown.accuracy else null
            LocationResult.Success(
                GeoPoint(
                    latitude = bestLastKnown.latitude,
                    longitude = bestLastKnown.longitude,
                    accuracyMeters = accuracy,
                    timestamp = bestLastKnown.time,
                    provider = bestLastKnown.provider
                )
            )
        } else {
            LocationResult.Failure("Unable to acquire GPS fix. Please select location on map.")
        }
    }
}
