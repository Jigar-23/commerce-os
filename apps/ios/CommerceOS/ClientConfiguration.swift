import Foundation
import SwiftUI

// MARK: - Domain & Environments
enum CommerceDomain: String, Codable {
    case generalCommerce = "general"
    case pharmacy = "pharmacy"
    case fashion = "fashion"
    case food = "food"
    case electronics = "electronics"
    case services = "services"
}

enum TenantEnvironment: String, Codable {
    case production = "PRODUCTION"
    case staging = "STAGING"
    case debug = "DEBUG"
}

// MARK: - Identity & Branding
struct ClientIdentity: Codable {
    let clientId: String
    let clientName: String
    let appName: String
    let logoUrl: String?
    let supportEmail: String
    let supportPhone: String
    let checkoutBranding: String
    let onboardingAsset: String
    let emptyStateAsset: String
    let errorStateAsset: String
}

struct ClientTheme: Codable {
    let primaryColorHex: String
    let secondaryColorHex: String
    let accentColorHex: String
    let backgroundColorHex: String
    let surfaceColorHex: String
    let textColorHex: String
    let successColorHex: String
    let warningColorHex: String
    let errorColorHex: String
    let ctaColorHex: String
    
    var primaryColor: Color { Color(hex: primaryColorHex) }
    var secondaryColor: Color { Color(hex: secondaryColorHex) }
    var accentColor: Color { Color(hex: accentColorHex) }
    var backgroundColor: Color { Color(hex: backgroundColorHex) }
    var surfaceColor: Color { Color(hex: surfaceColorHex) }
    var textColor: Color { Color(hex: textColorHex) }
    var successColor: Color { Color(hex: successColorHex) }
    var warningColor: Color { Color(hex: warningColorHex) }
    var errorColor: Color { Color(hex: errorColorHex) }
    var ctaColor: Color { Color(hex: ctaColorHex) }
}

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

// MARK: - Terminology
struct ClientTerminology: Codable {
    let cartLabel: String
    let wishlistLabel: String
    let checkoutLabel: String
    let searchPlaceholder: String
    let reorderLabel: String
    let orderTrackingTitle: String
    let bookingLabel: String
    let serviceLabel: String
    let prescriptionLabel: String
    let favoritesLabel: String
    let primaryCtaLabel: String
}

// MARK: - Feature Flags
struct ClientFeatureConfiguration: Codable {
    let enableWishlist: Bool
    let enablePrescriptionUpload: Bool
    let enableProductComparison: Bool
    let enableReorder: Bool
    let enableVoiceSearch: Bool
    let enableCameraSearch: Bool
    let enableBarcodeSearch: Bool
    let enableStoreLocationPicker: Bool
    let enableReviews: Bool
    let enableCoupons: Bool
    let enableServiceBooking: Bool
    let enableSubscriptions: Bool
}

// MARK: - Home Configuration
enum HomeSectionType: String, Codable {
    case hero = "hero"
    case editorial = "editorial"
    case dealGrid = "deal_grid"
    case categoryGrid = "category_grid"
    case brandShelf = "brand_shelf"
    case productShelf = "product_shelf"
    case restaurantShelf = "restaurant_shelf"
    case serviceShelf = "service_shelf"
    case reorder = "reorder"
    case collection = "collection"
}

struct HomeSectionConfig: Codable, Identifiable {
    var id: String { type.rawValue }
    let type: HomeSectionType
    let title: String
    let subtitle: String?
    let order: Int
    let isVisible: Bool
}

struct ClientHomeConfiguration: Codable {
    let sections: [HomeSectionConfig]
    let heroBannerTitle: String
    let heroBannerSubtitle: String
    let heroCtaText: String
}

// MARK: - Master Client Configuration
struct ClientConfiguration: Codable, Identifiable {
    var id: String { identity.clientId }
    let identity: ClientIdentity
    let domain: CommerceDomain
    let theme: ClientTheme
    let terminology: ClientTerminology
    let features: ClientFeatureConfiguration
    let home: ClientHomeConfiguration

    // MARK: - Built-in Profiles
    static let defaultGeneric = ClientConfiguration(
        identity: ClientIdentity(
            clientId: "generic_os",
            clientName: "Commerce OS Generic",
            appName: "Commerce OS",
            logoUrl: nil,
            supportEmail: "support@commerceos.io",
            supportPhone: "+1-800-COMMERCE",
            checkoutBranding: "Powered by Commerce OS",
            onboardingAsset: "generic_onboarding",
            emptyStateAsset: "generic_empty",
            errorStateAsset: "generic_error"
        ),
        domain: .generalCommerce,
        theme: ClientTheme(
            primaryColorHex: "#0066FF",
            secondaryColorHex: "#475569",
            accentColorHex: "#F59E0B",
            backgroundColorHex: "#F8FAFC",
            surfaceColorHex: "#FFFFFF",
            textColorHex: "#0F172A",
            successColorHex: "#10B981",
            warningColorHex: "#F59E0B",
            errorColorHex: "#EF4444",
            ctaColorHex: "#0066FF"
        ),
        terminology: ClientTerminology(
            cartLabel: "Cart",
            wishlistLabel: "Saved Items",
            checkoutLabel: "Checkout",
            searchPlaceholder: "Search catalog, brands & products...",
            reorderLabel: "Buy Again",
            orderTrackingTitle: "Order Tracker",
            bookingLabel: "Book Now",
            serviceLabel: "Services",
            prescriptionLabel: "Prescriptions",
            favoritesLabel: "Favorites",
            primaryCtaLabel: "Add to Cart"
        ),
        features: ClientFeatureConfiguration(
            enableWishlist: true,
            enablePrescriptionUpload: false,
            enableProductComparison: true,
            enableReorder: true,
            enableVoiceSearch: true,
            enableCameraSearch: true,
            enableBarcodeSearch: true,
            enableStoreLocationPicker: true,
            enableReviews: true,
            enableCoupons: true,
            enableServiceBooking: false,
            enableSubscriptions: true
        ),
        home: ClientHomeConfiguration(
            sections: [
                HomeSectionConfig(type: .hero, title: "Welcome to Commerce OS", subtitle: "Multi-vertical commerce platform", order: 1, isVisible: true),
                HomeSectionConfig(type: .categoryGrid, title: "Explore Categories", subtitle: "Browse catalog taxonomy", order: 2, isVisible: true),
                HomeSectionConfig(type: .productShelf, title: "Trending Products", subtitle: "Top rated items near you", order: 3, isVisible: true),
                HomeSectionConfig(type: .brandShelf, title: "Popular Brands", subtitle: "Official brand stores", order: 4, isVisible: true),
                HomeSectionConfig(type: .dealGrid, title: "Special Offers", subtitle: "Daily deals & discounts", order: 5, isVisible: true)
            ],
            heroBannerTitle: "Next-Gen Unified Commerce",
            heroBannerSubtitle: "Deliver rapid 10-minute SLAs & rich multi-vertical catalogs.",
            heroCtaText: "Shop Now"
        )
    )

    static let pharmacyClient = ClientConfiguration(
        identity: ClientIdentity(
            clientId: "rx_pharma",
            clientName: "RxCare Pharmacy",
            appName: "RxCare Health",
            logoUrl: nil,
            supportEmail: "rx@carehealth.com",
            supportPhone: "+1-800-RX-CARE",
            checkoutBranding: "Verified Licensed Pharmacy Network",
            onboardingAsset: "pharma_onboarding",
            emptyStateAsset: "pharma_empty",
            errorStateAsset: "pharma_error"
        ),
        domain: .pharmacy,
        theme: ClientTheme(
            primaryColorHex: "#059669",
            secondaryColorHex: "#0284C7",
            accentColorHex: "#10B981",
            backgroundColorHex: "#F0FDF4",
            surfaceColorHex: "#FFFFFF",
            textColorHex: "#064E3B",
            successColorHex: "#059669",
            warningColorHex: "#D97706",
            errorColorHex: "#DC2626",
            ctaColorHex: "#059669"
        ),
        terminology: ClientTerminology(
            cartLabel: "Medicine Box",
            wishlistLabel: "Rx Watchlist",
            checkoutLabel: "Place Medicine Order",
            searchPlaceholder: "Search medicines, salt composition & health products...",
            reorderLabel: "Refill Prescription",
            orderTrackingTitle: "Medicine Delivery Status",
            bookingLabel: "Consult Pharmacist",
            serviceLabel: "Lab Tests",
            prescriptionLabel: "Upload Rx",
            favoritesLabel: "My Medicines",
            primaryCtaLabel: "Add to Rx Box"
        ),
        features: ClientFeatureConfiguration(
            enableWishlist: true,
            enablePrescriptionUpload: true,
            enableProductComparison: false,
            enableReorder: true,
            enableVoiceSearch: true,
            enableCameraSearch: true,
            enableBarcodeSearch: true,
            enableStoreLocationPicker: true,
            enableReviews: true,
            enableCoupons: true,
            enableServiceBooking: true,
            enableSubscriptions: true
        ),
        home: ClientHomeConfiguration(
            sections: [
                HomeSectionConfig(type: .hero, title: "10-Min Fast Rx Delivery", subtitle: "Licensed pharmacies in your city", order: 1, isVisible: true),
                HomeSectionConfig(type: .categoryGrid, title: "Health Categories", subtitle: "Diabetes, Cardiac, General Wellness", order: 2, isVisible: true),
                HomeSectionConfig(type: .productShelf, title: "Essential Medicines", subtitle: "Verified OTC & Prescription drugs", order: 3, isVisible: true),
                HomeSectionConfig(type: .dealGrid, title: "Wellness Savings", subtitle: "Up to 30% off monthly refills", order: 4, isVisible: true)
            ],
            heroBannerTitle: "Upload Prescription & Get 20% OFF",
            heroBannerSubtitle: "Our licensed pharmacists verify and fulfill in 15 mins.",
            heroCtaText: "Upload Rx Now"
        )
    )

    static let foodClient = ClientConfiguration(
        identity: ClientIdentity(
            clientId: "bites_food",
            clientName: "Bites Express",
            appName: "Bites Food & Grocery",
            logoUrl: nil,
            supportEmail: "help@bitesexpress.com",
            supportPhone: "+1-888-BITES-GO",
            checkoutBranding: "Hot & Fresh Instant Delivery",
            onboardingAsset: "food_onboarding",
            emptyStateAsset: "food_empty",
            errorStateAsset: "food_error"
        ),
        domain: .food,
        theme: ClientTheme(
            primaryColorHex: "#EA580C",
            secondaryColorHex: "#F97316",
            accentColorHex: "#EAB308",
            backgroundColorHex: "#FFFBEB",
            surfaceColorHex: "#FFFFFF",
            textColorHex: "#451A03",
            successColorHex: "#16A34A",
            warningColorHex: "#CA8A04",
            errorColorHex: "#DC2626",
            ctaColorHex: "#EA580C"
        ),
        terminology: ClientTerminology(
            cartLabel: "Food Bag",
            wishlistLabel: "Saved Dishes",
            checkoutLabel: "Place Food Order",
            searchPlaceholder: "Search restaurants, dishes, cuisines & snacks...",
            reorderLabel: "Reorder Meal",
            orderTrackingTitle: "Driver Live Tracking",
            bookingLabel: "Reserve Table",
            serviceLabel: "Catering Services",
            prescriptionLabel: "Special Instructions",
            favoritesLabel: "Favorite Bites",
            primaryCtaLabel: "Add to Food Bag"
        ),
        features: ClientFeatureConfiguration(
            enableWishlist: true,
            enablePrescriptionUpload: false,
            enableProductComparison: false,
            enableReorder: true,
            enableVoiceSearch: true,
            enableCameraSearch: true,
            enableBarcodeSearch: false,
            enableStoreLocationPicker: true,
            enableReviews: true,
            enableCoupons: true,
            enableServiceBooking: true,
            enableSubscriptions: false
        ),
        home: ClientHomeConfiguration(
            sections: [
                HomeSectionConfig(type: .hero, title: "Superfast Meal Delivery", subtitle: "Pipining hot food from top kitchens", order: 1, isVisible: true),
                HomeSectionConfig(type: .restaurantShelf, title: "Top Rated Kitchens", subtitle: "Curated gourmet & street food", order: 2, isVisible: true),
                HomeSectionConfig(type: .categoryGrid, title: "Popular Cuisines", subtitle: "Italian, Asian, Burgers, Desserts", order: 3, isVisible: true),
                HomeSectionConfig(type: .dealGrid, title: "BOGO Deals", subtitle: "Buy 1 Get 1 Free offers", order: 4, isVisible: true)
            ],
            heroBannerTitle: "Free Delivery on First 3 Orders",
            heroBannerSubtitle: "Discover top-rated restaurants near your location.",
            heroCtaText: "Order Food"
        )
    )

    static let fashionClient = ClientConfiguration(
        identity: ClientIdentity(
            clientId: "vogue_fashion",
            clientName: "VogueFit Fashion",
            appName: "VogueFit",
            logoUrl: nil,
            supportEmail: "style@voguefit.com",
            supportPhone: "+1-800-VOGUE-FIT",
            checkoutBranding: "Official Designer & Retail Network",
            onboardingAsset: "fashion_onboarding",
            emptyStateAsset: "fashion_empty",
            errorStateAsset: "fashion_error"
        ),
        domain: .fashion,
        theme: ClientTheme(
            primaryColorHex: "#7C3AED",
            secondaryColorHex: "#DB2777",
            accentColorHex: "#F43F5E",
            backgroundColorHex: "#FAF5FF",
            surfaceColorHex: "#FFFFFF",
            textColorHex: "#3B0764",
            successColorHex: "#10B981",
            warningColorHex: "#F59E0B",
            errorColorHex: "#E11D48",
            ctaColorHex: "#7C3AED"
        ),
        terminology: ClientTerminology(
            cartLabel: "Shopping Bag",
            wishlistLabel: "Wishlist",
            checkoutLabel: "Proceed to Checkout",
            searchPlaceholder: "Search apparel, sneakers, watches & designers...",
            reorderLabel: "Buy Again",
            orderTrackingTitle: "Shipment Tracking",
            bookingLabel: "Book Stylist",
            serviceLabel: "Tailoring & Alterations",
            prescriptionLabel: "Custom Size Specs",
            favoritesLabel: "Style Favs",
            primaryCtaLabel: "Add to Bag"
        ),
        features: ClientFeatureConfiguration(
            enableWishlist: true,
            enablePrescriptionUpload: false,
            enableProductComparison: true,
            enableReorder: true,
            enableVoiceSearch: true,
            enableCameraSearch: true,
            enableBarcodeSearch: true,
            enableStoreLocationPicker: true,
            enableReviews: true,
            enableCoupons: true,
            enableServiceBooking: true,
            enableSubscriptions: false
        ),
        home: ClientHomeConfiguration(
            sections: [
                HomeSectionConfig(type: .hero, title: "Autumn/Winter Trends 2026", subtitle: "Exclusive designer collection drop", order: 1, isVisible: true),
                HomeSectionConfig(type: .brandShelf, title: "Luxury Houses", subtitle: "Gucci, Prada, Nike, Adidas", order: 2, isVisible: true),
                HomeSectionConfig(type: .categoryGrid, title: "Shop by Category", subtitle: "Apparel, Footwear, Accessories", order: 3, isVisible: true),
                HomeSectionConfig(type: .productShelf, title: "New Arrivals", subtitle: "Freshly curated style drops", order: 4, isVisible: true)
            ],
            heroBannerTitle: "End of Season Sale — Up to 50% OFF",
            heroBannerSubtitle: "Shop international luxury & urban streetwear brands.",
            heroCtaText: "Explore Lookbook"
        )
    )

    static let servicesClient = ClientConfiguration(
        identity: ClientIdentity(
            clientId: "pro_services",
            clientName: "ProHandy Services",
            appName: "ProHandy On-Demand",
            logoUrl: nil,
            supportEmail: "help@prohandy.com",
            supportPhone: "+1-800-PRO-HANDY",
            checkoutBranding: "Verified Background-Checked Professionals",
            onboardingAsset: "services_onboarding",
            emptyStateAsset: "services_empty",
            errorStateAsset: "services_error"
        ),
        domain: .services,
        theme: ClientTheme(
            primaryColorHex: "#0284C7",
            secondaryColorHex: "#0F766E",
            accentColorHex: "#06B6D4",
            backgroundColorHex: "#F0F9FF",
            surfaceColorHex: "#FFFFFF",
            textColorHex: "#0C4A6E",
            successColorHex: "#10B981",
            warningColorHex: "#F59E0B",
            errorColorHex: "#EF4444",
            ctaColorHex: "#0284C7"
        ),
        terminology: ClientTerminology(
            cartLabel: "Service Cart",
            wishlistLabel: "Saved Services",
            checkoutLabel: "Confirm Booking",
            searchPlaceholder: "Search home repair, cleaning, salon & experts...",
            reorderLabel: "Book Again",
            orderTrackingTitle: "Pro Live Location",
            bookingLabel: "Schedule Appointment",
            serviceLabel: "Expert Services",
            prescriptionLabel: "Service Instructions",
            favoritesLabel: "Preferred Pros",
            primaryCtaLabel: "Book Slot"
        ),
        features: ClientFeatureConfiguration(
            enableWishlist: true,
            enablePrescriptionUpload: false,
            enableProductComparison: true,
            enableReorder: true,
            enableVoiceSearch: true,
            enableCameraSearch: true,
            enableBarcodeSearch: false,
            enableStoreLocationPicker: true,
            enableReviews: true,
            enableCoupons: true,
            enableServiceBooking: true,
            enableSubscriptions: true
        ),
        home: ClientHomeConfiguration(
            sections: [
                HomeSectionConfig(type: .hero, title: "Verified On-Demand Experts", subtitle: "Plumbing, Electrical, AC Repair & Cleaning", order: 1, isVisible: true),
                HomeSectionConfig(type: .serviceShelf, title: "Popular Home Services", subtitle: "Fixed upfront pricing", order: 2, isVisible: true),
                HomeSectionConfig(type: .categoryGrid, title: "Service Categories", subtitle: "Home, Beauty, Appliance Repair", order: 3, isVisible: true),
                HomeSectionConfig(type: .dealGrid, title: "Service Bundles", subtitle: "Save up to $40 on combo packages", order: 4, isVisible: true)
            ],
            heroBannerTitle: "Book Professional AC Service Today",
            heroBannerSubtitle: "30-day post-service warranty on all repairs.",
            heroCtaText: "Select Time Slot"
        )
    )
}

// MARK: - Runtime Client Config Provider
@MainActor
final class ClientConfigProvider: ObservableObject {
    static let shared = ClientConfigProvider()

    @Published private(set) var currentConfig: ClientConfiguration = .defaultGeneric

    private init() {}

    func switchProfile(_ config: ClientConfiguration) {
        self.currentConfig = config
    }
}
