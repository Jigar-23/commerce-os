import Foundation

public enum RiderEndpoint {
    case sendOtp(phone: String)
    case verifyOtp(challengeId: String, phone: String, otp: String, name: String, vehicle: String)
    case acceptOffer(id: String)
    case declineOffer(id: String)
    case arriveMerchant(deliveryId: String)
    case confirmPickup(deliveryId: String)
    case arriveCustomer(deliveryId: String)
    case deliverWithOtp(deliveryId: String)
    case getTripsHistory
    case streamTelemetry
    case streamDeliveryTelemetry(deliveryId: String)
    case updatePresence
    case toggleShift(online: Bool)
    case registerDeviceToken
    case getProfile
    case activeSession
    case getActiveOffers

    public var path: String {
        switch self {
        case .sendOtp:
            return "api/v1/auth/rider/otp/send"
        case .verifyOtp:
            return "api/v1/auth/rider/otp/verify"
        case .acceptOffer(let id):
            return "api/v1/delivery/offers/\(id)/accept"
        case .declineOffer(let id):
            return "api/v1/delivery/offers/\(id)/decline"
        case .arriveMerchant(let deliveryId):
            return "api/v1/delivery/session/\(deliveryId)/arrive-merchant"
        case .confirmPickup(let deliveryId):
            return "api/v1/delivery/session/\(deliveryId)/pickup"
        case .arriveCustomer(let deliveryId):
            return "api/v1/delivery/session/\(deliveryId)/arrive-customer"
        case .deliverWithOtp(let deliveryId):
            return "api/v1/delivery/session/\(deliveryId)/complete"
        case .getTripsHistory:
            return "api/v1/delivery/rider/trips"
        case .streamTelemetry:
            return "api/v1/delivery/rider/telemetry"
        case .streamDeliveryTelemetry(let deliveryId):
            return "api/v1/delivery/\(deliveryId)/telemetry"
        case .updatePresence:
            return "api/v1/delivery/rider/presence"
        case .toggleShift:
            return "api/v1/delivery/rider/shift-status"
        case .registerDeviceToken:
            return "api/v1/delivery/rider/device-token"
        case .getProfile:
            return "api/v1/delivery/rider/profile"
        case .activeSession:
            return "api/v1/delivery/rider/active-session"
        case .getActiveOffers:
            return "api/v1/delivery/offers/active"
        }
    }

    public var method: String {
        switch self {
        case .sendOtp, .verifyOtp, .acceptOffer, .declineOffer, .arriveMerchant, .confirmPickup, .arriveCustomer, .deliverWithOtp, .streamTelemetry, .streamDeliveryTelemetry, .updatePresence, .toggleShift, .registerDeviceToken:
            return "POST"
        case .getTripsHistory, .getProfile, .activeSession, .getActiveOffers:
            return "GET"
        }
    }
}
