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
        if let savedToken = UserDefaults.standard.string(forKey: "pending_apns_device_token"), !savedToken.isEmpty {
            self.deviceToken = savedToken
            self.isRegisteredForRemoteNotifications = true
        }
        self.requestAuthorization()
        NotificationCenter.default.addObserver(
            forName: UIApplication.willEnterForegroundNotification,
            object: nil,
            queue: .main
        ) { [weak self] _ in
            self?.syncPendingToken()
        }
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
        UserDefaults.standard.set(tokenString, forKey: "pending_apns_device_token")
        self.registerTokenWithBackend(token: tokenString)
    }

    public func syncPendingToken() {
        if let unregisterToken = UserDefaults.standard.string(forKey: "pending_apns_unregister_token"), !unregisterToken.isEmpty {
            let pendingAuth = UserDefaults.standard.string(forKey: "pending_unregister_auth_token") ?? KeychainHelper.shared.get(key: "auth_token")
            Task {
                await self.unregisterTokenWithBackend(explicitToken: unregisterToken, authToken: pendingAuth)
            }
        }
        guard let token = UserDefaults.standard.string(forKey: "pending_apns_device_token"), !token.isEmpty else {
            return
        }
        self.registerTokenWithBackend(token: token)
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
                UserDefaults.standard.set(true, forKey: "apns_device_token_synced")
            } catch {
                // Ignore transient network errors; retries on next app foreground or authentication
            }
        }
    }

    public func unregisterTokenWithBackend(explicitToken: String? = nil, authToken: String? = nil) async {
        let tokenToUnregister = explicitToken 
            ?? self.deviceToken 
            ?? UserDefaults.standard.string(forKey: "pending_apns_unregister_token")
            ?? UserDefaults.standard.string(forKey: "pending_apns_device_token")

        if explicitToken == nil {
            self.deviceToken = nil
            self.isRegisteredForRemoteNotifications = false
            UserDefaults.standard.removeObject(forKey: "pending_apns_device_token")
            UserDefaults.standard.removeObject(forKey: "apns_device_token_synced")
        }

        struct UnregisterPayload: Encodable {
            let deviceToken: String?
            let platform: String
        }
        let body = UnregisterPayload(deviceToken: tokenToUnregister, platform: "IOS")

        var headers: [String: String] = [:]
        let effectiveAuth = authToken ?? KeychainHelper.shared.get(key: "auth_token")
        if let token = effectiveAuth, !token.isEmpty {
            headers["Authorization"] = "Bearer \(token)"
        }

        do {
            let encoder = JSONEncoder()
            encoder.keyEncodingStrategy = .convertToSnakeCase
            let data = try encoder.encode(body)
            let _: [String: AnyCodable] = try await APIClient.shared.request(
                endpoint: "/api/v1/customer/device-token",
                method: "DELETE",
                body: data,
                headers: headers
            )
            // Clear pending retry markers strictly upon backend confirmation (Finding 5)
            UserDefaults.standard.removeObject(forKey: "pending_apns_unregister_token")
            UserDefaults.standard.removeObject(forKey: "pending_unregister_auth_token")
        } catch {
            // Persist failed unregister token and auth context for reliable retry on next sync (Finding 5)
            if let t = tokenToUnregister {
                UserDefaults.standard.set(t, forKey: "pending_apns_unregister_token")
            }
            if let a = effectiveAuth {
                UserDefaults.standard.set(a, forKey: "pending_unregister_auth_token")
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
