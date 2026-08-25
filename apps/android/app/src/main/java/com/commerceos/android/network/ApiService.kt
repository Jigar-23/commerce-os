package com.commerceos.android.network

import com.commerceos.android.BuildConfig
import com.commerceos.android.model.*
import com.google.gson.JsonParser
import com.google.gson.annotations.SerializedName
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.concurrent.TimeUnit

interface CatalogApi {
    @GET("/api/v1/catalog/home-feed")
    suspend fun getHomeFeed(
        @Query("customerId") customerId: String,
        @Query("addressId") addressId: String? = null
    ): HomeFeedResponse

    @GET("/api/v1/catalog/medicines/search")
    suspend fun searchMedicines(
        @Query("query") query: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null
    ): PageResponse<ApiMedicine>

    @GET("/api/v1/catalog/medicines/category")
    suspend fun getMedicinesByCategory(
        @Query("category") category: String,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null
    ): PageResponse<ApiMedicine>

    @GET("/api/v1/catalog/medicines")
    suspend fun getMedicines(
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null
    ): PageResponse<ApiMedicine>

    @GET("/api/v1/catalog/medicines/{id}")
    suspend fun getMedicineById(@Path("id") id: String): ApiMedicine

    @GET("/api/v1/catalog/medicines/{id}")
    suspend fun getMedicineDetail(@Path("id") id: String): MedicineDetail

    @GET("/api/v1/catalog/categories")
    suspend fun getCategories(): PageResponse<CatalogCategory>

    @GET("/api/v1/catalog/destinations")
    suspend fun getDestinations(): List<Destination>

    @GET("/api/v1/catalog/products")
    suspend fun getProducts(
        @Query("query") query: String? = null,
        @Query("category") category: String? = null,
        @Query("categoryId") categoryId: String? = null,
        @Query("brandId") brandId: String? = null,
        @Query("storeId") storeId: String? = null,
        @Query("collectionId") collectionId: String? = null,
        @Query("campaignId") campaignId: String? = null,
        @Query("offerId") offerId: String? = null,
        @Query("vertical") vertical: String? = null,
        @Query("minPrice") minPrice: Double? = null,
        @Query("maxPrice") maxPrice: Double? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): PageResponse<CommerceProduct>

    @GET("/api/v1/catalog/products/{id}")
    suspend fun getProductById(
        @Path("id") id: String,
        @Query("vertical") vertical: String? = null
    ): CommerceProduct

    @GET("/api/v1/catalog/vertical/{verticalId}/home-feed")
    suspend fun getVerticalHomeFeed(
        @Path("verticalId") verticalId: String,
        @Query("addressId") addressId: String? = null
    ): VerticalHomeFeedResponse

    @GET("/api/v1/catalog/vertical/{verticalId}/taxonomy")
    suspend fun getVerticalTaxonomy(
        @Path("verticalId") verticalId: String
    ): VerticalTaxonomyResponse
}

interface SearchApi {
    @GET("/api/v1/search")
    suspend fun search(
        @Query("query") query: String,
        @Query("vertical") vertical: String? = null,
        @Query("intent") intent: String? = null,
        @Query("addressId") addressId: String? = null,
        @Query("limit") limit: Int = 20,
        @Query("offset") offset: Int = 0
    ): SearchResponse

    @GET("/api/v1/search/autocomplete")
    suspend fun autocomplete(
        @Query("query") query: String,
        @Query("vertical") vertical: String? = null
    ): List<SearchSuggestion>
}

interface CartApi {
    @GET("/api/v1/cart/{customerId}")
    suspend fun getCart(@Path("customerId") customerId: String): CartResponse

    @POST("/api/v1/cart/{customerId}/items")
    suspend fun addItem(
        @Path("customerId") customerId: String,
        @Body request: CartItem
    ): CartResponse

    @DELETE("/api/v1/cart/{customerId}/items/{sku}")
    suspend fun removeItem(
        @Path("customerId") customerId: String,
        @Path("sku") sku: String
    ): CartResponse

    @PATCH("/api/v1/cart/{customerId}/items/{sku}")
    suspend fun updateQuantity(
        @Path("customerId") customerId: String,
        @Path("sku") sku: String,
        @Body request: CartQuantityUpdateRequest
    ): CartResponse
}

interface AuthApi {
    @Deprecated("Password login is deprecated in favor of authentic phone/OTP authentication flow")
    @POST("/api/v1/auth/login")
    suspend fun login(@Body request: LoginRequest): AuthResponse

    @Deprecated("Direct registration is deprecated in favor of authentic phone/OTP verification flow")
    @POST("/api/v1/auth/register")
    suspend fun register(@Body request: RegisterRequest): AuthResponse

    @POST("/api/v1/auth/otp/send")
    suspend fun sendOtp(@Body request: SendOtpRequest): SendOtpResponse

    @POST("/api/v1/auth/otp/verify")
    suspend fun verifyOtp(@Body request: VerifyOtpRequest): AuthResponse

    @POST("/api/v1/auth/refresh")
    suspend fun refresh(@Body request: RefreshTokenRequest): AuthResponse
}

interface PaymentApi {
    @Deprecated("Release scope is COD ONLY. Online payment gateway is disabled in live flow.")
    @POST("/api/v1/payments/initiate")
    suspend fun initiatePayment(@Body request: PaymentIntentRequest): PaymentIntentResponse

    @Deprecated("Release scope is COD ONLY. Online payment gateway is disabled in live flow.")
    @POST("/api/v1/payments/{paymentId}/capture")
    suspend fun capturePayment(@Path("paymentId") paymentId: String): PaymentStatusResponse

    @Deprecated("Release scope is COD ONLY. Online payment gateway is disabled in live flow.")
    @GET("/api/v1/payments/{paymentId}")
    suspend fun getPaymentStatus(@Path("paymentId") paymentId: String): PaymentStatusResponse
}

interface OrderApi {
    @POST("/api/v1/orders/checkout-from-cart/{customerId}")
    suspend fun checkoutFromCart(
        @Path("customerId") customerId: String,
        @Body request: CartCheckoutRequest
    ): CustomerOrderApiResponse

    @POST("/api/v1/orders")
    suspend fun createOrder(@Body request: CreateOrderApiRequest): CustomerOrderApiResponse

    @GET("/api/v1/orders/{orderId}")
    suspend fun getOrderById(@Path("orderId") orderId: String): CustomerOrderApiResponse

    @GET("/api/v1/delivery/order/{orderId}")
    suspend fun getLiveTracking(@Path("orderId") orderId: String): CustomerOrderTrackingDto

    @GET("/api/v1/orders/customer/{customerId}")
    suspend fun getCustomerOrders(@Path("customerId") customerId: String): List<CustomerOrderApiResponse>

    @POST("/api/v1/orders/{orderId}/cancel")
    suspend fun cancelOrder(
        @Path("orderId") orderId: String,
        @Body request: CancelOrderRequest
    ): CustomerOrderApiResponse

    @POST("/api/v1/orders/{customerId}/{orderId}/status")
    suspend fun advanceOrderStatus(
        @Path("customerId") customerId: String,
        @Path("orderId") orderId: String
    ): CustomerOrderApiResponse

    @POST("/api/v1/orders/serviceability")
    suspend fun getServiceability(
        @Body request: ServiceabilityRequest
    ): ServiceabilityResponse

    @GET("/api/v1/orders/{orderId}/cancellation-policy")
    suspend fun getCancellationPolicy(@Path("orderId") orderId: String): CancellationPolicy
}

interface CustomerApi {
    @GET("/api/v1/customers/{customerId}")
    suspend fun getProfile(@Path("customerId") customerId: String): CustomerProfile

    @GET("/api/v1/customers/{customerId}/addresses")
    suspend fun getAddresses(@Path("customerId") customerId: String): List<ApiAddress>

    @POST("/api/v1/customers/{customerId}/addresses")
    suspend fun addAddress(
        @Path("customerId") customerId: String,
        @Body request: AddAddressRequest
    ): ApiAddress

    @DELETE("/api/v1/customers/{customerId}/addresses/{addressId}")
    suspend fun deleteAddress(
        @Path("customerId") customerId: String,
        @Path("addressId") addressId: String
    ): DeleteAddressResponse

    @PUT("/api/v1/customers/{customerId}/addresses/{addressId}")
    suspend fun updateAddress(
        @Path("customerId") customerId: String,
        @Path("addressId") addressId: String,
        @Body request: AddAddressRequest
    ): ApiAddress

    @POST("/api/v1/customers/{customerId}/addresses/{addressId}/default-shipping")
    suspend fun setDefaultAddress(
        @Path("customerId") customerId: String,
        @Path("addressId") addressId: String
    ): ApiAddress
}

interface PrescriptionApi {
    @POST("/api/v1/prescriptions")
    suspend fun upload(@Body request: UploadPrescriptionRequest): Prescription

    @GET("/api/v1/prescriptions/{id}")
    suspend fun getById(@Path("id") id: String): Prescription

    @GET("/api/v1/prescriptions/customer/{customerId}")
    suspend fun getCustomerPrescriptions(@Path("customerId") customerId: String): List<Prescription>
}

data class DeleteAddressResponse(
    @SerializedName(value = "deleted", alternate = ["ok", "success"])
    val deleted: Boolean = true,
    @SerializedName(value = "addressId", alternate = ["deletedId", "id"])
    val addressId: String = ""
)

object NetworkClient {
    private const val TAG = "NetworkClient"

    /**
     * Single API origin for the whole app, driven by BuildConfig (debug uses the
     * emulator's gateway host by default; a device build points the debug overlay
     * at a LAN gateway). The mock platform exposes one gateway:port that routes
     * /api/v1 routes by namespace, so the client never hardcodes internal service
     * topology or ports. Release builds cannot change the origin at runtime.
     */
    var baseUrl: String = BuildConfig.API_BASE_URL.trimEnd('/')
        set(value) {
            if (BuildConfig.ALLOW_BASE_URL_OVERRIDE) {
                field = value.trimEnd('/')
                clearCaches()
            }
        }

    /** Current access token (installed by [SessionManager]; never written by UI). */
    var authTokenProvider: () -> String = { "" }

    /** Current refresh token (installed by [SessionManager]; used for 401 recovery). */
    var refreshTokenProvider: () -> String = { "" }

    /** Called once a 401 has been recovered via [refreshTokens]. */
    var onSessionRefreshed: (accessToken: String, refreshToken: String) -> Unit = { _, _ -> }

    /** Called when the refresh token is invalid/expired and re-auth is required. */
    var onSessionExpired: () -> Unit = {}

    private val serviceCache = mutableMapOf<Class<*>, Any>()
    private val retrofitCache = mutableMapOf<String, Retrofit>()
    private val refreshLock = Any()
    @Volatile
    private var refreshing = false

    private val authInterceptor = Interceptor { chain ->
        val token = authTokenProvider()
        val request = if (token.isNotBlank()) {
            chain.request().newBuilder().header("Authorization", "Bearer $token").build()
        } else {
            chain.request()
        }
        chain.proceed(request)
    }

    /**
     * Recovers from a signed-in 401 by exchanging the refresh token once and
     * replaying the original request. Deliberately never engages for identity
     * endpoints (wrong OTP / bad credentials must not trigger a refresh).
     */
    private val refreshInterceptor = Interceptor { chain ->
        var response = chain.proceed(chain.request())
        if (response.code != 401) return@Interceptor response

        val request = chain.request()
        val path = request.url.encodedPath
        val refreshToken = refreshTokenProvider()
        val canRefresh = refreshToken.isNotBlank() &&
            !path.startsWith("/api/v1/auth")

        if (!canRefresh) return@Interceptor response

        val newTokens = refreshTokensBlocking(refreshToken)
        if (newTokens == null) {
            onSessionExpired()
            return@Interceptor response
        }

        onSessionRefreshed(newTokens.first, newTokens.second)
        response.close()
        chain.proceed(
            request.newBuilder()
                .header("Authorization", "Bearer ${newTokens.first}")
                .build()
        )
    }

    private fun refreshTokensBlocking(refreshToken: String): Pair<String, String>? = synchronized(refreshLock) {
        if (refreshing) return@synchronized null
        refreshing = true
        try {
            val requestBody = "{\"refreshToken\":\"$refreshToken\"}"
                .toRequestBody("application/json".toMediaType())
            val refreshRequest = Request.Builder()
                .url("$baseUrl/api/v1/auth/refresh")
                .post(requestBody)
                .build()
            rawClient.newCall(refreshRequest).execute().use { resp ->
                if (resp.code == 200) {
                    val json = try {
                        JsonParser().parse(resp.body?.string()).asJsonObject
                    } catch (e: Exception) {
                        return@use null
                    }
                    val access = json.get("accessToken")?.getAsString() ?: return@use null
                    val refresh = json.get("refreshToken")?.getAsString() ?: return@use null
                    access to refresh
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            null
        } finally {
            refreshing = false
        }
    }

    /** Plain client used for the refresh call - never applies the auth/refresh interceptors. */
    private val rawClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    private fun retrofitForBaseUrl(): Retrofit = synchronized(retrofitCache) {
        retrofitCache.getOrPut(baseUrl) {
            val client = OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(15, TimeUnit.SECONDS)
                .retryOnConnectionFailure(true)
                .addInterceptor(authInterceptor)
                .addInterceptor(refreshInterceptor)
                .addInterceptor(
                    HttpLoggingInterceptor { msg ->
                        android.util.Log.d("OkHttp", msg)
                    }.apply {
                        level = HttpLoggingInterceptor.Level.BODY
                    }
                )
                .build()
            Retrofit.Builder()
                .baseUrl(baseUrl)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun <T> service(serviceClass: Class<T>): T = synchronized(serviceCache) {
        serviceCache.getOrPut(serviceClass) { retrofitForBaseUrl().create(serviceClass) as Any } as T
    }

    private fun clearCaches() {
        synchronized(serviceCache) { serviceCache.clear() }
        synchronized(retrofitCache) { retrofitCache.clear() }
    }

    // Services are created once per base URL and cached - not per access.
    val catalogApi: CatalogApi get() = service(CatalogApi::class.java)
    val searchApi: SearchApi get() = service(SearchApi::class.java)
    val cartApi: CartApi get() = service(CartApi::class.java)
    val authApi: AuthApi get() = service(AuthApi::class.java)
    val paymentApi: PaymentApi get() = service(PaymentApi::class.java)
    val orderApi: OrderApi get() = service(OrderApi::class.java)
    val customerApi: CustomerApi get() = service(CustomerApi::class.java)
    val prescriptionApi: PrescriptionApi get() = service(PrescriptionApi::class.java)

    /** Authoritative Server-Sent Events (SSE) Stream Call for Live Order Telemetry */
    fun openOrderSseCall(orderId: String): okhttp3.Call {
        val token = authTokenProvider()
        val streamUrl = "$baseUrl/api/v1/delivery/order/$orderId/stream${if (token.isNotBlank()) "?token=${java.net.URLEncoder.encode(token, "UTF-8")}" else ""}"
        val request = Request.Builder()
            .url(streamUrl)
            .header("Accept", "text/event-stream")
            .apply {
                if (token.isNotBlank()) header("Authorization", "Bearer $token")
            }
            .build()
        val streamingClient = rawClient.newBuilder()
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
        return streamingClient.newCall(request)
    }
}
