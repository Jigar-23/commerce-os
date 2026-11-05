package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import java.math.BigDecimal
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch

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
        get() = itemsSubtotal > BigDecimal.ZERO && grandTotal >= BigDecimal.ZERO

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
    private var mutationGate: CartMutationGate? = null
) : ViewModel() {

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

    fun executeCheckout(paymentMethod: String) {
        if (uiState.isProcessing || uiState.state is CheckoutState.Confirmed) return
        val customerIdSnapshot = customerId.trim()
        if (customerIdSnapshot.isBlank()) {
            uiState = uiState.copy(errorMessage = "Authentication required for checkout")
            return
        }

        checkoutJob?.cancel()
        checkoutJob = viewModelScope.launch {
            mutationGate?.lockMutations()
            mutationGate?.awaitMutationsIdle()

            // Re-read and revalidate state after mutation barrier
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

            val address = freshSnapshot.address
            if (address == null) {
                failCheckout("CHECKOUT_NOT_READY", "Select a delivery address")
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
                paymentMethod,
                freshSnapshot.selectedSlotText
            )
            if (lastPayloadFingerprint != currentFingerprint) {
                checkoutIdempotencyKey = null
                lastPayloadFingerprint = currentFingerprint
            }

            val idempotencyKey = checkoutIdempotencyKey
                ?: UUID.randomUUID().toString().also { checkoutIdempotencyKey = it }

            // SERVER-AUTHORITATIVE AMOUNTS: omit client-calculated fees/discounts to eliminate trust-boundary risk
            val request = CartCheckoutRequest(
                addressId = address.id,
                prescriptionId = freshSnapshot.prescriptionId,
                paymentMethod = paymentMethod,
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
                    checkoutIdempotencyKey = null
                    lastPayloadFingerprint = null
                    _events.send(CheckoutEvent.OrderPlaced(result.data.id, result.data))
                }
                is ApiResult.Failure -> {
                    // Keep checkoutIdempotencyKey for identical payload retry
                    failCheckout("CHECKOUT_FAILED", result.error.message)
                }
            }
        }
    }

    fun reset() {
        mutationGate?.unlockMutations()
        serviceabilityJob?.cancel()
        checkoutJob?.cancel()
        checkoutIdempotencyKey = null
        lastPayloadFingerprint = null
        uiState = CheckoutUiState()
        customerId = ""
    }
}
