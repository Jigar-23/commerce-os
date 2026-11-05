package com.commerceos.android.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.commerceos.android.data.local.CartDao
import com.commerceos.android.data.local.CartItemEntity
import com.commerceos.android.data.local.LocalSaveForLaterStore
import com.commerceos.android.model.*
import com.commerceos.android.network.ApiResult
import com.commerceos.android.network.AppError
import com.commerceos.android.repository.AppRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
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
) : ViewModel(), CartMutationGate {

    var customerId by mutableStateOf("")
        private set

    private var customerGeneration = 0L
    private var cartLoadJob: Job? = null
    private var accountSyncJob: Job? = null
    private var reorderAllJob: Job? = null
    private var sanitizationCleanupJob: Job? = null
    private var isInitialized = false
    private val moveBackKeys = ConcurrentHashMap<String, String>()
    private val pendingAddKeys = ConcurrentHashMap<String, String>()
    private val pendingReorderKeys = ConcurrentHashMap<String, String>()
    private val reorderBatchKeys = ConcurrentHashMap<String, String>()
    private val reorderBatchSuccessfulSkus = ConcurrentHashMap<String, MutableSet<String>>()
    val uncertainMoveSkus = ConcurrentHashMap.newKeySet<String>()

    override var isLockedForCheckout by mutableStateOf(false)
        private set

    override fun lockMutations() {
        isLockedForCheckout = true
    }

    override fun unlockMutations() {
        isLockedForCheckout = false
    }

    override suspend fun awaitMutationsIdle(): List<CartItem> {
        // Join all pending mutation and loading jobs OUTSIDE the mutex to prevent deadlock
        while (true) {
            val syncJob = accountSyncJob
            val reorderJob = reorderAllJob
            val cleanupJob = sanitizationCleanupJob
            val loadJob = cartLoadJob
            val activeJobs = listOfNotNull(syncJob, reorderJob, cleanupJob, loadJob).filter { it.isActive }
            if (activeJobs.isEmpty()) break
            activeJobs.forEach { it.join() }
        }

        if (uncertainMoveSkus.isNotEmpty()) {
            val targetCustomerId = customerId.trim()
            if (targetCustomerId.isNotBlank()) {
                when (val check = repository.fetchBackendCart(targetCustomerId)) {
                    is ApiResult.Success -> {
                        cartMutex.withLock {
                            val backendItems = sanitizeCartItems(check.data.items ?: emptyList())
                            cartItems = backendItems
                            totals = check.data
                            uncertainMoveSkus.toList().forEach { sku ->
                                if (backendItems.none { it.sku == sku }) {
                                    uncertainMoveSkus.remove(sku)
                                    appContext?.let { ctx ->
                                        LocalSaveForLaterStore.removeUncertainMoveSku(ctx, targetCustomerId, sku)
                                    }
                                } else {
                                    val previousSaved = saveForLaterItems.filterNot { it.sku == sku }
                                    val persisted = appContext?.let { ctx ->
                                        LocalSaveForLaterStore.saveItems(ctx, targetCustomerId, previousSaved)
                                    } ?: true
                                    if (persisted) {
                                        uncertainMoveSkus.remove(sku)
                                        appContext?.let { ctx ->
                                            LocalSaveForLaterStore.removeUncertainMoveSku(ctx, targetCustomerId, sku)
                                        }
                                        saveForLaterItems = previousSaved
                                    }
                                }
                            }
                        }
                    }
                    is ApiResult.Failure -> {
                        throw IllegalStateException("Cannot checkout: cart contains items in an uncertain state (${uncertainMoveSkus.joinToString()}). Please refresh cart.")
                    }
                }
            }
        }
        if (uncertainMoveSkus.isNotEmpty()) {
            throw IllegalStateException("Cannot checkout: cart contains items in an uncertain state (${uncertainMoveSkus.joinToString()}). Please refresh cart.")
        }

        // Capture settled cart state under mutex
        return cartMutex.withLock {
            cartItems.toList()
        }
    }

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
            val price = i.mrp.takeIf { it > BigDecimal.ZERO } ?: i.unitPrice
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

    private fun mergeCartItem(items: List<CartItem>, incoming: CartItem): List<CartItem> {
        val index = items.indexOfFirst { it.sku == incoming.sku }
        if (index < 0) return items + incoming

        return items.toMutableList().also { merged ->
            val existing = merged[index]
            val quantity = (existing.quantity.toLong() + incoming.quantity)
                .coerceAtMost(Int.MAX_VALUE.toLong())
                .toInt()
            merged[index] = existing.copy(quantity = quantity)
        }
    }

    private val LOCAL_OFFLINE_CART_KEY = "local_offline_cart"

    init {
        saveForLaterItems = appContext?.let { ctx ->
            com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(ctx, "")
        } ?: emptyList()
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
        if (isInitialized && this.customerId == targetId) {
            refreshDeliveryConfigAndPromotions()
            return
        }
        isInitialized = true

        customerGeneration++
        val generation = customerGeneration
        accountSyncJob?.cancel()
        cartLoadJob?.cancel()
        isLoading = false // Account switch: reset loading immediately

        this.customerId = targetId
        cartItems = emptyList()
        totals = null
        saveForLaterItems = appContext?.let { ctx ->
            com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(ctx, targetId)
        } ?: emptyList()
        appliedCouponCode = null
        couponDiscountAmount = BigDecimal.ZERO
        couponFeedbackMessage = null
        isCouponError = false
        moveBackKeys.clear()
        pendingAddKeys.clear()
        pendingReorderKeys.clear()
        reorderBatchKeys.clear()
        reorderBatchSuccessfulSkus.clear()
        uncertainMoveSkus.clear()
        appContext?.let { ctx ->
            uncertainMoveSkus.addAll(com.commerceos.android.data.local.LocalSaveForLaterStore.getUncertainMoveSkus(ctx, targetId))
        }
        errorMessage = null

        refreshDeliveryConfigAndPromotions()

        if (targetId.isBlank()) {
            loadFromLocalStorage(LOCAL_OFFLINE_CART_KEY)
            return
        }

        accountSyncJob = viewModelScope.launch {
            cartMutex.withLock {
                if (generation != customerGeneration || customerId.trim() != targetId) return@withLock
                if (cartDao != null) {
                    try {
                        val guestItems = cartDao.getCartItems(LOCAL_OFFLINE_CART_KEY)
                        if (guestItems.isNotEmpty()) {
                            // 1. Fetch current backend cart; ABORT and retain guest rows if fetch fails
                            val serverCartResult = repository.fetchBackendCart(targetId)
                            if (serverCartResult !is ApiResult.Success) {
                                if (generation == customerGeneration && customerId.trim() == targetId) {
                                    errorMessage = "Could not sync offline cart. Will retry when connected."
                                }
                                return@withLock
                            }

                            for (guestItem in guestItems) {
                                if (generation != customerGeneration || customerId.trim() != targetId) return@withLock
                                // Stable idempotency key for additive merge
                                val syncKey = "guest_sync_${targetId}_${guestItem.sku}_${guestItem.quantity}_${guestItem.updatedAt}"
                                val addRes = repository.addCartItem(targetId, guestItem.toCartItem(), idempotencyKey = syncKey)
                                if (addRes is ApiResult.Success) {
                                    val deleted = deleteLocalCartItem(LOCAL_OFFLINE_CART_KEY, guestItem.sku)
                                    if (!deleted && generation == customerGeneration) {
                                        errorMessage = "Could not update offline cart cache."
                                    }
                                } else {
                                    errorMessage = "Some offline guest cart items could not be synced to your account."
                                }
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
    }

    private fun isValidCartItem(item: CartItem): Boolean =
        item.quantity > 0 &&
            item.unitPrice > BigDecimal.ZERO &&
            item.sku.isNotBlank() &&
            !item.sku.equals("para", ignoreCase = true) &&
            item.name.isNotBlank() &&
            !item.name.equals("Medicine Item", ignoreCase = true)

    private suspend fun syncLocalCartCache(
        targetCustId: String,
        items: List<CartItem>,
        retries: Int = 1
    ): Boolean {
        val dao = cartDao ?: return true
        if (targetCustId.isBlank()) return true
        var attempt = 0
        while (attempt <= retries) {
            try {
                dao.replaceCart(targetCustId, items.map { CartItemEntity.fromCartItem(it, targetCustId) })
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                attempt++
                if (attempt > retries) return false
            }
        }
        return false
    }

    private suspend fun deleteLocalCartItem(
        targetCustId: String,
        sku: String,
        retries: Int = 1
    ): Boolean {
        val dao = cartDao ?: return true
        if (targetCustId.isBlank()) return true
        var attempt = 0
        while (attempt <= retries) {
            try {
                dao.deleteBySku(sku, targetCustId)
                return true
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                attempt++
                if (attempt > retries) return false
            }
        }
        return false
    }

    private fun sanitizeCartItems(rawItems: List<CartItem>): List<CartItem> {
        val valid = rawItems.filter(::isValidCartItem)
        val invalid = rawItems.filterNot(::isValidCartItem)
        val activeCustId = customerId.trim()
        val generation = customerGeneration

        if (invalid.isNotEmpty()) {
            val previousJob = sanitizationCleanupJob
            sanitizationCleanupJob = viewModelScope.launch {
                previousJob?.join()
                cartMutex.withLock {
                    for (item in invalid) {
                        if (generation != customerGeneration || customerId.trim() != activeCustId) return@withLock
                        val sku = item.sku.trim()
                        if (sku.isBlank()) continue

                        var serverDeleted = false
                        if (activeCustId.isNotBlank()) {
                            try {
                                when (val result = repository.removeCartItem(activeCustId, sku)) {
                                    is ApiResult.Success -> serverDeleted = true
                                    is ApiResult.Failure -> {
                                        val retry = repository.removeCartItem(activeCustId, sku)
                                        serverDeleted = (retry is ApiResult.Success)
                                        if (!serverDeleted && generation == customerGeneration) {
                                            errorMessage = "Failed to prune invalid cart item: ${result.error.message}"
                                        }
                                    }
                                }
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (generation == customerGeneration) {
                                    errorMessage = "Could not reach server to prune invalid cart item."
                                }
                            }
                        } else {
                            serverDeleted = true
                        }

                        if (serverDeleted && cartDao != null) {
                            try {
                                if (activeCustId.isNotBlank()) cartDao.deleteBySku(sku, activeCustId)
                                cartDao.deleteBySku(sku, LOCAL_OFFLINE_CART_KEY)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                if (generation == customerGeneration) {
                                    errorMessage = "Could not update offline cart cache."
                                    if (activeCustId.isNotBlank()) syncLocalCartCache(activeCustId, valid)
                                }
                            }
                        }
                    }
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
                        appContext?.let { ctx ->
                            uncertainMoveSkus.addAll(LocalSaveForLaterStore.getUncertainMoveSkus(ctx, activeCustId))
                        }
                        if (uncertainMoveSkus.isNotEmpty()) {
                            uncertainMoveSkus.toList().forEach { uncertainSku ->
                                if (items.none { it.sku == uncertainSku }) {
                                    uncertainMoveSkus.remove(uncertainSku)
                                    appContext?.let { ctx ->
                                        LocalSaveForLaterStore.removeUncertainMoveSku(ctx, activeCustId, uncertainSku)
                                    }
                                } else {
                                    val previousSaved = saveForLaterItems.filterNot { it.sku == uncertainSku }
                                    val persisted = appContext?.let { ctx ->
                                        LocalSaveForLaterStore.saveItems(ctx, activeCustId, previousSaved)
                                    } ?: true
                                    if (persisted) {
                                        uncertainMoveSkus.remove(uncertainSku)
                                        appContext?.let { ctx ->
                                            LocalSaveForLaterStore.removeUncertainMoveSku(ctx, activeCustId, uncertainSku)
                                        }
                                        saveForLaterItems = previousSaved
                                    }
                                }
                            }
                        }
                        cartItems = items
                        totals = result.data
                        errorMessage = null
                        val cached = syncLocalCartCache(activeCustId, items)
                        if (!cached && generation == customerGeneration) {
                            errorMessage = "Could not update offline cart cache."
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

    fun addItem(product: CommerceProduct, operationKey: String? = null) {
        if (isLockedForCheckout) {
            val message = "Cart cannot be modified while checkout is in progress."
            errorMessage = message
            _addEvents.trySend(CartAddEvent.Failure(message))
            return
        }
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (isLockedForCheckout || generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock

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

                val opMapKey = "${targetCustomerId}:${item.sku}"
                val existingPendingKey = operationKey
                    ?: pendingAddKeys[opMapKey]
                    ?: appContext?.let { LocalSaveForLaterStore.getPendingAddKey(it, targetCustomerId, item.sku) }

                val opKey = existingPendingKey ?: "add_${targetCustomerId}_${item.sku}_${UUID.randomUUID()}".also { newKey ->
                    pendingAddKeys[opMapKey] = newKey
                    appContext?.let { ctx ->
                        LocalSaveForLaterStore.setPendingAddKey(ctx, targetCustomerId, item.sku, newKey)
                    }
                }
                if (existingPendingKey != null) {
                    pendingAddKeys[opMapKey] = existingPendingKey
                }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.addCartItem(targetCustomerId, item, opKey)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            pendingAddKeys.remove(opMapKey)
                            appContext?.let { ctx ->
                                LocalSaveForLaterStore.clearPendingAddKey(ctx, targetCustomerId, item.sku)
                            }
                            val backendItems = sanitizeCartItems(updated.data.items ?: cartItems)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            _addEvents.trySend(CartAddEvent.Success(product.sku, product.name))
                            val cached = syncLocalCartCache(targetCustomerId, backendItems)
                            if (!cached && generation == customerGeneration) {
                                errorMessage = "Could not update offline cart cache."
                            }
                        }
                        is ApiResult.Failure -> {
                            // Rollback optimistic update on failure to preserve authoritative state
                            cartItems = previousCartItems
                            totals = previousTotals
                            val errMsg = updated.error.message.ifBlank { "Could not add item to cart. Please check your network connection." }
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                        }
                    }
                } else {
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(updatedItem, LOCAL_OFFLINE_CART_KEY))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            if (generation == customerGeneration && customerId.trim() == targetCustomerId) {
                                cartItems = previousCartItems
                                totals = previousTotals
                                val message = "Could not save the offline cart."
                                errorMessage = message
                                _addEvents.trySend(CartAddEvent.Failure(message))
                            }
                            return@withLock
                        }
                    }
                    _addEvents.trySend(CartAddEvent.Success(product.sku, product.name))
                }
                syncCouponDiscount()
            }
        }
    }

    fun addReorderItem(item: OrderItem, operationKey: String? = null) {
        if (isLockedForCheckout) {
            val message = "Cart cannot be modified while checkout is in progress."
            errorMessage = message
            _addEvents.trySend(CartAddEvent.Failure(message))
            return
        }
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (isLockedForCheckout || generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock

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

                val opMapKey = "${targetCustomerId}:${sku}"
                val existingPendingKey = operationKey
                    ?: pendingReorderKeys[opMapKey]
                    ?: appContext?.let { LocalSaveForLaterStore.getPendingReorderItemKey(it, targetCustomerId, sku) }

                val opKey = existingPendingKey ?: "reorder_item_${targetCustomerId}_${sku}_${UUID.randomUUID()}".also { newKey ->
                    pendingReorderKeys[opMapKey] = newKey
                    appContext?.let { ctx ->
                        LocalSaveForLaterStore.setPendingReorderItemKey(ctx, targetCustomerId, sku, newKey)
                    }
                }
                if (existingPendingKey != null) {
                    pendingReorderKeys[opMapKey] = existingPendingKey
                }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.addCartItem(targetCustomerId, cartItem, opKey)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            pendingReorderKeys.remove(opMapKey)
                            appContext?.let { ctx ->
                                LocalSaveForLaterStore.clearPendingReorderItemKey(ctx, targetCustomerId, sku)
                            }
                            val backendItems = sanitizeCartItems(updated.data.items ?: cartItems)
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            _addEvents.trySend(CartAddEvent.Success(sku, name))
                            val cached = syncLocalCartCache(targetCustomerId, backendItems)
                            if (!cached && generation == customerGeneration) {
                                errorMessage = "Could not update offline cart cache."
                            }
                        }
                        is ApiResult.Failure -> {
                            cartItems = previousCartItems
                            totals = previousTotals
                            val errMsg = updated.error.message.ifBlank { "Could not reorder item. Please check your network connection." }
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                        }
                    }
                } else {
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(updatedItem, LOCAL_OFFLINE_CART_KEY))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            if (generation == customerGeneration && customerId.trim() == targetCustomerId) {
                                cartItems = previousCartItems
                                totals = previousTotals
                                val message = "Could not save the offline cart."
                                errorMessage = message
                                _addEvents.trySend(CartAddEvent.Failure(message))
                            }
                            return@withLock
                        }
                    }
                    _addEvents.trySend(CartAddEvent.Success(sku, name))
                }
                syncCouponDiscount()
            }
        }
    }

    fun reorderAll(items: List<OrderItem>) {
        if (items.isEmpty()) return
        if (isLockedForCheckout) {
            val message = "Cart cannot be modified while checkout is in progress."
            errorMessage = message
            _addEvents.trySend(CartAddEvent.Failure(message))
            return
        }
        if (reorderAllJob?.isActive == true) return
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        reorderAllJob = viewModelScope.launch {
            try {
                cartMutex.withLock {
                    if (isLockedForCheckout || generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    val failedSkus = mutableListOf<String>()
                    val batchSignature = items.map { "${it.effectiveSku}:${it.effectiveQuantity}" }.sorted().joinToString(";")
                    val batchKey = "${targetCustomerId}_${batchSignature}"

                    val persistedBatch = appContext?.let { LocalSaveForLaterStore.getPendingReorderBatch(it, targetCustomerId, batchSignature) }
                    val batchId = persistedBatch?.batchId
                        ?: reorderBatchKeys.computeIfAbsent(batchKey) {
                            UUID.randomUUID().toString()
                        }
                    val successfulSkus = reorderBatchSuccessfulSkus.computeIfAbsent(batchKey) {
                        ConcurrentHashMap.newKeySet<String>().apply {
                            if (persistedBatch != null) {
                                addAll(persistedBatch.successfulSkus)
                            }
                        }
                    }
                    if (persistedBatch == null) {
                        appContext?.let { ctx ->
                            LocalSaveForLaterStore.setPendingReorderBatch(
                                ctx,
                                targetCustomerId,
                                batchSignature,
                                LocalSaveForLaterStore.ReorderBatchRecord(batchId, successfulSkus)
                            )
                        }
                    }

                    items.forEachIndexed { index, item ->
                        if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                        val sku = item.effectiveSku
                        val name = item.effectiveName
                        val price = item.effectivePrice
                        if (sku.isBlank() || sku.equals("SKU_ITEM", ignoreCase = true) || price <= BigDecimal.ZERO) {
                            failedSkus += if (sku.isNotBlank()) sku else "UNKNOWN_SKU"
                            return@forEachIndexed
                        }

                        val itemKey = "${index}_${sku}"
                        if (successfulSkus.contains(itemKey)) {
                            // Already successfully committed for this reorder batch item; do not duplicate
                            return@forEachIndexed
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
                            val itemOpKey = "reorder_${targetCustomerId}_${batchId}_${index}_${sku}_${qty}"
                            try {
                                when (val updated = repository.addCartItem(targetCustomerId, cartItem, itemOpKey)) {
                                    is ApiResult.Success -> {
                                        if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                                        successfulSkus.add(itemKey)
                                        appContext?.let { ctx ->
                                            LocalSaveForLaterStore.setPendingReorderBatch(
                                                ctx,
                                                targetCustomerId,
                                                batchSignature,
                                                LocalSaveForLaterStore.ReorderBatchRecord(batchId, successfulSkus)
                                            )
                                        }
                                        val backendItems = sanitizeCartItems(updated.data.items ?: mergeCartItem(cartItems, cartItem))
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
                            val previousCartItems = cartItems
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
                                    successfulSkus.add(itemKey)
                                    appContext?.let { ctx ->
                                        LocalSaveForLaterStore.setPendingReorderBatch(
                                            ctx,
                                            targetCustomerId,
                                            batchSignature,
                                            LocalSaveForLaterStore.ReorderBatchRecord(batchId, successfulSkus)
                                        )
                                    }
                                } catch (e: CancellationException) {
                                    throw e
                                } catch (_: Exception) {
                                    cartItems = previousCartItems
                                    failedSkus += sku
                                }
                            } else {
                                successfulSkus.add(itemKey)
                                appContext?.let { ctx ->
                                    LocalSaveForLaterStore.setPendingReorderBatch(
                                        ctx,
                                        targetCustomerId,
                                        batchSignature,
                                        LocalSaveForLaterStore.ReorderBatchRecord(batchId, successfulSkus)
                                    )
                                }
                            }
                        }
                    }
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    if (targetCustomerId.isNotBlank()) {
                        val cached = syncLocalCartCache(targetCustomerId, cartItems)
                        if (!cached && generation == customerGeneration) {
                            errorMessage = "Could not update offline cart cache."
                        }
                    }
                    if (failedSkus.isNotEmpty()) {
                        val message = "Could not reorder ${failedSkus.size} item(s). Please refresh your cart."
                        errorMessage = message
                        _addEvents.trySend(CartAddEvent.Failure(message))
                    } else {
                        reorderBatchKeys.remove(batchKey)
                        reorderBatchSuccessfulSkus.remove(batchKey)
                        appContext?.let { ctx ->
                            LocalSaveForLaterStore.clearPendingReorderBatch(ctx, targetCustomerId, batchSignature)
                        }
                        _addEvents.trySend(CartAddEvent.Success("REORDER_ALL", "All order items added to cart"))
                    }
                    syncCouponDiscount()
                }
            } finally {
                reorderAllJob = null
            }
        }
    }

    fun updateQuantity(sku: String, quantity: Int) {
        if (quantity <= 0) {
            removeItem(sku)
            return
        }
        if (isLockedForCheckout) {
            errorMessage = "Cart cannot be modified while checkout is in progress."
            return
        }
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (isLockedForCheckout || generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
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
                            val cached = syncLocalCartCache(targetCustomerId, backendItems)
                            if (!cached && generation == customerGeneration) {
                                errorMessage = "Could not update offline cart cache."
                            }
                        }
                        is ApiResult.Failure -> {
                            cartItems = previousCartItems
                            totals = previousTotals
                            errorMessage = updated.error.message.ifBlank { "Could not update item quantity." }
                        }
                    }
                } else {
                    if (cartDao != null) {
                        val targetItem = cartItems.find { it.sku == sku }
                        if (targetItem != null) {
                            try {
                                cartDao.insertOrUpdate(CartItemEntity.fromCartItem(targetItem, LOCAL_OFFLINE_CART_KEY))
                            } catch (e: CancellationException) {
                                throw e
                            } catch (_: Exception) {
                                if (generation == customerGeneration && customerId.trim() == targetCustomerId) {
                                    cartItems = previousCartItems
                                    totals = previousTotals
                                    errorMessage = "Could not update the offline cart."
                                }
                                return@withLock
                            }
                        }
                    }
                }
                syncCouponDiscount()
            }
        }
    }

    fun removeItem(sku: String) {
        if (isLockedForCheckout) {
            errorMessage = "Cart cannot be modified while checkout is in progress."
            return
        }
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (isLockedForCheckout || generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
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
                            val deletedLocally = deleteLocalCartItem(targetCustomerId, sku)
                            if (!deletedLocally && generation == customerGeneration) {
                                errorMessage = "Could not update offline cart cache."
                                syncLocalCartCache(targetCustomerId, backendItems)
                            }
                        }
                        is ApiResult.Failure -> {
                            cartItems = previousCartItems
                            totals = previousTotals
                            errorMessage = updated.error.message.ifBlank { "Could not remove item from cart." }
                        }
                    }
                } else {
                    if (cartDao != null) {
                        try {
                            cartDao.deleteBySku(sku, LOCAL_OFFLINE_CART_KEY)
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            if (generation == customerGeneration && customerId.trim() == targetCustomerId) {
                                cartItems = previousCartItems
                                totals = previousTotals
                                errorMessage = "Could not remove item from the offline cart."
                            }
                            return@withLock
                        }
                    }
                }
                syncCouponDiscount()
            }
        }
    }

    private fun AppError.isAmbiguousMutationFailure(): Boolean = when (this) {
        is AppError.Network, is AppError.Unknown -> true
        is AppError.Server ->
            httpCode == 408 || httpCode == 425 || httpCode == 429 || httpCode in 500..599
        else -> false
    }

    fun moveToSaveForLater(sku: String) {
        if (isLockedForCheckout) {
            errorMessage = "Cart cannot be modified while checkout is in progress."
            return
        }
        if (uncertainMoveSkus.contains(sku)) {
            errorMessage = "Item move is pending resolution. Please refresh your cart."
            return
        }
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (isLockedForCheckout || generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                if (uncertainMoveSkus.contains(sku)) {
                    errorMessage = "Item move is pending resolution. Please refresh your cart."
                    return@withLock
                }
                val item = cartItems.firstOrNull { it.sku == sku } ?: return@withLock
                val previousSaved = saveForLaterItems
                val newSaved = previousSaved.filterNot { it.sku == sku } + item

                // Persist the saved item before removing it from the cart
                val persisted = appContext?.let { ctx ->
                    com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(ctx, targetCustomerId, newSaved)
                } ?: true

                if (!persisted) {
                    errorMessage = "Could not securely save this item for later."
                    return@withLock
                }

                if (targetCustomerId.isNotBlank()) {
                    val result = repository.removeCartItem(targetCustomerId, sku)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (result) {
                        is ApiResult.Failure -> {
                            // On ambiguous transport/network or gateway failure, reconcile against server cart before rolling back
                            val isAmbiguous = result.error.isAmbiguousMutationFailure()
                            val serverCommitted = if (isAmbiguous) {
                                when (val check = repository.fetchBackendCart(targetCustomerId)) {
                                    is ApiResult.Success -> {
                                        val items = check.data.items ?: emptyList()
                                        if (items.none { it.sku == sku }) {
                                            cartItems = sanitizeCartItems(items)
                                            totals = check.data
                                            true
                                        } else false
                                    }
                                    is ApiResult.Failure -> null
                                }
                            } else false

                            if (serverCommitted == true) {
                                uncertainMoveSkus.remove(sku)
                                appContext?.let { ctx ->
                                    LocalSaveForLaterStore.removeUncertainMoveSku(ctx, targetCustomerId, sku)
                                }
                                deleteLocalCartItem(targetCustomerId, sku)
                                saveForLaterItems = newSaved
                                errorMessage = null
                                syncCouponDiscount()
                                return@withLock
                            } else if (serverCommitted == null) {
                                uncertainMoveSkus.add(sku)
                                appContext?.let { ctx ->
                                    LocalSaveForLaterStore.addUncertainMoveSku(ctx, targetCustomerId, sku)
                                }
                                errorMessage = "Network timeout while moving item. Your item is preserved in Saved for Later, and cart checkout is held until state is resolved."
                                saveForLaterItems = newSaved
                                syncCouponDiscount()
                                return@withLock
                            }

                            uncertainMoveSkus.remove(sku)
                            appContext?.let { ctx ->
                                LocalSaveForLaterStore.removeUncertainMoveSku(ctx, targetCustomerId, sku)
                            }
                            // Rollback saved items store if backend removal failed
                            val rollbackPersisted = appContext?.let { ctx ->
                                com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(ctx, targetCustomerId, previousSaved)
                            } ?: true
                            if (!rollbackPersisted) {
                                errorMessage = "Failed to remove item from cart, and rolling back saved items failed. Please refresh your cart."
                                appContext?.let { ctx ->
                                    saveForLaterItems = com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(ctx, targetCustomerId)
                                }
                            } else {
                                errorMessage = result.error.message
                            }
                            return@withLock
                        }
                        is ApiResult.Success -> {
                            uncertainMoveSkus.remove(sku)
                            appContext?.let { ctx ->
                                LocalSaveForLaterStore.removeUncertainMoveSku(ctx, targetCustomerId, sku)
                            }
                            val backendItems = sanitizeCartItems(
                                result.data.items ?: cartItems.filterNot { it.sku == sku }
                            )
                            cartItems = backendItems
                            totals = result.data
                            val deletedLocally = deleteLocalCartItem(targetCustomerId, sku)
                            if (!deletedLocally && generation == customerGeneration) {
                                errorMessage = "Could not update offline cart cache."
                                syncLocalCartCache(targetCustomerId, backendItems)
                            }
                            if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                        }
                    }
                } else {
                    try {
                        cartDao?.deleteBySku(sku, LOCAL_OFFLINE_CART_KEY)
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        val rollbackPersisted = appContext?.let { ctx ->
                            com.commerceos.android.data.local.LocalSaveForLaterStore.saveItems(ctx, targetCustomerId, previousSaved)
                        } ?: true
                        errorMessage = if (!rollbackPersisted) {
                            "Failed to remove item from offline cart, and rolling back saved items failed."
                        } else {
                            "Could not save this item for later."
                        }
                        return@withLock
                    }
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    cartItems = cartItems.filterNot { it.sku == sku }
                }

                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                saveForLaterItems = newSaved
                syncCouponDiscount()
            }
        }
    }

    fun moveBackToCart(sku: String) {
        if (isLockedForCheckout) {
            val message = "Cart cannot be modified while checkout is in progress."
            errorMessage = message
            _addEvents.trySend(CartAddEvent.Failure(message))
            return
        }
        if (uncertainMoveSkus.contains(sku)) {
            val message = "Item move is pending resolution. Please refresh your cart."
            errorMessage = message
            _addEvents.trySend(CartAddEvent.Failure(message))
            return
        }
        val targetCustomerId = customerId.trim()
        val generation = customerGeneration
        viewModelScope.launch {
            cartMutex.withLock {
                if (isLockedForCheckout || generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                if (uncertainMoveSkus.contains(sku)) {
                    val message = "Item move is pending resolution. Please refresh your cart."
                    errorMessage = message
                    _addEvents.trySend(CartAddEvent.Failure(message))
                    return@withLock
                }
                val targetItem = saveForLaterItems.find { it.sku == sku } ?: return@withLock
                val previousSaved = saveForLaterItems
                val remainingSaved = previousSaved.filterNot { it.sku == sku }

                val prodSellingPrice = targetItem.unitPrice.toDouble().coerceAtLeast(1.0)
                val prodPrice = targetItem.mrp.toDouble().coerceAtLeast(prodSellingPrice)
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

                val moveMapKey = "${targetCustomerId}:${sku}"
                val existingPendingKey = appContext?.let { LocalSaveForLaterStore.getPendingMoveKey(it, targetCustomerId, sku) }
                    ?: moveBackKeys[moveMapKey]

                val moveKey: String
                if (existingPendingKey != null) {
                    moveKey = existingPendingKey
                } else {
                    val generatedKey = "move_back_${targetCustomerId}_${targetItem.sku}_${targetItem.quantity}_${UUID.randomUUID()}"
                    if (appContext != null) {
                        val stored = LocalSaveForLaterStore.setPendingMoveKey(appContext, targetCustomerId, sku, generatedKey)
                        if (!stored) {
                            val message = "Could not store durable idempotency key for moving item."
                            errorMessage = message
                            _addEvents.trySend(CartAddEvent.Failure(message))
                            return@withLock
                        }
                    }
                    moveBackKeys[moveMapKey] = generatedKey
                    moveKey = generatedKey
                }

                if (targetCustomerId.isNotBlank()) {
                    val updated = repository.addCartItem(targetCustomerId, targetItem, moveKey)
                    if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                    when (updated) {
                        is ApiResult.Success -> {
                            val persisted = appContext?.let { ctx ->
                                LocalSaveForLaterStore.saveItems(ctx, targetCustomerId, remainingSaved)
                            } ?: true
                            if (!persisted) {
                                errorMessage = "Could not update saved items storage."
                                return@withLock
                            }
                            val cleared = appContext?.let { ctx ->
                                LocalSaveForLaterStore.clearPendingMoveKey(ctx, targetCustomerId, sku)
                            } ?: true
                            if (!cleared) {
                                errorMessage = "Could not durably clear pending move operation key."
                                return@withLock
                            }
                            moveBackKeys.remove(moveMapKey)
                            val backendItems = sanitizeCartItems(updated.data.items ?: (cartItems + targetItem))
                            cartItems = backendItems
                            totals = updated.data
                            errorMessage = null
                            saveForLaterItems = remainingSaved
                            _addEvents.trySend(CartAddEvent.Success(prod.sku, prod.name))
                            val cached = syncLocalCartCache(targetCustomerId, backendItems)
                            if (!cached && generation == customerGeneration) {
                                errorMessage = "Could not update offline cart cache."
                            }
                            if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                        }
                        is ApiResult.Failure -> {
                            val isAmbiguous = updated.error.isAmbiguousMutationFailure()
                            if (isAmbiguous) {
                                // Reconcile against that operation's authoritative result by retrying with the same durable idempotency key
                                val reconciled = repository.addCartItem(targetCustomerId, targetItem, moveKey)
                                if (generation != customerGeneration || customerId.trim() != targetCustomerId) return@withLock
                                if (reconciled is ApiResult.Success) {
                                    val persisted = appContext?.let { ctx ->
                                        LocalSaveForLaterStore.saveItems(ctx, targetCustomerId, remainingSaved)
                                    } ?: true
                                    if (persisted) {
                                        val cleared = appContext?.let { ctx ->
                                            LocalSaveForLaterStore.clearPendingMoveKey(ctx, targetCustomerId, sku)
                                        } ?: true
                                        if (cleared) {
                                            moveBackKeys.remove(moveMapKey)
                                        } else {
                                            errorMessage = "Could not durably clear pending move operation key."
                                            return@withLock
                                        }
                                        saveForLaterItems = remainingSaved
                                    } else {
                                        errorMessage = "Could not update saved items storage."
                                        // Retain moveKey so that future reconciliation or retry can resolve it without duplicate add
                                        return@withLock
                                    }
                                    val backendItems = sanitizeCartItems(reconciled.data.items ?: (cartItems + targetItem))
                                    cartItems = backendItems
                                    totals = reconciled.data
                                    errorMessage = null
                                    _addEvents.trySend(CartAddEvent.Success(prod.sku, prod.name))
                                    syncLocalCartCache(targetCustomerId, backendItems)
                                    return@withLock
                                } else if (reconciled is ApiResult.Failure) {
                                    // Any failure on retry after an ambiguous first attempt leaves the outcome unresolved!
                                    // Retain the pending move key for future retry.
                                    val errMsg = reconciled.error.message.ifBlank { "Network timeout while moving item back to cart. Your item is preserved in Saved for Later." }
                                    errorMessage = errMsg
                                    _addEvents.trySend(CartAddEvent.Failure(errMsg))
                                    return@withLock
                                }
                            }

                            // Definitive first-attempt failure: clear pending key, item remains safely in saved items
                            val cleared = appContext?.let { ctx ->
                                LocalSaveForLaterStore.clearPendingMoveKey(ctx, targetCustomerId, sku)
                            } ?: true
                            if (cleared) {
                                moveBackKeys.remove(moveMapKey)
                            }
                            val errMsg = updated.error.message.ifBlank { "Could not move item back to cart." }
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                            return@withLock
                        }
                    }
                } else {
                    val existingIndex = cartItems.indexOfFirst { it.sku == targetItem.sku }
                    val updatedItem = if (existingIndex >= 0) {
                        val existing = cartItems[existingIndex]
                        existing.copy(quantity = existing.quantity + targetItem.quantity)
                    } else {
                        targetItem
                    }
                    val newCartItems = if (existingIndex >= 0) {
                        cartItems.toMutableList().also { it[existingIndex] = updatedItem }
                    } else {
                        cartItems + targetItem
                    }
                    if (cartDao != null) {
                        try {
                            cartDao.insertOrUpdate(CartItemEntity.fromCartItem(updatedItem, LOCAL_OFFLINE_CART_KEY))
                        } catch (e: CancellationException) {
                            throw e
                        } catch (_: Exception) {
                            val errMsg = "Could not restore item to the offline cart."
                            errorMessage = errMsg
                            _addEvents.trySend(CartAddEvent.Failure(errMsg))
                            return@withLock
                        }
                    }
                    val persisted = appContext?.let { ctx ->
                        LocalSaveForLaterStore.saveItems(ctx, targetCustomerId, remainingSaved)
                    } ?: true
                    if (!persisted) {
                        errorMessage = "Could not update saved items storage."
                        return@withLock
                    }
                    cartItems = newCartItems
                    saveForLaterItems = remainingSaved
                    errorMessage = null
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
        val activeCustId = customerId.ifBlank { LOCAL_OFFLINE_CART_KEY }
        customerGeneration++
        val clearGeneration = customerGeneration
        accountSyncJob?.cancel()
        cartLoadJob?.cancel()
        isLoading = false

        cartItems = emptyList()
        totals = null
        appliedCouponCode = null
        couponDiscountAmount = BigDecimal.ZERO
        couponFeedbackMessage = null
        isCouponError = false
        moveBackKeys.clear()
        pendingAddKeys.clear()
        pendingReorderKeys.clear()
        reorderBatchKeys.clear()
        reorderBatchSuccessfulSkus.clear()
        uncertainMoveSkus.clear()

        cartDao?.let { dao ->
            viewModelScope.launch {
                try {
                    dao.clearCart(activeCustId)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    if (clearGeneration == customerGeneration) {
                        errorMessage = "Could not clear the saved cart."
                    }
                }
            }
        }
    }

    fun reset() {
        reorderAllJob?.cancel()
        reorderAllJob = null
        sanitizationCleanupJob?.cancel()
        sanitizationCleanupJob = null
        isLockedForCheckout = false
        clear()
        customerId = ""
        isInitialized = false
        saveForLaterItems = appContext?.let { ctx ->
            com.commerceos.android.data.local.LocalSaveForLaterStore.getSavedItems(ctx, "")
        } ?: emptyList()
        errorMessage = null
        availableCampaigns = emptyList()
    }
}
