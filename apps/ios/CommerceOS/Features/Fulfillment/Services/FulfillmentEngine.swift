import Foundation

/** Strategy for selecting the optimal fulfillment node for an order. */
public enum NodeSelectionStrategy: String, CaseIterable, Equatable {
    case closestDistance
    case fastestSla
    case maximumInventory
}

/**
 * 1:1 Parity with Android FulfillmentEngine.kt (109 lines).
 * Universal Fulfillment Engine for Commerce OS.
 * Manages fulfillment node selection, serviceability evaluation, inventory reservation,
 * carrier assignment, and handoff proof of delivery verification.
 */
public final class FulfillmentEngine {
    public static let shared = FulfillmentEngine()

    private var activeNodes: [FulfillmentNode] = [
        FulfillmentNode(nodeId: "node_01", name: "Central Quick Hub", merchantId: "m_01", latitude: 19.0760, longitude: 72.8777, supportedModes: [.quick, .standard]),
        FulfillmentNode(nodeId: "node_02", name: "Regional Warehouse East", merchantId: "m_01", latitude: 19.1200, longitude: 72.9100, supportedModes: [.standard, .scheduled]),
        FulfillmentNode(nodeId: "node_03", name: "Store Outlet South", merchantId: "m_02", latitude: 18.9600, longitude: 72.8200, supportedModes: [.pickup, .quick])
    ]

    private var reservations: [String: InventoryReservation] = [:]

    private init() {}

    public func evaluateServiceability(
        customerLat: Double,
        customerLng: Double,
        mode: FulfillmentMode
    ) -> [ServiceabilityCheckResult] {
        return activeNodes
            .filter { $0.isActive && $0.supportedModes.contains(mode) }
            .map { node in
                let distKm = calculateDistanceKm(lat1: node.latitude, lon1: node.longitude, lat2: customerLat, lon2: customerLng)
                let slaMins: Int
                switch mode {
                case .quick: slaMins = Int(15 + (distKm * 3))
                case .standard: slaMins = 1440 // 24 hrs
                case .scheduled: slaMins = 2880 // 48 hrs
                case .pickup: slaMins = 30
                case .serviceBooking: slaMins = 60
                }

                let fee: Double
                switch mode {
                case .quick, .standard, .scheduled: fee = 2.0
                case .pickup: fee = 0.0
                case .serviceBooking: fee = 50.0
                }

                return ServiceabilityCheckResult(
                    isEligible: distKm <= 25.0,
                    fulfillmentMode: mode,
                    nodeId: node.nodeId,
                    nodeName: node.name,
                    estimatedSlaMinutes: slaMins,
                    deliveryFee: fee,
                    coldChainFee: 0.0,
                    distanceKm: distKm
                )
            }
            .filter { $0.isEligible }
    }

    public func selectOptimalNode(
        results: [ServiceabilityCheckResult],
        strategy: NodeSelectionStrategy = .fastestSla
    ) -> ServiceabilityCheckResult? {
        switch strategy {
        case .fastestSla:
            return results.min(by: { $0.estimatedSlaMinutes < $1.estimatedSlaMinutes })
        case .closestDistance:
            return results.min(by: { $0.distanceKm < $1.distanceKm })
        case .maximumInventory:
            return results.first
        }
    }

    public func reserveInventory(orderId: String, sku: String, quantity: Int, nodeId: String) -> InventoryReservation {
        let resId = "res_\(orderId)_\(sku)"
        let reservation = InventoryReservation(
            reservationId: resId,
            orderId: orderId,
            sku: sku,
            quantity: quantity,
            nodeId: nodeId,
            state: .reserved
        )
        reservations[resId] = reservation
        return reservation
    }

    public func updateReservationState(reservationId: String, newState: ReservationState) -> InventoryReservation? {
        guard var existing = reservations[reservationId] else { return nil }
        existing.state = newState
        reservations[reservationId] = existing
        return existing
    }

    public func verifyProofOfDelivery(pod: ProofOfDelivery) -> Bool {
        return pod.otpVerified || (pod.photoUrl != nil && !pod.photoUrl!.isEmpty) || (pod.customerSignatureUrl != nil && !pod.customerSignatureUrl!.isEmpty)
    }

    private func calculateDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double) -> Double {
        let r = 6371.0 // Earth radius in km
        let dLat = (lat2 - lat1) * .pi / 180.0
        let dLon = (lon2 - lon1) * .pi / 180.0
        let a = sin(dLat / 2) * sin(dLat / 2) +
                cos(lat1 * .pi / 180.0) * cos(lat2 * .pi / 180.0) *
                sin(dLon / 2) * sin(dLon / 2)
        let c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }
}
