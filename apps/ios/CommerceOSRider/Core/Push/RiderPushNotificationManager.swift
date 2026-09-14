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

    public func handleDeviceTokenRegistration(deviceTokenData: Data) {
        let token = deviceTokenData.map { String(format: "%02.2hhx", $0) }.joined()
        self.deviceToken = token
        self.isRegisteredForRemoteNotifications = true
        UserDefaults.standard.set(token, forKey: "rider_apns_device_token")
    }

    public func handleRemoteNotification(userInfo: [AnyHashable: Any]) {
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
            RiderPushNotificationManager.shared.handleRemoteNotification(userInfo: info)
        }
        completionHandler([.banner, .sound, .badge])
    }
}
