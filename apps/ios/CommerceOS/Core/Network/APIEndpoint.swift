import Foundation

public enum APIEndpoint {
    case catalogProducts
    case activeDelivery
    case placeOrder
    case orderHistory
    case deliveryRoute(orderId: String)
    case custom(String)

    public var path: String {
        switch self {
        case .catalogProducts:
            return "/api/v1/catalog/products"
        case .activeDelivery:
            return "/api/v1/orders/active-delivery"
        case .placeOrder:
            return "/api/v1/orders"
        case .orderHistory:
            return "/api/v1/orders/history"
        case .deliveryRoute(let orderId):
            return "/api/v1/delivery/route?order_id=\(orderId)"
        case .custom(let path):
            return path
        }
    }
}
