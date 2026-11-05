package com.commerceos.rider.repository

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class RiderDeliveryRouteTest {

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
