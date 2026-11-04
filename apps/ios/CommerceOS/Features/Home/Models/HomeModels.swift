import Foundation

// MARK: - Universal Navigation Destination (Ported from Android HomeModels.kt)
public enum HomeDestination: Equatable, Hashable {
    case product(productId: String, vertical: String = "general")
    case category(categoryId: String, vertical: String = "general")
    case brand(brandId: String, vertical: String = "general")
    case store(storeId: String)
    case restaurant(restaurantId: String)
    case service(serviceId: String)
    case search(query: UniversalSearchQuery)
    case vertical(verticalId: String)
    case campaign(campaignId: String)
    case collection(collectionId: String)
    case offer(offerId: String)
    case orders
    case prescriptions
    case cart
    case categories
}

// MARK: - Universal Search Query
public struct UniversalSearchQuery: Equatable, Hashable {
    public var text: String
    public var vertical: String?
    public var intent: String?
    public var locationAddressId: String?
    public var filters: [String: String]
    public var sessionId: String
    
    public init(
        text: String = "",
        vertical: String? = nil,
        intent: String? = nil,
        locationAddressId: String? = nil,
        filters: [String: String] = [:],
        sessionId: String = UUID().uuidString
    ) {
        self.text = text
        self.vertical = vertical
        self.intent = intent
        self.locationAddressId = locationAddressId
        self.filters = filters
        self.sessionId = sessionId
    }
}

// MARK: - Vertical Presentation Definition
public struct VerticalPresentation: Identifiable, Hashable {
    public var id: String { verticalId }
    public let verticalId: String
    public let verticalName: String
    public let visualSymbol: String
    public let catalogHeader: String
    public let subtitle: String
    public let primaryColorHex: String
    
    public init(
        verticalId: String,
        verticalName: String,
        visualSymbol: String,
        catalogHeader: String,
        subtitle: String = "",
        primaryColorHex: String = "0E675E"
    ) {
        self.verticalId = verticalId
        self.verticalName = verticalName
        self.visualSymbol = visualSymbol
        self.catalogHeader = catalogHeader
        self.subtitle = subtitle
        self.primaryColorHex = primaryColorHex
    }
    
    public static let standardVerticals: [VerticalPresentation] = [
        VerticalPresentation(
            verticalId: "pharmacy",
            verticalName: "Pharmacy",
            visualSymbol: "🏥",
            catalogHeader: "Medicines & Healthcare",
            subtitle: "Genuine medicines delivered in 10 mins",
            primaryColorHex: "0E675E"
        ),
        VerticalPresentation(
            verticalId: "food",
            verticalName: "Food",
            visualSymbol: "🍔",
            catalogHeader: "Restaurants & Quick Bites",
            subtitle: "Piping hot meals delivered fast",
            primaryColorHex: "DC2626"
        ),
        VerticalPresentation(
            verticalId: "fashion",
            verticalName: "Fashion",
            visualSymbol: "👗",
            catalogHeader: "Apparel & Accessories",
            subtitle: "Trending styles delivered to your door",
            primaryColorHex: "7C3AED"
        ),
        VerticalPresentation(
            verticalId: "electronics",
            verticalName: "Electronics",
            visualSymbol: "📱",
            catalogHeader: "Gadgets & Accessories",
            subtitle: "Cables, chargers & audio gear",
            primaryColorHex: "2563EB"
        ),
        VerticalPresentation(
            verticalId: "services",
            verticalName: "Services",
            visualSymbol: "🛠️",
            catalogHeader: "Home & Repair Services",
            subtitle: "Verified doorstep professionals",
            primaryColorHex: "D97706"
        )
    ]
}

// MARK: - Home Banner Model
public struct HomeBanner: Identifiable, Hashable {
    public let id: String
    public let title: String
    public let subtitle: String
    public let discountTag: String?
    public let imageUrl: String
    public let targetDestination: HomeDestination
    
    public init(
        id: String,
        title: String,
        subtitle: String,
        discountTag: String? = nil,
        imageUrl: String,
        targetDestination: HomeDestination
    ) {
        self.id = id
        self.title = title
        self.subtitle = subtitle
        self.discountTag = discountTag
        self.imageUrl = imageUrl
        self.targetDestination = targetDestination
    }
}

// MARK: - Category Tile Model
public struct CategoryTile: Identifiable, Hashable {
    public let id: String
    public let title: String
    public let iconSystemName: String
    public let badgeText: String?
    public let verticalId: String
    
    public init(
        id: String,
        title: String,
        iconSystemName: String,
        badgeText: String? = nil,
        verticalId: String = "pharmacy"
    ) {
        self.id = id
        self.title = title
        self.iconSystemName = iconSystemName
        self.badgeText = badgeText
        self.verticalId = verticalId
    }
}

// MARK: - Home Context (1:1 with Android HomeModels.kt)
public struct HomeContext: Codable, Equatable {
    public let addressId: String?
    public let tag: String?
    public let addressLine: String?
    public let cityZip: String?
    public let geoPoint: String?
    public let deliveryZone: String?
    public let fulfillment: FulfillmentContext
    public let sequenceId: Int64

    public var hasAddress: Bool {
        !(addressId?.isEmpty ?? true) && !(addressLine?.isEmpty ?? true)
    }
    public var formattedEta: String? { fulfillment.etaLabel }
    public var displayLabel: String? {
        if let tag = tag, let addressLine = addressLine, !tag.isEmpty, !addressLine.isEmpty {
            return "\(tag) · \(addressLine)"
        }
        return addressLine
    }
    public var isServiceable: Bool {
        fulfillment.addressId == addressId && fulfillment.status == .serviceable
    }

    public init(
        addressId: String? = nil,
        tag: String? = nil,
        addressLine: String? = nil,
        cityZip: String? = nil,
        geoPoint: String? = nil,
        deliveryZone: String? = nil,
        fulfillment: FulfillmentContext = FulfillmentContext(),
        sequenceId: Int64 = 0
    ) {
        self.addressId = addressId
        self.tag = tag
        self.addressLine = addressLine
        self.cityZip = cityZip
        self.geoPoint = geoPoint
        self.deliveryZone = deliveryZone
        self.fulfillment = fulfillment
        self.sequenceId = sequenceId
    }
}

public typealias ApiAddress = AddressDto

