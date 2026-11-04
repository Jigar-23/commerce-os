import SwiftUI
import UIKit

public enum ProductCardVariant {
    case compact // Horizontal row (92x92 image on left)
    case grid    // Vertical tile (2-column grid)
}

public struct CommerceProductCard: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    public let model: ProductCardModel
    public let onSelect: () -> Void
    public var variant: ProductCardVariant = .grid
    public var onWishlistToggle: ((String) -> Void)? = nil
    
    @State private var isWishlistedLocal: Bool = false
    @State private var heartScale: CGFloat = 1.0
    
    public init(
        model: ProductCardModel,
        variant: ProductCardVariant = .grid,
        onSelect: @escaping () -> Void = {},
        onWishlistToggle: ((String) -> Void)? = nil
    ) {
        self.model = model
        self.variant = variant
        self.onSelect = onSelect
        self.onWishlistToggle = onWishlistToggle
        self._isWishlistedLocal = State(initialValue: model.isWishlisted)
    }
    
    public init(
        product: ProductDto,
        variant: ProductCardVariant = .grid,
        onSelect: @escaping (ProductDto) -> Void = { _ in },
        onWishlistToggle: ((String) -> Void)? = nil
    ) {
        self.model = product.toProductCardModel()
        self.variant = variant
        self.onSelect = { onSelect(product) }
        self.onWishlistToggle = onWishlistToggle
        self._isWishlistedLocal = State(initialValue: false)
    }
    
    private var quantity: Int {
        cartStore.items[model.sku]?.quantity ?? 0
    }
    
    private var isOutOfStock: Bool {
        !model.inStock || model.stockCount == 0
    }
    
    private func triggerHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }
    
    public var body: some View {
        Group {
            switch variant {
            case .compact:
                compactLayout
            case .grid:
                gridLayout
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onSelect()
        }
    }
    
    // MARK: - Compact Horizontal Layout (Matching Android CompactProductCard)
    private var compactLayout: some View {
        HStack(alignment: .top, spacing: 12) {
            // Left Image Block (92x92)
            ZStack(alignment: .topLeading) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(hex: "F8FAFC"))
                    .frame(width: 92, height: 92)
                    .overlay(
                        productImageView
                            .padding(6)
                    )
                
                // Rating Pill (Bottom Left)
                VStack {
                    Spacer()
                    ratingPill
                        .padding(.leading, 4)
                        .padding(.bottom, 4)
                }
                .frame(width: 92, height: 92, alignment: .bottomLeading)
                
                // Wishlist Button (Top Right)
                VStack {
                    HStack {
                        Spacer()
                        wishlistButton
                            .padding(4)
                    }
                    Spacer()
                }
                .frame(width: 92, height: 92, alignment: .topTrailing)
            }
            
            // Right Information Block
            VStack(alignment: .leading, spacing: 3) {
                Text(HealthcareFormatters.formatTitle(model.name))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                    .lineLimit(2)
                
                let brand = HealthcareFormatters.formatBrand(model.brandName, title: model.name)
                let pack = HealthcareFormatters.formatPackSize(model.packSize)
                Text("\(brand) • \(pack)")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: "64748B"))
                    .lineLimit(1)
                
                Text(model.etaLabel ?? "⚡ In 10 mins")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: "059669"))
                
                // Status Badges (Rx, Cold Chain)
                if model.rxRequired || model.coldChain {
                    HStack(spacing: 4) {
                        if model.rxRequired {
                            Text("Rx Required")
                                .font(.system(size: 9, weight: .black))
                                .foregroundColor(Color(hex: "DC2626"))
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color(hex: "FEE2E2"))
                                .cornerRadius(3)
                        }
                        if model.coldChain {
                            Text("Cold Chain")
                                .font(.system(size: 9, weight: .bold))
                                .foregroundColor(Color(hex: "0284C7"))
                                .padding(.horizontal, 5)
                                .padding(.vertical, 2)
                                .background(Color(hex: "E0F2FE"))
                                .cornerRadius(3)
                        }
                    }
                }
                
                Spacer(minLength: 4)
                
                // Price & Add Button Row
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 1) {
                        HStack(spacing: 4) {
                            Text("₹\(String(format: "%.2f", model.sellingPrice))")
                                .font(.system(size: 15, weight: .heavy))
                                .foregroundColor(Color(hex: "0F172A"))
                            
                            if model.discountPercent > 0 {
                                Text("₹\(String(format: "%.2f", model.price))")
                                    .font(.system(size: 11))
                                    .strikethrough()
                                    .foregroundColor(Color(hex: "94A3B8"))
                            }
                        }
                        
                        if isOutOfStock {
                            Text("Out of stock")
                                .font(.system(size: 10, weight: .semibold))
                                .foregroundColor(Color(hex: "DC2626"))
                        } else if let stock = model.stockCount, stock <= 3 {
                            Text("Only \(stock) left")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Color(hex: "D97706"))
                        } else if model.discountPercent > 0 {
                            Text("Save ₹\(String(format: "%.0f", model.price - model.sellingPrice))")
                                .font(.system(size: 10, weight: .bold))
                                .foregroundColor(Color(hex: "16A34A"))
                        }
                    }
                    
                    Spacer()
                    
                    stepperOrAddButton
                        .frame(width: 72, height: 30)
                }
            }
        }
        .padding(12)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 1)
    }
    
    // MARK: - Grid Vertical Layout (Matching Android GridProductCard)
    private var gridLayout: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Image Tile (Top)
            ZStack(alignment: .top) {
                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(hex: "F8FAFC"))
                    .frame(height: 120)
                    .overlay(
                        productImageView
                            .padding(6)
                    )
                
                // Discount Chip (Top-Left)
                if model.discountPercent > 0 {
                    HStack {
                        Text("\(model.discountPercent)% OFF")
                            .font(.system(size: 9, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color(hex: "16A34A"))
                            .cornerRadius(4)
                        Spacer()
                    }
                    .padding(6)
                }
                
                // Wishlist Heart Button (Top-Right)
                HStack {
                    Spacer()
                    wishlistButton
                }
                .padding(6)
                
                // Rating Pill (Bottom-Left)
                VStack {
                    Spacer()
                    HStack {
                        ratingPill
                        Spacer()
                    }
                    .padding(6)
                }
            }
            .frame(height: 120)
            
            // Text Details
            VStack(alignment: .leading, spacing: 2) {
                Text(HealthcareFormatters.formatTitle(model.name))
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                    .lineLimit(2)
                    .frame(minHeight: 34, alignment: .topLeading)
                
                let brand = HealthcareFormatters.formatBrand(model.brandName, title: model.name)
                let pack = HealthcareFormatters.formatPackSize(model.packSize)
                Text("\(brand) • \(pack)")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: "64748B"))
                    .lineLimit(1)
                
                Text(model.etaLabel ?? "⚡ In 10 mins")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: "059669"))
            }
            
            Spacer(minLength: 4)
            
            // Stock Urgency / Savings
            if isOutOfStock {
                Text("Out of stock")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(Color(hex: "DC2626"))
            } else if let stock = model.stockCount, stock <= 3 {
                Text("Only \(stock) left")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: "D97706"))
            } else if model.discountPercent > 0 {
                Text("Save ₹\(String(format: "%.0f", model.price - model.sellingPrice))")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: "16A34A"))
            }
            
            // Price & Stepper Row
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("₹\(String(format: "%.2f", model.sellingPrice))")
                        .font(.system(size: 14, weight: .heavy))
                        .foregroundColor(Color(hex: "0F172A"))
                    
                    if model.discountPercent > 0 && model.price > model.sellingPrice {
                        Text("₹\(String(format: "%.2f", model.price))")
                            .font(.system(size: 10.5))
                            .strikethrough()
                            .foregroundColor(Color(hex: "94A3B8"))
                    }
                }
                
                Spacer()
                
                stepperOrAddButton
                    .frame(width: 68, height: 30)
            }
        }
        .padding(10)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.04), radius: 3, x: 0, y: 1)
    }
    
    // MARK: - Reusable Image View
    @ViewBuilder
    private var productImageView: some View {
        let resolved = model.image.isEmpty ? MedicineImageResolver.resolve(sku: model.sku, name: model.name) : model.image
        if let url = URL(string: resolved), !resolved.isEmpty {
            AsyncImage(url: url) { phase in
                switch phase {
                case .success(let image):
                    image
                        .resizable()
                        .aspectRatio(contentMode: .fit)
                default:
                    Image(systemName: "cross.case.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Color(hex: "0E675E").opacity(0.8))
                }
            }
        } else {
            Image(systemName: "cross.case.fill")
                .font(.system(size: 32))
                .foregroundColor(Color(hex: "0E675E").opacity(0.8))
        }
    }
    
    // MARK: - Rating Pill (Tata 1mg Signature)
    private var ratingPill: some View {
        let ratingVal = model.rating ?? 4.4
        return HStack(spacing: 2) {
            Text(String(format: "%.1f", ratingVal))
                .font(.system(size: 9.5, weight: .heavy))
                .foregroundColor(.white)
            Image(systemName: "star.fill")
                .font(.system(size: 8))
                .foregroundColor(.white)
        }
        .padding(.horizontal, 5)
        .padding(.vertical, 2)
        .background(Color(hex: "166534"))
        .cornerRadius(4)
    }
    
    // MARK: - Wishlist Button
    private var wishlistButton: some View {
        Button(action: {
            triggerHaptic()
            withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) {
                isWishlistedLocal.toggle()
                heartScale = 1.3
            }
            onWishlistToggle?(model.id)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                withAnimation(.spring(response: 0.2, dampingFraction: 0.7)) {
                    heartScale = 1.0
                }
            }
        }) {
            Circle()
                .fill(Color.white.opacity(0.92))
                .frame(width: 28, height: 28)
                .shadow(color: Color.black.opacity(0.1), radius: 2, x: 0, y: 1)
                .overlay(
                    Image(systemName: isWishlistedLocal ? "heart.fill" : "heart")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(isWishlistedLocal ? Color(hex: "EF4444") : Color(hex: "94A3B8"))
                )
                .scaleEffect(heartScale)
        }
        .buttonStyle(BorderlessButtonStyle())
    }
    
    // MARK: - Stepper or ADD Button
    private var stepperOrAddButton: some View {
        let brandTeal = Color(hex: "0E675E")
        
        return Group {
            if isOutOfStock {
                Text("OOS")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: "94A3B8"))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color(hex: "F8FAFC"))
                    .cornerRadius(6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
            } else if quantity <= 0 {
                Button(action: {
                    triggerHaptic()
                    let dto = ProductDto(
                        id: model.id,
                        sku: model.sku,
                        name: model.name,
                        brand: model.brandName,
                        mrp: model.price,
                        price: model.sellingPrice,
                        category: model.verticalId,
                        requiresPrescription: model.rxRequired,
                        isColdChain: model.coldChain,
                        inStock: model.inStock,
                        rating: model.rating,
                        reviewCount: model.reviewCount,
                        packSize: model.packSize,
                        imageUrl: model.image,
                        stockCount: model.stockCount ?? 100
                    )
                    cartStore.add(product: dto)
                }) {
                    HStack(spacing: 3) {
                        Text("ADD")
                            .font(.system(size: 12, weight: .black))
                        Image(systemName: "plus")
                            .font(.system(size: 10, weight: .bold))
                    }
                    .foregroundColor(brandTeal)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background(Color.white)
                    .cornerRadius(6)
                    .overlay(
                        RoundedRectangle(cornerRadius: 6)
                            .stroke(brandTeal, lineWidth: 1.2)
                    )
                }
                .buttonStyle(BorderlessButtonStyle())
            } else {
                HStack(spacing: 0) {
                    Button(action: {
                        triggerHaptic()
                        cartStore.decrement(sku: model.sku)
                    }) {
                        Text("−")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .buttonStyle(BorderlessButtonStyle())
                    
                    Text("\(quantity)")
                        .font(.system(size: 12, weight: .black))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                    
                    Button(action: {
                        triggerHaptic()
                        cartStore.increment(sku: model.sku)
                    }) {
                        Text("+")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                    }
                    .buttonStyle(BorderlessButtonStyle())
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(brandTeal)
                .cornerRadius(6)
            }
        }
    }
}
