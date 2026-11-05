package com.commerceos.android.viewmodel

import com.commerceos.android.data.local.CartDao
import com.commerceos.android.data.local.CartItemEntity
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class KodexAuditBatch1RegressionTest {

    private val testDispatcher = StandardTestDispatcher()

    open class FakeAppRepository : AppRepository() {
        override suspend fun getPromotions(): ApiResult<List<CouponCampaign>> = ApiResult.Success(emptyList())
        override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> = ApiResult.Success(CartResponse(items = emptyList()))
    }

    class FakeSecureCredentialStore : com.commerceos.android.session.SecureCredentialStore(null) {
        val data = mutableMapOf<String, String>()
        var shouldFailSave = false
        override fun save(alias: String, value: String): Boolean {
            if (shouldFailSave) return false
            data[alias] = value
            return true
        }
        override fun read(alias: String): String? = data[alias]
        override fun clear(alias: String) {
            data.remove(alias)
        }
        override fun deleteKeyAndValue(alias: String) {
            data.remove(alias)
        }
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = null
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = null
    }

    @After
    fun tearDown() {
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = null
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = null
        Dispatchers.resetMain()
    }

    private fun createFakeContext(store: MutableMap<String, String>): android.content.Context {
        lateinit var editor: android.content.SharedPreferences.Editor
        editor = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method?.name) {
                "putString" -> { store[args[0] as String] = args[1] as String; editor }
                "putLong" -> { store[args[0] as String] = (args[1] as Long).toString(); editor }
                "remove" -> { store.remove(args[0] as String); editor }
                "commit" -> true
                "apply" -> null
                else -> editor
            }
        } as android.content.SharedPreferences.Editor

        val prefs = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, method, args ->
            when (method?.name) {
                "getString" -> store[args[0] as String] ?: (args.getOrNull(1) as? String)
                "getLong" -> (args.getOrNull(1) as? Long) ?: 0L
                "getInt" -> (args.getOrNull(1) as? Int) ?: 0
                "getBoolean" -> (args.getOrNull(1) as? Boolean) ?: false
                "edit" -> editor
                else -> null
            }
        } as android.content.SharedPreferences

        return object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
            override fun getApplicationContext(): android.content.Context = this
        }
    }

    // -------------------------------------------------------------
    // CART VIEW MODEL TESTS
    // -------------------------------------------------------------

    @Test
    fun testCartViewModel_accountSwitchClearsPreviousCart() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(
                    CartResponse(
                        items = listOf(
                            CartItem(
                                productId = "p1",
                                sku = "sku1",
                                name = "Paracetamol",
                                unitPrice = BigDecimal("50.00"),
                                quantity = 2
                            )
                        ),
                        itemsSubtotal = BigDecimal("100.00"),
                        grandTotal = BigDecimal("100.00")
                    )
                )
            }
        }

        val viewModel = CartViewModel(repo)
        viewModel.init("customer_A")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("customer_A", viewModel.customerId)
        assertEquals(1, viewModel.cartItems.size)

        // Switch to customer_B: must clear previous cart state
        viewModel.init("customer_B")
        assertEquals("customer_B", viewModel.customerId)
        assertEquals(0, viewModel.cartItems.size)
        assertNull(viewModel.totals)
        assertEquals(BigDecimal.ZERO, viewModel.couponDiscountAmount)

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.cartItems.size)
    }

    @Test
    fun testCartViewModel_addItemRejectsBlankSkuAndInvalidPrice() = runTest {
        val repo = object : FakeAppRepository() {}
        val viewModel = CartViewModel(repo)
        viewModel.init("") // Guest cart mode
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Blank SKU must be rejected
        val badSkuProduct = CommerceProduct(
            id = "prod_1",
            sku = "  ",
            name = "Bad SKU Item",
            price = 100.0,
            sellingPrice = 100.0
        )
        viewModel.addItem(badSkuProduct)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.cartItems.size)
        assertTrue(viewModel.errorMessage?.contains("SKU is missing") == true)

        // 2. Zero / negative price must be rejected (NO invented 10.0 fallback!)
        val zeroPriceProduct = CommerceProduct(
            id = "prod_2",
            sku = "SKU_ZERO",
            name = "Zero Price Item",
            price = 0.0,
            sellingPrice = 0.0,
            discountedPrice = 0.0,
            mrp = 0.0
        )
        viewModel.addItem(zeroPriceProduct)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.cartItems.size)
        assertTrue(viewModel.errorMessage?.contains("price is unavailable") == true)
    }

    @Test
    fun testCartViewModel_couponRequiresActiveStatusAndMinOrderValue() = runTest {
        val repo = object : FakeAppRepository() {}
        val viewModel = CartViewModel(repo)
        viewModel.init("") // Guest cart mode
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.updateAvailableCampaigns(CartViewModel.DEFAULT_CAMPAIGNS)

        // Empty cart: SAVE10 (minOrderValue 199.00) must be rejected
        val resEmpty = viewModel.applyCouponWithFeedback("SAVE10")
        assertFalse(resEmpty.success)
        assertTrue(resEmpty.message.contains("more to apply SAVE10"))

        // Add item under threshold (₹150 < ₹199)
        viewModel.addItem(CommerceProduct(id = "p1", sku = "s1", name = "Syrup", price = 150.0, sellingPrice = 150.0))
        testDispatcher.scheduler.advanceUntilIdle()

        val resUnder = viewModel.applyCouponWithFeedback("SAVE10")
        assertFalse(resUnder.success)

        // Add more to exceed threshold (₹150 + ₹150 = ₹300 >= ₹199)
        viewModel.addItem(CommerceProduct(id = "p1", sku = "s1", name = "Syrup", price = 150.0, sellingPrice = 150.0))
        testDispatcher.scheduler.advanceUntilIdle()

        val resValid = viewModel.applyCouponWithFeedback("SAVE10")
        assertTrue(resValid.success)
        assertEquals("SAVE10", viewModel.appliedCouponCode)
        assertEquals(0, BigDecimal("100.00").compareTo(viewModel.couponDiscountAmount))
    }

    @Test
    fun testCartViewModel_reorderAllTracksFailures() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                return ApiResult.Failure(AppError.Network("Item out of stock"))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        val reorderItems = listOf(
            OrderItem(productId = "p1", sku = "sku1", name = "Item 1", price = BigDecimal("50.00"), quantity = 1)
        )
        viewModel.reorderAll(reorderItems)
        testDispatcher.scheduler.advanceUntilIdle()

        // Should report failure event, not false success
        assertEquals(0, viewModel.cartItems.size)
        assertTrue(viewModel.errorMessage?.contains("Could not reorder") == true)
    }

    // -------------------------------------------------------------
    // CHECKOUT VIEW MODEL TESTS
    // -------------------------------------------------------------

    @Test
    fun testCheckoutViewModel_validationProperties() {
        val repo = object : FakeAppRepository() {}
        val viewModel = CheckoutViewModel(repo)

        // 1. Initial / Idle state: NOT ready for payment
        assertFalse(viewModel.uiState.readyForPayment)
        assertFalse(viewModel.uiState.cartValid)
        assertFalse(viewModel.uiState.addressValid)
        assertFalse(viewModel.uiState.serviceabilityValid)

        // 2. Start with valid cart items
        val validItems = listOf(
            CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1)
        )
        viewModel.start("cust_1", validItems)
        assertTrue(viewModel.uiState.cartValid)
        assertTrue(viewModel.uiState.pricingValid)
        assertFalse(viewModel.uiState.addressValid)
        assertFalse(viewModel.uiState.readyForPayment)

        // 3. Serviceability Idle/Checking is NOT valid
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "Bangalore", postalCode = "560001"))
        assertTrue(viewModel.uiState.addressValid)
        assertFalse(viewModel.uiState.serviceabilityValid)
        assertFalse(viewModel.uiState.readyForPayment)
    }

    @Test
    fun testCheckoutViewModel_startClearsStateOnCustomerChange() {
        val repo = object : FakeAppRepository() {}
        val viewModel = CheckoutViewModel(repo)

        val itemsA = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        viewModel.start("cust_A", itemsA)
        viewModel.selectAddress(ApiAddress(id = "addr_A", addressLine = "Street A", city = "BLR", postalCode = "560001"))
        viewModel.attachPrescription("rx_A")
        viewModel.attachAppointmentSlot("10:00 AM")

        assertEquals("addr_A", viewModel.uiState.address?.id)
        assertEquals("rx_A", viewModel.uiState.prescriptionId)
        assertEquals("10:00 AM", viewModel.uiState.selectedSlotText)

        // Start with different customer: must wipe address, prescription, slot
        val itemsB = listOf(CartItem(productId = "p2", sku = "s2", name = "Item 2", unitPrice = BigDecimal("200.00"), quantity = 1))
        viewModel.start("cust_B", itemsB)

        assertEquals("cust_B", viewModel.customerId)
        assertNull(viewModel.uiState.address)
        assertNull(viewModel.uiState.prescriptionId)
        assertNull(viewModel.uiState.selectedSlotText)
    }

    @Test
    fun testCheckoutViewModel_prescriptionApprovalGate() = runTest {
        val rxItems = listOf(
            CartItem(
                productId = "p1",
                sku = "s1",
                name = "Rx Drug",
                unitPrice = BigDecimal("150.00"),
                quantity = 1,
                pharmacyAttributes = PharmacyCartAttributes(prescriptionRequired = true, coldChain = false)
            )
        )
        val addr = ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001")

        // 1. Unapproved prescription attempt
        val repoUnapproved = object : FakeAppRepository() {
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> {
                return ApiResult.Success(
                    listOf(Prescription(id = "rx_unapproved", customerId = customerId, status = "PENDING_REVIEW"))
                )
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true, deliveryFee = 0.0, coldChainFee = 0.0))
            }
        }
        val vmUnapproved = CheckoutViewModel(repoUnapproved)
        vmUnapproved.start("cust_1", rxItems)
        vmUnapproved.selectAddress(addr)
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vmUnapproved.uiState.serviceabilityValid)
        vmUnapproved.attachPrescription("rx_unapproved")
        vmUnapproved.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vmUnapproved.uiState.state is CheckoutState.Failed)
        assertEquals("Attach a pharmacist-approved prescription before checkout.", vmUnapproved.uiState.errorMessage)

        // 2. Approved prescription succeeds
        var checkoutCalled = false
        val repoApproved = object : FakeAppRepository() {
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> {
                return ApiResult.Success(
                    listOf(Prescription(id = "rx_approved", customerId = customerId, status = "APPROVED"))
                )
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true, deliveryFee = 0.0, coldChainFee = 0.0))
            }
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                checkoutCalled = true
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_123"))
            }
        }
        val vmApproved = CheckoutViewModel(repoApproved)
        vmApproved.start("cust_1", rxItems)
        vmApproved.selectAddress(addr)
        testDispatcher.scheduler.advanceUntilIdle()

        vmApproved.attachPrescription("rx_approved")
        vmApproved.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(checkoutCalled)
        assertTrue(vmApproved.uiState.state is CheckoutState.Confirmed)
    }

    // -------------------------------------------------------------
    // PRESCRIPTION VIEW MODEL TESTS
    // -------------------------------------------------------------

    @Test
    fun testPrescriptionViewModel_loadRejectsBlankCustomer() = runTest {
        val repo = object : FakeAppRepository() {}
        val viewModel = PrescriptionViewModel(repo)

        viewModel.load("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.prescriptions.size)
        assertEquals("Sign in to view prescriptions.", viewModel.errorMessage)
    }

    @Test
    fun testPrescriptionViewModel_uploadRequiresAuthAndAttachments() = runTest {
        val repo = object : FakeAppRepository() {}
        val viewModel = PrescriptionViewModel(repo)

        // 1. Unauthenticated upload fails
        viewModel.upload("", "Patient A", listOf("path/img.jpg"))
        assertEquals("Sign in to upload prescriptions", viewModel.errorMessage)

        // 2. Empty attachments fail
        viewModel.upload("cust_1", "Patient A", emptyList())
        assertEquals("Attach at least one prescription image", viewModel.errorMessage)
    }

    // -------------------------------------------------------------
    // REMAINING FINDINGS REGRESSION TESTS
    // -------------------------------------------------------------

    @Test
    fun testCartViewModel_promotionsFailClosedOnNetworkError() = runTest {
        var shouldFailPromotions = false
        val repo = object : FakeAppRepository() {
            override suspend fun getPromotions(): ApiResult<List<CouponCampaign>> {
                return if (shouldFailPromotions) {
                    ApiResult.Failure(AppError.Network("Promotion server unreachable"))
                } else {
                    ApiResult.Success(CartViewModel.DEFAULT_CAMPAIGNS)
                }
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Active campaigns loaded via repo
        assertEquals(3, viewModel.availableCampaigns.size)

        // Add item and apply SAVE10
        val prod = CommerceProduct(id = "p1", sku = "s1", name = "Syrup", price = 250.0, sellingPrice = 250.0)
        viewModel.addItem(prod)
        testDispatcher.scheduler.advanceUntilIdle()
        val applied = viewModel.applyCoupon("SAVE10")
        assertTrue(applied)
        assertEquals("SAVE10", viewModel.appliedCouponCode)

        // Network failure in refreshDeliveryConfigAndPromotions must fail closed (clear campaigns, clear coupon)
        shouldFailPromotions = true
        viewModel.refreshDeliveryConfigAndPromotions()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.availableCampaigns.isEmpty())
        assertNull(viewModel.appliedCouponCode)
        assertEquals(BigDecimal.ZERO, viewModel.couponDiscountAmount)
        assertEquals("Promotions could not be loaded.", viewModel.couponFeedbackMessage)
        assertTrue(viewModel.isCouponError)
    }

    @Test
    fun testCartViewModel_accountSwitchRejectsStaleMutationCommit() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = emptyList()))
            }
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                if (customerId == "cust_old") {
                    delay(500)
                    return ApiResult.Success(
                        CartResponse(
                            items = listOf(item),
                            itemsSubtotal = item.unitPrice,
                            grandTotal = item.unitPrice
                        )
                    )
                }
                return ApiResult.Success(CartResponse(items = emptyList()))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_old")
        testDispatcher.scheduler.advanceUntilIdle()

        val prod = CommerceProduct(id = "p1", sku = "s1", name = "Old Account Item", price = 100.0, sellingPrice = 100.0)
        viewModel.addItem(prod)

        // Immediately switch account before delayed addItem finishes
        viewModel.init("cust_new")

        // Advance dispatcher to allow the delayed cust_old request to complete
        testDispatcher.scheduler.advanceUntilIdle()

        // cust_new must NOT contain cust_old's items!
        assertEquals("cust_new", viewModel.customerId)
        assertEquals(0, viewModel.cartItems.size)
        assertFalse(viewModel.isLoading)
    }

    @Test
    fun testCartViewModel_invalidReorderDataSkippedAndRejected() = runTest {
        val addedItems = mutableListOf<CartItem>()
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                addedItems.add(item)
                return ApiResult.Success(CartResponse(items = addedItems.toList()))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. Single invalid reorder item with placeholder SKU_ITEM
        val invalidOrderItem = OrderItem(
            productId = "p_bad",
            sku = "SKU_ITEM",
            name = "Corrupted Item",
            price = BigDecimal("50.00"),
            quantity = 1
        )
        viewModel.addReorderItem(invalidOrderItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, addedItems.size)
        assertTrue(viewModel.errorMessage?.contains("cannot be reordered") == true)

        // 2. Single invalid reorder item with zero price
        val zeroPriceOrderItem = OrderItem(
            productId = "p_zero",
            sku = "REAL_SKU",
            name = "Free Item",
            price = BigDecimal.ZERO,
            quantity = 1
        )
        viewModel.addReorderItem(zeroPriceOrderItem)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, addedItems.size)

        // 3. Batch reorderAll with mix of invalid items and valid item
        val validItem = OrderItem(
            productId = "p_valid",
            sku = "VALID_SKU",
            name = "Valid Item",
            price = BigDecimal("120.00"),
            quantity = 1
        )
        viewModel.reorderAll(listOf(invalidOrderItem, zeroPriceOrderItem, validItem))
        testDispatcher.scheduler.advanceUntilIdle()

        // Only the valid item should have been passed to repo
        assertEquals(1, addedItems.size)
        assertEquals("VALID_SKU", addedItems[0].sku)
    }

    @Test
    fun testCartViewModel_moveBackToCartRetainsItemOnFailure() = runTest {
        var allowAdd = true
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                if (!allowAdd) {
                    return ApiResult.Failure(AppError.Network("Network down"))
                }
                return ApiResult.Success(CartResponse(items = listOf(item)))
            }
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = emptyList()))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        val prod = CommerceProduct(id = "p1", sku = "s1", name = "Saved Item", price = 100.0, sellingPrice = 100.0)
        viewModel.addItem(prod)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.cartItems.size)

        viewModel.moveToSaveForLater("s1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.cartItems.size)
        assertEquals(1, viewModel.saveForLaterItems.size)

        // Now disallow add to simulate failure
        allowAdd = false
        viewModel.moveBackToCart("s1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Item must NOT be lost: remains in saveForLaterItems
        assertEquals(1, viewModel.saveForLaterItems.size)
        assertEquals(0, viewModel.cartItems.size)
        assertTrue(viewModel.errorMessage?.contains("Unable to reach the server") == true)
    }

    @Test
    fun testCheckoutViewModel_startIgnoredWhenProcessing() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                delay(1000)
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_1"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
        val itemsA = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        viewModel.start("cust_1", itemsA)
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.uiState.isProcessing)

        // Calling start() while checkout is in-flight must be ignored
        val itemsB = listOf(CartItem(productId = "p2", sku = "s2", name = "Item 2", unitPrice = BigDecimal("200.00"), quantity = 1))
        viewModel.start("cust_2", itemsB)

        // Customer and state remain unchanged
        assertEquals("cust_1", viewModel.customerId)
        assertTrue(viewModel.uiState.isProcessing)

        testDispatcher.scheduler.advanceUntilIdle()
        assertFalse(viewModel.uiState.isProcessing)
    }

    @Test
    fun testCheckoutViewModel_idempotencyKeyRegeneratesOnPayloadFingerprintChange() = runTest {
        val capturedKeys = mutableListOf<String>()
        var checkoutFails = true
        val repo = object : FakeAppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                capturedKeys.add(request.idempotencyKey)
                return if (checkoutFails) {
                    ApiResult.Failure(AppError.Network("Temporary timeout"))
                } else {
                    ApiResult.Success(CustomerOrderApiResponse(id = "order_ok"))
                }
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        viewModel.start("cust_1", items)
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. First checkout attempt fails
        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, capturedKeys.size)
        val key1 = capturedKeys[0]
        assertTrue(key1.isNotBlank())

        // 2. Retry identical payload: must REUSE the idempotency key
        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, capturedKeys.size)
        assertEquals(key1, capturedKeys[1])

        // 3. Change address (fingerprint changed): must generate a NEW idempotency key
        viewModel.selectAddress(ApiAddress(id = "addr_2", addressLine = "Second St", city = "BLR", postalCode = "560002"))
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, capturedKeys.size)
        val key3 = capturedKeys[2]
        assertNotEquals(key1, key3)

        // 4. Change slot (fingerprint changed): must generate a NEW idempotency key
        viewModel.attachAppointmentSlot("2:00 PM")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(4, capturedKeys.size)
        val key4 = capturedKeys[3]
        assertNotEquals(key3, key4)
    }

    @Test
    fun testCheckoutViewModel_cartCheckoutRequestOmitsClientAmounts() = runTest {
        var capturedRequest: CartCheckoutRequest? = null
        val repo = object : FakeAppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                capturedRequest = request
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_ok"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true, deliveryFee = 25.0, coldChainFee = 0.0))
            }
        }
        val viewModel = CheckoutViewModel(repo)
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        viewModel.start("cust_1", items, couponDiscount = BigDecimal("10.00"), couponCode = "SAVE10")
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.executeCheckout("ONLINE")
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(capturedRequest)
        // Client-controlled pricing fields must be null so server calculates authoritatively
        assertNull(capturedRequest?.deliveryFee)
        assertNull(capturedRequest?.discountAmount)
        assertNull(capturedRequest?.packagingFee)
        assertEquals("SAVE10", capturedRequest?.couponCode)
    }

    @Test
    fun testCheckoutViewModel_duplicateOrderRiskBlockedAfterConfirmation() = runTest {
        var checkoutCount = 0
        val repo = object : FakeAppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                checkoutCount++
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_123"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        viewModel.start("cust_1", items)
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, checkoutCount)
        assertTrue(viewModel.uiState.state is CheckoutState.Confirmed)

        // After success, readyForPayment MUST be false and a second executeCheckout must be rejected!
        assertFalse(viewModel.uiState.readyForPayment)
        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, checkoutCount) // No second order placed!

        // Edits are also rejected after confirmation
        viewModel.attachAppointmentSlot("4:00 PM")
        assertNull(viewModel.uiState.selectedSlotText)
    }

    @Test
    fun testCartViewModel_clearAndResetResetGenerationsAndState() = runTest {
        var clearCalledOnDao = false
        val mockDao = object : CartDao {
            override fun observeCartItems(customerId: String): Flow<List<CartItemEntity>> = emptyFlow()
            override suspend fun getCartItems(customerId: String): List<CartItemEntity> = emptyList()
            override suspend fun insertOrUpdate(item: CartItemEntity) {}
            override suspend fun insertAll(items: List<CartItemEntity>) {}
            override suspend fun deleteBySku(sku: String, customerId: String) {}
            override suspend fun clearCart(customerId: String) {
                clearCalledOnDao = true
            }
        }
        val repo = object : FakeAppRepository() {}
        val viewModel = CartViewModel(repo, cartDao = mockDao)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.updateAvailableCampaigns(CartViewModel.DEFAULT_CAMPAIGNS)
        viewModel.clear()
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(clearCalledOnDao)
        assertTrue(viewModel.cartItems.isEmpty())
        assertNull(viewModel.totals)
        assertFalse(viewModel.isLoading)

        viewModel.reset()
        assertEquals("", viewModel.customerId)
        assertTrue(viewModel.saveForLaterItems.isEmpty())
        assertTrue(viewModel.availableCampaigns.isEmpty())
    }

    @Test
    fun testCartViewModel_moveToSaveForLaterPreservesCartWhenItemsNull() = runTest {
        val currentItems = mutableListOf<CartItem>()
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                currentItems.add(item)
                return ApiResult.Success(CartResponse(items = currentItems.toList()))
            }
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                currentItems.removeAll { it.sku == sku }
                // Server returns success but items is null
                return ApiResult.Success(CartResponse(items = null))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.addItem(CommerceProduct(id = "p1", sku = "s1", name = "Item 1", price = 10.0, sellingPrice = 10.0))
        viewModel.addItem(CommerceProduct(id = "p2", sku = "s2", name = "Item 2", price = 20.0, sellingPrice = 20.0))
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, viewModel.cartItems.size)

        viewModel.moveToSaveForLater("s1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.saveForLaterItems.size)
        assertEquals("s1", viewModel.saveForLaterItems[0].sku)
        // s2 must remain in cart, not wiped by null items list!
        assertEquals(1, viewModel.cartItems.size)
        assertEquals("s2", viewModel.cartItems[0].sku)
    }

    @Test
    fun testCartViewModel_offlineRoomFailureRollsBack() = runTest {
        val failingDao = object : CartDao {
            override fun observeCartItems(customerId: String): Flow<List<CartItemEntity>> = emptyFlow()
            override suspend fun getCartItems(customerId: String): List<CartItemEntity> = emptyList()
            override suspend fun insertOrUpdate(item: CartItemEntity) {
                throw RuntimeException("Room disk full")
            }
            override suspend fun insertAll(items: List<CartItemEntity>) {}
            override suspend fun deleteBySku(sku: String, customerId: String) {
                throw RuntimeException("Room disk full")
            }
            override suspend fun clearCart(customerId: String) {
                throw RuntimeException("Room disk full")
            }
        }
        val repo = object : FakeAppRepository() {}
        val viewModel = CartViewModel(repo, cartDao = failingDao)
        viewModel.init("") // Guest cart mode
        testDispatcher.scheduler.advanceUntilIdle()

        // 1. addItem failure rolls back
        val prod = CommerceProduct(id = "p1", sku = "s1", name = "Syrup", price = 100.0, sellingPrice = 100.0)
        viewModel.addItem(prod)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.cartItems.size)
        assertEquals("Could not save the offline cart.", viewModel.errorMessage)

        // 2. clear() surfaces failure
        viewModel.clear()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals("Could not clear the saved cart.", viewModel.errorMessage)
    }

    @Test
    fun testPrescriptionViewModel_serverStatusOverridesStaleLocal() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> {
                return ApiResult.Success(
                    listOf(
                        Prescription(id = "rx_1", customerId = customerId, status = "APPROVED")
                    )
                )
            }
        }
        val viewModel = PrescriptionViewModel(repo)
        viewModel.localPrescriptionsOverride = {
            listOf(Prescription(id = "rx_1", customerId = "cust_1", status = "PENDING_REVIEW"))
        }

        viewModel.load("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.prescriptions.size)
        // Pharmacist APPROVED status must override stale local status
        assertEquals("APPROVED", viewModel.prescriptions[0].status)
    }

    @Test
    fun testPrescriptionViewModel_uploadRejectsNonExistentFile() = runTest {
        val repo = object : FakeAppRepository() {}
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)

        viewModel.upload("cust_1", "Patient A", listOf("file:///non/existent/path/image.jpg"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertFalse(viewModel.isUploading)
        assertTrue(viewModel.errorMessage?.contains("Prescription image could not be read") == true)
    }

    @Test
    fun testPrescriptionViewModel_canceledUploadDoesNotClearNewJob() = runTest {
        var upload2Completed = false
        val repo = object : FakeAppRepository() {
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                if (request.patientName == "Patient 2") {
                    delay(500)
                    upload2Completed = true
                }
                return ApiResult.Success(Prescription(id = "rx_2", customerId = request.customerId))
            }
        }
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)

        // Launch upload 1
        viewModel.upload("cust_1", "Patient 1", listOf("data:image/jpeg;base64,111"))
        // Rapidly start upload 2 before 1 finishes (cancels upload 1)
        viewModel.upload("cust_1", "Patient 2", listOf("data:image/jpeg;base64,222"))

        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isUploading)

        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(upload2Completed)
        assertFalse(viewModel.isUploading)
    }

    @Test
    fun testPrescriptionViewModel_resetCancelsUploadJob() = runTest {
        var uploadCompleted = false
        val repo = object : FakeAppRepository() {
            override suspend fun uploadPrescription(
                request: UploadPrescriptionRequest
            ): ApiResult<Prescription> {
                delay(1000)
                uploadCompleted = true
                return ApiResult.Success(Prescription(id = "rx_1", customerId = request.customerId))
            }
        }
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)
        viewModel.upload("cust_1", "Patient A", listOf("data:image/jpeg;base64,abc123dummy"))

        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isUploading)
        // Reset immediately during upload
        viewModel.reset()

        assertFalse(viewModel.isUploading)
        testDispatcher.scheduler.advanceUntilIdle()

        // The delayed upload should have been cancelled before completing
        assertFalse(uploadCompleted)
    }

    @Test
    fun testCartViewModel_reorderAllConcurrentCallIgnored() = runTest {
        var callCount = 0
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                callCount++
                delay(500)
                return ApiResult.Success(CartResponse(items = listOf(item)))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()
        val orderItems = listOf(
            OrderItem(productId = "p1", sku = "sku_1", name = "Medicine A", unitPrice = BigDecimal("50.00"), quantity = 1)
        )

        // Rapid duplicate calls
        viewModel.reorderAll(orderItems)
        viewModel.reorderAll(orderItems)

        testDispatcher.scheduler.advanceUntilIdle()
        // Second call should have been ignored because first reorder was active
        assertEquals(1, callCount)
    }

    @Test
    fun testCartViewModel_reorderAllMergesDuplicatesWhenResponseItemsNull() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                // Server returns success but items is null/omitted
                return ApiResult.Success(CartResponse(items = null))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()
        val orderItems = listOf(
            OrderItem(productId = "p1", sku = "sku_same", name = "Item 1", unitPrice = BigDecimal("20.00"), quantity = 2),
            OrderItem(productId = "p2", sku = "sku_same", name = "Item 1", unitPrice = BigDecimal("20.00"), quantity = 3)
        )

        viewModel.reorderAll(orderItems)
        testDispatcher.scheduler.advanceUntilIdle()

        // Fallback should merge duplicate SKUs instead of appending duplicate rows
        assertEquals(1, viewModel.cartItems.size)
        assertEquals("sku_same", viewModel.cartItems[0].sku)
        assertEquals(5, viewModel.cartItems[0].quantity)
    }

    @Test
    fun testCartViewModel_saveForLaterPersistenceAcrossInit() = runTest {
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val store = mutableMapOf<String, String>()
        lateinit var editor: android.content.SharedPreferences.Editor
        editor = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method?.name) {
                "putString" -> { store[args[0] as String] = args[1] as String; editor }
                "putLong" -> { store[args[0] as String] = (args[1] as Long).toString(); editor }
                "remove" -> { store.remove(args[0] as String); editor }
                "commit" -> true
                "apply" -> null
                else -> editor
            }
        } as android.content.SharedPreferences.Editor

        val prefs = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, method, args ->
            when (method?.name) {
                "getString" -> store[args[0] as String] ?: (args.getOrNull(1) as? String)
                "getLong" -> (args.getOrNull(1) as? Long) ?: 0L
                "getInt" -> (args.getOrNull(1) as? Int) ?: 0
                "getBoolean" -> (args.getOrNull(1) as? Boolean) ?: false
                "edit" -> editor
                else -> null
            }
        } as android.content.SharedPreferences

        val fakeContext = object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
            override fun getApplicationContext(): android.content.Context = this
        }

        val savedCartItem = CartItem(productId = "p1", sku = "sku_save", name = "SaveMe", unitPrice = BigDecimal("10.00"), quantity = 1)
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = listOf(savedCartItem)))
            }
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = emptyList()))
            }
        }
        val viewModel1 = CartViewModel(repo, appContext = fakeContext)
        viewModel1.init("cust_1")
        viewModel1.loadCart()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel1.cartItems.size)

        viewModel1.moveToSaveForLater("sku_save")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel1.saveForLaterItems.size)

        // New ViewModel simulation
        val viewModel2 = CartViewModel(repo, appContext = fakeContext)
        viewModel2.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel2.saveForLaterItems.size)
        assertEquals("sku_save", viewModel2.saveForLaterItems[0].sku)
    }

    @Test
    fun testCheckoutViewModel_startBlockedWhenConfirmedUntilReset() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_confirmed"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        viewModel.start("cust_1", items)
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(viewModel.uiState.state is CheckoutState.Confirmed)

        // Attempting start() without reset must be rejected and preserve Confirmed state
        viewModel.start("cust_1", items)
        assertTrue(viewModel.uiState.state is CheckoutState.Confirmed)

        // Calling reset() cleanly resets state
        viewModel.reset()
        assertEquals(CheckoutState.Idle, viewModel.uiState.state)

        // Now start() works for the next checkout
        viewModel.start("cust_1", items)
        assertFalse(viewModel.uiState.state is CheckoutState.Confirmed)
    }

    @Test
    fun testPrescriptionViewModel_accountSwitchCancelsAndDropsPreviousAccountUpload() = runTest {
        var onUploadedCalled = false
        val repo = object : FakeAppRepository() {
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                delay(500)
                return ApiResult.Success(Prescription(id = "rx_cust_a", customerId = request.customerId))
            }
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> {
                return ApiResult.Success(emptyList())
            }
        }
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)
        viewModel.load("cust_A")
        testDispatcher.scheduler.advanceUntilIdle()

        // Start upload for cust_A
        viewModel.upload("cust_A", "Patient A", listOf("data:image/jpeg;base64,123")) {
            onUploadedCalled = true
        }
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isUploading)

        // Account switches to cust_B while upload for cust_A is in flight
        viewModel.load("cust_B")
        assertFalse(viewModel.isUploading) // load(newCustomer) cancels upload & sets isUploading = false

        testDispatcher.scheduler.advanceUntilIdle()

        // cust_A's prescription must NOT be added to current prescriptions or invoke onUploaded
        assertFalse(onUploadedCalled)
        assertTrue(viewModel.prescriptions.none { it.id == "rx_cust_a" })
    }

    @Test
    fun testPrescriptionViewModel_encodeAttachmentsRejectsFileOver5MiB() = runTest {
        val repo = object : FakeAppRepository() {}
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)

        val tempFile = java.io.File.createTempFile("large_rx", ".jpg")
        try {
            // Write 5 MiB + 1 byte
            val oversizedLength = (5 * 1024 * 1024) + 1
            java.io.RandomAccessFile(tempFile, "rw").use { raf ->
                raf.setLength(oversizedLength.toLong())
            }

            viewModel.upload("cust_1", "Patient 1", listOf(tempFile.absolutePath))
            testDispatcher.scheduler.advanceUntilIdle()

            assertNotNull(viewModel.errorMessage)
            assertTrue(
                viewModel.errorMessage!!.contains("5 MiB or smaller") ||
                viewModel.errorMessage!!.contains("too large")
            )
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testCartViewModel_mutationsBlockedWhileLockedForCheckout() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = listOf(item)))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()
        viewModel.lockMutations()
        assertTrue(viewModel.isLockedForCheckout)

        val prod = CommerceProduct(id = "p1", sku = "s1", name = "Prod 1", price = 100.0, sellingPrice = 100.0)
        viewModel.addItem(prod)

        // Mutation should be rejected immediately
        assertEquals(0, viewModel.cartItems.size)
        assertEquals("Cart cannot be modified while checkout is in progress.", viewModel.errorMessage)

        viewModel.unlockMutations()
        assertFalse(viewModel.isLockedForCheckout)
        viewModel.addItem(prod)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.cartItems.size)
    }

    @Test
    fun testCartViewModel_guestSaveForLaterRestoredOnInitAndReset() = runTest {
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val store = mutableMapOf<String, String>()
        lateinit var editor: android.content.SharedPreferences.Editor
        editor = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method?.name) {
                "putString" -> { store[args[0] as String] = args[1] as String; editor }
                "putLong" -> { store[args[0] as String] = (args[1] as Long).toString(); editor }
                "remove" -> { store.remove(args[0] as String); editor }
                "commit" -> true
                "apply" -> null
                else -> editor
            }
        } as android.content.SharedPreferences.Editor

        val prefs = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences::class.java.classLoader,
            arrayOf(android.content.SharedPreferences::class.java)
        ) { _, method, args ->
            when (method?.name) {
                "getString" -> store[args[0] as String] ?: (args.getOrNull(1) as? String)
                "getLong" -> (args.getOrNull(1) as? Long) ?: 0L
                "getInt" -> (args.getOrNull(1) as? Int) ?: 0
                "getBoolean" -> (args.getOrNull(1) as? Boolean) ?: false
                "edit" -> editor
                else -> null
            }
        } as android.content.SharedPreferences

        val fakeContext = object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
            override fun getApplicationContext(): android.content.Context = this
        }

        // Prepopulate guest saved items
        val guestSaved = listOf(CartItem(productId = "gp1", sku = "guest_sku", name = "Guest Item", unitPrice = BigDecimal("15.00"), quantity = 1))
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "", guestSaved)

        val repo = object : FakeAppRepository() {}
        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.saveForLaterItems.size)
        assertEquals("guest_sku", viewModel.saveForLaterItems[0].sku)

        // Switching to authenticated customer
        viewModel.init("cust_123")
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(0, viewModel.saveForLaterItems.size)

        // Resetting back to guest state must restore guest saved items
        viewModel.reset()
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, viewModel.saveForLaterItems.size)
        assertEquals("guest_sku", viewModel.saveForLaterItems[0].sku)
    }

    @Test
    fun testPrescriptionViewModel_uploadForNewCustomerCancelsPendingLoadForOldCustomer() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> {
                delay(1000)
                return ApiResult.Success(listOf(Prescription(id = "rx_old", customerId = customerId)))
            }
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                return ApiResult.Success(Prescription(id = "rx_new", customerId = request.customerId))
            }
        }
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)

        // Customer A starts a slow load
        viewModel.load("cust_A")
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isLoading)

        // Customer B starts an upload before A's load finishes
        viewModel.upload("cust_B", "Patient B", listOf("data:image/jpeg;base64,bbb"))
        testDispatcher.scheduler.runCurrent()

        // Pending load for cust_A should have been cancelled and previous list cleared
        assertFalse(viewModel.isLoading)
        assertTrue(viewModel.prescriptions.none { it.customerId == "cust_A" })

        testDispatcher.scheduler.advanceUntilIdle()
        // Final state contains only cust_B's upload
        assertEquals(1, viewModel.prescriptions.size)
        assertEquals("cust_B", viewModel.prescriptions[0].customerId)
    }

    // -------------------------------------------------------------
    // KODEX BATCH 1 RE-VERIFICATION TARGETED AUDIT TESTS
    // -------------------------------------------------------------

    @Test
    fun testCheckoutViewModel_revalidatesFreshSnapshotAfterAcquiringGateLock() = runTest {
        var checkoutAttempted = false
        val repo = object : FakeAppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                checkoutAttempted = true
                return ApiResult.Success(CustomerOrderApiResponse(id = "ord_ok"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        viewModel.start("cust_1", items)
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Gate that alters/invalidates state during awaitMutationsIdle
        val mutatingGate = object : CartMutationGate {
            override val isLockedForCheckout: Boolean get() = false
            override fun lockMutations() {}
            override fun unlockMutations() {}
            override suspend fun awaitMutationsIdle() {
                // Emulate a remote cart wipe while waiting for barrier
                viewModel.start("cust_1", emptyList())
            }
        }
        viewModel.setMutationGate(mutatingGate)

        viewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()

        // Fresh snapshot check must reject execution since cart is now empty!
        assertFalse(checkoutAttempted)
        assertFalse(viewModel.uiState.state is CheckoutState.Confirmed)
    }

    @Test
    fun testLocalSaveForLaterStore_failsClosedWhenEncryptionFailsAndDoesNotWritePlaintext() {
        val store = mutableMapOf<String, String>()
        val context = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        fakeSecureStore.shouldFailSave = true
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val items = listOf(CartItem(productId = "p1", sku = "med_1", name = "Sensitive Medicine", unitPrice = BigDecimal("50.00"), quantity = 1))
        val result = com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(context, "cust_1", items)

        // Must fail closed (return false) and never write to plaintext prefs!
        assertFalse(result)
        assertTrue(store.isEmpty())
    }

    @Test
    fun testLocalPrescriptionManager_enforcesCustomerPartitionBoundary() {
        val store = mutableMapOf<String, String>()
        val context = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val rxAlice = Prescription(id = "rx_alice_1", customerId = "cust_alice", patientName = "Alice")
        val saved = com.commerceos.android.data.local.LocalPrescriptionManager.savePrescription(context, rxAlice)
        assertTrue(saved)

        // Bob must not see Alice's prescription
        val bobList = com.commerceos.android.data.local.LocalPrescriptionManager.getPrescriptions(context, "cust_bob")
        assertTrue(bobList.isEmpty())

        // Blank customer must return empty list
        val blankList = com.commerceos.android.data.local.LocalPrescriptionManager.getAllPrescriptions(context, "")
        assertTrue(blankList.isEmpty())

        // Alice sees only Alice's prescription
        val aliceList = com.commerceos.android.data.local.LocalPrescriptionManager.getPrescriptions(context, "cust_alice")
        assertEquals(1, aliceList.size)
        assertEquals("rx_alice_1", aliceList[0].id)
    }

    @Test
    fun testLocalPrescriptionManager_preservesLegacyRecordWhenEncryptedSaveFails() {
        val store = mutableMapOf<String, String>()
        val context = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        fakeSecureStore.shouldFailSave = true
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val legacyRx = Prescription(id = "rx_legacy", customerId = "cust_1", patientName = "Legacy User")
        val legacyJson = com.google.gson.Gson().toJson(listOf(legacyRx))
        store["rx_records_cust_1"] = legacyJson

        val newRx = Prescription(id = "rx_new", customerId = "cust_1", patientName = "New User")
        val saved = com.commerceos.android.data.local.LocalPrescriptionManager.savePrescription(context, newRx)

        // Encryption failed, so save must return false and legacy plaintext must NOT be purged
        assertFalse(saved)
        assertEquals(legacyJson, store["rx_records_cust_1"])
    }

    @Test
    fun testCartItemEntity_compositePrimaryKeyDistinctPerCustomer() {
        val entityGuest = CartItemEntity(
            sku = "SKU_SAME",
            customerId = "offline_cart",
            productId = "p1",
            name = "Product 1",
            unitPrice = 10.0,
            quantity = 2
        )
        val entityAuth = CartItemEntity(
            sku = "SKU_SAME",
            customerId = "cust_user123",
            productId = "p1",
            name = "Product 1",
            unitPrice = 10.0,
            quantity = 5
        )

        // Composite primary key ensures distinct identity across customers
        assertNotEquals(entityGuest, entityAuth)
        assertEquals("offline_cart", entityGuest.customerId)
        assertEquals("cust_user123", entityAuth.customerId)
        assertEquals(entityGuest.sku, entityAuth.sku)
    }

    @Test
    fun testCartViewModel_surfacesOfflineCacheErrorWhenDaoFails() = runTest {
        val failingDao = object : CartDao {
            override fun observeCartItems(customerId: String): Flow<List<CartItemEntity>> = emptyFlow()
            override suspend fun getCartItems(customerId: String): List<CartItemEntity> = emptyList()
            override suspend fun insertOrUpdate(item: CartItemEntity) = throw RuntimeException("Disk I/O error")
            override suspend fun insertAll(items: List<CartItemEntity>) = throw RuntimeException("Disk I/O error")
            override suspend fun replaceCart(customerId: String, items: List<CartItemEntity>) = throw RuntimeException("Disk I/O error")
            override suspend fun deleteBySku(sku: String, customerId: String) {}
            override suspend fun clearCart(customerId: String) {}
        }
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = listOf(
                    CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("10.00"), quantity = 1)
                )))
            }
        }
        val viewModel = CartViewModel(repo, cartDao = failingDao)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        // Cache update failure should be surfaced in errorMessage
        assertEquals("Could not update offline cart cache.", viewModel.errorMessage)
    }
}
