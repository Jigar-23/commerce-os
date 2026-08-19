package com.commerceos.android.location

/**
 * Domain representation of a geographic coordinate.
 * Accuracy is nullable and NEVER synthetic/hardcoded.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float? = null,
    val timestamp: Long? = System.currentTimeMillis(),
    val provider: String? = null
)

/**
 * Geocode confidence levels to assess data quality for delivery routing.
 */
enum class GeocodeConfidence {
    HIGH,
    MEDIUM,
    LOW
}

/**
 * Structured geocoded place metadata returned by reverse/forward geocoders.
 */
data class GeocodedPlace(
    val placeId: String? = null,
    val formattedAddress: String,
    val houseNumber: String? = null,
    val street: String? = null,
    val subLocality: String? = null,
    val locality: String? = null,
    val city: String,
    val district: String? = null,
    val state: String,
    val postalCode: String,
    val country: String = "India",
    val confidence: GeocodeConfidence = GeocodeConfidence.MEDIUM,
    val geoPoint: GeoPoint
)

/**
 * Autocomplete location search result item.
 */
data class PlaceSearchResult(
    val placeId: String,
    val primaryText: String,
    val secondaryText: String,
    val fullAddress: String,
    val geoPoint: GeoPoint
)

/**
 * Hardware GPS & Runtime Permission state for Location Platform.
 */
sealed class LocationPermissionState {
    object Idle : LocationPermissionState()
    object Requesting : LocationPermissionState()
    object Granted : LocationPermissionState()
    data class Denied(val canRetry: Boolean) : LocationPermissionState()
    object PermanentlyDenied : LocationPermissionState()
    object LocationDisabled : LocationPermissionState()
    object Searching : LocationPermissionState()
    data class Success(val location: GeoPoint) : LocationPermissionState()
    data class Error(val message: String) : LocationPermissionState()
}

/**
 * Status wrapper for location acquisition tasks.
 */
sealed class LocationResult {
    data class Success(val location: GeoPoint) : LocationResult()
    data class Failure(val reason: String, val isPermissionError: Boolean = false) : LocationResult()
}
