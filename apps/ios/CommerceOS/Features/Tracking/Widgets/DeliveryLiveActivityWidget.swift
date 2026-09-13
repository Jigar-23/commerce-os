import SwiftUI
import WidgetKit
import ActivityKit

public struct DeliveryLiveActivityWidget: Widget {
    public let kind: String = "CommerceOSDeliveryActivityWidget"
    
    public init() {}
    
    public var body: some WidgetConfiguration {
        ActivityConfiguration(for: DeliveryActivityAttributes.self) { context in
            // Lock Screen & Notification Center Banner View
            DeliveryLockScreenBannerView(
                attributes: context.attributes,
                state: context.state
            )
        } dynamicIsland: { context in
            DynamicIsland {
                // Expanded Dynamic Island
                DynamicIslandExpandedRegion(.leading) {
                    HStack(spacing: 8) {
                        Image(systemName: "cross.case.fill")
                            .font(.system(size: 16))
                            .foregroundColor(.green)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(context.attributes.merchantName)
                                .font(.system(size: 13, weight: .bold))
                                .lineLimit(1)
                            Text("Order #\(context.attributes.orderId.suffix(6))")
                                .font(.system(size: 10))
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.leading, 4)
                }
                
                DynamicIslandExpandedRegion(.trailing) {
                    VStack(alignment: .trailing, spacing: 2) {
                        Text("\(context.state.etaMinutes) MINS")
                            .font(.system(size: 16, weight: .black))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                        Text("ETA")
                            .font(.system(size: 9, weight: .bold))
                            .foregroundColor(.secondary)
                    }
                    .padding(.trailing, 4)
                }
                
                DynamicIslandExpandedRegion(.center) {
                    if let otp = context.state.deliveryOtp {
                        HStack(spacing: 4) {
                            Text("PIN:")
                                .font(.system(size: 11, weight: .bold))
                                .foregroundColor(.secondary)
                            Text(otp)
                                .font(.system(size: 14, weight: .black, design: .monospaced))
                                .foregroundColor(.white)
                        }
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(CommerceOSTheme.Colors.brandPrimary.opacity(0.4))
                        .cornerRadius(6)
                    }
                }
                
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(spacing: 6) {
                        HStack {
                            Text(context.state.stageTitle)
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundColor(.white)
                            Spacer()
                            if let rider = context.state.riderName {
                                Text("Rider: \(rider)")
                                    .font(.system(size: 11))
                                    .foregroundColor(.secondary)
                            }
                        }
                        
                        // Progress Bar
                        GeometryReader { proxy in
                            ZStack(alignment: .leading) {
                                Capsule()
                                    .fill(Color.gray.opacity(0.3))
                                    .frame(height: 5)
                                Capsule()
                                    .fill(CommerceOSTheme.Colors.brandPrimary)
                                    .frame(width: proxy.size.width * CGFloat(context.state.progressPercentage), height: 5)
                            }
                        }
                        .frame(height: 5)
                    }
                    .padding(.top, 4)
                }
            } compactLeading: {
                Image(systemName: "box.truck.fill")
                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                    .font(.system(size: 12))
            } compactTrailing: {
                Text("\(context.state.etaMinutes)m")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
            } minimal: {
                Image(systemName: "box.truck.fill")
                    .foregroundColor(CommerceOSTheme.Colors.brandPrimary)
                    .font(.system(size: 11))
            }
        }
    }
}

// MARK: - Lock Screen Banner View
public struct DeliveryLockScreenBannerView: View {
    let attributes: DeliveryActivityAttributes
    let state: DeliveryActivityAttributes.ContentState
    
    public var body: some View {
        VStack(spacing: 12) {
            HStack {
                HStack(spacing: 8) {
                    ZStack {
                        Circle()
                            .fill(CommerceOSTheme.Colors.brandPrimarySoft)
                            .frame(width: 32, height: 32)
                        Image(systemName: "bolt.fill")
                            .font(.system(size: 16))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                    }
                    
                    VStack(alignment: .leading, spacing: 2) {
                        Text(attributes.merchantName)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.primary)
                        Text(state.stageTitle)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                }
                
                Spacer()
                
                VStack(alignment: .trailing, spacing: 2) {
                    Text("~ \(state.etaMinutes) MINS")
                        .font(.system(size: 15, weight: .black))
                        .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                    Text("Live GPS")
                        .font(.system(size: 9, weight: .bold))
                        .foregroundColor(.secondary)
                }
            }
            
            // Progress Bar
            HStack(spacing: 4) {
                ForEach(1...4, id: \.self) { stage in
                    RoundedRectangle(cornerRadius: 2.5)
                        .fill(stage <= state.currentStage ? CommerceOSTheme.Colors.brandPrimaryDark : Color.gray.opacity(0.3))
                        .frame(height: 4)
                }
            }
            
            // Footer: Delivery OTP & Rider Info
            HStack {
                if let otp = state.deliveryOtp {
                    HStack(spacing: 6) {
                        Text("DELIVERY PIN:")
                            .font(.system(size: 10, weight: .black))
                            .foregroundColor(.secondary)
                        Text(otp)
                            .font(.system(size: 16, weight: .black, design: .monospaced))
                            .foregroundColor(CommerceOSTheme.Colors.brandPrimaryDark)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 4)
                    .background(CommerceOSTheme.Colors.brandPrimarySoft)
                    .cornerRadius(6)
                }
                
                Spacer()
                
                if let rider = state.riderName {
                    HStack(spacing: 4) {
                        Image(systemName: "person.fill")
                            .font(.system(size: 10))
                        Text(rider)
                            .font(.system(size: 11, weight: .medium))
                    }
                    .foregroundColor(.secondary)
                }
            }
        }
        .padding(14)
        .background(Color(UIColor.secondarySystemBackground))
    }
}
