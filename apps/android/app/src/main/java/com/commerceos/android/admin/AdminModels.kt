package com.commerceos.android.admin

import java.math.BigDecimal

/** License states for Commerce OS tenant clients. */
enum class LicenseState {
    ACTIVE,
    PAYMENT_DUE,
    GRACE,
    SUSPENDED,
    TERMINATED
}

/** Entitlement plan entitlements configuration per tenant. */
data class TenantEntitlement(
    val tenantId: String,
    val planName: String = "Enterprise Multi-Tenant",
    val maxOrdersPerMonth: Int = 100000,
    val enabledVerticals: Set<String> = setOf("health", "grocery", "fashion", "electronics", "food", "services"),
    val supportsAiAssistant: Boolean = true,
    val supportsCustomMarketplacePlugins: Boolean = true,
    val supportsQuickDelivery: Boolean = true
)

/** Subscription status & billing record per tenant. */
data class TenantSubscription(
    val tenantId: String,
    val clientName: String,
    val licenseState: LicenseState = LicenseState.ACTIVE,
    val monthlyFee: BigDecimal = BigDecimal.valueOf(4999.0),
    val nextBillingTimestamp: Long = System.currentTimeMillis() + (30 * 24 * 3600 * 1000L),
    val gracePeriodEndTimestamp: Long? = null,
    val suspensionReason: String? = null
)

/** Audit log entry for tracking tenant mutations and platform changes. */
data class AuditLogEntry(
    val logId: String,
    val actor: String,
    val action: String,
    val tenantId: String,
    val resource: String,
    val oldValue: String? = null,
    val newValue: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val requestId: String = "req_${System.currentTimeMillis()}"
)
