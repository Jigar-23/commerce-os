package com.commerceos.android.model

import androidx.compose.runtime.Immutable
import com.google.gson.annotations.SerializedName
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

@Immutable
data class ApiMedicine(
    val id: String,
    val sku: String,
    val name: String,
    val brandName: String,
    val manufacturer: String,
    val packSize: String,
    val rxRequirement: String,
    val price: Double,
    val discountedPrice: Double,
    val expressDeliverySlaMins: Int,
    // Availability is SERVER-AUTHORITATIVE. Absent fields are treated as
    // "unknown", never as "in stock": the UI must not fabricate stock.
    val inStock: Boolean? = null,
    val stockCount: Int? = null,
    val coldChainRequired: Boolean = false,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val image: String? = null,
    val mrp: Double? = null,
    val discountPercentage: Int? = null,
    val therapeuticCategory: String? = null,
    val description: String? = null
)

@Immutable
data class PageResponse<T>(
    val content: List<T>,
    val totalElements: Int,
    val totalPages: Int = 1,
    val hasMore: Boolean = false,
    val nextOffset: Int? = null
)

/**
 * Product detail = the medicine plus server-composed medicine intelligence
 * (composition, monograph, storage) and live-computed equivalent substitutes.
 * Enriched on the SDK-facing catalog endpoint (GET /catalog/medicines/{id}).
 */
@Immutable
data class MedicineDetail(
    val medicine: ApiMedicine,
    val medicineInfo: MedicineInfo? = null,
    val substitutes: List<ApiMedicine> = emptyList()
)

@Immutable
data class MedicineInfo(
    val composition: String? = null,
    val salt: String? = null,
    val uses: List<String> = emptyList(),
    val warnings: List<String> = emptyList(),
    val sideEffects: List<String> = emptyList(),
    val storage: String? = null,
    val highlights: List<String> = emptyList()
)

// Server-derived catalog taxonomy (GET /api/v1/catalog/categories)
@Immutable
data class CatalogCategory(
    val id: String,
    val slug: String,
    val name: String,
    val productCount: Int = 0
)

data class PharmacyCartAttributes(
    val prescriptionRequired: Boolean = false,
    val coldChain: Boolean = false
)

data class CartItem(
    @SerializedName(value = "productId", alternate = ["product_id", "id"])
    val productId: String = "",
    val sku: String = "",
    val name: String = "",
    @SerializedName(value = "unitPrice", alternate = ["unit_price", "price", "discountedPrice"])
    val unitPrice: BigDecimal = BigDecimal.ZERO,
    var quantity: Int = 1,
    val verticalId: String = "pharmacy",
    val merchantId: String? = null,
    val mrp: BigDecimal = unitPrice,
    val brand: String? = null,
    val packSize: String? = null,
    val image: String? = null,
    val pharmacyAttributes: PharmacyCartAttributes? = null,
    @SerializedName(value = "requiresPrescription", alternate = ["prescriptionRequired", "rx_requirement"])
    val requiresPrescription: Boolean? = null,
    @SerializedName(value = "coldChain", alternate = ["cold_chain"])
    val coldChainItem: Boolean? = null
) {
    val prescriptionRequired: Boolean get() = requiresPrescription == true || pharmacyAttributes?.prescriptionRequired == true
    val coldChain: Boolean get() = coldChainItem == true || pharmacyAttributes?.coldChain == true
}

data class CartResponse(
    @SerializedName(value = "customerId", alternate = ["customer_id"])
    val customerId: String = "",
    val items: List<CartItem>? = emptyList(),
    @SerializedName(value = "itemsSubtotal", alternate = ["subtotal", "items_subtotal"])
    val itemsSubtotal: BigDecimal? = null,
    @SerializedName(value = "expressDeliveryFee", alternate = ["deliveryFee", "delivery_fee", "express_delivery_fee"])
    val expressDeliveryFee: BigDecimal? = null,
    @SerializedName(value = "coldChainPackagingFee", alternate = ["packagingFee", "handlingFee", "cold_chain_packaging_fee"])
    val coldChainPackagingFee: BigDecimal? = null,
    @SerializedName(value = "discount", alternate = ["couponDiscount", "discountAmount", "discount_amount"])
    val discount: BigDecimal? = null,
    @SerializedName(value = "grandTotal", alternate = ["total", "grand_total", "totalAmount", "total_amount"])
    val grandTotal: BigDecimal? = null,
    @SerializedName(value = "freeDeliveryThreshold", alternate = ["free_delivery_threshold"])
    val freeDeliveryThreshold: BigDecimal? = null,
    @SerializedName(value = "freeDeliveryEligible", alternate = ["free_delivery_eligible"])
    val freeDeliveryEligible: Boolean? = null,
    @SerializedName(value = "remainingForFreeDelivery", alternate = ["remaining_for_free_delivery"])
    val remainingForFreeDelivery: BigDecimal? = null
)

enum class CouponDiscountType {
    PERCENTAGE,
    FLAT,
    FREE_SHIPPING
}

data class CouponCampaign(
    val id: String,
    val code: String,
    val title: String,
    val description: String,
    val minOrderValue: BigDecimal,
    val discountType: CouponDiscountType = CouponDiscountType.PERCENTAGE,
    val discountPercent: Int = 0,
    val flatDiscount: BigDecimal = BigDecimal.ZERO,
    val maxDiscount: BigDecimal? = null,
    val isFreeShipping: Boolean = false,
    val badge: String = "POPULAR",
    val expiryText: String = "Limited Time",
    val status: String = "ACTIVE"
)

data class CouponApplyResult(
    val success: Boolean,
    val message: String,
    val discountAmount: BigDecimal = BigDecimal.ZERO
)

data class LoginRequest(
    val email: String,
    val password: String
)

data class RegisterRequest(
    val email: String,
    val fullName: String,
    val phone: String,
    val password: String
)

data class SendOtpRequest(
    val phone: String
)

/**
 * OTP challenge issuance. The OTP itself NEVER leaves the server; the client only
 * receives a challenge id to submit at verification plus the server's throttle
 * contract (window + resend cooldown) to render countdowns.
 */
data class SendOtpResponse(
    val message: String,
    val challengeId: String,
    val expiresInSeconds: Int,
    val resendAfterSeconds: Int
)

data class VerifyOtpRequest(
    val challengeId: String,
    val phone: String,
    val otpCode: String
)

data class VerifyGoogleSmsRequest(
    val idToken: String,
    val phone: String? = null
)

data class RefreshTokenRequest(
    val refreshToken: String
)

data class AuthCustomerDto(
    val id: String? = null,
    val phone: String? = null,
    val name: String? = null,
    val email: String? = null
)

data class AuthResponse(
    val userId: String? = null,
    val phone: String? = null,
    val email: String? = null,
    val fullName: String? = null,
    val roles: Set<String> = emptySet(),
    val accessToken: String = "",
    val refreshToken: String? = null,
    val customer: AuthCustomerDto? = null
) {
    val resolvedCustomerId: String
        get() = userId?.takeIf { it.isNotBlank() }
            ?: customer?.id?.takeIf { it.isNotBlank() }
            ?: ""
}

// Server-owned address book (GET /api/v1/customers/:id/addresses)
@Immutable
data class ApiAddress(
    val id: String = "",
    val tag: String = "Home",
    val addressType: String = "HOME",
    val addressLine: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "India",
    val landmark: String = "",
    val contactName: String = "",
    val contactPhone: String = "",
    val isDefault: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val deliveryInstructions: String? = null,
    val placeId: String? = null,
    val accuracyMeters: Float? = null
)

data class AddAddressRequest(
    val tag: String,
    val addressLine: String,
    val city: String,
    val state: String,
    val postalCode: String,
    val country: String,
    val landmark: String,
    val contactName: String,
    val contactPhone: String,
    val isDefault: Boolean = false,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val deliveryInstructions: String? = null,
    val placeId: String? = null,
    val accuracyMeters: Float? = null
)

data class ApiVerticalAvailability(
    val verticalId: String,
    val status: String,
    val eta: String? = null,
    val deliveryMode: String? = null,
    val fee: Double? = null,
    val fulfillmentNode: String? = null
)

// Server-authoritative delivery promise (POST /api/v1/orders/serviceability)
data class ServiceabilityResponse(
    val orderId: String? = null,
    val eligible: Boolean,
    val etaMinutes: EtaWindow? = null,
    val etaLabel: String? = null,
    val fulfillmentNode: FulfillmentNode? = null,
    val deliveryFee: Double = 0.0,
    val coldChainFee: Double = 0.0,
    val address: ServiceableAddress? = null,
    val verticals: List<ApiVerticalAvailability>? = null
)

data class EtaWindow(val min: Int, val max: Int)

data class FulfillmentNode(val id: String?, val name: String?, val slaMinutes: Int?)

data class ServiceableAddress(
    val id: String?,
    val addressLine: String?,
    val city: String?,
    val postalCode: String?
)

// Cart checkout — address + optional pharmacist-APPROVED prescription + idempotency
data class CartCheckoutRequest(
    val addressId: String,
    val prescriptionId: String?,
    val paymentMethod: String,
    val idempotencyKey: String,
    val deliveryAddress: ApiAddress? = null,
    val deliveryFee: BigDecimal? = null,
    val discountAmount: BigDecimal? = null,
    val couponCode: String? = null,
    val packagingFee: BigDecimal? = null,
    val slotText: String? = null
)

data class BillSummaryDto(
    @SerializedName(value = "itemsSubtotal", alternate = ["items_subtotal"])
    val itemsSubtotal: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "discountAmount", alternate = ["discount_amount"])
    val discountAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "deliveryFee", alternate = ["delivery_fee"])
    val deliveryFee: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "handlingFee", alternate = ["handling_fee", "packaging_fee"])
    val handlingFee: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "taxAmount", alternate = ["tax_amount"])
    val taxAmount: BigDecimal = BigDecimal.ZERO,
    @SerializedName(value = "grandTotal", alternate = ["grand_total", "total_amount", "totalAmount"])
    val grandTotal: BigDecimal = BigDecimal.ZERO
)

// Prescription upload / pharmacist verification (port 8089)
data class Prescription(
    @SerializedName(value = "id")
    val id: String = "",
    @SerializedName(value = "customerId", alternate = ["customer_id"])
    val customerId: String = "",
    @SerializedName(value = "patientName", alternate = ["patient_name"])
    val patientName: String = "Patient",
    @SerializedName(value = "age")
    val age: Int? = null,
    @SerializedName(value = "gender")
    val gender: String? = null,
    @SerializedName(value = "doctorName", alternate = ["doctor_name"])
    val doctorName: String? = null,
    @SerializedName(value = "doctorRegistrationNo", alternate = ["doctor_registration_no"])
    val doctorRegistrationNo: String? = null,
    @SerializedName(value = "attachments")
    val attachments: List<String> = emptyList(),
    @SerializedName(value = "note")
    val note: String = "",
    @SerializedName(value = "status")
    val status: String = "PENDING",
    @SerializedName(value = "pharmacistId", alternate = ["pharmacist_id"])
    val pharmacistId: String? = null,
    @SerializedName(value = "licenseNo", alternate = ["license_no"])
    val licenseNo: String? = null,
    @SerializedName(value = "rejectionReason", alternate = ["rejection_reason"])
    val rejectionReason: String? = null,
    @SerializedName(value = "reviewedAt", alternate = ["reviewed_at"])
    val reviewedAt: String? = null,
    @SerializedName(value = "createdAt", alternate = ["created_at"])
    val createdAt: String? = null
)

data class UploadPrescriptionRequest(
    val customerId: String,
    val patientName: String,
    val attachments: List<String>,
    val note: String = "",
    val idempotencyKey: String? = null
)

data class VerifyPrescriptionRequest(
    val approved: Boolean,
    val pharmacistId: String? = null,
    val pharmacistLicenseNo: String? = null,
    val rejectionReason: String? = null
)

// Cancellation policy (GET /api/v1/orders/:id/cancellation-policy)
data class CancellationPolicy(
    val orderId: String,
    val canCancel: Boolean,
    val window: CancellationWindow? = null,
    val reasons: List<CancellationReason> = emptyList(),
    val refund: RefundPolicy? = null
)

data class CancellationWindow(val closesAfter: String?, val currentStatus: String?)

data class CancellationReason(val code: String, val label: String, val refundEligible: Boolean)

data class RefundPolicy(val eligible: Boolean, val method: String?)

data class CancelOrderRequest(
    val reason: String,
    val cancelledBy: String = "CUSTOMER"
)

// Payment state machine (initiate -> AUTHORIZED, capture -> CAPTURED)
data class PaymentIntentRequest(
    val orderId: String,
    val amount: BigDecimal,
    val paymentMethod: String
)

data class PaymentIntentResponse(
    val paymentId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: String,
    val clientSecret: String,
    val status: String
)

data class PaymentStatusResponse(
    val paymentId: String,
    val orderId: String,
    val amount: BigDecimal,
    val currency: String,
    val paymentMethod: String,
    val status: String,
    val capturedAt: String? = null
)

data class CreateOrderApiRequest(
    val customerId: String,
    val orderType: String,
    val totalAmount: BigDecimal,
    val taxAmount: BigDecimal,
    val deliveryFee: BigDecimal,
    val prescriptionId: String?,
    val paymentMethod: String,
    val deliveryAddressJson: String,
    val deliverySlaMins: Int
)

data class CustomerDeliverySessionDto(
    val deliveryId: String? = null,
    val orderId: String? = null,
    val otp: String? = null,
    val state: String? = null
)

data class CustomerOrderApiResponse(
    val id: String = "",
    val orderId: String? = null,
    val order_id: String? = null,
    val shortOrderId: String? = null,
    val short_order_id: String? = null,
    val numericOrderId: Long? = null,
    val orderStatus: String = "PLACED",
    val status: String? = null,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val total_amount: BigDecimal? = null,
    val deliveryFee: BigDecimal? = null,
    val delivery_fee: BigDecimal? = null,
    val taxAmount: BigDecimal? = null,
    val tax_amount: BigDecimal? = null,
    val discountAmount: BigDecimal? = null,
    val packagingFee: BigDecimal? = null,
    val handlingFee: BigDecimal? = null,
    val billSummary: BillSummaryDto? = null,
    val bill_summary: BillSummaryDto? = null,
    val paymentMethod: String = "COD",
    val paymentStatus: String = "COD_PENDING",
    val deliverySlaMins: Int = 15,
    val deliveryOtp: String? = null,
    val deliverySession: CustomerDeliverySessionDto? = null,
    val deliveryHandoffOtpAvailable: Boolean? = null,
    val consignmentNumber: String? = null,
    val provider: String? = "SELLER_MANAGED",
    val deliveryModel: String? = "CHECKPOINT",
    val createdAt: String? = null,
    val created_at: String? = null,
    val orderDate: String? = null,
    val order_date: String? = null,
    val deliveryAddress: ApiAddress? = null,
    val items: List<OrderItem>? = null,
    val prescriptionId: String? = null,
    val prescription_id: String? = null,
    val pharmacistVerification: PharmacistVerification? = null,
    val storeName: String? = null,
    val storeId: String? = null,
    val storePhone: String? = null,
    val merchantPhone: String? = null,
    val riderName: String? = null,
    val riderPhone: String? = null,
    val riderVehicle: String? = null
) {
    val effectiveTotalAmount: BigDecimal
        get() = (billSummary?.grandTotal ?: bill_summary?.grandTotal)?.takeIf { it > BigDecimal.ZERO }
            ?: (if (totalAmount > BigDecimal.ZERO) totalAmount else (total_amount ?: BigDecimal.ZERO)).takeIf { it > BigDecimal.ZERO }
            ?: items?.map { it.unitPrice * BigDecimal(it.quantity) }?.fold(BigDecimal.ZERO, BigDecimal::add)?.let { sub ->
                if (sub > BigDecimal.ZERO) sub + effectiveDeliveryFee else BigDecimal.ZERO
            }
            ?: BigDecimal.ZERO

    val effectiveDeliveryFee: BigDecimal
        get() = billSummary?.deliveryFee ?: bill_summary?.deliveryFee ?: deliveryFee ?: delivery_fee ?: BigDecimal("2.00")

    val effectiveDeliveryPin: String?
        get() = deliveryOtp?.takeIf { it.isNotBlank() } ?: deliverySession?.otp?.takeIf { it.isNotBlank() }

    val resolvedId: String
        get() = id.takeIf { it.isNotBlank() }
            ?: orderId?.takeIf { it.isNotBlank() }
            ?: order_id?.takeIf { it.isNotBlank() }
            ?: ""

    val displayOrderId: String
        get() {
            val rid = resolvedId
            if (rid.isNotBlank()) return rid
            return "ord_${System.currentTimeMillis()}"
        }

    val displayOrderCode: String
        get() {
            val shortId = shortOrderId?.takeIf { it.isNotBlank() } ?: short_order_id?.takeIf { it.isNotBlank() }
            if (shortId != null) {
                return if (shortId.length == 10) "${shortId.substring(0, 4)}-${shortId.substring(4, 7)}-${shortId.substring(7)}" else shortId
            }
            return displayOrderId
        }

    val displayOrderDateTime: String
        get() = formatOrderDateTime(createdAt ?: created_at ?: orderDate ?: order_date)

    val displayOrderDate: String
        get() = formatOrderDateOnly(createdAt ?: created_at ?: orderDate ?: order_date)

    val effectiveStatus: String
        get() = status?.takeIf { it.isNotBlank() } ?: orderStatus

    val displayOrderTime: String
        get() = formatOrderTimeOnly(createdAt ?: created_at ?: orderDate ?: order_date)
}

fun formatOrderDateTime(raw: String?): String {
    if (raw.isNullOrBlank()) {
        return "Order Date Pending"
    }
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.ENGLISH)
            if (pattern.contains("'Z'") || pattern.contains("XXX")) {
                parser.timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(raw)
            if (parsedDate != null) {
                val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
                return formatter.format(parsedDate)
            }
        } catch (_: Exception) {}
    }
    raw.toLongOrNull()?.let { epoch ->
        try {
            val formatter = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.ENGLISH)
            return formatter.format(Date(epoch))
        } catch (_: Exception) {}
    }
    return raw
}

fun formatOrderDateOnly(raw: String?): String {
    if (raw.isNullOrBlank()) {
        return ""
    }
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.ENGLISH)
            if (pattern.contains("'Z'") || pattern.contains("XXX")) {
                parser.timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(raw)
            if (parsedDate != null) {
                val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
                return formatter.format(parsedDate)
            }
        } catch (_: Exception) {}
    }
    raw.toLongOrNull()?.let { epoch ->
        try {
            val formatter = SimpleDateFormat("dd MMM yyyy", Locale.ENGLISH)
            return formatter.format(Date(epoch))
        } catch (_: Exception) {}
    }
    return raw
}

fun formatOrderTimeOnly(raw: String?): String {
    if (raw.isNullOrBlank()) {
        return ""
    }
    val patterns = arrayOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss'Z'",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss"
    )
    for (pattern in patterns) {
        try {
            val parser = SimpleDateFormat(pattern, Locale.ENGLISH)
            if (pattern.contains("'Z'") || pattern.contains("XXX")) {
                parser.timeZone = TimeZone.getTimeZone("UTC")
            }
            val parsedDate = parser.parse(raw)
            if (parsedDate != null) {
                val formatter = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
                return formatter.format(parsedDate)
            }
        } catch (_: Exception) {}
    }
    raw.toLongOrNull()?.let { epoch ->
        try {
            val formatter = SimpleDateFormat("hh:mm a", Locale.ENGLISH)
            return formatter.format(Date(epoch))
        } catch (_: Exception) {}
    }
    return raw
}

data class OrderItem(
    val productId: String = "",
    val sku: String = "",
    val name: String = "",
    val unitPrice: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal? = null,
    val quantity: Int = 1,
    val qty: Int? = null,
    val lineTotal: BigDecimal? = null,
    val verticalId: String = "general",
    val merchantId: String? = null,
    val rxRequired: Boolean = false,
    val isRxRequired: Boolean? = null,
    val image: String? = null,
    val imageUrl: String? = null
) {
    val effectiveSku: String
        get() = sku.takeIf { it.isNotBlank() } ?: productId.takeIf { it.isNotBlank() } ?: "SKU_ITEM"

    val effectiveName: String
        get() = name.takeIf { it.isNotBlank() } ?: sku.takeIf { it.isNotBlank() } ?: "Item"

    val effectivePrice: BigDecimal
        get() = if (unitPrice > BigDecimal.ZERO) unitPrice
                else price ?: lineTotal?.let {
                    val q = effectiveQuantity
                    if (q > 0) it.divide(BigDecimal(q), 2, java.math.RoundingMode.HALF_UP) else it
                } ?: BigDecimal.ZERO

    val effectiveQuantity: Int
        get() = if (quantity > 0) quantity else (qty ?: 1)

    val effectiveRxRequired: Boolean
        get() = rxRequired || isRxRequired == true

    val effectiveImage: String?
        get() = image?.takeIf { it.isNotBlank() } ?: imageUrl?.takeIf { it.isNotBlank() }

    val effectiveProductId: String
        get() = productId.takeIf { it.isNotBlank() } ?: effectiveSku

    val effectiveVerticalId: String
        get() = (verticalId as? String)?.takeIf { it.isNotBlank() } ?: "general"
}

data class PharmacistVerification(
    val status: String,
    val pharmacistId: String? = null,
    val licenseNo: String? = null,
    val verifiedAt: String? = null,
    val rejectionReason: String? = null
)

typealias PaginatedResponse<T> = PageResponse<T>
typealias BackendCartResponse = CartResponse

data class ServiceabilityItem(
    val sku: String,
    val quantity: Int,
    val coldChain: Boolean = false
)

data class ServiceabilityRequest(
    val customerId: String,
    val addressId: String,
    val items: List<ServiceabilityItem>
)

data class CartQuantityUpdateRequest(
    val quantity: Int
)

data class AuthRequest(
    val phone: String,
    val otp: String? = null
)

data class RegisterCustomerRequest(
    val fullName: String,
    val phone: String,
    val email: String? = null
)

// Server-owned customer profile (GET /api/v1/customers/:id)
data class CustomerProfile(
    @com.google.gson.annotations.SerializedName(value = "id", alternate = ["customerId", "customer_id"])
    val id: String? = null,

    @com.google.gson.annotations.SerializedName(value = "fullName", alternate = ["full_name", "name", "contactName", "contact_name"])
    val fullName: String? = null,

    @com.google.gson.annotations.SerializedName(value = "email", alternate = ["emailAddress", "email_address"])
    val email: String? = null,

    @com.google.gson.annotations.SerializedName(value = "phone", alternate = ["phoneNumber", "phone_number", "contactPhone", "contact_phone"])
    val phone: String? = null,

    @com.google.gson.annotations.SerializedName(value = "status", alternate = ["tier", "user_status"])
    val status: String? = "ACTIVE"
) {
    val displayName: String get() {
        val name = fullName?.trim()
        if (!name.isNullOrBlank()) return name
        val p = phone?.filter { it.isDigit() } ?: ""
        return if (p.length >= 4) "Customer ${p.takeLast(4)}" else "Customer"
    }

    val maskedPhone: String get() {
        val p = phone?.trim() ?: ""
        val digits = p.filter { it.isDigit() }
        return if (digits.length >= 10) {
            "+91 ${digits.take(5)} ${digits.takeLast(5)}"
        } else if (p.isNotBlank()) p else ""
    }
}

