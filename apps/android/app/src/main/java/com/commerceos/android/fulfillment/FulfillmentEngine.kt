package com.commerceos.android.fulfillment

import java.math.BigDecimal

/** Strategy for selecting the optimal fulfillment node for an order. */
enum class NodeSelectionStrategy {
    CLOSEST_DISTANCE,
    FASTEST_SLA,
    MAXIMUM_INVENTORY
}

/**
 * Universal Fulfillment Engine for Commerce OS.
 * Manages fulfillment node selection, serviceability evaluation, inventory reservation,
 * carrier assignment, and handoff proof of delivery verification.
 */
object FulfillmentEngine {

    private val activeNodes = mutableListOf(
        FulfillmentNode("node_01", "Central Quick Hub", "m_01", 19.0760, 72.8777, setOf(FulfillmentMode.QUICK, FulfillmentMode.STANDARD)),
        FulfillmentNode("node_02", "Regional Warehouse East", "m_01", 19.1200, 72.9100, setOf(FulfillmentMode.STANDARD, FulfillmentMode.SCHEDULED)),
        FulfillmentNode("node_03", "Store Outlet South", "m_02", 18.9600, 72.8200, setOf(FulfillmentMode.PICKUP, FulfillmentMode.QUICK))
    )

    private val reservations = mutableMapOf<String, InventoryReservation>()

    fun evaluateServiceability(
        customerLat: Double,
        customerLng: Double,
        mode: FulfillmentMode
    ): List<ServiceabilityCheckResult> {
        return activeNodes
            .filter { it.isActive && it.supportedModes.contains(mode) }
            .map { node ->
                val distKm = calculateDistanceKm(node.latitude, node.longitude, customerLat, customerLng)
                val slaMins = when (mode) {
                    FulfillmentMode.QUICK -> (15 + (distKm * 3)).toInt()
                    FulfillmentMode.STANDARD -> 1440 // 24 hrs
                    FulfillmentMode.SCHEDULED -> 2880 // 48 hrs
                    FulfillmentMode.PICKUP -> 30
                    FulfillmentMode.SERVICE_BOOKING -> 60
                }
                val fee = when (mode) {
                    FulfillmentMode.QUICK -> BigDecimal.valueOf(2.0)
                    FulfillmentMode.STANDARD -> BigDecimal.valueOf(2.0)
                    FulfillmentMode.SCHEDULED -> BigDecimal.valueOf(2.0)
                    FulfillmentMode.PICKUP -> BigDecimal.ZERO
                    FulfillmentMode.SERVICE_BOOKING -> BigDecimal.valueOf(50.0)
                }

                ServiceabilityCheckResult(
                    isEligible = distKm <= 25.0,
                    fulfillmentMode = mode,
                    nodeId = node.nodeId,
                    nodeName = node.name,
                    estimatedSlaMinutes = slaMins,
                    deliveryFee = fee,
                    distanceKm = distKm
                )
            }
            .filter { it.isEligible }
    }

    fun selectOptimalNode(
        results: List<ServiceabilityCheckResult>,
        strategy: NodeSelectionStrategy = NodeSelectionStrategy.FASTEST_SLA
    ): ServiceabilityCheckResult? {
        return when (strategy) {
            NodeSelectionStrategy.FASTEST_SLA -> results.minByOrNull { it.estimatedSlaMinutes }
            NodeSelectionStrategy.CLOSEST_DISTANCE -> results.minByOrNull { it.distanceKm }
            NodeSelectionStrategy.MAXIMUM_INVENTORY -> results.firstOrNull()
        }
    }

    fun reserveInventory(orderId: String, sku: String, quantity: Int, nodeId: String): InventoryReservation {
        val reservationId = "res_${orderId}_$sku"
        val reservation = InventoryReservation(
            reservationId = reservationId,
            orderId = orderId,
            sku = sku,
            quantity = quantity,
            nodeId = nodeId,
            state = ReservationState.RESERVED
        )
        reservations[reservationId] = reservation
        return reservation
    }

    fun updateReservationState(reservationId: String, newState: ReservationState): InventoryReservation? {
        val existing = reservations[reservationId] ?: return null
        val updated = existing.copy(state = newState)
        reservations[reservationId] = updated
        return updated
    }

    fun verifyProofOfDelivery(pod: ProofOfDelivery): Boolean {
        return pod.otpVerified || !pod.photoUrl.isNullOrBlank() || !pod.customerSignatureUrl.isNullOrBlank()
    }

    private fun calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return 6371 * c
    }
}
