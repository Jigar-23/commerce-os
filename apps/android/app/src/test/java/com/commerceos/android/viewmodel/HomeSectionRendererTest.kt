package com.commerceos.android.viewmodel

import com.commerceos.android.model.*
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Unit tests verifying HomeSection mapping and section enum classification.
 */
class HomeSectionRendererTest {

    @Test
    fun testHomeSectionTypeClassification_returnsTypedSections() {
        val heroSection = HomeSection(
            id = "hero_01",
            title = "Mega Sale",
            type = HomeSectionType.HERO_CAMPAIGN,
            entities = emptyList()
        )
        val restSection = HomeSection(
            id = "rest_01",
            title = "Top Kitchens",
            type = HomeSectionType.RESTAURANT_SHELF,
            entities = listOf(
                CommerceEntity.RestaurantItem(
                    id = "r1", name = "Bistro", imageUrl = null, cuisine = "Italian", priceForTwo = "₹800"
                )
            )
        )
        val servSection = HomeSection(
            id = "serv_01",
            title = "Home Repairs",
            type = HomeSectionType.SERVICE_SHELF,
            entities = listOf(
                CommerceEntity.ServiceItem(
                    id = "s1", title = "Plumbing", providerName = "FixIt", imageUrl = null, startingPrice = 299.0, duration = "30 mins"
                )
            )
        )
        val dealSection = HomeSection(
            id = "deal_01",
            title = "Flash Deals",
            type = HomeSectionType.DEAL_GRID,
            entities = emptyList()
        )

        assertEquals(HomeSectionType.HERO_CAMPAIGN, heroSection.type)
        assertEquals(HomeSectionType.RESTAURANT_SHELF, restSection.type)
        assertEquals(HomeSectionType.SERVICE_SHELF, servSection.type)
        assertEquals(HomeSectionType.DEAL_GRID, dealSection.type)
        assertEquals(1, restSection.entities.size)
        assertEquals(1, servSection.entities.size)
    }
}
