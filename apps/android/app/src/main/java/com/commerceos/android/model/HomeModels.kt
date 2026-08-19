package com.commerceos.android.model

/**
 * Universal Navigation Destinations across Commerce OS Home & Vertical Hubs.
 * The Home UI only ever emits a [HomeDestination]; the root resolves the real
 * screen. No screen leaks its internal domain into another's expectations.
 */
sealed interface HomeDestination {
    data class Product(val productId: String, val vertical: String = "general") : HomeDestination
    data class Category(val categoryId: String, val vertical: String = "general") : HomeDestination
    data class Brand(val brandId: String, val vertical: String = "general") : HomeDestination
    data class Store(val storeId: String) : HomeDestination
    data class Restaurant(val restaurantId: String) : HomeDestination
    data class Service(val serviceId: String) : HomeDestination
    data class Search(val query: UniversalSearchQuery) : HomeDestination
    data class Vertical(val verticalId: String) : HomeDestination
    data class Campaign(val campaignId: String) : HomeDestination
    data class Collection(val collectionId: String) : HomeDestination
    data class Offer(val offerId: String) : HomeDestination
    data object Orders : HomeDestination
    data object Prescriptions : HomeDestination
    data object Cart : HomeDestination
    data object Categories : HomeDestination
}

/**
 * Universal search query spanning intent, query string, location, vertical and customer context.
 */
data class UniversalSearchQuery(
    val text: String = "",
    val vertical: String? = null,
    val intent: String? = null,
    val locationAddressId: String? = null,
    val filters: Map<String, String> = emptyMap(),
    val sessionId: String = ""
)

/**
 * Generic Commerce Entity representation replacing hardcoded pharmacy-only models.
 * A [CommerceEntity] is domain-agnostic: screens render what they carry and the
 * root decides the destination from entity type — never from a medicine-specific
 * callback name.
 */
/** Entity capability metadata describing allowed actions. */
data class EntityCapabilityMetadata(
    val canAddToCart: Boolean = false,
    val canBook: Boolean = false,
    val canSchedule: Boolean = false,
    val rxRequired: Boolean = false,
    val requiresLocation: Boolean = false,
    val supportsDelivery: Boolean = false,
    val isExpressEligible: Boolean? = null
)

/**
 * Generic Commerce Entity representation replacing hardcoded models.
 * A [CommerceEntity] is domain-agnostic: screens render what they carry and the
 * root decides the destination from entity type — never from a vertical-specific callback.
 */
sealed interface CommerceEntity {
    val capabilities: EntityCapabilityMetadata
        get() = EntityCapabilityMetadata()

    data class ProductItem(
        val product: CommerceProduct,
        val vertical: String = product.verticalId ?: "general",
        val isFastFulfillmentAvailable: Boolean? = null
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(
                canAddToCart = true,
                rxRequired = product.medicineDetails?.prescriptionRequired == true,
                supportsDelivery = true,
                isExpressEligible = isFastFulfillmentAvailable
            )
    }

    data class CategoryItem(
        val group: CategoryGroup,
        val vertical: String = "general"
    ) : CommerceEntity

    data class Brand(
        val item: BrandItem,
        val vertical: String = "general"
    ) : CommerceEntity

    data class Shortcut(
        val id: String,
        val label: String,
        val iconType: String,
        val destination: HomeDestination
    ) : CommerceEntity

    data class RestaurantItem(
        val id: String,
        val name: String,
        val imageUrl: String?,
        val cuisine: String? = null,
        val rating: Double? = null,
        val deliveryEta: String? = null,
        val priceForTwo: String? = null,
        val offerText: String? = null
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(canBook = true, supportsDelivery = true)
    }

    data class DishItem(
        val id: String,
        val restaurantId: String,
        val name: String,
        val price: Double,
        val imageUrl: String? = null,
        val isVeg: Boolean? = null,
        val rating: Double? = null,
        val description: String? = null
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(canAddToCart = true, supportsDelivery = true)
    }

    data class ServiceItem(
        val id: String,
        val title: String,
        val providerName: String? = null,
        val imageUrl: String? = null,
        val rating: Double? = null,
        val startingPrice: Double = 0.0,
        val duration: String? = null
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(canBook = true, canSchedule = true)
    }

    data class StoreItem(
        val id: String,
        val name: String,
        val imageUrl: String? = null,
        val address: String? = null,
        val rating: Double? = null,
        val storeType: String? = null,
        val distanceText: String? = null
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(requiresLocation = true)
    }

    data class CollectionItem(
        val id: String,
        val title: String,
        val description: String? = null,
        val bannerUrl: String? = null,
        val itemCount: Int? = null
    ) : CommerceEntity

    data class CampaignItem(
        val id: String,
        val title: String,
        val subtitle: String? = null,
        val badge: String? = null,
        val ctaText: String? = null,
        val imageUrl: String? = null
    ) : CommerceEntity

    data class OfferItem(
        val id: String,
        val title: String,
        val discountCode: String? = null,
        val discountText: String? = null,
        val expiryText: String? = null
    ) : CommerceEntity

    data class ProviderItem(
        val id: String,
        val name: String,
        val category: String? = null,
        val rating: Double? = null,
        val avatarUrl: String? = null
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(canBook = true)
    }

    data class PrescriptionItem(
        val prescription: Prescription
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(rxRequired = true)
    }

    data class BookingAppointmentItem(
        val id: String,
        val serviceName: String,
        val providerName: String,
        val dateTimeText: String,
        val status: String
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(canSchedule = true)
    }

    data class MembershipSubscriptionItem(
        val id: String,
        val planTitle: String,
        val benefits: List<String> = emptyList(),
        val priceFormatted: String,
        val billingCycle: String
    ) : CommerceEntity {
        override val capabilities: EntityCapabilityMetadata
            get() = EntityCapabilityMetadata(canAddToCart = true)
    }

    data class CustomClientEntity(
        val entityId: String,
        val typeName: String,
        val title: String,
        val subtitle: String? = null,
        val attributes: Map<String, String> = emptyMap()
    ) : CommerceEntity

    data class UnknownEntity(
        val entityId: String,
        val rawType: String,
        val title: String = "Unknown Entity",
        val subtitle: String? = null
    ) : CommerceEntity
}

enum class HomeSectionType {
    LOCATION_HEADER,
    UNIVERSAL_SEARCH,
    VERTICAL_NAV_RAIL,
    HERO_CAMPAIGN,
    BUY_AGAIN,
    FAST_FULFILLMENT_NEAR_YOU,
    TOP_DEALS,
    POPULAR_PICKS,
    CATEGORY_GRID,
    BRAND_PARTNERS,
    RESTAURANT_SHELF,
    SERVICE_SHELF,
    DEAL_GRID,
    EDITORIAL,
    RECOMMENDED_FEED,
    COLLECTION_SECTION,
    DISH_SHELF
}

/** A server-authored Home section. The client renders, never composes. */
data class HomeSection(
    val id: String,
    val type: HomeSectionType,
    val title: String? = null,
    val subtitle: String? = null,
    val priority: Int = 0,
    val entities: List<CommerceEntity> = emptyList(),
    val heroDto: HomeHeroDto? = null
)

/**
 * Location and shopping context for Home. A formatted string is not enough: feed
 * eligibility, ETA and offers all depend on location identity and fulfillment status.
 */
data class HomeContext(
    val addressId: String? = null,
    val tag: String? = null,
    val addressLine: String? = null,
    val cityZip: String? = null,
    val geoPoint: String? = null,
    val deliveryZone: String? = null,
    val fulfillment: FulfillmentContext = FulfillmentContext(),
    val sequenceId: Long = 0L
) {
    val hasAddress: Boolean get() = !addressId.isNullOrBlank() && !addressLine.isNullOrBlank()
    val formattedEta: String? get() = fulfillment.etaLabel
    val displayLabel: String?
        get() = when {
            tag != null && addressLine != null -> "$tag · $addressLine"
            addressLine != null -> addressLine
            else -> null
        }
    val isServiceable: Boolean get() = fulfillment.addressId == addressId && fulfillment.status == FulfillmentStatus.SERVICEABLE
}

/**
 * Operational serviceability & fulfillment status for a Commerce OS vertical at the user's location.
 */
data class VerticalStatus(
    val enabled: Boolean,
    val status: VerticalOperationalStatus = if (enabled) VerticalOperationalStatus.AVAILABLE else VerticalOperationalStatus.OUT_OF_ZONE,
    val etaLabel: String? = null,
    val fulfillmentMode: String = "standard"
) {
    val isServiceable: Boolean get() = enabled && (status == VerticalOperationalStatus.AVAILABLE || status == VerticalOperationalStatus.DEGRADED)
}

/**
 * A Commerce OS vertical (Health, Grocery, Food, Fashion...). Availability is
 * SERVER-AUTHORITATIVE via the home feed: a vertical the platform does not serve
 * yet must be advertised as "coming soon", never as a dead button.
 */
data class HomeVertical(
    val id: String,
    val label: String,
    val tagline: String,
    val iconKey: String,
    val isLive: Boolean,
    val status: VerticalStatus? = VerticalStatus(
        enabled = isLive,
        status = if (isLive) VerticalOperationalStatus.AVAILABLE else VerticalOperationalStatus.OUT_OF_ZONE
    )
)

data class CategoryGroup(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String? = null,
    val itemCount: Int? = null,
    val verticalId: String? = null
)

data class BrandItem(
    val id: String,
    val name: String,
    val verticalId: String? = null
)

/** Server-authored home feed payload (GET /api/v1/catalog/home-feed). */
data class HomeFeedResponse(
    val sections: List<HomeSection>? = null,
    val hero: HomeHeroDto? = null,
    val verticals: List<HomeVertical> = emptyList(),
    val buyAgain: List<CommerceProduct> = emptyList(),
    val fastFulfillment: List<CommerceProduct> = emptyList(),
    val topDeals: List<CommerceProduct> = emptyList(),
    val popular: List<CommerceProduct> = emptyList(),
    val popularLabel: String? = null,
    val categories: List<CategoryGroup> = emptyList(),
    val brands: List<BrandItem> = emptyList(),
    val restaurants: List<CommerceEntity.RestaurantItem> = emptyList(),
    val services: List<CommerceEntity.ServiceItem> = emptyList(),
    val feed: List<CommerceProduct> = emptyList(),
    val generatedAt: Long? = null,
    val expiresAt: Long? = null,
    val version: String? = null
)

data class VerticalCategory(
    val id: String,
    val name: String,
    val slug: String,
    val parentId: String? = null,
    val image: String? = null,
    val sortOrder: Int = 0,
    val verticalId: String
)

data class VerticalTaxonomyResponse(
    val verticalId: String,
    val taxonomyVersion: String = "1.0",
    val categories: List<VerticalCategory> = emptyList()
)

/** Server-authored vertical hub feed payload. */
data class VerticalHomeFeedResponse(
    val verticalId: String,
    val title: String,
    val subtitle: String,
    val ctaText: String? = null,
    val hero: HomeHeroDto? = null,
    val featuredProducts: List<CommerceProduct> = emptyList(),
    val categories: List<VerticalCategory> = emptyList(),
    val brands: List<BrandItem> = emptyList()
)

/**
 * A campaign hero. Copy, discount, eligibility, geo and creative live on the
 * server (CMS) — the client never invents a promotion. [imageUrl] is optional:
 * when absent the client renders a brand composition, it never fakes an asset.
 */
data class HomeHeroDto(
    val campaignId: String,
    val title: String,
    val subtitle: String,
    val badge: String,
    val ctaText: String,
    val imageUrl: String? = null,
    val themeKey: String? = null
)