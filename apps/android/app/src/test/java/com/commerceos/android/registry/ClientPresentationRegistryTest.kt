package com.commerceos.android.registry

import com.commerceos.android.config.ClientConfiguration
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

/**
 * 🔴 P0 — CLIENT PRESENTATION REGISTRY TEST SUITE
 * Verifies resolution of vertical symbols, headers, CTAs, hero titles, card variants, and custom client vertical registration.
 */
class ClientPresentationRegistryTest {

    @Before
    fun setup() {
        ClientPresentationRegistry.clearCustomVerticals()
    }

    @Test
    fun testFoodPresentationResolution() {
        val presentation = ClientPresentationRegistry.resolvePresentation("food", ClientConfiguration.FoodClient)
        assertEquals("🍽️", presentation.visualSymbol)
        assertEquals("Cuisines & Dining", presentation.catalogHeader)
        assertEquals("Order Now", presentation.defaultCta)
        assertEquals(CardVariant.RESTAURANT_CARD, presentation.cardVariant)
    }

    @Test
    fun testFashionPresentationResolution() {
        val presentation = ClientPresentationRegistry.resolvePresentation("fashion", ClientConfiguration.FashionClient)
        assertEquals("👔", presentation.visualSymbol)
        assertEquals("Style & Apparel", presentation.catalogHeader)
        assertEquals("Shop Collection", presentation.defaultCta)
        assertEquals(CardVariant.FASHION_PRODUCT, presentation.cardVariant)
    }

    @Test
    fun testServicesPresentationResolution() {
        val presentation = ClientPresentationRegistry.resolvePresentation("services", ClientConfiguration.ServicesClient)
        assertEquals("🔧", presentation.visualSymbol)
        assertEquals("Home Services", presentation.catalogHeader)
        assertEquals("Book Service", presentation.defaultCta)
        assertEquals(CardVariant.SERVICE_CARD, presentation.cardVariant)
    }

    @Test
    fun testCustomVerticalRegistration_ResolvesRegisteredPresentation() {
        val customPres = VerticalPresentation(
            visualSymbol = "🚀",
            catalogHeader = "Space Gear",
            defaultCta = "Launch Now"
        )
        ClientPresentationRegistry.registerCustomVertical("aerospace", customPres)

        val resolved = ClientPresentationRegistry.resolvePresentation("aerospace", ClientConfiguration.DefaultGeneric)
        assertEquals("🚀", resolved.visualSymbol)
        assertEquals("Space Gear", resolved.catalogHeader)
        assertEquals("Launch Now", resolved.defaultCta)
    }
}
