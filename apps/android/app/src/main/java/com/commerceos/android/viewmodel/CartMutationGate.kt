package com.commerceos.android.viewmodel

import com.commerceos.android.model.CartItem

interface CartMutationGate {
    val isLockedForCheckout: Boolean
    fun lockMutations()
    fun unlockMutations()
    suspend fun awaitMutationsIdle(): List<CartItem>
}
