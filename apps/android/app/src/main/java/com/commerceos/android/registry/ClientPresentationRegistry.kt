package com.commerceos.android.registry

import com.commerceos.android.config.ClientConfiguration
import com.commerceos.android.config.CommerceDomain
import com.commerceos.android.model.HomeSectionType

/**
 * Universal Vertical Presentation configuration containing symbols, terminology, icons,
 * hero styling, card mappings, section arrangements, search renderers, CTAs, and workflows.
 */
data class VerticalPresentation(
    val visualSymbol: String,
    val iconKey: String = "shopping_bag",
    val catalogHeader: String,
    val defaultCta: String,
    val itemTerminology: String = "Product",
    val heroTitle: String = "Discover Quality",
    val heroSubtitle: String = "Curated collections for you",
    val cardVariant: CardVariant = CardVariant.GENERIC_PRODUCT,
    val preferredSectionOrder: List<HomeSectionType> = listOf(
        HomeSectionType.HERO_CAMPAIGN,
        HomeSectionType.CATEGORY_GRID,
        HomeSectionType.POPULAR_PICKS,
        HomeSectionType.DEAL_GRID,
        HomeSectionType.RECOMMENDED_FEED
    ),
    val searchRendererType: String = "STANDARD_GRID",
    val workflowType: String = "STANDARD_COMMERCE"
)

/**
 * Client Presentation Registry mapping entity types, vertical IDs, and custom client verticals to presentation traits.
 */
object ClientPresentationRegistry {

    private val customVerticals = mutableMapOf<String, VerticalPresentation>()

    fun registerCustomVertical(verticalId: String, presentation: VerticalPresentation) {
        customVerticals[verticalId.lowercase()] = presentation
    }

    fun clearCustomVerticals() {
        customVerticals.clear()
    }

    fun resolvePresentation(verticalId: String, config: ClientConfiguration): VerticalPresentation {
        val key = verticalId.lowercase().trim()
        
        // 1. Custom client vertical registered dynamically
        customVerticals[key]?.let { return it }

        // 2. Client Domain / Vertical fallback
        val domain = config.domain
        return when {
            domain == CommerceDomain.FOOD || key in listOf("food", "restaurant", "dining") ->
                VerticalPresentation(
                    visualSymbol = "🍽️",
                    iconKey = "restaurant",
                    catalogHeader = "Cuisines & Dining",
                    defaultCta = "Order Now",
                    itemTerminology = "Dish",
                    heroTitle = "Delicious Food Delivered Fast",
                    heroSubtitle = "Top rated local restaurants & cuisines",
                    cardVariant = CardVariant.RESTAURANT_CARD,
                    preferredSectionOrder = listOf(
                        HomeSectionType.HERO_CAMPAIGN,
                        HomeSectionType.RESTAURANT_SHELF,
                        HomeSectionType.TOP_DEALS,
                        HomeSectionType.CATEGORY_GRID
                    ),
                    searchRendererType = "RESTAURANT_LIST",
                    workflowType = "FOOD_DELIVERY"
                )

            domain == CommerceDomain.FASHION || key in listOf("fashion", "style", "apparel") ->
                VerticalPresentation(
                    visualSymbol = "👔",
                    iconKey = "checkroom",
                    catalogHeader = "Style & Apparel",
                    defaultCta = "Shop Collection",
                    itemTerminology = "Apparel",
                    heroTitle = "New Season Collection",
                    heroSubtitle = "Trendy fashion, footwear & accessories",
                    cardVariant = CardVariant.FASHION_PRODUCT,
                    preferredSectionOrder = listOf(
                        HomeSectionType.HERO_CAMPAIGN,
                        HomeSectionType.BRAND_PARTNERS,
                        HomeSectionType.CATEGORY_GRID,
                        HomeSectionType.EDITORIAL
                    ),
                    searchRendererType = "FASHION_GRID",
                    workflowType = "FASHION_CATALOG"
                )

            domain == CommerceDomain.ELECTRONICS || key in listOf("electronics", "tech", "gadgets") ->
                VerticalPresentation(
                    visualSymbol = "📱",
                    iconKey = "devices",
                    catalogHeader = "Tech & Devices",
                    defaultCta = "Explore Tech",
                    itemTerminology = "Gadget",
                    heroTitle = "Next-Gen Tech Essentials",
                    heroSubtitle = "Laptops, smartphones & smart accessories",
                    cardVariant = CardVariant.ELECTRONICS_PRODUCT,
                    preferredSectionOrder = listOf(
                        HomeSectionType.HERO_CAMPAIGN,
                        HomeSectionType.FAST_FULFILLMENT_NEAR_YOU,
                        HomeSectionType.CATEGORY_GRID,
                        HomeSectionType.BRAND_PARTNERS
                    ),
                    searchRendererType = "TECH_SPEC_GRID",
                    workflowType = "ELECTRONICS_SPEC"
                )

            domain == CommerceDomain.PHARMACY || key in listOf("health", "pharmacy", "medicine") ->
                VerticalPresentation(
                    visualSymbol = "💊",
                    iconKey = "medical_services",
                    catalogHeader = "Healthcare & Rx",
                    defaultCta = "Order Medicines",
                    itemTerminology = "Medicine",
                    heroTitle = "Certified Online Pharmacy",
                    heroSubtitle = "Express medicine delivery & Rx verification",
                    cardVariant = CardVariant.PHARMACY_PRODUCT,
                    preferredSectionOrder = listOf(
                        HomeSectionType.HERO_CAMPAIGN,
                        HomeSectionType.BUY_AGAIN,
                        HomeSectionType.FAST_FULFILLMENT_NEAR_YOU,
                        HomeSectionType.CATEGORY_GRID
                    ),
                    searchRendererType = "PHARMACY_CARD",
                    workflowType = "RX_VERIFICATION"
                )

            domain == CommerceDomain.SERVICES || key in listOf("local", "services", "home_services") ->
                VerticalPresentation(
                    visualSymbol = "🔧",
                    iconKey = "handyman",
                    catalogHeader = "Home Services",
                    defaultCta = "Book Service",
                    itemTerminology = "Service",
                    heroTitle = "Expert Home & Local Services",
                    heroSubtitle = "Verified professionals at your doorstep",
                    cardVariant = CardVariant.SERVICE_CARD,
                    preferredSectionOrder = listOf(
                        HomeSectionType.HERO_CAMPAIGN,
                        HomeSectionType.SERVICE_SHELF,
                        HomeSectionType.CATEGORY_GRID
                    ),
                    searchRendererType = "SERVICE_LIST",
                    workflowType = "SERVICE_BOOKING"
                )

            else ->
                VerticalPresentation(
                    visualSymbol = "🛒",
                    iconKey = "storefront",
                    catalogHeader = "Storefront Catalog",
                    defaultCta = "Browse Store",
                    itemTerminology = "Item",
                    heroTitle = "Welcome to ${config.identity.clientName}",
                    heroSubtitle = "Discover curated products across top categories",
                    cardVariant = CardVariant.GENERIC_PRODUCT,
                    preferredSectionOrder = config.enabledHomeSections,
                    searchRendererType = "STANDARD_GRID",
                    workflowType = "STANDARD_COMMERCE"
                )
        }
    }
}
