import SwiftUI

/**
 * 1:1 Parity with Android HomeScreen.kt (1,434 lines).
 * Flagship Healthcare & Pharmacy Home Screen matching Tata 1mg & PharmEasy benchmarks.
 * 
 * Strict Medicine Vertical Focus:
 * 1. Edge-to-Edge Medical Teal HeaderCanopy (Location GPS locator, ETA badge, bell, stadium search & Upload Rx action).
 * 2. HeroDualActionCards (Medicines Flat 20% OFF vs Lab Tests Up to 70% OFF 50/50 split).
 * 3. PromotionalCampaignsStrip (Active live coupon codes: HEALTH30, SAVE10).
 * 4. AssistedCommerceRibbon (Upload Rx, WhatsApp Pharmacist Support, 24x7 Call Helpdesk).
 * 5. MedicineCategoryRail (Popular Healthcare Categories with pastel containers & verified pack images).
 * 6. Dynamic Pharmacy Shelves (Curated Bestsellers, Essentials, Top Deals with live cart synchronization).
 */
public struct HomeScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @EnvironmentObject private var addressRepository: AddressRepository
    @ObservedObject private var navigator = AppNavigator.shared
    
    @State private var selectedCategory: String = "All"
    @State private var products: [ProductDto] = []
    @State private var buyAgainProducts: [ProductDto] = []
    @State private var homeFeed: HomeFeedResponse? = nil
    @State private var isLoading: Bool = false
    
    @State private var categories: [String] = [
        "All", "Pain & Fever", "Cold & Cough", "Diabetes Care", "Antibiotics", "Vitamins", "Acidity & Gas"
    ]
    
    public let onOpenCatalog: () -> Void
    
    public init(onOpenCatalog: @escaping () -> Void = {}) {
        self.onOpenCatalog = onOpenCatalog
    }
    
    private struct MedicalCategoryItem: Identifiable {
        let id: String
        let name: String
        let query: String
        let imageUrl: String
        let badgeText: String?
        let backgroundColorHex: String
    }

    private let medicineCategories: [MedicalCategoryItem] = [
        MedicalCategoryItem(
            id: "pain_fever",
            name: "Pain & Fever",
            query: "Pain & Fever",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
            badgeText: "Best Seller",
            backgroundColorHex: "FFF5ED"
        ),
        MedicalCategoryItem(
            id: "cold_cough",
            name: "Cold & Cough",
            query: "Cold & Cough",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
            badgeText: "Trending",
            backgroundColorHex: "EFF6FF"
        ),
        MedicalCategoryItem(
            id: "diabetes",
            name: "Diabetes Care",
            query: "Diabetes",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
            badgeText: nil,
            backgroundColorHex: "F0FDF4"
        ),
        MedicalCategoryItem(
            id: "antibiotics",
            name: "Antibiotics",
            query: "Antibiotics",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
            badgeText: "Rx Verified",
            backgroundColorHex: "FEF3C7"
        ),
        MedicalCategoryItem(
            id: "vitamins",
            name: "Vitamins",
            query: "Vitamins",
            imageUrl: "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg",
            badgeText: "Trending",
            backgroundColorHex: "FFF1F2"
        ),
        MedicalCategoryItem(
            id: "acidity_gas",
            name: "Acidity & Gas",
            query: "Acidity & Gas",
            imageUrl: "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg",
            badgeText: "Fast Relief",
            backgroundColorHex: "FAF5FF"
        )
    ]
    
    public var body: some View {
        VStack(spacing: 0) {
            // 1. Edge-to-Edge Medical Teal Canopy Header (100% Shared Canopy Header)
            HeaderCanopy(
                selectedAddress: addressRepository.selectedAddress,
                calculatedEtaMinutes: addressRepository.calculatedEtaMinutes,
                locationHeaderLabel: addressRepository.locationHeaderTitle,
                isLocating: addressRepository.isLoading,
                unreadNotificationCount: LocalNotificationStorage.shared.notifications.filter { !$0.isRead }.count,
                onChangeAddress: {
                    navigator.push(.addressSelection(fromCheckout: false, fromProfile: false))
                },
                onProfileClick: {
                    navigator.switchTab(to: 3)
                },
                onNotificationsClick: {
                    navigator.push(.notifications)
                },
                onSearchClick: { query in
                    navigator.push(.search(query: query))
                },
                onUploadPrescription: {
                    navigator.push(.prescriptions)
                }
            )

            ScrollView(showsIndicators: false) {
                VStack(spacing: 16) {
                    // 2. Dual Service Hero Tiles (Medicines vs Lab Tests 50/50 split)
                    heroDualActionCards

                    // 2b. Tiered Promotional Campaigns Strip (Special Offers & Active Authoritative Coupons)
                    promotionalCampaignsStrip

                    // 3. Assisted Commerce Ribbon (Upload Rx / WhatsApp / Call 24x7)
                    assistedCommerceRibbon

                    // 4. Popular Healthcare Categories Rail (Verbatim Android MedicineCategoryRail)
                    medicineCategoryRail

                    // 5. Category Filter Chips
                    categoryFilterChips

                    // 6. Buy Again Shelf (if any)
                    if !buyAgainProducts.isEmpty {
                        buyAgainShelf
                    }

                    // 7. Dynamic Curated Medicines Section
                    productsSection

                    // Spacer to ensure last card clears floating GlobalCartBar
                    Spacer(minLength: cartStore.totalItemCount > 0 ? 84 : 28)
                }
                .padding(.top, 12)
            }
        }
        .background(Color(hex: "F4F6F8").ignoresSafeArea())
        .task {
            loadProducts()
        }
    }

    // MARK: - 2. Dual Service Hero Tiles (1:1 Android HeroDualActionCards lines 423-547)
    private var heroDualActionCards: some View {
        HStack(spacing: 12) {
            // Medicines Tile (50% Split)
            Button(action: {
                navigator.push(.search(query: ""))
            }) {
                VStack(alignment: .leading, spacing: 0) {
                    // Tag
                    Text("FLAT 20% OFF")
                        .font(.system(size: 9, weight: .black))
                        .foregroundColor(Color(hex: "166534"))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2.5)
                        .background(Color(hex: "DCFCE7"))
                        .cornerRadius(6)

                    Spacer(minLength: 8)

                    Text("Medicines")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    Text("Delivered in 10 mins")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))

                    Spacer(minLength: 10)

                    HStack(spacing: 3) {
                        Text("Order Now")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: 114)
                .background(Color.white)
                .cornerRadius(18)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.03), radius: 2, x: 0, y: 1)
            }
            .buttonStyle(PlainButtonStyle())

            // Lab Tests Tile (50% Split)
            Button(action: {
                navigator.push(.search(query: "Lab Tests"))
            }) {
                VStack(alignment: .leading, spacing: 0) {
                    // Tag
                    Text("UP TO 70% OFF")
                        .font(.system(size: 9, weight: .black))
                        .foregroundColor(Color(hex: "1E40AF"))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2.5)
                        .background(Color(hex: "DBEAFE"))
                        .cornerRadius(6)

                    Spacer(minLength: 8)

                    Text("Lab Tests")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    Text("Free sample pickup")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))

                    Spacer(minLength: 10)

                    HStack(spacing: 3) {
                        Text("Book Test")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "2563EB"))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(hex: "2563EB"))
                    }
                }
                .padding(14)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: 114)
                .background(Color.white)
                .cornerRadius(18)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.03), radius: 2, x: 0, y: 1)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 14)
    }

    // MARK: - 2b. Tiered Promotional Campaigns Strip (1:1 Android lines 553-685)
    private var promotionalCampaignsStrip: some View {
        VStack(spacing: 8) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "tag.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                    Text("Special Offers & Discounts")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(Color(hex: "0F172A"))
                }
                Spacer()
                Text("Apply at Cart")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: "0E675E"))
            }
            .padding(.horizontal, 14)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    // Campaign 1: HEALTH30
                    Button(action: {
                        navigator.push(.search(query: ""))
                    }) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("FLAT ₹150 OFF")
                                    .font(.system(size: 11, weight: .black))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.white.opacity(0.2))
                                    .cornerRadius(4)
                                Spacer()
                                Image(systemName: "ticket.fill")
                                    .font(.system(size: 13))
                                    .foregroundColor(.white.opacity(0.6))
                            }
                            Text("On orders > ₹799 (Medicines)")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.white.opacity(0.9))
                                .lineLimit(1)
                            
                            Spacer(minLength: 4)

                            HStack(spacing: 4) {
                                Text("USE CODE:")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(Color(hex: "475569"))
                                Text("HEALTH30")
                                    .font(.system(size: 10.5, weight: .black))
                                    .foregroundColor(Color(hex: "0F172A"))
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.white)
                            .cornerRadius(6)
                        }
                        .padding(12)
                        .frame(width: 220, height: 96, alignment: .leading)
                        .background(
                            LinearGradient(
                                colors: [Color(hex: "064E3B"), Color(hex: "047857")],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(14)
                        .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
                    }
                    .buttonStyle(PlainButtonStyle())

                    // Campaign 2: SAVE10
                    Button(action: {
                        navigator.push(.search(query: ""))
                    }) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack {
                                Text("FLAT ₹100 OFF")
                                    .font(.system(size: 11, weight: .black))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color.white.opacity(0.2))
                                    .cornerRadius(4)
                                Spacer()
                                Image(systemName: "ticket.fill")
                                    .font(.system(size: 13))
                                    .foregroundColor(.white.opacity(0.6))
                            }
                            Text("On orders > ₹199 (Instant)")
                                .font(.system(size: 11, weight: .medium))
                                .foregroundColor(.white.opacity(0.9))
                                .lineLimit(1)

                            Spacer(minLength: 4)

                            HStack(spacing: 4) {
                                Text("USE CODE:")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(Color(hex: "475569"))
                                Text("SAVE10")
                                    .font(.system(size: 10.5, weight: .black))
                                    .foregroundColor(Color(hex: "0F172A"))
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color.white)
                            .cornerRadius(6)
                        }
                        .padding(12)
                        .frame(width: 220, height: 96, alignment: .leading)
                        .background(
                            LinearGradient(
                                colors: [Color(hex: "1E1B4B"), Color(hex: "4338CA")],
                                startPoint: .leading,
                                endPoint: .trailing
                            )
                        )
                        .cornerRadius(14)
                        .shadow(color: Color.black.opacity(0.04), radius: 2, x: 0, y: 1)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(.horizontal, 14)
            }
        }
    }

    // MARK: - 3. Assisted Commerce Ribbon (1:1 Android lines 693-830)
    private var assistedCommerceRibbon: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "checkmark.shield.fill")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "059669"))
                    Text("ORDER WITH ASSISTANCE")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(Color(hex: "065F46"))
                        .tracking(0.5)
                }
                Spacer()
                Text("Rx Required")
                    .font(.system(size: 9, weight: .bold))
                    .foregroundColor(Color(hex: "047857"))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 4)
                            .stroke(Color(hex: "86EFAC"), lineWidth: 0.5)
                    )
                    .cornerRadius(4)
            }

            Text("Don't know exact medicine name? Upload prescription or message our pharmacist directly.")
                .font(.system(size: 11.5))
                .foregroundColor(Color(hex: "047857"))
                .lineSpacing(2)

            HStack(spacing: 8) {
                // Upload Rx Pill Button
                Button(action: {
                    navigator.push(.prescriptions)
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "arrow.up.doc.fill")
                            .font(.system(size: 12, weight: .bold))
                        Text("Upload Rx")
                            .font(.system(size: 11.5, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(Color(hex: "0E675E"))
                    .cornerRadius(10)
                }
                .buttonStyle(PlainButtonStyle())

                // WhatsApp Pill Button
                Button(action: {
                    if let url = URL(string: "https://wa.me/919817916180?text=Hello%20CommerceOS%20Pharmacy%2C%20I%20would%20like%20to%20order%20medicines.") {
                        UIApplication.shared.open(url)
                    }
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "message.fill")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "059669"))
                        Text("WhatsApp")
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundColor(Color(hex: "065F46"))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: "86EFAC"), lineWidth: 1)
                    )
                    .cornerRadius(10)
                }
                .buttonStyle(PlainButtonStyle())

                // Call Pharmacist Pill Button
                Button(action: {
                    if let url = URL(string: "tel:+919817916180") {
                        UIApplication.shared.open(url)
                    }
                }) {
                    HStack(spacing: 5) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "059669"))
                        Text("Call 24x7")
                            .font(.system(size: 11.5, weight: .bold))
                            .foregroundColor(Color(hex: "065F46"))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 9)
                    .background(Color.white)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: "86EFAC"), lineWidth: 1)
                    )
                    .cornerRadius(10)
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
        .padding(.horizontal, 14)
    }

    // MARK: - 4. Popular Healthcare Categories (1:1 Android MedicineCategoryRail lines 260-370)
    private var medicineCategoryRail: some View {
        VStack(spacing: 12) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Popular Categories")
                        .font(.system(size: 17, weight: .black))
                        .foregroundColor(Color(hex: "0F172A"))
                    Text("Genuine medicines verified by certified pharmacists")
                        .font(.system(size: 11.5))
                        .foregroundColor(Color(hex: "64748B"))
                }
                Spacer()
                Button(action: {
                    navigator.switchTab(to: 1)
                }) {
                    HStack(spacing: 2) {
                        Text("See All")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                        Image(systemName: "arrow.right")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.horizontal, 14)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(medicineCategories) { cat in
                        Button(action: {
                            navigator.push(.search(query: cat.query))
                        }) {
                            VStack(spacing: 6) {
                                // Soft Pastel Rounded Container (Tata 1mg signature benchmark)
                                ZStack {
                                    RoundedRectangle(cornerRadius: 16)
                                        .fill(Color(hex: cat.backgroundColorHex))
                                        .frame(width: 86, height: 86)

                                    if let url = URL(string: cat.imageUrl) {
                                        AsyncImage(url: url) { phase in
                                            switch phase {
                                            case .success(let img):
                                                img.resizable()
                                                    .aspectRatio(contentMode: .fit)
                                                    .padding(10)
                                            default:
                                                Image(systemName: "pills.fill")
                                                    .font(.system(size: 24))
                                                    .foregroundColor(Color(hex: "0E675E"))
                                            }
                                        }
                                        .frame(width: 86, height: 86)
                                    }

                                    if let badge = cat.badgeText {
                                        VStack {
                                            HStack {
                                                Spacer()
                                                Text(badge)
                                                    .font(.system(size: 7.5, weight: .black))
                                                    .foregroundColor(.white)
                                                    .padding(.horizontal, 4)
                                                    .padding(.vertical, 1.5)
                                                    .background(Color(hex: "0E675E"))
                                                    .cornerRadius(4)
                                                    .offset(x: -4, y: 4)
                                            }
                                            Spacer()
                                        }
                                        .frame(width: 86, height: 86)
                                    }
                                }

                                Text(cat.name)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                    .lineLimit(1)
                                    .frame(width: 86)
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 14)
            }
        }
    }

    // MARK: - 5. Category Filter Chips
    private var categoryFilterChips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(categories, id: \.self) { cat in
                    Button(action: {
                        withAnimation(.easeInOut(duration: 0.16)) {
                            selectedCategory = cat
                        }
                    }) {
                        Text(cat)
                            .font(.system(size: 12, weight: .bold))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 7)
                            .background(selectedCategory == cat ? Color(hex: "0E675E") : Color.white)
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

    // MARK: - 6. Buy Again Shelf
    private var buyAgainShelf: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text("Buy Again")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
            }
            .padding(.horizontal, 14)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(buyAgainProducts) { product in
                        BuyAgainCard(product: product, onSelect: { prod in
                            navigator.push(.productDetail(product: prod))
                        })
                    }
                }
                .padding(.horizontal, 14)
            }
        }
    }

    // MARK: - 7. Dynamic Curated Medicines Section
    private var productsSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(selectedCategory == "All" ? "Popular Medicines & Essentials" : selectedCategory)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
            }
            .padding(.horizontal, 14)

            if isLoading && products.isEmpty {
                VStack(spacing: 12) {
                    ProgressView()
                    Text("Loading genuine pharmacy inventory...")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))
                }
                .frame(maxWidth: .infinity, minHeight: 180)
            } else if filteredProducts.isEmpty && !isLoading {
                VStack(spacing: 8) {
                    Image(systemName: "pills")
                        .font(.system(size: 32))
                        .foregroundColor(Color(hex: "94A3B8"))
                    Text("No medicines found under \(selectedCategory)")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Color(hex: "64748B"))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 32)
            } else {
                LazyVGrid(columns: [GridItem(.adaptive(minimum: 160, maximum: 240), spacing: 12)], spacing: 12) {
                    ForEach(filteredProducts) { product in
                        CommerceProductCard(product: product, onSelect: { prod in
                            navigator.push(.productDetail(product: prod))
                        })
                    }
                }
                .padding(.horizontal, 14)
            }
        }
    }

    private var filteredProducts: [ProductDto] {
        if selectedCategory == "All" { return products }
        return products.filter {
            $0.category.localizedCaseInsensitiveContains(selectedCategory) ||
            $0.name.localizedCaseInsensitiveContains(selectedCategory)
        }
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
                if let res: PageResponse<ProductDto> = try? await container.apiClient.get(endpoint: "/api/v1/catalog/products") {
                    let dynamicProducts = res.content
                    await MainActor.run {
                        self.products = dynamicProducts
                        self.isLoading = false
                    }
                } else if let arrayRes: [ProductDto] = try? await container.apiClient.get(endpoint: "/api/v1/catalog/products") {
                    await MainActor.run {
                        self.products = arrayRes
                        self.isLoading = false
                    }
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
                    } else if isOutOfStock {
                        Text("OUT")
                            .font(.system(size: compact ? 10 : 12, weight: .bold))
                    } else {
                        Image(systemName: "plus")
                            .font(.system(size: compact ? 11 : 13, weight: .black))
                        Text("ADD")
                            .font(.system(size: compact ? 10 : 12, weight: .black))
                    }
                }
                .foregroundColor(isOutOfStock ? Color(hex: "94A3B8") : Color(hex: "0E675E"))
                .padding(.horizontal, compact ? 8 : 12)
                .padding(.vertical, compact ? 4 : 6)
                .background(isOutOfStock ? Color(hex: "F1F5F9") : Color(hex: "ECFDF5"))
                .cornerRadius(6)
                .overlay(
                    RoundedRectangle(cornerRadius: 6)
                        .stroke(isOutOfStock ? Color(hex: "E2E8F0") : Color(hex: "A7F3D0"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
            .disabled(isOutOfStock)
        } else {
            HStack(spacing: 8) {
                Button(action: {
                    cartStore.decrement(sku: product.sku)
                }) {
                    Text("−")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(Color(hex: "0E675E"))
                }
                Text("\(quantity)")
                    .font(.system(size: 12, weight: .black))
                    .foregroundColor(Color(hex: "0F172A"))
                Button(action: {
                    cartStore.increment(sku: product.sku)
                }) {
                    Text("+")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(Color(hex: "0E675E"))
                }
            }
            .padding(.horizontal, 6)
            .padding(.vertical, 3)
            .background(Color(hex: "ECFDF5"))
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Color(hex: "A7F3D0"), lineWidth: 1)
            )
        }
    }
}
