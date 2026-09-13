import SwiftUI

public struct RiderRootView: View {
    @EnvironmentObject private var container: RiderContainer
    @EnvironmentObject private var sessionManager: RiderSessionManager
    @EnvironmentObject private var offerPipeline: RiderOfferEventPipeline
    @State private var selectedTab: Int = 0
    
    public init() {}
    
    public var body: some View {
        ZStack {
            TabView(selection: $selectedTab) {
                // Tab 1: Live Navigation & Active Delivery
                ActiveDeliveryScreen()
                    .tabItem {
                        Label("Duty", systemImage: "bicycle")
                    }
                    .tag(0)
                
                // Tab 2: Earnings & Trip Log
                EarningsDashboardView()
                    .tabItem {
                        Label("Earnings", systemImage: "dollarsign.circle.fill")
                    }
                    .tag(1)
            }
            .accentColor(RiderTheme.Colors.safetyGreen)
            
            // Top Persistent Shift Status Bar
            VStack {
                HStack {
                    HStack(spacing: 8) {
                        Circle()
                            .fill(sessionManager.isShiftOnline ? RiderTheme.Colors.safetyGreen : Color(hex: "64748B"))
                            .frame(width: 8, height: 8)
                        Text(sessionManager.isShiftOnline ? "ONLINE • RECEIVING ORDERS" : "OFFLINE • SHIFT PAUSED")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(sessionManager.isShiftOnline ? RiderTheme.Colors.safetyGreen : Color(hex: "94A3B8"))
                    }
                    
                    Spacer()
                    
                    Button(action: {
                        Task {
                            try? await sessionManager.toggleShift()
                        }
                    }) {
                        Text(sessionManager.isShiftOnline ? "GO OFFLINE" : "GO ONLINE")
                            .font(.system(size: 11, weight: .black))
                            .padding(.horizontal, 14)
                            .padding(.vertical, 6)
                            .background(sessionManager.isShiftOnline ? Color(hex: "E11D48") : RiderTheme.Colors.safetyGreen)
                            .foregroundColor(.white)
                            .cornerRadius(16)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(RiderTheme.Colors.hudBackground)
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(RiderTheme.Colors.hudBorder, lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.4), radius: 8, x: 0, y: 3)
                .padding(.horizontal, 16)
                .padding(.top, 48)
                
                Spacer()
            }
            
            // Floating Modal Dispatch Offer Card Overlay
            if let offer = offerPipeline.activeOffer {
                ZStack {
                    Color.black.opacity(0.6)
                        .edgesIgnoringSafeArea(.all)
                    
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
