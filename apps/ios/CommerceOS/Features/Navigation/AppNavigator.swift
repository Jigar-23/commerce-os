import SwiftUI

/**
 * 1:1 Parity with Android Screen.kt & CommerceOSApp.kt Navigation Stack.
 * Centralized, authoritative navigation engine for the iOS application.
 * Manages full-screen push/pop transitions and bottom bar primary tab coordination.
 */
public enum AppScreen: Identifiable, Equatable {
    case notifications
    case search(query: String)
    case catalog(query: String)
    case productDetail(product: ProductDto)
    case cart
    case orderTracking(orderId: String)
    case prescriptions
    case addressSelection(fromCheckout: Bool, fromProfile: Bool)
    case addAddressFlow
    case paymentGateway(quote: AuthoritativeCartQuote?, couponCode: String?)

    public static var paymentGateway: AppScreen {
        .paymentGateway(quote: CartLocalStore.shared.authoritativeQuote, couponCode: CartLocalStore.shared.appliedCouponCode)
    }

    public var id: String {
        switch self {
        case .notifications: return "notifications"
        case .search(let q): return "search_\(q)"
        case .catalog(let q): return "catalog_\(q)"
        case .productDetail(let p): return "product_\(p.id)"
        case .cart: return "cart"
        case .orderTracking(let id): return "tracking_\(id)"
        case .prescriptions: return "prescriptions"
        case .addressSelection(let c, let p): return "address_c\(c)_p\(p)"
        case .addAddressFlow: return "add_address_flow"
        case .paymentGateway(let quote, let couponCode):
            return "payment_gateway_\(couponCode ?? "")_\(quote?.effectiveGrandTotal ?? 0)"
        }
    }

    public static func == (lhs: AppScreen, rhs: AppScreen) -> Bool {
        lhs.id == rhs.id
    }
}

@MainActor
public final class AppNavigator: ObservableObject {
    public static let shared = AppNavigator()

    @Published public var selectedTab: Int = 0 // 0: Home, 1: Categories, 2: Orders, 3: Account
    @Published public var screenStack: [AppScreen] = []

    public init() {}

    public var currentScreen: AppScreen? {
        screenStack.last
    }

    public func push(_ screen: AppScreen) {
        withAnimation(.easeInOut(duration: 0.22)) {
            screenStack.append(screen)
        }
    }

    public func pop() {
        withAnimation(.easeInOut(duration: 0.2)) {
            if !screenStack.isEmpty {
                _ = screenStack.removeLast()
            }
        }
    }

    public func popToRoot() {
        withAnimation(.easeInOut(duration: 0.2)) {
            screenStack.removeAll()
        }
    }

    public func switchTab(to index: Int) {
        withAnimation(.easeInOut(duration: 0.16)) {
            screenStack.removeAll()
            selectedTab = index
        }
    }
}
