import SwiftUI

public struct GlobalCartBar: View {
    @EnvironmentObject private var cartStore: CartLocalStore
    @EnvironmentObject private var configProvider: ClientConfigProvider
    let onTap: () -> Void
    
    public var body: some View {
        Button(action: onTap) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    let count = cartStore.totalItemCount
                    let label = "\(count) \(count == 1 ? "ITEM" : "ITEMS") • $\(String(format: "%.2f", cartStore.subtotal))"
                    Text(label)
                        .font(.system(size: 14, weight: .bold))
                        .foregroundColor(.white)
                    Text("Arrives in 10-15 mins")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundColor(CommerceOSTheme.Colors.brandPrimarySoft)
                }
                
                Spacer()
                
                HStack(spacing: 6) {
                    Text("View Cart")
                        .font(.system(size: 13, weight: .black))
                        .foregroundColor(.white)
                    Image(systemName: "cart.fill")
                        .font(.system(size: 13, weight: .bold))
                        .foregroundColor(.white)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(CommerceOSTheme.Colors.brandPrimaryDark)
            .cornerRadius(12)
            .shadow(color: Color.black.opacity(0.20), radius: 10, x: 0, y: 5)
        }
        .buttonStyle(PlainButtonStyle())
    }
}
