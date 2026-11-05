import Foundation
import UserNotifications
import UIKit

@MainActor
public final class CustomerPushNotificationManager: NSObject, ObservableObject, UNUserNotificationCenterDelegate {
    public static let shared = CustomerPushNotificationManager()

    @Published public var deviceToken: String? = nil
    @Published public var isRegisteredForRemoteNotifications: Bool = false

    public override init() {
        super.init()
        self.requestAuthorization()
    }

    public func requestAuthorization() {
        UNUserNotificationCenter.current().delegate = self
        UNUserNotificationCenter.current().requestAuthorization(options: [.alert, .sound, .badge]) { granted, _ in
            if granted {
                DispatchQueue.main.async {
                    UIApplication.shared.registerForRemoteNotifications()
                }
            }
        }
    }

    public func handleDeviceToken(deviceToken: Data) {
        let tokenString = deviceToken.map { String(format: "%02.2hhx", $0) }.joined()
        self.deviceToken = tokenString
        self.isRegisteredForRemoteNotifications = true
        self.registerTokenWithBackend(token: tokenString)
    }

    public func registerTokenWithBackend(token: String) {
        Task {
            do {
                struct CustomerDeviceTokenPayload: Encodable {
                    let deviceToken: String
                    let platform: String
                }
                let body = CustomerDeviceTokenPayload(deviceToken: token, platform: "IOS")
                let _: [String: AnyCodable] = try await APIClient.shared.post(
                    endpoint: "/api/v1/customer/device-token",
                    body: body
                )
            } catch {
                // Ignore transient network errors; retries on next app foreground
            }
        }
    }

    public func userNotificationCenter(
        _ center: UNUserNotificationCenter,
        willPresent notification: UNNotification,
        withCompletionHandler completionHandler: @escaping (UNNotificationPresentationOptions) -> Void
    ) {
        if #available(iOS 14.0, *) {
            completionHandler([.banner, .sound, .badge])
        } else {
            completionHandler([.alert, .sound, .badge])
        }
    }
}
