package com.commerceos.android.ui.orders

import com.commerceos.android.ui.theme.CommerceColors

/**
 * Single canonical map from backend order-status codes to customer-facing
 * presentation. Every consumer surface (Order History, Tracking, Notifications)
 * renders through this mapper so copies can never diverge between screens.
 *
 * Backend codes are opaque (SELLER_ACCEPTED, PRESCRIPTION_VERIFICATION_PENDING,
 * OUT_FOR_DELIVERY...) — customers only ever see the mapped [presentedLabel].
 */
object OrderStatusPresentationMapper {

    /** Canonical customer-facing stages, in fulfillment order. */
    enum class CustomerOrderStatus {
        PLACED,
        CONFIRMED,
        PACKED,
        IN_TRANSIT,
        OUT_FOR_DELIVERY,
        REACHING_YOU,   // follows OUT_FOR_DELIVERY for delivery attempt
        DELIVERED,
        CANCELLED,
        DELIVERY_ATTEMPT_FAILED,
        RETURNED_TO_SELLER,
        PENDING_RX_REVIEW,
        PROCESSING,

        /** Not yet mapped — surface raw but flagged for a mapping gap. */
        UNKNOWN
    }

    data class PresentedStatus(
        val status: CustomerOrderStatus,
        val presentedLabel: String,
        val description: String,
        val isTerminal: Boolean,
        val isCancelled: Boolean,
        val isDelivered: Boolean
    ) {
        val chipBackground: androidx.compose.ui.graphics.Color
            @androidx.compose.runtime.Composable get() = when (status) {
                CustomerOrderStatus.CANCELLED -> CommerceColors.DangerSoft
                CustomerOrderStatus.DELIVERED -> CommerceColors.SavingsSoft
                CustomerOrderStatus.PENDING_RX_REVIEW -> CommerceColors.VerificationSoft
                CustomerOrderStatus.DELIVERY_ATTEMPT_FAILED -> CommerceColors.WarningSoft
                else -> CommerceColors.InfoContainer
            }
        val chipContent: androidx.compose.ui.graphics.Color
            @androidx.compose.runtime.Composable get() = when (status) {
                CustomerOrderStatus.CANCELLED -> CommerceColors.Danger
                CustomerOrderStatus.DELIVERED -> CommerceColors.Savings
                CustomerOrderStatus.PENDING_RX_REVIEW -> CommerceColors.Verification
                CustomerOrderStatus.DELIVERY_ATTEMPT_FAILED -> CommerceColors.Warning
                else -> CommerceColors.Primary
            }
        val hasCancelAction: Boolean
            get() = !isTerminal && status in setOf(
                CustomerOrderStatus.PLACED,
                CustomerOrderStatus.CONFIRMED
            )
    }

    /** Presentation for a raw backend status string. Never throws on unknown codes. */
    fun present(rawStatus: String?): PresentedStatus {
        val raw = rawStatus?.trim()?.uppercase() ?: ""
        return when (raw) {
            "PLACED", "CREATED" -> PresentedStatus(
                CustomerOrderStatus.PLACED, "Order Placed",
                "We have received your order", isTerminal = false, isCancelled = false, isDelivered = false
            )
            "SELLER_ACCEPTED", "CONFIRMED", "ALLOCATED_DARK_STORE" -> PresentedStatus(
                CustomerOrderStatus.CONFIRMED, "Order Accepted & Being Packed",
                "Store has accepted your order and is packing items", isTerminal = false, isCancelled = false, isDelivered = false
            )
            "PACKED", "PACKED_FEFO" -> PresentedStatus(
                CustomerOrderStatus.PACKED, "Packed & Ready",
                "Your order is packed for delivery", isTerminal = false, isCancelled = false, isDelivered = false
            )
            "SHIPPED", "PICKED_UP" -> PresentedStatus(
                CustomerOrderStatus.IN_TRANSIT, "On the Way",
                "Your order is on its way to you", isTerminal = false, isCancelled = false, isDelivered = false
            )
            "OUT_FOR_DELIVERY" -> PresentedStatus(
                CustomerOrderStatus.OUT_FOR_DELIVERY, "Out for Delivery",
                "Your delivery partner is heading to you", isTerminal = false, isCancelled = false, isDelivered = false
            )
            "DELIVERED" -> PresentedStatus(
                CustomerOrderStatus.DELIVERED, "Delivered",
                "Your order has been delivered", isTerminal = true, isCancelled = false, isDelivered = true
            )
            "CANCELLED" -> PresentedStatus(
                CustomerOrderStatus.CANCELLED, "Cancelled",
                "This order was cancelled", isTerminal = true, isCancelled = true, isDelivered = false
            )
            "DELIVERY_ATTEMPT_FAILED" -> PresentedStatus(
                CustomerOrderStatus.DELIVERY_ATTEMPT_FAILED, "Delivery Attempted",
                "We could not complete delivery — we will retry", isTerminal = false, isCancelled = false, isDelivered = false
            )
            "RETURNED_TO_SELLER" -> PresentedStatus(
                CustomerOrderStatus.RETURNED_TO_SELLER, "Returned to Pharmacy",
                "This order was returned", isTerminal = true, isCancelled = false, isDelivered = false
            )
            "PRESCRIPTION_VERIFICATION_PENDING" -> PresentedStatus(
                CustomerOrderStatus.PENDING_RX_REVIEW, "Prescription Review",
                "A pharmacist is verifying your prescription", isTerminal = false, isCancelled = false, isDelivered = false
            )
            "PHARMACIST_APPROVED" -> PresentedStatus(
                CustomerOrderStatus.CONFIRMED, "Order Confirmed",
                "Your prescription was approved", isTerminal = false, isCancelled = false, isDelivered = false
            )
            else -> PresentedStatus(
                CustomerOrderStatus.UNKNOWN, raw.ifBlank { "Processing" },
                "", isTerminal = false, isCancelled = false, isDelivered = false
            )
        }
    }

    /** Ordered journey steps used by the Tracking timeline. */
    fun timelineSteps(): List<CustomerOrderStatus> = listOf(
        CustomerOrderStatus.PLACED,
        CustomerOrderStatus.CONFIRMED,
        CustomerOrderStatus.PACKED,
        CustomerOrderStatus.IN_TRANSIT,
        CustomerOrderStatus.OUT_FOR_DELIVERY,
        CustomerOrderStatus.DELIVERED
    )

    /** Timeline progress [0..N] for the current status, for the ubiquitous line. */
    fun timelineProgress(rawStatus: String?): Pair<Int, Int> {
        val steps = timelineSteps()
        val current = present(rawStatus).status
        val index = steps.indexOf(current)
        return if (index < 0) 0 to steps.size else (index + 1) to steps.size
    }

    /** Customer-facing label per timeline step. */
    fun label(step: CustomerOrderStatus): String = when (step) {
        CustomerOrderStatus.PLACED -> "Order placed"
        CustomerOrderStatus.CONFIRMED -> "Order confirmed"
        CustomerOrderStatus.PACKED -> "Packed & ready"
        CustomerOrderStatus.IN_TRANSIT -> "On the way"
        CustomerOrderStatus.OUT_FOR_DELIVERY -> "Out for delivery"
        CustomerOrderStatus.REACHING_YOU -> "Reaching you"
        CustomerOrderStatus.DELIVERED -> "Delivered"
        CustomerOrderStatus.CANCELLED -> "Cancelled"
        CustomerOrderStatus.DELIVERY_ATTEMPT_FAILED -> "Delivery attempted"
        CustomerOrderStatus.RETURNED_TO_SELLER -> "Returned"
        CustomerOrderStatus.PENDING_RX_REVIEW -> "Prescription review"
        CustomerOrderStatus.PROCESSING -> "Processing"
        CustomerOrderStatus.UNKNOWN -> "Processing"
    }
}