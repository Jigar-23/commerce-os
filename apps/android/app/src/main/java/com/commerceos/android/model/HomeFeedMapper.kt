package com.commerceos.android.model

/**
 * Server DTO -> UI Domain Section Mapper for Commerce OS Home Feed.
 * Ensures clean, zero-fabricated transformation of backend payloads into HomeSection structures.
 */
object HomeFeedMapper {
    fun mapFeedToSections(feed: HomeFeedResponse): List<HomeSection> {
        if (!feed.sections.isNullOrEmpty()) {
            return feed.sections
        }

        val list = mutableListOf<HomeSection>()

        feed.hero?.let {
            list.add(
                HomeSection(
                    id = "hero_" + it.campaignId,
                    type = HomeSectionType.HERO_CAMPAIGN,
                    heroDto = it
                )
            )
        }

        if (feed.buyAgain.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "buy_again",
                    type = HomeSectionType.BUY_AGAIN,
                    title = "Buy Again",
                    subtitle = null,
                    entities = feed.buyAgain.map { CommerceEntity.ProductItem(product = it, vertical = it.verticalId ?: "general") }
                )
            )
        }

        if (feed.fastFulfillment.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "fast_fulfillment",
                    type = HomeSectionType.FAST_FULFILLMENT_NEAR_YOU,
                    title = "Fast Fulfillment Near You",
                    subtitle = null,
                    entities = feed.fastFulfillment.map { CommerceEntity.ProductItem(product = it, vertical = it.verticalId ?: "general", isFastFulfillmentAvailable = true) }
                )
            )
        }

        if (feed.topDeals.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "top_deals",
                    type = HomeSectionType.TOP_DEALS,
                    title = "Top Deals",
                    subtitle = null,
                    entities = feed.topDeals.map { CommerceEntity.ProductItem(product = it, vertical = it.verticalId ?: "general") }
                )
            )
        }

        if (feed.restaurants.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "restaurants_near_you",
                    type = HomeSectionType.RESTAURANT_SHELF,
                    title = "Top Restaurants Near You",
                    subtitle = null,
                    entities = feed.restaurants
                )
            )
        }

        if (feed.services.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "services_near_you",
                    type = HomeSectionType.SERVICE_SHELF,
                    title = "Local On-Demand Services",
                    subtitle = null,
                    entities = feed.services
                )
            )
        }

        if (feed.popular.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "popular_picks",
                    type = HomeSectionType.POPULAR_PICKS,
                    title = "Popular Picks",
                    subtitle = feed.popularLabel,
                    entities = feed.popular.map { CommerceEntity.ProductItem(product = it, vertical = it.verticalId ?: "general") }
                )
            )
        }

        if (feed.categories.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "categories",
                    type = HomeSectionType.CATEGORY_GRID,
                    title = "Shop by category",
                    entities = feed.categories.map { CommerceEntity.CategoryItem(group = it, vertical = it.verticalId ?: "general") }
                )
            )
        }

        if (feed.brands.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "brands",
                    type = HomeSectionType.BRAND_PARTNERS,
                    title = "Popular brands",
                    entities = feed.brands.map { CommerceEntity.Brand(item = it, vertical = it.verticalId ?: "general") }
                )
            )
        }

        if (feed.feed.isNotEmpty()) {
            list.add(
                HomeSection(
                    id = "recommended",
                    type = HomeSectionType.RECOMMENDED_FEED,
                    title = "Recommended for you",
                    entities = feed.feed.map { CommerceEntity.ProductItem(product = it, vertical = it.verticalId ?: "general") }
                )
            )
        }

        return list
    }
}
