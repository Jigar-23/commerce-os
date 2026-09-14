import SwiftUI

public struct CustomerHandoffView: View {
    let session: ActiveDeliverySessionDto
    let onVerifyOtp: (String, Double?) async -> Bool
    let onComplete: () -> Void
    
    @State private var enteredOtp: String = ""
    @State private var enteredCodAmount: String = ""
    @State private var codReconciled: Bool = false
    @State private var isSubmitting: Bool = false
    @State private var errorMessage: String? = nil
    @State private var resendCooldown: Int = 0
    @State private var timer: Timer? = nil
    
    public init(
        session: ActiveDeliverySessionDto,
        onVerifyOtp: @escaping (String, Double?) async -> Bool,
        onComplete: @escaping () -> Void
    ) {
        self.session = session
        self.onVerifyOtp = onVerifyOtp
        self.onComplete = onComplete
    }
    
    private var expectedCodAmount: Int {
        Int(session.codAmountToCollect ?? 0)
    }
    
    public var body: some View {
        VStack(spacing: 16) {
            // Customer Header Info Card
            HStack {
                HStack(spacing: 12) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 12)
                            .fill(Color(hex: "F59E0B").opacity(0.15))
                            .frame(width: 44, height: 44)
                        Image(systemName: "house.fill")
                            .font(.system(size: 20))
                            .foregroundColor(Color(hex: "F59E0B"))
                    }
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text("DOORSTEP HANDOFF")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(Color(hex: "F59E0B"))
                            .tracking(0.5)
                        Text(session.customerName.isEmpty ? "Customer" : session.customerName)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                    }
                }
                
                Spacer()
                
                if !session.customerPhone.isEmpty {
                    Button(action: {
                        if let url = URL(string: "tel://\(session.customerPhone.replacingOccurrences(of: " ", with: ""))") {
                            UIApplication.shared.open(url)
                        }
                    }) {
                        HStack(spacing: 4) {
                            Image(systemName: "phone.fill")
                                .font(.system(size: 12))
                            Text("Call")
                                .font(.system(size: 12, weight: .bold))
                        }
                        .foregroundColor(Color(hex: "38BDF8"))
                        .padding(.horizontal, 10)
                        .padding(.vertical, 6)
                        .background(Color(hex: "22252E"))
                        .cornerRadius(10)
                        .overlay(
                            RoundedRectangle(cornerRadius: 10)
                                .stroke(Color(hex: "2B2F3B"), lineWidth: 1)
                        )
                    }
                }
            }
            .padding(14)
            .background(Color(hex: "16181F"))
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color(hex: "262933"), lineWidth: 1)
            )
            
            // Customer Address
            VStack(alignment: .leading, spacing: 4) {
                Text("Delivery Address")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Color(hex: "94A3B8"))
                Text(session.customerAddress)
                    .font(.system(size: 13, weight: .medium))
                    .foregroundColor(.white)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(12)
            .background(Color(hex: "22252E"))
            .cornerRadius(12)
            
            // STEP 1: Cash Collection (If COD order)
            if session.isCod || expectedCodAmount > 0 {
                if !codReconciled {
                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            VStack(alignment: .leading, spacing: 2) {
                                Text("STEP 1: CASH TO COLLECT")
                                    .font(.system(size: 10, weight: .black))
                                    .foregroundColor(Color(hex: "FBBF24"))
                                    .tracking(0.5)
                                Text("Cash On Delivery (COD)")
                                    .font(.system(size: 12))
                                    .foregroundColor(Color(hex: "FDE68A"))
                            }
                            Spacer()
                            Text("₹\(expectedCodAmount)")
                                .font(.system(size: 26, weight: .black))
                                .foregroundColor(Color(hex: "FBBF24"))
                        }
                        
                        // Preset Chips
                        HStack(spacing: 8) {
                            Button(action: { enteredCodAmount = "\(expectedCodAmount)" }) {
                                Text("Exact ₹\(expectedCodAmount)")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundColor(enteredCodAmount == "\(expectedCodAmount)" ? .black : .white)
                                    .padding(.vertical, 8)
                                    .frame(maxWidth: .infinity)
                                    .background(enteredCodAmount == "\(expectedCodAmount)" ? Color(hex: "FBBF24") : Color(hex: "262933"))
                                    .cornerRadius(8)
                            }
                            
                            ForEach([100, 200, 500], id: \.self) { note in
                                if note >= expectedCodAmount {
                                    Button(action: { enteredCodAmount = "\(note)" }) {
                                        Text("₹\(note)")
                                            .font(.system(size: 11, weight: .bold))
                                            .foregroundColor(enteredCodAmount == "\(note)" ? .black : .white)
                                            .padding(.vertical, 8)
                                            .frame(maxWidth: .infinity)
                                            .background(enteredCodAmount == "\(note)" ? Color(hex: "FBBF24") : Color(hex: "262933"))
                                            .cornerRadius(8)
                                    }
                                }
                            }
                        }
                        
                        // Amount input
                        HStack {
                            Text("₹")
                                .font(.system(size: 16, weight: .black))
                                .foregroundColor(Color(hex: "94A3B8"))
                            TextField("Amount received", text: $enteredCodAmount)
                                .font(.system(size: 16, weight: .bold))
                                .foregroundColor(.white)
                                .keyboardType(.numberPad)
                        }
                        .padding(10)
                        .background(Color(hex: "16181F"))
                        .cornerRadius(8)
                        .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: "475569"), lineWidth: 1))
                        
                        Button(action: {
                            withAnimation {
                                codReconciled = true
                            }
                        }) {
                            Text("CONFIRM CASH COLLECTED")
                                .font(.system(size: 13, weight: .black))
                                .foregroundColor(.black)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 12)
                                .background(!enteredCodAmount.isEmpty ? Color(hex: "FBBF24") : Color.gray)
                                .cornerRadius(10)
                        }
                        .disabled(enteredCodAmount.isEmpty)
                    }
                    .padding(14)
                    .background(Color(hex: "271B0B"))
                    .cornerRadius(14)
                    .overlay(
                        RoundedRectangle(cornerRadius: 14)
                            .stroke(Color(hex: "F59E0B").opacity(0.6), lineWidth: 1)
                    )
                } else {
                    // COD Reconciled State
                    HStack(spacing: 12) {
                        Image(systemName: "checkmark.circle.fill")
                            .font(.system(size: 24))
                            .foregroundColor(Color(hex: "34D399"))
                        VStack(alignment: .leading, spacing: 2) {
                            Text("CASH COLLECTED & RECONCILED")
                                .font(.system(size: 10, weight: .black))
                                .foregroundColor(Color(hex: "34D399"))
                            Text("₹\(enteredCodAmount.isEmpty ? "\(expectedCodAmount)" : enteredCodAmount) received safely")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundColor(.white)
                        }
                        Spacer()
                    }
                    .padding(14)
                    .background(Color(hex: "064E3B").opacity(0.4))
                    .cornerRadius(12)
                    .overlay(
                        RoundedRectangle(cornerRadius: 12)
                            .stroke(Color(hex: "10B981"), lineWidth: 1)
                    )
                }
            }
            
            // STEP 2: Customer Delivery PIN Verification
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "lock.shield.fill")
                            .foregroundColor(Color(hex: "38BDF8"))
                        Text(session.isCod ? "STEP 2: DELIVERY PIN" : "CUSTOMER DELIVERY PIN")
                            .font(.system(size: 11, weight: .black))
                            .foregroundColor(Color(hex: "38BDF8"))
                            .tracking(0.5)
                    }
                    
                    Spacer()
                    
                    if resendCooldown > 0 {
                        Text("Resend in \(resendCooldown)s")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundColor(Color(hex: "94A3B8"))
                    } else {
                        Button(action: {
                            resendCooldown = 30
                            startCooldownTimer()
                        }) {
                            Text("Resend PIN")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(Color(hex: "38BDF8"))
                        }
                    }
                }
                
                Text("Ask customer for the 4-digit Delivery PIN displayed on their live tracking screen.")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "94A3B8"))
                
                // 4 Discrete PIN Boxes
                HStack(spacing: 12) {
                    ForEach(0..<4, id: \.self) { index in
                        ZStack {
                            RoundedRectangle(cornerRadius: 10)
                                .fill(Color(hex: "16181F"))
                                .frame(height: 52)
                                .overlay(
                                    RoundedRectangle(cornerRadius: 10)
                                        .stroke(pinDigit(at: index).isEmpty ? Color(hex: "475569") : Color(hex: "38BDF8"), lineWidth: 1.5)
                                )
                            Text(pinDigit(at: index))
                                .font(.system(size: 22, weight: .black, design: .monospaced))
                                .foregroundColor(.white)
                        }
                    }
                }
                
                // Hidden or Direct Text Field for easy keyboard input
                TextField("Enter 4-digit PIN", text: $enteredOtp)
                    .keyboardType(.numberPad)
                    .font(.system(size: 16, weight: .bold))
                    .multilineTextAlignment(.center)
                    .padding(10)
                    .background(Color(hex: "16181F"))
                    .cornerRadius(8)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Color(hex: "334155"), lineWidth: 1))
                    .onChange(of: enteredOtp) { val in
                        if val.count > 4 {
                            enteredOtp = String(val.prefix(4))
                        }
                    }
                
                if let err = errorMessage {
                    Text(err)
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(hex: "FCA5A5"))
                        .padding(8)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color(hex: "7F1D1D").opacity(0.4))
                        .cornerRadius(6)
                }
                
                // Complete Delivery CTA
                Button(action: verifyAndComplete) {
                    if isSubmitting {
                        ProgressView().progressViewStyle(CircularProgressViewStyle(tint: .black))
                    } else {
                        Text("VERIFY PIN & COMPLETE DELIVERY")
                            .font(.system(size: 13, weight: .black))
                            .foregroundColor(.black)
                            .tracking(0.5)
                    }
                }
                .frame(maxWidth: .infinity)
                .frame(height: 50)
                .background(canSubmit ? RiderTheme.Colors.safetyGreen : Color.gray)
                .cornerRadius(12)
                .disabled(!canSubmit || isSubmitting)
            }
            .padding(16)
            .background(Color(hex: "22252E"))
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(Color(hex: "2B2F3B"), lineWidth: 1)
            )
        }
    }
    
    private var canSubmit: Bool {
        let pinValid = enteredOtp.count >= 4
        let codValid = !session.isCod || expectedCodAmount == 0 || codReconciled
        return pinValid && codValid
    }
    
    private func pinDigit(at index: Int) -> String {
        if index < enteredOtp.count {
            let start = enteredOtp.index(enteredOtp.startIndex, offsetBy: index)
            return String(enteredOtp[start])
        }
        return ""
    }
    
    private func startCooldownTimer() {
        timer?.invalidate()
        timer = Timer.scheduledTimer(withTimeInterval: 1.0, repeats: true) { _ in
            if resendCooldown > 0 {
                resendCooldown -= 1
            } else {
                timer?.invalidate()
            }
        }
    }
    
    private func verifyAndComplete() {
        isSubmitting = true
        errorMessage = nil
        let cashDouble = Double(enteredCodAmount) ?? Double(expectedCodAmount)
        Task {
            let success = await onVerifyOtp(enteredOtp, cashDouble)
            await MainActor.run {
                isSubmitting = false
                if success {
                    onComplete()
                } else {
                    errorMessage = "Invalid delivery PIN. Please ask customer to re-check the 4-digit PIN."
                }
            }
        }
    }
}
