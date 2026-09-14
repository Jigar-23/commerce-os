import SwiftUI

public struct CommerceProductCard: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    let product: ProductDto
    let onSelect: (ProductDto) -> Void
    
    @State private var isWishlisted: Bool = false
    @State private var heartScale: CGFloat = 1.0
    
    public init(product: ProductDto, onSelect: @escaping (ProductDto) -> Void = { _ in }) {
        self.product = product
        self.onSelect = onSelect
    }
    
    private var quantity: Int {
        cartStore.items[product.sku]?.quantity ?? 0
    }
    
    private var isOutOfStock: Bool {
        !product.inStock || product.stockCount == 0
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            // Top Image Tile with Wishlist & Discount Badge
            ZStack(alignment: .top) {
                // Background Tile with Packaging Photo
                let resolvedUrl = MedicineImageResolver.resolve(sku: product.sku, name: product.name, rawImage: product.imageUrl)

                RoundedRectangle(cornerRadius: 10)
                    .fill(Color(.systemGray6))
                    .frame(height: 110)
                    .overlay(
                        Group {
                            if let url = URL(string: resolvedUrl), !resolvedUrl.isEmpty {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let image):
                                        image
                                            .resizable()
                                            .aspectRatio(contentMode: .fit)
                                            .padding(6)
                                    default:
                                        Image(systemName: iconForCategory(product.category))
                                            .font(.system(size: 34))
                                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark.opacity(0.85))
                                    }
                                }
                            } else {
                                Image(systemName: iconForCategory(product.category))
                                    .font(.system(size: 34))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark.opacity(0.85))
                            }
                        }
                    )
                
                // Overlay Badges: Discount on top-left, Wishlist on top-right
                HStack {
                    if product.discountPercent > 0 {
                        Text("\(product.discountPercent)% OFF")
                            .font(.system(size: 9, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(CommerceOSTheme.Colors.brandPrimaryDark)
                            .cornerRadius(4)
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.5)) {
                            isWishlisted.toggle()
                            heartScale = 1.3
                        }
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                            withAnimation(.spring(response: 0.2, dampingFraction: 0.7)) {
                                heartScale = 1.0
                            }
                        }
                    }) {
                        Circle()
                            .fill(Color.white.opacity(0.95))
                            .frame(width: 28, height: 28)
                            .shadow(color: Color.black.opacity(0.08), radius: 2, x: 0, y: 1)
                            .overlay(
                                Image(systemName: isWishlisted ? "heart.fill" : "heart")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(isWishlisted ? Color(hex: "EF4444") : Color(.secondaryLabel))
                            )
                            .scaleEffect(heartScale)
                    }
                    .buttonStyle(BorderlessButtonStyle())
                }
                .padding(6)
            }
            .contentShape(Rectangle())
            .onTapGesture {
                onSelect(product)
            }
            
            // Product Title
            Text(product.name)
                .font(.system(size: 13, weight: .semibold))
                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                .lineLimit(2)
                .frame(minHeight: 34, alignment: .topLeading)
                .contentShape(Rectangle())
                .onTapGesture {
                    onSelect(product)
                }
            
            // Brand & Pack Size Row
            Text("\(product.brand ?? product.category) • \(product.effectivePackSize)")
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(.secondary)
                .lineLimit(1)
            
            // Rating & ETA Row
            HStack(spacing: 6) {
                HStack(spacing: 2) {
                    Image(systemName: "star.fill")
                        .font(.system(size: 9))
                        .foregroundColor(Color(hex: "F59E0B"))
                    Text(String(format: "%.1f", product.effectiveRating))
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                    Text("(\(product.effectiveReviewCount))")
                        .font(.system(size: 9))
                        .foregroundColor(.secondary)
                }
                
                Spacer()
                
                // 11 MINS SLA Pill
                Text("11 MINS")
                    .font(.system(size: 8, weight: .black))
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(Color(hex: "FEF3C7"))
                    .foregroundColor(Color(hex: "B45309"))
                    .cornerRadius(3)
            }
            
            // Status Badges (Rx & Cold Chain)
            if product.requiresPrescription || product.isColdChain == true {
                HStack(spacing: 4) {
                    if product.requiresPrescription {
                        Text("Rx")
                            .font(.system(size: 8, weight: .black))
                            .padding(.horizontal, 4)
                            .padding(.vertical, 1)
                            .background(Color(hex: "FEE2E2"))
                            .foregroundColor(Color(hex: "DC2626"))
                            .cornerRadius(3)
                    }
                    if product.isColdChain == true {
                        HStack(spacing: 1) {
                            Image(systemName: "snowflake")
                                .font(.system(size: 7))
                            Text("2-8°C")
                                .font(.system(size: 8, weight: .bold))
                        }
                        .padding(.horizontal, 4)
                        .padding(.vertical, 1)
                        .background(Color(hex: "E0F2FE"))
                        .foregroundColor(Color(hex: "0284C7"))
                        .cornerRadius(3)
                    }
                }
            }
            
            Spacer(minLength: 4)
            
            // Stock Urgency / Savings Callout
            if isOutOfStock {
                Text("Out of stock")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundColor(Color(hex: "DC2626"))
            } else if let stock = product.stockCount, stock <= 3 {
                Text("Only \(stock) left")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(Color(hex: "D97706"))
            } else if product.mrp > product.price {
                Text("Save ₹\(String(format: "%.0f", product.mrp - product.price))")
                    .font(.system(size: 10, weight: .bold))
                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
            }
            
            // Price Block & Add Button Row
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 1) {
                    Text("₹\(String(format: "%.2f", product.price))")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                    
                    if product.mrp > product.price {
                        Text("₹\(String(format: "%.2f", product.mrp))")
                            .font(.system(size: 10))
                            .strikethrough()
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
                
                // Add / Stepper Button
                if isOutOfStock {
                    Text("OUT")
                        .font(.system(size: 10, weight: .bold))
                        .foregroundColor(.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 5)
                        .background(Color(.systemGray6))
                        .cornerRadius(6)
                } else if quantity <= 0 {
                    Button(action: {
                        cartStore.add(product: product)
                    }) {
                        HStack(spacing: 3) {
                            Text("ADD")
                                .font(.system(size: 11, weight: .black))
                            Image(systemName: "plus")
                                .font(.system(size: 9, weight: .bold))
                        }
                        .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(CommerceOSTheme.Colors.brandPrimarySoft)
                        .cornerRadius(6)
                        .overlay(
                            RoundedRectangle(cornerRadius: 6)
                                .stroke(CommerceOSTheme.Colors.brandPrimaryDark, lineWidth: 1)
                        )
                    }
                    .buttonStyle(BorderlessButtonStyle())
                } else {
                    HStack(spacing: 6) {
                        Button(action: {
                            cartStore.decrement(sku: product.sku)
                        }) {
                            Text("−")
                                .font(.system(size: 13, weight: .black))
                                .foregroundColor(.white)
                                .frame(width: 20, height: 20)
                        }
                        .buttonStyle(BorderlessButtonStyle())
                        
                        Text("\(quantity)")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(.white)
                        
                        Button(action: {
                            cartStore.increment(sku: product.sku)
                        }) {
                            Text("+")
                                .font(.system(size: 13, weight: .black))
                                .foregroundColor(.white)
                                .frame(width: 20, height: 20)
                        }
                        .buttonStyle(BorderlessButtonStyle())
                    }
                    .padding(.horizontal, 6)
                    .padding(.vertical, 3)
                    .background(CommerceOSTheme.Colors.brandPrimaryDark)
                    .cornerRadius(6)
                    .shadow(color: CommerceOSTheme.Colors.brandPrimaryDark.opacity(0.3), radius: 2, x: 0, y: 1)
                }
            }
        }
        .padding(10)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 2)
    }
    
    private func iconForCategory(_ category: String) -> String {
        let cat = category.lowercased()
        if cat.contains("fever") || cat.contains("pain") || cat.contains("pharma") || cat.contains("medicine") {
            return "cross.case.fill"
        } else if cat.contains("cold") || cat.contains("cough") {
            return "allergens"
        } else if cat.contains("vitamin") {
            return "sparkles"
        } else if cat.contains("milk") || cat.contains("dairy") {
            return "cup.and.saucer.fill"
        } else if cat.contains("fruit") || cat.contains("vegetable") {
            return "carrot.fill"
        } else {
            return "bag.fill"
        }
    }
}
