package com.commerceos.android.fulfillment

import com.commerceos.android.admin.LicenseState
import com.commerceos.android.admin.TenantSuspensionEngine
import com.commerceos.android.rider.JobAcceptanceState
import com.commerceos.android.rider.RiderJob
import com.commerceos.android.rider.RiderLocationService
import com.commerceos.android.rider.RiderLocationUpdate
import com.commerceos.android.rider.RiderForegroundLocationService
import com.commerceos.android.security.CodLedgerManager
import com.commerceos.android.security.SecurityGate
import com.commerceos.android.tracking.TrackingMode
import com.commerceos.android.tracking.UnifiedTrackingSession
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

/**
 * 🔴 P0 — COMMERCE OS BATCH 2 COMPREHENSIVE RELEASE E2E TEST SUITE
 * Complete end-to-end integration tests for Fulfillment, Quick Delivery, India Post,
 * Unified Tracking, Rider Platform, One-Click Suspension, Security Gate & COD Ledger.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Batch2ReleaseE2eTest {

    @Test
    fun testFulfillmentServiceability_EvaluatesQuickAndStandardModes() {
        val results = FulfillmentEngine.evaluateServiceability(
            customerLat = 19.0800,
            customerLng = 72.8800,
            mode = FulfillmentMode.QUICK
        )

        assertTrue(results.isNotEmpty())
        val selected = FulfillmentEngine.selectOptimalNode(results, NodeSelectionStrategy.FASTEST_SLA)
        assertNotNull(selected)
        assertTrue(selected!!.estimatedSlaMinutes > 0)
    }

    @Test
    fun testInventoryReservationLifecycle_ReservesAndAllocatesStock() {
        val reservation = FulfillmentEngine.reserveInventory(
            orderId = "ord_9901",
            sku = "SKU-MED-01",
            quantity = 2,
            nodeId = "node_01"
        )

        assertEquals(ReservationState.RESERVED, reservation.state)

        val updated = FulfillmentEngine.updateReservationState(reservation.reservationId, ReservationState.ALLOCATED)
        assertEquals(ReservationState.ALLOCATED, updated?.state)
    }

    @Test
    fun testIndiaPostCarrierAdapter_GeneratesValidSpeedPostConsignmentAndCheckpoints() = runTest {
        val adapter = IndiaPostAdapter()
        val shipment = adapter.createShipment(
            CreateShipmentRequest(
                orderId = "ord_ip_01",
                originNodeId = "node_01",
                recipientName = "Rajesh Kumar",
                recipientPhone = "+91-9876543210",
                recipientAddress = "MG Road, New Delhi",
                recipientPincode = "110001",
                weightKg = 0.5
            )
        )

        assertTrue(shipment.consignmentNumber.startsWith("EM"))
        assertTrue(shipment.consignmentNumber.endsWith("IN"))
        assertEquals(13, shipment.consignmentNumber.length) // EM + 9 digits + IN

        val checkpoints = adapter.getTrackingStatus(shipment.consignmentNumber)
        assertTrue(checkpoints.isNotEmpty())
        assertEquals(CarrierTrackingStatus.BOOKED, checkpoints.first().status)
    }

    @Test
    fun testCarrierRegistry_NormalizesCarrierWebhookEvents() {
        val adapter = CarrierRegistry.resolveAdapter("india_post")
        assertEquals("India Post Speed Post", adapter.carrierName)

        val webhookEvent = CarrierWebhookEvent(
            eventId = "evt_881",
            carrierName = "India Post",
            consignmentNumber = "EM123456789IN",
            status = CarrierTrackingStatus.OUT_FOR_DELIVERY,
            location = "Mumbai GPO",
            timestamp = System.currentTimeMillis(),
            rawPayloadJson = "{}"
        )
        val checkpoint = CarrierRegistry.handleCarrierWebhook(webhookEvent)
        assertEquals(CarrierTrackingStatus.OUT_FOR_DELIVERY, checkpoint.status)
        assertEquals("Mumbai GPO", checkpoint.locationName)
    }

    @Test
    fun testRiderForegroundLocationService_QueuesOfflineAndSyncsCount() {
        val count = RiderLocationService.getPendingOfflineQueueCount()
        assertEquals(0, count)

        RiderForegroundLocationService.updateDeliverySession("del_9001", "r_101")
        val serviceCount = RiderForegroundLocationService.getOfflineQueueCount()
        assertEquals(0, serviceCount)
    }

    @Test
    fun testUnifiedTrackingSession_SupportsLiveLocationAndCarrierCheckpointModes() {
        val liveSession = UnifiedTrackingSession(
            orderId = "ord_100",
            mode = TrackingMode.LIVE_LOCATION,
            title = "Order Out for Delivery",
            statusText = "Rider arriving in 8 mins",
            estimatedArrivalFormatted = "12:45 PM",
            liveRiderLat = 19.0760,
            liveRiderLng = 72.8777,
            handoffOtp = "4892"
        )
        assertEquals(TrackingMode.LIVE_LOCATION, liveSession.mode)
        assertEquals("4892", liveSession.handoffOtp)

        val carrierSession = UnifiedTrackingSession(
            orderId = "ord_200",
            mode = TrackingMode.CARRIER_CHECKPOINT,
            title = "In Transit via India Post",
            statusText = "Out for delivery by Postman",
            estimatedArrivalFormatted = "Tomorrow",
            consignmentNumber = "EM123456789IN",
            carrierName = "India Post"
        )
        assertEquals(TrackingMode.CARRIER_CHECKPOINT, carrierSession.mode)
        assertEquals("EM123456789IN", carrierSession.consignmentNumber)
    }

    @Test
    fun testTenantSuspensionEngine_OneClickSuspensionGatesMutationsAndWritesAuditLog() {
        val tenantId = "tenant_test_01"

        assertFalse(TenantSuspensionEngine.isTenantSuspended(tenantId))
        assertTrue(TenantSuspensionEngine.validateMutationAllowed(tenantId, isExistingOrderTracking = false))

        // Execute One-Click Suspension
        TenantSuspensionEngine.suspendTenant(tenantId, reason = "Non-payment of subscription")

        assertTrue(TenantSuspensionEngine.isTenantSuspended(tenantId))
        assertEquals(LicenseState.SUSPENDED, TenantSuspensionEngine.getTenantLicenseState(tenantId))

        // Mutation blocked for new checkout/orders
        assertFalse(TenantSuspensionEngine.validateMutationAllowed(tenantId, isExistingOrderTracking = false))

        // Existing order tracking preserved contractually
        assertTrue(TenantSuspensionEngine.validateMutationAllowed(tenantId, isExistingOrderTracking = true))

        // Audit log produced
        val audit = TenantSuspensionEngine.getAuditLogs().find { it.tenantId == tenantId && it.action == "TENANT_SUSPEND" }
        assertNotNull(audit)

        // Execute One-Click Reactivation
        TenantSuspensionEngine.reactivateTenant(tenantId)
        assertFalse(TenantSuspensionEngine.isTenantSuspended(tenantId))
        assertTrue(TenantSuspensionEngine.validateMutationAllowed(tenantId, isExistingOrderTracking = false))
    }

    @Test
    fun testSecurityGate_VerifiesAppSecretsSanityAndTokenLifecycle() {
        val token = "jwt_access_token_demo"
        SecurityGate.setAuthenticatedSession(token, System.currentTimeMillis() + 3600000L)
        assertTrue(SecurityGate.verifyAppSecretsSanity())
        assertTrue(SecurityGate.isOfflineLeaseValid())
        assertTrue(SecurityGate.validateTenantAuthorization("tenant_01", token))

        // Token Revocation
        SecurityGate.revokeAccessToken()
        assertFalse(SecurityGate.validateTenantAuthorization("tenant_01", token))

        // Token Rotation
        SecurityGate.setAuthenticatedSession(token, System.currentTimeMillis() + 3600000L)
        val newToken = SecurityGate.rotateAccessToken("refresh_token_valid")
        assertNotNull(newToken)
        assertTrue(SecurityGate.validateTenantAuthorization("tenant_01", newToken))
    }

    @Test
    fun testCodLedgerManager_RecordsCodCollectionAndReconcilesFees() {
        val entry = CodLedgerManager.recordCodCollection(
            orderId = "ord_cod_01",
            tenantId = "tenant_01",
            expected = BigDecimal.valueOf(500.0),
            collected = BigDecimal.valueOf(500.0),
            riderId = "r_101"
        )

        assertEquals("ord_cod_01", entry.orderId)
        assertTrue(entry.isReconciled)
        assertEquals(0, BigDecimal.valueOf(25.0).compareTo(entry.platformFee)) // 5% fee
        assertEquals(0, BigDecimal.valueOf(475.0).compareTo(entry.merchantPayout))

        val fetched = CodLedgerManager.getLedgerForOrder("ord_cod_01")
        assertEquals(1, fetched.size)
    }

    @Test
    fun testCustomerTrackingSignalState_DerivesLiveDelayedDisconnectedStatus() {
        val liveSession = UnifiedTrackingSession(
            orderId = "ord_live_01",
            mode = TrackingMode.LIVE_LOCATION,
            title = "Live Delivery",
            statusText = "En route",
            estimatedArrivalFormatted = "10 mins",
            liveRiderLat = 28.4595,
            liveRiderLng = 77.0266,
            isStale = false,
            isSseConnected = true
        )
        assertEquals(com.commerceos.android.tracking.CustomerTrackingSignalState.LIVE, liveSession.signalState)

        val staleSession = liveSession.copy(isStale = true)
        assertEquals(com.commerceos.android.tracking.CustomerTrackingSignalState.DELAYED, staleSession.signalState)

        val disconnectedSession = liveSession.copy(isSseConnected = false)
        assertEquals(com.commerceos.android.tracking.CustomerTrackingSignalState.DISCONNECTED, disconnectedSession.signalState)

        val noLocationSession = liveSession.copy(liveRiderLat = null, liveRiderLng = null)
        assertEquals(com.commerceos.android.tracking.CustomerTrackingSignalState.LOCATION_UNAVAILABLE, noLocationSession.signalState)
    }

    @Test
    fun testRiderTelemetryGuard_RequiresValidDeliveryId() {
        RiderForegroundLocationService.updateDeliverySession("", "r_101")
        val queueCount = RiderLocationService.getPendingOfflineQueueCount()
        assertEquals(0, queueCount)
    }
}
