package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import com.commerceos.android.util.MoneyFormatter
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ServiceabilityState {
    val targetAddressId: String?

    data object Idle : ServiceabilityState { override val targetAddressId: String? = null }
    data class Checking(val addressId: String) : ServiceabilityState { override val targetAddressId: String? = addressId }
    data class Success(val addressId: String, val response: ServiceabilityResponse) : ServiceabilityState { override val targetAddressId: String? = addressId }
    data class Partial(val addressId: String, val response: ServiceabilityResponse) : ServiceabilityState { override val targetAddressId: String? = addressId }
    data class Unavailable(val addressId: String) : ServiceabilityState { override val targetAddressId: String? = addressId }
    data class Error(val addressId: String?, val message: String) : ServiceabilityState { override val targetAddressId: String? = addressId }
}

/** Explicit checkout state machine. Every transition is driven by the ViewModel —
 *  the UI navigates only because these states exist, never the reverse. */
sealed interface CheckoutState {
    data object Idle : CheckoutState
    data object Validating : CheckoutState
    data object ReadyForPayment : CheckoutState
    data object PlacingOrder : CheckoutState
    data object Paying : CheckoutState
    data class Confirmed(val order: CustomerOrderApiResponse) : CheckoutState
    data class Failed(val code: String, val message: String) : CheckoutState
}

/** One-shot checkout outcome — never persistent state used as an event trigger. */
sealed interface CheckoutEvent {
    data class OrderPlaced(val orderId: String, val order: CustomerOrderApiResponse? = null) : CheckoutEvent
}

/**
 * UI-facing checkout state. The UI observes this and never touches the internal
 * domain/session model directly. Validation flags make the payment gate explicit:
 * [readyForPayment] is the single condition the ViewModel enforces before placing
 * an order — no screen can bypass it by navigating.
 */
data class CheckoutUiState(
    val items: List<CartItem> = emptyList(),
    val address: ApiAddress? = null,
    val prescriptionId: String? = null,
    val selectedSlotText: String? = null,
    val itemsSubtotal: BigDecimal = BigDecimal.ZERO,
    val deliveryFee: BigDecimal = BigDecimal.ZERO,
    val coldChainFee: BigDecimal = BigDecimal.ZERO,
    val couponDiscount: BigDecimal = BigDecimal.ZERO,
    val couponCode: String? = null,
    val grandTotal: BigDecimal = BigDecimal.ZERO,
    val serviceability: ServiceabilityState = ServiceabilityState.Idle,
    val state: CheckoutState = CheckoutState.Idle,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
) {
    val cartValid: Boolean
        get() = items.isNotEmpty() && items.all {
            it.sku.isNotBlank() &&
                it.quantity > 0 &&
                it.unitPrice > BigDecimal.ZERO
        }

    val addressValid: Boolean
        get() = !address?.id.isNullOrBlank()

    val serviceabilityValid: Boolean
        get() = (serviceability as? ServiceabilityState.Success)?.addressId == address?.id &&
            addressValid

    val pricingValid: Boolean
        get() = itemsSubtotal > BigDecimal.ZERO && grandTotal > BigDecimal.ZERO

    val prescriptionValid: Boolean
        get() = if (items.any { it.prescriptionRequired }) {
            !prescriptionId.isNullOrBlank()
        } else {
            true
        }

    val readyForPayment: Boolean
        get() = state != CheckoutState.PlacingOrder &&
            state != CheckoutState.Paying &&
            state !is CheckoutState.Confirmed &&
            cartValid &&
            addressValid &&
            serviceabilityValid &&
            pricingValid &&
            prescriptionValid &&
            !isProcessing
}

class CheckoutViewModel(
    private val repository: AppRepository,
    private var mutationGate: CartMutationGate? = null,
    private val context: android.content.Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    constructor(repository: AppRepository) : this(repository, null, null, Dispatchers.IO)

    fun setMutationGate(gate: CartMutationGate?) {
        this.mutationGate = gate
    }

    var uiState by mutableStateOf(CheckoutUiState())
        private set

    var customerId by mutableStateOf("")
        private set

    private val _events = Channel<CheckoutEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var checkoutIdempotencyKey: String? = null
    private var lastPayloadFingerprint: String? = null

    init {
        loadPersistedIdempotency()
    }

    private suspend fun persistIdempotency(key: String?, fingerprint: String?): Boolean {
        val currentContext = context ?: run {
            checkoutIdempotencyKey = key
            lastPayloadFingerprint = fingerprint
            return true
        }
        return withContext(ioDispatcher) {
            val prefs = currentContext.getSharedPreferences("commerce_checkout_idempotency", android.content.Context.MODE_PRIVATE)
            val editor = prefs.edit()
            if (key == null) {
                editor.remove("idempotency_key")
                    .remove("payload_fingerprint")
            } else {
                editor.putString("idempotency_key", key)
                    .putString("payload_fingerprint", fingerprint)
                    .putBoolean("pending_rotation", false)
            }
            val committed = editor.commit()
            if (committed) {
                checkoutIdempotencyKey = key
                lastPayloadFingerprint = fingerprint
            }
            committed
        }
    }

    private suspend fun recordPendingKeyRotation(lastOrderId: String? = null): Boolean {
        val currentContext = context ?: return true
        return withContext(ioDispatcher) {
            val prefs = currentContext.getSharedPreferences("commerce_checkout_idempotency", android.content.Context.MODE_PRIVATE)
            prefs.edit()
                .putBoolean("pending_rotation", true)
                .putString("last_completed_order_id", lastOrderId)
                .remove("idempotency_key")
                .remove("payload_fingerprint")
                .commit()
        }
    }

    private fun loadPersistedIdempotency() {
        val prefs = context?.getSharedPreferences("commerce_checkout_idempotency", android.content.Context.MODE_PRIVATE) ?: return
        if (prefs.getBoolean("pending_rotation", false)) {
            checkoutIdempotencyKey = null
            lastPayloadFingerprint = null
            return
        }
        val savedKey = prefs.getString("idempotency_key", null)
        val savedFingerprint = prefs.getString("payload_fingerprint", null)
        if (!savedKey.isNullOrBlank() && !savedFingerprint.isNullOrBlank()) {
            checkoutIdempotencyKey = savedKey
            lastPayloadFingerprint = savedFingerprint
        }
    }
    private var serviceabilityJob: Job? = null
    private var checkoutJob: Job? = null

    private fun computePayloadFingerprint(
        customerId: String,
        address: ApiAddress,
        items: List<CartItem>,
        prescriptionId: String?,
        couponCode: String?,
        paymentMethod: String?,
        slotText: String?
    ): String {
        val itemSignature = items.joinToString("\u001e") {
            "${it.sku.length}:${it.sku}:${it.quantity}:${it.unitPrice.toPlainString()}"
        }
        val canonical = listOf(
            customerId,
            address.toString(),
            itemSignature,
            prescriptionId.orEmpty(),
            couponCode.orEmpty(),
            paymentMethod.orEmpty(),
            slotText.orEmpty()
        ).joinToString("\u001f")

        return java.security.MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it.toInt() and 0xff) }
    }

    private fun serviceabilitySignature(items: List<CartItem>): String =
        items.joinToString("|") {
            "${it.sku.length}:${it.sku}:${it.quantity}:${it.coldChain}"
        }

    /** Called when the customer reaches address selection: snapshot cart + totals. Preserves selected address/Rx if already set. */
    fun start(
        customerId: String,
        cartItems: List<CartItem>,
        couponDiscount: BigDecimal = BigDecimal.ZERO,
        couponCode: String? = null
    ) {
        if (uiState.isProcessing || uiState.state is CheckoutState.Confirmed) return

        val targetCustomerId = customerId.trim()
        val sameCustomer = this.customerId == targetCustomerId

        if (!sameCustomer) {
            serviceabilityJob?.cancel()
            checkoutJob?.cancel()
            checkoutIdempotencyKey = null
            lastPayloadFingerprint = null
            this.customerId = targetCustomerId
        }

        val old = uiState
        val items = cartItems.toList()
        val address = if (sameCustomer) old.address else null
        val prescriptionId = if (sameCustomer) old.prescriptionId else null
        val slotText = if (sameCustomer) old.selectedSlotText else null
        val subtotal = itemsSubtotal(items)
        val safeDiscount = couponDiscount.coerceAtLeast(BigDecimal.ZERO).min(subtotal)

        serviceabilityJob?.cancel()
        uiState = CheckoutUiState(
            items = items,
            address = address,
            prescriptionId = prescriptionId,
            selectedSlotText = slotText,
            itemsSubtotal = subtotal,
            couponDiscount = safeDiscount,
            couponCode = couponCode,
            grandTotal = subtotal.subtract(safeDiscount).coerceAtLeast(BigDecimal.ZERO),
            serviceability = ServiceabilityState.Idle,
            state = CheckoutState.Validating
        )

        if (address != null) refreshServiceability()
    }

    private fun canEditCheckout(): Boolean =
        !uiState.isProcessing && uiState.state !is CheckoutState.Confirmed

    /** Address change triggers immediate universal serviceability. */
    fun selectAddress(address: ApiAddress) {
        if (!canEditCheckout()) return
        uiState = uiState.copy(address = address)
        refreshServiceability()
    }

    fun attachPrescription(prescriptionId: String?) {
        if (!canEditCheckout()) return
        uiState = uiState.copy(prescriptionId = prescriptionId)
        revalidateIfReady()
    }

    fun attachAppointmentSlot(slotText: String?) {
        if (!canEditCheckout()) return
        uiState = uiState.copy(selectedSlotText = slotText)
        revalidateIfReady()
    }

    /** Address-aware & cart-aware server serviceability check. */
    fun refreshServiceability() {
        val snapshot = uiState
        val customerIdSnapshot = customerId.trim()
        val address = snapshot.address
        if (address == null || address.id.isBlank() || customerIdSnapshot.isBlank()) {
            serviceabilityJob?.cancel()
            uiState = snapshot.copy(
                serviceability = ServiceabilityState.Idle,
                deliveryFee = BigDecimal.ZERO,
                coldChainFee = BigDecimal.ZERO,
                state = CheckoutState.Validating
            )
            return
        }

        val addressId = address.id
        val items = snapshot.items.toList()
        val itemSignature = serviceabilitySignature(items)
        val requestItems = items.map {
            ServiceabilityItem(it.sku, it.quantity, it.coldChain)
        }

        serviceabilityJob?.cancel()
        uiState = snapshot.copy(serviceability = ServiceabilityState.Checking(addressId))

        serviceabilityJob = viewModelScope.launch {
            val result = repository.getServiceability(customerIdSnapshot, addressId, requestItems)

            val currentSignature = serviceabilitySignature(uiState.items)
            if (customerId.trim() != customerIdSnapshot ||
                uiState.address?.id != addressId ||
                currentSignature != itemSignature
            ) return@launch

            when (result) {
                is ApiResult.Success -> {
                    val response = result.data
                    if (!response.eligible) {
                        uiState = uiState.copy(
                            serviceability = ServiceabilityState.Unavailable(addressId),
                            deliveryFee = BigDecimal.ZERO,
                            coldChainFee = BigDecimal.ZERO,
                            grandTotal = uiState.itemsSubtotal
                                .subtract(uiState.couponDiscount)
                                .coerceAtLeast(BigDecimal.ZERO),
                            state = CheckoutState.Failed(
                                "UNSERVICEABLE",
                                "Selected address is currently outside our delivery zone."
                            )
                        )
                        return@launch
                    }

                    val deliveryFee = BigDecimal.valueOf(response.deliveryFee)
                    val coldChainFee = BigDecimal.valueOf(response.coldChainFee)
                    val updated = uiState.copy(
                        serviceability = ServiceabilityState.Success(addressId, response),
                        deliveryFee = deliveryFee,
                        coldChainFee = coldChainFee,
                        grandTotal = uiState.itemsSubtotal
                            .add(deliveryFee)
                            .add(coldChainFee)
                            .subtract(uiState.couponDiscount)
                            .coerceAtLeast(BigDecimal.ZERO)
                    )
                    uiState = updated.copy(
                        state = if (updated.readyForPayment) CheckoutState.ReadyForPayment
                        else CheckoutState.Validating
                    )
                }
                is ApiResult.Failure -> {
                    val message = result.error.message
                    uiState = uiState.copy(
                        serviceability = ServiceabilityState.Error(addressId, message),
                        deliveryFee = BigDecimal.ZERO,
                        coldChainFee = BigDecimal.ZERO,
                        state = CheckoutState.Failed("SERVICEABILITY_ERROR", message)
                    )
                }
            }
        }
    }

    private fun revalidateIfReady() {
        uiState = uiState.copy(
            state = if (uiState.readyForPayment) CheckoutState.ReadyForPayment else CheckoutState.Validating
        )
    }

    private fun itemsSubtotal(items: List<CartItem>): BigDecimal =
        items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.unitPrice.multiply(BigDecimal.valueOf(item.quantity.toLong()))) }

    private fun failCheckout(code: String, message: String) {
        mutationGate?.unlockMutations()
        uiState = uiState.copy(
            state = CheckoutState.Failed(code, message),
            errorMessage = message,
            isProcessing = false
        )
    }

    private val COD_PAYMENT_ALLOWLIST = setOf("COD", "CASH", "PAY_ON_DELIVERY", "CASH_ON_DELIVERY", "POD")

    fun executeCheckout(paymentMethod: String) {
        val methodUpper = paymentMethod.trim().uppercase()
        if (methodUpper.isBlank() || methodUpper !in COD_PAYMENT_ALLOWLIST) {
            failCheckout("INVALID_PAYMENT_METHOD", "Only Cash on Delivery (COD) / Pay on Delivery is supported in this deployment.")
            return
        }
        val canonicalPaymentMethod = "COD"

        if (checkoutJob?.isActive == true || uiState.isProcessing || uiState.state is CheckoutState.Confirmed) return
        val customerIdSnapshot = customerId.trim()
        if (customerIdSnapshot.isBlank()) {
            uiState = uiState.copy(errorMessage = "Authentication required for checkout")
            return
        }

        val gate = mutationGate
        checkoutJob = viewModelScope.launch {
            gate?.lockMutations()
            try {
                val settledCart = gate?.awaitMutationsIdle() ?: uiState.items

                val currentItems = settledCart.toList()
                val currentSubtotal = itemsSubtotal(currentItems)
                val currentDiscount = uiState.couponDiscount.min(currentSubtotal)

                var authoritativeItems = currentItems
                var authoritativeSubtotal = currentSubtotal
                var authoritativeDiscount = currentDiscount
                var authoritativeDeliveryFee = uiState.deliveryFee
                var authoritativeColdChainFee = uiState.coldChainFee
                var authoritativeGrandTotal = currentSubtotal
                    .add(uiState.deliveryFee)
                    .add(uiState.coldChainFee)
                    .subtract(currentDiscount)
                    .coerceAtLeast(BigDecimal.ZERO)
                var authoritativeCartQuote: CartResponse? = null
                val couponParam = uiState.couponCode?.takeIf { it.isNotBlank() }

                when (val backendQuote = repository.fetchBackendCart(customerIdSnapshot, couponParam)) {
                    is ApiResult.Success -> {
                        val cartDto = backendQuote.data
                        authoritativeCartQuote = cartDto
                        val serverItems = cartDto.items
                        if (serverItems == null) {
                            failCheckout("CART_SYNC_ERROR", "Server returned incomplete cart quote: missing items")
                            return@launch
                        }
                        if (serverItems.isNotEmpty()) {
                            if (cartDto.itemsSubtotal == null || cartDto.grandTotal == null) {
                                failCheckout("CART_SYNC_ERROR", "Server returned incomplete cart quote: missing subtotal or grandTotal")
                                return@launch
                            }
                            authoritativeItems = serverItems
                            val serverSubtotal = cartDto.itemsSubtotal
                            val serverDiscount = (cartDto.discount ?: BigDecimal.ZERO).min(serverSubtotal)
                            authoritativeSubtotal = serverSubtotal
                            authoritativeDiscount = serverDiscount
                            authoritativeDeliveryFee = cartDto.expressDeliveryFee ?: uiState.deliveryFee
                            authoritativeColdChainFee = cartDto.coldChainPackagingFee ?: uiState.coldChainFee
                            authoritativeGrandTotal = cartDto.grandTotal
                        } else {
                            failCheckout("CART_SYNC_ERROR", "Server returned incomplete cart quote: empty items or missing totals")
                            return@launch
                        }
                    }
                    is ApiResult.Failure -> {
                        failCheckout("CART_SYNC_ERROR", backendQuote.error.message.ifBlank { "Failed to refresh cart quote" })
                        return@launch
                    }
                }

                val initialAuthSig = currentItems.map {
                    "${it.sku.trim()}:${it.quantity}:${it.unitPrice.stripTrailingZeros().toPlainString()}:${it.prescriptionRequired}:${it.coldChain}"
                }.sorted()
                val uiStateAuthSig = uiState.items.map {
                    "${it.sku.trim()}:${it.quantity}:${it.unitPrice.stripTrailingZeros().toPlainString()}:${it.prescriptionRequired}:${it.coldChain}"
                }.sorted()
                val freshAuthSig = authoritativeItems.map {
                    "${it.sku.trim()}:${it.quantity}:${it.unitPrice.stripTrailingZeros().toPlainString()}:${it.prescriptionRequired}:${it.coldChain}"
                }.sorted()

                // Compare against the last total actually shown to the customer when
                // reviewing this exact item set. The server quote may include server-only
                // components (for example surge) that are not represented by locally
                // recomputed fee components. When the settled cart brought in new items
                // that were not part of the previous uiState snapshot, compare against
                // the locally recomputed baseline for those new items.
                val currentGrandTotal = if (initialAuthSig == uiStateAuthSig) {
                    uiState.grandTotal
                } else {
                    currentSubtotal
                        .add(uiState.deliveryFee)
                        .add(uiState.coldChainFee)
                        .subtract(currentDiscount)
                        .coerceAtLeast(BigDecimal.ZERO)
                }

                val itemsChanged = initialAuthSig != freshAuthSig
                val totalChanged = authoritativeGrandTotal.compareTo(currentGrandTotal) != 0
                val subtotalChanged = authoritativeSubtotal.compareTo(currentSubtotal) != 0
                val discountChanged = authoritativeDiscount.compareTo(currentDiscount) != 0
                val deliveryFeeChanged = authoritativeDeliveryFee.compareTo(uiState.deliveryFee) != 0
                val coldChainFeeChanged = authoritativeColdChainFee.compareTo(uiState.coldChainFee) != 0

                val invalidatedCoupon = if (uiState.couponCode != null && (authoritativeCartQuote?.invalidCouponReason != null || (currentDiscount > BigDecimal.ZERO && authoritativeDiscount.compareTo(BigDecimal.ZERO) == 0))) {
                    uiState.couponCode
                } else null

                if (itemsChanged || totalChanged || subtotalChanged || discountChanged || deliveryFeeChanged || coldChainFeeChanged || invalidatedCoupon != null) {
                    val changeDetails = mutableListOf<String>()

                    if (invalidatedCoupon != null) {
                        val reason = authoritativeCartQuote?.invalidCouponReason ?: "Coupon '$invalidatedCoupon' is no longer active and has been removed."
                        changeDetails.add(reason)
                    } else if (discountChanged) {
                        changeDetails.add("Coupon discount changed from ${MoneyFormatter.format(currentDiscount)} to ${MoneyFormatter.format(authoritativeDiscount)}")
                    }

                    val oldItemMap = currentItems.associateBy { it.sku.trim() }
                    for (newItem in authoritativeItems) {
                        val oldItem = oldItemMap[newItem.sku.trim()]
                        if (oldItem != null && newItem.unitPrice.compareTo(oldItem.unitPrice) != 0) {
                            val diffName = newItem.name.ifBlank { newItem.sku }
                            changeDetails.add("Price of '$diffName' updated from ${MoneyFormatter.format(oldItem.unitPrice)} to ${MoneyFormatter.format(newItem.unitPrice)}")
                        }
                    }
                    for (oldItem in currentItems) {
                        if (authoritativeItems.none { it.sku.trim() == oldItem.sku.trim() }) {
                            changeDetails.add("'${oldItem.name.ifBlank { oldItem.sku }}' is no longer available and was removed.")
                        }
                    }
                    if (deliveryFeeChanged) {
                        changeDetails.add("Delivery fee updated to ${MoneyFormatter.format(authoritativeDeliveryFee)}")
                    }
                    if (coldChainFeeChanged) {
                        changeDetails.add("Packaging fee updated to ${MoneyFormatter.format(authoritativeColdChainFee)}")
                    }

                    val noticeMessage = if (changeDetails.isEmpty()) {
                        "Cart contents or price updated. Please review and confirm your order."
                    } else {
                        changeDetails.joinToString("\n")
                    }

                    authoritativeCartQuote?.let { quote ->
                        gate?.reconcileWithServer(quote, invalidatedCouponCode = invalidatedCoupon, changeNotice = noticeMessage)
                    }
                    val effectiveCoupon = if (invalidatedCoupon != null) null else uiState.couponCode
                    uiState = uiState.copy(
                        items = authoritativeItems,
                        itemsSubtotal = authoritativeSubtotal,
                        couponCode = effectiveCoupon,
                        couponDiscount = authoritativeDiscount,
                        deliveryFee = authoritativeDeliveryFee,
                        coldChainFee = authoritativeColdChainFee,
                        grandTotal = authoritativeGrandTotal,
                        isProcessing = false,
                        errorMessage = noticeMessage
                    )
                    return@launch
                }

                uiState = uiState.copy(
                    items = authoritativeItems,
                    itemsSubtotal = authoritativeSubtotal,
                    couponDiscount = authoritativeDiscount,
                    deliveryFee = authoritativeDeliveryFee,
                    coldChainFee = authoritativeColdChainFee,
                    grandTotal = authoritativeGrandTotal
                )

                val address = uiState.address
                if (address == null || address.id.isBlank()) {
                    failCheckout("CHECKOUT_NOT_READY", "Select a delivery address")
                    return@launch
                }
                if (authoritativeItems.isEmpty()) {
                    failCheckout("CHECKOUT_NOT_READY", "Cart is empty")
                    return@launch
                }

                // Rerun server serviceability check against settled authoritative cart items & address
                val requestItems = authoritativeItems.map { ServiceabilityItem(it.sku, it.quantity, it.coldChain) }
                when (val srvResult = repository.getServiceability(customerIdSnapshot, address.id, requestItems)) {
                    is ApiResult.Success -> {
                        if (!srvResult.data.eligible) {
                            failCheckout("UNSERVICEABLE", "Selected address is currently outside our delivery zone.")
                            return@launch
                        }
                        val deliveryFee = BigDecimal.valueOf(srvResult.data.deliveryFee)
                        val coldChainFee = BigDecimal.valueOf(srvResult.data.coldChainFee)

                        // Refresh server quote after serviceability to check quote against final serviceability rates
                        val refreshedQuote = repository.fetchBackendCart(customerIdSnapshot, couponParam)
                        if (refreshedQuote !is ApiResult.Success) {
                            failCheckout("CART_SYNC_ERROR", (refreshedQuote as? ApiResult.Failure)?.error?.message?.ifBlank { "Failed to refresh final quote" } ?: "Failed to refresh final quote")
                            return@launch
                        }
                        val refreshedData = refreshedQuote.data
                        val refreshedItems = refreshedData.items
                        val finalGrandTotal = refreshedData.grandTotal

                        if (refreshedItems != null && refreshedItems.isNotEmpty()) {
                            if (finalGrandTotal == null || refreshedData.itemsSubtotal == null) {
                                failCheckout("CART_SYNC_ERROR", "Server returned incomplete quote after serviceability check: missing subtotal or grandTotal")
                                return@launch
                            }
                            val refreshedSubtotal = refreshedData.itemsSubtotal
                            val refreshedDiscount = (refreshedData.discount ?: BigDecimal.ZERO).min(refreshedSubtotal)
                            val finalDeliveryFee = refreshedData.expressDeliveryFee ?: deliveryFee
                            val finalColdChainFee = refreshedData.coldChainPackagingFee ?: coldChainFee

                            val authSig = authoritativeItems.map {
                                "${it.sku.trim()}:${it.quantity}:${it.unitPrice.stripTrailingZeros().toPlainString()}:${it.prescriptionRequired}:${it.coldChain}"
                            }.sorted()
                            val refSig = refreshedItems.map {
                                "${it.sku.trim()}:${it.quantity}:${it.unitPrice.stripTrailingZeros().toPlainString()}:${it.prescriptionRequired}:${it.coldChain}"
                            }.sorted()

                            val finalItemsChanged = authSig != refSig
                            val finalPricingChanged = finalGrandTotal.compareTo(authoritativeGrandTotal) != 0 ||
                                refreshedSubtotal.compareTo(authoritativeSubtotal) != 0 ||
                                refreshedDiscount.compareTo(authoritativeDiscount) != 0 ||
                                finalDeliveryFee.compareTo(deliveryFee) != 0 ||
                                finalColdChainFee.compareTo(coldChainFee) != 0

                            val finalInvalidatedCoupon = if (uiState.couponCode != null && (refreshedData.invalidCouponReason != null || (authoritativeDiscount > BigDecimal.ZERO && refreshedDiscount.compareTo(BigDecimal.ZERO) == 0))) {
                                uiState.couponCode
                            } else null

                            if (finalItemsChanged || finalPricingChanged || finalInvalidatedCoupon != null) {
                                val changeDetails = mutableListOf<String>()

                                if (finalInvalidatedCoupon != null) {
                                    val reason = refreshedData.invalidCouponReason ?: "Coupon '$finalInvalidatedCoupon' is no longer active and has been removed."
                                    changeDetails.add(reason)
                                } else if (refreshedDiscount.compareTo(authoritativeDiscount) != 0) {
                                    changeDetails.add("Coupon discount changed from ${MoneyFormatter.format(authoritativeDiscount)} to ${MoneyFormatter.format(refreshedDiscount)}")
                                }

                                val oldItemMap = authoritativeItems.associateBy { it.sku.trim() }
                                for (newItem in refreshedItems) {
                                    val oldItem = oldItemMap[newItem.sku.trim()]
                                    if (oldItem != null && newItem.unitPrice.compareTo(oldItem.unitPrice) != 0) {
                                        val diffName = newItem.name.ifBlank { newItem.sku }
                                        changeDetails.add("Price of '$diffName' updated from ${MoneyFormatter.format(oldItem.unitPrice)} to ${MoneyFormatter.format(newItem.unitPrice)}")
                                    }
                                }
                                for (oldItem in authoritativeItems) {
                                    if (refreshedItems.none { it.sku.trim() == oldItem.sku.trim() }) {
                                        changeDetails.add("'${oldItem.name.ifBlank { oldItem.sku }}' is no longer available and was removed.")
                                    }
                                }
                                if (finalDeliveryFee.compareTo(deliveryFee) != 0) {
                                    changeDetails.add("Delivery fee updated to ${MoneyFormatter.format(finalDeliveryFee)}")
                                }
                                if (finalColdChainFee.compareTo(coldChainFee) != 0) {
                                    changeDetails.add("Packaging fee updated to ${MoneyFormatter.format(finalColdChainFee)}")
                                }

                                val noticeMessage = if (changeDetails.isEmpty()) {
                                    "Cart contents or pricing updated. Please review and confirm your order."
                                } else {
                                    changeDetails.joinToString("\n")
                                }

                                gate?.reconcileWithServer(refreshedData, invalidatedCouponCode = finalInvalidatedCoupon, changeNotice = noticeMessage)
                                val effectiveCoupon = if (finalInvalidatedCoupon != null) null else uiState.couponCode
                                uiState = uiState.copy(
                                    items = refreshedItems,
                                    itemsSubtotal = refreshedSubtotal,
                                    couponCode = effectiveCoupon,
                                    couponDiscount = refreshedDiscount,
                                    serviceability = ServiceabilityState.Success(address.id, srvResult.data),
                                    deliveryFee = finalDeliveryFee,
                                    coldChainFee = finalColdChainFee,
                                    grandTotal = finalGrandTotal,
                                    isProcessing = false,
                                    errorMessage = noticeMessage
                                )
                                return@launch
                            }

                            uiState = uiState.copy(
                                items = refreshedItems,
                                itemsSubtotal = refreshedSubtotal,
                                couponDiscount = refreshedDiscount,
                                serviceability = ServiceabilityState.Success(address.id, srvResult.data),
                                deliveryFee = finalDeliveryFee,
                                coldChainFee = finalColdChainFee,
                                grandTotal = finalGrandTotal
                            )
                        } else {
                            failCheckout("CART_SYNC_ERROR", "Server returned incomplete quote after serviceability check: missing items or grandTotal")
                            return@launch
                        }
                    }
                    is ApiResult.Failure -> {
                        failCheckout("SERVICEABILITY_ERROR", srvResult.error.message)
                        return@launch
                    }
                }

                // Re-read and revalidate state after mutation barrier & serviceability refresh
                val freshSnapshot = uiState
                if (!freshSnapshot.readyForPayment) {
                    val reason = when {
                        !freshSnapshot.addressValid -> "Select a delivery address"
                        !freshSnapshot.serviceabilityValid -> "Resolve delivery availability first"
                        !freshSnapshot.cartValid -> "Cart is empty"
                        !freshSnapshot.pricingValid -> "Checkout quote is incomplete"
                        !freshSnapshot.prescriptionValid -> "Attach a pharmacist-approved prescription"
                        else -> "Checkout is not ready for payment"
                    }
                    failCheckout("CHECKOUT_NOT_READY", reason)
                    return@launch
                }

                uiState = freshSnapshot.copy(isProcessing = true, errorMessage = null, state = CheckoutState.PlacingOrder)

                val prescriptionApproved = if (freshSnapshot.items.any { it.prescriptionRequired }) {
                    val serverPrescriptions = when (
                        val result = repository.getCustomerPrescriptions(customerIdSnapshot)
                    ) {
                        is ApiResult.Success -> result.data
                        is ApiResult.Failure -> {
                            failCheckout("PRESCRIPTION_LOOKUP_FAILED", result.error.message)
                            return@launch
                        }
                    }

                    serverPrescriptions.any {
                        it.id == freshSnapshot.prescriptionId &&
                            it.customerId == customerIdSnapshot &&
                            it.status.equals("APPROVED", ignoreCase = true)
                    }
                } else {
                    true
                }

                if (!prescriptionApproved) {
                    failCheckout(
                        "PRESCRIPTION_NOT_APPROVED",
                        "Attach a pharmacist-approved prescription before checkout."
                    )
                    return@launch
                }

                val currentFingerprint = computePayloadFingerprint(
                    customerIdSnapshot,
                    address,
                    freshSnapshot.items,
                    freshSnapshot.prescriptionId,
                    freshSnapshot.couponCode,
                    canonicalPaymentMethod,
                    freshSnapshot.selectedSlotText
                )
                if (lastPayloadFingerprint != currentFingerprint) {
                    val cleared = persistIdempotency(null, currentFingerprint)
                    if (!cleared) {
                        failCheckout("STORAGE_ERROR", "Failed to clear stale checkout idempotency state")
                        return@launch
                    }
                }

                val idempotencyKey = checkoutIdempotencyKey
                    ?: run {
                        val newKey = UUID.randomUUID().toString()
                        val persisted = persistIdempotency(newKey, currentFingerprint)
                        if (!persisted) {
                            failCheckout("STORAGE_ERROR", "Failed to persist checkout idempotency key")
                            return@launch
                        }
                        newKey
                    }

                // SERVER-AUTHORITATIVE AMOUNTS: omit client-calculated fees/discounts to eliminate trust-boundary risk
                val request = CartCheckoutRequest(
                    addressId = address.id,
                    prescriptionId = freshSnapshot.prescriptionId,
                    paymentMethod = canonicalPaymentMethod,
                    idempotencyKey = idempotencyKey,
                    deliveryAddress = address,
                    deliveryFee = null,
                    discountAmount = null,
                    couponCode = freshSnapshot.couponCode,
                    packagingFee = null,
                    slotText = freshSnapshot.selectedSlotText
                )

                when (val result = repository.checkoutFromCart(customerIdSnapshot, request)) {
                    is ApiResult.Success -> {
                        uiState = freshSnapshot.copy(state = CheckoutState.Confirmed(result.data), isProcessing = false)
                        val cleared = persistIdempotency(null, null)
                        if (!cleared) {
                            recordPendingKeyRotation(result.data.id)
                        }
                        _events.send(CheckoutEvent.OrderPlaced(result.data.id, result.data))
                    }
                    is ApiResult.Failure -> {
                        // Keep checkoutIdempotencyKey for identical payload retry
                        failCheckout("CHECKOUT_FAILED", result.error.message)
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failCheckout("CHECKOUT_ERROR", e.message ?: "Unable to proceed with checkout")
            } finally {
                if (uiState.state !is CheckoutState.Confirmed) {
                    gate?.unlockMutations()
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        mutationGate?.unlockMutations()
    }

    fun reset() {
        mutationGate?.unlockMutations()
        serviceabilityJob?.cancel()
        checkoutJob?.cancel()
        uiState = CheckoutUiState()
        customerId = ""
    }
}
