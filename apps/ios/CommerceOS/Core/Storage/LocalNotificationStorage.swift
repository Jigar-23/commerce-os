import Foundation

// MARK: - In-App Notification Model (Ported from Android LocalNotificationStorage.kt)
public struct InAppNotificationItem: Identifiable, Codable, Equatable {
    public let id: String
    public let title: String
    public let message: String
    public let category: String
    public let timestamp: Date
    public var isRead: Bool
    public let actionRoute: String?
    
    public init(
        id: String = UUID().uuidString,
        title: String,
        message: String,
        category: String = "Orders",
        timestamp: Date = Date(),
        isRead: Bool = false,
        actionRoute: String? = nil
    ) {
        self.id = id
        self.title = title
        self.message = message
        self.category = category
        self.timestamp = timestamp
        self.isRead = isRead
        self.actionRoute = actionRoute
    }
}

// MARK: - Local Notification Storage Manager
public class LocalNotificationStorage: ObservableObject {
    public static let shared = LocalNotificationStorage()
    private let storageKey = "commerce_os_in_app_notifications"
    
    @Published public private(set) var notifications: [InAppNotificationItem] = []
    
    public init() {
        loadNotifications()
    }
    
    private func loadNotifications() {
        if let data = UserDefaults.standard.data(forKey: storageKey),
           let decoded = try? JSONDecoder().decode([InAppNotificationItem].self, from: data) {
            self.notifications = decoded
        } else {
            // Production parity with Android LocalNotificationStorage.kt: zero fake clutter
            self.notifications = []
        }
    }
    
    public func markAllAsRead() {
        for i in 0..<notifications.count {
            notifications[i].isRead = true
        }
        save()
    }
    
    public func markAsRead(id: String) {
        if let idx = notifications.firstIndex(where: { $0.id == id }) {
            notifications[idx].isRead = true
            save()
        }
    }
    
    public func add(item: InAppNotificationItem) {
        notifications.insert(item, at: 0)
        save()
    }

    public func clear() {
        notifications.removeAll()
        UserDefaults.standard.removeObject(forKey: storageKey)
    }
    
    private func save() {
        if let encoded = try? JSONEncoder().encode(notifications) {
            UserDefaults.standard.set(encoded, forKey: storageKey)
        }
    }
}
