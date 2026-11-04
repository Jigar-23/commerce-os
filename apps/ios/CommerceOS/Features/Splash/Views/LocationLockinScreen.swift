import SwiftUI
import CoreLocation

// MARK: - Location Lock-in Interstitial (Ported from Android LocationLockinScreen.kt)
/// High-performing Location Lock-in Interstitial benchmarked directly against Tata 1mg.
/// Appears for ~1.2s right after splash or during startup location acquisition:
/// 1. Phase 1 (0ms - 600ms): Soft stylized map background with pulsing dark radar wave "Locating delivery address..."
/// 2. Phase 2 (600ms - 1300ms): Spotlight focus locks onto the map, turns quick-commerce emerald green,
///    boldly displaying "Delivering to [Locality] • 10 MINS".
/// 3. Tap to dismiss immediately for power users.
public struct LocationLockinScreen: View {
    public let locality: String
    public let onDismiss: () -> Void
    
    @State private var isLockedIn: Bool = false
    @State private var radarScale: CGFloat = 0.5
    @State private var radarOpacity: Double = 0.8
    @State private var spotlightScale: CGFloat = 0.7
    @State private var arrowAngle: Double = -35
    
    public init(locality: String = "Rewari", onDismiss: @escaping () -> Void) {
        self.locality = locality
        self.onDismiss = onDismiss
    }
    
    private var displayLocality: String {
        let clean = locality.trimmingCharacters(in: .whitespacesAndNewlines)
        if clean.isEmpty || clean.caseInsensitiveCompare("Select Delivery Location") == .orderedSame {
            return "Current Location"
        }
        return clean
    }
    
    public var body: some View {
        ZStack {
            // Background Canvas: Soft Warm Cream
            Color(hex: "FCFBF9")
                .ignoresSafeArea()
            
            // 1. Procedural Stylized Vector Grid Background
            ProceduralVectorMap(isLockedIn: isLockedIn)
                .ignoresSafeArea()
            
            // 2. Central Radar & Location Lock Target
            VStack(spacing: 24) {
                Spacer()
                
                ZStack {
                    // Outer Pulsing Radar Wave
                    Circle()
                        .stroke(
                            isLockedIn ? Color(hex: "10B981").opacity(radarOpacity * 0.4) : Color(hex: "0E675E").opacity(radarOpacity * 0.3),
                            lineWidth: 3
                        )
                        .frame(width: 170 * radarScale, height: 170 * radarScale)
                    
                    Circle()
                        .fill(
                            isLockedIn ? Color(hex: "10B981").opacity(0.12) : Color(hex: "0E675E").opacity(0.08)
                        )
                        .frame(width: 140 * radarScale, height: 140 * radarScale)
                    
                    // Center Location Pin Hub
                    Circle()
                        .fill(Color.white)
                        .frame(width: 68, height: 68)
                        .shadow(color: Color.black.opacity(0.12), radius: 8, x: 0, y: 4)
                        .overlay(
                            Image(systemName: isLockedIn ? "checkmark.circle.fill" : "location.north.circle.fill")
                                .font(.system(size: 34, weight: .bold))
                                .foregroundColor(isLockedIn ? Color(hex: "059669") : Color(hex: "0E675E"))
                                .rotationEffect(.degrees(arrowAngle))
                        )
                        .scaleEffect(spotlightScale)
                }
                .frame(height: 200)
                
                // 3. Status Microcopy & Promise
                VStack(spacing: 8) {
                    if isLockedIn {
                        VStack(spacing: 4) {
                            Text("DELIVERING TO")
                                .font(.system(size: 11, weight: .heavy))
                                .foregroundColor(Color(hex: "64748B"))
                                .tracking(1.2)
                            
                            Text(displayLocality.uppercased())
                                .font(.system(size: 22, weight: .black))
                                .foregroundColor(Color(hex: "0F172A"))
                            
                            HStack(spacing: 5) {
                                Image(systemName: "bolt.fill")
                                    .font(.system(size: 11))
                                    .foregroundColor(Color(hex: "059669"))
                                Text("Express 10 Mins Delivery")
                                    .font(.system(size: 13, weight: .bold))
                                    .foregroundColor(Color(hex: "059669"))
                            }
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color(hex: "ECFDF5"))
                            .cornerRadius(12)
                        }
                        .transition(.opacity.combined(with: .scale(scale: 0.95)))
                    } else {
                        VStack(spacing: 4) {
                            Text("LOCATING YOUR ADDRESS...")
                                .font(.system(size: 12, weight: .bold))
                                .foregroundColor(Color(hex: "0E675E"))
                                .tracking(0.8)
                            
                            Text("Connecting to nearest fulfillment hub")
                                .font(.system(size: 13, weight: .medium))
                                .foregroundColor(Color(hex: "64748B"))
                        }
                        .transition(.opacity)
                    }
                }
                .animation(.easeInOut(duration: 0.3), value: isLockedIn)
                
                Spacer()
                
                // Bottom Quick Skip Hint
                Text("Tap anywhere to continue")
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundColor(Color(hex: "94A3B8"))
                    .padding(.bottom, 24)
            }
        }
        .contentShape(Rectangle())
        .onTapGesture {
            onDismiss()
        }
        .onAppear {
            // Pulse animation
            withAnimation(Animation.easeOut(duration: 0.9).repeatForever(autoreverses: false)) {
                radarScale = 1.6
                radarOpacity = 0.0
            }
            
            // Latch sequence
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.6) {
                withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) {
                    isLockedIn = true
                    spotlightScale = 1.05
                    arrowAngle = 0
                }
                
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.7) {
                    onDismiss()
                }
            }
        }
    }
}

// MARK: - Procedural Map Lines Background (1:1 with Android LocationLockinScreen.kt)
private struct ProceduralVectorMap: View {
    let isLockedIn: Bool
    
    var body: some View {
        Canvas { context, size in
            let w = size.width
            let h = size.height

            // 1. River curve in faint blue (stroke 38)
            var riverPath = Path()
            riverPath.move(to: CGPoint(x: 0, y: h * 0.72))
            riverPath.addCurve(
                to: CGPoint(x: w * 0.40, y: h * 0.95),
                control1: CGPoint(x: w * 0.35, y: h * 0.65),
                control2: CGPoint(x: w * 0.15, y: h * 0.90)
            )
            context.stroke(riverPath, with: .color(Color(hex: "E8F1FA")), lineWidth: 38)

            // 2. Arterial roads in faint sand / gold
            let roadColor = Color(hex: isLockedIn ? "F7E9CE" : "F1EDE4")
            var road1 = Path()
            road1.move(to: CGPoint(x: 0, y: h * 0.28))
            road1.addLine(to: CGPoint(x: w, y: h * 0.36))
            context.stroke(road1, with: .color(roadColor), lineWidth: 14)

            var road2 = Path()
            road2.move(to: CGPoint(x: w * 0.22, y: 0))
            road2.addLine(to: CGPoint(x: w * 0.45, y: h))
            context.stroke(road2, with: .color(roadColor), lineWidth: 16)

            var road3 = Path()
            road3.move(to: CGPoint(x: w * 0.85, y: 0))
            road3.addLine(to: CGPoint(x: w * 0.60, y: h))
            context.stroke(road3, with: .color(roadColor), lineWidth: 12)

            var road4 = Path()
            road4.move(to: CGPoint(x: 0, y: h * 0.52))
            road4.addLine(to: CGPoint(x: w, y: h * 0.46))
            context.stroke(road4, with: .color(roadColor), lineWidth: 20)

            // 3. Local connecting streets
            let streetColor = Color(hex: "F4EFE6")
            var st1 = Path()
            st1.move(to: CGPoint(x: w * 0.10, y: h * 0.10))
            st1.addLine(to: CGPoint(x: w * 0.90, y: h * 0.22))
            context.stroke(st1, with: .color(streetColor), lineWidth: 8)

            var st2 = Path()
            st2.move(to: CGPoint(x: w * 0.15, y: h * 0.40))
            st2.addLine(to: CGPoint(x: w * 0.80, y: h * 0.65))
            context.stroke(st2, with: .color(streetColor), lineWidth: 8)

            var st3 = Path()
            st3.move(to: CGPoint(x: w * 0.50, y: h * 0.30))
            st3.addLine(to: CGPoint(x: w * 0.30, y: h * 0.85))
            context.stroke(st3, with: .color(streetColor), lineWidth: 9)

            // 4. Junction nodes
            let nodeColor = Color(hex: "FEE2E2")
            context.fill(Path(ellipseIn: CGRect(x: w * 0.66 - 6, y: h * 0.44 - 6, width: 12, height: 12)), with: .color(nodeColor))
            context.fill(Path(ellipseIn: CGRect(x: w * 0.66 - 6, y: h * 0.85 - 6, width: 12, height: 12)), with: .color(nodeColor))
        }
    }
}
