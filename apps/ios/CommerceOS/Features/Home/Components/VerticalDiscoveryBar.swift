import SwiftUI
import UIKit

// MARK: - Vertical Discovery Bar (Ported from Android VerticalDiscoveryBar.kt)
/// Dynamically presents client-configured verticals with custom labels, icons, ordering,
/// active states, and tactile haptic feedback.
public struct VerticalDiscoveryBar: View {
    public let activeVerticalId: String
    public let onSelectVertical: (String) -> Void
    public var verticals: [VerticalPresentation] = VerticalPresentation.standardVerticals
    
    public init(
        activeVerticalId: String = "pharmacy",
        verticals: [VerticalPresentation] = VerticalPresentation.standardVerticals,
        onSelectVertical: @escaping (String) -> Void
    ) {
        self.activeVerticalId = activeVerticalId
        self.verticals = verticals
        self.onSelectVertical = onSelectVertical
    }
    
    private func triggerHaptic() {
        let generator = UISelectionFeedbackGenerator()
        generator.prepare()
        generator.selectionChanged()
    }
    
    public var body: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                ForEach(verticals) { item in
                    let isActive = item.verticalId.lowercased() == activeVerticalId.lowercased()
                    let primaryColor = Color(hex: item.primaryColorHex)
                    
                    Button(action: {
                        triggerHaptic()
                        onSelectVertical(item.verticalId)
                    }) {
                        HStack(spacing: 6) {
                            Text(item.visualSymbol)
                                .font(.system(size: 15))
                            
                            Text(item.verticalName)
                                .font(.system(size: 13, weight: isActive ? .bold : .medium))
                                .foregroundColor(isActive ? .white : Color(hex: "0F172A"))
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 8)
                        .background(isActive ? primaryColor : Color.white)
                        .cornerRadius(20)
                        .overlay(
                            RoundedRectangle(cornerRadius: 20)
                                .stroke(isActive ? primaryColor : Color(hex: "E2E8F0"), lineWidth: 1)
                        )
                        .shadow(
                            color: isActive ? primaryColor.opacity(0.3) : Color.black.opacity(0.03),
                            radius: isActive ? 4 : 2,
                            x: 0,
                            y: 1
                        )
                    }
                    .buttonStyle(PlainButtonStyle())
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
        }
    }
}
