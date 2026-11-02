import SwiftUI

public struct HealthcareCategoryItem: Identifiable, Codable {
    public let id: String
    public let name: String
    public let emoji: String
    public let highlights: String
    public let productCount: Int
    public let iconBg: String
    public let iconTint: String

    public init(id: String, name: String, emoji: String, highlights: String, productCount: Int, iconBg: String, iconTint: String) {
        self.id = id
        self.name = name
        self.emoji = emoji
        self.highlights = highlights
        self.productCount = productCount
        self.iconBg = iconBg
        self.iconTint = iconTint
    }
}

public struct CategoriesScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @State private var selectedCategory: HealthcareCategoryItem? = nil

    private let defaultCategories: [HealthcareCategoryItem] = [
        HealthcareCategoryItem(id: "cat_pain_fever", name: "Pain & Fever", emoji: "🌡️", highlights: "Paracetamol, Dolo, Balms", productCount: 18, iconBg: "FEF2F2", iconTint: "EF4444"),
        HealthcareCategoryItem(id: "cat_cold_cough", name: "Cold & Cough", emoji: "🤧", highlights: "Syrups, Lozenges, Inhalers", productCount: 14, iconBg: "F0F9FF", iconTint: "0284C7"),
        HealthcareCategoryItem(id: "cat_diabetes_care", name: "Diabetes Care", emoji: "🩺", highlights: "Glucometers, Strips, Care", productCount: 12, iconBg: "EEF2FF", iconTint: "4F46E5"),
        HealthcareCategoryItem(id: "cat_antibiotics", name: "Antibiotics", emoji: "🧪", highlights: "Amoxiclav, Azithral", productCount: 9, iconBg: "F0FDFA", iconTint: "0D9488"),
        HealthcareCategoryItem(id: "cat_vitamins", name: "Vitamins & Daily Wellness", emoji: "🌿", highlights: "Multivitamins, Zinc, Calcium", productCount: 24, iconBg: "ECFDF5", iconTint: "059669"),
        HealthcareCategoryItem(id: "cat_digestive", name: "Stomach & Digestion", emoji: "💊", highlights: "Antacids, Digene, ORS", productCount: 16, iconBg: "FFFBEB", iconTint: "D97706"),
        HealthcareCategoryItem(id: "cat_cardiac", name: "Heart & Blood Pressure", emoji: "❤️", highlights: "BP Monitors, Heart Health", productCount: 8, iconBg: "FDF2F8", iconTint: "DB2777"),
        HealthcareCategoryItem(id: "cat_first_aid", name: "First Aid & Essentials", emoji: "🩹", highlights: "Bandages, Antiseptic, Dettol", productCount: 20, iconBg: "FFF1F2", iconTint: "E11D48"),
        HealthcareCategoryItem(id: "cat_skin_care", name: "Skin & Dermatology", emoji: "✨", highlights: "Derma Creams, Lotions", productCount: 15, iconBg: "FAF5FF", iconTint: "9333EA"),
        HealthcareCategoryItem(id: "cat_baby_care", name: "Baby & Mother Care", emoji: "👶", highlights: "Diapers, Wipes, Gripe Water", productCount: 11, iconBg: "EFF6FF", iconTint: "2563EB")
    ]

    public init() {}

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                // Top Header (Matching Android CategoriesScreen.kt verbatim)
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Shop by Category")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Certified medicines & healthcare essentials")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "64748B"))
                    }

                    Spacer()

                    // SLA Badge (⚡ 10-Min Delivery)
                    HStack(spacing: 4) {
                        Text("⚡")
                            .font(.system(size: 11))
                        Text("10-Min Delivery")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "059669"))
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(Color(hex: "ECFDF5"))
                    .cornerRadius(20)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color(hex: "10B981").opacity(0.3), lineWidth: 1)
                    )
                }
                .padding(.horizontal, 14)
                .padding(.top, 4)

                // Reassuring Safety Banner (Subtle Healthcare Mint)
                HStack(spacing: 10) {
                    Circle()
                        .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                        .frame(width: 32, height: 32)
                        .overlay(
                            Image(systemName: "checkmark.shield.fill")
                                .font(.system(size: 15))
                                .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                        )

                    VStack(alignment: .leading, spacing: 1) {
                        Text("100% Genuine Medicines")
                            .font(CommerceOSTheme.Typography.caption().weight(.bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Sourced directly from licensed pharmacies • Cold chain maintained")
                            .font(.system(size: 10, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                            .lineLimit(1)
                    }

                    Spacer()
                }
                .padding(10)
                .background(Color.white)
                .cornerRadius(12)
                .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
                .padding(.horizontal, 14)

                // 2-Column Categories Grid (Modern Pastel Surfaces)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(defaultCategories) { cat in
                        Button(action: {
                            HapticFeedbackEngine.shared.triggerSelection()
                            selectedCategory = cat
                        }) {
                            VStack(alignment: .leading, spacing: 10) {
                                // Centered Pastel Visual Tile
                                ZStack {
                                    RoundedRectangle(cornerRadius: 12)
                                        .fill(Color(hex: cat.iconBg))
                                        .frame(height: 72)

                                    Text(cat.emoji)
                                        .font(.system(size: 32))

                                    VStack {
                                        HStack {
                                            Spacer()
                                            Text("\(cat.productCount)+ items")
                                                .font(.system(size: 9, weight: .heavy))
                                                .foregroundColor(Color(hex: cat.iconTint))
                                                .padding(.horizontal, 6)
                                                .padding(.vertical, 2)
                                                .background(Color.white.opacity(0.9))
                                                .cornerRadius(6)
                                                .padding(6)
                                        }
                                        Spacer()
                                    }
                                }

                                VStack(alignment: .leading, spacing: 3) {
                                    Text(cat.name)
                                        .font(CommerceOSTheme.Typography.bodyMedium().weight(.bold))
                                        .foregroundColor(Color(hex: "0F172A"))
                                        .lineLimit(1)

                                    Text(cat.highlights)
                                        .font(.system(size: 10, weight: .medium))
                                        .foregroundColor(Color(hex: "64748B"))
                                        .lineLimit(1)
                                }

                                HStack(spacing: 4) {
                                    Text("Explore")
                                        .font(.system(size: 11, weight: .bold))
                                        .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                }
                                .padding(.top, 2)
                            }
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(16)
                            .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 1.5)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 140)
            }
            .padding(.top, 4)
        }
        .background(Color(hex: "F4F5F7").ignoresSafeArea())
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
            .navigationViewStyle(.stack)
        }
    }
}
