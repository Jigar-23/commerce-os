package com.commerceos.android.registry

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.CommerceDomain

/**
 * All canonical workflows supported across Commerce OS clients.
 */
enum class WorkflowType {
    AUTHENTICATION,
    CART,
    CHECKOUT,
    PAYMENT,
    DELIVERY,
    PRESCRIPTION,
    SERVICE_BOOKING,
    RETURNS,
    CANCELLATION,
    REORDER,
    WISHLIST,
    COMPARISON,
    SUBSCRIPTION,
    CUSTOM
}

/**
 * Capability details for a specific workflow.
 */
data class WorkflowCapability(
    val type: WorkflowType,
    val isSupported: Boolean = true,
    val route: String? = null,
    val title: String = type.name,
    val unsupportedMessage: String = "This feature is not available for your client profile."
)

/**
 * Domain Workflow Capabilities and Rules.
 */
data class DomainWorkflowRules(
    val requiresPrescriptionUpload: Boolean = false,
    val supportsExpressDelivery: Boolean = true,
    val supportsTimeSlotBooking: Boolean = false,
    val supportsSizeSelector: Boolean = false,
    val supportsProductComparison: Boolean = false,
    val supportsReorder: Boolean = true,
    val supportsWishlist: Boolean = true,
    val supportsReturns: Boolean = true,
    val supportsCancellation: Boolean = true,
    val checkoutCtaLabel: String = "Place Order",
    val cartTypeLabel: String = "Cart",
    val activeWorkflows: Set<WorkflowType> = defaultWorkflows()
)

private fun defaultWorkflows(): Set<WorkflowType> = setOf(
    WorkflowType.AUTHENTICATION,
    WorkflowType.CART,
    WorkflowType.CHECKOUT,
    WorkflowType.PAYMENT,
    WorkflowType.DELIVERY,
    WorkflowType.REORDER,
    WorkflowType.WISHLIST,
    WorkflowType.CANCELLATION,
    WorkflowType.RETURNS
)

/**
 * Registry mapping Client Configuration domains to active domain workflow rules.
 * Guarantees zero workflow leakage across client switches and enforces route/deep link blocking.
 */
object WorkflowRegistry {

    private val customWorkflows = mutableMapOf<String, WorkflowCapability>()

    fun registerCustomWorkflow(workflowKey: String, capability: WorkflowCapability) {
        customWorkflows[workflowKey] = capability
    }

    fun getCustomWorkflow(workflowKey: String): WorkflowCapability? = customWorkflows[workflowKey]

    /** Checks whether a specific workflow is supported for the active client configuration. */
    fun isWorkflowSupported(workflow: WorkflowType, config: ClientConfiguration): Boolean {
        val rules = resolveWorkflowRules(config)
        return when (workflow) {
            WorkflowType.AUTHENTICATION -> true
            WorkflowType.CART -> true
            WorkflowType.CHECKOUT -> true
            WorkflowType.PAYMENT -> true
            WorkflowType.DELIVERY -> true
            WorkflowType.PRESCRIPTION -> config.features.enablePrescriptionUpload
            WorkflowType.SERVICE_BOOKING -> config.features.enableServiceBooking
            WorkflowType.RETURNS -> rules.supportsReturns
            WorkflowType.CANCELLATION -> rules.supportsCancellation
            WorkflowType.REORDER -> config.features.enableReorder
            WorkflowType.WISHLIST -> config.features.enableWishlist
            WorkflowType.COMPARISON -> config.features.enableProductComparison
            WorkflowType.SUBSCRIPTION -> config.features.enableSubscriptions
            WorkflowType.CUSTOM -> true
        }
    }

    /** Resolves active domain rules for a given client configuration. */
    fun resolveWorkflowRules(config: ClientConfiguration): DomainWorkflowRules {
        return when (config.domain) {
            CommerceDomain.PHARMACY -> DomainWorkflowRules(
                requiresPrescriptionUpload = config.features.enablePrescriptionUpload,
                supportsExpressDelivery = true,
                supportsTimeSlotBooking = false,
                supportsSizeSelector = false,
                supportsProductComparison = false,
                supportsReorder = config.features.enableReorder,
                supportsWishlist = config.features.enableWishlist,
                supportsReturns = false, // Medicine returns gated for safety
                supportsCancellation = true,
                checkoutCtaLabel = config.terminology.checkoutLabel.ifBlank { "Confirm Order & Upload Rx" },
                cartTypeLabel = config.terminology.cartLabel,
                activeWorkflows = defaultWorkflows() + setOf(WorkflowType.PRESCRIPTION)
            )
            CommerceDomain.FASHION -> DomainWorkflowRules(
                requiresPrescriptionUpload = false,
                supportsExpressDelivery = true,
                supportsTimeSlotBooking = false,
                supportsSizeSelector = true,
                supportsProductComparison = false,
                supportsReorder = config.features.enableReorder,
                supportsWishlist = config.features.enableWishlist,
                supportsReturns = true,
                supportsCancellation = true,
                checkoutCtaLabel = config.terminology.checkoutLabel.ifBlank { "Checkout Bag" },
                cartTypeLabel = config.terminology.cartLabel,
                activeWorkflows = defaultWorkflows()
            )
            CommerceDomain.FOOD -> DomainWorkflowRules(
                requiresPrescriptionUpload = false,
                supportsExpressDelivery = true,
                supportsTimeSlotBooking = false,
                supportsSizeSelector = false,
                supportsProductComparison = false,
                supportsReorder = config.features.enableReorder,
                supportsWishlist = false,
                supportsReturns = false,
                supportsCancellation = true,
                checkoutCtaLabel = config.terminology.checkoutLabel.ifBlank { "Proceed to Delivery" },
                cartTypeLabel = config.terminology.cartLabel,
                activeWorkflows = defaultWorkflows() - setOf(WorkflowType.WISHLIST, WorkflowType.RETURNS)
            )
            CommerceDomain.ELECTRONICS -> DomainWorkflowRules(
                requiresPrescriptionUpload = false,
                supportsExpressDelivery = true,
                supportsTimeSlotBooking = false,
                supportsSizeSelector = false,
                supportsProductComparison = config.features.enableProductComparison,
                supportsReorder = config.features.enableReorder,
                supportsWishlist = config.features.enableWishlist,
                supportsReturns = true,
                supportsCancellation = true,
                checkoutCtaLabel = config.terminology.checkoutLabel.ifBlank { "Proceed to Checkout" },
                cartTypeLabel = config.terminology.cartLabel,
                activeWorkflows = defaultWorkflows() + setOf(WorkflowType.COMPARISON)
            )
            CommerceDomain.SERVICES -> DomainWorkflowRules(
                requiresPrescriptionUpload = false,
                supportsExpressDelivery = false,
                supportsTimeSlotBooking = true,
                supportsSizeSelector = false,
                supportsProductComparison = false,
                supportsReorder = config.features.enableReorder,
                supportsWishlist = false,
                supportsReturns = false,
                supportsCancellation = true,
                checkoutCtaLabel = config.terminology.checkoutLabel.ifBlank { "Confirm Service Booking" },
                cartTypeLabel = config.terminology.cartLabel,
                activeWorkflows = setOf(
                    WorkflowType.AUTHENTICATION,
                    WorkflowType.CART,
                    WorkflowType.CHECKOUT,
                    WorkflowType.PAYMENT,
                    WorkflowType.SERVICE_BOOKING,
                    WorkflowType.CANCELLATION,
                    WorkflowType.REORDER
                )
            )
            CommerceDomain.GENERAL_COMMERCE -> DomainWorkflowRules(
                requiresPrescriptionUpload = false,
                supportsExpressDelivery = true,
                supportsTimeSlotBooking = false,
                supportsSizeSelector = false,
                supportsProductComparison = config.features.enableProductComparison,
                supportsReorder = config.features.enableReorder,
                supportsWishlist = config.features.enableWishlist,
                supportsReturns = true,
                supportsCancellation = true,
                checkoutCtaLabel = config.terminology.checkoutLabel.ifBlank { "Place Order" },
                cartTypeLabel = config.terminology.cartLabel,
                activeWorkflows = defaultWorkflows()
            )
        }
    }
}
