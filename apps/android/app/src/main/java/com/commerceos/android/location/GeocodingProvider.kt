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
        val rawFullAddress = (0..address.maxAddressLineIndex)
            .mapNotNull { address.getAddressLine(it) }
            .joinToString(", ")
            .trim()

        val rawSegments = if (rawFullAddress.isNotBlank()) {
            rawFullAddress.split(",").map { it.trim() }.filter { it.isNotBlank() }
        } else {
            emptyList()
        }

        // Exclude trailing country segment ("India")
        val nonCountrySegments = rawSegments.filterNot { it.equals("India", ignoreCase = true) }

        // Resolve Locality and City
        val locality = address.locality ?: address.subAdminArea
        val state = address.adminArea ?: ""
        val city = locality ?: state.ifBlank { "Unknown City" }

        // Resolve PIN code (from address field or regex extraction from segments)
        val postalCode = address.postalCode ?: run {
            val pinRegex = Regex("\\b[1-9][0-9]{5}\\b")
            nonCountrySegments.firstNotNullOfOrNull { seg ->
                pinRegex.find(seg)?.value
            } ?: ""
        }
        val country = address.countryName ?: "India"

        // Candidate segments that represent local areas (excluding country, city, state, postal code)
        val localSegments = nonCountrySegments.filterNot { seg ->
            seg.equals(city, ignoreCase = true) ||
            seg.equals(state, ignoreCase = true) ||
            (postalCode.isNotBlank() && seg.contains(postalCode)) ||
            (state.isNotBlank() && seg.contains(state, ignoreCase = true))
        }

        // SubLocality resolution: use explicit Android field, or the closest local segment to the city
        val resolvedSubLocality = address.subLocality?.takeIf { it.isNotBlank() }
            ?: when {
                localSegments.size >= 2 -> localSegments.last()
                localSegments.size == 1 && (address.thoroughfare.isNullOrBlank() || address.thoroughfare == localSegments[0]) -> localSegments[0]
                else -> null
            }

        // Feature / Landmark resolution (building, apartment, colony, shop name)
        val rawFeature = address.featureName?.trim()
        val isValidFeature = !rawFeature.isNullOrBlank() &&
            !rawFeature.matches(Regex("^[0-9\\-]+$")) &&
            !rawFeature.contains("+") &&
            !rawFeature.equals(resolvedSubLocality, ignoreCase = true) &&
            !rawFeature.equals(city, ignoreCase = true) &&
            !rawFeature.equals(state, ignoreCase = true) &&
            !rawFeature.equals(postalCode, ignoreCase = true)

        // Street / Road / Landmark resolution
        val resolvedStreet = when {
            !address.thoroughfare.isNullOrBlank() -> {
                if (isValidFeature && rawFeature != address.thoroughfare) {
                    "$rawFeature, ${address.thoroughfare}"
                } else {
                    address.thoroughfare
                }
            }
            isValidFeature -> rawFeature
            localSegments.size >= 2 -> localSegments.dropLast(1).joinToString(", ")
            localSegments.size == 1 && localSegments[0] != resolvedSubLocality -> localSegments[0]
            else -> null
        }

        val houseNo = address.subThoroughfare?.takeIf { it.isNotBlank() }

        // Deduplicate and assemble ground-truth clean formatted address
        val cleanFull = cleanAddressSegments(
            if (rawFullAddress.isNotBlank()) rawFullAddress else {
                listOfNotNull(houseNo, resolvedStreet, resolvedSubLocality, locality, state, postalCode).joinToString(", ")
            }
        )

        val confidence = when {
            postalCode.isNotBlank() && (!resolvedStreet.isNullOrBlank() || !resolvedSubLocality.isNullOrBlank()) -> GeocodeConfidence.HIGH
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
            formattedAddress = cleanFull,
            houseNumber = houseNo,
            street = resolvedStreet?.let { cleanAddressSegments(it) },
            subLocality = resolvedSubLocality?.let { cleanAddressSegments(it) },
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
