package com.commerceos.android.model

object FallbackHomeFeed {
    fun createDefaultFeed(customerId: String): HomeFeedResponse {
        val verticals = listOf(
            HomeVertical("pharmacy", "Pharmacy & Health", "10 Mins", "ic_pharmacy", true),
            HomeVertical("food", "Food & Gourmet", "15 Mins", "ic_food", true),
            HomeVertical("fashion", "Fashion & Apparel", "30 Mins", "ic_fashion", true),
            HomeVertical("electronics", "Electronics", "20 Mins", "ic_electronics", true),
            HomeVertical("services", "Doctor Consult", "Instant", "ic_services", true)
        )

        val products = listOf(
            CommerceProduct(
                id = "med_001",
                sku = "SKU-PCM-650",
                name = "Dolo 650mg Paracetamol Tablet",
                brand = "Micro Labs",
                unitLabel = "15 Tablets Strip",
                price = 35.0,
                discountedPrice = 30.5,
                mrp = 35.0
            ),
            CommerceProduct(
                id = "med_002",
                sku = "SKU-AMOX-500",
                name = "Moxkind-CV 625 Dry Syrup / Tablet",
                brand = "Mankind Pharma",
                unitLabel = "10 Tablets Strip",
                price = 180.0,
                discountedPrice = 155.0,
                mrp = 180.0
            ),
            CommerceProduct(
                id = "med_003",
                sku = "SKU-PAN-40",
                name = "Pan-40 Gastro-Resistant Tablet",
                brand = "Alkem Laboratories",
                unitLabel = "15 Tablets Strip",
                price = 155.0,
                discountedPrice = 132.0,
                mrp = 155.0
            )
        )

        val categories = listOf(
            CategoryGroup("cat_01", "Medicines & Rx", "Essential Healthcare"),
            CategoryGroup("cat_02", "Personal Care", "Skincare & Hygiene"),
            CategoryGroup("cat_03", "Baby Care", "Diapers & Nutrition"),
            CategoryGroup("cat_04", "Wellness", "Vitamins & Supplements")
        )

        val brands = listOf(
            BrandItem("b_01", "Micro Labs"),
            BrandItem("b_02", "Mankind Pharma"),
            BrandItem("b_03", "Alkem")
        )

        val hero = HomeHeroDto(
            campaignId = "hero_01",
            title = "10-Minute Express Quick Commerce",
            subtitle = "Cash on Delivery Available • Verified Healthcare",
            badge = "OFFICIAL",
            ctaText = "Shop Now"
        )

        return HomeFeedResponse(
            generatedAt = System.currentTimeMillis(),
            verticals = verticals,
            hero = hero,
            buyAgain = emptyList(),
            fastFulfillment = products,
            topDeals = products,
            popular = products,
            categories = categories,
            brands = brands,
            feed = products
        )
    }
}
