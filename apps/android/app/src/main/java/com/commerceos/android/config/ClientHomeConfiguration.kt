package com.commerceos.android.config

import com.commerceos.android.model.HomeSectionType
import com.commerceos.android.registry.CardVariant

/**
 * Configuration model for client-defined Home Hero section.
 */
data class HeroConfig(
    val title: String = "Special Campaign",
    val subtitle: String = "Exclusive offers tailored for you",
    val ctaText: String = "Explore Deals",
    val bannerImageUrl: String? = null,
    val isVisible: Boolean = true
)

/**
 * Configuration model for client-defined Editorial section.
 */
data class EditorialConfig(
    val title: String = "Spotlight & Stories",
    val body: String = "Discover curated picks and expert recommendations.",
    val imageUrl: String? = null,
    val isVisible: Boolean = true
)

/**
 * Configuration model for section layout preferences.
 */
data class SectionLayoutConfig(
    val sectionType: HomeSectionType,
    val title: String? = null,
    val subtitle: String? = null,
    val isVisible: Boolean = true,
    val orderPriority: Int = 0
)

/**
 * Client-defined Home Screen Configuration.
 */
data class ClientHomeConfiguration(
    val enabledHomeSections: List<HomeSectionType> = listOf(
        HomeSectionType.HERO_CAMPAIGN,
        HomeSectionType.CATEGORY_GRID,
        HomeSectionType.DEAL_GRID,
        HomeSectionType.RECOMMENDED_FEED,
        HomeSectionType.EDITORIAL
    ),
    val sectionTitles: Map<HomeSectionType, String> = mapOf(
        HomeSectionType.HERO_CAMPAIGN to "Featured Campaigns",
        HomeSectionType.CATEGORY_GRID to "Shop by Category",
        HomeSectionType.DEAL_GRID to "Top Offers & Deals",
        HomeSectionType.RECOMMENDED_FEED to "Recommended for You",
        HomeSectionType.EDITORIAL to "Editor's Picks",
        HomeSectionType.RESTAURANT_SHELF to "Popular Kitchens",
        HomeSectionType.SERVICE_SHELF to "Top Rated Services"
    ),
    val sectionSubtitles: Map<HomeSectionType, String> = mapOf(
        HomeSectionType.HERO_CAMPAIGN to "Handpicked selections",
        HomeSectionType.DEAL_GRID to "Limited time savings",
        HomeSectionType.SERVICE_SHELF to "Book verified professionals"
    ),
    val heroConfig: HeroConfig = HeroConfig(),
    val editorialConfig: EditorialConfig = EditorialConfig(),
    val defaultCardVariant: CardVariant = CardVariant.GENERIC_PRODUCT,
    val customPrimaryCta: String? = null
)
