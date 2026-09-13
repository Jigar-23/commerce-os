import SwiftUI
import UIKit

// MARK: - Semantic Design Tokens & Colors
public struct CommerceOSTheme {
    public static let shared = CommerceOSTheme()
    
    public struct Colors {
        // Quick-Commerce Emerald Vitality Palette (matching Android CommerceColors)
        public static let brandPrimary = Color(hex: "0DA314")      // QuickGreen (#0DA314)
        public static let brandPrimaryDark = Color(hex: "0C831F")  // QuickGreenDark (#0C831F)
        public static let brandPrimarySoft = Color(hex: "EAF8E8")  // Soft Green Pill (#EAF8E8)
        public static let brandSecondary = Color(hex: "0B132B")    // Midnight Navy (#0B132B)
        public static let brandAccent = Color(hex: "0DA314")       // QuickGreen SLA (#0DA314)
        public static let speedYellow = Color(hex: "E9BE3A")       // Rating / Speed Yellow (#E9BE3A)
        public static let sushiInk = Color(hex: "1C1C1C")          // Text Primary Ink (#1C1C1C)
        public static let homeGrid = Color(hex: "F4F5F7")          // Quick-Commerce Background Grid
        public static let sushiGrey = Color(hex: "EFEFEF")         // Sushi Grey
        
        public static let success = Color(hex: "0DA314")
        public static let warning = Color(hex: "E9BE3A")
        public static let error = Color(hex: "E11D48")
        
        public static var background: Color {
            Color(UIColor { trait in
                trait.userInterfaceStyle == .dark
                    ? UIColor(red: 0.05, green: 0.07, blue: 0.10, alpha: 1.0)
                    : UIColor(red: 0.957, green: 0.961, blue: 0.969, alpha: 1.0) // #F4F5F7
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
    
    // MARK: - Dynamic Type Typography
    public struct Typography {
        public static func displayLarge() -> Font {
            .system(size: 28, weight: .black, design: .default)
        }
        
        public static func titleMedium() -> Font {
            .system(size: 18, weight: .bold, design: .default)
        }
        
        public static func body() -> Font {
            .system(size: 14, weight: .regular, design: .default)
        }
        
        public static func bodyBold() -> Font {
            .system(size: 14, weight: .semibold, design: .default)
        }
        
        public static func caption() -> Font {
            .system(size: 11, weight: .medium, design: .default)
        }
        
        public static func priceTag() -> Font {
            .system(size: 15, weight: .black, design: .rounded)
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
