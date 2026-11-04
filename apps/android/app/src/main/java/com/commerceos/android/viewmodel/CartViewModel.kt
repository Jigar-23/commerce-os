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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.RoundingMode
import com.commerceos.android.util.MoneyFormatter

/** One-shot outcome of a cart mutation. State and events are separate concerns:
 *  the UI observes [CartViewModel.addEvents] for a single delivery, while the
 *  persistent cart contents stay in [cartItems]. */
sealed interface CartAddEvent {
    data class Success(val sku: String, val name: String) : CartAddEvent
    data class Failure(val message: String) : CartAddEvent
}

class CartViewModel(
    private val repository: AppRepository,
    private val cartDao: CartDao? = null,
    private val appContext: android.content.Context? = null
) : ViewModel() {

    var customerId by mutableStateOf("")
        private set

    private var customerGeneration = 0L
    private var cartLoadJob: Job? = null
    private var accountSyncJob: Job? = null

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

    var couponFeedbackMessage by mutableStateOf<String?>(null)
    var isCouponError by mutableStateOf(false)

    companion object {
        val DEFAULT_CAMPAIGNS = listOf(
            CouponCampaign(
                id = "promo1",
                code = "WELCOME20",
                title = "20% OFF",
                description = "20% OFF up to ₹100 on orders above ₹299",
                minOrderValue = BigDecimal("299.00"),
                discountType = CouponDiscountType.PERCENTAGE,
                discountPercent = 20,
                maxDiscount = BigDecimal("100.00"),
                badge = "20% OFF",
                expiryText = "Valid till Dec 31",
                status = "ACTIVE"
            ),
            CouponCampaign(
                id = "promo2",
                code = "HEALTH30",
                title = "FLAT ₹150 OFF",
                description = "Flat ₹150 OFF on orders above ₹799",
                minOrderValue = BigDecimal("799.00"),
                discountType = CouponDiscountType.FLAT,
                flatDiscount = BigDecimal("150.00"),
                maxDiscount = BigDecimal("150.00"),
                badge = "₹150 OFF",
                expiryText = "Valid till Nov 30",
                status = "ACTIVE"
            ),
            CouponCampaign(
                id = "promo_save10",
                code = "SAVE10",
                title = "FLAT ₹100 OFF",
                description = "Flat ₹100 OFF on orders above ₹199",
                minOrderValue = BigDecimal("199.00"),
                discountType = CouponDiscountType.FLAT,
                flatDiscount = BigDecimal("100.00"),
                maxDiscount = BigDecimal("100.00"),
                badge = "INSTANT SAVINGS",
                expiryText = "Valid till Dec 31",
                status = "ACTIVE"
            )
        )
    }

    var availableCampaigns by mutableStateOf<List<CouponCampaign>>(emptyList())
        private set

    fun updateAvailableCampaigns(campaigns: List<CouponCampaign>) {
        availableCampaigns = campaigns
        syncCouponDiscount()
    }

    /** Signature of the current cart used as serviceability context. */
    val cartSignature: String get() = cartItems.joinToString("|") { "${it.sku}:${it.quantity}" }

    private val _addEvents = Channel<CartAddEvent>(Channel.BUFFERED)
    val addEvents = _addEvents.receiveAsFlow()

    val containsRx: Boolean get() = cartItems.any { it.prescriptionRequired }
    val containsColdChain: Boolean get() = cartItems.any { it.coldChain }
    /** Badge/header count = number of distinct cart ROWS, not summed quantity. */
    val itemCount: Int get() = cartItems.size

    val effectiveSubtotal: BigDecimal
        get() = cartItems.fold(BigDecimal.ZERO) { acc, item ->
            val price = item.unitPrice
            acc.add(price.multiply(BigDecimal.valueOf(item.quantity.toLong())))
        }

    val freeDeliveryThreshold: BigDecimal
        get() = totals?.freeDeliveryThreshold ?: com.commerceos.android.data.local.DeliveryConfigLocalStore.getFreeDeliveryThreshold(appContext)

    val freeDeliveryEligible: Boolean
        get() = totals?.freeDeliveryEligible ?: (effectiveSubtotal >= freeDeliveryThreshold)

    val remainingForFreeDelivery: BigDecimal
        get() = totals?.remainingForFreeDelivery ?: (freeDeliveryThreshold.subtract(effectiveSubtotal)).coerceAtLeast(BigDecimal.ZERO)

    val effectiveExpressFee: BigDecimal
        get() = totals?.expressDeliveryFee ?: if (freeDeliveryEligible || effectiveSubtotal == BigDecimal.ZERO) BigDecimal.ZERO else com.commerceos.android.data.local.DeliveryConfigLocalStore.getStandardDeliveryFee(appContext)

    val effectiveColdChainFee: BigDecimal
        get() = totals?.coldChainPackagingFee ?: if (containsColdChain) BigDecimal("15.00") else BigDecimal.ZERO

    val effectiveGrandTotal: BigDecimal
        get() = (totals?.grandTotal ?: effectiveSubtotal.add(effectiveExpressFee).add(effectiveColdChainFee)).subtract(couponDiscountAmount).coerceAtLeast(BigDecimal.ZERO)

    val mrpTotal: BigDecimal
        get() = cartItems.fold(BigDecimal.ZERO) { acc, i ->
            val price = i.mrp ?: i.unitPrice
            acc.add(price.multiply(BigDecimal.valueOf(i.quantity.toLong())))
        }

    val itemMrpSavings: BigDecimal
        get() {
            val sub = totals?.itemsSubtotal ?: effectiveSubtotal
            return mrpTotal.subtract(sub).coerceAtLeast(BigDecimal.ZERO)
        }

    val totalSavings: BigDecimal
        get() {
            return itemMrpSavings.add(couponDiscountAmount).coerceAtLeast(BigDecimal.ZERO)
        }

    private val LOCAL_OFFLINE_CART_KEY = "local_offline_cart"

    init {
        loadFromLocalStorage(LOCAL_OFFLINE_CART_KEY)
        refreshDeliveryConfigAndPromotions()
    }

    fun refreshDeliveryConfigAndPromotions() {
        viewModelScope.launch {
            appContext?.let {
                com.commerceos.android.data.local.DeliveryConfigLocalStore.syncIfStale(it)
            }
            try {
                when (val result = repository.getPromotions()) {
                    is ApiResult.Success -> {
                        availableCampaigns = result.data
                        syncCouponDiscount()
                    }
                    is ApiResult.Failure -> {
                        availableCampaigns = emptyList()
                        appliedCouponCode = null
                        couponDiscountAmount = BigDecimal.ZERO
                        couponFeedbackMessage = "Promotions could not be loaded."
                        isCouponError = true
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                availableCampaigns = emptyList()
                appliedCouponCode = null
                couponDiscountAmount = BigDecimal.ZERO
                couponFeedbackMessage = "Promotions could not be loaded."
                isCouponError = true
            }
        }
    }

    fun init(customerId: String) {
        val targetId = customerId.trim()
        if (this.customerId == targetId) {
            refreshDeliveryConfigAndPromotions()
            return
        }

        customerGeneration++
        val generation = customerGeneration
        accountSyncJob?.cancel()
        cartLoadJob?.cancel()
        isLoading = false // Account switch: reset loading immediately

        this.customerId = targetId
        cartItems = emptyList()
        totals = null
        saveForLaterItems = emptyList()
        appliedCouponCode = null
        couponDiscountAmount = BigDecimal.ZERO
        couponFeedbackMessage = null
        isCouponError = false
        errorMessage = null

        refreshDeliveryConfigAndPromotions()

        if (targetId.isBlank()) {
            loadFromLocalStorage(LOCAL_OFFLINE_CART_KEY)
            return
        }

        accountSyncJob = viewModelScope.launch {
            if (cartDao != null) {
                try {
                    val guestItems = cartDao.getCartItems(LOCAL_OFFLINE_CART_KEY)
                    for (guestItem in guestItems) {
                        if (generation != customerGeneration || customerId.trim() != targetId) return@launch
                        when (repository.addCartItem(targetId, guestItem.toCartItem())) {
                            is ApiResult.Success ->
                                cartDao.deleteBySku(guestItem.sku, LOCAL_OFFLINE_CART_KEY)
                            is ApiResult.Failure ->
                                errorMessage = "Some offline guest cart items could not be synced to your account."
                        }
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    if (generation == customerGeneration && customerId.trim() == targetId) {
                        errorMessage = "Cart synchronization encountered an error."
                    }
                }
            }

            if (generation == customerGeneration && customerId.trim() == targetId) loadCart()
        }
    }

    private fun isValidCartItem(item: CartItem): Boolean =
        item.quantity > 0 &&
            item.unitPrice > BigDecimal.ZERO &&
            item.sku.isNotBlank() &&
            !item.sku.equals("para", ignoreCase = true) &&
            item.name.isNotBlank() &&
            !item.name.equals("Medicine Item", ignoreCase = true)

    private fun sanitizeCartItems(rawItems: List<CartItem>): List<CartItem> {
        val valid = rawItems.filter(::isValidCartItem)
        val invalid = rawItems.filterNot(::isValidCartItem)
        val activeCustId = customerId.trim()

        invalid.forEach { item ->
            viewModelScope.launch {
                if (activeCustId.isNotBlank() && item.sku.isNotBlank()) {
                    try { repository.removeCartItem(activeCustId, item.sku) } catch (_: Exception) {}
                }
                if (cartDao != null && item.sku.isNotBlank()) {
                    try {
                        if (activeCustId.isNotBlank()) cartDao.deleteBySku(item.sku, activeCustId)
                        cartDao.deleteBySku(item.sku, LOCAL_OFFLINE_CART_KEY)
                    } catch (_: Exception) {}
                }
            }
        }
        return valid
    }

    private fun loadFromLocalStorage(targetCustomerId: String) {
        val dao = cartDao ?: return
        val generation = customerGeneration

        viewModelScope.launch {
            try {
                val items = dao.getCartItems(targetCustomerId)
                    .filter {
                        it.quantity > 0 &&
                            it.unitPrice > 0.0 &&
                            it.sku.isNotBlank() &&
                            !it.sku.equals("para", ignoreCase = true) &&
                            it.name.isNotBlank() &&
                            !it.name.equals("Medicine Item", ignoreCase = true)
                    }
                    .map { it.toCartItem() }

                val targetIsCurrent =
                    (targetCustomerId == LOCAL_OFFLINE_CART_KEY && customerId.isBlank()) ||
                        targetCustomerId == customerId

                if (generation == customerGeneration && targetIsCurrent) {
                    cartItems = items
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == customerGeneration) {
                    errorMessage = "Could not load the saved cart."
                }
            }
        }
    }

    fun loadCart() {
        val activeCustId = customerId.trim()
        if (activeCustId.isBlank()) {
            totals = null
            loadFromLocalStorage(LOCAL_OFFLINE_CART_KEY)
            return
        }

        cartLoadJob?.cancel()
        val generation = customerGeneration
        cartLoadJob = viewModelScope.launch {
            isLoading = true
            try {
                if (cartDao != null) {
                    val localItems = cartDao.getCartItems(activeCustId)
                        .filter {
                            it.quantity > 0 && it.unitPrice > 0.0 &&
                                it.sku.isNotBlank() &&
                                !it.sku.equals("para", ignoreCase = true) &&
                                it.name.isNotBlank() &&
                                !it.name.equals("Medicine Item", ignoreCase = true)
                        }
                        .map { it.toCartItem() }

                    if (generation != customerGeneration || customerId.trim() != activeCustId) return@launch
                    cartItems = localItems
                }

                when (val result = repository.fetchBackendCart(activeCustId)) {
                    is ApiResult.Success -> {
                        if (generation != customerGeneration || customerId.trim() != activeCustId) return@launch
                        val items = sanitizeCartItems(result.data.items ?: emptyList())
                        cartItems = items
                        totals = result.data
                        errorMessage = null

                        if (cartDao != null) {
                            try {
                                cartDao.clearCart(activeCustId)
                                if (items.isNotEmpty()) {
                                    cartDao.insertAll(items.map { CartItemEntity.fromCartItem(it, activeCustId) })
                                }
                            } catch (_: Exception) {}
                        }
                    }
                    is ApiResult.Failure -> {
                        if (generation == customerGeneration) errorMessage = result.error.message
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (generation == customerGeneration) {
                    errorMessage = "Could not load your cart."
                }
            } finally {
                if (generation == customerGeneration) isLoading = false
            }
        }
    }

    private val cartMutex = kotlinx.coroutines.sync.Mutex()

    fun addItem(product: CommerceProduct) {
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock

                if (product.sku.isBlank()) {
                    val message = "This product cannot be added because its SKU is missing."
                    errorMessage = message
                    _addEvents.trySend(CartAddEvent.Failure(message))
                    return@withLock
                }

                val safeSellingPrice = listOf(
                    product.effectiveSellingPrice,
                    product.sellingPrice,
                    product.discountedPrice,
                    product.price,
                    product.mrp ?: 0.0
                ).firstOrNull { it.isFinite() && it > 0.0 } ?: run {
                    val message = "This product price is unavailable."
                    errorMessage = message
                    _addEvents.trySend(CartAddEvent.Failure(message))
                    return@withLock
                }

                val safeMrp = listOf(
                    product.displayPrice,
                    product.price,
                    product.mrp ?: 0.0
                ).firstOrNull { it.isFinite() && it >= safeSellingPrice } ?: safeSellingPrice

                val pharmacyAttr = product.medicineDetails?.let {
                    PharmacyCartAttributes(
                        prescriptionRequired = it.prescriptionRequired,
                        coldChain = it.coldChain
                    )
                }
                val safeName = product.name.ifBlank { "Medicine" }
                val item = CartItem(
                    productId = product.id.ifBlank { product.sku },
                    sku = product.sku,
                    name = safeName,
                    unitPrice = BigDecimal.valueOf(safeSellingPrice),
                    quantity = 1,
                    verticalId = product.verticalId ?: "pharmacy",
                    merchantId = product.merchantId,
                    mrp = BigDecimal.valueOf(safeMrp),
                    pharmacyAttributes = pharmacyAttr
                )

                // 1. Optimistic Instant UI Update (0ms latency for ADD -> [- 1 +] button transition)
                val previousCartItems = cartItems
                val previousTotals = totals
                val existingIndex = cartItems.indexOfFirst { it.sku == item.sku }
                val updatedItem = if (existingIndex >= 0) {
                    val existing = cartItems[existingIndex]
                    existing.copy(quantity = existing.quantity + item.quantity)
                } else {
                    item
                }
                cartItems = if (existingIndex >= 0) {
                    cartItems.toMutableList().also { it[existingIndex] = updatedItem }
                } else {
                    cartItems + item
                }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.addCartItem(targetCustomerId, item)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            val backendItems = sanitizeCartItems(updated.data.items ?: cartItems)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            _addEvents.trySend(CartAddEvent.Success(product.sku, product.name))
                            if (cartDao != null) {
                                try {
                                    cartDao.clearCart(targetCustomerId)
                                    cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, targetCustomerId) })
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            // Rollback optimistic update on failure to preserve authoritative state
                            cartItems = previousCartItems
                            totals = previousTotals
                            val errMsg = updated.error.message ?: "Could not add item to cart. Please check your network connection."
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                        }
                    }
                } else {
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(updatedItem, LOCAL_OFFLINE_CART_KEY))
                        } catch (_: Exception) {}
                    }
                    _addEvents.trySend(CartAddEvent.Success(product.sku, product.name))
                }
                syncCouponDiscount()
            }
        }
    }

    fun addReorderItem(item: OrderItem) {
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock

                val sku = item.effectiveSku
                val name = item.effectiveName
                val price = item.effectivePrice
                if (sku.isBlank() || sku.equals("SKU_ITEM", ignoreCase = true) || price <= BigDecimal.ZERO) {
                    val message = "This item cannot be reordered because its details or pricing are unavailable."
                    errorMessage = message
                    _addEvents.trySend(CartAddEvent.Failure(message))
                    return@withLock
                }

                val qty = item.effectiveQuantity.coerceAtLeast(1)
                val prodId = item.effectiveProductId
                val vertId = item.effectiveVerticalId
                val pharmacyAttr = if (item.effectiveRxRequired) PharmacyCartAttributes(prescriptionRequired = true, coldChain = false) else null
                val cartItem = CartItem(
                    productId = prodId,
                    sku = sku,
                    name = name,
                    unitPrice = price,
                    quantity = qty,
                    verticalId = vertId,
                    merchantId = item.merchantId,
                    mrp = price,
                    pharmacyAttributes = pharmacyAttr
                )

                val previousCartItems = cartItems
                val previousTotals = totals
                val existingIndex = cartItems.indexOfFirst { it.sku == cartItem.sku }
                val updatedItem = if (existingIndex >= 0) {
                    val existing = cartItems[existingIndex]
                    existing.copy(quantity = existing.quantity + cartItem.quantity)
                } else {
                    cartItem
                }
                cartItems = if (existingIndex >= 0) {
                    cartItems.toMutableList().also { it[existingIndex] = updatedItem }
                } else {
                    cartItems + cartItem
                }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.addCartItem(targetCustomerId, cartItem)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            val backendItems = sanitizeCartItems(updated.data.items ?: cartItems)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            _addEvents.trySend(CartAddEvent.Success(sku, name))
                            if (cartDao != null) {
                                try {
                                    cartDao.clearCart(targetCustomerId)
                                    cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, targetCustomerId) })
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            cartItems = previousCartItems
                            totals = previousTotals
                            val errMsg = updated.error.message ?: "Could not reorder item. Please check your network connection."
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                        }
                    }
                } else {
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(updatedItem, LOCAL_OFFLINE_CART_KEY))
                        } catch (_: Exception) {}
                    }
                    _addEvents.trySend(CartAddEvent.Success(sku, name))
                }
                syncCouponDiscount()
            }
        }
    }

    fun reorderAll(items: List<OrderItem>) {
        if (items.isEmpty()) return
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                val failedSkus = mutableListOf<String>()
                items.forEach { item ->
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    val sku = item.effectiveSku
                    val name = item.effectiveName
                    val price = item.effectivePrice
                    if (sku.isBlank() || sku.equals("SKU_ITEM", ignoreCase = true) || price <= BigDecimal.ZERO) {
                        failedSkus += if (sku.isNotBlank()) sku else "UNKNOWN_SKU"
                        return@forEach
                    }

                    val qty = item.effectiveQuantity.coerceAtLeast(1)
                    val prodId = item.effectiveProductId
                    val vertId = item.effectiveVerticalId
                    val pharmacyAttr = if (item.effectiveRxRequired) PharmacyCartAttributes(prescriptionRequired = true, coldChain = false) else null

                    val cartItem = CartItem(
                        productId = prodId,
                        sku = sku,
                        name = name,
                        unitPrice = price,
                        quantity = qty,
                        verticalId = vertId,
                        merchantId = item.merchantId,
                        mrp = price,
                        pharmacyAttributes = pharmacyAttr
                    )

                    if (targetCustomerId.isNotBlank()) {
                        try {
                            when (val updated = repository.addCartItem(targetCustomerId, cartItem)) {
                                is ApiResult.Success -> {
                                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                                    val backendItems = sanitizeCartItems(updated.data.items ?: (cartItems + cartItem))
                                    cartItems = backendItems
                                    totals = updated.data
                                    errorMessage = null
                                }
                                is ApiResult.Failure -> {
                                    failedSkus += sku
                                }
                            }
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            failedSkus += sku
                        }
                    } else {
                        val existingIndex = cartItems.indexOfFirst { it.sku == cartItem.sku }
                        val updatedItem = if (existingIndex >= 0) {
                            cartItems.toMutableList().also { it[existingIndex] = it[existingIndex].copy(quantity = it[existingIndex].quantity + cartItem.quantity) }
                        } else {
                            cartItems + cartItem
                        }
                        cartItems = updatedItem
                        if (cartDao != null) {
                            try {
                                val savedItem = cartItems.find { it.sku == cartItem.sku } ?: cartItem
                                cartDao.insertOrUpdate(CartItemEntity.fromCartItem(savedItem, LOCAL_OFFLINE_CART_KEY))
                            } catch (_: Exception) {}
                        }
                    }
                }
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                if (cartDao != null && targetCustomerId.isNotBlank()) {
                    try {
                        cartDao.clearCart(targetCustomerId)
                        cartDao.insertAll(cartItems.map { CartItemEntity.fromCartItem(it, targetCustomerId) })
                    } catch (_: Exception) {}
                }
                if (failedSkus.isNotEmpty()) {
                    val message = "Could not reorder ${failedSkus.size} item(s). Please refresh your cart."
                    errorMessage = message
                    _addEvents.trySend(CartAddEvent.Failure(message))
                } else {
                    _addEvents.trySend(CartAddEvent.Success("REORDER_ALL", "All order items added to cart"))
                }
                syncCouponDiscount()
            }
        }
    }

    fun updateQuantity(sku: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(sku)
            return
        }
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                val previousCartItems = cartItems
                val previousTotals = totals
                // Optimistic instant update
                cartItems = cartItems.map { if (it.sku == sku) it.copy(quantity = quantity) else it }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.updateCartQuantity(targetCustomerId, sku, quantity)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            val backendItems = sanitizeCartItems(updated.data.items ?: cartItems)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            if (cartDao != null) {
                                try {
                                    cartDao.clearCart(targetCustomerId)
                                    cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, targetCustomerId) })
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            cartItems = previousCartItems
                            totals = previousTotals
                            errorMessage = updated.error.message ?: "Could not update item quantity."
                        }
                    }
                } else {
                    if (cartDao != null) {
                        val targetItem = cartItems.find { it.sku == sku }
                        if (targetItem != null) {
                            try {
                                cartDao.insertOrUpdate(CartItemEntity.fromCartItem(targetItem, LOCAL_OFFLINE_CART_KEY))
                            } catch (_: Exception) {}
                        }
                    }
                }
                syncCouponDiscount()
            }
        }
    }

    fun removeItem(sku: String) {
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                val previousCartItems = cartItems
                val previousTotals = totals
                // Optimistic instant update
                cartItems = cartItems.filter { it.sku != sku }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.removeCartItem(targetCustomerId, sku)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            val backendItems = sanitizeCartItems(updated.data.items ?: cartItems)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            if (cartDao != null) {
                                try {
                                    cartDao.deleteBySku(sku, targetCustomerId)
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            cartItems = previousCartItems
                            totals = previousTotals
                            errorMessage = updated.error.message ?: "Could not remove item from cart."
                        }
                    }
                } else {
                    if (cartDao != null) {
                        try {
                            cartDao.deleteBySku(sku, LOCAL_OFFLINE_CART_KEY)
                        } catch (_: Exception) {}
                    }
                }
                syncCouponDiscount()
            }
        }
    }

    fun moveToSaveForLater(sku: String) {
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                val item = cartItems.firstOrNull { it.sku == sku } ?: return@withLock

                if (targetCustomerId.isNotBlank()) {
                    val result = repository.removeCartItem(targetCustomerId, sku)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (result) {
                        is ApiResult.Failure -> {
                            errorMessage = result.error.message
                            return@withLock
                        }
                        is ApiResult.Success -> {
                            val backendItems = sanitizeCartItems(result.data.items ?: emptyList())
                            cartItems = backendItems
                            totals = result.data
                            if (cartDao != null) {
                                try { cartDao.deleteBySku(sku, targetCustomerId) } catch (_: Exception) {}
                            }
                        }
                    }
                } else {
                    try {
                        cartDao?.deleteBySku(sku, LOCAL_OFFLINE_CART_KEY)
                        cartItems = cartItems.filterNot { it.sku == sku }
                    } catch (e: Exception) {
                        errorMessage = "Could not save this item for later."
                        return@withLock
                    }
                }

                saveForLaterItems = saveForLaterItems.filterNot { it.sku == sku } + item
                syncCouponDiscount()
            }
        }
    }

    fun moveBackToCart(sku: String) {
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                val targetItem = saveForLaterItems.find { it.sku == sku } ?: return@withLock
                val prodSellingPrice = targetItem.unitPrice.toDouble().coerceAtLeast(1.0)
                val prodPrice = (targetItem.mrp?.toDouble() ?: prodSellingPrice).coerceAtLeast(prodSellingPrice)
                val prod = CommerceProduct(
                    id = targetItem.productId,
                    sku = targetItem.sku,
                    name = targetItem.name,
                    price = prodPrice,
                    discountedPrice = prodSellingPrice,
                    sellingPrice = prodSellingPrice,
                    mrp = prodPrice,
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

                val previousCartItems = cartItems
                val previousTotals = totals
                val existingIndex = cartItems.indexOfFirst { it.sku == targetItem.sku }
                val updatedItem = if (existingIndex >= 0) {
                    val existing = cartItems[existingIndex]
                    existing.copy(quantity = existing.quantity + targetItem.quantity)
                } else {
                    targetItem
                }
                cartItems = if (existingIndex >= 0) {
                    cartItems.toMutableList().also { it[existingIndex] = updatedItem }
                } else {
                    cartItems + targetItem
                }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.addCartItem(targetCustomerId, targetItem)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            val backendItems = sanitizeCartItems(updated.data.items ?: cartItems)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            saveForLaterItems = saveForLaterItems.filterNot { it.sku == sku }
                            _addEvents.trySend(CartAddEvent.Success(prod.sku, prod.name))
                            if (cartDao != null) {
                                try {
                                    cartDao.clearCart(targetCustomerId)
                                    cartDao.insertAll(backendItems.map { CartItemEntity.fromCartItem(it, targetCustomerId) })
                                } catch (_: Exception) {}
                            }
                        }
                        is ApiResult.Failure -> {
                            cartItems = previousCartItems
                            totals = previousTotals
                            val errMsg = updated.error.message ?: "Could not move item back to cart."
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                            return@withLock
                        }
                    }
                } else {
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(updatedItem, LOCAL_OFFLINE_CART_KEY))
                        } catch (_: Exception) {}
                    }
                    saveForLaterItems = saveForLaterItems.filterNot { it.sku == sku }
                    _addEvents.trySend(CartAddEvent.Success(prod.sku, prod.name))
                }
                syncCouponDiscount()
            }
        }
    }

    private fun calculateDiscount(campaign: CouponCampaign, subtotal: BigDecimal): BigDecimal =
        when (campaign.discountType) {
            CouponDiscountType.PERCENTAGE -> {
                val value = subtotal
                    .multiply(BigDecimal(campaign.discountPercent))
                    .divide(BigDecimal(100), 2, RoundingMode.HALF_UP)
                campaign.maxDiscount?.let(value::min) ?: value
            }
            CouponDiscountType.FLAT -> campaign.flatDiscount.min(subtotal)
            CouponDiscountType.FREE_SHIPPING -> effectiveExpressFee
        }.coerceAtLeast(BigDecimal.ZERO).min(subtotal)

    fun syncCouponDiscount() {
        val code = appliedCouponCode ?: return
        val campaign = availableCampaigns.firstOrNull {
            it.code.equals(code, ignoreCase = true) &&
                it.status.equals("ACTIVE", ignoreCase = true)
        }

        if (campaign == null) {
            appliedCouponCode = null
            couponDiscountAmount = BigDecimal.ZERO
            return
        }

        if (effectiveSubtotal < campaign.minOrderValue) {
            appliedCouponCode = null
            couponDiscountAmount = BigDecimal.ZERO
            couponFeedbackMessage = "Coupon '${campaign.code}' was removed because order value dropped below ${MoneyFormatter.format(campaign.minOrderValue)}"
            isCouponError = true
            return
        }

        couponDiscountAmount = calculateDiscount(campaign, effectiveSubtotal)
    }

    fun applyCouponWithFeedback(code: String): CouponApplyResult {
        val cleanCode = code.trim().uppercase()
        if (cleanCode.isBlank()) {
            val res = CouponApplyResult(false, "Please enter a coupon code")
            couponFeedbackMessage = res.message
            isCouponError = true
            return res
        }
        if (cleanCode == "FREESHIP") {
            val res = CouponApplyResult(false, "Free delivery is an automatic policy above ${MoneyFormatter.format(freeDeliveryThreshold)}, not a coupon")
            couponFeedbackMessage = res.message
            isCouponError = true
            return res
        }

        val campaign = availableCampaigns.firstOrNull {
            it.code.equals(cleanCode, ignoreCase = true) &&
                it.status.equals("ACTIVE", ignoreCase = true)
        }
        if (campaign == null) {
            val pausedOrInactive = availableCampaigns.firstOrNull { it.code.equals(cleanCode, ignoreCase = true) }
            val res = if (pausedOrInactive != null) {
                CouponApplyResult(false, "Coupon '${pausedOrInactive.code}' is currently paused or inactive")
            } else {
                CouponApplyResult(false, "Invalid coupon code '$cleanCode'")
            }
            couponFeedbackMessage = res.message
            isCouponError = true
            return res
        }

        if (effectiveSubtotal < campaign.minOrderValue) {
            val remaining = campaign.minOrderValue.subtract(effectiveSubtotal)
            val res = CouponApplyResult(false, "Add ${MoneyFormatter.format(remaining)} more to apply ${campaign.code}")
            couponFeedbackMessage = res.message
            isCouponError = true
            return res
        }

        val discount = calculateDiscount(campaign, effectiveSubtotal)
        appliedCouponCode = campaign.code
        couponDiscountAmount = discount
        val res = CouponApplyResult(
            true,
            "Coupon '${campaign.code}' applied! You saved ${MoneyFormatter.format(discount)}",
            discount
        )
        couponFeedbackMessage = res.message
        isCouponError = false
        return res
    }

    fun applyCoupon(code: String): Boolean {
        val res = applyCouponWithFeedback(code)
        return res.success
    }

    fun removeCoupon() {
        appliedCouponCode = null
        couponDiscountAmount = BigDecimal.ZERO
        couponFeedbackMessage = null
        isCouponError = false
    }

    fun clear() {
        val activeCustId = if (customerId.isNotBlank()) customerId else LOCAL_OFFLINE_CART_KEY
        cartItems = emptyList()
        totals = null
        appliedCouponCode = null
        couponDiscountAmount = BigDecimal.ZERO
        couponFeedbackMessage = null
        isCouponError = false
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
