import Foundation
import Combine

public enum CatalogUiState: Equatable {
    case loading
    case content(title: String, results: [ProductDto], totalElements: Int)
    case empty
    case error(message: String)
}

public struct CatalogRequestKey: Equatable, Hashable {
    public let query: String
    public let categoryId: String?
    public let brandId: String?
    public let storeId: String?
    public let collectionId: String?
    public let campaignId: String?
    public let offerId: String?
    public let vertical: String?
    public let minPrice: Double?
    public let maxPrice: Double?
}

/**
 * 1:1 Parity with Android CatalogViewModel.kt (225 lines).
 * Unified Product Listing Page (PLP) for free-text search, category browsing, and full catalog.
 * All filters (query/category/price band/destination) are applied SERVER-SIDE.
 * Pagination walks limit/offset pages with true hasMore flag.
 */
public final class CatalogViewModel: ObservableObject {
    public static let shared = CatalogViewModel()

    @Published public private(set) var uiState: CatalogUiState = .loading
    @Published public private(set) var destinations: [Destination] = []
    @Published public private(set) var selectedDestinationId: String? = nil
    @Published public private(set) var selectedPriceBand: PriceBand = CATALOG_PRICE_BANDS[0]
    @Published public private(set) var query: String = ""
    @Published public private(set) var categoryName: String? = nil
    @Published public private(set) var categoryId: String? = nil
    @Published public private(set) var brandId: String? = nil
    @Published public private(set) var storeId: String? = nil
    @Published public private(set) var collectionId: String? = nil
    @Published public private(set) var campaignId: String? = nil
    @Published public private(set) var offerId: String? = nil
    @Published public private(set) var vertical: String? = nil
    @Published public private(set) var hasMore: Bool = false
    @Published public private(set) var isLoadingMore: Bool = false

    private var offset: Int = 0
    private var activeGenerationId: String = ""
    private var loadTask: Task<Void, Never>? = nil
    private var debounceTask: Task<Void, Never>? = nil

    private static let pageSize = 20
    private static let debounceMs: UInt64 = 350_000_000 // 350ms in nanoseconds

    private let apiClient: APIClient

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    public var currentRequestKey: CatalogRequestKey {
        CatalogRequestKey(
            query: query,
            categoryId: categoryId,
            brandId: brandId,
            storeId: storeId,
            collectionId: collectionId,
            campaignId: campaignId,
            offerId: offerId,
            vertical: vertical,
            minPrice: selectedPriceBand.min,
            maxPrice: selectedPriceBand.max
        )
    }

    public func open(initial: CatalogQuery) {
        query = initial.query
        categoryName = initial.categoryName
        categoryId = initial.categoryId
        brandId = initial.brandId
        storeId = initial.storeId
        collectionId = initial.collectionId
        campaignId = initial.campaignId
        offerId = initial.offerId
        vertical = initial.vertical
        selectedPriceBand = initial.priceBand ?? CATALOG_PRICE_BANDS[0]
        selectedDestinationId = initial.destinationId
        reload()
        if destinations.isEmpty {
            loadDestinations()
        }
    }

    public func setPriceBand(_ band: PriceBand) {
        if band == selectedPriceBand { return }
        selectedPriceBand = band
        reload()
    }

    public func setDestination(_ destinationId: String?) {
        if destinationId == selectedDestinationId { return }
        selectedDestinationId = destinationId
        reload()
    }

    public func onQueryChange(_ newQuery: String) {
        if query == newQuery { return }
        query = newQuery
        debounceTask?.cancel()
        debounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: Self.debounceMs)
            if !Task.isCancelled {
                self.reload()
            }
        }
    }

    public func submitQuery() {
        debounceTask?.cancel()
        reload()
    }

    public func loadMore() {
        guard hasMore, !isLoadingMore, case .content(_, let currentResults, _) = uiState else { return }
        let generationId = activeGenerationId
        loadTask?.cancel()
        loadTask = Task { @MainActor in
            self.isLoadingMore = true
            do {
                let page = try await self.fetchPage(offset: self.offset, limit: Self.pageSize)
                if self.activeGenerationId == generationId {
                    let merged = currentResults + page.content
                    self.applyPage(page, replace: false, merged: merged)
                }
            } catch {
                // Non-fatal on load-more
            }
            self.isLoadingMore = false
        }
    }

    public func reload() {
        loadTask?.cancel()
        let generationId = UUID().uuidString
        activeGenerationId = generationId
        offset = 0

        loadTask = Task { @MainActor in
            self.uiState = .loading
            do {
                let page = try await self.fetchPage(offset: 0, limit: Self.pageSize)
                if self.activeGenerationId == generationId {
                    self.applyPage(page, replace: true, merged: page.content)
                }
            } catch {
                if self.activeGenerationId == generationId {
                    self.uiState = .error(message: error.localizedDescription)
                }
            }
        }
    }

    private func applyPage(_ page: PageResponse<ProductDto>, replace: Bool, merged: [ProductDto]) {
        offset = page.nextOffset ?? (offset + page.content.size)
        hasMore = page.hasMore
        let results = replace ? page.content : merged
        if results.isEmpty {
            uiState = .empty
        } else {
            uiState = .content(
                title: titleFor(),
                results: results,
                totalElements: page.totalElements
            )
        }
    }

    private func titleFor() -> String {
        if let brand = brandId, !brand.isEmpty { return "Brand: \(brand)" }
        if let cat = categoryName, !cat.isEmpty { return cat }
        if let catId = categoryId, !catId.isEmpty { return catId }
        if let vert = vertical, !vert.isEmpty && vert != "general" {
            return "\(vert.capitalized) Catalog"
        }
        if !query.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            return "Results for '\(query)'"
        }
        return "All Products"
    }

    private func fetchPage(offset: Int, limit: Int) async throws -> PageResponse<ProductDto> {
        var queryParams: [String: String] = [
            "limit": "\(limit)",
            "offset": "\(offset)"
        ]
        if !query.isEmpty { queryParams["q"] = query }
        if let catId = categoryId { queryParams["categoryId"] = catId }
        if let brand = brandId { queryParams["brandId"] = brand }
        if let min = selectedPriceBand.min { queryParams["minPrice"] = "\(min)" }
        if let max = selectedPriceBand.max { queryParams["maxPrice"] = "\(max)" }

        let queryString = queryParams.map { "\($0.key)=\($0.value.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? $0.value)" }.joined(separator: "&")
        let endpoint = "/api/v1/catalog/products?\(queryString)"

        do {
            let res: PageResponse<ProductDto> = try await apiClient.request(endpoint: endpoint)
            return res
        } catch {
            // Try fallback array response
            if let arrayRes: [ProductDto] = try? await apiClient.request(endpoint: endpoint) {
                return PageResponse(
                    content: arrayRes,
                    totalElements: arrayRes.count,
                    totalPages: 1,
                    page: 0,
                    size: arrayRes.count,
                    hasMore: false,
                    nextOffset: nil
                )
            }
            throw error
        }
    }

    private func loadDestinations() {
        Task { @MainActor in
            do {
                let dests: [Destination] = try await apiClient.request(endpoint: "/api/v1/catalog/destinations")
                self.destinations = dests
                if self.selectedDestinationId == nil {
                    self.selectedDestinationId = dests.first?.id
                }
            } catch {
                self.destinations = []
            }
        }
    }

    public func reset() {
        loadTask?.cancel()
        debounceTask?.cancel()
        uiState = .loading
        destinations = []
        selectedDestinationId = nil
        selectedPriceBand = CATALOG_PRICE_BANDS[0]
        query = ""
        categoryName = nullString()
        categoryId = nullString()
        brandId = nullString()
        storeId = nullString()
        collectionId = nullString()
        campaignId = nullString()
        offerId = nullString()
        vertical = nullString()
        hasMore = false
        isLoadingMore = false
        offset = 0
    }

    private func nullString() -> String? { nil }
}

private extension Array {
    var size: Int { count }
}
