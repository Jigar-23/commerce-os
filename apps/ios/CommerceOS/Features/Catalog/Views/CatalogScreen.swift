import SwiftUI

public struct CatalogScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    
    @State private var searchText: String = ""
    @State private var filterColdChainOnly: Bool = false
    @State private var searchResults: [ProductDto] = []
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var searchTask: Task<Void, Never>? = nil
    @State private var selectedProductForDetail: ProductDto? = nil
    
    public init() {}
    
    public var body: some View {
        NavigationView {
            VStack(spacing: 12) {
                // Search Input
                HStack {
                    Image(systemName: "magnifyingglass")
                        .foregroundColor(.gray)
                    TextField("Search salt (e.g. Paracetamol, Atorvastatin)", text: $searchText)
                        .autocapitalization(.none)
                        .disableAutocorrection(true)
                    if !searchText.isEmpty {
                        Button(action: {
                            searchText = ""
                            loadProducts()
                        }) {
                            Image(systemName: "xmark.circle.fill")
                                .foregroundColor(.gray)
                        }
                    }
                }
                .padding(12)
                .background(Color(.systemGray6))
                .cornerRadius(10)
                .padding(.horizontal)
                
                // Filter Toggles
                HStack(spacing: 12) {
                    Toggle("Cold-Chain 2-8°C", isOn: $filterColdChainOnly)
                        .toggleStyle(ButtonToggleStyle(activeColor: Color(hex: "0284C7")))
                    Spacer()
                }
                .padding(.horizontal)
                
                // Generic Substitute Callout Banner
                if !searchText.isEmpty {
                    HStack {
                        Image(systemName: "sparkles")
                            .foregroundColor(.orange)
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Bio-Equivalent Generic Engine Active")
                                .font(.system(size: 12, weight: .bold))
                            Text("Save up to 80% with identical active therapeutic salts.")
                                .font(.system(size: 11))
                                .foregroundColor(.secondary)
                        }
                        Spacer()
                    }
                    .padding(10)
                    .background(Color.orange.opacity(0.12))
                    .cornerRadius(8)
                    .padding(.horizontal)
                }
                
                // Status / Error Banner
                if let err = errorMessage {
                    HStack {
                        Image(systemName: "info.circle.fill")
                            .foregroundColor(.orange)
                        Text(err)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .padding(8)
                    .background(Color.orange.opacity(0.08))
                    .cornerRadius(8)
                    .padding(.horizontal)
                }
                
                // Results List / Loading State
                if isLoading && searchResults.isEmpty {
                    VStack(spacing: 12) {
                        Spacer()
                        ProgressView("Loading dynamic medicines...")
                            .progressViewStyle(CircularProgressViewStyle())
                        Spacer()
                    }
                } else if filteredResults.isEmpty && !isLoading {
                    VStack(spacing: 12) {
                        Spacer()
                        Image(systemName: "cross.case")
                            .font(.system(size: 40))
                            .foregroundColor(.secondary)
                        Text("No matching medicines found")
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundColor(.secondary)
                        Text("Try searching by generic salt composition or brand name.")
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                        Spacer()
                    }
                    .padding()
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(filteredResults) { product in
                                VStack(spacing: 8) {
                                    // Main Product Row
                                    ProductListRow(product: product, onSelect: { prod in
                                        selectedProductForDetail = prod
                                    })
                                    
                                    // Generic Bio-Equivalent Alternative Card if Available
                                    if let generic = product.genericSubstitute {
                                        GenericComparisonCard(brandProduct: product, substitute: generic)
                                    }
                                }
                                .padding()
                                .background(Color(.systemBackground))
                                .cornerRadius(12)
                                .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
                                .padding(.horizontal)
                            }
                        }
                        .padding(.top, 4)
                        Spacer(minLength: 80)
                    }
                }
            }
            .navigationTitle("Salt & Medicine Catalog")
            .navigationBarTitleDisplayMode(.inline)
            .sheet(item: $selectedProductForDetail) { prod in
                ProductDetailSheet(product: prod)
            }
            .onAppear {
                loadProducts()
            }
            .onChange(of: searchText) { newQuery in
                performSearch(query: newQuery)
            }
        }
        .navigationViewStyle(.stack)
    }
    
    private var filteredResults: [ProductDto] {
        var list = searchResults
        if filterColdChainOnly {
            list = list.filter { $0.isColdChain == true }
        }
        return list
    }
    
    private func loadProducts() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                // 1. Fetch live dynamic products from backend /api/v1/catalog/products
                let res: CatalogProductsResponse = try await container.apiClient.get(endpoint: "/api/v1/catalog/products")
                let dynamicItems = res.content.map { $0.toProductDto() }
                
                await MainActor.run {
                    self.searchResults = dynamicItems
                    self.isLoading = false
                    self.errorMessage = nil
                }
            } catch {
                // 2. Offline fallback using persistent OfflineCatalogCache
                let cached = await container.offlineCache.getCachedProducts()
                let dynamicCached = cached.map { $0.toProductDto() }
                
                await MainActor.run {
                    if !dynamicCached.isEmpty {
                        self.searchResults = dynamicCached
                        self.errorMessage = "Offline Mode: Showing \(dynamicCached.count) cached catalog medicines."
                    } else {
                        self.errorMessage = "Unable to connect to live catalog server."
                    }
                    self.isLoading = false
                }
            }
        }
    }
    
    private func performSearch(query: String) {
        let trimmed = query.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            loadProducts()
            return
        }
        
        searchTask?.cancel()
        searchTask = Task {
            // Debounce 250ms for responsive keystroke handling
            try? await Task.sleep(nanoseconds: 250_000_000)
            if Task.isCancelled { return }
            
            await MainActor.run {
                self.isLoading = true
            }
            
            let serverMatches = await container.catalogRepository.searchProducts(query: trimmed)
            let matches = serverMatches.map { $0.toProductDto() }
            
            await MainActor.run {
                self.searchResults = matches
                self.isLoading = false
                self.errorMessage = matches.isEmpty ? "No medicines found matching '\(trimmed)'." : nil
            }
        }
    }
}

public struct ProductListRow: View {
    @EnvironmentObject private var configProvider: ClientConfigProvider
    let product: ProductDto
    var onSelect: ((ProductDto) -> Void)? = nil

    public init(product: ProductDto, onSelect: ((ProductDto) -> Void)? = nil) {
        self.product = product
        self.onSelect = onSelect
    }

    public var body: some View {
        HStack(alignment: .top, spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(product.name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                if let salt = product.saltComposition {
                    Text("Salt: \(salt)")
                        .font(.system(size: 12))
                        .foregroundColor(.secondary)
                }
                HStack(spacing: 6) {
                    Text("₹\(String(format: "%.2f", product.price))")
                        .font(.system(size: 16, weight: .black))
                        .foregroundColor(configProvider.currentConfig.theme.primaryColor)
                    if product.mrp > product.price {
                        Text("₹\(String(format: "%.2f", product.mrp))")
                            .font(.system(size: 12))
                            .strikethrough()
                            .foregroundColor(.secondary)
                    }
                }
            }
            .contentShape(Rectangle())
            .onTapGesture {
                onSelect?(product)
            }
            
            Spacer()
            
            QuickAddToCartButton(product: product)
        }
    }
}

public struct GenericComparisonCard: View {
    let brandProduct: ProductDto
    let substitute: GenericSubstituteDto
    
    public var body: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                HStack(spacing: 4) {
                    Image(systemName: "leaf.fill")
                        .foregroundColor(.green)
                        .font(.system(size: 11))
                    Text(substitute.genericName)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.green)
                }
                Text("Same active salt • ₹\(String(format: "%.2f", substitute.genericPrice)) vs ₹\(String(format: "%.2f", substitute.brandPrice))")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            
            Spacer()
            
            Text("Save \(substitute.savingsPercentage)%")
                .font(.system(size: 11, weight: .black))
                .padding(.horizontal, 8)
                .padding(.vertical, 4)
                .background(Color.green.opacity(0.2))
                .foregroundColor(.green)
                .cornerRadius(6)
        }
        .padding(8)
        .background(Color.green.opacity(0.06))
        .cornerRadius(8)
    }
}

public struct ButtonToggleStyle: ToggleStyle {
    let activeColor: Color
    public func makeBody(configuration: Configuration) -> some View {
        Button(action: { configuration.isOn.toggle() }) {
            HStack(spacing: 4) {
                Image(systemName: configuration.isOn ? "checkmark.circle.fill" : "circle")
                    .font(.system(size: 12))
                configuration.label
                    .font(.system(size: 12, weight: .semibold))
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(configuration.isOn ? activeColor.opacity(0.15) : Color(.systemGray6))
            .foregroundColor(configuration.isOn ? activeColor : .secondary)
            .cornerRadius(16)
        }
    }
}
