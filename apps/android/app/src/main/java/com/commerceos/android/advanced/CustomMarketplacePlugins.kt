package com.commerceos.android.advanced

import com.commerceos.android.model.CommerceEntity.CustomClientEntity
import com.commerceos.android.registry.CardVariant

/** Custom client workflow extension registered by third-party developer. */
data class CustomClientWorkflow(
    val workflowId: String,
    val name: String,
    val handlerRoute: String,
    val isEnabled: Boolean = true
)

/** Custom marketplace model definition (e.g. B2B, Peer-to-Peer, Rental, Auction). */
enum class MarketplaceModelType {
    DIRECT_TO_CONSUMER,
    MULTI_VENDOR_MARKETPLACE,
    HYPERLOCAL_ON_DEMAND,
    B2B_WHOLESALE,
    RENTAL_SUBSCRIPTION,
    AUCTION_BIDDING
}

data class CustomMarketplaceModel(
    val modelId: String,
    val type: MarketplaceModelType,
    val name: String,
    val supportsEscrow: Boolean = true,
    val commissionPercent: Double = 5.0
)

/** Registry allowing dynamic registration of third-party custom card plugins. */
object CardPluginRegistry {
    private val plugins = mutableMapOf<String, CardVariant>()

    fun registerCardPlugin(typeName: String, variant: CardVariant) {
        plugins[typeName.lowercase()] = variant
    }

    fun resolveCardPlugin(typeName: String): CardVariant {
        return plugins[typeName.lowercase()] ?: CardVariant.FALLBACK_GENERIC_CARD
    }
}
