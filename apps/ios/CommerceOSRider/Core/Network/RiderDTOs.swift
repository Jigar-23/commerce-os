import Foundation

public struct DispatchOfferDto: Identifiable, Codable {
    public var id: String { offerId }
    public let offerId: String
    public let orderId: String
    public let payoutAmount: Double
    public let merchantName: String
    public let merchantAddress: String
    public let merchantLat: Double
    public let merchantLng: Double
    public let customerName: String
    public let customerAddress: String
    public let customerLat: Double
    public let customerLng: Double
    public let totalDistanceKm: Double
    public let expiresAt: Date?

    public init(
        offerId: String,
        orderId: String,
        payoutAmount: Double,
        merchantName: String,
        merchantAddress: String,
        merchantLat: Double,
        merchantLng: Double,
        customerName: String,
        customerAddress: String,
        customerLat: Double,
        customerLng: Double,
        totalDistanceKm: Double,
        expiresAt: Date? = nil
    ) {
        self.offerId = offerId
        self.orderId = orderId
        self.payoutAmount = payoutAmount
        self.merchantName = merchantName
        self.merchantAddress = merchantAddress
        self.merchantLat = merchantLat
        self.merchantLng = merchantLng
        self.customerName = customerName
        self.customerAddress = customerAddress
        self.customerLat = customerLat
        self.customerLng = customerLng
        self.totalDistanceKm = totalDistanceKm
        self.expiresAt = expiresAt
    }
}

public struct ActiveDeliverySessionDto: Identifiable, Codable {
    public var id: String { deliveryId }
    public let deliveryId: String
    public let orderId: String
    public let status: String
    public let merchantName: String
    public let merchantAddress: String
    public let merchantLat: Double
    public let merchantLng: Double
    public let customerName: String
    public let customerPhone: String
    public let customerAddress: String
    public let customerLat: Double
    public let customerLng: Double
    public let items: [String]?
    public let isCod: Bool
    public let codAmountToCollect: Double?
    public let routePolyline: String?
    public var codReconciled: Bool
    public var codCollectedAmount: Double?
    public var payoutAmount: Double?

    public init(
        deliveryId: String,
        orderId: String,
        status: String,
        merchantName: String,
        merchantAddress: String,
        merchantLat: Double,
        merchantLng: Double,
        customerName: String,
        customerPhone: String,
        customerAddress: String,
        customerLat: Double,
        customerLng: Double,
        items: [String]? = nil,
        isCod: Bool = false,
        codAmountToCollect: Double? = nil,
        routePolyline: String? = nil,
        codReconciled: Bool = false,
        codCollectedAmount: Double? = nil,
        payoutAmount: Double? = nil
    ) {
        self.deliveryId = deliveryId
        self.orderId = orderId
        self.status = status
        self.merchantName = merchantName
        self.merchantAddress = merchantAddress
        self.merchantLat = merchantLat
        self.merchantLng = merchantLng
        self.customerName = customerName
        self.customerPhone = customerPhone
        self.customerAddress = customerAddress
        self.customerLat = customerLat
        self.customerLng = customerLng
        self.items = items
        self.isCod = isCod
        self.codAmountToCollect = codAmountToCollect
        self.routePolyline = routePolyline
        self.codReconciled = codReconciled
        self.codCollectedAmount = codCollectedAmount
        self.payoutAmount = payoutAmount
    }
}

public struct DeliverWithOtpRequest: Codable {
    public let otp: String
    public let cashCollected: Double?

    public init(otp: String, cashCollected: Double? = nil) {
        self.otp = otp
        self.cashCollected = cashCollected
    }
}

public struct DeliverWithOtpResponse: Codable {
    public let success: Bool
    public let message: String?

    public init(success: Bool, message: String? = nil) {
        self.success = success
        self.message = message
    }
}

public struct RiderProfileDto: Identifiable, Codable {
    public var id: String { riderId }
    public let riderId: String
    public let name: String
    public let phone: String
    public let vehicleType: String?
    public let todayEarnings: Double
    public let todayCompletedOrders: Int
    public let rating: Double
    public let assignedHub: String?
    public let vehicleNumber: String?

    public init(
        riderId: String,
        name: String,
        phone: String,
        vehicleType: String? = nil,
        todayEarnings: Double = 0.0,
        todayCompletedOrders: Int = 0,
        rating: Double = 5.0,
        assignedHub: String? = "Koramangala Dark Store Hub #04",
        vehicleNumber: String? = "KA 01 EQ 4920"
    ) {
        self.riderId = riderId
        self.name = name
        self.phone = phone
        self.vehicleType = vehicleType
        self.todayEarnings = todayEarnings
        self.todayCompletedOrders = todayCompletedOrders
        self.rating = rating
        self.assignedHub = assignedHub ?? "Koramangala Dark Store Hub #04"
        self.vehicleNumber = vehicleNumber ?? "KA 01 EQ 4920"
    }
}

public struct RiderShiftDto: Codable {
    public let riderId: String
    public let isOnline: Bool
    public let lastShiftStart: Date?

    public init(riderId: String, isOnline: Bool, lastShiftStart: Date? = nil) {
        self.riderId = riderId
        self.isOnline = isOnline
        self.lastShiftStart = lastShiftStart
    }
}

public struct RiderNotificationItem: Identifiable, Codable {
    public var id: String { notificationId }
    public let notificationId: String
    public let title: String
    public let message: String
    public let category: String
    public let createdAt: Date?
    public var readAt: Date?
    public let orderId: String?
    public let offerId: String?

    public init(
        notificationId: String,
        title: String,
        message: String,
        category: String,
        createdAt: Date? = nil,
        readAt: Date? = nil,
        orderId: String? = nil,
        offerId: String? = nil
    ) {
        self.notificationId = notificationId
        self.title = title
        self.message = message
        self.category = category
        self.createdAt = createdAt
        self.readAt = readAt
        self.orderId = orderId
        self.offerId = offerId
    }
}
