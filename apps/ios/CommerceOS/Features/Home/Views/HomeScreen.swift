import SwiftUI

public struct HomeScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @EnvironmentObject private var addressRepository: AddressRepository
    @ObservedObject private var serverConfig = ServerEnvironmentConfig.shared
    @State private var selectedCategory: String = "All"
    @State private var products: [ProductDto] = []
    @State private var buyAgainProducts: [ProductDto] = []
    @State private var homeFeed: HomeFeedResponse? = nil
    @State private var isLoading: Bool = false
    private enum HomeSheetDestination: Identifiable {
        case account
        case addressSelection
        case addAddressFlow
        case serverSettings
        case productDetail(ProductDto)
        case search

        var id: String {
            switch self {
            case .account: return "account"
            case .addressSelection: return "addressSelection"
            case .addAddressFlow: return "addAddressFlow"
            case .serverSettings: return "serverSettings"
            case .productDetail(let prod): return "product_\(prod.id)"
            case .search: return "search"
            }
        }
    }
    @State private var activeSheet: HomeSheetDestination? = nil
    @State private var searchHintIndex: Int = 0
    let onOpenCatalog: () -> Void
    
    @State private var categories: [String] = ["All", "Pain & Fever", "Cold & Cough", "Diabetes", "Antibiotics", "Vitamins", "Acidity & Gas"]
    
    private let searchHints = [
        "Search medicines, e.g. Dolo 650...",
        "Search for Paracetamol, Crocin...",
        "Search for Augmentin 625 Duo...",
        "Search for Benadryl Cough syrup...",
        "Search for Glycomet, Insulin...",
        "Search for Pan 40, Digene..."
    ]

    private struct MedicalCategoryItem: Identifiable {
        let id: String
        let name: String
        let query: String
        let imageUrl: String
    }

    private let medicineCategories: [MedicalCategoryItem] = [
        MedicalCategoryItem(
            id: "pain_fever",
            name: "Pain & Fever",
            query: "Pain & Fever",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg"
        ),
        MedicalCategoryItem(
            id: "cold_cough",
            name: "Cold & Cough",
            query: "Cold & Cough",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg"
        ),
        MedicalCategoryItem(
            id: "diabetes",
            name: "Diabetes",
            query: "Diabetes",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg"
        ),
        MedicalCategoryItem(
            id: "antibiotics",
            name: "Antibiotics",
            query: "Antibiotics",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg"
        ),
        MedicalCategoryItem(
            id: "vitamins",
            name: "Vitamins",
            query: "Vitamins",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg"
        ),
        MedicalCategoryItem(
            id: "acidity_gas",
            name: "Acidity & Gas",
            query: "Acidity & Gas",
            imageUrl: "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg"
        )
    ]
    
    public init(onOpenCatalog: @escaping () -> Void = {}) {
        self.onOpenCatalog = onOpenCatalog
    }
    
    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 14) {
                // 1. Fixed First-Viewport Chrome: Delivery Address Widget (Verbatim Android HomeScreen.kt)
                deliveryAddressWidget
                
                // 2. Universal Search Bar (Verbatim Android UniversalSearchBar)
                universalSearchBar
                
                // 3. Medicine Popular Categories Rail (Verbatim Android MedicineCategoryRail)
                medicineCategoryRail
                
                // 4. Hero Campaign Banner (Verbatim Android HeroCampaignWidget)
                heroCampaignWidget
                
                // 5. Category Filter Chips
                categoryFilterChips
                
                // 6. Buy Again Shelf (if any)
                if !buyAgainProducts.isEmpty {
                    buyAgainShelf
                }
                
                // 7. Trending & Popular Products Section
                productsSection
                
                Spacer(minLength: 90)
            }
            .padding(.top, 4)
        }
        .background(Color(hex: "F4F5F7").ignoresSafeArea())
        .sheet(item: $activeSheet) { destination in
            switch destination {
            case .account:
                AccountScreen()
            case .addressSelection:
                AddressSelectionBottomSheet(
                    addressRepository: addressRepository,
                    customerId: container.customerSession?.customerId ?? "",
                    onDismiss: { activeSheet = nil },
                    onAddNewAddress: {
                        activeSheet = .addAddressFlow
                    }
                )
            case .addAddressFlow:
                AddAddressFlowView(onSaveAddress: { newAddr in
                    addressRepository.selectAddress(newAddr)
                    activeSheet = nil
                })
            case .serverSettings:
                ServerSettingsSheet()
            case .productDetail(let product):
                ProductDetailSheet(product: product)
            case .search:
                SearchScreenView(onBack: {
                    activeSheet = nil
                })
            }
        }
        .task {
            loadProducts()
        }
        .task {
            while !Task.isCancelled {
                try? await Task.sleep(nanoseconds: 3_000_000_000)
                withAnimation {
                    searchHintIndex = (searchHintIndex + 1) % searchHints.count
                }
            }
        }
    }
    
    // MARK: - Delivery Address Widget (Matching Android DeliveryAddressWidget)
    private var deliveryAddressWidget: some View {
        HStack(alignment: .center, spacing: 10) {
            Button(action: { activeSheet = .addressSelection }) {
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 4) {
                        Image(systemName: "mappin.and.ellipse")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Color(hex: "059669"))
                        Text(addressRepository.locationHeaderTitle)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                            .lineLimit(1)
                        Image(systemName: "chevron.down")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))
                    }

                    HStack(spacing: 4) {
                        Text("Delivering in")
                            .font(.system(size: 12, weight: .regular))
                            .foregroundColor(Color(hex: "64748B"))
                        Text("\(addressRepository.calculatedEtaMinutes) mins")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "059669"))
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())

            Spacer()

            Button(action: { activeSheet = .account }) {
                ZStack {
                    Circle()
                        .fill(Color(hex: "ECFDF5"))
                        .frame(width: 38, height: 38)
                        .overlay(
                            Circle()
                                .stroke(Color(hex: "10B981"), lineWidth: 1.2)
                        )
                    
                    if let name = container.customerSession?.name, let initial = name.first {
                        Text(String(initial).uppercased())
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(Color(hex: "065F46"))
                    } else {
                        Image(systemName: "person.fill")
                            .font(.system(size: 16))
                            .foregroundColor(Color(hex: "065F46"))
                    }
                }
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 14)
        .padding(.top, 4)
    }

    // MARK: - Universal Search Bar (Matching Android UniversalSearchBar)
    private var universalSearchBar: some View {
        Button(action: {
            activeSheet = .search
        }) {
            HStack(spacing: 10) {
                Image(systemName: "magnifyingglass")
                    .font(.system(size: 16))
                    .foregroundColor(Color(hex: "64748B"))
                
                Text(searchHints[searchHintIndex])
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: "64748B"))
                    .lineLimit(1)
                    .id("search_hint_\(searchHintIndex)")
                    .transition(.opacity)
                    .animation(.easeInOut(duration: 0.3), value: searchHintIndex)
                
                Spacer()
                
                Image(systemName: "mic.fill")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "64748B"))
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 12)
            .background(Color(hex: "F8FAFC"))
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
            .padding(.horizontal, 14)
        }
        .buttonStyle(PlainButtonStyle())
    }

    // MARK: - Medicine Category Rail (Matching Android MedicineCategoryRail)
    private var medicineCategoryRail: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Popular Categories")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
                .padding(.horizontal, 14)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(medicineCategories) { cat in
                        Button(action: {
                            selectedCategory = cat.name
                        }) {
                            VStack(spacing: 6) {
                                ZStack {
                                    Circle()
                                        .fill(Color(hex: "F8FAFC"))
                                        .frame(width: 46, height: 46)
                                        .overlay(Circle().stroke(Color(hex: "E2E8F0"), lineWidth: 0.5))

                                    if let url = URL(string: cat.imageUrl) {
                                        AsyncImage(url: url) { phase in
                                            switch phase {
                                            case .success(let img):
                                                img.resizable()
                                                    .aspectRatio(contentMode: .fit)
                                                    .clipShape(Circle())
                                                    .padding(4)
                                            default:
                                                Image(systemName: "pills.fill")
                                                    .font(.system(size: 18))
                                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                            }
                                        }
                                        .frame(width: 46, height: 46)
                                    }
                                }

                                Text(cat.name)
                                    .font(.system(size: 11, weight: .medium))
                                    .foregroundColor(Color(hex: "1E293B"))
                                    .lineLimit(1)
                                    .frame(maxWidth: .infinity)
                            }
                            .padding(.vertical, 8)
                            .padding(.horizontal, 6)
                            .frame(width: 96)
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(selectedCategory == cat.name ? Color(hex: "059669") : Color(hex: "E2E8F0"), lineWidth: selectedCategory == cat.name ? 1.5 : 1)
                            )
                            .shadow(color: Color.black.opacity(0.02), radius: 1, x: 0, y: 0.5)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 14)
            }
        }
    }

    // MARK: - Hero Campaign Banner (Matching Android HeroCampaignWidget)
    private var heroCampaignWidget: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 16)
                .fill(
                    LinearGradient(
                        colors: [Color(hex: "0F172A"), Color(hex: "0B101D")],
                        startPoint: .top,
                        endPoint: .bottom
                    )
                )
                .frame(height: 190)

            HStack {
                Spacer()
                Image(systemName: "cross.case.fill")
                    .font(.system(size: 90))
                    .foregroundColor(Color(hex: "10B981").opacity(0.12))
                    .padding(.trailing, 24)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("SUPER SAVER")
                    .font(.system(size: 10, weight: .black))
                    .foregroundColor(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(Color(hex: "059669"))
                    .cornerRadius(4)

                Text("10-Minute Express Delivery")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(.white)
                    .lineLimit(2)

                Text("Certified medicines, daily essentials & baby care at your doorstep.")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "94A3B8"))
                    .lineLimit(2)

                Button(action: onOpenCatalog) {
                    Text("Explore Now")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(.white)
                        .padding(.horizontal, 16)
                        .padding(.vertical, 8)
                        .background(Color(hex: "059669"))
                        .cornerRadius(8)
                }
                .buttonStyle(PlainButtonStyle())
                .padding(.top, 4)
            }
            .padding(18)
            .frame(maxWidth: 280, alignment: .leading)
        }
        .frame(height: 190)
        .cornerRadius(16)
        .padding(.horizontal, 14)
    }

    // MARK: - Category Filter Chips
    private var categoryFilterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(categories, id: \.self) { cat in
                    Button(action: { selectedCategory = cat }) {
                        Text(cat)
                            .font(.system(size: 12, weight: .bold))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(selectedCategory == cat ? Color(hex: "059669") : Color.white)
                            .foregroundColor(selectedCategory == cat ? .white : Color(hex: "1E293B"))
                            .cornerRadius(20)
                            .overlay(
                                RoundedRectangle(cornerRadius: 20)
                                    .stroke(selectedCategory == cat ? Color.clear : Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 14)
        }
    }

    // MARK: - Buy Again Shelf
    private var buyAgainShelf: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Buy Again")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
                .padding(.horizontal, 14)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(buyAgainProducts) { product in
                        BuyAgainCard(product: product, onSelect: { prod in
                            activeSheet = .productDetail(prod)
                        })
                    }
                }
                .padding(.horizontal, 14)
            }
        }
    }

    // MARK: - Products Section
    private var productsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Popular Products & Essentials")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
                .padding(.horizontal, 14)

            if isLoading && products.isEmpty {
                ProgressView()
                    .frame(maxWidth: .infinity, minHeight: 180)
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
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 160, maximum: 240), spacing: 12)], spacing: 12) {
                    ForEach(filteredProducts) { product in
                        CommerceProductCard(product: product, onSelect: { prod in
                            activeSheet = .productDetail(prod)
                        })
                    }
                }
                .padding(.horizontal, 14)
            }
        }
    }
    
    private var filteredProducts: [ProductDto] {
        if selectedCategory == "All" { return products }
        return products.filter { $0.category.localizedCaseInsensitiveContains(selectedCategory) || $0.name.localizedCaseInsensitiveContains(selectedCategory) }
    }
    
    private func loadProducts() {
        isLoading = true
        Task {
            do {
                let custId = container.customerSession?.customerId ?? ""
                let endpoint = custId.isEmpty ? "/api/v1/catalog/home-feed" : "/api/v1/catalog/home-feed?customerId=\(custId)"
                let feed: HomeFeedResponse = try await container.apiClient.get(endpoint: endpoint)
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

// MARK: - Buy Again Card
public struct BuyAgainCard: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    let product: ProductDto
    var onSelect: ((ProductDto) -> Void)? = nil

    public init(product: ProductDto, onSelect: ((ProductDto) -> Void)? = nil) {
        self.product = product
        self.onSelect = onSelect
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(product.name)
                .font(.system(size: 12, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
                .lineLimit(2)
            
            Spacer(minLength: 4)
            
            HStack {
                Text("₹\(String(format: "%.2f", product.price))")
                    .font(.system(size: 13, weight: .black))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
                QuickAddToCartButton(product: product, compact: true)
            }
        }
        .padding(10)
        .frame(width: 140, height: 110)
        .background(Color.white)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
        .contentShape(Rectangle())
        .onTapGesture {
            onSelect?(product)
        }
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
                            .foregroundColor(Color(hex: "059669"))
                    } else if isOutOfStock {
                        Text("OUT OF STOCK")
                            .font(.system(size: compact ? 9 : 11, weight: .bold))
                            .foregroundColor(.secondary)
                    } else {
                        Text("ADD")
                            .font(.system(size: compact ? 11 : 13, weight: .black))
                            .foregroundColor(Color(hex: "059669"))
                        Image(systemName: "plus")
                            .font(.system(size: compact ? 9 : 11, weight: .bold))
                            .foregroundColor(Color(hex: "059669"))
                    }
                }
                .padding(.horizontal, compact ? 8 : 12)
                .padding(.vertical, compact ? 4 : 6)
                .background(isOutOfStock ? Color(.systemGray6) : Color(hex: "ECFDF5"))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(isOutOfStock ? Color.clear : Color(hex: "059669"), lineWidth: 1)
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
            .background(Color(hex: "059669"))
            .cornerRadius(8)
            .shadow(color: Color(hex: "059669").opacity(0.3), radius: 3, x: 0, y: 1)
        }
    }
}

// MARK: - Catalog Response Helpers
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
    public let imageUrl: String?
    
    public func toProductDto() -> ProductDto {
        let isRx = (rxRequirement ?? "").contains("RX") || (rxRequirement ?? "").contains("SCHEDULE")
        let isCold = coldChainRequired ?? false
        let sellPrice = discountedPrice ?? price
        let effectiveMrp = mrp ?? price
        let cat = category ?? therapeuticCategory ?? "General"
        let resolved = MedicineImageResolver.resolve(sku: sku, name: name, rawImage: imageUrl)
        
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
            genericSubstitute: genericSub,
            imageUrl: resolved.isEmpty ? nil : resolved
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
