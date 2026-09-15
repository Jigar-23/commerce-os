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
        Group {
            if !container.apiClient.isAuthenticated {
                RiderAuthView(onLoginSuccess: {
                    Task {
                        await sessionManager.ensureOnlineShift()
                    }
                })
            } else {
                authenticatedRootView
            }
        }
    }

    private var authenticatedRootView: some View {
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
        .onAppear {
            Task {
                await sessionManager.ensureOnlineShift()
            }
        }
    }
}

public struct RiderAuthView: View {
    @ObservedObject private var apiClient = RiderAPIClient.shared
    public let onLoginSuccess: () -> Void

    @State private var phone: String = "9817916180"
    @State private var otp: String = "123456"
    @State private var challengeId: String? = nil
    @State private var isOtpSent: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil

    public init(onLoginSuccess: @escaping () -> Void) {
        self.onLoginSuccess = onLoginSuccess
    }

    public var body: some View {
        ZStack {
            Color(hex: "0D0F14").ignoresSafeArea()

            ScrollView {
                VStack(spacing: 24) {
                    Spacer(minLength: 40)

                    // Partner Emblem
                    ZStack {
                        RoundedRectangle(cornerRadius: 24)
                            .fill(Color(hex: "10B981"))
                            .frame(width: 80, height: 80)
                            .shadow(color: Color(hex: "10B981").opacity(0.3), radius: 12, x: 0, y: 6)

                        Image(systemName: "bicycle")
                            .font(.system(size: 40, weight: .bold))
                            .foregroundColor(.white)
                    }

                    VStack(spacing: 6) {
                        Text("CommerceOS Rider")
                            .font(.system(size: 28, weight: .black))
                            .foregroundColor(.white)

                        Text("Delivery Partner Portal")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(hex: "94A3B8"))
                    }

                    VStack(spacing: 16) {
                        if let error = errorMessage {
                            HStack {
                                Image(systemName: "exclamationmark.triangle.fill")
                                    .foregroundColor(Color(hex: "EF4444"))
                                Text(error)
                                    .font(.system(size: 13))
                                    .foregroundColor(Color(hex: "EF4444"))
                            }
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Color(hex: "EF4444").opacity(0.1))
                            .cornerRadius(10)
                        }

                        // Phone Input
                        VStack(alignment: .leading, spacing: 6) {
                            Text("Mobile Number")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Color(hex: "94A3B8"))

                            HStack {
                                Text("+91")
                                    .font(.system(size: 16, weight: .bold))
                                    .foregroundColor(.white)
                                    .padding(.leading, 12)

                                TextField("10-digit number", text: $phone)
                                    .keyboardType(.numberPad)
                                    .foregroundColor(.white)
                                    .font(.system(size: 16, weight: .medium))
                                    .padding(12)
                            }
                            .background(Color(hex: "16181F"))
                            .cornerRadius(12)
                            .overlay(
                                RoundedRectangle(cornerRadius: 12)
                                    .stroke(Color(hex: "262A36"), lineWidth: 1)
                            )
                        }

                        if isOtpSent {
                            // OTP Input
                            VStack(alignment: .leading, spacing: 6) {
                                Text("Enter OTP Code")
                                    .font(.system(size: 13, weight: .medium))
                                    .foregroundColor(Color(hex: "94A3B8"))

                                TextField("6-digit OTP", text: $otp)
                                    .keyboardType(.numberPad)
                                    .foregroundColor(.white)
                                    .font(.system(size: 18, weight: .bold))
                                    .padding(12)
                                    .background(Color(hex: "16181F"))
                                    .cornerRadius(12)
                                    .overlay(
                                        RoundedRectangle(cornerRadius: 12)
                                            .stroke(Color(hex: "10B981"), lineWidth: 1)
                                    )
                            }

                            Button(action: verifyOtp) {
                                HStack {
                                    if isLoading {
                                        ProgressView().tint(.white).padding(.trailing, 6)
                                    }
                                    Text("Verify & Start Shift")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color(hex: "10B981"))
                                .cornerRadius(12)
                            }
                            .disabled(isLoading || otp.isEmpty)
                        } else {
                            Button(action: requestOtp) {
                                HStack {
                                    if isLoading {
                                        ProgressView().tint(.white).padding(.trailing, 6)
                                    }
                                    Text("Send Verification OTP")
                                        .font(.system(size: 16, weight: .bold))
                                        .foregroundColor(.white)
                                }
                                .frame(maxWidth: .infinity)
                                .frame(height: 50)
                                .background(Color(hex: "10B981"))
                                .cornerRadius(12)
                            }
                            .disabled(isLoading || phone.count < 10)
                        }

                        // Fast 1-Tap Partner Login
                        Button(action: fastLoginJigar) {
                            HStack(spacing: 8) {
                                Image(systemName: "bolt.fill")
                                    .foregroundColor(Color(hex: "F59E0B"))
                                Text("Instant Partner Login (Jigar)")
                                    .font(.system(size: 14, weight: .bold))
                                    .foregroundColor(Color(hex: "E2E8F0"))
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 44)
                            .background(Color(hex: "1E293B"))
                            .cornerRadius(10)
                        }
                    }
                    .padding(20)
                    .background(Color(hex: "16181F"))
                    .cornerRadius(18)
                    .padding(.horizontal, 16)

                    Spacer(minLength: 40)
                }
            }
        }
    }

    private func requestOtp() {
        isLoading = true
        errorMessage = nil
        Task {
            do {
                let chId = try await apiClient.sendRiderOtp(phone: phone)
                await MainActor.run {
                    self.challengeId = chId
                    self.isOtpSent = true
                    self.isLoading = false
                    self.otp = "123456"
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }

    private func verifyOtp() {
        guard let chId = challengeId else { return }
        isLoading = true
        errorMessage = nil
        Task {
            do {
                _ = try await apiClient.verifyRiderOtp(
                    challengeId: chId,
                    phone: phone,
                    code: otp,
                    name: "Jigar (Partner)"
                )
                await MainActor.run {
                    self.isLoading = false
                    self.onLoginSuccess()
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }

    private func fastLoginJigar() {
        apiClient.setAuth(
            token: RiderAPIClient.defaultRiderToken,
            riderId: RiderAPIClient.defaultRiderId,
            phone: RiderAPIClient.defaultRiderPhone,
            name: "Jigar (Partner)"
        )
        onLoginSuccess()
    }
}
