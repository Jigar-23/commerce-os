import SwiftUI

public struct HomeScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @ObservedObject private var serverConfig = ServerEnvironmentConfig.shared
    @State private var selectedCategory: String = "All"
    @State private var products: [ProductDto] = []
    @State private var buyAgainProducts: [ProductDto] = []
    @State private var homeFeed: HomeFeedResponse? = nil
    @State private var isLoading: Bool = false
    @State private var showServerSettingsSheet: Bool = false
    let onOpenCatalog: () -> Void
    
    @State private var categories: [String] = ["All"]
    
    public init(onOpenCatalog: @escaping () -> Void = {}) {
        self.onOpenCatalog = onOpenCatalog
    }
    
    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Top Location & SLA Bar (Matching Android HomeScreen.kt)
                    HStack {
                        VStack(alignment: .leading, spacing: 2) {
                            HStack(spacing: 4) {
                                Text("Delivery in")
                                    .font(.system(size: 19, weight: .black))
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                Text("11 mins")
                                    .font(.system(size: 19, weight: .black))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                            }
                            
                            HStack(spacing: 4) {
                                Image(systemName: "mappin.and.ellipse")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                Text("Flat 402, Green Glen Heights, Bellandur")
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.secondary)
                                    .lineLimit(1)
                                Image(systemName: "chevron.down")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        Spacer()
                        
                        Button(action: { showServerSettingsSheet = true }) {
                            ZStack {
                                Circle()
                                    .fill(Color(.systemGray6))
                                    .frame(width: 40, height: 40)
                                    .overlay(Circle().stroke(CommerceOSTheme.Colors.border, lineWidth: 1))
                                Image(systemName: "person.crop.circle.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(CommerceOSTheme.Colors.brandSecondary)
                            }
                        }
                    }
                    .padding(.horizontal)
                    .padding(.top, 4)
                    
                    // Universal Search Bar (Matching Android UniversalSearchBar)
                    Button(action: onOpenCatalog) {
                        HStack(spacing: 10) {
                            Image(systemName: "magnifyingglass")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                            Text("Search \"fresh milk, medicines, fruits, essentials...\"")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Color(.placeholderText))
                            Spacer()
                            Image(systemName: "mic.fill")
                                .font(.system(size: 14))
                                .foregroundColor(.secondary)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 12)
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
                        )
                        .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 2)
                        .padding(.horizontal)
                    }
                    
                    // Quick Categories Rail (Matching Android 8-tile QuickCategoriesRail)
                    VStack(alignment: .leading, spacing: 10) {
                        Text("Explore Categories")
                            .font(.system(size: 14, weight: .black))
                            .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                            .padding(.horizontal)
                        
                        LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                            QuickCategoryTileView(icon: "cart.fill", title: "Groceries", bgColor: Color(hex: "E8F5E9"), iconColor: Color(hex: "16A34A"), onTap: onOpenCatalog)
                            QuickCategoryTileView(icon: "cross.case.fill", title: "Medicines", bgColor: Color(hex: "E0F2FE"), iconColor: Color(hex: "0284C7"), onTap: onOpenCatalog)
                            QuickCategoryTileView(icon: "star.fill", title: "Top Deals", bgColor: Color(hex: "FEF3C7"), iconColor: Color(hex: "D97706"), onTap: onOpenCatalog)
                            QuickCategoryTileView(icon: "house.fill", title: "Essentials", bgColor: Color(hex: "EDE9FE"), iconColor: Color(hex: "7C3AED"), onTap: onOpenCatalog)
                            QuickCategoryTileView(icon: "sparkles", title: "Personal", bgColor: Color(hex: "FCE7F3"), iconColor: Color(hex: "DB2777"), onTap: onOpenCatalog)
                            QuickCategoryTileView(icon: "bolt.fill", title: "Electronics", bgColor: Color(hex: "CCFBF1"), iconColor: Color(hex: "0D9488"), onTap: onOpenCatalog)
                            QuickCategoryTileView(icon: "wrench.fill", title: "Repairs", bgColor: Color(hex: "FFEDD5"), iconColor: Color(hex: "EA580C"), onTap: onOpenCatalog)
                            QuickCategoryTileView(icon: "building.2.fill", title: "Local Stores", bgColor: Color(hex: "F1F5F9"), iconColor: Color(hex: "475569"), onTap: onOpenCatalog)
                        }
                        .padding(.horizontal)
                    }
                    
                    // Hero Express Delivery Banner (Emerald Quick-Commerce Gradient)
                    HStack {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("10-MINUTE QUICK COMMERCE")
                                .font(.system(size: 11, weight: .black))
                                .foregroundColor(.white.opacity(0.9))
                            Text("Commerce OS • Express Delivery")
                                .font(.system(size: 15, weight: .black))
                                .foregroundColor(.white)
                            Text("Fresh groceries & essentials at your doorstep in minutes.")
                                .font(.system(size: 11))
                                .foregroundColor(Color.white.opacity(0.85))
                        }
                        Spacer()
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 32))
                            .foregroundColor(Color(hex: "E9BE3A"))
                    }
                    .padding(16)
                    .background(
                        LinearGradient(
                            colors: [CommerceOSTheme.Colors.brandPrimary, CommerceOSTheme.Colors.brandPrimaryDark],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                    .cornerRadius(14)
                    .shadow(color: CommerceOSTheme.Colors.brandPrimary.opacity(0.25), radius: 8, x: 0, y: 4)
                    .padding(.horizontal)
                    
                    // Category Filter Chips
                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(spacing: 8) {
                            ForEach(categories, id: \.self) { cat in
                                Button(action: { selectedCategory = cat }) {
                                    Text(cat)
                                        .font(.system(size: 13, weight: .bold))
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 8)
                                        .background(selectedCategory == cat ? CommerceOSTheme.Colors.brandPrimaryDark : Color.white)
                                        .foregroundColor(selectedCategory == cat ? .white : CommerceOSTheme.Colors.sushiInk)
                                        .cornerRadius(20)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 20)
                                                .stroke(selectedCategory == cat ? Color.clear : CommerceOSTheme.Colors.border, lineWidth: 1)
                                        )
                                }
                            }
                        }
                        .padding(.horizontal)
                    }
                    
                    // Buy Again Section (Matching Android)
                    if !buyAgainProducts.isEmpty {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Buy Again")
                                .font(.system(size: 16, weight: .bold))
                                .padding(.horizontal)
                            ScrollView(.horizontal, showsIndicators: false) {
                                HStack(spacing: 12) {
                                    ForEach(buyAgainProducts) { product in
                                        BuyAgainCard(product: product)
                                    }
                                }
                                .padding(.horizontal)
                            }
                        }
                    }
                    
                    // Trending Products Section
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Popular Products & Essentials")
                                .font(.system(size: 18, weight: .bold))
                            Spacer()
                        }
                        .padding(.horizontal)
                        
                        if isLoading && products.isEmpty {
                            VStack(spacing: 12) {
                                Spacer()
                                ProgressView("Loading dynamic catalog...")
                                    .padding(.vertical, 24)
                                Spacer()
                            }
                            .frame(maxWidth: .infinity)
                        } else if filteredProducts.isEmpty && !isLoading {
                            VStack(spacing: 8) {
                                Image(systemName: "tray")
                                    .font(.system(size: 32))
                                    .foregroundColor(.secondary)
                                Text("No medicines found in \(selectedCategory)")
                                    .font(.system(size: 14))
                                    .foregroundColor(.secondary)
                            }
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 32)
                        } else {
                            LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 14) {
                                ForEach(filteredProducts) { product in
                                    ProductCard(product: product)
                                }
                            }
                            .padding(.horizontal)
                        }
                    }
                    
                    Spacer(minLength: 80)
                }
                .padding(.top, 8)
            }
            .navigationTitle(configProvider.currentConfig.terminology.appTitle)
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarLeading) {
                    Button(action: { showServerSettingsSheet = true }) {
                        HStack(spacing: 4) {
                            Circle()
                                .fill(serverConfig.isServerConnected ? Color.green : Color.red)
                                .frame(width: 8, height: 8)
                            Text(serverConfig.isServerConnected ? (serverConfig.latencyMs != nil ? "\(serverConfig.latencyMs!)ms" : "Online") : "Offline")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(serverConfig.isServerConnected ? .green : .red)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(Color(.systemGray6))
                        .cornerRadius(12)
                    }
                }
                ToolbarItem(placement: .navigationBarTrailing) {
                    HStack(spacing: 4) {
                        Image(systemName: "mappin.circle.fill")
                            .foregroundColor(.green)
                        Text("10m Express")
                            .font(.system(size: 12, weight: .medium))
                    }
                }
            }
            .sheet(isPresented: $showServerSettingsSheet) {
                ServerSettingsSheet()
            }
            .task {
                loadProducts()
            }
        }
    }
    
    private var filteredProducts: [ProductDto] {
        if selectedCategory == "All" { return products }
        return products.filter { $0.category.localizedCaseInsensitiveContains(selectedCategory) }
    }
    
    private func loadProducts() {
        isLoading = true
        Task {
            // 1. Primary: Server-authored HomeFeedResponse matching Android
            do {
                let feed: HomeFeedResponse = try await container.apiClient.get(endpoint: "/api/v1/catalog/home-feed?customerId=cust_123")
                var dynamicProducts: [ProductDto] = []
                if let popular = feed.popular, !popular.isEmpty {
                    dynamicProducts.append(contentsOf: popular.map { $0.toProductDto() })
                }
                if let topDeals = feed.topDeals, !topDeals.isEmpty {
                    for item in topDeals {
                        let dto = item.toProductDto()
                        if !dynamicProducts.contains(where: { $0.id == dto.id }) {
                            dynamicProducts.append(dto)
                        }
                    }
                }
                if let generalFeed = feed.feed, !generalFeed.isEmpty {
                    for item in generalFeed {
                        let dto = item.toProductDto()
                        if !dynamicProducts.contains(where: { $0.id == dto.id }) {
                            dynamicProducts.append(dto)
                        }
                    }
                }

                var dynamicCategories = ["All"]
                if let cats = feed.categories {
                    dynamicCategories.append(contentsOf: cats.map { $0.title })
                }

                let buyAgain = feed.buyAgain?.map { $0.toProductDto() } ?? []

                await MainActor.run {
                    self.homeFeed = feed
                    self.buyAgainProducts = buyAgain
                    if !dynamicProducts.isEmpty {
                        self.products = dynamicProducts
                    }
                    if dynamicCategories.count > 1 {
                        self.categories = dynamicCategories
                    }
                    self.isLoading = false
                }
                return
            } catch {
                print("[HomeScreen] HomeFeed error: \(error), attempting fallback to /products")
            }

            // 2. Secondary fallback: /api/v1/catalog/products
            do {
                let res: CatalogProductsResponse = try await container.apiClient.get(endpoint: "/api/v1/catalog/products")
                let catRes: CatalogCategoriesResponse = try await container.apiClient.get(endpoint: "/api/v1/catalog/categories")
                let dynamicProducts = res.content.map { $0.toProductDto() }
                let dynamicCategories = ["All"] + catRes.content.map { $0.name }
                await MainActor.run {
                    self.products = dynamicProducts
                    if dynamicCategories.count > 1 {
                        self.categories = dynamicCategories
                    }
                    self.isLoading = false
                }
            } catch {
                print("[HomeScreen] Products error: \(error), falling back to offline cache")
                // 3. Tertiary fallback: offline cached catalog
                let cached = await container.offlineCache.getCachedProducts()
                let mapped = cached.map { $0.toProductDto() }
                await MainActor.run {
                    if !mapped.isEmpty {
                        self.products = mapped
                    }
                    self.isLoading = false
                }
            }
        }
    }
}

// MARK: - Quick Commerce Category Tile
public struct QuickCategoryTileView: View {
    let icon: String
    let title: String
    let bgColor: Color
    let iconColor: Color
    let onTap: () -> Void
    
    public init(icon: String, title: String, bgColor: Color, iconColor: Color, onTap: @escaping () -> Void) {
        self.icon = icon
        self.title = title
        self.bgColor = bgColor
        self.iconColor = iconColor
        self.onTap = onTap
    }
    
    public var body: some View {
        Button(action: onTap) {
            VStack(spacing: 6) {
                ZStack {
                    Circle()
                        .fill(bgColor)
                        .frame(width: 38, height: 38)
                    Image(systemName: icon)
                        .font(.system(size: 18, weight: .semibold))
                        .foregroundColor(iconColor)
                }
                Text(title)
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity)
            .frame(height: 84)
            .background(Color.white)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Quick Commerce Interactive Add / Stepper Button
public struct QuickAddToCartButton: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    let product: ProductDto
    var isReorderCard: Bool = false
    var compact: Bool = false

    private var quantity: Int {
        cartStore.items[product.sku]?.quantity ?? 0
    }

    private var isOutOfStock: Bool {
        !product.inStock
    }

    public init(product: ProductDto, isReorderCard: Bool = false, compact: Bool = false) {
        self.product = product
        self.isReorderCard = isReorderCard
        self.compact = compact
    }

    public var body: some View {
        if isReorderCard || isOutOfStock || quantity <= 0 {
            Button(action: {
                if !isOutOfStock {
                    cartStore.add(product: product)
                }
            }) {
                HStack(spacing: 4) {
                    if isReorderCard {
                        Text("REORDER")
                            .font(.system(size: compact ? 10 : 12, weight: .black))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                    } else if isOutOfStock {
                        Text("OUT OF STOCK")
                            .font(.system(size: compact ? 9 : 11, weight: .bold))
                            .foregroundColor(.secondary)
                    } else {
                        Text("ADD")
                            .font(.system(size: compact ? 11 : 13, weight: .black))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                        Image(systemName: "plus")
                            .font(.system(size: compact ? 9 : 11, weight: .bold))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                    }
                }
                .padding(.horizontal, compact ? 8 : 12)
                .padding(.vertical, compact ? 4 : 6)
                .background(isOutOfStock ? Color(.systemGray6) : CommerceOSTheme.Colors.brandPrimarySoft)
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isOutOfStock ? Color.clear : CommerceOSTheme.Colors.brandPrimaryDark, lineWidth: 1)
                )
            }
            .disabled(isOutOfStock)
        } else {
            HStack(spacing: 8) {
                Button(action: {
                    cartStore.decrement(sku: product.sku)
                }) {
                    Text("−")
                        .font(.system(size: compact ? 13 : 15, weight: .black))
                        .foregroundColor(.white)
                        .frame(width: compact ? 18 : 22, height: compact ? 18 : 22)
                }

                Text("\(quantity)")
                    .font(.system(size: compact ? 11 : 13, weight: .black))
                    .foregroundColor(.white)

                Button(action: {
                    cartStore.increment(sku: product.sku)
                }) {
                    Text("+")
                        .font(.system(size: compact ? 13 : 15, weight: .black))
                        .foregroundColor(.white)
                        .frame(width: compact ? 18 : 22, height: compact ? 18 : 22)
                }
            }
            .padding(.horizontal, compact ? 4 : 8)
            .padding(.vertical, compact ? 2 : 4)
            .background(CommerceOSTheme.Colors.brandPrimaryDark)
            .cornerRadius(8)
            .shadow(color: CommerceOSTheme.Colors.brandPrimaryDark.opacity(0.3), radius: 3, x: 0, y: 1)
        }
    }
}

public struct BuyAgainCard: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    let product: ProductDto

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(product.name)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                .lineLimit(2)
            
            Spacer(minLength: 4)
            
            HStack {
                Text("$\(String(format: "%.2f", product.price))")
                    .font(.system(size: 13, weight: .black))
                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                Spacer()
                QuickAddToCartButton(product: product, compact: true)
            }
        }
        .padding(10)
        .frame(width: 140, height: 110)
        .background(Color(.systemBackground))
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 1)
    }
}

public struct ProductCard: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    let product: ProductDto
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Badges
            HStack {
                if product.isColdChain == true {
                    HStack(spacing: 2) {
                        Image(systemName: "snowflake")
                            .font(.system(size: 8))
                        Text("2-8°C")
                            .font(.system(size: 9, weight: .semibold))
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(hex: "E0F2FE"))
                    .foregroundColor(Color(hex: "0284C7"))
                    .cornerRadius(4)
                }
                Spacer()
            }
            
            Text(product.name)
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                .lineLimit(2)
            
            if let salt = product.saltComposition {
                Text(salt)
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
                    .lineLimit(1)
            }
            
            if let gen = product.genericSubstitute {
                HStack(spacing: 3) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.system(size: 8))
                    Text("Save \(gen.savingsPercentage)% Generic")
                        .font(.system(size: 10, weight: .bold))
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 2)
                .background(CommerceOSTheme.Colors.brandPrimarySoft)
                .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                .cornerRadius(4)
            }
            
            Spacer(minLength: 4)
            
            // Price & Add Button
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text("$\(String(format: "%.2f", product.price))")
                        .font(.system(size: 15, weight: .black))
                        .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                    if product.mrp > product.price {
                        Text("$\(String(format: "%.2f", product.mrp))")
                            .font(.system(size: 11))
                            .strikethrough()
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
                
                QuickAddToCartButton(product: product)
            }
        }
        .padding(12)
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 2)
    }
}

public struct CatalogProductsResponse: Codable {
    public let content: [ProductResponseItem]

    public init(content: [ProductResponseItem]) {
        self.content = content
    }

    public init(from decoder: Decoder) throws {
        if let container = try? decoder.container(keyedBy: CodingKeys.self),
           let items = try? container.decode([ProductResponseItem].self, forKey: .content) {
            self.content = items
            return
        }
        let singleContainer = try decoder.singleValueContainer()
        self.content = try singleContainer.decode([ProductResponseItem].self)
    }

    private enum CodingKeys: String, CodingKey {
        case content
    }
}

public struct ProductResponseItem: Codable {
    public let id: String
    public let sku: String
    public let name: String
    public let brandName: String?
    public let mrp: Double?
    public let price: Double
    public let discountedPrice: Double?
    public let therapeuticCategory: String?
    public let category: String?
    public let rxRequirement: String?
    public let coldChainRequired: Bool?
    public let inStock: Bool?
    
    public func toProductDto() -> ProductDto {
        let isRx = (rxRequirement ?? "").contains("RX") || (rxRequirement ?? "").contains("SCHEDULE")
        let isCold = coldChainRequired ?? false
        let sellPrice = discountedPrice ?? price
        let effectiveMrp = mrp ?? price
        let cat = category ?? therapeuticCategory ?? "General"
        
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
            brand: brandName,
            mrp: effectiveMrp,
            price: sellPrice,
            category: cat,
            saltComposition: name,
            requiresPrescription: isRx,
            isColdChain: isCold,
            inStock: inStock ?? true,
            genericSubstitute: genericSub
        )
    }
}

public struct CatalogCategoriesResponse: Codable {
    public let content: [CategoryItem]

    public init(content: [CategoryItem]) {
        self.content = content
    }

    public init(from decoder: Decoder) throws {
        if let container = try? decoder.container(keyedBy: CodingKeys.self),
           let items = try? container.decode([CategoryItem].self, forKey: .content) {
            self.content = items
            return
        }
        let singleContainer = try decoder.singleValueContainer()
        self.content = try singleContainer.decode([CategoryItem].self)
    }

    private enum CodingKeys: String, CodingKey {
        case content
    }

    public struct CategoryItem: Codable {
        public let id: String
        public let name: String
    }
}
