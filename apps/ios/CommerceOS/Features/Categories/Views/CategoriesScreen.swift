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

                // Reassuring Safety Banner (Verbatim Android CategoriesScreen.kt)
                HStack(spacing: 10) {
                    Circle()
                        .fill(Color(hex: "ECFDF5"))
                        .frame(width: 32, height: 32)
                        .overlay(
                            Image(systemName: "checkmark.shield.fill")
                                .font(.system(size: 15))
                                .foregroundColor(Color(hex: "059669"))
                        )

                    VStack(alignment: .leading, spacing: 1) {
                        Text("100% Genuine Medicines")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Sourced directly from licensed pharmacies • Cold chain maintained")
                            .font(.system(size: 10))
                            .foregroundColor(Color(hex: "64748B"))
                            .lineLimit(1)
                    }

                    Spacer()
                }
                .padding(10)
                .background(Color.white)
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.02), radius: 1, x: 0, y: 0.5)
                .padding(.horizontal, 14)

                // 2-Column Categories Grid (Verbatim Android CategoriesScreen.kt)
                LazyVGrid(columns: [GridItem(.flexible(), spacing: 10), GridItem(.flexible(), spacing: 10)], spacing: 10) {
                    ForEach(defaultCategories) { cat in
                        Button(action: {
                            selectedCategory = cat
                        }) {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    ZStack {
                                        Circle()
                                            .fill(Color(hex: cat.iconBg))
                                            .frame(width: 44, height: 44)
                                        Text(cat.emoji)
                                            .font(.system(size: 22))
                                    }

                                    Spacer()

                                    Text("\(cat.productCount)+")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(Color(hex: "64748B"))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color(hex: "F1F5F9"))
                                        .cornerRadius(6)
                                }

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(cat.name)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(Color(hex: "0F172A"))
                                        .lineLimit(1)

                                    Text(cat.highlights)
                                        .font(.system(size: 10))
                                        .foregroundColor(Color(hex: "64748B"))
                                        .lineLimit(1)
                                }

                                HStack {
                                    Text("View items")
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(Color(hex: "059669"))
                                    Image(systemName: "arrow.right")
                                        .font(.system(size: 9, weight: .bold))
                                        .foregroundColor(Color(hex: "059669"))
                                }
                                .padding(.top, 2)
                            }
                            .padding(12)
                            .background(Color.white)
                            .cornerRadius(14)
                            .overlay(
                                RoundedRectangle(cornerRadius: 14)
                                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                            .shadow(color: Color.black.opacity(0.02), radius: 1, x: 0, y: 0.5)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 14)
                .padding(.bottom, 90)
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
