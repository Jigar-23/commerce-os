import SwiftUI

public struct DeliveryCompletionCelebrationView: View {
    let orderId: String
    let customerName: String
    let payoutFormatted: String
    let isCod: Bool
    let codAmount: Double?
    let onDismiss: () -> Void
    
    public init(
        orderId: String,
        customerName: String,
        payoutFormatted: String = "₹65.00",
        isCod: Bool = false,
        codAmount: Double? = nil,
        onDismiss: @escaping () -> Void
    ) {
        self.orderId = orderId
        self.customerName = customerName
        self.payoutFormatted = payoutFormatted
        self.isCod = isCod
        self.codAmount = codAmount
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            Color.black.opacity(0.75)
                .ignoresSafeArea()
            
            VStack(spacing: 20) {
                // Celebration Icon Badge
                ZStack {
                    Circle()
                        .fill(RiderTheme.Colors.safetyGreen.opacity(0.18))
                        .frame(width: 80, height: 80)
                    Image(systemName: "checkmark.seal.fill")
                        .font(.system(size: 48))
                        .foregroundColor(RiderTheme.Colors.safetyGreen)
                }
                .padding(.top, 12)
                
                VStack(spacing: 6) {
                    Text("DELIVERY COMPLETED! 🎉")
                        .font(.system(size: 20, weight: .black))
                        .foregroundColor(.white)
                    
                    let shortId = orderId.count > 8 ? String(orderId.suffix(8)).uppercased() : orderId.uppercased()
                    Text("Order #\(shortId) handoff confirmed")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundColor(Color(hex: "94A3B8"))
                }
                
                // Order Bill Summary Card
                VStack(spacing: 12) {
                    HStack {
                        Text("Order Bill:")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundColor(Color(hex: "94A3B8"))
                        Spacer()
                        Text(payoutFormatted)
                            .font(.system(size: 22, weight: .black))
                            .foregroundColor(RiderTheme.Colors.safetyGreen)
                    }
                    
                    Divider()
                        .background(Color(hex: "2B2F3B"))
                    
                    HStack {
                        Text("Customer:")
                            .font(.system(size: 13))
                            .foregroundColor(Color(hex: "94A3B8"))
                        Spacer()
                        Text(customerName.isEmpty ? "Customer" : customerName)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                    }
                    
                    if isCod, let amount = codAmount, amount > 0 {
                        HStack {
                            Text("COD Cash Collected:")
                                .font(.system(size: 13))
                                .foregroundColor(Color(hex: "94A3B8"))
                            Spacer()
                            Text("₹\(Int(amount))")
                                .font(.system(size: 13, weight: .black))
                                .foregroundColor(RiderTheme.Colors.safetyYellow)
                        }
                    }
                }
                .padding(16)
                .background(Color(hex: "22252E"))
                .cornerRadius(14)
                .overlay(
                    RoundedRectangle(cornerRadius: 14)
                        .stroke(Color(hex: "2B2F3B"), lineWidth: 1)
                )
                
                // Done CTA Button
                Button(action: onDismiss) {
                    Text("Ready for next offer")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundColor(.black)
                        .frame(maxWidth: .infinity)
                        .frame(height: 50)
                        .background(RiderTheme.Colors.safetyGreen)
                        .cornerRadius(12)
                }
                .padding(.bottom, 8)
            }
            .padding(24)
            .background(Color(hex: "16181F"))
            .cornerRadius(24)
            .overlay(
                RoundedRectangle(cornerRadius: 24)
                    .stroke(Color(hex: "2B2F3B"), lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.5), radius: 20, x: 0, y: 10)
            .padding(.horizontal, 24)
        }
    }
}
