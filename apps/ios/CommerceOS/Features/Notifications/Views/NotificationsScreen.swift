import SwiftUI
import UIKit

// MARK: - Customer Notification Center (Ported from Android NotificationsScreen.kt)
public struct NotificationsScreen: View {
    @Environment(\.presentationMode) private var presentationMode
    @ObservedObject private var storage = LocalNotificationStorage.shared
    
    public let onBack: () -> Void
    public let onNavigateToOrders: (() -> Void)?
    public let onNavigateToPrescriptions: (() -> Void)?
    
    @State private var selectedCategory: String = "All"
    private let categories = ["All", "Orders", "Prescriptions", "Refills"]
    
    public init(
        onBack: @escaping () -> Void = {},
        onNavigateToOrders: (() -> Void)? = nil,
        onNavigateToPrescriptions: (() -> Void)? = nil
    ) {
        self.onBack = onBack
        self.onNavigateToOrders = onNavigateToOrders
        self.onNavigateToPrescriptions = onNavigateToPrescriptions
    }
    
    private var filteredList: [InAppNotificationItem] {
        if selectedCategory == "All" {
            return storage.notifications
        } else {
            return storage.notifications.filter { $0.category.caseInsensitiveCompare(selectedCategory) == .orderedSame }
        }
    }
    
    private var unreadCount: Int {
        storage.notifications.filter { !$0.isRead }.count
    }
    
    public var body: some View {
        VStack(spacing: 0) {
            // Header Bar
            HStack {
                Button(action: {
                    onBack()
                    presentationMode.wrappedValue.dismiss()
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
                
                HStack(spacing: 6) {
                    Text("Notifications")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    
                    if unreadCount > 0 {
                        Text("\(unreadCount)")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.white)
                            .padding(.horizontal, 6)
                            .padding(.vertical, 2)
                            .background(Color(hex: "DC2626"))
                            .cornerRadius(10)
                    }
                }
                .padding(.leading, 6)
                
                Spacer()
                
                if unreadCount > 0 {
                    Button(action: {
                        storage.markAllAsRead()
                    }) {
                        Text("Mark all read")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(Color(hex: "0E675E"))
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 10)
            .background(Color.white)
            .overlay(Divider(), alignment: .bottom)
            
            // Category Tabs
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    ForEach(categories, id: \.self) { cat in
                        let isSelected = selectedCategory == cat
                        Button(action: {
                            selectedCategory = cat
                        }) {
                            Text(cat)
                                .font(.system(size: 12, weight: isSelected ? .bold : .medium))
                                .foregroundColor(isSelected ? .white : Color(hex: "0F172A"))
                                .padding(.horizontal, 14)
                                .padding(.vertical, 7)
                                .background(isSelected ? Color(hex: "0E675E") : Color.white)
                                .cornerRadius(16)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 16)
                                        .stroke(isSelected ? Color(hex: "0E675E") : Color(hex: "E2E8F0"), lineWidth: 1)
                                )
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 8)
            }
            .background(Color.white)
            .overlay(Divider(), alignment: .bottom)
            
            // List of Notifications
            if filteredList.isEmpty {
                VStack(spacing: 12) {
                    Spacer().frame(height: 60)
                    Circle()
                        .fill(Color(hex: "ECFDF5"))
                        .frame(width: 72, height: 72)
                        .overlay(
                            Image(systemName: "bell.slash.fill")
                                .font(.system(size: 28))
                                .foregroundColor(Color(hex: "0E675E"))
                        )
                    Text("No Notifications")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(Color(hex: "0F172A"))
                    Text("You're all caught up! New order and refill alerts will appear here.")
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "64748B"))
                        .multilineTextAlignment(.center)
                        .padding(.horizontal, 32)
                    Spacer()
                }
                .frame(maxWidth: .infinity)
                .background(Color(hex: "F8FAFC"))
            } else {
                ScrollView(.vertical, showsIndicators: false) {
                    VStack(spacing: 10) {
                        ForEach(filteredList) { item in
                            notificationCard(item: item)
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 14)
                }
                .background(Color(hex: "F8FAFC"))
            }
        }
        .navigationBarHidden(true)
    }
    
    private func notificationCard(item: InAppNotificationItem) -> some View {
        Button(action: {
            storage.markAsRead(id: item.id)
            if item.category == "Orders" {
                onNavigateToOrders?()
            } else if item.category == "Prescriptions" {
                onNavigateToPrescriptions?()
            }
        }) {
            HStack(alignment: .top, spacing: 12) {
                // Category Icon
                Circle()
                    .fill(categoryColor(item.category).opacity(0.12))
                    .frame(width: 40, height: 40)
                    .overlay(
                        Image(systemName: categoryIcon(item.category))
                            .font(.system(size: 17))
                            .foregroundColor(categoryColor(item.category))
                    )
                
                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text(item.title)
                            .font(.system(size: 13.5, weight: item.isRead ? .semibold : .bold))
                            .foregroundColor(Color(hex: "0F172A"))
                        
                        Spacer()
                        
                        if !item.isRead {
                            Circle()
                                .fill(Color(hex: "0E675E"))
                                .frame(width: 8, height: 8)
                        }
                    }
                    
                    Text(item.message)
                        .font(.system(size: 12))
                        .foregroundColor(Color(hex: "475569"))
                        .lineLimit(2)
                        .lineSpacing(2)
                    
                    Text(formatTimeAgo(item.timestamp))
                        .font(.system(size: 10.5))
                        .foregroundColor(Color(hex: "94A3B8"))
                        .padding(.top, 2)
                }
            }
            .padding(14)
            .background(item.isRead ? Color.white : Color(hex: "F0FDF4"))
            .cornerRadius(12)
            .overlay(
                RoundedRectangle(cornerRadius: 12)
                    .stroke(item.isRead ? Color(hex: "E2E8F0") : Color(hex: "BBF7D0"), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.02), radius: 2, x: 0, y: 1)
        }
        .buttonStyle(PlainButtonStyle())
    }
    
    private func categoryIcon(_ category: String) -> String {
        switch category.lowercased() {
        case "orders": return "bag.fill"
        case "prescriptions": return "doc.text.fill"
        case "refills": return "arrow.triangle.2.circlepath.circle.fill"
        default: return "bell.fill"
        }
    }
    
    private func categoryColor(_ category: String) -> Color {
        switch category.lowercased() {
        case "orders": return Color(hex: "0E675E")
        case "prescriptions": return Color(hex: "0284C7")
        case "refills": return Color(hex: "D97706")
        default: return Color(hex: "64748B")
        }
    }
    
    private func formatTimeAgo(_ date: Date) -> String {
        let diff = Int(Date().timeIntervalSince(date))
        if diff < 60 { return "Just now" }
        if diff < 3600 { return "\(diff / 60) mins ago" }
        if diff < 86400 { return "\(diff / 3600) hours ago" }
        return "\(diff / 86400) days ago"
    }
}
