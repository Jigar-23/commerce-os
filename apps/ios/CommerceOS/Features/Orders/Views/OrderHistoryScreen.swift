import SwiftUI

public struct IdentifiableOrderWrapper: Identifiable {
    public var id: String { value }
    public let value: String
    public init(value: String) { self.value = value }
}

public struct OrderHistoryScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @ObservedObject private var orderRepo = OrderRepository.shared
    @State private var selectedFilter: String = "ALL"
    @State private var trackedOrderId: String? = nil
    @State private var pollTimer: Timer? = nil
    public var onTrackOrder: ((String) -> Void)? = nil
    
    public init(onTrackOrder: ((String) -> Void)? = nil) {
        self.onTrackOrder = onTrackOrder
    }
    
    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                // Top Header (Matching Android OrderHistoryScreen.kt lines 51-88)
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Your Orders")
                            .font(.system(size: 20, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        Text("Track deliveries & view past medicines")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    
                    Spacer()
                    
                    // Refresh Button (Matching Android 38dp circle)
                    Button(action: {
                        Task { await orderRepo.fetchCustomerOrders() }
                    }) {
                        Circle()
                            .fill(Color.white)
                            .frame(width: 38, height: 38)
                            .overlay(
                                Circle()
                                    .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                            )
                            .overlay(
                                Image(systemName: "arrow.clockwise")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(Color(hex: "059669"))
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                .padding(.horizontal, 14)
                .padding(.top, 4)
                
                // Filter Pills (Matching Android OrderHistoryScreen.kt lines 92-114)
                HStack(spacing: 8) {
                    filterPill(key: "ALL", label: "All Orders", count: orderRepo.customerOrders.count)
                    filterPill(key: "ACTIVE", label: "Active", count: activeOrders.count)
                    filterPill(key: "DELIVERED", label: "Delivered", count: deliveredOrders.count)
                }
                .padding(.horizontal, 14)
                
                // Orders List
                if orderRepo.isLoadingOrders && orderRepo.customerOrders.isEmpty {
                    VStack(spacing: 12) {
                        ProgressView()
                            .scaleEffect(1.2)
                        Text("Fetching your orders...")
                            .font(.system(size: 13, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 60)
                } else if filteredOrders.isEmpty {
                    emptyOrdersView
                        .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 14) {
                        ForEach(filteredOrders) { order in
                            OrderHistoryCard(
                                order: order,
                                onTrack: {
                                    if let onTrackOrder = onTrackOrder {
                                        onTrackOrder(order.id)
                                    } else {
                                        trackedOrderId = order.id
                                    }
                                },
                                onCancel: {
                                    Task {
                                        try? await orderRepo.cancelOrder(orderId: order.id)
                                    }
                                }
                            )
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 90)
                }
            }
            .padding(.top, 4)
        }
        .background(Color(hex: "F4F5F7").ignoresSafeArea())
        .refreshable {
            await orderRepo.fetchCustomerOrders()
        }
        .onAppear {
            Task {
                await orderRepo.fetchCustomerOrders()
            }
            // Auto-refresh orders every 8 seconds if active orders are present
            pollTimer?.invalidate()
            pollTimer = Timer.scheduledTimer(withTimeInterval: 8.0, repeats: true) { _ in
                if !activeOrders.isEmpty {
                    Task {
                        await orderRepo.fetchCustomerOrders()
                    }
                }
            }
        }
        .onDisappear {
            pollTimer?.invalidate()
            pollTimer = nil
        }
        .sheet(item: Binding<IdentifiableOrderWrapper?>(
            get: { trackedOrderId.map { IdentifiableOrderWrapper(value: $0) } },
            set: { trackedOrderId = $0?.value }
        )) { wrapper in
            NavigationView {
                OrderTrackingScreen(orderId: wrapper.value)
                    .navigationTitle("Order Tracking")
                    .navigationBarTitleDisplayMode(.inline)
                    .toolbar {
                        ToolbarItem(placement: .navigationBarLeading) {
                            Button("Close") {
                                trackedOrderId = nil
                                Task { await orderRepo.fetchCustomerOrders() }
                            }
                        }
                    }
            }
            .navigationViewStyle(.stack)
        }
    }
    
    private var activeOrders: [ServerOrderResponse] {
        orderRepo.customerOrders.filter {
            let s = ($0.status).uppercased()
            return s != "DELIVERED" && s != "CANCELLED"
        }
    }
    
    private var deliveredOrders: [ServerOrderResponse] {
        orderRepo.customerOrders.filter {
            $0.status.uppercased() == "DELIVERED"
        }
    }
    
    private var filteredOrders: [ServerOrderResponse] {
        switch selectedFilter {
        case "ACTIVE": return activeOrders
        case "DELIVERED": return deliveredOrders
        default: return orderRepo.customerOrders
        }
    }
    
    private func filterPill(key: String, label: String, count: Int) -> some View {
        let isSelected = selectedFilter == key
        return Button(action: { selectedFilter = key }) {
            HStack(spacing: 4) {
                Text(label)
                    .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                if count > 0 {
                    Text("(\(count))")
                        .font(.system(size: 10, weight: .bold))
                }
            }
            .padding(.horizontal, 14)
            .padding(.vertical, 7)
            .background(isSelected ? Color(hex: "059669") : Color.white)
            .foregroundColor(isSelected ? .white : Color(hex: "1E293B"))
            .cornerRadius(20)
            .overlay(
                RoundedRectangle(cornerRadius: 20)
                    .stroke(isSelected ? Color.clear : Color(hex: "E2E8F0"), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.02), radius: 1, x: 0, y: 0.5)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private var emptyOrdersView: some View {
        VStack(spacing: 12) {
            Circle()
                .fill(Color(hex: "E2E8F0").opacity(0.5))
                .frame(width: 64, height: 64)
                .overlay(
                    Image(systemName: "cart.badge.questionmark")
                        .font(.system(size: 28))
                        .foregroundColor(Color(hex: "64748B"))
                )
            Text("No Orders Found")
                .font(.system(size: 16, weight: .bold))
                .foregroundColor(Color(hex: "0F172A"))
            Text("You have no \(selectedFilter.lowercased()) orders in your history.")
                .font(.system(size: 12))
                .foregroundColor(Color(hex: "64748B"))
        }
        .frame(maxWidth: .infinity)
    }
}

public struct OrderHistoryCard: View {
    public let order: ServerOrderResponse
    public let onTrack: () -> Void
    public let onCancel: () -> Void
    
    @State private var userRating: Int = 0
    
    public init(order: ServerOrderResponse, onTrack: @escaping () -> Void, onCancel: @escaping () -> Void = {}) {
        self.order = order
        self.onTrack = onTrack
        self.onCancel = onCancel
    }
    
    private var isActive: Bool {
        let s = order.status.uppercased()
        return s != "DELIVERED" && s != "CANCELLED"
    }
    
    private var isDelivered: Bool {
        order.status.uppercased() == "DELIVERED"
    }
    
    private var isCancelled: Bool {
        order.status.uppercased() == "CANCELLED"
    }
    
    private var canCancel: Bool {
        let s = order.status.uppercased()
        return s == "PLACED" || s == "CONFIRMED" || s == "SEARCHING_FOR_RIDER" || s == "READY_FOR_PICKUP"
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 12) {
            // Header Row: Order Number (last 8 chars) & Status Pill
            HStack {
                Text("Order #\(order.id.suffix(8).uppercased())")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "0F172A"))
                
                Spacer()
                
                statusBadge
            }
            
            Divider()
                .background(Color(hex: "F1F5F9"))
            
            // Items List with High-Res Medicine Thumbnails
            if let items = order.items, !items.isEmpty {
                let displayItems = items.prefix(2)
                VStack(alignment: .leading, spacing: 10) {
                    ForEach(displayItems, id: \.sku) { item in
                        HStack(alignment: .center, spacing: 12) {
                            // High-Res Medicine Thumbnail Tile (52x52 Matching Android ProductThumbTile)
                            let imgUrl = MedicineImageResolver.resolve(sku: item.sku, name: item.name)
                            ZStack {
                                RoundedRectangle(cornerRadius: 10)
                                    .fill(Color(hex: "F8FAFC"))
                                    .frame(width: 52, height: 52)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 10)
                                            .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                                    )
                                
                                if let url = URL(string: imgUrl), !imgUrl.isEmpty {
                                    AsyncImage(url: url) { phase in
                                        switch phase {
                                        case .success(let img):
                                            img.resizable()
                                                .aspectRatio(contentMode: .fit)
                                                .frame(width: 44, height: 44)
                                                .cornerRadius(8)
                                        default:
                                            Image(systemName: "pills.fill")
                                                .font(.system(size: 20))
                                                .foregroundColor(Color(hex: "059669"))
                                        }
                                    }
                                } else {
                                    Image(systemName: "pills.fill")
                                        .font(.system(size: 20))
                                        .foregroundColor(Color(hex: "059669"))
                                }
                            }
                            .frame(width: 52, height: 52)
                            
                            // Item Description & Pricing
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name ?? item.sku)
                                    .font(.system(size: 13, weight: .semibold))
                                    .foregroundColor(Color(hex: "1E293B"))
                                    .lineLimit(1)
                                
                                HStack(spacing: 4) {
                                    Text("Qty: \(item.quantity)")
                                        .font(.system(size: 12, weight: .medium))
                                        .foregroundColor(Color(hex: "64748B"))
                                    Text("• ₹\(Int(item.effectivePrice)) each")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color(hex: "94A3B8"))
                                }
                            }
                            
                            Spacer()
                            
                            // Line Total
                            Text("₹\(Int(item.effectivePrice * Double(item.quantity)))")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                    }
                    
                    if items.count > displayItems.count {
                        Text("+\(items.count - displayItems.count) more items")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Color(hex: "059669"))
                            .padding(.leading, 64)
                    }
                }
                
                Divider()
                    .background(Color(hex: "F1F5F9"))
            }
            
            // Summary Row: Total and Payment Method
            HStack {
                let totalItemsCount = (order.items ?? []).reduce(0) { $0 + $1.quantity }
                VStack(alignment: .leading, spacing: 2) {
                    Text("\(totalItemsCount) \(totalItemsCount == 1 ? "item" : "items") • Total ₹\(Int(order.totalAmount))")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    
                    Text("\((order.paymentMethod?.uppercased() == "COD") ? "Cash on Delivery" : "Paid Online") • \((order.paymentStatus?.uppercased() == "PAID") ? "Paid" : "Payment pending")")
                        .font(.system(size: 11))
                        .foregroundColor(Color(hex: "64748B"))
                }
                
                Spacer()
                
                // Delivery SLA ETA Badge if active
                if isActive {
                    Text("⚡ In \(order.effectiveSlaMins) mins")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(hex: "059669"))
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color(hex: "ECFDF5"))
                        .cornerRadius(6)
                }
            }
            
            // Delivery PIN Badge for Active Orders (Matching Android lines 358-391)
            if isActive, let pin = order.effectiveDeliveryPin {
                HStack(alignment: .center, spacing: 6) {
                    Text("🔑")
                        .font(.system(size: 14))
                    Text("Delivery PIN to give rider:")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(hex: "92400E"))
                    Spacer()
                    Text(pin)
                        .font(.system(size: 16, weight: .black, design: .monospaced))
                        .foregroundColor(Color(hex: "B45309"))
                        .tracking(2)
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 8)
                .background(Color(hex: "FEF3C7"))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color(hex: "F59E0B").opacity(0.5), lineWidth: 1)
                )
            }
            
            // Star Rating for Delivered Orders (Matching Android lines 394-426)
            if isDelivered {
                HStack {
                    Text(userRating > 0 ? "Rated \(userRating)/5 ⭐" : "Rate your order:")
                        .font(.system(size: 12, weight: .medium))
                        .foregroundColor(Color(hex: "475569"))
                    Spacer()
                    HStack(spacing: 4) {
                        ForEach(1...5, id: \.self) { star in
                            Button(action: { userRating = star }) {
                                Text(star <= userRating ? "⭐" : "☆")
                                    .font(.system(size: 16))
                            }
                            .buttonStyle(PlainButtonStyle())
                        }
                    }
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color(hex: "F8FAFC"))
                .cornerRadius(10)
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
            }
            
            // Action Buttons (Matching Android lines 431-482)
            if isActive {
                HStack(spacing: 10) {
                    Button(action: onTrack) {
                        Text("⚡ Track Order")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color(hex: "059669"))
                            .cornerRadius(10)
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    if canCancel {
                        Button(action: onCancel) {
                            Text("Cancel")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "DC2626"))
                                .padding(.horizontal, 16)
                                .padding(.vertical, 10)
                                .background(Color.white)
                                .cornerRadius(10)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(Color(hex: "DC2626").opacity(0.4), lineWidth: 1)
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            } else {
                HStack(spacing: 10) {
                    Button(action: onTrack) {
                        Text("View Complete Details")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(hex: "059669"))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(Color.white)
                            .cornerRadius(10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color(hex: "059669"), lineWidth: 1)
                            )
                    }
                    .buttonStyle(PlainButtonStyle())
                    
                    if !isCancelled && (order.items != nil && !order.items!.isEmpty) {
                        Button(action: onTrack) {
                            Text("Order Again")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(.white)
                                .padding(.horizontal, 20)
                                .padding(.vertical, 10)
                                .background(Color(hex: "059669"))
                                .cornerRadius(10)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
            }
        }
        .padding(16)
        .background(Color.white)
        .cornerRadius(16)
        .overlay(
            RoundedRectangle(cornerRadius: 16)
                .stroke(isActive ? Color(hex: "059669").opacity(0.5) : Color(hex: "E2E8F0"), lineWidth: isActive ? 1.5 : 1)
        )
        .shadow(color: Color.black.opacity(isActive ? 0.04 : 0.02), radius: isActive ? 4 : 2, x: 0, y: 1)
        .contentShape(Rectangle())
        .onTapGesture {
            onTrack()
        }
    }
    
    private var statusBadge: some View {
        let s = order.status.uppercased()
        let isDelivered = s == "DELIVERED"
        let isCancelled = s == "CANCELLED"
        let bg = isDelivered ? Color(hex: "EFF6FF") : (isCancelled ? Color(hex: "FEF2F2") : (isActive ? Color(hex: "ECFDF5") : Color(hex: "F1F5F9")))
        let fg = isDelivered ? Color(hex: "1D4ED8") : (isCancelled ? Color(hex: "DC2626") : (isActive ? Color(hex: "059669") : Color(hex: "475569")))
        
        let label: String = {
            switch s {
            case "PLACED": return "Order Placed"
            case "CONFIRMED": return "Confirmed"
            case "SEARCHING_FOR_RIDER": return "Finding Rider"
            case "READY_FOR_PICKUP": return "Ready for Pickup"
            case "PICKED_UP", "OUT_FOR_DELIVERY": return "Out for Delivery"
            case "DELIVERED": return "Delivered"
            case "CANCELLED": return "Cancelled"
            default: return s.replacingOccurrences(of: "_", with: " ")
            }
        }()
        
        return Text(label)
            .font(.system(size: 11, weight: .bold))
            .foregroundColor(fg)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(bg)
            .cornerRadius(8)
    }
}
