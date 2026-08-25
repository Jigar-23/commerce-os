package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.data.local.CartDao
import com.commerceos.android.data.local.CartItemEntity
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal

/** One-shot outcome of a cart mutation. State and events are separate concerns:
 *  the UI observes [CartViewModel.addEvents] for a single delivery, while the
 *  persistent cart contents stay in [cartItems]. */
sealed interface CartAddEvent {
    data class Success(val sku: String, val name: String) : CartAddEvent
    data class Failure(val message: String) : CartAddEvent
}

class CartViewModel(
    private val repository: AppRepository,
    private val cartDao: CartDao? = null
) : ViewModel() {

    var customerId by mutableStateOf("")
        private set

    var cartItems by mutableStateOf<List<CartItem>>(emptyList())
        private set

    var totals by mutableStateOf<CartResponse?>(null)
        private set

    var isLoading by mutableStateOf(false)
        private set

    var errorMessage by mutableStateOf<String?>(null)
        private set

    var saveForLaterItems by mutableStateOf<List<CartItem>>(emptyList())
        private set

    var appliedCouponCode by mutableStateOf<String?>(null)
        private set

    var couponDiscountAmount by mutableStateOf(BigDecimal.ZERO)
        private set

    /** Signature of the current cart used as serviceability context. */
    val cartSignature: String get() = (cartItems ?: emptyList()).joinToString("|") { "${it.sku}:${it.quantity}" }

    private val _addEvents = Channel<CartAddEvent>(Channel.BUFFERED)
    val addEvents = _addEvents.receiveAsFlow()

    val containsRx: Boolean get() = (cartItems ?: emptyList()).any { it.prescriptionRequired }
    val containsColdChain: Boolean get() = (cartItems ?: emptyList()).any { it.coldChain }
    /** Badge/header count = number of distinct cart ROWS, not summed quantity. */
    val itemCount: Int get() = (cartItems ?: emptyList()).size

    val effectiveSubtotal: BigDecimal
        get() = totals?.itemsSubtotal ?: (cartItems ?: emptyList()).fold(BigDecimal.ZERO) { acc, item ->
            val price = item.unitPrice
            acc.add(price.multiply(BigDecimal.valueOf(item.quantity.toLong())))
        }

    val freeDeliveryThreshold: BigDecimal
        get() = totals?.freeDeliveryThreshold ?: BigDecimal("199.00")

    val freeDeliveryEligible: Boolean
        get() = totals?.freeDeliveryEligible ?: (effectiveSubtotal >= freeDeliveryThreshold)

    val remainingForFreeDelivery: BigDecimal
        get() = totals?.remainingForFreeDelivery ?: (freeDeliveryThreshold.subtract(effectiveSubtotal)).coerceAtLeast(BigDecimal.ZERO)

    val effectiveExpressFee: BigDecimal
        get() = totals?.expressDeliveryFee ?: if (freeDeliveryEligible || effectiveSubtotal == BigDecimal.ZERO) BigDecimal.ZERO else BigDecimal("2.00")

    val effectiveColdChainFee: BigDecimal
        get() = totals?.coldChainPackagingFee ?: if (containsColdChain) BigDecimal("15.00") else BigDecimal.ZERO

    val effectiveGrandTotal: BigDecimal
        get() = totals?.grandTotal ?: effectiveSubtotal.add(effectiveExpressFee).add(effectiveColdChainFee).subtract(couponDiscountAmount).coerceAtLeast(BigDecimal.ZERO)

    val mrpTotal: BigDecimal
        get() = (cartItems ?: emptyList()).fold(BigDecimal.ZERO) { acc, i ->
            val price = i.mrp ?: i.unitPrice
            acc.add(price.multiply(BigDecimal.valueOf(i.quantity.toLong())))
        }

    val totalSavings: BigDecimal
        get() = if (totals != null) mrpTotal.subtract(totals!!.itemsSubtotal).coerceAtLeast(BigDecimal.ZERO)
                else mrpTotal.subtract(effectiveSubtotal).coerceAtLeast(BigDecimal.ZERO)

    private val LOCAL_OFFLINE_CART_KEY = "local_offline_cart"

    init {
        loadFromLocalStorage(LOCAL_OFFLINE_CART_KEY)
    }

    fun init(customerId: String) {
        val targetId = customerId.trim()
        if (this.customerId != targetId) {
            this.customerId = targetId
            if (targetId.isNotBlank()) {
                // Merge any offline guest cart items to authoritative server account upon login
                viewModelScope.launch {
                    if (cartDao != null) {
                        try {
                            val guestEntities = cartDao.getCartItems(LOCAL_OFFLINE_CART_KEY)
                            if (guestEntities.isNotEmpty()) {
                                var hasFailure = false
                                for (guestItem in guestEntities) {
                                    val res = repository.addCartItem(targetId, guestItem.toCartItem())
                                    if (res is com.commerceos.android.network.ApiResult.Success) {
                                        cartDao.deleteBySku(guestItem.sku, LOCAL_OFFLINE_CART_KEY)
                                    } else {
                                        hasFailure = true
                                    }
                                }
                                if (hasFailure) {
                                    errorMessage = "Some offline guest cart items could not be synced to your account."
                                }
                            }
                        } catch (e: Exception) {
                            errorMessage = "Cart synchronization encountered an error: ${e.message}"
                        }
                    }
                    loadFromLocalStorage(targetId)
                    loadCart()
                }
            } else {
                loadFromLocalStorage(LOCAL_OFFLINE_CART_KEY)
            }
        }
    }

    private fun loadFromLocalStorage(targetCustomerId: String) {
        if (cartDao == null) return
        viewModelScope.launch {
            try {
                val localEntities = cartDao.getCartItems(targetCustomerId)
                if (localEntities.isNotEmpty() && cartItems.isEmpty()) {
                    cartItems = localEntities.map { it.toCartItem() }
                }
            } catch (e: Exception) {
                // Ignore local read errors on startup
            }
        }
    }

    fun loadCart() {
        val activeCustId = customerId.trim()
        if (activeCustId.isBlank()) {
            // Unauthenticated: load from local offline cache only
            loadFromLocalStorage(LOCAL_OFFLINE_CART_KEY)
            return
        }
        viewModelScope.launch {
            // 1. Immediately hydrate from Room DB so UI never flashes empty
            if (cartDao != null) {
                try {
                    val localEntities = cartDao.getCartItems(activeCustId)
                    if (localEntities.isNotEmpty()) {
                        cartItems = localEntities.map { it.toCartItem() }
                    }
                } catch (e: Exception) {
                    // fallback to network
                }
            }

            isLoading = true
            when (val resp = repository.fetchBackendCart(activeCustId)) {
                is ApiResult.Success -> {
                    val backendItems = resp.data.items ?: emptyList()
                    cartItems = backendItems
                    totals = resp.data
                    errorMessage = null

                    // 2. Persist backend items into Room DB cache
                    if (cartDao != null) {
                        try {
                            cartDao.clearCart(activeCustId)
                            if (backendItems.isNotEmpty()) {
                                cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, activeCustId) })
                            }
                        } catch (e: Exception) {
                            // ignore cache write error
                        }
                    }
                }
                is ApiResult.Failure -> {
                    errorMessage = resp.error.message
                }
            }
            isLoading = false
        }
    }

    private val cartMutex = kotlinx.coroutines.sync.Mutex()

    fun addItem(product: CommerceProduct) {
        viewModelScope.launch {
            cartMutex.withLock {
                val pharmacyAttr = product.medicineDetails?.let {
                    PharmacyCartAttributes(
                        prescriptionRequired = it.prescriptionRequired,
                        coldChain = it.coldChain
                    )
                }
                val safeSellingPrice = product.sellingPrice
                val safeMrp = if (product.price > safeSellingPrice) product.price else safeSellingPrice
                val item = CartItem(
                    productId = product.id,
                    sku = product.sku,
                    name = product.name,
                    unitPrice = java.math.BigDecimal.valueOf(safeSellingPrice),
                    quantity = 1,
                    verticalId = product.verticalId ?: "general",
                    merchantId = product.merchantId,
                    mrp = java.math.BigDecimal.valueOf(safeMrp),
                    pharmacyAttributes = pharmacyAttr
                )

                if (customerId.isNotBlank()) {
                    // Server-first authoritative mutation: only persist locally if server accepts
                    val updated = repository.addCartItem(customerId, item)
                    when (updated) {
                        is ApiResult.Success -> {
                            val backendItems = updated.data.items ?: (cartItems + item)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            _addEvents.trySend(CartAddEvent.Success(product.sku, product.name))
                            if (cartDao != null) {
                                try {
                                    cartDao.clearCart(customerId)
                                    cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, customerId) })
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            val errMsg = updated.error.message ?: "Could not add item to cart. Please check your network connection."
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                        }
                    }
                } else {
                    // Local offline guest add
                    cartItems = cartItems + item
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(item, LOCAL_OFFLINE_CART_KEY))
                        } catch (_: Exception) {}
                    }
                    _addEvents.trySend(CartAddEvent.Success(product.sku, product.name))
                }
            }
        }
    }

    fun addReorderItem(item: OrderItem) {
        viewModelScope.launch {
            cartMutex.withLock {
                val pharmacyAttr = if (item.rxRequired) PharmacyCartAttributes(prescriptionRequired = true, coldChain = false) else null
                val cartItem = CartItem(
                    productId = item.productId.ifBlank { item.sku },
                    sku = item.sku,
                    name = item.name,
                    unitPrice = item.unitPrice,
                    quantity = item.quantity.coerceAtLeast(1),
                    verticalId = item.verticalId,
                    merchantId = item.merchantId,
                    mrp = item.unitPrice,
                    pharmacyAttributes = pharmacyAttr
                )

                if (customerId.isNotBlank()) {
                    when (val updated = repository.addCartItem(customerId, cartItem)) {
                        is ApiResult.Success -> {
                            val backendItems = updated.data.items ?: (cartItems + cartItem)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            _addEvents.trySend(CartAddEvent.Success(item.sku, item.name))
                            if (cartDao != null) {
                                try {
                                    cartDao.clearCart(customerId)
                                    cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, customerId) })
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            val errMsg = updated.error.message ?: "Could not reorder item. Please check your network connection."
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                        }
                    }
                } else {
                    cartItems = cartItems + cartItem
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(cartItem, LOCAL_OFFLINE_CART_KEY))
                        } catch (_: Exception) {}
                    }
                    _addEvents.trySend(CartAddEvent.Success(item.sku, item.name))
                }
            }
        }
    }

    fun updateQuantity(sku: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(sku)
            return
        }
        viewModelScope.launch {
            cartMutex.withLock {
                if (customerId.isNotBlank()) {
                    when (val updated = repository.updateCartQuantity(customerId, sku, quantity)) {
                        is ApiResult.Success -> {
                            val backendItems = updated.data.items ?: cartItems.map { if (it.sku == sku) it.copy(quantity = quantity) else it }
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            if (cartDao != null) {
                                try {
                                    cartDao.clearCart(customerId)
                                    cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, customerId) })
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            errorMessage = updated.error.message ?: "Could not update item quantity."
                        }
                    }
                } else {
                    cartItems = cartItems.map { if (it.sku == sku) it.copy(quantity = quantity) else it }
                    if (cartDao != null) {
                        val targetItem = cartItems.find { it.sku == sku }
                        if (targetItem != null) {
                            try {
                                cartDao.insertOrUpdate(CartItemEntity.fromCartItem(targetItem, LOCAL_OFFLINE_CART_KEY))
                            } catch (_: Exception) {}
                        }
                    }
                }
            }
        }
    }

    fun removeItem(sku: String) {
        viewModelScope.launch {
            cartMutex.withLock {
                if (customerId.isNotBlank()) {
                    val updated = repository.removeCartItem(customerId, sku)
                    when (updated) {
                        is ApiResult.Success -> {
                            val backendItems = updated.data.items ?: cartItems.filter { it.sku != sku }
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            if (cartDao != null) {
                                try {
                                    cartDao.deleteBySku(sku, customerId)
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            errorMessage = updated.error.message ?: "Could not remove item from cart."
                        }
                    }
                } else {
                    cartItems = cartItems.filter { it.sku != sku }
                    if (cartDao != null) {
                        try {
                            cartDao.deleteBySku(sku, LOCAL_OFFLINE_CART_KEY)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    }

    fun moveToSaveForLater(sku: String) {
        val targetItem = cartItems.find { it.sku == sku } ?: return
        cartItems = cartItems.filter { it.sku != sku }
        saveForLaterItems = saveForLaterItems + targetItem
        removeItem(sku)
    }

    fun moveBackToCart(sku: String) {
        val targetItem = saveForLaterItems.find { it.sku == sku } ?: return
        saveForLaterItems = saveForLaterItems.filter { it.sku != sku }
        val prod = CommerceProduct(
            id = targetItem.productId,
            sku = targetItem.sku,
            name = targetItem.name,
            price = targetItem.mrp?.toDouble() ?: targetItem.unitPrice.toDouble(),
            discountedPrice = targetItem.unitPrice.toDouble(),
            mrp = targetItem.mrp?.toDouble() ?: targetItem.unitPrice.toDouble(),
            inStock = true,
            verticalId = targetItem.verticalId,
            merchantId = targetItem.merchantId,
            medicineDetails = targetItem.pharmacyAttributes?.let {
                MedicineAttributes(
                    prescriptionRequired = it.prescriptionRequired,
                    coldChain = it.coldChain
                )
            }
        )
        addItem(prod)
    }

    fun applyCoupon(code: String): Boolean {
        if (code.isBlank()) return false
        appliedCouponCode = code.uppercase()
        couponDiscountAmount = if (code.equals("SAVE10", ignoreCase = true)) {
            BigDecimal.valueOf(100.0)
        } else {
            BigDecimal("5.00")
        }
        return true
    }

    fun removeCoupon() {
        appliedCouponCode = null
        couponDiscountAmount = BigDecimal.ZERO
    }

    fun clear() {
        val activeCustId = if (customerId.isNotBlank()) customerId else LOCAL_OFFLINE_CART_KEY
        cartItems = emptyList()
        totals = null
        appliedCouponCode = null
        couponDiscountAmount = BigDecimal.ZERO
        if (cartDao != null) {
            viewModelScope.launch {
                try {
                    cartDao.clearCart(activeCustId)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    fun reset() {
        clear()
    }
}
