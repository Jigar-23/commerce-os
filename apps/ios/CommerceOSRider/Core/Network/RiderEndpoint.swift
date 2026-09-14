import Foundation

public enum RiderEndpoint {
    case acceptOffer(id: String)
    case declineOffer(id: String)
    case arriveMerchant(deliveryId: String)
    case confirmPickup(deliveryId: String)
    case arriveCustomer(deliveryId: String)
    case deliverWithOtp(deliveryId: String)
    case getTripsHistory
    case streamTelemetry
    case toggleShift(online: Bool)
    case getProfile
    case activeSession

    public var path: String {
        switch self {
        case .acceptOffer(let id):
            return "api/v1/delivery/offers/\(id)/accept"
        case .declineOffer(let id):
            return "api/v1/delivery/offers/\(id)/decline"
        case .arriveMerchant(let deliveryId):
            return "api/v1/delivery/\(deliveryId)/arrive-store"
        case .confirmPickup(let deliveryId):
            return "api/v1/delivery/\(deliveryId)/pickup"
        case .arriveCustomer(let deliveryId):
            return "api/v1/delivery/\(deliveryId)/arrive-customer"
        case .deliverWithOtp(let deliveryId):
            return "api/v1/delivery/\(deliveryId)/complete"
        case .getTripsHistory:
            return "api/v1/rider/trips"
        case .streamTelemetry:
            return "api/v1/rider/telemetry"
        case .toggleShift:
            return "api/v1/rider/shift"
        case .getProfile:
            return "api/v1/rider/profile"
        case .activeSession:
            return "api/v1/rider/active-session"
        }
    }

    public var method: String {
        switch self {
        case .acceptOffer, .declineOffer, .arriveMerchant, .confirmPickup, .arriveCustomer, .deliverWithOtp, .streamTelemetry, .toggleShift:
            return "POST"
        case .getTripsHistory, .getProfile, .activeSession:
            return "GET"
        }
    }
}
