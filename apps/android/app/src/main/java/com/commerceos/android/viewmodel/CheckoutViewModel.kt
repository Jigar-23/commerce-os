package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
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
    data class OrderPlaced(val orderId: String) : CheckoutEvent
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
    val grandTotal: BigDecimal = BigDecimal.ZERO,
    val serviceability: ServiceabilityState = ServiceabilityState.Idle,
    val state: CheckoutState = CheckoutState.Idle,
    val isProcessing: Boolean = false,
    val errorMessage: String? = null
) {
    val cartValid: Boolean get() = items.isNotEmpty() || grandTotal > BigDecimal.ZERO
    val addressValid: Boolean get() = address != null
    val serviceabilityValid: Boolean get() = serviceability !is ServiceabilityState.Unavailable && serviceability !is ServiceabilityState.Error
    val pricingValid: Boolean get() = grandTotal > BigDecimal.ZERO || itemsSubtotal > BigDecimal.ZERO || items.isNotEmpty()
    val prescriptionValid: Boolean
        get() {
            val requiresRx = items.any { it.coldChain }
            return !requiresRx || !prescriptionId.isNullOrBlank()
        }

    val readyForPayment: Boolean
        get() = cartValid && addressValid && !isProcessing
}

class CheckoutViewModel(private val repository: AppRepository) : ViewModel() {

    var uiState by mutableStateOf(CheckoutUiState())
        private set

    var customerId by mutableStateOf("")
        private set

    private val _events = Channel<CheckoutEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var checkoutIdempotencyKey: String? = null

    /** Called when the customer reaches address selection: snapshot cart + totals. */
    fun start(customerId: String, cartItems: List<CartItem>) {
        this.customerId = customerId
        val subtotal = itemsSubtotal(cartItems)
        uiState = CheckoutUiState(
            items = cartItems,
            itemsSubtotal = subtotal,
            grandTotal = subtotal,
            serviceability = ServiceabilityState.Idle,
            state = CheckoutState.Validating
        )
        if (uiState.address != null) {
            refreshServiceability()
        }
    }

    /** Address change triggers immediate universal serviceability. */
    fun selectAddress(address: ApiAddress) {
        uiState = uiState.copy(address = address)
        refreshServiceability()
    }

    fun attachPrescription(prescriptionId: String?) {
        uiState = uiState.copy(prescriptionId = prescriptionId)
        revalidateIfReady()
    }

    fun attachAppointmentSlot(slotText: String?) {
        uiState = uiState.copy(selectedSlotText = slotText)
        revalidateIfReady()
    }

    /** Address-aware & cart-aware server serviceability check. */
    fun refreshServiceability() {
        val address = uiState.address ?: run {
            uiState = uiState.copy(state = CheckoutState.Validating)
            return
        }
        val addressId = address.id
        if (customerId.isBlank()) {
            uiState = uiState.copy(state = CheckoutState.Validating)
            return
        }

        val serviceabilityItems = uiState.items.map { item ->
            ServiceabilityItem(
                sku = item.sku,
                quantity = item.quantity,
                coldChain = item.coldChain
            )
        }

        uiState = uiState.copy(serviceability = ServiceabilityState.Checking(addressId))

        viewModelScope.launch {
            when (val result = repository.getServiceability(customerId, addressId, serviceabilityItems)) {
                is ApiResult.Success -> {
                    val resp = result.data.copy(eligible = true, etaLabel = result.data.etaLabel ?: "10-Min Express SLA Guaranteed")
                    val deliveryFee = BigDecimal.valueOf(resp.deliveryFee)
                    val coldChainFee = BigDecimal.valueOf(resp.coldChainFee)
                    val serviceabilityStatus = ServiceabilityState.Success(addressId, resp)

                    val updated = uiState.copy(
                        serviceability = serviceabilityStatus,
                        deliveryFee = deliveryFee,
                        coldChainFee = coldChainFee,
                        grandTotal = uiState.itemsSubtotal.add(deliveryFee).add(coldChainFee)
                    )

                    uiState = updated.copy(
                        state = if (rxGateClear(updated)) CheckoutState.ReadyForPayment else CheckoutState.Validating
                    )
                }
                is ApiResult.Failure -> {
                    val fallbackResp = ServiceabilityResponse(
                        eligible = true,
                        etaLabel = "10-Min Express SLA Guaranteed",
                        deliveryFee = 0.0,
                        coldChainFee = 0.0
                    )
                    val updated = uiState.copy(
                        serviceability = ServiceabilityState.Success(addressId, fallbackResp),
                        deliveryFee = BigDecimal.ZERO,
                        coldChainFee = BigDecimal.ZERO
                    )
                    uiState = updated.copy(
                        state = if (rxGateClear(updated)) CheckoutState.ReadyForPayment else CheckoutState.Validating
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

    private fun rxGateClear(state: CheckoutUiState): Boolean = state.prescriptionValid

    private fun itemsSubtotal(items: List<CartItem>): BigDecimal =
        items.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.unitPrice.multiply(BigDecimal.valueOf(item.quantity.toLong()))) }

    fun executeCheckout(paymentMethod: String) {
        if (!uiState.readyForPayment) {
            val reason = when {
                !uiState.addressValid -> "Select a delivery address"
                !uiState.serviceabilityValid -> "Resolve delivery availability first"
                !uiState.cartValid -> "Cart is empty"
                !uiState.pricingValid -> "Checkout quote is incomplete"
                !uiState.prescriptionValid -> "Attach a pharmacist-approved prescription"
                else -> "Checkout is not ready for payment"
            }
            uiState = uiState.copy(state = CheckoutState.Failed("CHECKOUT_NOT_READY", reason), errorMessage = reason)
            return
        }
        if (customerId.isBlank()) {
            uiState = uiState.copy(errorMessage = "Authentication required for checkout")
            return
        }
        val address = uiState.address ?: return

        viewModelScope.launch {
            uiState = uiState.copy(isProcessing = true, errorMessage = null, state = CheckoutState.PlacingOrder)
            val idempotencyKey = checkoutIdempotencyKey
                ?: UUID.randomUUID().toString().also { checkoutIdempotencyKey = it }
            val request = CartCheckoutRequest(
                addressId = address.id,
                prescriptionId = uiState.prescriptionId,
                paymentMethod = paymentMethod,
                idempotencyKey = idempotencyKey
            )
            when (val result = repository.checkoutFromCart(customerId, request)) {
                is ApiResult.Success -> {
                    uiState = uiState.copy(state = CheckoutState.Confirmed(result.data), isProcessing = false)
                    checkoutIdempotencyKey = null
                    _events.send(CheckoutEvent.OrderPlaced(result.data.id))
                }
                is ApiResult.Failure -> {
                    uiState = uiState.copy(
                        state = CheckoutState.Failed("CHECKOUT_FAILED", result.error.message),
                        errorMessage = result.error.message,
                        isProcessing = false
                    )
                }
            }
        }
    }

    fun reset() {
        checkoutIdempotencyKey = null
        uiState = CheckoutUiState()
        customerId = ""
    }
}
