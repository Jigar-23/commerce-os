import SwiftUI

// MARK: - Multi-Vertical Storefront Screen (Ported from Android VerticalHomeScreen.kt)
/// Hub presenting dynamic quick-commerce verticals (Pharmacy, Food, Fashion, Electronics, Services)
/// with seamless tab switching, category discovery, curated rails, and responsive grids.
public struct VerticalHomeScreen: View {
    @State private var activeVerticalId: String = "pharmacy"
    public let onSelectProduct: (ProductDto) -> Void
    public let onOpenCatalog: (String?) -> Void
    public var products: [ProductDto] = []
    
    public init(
        initialVerticalId: String = "pharmacy",
        products: [ProductDto] = [],
        onSelectProduct: @escaping (ProductDto) -> Void = { _ in },
        onOpenCatalog: @escaping (String?) -> Void = { _ in }
    ) {
        self._activeVerticalId = State(initialValue: initialVerticalId)
        self.products = products
        self.onSelectProduct = onSelectProduct
        self.onOpenCatalog = onOpenCatalog
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // 1. Horizontal Discovery Pill Bar
            VerticalDiscoveryBar(
                activeVerticalId: activeVerticalId,
                onSelectVertical: { selectedId in
                    withAnimation(.easeInOut(duration: 0.2)) {
                        activeVerticalId = selectedId
                    }
                }
            )
            .background(Color.white)
            
            Divider()
            
            // 2. Active Vertical Section Content
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 20) {
                    switch activeVerticalId.lowercased() {
                    case "pharmacy", "health":
                        PharmacyVerticalSection(
                            featuredProducts: products,
                            onOpenCatalog: onOpenCatalog,
                            onSelectProduct: onSelectProduct
                        )
                    case "food":
                        FoodVerticalSection(onOpenCatalog: onOpenCatalog)
                    case "fashion":
                        FashionVerticalSection(onOpenCatalog: onOpenCatalog)
                    case "electronics":
                        ElectronicsVerticalSection(onOpenCatalog: onOpenCatalog)
                    case "services":
                        ServicesVerticalSection(onOpenCatalog: onOpenCatalog)
                    default:
                        PharmacyVerticalSection(
                            featuredProducts: products,
                            onOpenCatalog: onOpenCatalog,
                            onSelectProduct: onSelectProduct
                        )
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 16)
            }
            .background(Color(hex: "F8FAFC"))
        }
    }
}
