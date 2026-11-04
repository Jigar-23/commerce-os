import Foundation
import Combine

public enum CategoryTaxonomyUiState: Equatable {
    case loading
    case content(categories: [CatalogCategory])
    case error(message: String)
}

/**
 * 1:1 Parity with Android CategoryViewModel.kt (50 lines).
 * Server-owned category directory. Driven by active dynamic taxonomy.
 */
public final class CategoryViewModel: ObservableObject {
    public static let shared = CategoryViewModel()

    @Published public private(set) var taxonomy: CategoryTaxonomyUiState = .loading

    private let apiClient: APIClient

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    public func loadTaxonomy() {
        taxonomy = .loading
        Task { @MainActor in
            do {
                let categories: [CategoryDto] = try await apiClient.request(endpoint: "/api/v1/catalog/categories")
                if categories.isEmpty {
                    self.taxonomy = .error(message: "No catalog categories available from server.")
                } else {
                    let mapped = categories.map { dto in
                        CatalogCategory(
                            id: dto.id,
                            slug: dto.id.lowercased().replacingOccurrences(of: " ", with: "-"),
                            name: dto.name,
                            productCount: 15
                        )
                    }
                    self.taxonomy = .content(categories: mapped)
                }
            } catch {
                self.taxonomy = .error(message: error.localizedDescription)
            }
        }
    }

    public func reset() {
        taxonomy = .loading
    }
}
