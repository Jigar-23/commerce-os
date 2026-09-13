import Foundation
import ActivityKit

public struct DeliveryActivityAttributes: ActivityAttributes {
    public struct ContentState: Codable, Hashable {
        public let status: String           // "PLACED", "PACKED", "DISPATCHED", "ARRIVED", "DELIVERED"
        public let stageTitle: String       // e.g. "On the way to your doorstep"
        public let etaMinutes: Int          // e.g. 6
        public let riderName: String?
        public let riderPhone: String?
        public let deliveryOtp: String?     // e.g. "4819"
        public let currentStage: Int        // 1 to 4
        public let progressPercentage: Double
        public let lastUpdated: Date
        
        public init(
            status: String,
            stageTitle: String,
            etaMinutes: Int,
            riderName: String? = nil,
            riderPhone: String? = nil,
            deliveryOtp: String? = nil,
            currentStage: Int = 3,
            progressPercentage: Double = 0.75,
            lastUpdated: Date = Date()
        ) {
            self.status = status
            self.stageTitle = stageTitle
            self.etaMinutes = etaMinutes
            self.riderName = riderName
            self.riderPhone = riderPhone
            self.deliveryOtp = deliveryOtp
            self.currentStage = currentStage
            self.progressPercentage = progressPercentage
            self.lastUpdated = lastUpdated
        }
    }
    
    // Constant attributes for this delivery
    public let orderId: String
    public let merchantName: String
    public let customerAddress: String
    public let itemCount: Int
    public let totalAmount: Double
    
    public init(
        orderId: String,
        merchantName: String,
        customerAddress: String,
        itemCount: Int,
        totalAmount: Double
    ) {
        self.orderId = orderId
        self.merchantName = merchantName
        self.customerAddress = customerAddress
        self.itemCount = itemCount
        self.totalAmount = totalAmount
    }
}

// MARK: - Activity Lifecycle Manager
public final class DeliveryActivityManager {
    public static let shared = DeliveryActivityManager()
    
    private var currentActivity: Activity<DeliveryActivityAttributes>? = nil
    
    private init() {}
    
    public var areActivitiesEnabled: Bool {
        ActivityAuthorizationInfo().areActivitiesEnabled
    }
    
    /// Starts a live Lock Screen and Dynamic Island Activity
    @discardableResult
    public func startLiveActivity(
        orderId: String,
        merchantName: String,
        customerAddress: String,
        itemCount: Int,
        totalAmount: Double,
        initialState: DeliveryActivityAttributes.ContentState
    ) -> Activity<DeliveryActivityAttributes>? {
        guard areActivitiesEnabled else { return nil }
        
        let attributes = DeliveryActivityAttributes(
            orderId: orderId,
            merchantName: merchantName,
            customerAddress: customerAddress,
            itemCount: itemCount,
            totalAmount: totalAmount
        )
        
        do {
            let activity = try Activity<DeliveryActivityAttributes>.request(
                attributes: attributes,
                content: .init(state: initialState, staleDate: Calendar.current.date(byAdding: .minute, value: 30, to: Date()))
            )
            self.currentActivity = activity
            return activity
        } catch {
            print("[DeliveryActivityManager Error] Failed starting Live Activity: \(error.localizedDescription)")
            return nil
        }
    }
    
    /// Updates dynamic content state (ETA, stage, OTP)
    public func updateLiveActivity(state: DeliveryActivityAttributes.ContentState) async {
        guard let activity = currentActivity else { return }
        await activity.update(.init(state: state, staleDate: nil))
    }
    
    /// Ends the activity upon delivery confirmation
    public func endLiveActivity(finalState: DeliveryActivityAttributes.ContentState? = nil) async {
        guard let activity = currentActivity else { return }
        let content = finalState != nil ? ActivityContent(state: finalState!, staleDate: nil) : nil
        await activity.end(content, dismissalPolicy: .after(Date().addingTimeInterval(5)))
        self.currentActivity = nil
    }
}
