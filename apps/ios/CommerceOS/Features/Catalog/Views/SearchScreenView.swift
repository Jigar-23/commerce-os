import SwiftUI
import UIKit

/**
 * Universal Commerce OS Search Screen (1:1 Android Parity with SearchScreen.kt).
 * Sleek industrial-standard medical search experience matching Apollo 24|7 & Tata 1mg:
 * - Search bar with clear, mic, and camera actions
 * - Typo correction and synonym expansion via SearchTypoSynonymEngine
 * - Category filter chips: All, Medicines, Generic Substitutes, Personal Care, Devices, Ayurveda
 * - Promotional discount voucher banner (FLAT 27% OFF)
 * - Recent Searches with UserDefaults persistence
 * - Popular / Trending search pills & categories carousel
 * - Live dynamic product search with 2-column grid and product details screen
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
    @State private var selectedFilter: String = "All"
    @State private var typoCorrection: TypoCorrectionResult? = nil
    
    private let filterOptions = ["All", "Medicines", "Generic Substitutes", "Personal Care", "Devices", "Ayurveda"]
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
    
    private let popularCategories: [(name: String, icon: String, color: String)] = [
        ("Fever & Pain", "cross.case.fill", "0E675E"),
        ("Cough & Cold", "allergens", "0284C7"),
        ("Diabetes Care", "drop.fill", "DC2626"),
        ("Vitamins & Zinc", "sparkles", "F59E0B"),
        ("Digestion", "leaf.fill", "10B981")
    ]
    
    public init(initialQuery: String = "", onBack: @escaping () -> Void = {}) {
        self.initialQuery = initialQuery
        self.onBack = onBack
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // TOP SEARCH BAR
            searchHeaderView
            
            // CATEGORY FILTER CHIPS ROW
            filterChipsRow
            
            // CONTENT AREA
            ScrollView(showsIndicators: false) {
                VStack(alignment: .leading, spacing: 14) {
                    // Promotional Voucher Banner
                    promoVoucherBanner
                    
                    if queryText.trimmingCharacters(in: .whitespaces).isEmpty {
                        // IDLE STATE: RECENT SEARCHES & TRENDING SUGGESTIONS
                        idleSuggestionsView
                    } else if isLoading {
                        loadingView
                    } else if searchResults.isEmpty {
                        // EMPTY RESULTS STATE WITH TYPO SUGGESTIONS
                        emptyResultsView
                    } else {
                        // SEARCH RESULTS GRID
                        searchResultsView
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
        }
        .background(Color(hex: "F8FAFC").ignoresSafeArea())
        .fullScreenCover(item: $selectedProductForDetail) { product in
            ProductDetailScreen(product: product) {
                selectedProductForDetail = nil
            }
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
                typoCorrection = nil
            }
        }
    }
    
    // MARK: - Search Header
    private var searchHeaderView: some View {
        HStack(spacing: 10) {
            Button(action: {
                onBack()
                presentationMode.wrappedValue.dismiss()
            }) {
                Circle()
                    .fill(Color(hex: "F1F5F9"))
                    .frame(width: 38, height: 38)
                    .overlay(
                        Image(systemName: "arrow.left")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                    )
            }
            .buttonStyle(PlainButtonStyle())
            
            HStack(spacing: 8) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "64748B"))
                
                TextField("Search medicines, salts, health items...", text: $queryText)
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "0F172A"))
                    .accentColor(Color(hex: "0E675E"))
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                    .onSubmit {
                        commitSearch(queryText)
                    }
                
                if !queryText.isEmpty {
                    Button(action: {
                        queryText = ""
                        searchResults = []
                        typoCorrection = nil
                    }) {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 16))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                
                Image(systemName: "mic.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "0E675E"))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 9)
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
    }
    
    // MARK: - Filter Chips Row
    private var filterChipsRow: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(filterOptions, id: \.self) { opt in
                    let isSelected = selectedFilter == opt
                    Button(action: {
                        selectedFilter = opt
                    }) {
                        Text(opt)
                            .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                            .foregroundColor(isSelected ? .white : Color(hex: "0F172A"))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 6)
                            .background(isSelected ? Color(hex: "0E675E") : Color.white)
                            .cornerRadius(16)
                            .overlay(
                                RoundedRectangle(cornerRadius: 16)
                                    .stroke(isSelected ? Color(hex: "0E675E") : Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
        .background(Color.white)
        .overlay(Divider(), alignment: .bottom)
    }
    
    // MARK: - Promotional Voucher Banner
    private var promoVoucherBanner: some View {
        HStack(spacing: 10) {
            Circle()
                .fill(Color(hex: "0E675E").opacity(0.12))
                .frame(width: 36, height: 36)
                .overlay(
                    Image(systemName: "tag.fill")
                        .font(.system(size: 15))
                        .foregroundColor(Color(hex: "0E675E"))
                )
            
            VStack(alignment: .leading, spacing: 2) {
                Text("FLAT 27% OFF ON FIRST MEDICINE ORDER")
                    .font(.system(size: 11, weight: .heavy))
                    .foregroundColor(Color(hex: "0E675E"))
                    .tracking(0.5)
                Text("Use code HEALTH27 at checkout • Max discount ₹300")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: "64748B"))
            }
            
            Spacer()
        }
        .padding(12)
        .background(Color(hex: "ECFDF5"))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(hex: "A7F3D0"), lineWidth: 1)
        )
    }
    
    // MARK: - Idle Suggestions & Recent Searches View
    private var idleSuggestionsView: some View {
        VStack(alignment: .leading, spacing: 18) {
            // Popular Categories Carousel
            VStack(alignment: .leading, spacing: 10) {
                Text("Popular Healthcare Categories")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 10) {
                        ForEach(popularCategories, id: \.name) { cat in
                            Button(action: {
                                queryText = cat.name
                                commitSearch(cat.name)
                            }) {
                                VStack(spacing: 6) {
                                    Circle()
                                        .fill(Color(hex: cat.color).opacity(0.12))
                                        .frame(width: 50, height: 50)
                                        .overlay(
                                            Image(systemName: cat.icon)
                                                .font(.system(size: 20))
                                                .foregroundColor(Color(hex: cat.color))
                                        )
                                    Text(cat.name)
                                        .font(.system(size: 10.5, weight: .semibold))
                                        .foregroundColor(Color(hex: "0F172A"))
                                        .lineLimit(1)
                                }
                                .frame(width: 82)
                                .padding(.vertical, 8)
                                .background(Color.white)
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
            }
            
            // Recent Searches (if any)
            if !recentSearches.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        HStack(spacing: 6) {
                            Image(systemName: "clock.arrow.circlepath")
                                .font(.system(size: 13))
                                .foregroundColor(Color(hex: "0E675E"))
                            Text("Recent Searches")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                        Spacer()
                        Button(action: clearRecentSearches) {
                            Text("Clear All")
                                .font(.system(size: 12, weight: .bold))
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
                                        .font(.system(size: 13.5, weight: .medium))
                                        .foregroundColor(Color(hex: "1E293B"))
                                    Spacer()
                                    Image(systemName: "arrow.up.left")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color(hex: "CBD5E1"))
                                }
                                .padding(.horizontal, 14)
                                .padding(.vertical, 11)
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
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "EA580C"))
                    Text("Top Medicine Searches")
                        .font(.system(size: 14, weight: .bold))
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
                                    .foregroundColor(Color(hex: "0E675E"))
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
            // Typo Correction Banner if any
            if let tc = typoCorrection, tc.wasCorrected {
                HStack(spacing: 6) {
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "0E675E"))
                    Text("Showing results for ")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))
                    Text(tc.correctedQuery)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                }
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color(hex: "ECFDF5"))
                .cornerRadius(6)
            }
            
            HStack {
                Text("\(searchResults.count) medicines found for '\(queryText)'")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
            }
            
            LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                ForEach(searchResults) { product in
                    CommerceProductCard(
                        product: product,
                        variant: .grid,
                        onSelect: { prod in selectedProductForDetail = prod }
                    )
                }
            }
        }
    }
    
    // MARK: - Empty Results View
    private var emptyResultsView: some View {
        VStack(spacing: 14) {
            Spacer().frame(height: 30)
            Circle()
                .fill(Color(hex: "ECFDF5"))
                .frame(width: 72, height: 72)
                .overlay(
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 32, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                )
            
            Text("No medicines found for '\(queryText)'")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            Text("Try searching for generic salts like Paracetamol or branded medicines like Dolo 650.")
                .font(.system(size: 12))
                .foregroundColor(Color(hex: "64748B"))
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
            
            // Zero-result suggestions
            let suggestions = SearchTypoSynonymEngine.getZeroResultSuggestions(query: queryText)
            VStack(spacing: 8) {
                Text("Suggested Searches:")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: "64748B"))
                
                HStack(spacing: 8) {
                    ForEach(suggestions, id: \.self) { sug in
                        Button(action: {
                            queryText = sug
                            commitSearch(sug)
                        }) {
                            Text(sug)
                                .font(.system(size: 11, weight: .semibold))
                                .foregroundColor(Color(hex: "0E675E"))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color.white)
                                .cornerRadius(12)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(Color(hex: "0E675E"), lineWidth: 1)
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
            .padding(.top, 8)
            
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
    
    // MARK: - Loading Skeleton
    private var loadingView: some View {
        VStack(spacing: 12) {
            Spacer().frame(height: 40)
            ProgressView()
                .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "0E675E")))
                .scaleEffect(1.2)
            Text("Searching catalog...")
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(Color(hex: "64748B"))
        }
        .frame(maxWidth: .infinity)
    }
    
    // MARK: - Logic & Persistence
    private func commitSearch(_ term: String) {
        let trimmed = term.trimmingCharacters(in: .whitespaces)
        guard !trimmed.isEmpty else { return }
        
        let typo = SearchTypoSynonymEngine.correctTypo(query: trimmed)
        self.typoCorrection = typo
        
        var updated = [trimmed] + recentSearches.filter { $0.caseInsensitiveCompare(trimmed) != .orderedSame }
        if updated.count > 10 {
            updated = Array(updated.prefix(10))
        }
        recentSearches = updated
        saveRecentSearches(updated)
        
        performLiveSearch(typo.correctedQuery)
    }
    
    private func performLiveSearch(_ query: String) {
        isLoading = true
        let typo = SearchTypoSynonymEngine.correctTypo(query: query)
        self.typoCorrection = typo
        let effectiveQuery = typo.correctedQuery
        
        Task {
            let serverResults = await container.catalogRepository.searchProducts(query: effectiveQuery)
            let dtos: [ProductDto]
            if !serverResults.isEmpty {
                dtos = serverResults.map { $0.toProductDto() }
            } else {
                let normalized = effectiveQuery.lowercased()
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
