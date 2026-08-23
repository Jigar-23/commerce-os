package com.commerceos.android.repository

import com.commerceos.android.model.*
import com.commerceos.android.network.Api
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.DeleteAddressResponse
import com.commerceos.android.network.NetworkClient
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.math.BigDecimal

/**
 * Data gate to the CommerceOS API gateway. Every method returns [ApiResult]:
 * [ApiResult.Success] with the payload (including legitimately-empty lists) or
 * [ApiResult.Failure] with a typed error. ViewModels MUST treat these as
 * distinct — an empty storefront and an unreachable server are not the same.
 */
open class AppRepository {

    suspend fun loginCustomer(request: LoginRequest): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.authApi.login(request) } }

    suspend fun registerCustomer(request: RegisterRequest): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.authApi.register(request) } }

    suspend fun sendOtp(phone: String): ApiResult<SendOtpResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.authApi.sendOtp(SendOtpRequest(phone)) } }

    suspend fun verifyOtp(challengeId: String, phone: String, otpCode: String): ApiResult<AuthResponse> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.authApi.verifyOtp(VerifyOtpRequest(challengeId, phone, otpCode)) }
        }

    open suspend fun getMedicines(query: String = ""): ApiResult<List<ApiMedicine>> =
        withContext(Dispatchers.IO) {
            Api.run {
                if (query.isBlank()) {
                    NetworkClient.catalogApi.getMedicines().content
                } else {
                    NetworkClient.catalogApi.searchMedicines(query).content
                }
            }
        }

    /** Server-composed home shelves (hero, buy-again, honest popular, deals, feed). */
    open suspend fun getHomeFeed(customerId: String, addressId: String? = null): ApiResult<HomeFeedResponse> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.catalogApi.getHomeFeed(customerId, addressId) }
        }

    /** Server-composed vertical hub feed for health, food, grocery, fashion, electronics, services. */
    open suspend fun getVerticalHomeFeed(verticalId: String, addressId: String? = null): ApiResult<VerticalHomeFeedResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.catalogApi.getVerticalHomeFeed(verticalId, addressId) } }

    /**
     * Generic Product Catalog query. Sends structured filtering parameters (brandId, storeId,
     * collectionId, campaignId, offerId, vertical, categoryId, query) directly to backend.
     */
    open suspend fun queryCatalogProducts(
        query: String,
        category: String,
        categoryId: String? = null,
        brandId: String? = null,
        storeId: String? = null,
        collectionId: String? = null,
        campaignId: String? = null,
        offerId: String? = null,
        vertical: String? = null,
        priceBand: PriceBand?,
        limit: Int,
        offset: Int
    ): ApiResult<PageResponse<CommerceProduct>> =
        withContext(Dispatchers.IO) {
            Api.run {
                NetworkClient.catalogApi.getProducts(
                    query = query.takeIf { it.isNotBlank() },
                    category = category.takeIf { it.isNotBlank() },
                    categoryId = categoryId,
                    brandId = brandId,
                    storeId = storeId,
                    collectionId = collectionId,
                    campaignId = campaignId,
                    offerId = offerId,
                    vertical = vertical,
                    minPrice = priceBand?.min,
                    maxPrice = priceBand?.max,
                    limit = limit,
                    offset = offset
                )
            }
        }

    suspend fun getDestinations(): ApiResult<List<Destination>> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.catalogApi.getDestinations() } }

    suspend fun getMedicinesByCategory(category: String): ApiResult<List<ApiMedicine>> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.catalogApi.getMedicinesByCategory(category).content }
        }

    suspend fun getCatalogCategories(): ApiResult<List<CatalogCategory>> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.catalogApi.getCategories().content }
        }

    suspend fun getMedicineById(id: String): ApiResult<ApiMedicine> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.catalogApi.getMedicineById(id) } }

    /** Generic Product Detail endpoint returning CommerceProduct — no health fallback for non-health products. */
    open suspend fun getProductDetail(productId: String, verticalId: String? = null): ApiResult<CommerceProduct> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.catalogApi.getProductById(productId, verticalId) }
        }

    /** Legacy medicine detail incl. medicine info + live-computed equivalent substitutes. */
    suspend fun getMedicineDetail(id: String): ApiResult<MedicineDetail> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.catalogApi.getMedicineDetail(id) } }

    suspend fun fetchBackendCart(customerId: String): ApiResult<CartResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.cartApi.getCart(customerId) } }

    suspend fun addCartItem(customerId: String, item: CartItem): ApiResult<CartResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.cartApi.addItem(customerId, item) } }

    suspend fun removeCartItem(customerId: String, sku: String): ApiResult<CartResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.cartApi.removeItem(customerId, sku) } }

    suspend fun updateCartQuantity(customerId: String, sku: String, quantity: Int): ApiResult<CartResponse> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.cartApi.updateQuantity(customerId, sku, CartQuantityUpdateRequest(quantity)) }
        }

    suspend fun checkoutFromCart(customerId: String, request: CartCheckoutRequest): ApiResult<CustomerOrderApiResponse> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.orderApi.checkoutFromCart(customerId, request) }
        }

    suspend fun getOrderById(orderId: String): ApiResult<CustomerOrderApiResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.orderApi.getOrderById(orderId) } }

    suspend fun getCustomerOrders(customerId: String): ApiResult<List<CustomerOrderApiResponse>> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.orderApi.getCustomerOrders(customerId) } }

    suspend fun cancelOrder(orderId: String, reason: String): ApiResult<CustomerOrderApiResponse> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.orderApi.cancelOrder(orderId, CancelOrderRequest(reason, "CUSTOMER")) }
        }

    suspend fun getLiveTracking(orderId: String): ApiResult<CustomerOrderTrackingDto> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.orderApi.getLiveTracking(orderId) } }

    /** Realtime Server-Sent Events (SSE) Stream for Continuous Live Order Telemetry */
    fun streamLiveOrderTracking(orderId: String): kotlinx.coroutines.flow.Flow<CustomerOrderTrackingDto> = kotlinx.coroutines.flow.callbackFlow {
        val call = NetworkClient.openOrderSseCall(orderId)
        val gson = com.google.gson.Gson()
        val job = kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
            try {
                val response = call.execute()
                val source = response.body?.source()
                if (response.isSuccessful && source != null) {
                    while (!source.exhausted() && isActive) {
                        val line = source.readUtf8Line() ?: break
                        if (line.startsWith("data:")) {
                            val dataJson = line.removePrefix("data:").trim()
                            if (dataJson.isNotEmpty() && dataJson != "{}") {
                                try {
                                    val dto = gson.fromJson(dataJson, CustomerOrderTrackingDto::class.java)
                                    if (dto != null) {
                                        trySend(dto)
                                    }
                                } catch (_: Exception) {}
                            }
                        }
                    }
                }
            } catch (_: Exception) {
            } finally {
                try { call.cancel() } catch (_: Exception) {}
            }
        }
        awaitClose {
            job.cancel()
            try { call.cancel() } catch (_: Exception) {}
        }
    }

    suspend fun getCancellationPolicy(orderId: String): ApiResult<CancellationPolicy> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.orderApi.getCancellationPolicy(orderId) } }

    open suspend fun getServiceability(
        customerId: String,
        addressId: String,
        items: List<ServiceabilityItem>
    ): ApiResult<ServiceabilityResponse> =
        withContext(Dispatchers.IO) {
            Api.run { NetworkClient.orderApi.getServiceability(ServiceabilityRequest(customerId, addressId, items)) }
        }

    open suspend fun checkServiceability(addressId: String): ApiResult<ServiceabilityResponse> =
        getServiceability(customerId = "guest", addressId = addressId, items = emptyList())

    suspend fun initiatePayment(request: PaymentIntentRequest): ApiResult<PaymentIntentResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.paymentApi.initiatePayment(request) } }

    suspend fun capturePayment(paymentId: String): ApiResult<PaymentStatusResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.paymentApi.capturePayment(paymentId) } }

    suspend fun getPaymentStatus(paymentId: String): ApiResult<PaymentStatusResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.paymentApi.getPaymentStatus(paymentId) } }

    suspend fun getCustomerProfile(customerId: String): ApiResult<CustomerProfile> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.customerApi.getProfile(customerId) } }

    suspend fun getAddresses(customerId: String): ApiResult<List<ApiAddress>> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.customerApi.getAddresses(customerId) } }

    suspend fun addAddress(customerId: String, request: AddAddressRequest): ApiResult<ApiAddress> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.customerApi.addAddress(customerId, request) } }

    suspend fun updateAddress(customerId: String, addressId: String, request: AddAddressRequest): ApiResult<ApiAddress> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.customerApi.updateAddress(customerId, addressId, request) } }

    suspend fun deleteAddress(customerId: String, addressId: String): ApiResult<DeleteAddressResponse> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.customerApi.deleteAddress(customerId, addressId) } }

    suspend fun setDefaultAddress(customerId: String, addressId: String): ApiResult<ApiAddress> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.customerApi.setDefaultAddress(customerId, addressId) } }

    suspend fun uploadPrescription(request: UploadPrescriptionRequest): ApiResult<Prescription> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.prescriptionApi.upload(request) } }

    suspend fun getCustomerPrescriptions(customerId: String): ApiResult<List<Prescription>> =
        withContext(Dispatchers.IO) { Api.run { NetworkClient.prescriptionApi.getCustomerPrescriptions(customerId) } }
}
