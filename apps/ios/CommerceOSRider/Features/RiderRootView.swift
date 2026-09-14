import SwiftUI

public struct RiderRootView: View {
    @EnvironmentObject private var container: RiderContainer
    @EnvironmentObject private var sessionManager: RiderSessionManager
    @EnvironmentObject private var offerPipeline: RiderOfferEventPipeline
    @State private var selectedTab: Int = 0
    @State private var isTogglingShift: Bool = false
    
    public init() {
        let appearance = UITabBarAppearance()
        appearance.configureWithOpaqueBackground()
        appearance.backgroundColor = UIColor(Color(hex: "16181F"))
        UITabBar.appearance().standardAppearance = appearance
        if #available(iOS 15.0, *) {
            UITabBar.appearance().scrollEdgeAppearance = appearance
        }
    }
    
    private var greeting: String {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<12: return "GOOD MORNING"
        case 12..<17: return "GOOD AFTERNOON"
        default: return "GOOD EVENING"
        }
    }
    
    private var partnerName: String {
        sessionManager.profile?.name ?? "Partner"
    }
    
    public var body: some View {
        ZStack {
            VStack(spacing: 0) {
                // TopAppBar (Verbatim Android RiderMainScreen.kt lines 272-324)
                HStack(alignment: .center) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("\(greeting), \(partnerName) 👋")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundColor(.white)
                        
                        HStack(spacing: 6) {
                            Circle()
                                .fill(sessionManager.isShiftOnline ? Color(hex: "10B981") : Color(hex: "64748B"))
                                .frame(width: 7, height: 7)
                            Text(isTogglingShift ? "Updating…" : (sessionManager.isShiftOnline ? "Online" : "Offline"))
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(sessionManager.isShiftOnline ? Color(hex: "10B981") : Color(hex: "94A3B8"))
                        }
                    }
                    
                    Spacer()
                    
                    // Material3 Switch / iOS Toggle (Verbatim Android RiderMainScreen.kt lines 299-322)
                    Toggle("", isOn: Binding<Bool>(
                        get: { sessionManager.isShiftOnline },
                        set: { newStatus in
                            guard !isTogglingShift else { return }
                            isTogglingShift = true
                            Task {
                                try? await sessionManager.toggleShift()
                                await MainActor.run {
                                    isTogglingShift = false
                                }
                            }
                        }
                    ))
                    .labelsHidden()
                    .toggleStyle(SwitchToggleStyle(tint: Color(hex: "10B981")))
                    .disabled(isTogglingShift)
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 12)
                .background(Color(hex: "16181F"))
                
                // Main App 5-Tab Navigation (Verbatim Android RiderMainScreen.kt lines 327-405)
                TabView(selection: $selectedTab) {
                    // Tab 0: Home (Icons.Default.Home)
                    Group {
                        if sessionManager.activeSession != nil {
                            ActiveDeliveryScreen()
                        } else {
                            RiderHomeDashboardView()
                        }
                    }
                    .tabItem {
                        Label("Home", systemImage: "house.fill")
                    }
                    .tag(0)
                    
                    // Tab 1: Orders (Icons.Default.ShoppingCart)
                    RiderOrdersHistoryView(onSelectActiveOrder: {
                        selectedTab = 0
                    })
                    .tabItem {
                        Label("Orders", systemImage: "cart.fill")
                    }
                    .tag(1)
                    
                    // Tab 2: Earnings (Icons.Default.Star)
                    EarningsDashboardView()
                    .tabItem {
                        Label("Earnings", systemImage: "indianrupeesign.circle.fill")
                    }
                    .tag(2)
                    
                    // Tab 3: Alerts (Icons.Default.Notifications with badge)
                    RiderNotificationCenterView()
                    .tabItem {
                        Label("Alerts", systemImage: "bell.fill")
                    }
                    .badge(2)
                    .tag(3)
                    
                    // Tab 4: Partner Profile (Icons.Default.Person)
                    RiderProfileView()
                    .tabItem {
                        Label("Profile", systemImage: "person.fill")
                    }
                    .tag(4)
                }
                .accentColor(Color(hex: "10B981"))
            }
            .background(Color(hex: "16181F").ignoresSafeArea(edges: .top))
            .background(Color(hex: "0D0F14").ignoresSafeArea(edges: .bottom))
            
            // Modal Dispatch Offer Card Overlay (when dispatch offer is available)
            if let offer = offerPipeline.activeOffer {
                ZStack {
                    Color.black.opacity(0.65)
                        .ignoresSafeArea()
                    
                    VStack {
                        Spacer()
                        RiderOfferCardView(offer: offer)
                            .padding(.bottom, 32)
                    }
                }
                .transition(.move(edge: .bottom).combined(with: .opacity))
                .zIndex(100)
            }
        }
    }
}
