import SwiftUI

/**
 * 1:1 Parity with Android RiderAuthScreen.kt (360 lines).
 * Rider partner authentication portal with mobile OTP verification,
 * challenge tracking, optional name/vehicle profile registration,
 * and secure credential storage.
 */
public struct RiderAuthScreen: View {
    public let onLoginSuccess: (RiderProfileDto) -> Void

    @EnvironmentObject private var sessionManager: RiderSessionManager
    @State private var phone: String = ""
    @State private var otp: String = ""
    @State private var riderName: String = ""
    @State private var vehicleNumber: String = ""
    @State private var challengeId: String? = nil

    @State private var isOtpSent: Bool = false
    @State private var isLoading: Bool = false
    @State private var errorMessage: String? = nil

    public init(onLoginSuccess: @escaping (RiderProfileDto) -> Void = { _ in }) {
        self.onLoginSuccess = onLoginSuccess
    }

    public var body: some View {
        ScrollView {
            VStack(spacing: 20) {
                Spacer().frame(height: 30)

                // Rider Icon Badge
                ZStack {
                    RoundedRectangle(cornerRadius: 24)
                        .fill(Color(hex: "10B981"))
                        .frame(width: 76, height: 76)
                        .shadow(color: Color(hex: "10B981").opacity(0.3), radius: 8, y: 4)

                    Image(systemName: "paperplane.fill")
                        .font(.system(size: 34))
                        .foregroundColor(.white)
                }

                VStack(spacing: 4) {
                    Text("CommerceOS Rider")
                        .font(.system(size: 26, weight: .bold))
                        .foregroundColor(.white)

                    Text("Delivery Partner Portal")
                        .font(.system(size: 14, weight: .medium))
                        .foregroundColor(Color(hex: "94A3B8"))
                }

                // Main Auth Card
                VStack(alignment: .leading, spacing: 18) {
                    if !isOtpSent {
                        VStack(alignment: .leading, spacing: 4) {
                            Text("Partner Login")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)

                            Text("Enter your 10-digit registered mobile number")
                                .font(.system(size: 13))
                                .foregroundColor(Color(hex: "94A3B8"))
                        }

                        // Phone Input
                        HStack(spacing: 8) {
                            Text("+91")
                                .font(.system(size: 15, weight: .bold))
                                .foregroundColor(Color(hex: "10B981"))
                                .padding(.leading, 12)

                            TextField("9876543210", text: $phone)
                                .keyboardType(.phonePad)
                                .font(.system(size: 15))
                                .foregroundColor(.white)
                        }
                        .frame(height: 50)
                        .background(Color(hex: "101216"))
                        .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "262A36"), lineWidth: 1))
                        .cornerRadius(14)

                        if let err = errorMessage {
                            Text(err)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "EF4444"))
                        }

                        // Send OTP Button
                        Button(action: sendOtp) {
                            HStack {
                                if isLoading {
                                    ProgressView().tint(.white).padding(.trailing, 6)
                                }
                                Text("Send OTP")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(phone.count == 10 ? Color(hex: "10B981") : Color(hex: "262A36"))
                            .cornerRadius(14)
                        }
                        .disabled(isLoading || phone.count != 10)

                    } else {
                        // OTP Verification Step
                        HStack {
                            Text("Verify OTP")
                                .font(.system(size: 20, weight: .bold))
                                .foregroundColor(.white)

                            Spacer()

                            Button(action: {
                                isOtpSent = false
                                otp = ""
                                errorMessage = nil
                            }) {
                                Text("Edit Phone")
                                    .font(.system(size: 12, weight: .bold))
                                    .foregroundColor(Color(hex: "10B981"))
                            }
                        }

                        Text("Enter 6-digit code sent to +91 \(phone)\n💡 Direct dev code 123456 verifies immediately")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: "94A3B8"))

                        // OTP Input
                        TextField("••••••", text: $otp)
                            .keyboardType(.numberPad)
                            .font(.system(size: 18, weight: .bold))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .frame(height: 50)
                            .background(Color(hex: "101216"))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "10B981"), lineWidth: 1))
                            .cornerRadius(14)

                        // Name Input
                        TextField("Your Name (Optional)", text: $riderName)
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .frame(height: 48)
                            .background(Color(hex: "101216"))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "262A36"), lineWidth: 1))
                            .cornerRadius(14)

                        // Vehicle Input
                        TextField("Vehicle Number (e.g. HR-26-AB-1234)", text: $vehicleNumber)
                            .font(.system(size: 14))
                            .foregroundColor(.white)
                            .padding(.horizontal, 14)
                            .frame(height: 48)
                            .background(Color(hex: "101216"))
                            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Color(hex: "262A36"), lineWidth: 1))
                            .cornerRadius(14)

                        if let err = errorMessage {
                            Text(err)
                                .font(.system(size: 12, weight: .medium))
                                .foregroundColor(Color(hex: "EF4444"))
                        }

                        // Verify Button
                        Button(action: verifyOtp) {
                            HStack {
                                if isLoading {
                                    ProgressView().tint(.white).padding(.trailing, 6)
                                }
                                Text("Verify & Enter Shift")
                                    .font(.system(size: 15, weight: .bold))
                                    .foregroundColor(.white)
                            }
                            .frame(maxWidth: .infinity)
                            .frame(height: 50)
                            .background(Color(hex: "10B981"))
                            .cornerRadius(14)
                        }
                        .disabled(isLoading || otp.trimmingCharacters(in: .whitespacesAndNewlines).count != 6)
                    }
                }
                .padding(24)
                .background(Color(hex: "16181F"))
                .overlay(RoundedRectangle(cornerRadius: 20).stroke(Color(hex: "262A36"), lineWidth: 1))
                .cornerRadius(20)
                .padding(.horizontal, 16)

                Spacer().frame(height: 40)
            }
        }
        .background(Color(hex: "0D0E12").ignoresSafeArea())
    }

    private func sendOtp() {
        guard phone.count == 10 else {
            errorMessage = "Please enter a valid 10-digit mobile number"
            return
        }
        errorMessage = nil
        isLoading = true

        Task {
            do {
                let chId = try await RiderAPIClient.shared.sendRiderOtp(phone: phone)
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
        let cleanOtp = otp.trimmingCharacters(in: .whitespacesAndNewlines)
        guard cleanOtp.count == 6 else {
            errorMessage = "Please enter the 6-digit OTP"
            return
        }
        guard let chId = challengeId else {
            errorMessage = "OTP session expired. Please request a new OTP."
            return
        }

        errorMessage = nil
        isLoading = true

        Task {
            do {
                let tokenRes = try await RiderAPIClient.shared.verifyRiderOtp(
                    challengeId: chId,
                    phone: phone,
                    code: cleanOtp,
                    name: riderName.isEmpty ? "Rider Partner" : riderName
                )

                _ = RiderSecureCredentialStore.shared.save(alias: "rider_jwt_token", value: tokenRes.token)

                let profile = RiderProfileDto(
                    riderId: tokenRes.riderId.isEmpty ? "rider_\(phone)" : tokenRes.riderId,
                    name: riderName.isEmpty ? "Rider Partner" : riderName,
                    phone: phone,
                    vehicleType: vehicleNumber.isEmpty ? "EV 2-Wheeler" : vehicleNumber,
                    todayEarnings: 0.0,
                    todayCompletedOrders: 0,
                    rating: 5.0
                )

                await MainActor.run {
                    self.isLoading = false
                    sessionManager.profile = profile
                    onLoginSuccess(profile)
                }
            } catch {
                await MainActor.run {
                    self.errorMessage = error.localizedDescription
                    self.isLoading = false
                }
            }
        }
    }
}
