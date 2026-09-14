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
    public let addressId: String?
    public let deliveryAddress: DeliveryAddressPayload
    public let items: [OrderItemPayload]
    public let prescriptionId: String?

    public init(
        idempotencyKey: String = UUID().uuidString,
        paymentMethod: String = "COD",
        addressId: String? = nil,
        deliveryAddress: DeliveryAddressPayload,
        items: [OrderItemPayload],
        prescriptionId: String? = nil
    ) {
        self.idempotencyKey = idempotencyKey
        self.paymentMethod = paymentMethod
        self.addressId = addressId
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

public struct ServerOrderItemResponse: Codable {
    public let sku: String
    public let name: String?
    public let price: Double?
    public let quantity: Int

    public init(sku: String, name: String? = nil, price: Double? = nil, quantity: Int = 1) {
        self.sku = sku
        self.name = name
        self.price = price
        self.quantity = quantity
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
    public let items: [ServerOrderItemResponse]?

    public init(
        id: String,
        orderId: String? = nil,
        customerId: String? = nil,
        status: String = "PLACED",
        orderStatus: String? = nil,
        totalAmount: Double = 0.0,
        deliveryFee: Double? = nil,
        paymentMethod: String? = nil,
        paymentStatus: String? = nil,
        deliveryOtp: String? = nil,
        createdAt: String? = nil,
        items: [ServerOrderItemResponse]? = nil
    ) {
        self.id = id
        self.orderId = orderId ?? id
        self.customerId = customerId
        self.status = status
        self.orderStatus = orderStatus ?? status
        self.totalAmount = totalAmount
        self.deliveryFee = deliveryFee
        self.paymentMethod = paymentMethod
        self.paymentStatus = paymentStatus
        self.deliveryOtp = deliveryOtp
        self.createdAt = createdAt
        self.items = items
    }

    enum CodingKeys: String, CodingKey {
        case id
        case orderId = "order_id"
        case orderIdCamel = "orderId"
        case customerId = "customer_id"
        case customerIdCamel = "customerId"
        case status
        case orderStatus = "order_status"
        case orderStatusCamel = "orderStatus"
        case totalAmount = "total_amount"
        case totalAmountCamel = "totalAmount"
        case deliveryFee = "delivery_fee"
        case deliveryFeeCamel = "deliveryFee"
        case paymentMethod = "payment_method"
        case paymentMethodCamel = "paymentMethod"
        case paymentStatus = "payment_status"
        case paymentStatusCamel = "paymentStatus"
        case deliveryOtp = "delivery_otp"
        case deliveryOtpCamel = "deliveryOtp"
        case createdAt = "created_at"
        case createdAtCamel = "createdAt"
        case items
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let resolvedId = (try? container.decode(String.self, forKey: .id))
            ?? (try? container.decode(String.self, forKey: .orderId))
            ?? (try? container.decode(String.self, forKey: .orderIdCamel))
            ?? UUID().uuidString
        self.id = resolvedId
        self.orderId = (try? container.decode(String.self, forKey: .orderId))
            ?? (try? container.decode(String.self, forKey: .orderIdCamel))
            ?? resolvedId
        self.customerId = (try? container.decode(String.self, forKey: .customerId))
            ?? (try? container.decode(String.self, forKey: .customerIdCamel))
        self.status = (try? container.decode(String.self, forKey: .status))
            ?? (try? container.decode(String.self, forKey: .orderStatus))
            ?? (try? container.decode(String.self, forKey: .orderStatusCamel))
            ?? "PLACED"
        self.orderStatus = self.status
        self.totalAmount = (try? container.decode(Double.self, forKey: .totalAmount))
            ?? (try? container.decode(Double.self, forKey: .totalAmountCamel))
            ?? 0.0
        self.deliveryFee = (try? container.decode(Double.self, forKey: .deliveryFee))
            ?? (try? container.decode(Double.self, forKey: .deliveryFeeCamel))
        self.paymentMethod = (try? container.decode(String.self, forKey: .paymentMethod))
            ?? (try? container.decode(String.self, forKey: .paymentMethodCamel))
        self.paymentStatus = (try? container.decode(String.self, forKey: .paymentStatus))
            ?? (try? container.decode(String.self, forKey: .paymentStatusCamel))
        self.deliveryOtp = (try? container.decode(String.self, forKey: .deliveryOtp))
            ?? (try? container.decode(String.self, forKey: .deliveryOtpCamel))
        self.createdAt = (try? container.decode(String.self, forKey: .createdAt))
            ?? (try? container.decode(String.self, forKey: .createdAtCamel))
        self.items = try? container.decode([ServerOrderItemResponse].self, forKey: .items)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encodeIfPresent(orderId, forKey: .orderId)
        try container.encodeIfPresent(customerId, forKey: .customerId)
        try container.encode(status, forKey: .status)
        try container.encodeIfPresent(orderStatus, forKey: .orderStatus)
        try container.encode(totalAmount, forKey: .totalAmount)
        try container.encodeIfPresent(deliveryFee, forKey: .deliveryFee)
        try container.encodeIfPresent(paymentMethod, forKey: .paymentMethod)
        try container.encodeIfPresent(paymentStatus, forKey: .paymentStatus)
        try container.encodeIfPresent(deliveryOtp, forKey: .deliveryOtp)
        try container.encodeIfPresent(createdAt, forKey: .createdAt)
        try container.encodeIfPresent(items, forKey: .items)
    }
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
        paymentMethod: String = "COD",
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
