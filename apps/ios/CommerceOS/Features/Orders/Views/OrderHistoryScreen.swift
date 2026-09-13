import SwiftUI

public struct IdentifiableOrderWrapper: Identifiable {
    public var id: String { value }
    public let value: String
    public init(value: String) { self.value = value }
}

public struct OrderHistoryScreen: View {
    @EnvironmentObject private var container: AppContainer
    @EnvironmentObject private var configProvider: ClientConfigProvider
    @StateObject private var orderRepo = OrderRepository.shared
    @State private var selectedFilter: String = "ALL"
    @State private var trackedOrderId: String? = nil
    
    public init() {}
    
    public var body: some View {
        NavigationView {
            VStack(alignment: .leading, spacing: 0) {
                // Header
                VStack(alignment: .leading, spacing: 4) {
                    Text("Your Orders")
                        .font(.system(size: 24, weight: .black))
                        .foregroundColor(.primary)
                    Text("Track deliveries & rate past orders")
                        .font(.system(size: 13))
                        .foregroundColor(.secondary)
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                .padding(.bottom, 12)
                
                // Segmented Filter Rail (Domino's / Blinkit pattern)
                HStack(spacing: 8) {
                    filterButton(title: "ALL", count: orderRepo.customerOrders.count)
                    filterButton(title: "ACTIVE", count: activeOrders.count)
                    filterButton(title: "DELIVERED", count: deliveredOrders.count)
                }
                .padding(.horizontal, 16)
                .padding(.bottom, 12)
                
                Divider()
                
                // Orders List
                if filteredOrders.isEmpty {
                    emptyOrdersView
                } else {
                    ScrollView {
                        LazyVStack(spacing: 12) {
                            ForEach(filteredOrders) { order in
                                OrderHistoryCard(order: order) {
                                    trackedOrderId = order.id
                                }
                            }
                        }
                        .padding(16)
                        .padding(.bottom, 80)
                    }
                }
            }
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button(action: {
                        Task { await orderRepo.fetchCustomerOrders() }
                    }) {
                        Image(systemName: "arrow.clockwise")
                            .font(.system(size: 14, weight: .semibold))
                    }
                }
            }
            .onAppear {
                Task {
                    await orderRepo.fetchCustomerOrders()
                }
            }
            .sheet(item: Binding<IdentifiableOrderWrapper?>(
                get: { trackedOrderId.map { IdentifiableOrderWrapper(value: $0) } },
                set: { trackedOrderId = $0?.value }
            )) { wrapper in
                NavigationView {
                    OrderTrackingScreen()
                        .navigationTitle("Order Tracking")
                        .navigationBarTitleDisplayMode(.inline)
                        .toolbar {
                            ToolbarItem(placement: .navigationBarLeading) {
                                Button("Close") {
                                    trackedOrderId = nil
                                }
                            }
                        }
                }
            }
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
    
    private func filterButton(title: String, count: Int) -> some View {
        Button(action: { selectedFilter = title }) {
            HStack(spacing: 4) {
                Text(title)
                    .font(.system(size: 12, weight: selectedFilter == title ? .bold : .medium))
                if count > 0 {
                    Text("(\(count))")
                        .font(.system(size: 11))
                }
            }
            .padding(.horizontal, 12)
            .padding(.vertical, 6)
            .background(selectedFilter == title ? configProvider.currentConfig.theme.primaryColor.opacity(0.15) : Color(.systemGray6))
            .foregroundColor(selectedFilter == title ? configProvider.currentConfig.theme.primaryColor : .secondary)
            .cornerRadius(8)
        }
    }
    
    private var emptyOrdersView: some View {
        VStack(spacing: 16) {
            Spacer()
            Image(systemName: "cart.badge.questionmark")
                .font(.system(size: 48))
                .foregroundColor(.secondary.opacity(0.6))
            Text("No Orders Found")
                .font(.system(size: 17, weight: .semibold))
                .foregroundColor(.primary)
            Text("You have no \(selectedFilter.lowercased()) orders in your history.")
                .font(.system(size: 13))
                .foregroundColor(.secondary)
            Spacer()
        }
        .frame(maxWidth: .infinity)
    }
}

public struct OrderHistoryCard: View {
    public let order: ServerOrderResponse
    public let onTrack: () -> Void
    
    public init(order: ServerOrderResponse, onTrack: @escaping () -> Void) {
        self.order = order
        self.onTrack = onTrack
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Order #\(order.id.prefix(8).uppercased())")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.primary)
                    if let date = order.createdAt {
                        Text(date)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                }
                Spacer()
                statusBadge
            }
            
            Divider()
            
            HStack {
                Text("Total: ₹\(String(format: "%.2f", order.totalAmount))")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundColor(.primary)
                Spacer()
                if isTrackable {
                    Button(action: onTrack) {
                        HStack(spacing: 4) {
                            Image(systemName: "location.fill")
                                .font(.system(size: 11))
                            Text("Track Order")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(CommerceOSTheme.Colors.brandPrimaryDark)
                        .foregroundColor(.white)
                        .cornerRadius(6)
                    }
                }
            }
        }
        .padding(14)
        .background(Color(.systemBackground))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
        )
        .shadow(color: Color.black.opacity(0.03), radius: 4, x: 0, y: 1)
    }
    
    private var isTrackable: Bool {
        let s = order.status.uppercased()
        return s != "DELIVERED" && s != "CANCELLED"
    }
    
    private var statusBadge: some View {
        let (label, color) = badgeConfig
        return Text(label)
            .font(.system(size: 11, weight: .bold))
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(color.opacity(0.12))
            .foregroundColor(color)
            .cornerRadius(6)
    }
    
    private var badgeConfig: (String, Color) {
        switch order.status.uppercased() {
        case "DELIVERED": return ("Delivered", CommerceOSTheme.Colors.brandPrimary)
        case "OUT_FOR_DELIVERY", "IN_TRANSIT": return ("Out for Delivery", CommerceOSTheme.Colors.brandAccent)
        case "PREPARING", "ACCEPTED": return ("Preparing", CommerceOSTheme.Colors.warning)
        case "CANCELLED": return ("Cancelled", CommerceOSTheme.Colors.error)
        default: return (order.status, .gray)
        }
    }
}
