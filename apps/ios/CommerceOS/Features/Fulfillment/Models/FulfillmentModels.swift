import Foundation

/**
 * 1:1 Parity with Android FulfillmentModels.kt (71 lines).
 * Canonical fulfillment modes, inventory reservation states, nodes, and proofs of delivery.
 */
public enum FulfillmentStatus: String, Codable, CaseIterable {
    case unknown = "UNKNOWN"
    case checking = "CHECKING"
    case serviceable = "SERVICEABLE"
    case partial = "PARTIAL"
    case unserviceable = "UNSERVICEABLE"
    case error = "ERROR"
}

public enum VerticalOperationalStatus: String, Codable, CaseIterable {
    case unknown = "UNKNOWN"
    case available = "AVAILABLE"
    case comingSoon = "COMING_SOON"
    case outOfZone = "OUT_OF_ZONE"
    case temporarilyUnavailable = "TEMPORARILY_UNAVAILABLE"
    case degraded = "DEGRADED"
}

public struct VerticalAvailability: Codable, Equatable {
    public let verticalId: String
    public let addressId: String
    public let status: VerticalOperationalStatus
    public let etaLabel: String?
    public let fulfillmentMode: String

    public var isServiceable: Bool {
        status == .available || status == .degraded
    }

    public init(verticalId: String, addressId: String, status: VerticalOperationalStatus, etaLabel: String? = nil, fulfillmentMode: String = "standard") {
        self.verticalId = verticalId
        self.addressId = addressId
        self.status = status
        self.etaLabel = etaLabel
        self.fulfillmentMode = fulfillmentMode
    }
}

public struct FulfillmentContext: Codable, Equatable {
    public let addressId: String?
    public let status: FulfillmentStatus
    public let etaLabel: String?
    public let verticalFulfillments: [String: VerticalAvailability]
    public let generatedAt: Int64
    public let expiresAt: Int64
    public let requestId: String

    public init(
        addressId: String? = nil,
        status: FulfillmentStatus = .unknown,
        etaLabel: String? = nil,
        verticalFulfillments: [String: VerticalAvailability] = [:],
        generatedAt: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        expiresAt: Int64 = Int64((Date().timeIntervalSince1970 + 15 * 60) * 1000),
        requestId: String = UUID().uuidString
    ) {
        self.addressId = addressId
        self.status = status
        self.etaLabel = etaLabel
        self.verticalFulfillments = verticalFulfillments
        self.generatedAt = generatedAt
        self.expiresAt = expiresAt
        self.requestId = requestId
    }
}

public enum FulfillmentMode: String, Codable, CaseIterable, Equatable {
    case quick = "QUICK"
    case standard = "STANDARD"
    case scheduled = "SCHEDULED"
    case pickup = "PICKUP"
    case serviceBooking = "SERVICE_BOOKING"
}

public enum ReservationState: String, Codable, CaseIterable, Equatable {
    case reserved = "RESERVED"
    case allocated = "ALLOCATED"
    case picked = "PICKED"
    case packed = "PACKED"
    case handedOff = "HANDED_OFF"
    case delivered = "DELIVERED"
    case cancelled = "CANCELLED"
    case released = "RELEASED"
}

public struct FulfillmentNode: Codable, Identifiable, Equatable {
    public var id: String { nodeId }
    public let nodeId: String
    public let name: String
    public let merchantId: String
    public let latitude: Double
    public let longitude: Double
    public let supportedModes: Set<FulfillmentMode>
    public let isActive: Bool

    public init(
        nodeId: String,
        name: String,
        merchantId: String,
        latitude: Double,
        longitude: Double,
        supportedModes: Set<FulfillmentMode> = [.standard, .quick],
        isActive: Bool = true
    ) {
        self.nodeId = nodeId
        self.name = name
        self.merchantId = merchantId
        self.latitude = latitude
        self.longitude = longitude
        self.supportedModes = supportedModes
        self.isActive = isActive
    }
}

public struct InventoryReservation: Codable, Identifiable, Equatable {
    public var id: String { reservationId }
    public let reservationId: String
    public let orderId: String
    public let sku: String
    public let quantity: Int
    public let nodeId: String
    public var state: ReservationState
    public let createdAtTimestamp: Int64

    public init(
        reservationId: String,
        orderId: String,
        sku: String,
        quantity: Int,
        nodeId: String,
        state: ReservationState = .reserved,
        createdAtTimestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.reservationId = reservationId
        self.orderId = orderId
        self.sku = sku
        self.quantity = quantity
        self.nodeId = nodeId
        self.state = state
        self.createdAtTimestamp = createdAtTimestamp
    }
}

public struct ServiceabilityCheckResult: Codable, Identifiable, Equatable {
    public var id: String { "\(nodeId)_\(fulfillmentMode.rawValue)" }
    public let isEligible: Bool
    public let fulfillmentMode: FulfillmentMode
    public let nodeId: String
    public let nodeName: String
    public let estimatedSlaMinutes: Int
    public let deliveryFee: Double
    public let coldChainFee: Double
    public let distanceKm: Double

    public init(
        isEligible: Bool,
        fulfillmentMode: FulfillmentMode,
        nodeId: String,
        nodeName: String,
        estimatedSlaMinutes: Int,
        deliveryFee: Double,
        coldChainFee: Double = 0.0,
        distanceKm: Double = 0.0
    ) {
        self.isEligible = isEligible
        self.fulfillmentMode = fulfillmentMode
        self.nodeId = nodeId
        self.nodeName = nodeName
        self.estimatedSlaMinutes = estimatedSlaMinutes
        self.deliveryFee = deliveryFee
        self.coldChainFee = coldChainFee
        self.distanceKm = distanceKm
    }
}

public struct ProofOfDelivery: Codable, Equatable {
    public let orderId: String
    public let handoffTimestamp: Int64
    public let otpVerified: Bool
    public let photoUrl: String?
    public let customerSignatureUrl: String?
    public let gpsLatitude: Double?
    public let gpsLongitude: Double?
    public let recipientName: String?
    public let collectorRiderId: String?

    public init(
        orderId: String,
        handooffTimestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        otpVerified: Bool = false,
        photoUrl: String? = nil,
        customerSignatureUrl: String? = nil,
        gpsLatitude: Double? = nil,
        gpsLongitude: Double? = nil,
        recipientName: String? = nil,
        collectorRiderId: String? = nil
    ) {
        self.orderId = orderId
        self.handoffTimestamp = handooffTimestamp
        self.otpVerified = otpVerified
        self.photoUrl = photoUrl
        self.customerSignatureUrl = customerSignatureUrl
        self.gpsLatitude = gpsLatitude
        self.gpsLongitude = gpsLongitude
        self.recipientName = recipientName
        self.collectorRiderId = collectorRiderId
    }
}
