package com.commerceos.android.config

/**
 * Visual branding and copy assets for client onboarding.
 */
data class ClientOnboardingAssets(
    val heroImageUrl: String? = null,
    val welcomeTitle: String = "Welcome",
    val welcomeSubtitle: String = "Discover products and services tailored for you.",
    val splashLogoUrl: String? = null
)

/**
 * Visual branding and copy assets for empty states.
 */
data class ClientEmptyStateAssets(
    val emptyCartTitle: String = "Your cart is empty",
    val emptyCartSubtitle: String = "Explore our catalog to add items.",
    val emptySearchTitle: String = "No results found",
    val emptySearchSubtitle: String = "Try searching with different keywords.",
    val emptyWishlistTitle: String = "No saved items",
    val emptyWishlistSubtitle: String = "Save items to view them later.",
    val emptyCartImageUrl: String? = null,
    val emptySearchImageUrl: String? = null,
    val emptyWishlistImageUrl: String? = null
)

/**
 * Visual branding and copy assets for error states.
 */
data class ClientErrorStateAssets(
    val networkErrorTitle: String = "Connection Issue",
    val networkErrorSubtitle: String = "Please check your network and try again.",
    val genericErrorTitle: String = "Something went wrong",
    val genericErrorSubtitle: String = "We are working to fix this.",
    val errorImageUrl: String? = null
)

/**
 * Client Notification Branding settings.
 */
data class ClientNotificationBranding(
    val smallIconResId: String = "ic_notification_default",
    val accentColorHex: String = "#1E88E5",
    val soundName: String = "default"
)

/**
 * Client Checkout Branding settings.
 */
data class ClientCheckoutBranding(
    val bannerImageUrl: String? = null,
    val trustBadgeText: String = "100% Secure Checkout",
    val guaranteeText: String = "Money-back Guarantee & Quality Support",
    val orderConfirmationNote: String = "Thank you for shopping with us!"
)

/**
 * Complete Client Identity Configuration.
 */
data class ClientIdentity(
    val clientId: String,
    val clientName: String,
    val appName: String,
    val logoUrl: String? = null,
    val iconUrl: String? = null,
    val supportEmail: String = "support@commerceos.io",
    val supportPhone: String = "+1-800-555-0199",
    val splashBrandingUrl: String? = null,
    val splashTitle: String? = null,
    val splashTagline: String? = null,
    val onboardingAssets: ClientOnboardingAssets = ClientOnboardingAssets(),
    val emptyStateAssets: ClientEmptyStateAssets = ClientEmptyStateAssets(),
    val errorStateAssets: ClientErrorStateAssets = ClientErrorStateAssets(),
    val notificationBranding: ClientNotificationBranding = ClientNotificationBranding(),
    val checkoutBranding: ClientCheckoutBranding = ClientCheckoutBranding()
)
