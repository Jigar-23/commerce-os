import SwiftUI

/**
 * High-aesthetic Splash Screen tailored for quick-commerce healthcare.
 * 1:1 Parity with Android SplashScreen.kt
 * Inspired by Tata 1mg, Truemeds, and PharmEasy with brand trust badges,
 * 10-minute delivery cues, and smooth entry animation.
 */
public struct SplashScreen: View {
    public let onDismiss: () -> Void
    
    @State private var scale: CGFloat = 0.92
    @State private var pulseScale: CGFloat = 1.0
    @State private var opacity: Double = 1.0
    
    public init(onDismiss: @escaping () -> Void) {
        self.onDismiss = onDismiss
    }
    
    public var body: some View {
        ZStack {
            // Background vertical gradient
            LinearGradient(
                colors: [
                    Color.white,
                    Color(hex: "F0FDF4"),
                    Color(hex: "E6F7EF")
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .ignoresSafeArea()
            
            // Center Brand Identity
            VStack(spacing: 0) {
                Spacer()
                
                // Glowing Pharmacy Icon Badge
                ZStack {
                    // Ambient halo
                    Circle()
                        .fill(Color(hex: "10B981").opacity(0.15))
                        .frame(width: 110, height: 110)
                        .scaleEffect(pulseScale)
                    
                    // Emerald Core Icon Container
                    RoundedRectangle(cornerRadius: 26, style: .continuous)
                        .fill(Color(hex: "059669"))
                        .frame(width: 80, height: 80)
                        .shadow(color: Color(hex: "059669").opacity(0.35), radius: 8, x: 0, y: 4)
                        .overlay(
                            Image(systemName: "cross.case.fill")
                                .font(.system(size: 38, weight: .bold))
                                .foregroundColor(.white)
                        )
                }
                
                Spacer().frame(height: 24)
                
                // Brand Title
                Text("CommerceOS")
                    .font(.system(size: 28, weight: .heavy))
                    .foregroundColor(Color(hex: "0F172A"))
                    .tracking(-0.5)
                
                Spacer().frame(height: 6)
                
                // Brand Tagline
                Text("Your Trusted Licensed Pharmacy Partner")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundColor(Color(hex: "047857"))
                
                Spacer().frame(height: 18)
                
                // 10-Minute Delivery Badge
                HStack(spacing: 6) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 14))
                        .foregroundColor(Color(hex: "059669"))
                    Text("10-Minute Express Delivery")
                        .font(.system(size: 12, weight: .bold))
                        .foregroundColor(Color(hex: "065F46"))
                }
                .padding(.horizontal, 14)
                .padding(.vertical, 6)
                .background(Color(hex: "059669").opacity(0.10))
                .cornerRadius(20)
                .overlay(
                    RoundedRectangle(cornerRadius: 20)
                        .stroke(Color(hex: "10B981").opacity(0.35), lineWidth: 1)
                )
                
                Spacer()
                
                // Bottom Trust Section
                VStack(spacing: 12) {
                    HStack(spacing: 8) {
                        trustBadgePill(icon: "checkmark.seal.fill", text: "100% Genuine")
                        trustBadgePill(icon: "cross.fill", text: "Rx Verified")
                        trustBadgePill(icon: "snowflake", text: "Cold-Chain")
                    }
                    
                    Text("Licensed Pharmacists • Real-Time Inventory • Express Dispatch")
                        .font(.system(size: 10, weight: .regular))
                        .foregroundColor(Color(hex: "64748B"))
                }
                .padding(.bottom, 28)
            }
            .scaleEffect(scale)
            .opacity(opacity)
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onDismiss()
        }
        .onAppear {
            withAnimation(.easeOut(duration: 0.5)) {
                scale = 1.0
            }
            withAnimation(Animation.easeInOut(duration: 0.9).repeatForever(autoreverses: true)) {
                pulseScale = 1.08
            }
            DispatchQueue.main.asyncAfter(deadline: .now() + 1.8) {
                withAnimation(.easeInOut(duration: 0.3)) {
                    onDismiss()
                }
            }
        }
    }
    
    private func trustBadgePill(icon: String, text: String) -> some View {
        HStack(spacing: 4) {
            Image(systemName: icon)
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(Color(hex: "059669"))
            Text(text)
                .font(.system(size: 10, weight: .semibold))
                .foregroundColor(Color(hex: "334155"))
        }
        .padding(.horizontal, 8)
        .padding(.vertical, 4)
        .background(Color.white.opacity(0.85))
        .cornerRadius(12)
        .overlay(
            RoundedRectangle(cornerRadius: 12)
                .stroke(Color(hex: "E2E8F0"), lineWidth: 1)
        )
    }
}
