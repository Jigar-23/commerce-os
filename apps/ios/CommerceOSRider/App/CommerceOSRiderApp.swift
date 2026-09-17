import SwiftUI

@main
struct CommerceOSRiderApp: App {
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
