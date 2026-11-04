import SwiftUI
import CoreLocation

public struct CartScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @EnvironmentObject private var addressRepository: AddressRepository
    
    @State private var paymentMethod: String = "COD"
    @State private var selectedTip: Int = 0
    @State private var isPlacingOrder: Bool = false
    @State private var orderError: String? = nil
    
    private enum CartSheetDestination: Identifiable {
        case addressSelection
        case addAddressFlow
        case vaultSheet
        case vaultUpload
        case preview(PrescriptionItem)

        var id: String {
            switch self {
            case .addressSelection: return "addressSelection"
            case .addAddressFlow: return "addAddressFlow"
            case .vaultSheet: return "vaultSheet"
            case .vaultUpload: return "vaultUpload"
            case .preview(let rx): return "preview_\(rx.id)"
            }
        }
    }
    @State private var activeCartSheet: CartSheetDestination? = nil
    @State private var isPrescriptionAttached: Bool = true
    @State private var savedPrescriptions: [PrescriptionItem] = []
    @State private var attachedPrescription: PrescriptionItem? = nil
    
    // Coupons & Offers State (1:1 Android Parity)
    @State private var inputCouponCode: String = ""
    @State private var appliedCouponCode: String? = nil
    @State private var couponDiscountAmount: Double = 0.0
    @State private var couponFeedbackMessage: String? = nil
    @State private var isCouponError: Bool = false
    
    private struct CouponCampaignItem: Identifiable {
        let id: String
        let code: String
        let title: String
        let description: String
        let minOrderValue: Double
        let flatDiscount: Double
        let badge: String
    }
    
    private let availableCampaigns: [CouponCampaignItem] = [
        CouponCampaignItem(
            id: "promo_save10",
            code: "SAVE10",
            title: "FLAT ₹100 OFF",
            description: "Flat ₹100 OFF on orders above ₹199",
            minOrderValue: 199.00,
            flatDiscount: 100.00,
            badge: "INSTANT SAVINGS"
        ),
        CouponCampaignItem(
            id: "promo_health30",
            code: "HEALTH30",
            title: "FLAT ₹150 OFF",
            description: "Flat ₹150 OFF on orders above ₹799",
            minOrderValue: 799.00,
            flatDiscount: 150.00,
            badge: "₹150 OFF"
        ),
        CouponCampaignItem(
            id: "promo_welcome20",
            code: "WELCOME20",
            title: "20% OFF",
            description: "20% OFF up to ₹100 on orders above ₹299",
            minOrderValue: 299.00,
            flatDiscount: 60.00,
            badge: "20% OFF"
        )
    ]
    
    let onCheckoutSuccess: (String) -> Void
    public var onBack: (() -> Void)? = nil

    public init(
        onCheckoutSuccess: @escaping (String) -> Void = { _ in },
        onBack: (() -> Void)? = nil
    ) {
        self.onCheckoutSuccess = onCheckoutSuccess
        self.onBack = onBack
    }
    
    public var body: some View {
        if cartStore.items.isEmpty {
            VStack(spacing: 16) {
                if let onBack = onBack {
                    HStack {
                        Button(action: onBack) {
                            Image(systemName: "arrow.left")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                                .padding(8)
                                .background(Color.white)
                                .clipShape(Circle())
                        }
                        .buttonStyle(PlainButtonStyle())
                        Spacer()
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 14)
                }

                Spacer()
                Image(systemName: "cart")
                    .font(.system(size: 64))
                    .foregroundColor(Color(hex: "94A3B8"))
                Text("Your cart is empty")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Text("Add fresh medicines, baby care, or daily essentials to place a 10-minute delivery order.")
                    .font(.system(size: 13))
                    .foregroundColor(Color(hex: "64748B"))
                    .multilineTextAlignment(.center)
                    .padding(.horizontal, 32)
                Spacer()
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background(Color(hex: "F4F5F7").ignoresSafeArea())
        } else {
            ScrollView(showsIndicators: false) {
                VStack(spacing: 14) {
                    // Top Header (Matching Android CartScreen.kt)
                    HStack(alignment: .center, spacing: 10) {
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

                        VStack(alignment: .leading, spacing: 2) {
                            Text("My Cart")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                            Text("\(cartStore.totalItemCount) items • 10-Min Express Delivery")
                                .font(.system(size: 12))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                        Spacer()
                    }
                    .padding(.horizontal, 14)
                    .padding(.top, 4)

                    // Delivery Address Card
                    HStack {
                        Image(systemName: isAddressSelected ? "house.fill" : "mappin.circle.fill")
                            .foregroundColor(isAddressSelected ? Color(hex: "059669") : Color(hex: "D97706"))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(isAddressSelected ? "Delivering to \(addressRepository.selectedAddress?.displayTag.isEmpty == false ? addressRepository.selectedAddress!.displayTag : "Selected Address")" : "No Delivery Address Selected")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                            Text(isAddressSelected ? "\(addressRepository.locationHeaderTitle) • \(addressRepository.calculatedEtaMinutes) Mins" : "Select or add an address to proceed to checkout")
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: "64748B"))
                                .lineLimit(1)
                        }
                        Spacer()
                        Button(isAddressSelected ? "Change" : "Select") {
                            activeCartSheet = .addressSelection
                        }
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "059669"))
                    }
                    .padding(12)
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                    .padding(.horizontal, 14)
                    
                    // Free Delivery Progress Banner (1:1 Android Parity)
                    freeDeliveryBanner
                    
                    // Cart Items List
                    VStack(spacing: 0) {
                        ForEach(Array(cartStore.items.values), id: \.product.sku) { entry in
                            HStack(spacing: 12) {
                                let imgUrl = MedicineImageResolver.resolve(sku: entry.product.sku, name: entry.product.name, rawImage: entry.product.imageUrl)
                                if let url = URL(string: imgUrl), !imgUrl.isEmpty {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let img):
                                            img.resizable()
                                                .aspectRatio(contentMode: .fit)
                                                .frame(width: 44, height: 44)
                                        default:
                                            Image(systemName: "pills.fill")
                                                .font(.system(size: 22))
                                                .foregroundColor(Color(hex: "059669"))
                                                .frame(width: 44, height: 44)
                                        }
                                    }
                                } else {
                                    Image(systemName: "pills.fill")
                                        .font(.system(size: 22))
                                        .foregroundColor(Color(hex: "059669"))
                                        .frame(width: 44, height: 44)
                                }

                                VStack(alignment: .leading, spacing: 2) {
                                    Text(entry.product.name)
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(Color(hex: "0F172A"))
                                        .lineLimit(2)
                                    Text("₹\(String(format: "%.2f", entry.product.price)) each")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: "64748B"))
                                }
                                Spacer()
                                
                                // Stepper
                                HStack(spacing: 10) {
                                    Button(action: { cartStore.decrement(sku: entry.product.sku) }) {
                                        Image(systemName: "minus")
                                            .font(.system(size: 11, weight: .bold))
                                            .frame(width: 26, height: 26)
                                            .background(Color(.systemGray5))
                                            .cornerRadius(6)
                                    }
                                    Text("\(entry.quantity)")
                                        .font(.system(size: 13, weight: .bold))
                                        .frame(minWidth: 18)
                                    Button(action: { cartStore.increment(sku: entry.product.sku) }) {
                                        Image(systemName: "plus")
                                            .font(.system(size: 11, weight: .bold))
                                            .frame(width: 26, height: 26)
                                            .background(Color(hex: "059669"))
                                            .foregroundColor(.white)
                                            .cornerRadius(6)
                                    }
                                }
                            }
                            .padding(.vertical, 10)
                            .padding(.horizontal, 14)
                            Divider()
                                .background(Color(hex: "F1F5F9"))
                        }
                    }
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                    .padding(.horizontal, 14)
                    
                    // Doctor's Prescription Verification Card (1:1 Android Parity)
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            HStack(spacing: 8) {
                                ZStack {
                                    RoundedRectangle(cornerRadius: 8)
                                        .fill(attachedPrescription != nil ? CommerceOSTheme.Colors.brandPrimarySoft : Color(hex: "EFF6FF"))
                                        .frame(width: 32, height: 32)
                                    Image(systemName: attachedPrescription != nil ? "checkmark.circle.fill" : "info.circle.fill")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(attachedPrescription != nil ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "0284C7"))
                                }
                                Text("Doctor's Prescription")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(Color(hex: "0F172A"))
                            }
                            Spacer()
                            Text(attachedPrescription != nil ? "ATTACHED" : "OPTIONAL")
                                .font(.system(size: 10, weight: .heavy))
                                .foregroundColor(attachedPrescription != nil ? CommerceOSTheme.Colors.brandPrimary : Color(hex: "64748B"))
                                .padding(.horizontal, 7)
                                .padding(.vertical, 3)
                                .background(attachedPrescription != nil ? CommerceOSTheme.Colors.brandPrimarySoft : Color(hex: "F1F5F9"))
                                .cornerRadius(6)
                        }

                        if let rx = attachedPrescription {
                            HStack(spacing: 12) {
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Attached: \(rx.patientName.isEmpty ? "Patient Rx" : rx.patientName)")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(Color(hex: "0F172A"))
                                    Text("Rx #\(rx.id.suffix(6).uppercased()) • Pharmacist will verify")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                                }
                                Spacer()
                                Button("Change") {
                                    activeCartSheet = .vaultSheet
                                }
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "0284C7"))

                                Button("Remove") {
                                    attachedPrescription = nil
                                }
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "DC2626"))
                            }
                            .padding(12)
                            .background(Color(hex: "F8FAFC"))
                            .cornerRadius(10)
                            .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: "E2E8F0"), lineWidth: 1))
                        } else {
                            Text("Upload prescription for licensed pharmacist verification (optional). You can still proceed without it.")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "64748B"))

                            HStack(spacing: 8) {
                                Button(action: { activeCartSheet = .vaultSheet }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "list.bullet")
                                            .font(.system(size: 13, weight: .bold))
                                        Text(savedPrescriptions.isEmpty ? "Select Saved" : "From Vault (\(savedPrescriptions.count))")
                                            .font(.system(size: 12, weight: .bold))
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 38)
                                    .foregroundColor(Color(hex: "334155"))
                                    .background(Color.white)
                                    .cornerRadius(10)
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: "CBD5E1"), lineWidth: 1))
                                }

                                Button(action: { activeCartSheet = .vaultUpload }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "plus")
                                            .font(.system(size: 13, weight: .bold))
                                        Text("Upload New")
                                            .font(.system(size: 12, weight: .bold))
                                    }
                                    .frame(maxWidth: .infinity)
                                    .frame(height: 38)
                                    .foregroundColor(.white)
                                    .background(CommerceOSTheme.Colors.brandPrimary)
                                    .cornerRadius(10)
                                }
                            }
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(14)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(attachedPrescription != nil ? CommerceOSTheme.Colors.brandPrimary.opacity(0.4) : Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                    .padding(.horizontal, 14)
                    
                    // Tip Delivery Partner Widget
                    VStack(alignment: .leading, spacing: 10) {
                        HStack(spacing: 8) {
                            Text("🛵")
                                .font(.system(size: 20))
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Tip your delivery partner")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                Text("100% of the tip goes directly to your partner")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "64748B"))
                            }
                        }
                        
                        HStack(spacing: 8) {
                            ForEach([10, 20, 30, 50], id: \.self) { tip in
                                Button(action: {
                                    selectedTip = (selectedTip == tip ? 0 : tip)
                                }) {
                                    Text("+₹\(tip)")
                                        .font(.system(size: 12, weight: .bold))
                                        .padding(.vertical, 8)
                                        .frame(maxWidth: .infinity)
                                        .background(selectedTip == tip ? Color(hex: "ECFDF5") : Color(hex: "F1F5F9"))
                                        .foregroundColor(selectedTip == tip ? Color(hex: "059669") : Color(hex: "0F172A"))
                                        .cornerRadius(8)
                                        .overlay(
                                            RoundedRectangle(cornerRadius: 8)
                                                .stroke(selectedTip == tip ? Color(hex: "059669") : Color.clear, lineWidth: 1)
                                        )
                                }
                            }
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                    .padding(.horizontal, 14)
                    
                    // Payment Method: Cash on Delivery (COD)
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Payment Option")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                            .padding(.horizontal, 14)
                        
                        HStack(spacing: 12) {
                            ZStack {
                                Circle()
                                    .fill(Color(hex: "ECFDF5"))
                                    .frame(width: 36, height: 36)
                                Image(systemName: "banknote.fill")
                                    .font(.system(size: 18))
                                    .foregroundColor(Color(hex: "059669"))
                            }
                            
                            VStack(alignment: .leading, spacing: 2) {
                                HStack {
                                    Text("Cash on Delivery (COD)")
                                        .font(.system(size: 14, weight: .bold))
                                        .foregroundColor(Color(hex: "0F172A"))
                                    Text("ONLY")
                                        .font(.system(size: 9, weight: .black))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(Color(hex: "ECFDF5"))
                                        .foregroundColor(Color(hex: "059669"))
                                        .cornerRadius(4)
                                }
                                Text("Pay cash to delivery partner at your doorstep")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "64748B"))
                            }
                            Spacer()
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 20))
                                .foregroundColor(Color(hex: "059669"))
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(hex: "059669"), lineWidth: 1.5)
                        )
                        .padding(.horizontal, 14)
                    }

                    // Coupons & Offers Section (1:1 Android Parity)
                    couponsSectionView
                    
                    // Bill Breakdown
                    VStack(spacing: 8) {
                        HStack {
                            Text("Item Total")
                                .foregroundColor(Color(hex: "0F172A"))
                            Spacer()
                            Text("₹\(String(format: "%.2f", cartStore.subtotal))")
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                        .font(.system(size: 13))
                        
                        HStack {
                            Text("10-Min Express Delivery Fee")
                                .foregroundColor(Color(hex: "0F172A"))
                            Spacer()
                            Text(cartStore.subtotal > 300 ? "FREE" : "₹25.00")
                                .foregroundColor(cartStore.subtotal > 300 ? Color(hex: "059669") : Color(hex: "0F172A"))
                                .fontWeight(cartStore.subtotal > 300 ? .bold : .regular)
                        }
                        .font(.system(size: 13))
                        
                        HStack {
                            Text("Handling & Packaging Fee")
                                .foregroundColor(Color(hex: "0F172A"))
                            Spacer()
                            Text("₹5.00")
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                        .font(.system(size: 13))
                        
                        if selectedTip > 0 {
                            HStack {
                                Text("Delivery Partner Tip")
                                    .foregroundColor(Color(hex: "0F172A"))
                                Spacer()
                                Text("+₹\(selectedTip)")
                                    .foregroundColor(Color(hex: "059669"))
                                    .fontWeight(.bold)
                            }
                            .font(.system(size: 13))
                        }

                        if couponDiscountAmount > 0 {
                            HStack {
                                HStack(spacing: 4) {
                                    Image(systemName: "tag.fill")
                                        .font(.system(size: 11))
                                    Text("Coupon Discount (\(appliedCouponCode ?? ""))")
                                }
                                .foregroundColor(Color(hex: "059669"))
                                Spacer()
                                Text("-₹\(String(format: "%.2f", couponDiscountAmount))")
                                    .foregroundColor(Color(hex: "059669"))
                                    .fontWeight(.bold)
                            }
                            .font(.system(size: 13))
                        }
                        
                        Divider()
                        
                        HStack {
                            Text("To Pay")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                            Spacer()
                            Text("₹\(String(format: "%.2f", grandTotal))")
                                .font(.system(size: 18, weight: .black))
                                .foregroundColor(Color(hex: "059669"))
                        }
                    }
                    .padding(14)
                    .background(Color.white)
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                    )
                    .padding(.horizontal, 14)
                    
                    // Error Banner if order placement fails
                    if let err = orderError {
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top, spacing: 8) {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(.red)
                                Text(err)
                                    .font(.system(size: 12, weight: .semibold))
                                    .foregroundColor(.red)
                                Spacer()
                            }
                            
                            if err.contains("sign in") || err.contains("Authentication") || err.contains("Session expired") {
                                Button(action: {
                                    container.logout()
                                }) {
                                    HStack(spacing: 6) {
                                        Image(systemName: "person.crop.circle.badge.checkmark")
                                        Text("Sign In Now")
                                    }
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.horizontal, 14)
                                    .padding(.vertical, 8)
                                    .background(Color(hex: "059669"))
                                    .cornerRadius(8)
                                }
                                .padding(.top, 2)
                            }
                        }
                        .padding(10)
                        .background(Color.red.opacity(0.12))
                        .cornerRadius(8)
                        .padding(.horizontal, 14)
                    }
                    
                    // Express COD Checkout / Review & Pay Trigger Button
                    Button(action: {
                        if !isAddressSelected {
                            activeCartSheet = .addressSelection
                        } else {
                            AppNavigator.shared.push(.paymentGateway)
                        }
                    }) {
                        HStack {
                            Spacer()
                            if isPlacingOrder {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                if isAddressSelected {
                                    Text("Review and Pay • ₹\(String(format: "%.2f", grandTotal))")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                } else {
                                    Text("Select Address to Proceed • ₹\(String(format: "%.2f", grandTotal))")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            Spacer()
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(isAddressSelected ? Color(hex: "059669") : Color(hex: "94A3B8"))
                        .cornerRadius(12)
                        .contentShape(Rectangle())
                        .shadow(color: isAddressSelected ? Color(hex: "059669").opacity(0.35) : Color.clear, radius: 8, x: 0, y: 4)
                    }
                    .buttonStyle(PlainButtonStyle())
                    .padding(.horizontal, 14)
                    .disabled(isPlacingOrder)
                    
                    Spacer(minLength: 90)
                }
                .padding(.top, 4)
            }
            .background(Color(hex: "F4F5F7").ignoresSafeArea())
            .sheet(item: $activeCartSheet) { destination in
                switch destination {
                case .addressSelection:
                    AddressSelectionBottomSheet(
                        addressRepository: addressRepository,
                        customerId: container.customerSession?.customerId ?? "",
                        onDismiss: { activeCartSheet = nil },
                        onAddNewAddress: {
                            activeCartSheet = .addAddressFlow
                        }
                    )
                case .addAddressFlow:
                    AddAddressFlowView { newAddr in
                        addressRepository.selectAddress(newAddr)
                        activeCartSheet = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.35) {
                            AppNavigator.shared.push(.paymentGateway)
                        }
                    }
                case .vaultSheet:
                    SavedPrescriptionsBottomSheet(
                        prescriptions: savedPrescriptions,
                        selectedRxId: attachedPrescription?.id,
                        onSelect: { rx in
                            attachedPrescription = rx
                            activeCartSheet = nil
                        },
                        onUploadNew: {
                            activeCartSheet = .vaultUpload
                        },
                        onPreview: { rx in
                            activeCartSheet = .preview(rx)
                        }
                    )
                case .vaultUpload:
                    UploadPrescriptionModal(onUploadSuccess: { newRx in
                        attachedPrescription = newRx
                        savedPrescriptions.insert(newRx, at: 0)
                        activeCartSheet = nil
                    })
                    .environmentObject(container)
                case .preview(let rx):
                    PrescriptionQuickPreviewModal(
                        prescription: rx,
                        onAttach: {
                            attachedPrescription = rx
                            activeCartSheet = nil
                        }
                    )
                }
            }
            .onAppear {
                loadSavedPrescriptions()
            }
        }
    }
    
    // MARK: - Coupons & Offers Section View (1:1 Android Parity)
    private var couponsSectionView: some View {
        VStack(alignment: .leading, spacing: 12) {
            if let appliedCode = appliedCouponCode {
                // Applied Coupon Card
                HStack {
                    Image(systemName: "tag.fill")
                        .foregroundColor(Color(hex: "059669"))
                    Text("'\(appliedCode)' applied")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: "065F46"))
                    Text("•")
                        .foregroundColor(Color(hex: "059669"))
                    Text("Saved ₹\(String(format: "%.2f", couponDiscountAmount))")
                        .font(.system(size: 13, weight: .heavy))
                        .foregroundColor(Color(hex: "059669"))
                    Spacer()
                    Button("Remove") {
                        removeCoupon()
                    }
                    .font(.system(size: 12.5, weight: .bold))
                    .foregroundColor(Color(hex: "DC2626"))
                }
                .padding(12)
                .background(Color(hex: "F0FDF4"))
                .cornerRadius(10)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color(hex: "A7F3D0"), lineWidth: 1))
            } else {
                // Header
                HStack {
                    HStack(spacing: 8) {
                        Image(systemName: "tag.fill")
                            .font(.system(size: 14))
                            .foregroundColor(Color(hex: "0E675E"))
                            .padding(7)
                            .background(Color(hex: "F0FDFA"))
                            .cornerRadius(8)
                        
                        VStack(alignment: .leading, spacing: 1) {
                            Text("Coupons & Offers")
                                .font(.system(size: 14, weight: .heavy))
                                .foregroundColor(Color(hex: "0F172A"))
                            Text("Save extra with tiered voucher discounts")
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                    }
                    Spacer()
                    Text("\(availableCampaigns.count) OFFERS")
                        .font(.system(size: 10, weight: .black))
                        .foregroundColor(Color(hex: "0E675E"))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color(hex: "F0FDFA"))
                        .cornerRadius(6)
                }
                
                // Code Input Field
                HStack(spacing: 8) {
                    TextField("Enter coupon code (e.g. HEALTH30)", text: $inputCouponCode)
                        .font(.system(size: 13, weight: .bold))
                        .autocapitalization(.allCharacters)
                        .disableAutocorrection(true)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 8)
                        .background(Color(hex: "F8FAFC"))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: "E2E8F0"), lineWidth: 1))
                    
                    Button(action: {
                        applyCoupon(code: inputCouponCode)
                    }) {
                        Text("APPLY")
                            .font(.system(size: 12, weight: .heavy))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 9)
                            .background(inputCouponCode.trimmingCharacters(in: .whitespaces).isEmpty ? Color(hex: "CBD5E1") : Color(hex: "0E675E"))
                            .cornerRadius(8)
                    }
                    .disabled(inputCouponCode.trimmingCharacters(in: .whitespaces).isEmpty)
                }
                
                // Feedback Message
                if let msg = couponFeedbackMessage {
                    Text(msg)
                        .font(.system(size: 11.5, weight: .semibold))
                        .foregroundColor(isCouponError ? Color(hex: "DC2626") : Color(hex: "059669"))
                }
                
                Divider()
                
                // Tiered Campaign Cards
                VStack(spacing: 8) {
                    ForEach(availableCampaigns) { campaign in
                        let isEligible = cartStore.subtotal >= campaign.minOrderValue
                        let diffNeeded = campaign.minOrderValue - cartStore.subtotal
                        
                        HStack {
                            VStack(alignment: .leading, spacing: 3) {
                                HStack(spacing: 6) {
                                    Text(campaign.code)
                                        .font(.system(size: 11.5, weight: .black))
                                        .foregroundColor(Color(hex: "0E675E"))
                                        .padding(.horizontal, 7)
                                        .padding(.vertical, 2)
                                        .background(Color(hex: "F0FDFA"))
                                        .cornerRadius(6)
                                        .overlay(RoundedRectangle(cornerRadius: 6).stroke(Color(hex: "99F6E4"), lineWidth: 1))
                                    
                                    Text(campaign.title)
                                        .font(.system(size: 10, weight: .bold))
                                        .foregroundColor(isEligible ? Color(hex: "059669") : Color(hex: "D97706"))
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .background(isEligible ? Color(hex: "DCFCE7") : Color(hex: "FEF3C7"))
                                        .cornerRadius(4)
                                }
                                
                                Text(campaign.description)
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "64748B"))
                            }
                            
                            Spacer()
                            
                            if isEligible {
                                Button("APPLY") {
                                    applyCoupon(code: campaign.code)
                                }
                                .font(.system(size: 11, weight: .heavy))
                                .foregroundColor(Color(hex: "059669"))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 5)
                                .background(Color(hex: "ECFDF5"))
                                .cornerRadius(6)
                            } else {
                                Text("Add ₹\(Int(ceil(diffNeeded)))")
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(Color(hex: "94A3B8"))
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 4)
                                    .background(Color(hex: "F1F5F9"))
                                    .cornerRadius(6)
                            }
                        }
                        .padding(10)
                        .background(isEligible ? Color(hex: "FAFDFA") : Color(hex: "FAFAFA"))
                        .cornerRadius(10)
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(isEligible ? Color(hex: "86EFAC") : Color(hex: "E2E8F0"), lineWidth: 1))
                    }
                }
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(12)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "E2E8F0"), lineWidth: 1))
        .padding(.horizontal, 14)
    }
    
    private func applyCoupon(code: String) {
        let clean = code.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
        guard !clean.isEmpty else {
            couponFeedbackMessage = "Please enter a coupon code"
            isCouponError = true
            return
        }
        guard let campaign = availableCampaigns.first(where: { $0.code == clean }) else {
            couponFeedbackMessage = "Invalid coupon code '\(clean)'"
            isCouponError = true
            return
        }
        if cartStore.subtotal < campaign.minOrderValue {
            let diff = campaign.minOrderValue - cartStore.subtotal
            couponFeedbackMessage = "Add ₹\(Int(ceil(diff))) more to apply \(campaign.code)"
            isCouponError = true
            return
        }
        
        appliedCouponCode = campaign.code
        couponDiscountAmount = campaign.flatDiscount
        couponFeedbackMessage = "Coupon '\(campaign.code)' applied! Saved ₹\(Int(campaign.flatDiscount))"
        isCouponError = false
        inputCouponCode = ""
    }
    
    private func removeCoupon() {
        appliedCouponCode = nil
        couponDiscountAmount = 0.0
        couponFeedbackMessage = nil
        isCouponError = false
    }
    
    private var isAddressSelected: Bool {
        guard let addr = addressRepository.selectedAddress else { return false }
        return !addr.id.isEmpty &&
               !addr.id.hasPrefix("temp_gps_") &&
               (addr.tag?.caseInsensitiveCompare("Current Location") != .orderedSame) &&
               !addr.addressLine.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
    }
    
    private var grandTotal: Double {
        let delivery = cartStore.subtotal > 300 ? 0.0 : 25.0
        let handling = 5.00
        let tip = Double(selectedTip)
        let total = cartStore.subtotal + delivery + handling + tip - couponDiscountAmount
        return max(0.0, total)
    }

    private func loadSavedPrescriptions() {
        let customerId = UserDefaults.standard.string(forKey: "customer_id") ?? "self"
        let local = LocalPrescriptionManager.shared.getPrescriptions(customerId: customerId).map { $0.toPrescriptionItem() }
        self.savedPrescriptions = local
        if self.attachedPrescription == nil, let first = local.first {
            self.attachedPrescription = first
        }
        Task {
            do {
                let list: [PrescriptionServerDto] = try await container.apiClient.get(endpoint: "/api/v1/prescriptions/customer/\(customerId)")
                let localIds = Set(local.map { $0.id })
                let mapped = list.filter { !localIds.contains($0.id) }.map { dto in
                    PrescriptionItem(
                        id: dto.id,
                        doctorName: dto.doctorName ?? "",
                        doctorRegNo: "",
                        patientName: dto.patientName ?? "Patient",
                        extractedMedicines: [],
                        pharmacistStatus: dto.status ?? "PENDING",
                        uploadedDate: dto.createdAt ?? "Recently uploaded"
                    )
                }
                await MainActor.run {
                    self.savedPrescriptions = local + mapped
                    if self.attachedPrescription == nil, let first = self.savedPrescriptions.first {
                        self.attachedPrescription = first
                    }
                }
            } catch {
                // Ignore silent fetch failures in background
            }
        }
    }
    
    private func placeExpressOrder() {
        isPlacingOrder = true
        orderError = nil
        Task {
            do {
                guard container.isAuthenticated, let token = container.apiClient.authToken, !token.isEmpty else {
                    await MainActor.run {
                        self.isPlacingOrder = false
                        self.orderError = "Please sign in to place your order."
                    }
                    return
                }

                let customerId = container.customerSession?.customerId ?? UserDefaults.standard.string(forKey: "customer_id") ?? ""
                
                // 1. Resolve delivery address
                var activeAddress = addressRepository.selectedAddress
                if activeAddress == nil {
                    if let first = addressRepository.addresses.first {
                        activeAddress = first
                        await MainActor.run { self.addressRepository.selectAddress(first) }
                    } else {
                        await addressRepository.useCurrentLocation()
                        activeAddress = addressRepository.selectedAddress
                    }
                }
                
                guard let addr = activeAddress else {
                    await MainActor.run {
                        self.isPlacingOrder = false
                        self.orderError = "Please select or add a delivery address to proceed."
                        self.activeCartSheet = .addressSelection
                    }
                    return
                }
                
                // 2. Ensure address has an authoritative ID from PostgreSQL address book
                var authoritativeId = addr.id
                let isKnownSaved = addressRepository.addresses.contains(where: { $0.id == authoritativeId })
                if !customerId.isEmpty && (authoritativeId.hasPrefix("temp_") || !isKnownSaved) {
                    do {
                        let saved = try await addressRepository.addAddress(customerId: customerId, address: addr)
                        authoritativeId = saved.id
                        await MainActor.run { self.addressRepository.selectAddress(saved) }
                    } catch {
                        print("[Cart] Auto-provision address returned: \(error.localizedDescription)")
                    }
                }
                
                let itemsPayload = cartStore.items.values.map { entry in
                    OrderItemPayload(sku: entry.product.sku, quantity: entry.quantity)
                }
                let safeLat = addr.latitude
                let safeLng = addr.longitude
                let summary = addr.displaySummary
                let cleanLine = !summary.isEmpty ? summary : (!addr.addressLine.isEmpty ? addr.addressLine : "Delivery Address")
                let addressPayload = DeliveryAddressPayload(
                    addressLine: cleanLine,
                    city: addr.city ?? "",
                    postalCode: addr.postalCode ?? "",
                    latitude: safeLat,
                    longitude: safeLng
                )
                let payload = PlaceOrderRequest(
                    idempotencyKey: "ios_order_\(UUID().uuidString)",
                    paymentMethod: "COD",
                    addressId: authoritativeId,
                    deliveryAddress: addressPayload,
                    items: itemsPayload,
                    prescriptionId: attachedPrescription?.id
                )
                
                let orderRes: ServerOrderResponse = try await container.apiClient.post(
                    endpoint: .placeOrder,
                    body: payload
                )
                
                await MainActor.run {
                    self.container.orderRepository.lastPlacedOrder = orderRes
                    if !self.container.orderRepository.customerOrders.contains(where: { $0.id == orderRes.id }) {
                        self.container.orderRepository.customerOrders.insert(orderRes, at: 0)
                    }
                    self.cartStore.clear()
                    self.isPlacingOrder = false
                    self.orderError = nil
                    self.onCheckoutSuccess(orderRes.id)
                }
                Task {
                    await self.container.orderRepository.fetchCustomerOrders()
                }
            } catch {
                await MainActor.run {
                    self.isPlacingOrder = false
                    if let apiErr = error as? APIError, case .unauthenticated = apiErr {
                        self.container.logout()
                        self.orderError = "Session expired. Please sign in with your phone number."
                    } else {
                        self.orderError = "Order Placement Failed: \(error.localizedDescription)"
                    }
                }
            }
        }
    }

    // MARK: - Free Delivery Progress Banner (1:1 Android Parity)
    private var freeDeliveryBanner: some View {
        let threshold: Double = 199.0
        let currentSubtotal = cartStore.subtotal
        let remaining = max(0.0, threshold - currentSubtotal)
        let isUnlocked = remaining <= 0.0
        let progress = min(max(currentSubtotal / threshold, 0.0), 1.0)
        
        return VStack(alignment: .leading, spacing: 8) {
            HStack {
                Text(isUnlocked ? "🎉 Free delivery unlocked!" : "Add ₹\(Int(ceil(remaining))) more for FREE Express Delivery")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(isUnlocked ? Color(hex: "059669") : Color(hex: "0F172A"))
                Spacer()
                if !isUnlocked {
                    Text("₹\(Int(currentSubtotal)) / ₹199")
                        .font(.system(size: 11, weight: .semibold, design: .monospaced))
                        .foregroundColor(Color(hex: "64748B"))
                }
            }
            
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color(hex: "E2E8F0"))
                        .frame(height: 6)
                    
                    RoundedRectangle(cornerRadius: 3)
                        .fill(isUnlocked ? Color(hex: "059669") : Color(hex: "10B981"))
                        .frame(width: max(0, min(geo.size.width * CGFloat(progress), geo.size.width)), height: 6)
                }
            }
            .frame(height: 6)
        }
        .padding(12)
        .background(isUnlocked ? Color(hex: "ECFDF5") : Color.white)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(isUnlocked ? Color(hex: "A7F3D0") : Color(hex: "E2E8F0"), lineWidth: 1)
        )
        .padding(.horizontal, 14)
    }
}

