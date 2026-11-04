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
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAvailableCampaigns(CartViewModel.DEFAULT_CAMPAIGNS)

        val prod = CommerceProduct(id = "p1", sku = "sku1", name = "Syrup", price = 250.0, sellingPrice = 250.0)
        viewModel.addItem(prod)
        testDispatcher.scheduler.advanceUntilIdle()

        val applied = viewModel.applyCoupon("SAVE10")
        assertTrue(applied)
        assertEquals("SAVE10", viewModel.appliedCouponCode)
        assertEquals(0, java.math.BigDecimal.valueOf(100.0).compareTo(viewModel.couponDiscountAmount))

        viewModel.removeCoupon()
        assertNull(viewModel.appliedCouponCode)
        assertEquals(java.math.BigDecimal.ZERO, viewModel.couponDiscountAmount)
    }

    @Test
    fun testTieredCampaigns_Welcome20_Health30_AndRemoval() {
        val viewModel = CartViewModel(object : com.commerceos.android.repository.AppRepository() {})
        viewModel.init("")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAvailableCampaigns(CartViewModel.DEFAULT_CAMPAIGNS)

        // 1. Empty cart: WELCOME20 fails minOrderValue threshold (>= 299)
        val res1 = viewModel.applyCouponWithFeedback("WELCOME20")
        assertFalse(res1.success)
        assertTrue(res1.message.contains("more to apply WELCOME20"))

        // 2. Add product below threshold: 1x ₹150 = ₹150
        val prod1 = CommerceProduct(id = "p1", sku = "sku1", name = "Syrup", price = 150.0, sellingPrice = 150.0)
        viewModel.addItem(prod1)
        testDispatcher.scheduler.advanceUntilIdle()
        val res2 = viewModel.applyCouponWithFeedback("WELCOME20")
        assertFalse(res2.success)

        // 3. Add more: total = ₹300 (eligible for WELCOME20: 20% of 300 = ₹60.00)
        viewModel.addItem(prod1) // qty = 2, total = ₹300
        testDispatcher.scheduler.advanceUntilIdle()
        val res3 = viewModel.applyCouponWithFeedback("WELCOME20")
        assertTrue(res3.success)
        assertEquals("WELCOME20", viewModel.appliedCouponCode)
        assertEquals(java.math.BigDecimal("60.00"), viewModel.couponDiscountAmount)

        // 4. Test removeCoupon
        viewModel.removeCoupon()
        assertNull(viewModel.appliedCouponCode)
        assertEquals(java.math.BigDecimal.ZERO, viewModel.couponDiscountAmount)

        // 5. Test HEALTH30 (requires >= 799 for flat ₹150 off)
        val res4 = viewModel.applyCouponWithFeedback("HEALTH30")
        assertFalse(res4.success)

        val prodHigh = CommerceProduct(id = "p2", sku = "sku2", name = "Monitor", price = 600.0, sellingPrice = 600.0)
        viewModel.addItem(prodHigh) // total = 300 + 600 = 900 >= 799
        testDispatcher.scheduler.advanceUntilIdle()
        val res5 = viewModel.applyCouponWithFeedback("HEALTH30")
        assertTrue(res5.success)
        assertEquals("HEALTH30", viewModel.appliedCouponCode)
        assertEquals(java.math.BigDecimal("150.00"), viewModel.couponDiscountAmount)
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
