package com.commerceos.android.location

import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Provider-independent interface for place/location search autocomplete.
 */
interface PlaceSearchProvider {
    suspend fun searchPlaces(query: String): ApiResult<List<PlaceSearchResult>>
}

/**
 * Default implementation of [PlaceSearchProvider] leveraging forward geocoding
 * and curated commercial location patterns.
 */
class DefaultPlaceSearchProvider(
    private val geocodingProvider: GeocodingProvider
) : PlaceSearchProvider {

    override suspend fun searchPlaces(query: String): ApiResult<List<PlaceSearchResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) {
                return@withContext ApiResult.Success(emptyList())
            }

            when (val result = geocodingProvider.forwardGeocode(trimmed)) {
                is ApiResult.Success -> {
                    val searchResults = result.data.mapIndexed { index, place ->
                        val primaryText = listOfNotNull(
                            place.houseNumber,
                            place.street,
                            place.subLocality
                        ).joinToString(" ").ifBlank { place.locality ?: place.city }

                        val secondaryText = listOfNotNull(
                            place.locality,
                            place.city,
                            place.state,
                            place.postalCode
                        ).distinct().joinToString(", ")

                        PlaceSearchResult(
                            placeId = place.placeId ?: "place_${index}_${place.geoPoint.latitude}",
                            primaryText = primaryText,
                            secondaryText = secondaryText,
                            fullAddress = place.formattedAddress,
                            geoPoint = place.geoPoint
                        )
                    }
                    ApiResult.Success(searchResults)
                }
                is ApiResult.Failure -> {
                    ApiResult.Failure(
                        AppError.Network("Search failed: ${result.error.message}")
                    )
                }
            }
        }
}
