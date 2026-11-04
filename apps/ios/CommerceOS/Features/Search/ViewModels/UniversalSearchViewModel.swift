import Foundation
import Combine
import SwiftUI

public enum SearchUiState: Equatable {
    case idle
    case searching
    case success(query: String, results: [SearchResult])
    case empty(query: String)
    case error(message: String)
}

public struct SearchSession: Equatable {
    public var queryText: String = ""
    public var vertical: String? = nil
    public var locationAddressId: String? = nil
    public var intent: String? = nil
    public var filters: [String: String] = [:]
    public var sessionId: String = UUID().uuidString

    public init(
        queryText: String = "",
        vertical: String? = nil,
        locationAddressId: String? = nil,
        intent: String? = nil,
        filters: [String: String] = [:],
        sessionId: String = UUID().uuidString
    ) {
        self.queryText = queryText
        self.vertical = vertical
        self.locationAddressId = locationAddressId
        self.intent = intent
        self.filters = filters
        self.sessionId = sessionId
    }
}

/**
 * 1:1 Parity with Android UniversalSearchViewModel.kt (172 lines).
 * Domain ViewModel owning Universal Search pipeline, debounce scheduling & session tracking.
 */
public class UniversalSearchViewModel: ObservableObject {
    public static let shared = UniversalSearchViewModel()

    @Published public var session: SearchSession = SearchSession()
    @Published public private(set) var uiState: SearchUiState = .idle
    @Published public private(set) var suggestions: [SearchSuggestion] = []

    private let searchRepository: UniversalSearchRepository
    private var activeQueryRequestId: String = ""
    private var debounceTask: Task<Void, Never>? = nil
    private var searchTask: Task<Void, Never>? = nil
    private var autocompleteTask: Task<Void, Never>? = nil

    public init(searchRepository: UniversalSearchRepository = UniversalSearchRepository()) {
        self.searchRepository = searchRepository
    }

    public func initSession(queryText: String = "", vertical: String? = nil) {
        session = SearchSession(
            queryText: queryText,
            vertical: vertical,
            sessionId: UUID().uuidString
        )
        if !queryText.isEmpty {
            executeSearch(text: queryText, verticalScope: vertical)
        }
    }

    public func updateQuery(_ text: String, debounceMs: UInt64 = 300) {
        session.queryText = text
        executeDebouncedSearch(text: text, verticalScope: session.vertical, debounceMs: debounceMs)
        loadAutocomplete(query: text)
    }

    public func updateVertical(_ vertical: String?) {
        session.vertical = vertical
        if !session.queryText.isEmpty {
            executeSearch(text: session.queryText, verticalScope: vertical)
        }
    }

    public func executeDebouncedSearch(text: String, verticalScope: String?, debounceMs: UInt64 = 300) {
        debounceTask?.cancel()
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.count < 2 && (verticalScope == nil || verticalScope!.isEmpty) {
            uiState = .idle
            return
        }
        debounceTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: debounceMs * 1_000_000)
            if !Task.isCancelled {
                self.executeSearch(text: trimmed, verticalScope: verticalScope)
            }
        }
    }

    public func loadAutocomplete(query: String) {
        autocompleteTask?.cancel()
        guard !query.trimmingCharacters(in: .whitespaces).isEmpty else {
            self.suggestions = []
            return
        }
        autocompleteTask = Task { @MainActor in
            try? await Task.sleep(nanoseconds: 150 * 1_000_000)
            if !Task.isCancelled {
                let items = await searchRepository.autocomplete(query: query, vertical: session.vertical)
                if !Task.isCancelled {
                    self.suggestions = items
                }
            }
        }
    }

    public func executeSearch(text: String, verticalScope: String?) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty && (verticalScope == nil || verticalScope!.isEmpty) {
            searchTask?.cancel()
            uiState = .idle
            return
        }

        searchTask?.cancel()
        let requestId = UUID().uuidString
        activeQueryRequestId = requestId
        uiState = .searching

        searchTask = Task { @MainActor in
            do {
                let items = try await searchRepository.executeSearch(
                    query: trimmed,
                    vertical: verticalScope
                )

                // Latest-query-wins protection: discard older responses
                if self.activeQueryRequestId == requestId {
                    if items.isEmpty {
                        self.uiState = .empty(query: trimmed)
                    } else {
                        self.uiState = .success(query: trimmed, results: items)
                    }
                }
            } catch {
                if self.activeQueryRequestId == requestId {
                    self.uiState = .error(message: error.localizedDescription)
                }
            }
        }
    }

    public func reset() {
        session = SearchSession()
        uiState = .idle
        suggestions = []
        activeQueryRequestId = ""
    }
}
