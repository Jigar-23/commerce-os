import SwiftUI
import UIKit

public struct GlobalCartBar: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    public let onTap: () -> Void
    
    public init(onTap: @escaping () -> Void) {
        self.onTap = onTap
    }
    
    private func triggerHaptic() {
        let generator = UIImpactFeedbackGenerator(style: .medium)
        generator.prepare()
        generator.impactOccurred()
    }
    
    public var body: some View {
        let count = cartStore.totalItemCount
        if count > 0 {
            Button(action: {
                triggerHaptic()
                onTap()
            }) {
                HStack(spacing: 12) {
                    // Left Cart Icon Pill
                    ZStack {
                        Circle()
                            .fill(Color.white.opacity(0.2))
                            .frame(width: 36, height: 36)
                        Image(systemName: "cart.fill")
                            .font(.system(size: 16, weight: .bold))
                            .foregroundColor(.white)
                    }
                    
                    // Center Price & Speed Details
                    VStack(alignment: .leading, spacing: 2) {
                        let countLabel = "\(count) \(count == 1 ? "ITEM" : "ITEMS")"
                        Text("\(countLabel) • ₹\(String(format: "%.2f", cartStore.subtotal))")
                            .font(.system(size: 13.5, weight: .bold))
                            .foregroundColor(.white)
                        
                        Text("⚡ 10 MINS DELIVERY")
                            .font(.system(size: 10, weight: .heavy))
                            .foregroundColor(Color(hex: "A7F3D0"))
                    }
                    
                    Spacer()
                    
                    // Right View Cart CTA
                    HStack(spacing: 4) {
                        Text("View Cart")
                            .font(.system(size: 13, weight: .bold))
                            .foregroundColor(.white)
                        Image(systemName: "arrow.right")
                            .font(.system(size: 13, weight: .heavy))
                            .foregroundColor(.white)
                    }
                }
                .padding(.horizontal, 16)
                .padding(.vertical, 10)
                .background(
                    LinearGradient(
                        colors: [Color(hex: "047857"), Color(hex: "065F46")],
                        startPoint: .leading,
                        endPoint: .trailing
                    )
                )
                .cornerRadius(14)
                .shadow(color: Color(hex: "047857").opacity(0.35), radius: 8, x: 0, y: 4)
            }
            .buttonStyle(PlainButtonStyle())
            .padding(.horizontal, 14)
            .padding(.vertical, 6)
            .transition(.move(edge: .bottom).combined(with: .opacity))
            .animation(.spring(response: 0.35, dampingFraction: 0.75), value: count)
        }
    }
}
