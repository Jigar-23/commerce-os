import SwiftUI

public struct CategoryTaxonomyItem: Identifiable, Codable {
    public let id: String
    public let name: String
    public let verticalId: String?
}

public struct CategoriesScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @State private var categories: [CategoryTaxonomyItem] = []
    @State private var isLoading: Bool = false
    @State private var selectedCategory: CategoryTaxonomyItem? = nil
    
    public init() {}
    
    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Categories")
                            .font(.system(size: 24, weight: .bold))
                            .foregroundColor(.primary)
                        Text("Browse \(configProvider.currentConfig.identity.clientName) catalog taxonomy")
                            .font(.system(size: 13))
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    
                    if isLoading && categories.isEmpty {
                        ProgressView()
                            .frame(maxWidth: .infinity, minHeight: 200)
                    } else {
                        LazyVGrid(columns: [GridItem(.flexible(), spacing: 14), GridItem(.flexible(), spacing: 14)], spacing: 14) {
                            ForEach(categories) { category in
                                CategoryCardView(category: category) {
                                    selectedCategory = category
                                }
                            }
                        }
                        .padding(.horizontal, 16)
                    }
                }
                .padding(.bottom, 80)
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: { Task { await loadTaxonomy() } }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
            }
            .onAppear {
                Task {
                    await loadTaxonomy()
                }
            }
            .sheet(item: $selectedCategory) { cat in
                NavigationView {
                    CatalogScreen()
                        .navigationTitle(cat.name)
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .navigationBarLeading) {
                                Button("Close") {
                                    selectedCategory = nil
                                }
                            }
                        }
                }
            }
        }
    }
    
    private func loadTaxonomy() async {
        await MainActor.run { isLoading = true }
        do {
            let fetched: [CategoryTaxonomyItem] = try await container.apiClient.get(endpoint: "/api/v1/catalog/categories")
            await MainActor.run {
                self.categories = fetched
                self.isLoading = false
            }
        } catch {
            await MainActor.run {
                self.categories = [
                    CategoryTaxonomyItem(id: "medicines", name: "Medicines", verticalId: "pharma"),
                    CategoryTaxonomyItem(id: "wellness", name: "Wellness & Nutrition", verticalId: "pharma"),
                    CategoryTaxonomyItem(id: "personal-care", name: "Personal Care", verticalId: "pharma"),
                    CategoryTaxonomyItem(id: "first-aid", name: "First Aid & Devices", verticalId: "pharma"),
                    CategoryTaxonomyItem(id: "baby-care", name: "Baby Care", verticalId: "pharma"),
                    CategoryTaxonomyItem(id: "ayurveda", name: "Ayurveda & Herbals", verticalId: "pharma")
                ]
                self.isLoading = false
            }
        }
    }
}

public struct CategoryCardView: View {
    public let category: CategoryTaxonomyItem
    public let onTap: () -> Void
    
    public init(category: CategoryTaxonomyItem, onTap: @escaping () -> Void) {
        self.category = category
        self.onTap = onTap
    }
    
    public var body: some View {
        Button(action: onTap) {
            VStack(alignment: .leading, spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 12)
                        .fill(categoryColor.opacity(0.12))
                        .frame(height: 80)
                    
                    Image(systemName: categoryIcon)
                        .font(.system(size: 32))
                        .foregroundColor(categoryColor)
                }
                
                VStack(alignment: .leading, spacing: 2) {
                    Text(category.name)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.primary)
                        .lineLimit(1)
                    
                    Text("Browse catalog")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(.secondary)
                }
            }
            .padding(10)
            .background(Color(.systemBackground))
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var categoryIcon: String {
        let lower = category.id.lowercased()
        if lower.contains("medicine") || lower.contains("pharma") { return "pills.fill" }
        if lower.contains("well") || lower.contains("nutri") { return "heart.text.square.fill" }
        if lower.contains("care") || lower.contains("person") { return "sparkles" }
        if lower.contains("aid") || lower.contains("device") { return "cross.case.fill" }
        if lower.contains("baby") { return "figure.and.child.holdinghands" }
        if lower.contains("ayur") || lower.contains("herb") { return "leaf.fill" }
        if lower.contains("groc") { return "cart.fill" }
        return "square.grid.2x2.fill"
    }
    
    private var categoryColor: Color {
        let lower = category.id.lowercased()
        if lower.contains("medicine") { return Color(hex: "0284C7") }
        if lower.contains("well") || lower.contains("groc") { return CommerceOSTheme.Colors.brandPrimary }
        if lower.contains("care") { return Color(hex: "DB2777") }
        if lower.contains("aid") { return Color(hex: "DC2626") }
        if lower.contains("baby") { return Color(hex: "7C3AED") }
        if lower.contains("ayur") { return Color(hex: "059669") }
        return CommerceOSTheme.Colors.brandAccent
    }
}
