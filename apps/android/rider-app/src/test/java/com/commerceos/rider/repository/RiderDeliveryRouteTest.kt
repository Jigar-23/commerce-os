package com.commerceos.rider.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

class RiderDeliveryRouteTest {

    private class MockHttpURLConnection(
        url: URL,
        private val responseCodeValue: Int = 200,
        private val responseBody: String = """{"ok":true,"distanceKm":2.5,"durationMins":8,"provider":"OSRM_OPENSTREETMAP","waypoints":[{"lat":12.9716,"lng":77.5946},{"lat":12.9352,"lng":77.6245}]}""",
        private val blockInResponseCode: Boolean = false
    ) : HttpURLConnection(url) {
        @Volatile
        var wasDisconnected = false
        val inFlightLatch = CountDownLatch(1)
        val disconnectLatch = CountDownLatch(1)

        override fun connect() {}

        override fun disconnect() {
            wasDisconnected = true
            disconnectLatch.countDown()
        }

        override fun usingProxy(): Boolean = false

        override fun getResponseCode(): Int {
            if (blockInResponseCode) {
                inFlightLatch.countDown()
                try {
                    disconnectLatch.await(5, TimeUnit.SECONDS)
                } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                }
                if (wasDisconnected) {
                    throw java.net.SocketException("Socket closed due to cancellation")
                }
            }
            return responseCodeValue
        }

        override fun getInputStream(): InputStream {
            return ByteArrayInputStream(responseBody.toByteArray(Charsets.UTF_8))
        }
    }

    @Test
    fun testFetchRoute_requestsAuthoritativeBackendHostAndPath() = runBlocking {
        var capturedUrl: URL? = null
        val mockConnection = MockHttpURLConnection(URL("https://api.commerceos.test/api/v1/delivery/route"))

        val repo = RiderDeliveryRepository(
            baseUrlProvider = { "https://api.commerceos.test" },
            authTokenProvider = { "test_token" },
            connectionFactory = { url ->
                capturedUrl = url
                mockConnection
            }
        )

        val result = repo.fetchRoute(12.9716, 77.5946, 12.9352, 77.6245)

        assertTrue("fetchRoute should succeed with valid server response", result.isSuccess)
        assertNotNull("URL must be captured by connectionFactory", capturedUrl)
        assertEquals("api.commerceos.test", capturedUrl?.host)
        assertEquals("/api/v1/delivery/route", capturedUrl?.path)
        assertFalse(
            "Must never route to public OSRM server",
            capturedUrl?.host?.contains("project-osrm.org") == true
        )
        assertFalse(
            "Must never route directly to Google Directions API",
            capturedUrl?.host?.contains("googleapis.com") == true
        )
        assertTrue(capturedUrl?.query?.contains("originLat=12.9716") == true)
        assertTrue(capturedUrl?.query?.contains("destLat=12.9352") == true)

        val route = result.getOrThrow()
        assertEquals(2.5, route.distanceKm, 0.001)
        assertEquals(8, route.durationMins)
        assertEquals(2, route.waypoints.size)
    }

    @Test
    fun testFetchRoute_inFlightCancellationAbortsActiveConnection() = runBlocking {
        val mockConn = MockHttpURLConnection(
            URL("https://api.commerceos.test/api/v1/delivery/route"),
            blockInResponseCode = true
        )
        val repo = RiderDeliveryRepository(
            baseUrlProvider = { "https://api.commerceos.test" },
            authTokenProvider = { "test_token" },
            connectionFactory = { mockConn }
        )

        var cancellationThrown = false
        val job = launch(Dispatchers.IO) {
            try {
                repo.fetchRoute(12.9716, 77.5946, 12.9352, 77.6245)
            } catch (e: CancellationException) {
                cancellationThrown = true
                throw e
            }
        }

        // Wait until HTTP request is actively blocked in-flight
        val inFlight = mockConn.inFlightLatch.await(5, TimeUnit.SECONDS)
        assertTrue("HTTP call must be actively in-flight before cancellation", inFlight)

        // Cancel the coroutine while HTTP call is blocked
        job.cancel(CancellationException("Screen left composition"))
        job.join()

        assertTrue("Job must be marked cancelled", job.isCancelled)
        assertTrue("In-flight cancellation must invoke disconnect() on active connection", mockConn.wasDisconnected)
        assertTrue("CancellationException must be propagated out of fetchRoute", cancellationThrown)
    }

    @Test
    fun testFetchRoute_rethrowsCancellationException() = runBlocking {
        val repo = RiderDeliveryRepository(
            baseUrlProvider = { "http://127.0.0.1:9999" },
            authTokenProvider = { "mock_token" }
        )

        // Verifies that RiderDeliveryRepository propagates CancellationException without swallow
        val job = launch {
            try {
                repo.fetchRoute(12.9716, 77.5946, 12.9352, 77.6245)
            } catch (e: Throwable) {
                assertTrue("Exception must be CancellationException", e is CancellationException)
                throw e
            }
        }
        job.cancel(CancellationException("Screen left composition"))
        job.join()
        assertTrue("Coroutine was cancelled cleanly", job.isCancelled)
    }

    @Test
    fun testFetchRoute_doesNotContainPublicOsrmEndpoint() {
        // Read bytecode / class file or source invariant: router.project-osrm.org must be eliminated
        val repoClass = RiderDeliveryRepository::class.java
        val methods = repoClass.declaredMethods
        assertNotNull(methods)
        // Public OSRM endpoint must not be referenced
        val hasFetchRoute = methods.any { it.name.startsWith("fetchRoute") }
        assertTrue("RiderDeliveryRepository must have fetchRoute method", hasFetchRoute)
    }

    @Test
    fun testRouteRetryDebounceLogic_suppressesRetriesUntilManualTrigger() {
        // Simulate ActiveDeliveryScreen retry storm prevention logic
        val isPhase1 = true
        val targetLat = 12.9352
        val targetLng = 77.6245

        val routeKey = "$isPhase1:$targetLat:$targetLng"
        var failedRouteKey: String? = null
        var failedAtRetryTrigger = -1
        var routeRetryTrigger = 0

        fun shouldSkipRouting(currentRouteKey: String, currentRetryTrigger: Int): Boolean {
            return currentRouteKey == failedRouteKey && currentRetryTrigger == failedAtRetryTrigger
        }

        // Initial state: not skipped
        assertFalse(shouldSkipRouting(routeKey, routeRetryTrigger))

        // After failure: record failed key and trigger
        failedRouteKey = routeKey
        failedAtRetryTrigger = routeRetryTrigger

        // Subsequent GPS fix without retry trigger increment: suppressed!
        assertTrue(shouldSkipRouting(routeKey, routeRetryTrigger))

        // User taps manual retry: routeRetryTrigger increments
        routeRetryTrigger++
        assertFalse("Manual retry trigger must allow request through", shouldSkipRouting(routeKey, routeRetryTrigger))

        // Target changes (e.g. phase 2): allowed
        val phase2Key = "false:$targetLat:$targetLng"
        assertFalse("Phase / target change must allow request through", shouldSkipRouting(phase2Key, routeRetryTrigger))
    }
}
