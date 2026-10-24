import SwiftUI
import UIKit

public class RiderAppDelegate: NSObject, UIApplicationDelegate {
    public func application(
        _ application: UIApplication,
        didFinishLaunchingWithOptions launchOptions: [UIApplication.LaunchOptionsKey: Any]? = nil
    ) -> Bool {
        Task { @MainActor in
            RiderPushNotificationManager.shared.requestAuthorization()
        }
        return true
    }

    public func application(
        _ application: UIApplication,
        didRegisterForRemoteNotificationsWithDeviceToken deviceToken: Data
    ) {
        Task { @MainActor in
            RiderPushNotificationManager.shared.handleDeviceTokenRegistration(deviceTokenData: deviceToken)
            await RiderSessionManager.shared.ensureOnlineShift()
        }
    }

    public func application(
        _ application: UIApplication,
        didFailToRegisterForRemoteNotificationsWithError error: Error
    ) {
        print("[RiderAppDelegate] APNs registration error: \(error.localizedDescription)")
    }

    public func application(
        _ application: UIApplication,
        didReceiveRemoteNotification userInfo: [AnyHashable: Any],
        fetchCompletionHandler completionHandler: @escaping (UIBackgroundFetchResult) -> Void
    ) {
        Task { @MainActor in
            RiderPushNotificationManager.shared.handleRemoteNotification(userInfo: userInfo)
            await RiderOfferEventPipeline.shared.pollActiveOffersOnce()
            completionHandler(.newData)
        }
    }
}

@main
struct CommerceOSRiderApp: App {
    @UIApplicationDelegateAdaptor(RiderAppDelegate.self) var appDelegate
    @StateObject private var container = RiderContainer.shared
    @Environment(\.scenePhase) private var scenePhase
    
    var body: some Scene {
        WindowGroup {
            RiderRootView()
                .environmentObject(container)
                .environmentObject(container.sessionManager)
                .environmentObject(container.locationManager)
                .environmentObject(container.offerPipeline)
                .preferredColorScheme(.dark)
                .background(RiderTheme.Colors.hudBackground.ignoresSafeArea())
                .accentColor(RiderTheme.Colors.safetyGreen)
                .onAppear {
                    RiderHapticEngine.shared.playManeuverPulse()
                }
        }
        .onChange(of: scenePhase) { newPhase in
            switch newPhase {
            case .active:
                Task {
                    await container.sessionManager.refreshActiveSession()
                }
                if container.sessionManager.isShiftOnline || container.sessionManager.activeSession != nil {
                    container.locationManager.startBackgroundTracking()
                }
            case .background:
                if container.sessionManager.isShiftOnline || container.sessionManager.activeSession != nil {
                    container.locationManager.startBackgroundTracking()
                }
            case .inactive:
                break
            @unknown default:
                break
            }
        }
    }
}
