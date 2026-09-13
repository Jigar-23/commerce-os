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

object MedicineImageResolver {
    private val PACKAGING_PHOTOS = mapOf(
        "SKU-PCM-650" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
        "SKU-PARA-500" to "https://cdn01.pharmeasy.in/dam/products_otc/H45820/crocin-650mg-strip-of-15-tablets-6.1-1775911968.jpg",
        "SKU-AMOX-625" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
        "SKU-COLD-01" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
        "SKU-INS-01" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/192397/lantus-100iu-cartridge-of-3ml-solution-for-injection-box-front-1-1756885208-non-watermarked.jpg",
        "SKU-PARACIP-500" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/266954/paracip-500mg-strip-of-10-tablets-box-front-1-1756826302-non-watermarked.jpg",
        "SKU-GLYC-500" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
        "SKU-CET-10" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/S21238/cetzine-10mg-strip-of-15-tablets-box-front-1-1756971688-non-watermarked.jpg",
        "SKU-AZI-500" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/310659/azithral-azithromycin-500mg-strip-of-5-tablets-front-2-1756922244-non-watermarked.jpg"
    )

    private val KEYWORD_PHOTOS = listOf(
        "dolo" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
        "paracetamol" to "https://cdn01.pharmeasy.in/dam/products_otc/H45820/crocin-650mg-strip-of-15-tablets-6.1-1775911968.jpg",
        "crocin" to "https://cdn01.pharmeasy.in/dam/products_otc/H45820/crocin-650mg-strip-of-15-tablets-6.1-1775911968.jpg",
        "amoxiclav" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
        "augmentin" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
        "cold" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
        "cough" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
        "benadryl" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
        "insulin" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/192397/lantus-100iu-cartridge-of-3ml-solution-for-injection-box-front-1-1756885208-non-watermarked.jpg",
        "lantus" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/192397/lantus-100iu-cartridge-of-3ml-solution-for-injection-box-front-1-1756885208-non-watermarked.jpg",
        "paracip" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/266954/paracip-500mg-strip-of-10-tablets-box-front-1-1756826302-non-watermarked.jpg",
        "glycomet" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
        "cetrizet" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/S21238/cetzine-10mg-strip-of-15-tablets-box-front-1-1756971688-non-watermarked.jpg",
        "cetzine" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/S21238/cetzine-10mg-strip-of-15-tablets-box-front-1-1756971688-non-watermarked.jpg",
        "azithral" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/310659/azithral-azithromycin-500mg-strip-of-5-tablets-front-2-1756922244-non-watermarked.jpg",
        "azithromycin" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/310659/azithral-azithromycin-500mg-strip-of-5-tablets-front-2-1756922244-non-watermarked.jpg",
        "pan 40" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/I00306/pan-40mg-strip-of-15-tablets-front-2-1756099995-non-watermarked.jpg",
        "shelcal" to "https://cdn01.pharmeasy.in/dam/products_otc/K78299/shelcal-500mg-bottle-of-30-tablets-6.1-1787223246.jpg",
        "becosules" to "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg",
        "volini" to "https://cdn01.pharmeasy.in/dam/products_otc/I00392/volini-pain-relief-gel-tube-of-100-g-6.1-1712725504.jpg",
        "digene" to "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg"
    )

    fun resolve(sku: String?, name: String? = null, rawImage: String? = null): String {
        if (!rawImage.isNullOrBlank() && rawImage.startsWith("http") && !rawImage.contains("unsplash.com")) return rawImage
        if (!sku.isNullOrBlank() && PACKAGING_PHOTOS.containsKey(sku.uppercase())) {
            return PACKAGING_PHOTOS[sku.uppercase()]!!
        }
        if (!name.isNullOrBlank()) {
            val lower = name.lowercase()
            for ((kw, url) in KEYWORD_PHOTOS) {
                if (lower.contains(kw)) return url
            }
        }
        if (!rawImage.isNullOrBlank() && rawImage.startsWith("http")) return rawImage
        return ""
    }
}

fun CommerceProduct.toProductCardModel(
    etaLabel: String? = null,
    isWishlisted: Boolean = false
): ProductCardModel {
    val safeSellingPrice = effectiveSellingPrice
    val safePrice = if (displayPrice > safeSellingPrice) displayPrice else (if (price > safeSellingPrice) price else safeSellingPrice)
    val discount = if (safePrice > safeSellingPrice && safePrice > 0) {
        (((safePrice - safeSellingPrice) / safePrice) * 100).toInt()
    } else 0
    return ProductCardModel(
        id = id,
        sku = sku,
        name = name,
        brandName = brandName ?: brand ?: "",
        packSize = packSize ?: unitLabel ?: "",
        price = safePrice,
        sellingPrice = safeSellingPrice,
        image = MedicineImageResolver.resolve(sku, name, image),
        inStock = inStock ?: true,
        stockCount = null,
        discountPercent = discount,
        rating = rating,
        reviewCount = reviewCount,
        etaLabel = etaLabel, // Authentic fulfillment SLA; null if not available
        rxRequired = (rxRequirement != null && rxRequirement != "OTC") || (medicineDetails?.prescriptionRequired ?: false),
        coldChain = medicineDetails?.coldChain ?: false,
        isWishlisted = isWishlisted,
        verticalId = verticalId ?: "general"
    )
}

fun ApiMedicine.toProductCardModel(
    isWishlisted: Boolean = false
): ProductCardModel {
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
    return ProductCardModel(
        id = id,
        sku = sku,
        name = name,
        brandName = brandName ?: "",
        packSize = packSize ?: "",
        price = effectivePrice,
        sellingPrice = effectiveSellingPrice,
        image = MedicineImageResolver.resolve(sku, name, image),
        inStock = inStock ?: true,
        stockCount = stockCount,
        discountPercent = if (effectivePrice > effectiveSellingPrice && effectivePrice > 0) {
            ((((effectivePrice - effectiveSellingPrice) / effectivePrice) * 100).toInt())
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
