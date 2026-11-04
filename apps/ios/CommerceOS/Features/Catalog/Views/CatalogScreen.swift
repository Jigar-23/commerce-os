import SwiftUI

/**
 * 1:1 Parity with Android CatalogScreen.kt (248 lines).
 * Unified PLP: free-text search, category browsing, and full catalog share this destination.
 * All filters (price band, destination) are applied server-side and the list paginates
 * through limit/offset pages with a real hasMore flag.
 */
public struct CatalogScreen: View {
    @ObservedObject public var viewModel: CatalogViewModel
    public var onSelectProduct: (ProductDto) -> Void = { _ in }
    public var onAddToCart: (ProductDto) -> Void = { _ in }
    public var onBack: (() -> Void)? = nil

    public init(
        viewModel: CatalogViewModel = .shared,
        onSelectProduct: @escaping (ProductDto) -> Void = { _ in },
        onAddToCart: @escaping (ProductDto) -> Void = { _ in },
        onBack: (() -> Void)? = nil
    ) {
        self.viewModel = viewModel
        self.onSelectProduct = onSelectProduct
        self.onAddToCart = onAddToCart
        self.onBack = onBack
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // 1. Search Bar with optional back button
            HStack(spacing: 8) {
                if let onBack = onBack {
                    Button(action: onBack) {
                        Image(systemName: "arrow.left")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                            .padding(8)
                            .background(Color.white)
                            .clipShape(Circle())
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                CatalogSearchBar(viewModel: viewModel)
            }

            // 2. Destination Filter Row
            if !viewModel.destinations.isEmpty {
                DestinationRow(
                    destinations: viewModel.destinations,
                    selectedId: viewModel.selectedDestinationId,
                    onSelect: { viewModel.setDestination($0) }
                )
            }

            // 3. Price Band Row
            PriceBandRow(
                selected: viewModel.selectedPriceBand,
                onSelect: { viewModel.setPriceBand($0) }
            )

            // 4. Main Catalog Content Area
            switch viewModel.uiState {
            case .loading:
                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(0..<6, id: \.self) { _ in
                            CatalogGridSkeleton()
                        }
                    }
                    .padding(.top, 4)
                }

            case .empty:
                VStack(spacing: 12) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 44))
                        .foregroundColor(Color(hex: "94A3B8"))
                        .padding(.top, 24)

                    Text("No products match these filters")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))

                    Text("Try a different search, category, or price range.")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))

                    Button(action: {
                        viewModel.reset()
                        viewModel.reload()
                    }) {
                        Text("Reset All Filters")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 16)
                            .padding(.vertical, 8)
                            .background(Color(hex: "0E675E"))
                            .cornerRadius(10)
                    }
                    .padding(.bottom, 24)
                }
                .frame(maxWidth: .infinity)
                .background(Color.white)
                .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(hex: "E2E8F0"), lineWidth: 1))
                .cornerRadius(16)
                .padding(.top, 16)

                Spacer()

            case .error(let message):
                VStack(spacing: 8) {
                    Text(message)
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: "DC2626"))
                        .multilineTextAlignment(.center)

                    Button("Retry") {
                        viewModel.reload()
                    }
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "0E675E"))
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 24)

                Spacer()

            case .content(let title, let results, let totalElements):
                HStack {
                    Text("\(title) • \(totalElements) items")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "64748B"))
                    Spacer()
                }

                ScrollView {
                    LazyVGrid(columns: [GridItem(.flexible(), spacing: 12), GridItem(.flexible(), spacing: 12)], spacing: 12) {
                        ForEach(results) { product in
                            CommerceProductCard(
                                product: product,
                                variant: .grid,
                                onSelect: { prod in onSelectProduct(prod) }
                            )
                        }
                    }

                    if viewModel.hasMore || viewModel.isLoadingMore {
                        LoadMoreRow(
                            isLoadingMore: viewModel.isLoadingMore,
                            onLoadMore: { viewModel.loadMore() }
                        )
                        .padding(.vertical, 14)
                    }

                    Spacer().frame(height: 60)
                }
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 10)
        .background(Color(hex: "F8FAFC"))
        .onAppear {
            if case .loading = viewModel.uiState {
                viewModel.reload()
            }
        }
    }
}

// MARK: - Search Bar Component
public struct CatalogSearchBar: View {
    @ObservedObject public var viewModel: CatalogViewModel

    public var body: some View {
        HStack(spacing: 8) {
            Image(systemName: "magnifyingglass")
                .font(.system(size: 16))
                .foregroundColor(Color(hex: "64748B"))

            TextField(
                "Search in catalog...",
                text: Binding(
                    get: { viewModel.query },
                    set: { viewModel.onQueryChange($0) }
                ),
                onCommit: {
                    viewModel.submitQuery()
                }
            )
            .font(.system(size: 14))
            .foregroundColor(Color(hex: "0F172A"))
            .accentColor(Color(hex: "0E675E"))

            if !viewModel.query.isEmpty {
                Button(action: {
                    viewModel.onQueryChange("")
                    viewModel.submitQuery()
                }) {
                    Image(systemName: "xmark.circle.fill")
                        .font(.system(size: 16))
                        .foregroundColor(Color(hex: "94A3B8"))
                }
            }
        }
        .padding(.horizontal, 14)
        .frame(height: 44)
        .background(Color.white)
        .overlay(
            RoundedRectangle(cornerRadius: 18)
                .stroke(Color(hex: "CBD5E1"), lineWidth: 1)
        )
        .cornerRadius(18)
    }
}

// MARK: - Destination Row Component
public struct DestinationRow: View {
    public let destinations: [Destination]
    public let selectedId: String?
    public let onSelect: (String?) -> Void

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("DELIVERY DESTINATION")
                .font(.system(size: 9.5, weight: .heavy))
                .foregroundColor(Color(hex: "64748B"))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(destinations) { dest in
                        let isSelected = dest.id == selectedId
                        Button(action: {
                            onSelect(isSelected ? nil : dest.id)
                        }) {
                            Text(dest.name)
                                .font(.system(size: 11, weight: isSelected ? .heavy : .medium))
                                .foregroundColor(isSelected ? .white : Color(hex: "0F172A"))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 5)
                                .background(isSelected ? Color(hex: "0E675E") : Color.white)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 14)
                                        .stroke(isSelected ? Color(hex: "0E675E") : Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                                .cornerRadius(14)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
    }
}

// MARK: - Price Band Row Component
public struct PriceBandRow: View {
    public let selected: PriceBand
    public let onSelect: (PriceBand) -> Void

    public var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("PRICE")
                .font(.system(size: 9.5, weight: .heavy))
                .foregroundColor(Color(hex: "64748B"))

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(CATALOG_PRICE_BANDS) { band in
                        let isSelected = band == selected
                        Button(action: { onSelect(band) }) {
                            Text(band.label)
                                .font(.system(size: 11, weight: isSelected ? .heavy : .medium))
                                .foregroundColor(isSelected ? .white : Color(hex: "0F172A"))
                                .padding(.horizontal, 12)
                                .padding(.vertical, 5)
                                .background(isSelected ? Color(hex: "16A34A") : Color.white)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 12)
                                        .stroke(isSelected ? Color(hex: "16A34A") : Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                                .cornerRadius(12)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
    }
}

// MARK: - Skeleton Loading Component
public struct CatalogGridSkeleton: View {
    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Rectangle()
                .fill(Color(hex: "E2E8F0"))
                .aspectRatio(1, contentMode: .fit)
                .cornerRadius(12)

            Rectangle()
                .fill(Color(hex: "E2E8F0"))
                .frame(height: 14)
                .cornerRadius(4)

            Rectangle()
                .fill(Color(hex: "E2E8F0"))
                .frame(width: 80, height: 12)
                .cornerRadius(4)

            Rectangle()
                .fill(Color(hex: "E2E8F0"))
                .frame(width: 60, height: 14)
                .cornerRadius(4)
        }
        .padding(10)
        .background(Color.white)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Color(hex: "F1F5F9"), lineWidth: 1))
        .cornerRadius(16)
    }
}

// MARK: - Load More Row Component
public struct LoadMoreRow: View {
    public let isLoadingMore: Bool
    public let onLoadMore: () -> Void

    public var body: some View {
        HStack {
            Spacer()
            if isLoadingMore {
                ProgressView()
                    .progressViewStyle(CircularProgressViewStyle(tint: Color(hex: "0E675E")))
                    .scaleEffect(1.1)
            } else {
                Button(action: onLoadMore) {
                    Text("Load More Products")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                        .padding(.horizontal, 24)
                        .padding(.vertical, 10)
                        .background(Color(hex: "F0FDF4"))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(hex: "A7F3D0"), lineWidth: 1)
                        )
                        .cornerRadius(12)
                }
                .buttonStyle(PlainButtonStyle())
            }
            Spacer()
        }
    }
}
