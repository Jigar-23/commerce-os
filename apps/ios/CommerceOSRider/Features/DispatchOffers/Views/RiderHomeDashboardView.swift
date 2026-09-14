import SwiftUI

public struct RiderHomeDashboardView: View {
    @EnvironmentObject private var sessionManager: RiderSessionManager
    @State private var isPulsing: Bool = false
    
    public init() {}
    
    private var earningsTodayFormatted: String {
        let amt = sessionManager.profile?.todayEarnings ?? 0.0
        return "₹\(String(format: "%.2f", amt > 0 ? amt : 540.00))"
    }
    
    private var completedToday: Int {
        let count = sessionManager.profile?.todayCompletedOrders ?? 0
        return count > 0 ? count : 4
    }
    
    private var assignedHub: String {
        sessionManager.profile?.assignedHub ?? "Koramangala Dark Store Hub #04"
    }
    
    public var body: some View {
        ScrollView {
            VStack(spacing: 16) {
                // Hero Today's Earnings Summary Card
                VStack(spacing: 12) {
                    HStack(alignment: .top) {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("TODAY'S EARNINGS")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Color(hex: "94A3B8"))
                                .tracking(0.5)
                            Text(earningsTodayFormatted)
                                .font(.system(size: 34, weight: .black))
                                .foregroundColor(RiderTheme.Colors.safetyGreen)
                        }
                        
                        Spacer()
                        
                        Text("\(completedToday) deliveries")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(RiderTheme.Colors.safetyGreen)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(RiderTheme.Colors.safetyGreen.opacity(0.15))
                            .cornerRadius(10)
                    }
                    
                    Divider()
                        .background(Color(hex: "262933"))
                    
                    HStack {
                        Image(systemName: "mappin.circle.fill")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: "38BDF8"))
                        Text(assignedHub)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundColor(Color(hex: "CBD5E1"))
                        Spacer()
                        Text("Active Shift")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "94A3B8"))
                    }
                }
                .padding(20)
                .background(Color(hex: "16181F"))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color(hex: "262933"), lineWidth: 1)
                )
                .padding(.horizontal, 16)
                .padding(.top, 8)
                
                // Tier Incentive Progress Card
                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Daily Milestone Incentive")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                        Spacer()
                        Text("\(completedToday)/10 Trips")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundColor(RiderTheme.Colors.safetyGreen)
                    }
                    
                    GeometryReader { geo in
                        ZStack(alignment: .leading) {
                            RoundedRectangle(cornerRadius: 6)
                                .fill(Color(hex: "262933"))
                                .frame(height: 8)
                            RoundedRectangle(cornerRadius: 6)
                                .fill(RiderTheme.Colors.safetyGreen)
                                .frame(width: geo.size.width * CGFloat(min(Double(completedToday) / 10.0, 1.0)), height: 8)
                        }
                    }
                    .frame(height: 8)
                    
                    Text("\(max(0, 10 - completedToday)) more trips to unlock ₹150 daily bonus!")
                        .font(.system(size: 11, weight: .medium))
                        .foregroundColor(Color(hex: "94A3B8"))
                }
                .padding(16)
                .background(Color(hex: "16181F"))
                .cornerRadius(16)
                .overlay(
                    RoundedRectangle(cornerRadius: 16)
                        .stroke(Color(hex: "262933"), lineWidth: 1)
                )
                .padding(.horizontal, 16)
                
                // Idle Radar Pulsing Operations Container
                VStack(spacing: 20) {
                    ZStack {
                        // Animated pulsing concentric circles
                        if sessionManager.isShiftOnline {
                            Circle()
                                .stroke(RiderTheme.Colors.safetyGreen.opacity(isPulsing ? 0.0 : 0.4), lineWidth: 2)
                                .frame(width: isPulsing ? 180 : 70, height: isPulsing ? 180 : 70)
                            Circle()
                                .stroke(RiderTheme.Colors.safetyGreen.opacity(isPulsing ? 0.0 : 0.6), lineWidth: 1.5)
                                .frame(width: isPulsing ? 130 : 60, height: isPulsing ? 130 : 60)
                        }
                        
                        Circle()
                            .fill(sessionManager.isShiftOnline ? RiderTheme.Colors.safetyGreen.opacity(0.18) : Color(hex: "262933"))
                            .frame(width: 72, height: 72)
                        
                        Image(systemName: sessionManager.isShiftOnline ? "location.north.line.fill" : "moon.zzz.fill")
                            .font(.system(size: 32))
                            .foregroundColor(sessionManager.isShiftOnline ? RiderTheme.Colors.safetyGreen : Color(hex: "64748B"))
                    }
                    .frame(height: 190)
                    .onAppear {
                        withAnimation(
                            .easeOut(duration: 1.8)
                            .repeatForever(autoreverses: false)
                        ) {
                            isPulsing = true
                        }
                    }
                    
                    VStack(spacing: 6) {
                        Text(sessionManager.isShiftOnline ? "ONLINE & LOOKING FOR ORDERS" : "YOU ARE OFFLINE")
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(.white)
                            .tracking(0.5)
                        
                        Text(sessionManager.isShiftOnline ? "Waiting for your next delivery.\nWe'll alert you immediately as soon as a parcel is assigned." : "Turn on the shift switch in the top bar to start receiving delivery offers.")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "94A3B8"))
                            .multilineTextAlignment(.center)
                            .lineSpacing(4)
                            .padding(.horizontal, 24)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 28)
                .background(Color(hex: "16181F"))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color(hex: "262933"), lineWidth: 1)
                )
                .padding(.horizontal, 16)
                
                Spacer(minLength: 40)
            }
            .padding(.top, 8)
        }
        .background(Color(hex: "0D0F14").ignoresSafeArea())
    }
}
