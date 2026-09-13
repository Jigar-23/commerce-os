import Foundation

// MARK: - Auth DTOs
public struct CustomerOtpSendRequest: Codable {
    public let phone: String
    public init(phone: String) { self.phone = phone }
}

public struct CustomerOtpSendResponse: Codable {
    public let success: Bool
    public let message: String?
    public let requestId: String?
}

public struct CustomerOtpVerifyRequest: Codable {
    public let phone: String
    public let otp: String
    public init(phone: String, otp: String) {
        self.phone = phone
        self.otp = otp
    }
}

public struct CustomerSessionDto: Codable {
    public let customerId: String
    public let phone: String
    public let accessToken: String
    public let refreshToken: String?
    public let name: String?
}

// MARK: - Catalog & Search DTOs
public struct HomeFeedResponse: Codable {
    public let hero: HomeHeroDto?
    public let verticals: [HomeVerticalDto]?
    public let buyAgain: [HomeProductItemDto]?
    public let topDeals: [HomeProductItemDto]?
    public let popular: [HomeProductItemDto]?
    public let popularLabel: String?
    public let categories: [HomeCategoryDto]?
    public let feed: [HomeProductItemDto]?
}

public struct HomeHeroDto: Codable {
    public let campaignId: String?
    public let title: String
    public let subtitle: String?
    public let badge: String?
    public let ctaText: String?
    public let imageUrl: String?
    public let themeKey: String?
}

public struct HomeVerticalDto: Codable, Identifiable {
    public let id: String
    public let label: String
    public let tagline: String?
    public let iconKey: String?
    public let isLive: Bool
}

public struct HomeCategoryDto: Codable, Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String?
    public let itemCount: Int?
    public let verticalId: String?
}

public struct HomeProductItemDto: Codable, Identifiable {
    public let id: String
    public let sku: String
    public let name: String
    public let category: String?
    public let verticalId: String?
    public let price: Double
    public let sellingPrice: Double?
    public let discountedPrice: Double?
    public let mrp: Double?
    public let imageUrl: String?
    public let inStock: Bool?
    public let medicineDetails: HomeMedicineDetailsDto?

    public func toProductDto() -> ProductDto {
        let effPrice = discountedPrice ?? sellingPrice ?? price
        let effMrp = mrp ?? price
        let isRx = medicineDetails?.prescriptionRequired ?? false
        let isCold = medicineDetails?.coldChain ?? false
        let cat = category ?? "General"

        let genericSub: GenericSubstituteDto? = (effPrice > 20.0) ? GenericSubstituteDto(
            genericName: "\(name.components(separatedBy: " ").first ?? name) Bio-Equivalent",
            brandPrice: effPrice,
            genericPrice: max(5.0, round(effPrice * 0.35)),
            savingsPercentage: 65
        ) : nil

        return ProductDto(
            id: id,
            sku: sku,
            name: name,
            brand: nil,
            mrp: effMrp,
            price: effPrice,
            category: cat,
            saltComposition: name,
            requiresPrescription: isRx,
            isColdChain: isCold,
            inStock: inStock ?? true,
            genericSubstitute: genericSub
        )
    }
}

public struct HomeMedicineDetailsDto: Codable {
    public let prescriptionRequired: Bool?
    public let coldChain: Bool?
}

public struct HomeFeedDto: Codable {
    public let banners: [HomeBannerDto]
    public let categories: [CategoryDto]
    public let trendingProducts: [ProductDto]
    public let serviceabilityStatus: String?
}

public struct HomeBannerDto: Codable, Identifiable {
    public let id: String
    public let title: String
    public let subtitle: String?
    public let imageUrl: String?
    public let deepLink: String?
}

public struct CategoryDto: Codable, Identifiable {
    public let id: String
    public let name: String
    public let iconUrl: String?
    public let isPrescriptionRequired: Bool?
}

public struct ProductDto: Codable, Identifiable, Equatable {
    public let id: String
    public let sku: String
    public let name: String
    public let brand: String?
    public let mrp: Double
    public let price: Double
    public let category: String
    public let saltComposition: String?
    public let requiresPrescription: Bool
    public let isColdChain: Bool?
    public let inStock: Bool
    public let genericSubstitute: GenericSubstituteDto?
}

public struct GenericSubstituteDto: Codable, Equatable {
    public let genericName: String
    public let brandPrice: Double
    public let genericPrice: Double
    public let savingsPercentage: Int
}

// MARK: - Cart DTOs
public struct ServerCartDto: Codable {
    public let customerId: String
    public let items: [CartItemDto]
    public let subtotal: Double
    public let deliveryFee: Double
    public let surgeFee: Double
    public let total: Double
    public let requiresPrescription: Bool
}

public struct CartItemDto: Codable, Identifiable {
    public var id: String { sku }
    public let sku: String
    public let productId: String
    public let name: String
    public let price: Double
    public var quantity: Int
    public let requiresPrescription: Bool
}

public struct AddCartItemRequest: Codable {
    public let sku: String
    public let productId: String
    public let quantity: Int
    public init(sku: String, productId: String, quantity: Int) {
        self.sku = sku
        self.productId = productId
        self.quantity = quantity
    }
}

// MARK: - Address DTOs
public struct AddressDto: Codable, Identifiable {
    public let id: String
    public let customerId: String
    public let label: String // "Home", "Work", "Other"
    public let addressLine: String
    public let flatNumber: String?
    public let landmark: String?
    public let latitude: Double
    public let longitude: Double
    public let recipientName: String?
    public let recipientPhone: String?
    public let isDefault: Bool
}

public struct CreateAddressRequest: Codable {
    public let label: String
    public let addressLine: String
    public let flatNumber: String?
    public let landmark: String?
    public let latitude: Double
    public let longitude: Double
    public let recipientName: String?
    public let recipientPhone: String?
}

// MARK: - Order Placement & Tracking DTOs
public struct ServiceabilityRequest: Codable {
    public let latitude: Double
    public let longitude: Double
}

public struct ServiceabilityResponse: Codable {
    public let serviceable: Bool
    public let storeId: String?
    public let estimatedDeliveryMinutes: Int?
    public let darkStoreDistanceKm: Double?
}

public struct OrderQuoteRequest: Codable {
    public let customerId: String
    public let items: [CartItemDto]
    public let addressId: String
}

public struct OrderQuoteResponse: Codable {
    public let subtotal: Double
    public let deliveryFee: Double
    public let surgeFee: Double
    public let discount: Double
    public let grandTotal: Double
    public let estimatedMinutes: Int
}

public struct OrderConfirmationDto: Codable {
    public let orderId: String
    public let deliveryId: String?
    public let status: String
    public let totalAmount: Double
    public let estimatedDeliveryMinutes: Int
    public let placedAt: String
}

public struct CustomerOrderTrackingDto: Codable {
    public let orderId: String
    public let deliveryId: String?
    public let status: String // PLACED, ACCEPTED, PACKED, DISPATCHED, ARRIVED, DELIVERED
    public let currentStage: Int
    public let estimatedMinutes: Int
    public let merchantName: String?
    public let merchantAddress: String?
    public let merchantLat: Double?
    public let merchantLng: Double?
    public let customerAddress: String?
    public let customerLat: Double?
    public let customerLng: Double?
    public let riderName: String?
    public let riderPhone: String?
    public let riderLat: Double?
    public let riderLng: Double?
    public let routePolyline: String?
    public let deliveryOtp: String? // Authoritative sanitized 4-digit OTP shown to customer
}
