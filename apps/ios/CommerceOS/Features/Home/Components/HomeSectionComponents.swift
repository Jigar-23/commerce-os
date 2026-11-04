import SwiftUI

/**
 * 1:1 Parity with Android HomeSectionComponents.kt (620 lines).
 * Universal Home Section Renderer powering Commerce OS feed layout.
 * Dispatches server-authored section types to specialized visual section composables.
 */

public struct HomeFeedSection: Identifiable, Equatable {
    public let id: String
    public let type: String
    public let title: String?
    public let subtitle: String?
    public let priority: Int
    public let products: [ProductDto]
    public let heroDto: HomeHeroDto?

    public init(
        id: String = UUID().uuidString,
        type: String,
        title: String? = nil,
        subtitle: String? = nil,
        priority: Int = 0,
        products: [ProductDto] = [],
        heroDto: HomeHeroDto? = nil
    ) {
        self.id = id
        self.type = type
        self.title = title
        self.subtitle = subtitle
        self.priority = priority
        self.products = products
        self.heroDto = heroDto
    }

    public static func == (lhs: HomeFeedSection, rhs: HomeFeedSection) -> Bool {
        lhs.id == rhs.id
    }
}

// MARK: - Section Header View
public struct SectionHeaderView: View {
    public let title: String?
    public let subtitle: String?
    public let actionLabel: String?
    public let onAction: (() -> Void)?

    public init(
        title: String? = nil,
        subtitle: String? = nil,
        actionLabel: String? = nil,
        onAction: (() -> Void)? = nil
    ) {
        self.title = title
        self.subtitle = subtitle
        self.actionLabel = actionLabel
        self.onAction = onAction
    }

    public var body: some View {
        HStack(alignment: .firstTextBaseline) {
            VStack(alignment: .leading, spacing: 2) {
                if let title = title, !title.isEmpty {
                    Text(title)
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                }
                if let subtitle = subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))
                }
            }
            Spacer()
            if let actionLabel = actionLabel, let onAction = onAction {
                Button(action: onAction) {
                    Text(actionLabel)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                }
            }
        }
        .padding(.horizontal, 14)
    }
}

// MARK: - Product Row Section
public struct ProductRowSectionView: View {
    public let title: String?
    public let subtitle: String?
    public let products: [ProductDto]
    public let actionLabel: String?
    public let onAction: (() -> Void)?
    public let onSelectProduct: (ProductDto) -> Void

    public init(
        title: String? = nil,
        subtitle: String? = nil,
        products: [ProductDto],
        actionLabel: String? = nil,
        onAction: (() -> Void)? = nil,
        onSelectProduct: @escaping (ProductDto) -> Void = { _ in }
    ) {
        self.title = title
        self.subtitle = subtitle
        self.products = products
        self.actionLabel = actionLabel
        self.onAction = onAction
        self.onSelectProduct = onSelectProduct
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeaderView(
                title: title,
                subtitle: subtitle,
                actionLabel: actionLabel,
                onAction: onAction
            )

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 12) {
                    ForEach(products) { prod in
                        CommerceProductCard(
                            product: prod,
                            variant: .grid,
                            onSelect: onSelectProduct
                        )
                        .frame(width: 160)
                    }
                }
                .padding(.horizontal, 14)
            }
        }
    }
}

// MARK: - Deal Card
public struct DealCardView: View {
    public let product: ProductDto
    public let onSelect: (ProductDto) -> Void

    public init(product: ProductDto, onSelect: @escaping (ProductDto) -> Void) {
        self.product = product
        self.onSelect = onSelect
    }

    private var discountPercentage: Int {
        if product.mrp > product.price {
            return Int(round(((product.mrp - product.price) / product.mrp) * 100))
        }
        return 0
    }

    public var body: some View {
        Button(action: { onSelect(product) }) {
            VStack(alignment: .leading, spacing: 6) {
                ZStack(alignment: .topTrailing) {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(Color(hex: "F8FAFC"))
                        .aspectRatio(1, contentMode: .fit)

                    if discountPercentage > 0 {
                        Text("\(discountPercentage)% OFF")
                            .font(.system(size: 9, weight: .heavy))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 3)
                            .background(Color(hex: "DC2626"))
                            .cornerRadius(6)
                            .padding(6)
                    }
                }

                Text(product.name)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                    .lineLimit(1)

                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("₹\(Int(product.price))")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(Color(hex: "0E675E"))

                    if product.mrp > product.price {
                        Text("₹\(Int(product.mrp))")
                            .font(.system(size: 11))
                            .strikethrough()
                            .foregroundColor(Color(hex: "94A3B8"))
                    }
                }
            }
            .padding(10)
            .background(Color.white)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "E2E8F0"), lineWidth: 1))
            .cornerRadius(14)
        }
        .buttonStyle(PlainButtonStyle())
    }
}

// MARK: - Deal Grid Section
public struct DealGridSectionView: View {
    public let title: String?
    public let subtitle: String?
    public let products: [ProductDto]
    public let onSelectProduct: (ProductDto) -> Void

    public init(
        title: String? = "Flash Deals of the Day",
        subtitle: String? = "Limited stock available at deep discounts",
        products: [ProductDto],
        onSelectProduct: @escaping (ProductDto) -> Void
    ) {
        self.title = title
        self.subtitle = subtitle
        self.products = products
        self.onSelectProduct = onSelectProduct
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            SectionHeaderView(title: title, subtitle: subtitle, actionLabel: "See all deals", onAction: {})

            LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                ForEach(products.prefix(4)) { prod in
                    DealCardView(product: prod, onSelect: onSelectProduct)
                }
            }
            .padding(.horizontal, 14)
        }
    }
}

// MARK: - Editorial Card View
public struct EditorialCardView: View {
    public let hero: HomeHeroDto
    public let onCta: () -> Void

    public init(hero: HomeHeroDto, onCta: @escaping () -> Void) {
        self.hero = hero
        self.onCta = onCta
    }

    public var body: some View {
        ZStack(alignment: .leading) {
            RoundedRectangle(cornerRadius: 18)
                .fill(
                    LinearGradient(
                        colors: [
                            Color(hex: "0E675E"),
                            Color(hex: "09443E")
                        ],
                        startPoint: .topLeading,
                        endPoint: .bottomTrailing
                    )
                )

            VStack(alignment: .leading, spacing: 8) {
                if let badge = hero.badge, !badge.isEmpty {
                    Text(badge)
                        .font(.system(size: 8.5, weight: .black))
                        .foregroundColor(Color(hex: "0E675E"))
                        .padding(.horizontal, 7)
                        .padding(.vertical, 3)
                        .background(Color.white)
                        .cornerRadius(6)
                }

                Text(hero.title)
                    .font(.system(size: 17, weight: .heavy))
                    .foregroundColor(.white)
                    .lineLimit(1)

                if let subtitle = hero.subtitle, !subtitle.isEmpty {
                    Text(subtitle)
                        .font(.system(size: 11.5))
                        .foregroundColor(Color.white.opacity(0.85))
                        .lineLimit(2)
                }

                Button(action: onCta) {
                    Text(hero.ctaText ?? "Shop Story →")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.white)
                        .cornerRadius(8)
                }
                .buttonStyle(PlainButtonStyle())
                .padding(.top, 4)
            }
            .padding(16)
        }
        .frame(height: 140)
        .padding(.horizontal, 14)
    }
}

// MARK: - Home Section Renderer
public struct HomeSectionRendererView: View {
    public let section: HomeFeedSection
    public let onSelectProduct: (ProductDto) -> Void
    public let onOpenCatalog: () -> Void

    public init(
        section: HomeFeedSection,
        onSelectProduct: @escaping (ProductDto) -> Void = { _ in },
        onOpenCatalog: @escaping () -> Void = {}
    ) {
        self.section = section
        self.onSelectProduct = onSelectProduct
        self.onOpenCatalog = onOpenCatalog
    }

    public var body: some View {
        switch section.type.lowercased() {
        case "hero_campaign", "hero":
            if let hero = section.heroDto {
                EditorialCardView(hero: hero, onCta: onOpenCatalog)
            }
        case "top_deals", "deal_grid":
            DealGridSectionView(
                title: section.title,
                subtitle: section.subtitle,
                products: section.products,
                onSelectProduct: onSelectProduct
            )
        case "buy_again", "reorder":
            ProductRowSectionView(
                title: section.title ?? "Buy Again",
                subtitle: section.subtitle ?? "Frequently ordered daily essentials",
                products: section.products,
                actionLabel: "View all",
                onAction: onOpenCatalog,
                onSelectProduct: onSelectProduct
            )
        default:
            ProductRowSectionView(
                title: section.title ?? "Recommended For You",
                subtitle: section.subtitle,
                products: section.products,
                actionLabel: "See all",
                onAction: onOpenCatalog,
                onSelectProduct: onSelectProduct
            )
        }
    }
}
