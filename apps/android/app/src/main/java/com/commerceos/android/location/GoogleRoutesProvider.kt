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
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

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

    private class InFlightRouteTask(
        val deferred: CompletableDeferred<ApiResult<RouteResult>> = CompletableDeferred(),
        val waiterCount: AtomicInteger = AtomicInteger(1),
        @Volatile var activeCall: Call? = null
    )

    private val inFlightRequests = ConcurrentHashMap<String, InFlightRouteTask>()

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

        // Coalesce in-flight requests for identical coordinates with waiter reference counting (Finding 7)
        val newTask = InFlightRouteTask()
        val existingTask = inFlightRequests.putIfAbsent(cacheKey, newTask)
        val task = existingTask ?: newTask
        val isLeader = (existingTask == null)

        if (!isLeader) {
            task.waiterCount.incrementAndGet()
        } else {
            try {
                val backendBase = baseUrlProvider().trimEnd('/')
                val backendUrl = "$backendBase/api/v1/delivery/route?originLat=$originLat&originLng=$originLng&destLat=$destLat&destLng=$destLng"
                val reqBuilder = Request.Builder()
                    .url(backendUrl)
                    .get()
                    .header("User-Agent", "CommerceOS-Customer/2.0")

                val token = NetworkClient.authTokenProvider()
                if (token.isNotBlank()) {
                    reqBuilder.header("Authorization", "Bearer $token")
                }

                val req = reqBuilder.build()
                val call = httpClient.newCall(req)
                task.activeCall = call

                call.enqueue(object : Callback {
                    override fun onResponse(call: Call, response: Response) {
                        try {
                            response.use { resp ->
                                val body = resp.body?.string()
                                if (resp.isSuccessful && !body.isNullOrBlank()) {
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
                                            task.deferred.complete(ApiResult.Success(result))
                                            return
                                        }
                                    }
                                }
                                task.deferred.complete(ApiResult.Failure(AppError.Network("Road routing currently unavailable for coordinates")))
                            }
                        } catch (e: Exception) {
                            task.deferred.complete(ApiResult.Failure(AppError.Network("Road routing failed: ${e.message ?: "network error"}")))
                        } finally {
                            inFlightRequests.remove(cacheKey, task)
                        }
                    }

                    override fun onFailure(call: Call, e: IOException) {
                        try {
                            task.deferred.complete(ApiResult.Failure(AppError.Network("Road routing failed: ${e.message ?: "network error"}")))
                        } finally {
                            inFlightRequests.remove(cacheKey, task)
                        }
                    }
                })
            } catch (e: Exception) {
                inFlightRequests.remove(cacheKey, task)
                val failureRes = ApiResult.Failure(AppError.Network("Road routing failed to dispatch: ${e.message ?: "network error"}"))
                task.deferred.complete(failureRes)
            }
        }

        try {
            return@withContext task.deferred.await()
        } catch (cancelled: CancellationException) {
            val remaining = task.waiterCount.decrementAndGet()
            if (remaining <= 0) {
                task.activeCall?.cancel()
                task.deferred.cancel(cancelled)
                inFlightRequests.remove(cacheKey, task)
            }
            throw cancelled
        }
    }

    private fun Double.format4(): String = String.format(java.util.Locale.US, "%.4f", this)
}
