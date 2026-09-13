package com.commerceos.android.location

import android.content.Context
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.google.android.gms.maps.model.LatLng
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.model.AutocompleteSessionToken
import com.google.android.libraries.places.api.model.RectangularBounds
import com.google.android.libraries.places.api.model.Place
import com.google.android.libraries.places.api.net.FetchPlaceRequest
import com.google.android.libraries.places.api.net.FindAutocompletePredictionsRequest
import com.google.android.libraries.places.api.net.PlacesClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * Production Google Places Autocomplete & Place Details Provider.
 * Leverages Google's ranking engine with dynamic viewport proximity biasing:
 * - Common terms (e.g. "Starbucks", "Hospital", "Pharmacy") prioritize nearby locations first.
 * - Global & unique landmarks (e.g. "Eiffel Tower", "Marine Drive") are intelligently resolved without restriction.
 */
class GooglePlacesSearchProvider(
    private val context: Context,
    private val apiKey: String = "AIzaSyCzi_sMDds2_im406sGTCU8WAFZoTyNg5c"
) : PlaceSearchProvider {

    private val placesClient: PlacesClient by lazy {
        if (!Places.isInitialized()) {
            Places.initialize(context.applicationContext, apiKey)
        }
        Places.createClient(context.applicationContext)
    }

    private var currentSessionToken: AutocompleteSessionToken? = null

    // Track active map center coordinates for dynamic proximity ranking
    var activeCameraCenter: LatLng? = null

    override suspend fun searchPlaces(query: String): ApiResult<List<PlaceSearchResult>> =
        withContext(Dispatchers.IO) {
            val trimmed = query.trim()
            if (trimmed.length < 2) {
                return@withContext ApiResult.Success(emptyList())
            }

            if (currentSessionToken == null) {
                currentSessionToken = AutocompleteSessionToken.newInstance()
            }

            try {
                val requestBuilder = FindAutocompletePredictionsRequest.builder()
                    .setQuery(trimmed)
                    .setSessionToken(currentSessionToken)

                // Smart Proximity Bias: Bias towards current map camera center (~50km bounding box)
                // Note: Using LocationBias (soft preference) ensures nearby results rank first,
                // while global/unique places are never blocked.
                val center = activeCameraCenter ?: LatLng(28.1970, 76.6190)
                val southWest = LatLng(center.latitude - 0.45, center.longitude - 0.45)
                val northEast = LatLng(center.latitude + 0.45, center.longitude + 0.45)
                requestBuilder.setLocationBias(RectangularBounds.newInstance(southWest, northEast))

                val predictions = suspendCancellableCoroutine { continuation ->
                    placesClient.findAutocompletePredictions(requestBuilder.build())
                        .addOnSuccessListener { response ->
                            continuation.resume(response.autocompletePredictions)
                        }
                        .addOnFailureListener {
                            continuation.resume(null)
                        }
                }

                if (predictions == null) {
                    return@withContext ApiResult.Failure(
                        AppError.Network("Google Places search request failed")
                    )
                }

                val results = predictions.map { prediction ->
                    val distM = prediction.distanceMeters
                    val distStr = when {
                        distM == null -> null
                        distM < 1000 -> "${distM} m"
                        else -> String.format(java.util.Locale.US, "%.1f km", distM / 1000.0)
                    }

                    PlaceSearchResult(
                        placeId = prediction.placeId,
                        primaryText = prediction.getPrimaryText(null).toString(),
                        secondaryText = prediction.getSecondaryText(null).toString(),
                        fullAddress = prediction.getFullText(null).toString(),
                        geoPoint = GeoPoint(0.0, 0.0),
                        distance = distStr
                    )
                }

                ApiResult.Success(results)
            } catch (e: Exception) {
                ApiResult.Failure(AppError.Network("Places search error: ${e.localizedMessage}"))
            }
        }

    /**
     * Resolves exact LatLng coordinates and structured address components for selected Google Place.
     */
    suspend fun fetchPlaceDetails(placeId: String): ApiResult<GeocodedPlace> =
        withContext(Dispatchers.IO) {
            try {
                val placeFields = listOf(
                    Place.Field.ID,
                    Place.Field.NAME,
                    Place.Field.LAT_LNG,
                    Place.Field.ADDRESS,
                    Place.Field.ADDRESS_COMPONENTS
                )
                val request = FetchPlaceRequest.builder(placeId, placeFields)
                    .setSessionToken(currentSessionToken)
                    .build()

                val place = suspendCancellableCoroutine<Place?> { continuation ->
                    placesClient.fetchPlace(request)
                        .addOnSuccessListener { response ->
                            // Reset session token after fetch (Google billing optimization)
                            currentSessionToken = null
                            continuation.resume(response.place)
                        }
                        .addOnFailureListener {
                            continuation.resume(null)
                        }
                }

                if (place == null || place.latLng == null) {
                    return@withContext ApiResult.Failure(AppError.Network("Failed to resolve place coordinates"))
                }

                val latLng = place.latLng!!
                var street: String? = null
                var subLocality: String? = null
                var locality: String? = null
                var city: String = "Rewari"
                var state: String = "Haryana"
                var postalCode: String = ""

                place.addressComponents?.asList()?.forEach { comp ->
                    val types = comp.types
                    when {
                        types.contains("sublocality_level_1") || types.contains("sublocality") || types.contains("neighborhood") ->
                            subLocality = comp.name
                        types.contains("route") || types.contains("street_address") ->
                            street = comp.name
                        types.contains("locality") -> {
                            locality = comp.name
                            city = comp.name
                        }
                        types.contains("administrative_area_level_1") ->
                            state = comp.name
                        types.contains("postal_code") ->
                            postalCode = comp.name
                    }
                }

                val geocoded = GeocodedPlace(
                    placeId = place.id,
                    formattedAddress = place.address ?: place.name ?: "Selected Location",
                    houseNumber = null,
                    street = street,
                    subLocality = subLocality ?: place.name,
                    locality = locality,
                    city = city,
                    state = state,
                    postalCode = postalCode,
                    country = "India",
                    geoPoint = GeoPoint(latLng.latitude, latLng.longitude),
                    confidence = GeocodeConfidence.HIGH
                )

                ApiResult.Success(geocoded)
            } catch (e: Exception) {
                ApiResult.Failure(AppError.Network("Place details fetch error: ${e.localizedMessage}"))
            }
        }
}
