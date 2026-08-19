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

        // Try fast last-known location first if recent enough
        val lastGps = try { lm.getLastKnownLocation(LocationManager.GPS_PROVIDER) } catch (_: Exception) { null }
        val lastNetwork = try { lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) } catch (_: Exception) { null }

        val bestLastKnown = listOfNotNull(lastGps, lastNetwork)
            .maxByOrNull { it.time }

        if (bestLastKnown != null && (System.currentTimeMillis() - bestLastKnown.time) < 60_000) {
            val accuracy = if (bestLastKnown.hasAccuracy()) bestLastKnown.accuracy else null
            return@withContext LocationResult.Success(
                GeoPoint(
                    latitude = bestLastKnown.latitude,
                    longitude = bestLastKnown.longitude,
                    accuracyMeters = accuracy,
                    timestamp = bestLastKnown.time,
                    provider = bestLastKnown.provider
                )
            )
        }

        // Fresh location fix request with timeout
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
                    val provider = when {
                        lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                        lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                        else -> LocationManager.PASSIVE_PROVIDER
                    }
                    lm.requestSingleUpdate(provider, listener, null)
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
