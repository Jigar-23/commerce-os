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

    enum CodingKeys: String, CodingKey {
        case idempotencyKey = "idempotency_key"
        case idempotencyKeyCamel = "idempotencyKey"
        case paymentMethod = "payment_method"
        case paymentMethodCamel = "paymentMethod"
        case addressId = "address_id"
        case addressIdCamel = "addressId"
        case deliveryAddress = "delivery_address"
        case deliveryAddressCamel = "deliveryAddress"
        case items
        case prescriptionId = "prescription_id"
        case prescriptionIdCamel = "prescriptionId"
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.idempotencyKey = (try? container.decode(String.self, forKey: .idempotencyKey))
            ?? (try? container.decode(String.self, forKey: .idempotencyKeyCamel))
            ?? UUID().uuidString
        self.paymentMethod = (try? container.decode(String.self, forKey: .paymentMethod))
            ?? (try? container.decode(String.self, forKey: .paymentMethodCamel))
            ?? "COD"
        self.addressId = (try? container.decode(String.self, forKey: .addressId))
            ?? (try? container.decode(String.self, forKey: .addressIdCamel))
        self.deliveryAddress = (try? container.decode(DeliveryAddressPayload.self, forKey: .deliveryAddress))
            ?? (try? container.decode(DeliveryAddressPayload.self, forKey: .deliveryAddressCamel))
            ?? DeliveryAddressPayload(addressLine: "Rewari Hub", city: "Rewari", postalCode: "123401", latitude: 28.202224, longitude: 76.615418)
        self.items = (try? container.decode([OrderItemPayload].self, forKey: .items)) ?? []
        self.prescriptionId = (try? container.decode(String.self, forKey: .prescriptionId))
            ?? (try? container.decode(String.self, forKey: .prescriptionIdCamel))
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(idempotencyKey, forKey: .idempotencyKey)
        try container.encode(idempotencyKey, forKey: .idempotencyKeyCamel)
        try container.encode(paymentMethod, forKey: .paymentMethod)
        try container.encode(paymentMethod, forKey: .paymentMethodCamel)
        try container.encodeIfPresent(addressId, forKey: .addressId)
        try container.encodeIfPresent(addressId, forKey: .addressIdCamel)
        try container.encode(deliveryAddress, forKey: .deliveryAddress)
        try container.encode(deliveryAddress, forKey: .deliveryAddressCamel)
        try container.encode(items, forKey: .items)
        try container.encodeIfPresent(prescriptionId, forKey: .prescriptionId)
        try container.encodeIfPresent(prescriptionId, forKey: .prescriptionIdCamel)
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

    enum CodingKeys: String, CodingKey {
        case addressLine = "address_line"
        case addressLineCamel = "addressLine"
        case city
        case postalCode = "postal_code"
        case postalCodeCamel = "postalCode"
        case latitude
        case longitude
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.addressLine = (try? container.decode(String.self, forKey: .addressLine))
            ?? (try? container.decode(String.self, forKey: .addressLineCamel))
            ?? ""
        self.city = (try? container.decode(String.self, forKey: .city)) ?? "Rewari"
        self.postalCode = (try? container.decode(String.self, forKey: .postalCode))
            ?? (try? container.decode(String.self, forKey: .postalCodeCamel))
            ?? "123401"
        self.latitude = (try? container.decode(Double.self, forKey: .latitude)) ?? 28.202224
        self.longitude = (try? container.decode(Double.self, forKey: .longitude)) ?? 76.615418
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(addressLine, forKey: .addressLine)
        try container.encode(addressLine, forKey: .addressLineCamel)
        try container.encode(city, forKey: .city)
        try container.encode(postalCode, forKey: .postalCode)
        try container.encode(postalCode, forKey: .postalCodeCamel)
        try container.encode(latitude, forKey: .latitude)
        try container.encode(longitude, forKey: .longitude)
    }
}

public struct ServerOrderItemResponse: Codable {
    public let sku: String
    public let name: String?
    public let price: Double?
    public let unitPrice: Double?
    public let quantity: Int

    public var effectivePrice: Double {
        return unitPrice ?? price ?? 0.0
    }

    public init(sku: String, name: String? = nil, price: Double? = nil, unitPrice: Double? = nil, quantity: Int = 1) {
        self.sku = sku
        self.name = name
        self.price = price ?? unitPrice
        self.unitPrice = unitPrice ?? price
        self.quantity = quantity
    }

    enum CodingKeys: String, CodingKey {
        case sku
        case name
        case price
        case unitPrice
        case unit_price
        case quantity
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.sku = (try? container.decode(String.self, forKey: .sku)) ?? ""
        self.name = try? container.decode(String.self, forKey: .name)

        var resolvedPrice: Double? = try? container.decode(Double.self, forKey: .price)
        if resolvedPrice == nil, let s = try? container.decode(String.self, forKey: .price) {
            resolvedPrice = Double(s)
        }

        var resolvedUnitPrice: Double? = (try? container.decode(Double.self, forKey: .unitPrice))
            ?? (try? container.decode(Double.self, forKey: .unit_price))
        if resolvedUnitPrice == nil, let s = (try? container.decode(String.self, forKey: .unitPrice)) ?? (try? container.decode(String.self, forKey: .unit_price)) {
            resolvedUnitPrice = Double(s)
        }

        self.price = resolvedPrice ?? resolvedUnitPrice
        self.unitPrice = resolvedUnitPrice ?? resolvedPrice

        var resolvedQuantity: Int? = try? container.decode(Int.self, forKey: .quantity)
        if resolvedQuantity == nil, let s = try? container.decode(String.self, forKey: .quantity) {
            resolvedQuantity = Int(s)
        } else if resolvedQuantity == nil, let d = try? container.decode(Double.self, forKey: .quantity) {
            resolvedQuantity = Int(d)
        }
        self.quantity = resolvedQuantity ?? 1
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(sku, forKey: .sku)
        try container.encodeIfPresent(name, forKey: .name)
        try container.encodeIfPresent(price, forKey: .price)
        try container.encodeIfPresent(unitPrice, forKey: .unitPrice)
        try container.encode(quantity, forKey: .quantity)
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
    public let deliverySlaMins: Int?
    public let deliveryAddress: DeliveryAddressPayload?
    public let items: [ServerOrderItemResponse]?
    public let riderName: String?
    public let riderPhone: String?
    public let riderVehicle: String?
    public let storeName: String?

    public var effectiveDeliveryPin: String? {
        guard let pin = deliveryOtp?.trimmingCharacters(in: .whitespacesAndNewlines), !pin.isEmpty else {
            return nil
        }
        return pin
    }

    public var effectiveSlaMins: Int {
        return max(deliverySlaMins ?? 10, 8)
    }

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
        deliverySlaMins: Int? = 10,
        deliveryAddress: DeliveryAddressPayload? = nil,
        items: [ServerOrderItemResponse]? = nil,
        riderName: String? = nil,
        riderPhone: String? = nil,
        riderVehicle: String? = nil,
        storeName: String? = nil
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
        self.deliverySlaMins = max(deliverySlaMins ?? 10, 8)
        self.deliveryAddress = deliveryAddress
        self.items = items
        self.riderName = riderName
        self.riderPhone = riderPhone
        self.riderVehicle = riderVehicle
        self.storeName = storeName
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
        case deliverySlaMins = "delivery_sla_mins"
        case deliverySlaMinsCamel = "deliverySlaMins"
        case deliveryAddress = "delivery_address"
        case deliveryAddressCamel = "deliveryAddress"
        case items
        case riderName = "rider_name"
        case riderNameCamel = "riderName"
        case riderPhone = "rider_phone"
        case riderPhoneCamel = "riderPhone"
        case riderVehicle = "rider_vehicle"
        case riderVehicleCamel = "riderVehicle"
        case storeName = "store_name"
        case storeNameCamel = "storeName"
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

        var resolvedTotal: Double? = (try? container.decode(Double.self, forKey: .totalAmount))
            ?? (try? container.decode(Double.self, forKey: .totalAmountCamel))
        if resolvedTotal == nil {
            if let s = (try? container.decode(String.self, forKey: .totalAmount)) ?? (try? container.decode(String.self, forKey: .totalAmountCamel)) {
                resolvedTotal = Double(s)
            }
        }
        self.totalAmount = resolvedTotal ?? 0.0

        var resolvedFee: Double? = (try? container.decode(Double.self, forKey: .deliveryFee))
            ?? (try? container.decode(Double.self, forKey: .deliveryFeeCamel))
        if resolvedFee == nil {
            if let s = (try? container.decode(String.self, forKey: .deliveryFee)) ?? (try? container.decode(String.self, forKey: .deliveryFeeCamel)) {
                resolvedFee = Double(s)
            }
        }
        self.deliveryFee = resolvedFee

        self.paymentMethod = (try? container.decode(String.self, forKey: .paymentMethod))
            ?? (try? container.decode(String.self, forKey: .paymentMethodCamel))
        self.paymentStatus = (try? container.decode(String.self, forKey: .paymentStatus))
            ?? (try? container.decode(String.self, forKey: .paymentStatusCamel))
        self.deliveryOtp = (try? container.decode(String.self, forKey: .deliveryOtp))
            ?? (try? container.decode(String.self, forKey: .deliveryOtpCamel))
        self.createdAt = (try? container.decode(String.self, forKey: .createdAt))
            ?? (try? container.decode(String.self, forKey: .createdAtCamel))

        var resolvedSla: Int? = (try? container.decode(Int.self, forKey: .deliverySlaMins))
            ?? (try? container.decode(Int.self, forKey: .deliverySlaMinsCamel))
        if resolvedSla == nil {
            if let s = (try? container.decode(String.self, forKey: .deliverySlaMins)) ?? (try? container.decode(String.self, forKey: .deliverySlaMinsCamel)) {
                resolvedSla = Int(s)
            } else if let d = (try? container.decode(Double.self, forKey: .deliverySlaMins)) ?? (try? container.decode(Double.self, forKey: .deliverySlaMinsCamel)) {
                resolvedSla = Int(d)
            }
        }
        self.deliverySlaMins = max(resolvedSla ?? 10, 8)

        // Delivery address: Object or stringified JSON
        if let addr = (try? container.decode(DeliveryAddressPayload.self, forKey: .deliveryAddress))
            ?? (try? container.decode(DeliveryAddressPayload.self, forKey: .deliveryAddressCamel)) {
            self.deliveryAddress = addr
        } else if let addrStr = (try? container.decode(String.self, forKey: .deliveryAddress))
            ?? (try? container.decode(String.self, forKey: .deliveryAddressCamel)),
            let data = addrStr.data(using: .utf8),
            let decoded = try? JSONDecoder().decode(DeliveryAddressPayload.self, from: data) {
            self.deliveryAddress = decoded
        } else {
            self.deliveryAddress = nil
        }

        // Items: Array or stringified JSON
        if let itemsArray = try? container.decode([ServerOrderItemResponse].self, forKey: .items) {
            self.items = itemsArray
        } else if let itemsStr = try? container.decode(String.self, forKey: .items),
                  let data = itemsStr.data(using: .utf8),
                  let decoded = try? JSONDecoder().decode([ServerOrderItemResponse].self, from: data) {
            self.items = decoded
        } else {
            self.items = nil
        }

        self.riderName = (try? container.decode(String.self, forKey: .riderName))
            ?? (try? container.decode(String.self, forKey: .riderNameCamel))
        self.riderPhone = (try? container.decode(String.self, forKey: .riderPhone))
            ?? (try? container.decode(String.self, forKey: .riderPhoneCamel))
        self.riderVehicle = (try? container.decode(String.self, forKey: .riderVehicle))
            ?? (try? container.decode(String.self, forKey: .riderVehicleCamel))
        self.storeName = (try? container.decode(String.self, forKey: .storeName))
            ?? (try? container.decode(String.self, forKey: .storeNameCamel))
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
        try container.encodeIfPresent(deliverySlaMins, forKey: .deliverySlaMins)
        try container.encodeIfPresent(deliveryAddress, forKey: .deliveryAddress)
        try container.encodeIfPresent(items, forKey: .items)
        try container.encodeIfPresent(riderName, forKey: .riderName)
        try container.encodeIfPresent(riderPhone, forKey: .riderPhone)
        try container.encodeIfPresent(riderVehicle, forKey: .riderVehicle)
        try container.encodeIfPresent(storeName, forKey: .storeName)
    }
}

public class OrderRepository: ObservableObject {
    public static let shared = OrderRepository()
    private let apiClient: APIClient

    @Published public var customerOrders: [ServerOrderResponse] = []
    @Published public var isPlacingOrder: Bool = false
    @Published public var isLoadingOrders: Bool = false
    @Published public var orderError: String? = nil
    @Published public var lastPlacedOrder: ServerOrderResponse? = nil

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    public func placeOrder(
        items: [(product: ServerProduct, quantity: Int)],
        address: DeliveryAddressPayload,
        addressId: String? = nil,
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
            addressId: addressId,
            deliveryAddress: address,
            items: itemPayloads,
            prescriptionId: prescriptionId
        )

        do {
            let response: ServerOrderResponse = try await apiClient.post(endpoint: "/api/v1/orders", body: request)
            await MainActor.run {
                self.lastPlacedOrder = response
                if !self.customerOrders.contains(where: { $0.id == response.id }) {
                    self.customerOrders.insert(response, at: 0)
                }
                self.isPlacingOrder = false
            }
            Task {
                await self.fetchCustomerOrders()
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

    public func fetchCustomerOrders(customerId: String? = nil) async {
        var targetId = customerId
            ?? apiClient.currentCustomerId
            ?? UserDefaults.standard.string(forKey: "customer_id")
            ?? KeychainHelper.shared.get(key: "customer_id")

        if targetId == nil || targetId?.isEmpty == true {
            if let token = apiClient.authToken, let sub = APIClient.extractSubFromJwt(token) {
                targetId = sub
                await MainActor.run {
                    self.apiClient.currentCustomerId = sub
                }
            }
        }

        guard let resolvedTargetId = targetId, !resolvedTargetId.isEmpty else {
            print("[OrderRepository] No customer ID or authentication session found; skipping orders fetch.")
            await MainActor.run {
                self.isLoadingOrders = false
            }
            return
        }

        await MainActor.run {
            self.isLoadingOrders = true
        }

        do {
            let endpoint = "/api/v1/orders/customer/\(resolvedTargetId)"
            struct OrdersWrapper: Decodable {
                let ok: Bool?
                let orders: [ServerOrderResponse]?
            }
            if let fetched: [ServerOrderResponse] = try? await apiClient.get(endpoint: endpoint) {
                await MainActor.run {
                    self.customerOrders = fetched
                    self.isLoadingOrders = false
                }
            } else if let wrapped: OrdersWrapper = try? await apiClient.get(endpoint: endpoint), let list = wrapped.orders {
                await MainActor.run {
                    self.customerOrders = list
                    self.isLoadingOrders = false
                }
            } else {
                let direct: [ServerOrderResponse] = try await apiClient.get(endpoint: endpoint)
                await MainActor.run {
                    self.customerOrders = direct
                    self.isLoadingOrders = false
                }
            }
        } catch {
            print("[OrderRepository] Failed to fetch customer orders for \(resolvedTargetId):", error.localizedDescription)
            await MainActor.run {
                self.isLoadingOrders = false
            }
        }
    }

    public struct CancelOrderResponse: Decodable {
        public let ok: Bool?
        public let error: String?
        public let message: String?
        public let status: String?
        public let orderStatus: String?
        public let id: String?
    }

    public func cancelOrder(orderId: String, reason: String = "USER_REQUESTED_CANCELLATION") async throws {
        struct CancelRequest: Codable {
            let reason: String
        }
        let res: CancelOrderResponse = try await apiClient.post(
            endpoint: "/api/v1/orders/\(orderId)/cancel",
            body: CancelRequest(reason: reason)
        )
        if let ok = res.ok, !ok {
            let msg = res.message ?? res.error ?? "Order cancellation failed."
            throw APIError.serverError(400, msg)
        }
        
        await MainActor.run {
            if let idx = self.customerOrders.firstIndex(where: { $0.id == orderId || $0.orderId == orderId }) {
                let current = self.customerOrders[idx]
                let updated = ServerOrderResponse(
                    id: current.id,
                    orderId: current.orderId,
                    customerId: current.customerId,
                    status: "CANCELLED",
                    orderStatus: "CANCELLED",
                    totalAmount: current.totalAmount,
                    deliveryFee: current.deliveryFee,
                    paymentMethod: current.paymentMethod,
                    paymentStatus: current.paymentStatus,
                    deliveryOtp: current.deliveryOtp,
                    createdAt: current.createdAt,
                    deliverySlaMins: current.deliverySlaMins,
                    deliveryAddress: current.deliveryAddress,
                    items: current.items,
                    riderName: current.riderName,
                    riderPhone: current.riderPhone,
                    riderVehicle: current.riderVehicle,
                    storeName: current.storeName
                )
                self.customerOrders[idx] = updated
            }
            if self.lastPlacedOrder?.id == orderId || self.lastPlacedOrder?.orderId == orderId {
                self.lastPlacedOrder = nil
            }
        }
        await fetchCustomerOrders()
    }

    public func getOrderById(orderId: String) async throws -> ServerOrderResponse {
        return try await apiClient.get(endpoint: "/api/v1/orders/\(orderId)")
    }

    public func fetchOrderDetail(orderId: String, forceRefresh: Bool = true) async -> ServerOrderResponse? {
        if !forceRefresh, let existing = customerOrders.first(where: { $0.id == orderId || $0.orderId == orderId }) {
            return existing
        }
        struct SingleOrderWrapper: Decodable {
            let ok: Bool?
            let order: ServerOrderResponse?
        }
        if let direct: ServerOrderResponse = try? await apiClient.get(endpoint: "/api/v1/orders/\(orderId)") {
            await MainActor.run {
                if let idx = self.customerOrders.firstIndex(where: { $0.id == direct.id || $0.orderId == direct.id }) {
                    self.customerOrders[idx] = direct
                } else {
                    self.customerOrders.insert(direct, at: 0)
                }
            }
            return direct
        }
        if let wrapped: SingleOrderWrapper = try? await apiClient.get(endpoint: "/api/v1/orders/\(orderId)"), let ord = wrapped.order {
            await MainActor.run {
                if let idx = self.customerOrders.firstIndex(where: { $0.id == ord.id || $0.orderId == ord.id }) {
                    self.customerOrders[idx] = ord
                } else {
                    self.customerOrders.insert(ord, at: 0)
                }
            }
            return ord
        }
        return customerOrders.first(where: { $0.id == orderId || $0.orderId == orderId })
    }
}
