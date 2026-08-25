package com.commerceos.android.config

/**
 * Client Workflow Configuration governing custom operational rules.
 */
data class ClientWorkflowConfiguration(
    val allowedCheckoutFlows: List<String> = listOf("STANDARD", "EXPRESS"),
    val requiresRxValidation: Boolean = false,
    val customStepName: String? = null,
    val customGuaranteeText: String? = null
)
