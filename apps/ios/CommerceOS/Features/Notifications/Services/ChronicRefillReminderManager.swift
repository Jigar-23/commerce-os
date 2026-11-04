import Foundation
import UserNotifications

// MARK: - On-Device Chronic Medicine Refill Scheduler (Ported from Android ChronicRefillReminderManager.kt)
/// Schedules local notifications entirely on-device without requiring server round-trips.
/// For chronic medications (e.g. Metformin, Telmisartan, Amlodipine), schedules an advance reminder
/// 3 days prior to supply depletion (Day 27 at 9:00 AM).
public enum ChronicRefillReminderManager {
    private static let chronicKeywords = [
        "metformin", "telmisartan", "amlodipine", "atorvastatin", "glimepiride",
        "thyronorm", "losartan", "pantoprazole", "aspirin", "clopidogrel",
        "insulin", "rosuvastatin", "montelukast", "bp", "sugar", "diabetes", "hypertension"
    ]
    
    public static func isChronicMedication(name: String, sku: String) -> Bool {
        let lower = (name + " " + sku).lowercased()
        return chronicKeywords.contains { lower.contains($0) }
    }
    
    public static func scheduleRefillReminder(
        medicineName: String,
        sku: String,
        totalSupplyDays: Int = 30
    ) {
        let center = UNUserNotificationCenter.current()
        
        // Request authorization if needed
        center.requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            guard granted else { return }
            
            let reminderDay = max(totalSupplyDays - 3, 1)
            let triggerDate = Calendar.current.date(byAdding: .day, value: reminderDay, to: Date()) ?? Date().addingTimeInterval(Double(reminderDay * 86400))
            
            var dateComponents = Calendar.current.dateComponents([.year, .month, .day], from: triggerDate)
            dateComponents.hour = 9
            dateComponents.minute = 0
            
            let content = UNMutableNotificationContent()
            content.title = "Refill Reminder: \(medicineName)"
            content.body = "Your 30-day supply of \(medicineName) is running low. Tap to reorder and get delivery in 10 minutes."
            content.sound = .default
            content.userInfo = [
                "type": "CHRONIC_REFILL",
                "sku": sku,
                "medicineName": medicineName
            ]
            
            let trigger = UNCalendarNotificationTrigger(dateMatching: dateComponents, repeats: false)
            let identifier = "refill_\(sku)_\(medicineName.hashValue)"
            let request = UNNotificationRequest(identifier: identifier, content: content, trigger: trigger)
            
            center.add(request) { error in
                if let error = error {
                    print("[ChronicRefillReminderManager] Scheduling failed: \(error.localizedDescription)")
                }
            }
        }
    }
}
