package com.commerceos.android.config

import com.commerceos.android.registry.CardVariant
import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests verifying Services Client profile configuration, identity, taxonomy, feature flags, and home configuration.
 */
class ServicesClientTest {

    private val servicesConfig = ClientConfiguration.ServicesClient

    @Test
    fun testServicesClient_identity() {
        assertEquals("home_services", servicesConfig.identity.clientId)
        assertEquals("FixIt Pro OS", servicesConfig.identity.clientName)
        assertEquals("FixIt Home Services", servicesConfig.identity.appName)
        assertEquals("support@fixitpro.com", servicesConfig.identity.supportEmail)
        assertEquals("+1-800-349-4877", servicesConfig.identity.supportPhone)
        assertEquals("FixIt Pro OS", servicesConfig.identity.splashTitle)
    }

    @Test
    fun testServicesClient_brandingAndTheme() {
        assertEquals("#3F51B5", servicesConfig.theme.primaryColorHex)
        assertEquals("#1A237E", servicesConfig.theme.secondaryColorHex)
        assertEquals("#5C6BC0", servicesConfig.theme.accentColorHex)
        assertEquals("#F3F4FA", servicesConfig.theme.backgroundColorHex)
    }

    @Test
    fun testServicesClient_terminology() {
        assertEquals("Bookings", servicesConfig.terminology.cartLabel)
        assertEquals("Saved Services", servicesConfig.terminology.wishlistLabel)
        assertEquals("Confirm Service Booking", servicesConfig.terminology.checkoutLabel)
        assertEquals("Search home repairs, plumbing, cleaning & electrical...", servicesConfig.terminology.searchPlaceholder)
        assertEquals("Book Again", servicesConfig.terminology.reorderLabel)
        assertEquals("Appointments & Bookings", servicesConfig.terminology.orderLabel)
        assertEquals("Book Service", servicesConfig.terminology.productCtaLabel)
    }

    @Test
    fun testServicesClient_featureFlags() {
        assertTrue(servicesConfig.features.enableServiceBooking)
        assertTrue(servicesConfig.features.enableWishlist)
        assertTrue(servicesConfig.features.enableReorder)
        assertTrue(servicesConfig.features.enableVoiceSearch)
        assertFalse(servicesConfig.features.enablePrescriptionUpload)
        assertFalse(servicesConfig.features.enableProductComparison)
    }

    @Test
    fun testServicesClient_homeAndCardVariant() {
        assertEquals(CardVariant.SERVICE_CARD, servicesConfig.homeConfig.defaultCardVariant)
        assertEquals("Expert Home Technicians at Your Doorstep", servicesConfig.homeConfig.heroConfig.title)
    }

    @Test
    fun testServicesClient_taxonomy() {
        assertEquals("services", servicesConfig.taxonomyConfig.verticalId)
        val defaultCategories = servicesConfig.taxonomyConfig.defaultCategories
        assertTrue(defaultCategories.isNotEmpty())
        assertTrue(defaultCategories.any { it.title.contains("Plumbing") })
        assertTrue(defaultCategories.any { it.title.contains("Electrical") })
    }
}
