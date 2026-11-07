package com.commerceos.android.viewmodel

import com.commerceos.android.model.CartItem
import com.commerceos.android.model.CartResponse

interface CartMutationGate {
    val isLockedForCheckout: Boolean
    fun lockMutations()
    fun unlockMutations()
    suspend fun awaitMutationsIdle(): List<CartItem>
    suspend fun reconcileWithServer(
        cart: CartResponse,
        invalidatedCouponCode: String? = null,
        changeNotice: String? = null
    ) {}
}
