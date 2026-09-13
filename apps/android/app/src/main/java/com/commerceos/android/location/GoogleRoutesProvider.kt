package com.commerceos.android.location

import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.google.android.gms.maps.model.LatLng
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import com.google.maps.android.PolyUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request

data class RouteResult(
    val waypoints: List<LatLng>,
    val distanceText: String,
    val distanceMeters: Int,
    val durationText: String,
    val durationSeconds: Int,
    val encodedPolyline: String
)

class GoogleRoutesProvider(
    private val httpClient: OkHttpClient = OkHttpClient(),
    private val apiKey: String = "AIzaSyCzi_sMDds2_im406sGTCU8WAFZoTyNg5c"
) {
    private val gson = Gson()
    private val routeCache = mutableMapOf<String, RouteResult>()

    suspend fun getDrivingRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): ApiResult<RouteResult> = withContext(Dispatchers.IO) {
        val cacheKey = "${originLat.format4()},${originLng.format4()}->${destLat.format4()},${destLng.format4()}"
        routeCache[cacheKey]?.let { return@withContext ApiResult.Success(it) }

        try {
            val url = "https://maps.googleapis.com/maps/api/directions/json" +
                    "?origin=$originLat,$originLng" +
                    "&destination=$destLat,$destLng" +
                    "&mode=driving" +
                    "&key=$apiKey"

            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            val response = httpClient.newCall(request).execute()
            val body = response.body?.string()

            if (!response.isSuccessful || body.isNullOrBlank()) {
                return@withContext ApiResult.Failure(
                    AppError.Network("Google Directions HTTP error ${response.code}")
                )
            }

            val apiResponse = gson.fromJson(body, DirectionsApiResponse::class.java)
            if (apiResponse.status != "OK" || apiResponse.routes.isNullOrEmpty()) {
                return@withContext ApiResult.Failure(
                    AppError.Network("Google Directions error: ${apiResponse.status} - ${apiResponse.errorMessage ?: "No routes found"}")
                )
            }

            val route = apiResponse.routes[0]
            val polylineStr = route.overviewPolyline?.points ?: ""
            val decodedPoints = if (polylineStr.isNotBlank()) {
                PolyUtil.decode(polylineStr)
            } else {
                listOf(LatLng(originLat, originLng), LatLng(destLat, destLng))
            }

            val leg = route.legs?.firstOrNull()
            val distanceText = leg?.distance?.text ?: "1.2 km"
            val distanceMeters = leg?.distance?.value ?: 1200
            val durationText = leg?.duration?.text ?: "6 mins"
            val durationSeconds = leg?.duration?.value ?: 360

            val result = RouteResult(
                waypoints = decodedPoints,
                distanceText = distanceText,
                distanceMeters = distanceMeters,
                durationText = durationText,
                durationSeconds = durationSeconds,
                encodedPolyline = polylineStr
            )

            routeCache[cacheKey] = result
            ApiResult.Success(result)
        } catch (e: Exception) {
            ApiResult.Failure(AppError.Network("Failed to compute Google Route: ${e.localizedMessage}"))
        }
    }

    private fun Double.format4(): String = String.format(java.util.Locale.US, "%.4f", this)
}

// Data Transfer Objects for Google Directions API
private data class DirectionsApiResponse(
    val status: String,
    val routes: List<DirectionsRoute>?,
    @SerializedName("error_message") val errorMessage: String?
)

private data class DirectionsRoute(
    @SerializedName("overview_polyline") val overviewPolyline: DirectionsPolyline?,
    val legs: List<DirectionsLeg>?
)

private data class DirectionsPolyline(
    val points: String
)

private data class DirectionsLeg(
    val distance: TextValue?,
    val duration: TextValue?
)

private data class TextValue(
    val text: String,
    val value: Int
)
