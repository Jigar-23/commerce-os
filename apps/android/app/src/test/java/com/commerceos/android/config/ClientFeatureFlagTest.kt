package com.commerceos.android.config

import com.commerceos.android.model.HomeDestination
import com.commerceos.android.navigation.AppDestinationRouter
import com.commerceos.android.ui.navigation.Screen
import org.junit.Assert.*
import org.junit.Test

class ClientFeatureFlagTest {

    private val router = AppDestinationRouter()

    @Test
    fun testPrescriptionFeatureFlag_whenEnabled_resolvesPrescriptionScreen() {
        val pharmacyConfig = ClientConfiguration.PharmacyClient
        val screen = router.resolve(HomeDestination.Prescriptions, pharmacyConfig)
        assertEquals(Screen.Prescriptions, screen)
    }

    @Test
    fun testPrescriptionFeatureFlag_whenDisabled_resolvesFeatureDisabledScreen() {
        val genericConfig = ClientConfiguration.DefaultGeneric
        val screen = router.resolve(HomeDestination.Prescriptions, genericConfig)
        assertTrue(screen is Screen.FeatureDisabled)
        assertEquals("Prescriptions", (screen as Screen.FeatureDisabled).featureName)
    }

    @Test
    fun testServiceBookingFeatureFlag_whenEnabled_resolvesServiceScreen() {
        val servicesConfig = ClientConfiguration.ServicesClient
        val screen = router.resolve(HomeDestination.Service("srv_123"), servicesConfig)
        assertTrue(screen is Screen.Service)
        assertEquals("srv_123", (screen as Screen.Service).serviceId)
    }

    @Test
    fun testServiceBookingFeatureFlag_whenDisabled_resolvesFeatureDisabledScreen() {
        val foodConfig = ClientConfiguration.FoodClient
        val screen = router.resolve(HomeDestination.Service("srv_123"), foodConfig)
        assertTrue(screen is Screen.FeatureDisabled)
        assertEquals("Service Booking", (screen as Screen.FeatureDisabled).featureName)
    }

    @Test
    fun testAllFeatureFlagsInPresetProfiles() {
        // Pharmacy
        assertTrue(ClientConfiguration.PharmacyClient.features.enablePrescriptionUpload)
        // Fashion
        assertFalse(ClientConfiguration.FashionClient.features.enablePrescriptionUpload)
        assertTrue(ClientConfiguration.FashionClient.features.enableWishlist)
        // Food
        assertFalse(ClientConfiguration.FoodClient.features.enableWishlist)
        assertTrue(ClientConfiguration.FoodClient.features.enableReorder)
        // Electronics
        assertTrue(ClientConfiguration.ElectronicsClient.features.enableProductComparison)
        // Services
        assertTrue(ClientConfiguration.ServicesClient.features.enableServiceBooking)
    }
}
