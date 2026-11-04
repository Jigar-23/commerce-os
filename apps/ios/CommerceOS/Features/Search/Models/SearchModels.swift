import Foundation

/** Strongly-typed search entity domains supported by Commerce OS Universal Search pipeline. */
public enum SearchEntityType: String, Codable, CaseIterable {
    case product = "PRODUCT"
    case store = "STORE"
    case restaurant = "RESTAURANT"
    case service = "SERVICE"
    case brand = "BRAND"
    case category = "CATEGORY"
    case collection = "COLLECTION"
    case campaign = "CAMPAIGN"
    case offer = "OFFER"
}

/** Typed Search Intent categories. */
public enum SearchIntent: String, Codable, CaseIterable {
    case productSearch = "PRODUCT_SEARCH"
    case categoryDiscovery = "CATEGORY_DISCOVERY"
    case restaurantDiscovery = "RESTAURANT_DISCOVERY"
    case storeDiscovery = "STORE_DISCOVERY"
    case serviceDiscovery = "SERVICE_DISCOVERY"
    case reorder = "REORDER"
    case nearMe = "NEAR_ME"
    case dealDiscovery = "DEAL_DISCOVERY"
}

/** Strongly-typed search result entry returned by Universal Search pipeline. */
public struct SearchResult: Identifiable, Codable, Equatable {
    public var id: String { entityId }
    public let entityType: SearchEntityType
    public let entityId: String
    public let title: String
    public let subtitle: String
    public let image: String?
    public let price: Double?
    public let mrp: Double?
    public let discountPercent: Int?
    public let vertical: String
    public let relevance: Float?
    public let globalRank: Int?
    public let score: Float?
    public let section: String?
    // Rich entity metadata
    public let sku: String?
    public let merchantId: String?
    public let storeType: String?
    public let location: String?
    public let cuisine: String?
    public let rating: Double?
    public let etaLabel: String?
    public let providerId: String?
    public let availabilityStatus: String?
    public let isExpressEligible: Bool?
    public let saltComposition: String?
    public let packSize: String?

    public init(
        entityType: SearchEntityType,
        entityId: String,
        title: String,
        subtitle: String = "",
        image: String? = nil,
        price: Double? = nil,
        mrp: Double? = nil,
        discountPercent: Int? = nil,
        vertical: String = "general",
        relevance: Float? = nil,
        globalRank: Int? = nil,
        score: Float? = nil,
        section: String? = nil,
        sku: String? = nil,
        merchantId: String? = nil,
        storeType: String? = nil,
        location: String? = nil,
        cuisine: String? = nil,
        rating: Double? = nil,
        etaLabel: String? = nil,
        providerId: String? = nil,
        availabilityStatus: String? = nil,
        isExpressEligible: Bool? = nil,
        saltComposition: String? = nil,
        packSize: String? = nil
    ) {
        self.entityType = entityType
        self.entityId = entityId
        self.title = title
        self.subtitle = subtitle
        self.image = image
        self.price = price
        self.mrp = mrp
        self.discountPercent = discountPercent
        self.vertical = vertical
        self.relevance = relevance
        self.globalRank = globalRank
        self.score = score
        self.section = section
        self.sku = sku
        self.merchantId = merchantId
        self.storeType = storeType
        self.location = location
        self.cuisine = cuisine
        self.rating = rating
        self.etaLabel = etaLabel
        self.providerId = providerId
        self.availabilityStatus = availabilityStatus
        self.isExpressEligible = isExpressEligible
        self.saltComposition = saltComposition
        self.packSize = packSize
    }
}

/** Server/domain-driven filter metadata for search queries. */
public struct SearchFilterOption: Identifiable, Codable, Equatable {
    public let id: String
    public let label: String

    public init(id: String, label: String) {
        self.id = id
        self.label = label
    }
}

public struct SearchFilterGroup: Identifiable, Codable, Equatable {
    public let id: String
    public let title: String
    public let options: [SearchFilterOption]

    public init(id: String, title: String, options: [SearchFilterOption]) {
        self.id = id
        self.title = title
        self.options = options
    }
}

public struct SearchFilterConfig: Codable, Equatable {
    public let vertical: String?
    public let filterGroups: [SearchFilterGroup]

    public init(vertical: String? = nil, filterGroups: [SearchFilterGroup] = []) {
        self.vertical = vertical
        self.filterGroups = filterGroups
    }

    public static func defaultConfigForVertical(_ vertical: String?) -> SearchFilterConfig {
        let base = [
            SearchFilterGroup(id: "fulfillment", title: "Fulfillment", options: [
                SearchFilterOption(id: "express_only", label: "⚡ Express Delivery")
            ]),
            SearchFilterGroup(id: "rating", title: "Rating", options: [
                SearchFilterOption(id: "4_plus", label: "★ 4.0 & Above"),
                SearchFilterOption(id: "4.5_plus", label: "★ 4.5 & Above")
            ]),
            SearchFilterGroup(id: "price", title: "Price", options: [
                SearchFilterOption(id: "under_500", label: "Under ₹500"),
                SearchFilterOption(id: "500_2000", label: "₹500 - ₹2,000"),
                SearchFilterOption(id: "above_2000", label: "Above ₹2,000")
            ])
        ]
        return SearchFilterConfig(vertical: vertical, filterGroups: base)
    }
}

/** Server-authored multi-domain search response. */
public struct SearchResponse: Codable, Equatable {
    public let query: String?
    public let totalCount: Int
    public let orderedResults: [SearchResult]?
    public let products: [SearchResult]?
    public let stores: [SearchResult]?
    public let restaurants: [SearchResult]?
    public let services: [SearchResult]?
    public let brands: [SearchResult]?
    public let categories: [SearchResult]?
    public let collections: [SearchResult]?
    public let campaigns: [SearchResult]?
    public let offers: [SearchResult]?

    public var allResults: [SearchResult] {
        if let ordered = orderedResults, !ordered.isEmpty {
            return ordered
        }
        var list: [SearchResult] = []
        if let p = products { list.append(contentsOf: p) }
        if let s = stores { list.append(contentsOf: s) }
        if let r = restaurants { list.append(contentsOf: r) }
        if let sv = services { list.append(contentsOf: sv) }
        if let b = brands { list.append(contentsOf: b) }
        if let c = categories { list.append(contentsOf: c) }
        if let col = collections { list.append(contentsOf: col) }
        if let cmp = campaigns { list.append(contentsOf: cmp) }
        if let o = offers { list.append(contentsOf: o) }
        return list.sorted { ($0.globalRank ?? Int.max) < ($1.globalRank ?? Int.max) }
    }
}

/** Strongly-typed discovery suggestion. */
public struct SearchSuggestion: Identifiable, Codable, Equatable {
    public let id: String
    public let type: SearchEntityType
    public let title: String
    public let subtitle: String
    public let vertical: String
    public let image: String?

    public init(
        id: String,
        type: SearchEntityType,
        title: String,
        subtitle: String = "",
        vertical: String = "general",
        image: String? = nil
    ) {
        self.id = id
        self.type = type
        self.title = title
        self.subtitle = subtitle
        self.vertical = vertical
        self.image = image
    }
}
