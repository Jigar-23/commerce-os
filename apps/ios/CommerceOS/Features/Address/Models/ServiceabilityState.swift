import Foundation

/**
 * 1:1 Parity with Android ServiceabilityState in CheckoutViewModel.kt
 * Represents the serviceability verification lifecycle for a selected delivery address.
 */
public enum ServiceabilityState: Equatable {
    case idle
    case checking(addressId: String)
    case success(addressId: String, response: ServiceabilityResponse)
    case partial(addressId: String, response: ServiceabilityResponse)
    case unavailable(addressId: String)
    case error(addressId: String?, message: String)

    public var targetAddressId: String? {
        switch self {
        case .idle:
            return nil
        case .checking(let id), .success(let id, _), .partial(let id, _), .unavailable(let id):
            return id
        case .error(let id, _):
            return id
        }
    }

    public var isServiceable: Bool {
        switch self {
        case .success, .partial:
            return true
        default:
            return false
        }
    }
}
