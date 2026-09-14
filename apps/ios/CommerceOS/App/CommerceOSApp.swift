import SwiftUI

@main
struct CommerceOSApp: App {
    @StateObject private var container = AppContainer.shared
    
    var body: some Scene {
        WindowGroup {
            Group {
                if container.isAuthenticated {
                    MainTabView()
                } else {
                    AuthScreen()
                }
            }
            .environmentObject(container)
            .environmentObject(container.cartStore)
            .environmentObject(container.configProvider)
            .environmentObject(container.trackingRepository)
            .environmentObject(container.orderRepository)
            .environmentObject(container.catalogRepository)
            .environmentObject(container.addressRepository)
        }
    }
}
