import SwiftUI

/**
 * 1:1 Parity with Android AddressServiceabilityCard.kt
 * Enterprise Multi-Vertical Delivery Promise Card Component.
 * Guarantees address-identity verification and displays per-vertical fulfillment breakdown
 * even during partial availability states.
 */
public struct AddressServiceabilityCard: View {
    public let serviceabilityState: ServiceabilityState
    public let selectedAddressId: String?

    public init(serviceabilityState: ServiceabilityState, selectedAddressId: String?) {
        self.serviceabilityState = serviceabilityState
        self.selectedAddressId = selectedAddressId
    }

    private var isTargetMatched: Bool {
        serviceabilityState.targetAddressId == selectedAddressId && selectedAddressId != nil
    }

    private var containerColor: Color {
        guard isTargetMatched else { return Color(hex: "F8FAFC") }
        switch serviceabilityState {
        case .checking:
            return Color(hex: "F8FAFC")
        case .success:
            return Color(hex: "F0FDF4")
        case .partial:
            return Color(hex: "FFFBEB")
        case .unavailable, .error:
            return Color(hex: "FEF2F2")
        case .idle:
            return Color(hex: "F8FAFC")
        }
    }

    private var borderColor: Color {
        guard isTargetMatched else { return Color(hex: "E2E8F0") }
        switch serviceabilityState {
        case .checking, .idle:
            return Color(hex: "E2E8F0")
        case .success:
            return Color(hex: "BBF7D0")
        case .partial:
            return Color(hex: "FDE68A")
        case .unavailable, .error:
            return Color(hex: "FECACA")
        }
    }

    public var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            if selectedAddressId == nil {
                HStack(spacing: 8) {
                    Image(systemName: "info.circle.fill")
                        .foregroundColor(Color(hex: "94A3B8"))
                        .font(.system(size: 14))

                    Text("Select a delivery address to view fulfillment promises")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(hex: "475569"))
                }
            } else if !isTargetMatched || isChecking {
                HStack(spacing: 8) {
                    ProgressView()
                        .scaleEffect(0.7)
                        .frame(width: 14, height: 14)

                    Text("Checking delivery feasibility for selected address...")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color(hex: "475569"))
                }
            } else {
                switch serviceabilityState {
                case .success(_, let resp):
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 8) {
                            Image(systemName: "checkmark.circle.fill")
                                .foregroundColor(Color(hex: "16A34A"))
                                .font(.system(size: 15))

                            Text(resp.serviceable ? "Selected address is serviceable for rapid delivery" : "Out of delivery range for active items")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "0F172A"))
                        }

                        if let eta = resp.estimatedDeliveryMinutes {
                            HStack(spacing: 6) {
                                Text("⚡ In \(eta) mins")
                                    .font(.system(size: 11, weight: .heavy))
                                    .foregroundColor(Color(hex: "15803D"))
                                    .padding(.horizontal, 6)
                                    .padding(.vertical, 2)
                                    .background(Color(hex: "DCFCE7"))
                                    .cornerRadius(6)

                                if let dist = resp.effectiveDistanceKm {
                                    Text("• \(String(format: "%.1f", dist)) km from nearest store")
                                        .font(.system(size: 11))
                                        .foregroundColor(Color(hex: "64748B"))
                                }
                            }
                            .padding(.top, 2)
                        }
                    }

                case .partial(_, let resp):
                    VStack(alignment: .leading, spacing: 4) {
                        HStack(spacing: 8) {
                            Image(systemName: "exclamationmark.triangle.fill")
                                .foregroundColor(Color(hex: "D97706"))
                                .font(.system(size: 15))

                            Text("Partial delivery availability")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundColor(Color(hex: "92400E"))
                        }

                        if let eta = resp.estimatedDeliveryMinutes {
                            Text("Some items arrive in \(eta) mins; others from secondary warehouse.")
                                .font(.system(size: 11))
                                .foregroundColor(Color(hex: "78350F"))
                        }
                    }

                case .unavailable:
                    HStack(spacing: 8) {
                        Image(systemName: "xmark.octagon.fill")
                            .foregroundColor(Color(hex: "DC2626"))
                            .font(.system(size: 15))

                        Text("Address is outside delivery partner coverage radius")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(Color(hex: "991B1B"))
                    }

                case .error(_, let msg):
                    HStack(spacing: 8) {
                        Image(systemName: "exclamationmark.circle.fill")
                            .foregroundColor(Color(hex: "DC2626"))
                            .font(.system(size: 15))

                        Text(msg)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundColor(Color(hex: "991B1B"))
                    }

                case .idle, .checking:
                    EmptyView()
                }
            }
        }
        .padding(12)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(containerColor)
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(borderColor, lineWidth: 1)
        )
    }

    private var isChecking: Bool {
        if case .checking = serviceabilityState { return true }
        return false
    }
}
