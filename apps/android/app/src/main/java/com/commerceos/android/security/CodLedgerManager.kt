package com.commerceos.android.security

import java.math.BigDecimal

/** Immutable financial event type for COD & tenant settlements. */
enum class FinancialEventType {
    COD_COLLECTION,
    RIDER_HANDOFF,
    MERCHANT_SETTLEMENT,
    PLATFORM_FEE_DEDUCTION,
    REFUND_DISBURSEMENT,
    DISPUTE_ADJUSTMENT
}

/** Immutable record of a financial event in Commerce OS ledger. */
data class FinancialLedgerEntry(
    val entryId: String,
    val orderId: String,
    val tenantId: String,
    val eventType: FinancialEventType,
    val expectedAmount: BigDecimal,
    val actualAmount: BigDecimal,
    val collectorId: String? = null,
    val platformFee: BigDecimal = BigDecimal.ZERO,
    val merchantPayout: BigDecimal = BigDecimal.ZERO,
    val isReconciled: Boolean = true,
    val timestamp: Long = System.currentTimeMillis()
)

/**
 * Enterprise Financial Ledger & COD Reconciliation Engine.
 * Ensures immutable audit trail for cash on delivery, rider handoffs, seller settlements, and platform fees.
 */
object CodLedgerManager {

    private val ledgerEntries = mutableListOf<FinancialLedgerEntry>()

    fun recordCodCollection(
        orderId: String,
        tenantId: String,
        expected: BigDecimal,
        collected: BigDecimal,
        riderId: String
    ): FinancialLedgerEntry {
        val platformFee = expected.multiply(BigDecimal.valueOf(0.05)) // 5% platform fee
        val merchantPayout = expected.subtract(platformFee)

        val entry = FinancialLedgerEntry(
            entryId = "fin_${System.currentTimeMillis()}",
            orderId = orderId,
            tenantId = tenantId,
            eventType = FinancialEventType.COD_COLLECTION,
            expectedAmount = expected,
            actualAmount = collected,
            collectorId = riderId,
            platformFee = platformFee,
            merchantPayout = merchantPayout,
            isReconciled = expected.compareTo(collected) == 0
        )
        ledgerEntries.add(entry)
        return entry
    }

    fun getLedgerForOrder(orderId: String): List<FinancialLedgerEntry> {
        return ledgerEntries.filter { it.orderId == orderId }
    }

    fun getAllLedgerEntries(): List<FinancialLedgerEntry> = ledgerEntries.toList()
}
