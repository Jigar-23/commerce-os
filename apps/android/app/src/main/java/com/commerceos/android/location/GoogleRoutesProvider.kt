package com.commerceos.android.location

import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.commerceos.android.network.NetworkClient
import com.google.android.gms.maps.model.LatLng
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

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
    private val baseUrlProvider: () -> String = { NetworkClient.baseUrl }
) {
    private val routeCache = ConcurrentHashMap<String, RouteResult>()
    private val inFlightRequests = ConcurrentHashMap<String, CompletableDeferred<ApiResult<RouteResult>>>()

    suspend fun getDrivingRoute(
        originLat: Double,
        originLng: Double,
        destLat: Double,
        destLng: Double
    ): ApiResult<RouteResult> = withContext(Dispatchers.IO) {
        val requestContext = currentCoroutineContext()
        requestContext.ensureActive()

        val cacheKey = "${originLat.format4()},${originLng.format4()}->${destLat.format4()},${destLng.format4()}"
        routeCache[cacheKey]?.let { return@withContext ApiResult.Success(it) }

        // Coalesce in-flight requests for identical coordinates
        val myDeferred = CompletableDeferred<ApiResult<RouteResult>>()
        val existingDeferred = inFlightRequests.putIfAbsent(cacheKey, myDeferred)
        if (existingDeferred != null) {
            try {
                return@withContext existingDeferred.await()
            } catch (cancelled: CancellationException) {
                throw cancelled
            }
        }

        try {
            val backendBase = baseUrlProvider().trimEnd('/')
            val backendUrl = "$backendBase/api/v1/delivery/route?originLat=$originLat&originLng=$originLng&destLat=$destLat&destLng=$destLng"
            val req = Request.Builder()
                .url(backendUrl)
                .get()
                .header("User-Agent", "CommerceOS-Customer/2.0")
                .build()

            val response = executeCancellable(req)
            val body = response.body?.string()

            requestContext.ensureActive()

            if (response.isSuccessful && !body.isNullOrBlank()) {
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
                        val successRes = ApiResult.Success(result)
                        myDeferred.complete(successRes)
                        return@withContext successRes
                    }
                }
            }

            val failureRes = ApiResult.Failure(AppError.Network("Road routing currently unavailable for coordinates"))
            myDeferred.complete(failureRes)
            return@withContext failureRes
        } catch (cancelled: CancellationException) {
            myDeferred.cancel(cancelled)
            throw cancelled
        } catch (e: Exception) {
            requestContext.ensureActive()
            val failureRes = ApiResult.Failure(AppError.Network("Road routing failed: ${e.message ?: "network error"}"))
            myDeferred.complete(failureRes)
            return@withContext failureRes
        } finally {
            inFlightRequests.remove(cacheKey, myDeferred)
        }
    }

    private suspend fun executeCancellable(request: Request): Response =
        suspendCancellableCoroutine { continuation ->
            val call = httpClient.newCall(request)
            continuation.invokeOnCancellation {
                call.cancel()
            }
            call.enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    continuation.resume(response)
                }

                override fun onFailure(call: Call, e: IOException) {
                    if (continuation.isCancelled) return
                    continuation.resumeWithException(e)
                }
            })
        }

    private fun Double.format4(): String = String.format(java.util.Locale.US, "%.4f", this)
}
