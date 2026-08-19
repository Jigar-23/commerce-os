import SwiftUI

// MARK: - Main Container View
struct ContentView: View {
    @StateObject private var configProvider = ClientConfigProvider.shared
    @StateObject private var catalogRepo = CatalogRepository.shared
    @StateObject private var orderRepo = OrderRepository.shared
    @StateObject private var trackingRepo = TrackingRepository.shared

    @State private var selectedTab: Int = 0
    @State private var cart: [String: (product: ServerProduct, quantity: Int)] = [:]

    var activeConfig: ClientConfiguration {
        configProvider.currentConfig
    }

    var cartCount: Int {
        cart.values.reduce(0) { $0 + $1.quantity }
    }

    var body: some View {
        TabView(selection: $selectedTab) {
            // Tab 1: Home Catalog
            NavigationView {
                IOSHomeScreen(
                    catalogRepo: catalogRepo,
                    cart: $cart,
                    onSwitchTab: { tab in selectedTab = tab }
                )
            }
            .tabItem {
                Label("Home", systemImage: "house.fill")
            }
            .tag(0)

            // Tab 2: Search Catalog
            NavigationView {
                IOSSearchView(catalogRepo: catalogRepo, cart: $cart)
            }
            .tabItem {
                Label("Search", systemImage: "magnifyingglass")
            }
            .tag(1)

            // Tab 3: Cart
            NavigationView {
                IOSCartView(
                    cart: $cart,
                    onCheckout: { selectedTab = 3 }
                )
            }
            .tabItem {
                Label(activeConfig.terminology.cartLabel, systemImage: "cart.fill")
            }
            .badge(cartCount)
            .tag(2)

            // Tab 4: Checkout & Order Placement
            NavigationView {
                IOSCheckoutView(
                    cart: $cart,
                    orderRepo: orderRepo,
                    onOrderPlaced: {
                        selectedTab = 4
                        trackingRepo.startLiveTracking()
                    }
                )
            }
            .tabItem {
                Label(activeConfig.terminology.checkoutLabel, systemImage: "creditcard.fill")
            }
            .tag(3)

            // Tab 5: Live Tracking & Orders History
            NavigationView {
                IOSAccountOrdersView(
                    orderRepo: orderRepo,
                    trackingRepo: trackingRepo
                )
            }
            .tabItem {
                Label("Tracking", systemImage: "location.fill")
            }
            .tag(4)
        }
        .accentColor(activeConfig.theme.primaryColor)
        .onAppear {
            Task {
                await catalogRepo.fetchProducts()
                await orderRepo.fetchCustomerOrders()
                await trackingRepo.fetchActiveDelivery()
            }
        }
    }
}

// MARK: - 1. Home Catalog Screen
struct IOSHomeScreen: View {
    @ObservedObject var catalogRepo: CatalogRepository
    @Binding var cart: [String: (product: ServerProduct, quantity: Int)]
    let onSwitchTab: (Int) -> Void

    var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                // Hero Banner
                ZStack(alignment: .bottomLeading) {
                    RoundedRectangle(cornerRadius: 18)
                        .fill(LinearGradient(gradient: Gradient(colors: [Color(hex: "0284C7"), Color(hex: "0F172A")]), startPoint: .topLeading, endPoint: .bottomTrailing))
                        .frame(height: 140)

                    VStack(alignment: .leading, spacing: 6) {
                        Text("10-MINUTE QUICK COMMERCE")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(Color(hex: "38BDF8"))
                            .tracking(1)
                        Text("Medicines & Daily Essentials")
                            .font(.system(size: 18, weight: .black))
                            .foregroundColor(.white)
                        Text("Authoritative local fulfillment with live GPS tracking.")
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "94A3B8"))
                    }
                    .padding(18)
                }
                .padding(.horizontal)

                // Product Grid Header
                HStack {
                    Text("Express Catalog")
                        .font(.headline)
                        .fontWeight(.bold)
                    Spacer()
                    if catalogRepo.isLoading {
                        ProgressView()
                            .scaleEffect(0.8)
                    } else {
                        Button(action: {
                            Task { await catalogRepo.fetchProducts() }
                        }) {
                            Image(systemName: "arrow.clockwise")
                                .font(.subheadline)
                                .foregroundColor(.accentColor)
                        }
                    }
                }
                .padding(.horizontal)

                if let err = catalogRepo.errorMessage {
                    Text(err)
                        .font(.caption)
                        .foregroundColor(.red)
                        .padding(.horizontal)
                }

                // Products List
                LazyVStack(spacing: 12) {
                    ForEach(catalogRepo.products) { product in
                        HStack(spacing: 14) {
                            RoundedRectangle(cornerRadius: 12)
                                .fill(Color(hex: "1E293B"))
                                .frame(width: 60, height: 60)
                                .overlay(
                                    Image(systemName: "cross.vial.fill")
                                        .foregroundColor(Color(hex: "38BDF8"))
                                )

                            VStack(alignment: .leading, spacing: 4) {
                                Text(product.name)
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(.primary)
                                    .lineLimit(1)
                                if let pack = product.packSize {
                                    Text(pack)
                                        .font(.system(size: 11))
                                        .foregroundColor(.secondary)
                                }
                                Text("₹\(String(format: "%.2f", product.effectivePrice))")
                                    .font(.system(size: 14, weight: .black))
                                    .foregroundColor(Color(hex: "10B981"))
                            }

                            Spacer()

                            // Cart Add/Remove Button
                            if let currentQty = cart[product.sku]?.quantity, currentQty > 0 {
                                HStack(spacing: 8) {
                                    Button(action: {
                                        if currentQty > 1 {
                                            cart[product.sku] = (product, currentQty - 1)
                                        } else {
                                            cart.removeValue(forKey: product.sku)
                                        }
                                    }) {
                                        Image(systemName: "minus.circle.fill")
                                            .foregroundColor(.red)
                                    }

                                    Text("\(currentQty)")
                                        .font(.system(size: 13, weight: .bold))

                                    Button(action: {
                                        cart[product.sku] = (product, currentQty + 1)
                                    }) {
                                        Image(systemName: "plus.circle.fill")
                                            .foregroundColor(Color(hex: "10B981"))
                                    }
                                }
                            } else {
                                Button(action: {
                                    cart[product.sku] = (product, 1)
                                }) {
                                    Text("ADD")
                                        .font(.system(size: 12, weight: .bold))
                                        .padding(.horizontal, 14)
                                        .padding(.vertical, 6)
                                        .background(Color(hex: "0284C7"))
                                        .foregroundColor(.white)
                                        .cornerRadius(8)
                                }
                            }
                        }
                        .padding(12)
                        .background(RoundedRectangle(cornerRadius: 14).fill(Color(.secondarySystemBackground)))
                        .padding(.horizontal)
                    }
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Commerce OS")
    }
}

// MARK: - 2. Search Screen
struct IOSSearchView: View {
    @ObservedObject var catalogRepo: CatalogRepository
    @Binding var cart: [String: (product: ServerProduct, quantity: Int)]
    @State private var query: String = ""

    var filtered: [ServerProduct] {
        if query.trimmingCharacters(in: .whitespaces).isEmpty {
            return catalogRepo.products
        }
        return catalogRepo.products.filter {
            $0.name.localizedCaseInsensitiveContains(query) ||
            $0.sku.localizedCaseInsensitiveContains(query)
        }
    }

    var body: some View {
        VStack {
            TextField("Search SKU or product name…", text: $query)
                .textFieldStyle(RoundedBorderTextFieldStyle())
                .padding(.horizontal)
                .padding(.top, 8)

            List(filtered) { product in
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(product.name)
                            .font(.system(size: 14, weight: .bold))
                        Text(product.sku)
                            .font(.system(size: 11, design: .monospaced))
                            .foregroundColor(.secondary)
                        Text("₹\(String(format: "%.2f", product.effectivePrice))")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(hex: "10B981"))
                    }
                    Spacer()
                    Button("Add") {
                        let qty = (cart[product.sku]?.quantity ?? 0) + 1
                        cart[product.sku] = (product, qty)
                    }
                    .buttonStyle(.borderedProminent)
                    .tint(Color(hex: "0284C7"))
                }
            }
        }
        .navigationTitle("Search Catalog")
    }
}

// MARK: - 3. Cart Screen
struct IOSCartView: View {
    @Binding var cart: [String: (product: ServerProduct, quantity: Int)]
    let onCheckout: () -> Void

    var subtotal: Double {
        cart.values.reduce(0) { $0 + ($1.product.effectivePrice * Double($1.quantity)) }
    }
    var deliveryFee: Double { 29.0 }
    var grandTotal: Double { subtotal > 0 ? subtotal + deliveryFee : 0 }

    var body: some View {
        VStack {
            if cart.isEmpty {
                VStack(spacing: 12) {
                    Image(systemName: "cart")
                        .font(.system(size: 50))
                        .foregroundColor(.secondary)
                    Text("Your cart is empty")
                        .font(.headline)
                    Text("Add medicines and essentials from the catalog.")
                        .font(.caption)
                        .foregroundColor(.secondary)
                }
                .padding()
                Spacer()
            } else {
                List {
                    Section(header: Text("Items in Cart")) {
                        ForEach(Array(cart.values), id: \.product.sku) { item in
                            HStack {
                                VStack(alignment: .leading) {
                                    Text(item.product.name)
                                        .font(.subheadline)
                                        .fontWeight(.semibold)
                                    Text("₹\(String(format: "%.2f", item.product.effectivePrice)) × \(item.quantity)")
                                        .font(.caption)
                                        .foregroundColor(.secondary)
                                }
                                Spacer()
                                Text("₹\(String(format: "%.2f", item.product.effectivePrice * Double(item.quantity)))")
                                    .fontWeight(.bold)
                            }
                        }
                    }

                    Section(header: Text("Bill Summary")) {
                        HStack {
                            Text("Item Subtotal")
                            Spacer()
                            Text("₹\(String(format: "%.2f", subtotal))")
                        }
                        HStack {
                            Text("Express Delivery Fee")
                            Spacer()
                            Text("₹\(String(format: "%.2f", deliveryFee))")
                        }
                        HStack {
                            Text("To Pay")
                                .fontWeight(.bold)
                            Spacer()
                            Text("₹\(String(format: "%.2f", grandTotal))")
                                .fontWeight(.black)
                                .foregroundColor(Color(hex: "10B981"))
                        }
                    }
                }

                Button(action: onCheckout) {
                    Text("Proceed to Checkout (₹\(String(format: "%.2f", grandTotal)))")
                        .font(.headline)
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .padding()
                        .background(Color(hex: "10B981"))
                        .cornerRadius(14)
                }
                .padding()
            }
        }
        .navigationTitle("My Cart")
    }
}

// MARK: - 4. Checkout Screen
struct IOSCheckoutView: View {
    @Binding var cart: [String: (product: ServerProduct, quantity: Int)]
    @ObservedObject var orderRepo: OrderRepository
    let onOrderPlaced: () -> Void

    @State private var paymentMethod: String = "UPI_INSTANT"
    @State private var addressLine: String = "Tower 4, DLF Phase 5, Golf Course Road"
    @State private var city: String = "Gurugram"
    @State private var postalCode: String = "122002"
    @State private var latitude: Double = 28.4710
    @State private var longitude: Double = 77.0390

    var body: some View {
        Form {
            Section(header: Text("Delivery Address")) {
                TextField("Address", text: $addressLine)
                TextField("City", text: $city)
                TextField("Postal Code", text: $postalCode)
            }

            Section(header: Text("Payment Method")) {
                Picker("Payment", selection: $paymentMethod) {
                    Text("UPI Instant Pay").tag("UPI_INSTANT")
                    Text("Cash on Delivery (COD)").tag("COD")
                }
                .pickerStyle(.segmented)
            }

            if let error = orderRepo.orderError {
                Section {
                    Text(error)
                        .font(.caption)
                        .foregroundColor(.red)
                }
            }

            Section {
                Button(action: {
                    Task {
                        let addr = DeliveryAddressPayload(
                            addressLine: addressLine,
                            city: city,
                            postalCode: postalCode,
                            latitude: latitude,
                            longitude: longitude
                        )
                        do {
                            _ = try await orderRepo.placeOrder(
                                items: Array(cart.values),
                                address: addr,
                                paymentMethod: paymentMethod
                            )
                            cart.removeAll()
                            onOrderPlaced()
                        } catch {
                            // Handled in orderRepo.orderError
                        }
                    }
                }) {
                    if orderRepo.isPlacingOrder {
                        HStack {
                            Spacer()
                            ProgressView()
                            Spacer()
                        }
                    } else {
                        Text("Place Order via PostgreSQL Outbox")
                            .font(.headline)
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding()
                            .background(Color(hex: "0284C7"))
                            .cornerRadius(12)
                    }
                }
                .disabled(cart.isEmpty || orderRepo.isPlacingOrder)
            }
        }
        .navigationTitle("Checkout")
    }
}

// MARK: - 5. Live Tracking & Orders Screen
struct IOSAccountOrdersView: View {
    @ObservedObject var orderRepo: OrderRepository
    @ObservedObject var trackingRepo: TrackingRepository

    var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Active Live Tracking Card
                if let active = trackingRepo.activeTracking {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("LIVE DELIVERY")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(Color(hex: "10B981"))
                                .tracking(1)
                            Spacer()
                            Text(active.status)
                                .font(.caption)
                                .fontWeight(.bold)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 3)
                                .background(Color(hex: "10B981").opacity(0.15))
                                .foregroundColor(Color(hex: "10B981"))
                                .cornerRadius(8)
                        }

                        if let mins = active.etaMinutes {
                            Text("Arriving in ~\(mins) mins")
                                .font(.system(size: 20, weight: .black))
                                .foregroundColor(.primary)
                        }

                        if let rider = active.riderName {
                            Text("Rider: \(rider) • \(active.riderPhone ?? "")")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }

                        if let pin = active.deliveryOtp {
                            HStack {
                                Text("Delivery PIN:")
                                    .font(.subheadline)
                                    .fontWeight(.bold)
                                Text(pin)
                                    .font(.system(size: 18, weight: .black, design: .monospaced))
                                    .foregroundColor(Color(hex: "0284C7"))
                            }
                            .padding(8)
                            .background(Color(.systemBackground))
                            .cornerRadius(8)
                        }
                    }
                    .padding()
                    .background(RoundedRectangle(cornerRadius: 16).fill(Color(.secondarySystemBackground)))
                    .padding(.horizontal)
                }

                // Recent Orders History
                VStack(alignment: .leading, spacing: 10) {
                    Text("Order History")
                        .font(.headline)
                        .fontWeight(.bold)
                        .padding(.horizontal)

                    if orderRepo.customerOrders.isEmpty {
                        Text("No previous orders found.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .padding(.horizontal)
                    } else {
                        ForEach(orderRepo.customerOrders) { ord in
                            HStack {
                                VStack(alignment: .leading, spacing: 4) {
                                    Text("Order #\(String(ord.id.suffix(8)))")
                                        .font(.subheadline)
                                        .fontWeight(.bold)
                                    Text(ord.status)
                                        .font(.caption)
                                        .foregroundColor(Color(hex: "10B981"))
                                }
                                Spacer()
                                Text("₹\(String(format: "%.2f", ord.totalAmount))")
                                    .font(.headline)
                                    .fontWeight(.black)
                            }
                            .padding()
                            .background(RoundedRectangle(cornerRadius: 12).fill(Color(.secondarySystemBackground)))
                            .padding(.horizontal)
                        }
                    }
                }
            }
            .padding(.vertical)
        }
        .navigationTitle("Active Tracking")
        .onAppear {
            trackingRepo.startLiveTracking()
        }
    }
}
