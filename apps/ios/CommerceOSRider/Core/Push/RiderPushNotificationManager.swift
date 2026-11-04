import Foundation
import UserNotifications
import UIKit

@MainActor
public final class RiderPushNotificationManager: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    public static let shared = RiderPushNotificationManager()

    @Published public var deviceToken: String? = nil
    @Published public var isRegisteredForRemoteNotifications: Bool = false

    public override init() {
        super.init()
        self.requestAuthorization()
    }

    public func requestAuthorization() {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, error in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }

    public func postOfferNotification(offer: DispatchOfferDto) {
        guard RiderAPIClient.shared.isAuthenticated && RiderSessionManager.shared.isShiftOnline else {
            print("[RiderPushNotificationManager] Suppressing local notification: Rider is logged out or offline.")
            return
        }
        let content = UNMutableNotificationContent()
        content.title = "🚀 New Delivery Dispatch Offer • ₹\(Int(offer.payoutAmount))"
        content.subtitle = "Order #\(offer.orderId.suffix(8).uppercased())"
        content.body = "Pickup: \(offer.merchantName)\nDrop: \(offer.customerAddress)\nTap to accept within 30s."
        content.sound = UNNotificationSound.default
        if #available(iOS 15.0, *) {
            content.interruptionLevel = .timeSensitive
        }
        content.userInfo = [
            "offerId": offer.offerId,
            "orderId": offer.orderId,
            "payoutAmount": offer.payoutAmount,
            "merchantName": offer.merchantName,
            "customerName": offer.customerName
        ]

        let trigger = UNTimeIntervalNotificationTrigger(timeInterval: 0.1, repeats: false)
        let req = UNNotificationRequest(
            identifier: "offer_\(offer.offerId)",
            content: content,
            trigger: trigger
        )

        UNUserNotificationCenter.current().add(req) { error in
            if let error = error {
                print("[RiderPushNotificationManager] Error posting local notification: \(error.localizedDescription)")
            }
        }
    }

    public func handleDeviceTokenRegistration(deviceTokenData: Data) {
        let token = deviceTokenData.map { String(format: "%02.2hhx", $0) }.joined()
        self.deviceToken = token
        self.isRegisteredForRemoteNotifications = true
        UserDefaults.standard.set(token, forKey: "rider_apns_device_token")
    }

    public func handleRemoteNotification(userInfo: [AnyHashable: Any]) {
        guard RiderAPIClient.shared.isAuthenticated && RiderSessionManager.shared.isShiftOnline else {
            print("[RiderPushNotificationManager] Dropped remote notification: Rider is logged out or offline.")
            return
        }

        // Handle Silent Watchdog PING_LOCATION Wakeups
        let action = (userInfo["action"] as? String) ?? (userInfo["type"] as? String) ?? ""
        if action.caseInsensitiveCompare("PING_LOCATION") == .orderedSame || action.caseInsensitiveCompare("WAKEUP_LOCATION") == .orderedSame {
            print("[RiderPushNotificationManager] Received silent watchdog PING_LOCATION. Triggering location tracking.")
            RiderBackgroundLocationManager.shared.startBackgroundTracking()
            return
        }

        if let offer = OfferPayloadValidator.validate(payload: userInfo) {
            RiderOfferEventPipeline.shared.receiveOffer(offer: offer)
        }
    }

    // MARK: - UNUserNotificationCenterDelegate
    nonisolated public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        let info = notification.request.content.userInfo
        Task { @MainActor in
            guard RiderAPIClient.shared.isAuthenticated && RiderSessionManager.shared.isShiftOnline else {
                print("[RiderPushNotificationManager] Suppressing foreground notification banner: Rider is logged out or offline.")
                completionHandler([])
                return
            }
            RiderPushNotificationManager.shared.handleRemoteNotification(userInfo: info)
            completionHandler([.banner, .sound, .badge, .list])
        }
    }

    nonisolated public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        didReceive response: UNNotificationResponse,
        withCompletionHandler completionHandler: @escaping () -> Void
    ) {
        let info = response.notification.request.content.userInfo
        Task { @MainActor in
            guard RiderAPIClient.shared.isAuthenticated && RiderSessionManager.shared.isShiftOnline else {
                print("[RiderPushNotificationManager] Discarding notification click: Rider is logged out or offline.")
                completionHandler()
                return
            }
            RiderPushNotificationManager.shared.handleRemoteNotification(userInfo: info)
            completionHandler()
        }
    }
}
