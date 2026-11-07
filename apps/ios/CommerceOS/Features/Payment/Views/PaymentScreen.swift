import SwiftUI
import UIKit

// MARK: - Review & Pay Screen (Ported from Android PaymentScreen.kt)
/// Enterprise Quick-Commerce Payment Screen benchmarked against Blinkit, Swiggy, and Tata 1mg.
public struct PaymentScreen: View {
    @Environment(\.presentationMode) private var presentationMode
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var addressRepository: AddressRepository
    
    public let address: AddressDto?
    public let quote: AuthoritativeCartQuote?
    public let onAuthorize: (String) -> Void
    public let onChangeAddress: (() -> Void)?
    public let onBack: (() -> Void)?
    
    @State private var selectedMethod: String = "COD"
    @State private var isPlacingOrder: Bool = false
    @State private var orderError: String? = nil
    @State private var authoritativeQuote: AuthoritativeCartQuote? = nil
    
    public init(
        address: AddressDto? = nil,
        quote: AuthoritativeCartQuote? = nil,
        onAuthorize: @escaping (String) -> Void,
        onChangeAddress: (() -> Void)? = nil,
        onBack: (() -> Void)? = nil
    ) {
        self.address = address
        self.quote = quote
        self.onAuthorize = onAuthorize
        self.onChangeAddress = onChangeAddress
        self.onBack = onBack
    }
    
    private var activeQuote: AuthoritativeCartQuote? {
        quote ?? authoritativeQuote
    }

    private var grandTotal: Double {
        if let q = activeQuote {
            return q.effectiveGrandTotal
        }
        let delivery = cartStore.subtotal > 300 ? 0.0 : 25.0
        let handling = 5.00
        return max(0.0, cartStore.subtotal + delivery + handling)
    }
    
    private func triggerHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // Top App Bar
            topBarView
            
            // Scrollable Summary
            ScrollView(.vertical, showsIndicators: false) {
                VStack(spacing: 14) {
                    // 1. Delivery Address Card
                    addressCardView
                    
                    // 2. Order Items Summary Accordion
                    itemsSummaryCard
                    
                    // 3. Bill Breakdown Card
                    billBreakdownCard
                    
                    // 4. Payment Method Selection
                    paymentMethodsCard
                    
                    if let err = orderError {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.circle.fill")
                                .foregroundColor(.red)
                            Text(err)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.red)
                            Spacer()
                        }
                        .padding(12)
                        .background(Color.red.opacity(0.1))
                        .cornerRadius(10)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 14)
            }
            .background(Color(hex: "F8FAFC"))
            
            // Bottom Sticky Place Order CTA
            bottomPlaceOrderBar
        }
        .navigationBarHidden(true)
        .onAppear {
            if quote == nil {
                let customerId = container.customerSession?.customerId ?? UserDefaults.standard.string(forKey: "customer_id") ?? "self"
                Task {
                    do {
                        let q = try await container.orderRepository.fetchCartQuote(customerId: customerId)
                        await MainActor.run { self.authoritativeQuote = q }
                    } catch {}
                }
            }
        }
    }
    
    // MARK: - Top App Bar
    private var topBarView: some View {
        HStack {
            Button(action: {
                if let onBack = onBack {
                    onBack()
                } else {
                    presentationMode.wrappedValue.dismiss()
                }
            }) {
                Circle()
                    .fill(Color(hex: "F1F5F9"))
                    .frame(width: 36, height: 36)
                    .overlay(
                        Image(systemName: "arrow.left")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                    )
            }
            .buttonStyle(PlainButtonStyle())
            
            VStack(alignment: .leading, spacing: 2) {
                Text("Review and pay")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                
                let count = cartStore.totalItemCount
                Text("\(count) \(count == 1 ? "item" : "items") • 10-Minute Express Delivery")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundColor(Color(hex: "059669"))
            }
            .padding(.leading, 6)
            
            Spacer()
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Color.white)
        .overlay(Divider(), alignment: .bottom)
    }
    
    // MARK: - 1. Delivery Address Card
    private var addressCardView: some View {
        HStack(alignment: .top, spacing: 12) {
            Circle()
                .fill(Color(hex: "ECFDF5"))
                .frame(width: 36, height: 36)
                .overlay(
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "059669"))
                )
            
            VStack(alignment: .leading, spacing: 3) {
                HStack(spacing: 6) {
                    Text("Deliver to")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color(hex: "64748B"))
                    
                    let resolved = address ?? addressRepository.selectedAddress
                    Text(resolved?.displayTag.uppercased() ?? "ADDRESS")
                        .font(.system(size: 9.5, weight: .black))
                        .foregroundColor(Color(hex: "334155"))
                        .padding(.horizontal, 5)
                        .padding(.vertical, 1.5)
                        .background(Color(hex: "F1F5F9"))
                        .cornerRadius(4)
                }
                
                let resolved = address ?? addressRepository.selectedAddress
                let line = resolved?.displaySummary ?? (resolved?.addressLine.isEmpty == false ? resolved!.addressLine : "No address selected")
                Text(line)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundColor(Color(hex: "0F172A"))
                    .lineLimit(2)
            }
            
            Spacer()
            
            if let onChangeAddress = onChangeAddress {
                Button(action: onChangeAddress) {
                    Text("Change")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "059669"))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                }
                .buttonStyle(PlainButtonStyle())
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
    }
    
    // MARK: - 2. Items Summary Card
    private var itemsSummaryCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                HStack(spacing: 6) {
                    Image(systemName: "shippingbox.fill")
                        .font(.system(size: 13))
                        .foregroundColor(Color(hex: "059669"))
                    Text("Order Items (\(cartStore.items.count))")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                }
                Spacer()
                Text("⚡ 10 MINS SLA")
                    .font(.system(size: 9.5, weight: .black))
                    .foregroundColor(Color(hex: "059669"))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 2)
                    .background(Color(hex: "ECFDF5"))
                    .cornerRadius(4)
            }
            
            Divider()
            
            VStack(spacing: 10) {
                ForEach(cartStore.items.keys.sorted(), id: \.self) { sku in
                    if let entry = cartStore.items[sku] {
                        let item = entry.product
                        let qty = entry.quantity
                        HStack(spacing: 10) {
                            let img = MedicineImageResolver.resolve(sku: item.sku, name: item.name)
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color(hex: "F8FAFC"))
                                .frame(width: 42, height: 42)
                                .overlay(
                                    Group {
                                        if let url = URL(string: img), !img.isEmpty {
                                            AsyncImage(url: url) { phase in
                                                switch phase {
                                                case .success(let image):
                                                    image.resizable().aspectRatio(contentMode: .fit).padding(2)
                                                default:
                                                    Image(systemName: "cross.case.fill")
                                                        .font(.system(size: 14))
                                                        .foregroundColor(Color(hex: "0E675E"))
                                                }
                                            }
                                        } else {
                                            Image(systemName: "cross.case.fill")
                                                .font(.system(size: 14))
                                                .foregroundColor(Color(hex: "0E675E"))
                                        }
                                    }
                                )
                            
                            VStack(alignment: .leading, spacing: 2) {
                                Text(HealthcareFormatters.formatTitle(item.name))
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(Color(hex: "0F172A"))
                                    .lineLimit(1)
                                Text("Qty: \(qty)")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "64748B"))
                            }
                            
                            Spacer()
                            
                            let itemTotal = item.price * Double(qty)
                            Text("₹\(String(format: "%.2f", itemTotal))")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                    }
                }
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
    }
    
    // MARK: - 3. Bill Breakdown Card
    private var billBreakdownCard: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Bill Breakdown")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            let itemsSubtotal = activeQuote?.effectiveSubtotal ?? cartStore.subtotal
            let deliveryFee = activeQuote?.effectiveDeliveryFee ?? (cartStore.subtotal > 300 ? 0.0 : 25.0)
            let packagingFee = activeQuote?.effectivePackagingFee ?? 5.00
            let discount = activeQuote?.effectiveDiscount ?? 0.0

            billRow(label: "Item total", value: "₹\(String(format: "%.2f", itemsSubtotal))")
            billRow(label: "Delivery fee", value: deliveryFee == 0 ? "FREE" : "₹\(String(format: "%.2f", deliveryFee))", isFree: deliveryFee == 0)
            billRow(label: "Packaging & Handling", value: "₹\(String(format: "%.2f", packagingFee))")
            if discount > 0 {
                billRow(label: "Coupon Discount (\(activeQuote?.couponCode ?? ""))", value: "-₹\(String(format: "%.2f", discount))", isFree: true)
            }
            
            Divider().padding(.vertical, 4)
            
            HStack {
                Text("To Pay")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                Spacer()
                Text("₹\(String(format: "%.2f", grandTotal))")
                    .font(.system(size: 18, weight: .black))
                    .foregroundColor(Color(hex: "0F172A"))
            }
        }
        .padding(14)
        .background(Color.white)
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
    }
    
    private func billRow(label: String, value: String, isFree: Bool = false) -> some View {
        HStack {
            Text(label)
                .font(.system(size: 12.5))
                .foregroundColor(Color(hex: "64748B"))
            Spacer()
            Text(value)
                .font(.system(size: 12.5, weight: isFree ? .bold : .semibold))
                .foregroundColor(isFree ? Color(hex: "059669") : Color(hex: "0F172A"))
        }
    }
    
    // MARK: - 4. Payment Methods Card
    private var paymentMethodsCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text("Payment Method")
                .font(.system(size: 14, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            
            // Cash on Delivery (Active & Recommended)
            Button(action: {
                triggerHaptic()
                selectedMethod = "COD"
            }) {
                HStack(spacing: 12) {
                    Circle()
                        .strokeBorder(Color(hex: "059669"), lineWidth: 2)
                        .background(Circle().fill(Color(hex: "059669")))
                        .frame(width: 18, height: 18)
                        .overlay(Circle().fill(Color.white).frame(width: 6, height: 6))
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Cash on Delivery")
                            .font(.system(size: 13.5, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Pay via Cash or UPI when order reaches your door")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    
                    Spacer()
                    
                    Text("RECOMMENDED")
                        .font(.system(size: 9, weight: .heavy))
                        .foregroundColor(Color(hex: "065F46"))
                        .padding(.horizontal, 6)
                        .padding(.vertical, 3)
                        .background(Color(hex: "10B981").opacity(0.15))
                        .cornerRadius(4)
                }
                .padding(12)
                .background(Color(hex: "ECFDF5"))
                .cornerRadius(12)
                .overlay(
                    RoundedRectangle(cornerRadius: 12)
                        .stroke(Color(hex: "059669"), lineWidth: 1.5)
                )
            }
            .buttonStyle(PlainButtonStyle())
            
            // UPI Instant (GPay / PhonePe / Paytm)
            HStack(spacing: 12) {
                Circle()
                    .strokeBorder(Color(hex: "CBD5E1"), lineWidth: 2)
                    .frame(width: 18, height: 18)
                
                VStack(alignment: .leading, spacing: 2) {
                    HStack(spacing: 6) {
                        Text("UPI (GPay / PhonePe / Paytm)")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Color(hex: "64748B"))
                        Text("Coming Soon")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))
                            .padding(.horizontal, 5)
                            .padding(.vertical, 1.5)
                            .background(Color(hex: "F1F5F9"))
                            .cornerRadius(4)
                    }
                    Text("Direct app handoff in rollout. COD is 100% active.")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "94A3B8"))
                }
                
                Spacer()
            }
            .padding(12)
            .background(Color.white.opacity(0.6))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
            )
        }
    }
    
    // MARK: - Sticky Bottom Place Order Bar
    private var bottomPlaceOrderBar: some View {
        let brandTeal = Color(hex: "0E675E")
        return HStack(spacing: 16) {
            VStack(alignment: .leading, spacing: 2) {
                Text("Total to pay")
                    .font(.system(size: 11))
                    .foregroundColor(Color(hex: "64748B"))
                Text("₹\(String(format: "%.2f", grandTotal))")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundColor(Color(hex: "0F172A"))
            }
            
            Spacer()
            
            let hasValidAddress = (address ?? addressRepository.selectedAddress) != nil
            Button(action: {
                if hasValidAddress {
                    executeOrderPlacement()
                } else {
                    onChangeAddress?()
                }
            }) {
                HStack(spacing: 6) {
                    if isPlacingOrder {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else if !hasValidAddress {
                        Image(systemName: "mappin.circle.fill")
                            .font(.system(size: 14))
                        Text("SELECT ADDRESS")
                            .font(.system(size: 13, weight: .heavy))
                    } else {
                        Image(systemName: "lock.shield.fill")
                            .font(.system(size: 14))
                        Text("PLACE ORDER")
                            .font(.system(size: 13, weight: .heavy))
                    }
                }
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 46)
                .background(hasValidAddress ? brandTeal : Color(hex: "94A3B8"))
                .cornerRadius(12)
                .shadow(color: hasValidAddress ? brandTeal.opacity(0.35) : Color.clear, radius: 6, x: 0, y: 3)
            }
            .disabled(isPlacingOrder)
            .buttonStyle(PlainButtonStyle())
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 12)
        .background(Color.white)
        .overlay(Divider(), alignment: .top)
        .shadow(color: Color.black.opacity(0.06), radius: 6, x: 0, y: -2)
    }
    
    private func executeOrderPlacement() {
        triggerHaptic()
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
                
                var activeAddress = address ?? addressRepository.selectedAddress ?? addressRepository.addresses.first
                if activeAddress == nil {
                    await addressRepository.useCurrentLocation()
                    activeAddress = addressRepository.selectedAddress
                }
                
                guard let addr = activeAddress else {
                    await MainActor.run {
                        self.isPlacingOrder = false
                        self.orderError = "Please select or add a delivery address to proceed."
                    }
                    return
                }
                
                var authoritativeId = addr.id
                let isKnownSaved = addressRepository.addresses.contains(where: { $0.id == authoritativeId })
                if !customerId.isEmpty && (authoritativeId.hasPrefix("temp_") || !isKnownSaved) {
                    do {
                        let saved = try await addressRepository.addAddress(customerId: customerId, address: addr)
                        authoritativeId = saved.id
                        await MainActor.run { self.addressRepository.selectAddress(saved) }
                    } catch {}
                }
                
                let itemsPayload = cartStore.items.values.map { entry in
                    OrderItemPayload(sku: entry.product.sku, quantity: entry.quantity)
                }
                let cleanLine = !addr.displaySummary.isEmpty ? addr.displaySummary : (!addr.addressLine.isEmpty ? addr.addressLine : "Delivery Address")
                let addressPayload = DeliveryAddressPayload(
                    addressLine: cleanLine,
                    city: addr.city ?? "",
                    postalCode: addr.postalCode ?? "",
                    latitude: addr.latitude,
                    longitude: addr.longitude
                )
                let payload = PlaceOrderRequest(
                    idempotencyKey: "ios_order_\(UUID().uuidString)",
                    paymentMethod: selectedMethod,
                    addressId: authoritativeId,
                    deliveryAddress: addressPayload,
                    items: itemsPayload,
                    prescriptionId: nil,
                    couponCode: activeQuote?.couponCode
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
                    self.onAuthorize(orderRes.id)
                }
                Task {
                    await self.container.orderRepository.fetchCustomerOrders()
                }
            } catch {
                await MainActor.run {
                    self.isPlacingOrder = false
                    self.orderError = error.localizedDescription
                }
            }
        }
    }
}
