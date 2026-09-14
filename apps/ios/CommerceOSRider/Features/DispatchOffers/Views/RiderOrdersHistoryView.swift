import SwiftUI

public struct RiderOrdersHistoryView: View {
    @EnvironmentObject private var sessionManager: RiderSessionManager
    let onSelectActiveOrder: () -> Void
    
    @State private var completedOrders: [CompletedOrderItem] = [
        CompletedOrderItem(
            orderId: "ord_8921a9c1",
            merchantName: "Koramangala Dark Store Hub",
            customerName: "Priya Sharma",
            customerAddress: "Flat 402, Green Glen Heights, Bellandur",
            itemsCount: 3,
            distanceKm: 2.4,
            payout: 65.00,
            deliveredAt: "10 mins ago"
        ),
        CompletedOrderItem(
            orderId: "ord_7612b4d8",
            merchantName: "Apollo Pharmacy HSR Hub",
            customerName: "Amit Verma",
            customerAddress: "Villa 12, Sobha Iris, Outer Ring Rd",
            itemsCount: 1,
            distanceKm: 3.1,
            payout: 85.00,
            deliveredAt: "45 mins ago"
        ),
        CompletedOrderItem(
            orderId: "ord_6501c3e4",
            merchantName: "Koramangala Dark Store Hub",
            customerName: "Rahul Menon",
            customerAddress: "Tower 3, Prestige Ferns, Kadubeesanahalli",
            itemsCount: 4,
            distanceKm: 1.8,
            payout: 55.00,
            deliveredAt: "1 hr ago"
        ),
        CompletedOrderItem(
            orderId: "ord_5419d2f7",
            merchantName: "Koramangala Dark Store Hub",
            customerName: "Sneha Reddy",
            customerAddress: "House 24, 5th Cross, HSR Sector 2",
            itemsCount: 2,
            distanceKm: 2.0,
            payout: 60.00,
            deliveredAt: "2 hrs ago"
        )
    ]
    
    @State private var copiedOrderId: String? = nil
    
    public init(onSelectActiveOrder: @escaping () -> Void = {}) {
        self.onSelectActiveOrder = onSelectActiveOrder
    }
    
    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Active Delivery Banner if one exists
                    if let session = sessionManager.activeSession {
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                Text("CURRENT ACTIVE DELIVERY")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(RiderTheme.Colors.safetyGreen)
                                    .tracking(0.5)
                                Spacer()
                                Text(session.status.replacingOccurrences(of: "_", with: " "))
                                    .font(.system(size: 10, weight: .bold))
                                    .foregroundColor(RiderTheme.Colors.safetyGreen)
                                    .padding(.horizontal, 8)
                                    .padding(.vertical, 3)
                                    .background(RiderTheme.Colors.safetyGreen.opacity(0.15))
                                    .cornerRadius(6)
                            }
                            
                            HStack {
                                VStack(alignment: .leading, spacing: 2) {
                                    let shortId = session.orderId.count > 8 ? String(session.orderId.suffix(8)).uppercased() : session.orderId.uppercased()
                                    Text("ORDER #\(shortId)")
                                        .font(.system(size: 15, weight: .black))
                                        .foregroundColor(.white)
                                    Text("\(session.merchantName) → \(session.customerName)")
                                        .font(.system(size: 12))
                                        .foregroundColor(Color(hex: "94A3B8"))
                                }
                                Spacer()
                                Button(action: onSelectActiveOrder) {
                                    Text("VIEW")
                                        .font(.system(size: 11, weight: .black))
                                        .foregroundColor(.black)
                                        .padding(.horizontal, 12)
                                        .padding(.vertical, 6)
                                        .background(RiderTheme.Colors.safetyGreen)
                                        .cornerRadius(8)
                                }
                            }
                        }
                        .padding(14)
                        .background(Color(hex: "16181F"))
                        .cornerRadius(16)
                        .overlay(
                            RoundedRectangle(cornerRadius: 16)
                                .stroke(RiderTheme.Colors.safetyGreen, lineWidth: 1.5)
                        )
                        .padding(.horizontal, 16)
                    }
                    
                    // Completed Orders Header
                    HStack {
                        Text("TODAY'S COMPLETED DELIVERIES")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "94A3B8"))
                            .tracking(0.5)
                        Spacer()
                        Text("\(completedOrders.count) orders")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                    .padding(.horizontal, 16)
                    .padding(.top, 4)
                    
                    // Completed Orders List
                    ForEach(completedOrders) { order in
                        VStack(alignment: .leading, spacing: 10) {
                            HStack {
                                HStack(spacing: 8) {
                                    Image(systemName: "checkmark.circle.fill")
                                        .font(.system(size: 14))
                                        .foregroundColor(RiderTheme.Colors.safetyGreen)
                                    
                                    let shortId = order.orderId.count > 8 ? String(order.orderId.suffix(8)).uppercased() : order.orderId.uppercased()
                                    Text("ORDER #\(shortId)")
                                        .font(.system(size: 13, weight: .bold))
                                        .foregroundColor(.white)
                                    
                                    Button(action: {
                                        UIPasteboard.general.string = order.orderId
                                        copiedOrderId = order.orderId
                                        DispatchQueue.main.asyncAfter(deadline: .now() + 2.0) {
                                            if copiedOrderId == order.orderId {
                                                copiedOrderId = nil
                                            }
                                        }
                                    }) {
                                        Text(copiedOrderId == order.orderId ? "COPIED" : "COPY")
                                            .font(.system(size: 9, weight: .black))
                                            .foregroundColor(Color(hex: "38BDF8"))
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(Color(hex: "1E293B"))
                                            .cornerRadius(4)
                                    }
                                }
                                
                                Spacer()
                                
                                Text("+₹\(String(format: "%.2f", order.payout))")
                                    .font(.system(size: 15, weight: .black))
                                    .foregroundColor(RiderTheme.Colors.safetyGreen)
                            }
                            
                            Text("\(order.merchantName) → \(order.customerName)")
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "CBD5E1"))
                            
                            HStack {
                                Text("📦 \(order.itemsCount) item(s) • \(String(format: "%.1f", order.distanceKm)) km")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "94A3B8"))
                                Spacer()
                                Text(order.deliveredAt)
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "64748B"))
                            }
                        }
                        .padding(14)
                        .background(Color(hex: "16181F"))
                        .cornerRadius(14)
                        .overlay(
                            RoundedRectangle(cornerRadius: 14)
                                .stroke(Color(hex: "262933"), lineWidth: 1)
                        )
                        .padding(.horizontal, 16)
                    }
                    
                    Spacer(minLength: 40)
                }
                .padding(.top, 8)
            }
            .background(Color(hex: "0D0F14").ignoresSafeArea())
            .navigationTitle("Orders History")
            .navigationBarTitleDisplayMode(.inline)
        }
        .navigationViewStyle(.stack)
    }
}

public struct CompletedOrderItem: Identifiable {
    public let id = UUID()
    public let orderId: String
    public let merchantName: String
    public let customerName: String
    public let customerAddress: String
    public let itemsCount: Int
    public let distanceKm: Double
    public let payout: Double
    public let deliveredAt: String
}
