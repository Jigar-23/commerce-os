import Foundation

/**
 * 1:1 Parity with Android CodLedgerManager.kt (69 lines).
 * Immutable financial event type for COD & tenant settlements.
 */
public enum FinancialEventType: String, Codable, CaseIterable, Equatable {
    case codCollection = "COD_COLLECTION"
    case riderHandoff = "RIDER_HANDOFF"
    case merchantSettlement = "MERCHANT_SETTLEMENT"
    case platformFeeDeduction = "PLATFORM_FEE_DEDUCTION"
    case refundDisbursement = "REFUND_DISBURSEMENT"
    case disputeAdjustment = "DISPUTE_ADJUSTMENT"
}

public struct FinancialLedgerEntry: Codable, Identifiable, Equatable {
    public var id: String { entryId }
    public let entryId: String
    public let orderId: String
    public let tenantId: String
    public let eventType: FinancialEventType
    public let expectedAmount: Double
    public let actualAmount: Double
    public let collectorId: String?
    public let platformFee: Double
    public let merchantPayout: Double
    public let isReconciled: Bool
    public let timestamp: Int64

    public init(
        entryId: String = "fin_\(Int64(Date().timeIntervalSince1970 * 1000))",
        orderId: String,
        tenantId: String,
        eventType: FinancialEventType,
        expectedAmount: Double,
        actualAmount: Double,
        collectorId: String? = nil,
        platformFee: Double = 0.0,
        merchantPayout: Double = 0.0,
        isReconciled: Bool = true,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000)
    ) {
        self.entryId = entryId
        self.orderId = orderId
        self.tenantId = tenantId
        self.eventType = eventType
        self.expectedAmount = expectedAmount
        self.actualAmount = actualAmount
        self.collectorId = collectorId
        self.platformFee = platformFee
        self.merchantPayout = merchantPayout
        self.isReconciled = isReconciled
        self.timestamp = timestamp
    }
}

/**
 * Enterprise Financial Ledger & COD Reconciliation Engine.
 * Ensures immutable audit trail for cash on delivery, rider handoffs, seller settlements, and platform fees.
 */
public final class CodLedgerManager {
    public static let shared = CodLedgerManager()

    private var ledgerEntries: [FinancialLedgerEntry] = []
    private let lock = NSLock()

    private init() {}

    @discardableResult
    public func recordCodCollection(
        orderId: String,
        tenantId: String,
        expected: Double,
        collected: Double,
        riderId: String
    ) -> FinancialLedgerEntry {
        lock.lock()
        defer { lock.unlock() }

        let platformFee = expected * 0.05 // 5% platform fee
        let merchantPayout = expected - platformFee

        let entry = FinancialLedgerEntry(
            entryId: "fin_\(Int64(Date().timeIntervalSince1970 * 1000))",
            orderId: orderId,
            tenantId: tenantId,
            eventType: .codCollection,
            expectedAmount: expected,
            actualAmount: collected,
            collectorId: riderId,
            platformFee: platformFee,
            merchantPayout: merchantPayout,
            isReconciled: abs(expected - collected) < 0.001,
            timestamp: Int64(Date().timeIntervalSince1970 * 1000)
        )
        ledgerEntries.append(entry)
        return entry
    }

    public func getLedgerForOrder(orderId: String) -> [FinancialLedgerEntry] {
        lock.lock()
        defer { lock.unlock() }
        return ledgerEntries.filter { $0.orderId == orderId }
    }

    public func getAllLedgerEntries() -> [FinancialLedgerEntry] {
        lock.lock()
        defer { lock.unlock() }
        return ledgerEntries
    }
}
