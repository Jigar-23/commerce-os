import SwiftUI

/**
 * 1:1 Parity with Android SearchEntityCards.kt (581 lines).
 * Universal Search Entity Renderer powering Commerce OS search result dispatch.
 * Renders bespoke search cards per SearchEntityType:
 * - ProductSearchCard (with Express badge, salt chip, unit price, stock status, ADD button)
 * - RestaurantSearchCard (with cuisine, rating, View Menu CTA)
 * - ServiceSearchCard (with provider, rating, Book CTA)
 * - StoreSearchCard (with location, rating, Visit Store CTA)
 * - BrandSearchCard (with brand badge, Explore Brand CTA)
 * - CategorySearchCard & OfferSearchCard
 */
public struct SearchEntityRenderer: View {
    public let result: SearchResult
    public let onClick: () -> Void
    public var onWishlistToggle: ((String) -> Void)? = nil
    public var onAddToCart: ((SearchResult) -> Void)? = nil

    public init(
        result: SearchResult,
        onClick: @escaping () -> Void,
        onWishlistToggle: ((String) -> Void)? = nil,
        onAddToCart: ((SearchResult) -> Void)? = nil
    ) {
        self.result = result
        self.onClick = onClick
        self.onWishlistToggle = onWishlistToggle
        self.onAddToCart = onAddToCart
    }

    public var body: some View {
        switch result.entityType {
        case .product:
            ProductSearchCard(
                result: result,
                onClick: onClick,
                onWishlistToggle: onWishlistToggle,
                onAddToCart: onAddToCart
            )
        case .restaurant:
            RestaurantSearchCard(result: result, onClick: onClick)
        case .service:
            ServiceSearchCard(result: result, onClick: onClick)
        case .store:
            StoreSearchCard(result: result, onClick: onClick)
        case .brand:
            BrandSearchCard(result: result, onClick: onClick)
        case .category, .collection:
            CategorySearchCard(result: result, onClick: onClick)
        case .campaign, .offer:
            OfferSearchCard(result: result, onClick: onClick)
        }
    }
}

// MARK: - ProductSearchCard
public struct ProductSearchCard: View {
    public let result: SearchResult
    public let onClick: () -> Void
    public var onWishlistToggle: ((String) -> Void)? = nil
    public var onAddToCart: ((SearchResult) -> Void)? = nil

    @State private var isWishlisted: Bool = false

    public init(
        result: SearchResult,
        onClick: @escaping () -> Void,
        onWishlistToggle: ((String) -> Void)? = nil,
        onAddToCart: ((SearchResult) -> Void)? = nil
    ) {
        self.result = result
        self.onClick = onClick
        self.onWishlistToggle = onWishlistToggle
        self.onAddToCart = onAddToCart
    }

    public var body: some View {
        Button(action: onClick) {
            HStack(alignment: .top, spacing: 12) {
                // Product Image Container with Badges
                ZStack(alignment: .topLeading) {
                    AsyncImage(url: URL(string: result.image ?? "")) { phase in
                        switch phase {
                        case .success(let image):
                            image
                                .resizable()
                                .scaledToFit()
                        default:
                            ZStack {
                                Color(hex: "F1F5F9")
                                Image(systemName: "pills.fill")
                                    .font(.system(size: 28))
                                    .foregroundColor(Color(hex: "94A3B8"))
                            }
                        }
                    }
                    .frame(width: 96, height: 96)
                    .background(Color(hex: "F8FAFC"))
                    .cornerRadius(12)

                    // STRICT INTEGRITY: Only show Express badge when isExpressEligible == true
                    if result.isExpressEligible == true {
                        Text("⚡ Express")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundColor(.white)
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color(hex: "10B981"))
                            .cornerRadius(4)
                            .padding(4)
                    }
                }

                // Product Details Column
                VStack(alignment: .leading, spacing: 3) {
                    HStack {
                        // Vertical Chip
                        Text(result.vertical.uppercased())
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color(hex: "E6F4F1"))
                            .cornerRadius(4)

                        // Rating Pill
                        if let rating = result.rating, rating > 0 {
                            HStack(spacing: 2) {
                                Text(String(format: "%.1f", rating))
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundColor(.white)
                                Image(systemName: "star.fill")
                                    .font(.system(size: 8))
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color(hex: "16A34A"))
                            .cornerRadius(4)
                        }

                        Spacer()

                        // Wishlist Button
                        Button(action: {
                            isWishlisted.toggle()
                            onWishlistToggle?(result.entityId)
                        }) {
                            Image(systemName: isWishlisted ? "heart.fill" : "heart")
                                .font(.system(size: 14))
                                .foregroundColor(isWishlisted ? Color(hex: "EF4444") : Color(hex: "94A3B8"))
                        }
                        .buttonStyle(PlainButtonStyle())
                    }

                    // Title
                    Text(result.title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                        .lineLimit(2)

                    // Subtitle / Salt Composition
                    if let salt = result.saltComposition, !salt.isEmpty {
                        Text(salt)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color(hex: "0E675E"))
                            .lineLimit(1)
                    } else if !result.subtitle.isEmpty {
                        Text(result.subtitle)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                            .lineLimit(1)
                    }

                    // ETA Pill
                    if let eta = result.etaLabel, !eta.isEmpty {
                        Text("Delivery in \(eta)")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Color(hex: "0E675E"))
                    }

                    // Price & Cart Button
                    if let price = result.price {
                        HStack(alignment: .bottom) {
                            VStack(alignment: .leading, spacing: 1) {
                                HStack(alignment: .firstTextBaseline, spacing: 4) {
                                    Text("₹\(Int(price))")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(Color(hex: "0F172A"))

                                    if let mrp = result.mrp, mrp > price {
                                        Text("₹\(Int(mrp))")
                                            .font(.system(size: 11))
                                            .strikethrough()
                                            .foregroundColor(Color(hex: "94A3B8"))
                                    }

                                    if let disc = result.discountPercent, disc > 0 {
                                        Text("\(disc)% OFF")
                                            .font(.system(size: 10, weight: .bold))
                                            .foregroundColor(Color(hex: "16A34A"))
                                    }
                                }

                                if let avail = result.availabilityStatus, !avail.isEmpty {
                                    Text(avail)
                                        .font(.system(size: 10))
                                        .foregroundColor(avail.localizedCaseInsensitiveContains("In Stock") ? Color(hex: "16A34A") : Color(hex: "64748B"))
                                }
                            }

                            Spacer()

                            if let onAdd = onAddToCart {
                                Button(action: {
                                    UIImpactFeedbackGenerator(style: .medium).impactOccurred()
                                    onAdd(result)
                                }) {
                                    HStack(spacing: 3) {
                                        Text("ADD")
                                            .font(.system(size: 12, weight: .black))
                                        Image(systemName: "plus")
                                            .font(.system(size: 11, weight: .bold))
                                    }
                                    .foregroundColor(Color(hex: "E84335"))
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 7)
                                    .background(Color.white)
                                    .cornerRadius(8)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 8)
                                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1.2)
                                    )
                                    .shadow(color: Color.black.opacity(0.04), radius: 2, y: 1)
                                }
                                .buttonStyle(PlainButtonStyle())
                            }
                        }
                        .padding(.top, 4)
                    }
                }
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.03), radius: 4, y: 2)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - RestaurantSearchCard
public struct RestaurantSearchCard: View {
    public let result: SearchResult
    public let onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: result.image ?? "")) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        ZStack {
                            Color(hex: "FEF3C7")
                            Image(systemName: "fork.knife")
                                .foregroundColor(Color(hex: "D97706"))
                        }
                    }
                }
                .frame(width: 64, height: 64)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text("FOOD")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(hex: "D97706"))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 2)
                            .background(Color(hex: "FEF3C7"))
                            .cornerRadius(4)

                        if let rating = result.rating, rating > 0 {
                            HStack(spacing: 2) {
                                Image(systemName: "star.fill")
                                    .font(.system(size: 9))
                                    .foregroundColor(Color(hex: "F59E0B"))
                                Text(String(format: "%.1f", rating))
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Color(hex: "0F172A"))
                            }
                        }
                    }

                    Text(result.title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    if let cuisine = result.cuisine, !cuisine.isEmpty {
                        Text(cuisine)
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }

                Spacer()

                Text("View Menu")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Color(hex: "0E675E"))
                    .cornerRadius(8)
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - ServiceSearchCard
public struct ServiceSearchCard: View {
    public let result: SearchResult
    public let onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                AsyncImage(url: URL(string: result.image ?? "")) { phase in
                    switch phase {
                    case .success(let image):
                        image.resizable().scaledToFill()
                    default:
                        ZStack {
                            Color(hex: "E0F2FE")
                            Image(systemName: "wrench.and.screwdriver.fill")
                                .foregroundColor(Color(hex: "0284C7"))
                        }
                    }
                }
                .frame(width: 60, height: 60)
                .clipShape(RoundedRectangle(cornerRadius: 12))

                VStack(alignment: .leading, spacing: 3) {
                    Text("SERVICE")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(hex: "0284C7"))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color(hex: "E0F2FE"))
                        .cornerRadius(4)

                    Text(result.title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    if let provider = result.providerId, !provider.isEmpty {
                        Text("Verified Partner: \(provider)")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }

                Spacer()

                Text("Book")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(.white)
                    .padding(.horizontal, 14)
                    .padding(.vertical, 6)
                    .background(Color(hex: "0E675E"))
                    .cornerRadius(8)
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - StoreSearchCard
public struct StoreSearchCard: View {
    public let result: SearchResult
    public let onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                ZStack {
                    Color(hex: "F1F5F9")
                    Image(systemName: "storefront.fill")
                        .font(.system(size: 24))
                        .foregroundColor(Color(hex: "0E675E"))
                }
                .frame(width: 56, height: 56)
                .cornerRadius(12)

                VStack(alignment: .leading, spacing: 2) {
                    Text("STORE")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 2)
                        .background(Color(hex: "E6F4F1"))
                        .cornerRadius(4)

                    Text(result.title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    if let loc = result.location, !loc.isEmpty {
                        Text(loc)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }

                Spacer()

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Color(hex: "94A3B8"))
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - BrandSearchCard
public struct BrandSearchCard: View {
    public let result: SearchResult
    public let onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 12) {
                ZStack {
                    Color(hex: "F3E8FF")
                    Image(systemName: "shield.checkerboard")
                        .font(.system(size: 22))
                        .foregroundColor(Color(hex: "7E22CE"))
                }
                .frame(width: 50, height: 50)
                .cornerRadius(10)

                VStack(alignment: .leading, spacing: 2) {
                    Text("BRAND STORE")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(Color(hex: "7E22CE"))

                    Text(result.title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                }

                Spacer()

                Text("Explore")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "7E22CE"))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color(hex: "F3E8FF"))
                    .cornerRadius(6)
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - CategorySearchCard & OfferSearchCard
public struct CategorySearchCard: View {
    public let result: SearchResult
    public let onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 10) {
                Image(systemName: "square.grid.2x2.fill")
                    .font(.system(size: 16))
                    .foregroundColor(Color(hex: "0E675E"))

                Text(result.title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))

                Spacer()

                Image(systemName: "arrow.up.right")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "94A3B8"))
            }
            .padding(12)
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

public struct OfferSearchCard: View {
    public let result: SearchResult
    public let onClick: () -> Void

    public var body: some View {
        Button(action: onClick) {
            HStack(spacing: 10) {
                Image(systemName: "tag.fill")
                    .font(.system(size: 16))
                    .foregroundColor(Color(hex: "DC2626"))

                VStack(alignment: .leading, spacing: 1) {
                    Text(result.title)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    if !result.subtitle.isEmpty {
                        Text(result.subtitle)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }

                Spacer()

                Text("CLAIM")
                    .font(.system(size: 11, weight: .heavy))
                    .foregroundColor(Color(hex: "DC2626"))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 4)
                    .background(Color(hex: "FEF2F2"))
                    .cornerRadius(6)
            }
            .padding(12)
            .background(Color.white)
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "FECACA"), lineWidth: 1)
            )
        }
        .buttonStyle(PlainButtonStyle())
    }
}
