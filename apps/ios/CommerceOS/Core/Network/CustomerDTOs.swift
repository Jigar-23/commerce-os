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

public struct CustomerProfileDto: Codable, Identifiable {
    public let id: String
    public let fullName: String?
    public let name: String?
    public let email: String?
    public let phone: String?
    public let status: String?

    public init(
        id: String,
        fullName: String? = nil,
        name: String? = nil,
        email: String? = nil,
        phone: String? = nil,
        status: String? = "ACTIVE"
    ) {
        self.id = id
        self.fullName = fullName
        self.name = name
        self.email = email
        self.phone = phone
        self.status = status
    }

    public var displayName: String {
        let n = fullName ?? name ?? ""
        return n.isEmpty ? "Customer" : n
    }

    public var effectivePhone: String {
        phone ?? ""
    }
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

        let resolvedImage = MedicineImageResolver.resolve(sku: sku, name: name, rawImage: imageUrl)

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
            genericSubstitute: genericSub,
            imageUrl: resolvedImage.isEmpty ? nil : resolvedImage
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
    public let rating: Double?
    public let reviewCount: Int?
    public let packSize: String?
    public let imageUrl: String?
    public let stockCount: Int?

    public init(
        id: String,
        sku: String,
        name: String,
        brand: String? = nil,
        mrp: Double,
        price: Double,
        category: String,
        saltComposition: String? = nil,
        requiresPrescription: Bool = false,
        isColdChain: Bool? = nil,
        inStock: Bool = true,
        genericSubstitute: GenericSubstituteDto? = nil,
        rating: Double? = nil,
        reviewCount: Int? = nil,
        packSize: String? = nil,
        imageUrl: String? = nil,
        stockCount: Int? = nil
    ) {
        self.id = id
        self.sku = sku
        self.name = name
        self.brand = brand
        self.mrp = mrp
        self.price = price
        self.category = category
        self.saltComposition = saltComposition
        self.requiresPrescription = requiresPrescription
        self.isColdChain = isColdChain
        self.inStock = inStock
        self.genericSubstitute = genericSubstitute
        self.rating = rating
        self.reviewCount = reviewCount
        self.packSize = packSize
        self.imageUrl = imageUrl
        self.stockCount = stockCount
    }

    public var effectiveRating: Double {
        if let rating = rating, rating > 0 { return rating }
        // Deterministic aesthetic rating based on SKU hash
        let hash = abs(sku.hashValue)
        return 4.5 + Double(hash % 5) / 10.0
    }

    public var effectiveReviewCount: Int {
        if let reviewCount = reviewCount, reviewCount > 0 { return reviewCount }
        let hash = abs(sku.hashValue)
        return 120 + (hash % 1800)
    }

    public var effectivePackSize: String {
        if let pack = packSize, !pack.isEmpty { return pack }
        if category.localizedCaseInsensitiveContains("Pharma") || category.localizedCaseInsensitiveContains("Medicine") || requiresPrescription {
            return "10 Tablets / Strip"
        } else if category.localizedCaseInsensitiveContains("Dairy") || category.localizedCaseInsensitiveContains("Milk") {
            return "500 ml"
        } else if category.localizedCaseInsensitiveContains("Fruit") || category.localizedCaseInsensitiveContains("Vegetable") {
            return "1 kg"
        } else {
            return "1 Unit"
        }
    }

    public var discountPercent: Int {
        guard mrp > price && mrp > 0 else { return 0 }
        return Int(round(((mrp - price) / mrp) * 100))
    }
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
public struct AddressDto: Codable, Identifiable, Hashable {
    public let id: String
    public let customerId: String?
    public let label: String?
    public let tag: String?
    public let addressType: String?
    public let addressLine: String
    public let city: String?
    public let state: String?
    public let postalCode: String?
    public let flatNumber: String?
    public let landmark: String?
    public let latitude: Double
    public let longitude: Double
    public let recipientName: String?
    public let recipientPhone: String?
    public let contactPhone: String?
    public let isDefault: Bool

    public init(
        id: String,
        customerId: String? = nil,
        label: String? = nil,
        tag: String? = nil,
        addressType: String? = nil,
        addressLine: String,
        city: String? = nil,
        state: String? = nil,
        postalCode: String? = nil,
        flatNumber: String? = nil,
        landmark: String? = nil,
        latitude: Double,
        longitude: Double,
        recipientName: String? = nil,
        recipientPhone: String? = nil,
        contactPhone: String? = nil,
        isDefault: Bool = false
    ) {
        self.id = id
        self.customerId = customerId
        self.label = label
        self.tag = tag ?? label
        self.addressType = addressType ?? (label?.uppercased() ?? "HOME")
        self.addressLine = addressLine
        self.city = city
        self.state = state
        self.postalCode = postalCode
        self.flatNumber = flatNumber
        self.landmark = landmark
        self.latitude = latitude
        self.longitude = longitude
        self.recipientName = recipientName
        self.recipientPhone = recipientPhone ?? contactPhone
        self.contactPhone = contactPhone ?? recipientPhone
        self.isDefault = isDefault
    }

    public var displayTag: String {
        let candidate = tag ?? label ?? addressType ?? "Home"
        if candidate.caseInsensitiveCompare("WORK") == .orderedSame { return "Work" }
        if candidate.caseInsensitiveCompare("OTHER") == .orderedSame { return "Other" }
        if candidate.caseInsensitiveCompare("CURRENT LOCATION") == .orderedSame { return "Current Location" }
        return "Home"
    }

    public var displaySummary: String {
        var parts: [String] = []
        if let flat = flatNumber, !flat.trimmingCharacters(in: .whitespaces).isEmpty {
            parts.append(flat)
        }
        if !addressLine.isEmpty {
            parts.append(addressLine)
        }
        if let c = city, !c.isEmpty && !addressLine.contains(c) {
            parts.append(c)
        }
        return parts.joined(separator: ", ")
    }
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
