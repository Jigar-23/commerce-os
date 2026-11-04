import SwiftUI

/**
 * 1:1 Parity with Android PickupFlowView.kt (131 lines).
 * Store pickup inspection checklist, item count & bill total confirmation,
 * Hub direct dialer, and pickup verification actions.
 */
public struct PickupFlowView: View {
    public let session: ActiveDeliverySessionDto
    public let onConfirmPickup: () -> Void
    public let onReportIssue: () -> Void

    public init(
        session: ActiveDeliverySessionDto,
        onConfirmPickup: @escaping () -> Void,
        onReportIssue: @escaping () -> Void
    ) {
        self.session = session
        self.onConfirmPickup = onConfirmPickup
        self.onReportIssue = onReportIssue
    }

    private var itemsSummary: String {
        if let items = session.items, !items.isEmpty {
            return items.joined(separator: ", ")
        }
        return "Order Items Verified"
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 14) {
            // Header
            HStack(spacing: 10) {
                ZStack {
                    RoundedRectangle(cornerRadius: 10)
                        .fill(Color(hex: "3B82F6").opacity(0.12))
                        .frame(width: 38, height: 38)
                    Image(systemName: "mappin.and.ellipse")
                        .font(.system(size: 18))
                        .foregroundColor(Color(hex: "3B82F6"))
                }

                VStack(alignment: .leading, spacing: 2) {
                    Text("STORE PICKUP")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(Color(hex: "3B82F6"))
                        .tracking(0.8)

                    Text("Order is ready for pickup")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundColor(.white)
                }
            }

            Text("\(session.merchantName) • \(session.merchantAddress)")
                .font(.system(size: 13))
                .foregroundColor(Color(hex: "94A3B8"))

            Divider()
                .background(Color(hex: "262A36"))

            // Verification Checklist
            Text("Check package & order details")
                .font(.system(size: 13, weight: .bold))
                .foregroundColor(.white)

            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "10B981"))
                Text("Order items packed and sealed in tamper-proof bag")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "94A3B8"))
            }

            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "10B981"))
                Text("Contents: \(itemsSummary)")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundColor(Color(hex: "60A5FA"))
            }

            HStack(alignment: .top, spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 15))
                    .foregroundColor(Color(hex: "10B981"))
                let cartVal = session.codAmountToCollect ?? 0.0
                let billText = cartVal > 0 ? " • Bill Total ₹\(Int(cartVal))" : ""
                let shortId = session.orderId.count > 14 ? String(session.orderId.prefix(12)) + "…" : session.orderId
                Text("Order #\(shortId)\(billText)")
                    .font(.system(size: 12))
                    .foregroundColor(Color(hex: "94A3B8"))
            }

            // Action Buttons
            HStack(spacing: 8) {
                // Call Hub
                Button(action: {
                    if let url = URL(string: "tel://1800123456") {
                        UIApplication.shared.open(url)
                    }
                }) {
                    HStack(spacing: 4) {
                        Image(systemName: "phone.fill")
                            .font(.system(size: 12))
                        Text("Call Hub")
                            .font(.system(size: 12, weight: .bold))
                    }
                    .foregroundColor(Color(hex: "60A5FA"))
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background(Color(hex: "16181F"))
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "262A36"), lineWidth: 1))
                    .cornerRadius(12)
                }
                .buttonStyle(PlainButtonStyle())

                // Report Issue
                Button(action: onReportIssue) {
                    Text("Report issue")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "EF4444"))
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color(hex: "16181F"))
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Color(hex: "EF4444").opacity(0.4), lineWidth: 1))
                        .cornerRadius(12)
                }
                .buttonStyle(PlainButtonStyle())

                // Confirm Pickup
                Button(action: onConfirmPickup) {
                    Text("Confirm pickup")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                        .frame(maxWidth: .infinity)
                        .frame(height: 48)
                        .background(Color(hex: "10B981"))
                        .cornerRadius(12)
                }
                .buttonStyle(PlainButtonStyle())
            }
            .padding(.top, 4)
        }
        .padding(18)
        .background(Color(hex: "16181F"))
        .overlay(RoundedRectangle(cornerRadius: 18).stroke(Color(hex: "262A36"), lineWidth: 1))
        .cornerRadius(18)
    }
}
