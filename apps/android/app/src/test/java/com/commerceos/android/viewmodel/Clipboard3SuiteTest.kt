package com.commerceos.android.viewmodel

import com.commerceos.android.advanced.*
import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.ClientTaxonomyConfiguration
import com.commerceos.android.engine.CustomVerticalDefinition
import com.commerceos.android.engine.GeneralizedVerticalEngine
import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.navigation.AppDestinationRouter
import com.commerceos.android.registry.CardVariant
import com.commerceos.android.registry.ClientCardRegistry
import com.commerceos.android.registry.WorkflowRegistry
import com.commerceos.android.registry.WorkflowType
import com.commerceos.android.ui.navigation.Screen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Assert.*
import org.junit.Test

/**
 * Comprehensive Unit Test Suite verifying all P0, P1, P2, and P3 requirements for
 * COMMERCE OS — CLIPBOARD 3: VERTICAL / ENTITY / WORKFLOW EXPERIENCE.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class Clipboard3SuiteTest {

    private val testDispatcher = StandardTestDispatcher()
    private val router = AppDestinationRouter()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun testGeneralizedVerticalEngine_dynamicRegistrationAndResolution() {
        val customVertical = CustomVerticalDefinition(
            verticalId = "pet_care",
            verticalName = "Pet Care & Veterinary",
            visualSymbol = "🐾",
            displayOrder = 7,
            defaultCta = "Explore Pet Supplies"
        )
        GeneralizedVerticalEngine.registerVertical(customVertical)

        val resolved = GeneralizedVerticalEngine.getVertical("pet_care")
        assertNotNull(resolved)
        assertEquals("Pet Care & Veterinary", resolved?.verticalName)
        assertEquals("🐾", resolved?.visualSymbol)

        val activeList = GeneralizedVerticalEngine.resolveActiveVerticals(ClientConfiguration.DefaultGeneric)
        assertTrue(activeList.any { it.verticalId == "pet_care" })
    }

    @Test
    fun testEntityModel_capabilitiesAndUnknownFallback() {
        val unknownEntity = CommerceEntity.UnknownEntity(
            entityId = "custom_999",
            rawType = "iot_device_feed",
            title = "Smart Meter Sensor"
        )
        val variant = ClientCardRegistry.resolveCardVariant(unknownEntity, ClientConfiguration.DefaultGeneric)
        assertEquals(CardVariant.FALLBACK_GENERIC_CARD, variant)

        val product = CommerceEntity.ProductItem(
            product = CommerceProduct(id = "p1", sku = "s1", name = "Test Prod", price = 100.0, sellingPrice = 90.0)
        )
        assertTrue(product.capabilities.canAddToCart)
    }

    @Test
    fun testWorkflowRegistry_isWorkflowSupportedAndDeepLinkBlocking() {
        // Pharmacy client supports Prescription upload
        assertTrue(WorkflowRegistry.isWorkflowSupported(WorkflowType.PRESCRIPTION, ClientConfiguration.PharmacyClient))
        assertFalse(WorkflowRegistry.isWorkflowSupported(WorkflowType.PRESCRIPTION, ClientConfiguration.FashionClient))

        // Deep link resolution for unsupported prescription on Fashion client blocks route
        val fashionDeepLink = router.resolveDeepLink("commerceos://prescriptions", ClientConfiguration.FashionClient)
        assertTrue(fashionDeepLink is Screen.FeatureDisabled)

        // Deep link resolution for supported prescription on Pharmacy client resolves to Screen.Prescriptions
        val rxDeepLink = router.resolveDeepLink("commerceos://prescriptions", ClientConfiguration.PharmacyClient)
        assertTrue(rxDeepLink is Screen.Prescriptions)

        // Deep link resolution for service booking on Fashion client blocks route
        val serviceDeepLink = router.resolveDeepLink("commerceos://services/s101", ClientConfiguration.FashionClient)
        assertTrue(serviceDeepLink is Screen.FeatureDisabled)

        // Deep link resolution for service booking on Services client resolves to Screen.Service
        val validServiceDeepLink = router.resolveDeepLink("commerceos://services/s101", ClientConfiguration.ServicesClient)
        assertTrue(validServiceDeepLink is Screen.Service)
    }

    @Test
    fun testClientProfiles_terminologyAndDomains() {
        assertEquals("Cart", ClientConfiguration.DefaultGeneric.terminology.cartLabel)
        assertEquals("Shopping Bag", ClientConfiguration.FashionClient.terminology.cartLabel)
        assertEquals("Health Basket", ClientConfiguration.PharmacyClient.terminology.cartLabel)
        assertEquals("Bookings", ClientConfiguration.ServicesClient.terminology.cartLabel)
        assertEquals("Food Tray", ClientConfiguration.FoodClient.terminology.cartLabel)
        assertEquals("Cart", ClientConfiguration.ElectronicsClient.terminology.cartLabel)
    }

    @Test
    fun testSharedCartEngine_saveForLaterAndCoupons() {
        val viewModel = CartViewModel(object : com.commerceos.android.repository.AppRepository() {})
        viewModel.init("cust_101")

        val applied = viewModel.applyCoupon("SAVE10")
        assertTrue(applied)
        assertEquals("SAVE10", viewModel.appliedCouponCode)
        assertEquals(java.math.BigDecimal.valueOf(100.0), viewModel.couponDiscountAmount)

        viewModel.removeCoupon()
        assertNull(viewModel.appliedCouponCode)
        assertEquals(java.math.BigDecimal.ZERO, viewModel.couponDiscountAmount)
    }

    @Test
    fun testConfigurableCheckoutEngine_appointmentSlotsAndValidation() {
        val viewModel = CheckoutViewModel(object : com.commerceos.android.repository.AppRepository() {})
        viewModel.start("cust_101", emptyList())
        assertFalse(viewModel.uiState.readyForPayment)

        viewModel.attachAppointmentSlot("Tomorrow at 10:00 AM")
        assertEquals("Tomorrow at 10:00 AM", viewModel.uiState.selectedSlotText)
    }

    @Test
    fun testP3_AiEnginesAndCardPlugins() {
        val naturalQueryResult = AiSearchEngine.parseNaturalLanguageQuery("cheap shoes under 50", ClientConfiguration.FashionClient)
        assertEquals("PRICE_SENSITIVE_SEARCH", naturalQueryResult.parsedIntent)

        val prod1 = CommerceProduct(id = "p1", sku = "s1", name = "Phone A", price = 699.0, sellingPrice = 599.0, rating = 4.5)
        val prod2 = CommerceProduct(id = "p2", sku = "s2", name = "Phone B", price = 899.0, sellingPrice = 799.0, rating = 4.8)

        val matrix = AiProductComparisonEngine.compareProducts(listOf(prod1, prod2))
        assertEquals("p2", matrix.recommendedWinnerId)

        CardPluginRegistry.registerCardPlugin("custom_gadget", CardVariant.ELECTRONICS_PRODUCT)
        val variant = CardPluginRegistry.resolveCardPlugin("custom_gadget")
        assertEquals(CardVariant.ELECTRONICS_PRODUCT, variant)
    }
}
