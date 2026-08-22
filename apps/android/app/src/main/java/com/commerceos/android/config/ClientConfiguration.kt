package com.commerceos.android.config

import com.commerceos.android.model.CategoryGroup
import com.commerceos.android.model.HomeSectionType
import com.commerceos.android.registry.CardVariant

/**
 * Domain Vertical Profile for the Client Configuration Engine.
 */
enum class CommerceDomain {
    GENERAL_COMMERCE,
    FOOD,
    FASHION,
    ELECTRONICS,
    PHARMACY,
    SERVICES
}

/**
 * Single source of truth for Client Behavior, Identity, Theme, Terminology, Features, and Layout.
 */
data class ClientConfiguration(
    val identity: ClientIdentity,
    val theme: ClientTheme,
    val domain: CommerceDomain,
    val terminology: TerminologyConfiguration = TerminologyConfiguration(),
    val features: ClientFeatureConfiguration = ClientFeatureConfiguration(),
    val homeConfig: ClientHomeConfiguration = ClientHomeConfiguration(),
    val workflowConfig: ClientWorkflowConfiguration = ClientWorkflowConfiguration(),
    val taxonomyConfig: ClientTaxonomyConfiguration = ClientTaxonomyConfiguration(),
    val searchConfig: ClientSearchConfiguration = ClientSearchConfiguration(),
    val version: Int = 1,
    val schemaVersion: String = "1.0",
    val configHash: String = "",
    val defaultTaxonomy: List<CategoryGroup> = emptyList(),
    val enabledHomeSections: List<HomeSectionType> = listOf(
        HomeSectionType.HERO_CAMPAIGN,
        HomeSectionType.CATEGORY_GRID,
        HomeSectionType.DEAL_GRID,
        HomeSectionType.RECOMMENDED_FEED,
    )
) {
    val tenantId: String get() = identity.clientId

    companion object {
        val DefaultGeneric = ClientConfiguration(
            identity = ClientIdentity(
                clientId = "generic_os",
                clientName = "Commerce OS",
                appName = "Commerce OS Store",
                logoUrl = "https://assets.commerceos.io/brands/generic_logo.png",
                supportEmail = "support@commerceos.io",
                supportPhone = "+1-800-555-0100",
                splashTitle = "Commerce OS",
                splashTagline = "Next-Gen Multi-Tenant E-Commerce Platform"
            ),
            theme = ClientTheme(
                primaryColorHex = "#16A34A",
                secondaryColorHex = "#0B132B",
                accentColorHex = "#4F46E5",
                backgroundColorHex = "#F4F5F7",
                surfaceColorHex = "#FFFFFF",
                textColorHex = "#1C1C1C",
                successColorHex = "#16A34A",
                warningColorHex = "#E9BE3A",
                errorColorHex = "#E11D48",
                discountColorHex = "#16A34A",
                badgeColorHex = "#16A34A",
                ctaColorHex = "#16A34A",
                deliveryColorHex = "#16A34A",
                verticalAccentColorHex = "#4F46E5"
            ),
            domain = CommerceDomain.GENERAL_COMMERCE,
            terminology = TerminologyConfiguration(
                cartLabel = "Cart",
                wishlistLabel = "Saved",
                checkoutLabel = "Place Order",
                searchPlaceholder = "Search products, brands and categories...",
                reorderLabel = "Buy Again",
                orderLabel = "Orders",
                productCtaLabel = "Add to Cart"
            ),
            features = ClientFeatureConfiguration(
                enableWishlist = true,
                enablePrescriptionUpload = false,
                enableProductComparison = true,
                enableReorder = true,
                enableVoiceSearch = true,
                enableCameraSearch = true,
                enableBarcodeSearch = true,
                enableStoreLocationPicker = true,
                enableServiceBooking = false,
                enableReviews = true,
                enableCoupons = true
            ),
            homeConfig = ClientHomeConfiguration(
                defaultCardVariant = CardVariant.GENERIC_PRODUCT
            ),
            version = 1
        )

        val PharmacyClient = ClientConfiguration(
            identity = ClientIdentity(
                clientId = "rx_pharma",
                clientName = "MediCare Express",
                appName = "MediCare Rx Store",
                logoUrl = "https://assets.commerceos.io/brands/medicare_logo.png",
                supportEmail = "rx-support@medicareexpress.com",
                supportPhone = "+1-800-555-0199",
                splashTitle = "MediCare Express",
                splashTagline = "Your Trusted Licensed Pharmacy Partner",
                checkoutBranding = ClientCheckoutBranding(
                    trustBadgeText = "Licensed Pharmacist Verified",
                    guaranteeText = "100% Genuine Medicines & Cold-Chain Fulfillment",
                    orderConfirmationNote = "Your prescription is being reviewed by a certified pharmacist."
                )
            ),
            theme = ClientTheme(
                primaryColorHex = "#00897B",
                secondaryColorHex = "#004D40",
                backgroundColorHex = "#F2F9F9",
                surfaceColorHex = "#FFFFFF",
                textColorHex = "#1A1A1A",
                accentColorHex = "#26A69A",
                ctaColorHex = "#00897B",
                deliveryColorHex = "#00897B"
            ),
            domain = CommerceDomain.PHARMACY,
            terminology = TerminologyConfiguration(
                cartLabel = "Health Basket",
                wishlistLabel = "Saved Medicines",
                searchPlaceholder = "Search medicines, healthcare & Rx products...",
                checkoutLabel = "Proceed to Checkout",
                reorderLabel = "Reorder",
                orderLabel = "My Orders",
                prescriptionLabel = "Prescriptions",
                productCtaLabel = "Add to Basket"
            ),
            features = ClientFeatureConfiguration(
                enablePrescriptionUpload = true,
                enableWishlist = true,
                enableReorder = true,
                enableProductComparison = false,
                enableVoiceSearch = true,
                enableCameraSearch = true,
                enableStoreLocationPicker = true,
                enableCoupons = true
            ),
            homeConfig = ClientHomeConfiguration(
                defaultCardVariant = CardVariant.PHARMACY_PRODUCT,
                sectionTitles = mapOf(
                    HomeSectionType.HERO_CAMPAIGN to "Healthcare Essentials",
                    HomeSectionType.CATEGORY_GRID to "Browse by Health Condition",
                    HomeSectionType.DEAL_GRID to "Wellness & Immunity Offers"
                )
            ),
            workflowConfig = ClientWorkflowConfiguration(
                requiresRxValidation = true,
                customStepName = "Prescription Review"
            ),
            version = 1
        )

        val FashionClient = ClientConfiguration(
            identity = ClientIdentity(
                clientId = "fashion_luxe",
                clientName = "Vogue OS",
                appName = "Vogue Apparel Hub",
                logoUrl = "https://assets.commerceos.io/brands/vogue_logo.png",
                supportEmail = "concierge@vogueapparel.com",
                supportPhone = "+1-800-555-0188",
                splashTitle = "Vogue OS",
                splashTagline = "Curated Fashion & Designer Apparel"
            ),
            theme = ClientTheme(
                primaryColorHex = "#D81B60",
                secondaryColorHex = "#880E4F",
                backgroundColorHex = "#FAF4F6",
                surfaceColorHex = "#FFFFFF",
                textColorHex = "#1A1A1A",
                accentColorHex = "#FF4081",
                ctaColorHex = "#D81B60"
            ),
            domain = CommerceDomain.FASHION,
            terminology = TerminologyConfiguration(
                cartLabel = "Shopping Bag",
                wishlistLabel = "Favorites",
                searchPlaceholder = "Search styles, designers and apparel...",
                checkoutLabel = "Proceed to Checkout",
                reorderLabel = "Buy Again",
                orderLabel = "Apparel Orders",
                productCtaLabel = "Add to Bag"
            ),
            features = ClientFeatureConfiguration(
                enableWishlist = true,
                enableProductComparison = false,
                enablePrescriptionUpload = false,
                enableReorder = true,
                enableVoiceSearch = true,
                enableCameraSearch = true,
                enableCoupons = true
            ),
            homeConfig = ClientHomeConfiguration(
                defaultCardVariant = CardVariant.FASHION_PRODUCT,
                sectionTitles = mapOf(
                    HomeSectionType.HERO_CAMPAIGN to "New Season Arrivals",
                    HomeSectionType.CATEGORY_GRID to "Explore Collections",
                    HomeSectionType.DEAL_GRID to "Exclusive Runway Discounts"
                )
            ),
            version = 1
        )

        val FoodClient = ClientConfiguration(
            identity = ClientIdentity(
                clientId = "food_bistro",
                clientName = "Gourmet OS",
                appName = "Gourmet Kitchens",
                logoUrl = "https://assets.commerceos.io/brands/gourmet_logo.png",
                supportEmail = "support@gourmetkitchens.com",
                supportPhone = "+1-800-555-0177",
                splashTitle = "Gourmet OS",
                splashTagline = "Fresh Meals & Artisanal Dining Delivered"
            ),
            theme = ClientTheme(
                primaryColorHex = "#E65100",
                secondaryColorHex = "#BF360C",
                backgroundColorHex = "#FFF8F5",
                surfaceColorHex = "#FFFFFF",
                textColorHex = "#1A1A1A",
                accentColorHex = "#FF6F00",
                ctaColorHex = "#E65100",
                deliveryColorHex = "#E65100"
            ),
            domain = CommerceDomain.FOOD,
            terminology = TerminologyConfiguration(
                cartLabel = "Food Tray",
                wishlistLabel = "Saved Dishes",
                searchPlaceholder = "Search dishes, kitchens and cuisines...",
                checkoutLabel = "Proceed to Delivery",
                reorderLabel = "Reorder Meal",
                orderLabel = "Food Orders",
                productCtaLabel = "Add to Tray"
            ),
            features = ClientFeatureConfiguration(
                enableWishlist = false,
                enableReorder = true,
                enablePrescriptionUpload = false,
                enableProductComparison = false,
                enableCoupons = true,
                enableReviews = true
            ),
            homeConfig = ClientHomeConfiguration(
                defaultCardVariant = CardVariant.RESTAURANT_CARD,
                enabledHomeSections = listOf(
                    HomeSectionType.HERO_CAMPAIGN,
                    HomeSectionType.RESTAURANT_SHELF,
                    HomeSectionType.CATEGORY_GRID,
                    HomeSectionType.DEAL_GRID,
                    HomeSectionType.EDITORIAL
                ),
                sectionTitles = mapOf(
                    HomeSectionType.HERO_CAMPAIGN to "Trending Cuisines",
                    HomeSectionType.RESTAURANT_SHELF to "Top Local Kitchens",
                    HomeSectionType.DEAL_GRID to "Combo Specials & Discounts"
                )
            ),
            version = 1
        )

        val ElectronicsClient = ClientConfiguration(
            identity = ClientIdentity(
                clientId = "tech_vault",
                clientName = "TechVault OS",
                appName = "TechVault Electronics",
                logoUrl = "https://assets.commerceos.io/brands/techvault_logo.png",
                supportEmail = "support@techvault.com",
                supportPhone = "+1-800-555-0166",
                splashTitle = "TechVault OS",
                splashTagline = "Premium Electronics, Laptops & Mobile Tech"
            ),
            theme = ClientTheme(
                primaryColorHex = "#1565C0",
                secondaryColorHex = "#0D47A1",
                backgroundColorHex = "#F4F6F9",
                surfaceColorHex = "#FFFFFF",
                textColorHex = "#1A1A1A",
                accentColorHex = "#29B6F6",
                ctaColorHex = "#1565C0"
            ),
            domain = CommerceDomain.ELECTRONICS,
            terminology = TerminologyConfiguration(
                cartLabel = "Cart",
                wishlistLabel = "Saved Tech",
                searchPlaceholder = "Search specs, smartphones, laptops and TVs...",
                checkoutLabel = "Complete Order",
                reorderLabel = "Buy Again",
                orderLabel = "Tech Orders",
                productCtaLabel = "Add to Cart"
            ),
            features = ClientFeatureConfiguration(
                enableProductComparison = true,
                enableWishlist = true,
                enableReorder = false,
                enablePrescriptionUpload = false,
                enableReviews = true,
                enableCoupons = true
            ),
            homeConfig = ClientHomeConfiguration(
                defaultCardVariant = CardVariant.ELECTRONICS_PRODUCT,
                sectionTitles = mapOf(
                    HomeSectionType.HERO_CAMPAIGN to "Tech & Device Launches",
                    HomeSectionType.CATEGORY_GRID to "Shop by Spec Category",
                    HomeSectionType.DEAL_GRID to "Tech Flash Deals"
                )
            ),
            version = 1
        )

        val ServicesClient = ClientConfiguration(
            identity = ClientIdentity(
                clientId = "home_services",
                clientName = "FixIt Pro OS",
                appName = "FixIt Home Services",
                logoUrl = "https://assets.commerceos.io/brands/fixit_logo.png",
                iconUrl = "https://assets.commerceos.io/brands/fixit_icon.png",
                supportEmail = "support@fixitpro.com",
                supportPhone = "+1-800-349-4877",
                splashTitle = "FixIt Pro OS",
                splashTagline = "Certified Technicians & Home Care Professionals",
                onboardingAssets = ClientOnboardingAssets(
                    welcomeTitle = "Expert Services at Your Doorstep",
                    welcomeSubtitle = "Plumbing, Electrical, Cleaning & Home Repairs."
                ),
                emptyStateAssets = ClientEmptyStateAssets(
                    emptyCartTitle = "No Scheduled Bookings",
                    emptyCartSubtitle = "Explore available home services to book a technician.",
                    emptySearchTitle = "No Services Found",
                    emptyWishlistTitle = "No Saved Services"
                ),
                checkoutBranding = ClientCheckoutBranding(
                    trustBadgeText = "Verified & Background Checked Pros",
                    guaranteeText = "30-Day Service Workmanship Guarantee",
                    orderConfirmationNote = "Your technician booking request has been dispatched."
                )
            ),
            theme = ClientTheme(
                primaryColorHex = "#3F51B5",
                secondaryColorHex = "#1A237E",
                backgroundColorHex = "#F3F4FA",
                surfaceColorHex = "#FFFFFF",
                textColorHex = "#1A1A1A",
                accentColorHex = "#5C6BC0",
                ctaColorHex = "#3F51B5",
                verticalAccentColorHex = "#5C6BC0",
                deliveryColorHex = "#3F51B5"
            ),
            domain = CommerceDomain.SERVICES,
            terminology = TerminologyConfiguration(
                cartLabel = "Bookings",
                wishlistLabel = "Saved Services",
                checkoutLabel = "Confirm Service Booking",
                searchPlaceholder = "Search home repairs, plumbing, cleaning & electrical...",
                reorderLabel = "Book Again",
                orderLabel = "Appointments & Bookings",
                bookingLabel = "Booking",
                serviceLabel = "Service",
                prescriptionLabel = "Service Details",
                saveFavoritesLabel = "Save Service",
                productCtaLabel = "Book Service"
            ),
            features = ClientFeatureConfiguration(
                enableServiceBooking = true,
                enableWishlist = true,
                enableReorder = true,
                enablePrescriptionUpload = false,
                enableProductComparison = false,
                enableVoiceSearch = true,
                enableCameraSearch = true,
                enableStoreLocationPicker = true,
                enableReviews = true,
                enableCoupons = true
            ),
            homeConfig = ClientHomeConfiguration(
                defaultCardVariant = CardVariant.SERVICE_CARD,
                enabledHomeSections = listOf(
                    HomeSectionType.HERO_CAMPAIGN,
                    HomeSectionType.SERVICE_SHELF,
                    HomeSectionType.CATEGORY_GRID,
                    HomeSectionType.DEAL_GRID,
                    HomeSectionType.EDITORIAL
                ),
                sectionTitles = mapOf(
                    HomeSectionType.HERO_CAMPAIGN to "Home Care & Maintenance",
                    HomeSectionType.SERVICE_SHELF to "Popular Home Services",
                    HomeSectionType.CATEGORY_GRID to "Explore Service Categories",
                    HomeSectionType.DEAL_GRID to "Seasonal Service Discounts"
                ),
                heroConfig = HeroConfig(
                    title = "Expert Home Technicians at Your Doorstep",
                    subtitle = "Licensed plumbing, electrical & appliance care",
                    ctaText = "Book a Professional"
                )
            ),
            workflowConfig = ClientWorkflowConfiguration(
                allowedCheckoutFlows = listOf("SERVICE_SCHEDULING"),
                customStepName = "Select Appointment Slot",
                customGuaranteeText = "Fixed Upfront Pricing with 100% Satisfaction Guarantee"
            ),
            taxonomyConfig = ClientTaxonomyConfiguration(
                verticalId = "services",
                defaultCategories = listOf(
                    CategoryGroup("cat_plumbing", "Plumbing Services", "Leak repairs, pipe fittings & drainage", verticalId = "services"),
                    CategoryGroup("cat_electrical", "Electrical Care", "Wiring, switches, light fixtures & breaker repairs", verticalId = "services"),
                    CategoryGroup("cat_cleaning", "Deep Home Cleaning", "Sofa, mattress, bathroom & full house cleaning", verticalId = "services"),
                    CategoryGroup("cat_hvac", "AC & HVAC Care", "AC servicing, gas refills & compressor repairs", verticalId = "services"),
                    CategoryGroup("cat_carpentry", "Carpentry & Assembly", "Furniture assembly, door lock repairs & woodwork", verticalId = "services")
                )
            ),
            version = 1
        )
    }
}

val LocalClientConfiguration = androidx.compose.runtime.staticCompositionLocalOf {
    ClientConfiguration.DefaultGeneric
}
