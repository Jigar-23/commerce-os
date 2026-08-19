package com.commerceos.android.model

/**
 * Clean UI presentation model for Commerce Product Cards.
 * Decouples universal UI rendering from legacy medicine shims and backend DTOs.
 * Delivery SLA / ETA comes strictly from fulfillment data — NEVER manufactured.
 */
data class ProductCardModel(
    val id: String,
    val sku: String,
    val name: String,
    val brandName: String,
    val packSize: String,
    val price: Double,
    val sellingPrice: Double,
    val image: String,
    val inStock: Boolean = true,
    val stockCount: Int? = null,
    val discountPercent: Int = 0,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val etaLabel: String? = null,
    val rxRequired: Boolean = false,
    val coldChain: Boolean = false,
    val isWishlisted: Boolean = false,
    val verticalId: String = "general"
)

fun CommerceProduct.toProductCardModel(
    etaLabel: String? = null,
    isWishlisted: Boolean = false
): ProductCardModel {
    return ProductCardModel(
        id = id,
        sku = sku,
        name = name,
        brandName = brand ?: brandName ?: "",
        packSize = unitLabel ?: "",
        price = price,
        sellingPrice = sellingPrice,
        image = image ?: "",
        inStock = inStock ?: true,
        stockCount = null,
        discountPercent = discountPercent,
        rating = rating,
        reviewCount = reviewCount,
        etaLabel = etaLabel, // Authentic fulfillment SLA; null if not available
        rxRequired = medicineDetails?.prescriptionRequired ?: false,
        coldChain = medicineDetails?.coldChain ?: false,
        isWishlisted = isWishlisted,
        verticalId = verticalId ?: "general"
    )
}

fun ApiMedicine.toProductCardModel(
    isWishlisted: Boolean = false
): ProductCardModel {
    return ProductCardModel(
        id = id,
        sku = sku,
        name = name,
        brandName = brandName ?: "",
        packSize = packSize ?: "",
        price = mrp ?: price,
        sellingPrice = discountedPrice,
        image = image ?: "",
        inStock = inStock ?: true,
        stockCount = stockCount,
        discountPercent = if ((mrp ?: price) > discountedPrice && (mrp ?: price) > 0) {
            ((((mrp ?: price) - discountedPrice) / (mrp ?: price)) * 100).toInt()
        } else 0,
        rating = rating,
        reviewCount = reviewCount,
        etaLabel = null, // No manufactured SLA
        rxRequired = rxRequirement != "OTC",
        coldChain = coldChainRequired ?: false,
        isWishlisted = isWishlisted,
        verticalId = "health"
    )
}
