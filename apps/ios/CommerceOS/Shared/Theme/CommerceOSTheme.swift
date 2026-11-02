import SwiftUI
import UIKit

// MARK: - Semantic Design Tokens & Colors
public struct CommerceOSTheme {
    public static let shared = CommerceOSTheme()
    
    public struct Colors {
        // Modern 2026 Healthcare Deep Teal & Mint Palette (matching Android CommerceColors)
        public static let brandPrimary = Color(hex: "0E675E")      // Healthcare Teal (#0E675E)
        public static let brandPrimaryDark = Color(hex: "0A4D46")  // Deep Teal Dark (#0A4D46)
        public static let brandPrimarySoft = Color(hex: "ECFDF5")  // Soft Mint Container (#ECFDF5)
        public static let brandSecondary = Color(hex: "0F172A")    // Midnight Slate (#0F172A)
        public static let brandAccent = Color(hex: "059669")       // Emerald SLA (#059669)
        public static let speedYellow = Color(hex: "E9BE3A")       // Rating / Speed Yellow (#E9BE3A)
        public static let sushiInk = Color(hex: "0F172A")          // Text Primary Slate (#0F172A)
        public static let homeGrid = Color(hex: "F4F6F8")          // Clean Healthcare Grid (#F4F6F8)
        public static let sushiGrey = Color(hex: "E2E8F0")         // Border Grey (#E2E8F0)
        
        public static let success = Color(hex: "059669")
        public static let warning = Color(hex: "D97706")
        public static let error = Color(hex: "E11D48")
        
        public static var background: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark
                    ? UIColor(red: 0.05, green: 0.07, blue: 0.10, alpha: 1.0)
                    : UIColor(red: 0.957, green: 0.965, blue: 0.973, alpha: 1.0) // #F4F6F8
            })
        }
        
        public static var surface: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark
                    ? UIColor(red: 0.09, green: 0.12, blue: 0.17, alpha: 1.0)
                    : UIColor.white
            })
        }
        
        public static var cardBackground: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark
                    ? UIColor(red: 0.12, green: 0.16, blue: 0.23, alpha: 1.0)
                    : UIColor.white
            })
        }
        
        public static var border: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark
                    ? UIColor(red: 0.20, green: 0.25, blue: 0.33, alpha: 1.0) // #334155
                    : UIColor(red: 0.89, green: 0.91, blue: 0.94, alpha: 1.0) // #E2E8F0
            })
        }
        
        public static var textPrimary: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark ? UIColor.white : UIColor(red: 0.11, green: 0.11, blue: 0.11, alpha: 1.0)
            })
        }
        
        public static var textSecondary: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark ? UIColor(white: 0.70, alpha: 1.0) : UIColor(red: 0.34, green: 0.35, blue: 0.35, alpha: 1.0)
            })
        }
        
        public static var textMuted: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark ? UIColor(white: 0.45, alpha: 1.0) : UIColor(red: 0.55, green: 0.56, blue: 0.59, alpha: 1.0)
            })
        }
    }
    
    // MARK: - High Ink-Density Typography (Matching Android Plus Jakarta Sans)
    public struct Typography {
        public static func displayLarge() -> Font {
            .custom("PlusJakartaSans-Regular", size: 28).weight(.heavy)
        }
        
        public static func heading() -> Font {
            .custom("PlusJakartaSans-Regular", size: 20).weight(.bold)
        }
        
        public static func titleMedium() -> Font {
            .custom("PlusJakartaSans-Regular", size: 17).weight(.bold)
        }
        
        public static func body() -> Font {
            .custom("PlusJakartaSans-Regular", size: 14).weight(.medium)
        }
        
        public static func bodyBold() -> Font {
            .custom("PlusJakartaSans-Regular", size: 14).weight(.bold)
        }
        
        public static func caption() -> Font {
            .custom("PlusJakartaSans-Regular", size: 12).weight(.semibold)
        }
        
        public static func meta() -> Font {
            .custom("PlusJakartaSans-Regular", size: 11).weight(.semibold)
        }
        
        public static func priceTag() -> Font {
            .system(size: 16, weight: .black, design: .rounded)
        }
    }
}

// MARK: - Native Haptics Engine
public final class HapticFeedbackEngine {
    public static let shared = HapticFeedbackEngine()
    
    private init() {}
    
    /// Triggers physical impact haptics
    public func triggerImpact(_ style: UIImpactFeedbackGenerator.FeedbackStyle = .medium) {
        DispatchQueue.main.async {
            let generator = UIImpactFeedbackGenerator(style: style)
            generator.prepare()
            generator.impactOccurred()
        }
    }
    
    /// Triggers notification haptics (success, warning, error)
    public func triggerNotification(_ type: UINotificationFeedbackGenerator.FeedbackType) {
        DispatchQueue.main.async {
            let generator = UINotificationFeedbackGenerator()
            generator.prepare()
            generator.notificationOccurred(type)
        }
    }
    
    /// Triggers subtle UI element selection clicks
    public func triggerSelection() {
        DispatchQueue.main.async {
            let generator = UISelectionFeedbackGenerator()
            generator.prepare()
            generator.selectionChanged()
        }
    }
}

// MARK: - SwiftUI View Helpers
public extension View {
    func commerceOSCardStyle() -> some View {
        self
            .padding()
            .background(CommerceOSTheme.Colors.cardBackground)
            .cornerRadius(14)
            .overlay(
                RoundedRectangle(cornerRadius: 14)
                    .stroke(CommerceOSTheme.Colors.border, lineWidth: 1)
            )
            .shadow(color: Color.black.opacity(0.04), radius: 6, x: 0, y: 3)
    }
    
    func withHapticTap(style: UIImpactFeedbackGenerator.FeedbackStyle = .medium, perform action: @escaping () -> Void) -> some View {
        Button(action: {
            HapticFeedbackEngine.shared.triggerImpact(style)
            action()
        }) {
            self
        }
        .buttonStyle(PlainButtonStyle())
    }
}
