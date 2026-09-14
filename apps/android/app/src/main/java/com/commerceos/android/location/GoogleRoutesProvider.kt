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

import com.commerceos.android.network.NetworkClient
import org.json.JSONObject

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

        // 1. Authoritative Backend Routing Gateway (Google Maps Directions Engine on Host)
        try {
            val backendBase = NetworkClient.baseUrl.trimEnd('/')
            val backendUrl = "$backendBase/api/v1/delivery/route?originLat=$originLat&originLng=$originLng&destLat=$destLat&destLng=$destLng"
            val req = Request.Builder().url(backendUrl).get().build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string()
            if (res.isSuccessful && !body.isNullOrBlank()) {
                val json = JSONObject(body)
                if (json.optBoolean("ok", false) && json.has("waypoints")) {
                    val arr = json.getJSONArray("waypoints")
                    val pts = mutableListOf<LatLng>()
                    for (i in 0 until arr.length()) {
                        val pt = arr.getJSONObject(i)
                        pts.add(LatLng(pt.getDouble("lat"), pt.getDouble("lng")))
                    }
                    if (pts.size >= 2) {
                        val distKm = json.optDouble("distanceKm", 1.2)
                        val durMins = json.optInt("durationMins", 5)
                        val result = RouteResult(
                            waypoints = pts,
                            distanceText = "$distKm km",
                            distanceMeters = (distKm * 1000).toInt(),
                            durationText = "$durMins mins",
                            durationSeconds = durMins * 60,
                            encodedPolyline = ""
                        )
                        routeCache[cacheKey] = result
                        return@withContext ApiResult.Success(result)
                    }
                }
            }
        } catch (_: Exception) {}

        // 2. Direct Google Directions API
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

            if (response.isSuccessful && !body.isNullOrBlank()) {
                val apiResponse = gson.fromJson(body, DirectionsApiResponse::class.java)
                if (apiResponse.status == "OK" && !apiResponse.routes.isNullOrEmpty()) {
                    val route = apiResponse.routes[0]
                    val polylineStr = route.overviewPolyline?.points ?: ""
                    if (polylineStr.isNotBlank()) {
                        val decodedPoints = PolyUtil.decode(polylineStr)
                        if (decodedPoints.size >= 2) {
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
                            return@withContext ApiResult.Success(result)
                        }
                    }
                }
            }
        } catch (_: Exception) {}

        // 3. Direct OSRM Fallback
        try {
            val osrmUrl = "https://router.project-osrm.org/route/v1/driving/$originLng,$originLat;$destLng,$destLat?overview=full&geometries=geojson"
            val req = Request.Builder().url(osrmUrl).get().build()
            val res = httpClient.newCall(req).execute()
            val body = res.body?.string()
            if (res.isSuccessful && !body.isNullOrBlank()) {
                val json = JSONObject(body)
                val routes = json.optJSONArray("routes")
                if (routes != null && routes.length() > 0) {
                    val geom = routes.getJSONObject(0).optJSONObject("geometry")
                    val coords = geom?.optJSONArray("coordinates")
                    if (coords != null && coords.length() >= 2) {
                        val pts = mutableListOf<LatLng>()
                        for (i in 0 until coords.length()) {
                            val c = coords.getJSONArray(i)
                            pts.add(LatLng(c.getDouble(1), c.getDouble(0)))
                        }
                        val distMeters = routes.getJSONObject(0).optDouble("distance", 1200.0)
                        val durSec = routes.getJSONObject(0).optDouble("duration", 300.0)
                        val distKm = Math.round((distMeters / 1000.0) * 10.0) / 10.0
                        val durMins = Math.max(1, (durSec / 60.0).toInt())
                        val result = RouteResult(
                            waypoints = pts,
                            distanceText = "$distKm km",
                            distanceMeters = distMeters.toInt(),
                            durationText = "$durMins mins",
                            durationSeconds = durSec.toInt(),
                            encodedPolyline = ""
                        )
                        routeCache[cacheKey] = result
                        return@withContext ApiResult.Success(result)
                    }
                }
            }
        } catch (_: Exception) {}

        ApiResult.Failure(AppError.Network("Road routing currently unavailable for coordinates"))
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
