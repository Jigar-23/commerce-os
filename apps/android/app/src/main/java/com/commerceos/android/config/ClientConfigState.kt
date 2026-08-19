package com.commerceos.android.config

/**
 * Configuration Loading & Bootstrap State Machine.
 */
sealed interface ClientConfigState {
    data object Uninitialized : ClientConfigState
    data object Loading : ClientConfigState
    data class Success(
        val config: ClientConfiguration,
        val isFromCache: Boolean = false,
        val isLastKnownGood: Boolean = false
    ) : ClientConfigState

    data class Failure(
        val errorMessage: String,
        val fallbackConfig: ClientConfiguration = ClientConfiguration.DefaultGeneric
    ) : ClientConfigState
}
