package com.commerceos.android.viewmodel

interface CartMutationGate {
    val isLockedForCheckout: Boolean
    fun lockMutations()
    fun unlockMutations()
    suspend fun awaitMutationsIdle()
}
