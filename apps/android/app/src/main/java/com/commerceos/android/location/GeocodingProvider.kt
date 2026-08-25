package com.commerceos.android.location

import android.content.Context
import android.location.Address
import android.location.Geocoder
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * Provider-independent interface for reverse and forward geocoding.
 */
interface GeocodingProvider {
    suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<GeocodedPlace>
    suspend fun forwardGeocode(query: String): ApiResult<List<GeocodedPlace>>
}

/**
 * Android system implementation of [GeocodingProvider] running asynchronously on Dispatchers.IO.
 * Parses structured geographic elements (subLocality, thoroughfare, postal code, locality, state)
 * and assesses geocode confidence. Never fabricates fake landmarks.
 */
class DefaultGeocodingProvider(private val context: Context) : GeocodingProvider {

    override suspend fun reverseGeocode(latitude: Double, longitude: Double): ApiResult<GeocodedPlace> =
        withContext(Dispatchers.IO) {
            try {
                if (!Geocoder.isPresent()) {
                    return@withContext ApiResult.Failure(
                        AppError.Network("Geocoder service unavailable on device")
                    )
                }

                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val matches = geocoder.getFromLocation(latitude, longitude, 1)

                if (matches.isNullOrEmpty()) {
                    return@withContext ApiResult.Failure(
                        AppError.Server(
                            httpCode = 404,
                            errorCode = "NOT_FOUND",
                            serverMessage = "No geographic details found for specified coordinates",
                            retryAfterSeconds = null,
                            attemptsLeft = null
                        )
                    )
                }

                val address = matches[0]
                val place = parseAddressToGeocodedPlace(address, latitude, longitude)
                ApiResult.Success(place)
            } catch (e: Exception) {
                ApiResult.Failure(
                    AppError.Network("Geocoding failed: ${e.localizedMessage ?: "Network/Service error"}")
                )
            }
        }

    override suspend fun forwardGeocode(query: String): ApiResult<List<GeocodedPlace>> =
        withContext(Dispatchers.IO) {
            try {
                if (query.isBlank()) return@withContext ApiResult.Success(emptyList())

                if (!Geocoder.isPresent()) {
                    return@withContext ApiResult.Failure(
                        AppError.Network("Geocoder service unavailable on device")
                    )
                }

                val geocoder = Geocoder(context, Locale.getDefault())
                @Suppress("DEPRECATION")
                val matches = geocoder.getFromLocationName(query, 5)

                if (matches.isNullOrEmpty()) {
                    return@withContext ApiResult.Success(emptyList())
                }

                val places = matches.map { addr ->
                    parseAddressToGeocodedPlace(addr, addr.latitude, addr.longitude)
                }
                ApiResult.Success(places)
            } catch (e: Exception) {
                ApiResult.Failure(
                    AppError.Network("Forward geocoding error: ${e.localizedMessage}")
                )
            }
        }

    private fun parseAddressToGeocodedPlace(address: Address, lat: Double, lng: Double): GeocodedPlace {
        val street = address.thoroughfare
        val houseNo = address.subThoroughfare
        val subLocality = address.subLocality
        val locality = address.locality ?: address.subAdminArea
        val city = locality ?: address.adminArea ?: "Unknown City"
        val state = address.adminArea ?: ""
        val postalCode = address.postalCode ?: ""
        val country = address.countryName ?: "India"

        val formattedParts = mutableListOf<String>()
        if (!houseNo.isNullOrBlank()) formattedParts.add(houseNo)
        if (!street.isNullOrBlank()) formattedParts.add(street)
        if (!subLocality.isNullOrBlank()) formattedParts.add(subLocality)
        if (!locality.isNullOrBlank() && locality != subLocality) formattedParts.add(locality)
        if (state.isNotBlank()) formattedParts.add(state)
        if (postalCode.isNotBlank()) formattedParts.add(postalCode)

        val formattedAddress = if (formattedParts.isNotEmpty()) {
            formattedParts.joinToString(", ")
        } else {
            (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }.joinToString(", ")
        }

        val confidence = when {
            !postalCode.isNullOrBlank() && (!street.isNullOrBlank() || !subLocality.isNullOrBlank()) -> GeocodeConfidence.HIGH
            !locality.isNullOrBlank() -> GeocodeConfidence.MEDIUM
            else -> GeocodeConfidence.LOW
        }

        val geoPoint = GeoPoint(
            latitude = lat,
            longitude = lng,
            accuracyMeters = null,
            provider = "geocoder"
        )

        return GeocodedPlace(
            placeId = if (address.url != null) address.url else "geo_${lat.toString().take(7)}_${lng.toString().take(7)}",
            formattedAddress = formattedAddress,
            houseNumber = houseNo,
            street = street,
            subLocality = subLocality,
            locality = locality,
            city = city,
            district = address.subAdminArea,
            state = state,
            postalCode = postalCode,
            country = country,
            confidence = confidence,
            geoPoint = geoPoint
        )
    }
}
