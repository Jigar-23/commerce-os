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

    enum CodingKeys: String, CodingKey {
        case offerId
        case offer_id
        case id
        case orderId
        case order_id
        case payout
        case payoutAmount
        case payout_amount
        case earningsAmount
        case earnings_amount
        case totalEarnings
        case merchantName
        case merchant_name
        case merchantAddress
        case merchant_address
        case pickupAddress
        case pickup_address
        case merchantLat
        case merchant_lat
        case merchantLng
        case merchant_lng
        case customerName
        case customer_name
        case customerAddress
        case customer_address
        case deliveryAddress
        case delivery_address
        case customerLat
        case customer_lat
        case customerLng
        case customer_lng
        case distanceKm
        case distance_km
        case totalDistanceKm
        case total_distance_km
        case expiresAt
        case expires_at
        case offerExpiresAt
        case offer_expires_at
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let resolvedOfferId = (try? container.decode(String.self, forKey: .offerId))
            ?? (try? container.decode(String.self, forKey: .offer_id))
            ?? (try? container.decode(String.self, forKey: .id))
            ?? UUID().uuidString
        self.offerId = resolvedOfferId

        self.orderId = (try? container.decode(String.self, forKey: .orderId))
            ?? (try? container.decode(String.self, forKey: .order_id))
            ?? ""

        var resolvedPayout: Double? = try? container.decode(Double.self, forKey: .payout)
        if resolvedPayout == nil { resolvedPayout = try? container.decode(Double.self, forKey: .payoutAmount) }
        if resolvedPayout == nil { resolvedPayout = try? container.decode(Double.self, forKey: .payout_amount) }
        if resolvedPayout == nil { resolvedPayout = try? container.decode(Double.self, forKey: .earningsAmount) }
        if resolvedPayout == nil { resolvedPayout = try? container.decode(Double.self, forKey: .earnings_amount) }
        if resolvedPayout == nil { resolvedPayout = try? container.decode(Double.self, forKey: .totalEarnings) }
        if resolvedPayout == nil, let s = try? container.decode(String.self, forKey: .payout) { resolvedPayout = Double(s) }
        if resolvedPayout == nil, let s = try? container.decode(String.self, forKey: .payoutAmount) { resolvedPayout = Double(s) }
        if resolvedPayout == nil, let s = try? container.decode(String.self, forKey: .earnings_amount) { resolvedPayout = Double(s) }
        self.payoutAmount = resolvedPayout ?? 35.0

        self.merchantName = (try? container.decode(String.self, forKey: .merchantName))
            ?? (try? container.decode(String.self, forKey: .merchant_name))
            ?? "CommerceOS Central Hub"

        self.merchantAddress = (try? container.decode(String.self, forKey: .merchantAddress))
            ?? (try? container.decode(String.self, forKey: .merchant_address))
            ?? (try? container.decode(String.self, forKey: .pickupAddress))
            ?? (try? container.decode(String.self, forKey: .pickup_address))
            ?? "Rewari Central Hub"

        self.merchantLat = (try? container.decode(Double.self, forKey: .merchantLat))
            ?? (try? container.decode(Double.self, forKey: .merchant_lat))
            ?? 28.202224

        self.merchantLng = (try? container.decode(Double.self, forKey: .merchantLng))
            ?? (try? container.decode(Double.self, forKey: .merchant_lng))
            ?? 76.615418

        self.customerName = (try? container.decode(String.self, forKey: .customerName))
            ?? (try? container.decode(String.self, forKey: .customer_name))
            ?? "Customer"

        self.customerAddress = (try? container.decode(String.self, forKey: .customerAddress))
            ?? (try? container.decode(String.self, forKey: .customer_address))
            ?? (try? container.decode(String.self, forKey: .deliveryAddress))
            ?? (try? container.decode(String.self, forKey: .delivery_address))
            ?? "Customer Delivery Location"

        self.customerLat = (try? container.decode(Double.self, forKey: .customerLat))
            ?? (try? container.decode(Double.self, forKey: .customer_lat))
            ?? 28.1918

        self.customerLng = (try? container.decode(Double.self, forKey: .customerLng))
            ?? (try? container.decode(Double.self, forKey: .customer_lng))
            ?? 76.6081

        var resolvedDist: Double? = try? container.decode(Double.self, forKey: .totalDistanceKm)
        if resolvedDist == nil { resolvedDist = try? container.decode(Double.self, forKey: .total_distance_km) }
        if resolvedDist == nil { resolvedDist = try? container.decode(Double.self, forKey: .distanceKm) }
        if resolvedDist == nil { resolvedDist = try? container.decode(Double.self, forKey: .distance_km) }
        if resolvedDist == nil, let s = try? container.decode(String.self, forKey: .totalDistanceKm) { resolvedDist = Double(s) }
        if resolvedDist == nil, let s = try? container.decode(String.self, forKey: .distanceKm) { resolvedDist = Double(s) }
        self.totalDistanceKm = resolvedDist ?? 2.5

        var rawExpMs: Double? = try? container.decode(Double.self, forKey: .offerExpiresAt)
        if rawExpMs == nil { rawExpMs = try? container.decode(Double.self, forKey: .offer_expires_at) }
        if rawExpMs == nil { rawExpMs = try? container.decode(Double.self, forKey: .expiresAt) }
        if rawExpMs == nil { rawExpMs = try? container.decode(Double.self, forKey: .expires_at) }

        if let expMs = rawExpMs {
            if expMs > 1_000_000_000_000 {
                self.expiresAt = Date(timeIntervalSince1970: expMs / 1000.0)
            } else if expMs > 1_000_000_000 {
                self.expiresAt = Date(timeIntervalSince1970: expMs)
            } else {
                self.expiresAt = Date().addingTimeInterval(120)
            }
        } else {
            self.expiresAt = Date().addingTimeInterval(120)
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(offerId, forKey: .offerId)
        try container.encode(orderId, forKey: .orderId)
        try container.encode(payoutAmount, forKey: .payoutAmount)
        try container.encode(merchantName, forKey: .merchantName)
        try container.encode(merchantAddress, forKey: .merchantAddress)
        try container.encode(merchantLat, forKey: .merchantLat)
        try container.encode(merchantLng, forKey: .merchantLng)
        try container.encode(customerName, forKey: .customerName)
        try container.encode(customerAddress, forKey: .customerAddress)
        try container.encode(customerLat, forKey: .customerLat)
        try container.encode(customerLng, forKey: .customerLng)
        try container.encode(totalDistanceKm, forKey: .totalDistanceKm)
    }
}

public struct ActiveOffersResponseDto: Codable {
    public let ok: Bool?
    public let count: Int?
    public let offers: [DispatchOfferDto]?

    public init(ok: Bool? = true, count: Int? = nil, offers: [DispatchOfferDto]? = nil) {
        self.ok = ok
        self.count = count
        self.offers = offers
    }

    enum CodingKeys: String, CodingKey {
        case ok
        case count
        case offers
    }

    public init(from decoder: Decoder) throws {
        // Direct array response: [ { offerId: ... }, ... ]
        if let singleContainer = try? decoder.singleValueContainer(),
           let directList = try? singleContainer.decode([DispatchOfferDto].self) {
            self.ok = true
            self.count = directList.count
            self.offers = directList
            return
        }

        let container = try? decoder.container(keyedBy: CodingKeys.self)
        self.ok = try? container?.decode(Bool.self, forKey: .ok)
        self.count = try? container?.decode(Int.self, forKey: .count)

        if let list = try? container?.decode([DispatchOfferDto].self, forKey: .offers) {
            self.offers = list
        } else if let singleOffer = try? DispatchOfferDto(from: decoder) {
            // Decoded the entire root object as a single DispatchOfferDto
            self.offers = [singleOffer]
        } else {
            self.offers = []
        }
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encodeIfPresent(ok, forKey: .ok)
        try container.encodeIfPresent(count, forKey: .count)
        try container.encodeIfPresent(offers, forKey: .offers)
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

    enum SessionCodingKeys: String, CodingKey {
        case session
    }

    enum CodingKeys: String, CodingKey {
        case deliveryId = "delivery_id"
        case deliveryIdCamel = "deliveryId"
        case id
        case orderId = "order_id"
        case orderIdCamel = "orderId"
        case status
        case state
        case orderStatus = "order_status"
        case merchantName = "merchant_name"
        case merchantAddress = "merchant_address"
        case pickupAddress = "pickup_address"
        case merchantLat = "merchant_lat"
        case merchantLng = "merchant_lng"
        case customerName = "customer_name"
        case customerPhone = "customer_phone"
        case customerAddress = "customer_address"
        case deliveryAddress = "delivery_address"
        case customerLat = "customer_lat"
        case customerLng = "customer_lng"
        case items
        case isCod = "is_cod"
        case codAmountToCollect = "cod_amount_to_collect"
        case codAmount = "cod_amount"
        case routePolyline = "route_polyline"
        case codReconciled = "cod_reconciled"
        case codCollectedAmount = "cod_collected_amount"
        case payoutAmount = "payout_amount"
        case payout
    }

    public init(from decoder: Decoder) throws {
        // If nested inside { "session": { ... } }
        let outerContainer = try? decoder.container(keyedBy: SessionCodingKeys.self)
        let container: KeyedDecodingContainer<CodingKeys>
        if let inner = try? outerContainer?.nestedContainer(keyedBy: CodingKeys.self, forKey: .session) {
            container = inner
        } else {
            container = try decoder.container(keyedBy: CodingKeys.self)
        }

        self.deliveryId = (try? container.decode(String.self, forKey: .deliveryId))
            ?? (try? container.decode(String.self, forKey: .deliveryIdCamel))
            ?? (try? container.decode(String.self, forKey: .id))
            ?? ""

        self.orderId = (try? container.decode(String.self, forKey: .orderId))
            ?? (try? container.decode(String.self, forKey: .orderIdCamel))
            ?? ""

        self.status = (try? container.decode(String.self, forKey: .status))
            ?? (try? container.decode(String.self, forKey: .state))
            ?? (try? container.decode(String.self, forKey: .orderStatus))
            ?? "ASSIGNED"

        self.merchantName = (try? container.decode(String.self, forKey: .merchantName))
            ?? "CommerceOS Central Hub"

        self.merchantAddress = (try? container.decode(String.self, forKey: .merchantAddress))
            ?? (try? container.decode(String.self, forKey: .pickupAddress))
            ?? "Rewari Central Hub"

        self.merchantLat = (try? container.decode(Double.self, forKey: .merchantLat)) ?? 28.202224
        self.merchantLng = (try? container.decode(Double.self, forKey: .merchantLng)) ?? 76.615418

        self.customerName = (try? container.decode(String.self, forKey: .customerName)) ?? "Customer"
        self.customerPhone = (try? container.decode(String.self, forKey: .customerPhone)) ?? "+919817916180"

        self.customerAddress = (try? container.decode(String.self, forKey: .customerAddress))
            ?? (try? container.decode(String.self, forKey: .deliveryAddress))
            ?? "Customer Location"

        self.customerLat = (try? container.decode(Double.self, forKey: .customerLat)) ?? 28.1918
        self.customerLng = (try? container.decode(Double.self, forKey: .customerLng)) ?? 76.6081

        self.items = try? container.decode([String].self, forKey: .items)
        self.isCod = (try? container.decode(Bool.self, forKey: .isCod)) ?? false

        var codAmt: Double? = try? container.decode(Double.self, forKey: .codAmountToCollect)
        if codAmt == nil { codAmt = try? container.decode(Double.self, forKey: .codAmount) }
        if codAmt == nil, let s = try? container.decode(String.self, forKey: .codAmount) { codAmt = Double(s) }
        self.codAmountToCollect = codAmt

        self.routePolyline = try? container.decode(String.self, forKey: .routePolyline)
        self.codReconciled = (try? container.decode(Bool.self, forKey: .codReconciled)) ?? false
        self.codCollectedAmount = try? container.decode(Double.self, forKey: .codCollectedAmount)

        var payout: Double? = try? container.decode(Double.self, forKey: .payoutAmount)
        if payout == nil { payout = try? container.decode(Double.self, forKey: .payout) }
        if payout == nil, let s = try? container.decode(String.self, forKey: .payout) { payout = Double(s) }
        self.payoutAmount = payout ?? 35.0
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(deliveryId, forKey: .deliveryId)
        try container.encode(orderId, forKey: .orderId)
        try container.encode(status, forKey: .status)
        try container.encode(merchantName, forKey: .merchantName)
        try container.encode(merchantAddress, forKey: .merchantAddress)
        try container.encode(merchantLat, forKey: .merchantLat)
        try container.encode(merchantLng, forKey: .merchantLng)
        try container.encode(customerName, forKey: .customerName)
        try container.encode(customerPhone, forKey: .customerPhone)
        try container.encode(customerAddress, forKey: .customerAddress)
        try container.encode(customerLat, forKey: .customerLat)
        try container.encode(customerLng, forKey: .customerLng)
        try container.encodeIfPresent(items, forKey: .items)
        try container.encode(isCod, forKey: .isCod)
        try container.encodeIfPresent(codAmountToCollect, forKey: .codAmountToCollect)
        try container.encodeIfPresent(routePolyline, forKey: .routePolyline)
        try container.encode(codReconciled, forKey: .codReconciled)
        try container.encodeIfPresent(codCollectedAmount, forKey: .codCollectedAmount)
        try container.encodeIfPresent(payoutAmount, forKey: .payoutAmount)
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

    enum CodingKeys: String, CodingKey {
        case success
        case ok
        case verified
        case message
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let resolvedSuccess = (try? container.decode(Bool.self, forKey: .success))
            ?? (try? container.decode(Bool.self, forKey: .ok))
            ?? (try? container.decode(Bool.self, forKey: .verified))
            ?? false
        self.success = resolvedSuccess
        self.message = try? container.decode(String.self, forKey: .message)
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(success, forKey: .success)
        try container.encodeIfPresent(message, forKey: .message)
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
        assignedHub: String? = "Rewari Central Hub",
        vehicleNumber: String? = "HR 26 AB 1234"
    ) {
        self.riderId = riderId
        self.name = name
        self.phone = phone
        self.vehicleType = vehicleType
        self.todayEarnings = todayEarnings
        self.todayCompletedOrders = todayCompletedOrders
        self.rating = rating
        self.assignedHub = assignedHub
        self.vehicleNumber = vehicleNumber
    }

    enum CodingKeys: String, CodingKey {
        case riderId = "rider_id"
        case riderIdCamel = "riderId"
        case id
        case name
        case fullName = "full_name"
        case phone
        case vehicleType = "vehicle_type"
        case todayEarnings = "today_earnings"
        case earningsToday = "earningsToday"
        case earningsTodayFormatted
        case todayCompletedOrders = "today_completed_orders"
        case completedToday
        case rating
        case assignedHub = "assigned_hub"
        case vehicleNumber = "vehicle_number"
        case vehicle
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.riderId = (try? container.decode(String.self, forKey: .riderId))
            ?? (try? container.decode(String.self, forKey: .riderIdCamel))
            ?? (try? container.decode(String.self, forKey: .id))
            ?? "rdr_9817916180"

        self.name = (try? container.decode(String.self, forKey: .name))
            ?? (try? container.decode(String.self, forKey: .fullName))
            ?? "Rider Partner"

        self.phone = (try? container.decode(String.self, forKey: .phone))
            ?? "+919817916180"

        self.vehicleType = (try? container.decode(String.self, forKey: .vehicleType))
            ?? "EV 2-Wheeler"

        var earnings: Double = 0.0
        if let d = try? container.decode(Double.self, forKey: .todayEarnings) {
            earnings = d
        } else if let d = try? container.decode(Double.self, forKey: .earningsToday) {
            earnings = d
        } else if let s = try? container.decode(String.self, forKey: .earningsTodayFormatted) {
            let digits = s.filter { $0.isNumber || $0 == "." }
            earnings = Double(digits) ?? 0.0
        }
        self.todayEarnings = earnings

        var completed: Int = 0
        if let i = try? container.decode(Int.self, forKey: .todayCompletedOrders) {
            completed = i
        } else if let i = try? container.decode(Int.self, forKey: .completedToday) {
            completed = i
        }
        self.todayCompletedOrders = completed

        self.rating = (try? container.decode(Double.self, forKey: .rating)) ?? 5.0

        self.assignedHub = (try? container.decode(String.self, forKey: .assignedHub))
            ?? "Rewari Central Hub"

        self.vehicleNumber = (try? container.decode(String.self, forKey: .vehicleNumber))
            ?? (try? container.decode(String.self, forKey: .vehicle))
            ?? "HR 26 AB 1234"
    }

    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(riderId, forKey: .riderId)
        try container.encode(name, forKey: .name)
        try container.encode(phone, forKey: .phone)
        try container.encodeIfPresent(vehicleType, forKey: .vehicleType)
        try container.encode(todayEarnings, forKey: .todayEarnings)
        try container.encode(todayCompletedOrders, forKey: .todayCompletedOrders)
        try container.encode(rating, forKey: .rating)
        try container.encodeIfPresent(assignedHub, forKey: .assignedHub)
        try container.encodeIfPresent(vehicleNumber, forKey: .vehicleNumber)
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
