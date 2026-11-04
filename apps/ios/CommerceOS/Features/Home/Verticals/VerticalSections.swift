import SwiftUI

// MARK: - Pharmacy Vertical Section (Ported from Android PharmacyVerticalSection.kt)
public struct PharmacyVerticalSection: View {
    public let onOpenCatalog: (String?) -> Void
    public let onSelectProduct: (ProductDto) -> Void
    public var featuredProducts: [ProductDto] = []
    
    public init(
        featuredProducts: [ProductDto] = [],
        onOpenCatalog: @escaping (String?) -> Void,
        onSelectProduct: @escaping (ProductDto) -> Void = { _ in }
    ) {
        self.featuredProducts = featuredProducts
        self.onOpenCatalog = onOpenCatalog
        self.onSelectProduct = onSelectProduct
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Health & Pharmacy Hero Card
            Button(action: { onOpenCatalog("pharmacy") }) {
                HStack {
                    VStack(alignment: .leading, spacing: 6) {
                        Text("HEALTH & PHARMACY")
                            .font(.system(size: 9.5, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Color(hex: "0E675E"))
                            .cornerRadius(4)
                        
                        HStack(spacing: 6) {
                            Image(systemName: "cross.case.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Color(hex: "0E675E"))
                            Text("Medicines & Healthcare Catalog")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                        
                        Text("100% Genuine • Temperature-Safe 10-Min Delivery")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    
                    Spacer()
                    
                    Image(systemName: "chevron.right")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                }
                .padding(14)
                .background(Color(hex: "ECFDF5"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "A7F3D0"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
            
            // Fast Prescriptions & Daily Needs
            if !featuredProducts.isEmpty {
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Popular Everyday Medicines")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Spacer()
                        Button(action: { onOpenCatalog("pharmacy") }) {
                            Text("See all")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "0E675E"))
                        }
                    }
                    
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(featuredProducts.prefix(4)) { prod in
                            CommerceProductCard(product: prod, variant: .grid, onSelect: onSelectProduct)
                        }
                    }
                }
            }
        }
    }
}

// MARK: - Food Vertical Section (Ported from Android FoodVerticalSection.kt)
public struct FoodVerticalSection: View {
    public let onOpenCatalog: (String?) -> Void
    
    private let cuisines: [(name: String, icon: String, color: String)] = [
        ("Biryani & Rice", "takeoutbag.and.cup.and.straw.fill", "F59E0B"),
        ("Burgers & Fast Food", "fork.knife", "EF4444"),
        ("Pizzas", "flame.fill", "DC2626"),
        ("Desserts & Sweets", "birthday.cake.fill", "EC4899"),
        ("Healthy Bowls", "leaf.fill", "10B981")
    ]
    
    public init(onOpenCatalog: @escaping (String?) -> Void) {
        self.onOpenCatalog = onOpenCatalog
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Cuisines & Dining Categories")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(cuisines, id: \.name) { item in
                        Button(action: { onOpenCatalog("food") }) {
                            VStack(spacing: 8) {
                                Circle()
                                    .fill(Color(hex: item.color).opacity(0.12))
                                    .frame(width: 54, height: 54)
                                    .overlay(
                                        Image(systemName: item.icon)
                                            .font(.system(size: 22))
                                            .foregroundColor(Color(hex: item.color))
                                    )
                                
                                Text(item.name)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                    .lineLimit(1)
                            }
                            .frame(width: 90)
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
            
            // Explore Food Catalog Banner
            Button(action: { onOpenCatalog("food") }) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Dining & Kitchens Catalog")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Explore Restaurants & Hot Food →")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "DC2626"))
                    }
                    Spacer()
                    Image(systemName: "fork.knife.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Color(hex: "DC2626").opacity(0.85))
                }
                .padding(14)
                .background(Color(hex: "FEF2F2"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "FECACA"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }
}

// MARK: - Fashion Vertical Section (Ported from Android FashionVerticalSection.kt)
public struct FashionVerticalSection: View {
    public let onOpenCatalog: (String?) -> Void
    
    private let fashionCategories: [(name: String, icon: String, color: String)] = [
        ("Men's Wear", "tshirt.fill", "3B82F6"),
        ("Women's Wear", "figure.dress.line.vertical.figure", "8B5CF6"),
        ("Footwear", "shoeprints.fill", "F59E0B"),
        ("Accessories", "eyeglasses", "EC4899")
    ]
    
    public init(onOpenCatalog: @escaping (String?) -> Void) {
        self.onOpenCatalog = onOpenCatalog
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Apparel & Fashion Collections")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(fashionCategories, id: \.name) { item in
                        Button(action: { onOpenCatalog("fashion") }) {
                            VStack(spacing: 8) {
                                Circle()
                                    .fill(Color(hex: item.color).opacity(0.12))
                                    .frame(width: 54, height: 54)
                                    .overlay(
                                        Image(systemName: item.icon)
                                            .font(.system(size: 22))
                                            .foregroundColor(Color(hex: item.color))
                                    )
                                
                                Text(item.name)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                    .lineLimit(1)
                            }
                            .frame(width: 85)
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
            
            Button(action: { onOpenCatalog("fashion") }) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Trending Styles & Brands")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Explore Fashion Storefront →")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "7C3AED"))
                    }
                    Spacer()
                    Image(systemName: "tag.circle.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Color(hex: "7C3AED").opacity(0.85))
                }
                .padding(14)
                .background(Color(hex: "FAF5FF"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "E9D5FF"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }
}

// MARK: - Electronics Vertical Section (Ported from Android ElectronicsVerticalSection.kt)
public struct ElectronicsVerticalSection: View {
    public let onOpenCatalog: (String?) -> Void
    
    private let gadgets: [(name: String, icon: String, color: String)] = [
        ("Cables & Chargers", "cable.connector", "2563EB"),
        ("Audio & Earphones", "headphones", "059669"),
        ("Smart Watches", "applewatch", "7C3AED"),
        ("Phone Cases", "iphone", "D97706")
    ]
    
    public init(onOpenCatalog: @escaping (String?) -> Void) {
        self.onOpenCatalog = onOpenCatalog
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Electronics & Mobile Accessories")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(gadgets, id: \.name) { item in
                        Button(action: { onOpenCatalog("electronics") }) {
                            VStack(spacing: 8) {
                                Circle()
                                    .fill(Color(hex: item.color).opacity(0.12))
                                    .frame(width: 54, height: 54)
                                    .overlay(
                                        Image(systemName: item.icon)
                                            .font(.system(size: 22))
                                            .foregroundColor(Color(hex: item.color))
                                    )
                                
                                Text(item.name)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                    .lineLimit(1)
                            }
                            .frame(width: 95)
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
            
            Button(action: { onOpenCatalog("electronics") }) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Express Tech Hub")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Shop Cables, Power Banks & Audio →")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "2563EB"))
                    }
                    Spacer()
                    Image(systemName: "bolt.badge.clock.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Color(hex: "2563EB").opacity(0.85))
                }
                .padding(14)
                .background(Color(hex: "EFF6FF"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "BFDBFE"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }
}

// MARK: - Services Vertical Section (Ported from Android ServicesVerticalSection.kt)
public struct ServicesVerticalSection: View {
    public let onOpenCatalog: (String?) -> Void
    
    private let homeServices: [(name: String, icon: String, color: String)] = [
        ("Plumbing", "wrench.adjustable.fill", "D97706"),
        ("Electrical", "bolt.fill", "2563EB"),
        ("Cleaning", "sparkles", "10B981"),
        ("AC Repair", "snowflake", "0284C7")
    ]
    
    public init(onOpenCatalog: @escaping (String?) -> Void) {
        self.onOpenCatalog = onOpenCatalog
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            Text("Doorstep Home Services")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 10) {
                    ForEach(homeServices, id: \.name) { item in
                        Button(action: { onOpenCatalog("services") }) {
                            VStack(spacing: 8) {
                                Circle()
                                    .fill(Color(hex: item.color).opacity(0.12))
                                    .frame(width: 54, height: 54)
                                    .overlay(
                                        Image(systemName: item.icon)
                                            .font(.system(size: 22))
                                            .foregroundColor(Color(hex: item.color))
                                    )
                                
                                Text(item.name)
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                    .lineLimit(1)
                            }
                            .frame(width: 85)
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
            
            Button(action: { onOpenCatalog("services") }) {
                HStack {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Verified Doorstep Technicians")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Book Instant Diagnostics & Repairs →")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "D97706"))
                    }
                    Spacer()
                    Image(systemName: "wrench.and.screwdriver.fill")
                        .font(.system(size: 32))
                        .foregroundColor(Color(hex: "D97706").opacity(0.85))
                }
                .padding(14)
                .background(Color(hex: "FFFBEB"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "FDE68A"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
    }
}
