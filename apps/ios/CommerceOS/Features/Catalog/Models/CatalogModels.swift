import Foundation

/**
 * 1:1 Parity with Android CatalogModels.kt (45 lines).
 * Sub-category / destination chip filter, price band filter, and catalog query payloads.
 */

public struct Destination: Codable, Identifiable, Equatable, Hashable {
    public let id: String
    public let name: String
    public let slug: String

    public init(id: String = "", name: String = "", slug: String = "") {
        self.id = id
        self.name = name
        self.slug = slug
    }
}

public struct PriceBand: Codable, Identifiable, Equatable, Hashable {
    public var id: String { label }
    public let min: Double?
    public let max: Double?
    public let label: String

    public init(min: Double? = nil, max: Double? = nil, label: String = "") {
        self.min = min
        self.max = max
        self.label = label
    }
}

public struct CatalogFilters: Codable, Equatable, Hashable {
    public let minPrice: Double?
    public let maxPrice: Double?
    public let inStockOnly: Bool
    public let fastFulfillmentOnly: Bool

    public init(
        minPrice: Double? = nil,
        maxPrice: Double? = nil,
        inStockOnly: Bool = false,
        fastFulfillmentOnly: Bool = false
    ) {
        self.minPrice = minPrice
        self.maxPrice = maxPrice
        self.inStockOnly = inStockOnly
        self.fastFulfillmentOnly = fastFulfillmentOnly
    }
}

public enum CatalogSort: String, Codable, CaseIterable {
    case relevance = "RELEVANCE"
    case priceLowToHigh = "PRICE_LOW_TO_HIGH"
    case priceHighToLow = "PRICE_HIGH_TO_LOW"
    case popularity = "POPULARITY"
    case newest = "NEWEST"
}

public struct CatalogCategory: Codable, Identifiable, Equatable, Hashable {
    public let id: String
    public let slug: String
    public let name: String
    public let productCount: Int

    public init(id: String, slug: String, name: String, productCount: Int = 0) {
        self.id = id
        self.slug = slug
        self.name = name
        self.productCount = productCount
    }
}

public struct CatalogQuery: Equatable, Hashable {
    public let text: String?
    public let vertical: String?
    public let categoryId: String?
    public let brandId: String?
    public let storeId: String?
    public let collectionId: String?
    public let campaignId: String?
    public let offerId: String?
    public let locationAddressId: String?
    public let query: String
    public let categoryName: String?
    public let destinationId: String?
    public let priceBand: PriceBand?
    public let filters: CatalogFilters
    public let sort: CatalogSort?

    public init(
        text: String? = nil,
        vertical: String? = nil,
        categoryId: String? = nil,
        brandId: String? = nil,
        storeId: String? = nil,
        collectionId: String? = nil,
        campaignId: String? = nil,
        offerId: String? = nil,
        locationAddressId: String? = nil,
        query: String? = nil,
        categoryName: String? = nil,
        destinationId: String? = nil,
        priceBand: PriceBand? = nil,
        filters: CatalogFilters = CatalogFilters(),
        sort: CatalogSort? = nil
    ) {
        self.text = text
        self.vertical = vertical
        self.categoryId = categoryId
        self.brandId = brandId
        self.storeId = storeId
        self.collectionId = collectionId
        self.campaignId = campaignId
        self.offerId = offerId
        self.locationAddressId = locationAddressId
        self.query = query ?? text ?? ""
        self.categoryName = categoryName
        self.destinationId = destinationId
        self.priceBand = priceBand
        self.filters = filters
        self.sort = sort
    }
}

public let CATALOG_PRICE_BANDS: [PriceBand] = [
    PriceBand(min: nil, max: nil, label: "All Prices"),
    PriceBand(min: 0.0, max: 100.0, label: "Under ₹100"),
    PriceBand(min: 100.0, max: 300.0, label: "₹100 – ₹300"),
    PriceBand(min: 300.0, max: nil, label: "Above ₹300")
]

public struct PageResponse<T: Codable>: Codable {
    public let content: [T]
    public let totalElements: Int
    public let totalPages: Int
    public let page: Int
    public let size: Int
    public let hasMore: Bool
    public let nextOffset: Int?

    public init(
        content: [T] = [],
        totalElements: Int = 0,
        totalPages: Int = 1,
        page: Int = 0,
        size: Int = 20,
        hasMore: Bool = false,
        nextOffset: Int? = nil
    ) {
        self.content = content
        self.totalElements = totalElements
        self.totalPages = totalPages
        self.page = page
        self.size = size
        self.hasMore = hasMore
        self.nextOffset = nextOffset
    }
}

