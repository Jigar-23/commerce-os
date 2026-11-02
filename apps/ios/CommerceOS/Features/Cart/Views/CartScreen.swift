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
    
    let onCheckoutSuccess: (String) -> Void
    
    public init(onCheckoutSuccess: @escaping (String) -> Void = { _ in }) {
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
                    
                    // Express COD Checkout Trigger Button
                    Button(action: placeExpressOrder) {
                        HStack {
                            Spacer()
                            if isPlacingOrder {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Text("Place COD Order • ₹\(String(format: "%.2f", grandTotal))")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            Spacer()
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(Color(hex: "059669"))
                        .cornerRadius(12)
                        .contentShape(Rectangle())
                        .shadow(color: Color(hex: "059669").opacity(0.35), radius: 8, x: 0, y: 4)
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
    
    private var grandTotal: Double {
        let delivery = cartStore.subtotal > 300 ? 0.0 : 25.0
        let handling = 5.00
        let tip = Double(selectedTip)
        return cartStore.subtotal + delivery + handling + tip
    }

    private func loadSavedPrescriptions() {
        Task {
            do {
                let customerId = UserDefaults.standard.string(forKey: "customer_id") ?? "self"
                let list: [PrescriptionServerDto] = try await container.apiClient.get(endpoint: "/api/v1/prescriptions/customer/\(customerId)")
                let mapped = list.map { dto in
                    PrescriptionItem(
                        id: dto.id,
                        doctorName: dto.doctorName ?? "Licensed Practitioner",
                        doctorRegNo: "CDSCO-VERIFIED",
                        patientName: dto.patientName ?? "Self Patient",
                        extractedMedicines: [],
                        pharmacistStatus: dto.status ?? "PENDING",
                        uploadedDate: dto.createdAt ?? "Recent"
                    )
                }
                await MainActor.run {
                    self.savedPrescriptions = mapped
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

