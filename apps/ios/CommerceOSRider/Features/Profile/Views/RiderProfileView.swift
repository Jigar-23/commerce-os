import SwiftUI

public struct RiderProfileView: View {
    @EnvironmentObject private var sessionManager: RiderSessionManager
    
    public init() {}
    
    private var riderName: String {
        sessionManager.profile?.name ?? "Rider Partner"
    }
    
    private var riderPhone: String {
        sessionManager.profile?.phone ?? ""
    }
    
    private var riderRating: Double {
        sessionManager.profile?.rating ?? 5.0
    }
    
    private var assignedHub: String {
        sessionManager.profile?.assignedHub ?? "Assigned Delivery Hub"
    }
    
    private var vehicleNumber: String {
        sessionManager.profile?.vehicleNumber ?? "EV Delivery"
    }
    
    public var body: some View {
        NavigationView {
            ScrollView {
                VStack(spacing: 16) {
                    // Profile Header Card
                    VStack(spacing: 12) {
                        HStack(spacing: 16) {
                            ZStack {
                                Circle()
                                    .fill(RiderTheme.Colors.safetyGreen.opacity(0.18))
                                    .frame(width: 64, height: 64)
                                Image(systemName: "person.fill")
                                    .font(.system(size: 32))
                                    .foregroundColor(RiderTheme.Colors.safetyGreen)
                            }
                            
                            VStack(alignment: .leading, spacing: 3) {
                                Text(riderName)
                                    .font(.system(size: 18, weight: .black))
                                    .foregroundColor(.white)
                                Text(riderPhone)
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(hex: "94A3B8"))
                                
                                HStack(spacing: 4) {
                                    Image(systemName: "star.fill")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: "F59E0B"))
                                    Text(String(format: "%.2f ★", riderRating))
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(.white)
                                    Text("(380 trips)")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: "94A3B8"))
                                }
                                .padding(.top, 2)
                            }
                            
                            Spacer()
                        }
                        
                        Divider()
                            .background(Color(hex: "262933"))
                        
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("ASSIGNED DARK STORE")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(Color(hex: "94A3B8"))
                                    .tracking(0.5)
                                Text(assignedHub)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color(hex: "38BDF8"))
                            }
                            Spacer()
                            VStack(alignment: .trailing, spacing: 2) {
                                Text("VEHICLE")
                                    .font(.system(size: 9, weight: .black))
                                    .foregroundColor(Color(hex: "94A3B8"))
                                    .tracking(0.5)
                                Text(vehicleNumber)
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(.white)
                            }
                        }
                    }
                    .padding(18)
                    .background(Color(hex: "16181F"))
                    .cornerRadius(20)
                    .overlay(
                        RoundedRectangle(cornerRadius: 20)
                            .stroke(Color(hex: "262933"), lineWidth: 1)
                    )
                    .padding(.horizontal, 16)
                    .padding(.top, 8)
                    
                    // Lifetime Metrics Grid
                    HStack(spacing: 12) {
                        MetricCard(title: "Acceptance", value: "98.4%", icon: "hand.thumbsup.fill", color: RiderTheme.Colors.safetyGreen)
                        MetricCard(title: "On-Time SLA", value: "99.1%", icon: "bolt.fill", color: Color(hex: "F59E0B"))
                        MetricCard(title: "All-Time Trips", value: "1,248", icon: "bicycle", color: Color(hex: "38BDF8"))
                    }
                    .padding(.horizontal, 16)
                    
                    // Safety & Equipment Compliance Section
                    VStack(alignment: .leading, spacing: 12) {
                        Text("EQUIPMENT & COMPLIANCE")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(Color(hex: "94A3B8"))
                            .tracking(0.5)
                        
                        ComplianceRow(icon: "checkmark.shield.fill", title: "Thermal Insulated Bag", status: "Verified Active")
                        ComplianceRow(icon: "snowflake", title: "Cold-Chain Ice Box (2-8°C)", status: "Equipped & Inspected")
                        ComplianceRow(icon: "shield.fill", title: "Helmet & Road Safety Kit", status: "Compliant")
                    }
                    .padding(16)
                    .background(Color(hex: "16181F"))
                    .cornerRadius(16)
                    .overlay(
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Color(hex: "262933"), lineWidth: 1)
                    )
                    .padding(.horizontal, 16)
                    
                    // Dispatch Support & SOS
                    VStack(spacing: 10) {
                        Button(action: {
                            if let url = URL(string: "tel://18002008800") {
                                UIApplication.shared.open(url)
                            }
                        }) {
                            HStack {
                                Image(systemName: "phone.bubble.left.fill")
                                    .foregroundColor(Color(hex: "38BDF8"))
                                Text("Call Dark Store Dispatcher")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(.white)
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            .padding(14)
                            .background(Color(hex: "22252E"))
                            .cornerRadius(12)
                        }
                        
                        Button(action: {
                            if let url = URL(string: "tel://112") {
                                UIApplication.shared.open(url)
                            }
                        }) {
                            HStack {
                                Image(systemName: "exclamationmark.octagon.fill")
                                    .foregroundColor(Color(hex: "EF4444"))
                                Text("Emergency SOS Assistance")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: "EF4444"))
                                Spacer()
                                Image(systemName: "chevron.right")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                            .padding(14)
                            .background(Color(hex: "7F1D1D").opacity(0.2))
                            .cornerRadius(12)
                            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "EF4444").opacity(0.4), lineWidth: 1))
                        }
                    }
                    .padding(.horizontal, 16)
                    
                    Spacer(minLength: 40)
                }
                .padding(.top, 8)
            }
            .background(Color(hex: "0D0F14").ignoresSafeArea())
            .navigationTitle("Partner Profile")
            .navigationBarTitleDisplayMode(.inline)
        }
        .navigationViewStyle(.stack)
    }
}

private struct MetricCard: View {
    let title: String
    let value: String
    let icon: String
    let color: Color
    
    var body: some View {
        VStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 16))
                .foregroundColor(color)
            Text(value)
                .font(.system(size: 15, weight: .black))
                .foregroundColor(.white)
            Text(title)
                .font(.system(size: 10, weight: .medium))
                .foregroundColor(Color(hex: "94A3B8"))
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        .background(Color(hex: "16181F"))
        .cornerRadius(14)
        .overlay(
            RoundedRectangle(cornerRadius: 14)
                .stroke(Color(hex: "262933"), lineWidth: 1)
        )
    }
}

private struct ComplianceRow: View {
    let icon: String
    let title: String
    let status: String
    
    var body: some View {
        HStack(spacing: 10) {
            Image(systemName: icon)
                .font(.system(size: 14))
                .foregroundColor(RiderTheme.Colors.safetyGreen)
            Text(title)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(.white)
            Spacer()
            Text(status)
                .font(.system(size: 10, weight: .bold))
                .foregroundColor(RiderTheme.Colors.safetyGreen)
        }
        .padding(.vertical, 4)
    }
}
