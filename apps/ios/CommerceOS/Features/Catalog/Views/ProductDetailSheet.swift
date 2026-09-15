import SwiftUI

public struct ProductDetailSheet: View {
    @Environment(\.presentationMode) private var presentationMode
    @EnvironmentObject private var cartStore: CartLocalStore
    let product: ProductDto
    @State private var isWishlisted: Bool = false
    
    public init(product: ProductDto) {
        self.product = product
    }
    
    private var quantity: Int {
        cartStore.items[product.sku]?.quantity ?? 0
    }
    
    private var isOutOfStock: Bool {
        !product.inStock
    }
    
    public var body: some View {
        NavigationView {
            ZStack(alignment: .bottom) {
                ScrollView {
                    VStack(alignment: .leading, spacing: 16) {
                        // Hero Image Tile
                        ZStack(alignment: .topTrailing) {
                            let resolvedHeroUrl = MedicineImageResolver.resolve(sku: product.sku, name: product.name, rawImage: product.imageUrl)

                            RoundedRectangle(cornerRadius: 16)
                                .fill(Color.white)
                                .frame(height: 220)
                                .overlay(
                                    Group {
                                        if let url = URL(string: resolvedHeroUrl), !resolvedHeroUrl.isEmpty {
                                            AsyncImage(url: url) { phase in
                                                switch phase {
                                                case .success(let image):
                                                    image
                                                        .resizable()
                                                        .aspectRatio(contentMode: .fit)
                                                        .padding(12)
                                                default:
                                                    VStack(spacing: 8) {
                                                        Image(systemName: iconForCategory(product.category))
                                                            .font(.system(size: 64))
                                                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                                        Text(product.name)
                                                            .font(.system(size: 13, weight: .bold))
                                                            .foregroundColor(.secondary)
                                                            .multilineTextAlignment(.center)
                                                            .padding(.horizontal, 24)
                                                    }
                                                }
                                            }
                                        } else {
                                            VStack(spacing: 8) {
                                                Image(systemName: iconForCategory(product.category))
                                                    .font(.system(size: 64))
                                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                                Text(product.name)
                                                    .font(.system(size: 13, weight: .bold))
                                                    .foregroundColor(.secondary)
                                                    .multilineTextAlignment(.center)
                                                    .padding(.horizontal, 24)
                                            }
                                        }
                                    }
                                )
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16)
                                        .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
                                )
                            
                            // Top Badges
                            HStack {
                                if product.discountPercent > 0 {
                                    Text("\(product.discountPercent)% OFF")
                                        .font(.system(size: 11, weight: .black))
                                        .padding(.horizontal, 8)
                                        .padding(.vertical, 4)
                                        .background(CommerceOSTheme.Colors.brandPrimaryDark)
                                        .foregroundColor(.white)
                                        .cornerRadius(6)
                                }
                                
                                Spacer()
                                
                                Button(action: {
                                    withAnimation(.spring(response: 0.3, dampingFraction: 0.6)) {
                                        isWishlisted.toggle()
                                    }
                                }) {
                                    Circle()
                                        .fill(Color.white)
                                        .frame(width: 38, height: 38)
                                        .shadow(color: Color.black.opacity(0.1), radius: 4, x: 0, y: 2)
                                        .overlay(
                                            Image(systemName: isWishlisted ? "heart.fill" : "heart")
                                                .font(.system(size: 18))
                                                .foregroundColor(isWishlisted ? Color(hex: "EF4444") : .secondary)
                                        )
                                }
                            }
                            .padding(12)
                        }
                        .padding(.horizontal)
                        
                        // Product Title & Brand
                        VStack(alignment: .leading, spacing: 4) {
                            if let brand = product.brand, !brand.isEmpty {
                                Text(brand.uppercased())
                                    .font(.system(size: 11, weight: .black))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                    .tracking(1.0)
                            }
                            
                            Text(product.name)
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                            
                            Text(product.effectivePackSize)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(.secondary)
                            
                            // Rating & Reviews Row
                            HStack(spacing: 8) {
                                HStack(spacing: 3) {
                                    Image(systemName: "star.fill")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color(hex: "F59E0B"))
                                    Text(String(format: "%.1f", product.effectiveRating))
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                }
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(Color(hex: "FEF3C7"))
                                .cornerRadius(6)
                                
                                Text("(\(product.effectiveReviewCount) verified reviews)")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                            .padding(.top, 4)
                        }
                        .padding(.horizontal)
                        
                        Divider()
                            .padding(.horizontal)
                        
                        // Price & Savings Block
                        VStack(alignment: .leading, spacing: 6) {
                            HStack(alignment: .firstTextBaseline, spacing: 8) {
                                Text("₹\(String(format: "%.2f", product.price))")
                                    .font(.system(size: 26, weight: .black))
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                
                                if product.mrp > product.price {
                                    Text("MRP ₹\(String(format: "%.2f", product.mrp))")
                                        .font(.system(size: 14))
                                        .strikethrough()
                                        .foregroundColor(.secondary)
                                }
                            }
                            
                            if product.mrp > product.price {
                                Text("You save ₹\(String(format: "%.2f", product.mrp - product.price)) (\(product.discountPercent)% OFF)")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            }
                        }
                        .padding(.horizontal)
                        
                        // 10-Minute SLA Delivery Promise Box
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                                    .frame(width: 42, height: 42)
                                Image(systemName: "bolt.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Express Delivery in 10-15 Mins")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                Text("Dispatched instantly from Koramangala Dark Store Hub")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                        }
                        .padding(12)
                        .background(CommerceOSTheme.Colors.brandPrimarySoft.opacity(0.4))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(CommerceOSTheme.Colors.brandPrimaryDark.opacity(0.3), lineWidth: 1)
                        )
                        .padding(.horizontal)
                        
                        // Badges / Gating Callouts
                        if product.requiresPrescription || product.isColdChain == true {
                            VStack(spacing: 8) {
                                if product.requiresPrescription {
                                    HStack(spacing: 10) {
                                        Image(systemName: "doc.text.fill")
                                            .foregroundColor(Color(hex: "DC2626"))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("Prescription Required (Rx)")
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundColor(Color(hex: "DC2626"))
                                            Text("A valid medical prescription from a registered doctor is required to process this order.")
                                                .font(.system(size: 11))
                                                .foregroundColor(.secondary)
                                        }
                                        Spacer()
                                    }
                                    .padding(12)
                                    .background(Color(hex: "FEF2F2"))
                                    .cornerRadius(10)
                                }
                                
                                if product.isColdChain == true {
                                    HStack(spacing: 10) {
                                        Image(systemName: "snowflake")
                                            .foregroundColor(Color(hex: "0284C7"))
                                        VStack(alignment: .leading, spacing: 2) {
                                            Text("Cold Chain Storage (2°C - 8°C)")
                                                .font(.system(size: 12, weight: .bold))
                                                .foregroundColor(Color(hex: "0284C7"))
                                            Text("Insulated ice-box delivery to ensure optimal temperature and potency.")
                                                .font(.system(size: 11))
                                                .foregroundColor(.secondary)
                                        }
                                        Spacer()
                                    }
                                    .padding(12)
                                    .background(Color(hex: "E0F2FE"))
                                    .cornerRadius(10)
                                }
                            }
                            .padding(.horizontal)
                        }
                        
                        // About Medicine / Product Section
                        VStack(alignment: .leading, spacing: 10) {
                            Text("About This Product")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                            
                            if let salt = product.saltComposition, !salt.isEmpty {
                                DetailRow(title: "Salt Composition", value: salt)
                                
                                // Active Molecule Breakdown Pills
                                let molecules = salt.components(separatedBy: "+").map { $0.trimmingCharacters(in: .whitespaces) }.filter { !$0.isEmpty }
                                if molecules.count > 1 {
                                    ScrollView(.horizontal, showsIndicators: false) {
                                        HStack(spacing: 6) {
                                            ForEach(molecules, id: \.self) { mol in
                                                HStack(spacing: 4) {
                                                    Image(systemName: "cross.fill")
                                                        .font(.system(size: 8))
                                                        .foregroundColor(Color(hex: "059669"))
                                                    Text(mol)
                                                        .font(.system(size: 11, weight: .semibold))
                                                        .foregroundColor(Color(hex: "0F172A"))
                                                }
                                                .padding(.horizontal, 8)
                                                .padding(.vertical, 4)
                                                .background(Color(hex: "ECFDF5"))
                                                .cornerRadius(6)
                                                .overlay(
                                                    RoundedRectangle(cornerRadius: 6)
                                                        .stroke(Color(hex: "059669").opacity(0.2), lineWidth: 1)
                                                )
                                            }
                                        }
                                    }
                                    .padding(.vertical, 2)
                                }
                            }
                            
                            DetailRow(title: "Category", value: product.category)
                            DetailRow(title: "Packaging", value: product.effectivePackSize)
                            DetailRow(title: "Storage Advice", value: product.isColdChain == true ? "Store refrigerated between 2°C and 8°C. Do not freeze." : "Store in a cool, dry place away from direct sunlight.")
                            DetailRow(title: "Country of Origin", value: "India")
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
                        )
                        .padding(.horizontal)
                        
                        // Safety Advice & Guidance Accordion (1:1 Android Parity)
                        SafetyGuidanceSection()
                            .padding(.horizontal)
                        
                        // Generic Substitute Banner (if applicable)
                        if let sub = product.genericSubstitute {
                            VStack(alignment: .leading, spacing: 8) {
                                HStack {
                                    Image(systemName: "arrow.triangle.2.circlepath")
                                        .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                    Text("Cost-Saving Generic Alternative Available")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                }
                                Text("\(sub.genericName) contains the identical active molecule at ₹\(String(format: "%.2f", sub.genericPrice)) (\(sub.savingsPercentage)% savings).")
                                    .font(.system(size: 12))
                                    .foregroundColor(.secondary)
                            }
                            .padding(12)
                            .background(CommerceOSTheme.Colors.brandPrimarySoft)
                            .cornerRadius(10)
                            .padding(.horizontal)
                        }
                        
                        Spacer(minLength: 90)
                    }
                    .padding(.top, 12)
                }
                
                // Sticky Bottom Purchase Bar
                VStack(spacing: 0) {
                    Divider()
                    HStack(spacing: 16) {
                        VStack(alignment: .leading, spacing: 2) {
                            Text("TOTAL PRICE")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(.secondary)
                            Text("₹\(String(format: "%.2f", product.price))")
                                .font(.system(size: 20, weight: .black))
                                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                        }
                        
                        Spacer()
                        
                        if isOutOfStock {
                            Text("OUT OF STOCK")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.secondary)
                                .padding(.horizontal, 24)
                                .padding(.vertical, 12)
                                .background(Color(.systemGray5))
                                .cornerRadius(10)
                        } else if quantity <= 0 {
                            Button(action: { cartStore.add(product: product) }) {
                                HStack(spacing: 6) {
                                    Text("ADD TO CART")
                                        .font(.system(size: 14, weight: .black))
                                    Image(systemName: "plus")
                                        .font(.system(size: 13, weight: .black))
                                }
                                .foregroundColor(.white)
                                .padding(.horizontal, 28)
                                .padding(.vertical, 13)
                                .background(CommerceOSTheme.Colors.brandPrimaryDark)
                                .cornerRadius(10)
                                .shadow(color: CommerceOSTheme.Colors.brandPrimaryDark.opacity(0.3), radius: 4, x: 0, y: 2)
                            }
                        } else {
                            HStack(spacing: 16) {
                                Button(action: { cartStore.decrement(sku: product.sku) }) {
                                    Text("−")
                                        .font(.system(size: 20, weight: .black))
                                        .foregroundColor(.white)
                                        .frame(width: 34, height: 34)
                                        .background(CommerceOSTheme.Colors.brandPrimaryDark)
                                        .cornerRadius(8)
                                }
                                
                                Text("\(quantity)")
                                    .font(.system(size: 17, weight: .black))
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                    .frame(minWidth: 24)
                                
                                Button(action: { cartStore.increment(sku: product.sku) }) {
                                    Text("+")
                                        .font(.system(size: 20, weight: .black))
                                        .foregroundColor(.white)
                                        .frame(width: 34, height: 34)
                                        .background(CommerceOSTheme.Colors.brandPrimaryDark)
                                        .cornerRadius(8)
                                }
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 4)
                            .background(Color(.systemGray6))
                            .cornerRadius(10)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 12)
                    .background(Color.white.ignoresSafeArea(edges: .bottom))
                }
            }
            .navigationBarTitle("Product Details", displayMode: .inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Done") { presentationMode.wrappedValue.dismiss() }
                        .font(.system(size: 15, weight: .bold))
                }
            }
        }
        .navigationViewStyle(.stack)
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

private struct DetailRow: View {
    let title: String
    let value: String
    
    var body: some View {
        VStack(alignment: .leading, spacing: 2) {
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.secondary)
            Text(value)
                .font(.system(size: 13, weight: .medium))
                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Safety Advice & Guidance Accordion
private struct SafetyGuidanceSection: View {
    @State private var expandedItem: String? = nil
    
    private let safetyItems: [(id: String, icon: String, title: String, warning: String, detail: String, colorHex: String)] = [
        ("alcohol", "wineglass.fill", "Alcohol", "Caution Advised", "Consuming alcohol with this medication may increase dizziness, drowsiness, or alter drug metabolism.", "D97706"),
        ("pregnancy", "figure.and.child.holdinghands", "Pregnancy", "Consult Doctor", "Please consult your healthcare provider before taking this medication during pregnancy.", "2563EB"),
        ("breastfeeding", "heart.text.square.fill", "Breastfeeding", "Safe if Prescribed", "Limited data available. Use only if clearly advised by your physician.", "059669"),
        ("driving", "car.fill", "Driving", "Caution Advised", "May cause drowsiness or dizziness. Avoid operating heavy machinery if affected.", "D97706"),
        ("kidney", "shield.fill", "Kidney & Liver", "Dose Adjustment May Be Needed", "Patients with renal or hepatic impairment should consult their physician for tailored dosage.", "64748B")
    ]
    
    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Safety Advice & Guidance")
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            VStack(spacing: 8) {
                ForEach(safetyItems, id: \.id) { item in
                    let isExpanded = expandedItem == item.id
                    VStack(alignment: .leading, spacing: 6) {
                        Button(action: {
                            withAnimation(.easeInOut(duration: 0.2)) {
                                expandedItem = isExpanded ? nil : item.id
                            }
                        }) {
                            HStack(spacing: 10) {
                                Image(systemName: item.icon)
                                    .font(.system(size: 15))
                                    .foregroundColor(Color(hex: item.colorHex))
                                    .frame(width: 24)
                                
                                Text(item.title)
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                
                                Spacer()
                                
                                Text(item.warning)
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Color(hex: item.colorHex))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color(hex: item.colorHex).opacity(0.12))
                                    .cornerRadius(6)
                                
                                Image(systemName: isExpanded ? "chevron.up" : "chevron.down")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(Color(hex: "94A3B8"))
                            }
                        }
                        .buttonStyle(PlainButtonStyle())
                        
                        if isExpanded {
                            Text(item.detail)
                                .font(.system(size: 12))
                                .foregroundColor(Color(hex: "475569"))
                                .lineSpacing(3)
                                .padding(.top, 4)
                                .padding(.leading, 34)
                        }
                    }
                    .padding(10)
                    .background(Color(hex: "F8FAFC"))
                    .cornerRadius(10)
                    .overlay(
                        RoundedRectangle(cornerRadius: 10)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                }
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
        )
    }
}

