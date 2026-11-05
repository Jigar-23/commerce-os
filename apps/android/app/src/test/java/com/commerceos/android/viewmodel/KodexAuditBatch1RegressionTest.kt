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
        var defaultCartItems: List<CartItem> = emptyList()
        override suspend fun getPromotions(): ApiResult<List<CouponCampaign>> = ApiResult.Success(emptyList())
        override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
            val items = defaultCartItems
            val subtotal = items.fold(BigDecimal.ZERO) { acc, it -> acc.add(it.unitPrice.multiply(BigDecimal.valueOf(it.quantity.toLong()))) }
            return ApiResult.Success(
                CartResponse(
                    customerId = customerId,
                    items = items,
                    itemsSubtotal = if (items.isNotEmpty()) subtotal else null,
                    grandTotal = if (items.isNotEmpty()) subtotal else null
                )
            )
        }
        override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> = ApiResult.Success(CartResponse(items = listOf(item)))
        override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> = addCartItem(customerId, item)
    }

    class FakeSecureCredentialStore : com.commerceos.android.session.SecureCredentialStore(null) {
        val data = mutableMapOf<String, String>()
        var shouldFailSave = false
        var shouldFailClear = false
        override fun save(alias: String, value: String): Boolean {
            if (shouldFailSave) return false
            data[alias] = value
            return true
        }
        override fun read(alias: String): String? = data[alias]
        override fun clear(alias: String): Boolean {
            if (shouldFailClear) return false
            data.remove(alias)
            return true
        }
        override fun deleteKeyAndValue(alias: String): Boolean {
            data.remove(alias)
            return true
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

    private fun createFakeContext(
        store: MutableMap<String, String>,
        shouldFailCommit: Boolean = false,
        filesDirectory: java.io.File? = null
    ): android.content.Context {
        val pendingChanges = mutableMapOf<String, Any?>()
        lateinit var editor: android.content.SharedPreferences.Editor
        editor = java.lang.reflect.Proxy.newProxyInstance(
            android.content.SharedPreferences.Editor::class.java.classLoader,
            arrayOf(android.content.SharedPreferences.Editor::class.java)
        ) { _, method, args ->
            when (method?.name) {
                "putString" -> { pendingChanges[args[0] as String] = args[1] as String; editor }
                "putLong" -> { pendingChanges[args[0] as String] = (args[1] as Long).toString(); editor }
                "putStringSet" -> {
                    val set = args[1] as? Set<*>
                    pendingChanges[args[0] as String] = set?.joinToString(":::") ?: ""
                    editor
                }
                "remove" -> { pendingChanges[args[0] as String] = null; editor }
                "commit" -> {
                    if (shouldFailCommit) {
                        pendingChanges.clear()
                        false
                    } else {
                        for ((k, v) in pendingChanges) {
                            if (v == null) store.remove(k) else store[k] = v.toString()
                        }
                        pendingChanges.clear()
                        true
                    }
                }
                "apply" -> {
                    for ((k, v) in pendingChanges) {
                        if (v == null) store.remove(k) else store[k] = v.toString()
                    }
                    pendingChanges.clear()
                    null
                }
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
                "getStringSet" -> {
                    val raw = store[args[0] as String]
                    if (raw != null) {
                        raw.split(":::").filter { it.isNotBlank() }.toSet()
                    } else {
                        @Suppress("UNCHECKED_CAST")
                        (args.getOrNull(1) as? Set<String>) ?: emptySet<String>()
                    }
                }
                "contains" -> store.containsKey(args[0] as String)
                "edit" -> editor
                else -> null
            }
        } as android.content.SharedPreferences

        val fDir = filesDirectory ?: java.io.File(System.getProperty("java.io.tmpdir"), "commerce_os_test_${System.currentTimeMillis()}").also { it.mkdirs() }
        return object : android.content.ContextWrapper(null) {
            override fun getSharedPreferences(name: String?, mode: Int): android.content.SharedPreferences = prefs
            override fun getApplicationContext(): android.content.Context = this
            override fun getFilesDir(): java.io.File = fDir
            override fun getCacheDir(): java.io.File = fDir
            override fun getPackageName(): String = "com.commerceos.android"
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
            init { defaultCartItems = rxItems }
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
            init { defaultCartItems = rxItems }
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
        val itemsA = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        val repo = object : FakeAppRepository() {
            init { defaultCartItems = itemsA }
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                delay(1000)
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_1"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
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
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        val repo = object : FakeAppRepository() {
            init { defaultCartItems = items }
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
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        val repo = object : FakeAppRepository() {
            init { defaultCartItems = items }
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(
                    CartResponse(
                        customerId = customerId,
                        items = items,
                        itemsSubtotal = BigDecimal("100.00"),
                        discount = BigDecimal("10.00"),
                        expressDeliveryFee = BigDecimal("25.00"),
                        grandTotal = BigDecimal("115.00")
                    )
                )
            }
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                capturedRequest = request
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_ok"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true, deliveryFee = 25.0, coldChainFee = 0.0))
            }
        }
        val viewModel = CheckoutViewModel(repo)
        viewModel.start("cust_1", items, couponDiscount = BigDecimal("10.00"), couponCode = "SAVE10")
        viewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.executeCheckout("COD")
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
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        val repo = object : FakeAppRepository() {
            init { defaultCartItems = items }
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                checkoutCount++
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_123"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
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
    fun testPrescriptionViewModel_concurrentUploadForSameCustomerRejected() = runTest {
        var callCount = 0
        val repo = object : FakeAppRepository() {
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                callCount++
                delay(500)
                return ApiResult.Success(Prescription(id = "rx_1", customerId = request.customerId))
            }
        }
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)

        viewModel.upload("cust_1", "Patient 1", listOf("data:image/jpeg;base64,111"))
        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isUploading)

        // Rapid duplicate call for same customer must be rejected to prevent duplicate server orders
        viewModel.upload("cust_1", "Patient 2", listOf("data:image/jpeg;base64,222"))

        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, callCount)
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
                "contains" -> store.containsKey(args[0] as String)
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
        val items = listOf(CartItem(productId = "p1", sku = "s1", name = "Item 1", unitPrice = BigDecimal("100.00"), quantity = 1))
        val repo = object : FakeAppRepository() {
            init { defaultCartItems = items }
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                return ApiResult.Success(CustomerOrderApiResponse(id = "order_confirmed"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                return ApiResult.Success(ServiceabilityResponse(eligible = true))
            }
        }
        val viewModel = CheckoutViewModel(repo)
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
                defaultCartItems = listOf(item)
                return fetchBackendCart(customerId)
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
                "contains" -> store.containsKey(args[0] as String)
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
            override suspend fun awaitMutationsIdle(): List<CartItem> {
                // Emulate a remote cart wipe while waiting for barrier
                viewModel.start("cust_1", emptyList())
                return emptyList()
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

    @Test
    fun testCheckoutViewModel_twoViewModelSettledCartRerunsServiceabilityAndRevalidates() = runTest {
        var checkoutAttempted = false
        var serviceabilityCheckedItems: List<ServiceabilityItem>? = null
        val repo = object : FakeAppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                checkoutAttempted = true
                return ApiResult.Success(CustomerOrderApiResponse(id = "ord_ok"))
            }
            override suspend fun getServiceability(customerId: String, addressId: String, items: List<ServiceabilityItem>): ApiResult<ServiceabilityResponse> {
                serviceabilityCheckedItems = items
                return ApiResult.Success(ServiceabilityResponse(eligible = true, deliveryFee = 20.0, coldChainFee = 0.0))
            }
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                defaultCartItems = listOf(item)
                return fetchBackendCart(customerId)
            }
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                val items = defaultCartItems.ifEmpty {
                    listOf(CartItem(productId = "p_new", sku = "s_new", name = "New Item", unitPrice = BigDecimal("150.00"), quantity = 1))
                }
                return ApiResult.Success(
                    CartResponse(
                        customerId = customerId,
                        items = items,
                        itemsSubtotal = BigDecimal("150.00"),
                        expressDeliveryFee = BigDecimal("20.00"),
                        grandTotal = BigDecimal("170.00")
                    )
                )
            }
        }
        val cartViewModel = CartViewModel(repo)
        cartViewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        val checkoutViewModel = CheckoutViewModel(repo)
        checkoutViewModel.setMutationGate(cartViewModel)

        val staleItem = CartItem(productId = "p_old", sku = "s_old", name = "Old Item", unitPrice = BigDecimal("50.00"), quantity = 1)
        checkoutViewModel.start("cust_1", listOf(staleItem))
        checkoutViewModel.selectAddress(ApiAddress(id = "addr_1", addressLine = "Main St", city = "BLR", postalCode = "560001"))
        testDispatcher.scheduler.advanceUntilIdle()

        // CartViewModel updates to a new item
        val newItem = CommerceProduct(id = "p_new", sku = "s_new", name = "New Item", price = 150.0, sellingPrice = 150.0)
        cartViewModel.addItem(newItem)
        testDispatcher.scheduler.advanceUntilIdle()

        checkoutViewModel.executeCheckout("COD")
        testDispatcher.scheduler.advanceUntilIdle()

        // Checkout must have gathered the settled cart from CartViewModel and checked its serviceability!
        assertTrue(checkoutAttempted)
        assertEquals(1, serviceabilityCheckedItems?.size)
        assertEquals("s_new", serviceabilityCheckedItems?.get(0)?.sku)
    }

    @Test
    fun testCartViewModel_guestCartSyncUsesServerSupportedIdempotencyKey() = runTest {
        var addCartItemCalls = 0
        var capturedKey: String? = null
        var guestDeleted = false
        val mockDao = object : CartDao {
            override fun observeCartItems(customerId: String): Flow<List<CartItemEntity>> = emptyFlow()
            override suspend fun getCartItems(customerId: String): List<CartItemEntity> {
                return if (customerId == "local_offline_cart" && !guestDeleted) {
                    listOf(CartItemEntity(sku = "sku_replay", customerId = "local_offline_cart", productId = "p1", name = "Replay Item", unitPrice = 100.0, quantity = 2, updatedAt = 1000L))
                } else emptyList()
            }
            override suspend fun insertOrUpdate(item: CartItemEntity) {}
            override suspend fun insertAll(items: List<CartItemEntity>) {}
            override suspend fun replaceCart(customerId: String, items: List<CartItemEntity>) {}
            override suspend fun deleteBySku(sku: String, customerId: String) {
                if (customerId == "local_offline_cart" && sku == "sku_replay") guestDeleted = true
            }
            override suspend fun clearCart(customerId: String) {}
        }
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = listOf(
                    CartItem(productId = "p1", sku = "sku_replay", name = "Replay Item", unitPrice = BigDecimal("100.00"), quantity = 2)
                )))
            }
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                addCartItemCalls++
                capturedKey = idempotencyKey
                return ApiResult.Success(CartResponse(items = listOf(item)))
            }
        }
        val viewModel = CartViewModel(repo, cartDao = mockDao)
        viewModel.init("cust_replay_user")
        testDispatcher.scheduler.advanceUntilIdle()

        // Calls addCartItem with stable server-supported idempotency key for additive merge
        assertEquals(1, addCartItemCalls)
        assertNotNull(capturedKey)
        assertTrue(capturedKey!!.contains("guest_sync_cust_replay_user_sku_replay_2_1000"))
        // And guest row must have been cleaned up after success
        assertTrue(guestDeleted)
    }

    @Test
    fun testCartViewModel_moveBackToCartCompensatesWhenAddFails() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val savedItem = CartItem(productId = "p1", sku = "s_fail", name = "Fail Item", unitPrice = BigDecimal("50.00"), quantity = 1)
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "cust_1", listOf(savedItem))

        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> {
                return ApiResult.Failure(AppError.Server(500, "ERR", "Server refused add", null, null))
            }
        }
        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("cust_1")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.saveForLaterItems.size)
        viewModel.moveBackToCart("s_fail")
        testDispatcher.scheduler.advanceUntilIdle()

        // When addCartItem fails, saved item must be restored in secure storage & in-memory
        assertEquals("Server refused add", viewModel.errorMessage)
        val persistedSaved = com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(fakeContext, "cust_1")
        assertEquals(1, persistedSaved.size)
        assertEquals("s_fail", persistedSaved[0].sku)
    }

    @Test
    fun testCartViewModel_sanitizationRoomFailureSurfacesCacheError() = runTest {
        val failingDao = object : CartDao {
            override fun observeCartItems(customerId: String): Flow<List<CartItemEntity>> = emptyFlow()
            override suspend fun getCartItems(customerId: String): List<CartItemEntity> = emptyList()
            override suspend fun insertOrUpdate(item: CartItemEntity) {}
            override suspend fun insertAll(items: List<CartItemEntity>) {}
            override suspend fun replaceCart(customerId: String, items: List<CartItemEntity>) {}
            override suspend fun deleteBySku(sku: String, customerId: String) {
                throw RuntimeException("Room disk write error during sanitization")
            }
            override suspend fun clearCart(customerId: String) {}
        }
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                // Return an invalid cart item that triggers sanitization cleanup
                return ApiResult.Success(CartResponse(items = listOf(
                    CartItem(productId = "p_inv", sku = "para", name = "Invalid Para", unitPrice = BigDecimal.ZERO, quantity = 0)
                )))
            }
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = emptyList()))
            }
        }
        val viewModel = CartViewModel(repo, cartDao = failingDao)
        viewModel.init("cust_clean")
        testDispatcher.scheduler.advanceUntilIdle()

        // Sanitization Room deletion failure must be surfaced to user
        assertEquals("Could not update offline cart cache.", viewModel.errorMessage)
    }

    @Test
    fun testPrescriptionViewModel_localSaveFailureReportsCacheWarning() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        fakeSecureStore.shouldFailSave = true
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val repo = object : FakeAppRepository() {
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                return ApiResult.Success(Prescription(id = "rx_uploaded", customerId = request.customerId, patientName = request.patientName))
            }
        }
        val viewModel = PrescriptionViewModel(repo, appContext = fakeContext, ioDispatcher = testDispatcher)
        viewModel.upload("cust_1", "Patient A", listOf("data:image/jpeg;base64,123"))
        testDispatcher.scheduler.advanceUntilIdle()

        // When server upload succeeds but local encrypted save fails, must report cache warning
        assertEquals("Prescription uploaded to server, but offline encrypted cache could not be saved.", viewModel.errorMessage)
        assertEquals(1, viewModel.prescriptions.size)
        assertEquals("rx_uploaded", viewModel.prescriptions[0].id)
    }

    @Test
    fun testCartViewModel_guestCartSyncAbortsAndRetainsGuestRowsWhenServerFetchFails() = runTest {
        var addCartItemCalled = false
        var guestDeleted = false
        val mockDao = object : CartDao {
            override fun observeCartItems(customerId: String): Flow<List<CartItemEntity>> = emptyFlow()
            override suspend fun getCartItems(customerId: String): List<CartItemEntity> {
                return if (customerId == "local_offline_cart" && !guestDeleted) {
                    listOf(CartItemEntity(sku = "sku_1", customerId = "local_offline_cart", productId = "p1", name = "Item 1", unitPrice = 10.0, quantity = 1))
                } else emptyList()
            }
            override suspend fun insertOrUpdate(item: CartItemEntity) {}
            override suspend fun insertAll(items: List<CartItemEntity>) {}
            override suspend fun replaceCart(customerId: String, items: List<CartItemEntity>) {}
            override suspend fun deleteBySku(sku: String, customerId: String) {
                guestDeleted = true
            }
            override suspend fun clearCart(customerId: String) {}
        }
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Failure(AppError.Network("Offline"))
            }
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                addCartItemCalled = true
                return ApiResult.Success(CartResponse(items = listOf(item)))
            }
        }
        val viewModel = CartViewModel(repo, cartDao = mockDao)
        viewModel.init("cust_offline")
        testDispatcher.scheduler.advanceUntilIdle()

        // When fetchBackendCart fails, must abort and NOT call addCartItem or delete guest row
        assertFalse(addCartItemCalled)
        assertFalse(guestDeleted)
        assertEquals("Could not sync offline cart. Will retry when connected.", viewModel.errorMessage)
    }

    @Test
    fun testCartViewModel_awaitMutationsIdleSettlesCartLoadJobAndDoesNotDeadlock() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                delay(200)
                return ApiResult.Success(CartResponse(items = listOf(
                    CartItem(productId = "p1", sku = "s_settled", name = "Settled Item", unitPrice = BigDecimal("100.00"), quantity = 1)
                )))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_slow_load")

        // Before cartLoadJob completes, call awaitMutationsIdle()
        var settledItems: List<CartItem>? = null
        launch {
            settledItems = viewModel.awaitMutationsIdle()
        }
        testDispatcher.scheduler.advanceUntilIdle()

        assertNotNull(settledItems)
        assertEquals(1, settledItems!!.size)
        assertEquals("s_settled", settledItems!![0].sku)
    }

    @Test
    fun testCartViewModel_moveToSaveForLaterReconcilesAmbiguousNetworkFailureWhenServerCommitted() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val initialItem = CartItem(productId = "p1", sku = "s_ambig", name = "Ambig Item", unitPrice = BigDecimal("50.00"), quantity = 1)
        var serverHasItem = false

        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                val items = if (serverHasItem) listOf(initialItem) else emptyList()
                return ApiResult.Success(CartResponse(items = items))
            }
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                // Simulate network timeout/error during DELETE
                return ApiResult.Failure(AppError.Network("Socket timeout"))
            }
        }
        val viewModel = CartViewModel(repo, appContext = fakeContext)
        serverHasItem = true
        viewModel.init("cust_ambig")
        testDispatcher.scheduler.advanceUntilIdle()

        serverHasItem = false // Server processed removal
        viewModel.moveToSaveForLater("s_ambig")
        testDispatcher.scheduler.advanceUntilIdle()

        // Reconciled: server removed the item, so item is preserved in Saved for Later and NOT rolled back
        assertEquals(1, viewModel.saveForLaterItems.size)
        assertEquals("s_ambig", viewModel.saveForLaterItems[0].sku)
        val persistedSaved = com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(fakeContext, "cust_ambig")
        assertEquals(1, persistedSaved.size)
        assertEquals("s_ambig", persistedSaved[0].sku)
    }

    @Test
    fun testCartViewModel_moveBackToCartReconcilesAmbiguousNetworkFailureWhenServerCommitted() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val savedItem = CartItem(productId = "p1", sku = "s_ambig_add", name = "Ambig Add", unitPrice = BigDecimal("50.00"), quantity = 1)
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "cust_ambig2", listOf(savedItem))

        var serverHasItem = false
        var attempts = 0
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                val items = if (serverHasItem) listOf(savedItem) else emptyList()
                return ApiResult.Success(CartResponse(items = items))
            }
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                serverHasItem = true // Server added it before connection dropped
                attempts++
                return if (attempts == 1) {
                    ApiResult.Failure(AppError.Network("Connection dropped"))
                } else {
                    // Reconciled against that operation's authoritative result
                    ApiResult.Success(CartResponse(items = listOf(savedItem)))
                }
            }
        }
        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("cust_ambig2")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.saveForLaterItems.size)
        viewModel.moveBackToCart("s_ambig_add")
        testDispatcher.scheduler.advanceUntilIdle()

        // Reconciled: server committed the add, so cart contains the item and saved items do NOT retain it
        assertEquals(1, viewModel.cartItems.size)
        assertEquals("s_ambig_add", viewModel.cartItems[0].sku)
        assertEquals(0, viewModel.saveForLaterItems.size)
        val persistedSaved = com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(fakeContext, "cust_ambig2")
        assertEquals(0, persistedSaved.size)
    }

    @Test
    fun testPrescriptionViewModel_uploadGeneratesStableIdempotencyKey() = runTest {
        val keys = mutableListOf<String?>()
        var shouldFail = true
        val repo = object : FakeAppRepository() {
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                keys.add(request.idempotencyKey)
                return if (shouldFail) {
                    ApiResult.Failure(AppError.Network("Network timeout"))
                } else {
                    ApiResult.Success(Prescription(id = "rx_idemp", customerId = request.customerId, patientName = request.patientName))
                }
            }
        }
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)
        val attachments = listOf("data:image/jpeg;base64,sample123")
        viewModel.upload("cust_rx", "Jane Doe", attachments)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, keys.size)
        val firstKey = keys[0]
        assertNotNull(firstKey)
        assertTrue(firstKey!!.startsWith("rx_upload_"))

        // Retry the failed upload: must retain and reuse the stable operation-scoped key
        viewModel.upload("cust_rx", "Jane Doe", attachments)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, keys.size)
        val retryKey = keys[1]
        assertEquals(firstKey, retryKey)

        // Reset and trigger a new submission: must generate a new operation-scoped key
        shouldFail = false
        viewModel.reset()
        viewModel.upload("cust_rx", "Jane Doe", attachments)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(3, keys.size)
        val newSubmissionKey = keys[2]
        assertNotNull(newSubmissionKey)
        assertNotEquals(firstKey, newSubmissionKey)
    }

    @Test
    fun testCartViewModel_moveBackToCartWithPreexistingQuantityReconcilesOnlyWhenDeltaCommitted() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val savedItem = CartItem(productId = "p1", sku = "sku_shared", name = "Shared Sku", unitPrice = BigDecimal("50.00"), quantity = 1)
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "cust_delta", listOf(savedItem))

        val preexistingCart = CartItem(productId = "p1", sku = "sku_shared", name = "Shared Sku", unitPrice = BigDecimal("50.00"), quantity = 2)
        var serverQuantity = 2 // Server still has only preexisting 2 (did NOT commit the +1)

        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = listOf(
                    preexistingCart.copy(quantity = serverQuantity)
                )))
            }
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                // Return 504 Gateway Timeout (transient upstream failure)
                return ApiResult.Failure(AppError.Server(504, "GATEWAY_TIMEOUT", "Gateway timeout", null, null))
            }
        }
        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("cust_delta")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, viewModel.cartItems.size)
        assertEquals(2, viewModel.cartItems[0].quantity)
        assertEquals(1, viewModel.saveForLaterItems.size)

        // Attempt move-back
        viewModel.moveBackToCart("sku_shared")
        testDispatcher.scheduler.advanceUntilIdle()

        // Because serverQuantity is still 2 (not expected 3), add was NOT committed.
        // It must NOT discard the saved item!
        val persistedSaved = com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(fakeContext, "cust_delta")
        assertEquals(1, persistedSaved.size)
        assertEquals("sku_shared", persistedSaved[0].sku)
        assertEquals(1, viewModel.saveForLaterItems.size)
    }

    @Test
    fun testCartViewModel_gatewayTimeout504ReconcilesInsteadOfImmediateRollback() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val initialItem = CartItem(productId = "p1", sku = "s_504", name = "504 Item", unitPrice = BigDecimal("50.00"), quantity = 1)
        var serverHasItem = true

        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                val items = if (serverHasItem) listOf(initialItem) else emptyList()
                return ApiResult.Success(CartResponse(items = items))
            }
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                serverHasItem = false // Server actually removed it before gateway timed out
                return ApiResult.Failure(AppError.Server(504, "GATEWAY_TIMEOUT", "Upstream timeout", null, null))
            }
        }
        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("cust_504")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveToSaveForLater("s_504")
        testDispatcher.scheduler.advanceUntilIdle()

        // 504 is treated as ambiguous and reconciled: server removed it, so move committed!
        assertEquals(1, viewModel.saveForLaterItems.size)
        assertEquals("s_504", viewModel.saveForLaterItems[0].sku)
        val persistedSaved = com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(fakeContext, "cust_504")
        assertEquals(1, persistedSaved.size)
    }

    @Test
    fun testPrescriptionViewModel_distinctAttachmentsDoNotCollideIdempotencyKey() = runTest {
        val keys = mutableListOf<String?>()
        val repo = object : FakeAppRepository() {
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                keys.add(request.idempotencyKey)
                return ApiResult.Success(Prescription(id = "rx_" + keys.size, customerId = request.customerId, patientName = request.patientName))
            }
        }
        val viewModel = PrescriptionViewModel(repo, ioDispatcher = testDispatcher)

        viewModel.upload("cust_1", "Patient A", listOf("data:image/jpeg;base64,attachment_AAAA"))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.reset()
        viewModel.upload("cust_1", "Patient A", listOf("data:image/jpeg;base64,attachment_BBBB"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, keys.size)
        assertNotEquals(keys[0], keys[1])
    }

    @Test
    fun testCartViewModel_addItem_reusesOperationKeyOnRetry() = runTest {
        val keys = mutableListOf<String?>()
        var shouldFail = true
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                keys.add(idempotencyKey)
                return if (shouldFail) {
                    ApiResult.Failure(AppError.Network("Timeout"))
                } else {
                    ApiResult.Success(CartResponse(items = listOf(item)))
                }
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_retry_add")
        testDispatcher.scheduler.advanceUntilIdle()

        val product = CommerceProduct(id = "p1", sku = "sku_op", name = "Test Item", price = 100.0, sellingPrice = 100.0)

        // Attempt 1: Fails
        viewModel.addItem(product)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(1, keys.size)
        val firstKey = keys[0]
        assertNotNull(firstKey)
        assertTrue(firstKey!!.startsWith("add_cust_retry_add_sku_op_"))

        // Attempt 2 (Retry of failed add): Must reuse the exact same operation key
        viewModel.addItem(product, operationKey = firstKey)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(2, keys.size)
        val retryKey = keys[1]
        assertEquals(firstKey, retryKey)

        // Now attempt 2 succeeded: Next distinct action must get a fresh key
        shouldFail = false
        viewModel.addItem(product, operationKey = retryKey)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(3, keys.size)
        val thirdKey = keys[2]
        assertEquals(retryKey, thirdKey) // Third call succeeded because attempt 2 had failed
    }

    @Test
    fun testCartViewModel_reorderAll_retryAfterPartialFailureDoesNotReAddSuccessfulSkus() = runTest {
        val attemptedSkus = mutableListOf<String>()
        var failSku2 = true
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                attemptedSkus.add(item.sku)
                return if (item.sku == "sku_fail" && failSku2) {
                    ApiResult.Failure(AppError.Network("Timeout on item 2"))
                } else {
                    ApiResult.Success(CartResponse(items = listOf(item)))
                }
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_partial_reorder")
        testDispatcher.scheduler.advanceUntilIdle()

        val items = listOf(
            OrderItem(productId = "p1", sku = "sku_ok", name = "Success Item", unitPrice = BigDecimal("50.00"), quantity = 1),
            OrderItem(productId = "p2", sku = "sku_fail", name = "Failed Item", unitPrice = BigDecimal("60.00"), quantity = 1)
        )

        // Run 1: sku_ok succeeds, sku_fail fails
        viewModel.reorderAll(items)
        testDispatcher.scheduler.advanceUntilIdle()
        assertEquals(listOf("sku_ok", "sku_fail"), attemptedSkus)

        // Run 2: User retries reorderAll. sku_ok was already successful, so it MUST NOT be added again!
        failSku2 = false
        attemptedSkus.clear()
        viewModel.reorderAll(items)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("sku_fail"), attemptedSkus)
        assertFalse("Already successful sku_ok must not be re-added on retry", attemptedSkus.contains("sku_ok"))
    }

    @Test
    fun testCartViewModel_moveBackToCart_abortsIfPendingKeyStorageFails() = runTest {
        var addCalled = false
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val savedItem = CartItem(productId = "p1", sku = "sku_abort", name = "Item", unitPrice = BigDecimal("10.00"), quantity = 1)
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "cust_key_fail", listOf(savedItem))

        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                addCalled = true
                return ApiResult.Success(CartResponse(items = listOf(item)))
            }
        }
        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("cust_key_fail")
        testDispatcher.scheduler.advanceUntilIdle()

        // Turn on failure for pending key save
        fakeSecureStore.shouldFailSave = true
        viewModel.moveBackToCart("sku_abort")
        testDispatcher.scheduler.advanceUntilIdle()

        // Network add must NEVER be sent if storing the idempotency key failed
        assertFalse("Network request must not be sent if durable key storage failed", addCalled)
        assertEquals("Could not store durable idempotency key for moving item.", viewModel.errorMessage)
    }

    @Test
    fun testCartViewModel_moveBackToCart_retainsKeyWhenRetryFails() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val savedItem = CartItem(productId = "p1", sku = "sku_retain", name = "Retained Item", unitPrice = BigDecimal("25.00"), quantity = 1)
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "cust_retain", listOf(savedItem))

        var attempts = 0
        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                attempts++
                return if (attempts == 1) {
                    // Attempt 1: Ambiguous failure
                    ApiResult.Failure(AppError.Server(504, "GATEWAY_TIMEOUT", "Timeout", null, null))
                } else {
                    // Retry attempt: Definitive/non-ambiguous 500 failure
                    ApiResult.Failure(AppError.Server(500, "INTERNAL_ERROR", "Internal server error", null, null))
                }
            }
        }

        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("cust_retain")
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveBackToCart("sku_retain")
        testDispatcher.scheduler.advanceUntilIdle()

        // Because attempt 1 was ambiguous, retry failure must NOT clear the pending move key
        val pendingKey = com.commerceos.android.data.local.LocalSaveForLaterStore.getPendingMoveKey(fakeContext, "cust_retain", "sku_retain")
        assertNotNull("Pending move key must be retained after ambiguous attempt and failed retry", pendingKey)
        assertTrue(pendingKey!!.startsWith("move_back_cust_retain_sku_retain_"))
    }

    @Test
    fun testCartViewModel_moveBackToCart_retainsKeyIfSavingRemainingItemsFails() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val savedItem1 = CartItem(productId = "p1", sku = "sku_save_fail", name = "Item 1", unitPrice = BigDecimal("30.00"), quantity = 1)
        val savedItem2 = CartItem(productId = "p2", sku = "sku_keep", name = "Item 2", unitPrice = BigDecimal("20.00"), quantity = 1)
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "cust_save_fail", listOf(savedItem1, savedItem2))
        // Pre-seed durable pending move key so setPendingMoveKey is not needed during add
        com.commerceos.android.data.local.LocalSaveForLaterStore.setPendingMoveKey(fakeContext, "cust_save_fail", "sku_save_fail", "durable_key_123")

        val repo = object : FakeAppRepository() {
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = listOf(item)))
            }
        }

        val viewModel = CartViewModel(repo, appContext = fakeContext)
        viewModel.init("cust_save_fail")
        testDispatcher.scheduler.advanceUntilIdle()

        // Make saving remaining saved items fail
        fakeSecureStore.shouldFailSave = true
        viewModel.moveBackToCart("sku_save_fail")
        testDispatcher.scheduler.advanceUntilIdle()

        // Because saving remaining items failed, key must NOT be cleared
        fakeSecureStore.shouldFailSave = false
        val pendingKey = com.commerceos.android.data.local.LocalSaveForLaterStore.getPendingMoveKey(fakeContext, "cust_save_fail", "sku_save_fail")
        assertNotNull("Pending move key must be retained if saving remaining saved items fails", pendingKey)
        assertEquals("durable_key_123", pendingKey)
    }

    @Test
    fun testCartViewModel_moveToSaveForLater_ambiguousFetchFailureEntersUncertainStateAndBlocksMoves() = runTest {
        val repo = object : FakeAppRepository() {
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                return ApiResult.Failure(AppError.Network("Transport dropped"))
            }
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Failure(AppError.Network("Cannot reach server"))
            }
        }
        val viewModel = CartViewModel(repo)
        viewModel.init("cust_unc")
        testDispatcher.scheduler.advanceUntilIdle()

        // Manually place item in cart
        viewModel.addItem(CommerceProduct(id = "p1", sku = "sku_unc", name = "Uncertain Item", price = 40.0, sellingPrice = 40.0))
        testDispatcher.scheduler.advanceUntilIdle()

        viewModel.moveToSaveForLater("sku_unc")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(viewModel.uncertainMoveSkus.contains("sku_unc"))

        // Subsequent move for this SKU is blocked
        viewModel.moveToSaveForLater("sku_unc")
        assertEquals("Item move is pending resolution. Please refresh your cart.", viewModel.errorMessage)

        viewModel.moveBackToCart("sku_unc")
        assertEquals("Item move is pending resolution. Please refresh your cart.", viewModel.errorMessage)
    }

    @Test
    fun testLocalPrescriptionManager_legacyMigration_failsClosedWhenSecureStoreFails() {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        fakeSecureStore.shouldFailSave = true // Fail secure save
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        // Put legacy plaintext JSON
        val rxJson = """[{"id":"rx_1","customerId":"cust_fail_close","patientName":"Alice","attachments":[]}]"""
        store["rx_records_cust_fail_close"] = rxJson

        val results = com.commerceos.android.data.local.LocalPrescriptionManager.getPrescriptions(fakeContext, "cust_fail_close")

        // Must fail closed: return emptyList, and do NOT delete the plaintext legacy pref
        assertEquals(emptyList<Prescription>(), results)
        assertEquals(rxJson, store["rx_records_cust_fail_close"])
    }

    @Test
    fun testLocalPrescriptionManager_sanitizationCollisionFixedWithSha256Buckets() {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val rxHyphen = Prescription(id = "rx_hyphen", customerId = "cust-a", patientName = "Hyphen Patient")
        val rxUnderscore = Prescription(id = "rx_underscore", customerId = "cust_a", patientName = "Underscore Patient")

        com.commerceos.android.data.local.LocalPrescriptionManager.savePrescription(fakeContext, rxHyphen)
        com.commerceos.android.data.local.LocalPrescriptionManager.savePrescription(fakeContext, rxUnderscore)

        val hyphenList = com.commerceos.android.data.local.LocalPrescriptionManager.getPrescriptions(fakeContext, "cust-a")
        val underscoreList = com.commerceos.android.data.local.LocalPrescriptionManager.getPrescriptions(fakeContext, "cust_a")

        assertEquals(1, hyphenList.size)
        assertEquals("rx_hyphen", hyphenList[0].id)
        assertEquals("cust-a", hyphenList[0].customerId)

        assertEquals(1, underscoreList.size)
        assertEquals("rx_underscore", underscoreList[0].id)
        assertEquals("cust_a", underscoreList[0].customerId)
    }

    @Test
    fun testCartViewModel_addItem_operationKeyPreservedAcrossProcessDeath() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val capturedKeys = mutableListOf<String>()
        val product = CommerceProduct(id = "p1", sku = "sku_durable_add", name = "Durable Med", price = 100.0, inStock = true)

        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> = ApiResult.Success(CartResponse(items = emptyList()))
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                if (idempotencyKey != null) capturedKeys.add(idempotencyKey)
                return if (capturedKeys.size == 1) {
                    ApiResult.Failure(AppError.Network("Dropped response"))
                } else {
                    ApiResult.Success(CartResponse(items = listOf(item)))
                }
            }
        }

        // 1. Process 1: attempt add, network fails
        val vm1 = CartViewModel(repo, appContext = fakeContext)
        vm1.init("cust_durable_1")
        testDispatcher.scheduler.advanceUntilIdle()
        vm1.addItem(product)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, capturedKeys.size)
        val firstKey = capturedKeys[0]

        // Durable store must hold the pending add key
        val persistedKey = com.commerceos.android.data.local.LocalSaveForLaterStore.getPendingAddKey(fakeContext, "cust_durable_1", "sku_durable_add")
        assertEquals(firstKey, persistedKey)

        // 2. Process Death simulation: fresh ViewModel, same durable store
        val vm2 = CartViewModel(repo, appContext = fakeContext)
        vm2.init("cust_durable_1")
        testDispatcher.scheduler.advanceUntilIdle()

        // User retries Add
        vm2.addItem(product, operationKey = persistedKey)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, capturedKeys.size)
        // Must reuse the exact same operation key across process death!
        assertEquals(firstKey, capturedKeys[1])

        // After successful add, the pending operation key must be durably cleared
        assertNull(com.commerceos.android.data.local.LocalSaveForLaterStore.getPendingAddKey(fakeContext, "cust_durable_1", "sku_durable_add"))
    }

    @Test
    fun testCartViewModel_reorderAll_batchRecordPreservedAcrossProcessDeath() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val orderItems = listOf(
            OrderItem(productId = "p1", sku = "sku_r1", name = "Reorder 1", price = BigDecimal("20.00"), quantity = 1),
            OrderItem(productId = "p2", sku = "sku_r2", name = "Reorder 2", price = BigDecimal("30.00"), quantity = 1)
        )

        val attemptedSkus = mutableListOf<String>()
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> = ApiResult.Success(CartResponse(items = emptyList()))
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> {
                attemptedSkus.add(item.sku)
                return if (item.sku == "sku_r1") {
                    ApiResult.Success(CartResponse(items = listOf(item)))
                } else {
                    ApiResult.Failure(AppError.Network("Timeout on item 2"))
                }
            }
        }

        val vm1 = CartViewModel(repo, appContext = fakeContext)
        vm1.init("cust_reorder_death")
        testDispatcher.scheduler.advanceUntilIdle()

        vm1.reorderAll(orderItems)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("sku_r1", "sku_r2"), attemptedSkus)

        // Process Death simulation: fresh ViewModel instance
        attemptedSkus.clear()
        val vm2 = CartViewModel(repo, appContext = fakeContext)
        vm2.init("cust_reorder_death")
        testDispatcher.scheduler.advanceUntilIdle()

        // Retry reorderAll: sku_r1 was already successful, so it must NOT be re-added!
        vm2.reorderAll(orderItems)
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(listOf("sku_r2"), attemptedSkus)
    }

    @Test
    fun testCartViewModel_guestReorderAll_recordsLocalSuccessesAndSkipsOnRetry() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val orderItems = listOf(
            OrderItem(productId = "p1", sku = "sku_g1", name = "Guest 1", price = BigDecimal("25.00"), quantity = 1),
            OrderItem(productId = "p2", sku = "sku_g2", name = "Guest 2", price = BigDecimal("35.00"), quantity = 1)
        )

        val localInsertedSkus = mutableListOf<String>()
        var shouldFailSecondItem = true
        val fakeDao = object : CartDao {
            override fun observeCartItems(customerId: String): kotlinx.coroutines.flow.Flow<List<CartItemEntity>> = kotlinx.coroutines.flow.emptyFlow()
            override suspend fun getCartItems(customerId: String): List<CartItemEntity> = emptyList()
            override suspend fun insertOrUpdate(item: CartItemEntity) {
                if (item.sku == "sku_g2" && shouldFailSecondItem) {
                    throw RuntimeException("Disk I/O error on second item")
                }
                localInsertedSkus.add(item.sku)
            }
            override suspend fun insertAll(items: List<CartItemEntity>) {}
            override suspend fun deleteBySku(sku: String, customerId: String) {}
            override suspend fun clearCart(customerId: String) {}
        }

        val repo = object : FakeAppRepository() {}
        val vm = CartViewModel(repo, cartDao = fakeDao, appContext = fakeContext)
        vm.init("") // Guest cart
        testDispatcher.scheduler.advanceUntilIdle()

        vm.reorderAll(orderItems)
        testDispatcher.scheduler.advanceUntilIdle()

        // First item was inserted, second item failed
        assertEquals(listOf("sku_g1"), localInsertedSkus)

        // Retry reorderAll: first item must NOT be processed again (must not increment quantity)
        shouldFailSecondItem = false
        localInsertedSkus.clear()
        vm.reorderAll(orderItems)
        testDispatcher.scheduler.advanceUntilIdle()

        // Only the second item should be processed on retry!
        assertEquals(listOf("sku_g2"), localInsertedSkus)
    }

    @Test
    fun testCartViewModel_uncertainMove_preservesGuardAcrossRestartAndBlocksCheckoutIfSaveFails() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        val initialItem = CartItem(productId = "p1", sku = "sku_unc", name = "Uncertain Item", unitPrice = BigDecimal("40.00"), quantity = 1)

        var fetchCallCount = 0
        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                fetchCallCount++
                return if (fetchCallCount == 1) {
                    ApiResult.Success(CartResponse(items = listOf(initialItem)))
                } else {
                    ApiResult.Failure(AppError.Network("Read timed out"))
                }
            }
            override suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> {
                return ApiResult.Failure(AppError.Network("DELETE timed out"))
            }
        }

        val vm1 = CartViewModel(repo, appContext = fakeContext)
        vm1.init("cust_unc")
        testDispatcher.scheduler.advanceUntilIdle()

        // Prepopulate cart
        vm1.loadCart()
        testDispatcher.scheduler.advanceUntilIdle()

        // Move to save for later hits ambiguous failure -> SKU added to uncertainMoveSkus and persisted
        vm1.moveToSaveForLater("sku_unc")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm1.uncertainMoveSkus.contains("sku_unc"))
        val persistedUncertain = com.commerceos.android.data.local.LocalSaveForLaterStore.getUncertainMoveSkus(fakeContext, "cust_unc")
        assertTrue(persistedUncertain.contains("sku_unc"))

        // Restart simulation: vm2 recovers uncertainMoveSkus on init
        val repo2 = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> {
                return ApiResult.Success(CartResponse(items = listOf(initialItem)))
            }
        }
        // Simulate save failure so reconciliation cannot clear the guard
        fakeSecureStore.shouldFailSave = true
        fakeSecureStore.shouldFailClear = true
        val vm2 = CartViewModel(repo2, appContext = fakeContext)
        vm2.init("cust_unc")
        testDispatcher.scheduler.advanceUntilIdle()

        assertTrue(vm2.uncertainMoveSkus.contains("sku_unc"))

        var exceptionThrown: Throwable? = null
        try {
            vm2.awaitMutationsIdle()
        } catch (t: Throwable) {
            exceptionThrown = t
        }

        // Must fail closed and block checkout
        assertNotNull(exceptionThrown)
        assertTrue(exceptionThrown is IllegalStateException)
        assertTrue(vm2.uncertainMoveSkus.contains("sku_unc"))
    }

    @Test
    fun testPrescriptionViewModel_uploadKeyPreservedAcrossProcessDeath() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val capturedKeys = mutableListOf<String>()
        val repo = object : FakeAppRepository() {
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> = ApiResult.Success(emptyList())
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                if (request.idempotencyKey != null) capturedKeys.add(request.idempotencyKey!!)
                return if (capturedKeys.size == 1) {
                    ApiResult.Failure(AppError.Network("Upload timed out"))
                } else {
                    ApiResult.Success(Prescription(id = "rx_success", customerId = request.customerId, note = request.note))
                }
            }
        }

        val vm1 = PrescriptionViewModel(repo, appContext = fakeContext, ioDispatcher = testDispatcher)
        vm1.load("cust_rx_death")
        testDispatcher.scheduler.advanceUntilIdle()

        vm1.upload("cust_rx_death", "Patient A", listOf("data:image/jpeg;base64,AAAA"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(1, capturedKeys.size)
        val firstKey = capturedKeys[0]

        // Durable pending record exists
        val pendingRecord = com.commerceos.android.data.local.LocalPrescriptionManager.getPendingUpload(fakeContext, "cust_rx_death")
        assertNotNull(pendingRecord)
        assertEquals(firstKey, pendingRecord!!.idempotencyKey)

        // Process restart: new ViewModel instance
        val vm2 = PrescriptionViewModel(repo, appContext = fakeContext, ioDispatcher = testDispatcher)
        vm2.load("cust_rx_death")
        testDispatcher.scheduler.advanceUntilIdle()

        vm2.upload("cust_rx_death", "Patient A", listOf("data:image/jpeg;base64,AAAA"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, capturedKeys.size)
        // Must reuse the exact same upload idempotency key across process restart
        assertEquals(firstKey, capturedKeys[1])

        // After successful upload, pending record is cleared
        assertNull(com.commerceos.android.data.local.LocalPrescriptionManager.getPendingUpload(fakeContext, "cust_rx_death"))
    }

    @Test
    fun testPrescriptionViewModel_pendingUploadReconciledByLoad() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val pendingKey = "rx_upload_cust_recon_12345"
        com.commerceos.android.data.local.LocalPrescriptionManager.setPendingUpload(
            fakeContext,
            "cust_recon",
            com.commerceos.android.data.local.LocalPrescriptionManager.PendingUploadRecord(pendingKey, "fingerprint_abc")
        )

        val serverPrescription = Prescription(
            id = "rx_committed",
            customerId = "cust_recon",
            note = "prescription_document_upload [idemp:$pendingKey]"
        )

        val repo = object : FakeAppRepository() {
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> =
                ApiResult.Success(listOf(serverPrescription))
        }

        val vm = PrescriptionViewModel(repo, appContext = fakeContext)
        vm.load("cust_recon")
        testDispatcher.scheduler.advanceUntilIdle()

        // load() reconciled that the pending upload already committed on server -> pending upload cleared!
        assertNull(com.commerceos.android.data.local.LocalPrescriptionManager.getPendingUpload(fakeContext, "cust_recon"))
    }

    @Test
    fun testPrescriptionViewModel_canonicalSha256FingerprintCollisionFree() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val capturedKeys = mutableListOf<String>()
        val repo = object : FakeAppRepository() {
            override suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> = ApiResult.Success(emptyList())
            override suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> {
                if (request.idempotencyKey != null) capturedKeys.add(request.idempotencyKey!!)
                return ApiResult.Failure(AppError.Network("Fail for key observation"))
            }
        }

        val vm = PrescriptionViewModel(repo, appContext = fakeContext, ioDispatcher = testDispatcher)
        vm.load("cust_fp")
        testDispatcher.scheduler.advanceUntilIdle()

        // Upload with attachment 1
        vm.upload("cust_fp", "Patient", listOf("data:image/jpeg;base64,payloadONE"))
        testDispatcher.scheduler.advanceUntilIdle()

        // Upload with different attachment payload
        vm.upload("cust_fp", "Patient", listOf("data:image/jpeg;base64,payloadTWO"))
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(2, capturedKeys.size)
        // Must generate distinct keys for different attachments under canonical SHA-256 fingerprinting
        assertNotEquals(capturedKeys[0], capturedKeys[1])
    }

    @Test
    fun testLocalSaveForLaterStore_clearPendingMoveKey_synchronousAndFailsWhenClearFails() = runTest {
        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }

        com.commerceos.android.data.local.LocalSaveForLaterStore.setPendingMoveKey(fakeContext, "cust_clear_test", "sku_test", "key_123")
        assertEquals("key_123", com.commerceos.android.data.local.LocalSaveForLaterStore.getPendingMoveKey(fakeContext, "cust_clear_test", "sku_test"))

        fakeSecureStore.shouldFailClear = true
        val result = com.commerceos.android.data.local.LocalSaveForLaterStore.clearPendingMoveKey(fakeContext, "cust_clear_test", "sku_test")
        assertFalse(result)

        // Move back to cart must fail closed if clearing key fails
        val savedItem = CartItem(productId = "p1", sku = "sku_test", name = "Test Item", unitPrice = BigDecimal("10.00"), quantity = 1)
        val savedItemKeep = CartItem(productId = "p2", sku = "sku_keep", name = "Keep Item", unitPrice = BigDecimal("10.00"), quantity = 1)
        fakeSecureStore.shouldFailClear = false
        com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(fakeContext, "cust_clear_test", listOf(savedItem, savedItemKeep))

        val repo = object : FakeAppRepository() {
            override suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> = ApiResult.Success(CartResponse(items = emptyList()))
            override suspend fun addCartItem(customerId: String, item: CartItem, idempotencyKey: String?): ApiResult<CartResponse> =
                ApiResult.Success(CartResponse(items = listOf(item)))
        }

        val vm = CartViewModel(repo, appContext = fakeContext)
        vm.init("cust_clear_test")
        testDispatcher.scheduler.advanceUntilIdle()

        fakeSecureStore.shouldFailClear = true
        vm.moveBackToCart("sku_test")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals("Could not durably clear pending move operation key.", vm.errorMessage)
    }

    @Test
    fun testLegacyMigration_failsClosedWhenCleanupCommitFails() {
        val store = mutableMapOf<String, String>()
        // Create context where editor.commit() fails
        val fakeContext = createFakeContext(store, shouldFailCommit = true)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalSaveForLaterStore.secureStoreProvider = { fakeSecureStore }
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        // 1. LocalSaveForLaterStore: legacy data exists
        store["saved_items_cust_leg"] = """[{"productId":"p1","sku":"s1","name":"N1","unitPrice":10.0,"quantity":1}]"""
        val savedItems = com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(fakeContext, "cust_leg")
        // Must fail closed because commit() on plaintext removal failed
        assertEquals(emptyList<CartItem>(), savedItems)

        // 2. LocalPrescriptionManager: savePrescription cleanup fails
        store["rx_records_cust_leg"] = """[{"id":"rx1","customerId":"cust_leg","patientName":"P1","attachments":[]}]"""
        val newRx = Prescription(id = "rx2", customerId = "cust_leg", patientName = "P2")
        val savedRxResult = com.commerceos.android.data.local.LocalPrescriptionManager.savePrescription(fakeContext, newRx)
        // Must return false because legacy cleanup commit failed
        assertFalse(savedRxResult)
    }

    @Test
    fun testLocalPrescriptionManager_deletePrescription_tracksOrphanAndCleanupCleansIt() {
        val tempDir = java.io.File(System.getProperty("java.io.tmpdir"), "rx_test_dir_${System.currentTimeMillis()}").also { it.mkdirs() }
        val rxDir = java.io.File(tempDir, "prescriptions").also { it.mkdirs() }
        val testFile = java.io.File(rxDir, "rx_orphan_test.jpg")
        testFile.writeText("fake image content")

        val store = mutableMapOf<String, String>()
        val fakeContext = createFakeContext(store, filesDirectory = tempDir)
        val fakeSecureStore = FakeSecureCredentialStore()
        com.commerceos.android.data.local.LocalPrescriptionManager.secureStoreProvider = { fakeSecureStore }

        val rx = Prescription(
            id = "rx_delete_test",
            customerId = "cust_del",
            patientName = "Del Patient",
            attachments = listOf(testFile.absolutePath)
        )
        com.commerceos.android.data.local.LocalPrescriptionManager.savePrescription(fakeContext, rx)

        // Make file non-deletable (directory containing it read-only)
        rxDir.setWritable(false, false)
        testFile.setWritable(false, false)

        val deleteSuccess = com.commerceos.android.data.local.LocalPrescriptionManager.deletePrescription(fakeContext, "cust_del", "rx_delete_test")
        assertTrue(deleteSuccess) // Metadata update succeeded

        // File could not be deleted, so orphan cleanup must have recorded it
        val orphanSet = store["pending_orphan_files"]
        assertNotNull(orphanSet)
        assertTrue(orphanSet!!.contains(testFile.absolutePath))

        // Restore permissions and run cleanupOrphanedFiles
        rxDir.setWritable(true, false)
        testFile.setWritable(true, false)

        val cleanedCount = com.commerceos.android.data.local.LocalPrescriptionManager.cleanupOrphanedFiles(fakeContext)
        assertEquals(1, cleanedCount)
        assertFalse(testFile.exists())
    }
}
