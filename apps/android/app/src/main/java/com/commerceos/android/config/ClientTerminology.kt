package com.commerceos.android.config

/**
 * Terminology Configuration for White-Label Personalization.
 * Provides client-controlled copy across all consumer touchpoints.
 */
data class TerminologyConfiguration(
    val cartLabel: String = "Cart",
    val wishlistLabel: String = "Saved",
    val checkoutLabel: String = "Place Order",
    val searchPlaceholder: String = "Search products, brands and categories...",
    val reorderLabel: String = "Buy Again",
    val orderLabel: String = "Orders",
    val bookingLabel: String = "Bookings",
    val serviceLabel: String = "Services",
    val prescriptionLabel: String = "Prescriptions",
    val saveFavoritesLabel: String = "Save for Later",
    val productCtaLabel: String = "Add to Cart",
    val clientSpecificTerminology: Map<String, String> = emptyMap(),
    val locale: String = "en-US"
) {
    /**
     * Resolves a custom client-specific key with fallback.
     */
    fun resolveCustomTerm(key: String, fallback: String): String {
        return clientSpecificTerminology[key] ?: fallback
    }
}

typealias ClientTerminology = TerminologyConfiguration
