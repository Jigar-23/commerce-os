import SwiftUI
import CoreLocation

public struct CartScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    
    @State private var paymentMethod: String = "COD"
    @State private var selectedTip: Int = 0
    @State private var isPlacingOrder: Bool = false
    @State private var orderError: String? = nil
    @State private var selectedAddress = DeliveryAddressPayload(
        addressLine: UserDefaults.standard.string(forKey: "customer_address_line") ?? "Primary Delivery Address",
        city: UserDefaults.standard.string(forKey: "customer_city") ?? "",
        postalCode: UserDefaults.standard.string(forKey: "customer_pincode") ?? "",
        latitude: UserDefaults.standard.double(forKey: "customer_lat") != 0 ? UserDefaults.standard.double(forKey: "customer_lat") : (CLLocationManager().location?.coordinate.latitude ?? 0.0),
        longitude: UserDefaults.standard.double(forKey: "customer_lng") != 0 ? UserDefaults.standard.double(forKey: "customer_lng") : (CLLocationManager().location?.coordinate.longitude ?? 0.0)
    )
    @State private var showingAddressSheet: Bool = false
    
    let onCheckoutSuccess: () -> Void
    
    public init(onCheckoutSuccess: @escaping () -> Void = {}) {
        self.onCheckoutSuccess = onCheckoutSuccess
    }
    
    public var body: some View {
        NavigationView {
            if cartStore.items.isEmpty {
                VStack(spacing: 16) {
                    Image(systemName: "cart")
                        .font(.system(size: 64))
                        .foregroundColor(.secondary)
                    Text("Your cart is empty")
                        .font(.system(size: 18, weight: .bold))
                    Text("Add fresh groceries, snacks, or daily essentials to place a 10-minute delivery order.")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                }
                .navigationTitle("Shopping Cart")
            } else {
                ScrollView {
                    VStack(spacing: 16) {
                        // Delivery Address Card
                        HStack {
                            Image(systemName: "house.fill")
                                .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                            VStack(alignment: .leading, spacing: 2) {
                                Text("Delivering to Selected Address")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                Text("\(selectedAddress.addressLine), \(selectedAddress.city) • 10 Mins")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            Spacer()
                            Button("Change") {
                                showingAddressSheet = true
                            }
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                        }
                        .padding(12)
                        .background(Color.white)
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
                        )
                        .padding(.horizontal)
                        
                        // Cart Items List
                        VStack(spacing: 0) {
                            ForEach(Array(cartStore.items.values), id: \.product.sku) { entry in
                                HStack {
                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(entry.product.name)
                                            .font(.system(size: 14, weight: .semibold))
                                        Text("$\(String(format: "%.2f", entry.product.price)) each")
                                            .font(.system(size: 11))
                                            .foregroundColor(.secondary)
                                    }
                                    Spacer()
                                    
                                    // Stepper
                                    HStack(spacing: 12) {
                                        Button(action: { cartStore.decrement(sku: entry.product.sku) }) {
                                            Image(systemName: "minus")
                                                .font(.system(size: 12, weight: .bold))
                                                .frame(width: 28, height: 28)
                                                .background(Color(.systemGray5))
                                                .cornerRadius(6)
                                        }
                                        Text("\(entry.quantity)")
                                            .font(.system(size: 14, weight: .bold))
                                            .frame(minWidth: 20)
                                        Button(action: { cartStore.increment(sku: entry.product.sku) }) {
                                            Image(systemName: "plus")
                                                .font(.system(size: 12, weight: .bold))
                                                .frame(width: 28, height: 28)
                                                .background(configProvider.currentConfig.theme.primaryColor)
                                                .foregroundColor(.white)
                                                .cornerRadius(6)
                                        }
                                    }
                                }
                                .padding(.vertical, 10)
                                .padding(.horizontal)
                                Divider()
                            }
                        }
                        .background(Color(.systemBackground))
                        .cornerRadius(12)
                        .padding(.horizontal)
                        
                        // Tip Delivery Partner Widget (Matching Android DeliveryPartnerTipWidget)
                        VStack(alignment: .leading, spacing: 10) {
                            HStack(spacing: 8) {
                                Text("🛵")
                                    .font(.system(size: 20))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text("Tip your delivery partner")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                    Text("100% of the tip goes directly to your partner")
                                        .font(.system(size: 11))
                                        .foregroundColor(.secondary)
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
                                            .background(selectedTip == tip ? CommerceOSTheme.Colors.brandPrimarySoft : Color(.systemGray6))
                                            .foregroundColor(selectedTip == tip ? CommerceOSTheme.Colors.brandPrimaryDark : CommerceOSTheme.Colors.sushiInk)
                                            .cornerRadius(8)
                                            .overlay(
                                                RoundedRectangle(cornerRadius: 8)
                                                    .stroke(selectedTip == tip ? CommerceOSTheme.Colors.brandPrimaryDark : Color.clear, lineWidth: 1)
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
                                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
                        )
                        .padding(.horizontal)
                        
                        // Payment Method: Strict Cash on Delivery (COD)
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Payment Option")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                .padding(.horizontal)
                            
                            HStack(spacing: 12) {
                                ZStack {
                                    Circle()
                                        .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                                        .frame(width: 36, height: 36)
                                    Image(systemName: "banknote.fill")
                                        .font(.system(size: 18))
                                        .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                }
                                
                                VStack(alignment: .leading, spacing: 2) {
                                    HStack {
                                        Text("Cash on Delivery (COD)")
                                            .font(.system(size: 14, weight: .bold))
                                            .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                        Text("ONLY")
                                            .font(.system(size: 9, weight: .black))
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(CommerceOSTheme.Colors.brandPrimarySoft)
                                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                            .cornerRadius(4)
                                    }
                                    Text("Pay cash to delivery partner at your doorstep")
                                        .font(.system(size: 11))
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Image(systemName: "checkmark.circle.fill")
                                    .font(.system(size: 20))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            }
                            .padding(14)
                            .background(Color.white)
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(CommerceOSTheme.Colors.brandPrimaryDark, lineWidth: 1.5)
                            )
                            .padding(.horizontal)
                        }
                        
                        // Bill Breakdown (Authoritative Dynamic Pricing)
                        VStack(spacing: 8) {
                            HStack {
                                Text("Item Total")
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                Spacer()
                                Text("$\(String(format: "%.2f", cartStore.subtotal))")
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                            }
                            .font(.system(size: 13))
                            
                            HStack {
                                Text("10-Min Express Delivery Fee")
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                Spacer()
                                Text(cartStore.subtotal > 30 ? "FREE" : "$2.50")
                                    .foregroundColor(cartStore.subtotal > 30 ? CommerceOSTheme.Colors.brandPrimaryDark : CommerceOSTheme.Colors.sushiInk)
                                    .fontWeight(cartStore.subtotal > 30 ? .bold : .regular)
                            }
                            .font(.system(size: 13))
                            
                            HStack {
                                Text("Handling & Packaging Fee")
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                Spacer()
                                Text("$1.00")
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                            }
                            .font(.system(size: 13))
                            
                            if selectedTip > 0 {
                                HStack {
                                    Text("Delivery Partner Tip")
                                        .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                    Spacer()
                                    Text("+₹\(selectedTip)")
                                        .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                                        .fontWeight(.bold)
                                }
                                .font(.system(size: 13))
                            }
                            
                            Divider()
                            
                            HStack {
                                Text("To Pay")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(CommerceOSTheme.Colors.sushiInk)
                                Spacer()
                                Text("$\(String(format: "%.2f", grandTotal))")
                                    .font(.system(size: 18, weight: .black))
                                    .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                            }
                        }
                        .padding(14)
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
                        )
                        .padding(.horizontal)
                        
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
                            .padding(.horizontal)
                        }
                        
                        // Express COD Checkout Trigger Button
                        Button(action: placeExpressOrder) {
                            if isPlacingOrder {
                                ProgressView()
                                    .progressViewStyle(CircularProgressViewStyle(tint: .white))
                            } else {
                                Text("Place COD Order • $\(String(format: "%.2f", grandTotal))")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 16)
                        .background(CommerceOSTheme.Colors.brandPrimaryDark)
                        .cornerRadius(12)
                        .shadow(color: CommerceOSTheme.Colors.brandPrimaryDark.opacity(0.35), radius: 8, x: 0, y: 4)
                        .padding(.horizontal)
                        .disabled(isPlacingOrder)
                        
                        Spacer(minLength: 40)
                    }
                    .padding(.top, 8)
                }
                .navigationTitle("Checkout")
                .navigationBarTitleDisplayMode(.inline)
                .sheet(isPresented: $showingAddressSheet) {
                    AddAddressFlowView { newAddr in
                        selectedAddress = DeliveryAddressPayload(
                            addressLine: newAddr.addressLine,
                            city: UserDefaults.standard.string(forKey: "customer_city") ?? "Delivery Area",
                            postalCode: UserDefaults.standard.string(forKey: "customer_pincode") ?? "",
                            latitude: newAddr.latitude,
                            longitude: newAddr.longitude
                        )
                        showingAddressSheet = false
                    }
                }
            }
        }
    }
    
    private var grandTotal: Double {
        let delivery = cartStore.subtotal > 30 ? 0.0 : 2.50
        let handling = 1.00
        let tip = Double(selectedTip) * 0.012 // Convert INR tip to relative USD/unit display
        return cartStore.subtotal + delivery + handling + tip
    }
    
    private func placeExpressOrder() {
        isPlacingOrder = true
        orderError = nil
        Task {
            // Live Transactional Order Placement matching POST /api/v1/orders
            do {
                let itemsPayload = cartStore.items.values.map { entry in
                    OrderItemPayload(sku: entry.product.sku, quantity: entry.quantity)
                }
                let addressPayload = selectedAddress
                let payload = PlaceOrderRequest(
                    idempotencyKey: "ios_order_\(UUID().uuidString)",
                    paymentMethod: "COD",
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
