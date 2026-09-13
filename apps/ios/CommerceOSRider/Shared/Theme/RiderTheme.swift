import SwiftUI
import UIKit

extension Color {
    init(hex: String) {
        let cleanHex = hex.trimmingCharacters(in: CharacterSet.alphanumerics.inverted)
        var int: UInt64 = 0
        Scanner(string: cleanHex).scanHexInt64(&int)
        let a, r, g, b: UInt64
        switch cleanHex.count {
        case 3:
            (a, r, g, b) = (255, (int >> 8 * 17), (int >> 4 & 0xF) * 17, (int & 0xF) * 17)
        case 6:
            (a, r, g, b) = (255, int >> 16, int >> 8 & 0xFF, int & 0xFF)
        case 8:
            (a, r, g, b) = (int >> 24, int >> 16 & 0xFF, int >> 8 & 0xFF, int & 0xFF)
        default:
            (a, r, g, b) = (255, 0, 0, 0)
        }
        self.init(
            .sRGB,
            red: Double(r) / 255,
            green: Double(g) / 255,
            blue: Double(b) / 255,
            opacity: Double(a) / 255
        )
    }
}

// MARK: - High-Visibility Night Riding HUD Tokens
public struct RiderTheme {
    public static let shared = RiderTheme()
    
    public struct Colors {
        // High-Contrast Night HUD Surfaces matching Android RiderColors (#0F172A / #1E293B)
        public static let hudBackground = Color(hex: "0F172A") // Midnight Navy / Slate 900
        public static let hudSurface = Color(hex: "1E293B")    // Slate 800
        public static let hudCard = Color(hex: "1E293B")       // Slate 800
        public static let hudBorder = Color(hex: "334155")     // Slate 700
        
        // High-Visibility Safety Accents matching Android RiderColors
        public static let safetyGreen = Color(hex: "10B981")   // Emerald 500 (#10B981)
        public static let primaryDark = Color(hex: "0B132B")   // Midnight Navy (#0B132B)
        public static let speedAccent = Color(hex: "4F46E5")   // Electric Indigo SLA (#4F46E5)
        public static let safetyYellow = Color(hex: "F59E0B")  // Caution Amber (#F59E0B)
        public static let urgentRed = Color(hex: "E11D48")     // Emergency Dispatch Red (#E11D48)
        public static let metricCyan = Color(hex: "06B6D4")    // Cyan Telemetry (#06B6D4)
        
        public static let textPrimary = Color.white
        public static let textSecondary = Color(hex: "94A3B8") // Slate 400
        public static let textMuted = Color(hex: "64748B")     // Slate 500
    }
    
    // MARK: - Handlebar-Mount High-Legibility Typography
    public struct Typography {
        public static func speedometer() -> Font {
            .system(size: 42, weight: .black, design: .monospaced)
        }
        
        public static func turnHeader() -> Font {
            .system(size: 22, weight: .black, design: .default)
        }
        
        public static func offerPayout() -> Font {
            .system(size: 26, weight: .black, design: .rounded)
        }
        
        public static func metricValue() -> Font {
            .system(size: 16, weight: .heavy, design: .monospaced)
        }
        
        public static func metricLabel() -> Font {
            .system(size: 10, weight: .black, design: .default)
        }
    }
}

// MARK: - Rider Multi-Pulse Haptic Engine
public final class RiderHapticEngine {
    public static let shared = RiderHapticEngine()
    
    private let impactHeavy = UIImpactFeedbackGenerator(style: .heavy)
    private let impactMedium = UIImpactFeedbackGenerator(style: .medium)
    private let notificationGenerator = UINotificationFeedbackGenerator()
    
    private init() {
        impactHeavy.prepare()
        impactMedium.prepare()
        notificationGenerator.prepare()
    }
    
    /// Multi-pulse aggressive haptic burst for incoming 30s dispatch offers
    public func playIncomingOfferBurst() {
        DispatchQueue.main.async {
            self.impactHeavy.impactOccurred(intensity: 1.0)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                self.impactHeavy.impactOccurred(intensity: 1.0)
                DispatchQueue.main.asyncAfter(deadline: .now() + 0.15) {
                    self.notificationGenerator.notificationOccurred(.warning)
                }
            }
        }
    }
    
    /// Turn maneuver warning pulse
    public func playManeuverPulse() {
        DispatchQueue.main.async {
            self.impactMedium.impactOccurred(intensity: 0.8)
        }
    }
    
    /// 50m geofence arrival tactile confirmation
    public func playGeofenceArrivalRumble() {
        DispatchQueue.main.async {
            self.impactHeavy.impactOccurred(intensity: 1.0)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.12) {
                self.notificationGenerator.notificationOccurred(.success)
            }
        }
    }
    
    /// OTP verification delivery completion sequence
    public func playDeliveryConfirmedSequence() {
        DispatchQueue.main.async {
            self.notificationGenerator.notificationOccurred(.success)
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.18) {
                self.impactHeavy.impactOccurred(intensity: 0.9)
            }
        }
    }
}

// MARK: - SwiftUI Rider Modifiers
public extension View {
    func riderHUDCardStyle() -> some View {
        self
            .padding()
            .background(RiderTheme.Colors.hudCard)
            .cornerRadius(16)
            .overlay(
                RoundedRectangle(cornerRadius: 16)
                    .stroke(RiderTheme.Colors.hudBorder, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.4), radius: 8, x: 0, y: 4)
    }
}
