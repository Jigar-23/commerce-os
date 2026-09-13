import Foundation

public struct ServerProduct: Identifiable, Codable {
    public let id: String
    public let sku: String
    public let name: String
    public let packSize: String?
    public let mrp: Double?
    public let price: Double
    public let discountedPrice: Double?
    public let rxRequirement: String?
    public let coldChainRequired: Bool?
    public let category: String?
    public let stockCount: Int?
    public let inStock: Bool?

    public var effectivePrice: Double {
        discountedPrice ?? price
    }
}

public class CatalogRepository: ObservableObject {
    public static let shared = CatalogRepository()
    private let apiClient: APIClient
    private let cache: OfflineCatalogCache

    @Published public var products: [ServerProduct] = []
    @Published public var isLoading: Bool = false
    @Published public var errorMessage: String? = nil
    @Published public var isUsingOfflineCache: Bool = false

    public init(
        apiClient: APIClient = .shared,
        cache: OfflineCatalogCache = .shared
    ) {
        self.apiClient = apiClient
        self.cache = cache
    }

    public func fetchProducts() async {
        // Fast-path: load cached items immediately
        let cached = await cache.getCachedProducts()
        if !cached.isEmpty {
            await MainActor.run {
                self.products = cached
                self.isUsingOfflineCache = true
            }
        }

        await MainActor.run {
            self.isLoading = true
            self.errorMessage = nil
        }

        do {
            let fetched: [ServerProduct] = try await apiClient.get(endpoint: "/api/v1/catalog/products")
            try? await cache.cacheProducts(fetched)
            
            await MainActor.run {
                self.products = fetched
                self.isLoading = false
                self.isUsingOfflineCache = false
            }
        } catch {
            await MainActor.run {
                if !cached.isEmpty {
                    self.errorMessage = "Offline Mode: Displaying \(cached.count) cached catalog items."
                    self.isUsingOfflineCache = true
                } else {
                    self.errorMessage = error.localizedDescription
                }
                self.isLoading = false
            }
        }
    }

    public func searchProducts(query: String) async -> [ServerProduct] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }
        let encoded = trimmed.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? trimmed
        do {
            let fetched: [ServerProduct] = try await apiClient.get(endpoint: "/api/v1/search?q=\(encoded)")
            return fetched
        } catch {
            return await searchOffline(query: trimmed)
        }
    }

    public func searchOffline(query: String) async -> [ServerProduct] {
        return await cache.searchOffline(query: query)
    }
}

extension ServerProduct {
    public func toProductDto() -> ProductDto {
        let isRx = (rxRequirement ?? "OTC").contains("RX") || (rxRequirement ?? "OTC").contains("SCHEDULE")
        let isCold = coldChainRequired ?? false
        let sellPrice = discountedPrice ?? price
        let effectiveMrp = mrp ?? price
        
        let genericSub: GenericSubstituteDto? = (sellPrice > 20.0) ? GenericSubstituteDto(
            genericName: "\(name.components(separatedBy: " ").first ?? name) Bio-Equivalent Salt",
            brandPrice: sellPrice,
            genericPrice: max(5.0, round(sellPrice * 0.35)),
            savingsPercentage: 65
        ) : nil

        return ProductDto(
            id: id,
            sku: sku,
            name: name,
            brand: packSize,
            mrp: effectiveMrp,
            price: sellPrice,
            category: category ?? "Medicines",
            saltComposition: name,
            requiresPrescription: isRx,
            isColdChain: isCold,
            inStock: inStock ?? ((stockCount ?? 1) > 0),
            genericSubstitute: genericSub
        )
    }
}

