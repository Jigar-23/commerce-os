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
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                // Top Header (Matching Android OrderHistoryScreen.kt)
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
                
                // Filter Pills (Matching Android OrderHistoryScreen.kt)
                HStack(spacing: 8) {
                    filterPill(key: "ALL", label: "All Orders", count: orderRepo.customerOrders.count)
                    filterPill(key: "ACTIVE", label: "Active", count: activeOrders.count)
                    filterPill(key: "DELIVERED", label: "Delivered", count: deliveredOrders.count)
                }
                .padding(.horizontal, 14)
                
                // Orders List
                if filteredOrders.isEmpty {
                    emptyOrdersView
                        .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 12) {
                        ForEach(filteredOrders) { order in
                            OrderHistoryCard(order: order) {
                                trackedOrderId = order.id
                            }
                        }
                    }
                    .padding(.horizontal, 14)
                    .padding(.bottom, 90)
                }
            }
            .padding(.top, 4)
        }
        .background(Color(hex: "F4F5F7").ignoresSafeArea())
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
    
    public init(order: ServerOrderResponse, onTrack: @escaping () -> Void) {
        self.order = order
        self.onTrack = onTrack
    }
    
    public var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Order #\(order.id.prefix(8).uppercased())")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    if let date = order.createdAt {
                        Text(date)
                            .font(.system(size: 11))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }
                Spacer()
                statusBadge
            }
            
            Divider()
                .background(Color(hex: "F1F5F9"))
            
            // Items List
            if let items = order.items, !items.isEmpty {
                VStack(alignment: .leading, spacing: 6) {
                    ForEach(items.prefix(3), id: \.sku) { item in
                        HStack(spacing: 8) {
                            let imgUrl = MedicineImageResolver.resolve(sku: item.sku, name: item.name)
                            if let url = URL(string: imgUrl), !imgUrl.isEmpty {
                                AsyncImage(url: url) { phase in
                                    switch phase {
                                    case .success(let img):
                                        img.resizable()
                                            .aspectRatio(contentMode: .fit)
                                            .frame(width: 28, height: 28)
                                    default:
                                        Image(systemName: "pills.fill")
                                            .font(.system(size: 12))
                                            .foregroundColor(Color(hex: "059669"))
                                            .frame(width: 28, height: 28)
                                    }
                                }
                            } else {
                                Image(systemName: "pills.fill")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(hex: "059669"))
                                    .frame(width: 28, height: 28)
                            }
                            
                            Text("\(item.quantity)x \(item.name ?? item.sku)")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "334155"))
                                .lineLimit(1)
                            Spacer()
                            Text("₹\(String(format: "%.2f", (item.price ?? 0.0) * Double(item.quantity)))")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                    }
                    if items.count > 3 {
                        Text("+ \(items.count - 3) more items")
                            .font(.system(size: 11, weight: .medium))
                            .foregroundColor(Color(hex: "64748B"))
                    }
                }
            }
            
            Divider()
                .background(Color(hex: "F1F5F9"))
            
            // Bottom Action Row
            HStack {
                VStack(alignment: .leading, spacing: 1) {
                    Text("Total Paid")
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Color(hex: "64748B"))
                    Text("₹\(String(format: "%.2f", order.totalAmount))")
                        .font(.system(size: 14, weight: .black))
                        .foregroundColor(Color(hex: "0F172A"))
                }
                
                Spacer()
                
                Button(action: onTrack) {
                    HStack(spacing: 4) {
                        Image(systemName: "location.fill")
                            .font(.system(size: 11))
                        Text(order.status.uppercased() == "DELIVERED" ? "View Details" : "Track Order")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 7)
                    .background(Color(hex: "059669"))
                    .foregroundColor(.white)
                    .cornerRadius(8)
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
        .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
    }
    
    private var statusBadge: some View {
        let s = order.status.uppercased()
        let isDelivered = s == "DELIVERED"
        let isCancelled = s == "CANCELLED"
        let bg = isDelivered ? Color(hex: "ECFDF5") : (isCancelled ? Color(hex: "FEF2F2") : Color(hex: "EFF6FF"))
        let fg = isDelivered ? Color(hex: "059669") : (isCancelled ? Color(hex: "DC2626") : Color(hex: "2563EB"))
        
        return Text(s.replacingOccurrences(of: "_", with: " "))
            .font(.system(size: 10, weight: .black))
            .foregroundColor(fg)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(bg)
            .cornerRadius(6)
    }
}
