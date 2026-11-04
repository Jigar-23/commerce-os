package com.commerceos.android.viewmodel

import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
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

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // -------------------------------------------------------------
    // CART VIEW MODEL TESTS
    // -------------------------------------------------------------

    @Test
    fun testCartViewModel_accountSwitchClearsPreviousCart() = runTest {
        val repo = object : AppRepository() {
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
        val repo = object : AppRepository() {}
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
        val repo = object : AppRepository() {}
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
        val repo = object : AppRepository() {
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
        val repo = object : AppRepository() {}
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
        val repo = object : AppRepository() {}
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
        val repoUnapproved = object : AppRepository() {
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
        val repoApproved = object : AppRepository() {
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
        val repo = object : AppRepository() {}
        val viewModel = PrescriptionViewModel(repo)

        viewModel.load("   ")
        testDispatcher.scheduler.advanceUntilIdle()

        assertEquals(0, viewModel.prescriptions.size)
        assertEquals("Sign in to view prescriptions.", viewModel.errorMessage)
    }

    @Test
    fun testPrescriptionViewModel_uploadRequiresAuthAndAttachments() = runTest {
        val repo = object : AppRepository() {}
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
        val repo = object : AppRepository() {
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
        val repo = object : AppRepository() {
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
        val repo = object : AppRepository() {
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
        val repo = object : AppRepository() {
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
        val repo = object : AppRepository() {
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
        val repo = object : AppRepository() {
            override suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> {
                capturedKeys.add(request.idempotencyKey ?: "")
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
    }

    @Test
    fun testCheckoutViewModel_cartCheckoutRequestOmitsClientAmounts() = runTest {
        var capturedRequest: CartCheckoutRequest? = null
        val repo = object : AppRepository() {
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
    fun testPrescriptionViewModel_resetCancelsUploadJob() = runTest {
        var uploadCompleted = false
        val repo = object : AppRepository() {
            override suspend fun uploadPrescription(
                request: UploadPrescriptionRequest
            ): ApiResult<Prescription> {
                delay(1000)
                uploadCompleted = true
                return ApiResult.Success(Prescription(id = "rx_1", customerId = request.customerId))
            }
        }
        val viewModel = PrescriptionViewModel(repo)
        viewModel.upload("cust_1", "Patient A", listOf("data:image/jpeg;base64,abc123dummy"))

        testDispatcher.scheduler.runCurrent()
        assertTrue(viewModel.isLoading)
        // Reset immediately during upload
        viewModel.reset()

        assertFalse(viewModel.isLoading)
        testDispatcher.scheduler.advanceUntilIdle()

        // The delayed upload should have been cancelled before completing
        assertFalse(uploadCompleted)
    }
}
