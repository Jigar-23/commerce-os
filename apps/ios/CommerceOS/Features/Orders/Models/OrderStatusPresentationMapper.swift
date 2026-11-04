import SwiftUI

/**
 * 1:1 Parity with Android OrderStatusPresentationMapper.kt (154 lines).
 * Single canonical map from backend order-status codes to customer-facing presentation.
 * Every consumer surface (Order History, Tracking, Notifications) renders through this
 * mapper so copies can never diverge between screens.
 */
public enum OrderStatusPresentationMapper {

    /** Canonical customer-facing stages, in fulfillment order. */
    public enum CustomerOrderStatus: String, CaseIterable, Equatable {
        case placed = "PLACED"
        case confirmed = "CONFIRMED"
        case packed = "PACKED"
        case inTransit = "IN_TRANSIT"
        case outForDelivery = "OUT_FOR_DELIVERY"
        case reachingYou = "REACHING_YOU"
        case delivered = "DELIVERED"
        case cancelled = "CANCELLED"
        case deliveryAttemptFailed = "DELIVERY_ATTEMPT_FAILED"
        case returnedToSeller = "RETURNED_TO_SELLER"
        case pendingRxReview = "PENDING_RX_REVIEW"
        case processing = "PROCESSING"
        case unknown = "UNKNOWN"
    }

    public struct PresentedStatus: Equatable {
        public let status: CustomerOrderStatus
        public let presentedLabel: String
        public let description: String
        public let isTerminal: Bool
        public let isCancelled: Bool
        public let isDelivered: Bool

        public init(
            status: CustomerOrderStatus,
            presentedLabel: String,
            description: String,
            isTerminal: Bool,
            isCancelled: Bool,
            isDelivered: Bool
        ) {
            self.status = status
            self.presentedLabel = presentedLabel
            self.description = description
            self.isTerminal = isTerminal
            self.isCancelled = isCancelled
            self.isDelivered = isDelivered
        }

        public var chipBackground: Color {
            switch status {
            case .cancelled:
                return Color(hex: "FEE2E2") // DangerSoft
            case .delivered:
                return Color(hex: "DCFCE7") // SavingsSoft
            case .pendingRxReview:
                return Color(hex: "FEF3C7") // VerificationSoft
            case .deliveryAttemptFailed:
                return Color(hex: "FFEDD5") // WarningSoft
            default:
                return Color(hex: "F0FDF4") // InfoContainer
            }
        }

        public var chipContent: Color {
            switch status {
            case .cancelled:
                return Color(hex: "DC2626") // Danger
            case .delivered:
                return Color(hex: "16A34A") // Savings
            case .pendingRxReview:
                return Color(hex: "D97706") // Verification
            case .deliveryAttemptFailed:
                return Color(hex: "EA580C") // Warning
            default:
                return Color(hex: "0E675E") // Primary Emerald
            }
        }

        public var hasCancelAction: Bool {
            return !isTerminal && (status == .placed || status == .confirmed)
        }
    }

    /** Presentation for a raw backend status string. Never throws on unknown codes. */
    public static func present(rawStatus: String?) -> PresentedStatus {
        let raw = rawStatus?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased() ?? ""
        switch raw {
        case "PLACED", "CREATED":
            return PresentedStatus(
                status: .placed,
                presentedLabel: "Order Placed",
                description: "We have received your order",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        case "SELLER_ACCEPTED", "CONFIRMED", "ALLOCATED_DARK_STORE", "ALLOCATED_STORE":
            return PresentedStatus(
                status: .confirmed,
                presentedLabel: "Order Accepted & Being Packed",
                description: "Store has accepted your order and is packing items",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        case "PACKED", "PACKED_FEFO":
            return PresentedStatus(
                status: .packed,
                presentedLabel: "Packed & Ready",
                description: "Your order is packed for delivery",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        case "SHIPPED", "PICKED_UP":
            return PresentedStatus(
                status: .inTransit,
                presentedLabel: "On the Way",
                description: "Your order is on its way to you",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        case "OUT_FOR_DELIVERY":
            return PresentedStatus(
                status: .outForDelivery,
                presentedLabel: "Out for Delivery",
                description: "Your delivery partner is heading to you",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        case "DELIVERED":
            return PresentedStatus(
                status: .delivered,
                presentedLabel: "Delivered",
                description: "Your order has been delivered",
                isTerminal: true,
                isCancelled: false,
                isDelivered: true
            )
        case "CANCELLED":
            return PresentedStatus(
                status: .cancelled,
                presentedLabel: "Cancelled",
                description: "This order was cancelled",
                isTerminal: true,
                isCancelled: true,
                isDelivered: false
            )
        case "DELIVERY_ATTEMPT_FAILED":
            return PresentedStatus(
                status: .deliveryAttemptFailed,
                presentedLabel: "Delivery Attempted",
                description: "We could not complete delivery — we will retry",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        case "RETURNED_TO_SELLER":
            return PresentedStatus(
                status: .returnedToSeller,
                presentedLabel: "Returned to Pharmacy",
                description: "This order was returned",
                isTerminal: true,
                isCancelled: false,
                isDelivered: false
            )
        case "PRESCRIPTION_VERIFICATION_PENDING":
            return PresentedStatus(
                status: .pendingRxReview,
                presentedLabel: "Prescription Review",
                description: "A pharmacist is verifying your prescription",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        case "PHARMACIST_APPROVED":
            return PresentedStatus(
                status: .confirmed,
                presentedLabel: "Order Confirmed",
                description: "Your prescription was approved",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        default:
            return PresentedStatus(
                status: .unknown,
                presentedLabel: raw.isEmpty ? "Processing" : raw.replacingOccurrences(of: "_", with: " ").capitalized,
                description: "",
                isTerminal: false,
                isCancelled: false,
                isDelivered: false
            )
        }
    }

    /** Ordered journey steps used by the Tracking timeline. */
    public static func timelineSteps() -> [CustomerOrderStatus] {
        return [
            .placed,
            .confirmed,
            .packed,
            .inTransit,
            .outForDelivery,
            .delivered
        ]
    }

    /** Timeline progress [1..N] for the current status. */
    public static func timelineProgress(rawStatus: String?) -> (current: Int, total: Int) {
        let steps = timelineSteps()
        let current = present(rawStatus: rawStatus).status
        if let index = steps.firstIndex(of: current) {
            return (index + 1, steps.count)
        }
        return (0, steps.count)
    }

    /** Customer-facing label per timeline step. */
    public static func label(for step: CustomerOrderStatus) -> String {
        switch step {
        case .placed: return "Order placed"
        case .confirmed: return "Order confirmed"
        case .packed: return "Packed & ready"
        case .inTransit: return "On the way"
        case .outForDelivery: return "Out for delivery"
        case .reachingYou: return "Reaching you"
        case .delivered: return "Delivered"
        case .cancelled: return "Cancelled"
        case .deliveryAttemptFailed: return "Delivery attempted"
        case .returnedToSeller: return "Returned"
        case .pendingRxReview: return "Prescription review"
        case .processing: return "Processing"
        case .unknown: return "In progress"
        }
    }
}
