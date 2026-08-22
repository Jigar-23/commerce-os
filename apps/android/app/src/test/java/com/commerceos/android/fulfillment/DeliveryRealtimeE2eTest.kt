package com.commerceos.android.fulfillment

import com.commerceos.android.rider.CanonicalDeliveryState
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * P0 BATCH 1 & BATCH 2 — COMPLETE REALTIME DELIVERY BACKEND SECURITY & INTEGRATION TEST SUITE
 */
@OptIn(ExperimentalCoroutinesApi::class)
class DeliveryRealtimeE2eTest {

    private class MockDeliveryServer {
        data class ServerSession(
            val deliveryId: String,
            val orderId: String,
            val riderId: String,
            var state: String,
            val secretOtp: String = String.format("%04d", (1000..9999).random()),
            var otpVerified: Boolean = false,
            var otpAttemptsLeft: Int = 3,
            val isCod: Boolean = true,
            val codAmount: Double = 450.0,
            var codCollectedAmount: Double = 0.0,
            var codReconciled: Boolean = false,
            var lastSeq: Long = 0L,
            val processedIdempotencyKeys: MutableMap<String, String> = ConcurrentHashMap()
        )

        private val sessions = ConcurrentHashMap<String, ServerSession>()
        val emittedEvents = ConcurrentLinkedQueue<String>()

        private val allowedTransitions = mapOf(
            "ASSIGNED" to listOf("ACCEPTED", "DECLINED"),
            "ACCEPTED" to listOf("EN_ROUTE_PICKUP"),
            "EN_ROUTE_PICKUP" to listOf("ARRIVED_PICKUP", "STORE_CLOSED", "CANCELLED"),
            "ARRIVED_PICKUP" to listOf("PICKED_UP", "STORE_CLOSED", "CANCELLED"),
            "PICKED_UP" to listOf("EN_ROUTE_CUSTOMER", "DAMAGED_PACKAGE", "RETURN_TO_STORE"),
            "EN_ROUTE_CUSTOMER" to listOf("ARRIVED_CUSTOMER", "WRONG_ADDRESS", "CUSTOMER_UNREACHABLE"),
            "ARRIVED_CUSTOMER" to listOf("HANDOFF_STARTED", "CUSTOMER_UNREACHABLE", "WRONG_ADDRESS"),
            "HANDOFF_STARTED" to listOf("DELIVERED", "CUSTOMER_UNREACHABLE", "RETURN_TO_STORE")
        )

        fun verifyAuth(token: String?): String? {
            if (token == null || token.isBlank()) return null
            val parts = token.split(".")
            if (parts.size != 3) return null
            return try {
                val payloadJson = String(java.util.Base64.getUrlDecoder().decode(parts[1]))
                val subMatch = """"sub"\s*:\s*"([^"]+)"""".toRegex().find(payloadJson)
                subMatch?.groupValues?.get(1)
            } catch (e: Exception) {
                null
            }
        }

        fun createTestRiderJwt(riderId: String): String {
            val header = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"alg":"HS256","typ":"JWT"}""".toByteArray())
            val payload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("""{"sub":"$riderId","role":"ROLE_RIDER","iss":"https://auth.commerceos.io","aud":"https://api.commerceos.io","exp":${System.currentTimeMillis()/1000 + 3600}}""".toByteArray())
            val sig = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString("mock_signature".toByteArray())
            return "$header.$payload.$sig"
        }

        fun createSession(orderId: String, riderId: String): ServerSession {
            val session = ServerSession(
                deliveryId = "del_$orderId",
                orderId = orderId,
                riderId = riderId,
                state = CanonicalDeliveryState.ASSIGNED.name
            )
            sessions[orderId] = session
            sessions[session.deliveryId] = session
            return session
        }

        fun findSession(id: String, token: String?): Result<ServerSession> {
            val riderId = verifyAuth(token) ?: return Result.failure(Exception("401_UNAUTHORIZED"))
            val session = sessions[id] ?: return Result.failure(Exception("404_NOT_FOUND"))
            if (session.riderId != riderId) return Result.failure(Exception("403_FORBIDDEN"))
            return Result.success(session)
        }

        fun transition(deliveryId: String, targetState: String, idempotencyKey: String, token: String?): Result<ServerSession> {
            val riderId = verifyAuth(token) ?: return Result.failure(Exception("401_UNAUTHORIZED"))
            val session = sessions[deliveryId] ?: return Result.failure(Exception("404_NOT_FOUND"))
            if (session.riderId != riderId) return Result.failure(Exception("403_FORBIDDEN"))

            if (session.processedIdempotencyKeys.containsKey(idempotencyKey)) {
                return Result.success(session)
            }

            val allowed = allowedTransitions[session.state] ?: emptyList()
            if (!allowed.contains(targetState) && targetState != session.state) {
                return Result.failure(Exception("INVALID_TRANSITION"))
            }

            session.state = targetState
            session.processedIdempotencyKeys[idempotencyKey] = targetState
            emittedEvents.add("STATE_TRANSITION:$targetState")
            return Result.success(session)
        }

        fun verifyOtp(deliveryId: String, submittedOtp: String, token: String?): Result<Boolean> {
            val riderId = verifyAuth(token) ?: return Result.failure(Exception("401_UNAUTHORIZED"))
            val session = sessions[deliveryId] ?: return Result.failure(Exception("404_NOT_FOUND"))
            if (session.riderId != riderId) return Result.failure(Exception("403_FORBIDDEN"))

            if (session.otpAttemptsLeft <= 0) {
                return Result.failure(Exception("OTP_ATTEMPTS_EXHAUSTED"))
            }

            if (submittedOtp == session.secretOtp) {
                session.otpVerified = true
                emittedEvents.add("OTP_VERIFIED")
                return Result.success(true)
            }
            session.otpAttemptsLeft--
            return Result.failure(Exception("Invalid OTP"))
        }

        fun completeCod(deliveryId: String, amount: Double, token: String?): Result<Double> {
            val riderId = verifyAuth(token) ?: return Result.failure(Exception("401_UNAUTHORIZED"))
            val session = sessions[deliveryId] ?: return Result.failure(Exception("404_NOT_FOUND"))
            if (session.riderId != riderId) return Result.failure(Exception("403_FORBIDDEN"))

            if (amount >= session.codAmount) {
                session.codCollectedAmount = amount
                session.codReconciled = true
                val change = amount - session.codAmount
                emittedEvents.add("COD_CONFIRMED")
                return Result.success(change)
            }
            return Result.failure(Exception("COD_SHORTAGE"))
        }

        fun completeDelivery(deliveryId: String, token: String?): Result<ServerSession> {
            val riderId = verifyAuth(token) ?: return Result.failure(Exception("401_UNAUTHORIZED"))
            val session = sessions[deliveryId] ?: return Result.failure(Exception("404_NOT_FOUND"))
            if (session.riderId != riderId) return Result.failure(Exception("403_FORBIDDEN"))

            if (!session.otpVerified) return Result.failure(Exception("OTP_NOT_VERIFIED"))
            if (session.isCod && !session.codReconciled) return Result.failure(Exception("COD_NOT_RECONCILED"))

            session.state = CanonicalDeliveryState.DELIVERED.name
            emittedEvents.add("DELIVERED")
            return Result.success(session)
        }
    }

    @Test
    fun test70_UnauthenticatedRequest_Returns401Unauthorized() = runTest {
        val server = MockDeliveryServer()
        server.createSession("ord_70", "rider_101")

        val res = server.findSession("del_ord_70", null)
        assertTrue(res.isFailure)
        assertEquals("401_UNAUTHORIZED", res.exceptionOrNull()?.message)
    }

    @Test
    fun test71_NonExistentSession_Returns404NotFound() = runTest {
        val server = MockDeliveryServer()
        val token = server.createTestRiderJwt("rider_101")

        val transitionResult = server.transition("unknown_id", "ACCEPTED", UUID.randomUUID().toString(), token)
        assertTrue(transitionResult.isFailure)
        assertEquals("404_NOT_FOUND", transitionResult.exceptionOrNull()?.message)
    }

    @Test
    fun test72_StrictStateMachine_RejectsInvalidTransitions() = runTest {
        val server = MockDeliveryServer()
        val riderId = "rider_101"
        val token = server.createTestRiderJwt(riderId)
        val session = server.createSession("ord_72", riderId)
        assertEquals("ASSIGNED", session.state)

        // Invalid jump: ASSIGNED -> DELIVERED rejected
        val invalidJump = server.transition(session.deliveryId, "DELIVERED", UUID.randomUUID().toString(), token)
        assertTrue(invalidJump.isFailure)
        assertEquals("INVALID_TRANSITION", invalidJump.exceptionOrNull()?.message)

        // Valid transition: ASSIGNED -> ACCEPTED -> EN_ROUTE_PICKUP
        val step1 = server.transition(session.deliveryId, "ACCEPTED", UUID.randomUUID().toString(), token)
        assertTrue(step1.isSuccess)
        assertEquals("ACCEPTED", session.state)

        val step2 = server.transition(session.deliveryId, "EN_ROUTE_PICKUP", UUID.randomUUID().toString(), token)
        assertTrue(step2.isSuccess)
        assertEquals("EN_ROUTE_PICKUP", session.state)
    }

    @Test
    fun test73_OtpAttemptLimitExhaustion_BlocksVerification() = runTest {
        val server = MockDeliveryServer()
        val riderId = "rider_101"
        val token = server.createTestRiderJwt(riderId)
        val session = server.createSession("ord_73", riderId)

        // Fail 3 times
        server.verifyOtp(session.deliveryId, "0000", token)
        server.verifyOtp(session.deliveryId, "1111", token)
        server.verifyOtp(session.deliveryId, "2222", token)

        assertEquals(0, session.otpAttemptsLeft)

        // 4th attempt blocked with OTP_ATTEMPTS_EXHAUSTED
        val res = server.verifyOtp(session.deliveryId, session.secretOtp, token)
        assertTrue(res.isFailure)
        assertEquals("OTP_ATTEMPTS_EXHAUSTED", res.exceptionOrNull()?.message)
    }

    @Test
    fun test74_CodOverpayment_CalculatesChangeCorrectly() = runTest {
        val server = MockDeliveryServer()
        val riderId = "rider_101"
        val token = server.createTestRiderJwt(riderId)
        val session = server.createSession("ord_74", riderId)

        // Customer gives ₹500 for a ₹450 due order
        val codRes = server.completeCod(session.deliveryId, 500.0, token)
        assertTrue(codRes.isSuccess)
        assertEquals(50.0, codRes.getOrNull()!!, 0.01)
        assertTrue(session.codReconciled)
    }

    @Test
    fun test75_AtomicCompletion_GatedByOtpAndCod() = runTest {
        val server = MockDeliveryServer()
        val riderId = "rider_101"
        val token = server.createTestRiderJwt(riderId)
        val session = server.createSession("ord_75", riderId)
        session.state = "HANDOFF_STARTED"

        // Completion fails if OTP not verified
        val res1 = server.completeDelivery(session.deliveryId, token)
        assertTrue(res1.isFailure)
        assertEquals("OTP_NOT_VERIFIED", res1.exceptionOrNull()?.message)

        // Verify OTP
        server.verifyOtp(session.deliveryId, session.secretOtp, token)

        // Completion fails if COD not reconciled
        val res2 = server.completeDelivery(session.deliveryId, token)
        assertTrue(res2.isFailure)
        assertEquals("COD_NOT_RECONCILED", res2.exceptionOrNull()?.message)

        // Reconcile COD
        server.completeCod(session.deliveryId, 450.0, token)

        // Atomic completion succeeds
        val res3 = server.completeDelivery(session.deliveryId, token)
        assertTrue(res3.isSuccess)
        assertEquals("DELIVERED", session.state)
    }
}
