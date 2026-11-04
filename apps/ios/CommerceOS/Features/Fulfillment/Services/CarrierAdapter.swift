import Foundation

/**
 * 1:1 Parity with Android CarrierAdapter.kt (69 lines).
 * Universal Carrier Adapter Abstraction Interface & normalized data contracts.
 */
public enum CarrierTrackingStatus: String, Codable, CaseIterable, Equatable {
    case booked = "BOOKED"
    case dispatched = "DISPATCHED"
    case inTransit = "IN_TRANSIT"
    case outForDelivery = "OUT_FOR_DELIVERY"
    case delivered = "DELIVERED"
    case exception = "EXCEPTION"
    case cancelled = "CANCELLED"
}

public struct ShipmentCheckpoint: Codable, Identifiable, Equatable {
    public var id: String { checkpointId }
    public let checkpointId: String
    public let facilityName: String
    public let locationName: String
    public let timestamp: Int64
    public let status: CarrierTrackingStatus
    public let description: String

    public init(
        checkpointId: String,
        facilityName: String,
        locationName: String,
        timestamp: Int64,
        status: CarrierTrackingStatus,
        description: String
    ) {
        self.checkpointId = checkpointId
        self.facilityName = facilityName
        self.locationName = locationName
        self.timestamp = timestamp
        self.status = status
        self.description = description
    }
}

public struct CreateShipmentRequest: Codable, Equatable {
    public let orderId: String
    public let originNodeId: String
    public let recipientName: String
    public let recipientPhone: String
    public let recipientAddress: String
    public let recipientPincode: String
    public let weightKg: Double
    public let isCod: Bool
    public let codAmount: Double

    public init(
        orderId: String,
        originNodeId: String,
        recipientName: String,
        recipientPhone: String,
        recipientAddress: String,
        recipientPincode: String,
        weightKg: Double,
        isCod: Bool = false,
        codAmount: Double = 0.0
    ) {
        self.orderId = orderId
        self.originNodeId = originNodeId
        self.recipientName = recipientName
        self.recipientPhone = recipientPhone
        self.recipientAddress = recipientAddress
        self.recipientPincode = recipientPincode
        self.weightKg = weightKg
        self.isCod = isCod
        self.codAmount = codAmount
    }
}

public struct ShipmentResponse: Codable, Identifiable, Equatable {
    public var id: String { shipmentId }
    public let shipmentId: String
    public let consignmentNumber: String
    public let carrierName: String
    public let labelUrl: String?
    public let expectedDeliveryTimestamp: Int64

    public init(
        shipmentId: String,
        consignmentNumber: String,
        carrierName: String,
        labelUrl: String? = nil,
        expectedDeliveryTimestamp: Int64
    ) {
        self.shipmentId = shipmentId
        self.consignmentNumber = consignmentNumber
        self.carrierName = carrierName
        self.labelUrl = labelUrl
        self.expectedDeliveryTimestamp = expectedDeliveryTimestamp
    }
}

public struct CarrierWebhookEvent: Codable, Identifiable, Equatable {
    public var id: String { eventId }
    public let eventId: String
    public let carrierName: String
    public let consignmentNumber: String
    public let status: CarrierTrackingStatus
    public let location: String
    public let timestamp: Int64
    public let rawPayloadJson: String

    public init(
        eventId: String,
        carrierName: String,
        consignmentNumber: String,
        status: CarrierTrackingStatus,
        location: String,
        timestamp: Int64,
        rawPayloadJson: String
    ) {
        self.eventId = eventId
        self.carrierName = carrierName
        self.consignmentNumber = consignmentNumber
        self.status = status
        self.location = location
        self.timestamp = timestamp
        self.rawPayloadJson = rawPayloadJson
    }
}

public protocol CarrierAdapter {
    var carrierId: String { get }
    var carrierName: String { get }

    func createShipment(request: CreateShipmentRequest) async throws -> ShipmentResponse
    func getTrackingStatus(consignmentNumber: String) async throws -> [ShipmentCheckpoint]
    func cancelShipment(consignmentNumber: String) async throws -> Bool
}
