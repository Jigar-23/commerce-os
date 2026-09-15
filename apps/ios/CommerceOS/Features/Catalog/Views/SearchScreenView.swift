import SwiftUI

/**
 * Universal Commerce OS Search Screen (1:1 Android Parity with SearchScreen.kt).
 * Sleek industrial-standard medical search experience matching Apollo 24|7 & Tata 1mg:
 * - Search bar with clear, mic, and camera actions
 * - Recent Searches with SharedPreferences/UserDefaults persistence ("commerce_os_search_prefs")
 * - Popular / Trending search pills
 * - Live dynamic product search with 2-column grid and product details sheet
 */
public struct SearchScreenView: View {
    @Environment(\.presentationMode) private var presentationMode
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    
    let initialQuery: String
    let onBack: () -> Void
    
    @State private var queryText: String = ""
    @State private var recentSearches: [String] = []
    @State private var searchResults: [ProductDto] = []
    @State private var isLoading: Bool = false
    @State private var selectedProductForDetail: ProductDto? = nil
    
    private let prefsKey = "recent_queries"
    private let prefsSuite = "commerce_os_search_prefs"
    
    private let popularSearches = [
        "Dolo 650",
        "Paracetamol",
        "Augmentin 625",
        "Benadryl",
        "Pan 40",
        "Vitamins",
        "Zincovit",
        "Skin Care",
        "First Aid"
    ]
    
    public init(initialQuery: String = "", onBack: @escaping () -> Void = {}) {
        self.initialQuery = initialQuery
        self.onBack = onBack
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // TOP SEARCH BAR WITH SLEEK INDUSTRIAL AESTHETIC (Verbatim Android SearchScreen.kt)
            HStack(spacing: 10) {
                Button(action: {
                    onBack()
                    presentationMode.wrappedValue.dismiss()
                }) {
                    Circle()
                        .fill(Color(hex: "F1F5F9"))
                        .frame(width: 40, height: 40)
                        .overlay(
                            Image(systemName: "arrow.left")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        )
                }
                .buttonStyle(PlainButtonStyle())
                
                HStack(spacing: 8) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 16))
                        .foregroundColor(Color(hex: "64748B"))
                    
                    TextField("Search medicines, health products...", text: $queryText)
                        .font(.system(size: 14))
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                        .onSubmit {
                            commitSearch(queryText)
                        }
                    
                    if !queryText.isEmpty {
                        Button(action: {
                            queryText = ""
                            searchResults = []
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    
                    Image(systemName: "mic.fill")
                        .font(.system(size: 15))
                        .foregroundColor(Color(hex: "0284C7"))
                    
                    Image(systemName: "camera.fill")
                        .font(.system(size: 15))
                        .foregroundColor(Color(hex: "0284C7"))
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 10)
                .background(Color(hex: "F8FAFC"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.white)
            .shadow(color: Color.black.opacity(0.03), radius: 2, x: 0, y: 1)
            
            // CONTENT AREA
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 16) {
                    if queryText.trimmingCharacters(in: .whitespaces).isEmpty {
                        // IDLE STATE: RECENT SEARCHES & TRENDING SUGGESTIONS
                        idleSuggestionsView
                    } else if isLoading {
                        VStack(spacing: 12) {
                            Spacer().frame(height: 60)
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "0284C7")))
                                .scaleEffect(1.2)
                            Text("Searching catalog...")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                        .frame(maxWidth: .infinity)
                    } else if searchResults.isEmpty {
                        // EMPTY RESULTS STATE
                        emptyResultsView
                    } else {
                        // SEARCH RESULTS GRID
                        searchResultsView
                    }
                }
                .padding(16)
            }
        }
        .background(Color(hex: "F8FAFC").ignoresSafeArea())
        .sheet(item: $selectedProductForDetail) { product in
            ProductDetailSheet(product: product)
        }
        .onAppear {
            loadRecentSearches()
            if !initialQuery.isEmpty {
                queryText = initialQuery
                commitSearch(initialQuery)
            }
        }
        .onChange(of: queryText) { _, newQuery in
            if newQuery.count >= 2 {
                performLiveSearch(newQuery)
            } else if newQuery.isEmpty {
                searchResults = []
            }
        }
    }
    
    // MARK: - Idle Suggestions & Recent Searches View
    private var idleSuggestionsView: some View {
        VStack(alignment: .leading, spacing: 20) {
            // Recent Searches (if any)
            if !recentSearches.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        HStack(spacing: 6) {
                            Image(systemName: "clock.arrow.circlepath")
                                .font(.system(size: 14))
                                .foregroundColor(Color(hex: "0284C7"))
                            Text("Recent Searches")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                        Spacer()
                        Button(action: clearRecentSearches) {
                            Text("Clear")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(Color(hex: "DC2626"))
                        }
                    }
                    
                    VStack(spacing: 6) {
                        ForEach(recentSearches, id: \.self) { item in
                            Button(action: {
                                queryText = item
                                commitSearch(item)
                            }) {
                                HStack(spacing: 12) {
                                    Image(systemName: "clock")
                                        .font(.system(size: 13))
                                        .foregroundColor(Color(hex: "94A3B8"))
                                    Text(item)
                                        .font(.system(size: 14, weight: .medium))
                                        .foregroundColor(Color(hex: "1E293B"))
                                    Spacer()
                                    Image(systemName: "arrow.up.left")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color(hex: "CBD5E1"))
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 12)
                                .background(Color.white)
                                .cornerRadius(10)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
            }
            
            // Popular & Trending Searches
            VStack(alignment: .leading, spacing: 10) {
                HStack(spacing: 6) {
                    Image(systemName: "flame.fill")
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "EA580C"))
                    Text("Trending Searches")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                }
                
                FlowLayout(spacing: 8) {
                    ForEach(popularSearches, id: \.self) { term in
                        Button(action: {
                            queryText = term
                            commitSearch(term)
                        }) {
                            HStack(spacing: 5) {
                                Image(systemName: "magnifyingglass")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "0284C7"))
                                Text(term)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(Color(hex: "0F172A"))
                            }
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Color.white)
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
    }
    
    // MARK: - Search Results View
    private var searchResultsView: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Text("\(searchResults.count) matches found for '\(queryText)'")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
            }
            
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(searchResults) { product in
                    CommerceProductCard(
                        product: product,
                        onSelect: { prod in selectedProductForDetail = prod }
                    )
                }
            }
        }
    }
    
    // MARK: - Empty Results View
    private var emptyResultsView: some View {
        VStack(spacing: 14) {
            Spacer().frame(height: 40)
            Circle()
                .fill(Color(hex: "F0F9FF"))
                .frame(width: 72, height: 72)
                .overlay(
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(Color(hex: "0284C7"))
                )
            
            Text("No results for '\(queryText)'")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            Text("Try searching for generic salts like Paracetamol or branded medicines like Dolo 650.")
                .font(.system(size: 12))
                .foregroundColor(Color(hex: "64748B"))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
    
    // MARK: - Logic & Persistence
    private func commitSearch(_ term: String) {
        let trimmed = term.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        
        // Save to Recent Searches (Verbatim Android logic: take top 10 unique)
        var updated = [trimmed] + recentSearches.filter { $0.caseInsensitiveCompare(trimmed) != .orderedSame }
        if updated.count > 10 {
            updated = Array(updated.prefix(10))
        }
        recentSearches = updated
        saveRecentSearches(updated)
        
        performLiveSearch(trimmed)
    }
    
    private func performLiveSearch(_ query: String) {
        isLoading = true
        Task {
            let serverResults = await container.catalogRepository.searchProducts(query: query)
            let dtos: [ProductDto]
            if !serverResults.isEmpty {
                dtos = serverResults.map { $0.toProductDto() }
            } else {
                let normalized = query.lowercased()
                let all = container.catalogRepository.products
                let filtered = all.filter { p in
                    p.name.lowercased().contains(normalized) ||
                    p.sku.lowercased().contains(normalized) ||
                    (p.category?.lowercased().contains(normalized) ?? false)
                }
                dtos = filtered.map { $0.toProductDto() }
            }
            
            await MainActor.run {
                self.searchResults = dtos
                self.isLoading = false
            }
        }
    }
    
    private func loadRecentSearches() {
        let defaults = UserDefaults.standard
        if let raw = defaults.string(forKey: "\(prefsSuite)_\(prefsKey)") {
            self.recentSearches = raw.components(separatedBy: "||").filter { !$0.trimmingCharacters(in: .whitespaces).isEmpty }
        }
    }
    
    private func saveRecentSearches(_ list: [String]) {
        let defaults = UserDefaults.standard
        let joined = list.joined(separator: "||")
        defaults.set(joined, forKey: "\(prefsSuite)_\(prefsKey)")
    }
    
    private func clearRecentSearches() {
        recentSearches = []
        let defaults = UserDefaults.standard
        defaults.removeObject(forKey: "\(prefsSuite)_\(prefsKey)")
    }
}

// MARK: - Simple Flow Layout for Chips
private struct FlowLayout: Layout {
    var spacing: CGFloat = 8

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) -> CGSize {
        let width = proposal.width ?? 0
        var height: CGFloat = 0
        var x: CGFloat = 0
        var y: CGFloat = 0
        var maxHeightInRow: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > width && x > 0 {
                x = 0
                y += maxHeightInRow + spacing
                maxHeightInRow = 0
            }
            x += size.width + spacing
            maxHeightInRow = max(maxHeightInRow, size.height)
            height = max(height, y + maxHeightInRow)
        }
        return CGSize(width: width, height: height)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize, subviews: Subviews, cache: inout ()) {
        var x = bounds.minX
        var y = bounds.minY
        var maxHeightInRow: CGFloat = 0

        for subview in subviews {
            let size = subview.sizeThatFits(.unspecified)
            if x + size.width > bounds.maxX && x > bounds.minX {
                x = bounds.minX
                y += maxHeightInRow + spacing
                maxHeightInRow = 0
            }
            subview.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(size))
            x += size.width + spacing
            maxHeightInRow = max(maxHeightInRow, size.height)
        }
    }
}
