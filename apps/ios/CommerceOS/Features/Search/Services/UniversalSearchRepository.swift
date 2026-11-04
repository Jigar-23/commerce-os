import Foundation

/**
 * 1:1 Parity with Android UniversalSearchRepository.kt (118 lines).
 * Repository querying omni-search, suggestions, and auto-correct APIs.
 */
public class UniversalSearchRepository {
    private let apiClient: APIClient

    public init(apiClient: APIClient = .shared) {
        self.apiClient = apiClient
    }

    /**
     * Executes omni-search across products, stores, restaurants, services, brands.
     * Integrates typo fallback if initial query returns zero matches.
     */
    public func executeSearch(
        query: String,
        vertical: String? = nil,
        category: String? = nil
    ) async throws -> [SearchResult] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return [] }

        var queryParams: [String] = ["q=\(trimmed.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? trimmed)"]
        if let v = vertical, !v.isEmpty && v != "all" {
            queryParams.append("vertical=\(v.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? v)")
        }
        if let c = category, !c.isEmpty && c != "All" {
            queryParams.append("category=\(c.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? c)")
        }

        let endpoint = "/api/v1/search?\(queryParams.joined(separator: "&"))"

        do {
            let response: SearchResponse = try await apiClient.request(endpoint: endpoint)
            var results = response.allResults
            if results.isEmpty {
                // Typo-correction fallback
                let typo = SearchTypoSynonymEngine.correctTypo(query: trimmed)
                if typo.wasCorrected {
                    let correctedEndpoint = "/api/v1/search?q=\(typo.correctedQuery.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? typo.correctedQuery)"
                    if let correctedResp: SearchResponse = try? await apiClient.request(endpoint: correctedEndpoint) {
                        results = correctedResp.allResults
                    }
                }
            }
            return results
        } catch {
            // Also try medicine search alias if /api/v1/search is not available
            let medEndpoint = "/api/v1/catalog/medicines/search?q=\(trimmed.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? trimmed)"
            if let products: [ProductDto] = try? await apiClient.request(endpoint: medEndpoint) {
                return products.map { p in
                    let discount: Int? = p.mrp > p.price ? Int(round(((p.mrp - p.price) / p.mrp) * 100)) : nil
                    return SearchResult(
                        entityType: .product,
                        entityId: p.id,
                        title: p.name,
                        subtitle: p.brand ?? p.saltComposition ?? "",
                        image: p.imageUrl,
                        price: p.price,
                        mrp: p.mrp,
                        discountPercent: discount,
                        vertical: p.category.lowercased(),
                        availabilityStatus: p.inStock ? "In Stock" : "Out of Stock",
                        isExpressEligible: true,
                        saltComposition: p.saltComposition,
                        packSize: p.packSize
                    )
                }
            }
            throw error
        }
    }

    /**
     * Loads live typeahead autocomplete suggestions.
     */
    public func autocomplete(query: String, vertical: String? = nil) async -> [SearchSuggestion] {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        guard trimmed.count >= 2 else { return [] }

        var queryParams: [String] = ["q=\(trimmed.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? trimmed)"]
        if let v = vertical, !v.isEmpty {
            queryParams.append("vertical=\(v.addingPercentEncoding(withAllowedCharacters: CharacterSet.urlQueryAllowed) ?? v)")
        }

        let endpoint = "/api/v1/search/autocomplete?\(queryParams.joined(separator: "&"))"
        if let suggestions: [SearchSuggestion] = try? await apiClient.request(endpoint: endpoint) {
            return suggestions
        }
        return []
    }
}
