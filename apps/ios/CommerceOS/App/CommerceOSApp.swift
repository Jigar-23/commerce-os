import SwiftUI

@main
struct CommerceOSApp: App {
    @StateObject private var container = AppContainer.shared
    @State private var isSplashVisible: Bool = true
    @State private var isLocationLockinVisible: Bool = false
    
    var body: some Scene {
        WindowGroup {
            Group {
                if isSplashVisible {
                    SplashScreen {
                        withAnimation(.easeInOut(duration: 0.3)) {
                            isSplashVisible = false
                            isLocationLockinVisible = true
                        }
                    }
                } else if isLocationLockinVisible {
                    let addr = container.addressRepository.selectedAddress
                    let resolvedLocality = addr?.city ?? addr?.displaySummary ?? container.addressRepository.locationHeaderTitle
                    LocationLockinScreen(locality: resolvedLocality) {
                        withAnimation(.easeInOut(duration: 0.35)) {
                            isLocationLockinVisible = false
                        }
                    }
                } else if container.isAuthenticated {
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
