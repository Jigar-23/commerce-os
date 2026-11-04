import SwiftUI
import UIKit

// MARK: - Rich Healthcare Product Detail Screen (Ported from Android ProductDetailScreen.kt)
/// Enterprise Quick Healthcare Product Detail view benchmarked against Tata 1mg, PharmEasy, and Truemeds.
public struct ProductDetailScreen: View {
    @Environment(\.presentationMode) private var presentationMode
    @EnvironmentObject private var cartStore: CartLocalStore
    
    public let product: ProductDto
    public let onDismiss: (() -> Void)?
    
    @State private var isWishlisted: Bool = false
    @State private var heartScale: CGFloat = 1.0
    @State private var showSubstituteAlert: Bool = false
    
    public init(product: ProductDto, onDismiss: (() -> Void)? = nil) {
        self.product = product
        self.onDismiss = onDismiss
    }
    
    private var quantity: Int {
        cartStore.items[product.sku]?.quantity ?? 0
    }
    
    private var isOutOfStock: Bool {
        !product.inStock || product.stockCount == 0
    }
    
    private func triggerHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // Top Navigation Bar
            topNavigationBar
            
            // Scrollable Content
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 14) {
                    // 1. Hero Packaging Image Tile
                    heroImageSection
                    
                    // 2. Title & Salt Composition Card
                    titleAndSaltCard
                    
                    // 3. Price & Delivery Promise Card
                    priceAndDeliveryCard
                    
                    // 4. Certified Generic Substitute Teaser Card
                    genericSubstituteCard
                    
                    // 5. Trust & Quality Badges
                    trustBadgesSection
                    
                    // 6. About Medicine Details (Indications, Dosage, Composition)
                    aboutMedicineSection
                    
                    // 7. Safety Advice (Alcohol, Pregnancy, Driving)
                    safetyAdviceSection
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
            }
            .background(Color(hex: "F8FAFC"))
            
            // Sticky Bottom Purchase Bar
            stickyBottomBar
        }
        .navigationBarHidden(true)
        .alert(isPresented: $showSubstituteAlert) {
            Alert(
                title: Text("Generic Substitute Added"),
                message: Text("Added cost-effective generic substitute with the exact same active pharmaceutical composition."),
                dismissButton: .default(Text("OK"))
            )
        }
    }
    
    // MARK: - Top Navigation Bar
    private var topNavigationBar: some View {
        HStack {
            Button(action: {
                if let onDismiss = onDismiss {
                    onDismiss()
                } else {
                    presentationMode.wrappedValue.dismiss()
                }
            }) {
                Circle()
                    .fill(Color(hex: "F1F5F9"))
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "chevron.left")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                    )
            }
            .buttonStyle(PlainButtonStyle())
            
            Spacer()
            
            // Wishlist Button
            Button(action: {
                triggerHaptic()
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
                    .fill(Color(hex: "F1F5F9"))
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: isWishlisted ? "heart.fill" : "heart")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(isWishlisted ? Color(hex: "EF4444") : Color(hex: "64748B"))
                    )
                    .scaleEffect(heartScale)
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color.white)
        .overlay(Divider(), alignment: .bottom)
    }
    
    // MARK: - 1. Hero Image Section
    private var heroImageSection: some View {
        let resolved = MedicineImageResolver.resolve(sku: product.sku, name: product.name, rawImage: product.imageUrl)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 16)
                .fill(Color.white)
                .frame(height: 220)
                .overlay(
                    Group {
                        if let url = URL(string: resolved), !resolved.isEmpty {
                            AsyncImage(url: url) { phase in
                                switch phase {
                                case .success(let image):
                                    image
                                        .resizable()
                                        .aspectRatio(contentMode: .fit)
                                        .padding(16)
                                default:
                                    Image(systemName: "cross.case.fill")
                                        .font(.system(size: 48))
                                        .foregroundColor(Color(hex: "0E675E").opacity(0.8))
                                }
                            }
                        } else {
                            Image(systemName: "cross.case.fill")
                                .font(.system(size: 48))
                                .foregroundColor(Color(hex: "0E675E").opacity(0.8))
                        }
                    }
                )
            
            // 100% Genuine Badge (Top Left)
            HStack(spacing: 4) {
                Image(systemName: "checkmark.seal.fill")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "047857"))
                Text("100% Genuine Medicine")
                    .font(.system(size: 10.5, weight: .bold))
                    .foregroundColor(Color(hex: "047857"))
            }
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color(hex: "ECFDF5"))
            .cornerRadius(6)
            .overlay(
                RoundedRectangle(cornerRadius: 6)
                    .stroke(Color(hex: "A7F3D0"), lineWidth: 0.5)
            )
            .padding(12)
            
            // Rating Pill (Bottom Left)
            VStack {
                Spacer()
                HStack(spacing: 3) {
                    Text(String(format: "%.1f", product.effectiveRating))
                        .font(.system(size: 11, weight: .heavy))
                        .foregroundColor(.white)
                    Image(systemName: "star.fill")
                        .font(.system(size: 9))
                        .foregroundColor(.white)
                }
                .padding(.horizontal, 6)
                .padding(.vertical, 3)
                .background(Color(hex: "166534"))
                .cornerRadius(6)
                .padding(12)
            }
            .frame(height: 220, alignment: .bottomLeading)
        }
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    // MARK: - 2. Title & Salt Card
    private var titleAndSaltCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            let brand = HealthcareFormatters.formatBrand(product.brand, title: product.name)
            Text("BY \(brand)".uppercased())
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color(hex: "059669"))
                .tracking(0.5)
            
            Text(HealthcareFormatters.formatTitle(product.name))
                .font(.system(size: 18, weight: .heavy))
                .foregroundColor(Color(hex: "0F172A"))
                .lineSpacing(4)
            
            let pack = HealthcareFormatters.formatPackSize(product.effectivePackSize)
            Text(pack)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color(hex: "64748B"))
            
            // Salt Composition Chip
            let salt = HealthcareFormatters.formatSalt(title: product.name)
            HStack(spacing: 6) {
                Image(systemName: "pills.fill")
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: "0E675E"))
                Text(salt)
                    .font(.system(size: 11.5, weight: .semibold))
                    .foregroundColor(Color(hex: "0F172A"))
            }
            .padding(.horizontal, 10)
            .padding(.vertical, 6)
            .background(Color(hex: "F1F5F9"))
            .cornerRadius(8)
            .overlay(
                RoundedRectangle(cornerRadius: 8)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 0.5)
            )
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
    }
    
    // MARK: - 3. Price & Delivery Card
    private var priceAndDeliveryCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 8) {
                Text("₹\(String(format: "%.2f", product.price))")
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundColor(Color(hex: "0F172A"))
                
                if product.mrp > product.price {
                    Text("₹\(String(format: "%.2f", product.mrp))")
                        .font(.system(size: 13))
                        .strikethrough()
                        .foregroundColor(Color(hex: "94A3B8"))
                    
                    let discount = Int(((product.mrp - product.price) / product.mrp) * 100)
                    Text("\(discount)% OFF")
                        .font(.system(size: 11, weight: .black))
                        .foregroundColor(.white)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(Color(hex: "16A34A"))
                        .cornerRadius(4)
                }
            }
            
            if product.mrp > product.price {
                Text("You save ₹\(String(format: "%.2f", product.mrp - product.price)) on this order")
                    .font(.system(size: 11.5, weight: .bold))
                    .foregroundColor(Color(hex: "16A34A"))
            }
            
            Divider()
            
            // Delivery SLA
            HStack(spacing: 8) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 14))
                    .foregroundColor(Color(hex: "059669"))
                Text("Express 10-Minute Delivery")
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundColor(Color(hex: "059669"))
                Spacer()
                Text("In Stock")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundColor(Color(hex: "047857"))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(hex: "ECFDF5"))
                    .cornerRadius(4)
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
    }
    
    // MARK: - 4. Generic Substitute Card
    private var genericSubstituteCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.triangle.2.circlepath")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                    Text("Certified Generic Substitute Available")
                        .font(.system(size: 12.5, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                }
                Spacer()
                Text("SAVE 40%")
                    .font(.system(size: 9.5, weight: .black))
                    .foregroundColor(Color(hex: "047857"))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(hex: "A7F3D0"))
                    .cornerRadius(4)
            }
            
            Text("Contains the same chemical salt composition and therapeutic potency at government Jan Aushadhi / verified pricing.")
                .font(.system(size: 11.5))
                .foregroundColor(Color(hex: "64748B"))
                .lineSpacing(2)
            
            Button(action: {
                triggerHaptic()
                showSubstituteAlert = true
            }) {
                HStack {
                    Spacer()
                    Text("Switch to Generic & Save")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                    Image(systemName: "arrow.right")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(hex: "0E675E"))
                    Spacer()
                }
                .padding(.vertical, 8)
                .background(Color(hex: "ECFDF5"))
                .cornerRadius(8)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(Color(hex: "A7F3D0"), lineWidth: 1)
                )
            }
            .buttonStyle(PlainButtonStyle())
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "A7F3D0"), lineWidth: 1)
        )
    }
    
    // MARK: - 5. Trust Badges
    private var trustBadgesSection: some View {
        HStack(spacing: 8) {
            trustBadgeItem(icon: "thermometer.snowflake", title: "Cold Chain", subtitle: "2-8°C Storage")
            trustBadgeItem(icon: "shield.checkerboard", title: "100% Sealed", subtitle: "Direct Pharmacy")
            trustBadgeItem(icon: "cross.vial.fill", title: "Quality Lab", subtitle: "Verified Potency")
        }
    }
    
    private func trustBadgeItem(icon: String, title: String, subtitle: String) -> some View {
        VStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 18))
                .foregroundColor(Color(hex: "0E675E"))
            Text(title)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            Text(subtitle)
                .font(.system(size: 9.5))
                .foregroundColor(Color(hex: "64748B"))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Color.white)
        .cornerRadius(10)
        .overlay(
            RoundedRectangle(cornerRadius: 10)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
    }
    
    // MARK: - 6. About Medicine Section
    private var aboutMedicineSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("About This Medicine")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            Text("Prescribed formulation for rapid symptomatic relief and therapeutic recovery. Please consume strictly as directed by a certified healthcare professional.")
                .font(.system(size: 12))
                .foregroundColor(Color(hex: "475569"))
                .lineSpacing(3)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
    }
    
    // MARK: - 7. Safety Advice Section
    private var safetyAdviceSection: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Safety Advice")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            safetyRow(icon: "wineglass.fill", title: "Alcohol", warning: "Avoid alcohol consumption while taking this medication as it may increase side effects.")
            safetyRow(icon: "figure.and.child.holdinghands", title: "Pregnancy & Lactation", warning: "Consult your doctor before taking if you are pregnant or breastfeeding.")
            safetyRow(icon: "car.fill", title: "Driving", warning: "Safe to drive unless experiencing dizziness or drowsiness.")
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "F1F5F9"), lineWidth: 1)
        )
    }
    
    private func safetyRow(icon: String, title: String, warning: String) -> some View {
        HStack(alignment: .top, spacing: 10) {
            Circle()
                .fill(Color(hex: "F1F5F9"))
                .frame(width: 28, height: 28)
                .overlay(
                    Image(systemName: icon)
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "475569"))
                )
            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Text(warning)
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: "64748B"))
                    .lineSpacing(2)
            }
        }
    }
    
    // MARK: - Sticky Bottom Purchase Bar
    private var stickyBottomBar: some View {
        let brandTeal = Color(hex: "0E675E")
        return HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Total Price")
                    .font(.system(size: 10.5, weight: .medium))
                    .foregroundColor(Color(hex: "64748B"))
                Text("₹\(String(format: "%.2f", product.price))")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundColor(Color(hex: "0F172A"))
            }
            
            Spacer()
            
            if isOutOfStock {
                Text("OUT OF STOCK")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundColor(Color(hex: "94A3B8"))
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(Color(hex: "F1F5F9"))
                    .cornerRadius(10)
            } else if quantity <= 0 {
                Button(action: {
                    triggerHaptic()
                    cartStore.add(product: product)
                }) {
                    HStack(spacing: 6) {
                        Image(systemName: "cart.badge.plus")
                            .font(.system(size: 15, weight: .bold))
                        Text("ADD TO CART")
                            .font(.system(size: 13, weight: .heavy))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: 44)
                    .background(brandTeal)
                    .cornerRadius(10)
                    .shadow(color: brandTeal.opacity(0.3), radius: 6, x: 0, y: 3)
                }
                .buttonStyle(PlainButtonStyle())
            } else {
                HStack(spacing: 0) {
                    Button(action: {
                        triggerHaptic()
                        cartStore.decrement(sku: product.sku)
                    }) {
                        Text("−")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    Text("\(quantity) IN CART")
                        .font(.system(size: 12.5, weight: .black))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                    
                    Button(action: {
                        triggerHaptic()
                        cartStore.increment(sku: product.sku)
                    }) {
                        Text("+")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .frame(width: 44, height: 44)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .frame(maxWidth: .infinity)
                .frame(height: 44)
                .background(brandTeal)
                .cornerRadius(10)
            }
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.white)
        .overlay(Divider(), alignment: .top)
        .shadow(color: Color.black.opacity(0.06), radius: 6, x: 0, y: -2)
    }
}
