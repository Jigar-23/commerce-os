import SwiftUI

public struct AuthScreen: View {
    @EnvironmentObject private var container: AppContainer
    @State private var phone: String = ""
    @State private var otpCode: String = ""
    @State private var challengeId: String = ""
    @State private var isOtpSent: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil
    @State private var resendCountdown: Int = 0
    @State private var showServerSettings: Bool = false
    @State private var timer: Timer? = nil

    public init() {}

    public var body: some View {
        ScrollView(showsIndicators: false) {
            VStack(spacing: 0) {
                Spacer(minLength: 50)

                // 1. App Icon (Verbatim Android CommerceColors.SpeedYellow cart badge)
                ZStack {
                    RoundedRectangle(cornerRadius: 20, style: .continuous)
                        .fill(Color(hex: "FFC20E"))
                        .frame(width: 72, height: 72)
                        .shadow(color: Color(hex: "FFC20E").opacity(0.35), radius: 8, x: 0, y: 4)

                    Image(systemName: "cart.fill")
                        .font(.system(size: 32, weight: .black))
                        .foregroundColor(Color(hex: "0F172A"))
                }

                Spacer().frame(height: 18)

                // 2. Title & Subtitle (Verbatim Android)
                Text("CommerceOS")
                    .font(.system(size: 28, weight: .black))
                    .foregroundColor(Color(hex: "0F172A"))

                Spacer().frame(height: 4)

                Text("India's 10-minute delivery app")
                    .font(.system(size: 14, weight: .bold))
                    .foregroundColor(Color(hex: "059669"))

                Spacer().frame(height: 32)

                // 3. Auth Card (Verbatim Android Card)
                VStack(alignment: .leading, spacing: 16) {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("Login or Sign up")
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(Color(hex: "0F172A"))

                        Text("Enter your 10-digit mobile number to continue")
                            .font(.system(size: 12))
                            .foregroundColor(Color(hex: "64748B"))
                    }

                    // Mobile Number Input Row
                    HStack(spacing: 10) {
                        // Country Code +91 Box
                        HStack(spacing: 4) {
                            Text("+91")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }
                        .padding(.horizontal, 14)
                        .frame(height: 52)
                        .background(Color(hex: "F8FAFC"))
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                        )

                        // Phone TextField
                        TextField("Phone number", text: Binding(
                            get: { phone },
                            set: { val in
                                let digits = val.filter { $0.isNumber }
                                phone = String(digits.prefix(10))
                                errorMessage = nil
                            }
                        ))
                        .keyboardType(.numberPad)
                        .disabled(isOtpSent || isLoading)
                        .font(.system(size: 15, weight: .medium))
                        .padding(.horizontal, 14)
                        .frame(height: 52)
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(phone.count == 10 ? Color(hex: "059669") : Color(hex: "CBD5E1"), lineWidth: 1.2)
                        )
                    }

                    // OTP Input Section (Verbatim Android when isOtpSent == true)
                    if isOtpSent {
                        // Success Confirmation Pill
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(Color(hex: "166534"))
                                .font(.system(size: 15))
                            Text("OTP sent to +91 \(phone)")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "166534"))
                            Spacer()
                        }
                        .padding(.horizontal, 12)
                        .padding(.vertical, 10)
                        .background(Color(hex: "DCFCE7"))
                        .cornerRadius(10)

                        // 6-digit OTP Field matching Android placeholder: "Enter OTP (e.g. 123456)"
                        HStack(spacing: 10) {
                            Image(systemName: "checkmark.circle.fill")
                                .font(.system(size: 16))
                                .foregroundColor(Color(hex: "059669"))
                                .padding(.leading, 12)

                            TextField("Enter OTP (e.g. 123456)", text: Binding(
                                get: { otpCode },
                                set: { val in
                                    let digits = val.filter { $0.isNumber }
                                    otpCode = String(digits.prefix(6))
                                    errorMessage = nil
                                }
                            ))
                            .keyboardType(.numberPad)
                            .font(.system(size: 15, weight: .bold))
                            .frame(height: 52)
                        }
                        .background(Color.white)
                        .cornerRadius(12)
                        .overlay(
                            RoundedRectangle(cornerRadius: 12)
                                .stroke(otpCode.count == 6 ? Color(hex: "059669") : Color(hex: "CBD5E1"), lineWidth: 1.2)
                        )
                    }

                    // Error Message (if any)
                    if let err = errorMessage {
                        HStack(spacing: 6) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(Color(hex: "DC2626"))
                                .font(.system(size: 12))
                            Text(err)
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "DC2626"))
                        }
                        .padding(.top, 2)
                    }

                    // Action Buttons (Verbatim Android)
                    if !isOtpSent {
                        Button(action: { sendOtp() }) {
                            HStack {
                                if isLoading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .frame(width: 20, height: 20)
                                } else {
                                    Text("Continue")
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(phone.count == 10 && !isLoading ? Color(hex: "059669") : Color(hex: "CBD5E1"))
                            .cornerRadius(12)
                        }
                        .disabled(phone.count != 10 || isLoading)
                    } else {
                        Button(action: { verifyOtp() }) {
                            HStack {
                                if isLoading {
                                    ProgressView()
                                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                                        .frame(width: 20, height: 20)
                                } else {
                                    Text("Verify & Sign In")
                                        .font(.system(size: 15, weight: .bold))
                                        .foregroundColor(.white)
                                }
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 52)
                            .background(!otpCode.isEmpty && !isLoading ? Color(hex: "059669") : Color(hex: "CBD5E1"))
                            .cornerRadius(12)
                        }
                        .disabled(otpCode.isEmpty || isLoading)

                        // Resend & Change Number Row (Verbatim Android)
                        HStack(spacing: 16) {
                            if resendCountdown > 0 {
                                Text("Resend code in \(resendCountdown)s")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color(hex: "94A3B8"))
                            } else {
                                Button(action: { resendOtp() }) {
                                    Text("Resend Code")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundColor(Color(hex: "059669"))
                                }
                            }

                            Spacer()

                            Button(action: { changeNumber() }) {
                                Text("Change Number")
                                    .font(.system(size: 12, weight: .medium))
                                    .foregroundColor(Color(hex: "64748B"))
                            }
                        }
                        .padding(.top, 4)
                    }
                }
                .padding(20)
                .background(Color.white)
                .cornerRadius(18)
                .overlay(
                    RoundedRectangle(cornerRadius: 18)
                        .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
                )
                .shadow(color: Color.black.opacity(0.04), radius: 10, x: 0, y: 4)
                .padding(.horizontal, 20)

                Spacer().frame(height: 28)

                // 4. Server Settings Button (Verbatim Android Server Settings row)
                Button(action: { showServerSettings = true }) {
                    HStack(spacing: 6) {
                        Image(systemName: "gearshape.fill")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: "94A3B8"))
                        Text("Server Settings (\(ServerEnvironmentConfig.shared.activePreset.rawValue))")
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color(hex: "94A3B8"))
                    }
                    .padding(.horizontal, 14)
                    .padding(.vertical, 8)
                }

                Spacer(minLength: 40)
            }
        }
        .background(Color(hex: "F4F5F7").ignoresSafeArea())
        .sheet(isPresented: $showServerSettings) {
            ServerSettingsSheet()
        }
        .onAppear {
            if phone.isEmpty, let savedPhone = UserDefaults.standard.string(forKey: "last_login_phone") {
                let digits = savedPhone.filter { $0.isNumber }
                phone = String(digits.suffix(10))
            }
        }
    }

    // MARK: - Actions

    private func sendOtp() {
        let digits = phone.filter { $0.isNumber }
        guard digits.count >= 10 else {
            errorMessage = "Please enter a valid 10-digit mobile number."
            return
        }
        let clean10 = String(digits.suffix(10))
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let formatted = "+91\(clean10)"
                let result = try await container.apiClient.sendOtp(phone: formatted)
                await MainActor.run {
                    self.challengeId = result.challengeId
                    self.isOtpSent = true
                    self.isLoading = false
                    self.startCountdown()
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func resendOtp() {
        guard resendCountdown == 0 else { return }
        sendOtp()
    }

    private func verifyOtp() {
        let otpDigits = otpCode.filter { $0.isNumber }
        guard !otpDigits.isEmpty else {
            errorMessage = "Please enter the 6-digit OTP code."
            return
        }
        let digits = phone.filter { $0.isNumber }
        let clean10 = String(digits.suffix(10))
        let formatted = "+91\(clean10)"
        
        isLoading = true
        errorMessage = nil

        Task {
            do {
                let auth = try await container.apiClient.verifyOtp(
                    challengeId: challengeId,
                    phone: formatted,
                    code: otpDigits,
                    name: nil
                )
                await MainActor.run {
                    self.isLoading = false
                    self.container.login(
                        customerId: auth.userId,
                        phone: auth.phone,
                        name: auth.name,
                        token: auth.accessToken
                    )
                }
            } catch {
                await MainActor.run {
                    self.isLoading = false
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }

    private func changeNumber() {
        timer?.invalidate()
        timer = nil
        isOtpSent = false
        otpCode = ""
        challengeId = ""
        errorMessage = nil
        resendCountdown = 0
    }

    private func startCountdown() {
        resendCountdown = 30
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            DispatchQueue.main.async {
                if self.resendCountdown > 0 {
                    self.resendCountdown -= 1
                } else {
                    self.timer?.invalidate()
                    self.timer = nil
                }
            }
        }
    }
}
