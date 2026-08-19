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

    @Published public var products: [ServerProduct] = []
    @Published public var isLoading: Bool = false
    @Published public var errorMessage: String? = nil

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    public func fetchProducts() async {
        await MainActor.run {
            self.isLoading = true
            self.errorMessage = nil
        }

        do {
            let fetched: [ServerProduct] = try await apiClient.get(endpoint: "/api/v1/catalog/products")
            await MainActor.run {
                self.products = fetched
                self.isLoading = false
            }
        } catch {
            await MainActor.run {
                self.errorMessage = error.localizedDescription
                self.isLoading = false
            }
        }
    }
}
