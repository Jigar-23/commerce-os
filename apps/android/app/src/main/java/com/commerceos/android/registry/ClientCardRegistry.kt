package com.commerceos.android.registry

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.CommerceDomain
import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchResult

/**
 * Universal Card Variant Enums for White-Label Presentation Resolution.
 */
enum class CardVariant {
    GENERIC_PRODUCT,
    FASHION_PRODUCT,
    PHARMACY_PRODUCT,
    ELECTRONICS_PRODUCT,
    GROCERY_PRODUCT,
    RESTAURANT_CARD,
    DISH_CARD,
    SERVICE_CARD,
    STORE_CARD,
    BRAND_CARD,
    CATEGORY_CARD,
    CAMPAIGN_CARD,
    OFFER_CARD,
    COLLECTION_CARD,
    FALLBACK_GENERIC_CARD
}

/**
 * Registry mapping CommerceEntity types and ClientConfiguration domains/verticals to appropriate Card Variants.
 */
object ClientCardRegistry {

    private val customEntityCardOverrides = mutableMapOf<String, CardVariant>()

    fun registerCardOverride(entityTypeId: String, variant: CardVariant) {
        customEntityCardOverrides[entityTypeId.lowercase()] = variant
    }

    fun clearOverrides() {
        customEntityCardOverrides.clear()
    }

    fun resolveCardVariant(entity: CommerceEntity, config: ClientConfiguration): CardVariant {
        val entityKey = entity::class.java.simpleName.lowercase()
        customEntityCardOverrides[entityKey]?.let { return it }

        return when (entity) {
            is CommerceEntity.ProductItem -> {
                val vertical = entity.vertical.lowercase()
                when {
                    vertical in listOf("fashion", "apparel", "clothing") || config.domain == CommerceDomain.FASHION ->
                        CardVariant.FASHION_PRODUCT
                    vertical in listOf("health", "pharmacy", "medicine") || config.domain == CommerceDomain.PHARMACY ->
                        CardVariant.PHARMACY_PRODUCT
                    vertical in listOf("electronics", "tech", "gadgets") || config.domain == CommerceDomain.ELECTRONICS ->
                        CardVariant.ELECTRONICS_PRODUCT
                    vertical in listOf("grocery", "supermarket", "fresh") ->
                        CardVariant.GROCERY_PRODUCT
                    else -> CardVariant.GENERIC_PRODUCT
                }
            }
            is CommerceEntity.RestaurantItem -> CardVariant.RESTAURANT_CARD
            is CommerceEntity.DishItem -> CardVariant.DISH_CARD
            is CommerceEntity.ServiceItem -> CardVariant.SERVICE_CARD
            is CommerceEntity.StoreItem -> CardVariant.STORE_CARD
            is CommerceEntity.Brand -> CardVariant.BRAND_CARD
            is CommerceEntity.CategoryItem -> CardVariant.CATEGORY_CARD
            is CommerceEntity.CollectionItem -> CardVariant.COLLECTION_CARD
            is CommerceEntity.CampaignItem -> CardVariant.CAMPAIGN_CARD
            is CommerceEntity.OfferItem -> CardVariant.OFFER_CARD
            is CommerceEntity.ProviderItem -> CardVariant.SERVICE_CARD
            is CommerceEntity.PrescriptionItem -> CardVariant.PHARMACY_PRODUCT
            is CommerceEntity.BookingAppointmentItem -> CardVariant.SERVICE_CARD
            is CommerceEntity.MembershipSubscriptionItem -> CardVariant.GENERIC_PRODUCT
            is CommerceEntity.Shortcut -> CardVariant.CATEGORY_CARD
            is CommerceEntity.CustomClientEntity -> com.commerceos.android.advanced.CardPluginRegistry.resolveCardPlugin(entity.typeName)
            else -> CardVariant.FALLBACK_GENERIC_CARD
        }
    }

    fun resolveSearchResultCardVariant(result: SearchResult, config: ClientConfiguration): CardVariant {
        config.searchConfig.resultCardMappings[result.entityType]?.let { return it }
        return when (result.entityType) {
            SearchEntityType.PRODUCT -> {
                val v = result.vertical.lowercase()
                when {
                    v in listOf("fashion", "apparel") || config.domain == CommerceDomain.FASHION -> CardVariant.FASHION_PRODUCT
                    v in listOf("health", "pharmacy") || config.domain == CommerceDomain.PHARMACY -> CardVariant.PHARMACY_PRODUCT
                    v in listOf("electronics", "tech") || config.domain == CommerceDomain.ELECTRONICS -> CardVariant.ELECTRONICS_PRODUCT
                    v in listOf("grocery", "supermarket") -> CardVariant.GROCERY_PRODUCT
                    else -> CardVariant.GENERIC_PRODUCT
                }
            }
            SearchEntityType.RESTAURANT -> CardVariant.RESTAURANT_CARD
            SearchEntityType.SERVICE -> CardVariant.SERVICE_CARD
            SearchEntityType.STORE -> CardVariant.STORE_CARD
            SearchEntityType.BRAND -> CardVariant.BRAND_CARD
            SearchEntityType.CATEGORY -> CardVariant.CATEGORY_CARD
            SearchEntityType.COLLECTION -> CardVariant.COLLECTION_CARD
            SearchEntityType.CAMPAIGN -> CardVariant.CAMPAIGN_CARD
            SearchEntityType.OFFER -> CardVariant.OFFER_CARD
        }
    }

    /** Safe fallback for unknown or unmapped card variants. */
    fun safeFallbackCardVariant(): CardVariant = CardVariant.FALLBACK_GENERIC_CARD
}
