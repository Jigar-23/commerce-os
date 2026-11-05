package com.commerceos.android.location

import com.commerceos.android.network.ApiResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class GoogleRoutesProviderTest {

    private val sampleJson = """
        {
            "ok": true,
            "distanceKm": 3.4,
            "durationMins": 11,
            "waypoints": [
                {"lat": 12.9716, "lng": 77.5946},
                {"lat": 12.9352, "lng": 77.6245}
            ]
        }
    """.trimIndent()

    @Test
    fun testDrivingRoute_requestsBackendRouteGatewayOnly() = runBlocking {
        var capturedUrl: String? = null

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val request = chain.request()
                capturedUrl = request.url.toString()
                Response.Builder()
                    .request(request)
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val provider = GoogleRoutesProvider(
            httpClient = client,
            baseUrlProvider = { "https://api.commerceos.test" }
        )

        val result = provider.getDrivingRoute(12.9716, 77.5946, 12.9352, 77.6245)

        if (result !is ApiResult.Success) {
            val netDetail = ((result as? ApiResult.Failure)?.error as? com.commerceos.android.network.AppError.Network)?.detail
            fail("Expected ApiResult.Success but was $result with detail: $netDetail")
        }
        val data = (result as ApiResult.Success).data
        assertEquals(2, data.waypoints.size)
        assertEquals("3.4 km", data.distanceText)
        assertEquals("11 mins", data.durationText)

        assertNotNull("Request URL must be captured", capturedUrl)
        assertTrue(
            "Request must target backend route gateway",
            capturedUrl!!.startsWith("https://api.commerceos.test/api/v1/delivery/route")
        )
        assertFalse(
            "Must never leak coordinates to public OSRM server",
            capturedUrl!!.contains("project-osrm.org")
        )
        assertFalse(
            "Must never send client directions request to Google Directions web API",
            capturedUrl!!.contains("maps.googleapis.com")
        )
    }

    @Test
    fun testDrivingRoute_inFlightCancellationAbortsCall() = runBlocking {
        val inFlightLatch = CountDownLatch(1)
        val cancelledLatch = CountDownLatch(1)

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                val call = chain.call()
                inFlightLatch.countDown()
                // Wait until cancelled or timeout
                try {
                    cancelledLatch.await(5, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (call.isCanceled()) {
                    throw IOException("Canceled")
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val provider = GoogleRoutesProvider(
            httpClient = client,
            baseUrlProvider = { "https://api.commerceos.test" }
        )

        var cancellationPropagated = false
        val job = launch(Dispatchers.IO) {
            try {
                provider.getDrivingRoute(12.9716, 77.5946, 12.9352, 77.6245)
            } catch (e: CancellationException) {
                cancellationPropagated = true
                throw e
            }
        }

        // Wait until request enters in-flight state
        val inFlight = inFlightLatch.await(5, TimeUnit.SECONDS)
        assertTrue("Request must be actively in flight", inFlight)

        // Cancel the coroutine and release latch
        job.cancel(CancellationException("Screen left composition"))
        cancelledLatch.countDown()
        job.join()

        assertTrue("Job must be marked cancelled", job.isCancelled)
        assertTrue("CancellationException must be propagated out", cancellationPropagated)
    }

    @Test
    fun testDrivingRoute_requestCoalescingForDuplicateCoordinates() = runBlocking {
        val networkCallCount = AtomicInteger(0)
        val interceptorLatch = CountDownLatch(1)

        val client = OkHttpClient.Builder()
            .addInterceptor(Interceptor { chain ->
                networkCallCount.incrementAndGet()
                // Wait slightly to ensure second request arrives while first is in-flight
                try {
                    interceptorLatch.await(300, TimeUnit.MILLISECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body(sampleJson.toResponseBody("application/json".toMediaType()))
                    .build()
            })
            .build()

        val provider = GoogleRoutesProvider(
            httpClient = client,
            baseUrlProvider = { "https://api.commerceos.test" }
        )

        // Launch two simultaneous calls for the same coordinates
        val call1 = async(Dispatchers.IO) {
            provider.getDrivingRoute(12.9716, 77.5946, 12.9352, 77.6245)
        }
        val call2 = async(Dispatchers.IO) {
            provider.getDrivingRoute(12.9716, 77.5946, 12.9352, 77.6245)
        }

        interceptorLatch.countDown()
        val res1 = call1.await()
        val res2 = call2.await()

        assertTrue(res1 is ApiResult.Success)
        assertTrue(res2 is ApiResult.Success)
        assertEquals(1, networkCallCount.get())
        assertEquals((res1 as ApiResult.Success).data.distanceText, (res2 as ApiResult.Success).data.distanceText)
    }
}
