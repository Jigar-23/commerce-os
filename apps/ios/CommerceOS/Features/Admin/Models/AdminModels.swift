import Foundation

/**
 * 1:1 Parity with Android AdminModels.kt (47 lines).
 * License states, tenant entitlements, subscriptions, and audit log entries.
 */
public enum LicenseState: String, Codable, CaseIterable, Equatable {
    case active = "ACTIVE"
    case paymentDue = "PAYMENT_DUE"
    case grace = "GRACE"
    case suspended = "SUSPENDED"
    case terminated = "TERMINATED"
}

public struct TenantEntitlement: Codable, Identifiable, Equatable {
    public var id: String { tenantId }
    public let tenantId: String
    public let planName: String
    public let maxOrdersPerMonth: Int
    public let enabledVerticals: Set<String>
    public let supportsAiAssistant: Bool
    public let supportsCustomMarketplacePlugins: Bool
    public let supportsQuickDelivery: Bool

    public init(
        tenantId: String,
        planName: String = "Enterprise Multi-Tenant",
        maxOrdersPerMonth: Int = 100000,
        enabledVerticals: Set<String> = ["health", "grocery", "fashion", "electronics", "food", "services"],
        supportsAiAssistant: Bool = true,
        supportsCustomMarketplacePlugins: Bool = true,
        supportsQuickDelivery: Bool = true
    ) {
        self.tenantId = tenantId
        self.planName = planName
        self.maxOrdersPerMonth = maxOrdersPerMonth
        self.enabledVerticals = enabledVerticals
        self.supportsAiAssistant = supportsAiAssistant
        self.supportsCustomMarketplacePlugins = supportsCustomMarketplacePlugins
        self.supportsQuickDelivery = supportsQuickDelivery
    }
}

public struct TenantSubscription: Codable, Identifiable, Equatable {
    public var id: String { tenantId }
    public let tenantId: String
    public let clientName: String
    public var licenseState: LicenseState
    public let monthlyFee: Double
    public let nextBillingTimestamp: Int64
    public var gracePeriodEndTimestamp: Int64?
    public var suspensionReason: String?

    public init(
        tenantId: String,
        clientName: String,
        licenseState: LicenseState = .active,
        monthlyFee: Double = 4999.0,
        nextBillingTimestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000) + (30 * 24 * 3600 * 1000),
        gracePeriodEndTimestamp: Int64? = nil,
        suspensionReason: String? = nil
    ) {
        self.tenantId = tenantId
        self.clientName = clientName
        self.licenseState = licenseState
        self.monthlyFee = monthlyFee
        self.nextBillingTimestamp = nextBillingTimestamp
        self.gracePeriodEndTimestamp = gracePeriodEndTimestamp
        self.suspensionReason = suspensionReason
    }
}

public struct AuditLogEntry: Codable, Identifiable, Equatable {
    public var id: String { logId }
    public let logId: String
    public let actor: String
    public let action: String
    public let tenantId: String
    public let resource: String
    public let oldValue: String?
    public let newValue: String?
    public let timestamp: Int64
    public let requestId: String

    public init(
        logId: String = "audit_\(UUID().uuidString)",
        actor: String,
        action: String,
        tenantId: String,
        resource: String,
        oldValue: String? = nil,
        newValue: String? = nil,
        timestamp: Int64 = Int64(Date().timeIntervalSince1970 * 1000),
        requestId: String = "req_\(UUID().uuidString.prefix(8))"
    ) {
        self.logId = logId
        self.actor = actor
        self.action = action
        self.tenantId = tenantId
        self.resource = resource
        self.oldValue = oldValue
        self.newValue = newValue
        self.timestamp = timestamp
        self.requestId = requestId
    }
}
