import Foundation

public struct OrderItemPayload: Codable {
    public let sku: String
    public let quantity: Int

    public init(sku: String, quantity: Int) {
        self.sku = sku
        self.quantity = quantity
    }
}

public struct PlaceOrderRequest: Codable {
    public let idempotencyKey: String
    public let paymentMethod: String
    public let deliveryAddress: DeliveryAddressPayload
    public let items: [OrderItemPayload]
    public let prescriptionId: String?

    public init(
        idempotencyKey: String = UUID().uuidString,
        paymentMethod: String = "UPI_INSTANT",
        deliveryAddress: DeliveryAddressPayload,
        items: [OrderItemPayload],
        prescriptionId: String? = nil
    ) {
        self.idempotencyKey = idempotencyKey
        self.paymentMethod = paymentMethod
        self.deliveryAddress = deliveryAddress
        self.items = items
        self.prescriptionId = prescriptionId
    }
}

public struct DeliveryAddressPayload: Codable {
    public let addressLine: String
    public let city: String
    public let postalCode: String
    public let latitude: Double
    public let longitude: Double

    public init(addressLine: String, city: String, postalCode: String, latitude: Double, longitude: Double) {
        self.addressLine = addressLine
        self.city = city
        self.postalCode = postalCode
        self.latitude = latitude
        self.longitude = longitude
    }
}

public struct ServerOrderResponse: Identifiable, Codable {
    public let id: String
    public let orderId: String?
    public let customerId: String?
    public let status: String
    public let orderStatus: String?
    public let totalAmount: Double
    public let deliveryFee: Double?
    public let paymentMethod: String?
    public let paymentStatus: String?
    public let deliveryOtp: String?
    public let createdAt: String?
}

public class OrderRepository: ObservableObject {
    public static let shared = OrderRepository()
    private let apiClient: APIClient

    @Published public var customerOrders: [ServerOrderResponse] = []
    @Published public var isPlacingOrder: Bool = false
    @Published public var orderError: String? = nil
    @Published public var lastPlacedOrder: ServerOrderResponse? = nil

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    public func placeOrder(
        items: [(product: ServerProduct, quantity: Int)],
        address: DeliveryAddressPayload,
        paymentMethod: String = "UPI_INSTANT",
        prescriptionId: String? = nil
    ) async throws -> ServerOrderResponse {
        await MainActor.run {
            self.isPlacingOrder = true
            self.orderError = nil
        }

        let itemPayloads = items.map { OrderItemPayload(sku: $0.product.sku, quantity: $0.quantity) }
        let request = PlaceOrderRequest(
            idempotencyKey: "ios_order_\(UUID().uuidString)",
            paymentMethod: paymentMethod,
            deliveryAddress: address,
            items: itemPayloads,
            prescriptionId: prescriptionId
        )

        do {
            let response: ServerOrderResponse = try await apiClient.post(endpoint: "/api/v1/orders", body: request)
            await MainActor.run {
                self.lastPlacedOrder = response
                self.customerOrders.insert(response, at: 0)
                self.isPlacingOrder = false
            }
            return response
        } catch {
            await MainActor.run {
                self.orderError = error.localizedDescription
                self.isPlacingOrder = false
            }
            throw error
        }
    }

    public func fetchCustomerOrders() async {
        do {
            let fetched: [ServerOrderResponse] = try await apiClient.get(endpoint: "/api/v1/orders/customer")
            await MainActor.run {
                self.customerOrders = fetched
            }
        } catch {
            print("[OrderRepository] Failed to fetch customer orders:", error.localizedDescription)
        }
    }
}
