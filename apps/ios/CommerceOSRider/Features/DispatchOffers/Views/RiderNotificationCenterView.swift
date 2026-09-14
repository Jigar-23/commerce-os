import SwiftUI

public struct RiderNotificationCenterView: View {
    @State private var notifications: [RiderNotificationItem] = [
        RiderNotificationItem(
            notificationId: "notif_surge",
            title: "₹25 Surge Bonus Active",
            message: "Earn an extra ₹25 on every delivery completed from Koramangala Hub until 9 PM.",
            category: "EARNINGS",
            createdAt: Date().addingTimeInterval(-180),
            readAt: nil
        ),
        RiderNotificationItem(
            notificationId: "notif_order",
            title: "Trip Completed • ₹65 Credited",
            message: "Delivery #ORD-8924 successfully handed off. ₹65 has been added to your earnings.",
            category: "ORDERS",
            createdAt: Date().addingTimeInterval(-1800),
            readAt: nil
        ),
        RiderNotificationItem(
            notificationId: "notif_milestone",
            title: "Daily Milestone 4/10 Reached",
            message: "6 more deliveries to unlock your daily ₹150 milestone incentive.",
            category: "EARNINGS",
            createdAt: Date().addingTimeInterval(-3600),
            readAt: Date().addingTimeInterval(-3500)
        ),
        RiderNotificationItem(
            notificationId: "notif_system",
            title: "GPS & Telemetry Compliant",
            message: "Background location telemetry streaming active to dispatch cluster.",
            category: "SYSTEM",
            createdAt: Date().addingTimeInterval(-7200),
            readAt: Date().addingTimeInterval(-7000)
        )
    ]
    
    @State private var selectedCategory: String = "ALL"
    
    public init() {}
    
    private var unreadCount: Int {
        notifications.filter { $0.readAt == nil }.count
    }
    
    private var filteredNotifications: [RiderNotificationItem] {
        if selectedCategory == "ALL" {
            return notifications
        } else {
            return notifications.filter { $0.category.uppercased() == selectedCategory }
        }
    }
    
    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(alignment: .leading, spacing: 14) {
                // Top Header (Matching Android RiderNotificationCenterScreen.kt)
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Alerts & Notifications")
                            .font(.system(size: 20, weight: .black))
                            .foregroundColor(.white)
                        
                        Text(unreadCount > 0 ? "\(unreadCount) unread alert(s)" : "All caught up")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(unreadCount > 0 ? Color(hex: "10B981") : Color(hex: "9CA3AF"))
                    }
                    
                    Spacer()
                    
                    if unreadCount > 0 {
                        Button(action: {
                            for i in notifications.indices {
                                notifications[i].readAt = Date()
                            }
                        }) {
                            HStack(spacing: 4) {
                                Image(systemName: "checkmark")
                                    .font(.system(size: 11, weight: .bold))
                                Text("Mark all read")
                                    .font(.system(size: 12, weight: .bold))
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(Color(hex: "10B981").opacity(0.12))
                            .foregroundColor(Color(hex: "10B981"))
                            .cornerRadius(10)
                            .overlay(
                                RoundedRectangle(cornerRadius: 10)
                                    .stroke(Color(hex: "10B981").opacity(0.3), lineWidth: 1)
                            )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
                .padding(.top, 8)
                
                // Category Filter Chips (Matching Android RiderNotificationCenterScreen.kt)
                HStack(spacing: 8) {
                    categoryChip(key: "ALL", label: "All")
                    categoryChip(key: "ORDERS", label: "📦 Orders")
                    categoryChip(key: "EARNINGS", label: "💰 Earnings")
                    categoryChip(key: "SYSTEM", label: "🔔 System")
                }
                .padding(.horizontal, 16)
                
                // Notification List Cards
                if filteredNotifications.isEmpty {
                    VStack(spacing: 12) {
                        Circle()
                            .fill(Color(hex: "1C1F28"))
                            .frame(width: 56, height: 56)
                            .overlay(
                                Image(systemName: "bell.slash.fill")
                                    .font(.system(size: 24))
                                    .foregroundColor(Color(hex: "9CA3AF"))
                            )
                        Text("No alerts in this category")
                            .font(.system(size: 14, weight: .bold))
                            .foregroundColor(.white)
                        Text("Realtime dispatch offers and system notifications will appear here.")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "9CA3AF"))
                            .multilineTextAlignment(.center)
                            .padding(.horizontal, 32)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top, 40)
                } else {
                    LazyVStack(spacing: 10) {
                        ForEach(filteredNotifications) { item in
                            notificationCard(item: item)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 90)
                }
            }
            .padding(.top, 4)
        }
        .background(Color(hex: "0D0F14").ignoresSafeArea())
    }
    
    private func categoryChip(key: String, label: String) -> some View {
        let isSelected = selectedCategory == key
        return Button(action: { selectedCategory = key }) {
            Text(label)
                .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                .foregroundColor(isSelected ? .white : Color(hex: "9CA3AF"))
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(isSelected ? Color(hex: "10B981") : Color(hex: "1C1F28"))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(isSelected ? Color(hex: "10B981") : Color(hex: "2B2F3B"), lineWidth: 1)
                )
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func notificationCard(item: RiderNotificationItem) -> some View {
        let isUnread = item.readAt == nil
        return HStack(alignment: .top, spacing: 12) {
            // Category Icon Badge
            ZStack {
                RoundedRectangle(cornerRadius: 10)
                    .fill(badgeBg(for: item.category))
                    .frame(width: 36, height: 36)
                Text(badgeEmoji(for: item.category))
                    .font(.system(size: 16))
            }
            
            // Content
            VStack(alignment: .leading, spacing: 4) {
                HStack {
                    Text(item.title)
                        .font(.system(size: 14, weight: isUnread ? .bold : .semibold))
                        .foregroundColor(.white)
                    
                    Spacer()
                    
                    if isUnread {
                        Circle()
                            .fill(Color(hex: "10B981"))
                            .frame(width: 7, height: 7)
                    }
                    
                    Button(action: {
                        notifications.removeAll { $0.notificationId == item.notificationId }
                    }) {
                        Image(systemName: "xmark")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color(hex: "64748B"))
                            .frame(width: 20, height: 20)
                    }
                    .buttonStyle(PlainButtonStyle())
                }
                
                Text(item.message)
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "94A3B8"))
                    .lineLimit(3)
                
                if let date = item.createdAt {
                    Text(timeAgo(date))
                        .font(.system(size: 10, weight: .medium))
                        .foregroundColor(Color(hex: "64748B"))
                        .padding(.top, 2)
                }
            }
        }
        .padding(14)
        .background(isUnread ? Color(hex: "1C1F28") : Color(hex: "14161C"))
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(isUnread ? Color(hex: "10B981").opacity(0.4) : Color(hex: "262933"), lineWidth: 1)
        )
        .onTapGesture {
            if let idx = notifications.firstIndex(where: { $0.notificationId == item.notificationId }) {
                notifications[idx].readAt = Date()
            }
        }
    }
    
    private func badgeBg(for category: String) -> Color {
        switch category.uppercased() {
        case "ORDERS": return Color(hex: "10B981").opacity(0.15)
        case "EARNINGS": return Color(hex: "F59E0B").opacity(0.15)
        default: return Color(hex: "3B82F6").opacity(0.15)
        }
    }
    
    private func badgeEmoji(for category: String) -> String {
        switch category.uppercased() {
        case "ORDERS": return "📦"
        case "EARNINGS": return "💰"
        default: return "🔔"
        }
    }
    
    private func timeAgo(_ date: Date) -> String {
        let interval = Int(Date().timeIntervalSince(date))
        if interval < 60 { return "Just now" }
        if interval < 3600 { return "\(interval / 60)m ago" }
        return "\(interval / 3600)h ago"
    }
}
