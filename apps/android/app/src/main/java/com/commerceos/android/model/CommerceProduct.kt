package com.commerceos.android.model

/** Pharmacy-specific product attributes. */
data class MedicineAttributes(
    val prescriptionRequired: Boolean = false,
    val composition: String? = null,
    val manufacturer: String? = null,
    val coldChain: Boolean = false,
    val packaging: String? = null
)

/** Grocery-specific product attributes. */
data class GroceryAttributes(
    val netWeight: String? = null,
    val expiryDays: Int? = null,
    val isPerishable: Boolean = false
)

/** Fashion-specific product attributes. */
data class FashionAttributes(
    val sizes: List<String> = emptyList(),
    val colors: List<String> = emptyList(),
    val material: String? = null
)

/** Electronics-specific product attributes. */
data class ElectronicsAttributes(
    val warrantyMonths: Int? = null,
    val modelNumber: String? = null
)

/**
 * Universal Commerce Product model used across Commerce OS Home, Vertical Hubs, Search,
 * and Catalog. Contains core product properties while supporting domain-specific attributes.
 */
data class CommerceProduct(
    val id: String,
    val sku: String,
    val name: String,
    val brand: String? = null,
    @com.google.gson.annotations.SerializedName("price") val price: Double = 0.0,
    @com.google.gson.annotations.SerializedName("discountedPrice") val discountedPrice: Double = 0.0,
    @com.google.gson.annotations.SerializedName("sellingPrice") val rawSellingPrice: Double = 0.0,
    @com.google.gson.annotations.SerializedName("mrp") val mrp: Double? = null,
    val image: String? = null,
    val inStock: Boolean? = null,
    val rating: Double? = null,
    val reviewCount: Int? = null,
    val verticalId: String? = null,
    val merchantId: String? = null,
    val categoryId: String? = null,
    val unitLabel: String? = null,
    val brandName: String? = brand,
    val medicineDetails: MedicineAttributes? = null,
    val groceryDetails: GroceryAttributes? = null,
    val fashionDetails: FashionAttributes? = null,
    val electronicsDetails: ElectronicsAttributes? = null
) {
    val sellingPrice: Double
        get() = when {
            discountedPrice > 0 -> discountedPrice
            rawSellingPrice > 0 -> rawSellingPrice
            price > 0 -> price
            (mrp ?: 0.0) > 0 -> mrp!!
            else -> 5.0
        }

    val displayPrice: Double
        get() = when {
            (mrp ?: 0.0) > 0 -> mrp!!
            price > 0 -> price
            else -> sellingPrice
        }

    val discountPercent: Int
        get() = if (displayPrice > sellingPrice && displayPrice > 0) {
            (((displayPrice - sellingPrice) / displayPrice) * 100).toInt()
        } else 0
}

/** Extension helper to map legacy ApiMedicine to universal CommerceProduct. */
fun ApiMedicine.toCommerceProduct(verticalId: String = "health"): CommerceProduct {
    val effectiveSellingPrice = when {
        discountedPrice > 0 -> discountedPrice
        price > 0 -> price
        (mrp ?: 0.0) > 0 -> mrp!!
        else -> 5.0
    }
    val effectivePrice = when {
        (mrp ?: 0.0) > 0 -> mrp!!
        price > 0 -> price
        else -> effectiveSellingPrice
    }
    return CommerceProduct(
        id = id,
        sku = sku,
        name = name,
        brand = brandName,
        price = effectivePrice,
        discountedPrice = effectiveSellingPrice,
        mrp = effectivePrice,
        image = image ?: "",
        inStock = inStock,
        rating = rating,
        reviewCount = reviewCount,
        verticalId = verticalId,
        merchantId = null,
        categoryId = therapeuticCategory ?: "health",
        unitLabel = packSize,
        medicineDetails = MedicineAttributes(
            prescriptionRequired = rxRequirement != "OTC",
            composition = null,
            manufacturer = manufacturer,
            coldChain = coldChainRequired ?: false,
            packaging = packSize
        )
    )
}
