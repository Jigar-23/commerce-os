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
        case vault

        var id: String {
            switch self {
            case .addressSelection: return "addressSelection"
            case .addAddressFlow: return "addAddressFlow"
            case .vault: return "vault"
            }
        }
    }
    @State private var activeCartSheet: CartSheetDestination? = nil
    @State private var isPrescriptionAttached: Bool = true
    
    let onCheckoutSuccess: () -> Void
    
    public init(onCheckoutSuccess: @escaping () -> Void = {}) {
        self.onCheckoutSuccess = onCheckoutSuccess
    }
    
    public var body: some View {
        if cartStore.items.isEmpty {
            VStack(spacing: 16) {
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
                    HStack(alignment: .center) {
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
                        Image(systemName: "house.fill")
                            .foregroundColor(Color(hex: "059669"))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("Delivering to Selected Address")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                            Text("\(addressRepository.locationHeaderTitle) • \(addressRepository.calculatedEtaMinutes) Mins")
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: "64748B"))
                                .lineLimit(1)
                        }
                        Spacer()
                        Button("Change") {
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
                    
                    // Prescription Gating Banner (Matching Android)
                    if cartStore.hasPrescriptionItem {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(alignment: .top, spacing: 10) {
                                ZStack {
                                    Circle()
                                        .fill(isPrescriptionAttached ? Color(hex: "DCFCE7") : Color(hex: "FEE2E2"))
                                        .frame(width: 38, height: 38)
                                    Image(systemName: isPrescriptionAttached ? "checkmark.seal.fill" : "doc.text.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(isPrescriptionAttached ? Color(hex: "16A34A") : Color(hex: "DC2626"))
                                }
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(isPrescriptionAttached ? "Prescription Attached & Verified" : "Prescription Required (Schedule H/H1)")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(Color(hex: "0F172A"))
                                    Text(isPrescriptionAttached ? "Valid doctor's prescription attached to this order." : "Your cart contains regulated medicines requiring a valid prescription.")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: "64748B"))
                                }
                                Spacer()
                            }
                            
                            Button(action: { activeCartSheet = .vault }) {
                                HStack(spacing: 6) {
                                    Image(systemName: isPrescriptionAttached ? "doc.badge.gearshape" : "camera.viewfinder")
                                        .font(.system(size: 13, weight: .bold))
                                    Text(isPrescriptionAttached ? "Change Attached Prescription" : "Scan / Select Prescription from Vault")
                                        .font(.system(size: 12, weight: .bold))
                                }
                                .foregroundColor(Color(hex: "059669"))
                                .padding(.vertical, 8)
                                .frame(maxWidth: .infinity)
                                .background(Color(hex: "ECFDF5"))
                                .cornerRadius(8)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 8)
                                        .stroke(Color(hex: "059669").opacity(0.4), lineWidth: 1)
                                )
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(isPrescriptionAttached ? Color(hex: "86EFAC") : Color(hex: "FCA5A5"), lineWidth: 1.5)
                        )
                        .padding(.horizontal, 14)
                    }
                    
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
                        HStack(alignment: .top, spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(.red)
                            Text(err)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.red)
                            Spacer()
                        }
                        .padding(10)
                        .background(Color.red.opacity(0.12))
                        .cornerRadius(8)
                        .padding(.horizontal, 14)
                    }
                    
                    // Express COD Checkout Trigger Button
                    Button(action: placeExpressOrder) {
                        if isPlacingOrder {
                            ProgressView()
                                .progressViewStyle(CircularProgressViewStyle(tint: .white))
                        } else {
                            Text("Place COD Order • ₹\(String(format: "%.2f", grandTotal))")
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                        }
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 16)
                    .background(Color(hex: "059669"))
                    .cornerRadius(12)
                    .shadow(color: Color(hex: "059669").opacity(0.35), radius: 8, x: 0, y: 4)
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
                    }
                case .vault:
                    PrescriptionVaultScreen()
                }
            }
        }
    }
    
    private var grandTotal: Double {
        let delivery = cartStore.subtotal > 300 ? 0.0 : 25.0
        let handling = 5.00
        let tip = Double(selectedTip)
        return cartStore.subtotal + delivery + handling + tip
    }
    
    private func placeExpressOrder() {
        isPlacingOrder = true
        orderError = nil
        Task {
            do {
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
                if authoritativeId.hasPrefix("temp_") && !customerId.isEmpty {
                    do {
                        let saved = try await addressRepository.addAddress(customerId: customerId, address: addr)
                        authoritativeId = saved.id
                        await MainActor.run { self.addressRepository.selectAddress(saved) }
                    } catch {
                        // If auto-save fails, server will provision from deliveryAddress payload
                    }
                }
                
                let itemsPayload = cartStore.items.values.map { entry in
                    OrderItemPayload(sku: entry.product.sku, quantity: entry.quantity)
                }
                let safeLat = (addr.latitude != 0.0) ? addr.latitude : 28.202224
                let safeLng = (addr.longitude != 0.0) ? addr.longitude : 76.615418
                let addressPayload = DeliveryAddressPayload(
                    addressLine: addr.displaySummary.isEmpty ? addr.addressLine : addr.displaySummary,
                    city: addr.city ?? "Rewari",
                    postalCode: addr.postalCode ?? "123401",
                    latitude: safeLat,
                    longitude: safeLng
                )
                let payload = PlaceOrderRequest(
                    idempotencyKey: "ios_order_\(UUID().uuidString)",
                    paymentMethod: "COD",
                    addressId: authoritativeId,
                    deliveryAddress: addressPayload,
                    items: itemsPayload,
                    prescriptionId: nil
                )
                
                let _: ServerOrderResponse = try await container.apiClient.post(
                    endpoint: .placeOrder,
                    body: payload
                )
                
                await MainActor.run {
                    self.cartStore.clear()
                    self.isPlacingOrder = false
                    self.orderError = nil
                    self.onCheckoutSuccess()
                }
            } catch {
                await MainActor.run {
                    self.isPlacingOrder = false
                    self.orderError = "Order Placement Failed: \(error.localizedDescription)"
                }
            }
        }
    }
}
