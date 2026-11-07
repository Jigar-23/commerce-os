package com.commerceos.android.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.ApiMedicine
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.ProductCardModel
import com.commerceos.android.model.toProductCardModel
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceElevation
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.PlusJakartaSans
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter

enum class ProductCardVariant {
    /** Full-width, image on the left — catalog main feed. */
    Compact,

    /** Vertical tile with a large image on top — 2-column grids and home shelves. */
    Grid
}

@Composable
fun CommerceProductCard(
    product: CommerceProduct,
    onSelect: (CommerceProduct) -> Unit,
    onAddToCart: (CommerceProduct) -> Unit,
    variant: ProductCardVariant = ProductCardVariant.Compact,
    isReorderCard: Boolean = false,
    quantity: Int = 0,
    onQuantityChange: (Int) -> Unit = {},
    onWishlistToggle: ((String) -> Unit)? = null,
    isWishlisted: Boolean = false,
    etaLabel: String? = null,
    contentScale: ContentScale = ContentScale.Fit, // Default to Fit for retail products
    modifier: Modifier = Modifier
) {
    val model = product.toProductCardModel(etaLabel = etaLabel, isWishlisted = isWishlisted)
    CommerceProductCard(
        model = model,
        onSelect = { onSelect(product) },
        onAddToCart = { onAddToCart(product) },
        variant = variant,
        isReorderCard = isReorderCard,
        quantity = quantity,
        onQuantityChange = onQuantityChange,
        onWishlistToggle = onWishlistToggle,
        contentScale = contentScale,
        modifier = modifier
    )
}

@Composable
fun CommerceProductCard(
    medicine: ApiMedicine,
    onSelect: (ApiMedicine) -> Unit,
    onAddToCart: (ApiMedicine) -> Unit,
    variant: ProductCardVariant = ProductCardVariant.Compact,
    isReorderCard: Boolean = false,
    quantity: Int = 0,
    onQuantityChange: (Int) -> Unit = {},
    onWishlistToggle: ((String) -> Unit)? = null,
    isWishlisted: Boolean = false,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
) {
    val model = medicine.toProductCardModel(isWishlisted = isWishlisted)
    CommerceProductCard(
        model = model,
        onSelect = { onSelect(medicine) },
        onAddToCart = { onAddToCart(medicine) },
        variant = variant,
        isReorderCard = isReorderCard,
        quantity = quantity,
        onQuantityChange = onQuantityChange,
        onWishlistToggle = onWishlistToggle,
        contentScale = contentScale,
        modifier = modifier
    )
}

/**
 * Universal Commerce OS Product Card consuming [ProductCardModel].
 * Image defaults to [ContentScale.Fit] to preserve packaged product detail.
 * Includes top-right Wishlist heart toggle and authentic SLA display (no hardcoded ETAs).
 */
@Composable
fun CommerceProductCard(
    model: ProductCardModel,
    onSelect: () -> Unit,
    onAddToCart: () -> Unit,
    variant: ProductCardVariant = ProductCardVariant.Compact,
    isReorderCard: Boolean = false,
    quantity: Int = 0,
    onQuantityChange: (Int) -> Unit = {},
    onWishlistToggle: ((String) -> Unit)? = null,
    contentScale: ContentScale = ContentScale.Fit,
    modifier: Modifier = Modifier
) {
    val cardModifier = modifier.clickable(onClick = onSelect)

    when (variant) {
        ProductCardVariant.Compact -> CompactProductCard(
            model = model,
            onAddToCart = onAddToCart,
            isReorderCard = isReorderCard,
            quantity = quantity,
            onQuantityChange = onQuantityChange,
            onWishlistToggle = onWishlistToggle,
            contentScale = contentScale,
            modifier = cardModifier
        )

        ProductCardVariant.Grid -> GridProductCard(
            model = model,
            onAddToCart = onAddToCart,
            isReorderCard = isReorderCard,
            quantity = quantity,
            onQuantityChange = onQuantityChange,
            onWishlistToggle = onWishlistToggle,
            contentScale = contentScale,
            modifier = cardModifier
        )
    }
}

/** Vertical specialization: Grocery Card focusing on pack size, price savings, SLA and instant ADD. */
@Composable
fun GroceryProductCard(
    model: ProductCardModel,
    onSelect: () -> Unit,
    onAddToCart: () -> Unit,
    quantity: Int = 0,
    onQuantityChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    CommerceProductCard(
        model = model.copy(rxRequired = false, coldChain = false),
        onSelect = onSelect,
        onAddToCart = onAddToCart,
        variant = ProductCardVariant.Grid,
        quantity = quantity,
        onQuantityChange = onQuantityChange,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

/** Vertical specialization: Pharmacy Card focusing on medicine details, Rx badges, cold chain and dosage. */
@Composable
fun PharmacyProductCard(
    model: ProductCardModel,
    onSelect: () -> Unit,
    onAddToCart: () -> Unit,
    quantity: Int = 0,
    onQuantityChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    CommerceProductCard(
        model = model,
        onSelect = onSelect,
        onAddToCart = onAddToCart,
        variant = ProductCardVariant.Grid,
        quantity = quantity,
        onQuantityChange = onQuantityChange,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
}

fun formatHealthcareTitle(raw: String?): String {
    val trimmed = raw?.trim().orEmpty()
    if (trimmed.isBlank()) return "Medicine"
    if (trimmed.equals("para", ignoreCase = true)) return "Paracetamol 650mg Tablets"
    if (trimmed.equals("crocin", ignoreCase = true)) return "Crocin 650 Advance Fast Action Tablets"
    if (trimmed.equals("dolo", ignoreCase = true) || trimmed.equals("dolo 650", ignoreCase = true) || trimmed.equals("dolo-650", ignoreCase = true)) return "Dolo 650mg Paracetamol Tablets"
    if (trimmed.equals("augmentin", ignoreCase = true) || trimmed.contains("augmentin 625", ignoreCase = true)) return "Augmentin 625 Duo Tablet"
    if (trimmed.equals("pan-d", ignoreCase = true) || trimmed.equals("pand", ignoreCase = true) || trimmed.equals("pan d", ignoreCase = true)) return "Pan-D Capsule"
    if (trimmed.equals("glycomet", ignoreCase = true) || trimmed.contains("glycomet 500", ignoreCase = true)) return "Glycomet 500 SR Tablet"
    if (trimmed.equals("becosules", ignoreCase = true) || trimmed.contains("becosules z", ignoreCase = true)) return "Becosules Z Multivitamin Capsule"
    if (trimmed.equals("benadryl", ignoreCase = true)) return "Benadryl Cough Formula Syrup"
    if (trimmed.equals("digene", ignoreCase = true)) return "Digene Gel Acidity Relief Mint"
    return if (trimmed == trimmed.lowercase()) {
        trimmed.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
        }
    } else trimmed
}

fun formatHealthcareBrand(raw: String?, title: String? = "", manufacturer: String? = null): String {
    val m = manufacturer?.trim()
    if (!m.isNullOrBlank() && !m.equals("Seller Brand", ignoreCase = true) && !m.equals("Generic", ignoreCase = true) && !m.equals("CommerceOS Health", ignoreCase = true)) {
        return m
    }
    val b = raw?.trim()
    if (!b.isNullOrBlank() && !b.equals("Seller Brand", ignoreCase = true) && !b.equals("Generic", ignoreCase = true)) {
        return b
    }
    val lower = (title ?: "").lowercase()
    return when {
        lower.contains("dolo") -> "Micro Labs Ltd"
        lower.contains("crocin") -> "GlaxoSmithKline"
        lower.contains("combiflam") -> "Sanofi India Ltd"
        lower.contains("paracip") || lower.contains("cip") -> "Cipla Limited"
        lower.contains("augmentin") -> "GlaxoSmithKline"
        lower.contains("pan") -> "Alkem Laboratories"
        lower.contains("glycomet") -> "USV Private Limited"
        lower.contains("becosules") -> "Pfizer Limited"
        lower.contains("benadryl") -> "Johnson & Johnson"
        lower.contains("digene") -> "Abbott India"
        lower.contains("volini") -> "Sun Pharma"
        lower.contains("ecosprin") -> "USV Private Limited"
        lower.contains("telma") -> "Glenmark Pharmaceuticals"
        lower.contains("limcee") -> "Abbott India"
        lower.contains("shelcal") -> "Torrent Pharmaceuticals"
        lower.contains("zincovit") -> "Apex Laboratories"
        lower.contains("cheston") -> "Cipla Limited"
        lower.contains("otrivin") -> "Haleon India"
        lower.contains("vicks") -> "Procter & Gamble"
        lower.contains("dettol") -> "Reckitt Benckiser"
        lower.contains("hansaplast") -> "Beiersdorf AG"
        lower.contains("eno") -> "Haleon India"
        lower.contains("triphala") || lower.contains("baidyanath") -> "Shree Baidyanath Ayurveda"
        else -> "CommerceOS Health"
    }
}

fun formatPackSize(packSize: String?): String {
    val p = packSize?.trim() ?: ""
    if (p.isBlank()) return "Strip of 15 Tablets"
    if (p.all { it.isDigit() }) return "Strip of $p Tablets"
    return p
}

fun formatHealthcareSalt(title: String?, rawSalt: String? = null): String {
    val s = rawSalt?.trim()
    if (!s.isNullOrBlank() && !s.equals("null", ignoreCase = true) && !s.equals("undefined", ignoreCase = true)) {
        return s
    }
    val lower = (title ?: "").lowercase()
    return when {
        lower.contains("dolo") -> "Paracetamol (650mg)"
        lower.contains("crocin") -> "Paracetamol (500mg) Fast Action"
        lower.contains("combiflam") -> "Ibuprofen (400mg) + Paracetamol (325mg)"
        lower.contains("para") -> "Paracetamol (500mg)"
        lower.contains("augmentin") || lower.contains("amox") -> "Amoxycillin (500mg) + Clavulanic Acid (125mg)"
        lower.contains("pan 40") || lower.contains("pan-40") || lower.contains("pan-d") -> "Pantoprazole (40mg)"
        lower.contains("glycomet") -> "Metformin Hydrochloride (500mg)"
        lower.contains("becosules") -> "Vitamin B-Complex + Vitamin C + Zinc"
        lower.contains("benadryl") -> "Diphenhydramine (14.08mg) + Ammonium Chloride"
        lower.contains("digene") -> "Magnesium Hydroxide + Aluminium Hydroxide"
        lower.contains("volini") -> "Diclofenac Diethylamine + Menthol Gel"
        lower.contains("ecosprin") -> "Aspirin / Acetylsalicylic Acid (75mg)"
        lower.contains("telma") -> "Telmisartan (40mg)"
        lower.contains("limcee") -> "Vitamin C (500mg) Chewable Tablets"
        lower.contains("shelcal") -> "Calcium (500mg) + Vitamin D3 (250 IU)"
        lower.contains("zincovit") -> "Multivitamin + Multimineral with Zinc"
        lower.contains("cheston") -> "Cetirizine (5mg) + Paracetamol (325mg) + Phenylephrine (10mg)"
        lower.contains("otrivin") -> "Xylometazoline Hydrochloride (0.1% w/v)"
        lower.contains("vicks") -> "Camphor + Menthol + Eucalyptus Oil"
        lower.contains("electral") -> "Oral Rehydration Salts (WHO Formula)"
        lower.contains("dettol") -> "Chloroxylenol (4.8% w/v)"
        lower.contains("hansaplast") -> "Antiseptic Medicated Dressing Strip"
        lower.contains("eno") -> "Svarjiksara (5g) + Nimbukamlam (5g)"
        lower.contains("triphala") -> "Amla + Haritaki + Bibhitaki Extract"
        lower.contains("accu-chek") || lower.contains("accuchek") -> "Blood Glucose Test Strips (FAD-GDH)"
        lower.contains("protinex") -> "Hydrolysed Peanut Protein + Minerals"
        else -> "Active Pharmaceutical Ingredient (IP Grade)"
    }
}

@Composable
private fun CompactProductCard(
    model: ProductCardModel,
    onAddToCart: () -> Unit,
    isReorderCard: Boolean,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    onWishlistToggle: ((String) -> Unit)?,
    contentScale: ContentScale,
    modifier: Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier.padding(Spacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = contentScale,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(96.dp)
                )
                if (model.discountPercent > 0) {
                    Surface(
                        color = Color(0xFF16A34A),
                        shape = RoundedCornerShape(topStart = 10.dp, bottomEnd = 6.dp),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            text = "${model.discountPercent}% OFF",
                            color = Color.White,
                            fontSize = 8.5.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                if (onWishlistToggle != null) {
                    WishlistHeartButton(
                        isWishlisted = model.isWishlisted,
                        onClick = { onWishlistToggle(model.id) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.xxs)
                    )
                }
            }

            Spacer(modifier = Modifier.width(Spacing.sm))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        formatHealthcareTitle(model.name),
                        style = CommerceTypography.ProductTitle,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (model.rxRequired) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(3.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                "Rx",
                                color = Color(0xFFDC2626),
                                fontSize = 8.5.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                            )
                        }
                    }
                    if (model.coldChain) {
                        Surface(
                            color = Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(3.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFBAE6FD))
                        ) {
                            Text(
                                "❄️",
                                fontSize = 8.sp,
                                modifier = Modifier.padding(horizontal = 2.dp, vertical = 1.dp)
                            )
                        }
                    }
                }

                // Active Chemical Salt / Composition (Prominently displayed)
                val salt = formatHealthcareSalt(model.name, model.saltComposition)
                if (!salt.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Contains: $salt",
                        fontSize = 10.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF0284C7),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Pack Size and Manufacturer
                val manufacturerFormatted = formatHealthcareBrand(model.brandName, model.name, model.manufacturer)
                val packFormatted = formatPackSize(model.packSize)
                Text(
                    text = "$packFormatted • By $manufacturerFormatted",
                    fontSize = 10.sp,
                    fontFamily = PlusJakartaSans,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(Spacing.xs))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(
                            MoneyFormatter.format(model.sellingPrice),
                            fontSize = 14.5.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A)
                        )
                        if (model.discountPercent > 0 && model.price > model.sellingPrice) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    MoneyFormatter.format(model.price),
                                    fontSize = 10.sp,
                                    fontFamily = PlusJakartaSans,
                                    color = Color(0xFF94A3B8),
                                    textDecoration = TextDecoration.LineThrough
                                )
                                Spacer(modifier = Modifier.width(3.dp))
                                Text(
                                    "${model.discountPercent}% OFF",
                                    fontSize = 9.sp,
                                    fontFamily = PlusJakartaSans,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF16A34A)
                                )
                            }
                        }
                    }

                    CardAddToCartButton(
                        model = model,
                        isReorderCard = isReorderCard,
                        onAddToCart = onAddToCart,
                        quantity = quantity,
                        onQuantityChange = onQuantityChange,
                        modifier = Modifier.width(68.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun GridProductCard(
    model: ProductCardModel,
    onAddToCart: () -> Unit,
    isReorderCard: Boolean,
    quantity: Int,
    onQuantityChange: (Int) -> Unit,
    onWishlistToggle: ((String) -> Unit)?,
    contentScale: ContentScale,
    modifier: Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            // 1. Image Box with authentic pharma tags
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(125.dp)
            ) {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = contentScale,
                    shape = RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )

                // Top badges row: Discount + Rx badge + Cold Chain
                Row(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(start = 6.dp, top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (model.discountPercent > 0) {
                        Surface(
                            color = Color(0xFF16A34A),
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "${model.discountPercent}% OFF",
                                color = Color.White,
                                fontSize = 9.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.ExtraBold,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (model.rxRequired) {
                        Surface(
                            color = Color(0xFFFEE2E2),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFFCA5A5))
                        ) {
                            Text(
                                text = "Rx",
                                color = Color(0xFFDC2626),
                                fontSize = 9.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                            )
                        }
                    }
                    if (model.coldChain) {
                        Surface(
                            color = Color(0xFFE0F2FE),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFBAE6FD))
                        ) {
                            Text(
                                text = "❄️",
                                fontSize = 8.5.sp,
                                modifier = Modifier.padding(horizontal = 3.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                if (onWishlistToggle != null) {
                    WishlistHeartButton(
                        isWishlisted = model.isWishlisted,
                        onClick = { onWishlistToggle(model.id) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(4.dp)
                    )
                }
            }

            // 2. Comprehensive Medicine Details Section
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    // Medicine Title
                    Text(
                        text = formatHealthcareTitle(model.name),
                        style = CommerceTypography.ProductTitle,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 2,
                        minLines = 2,
                        lineHeight = 15.sp,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(3.dp))

                    // Active Chemical Salt / Composition (Prominently displayed)
                    val salt = formatHealthcareSalt(model.name, model.saltComposition)
                    if (!salt.isNullOrBlank()) {
                        Surface(
                            color = Color(0xFFF0F9FF),
                            shape = RoundedCornerShape(4.dp),
                            border = BorderStroke(0.5.dp, Color(0xFFBAE6FD)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = salt,
                                fontSize = 9.5.sp,
                                fontFamily = PlusJakartaSans,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF0284C7),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(3.dp))
                    }

                    // Pack Size & Presentation (e.g., Strip of 15 Tablets)
                    val packFormatted = formatPackSize(model.packSize)
                    Text(
                        text = packFormatted,
                        fontSize = 10.5.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF475569),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Manufacturer / Marketer (e.g., By Micro Labs Ltd)
                    val manufacturerFormatted = formatHealthcareBrand(model.brandName, model.name, model.manufacturer)
                    Text(
                        text = "By $manufacturerFormatted",
                        fontSize = 9.5.sp,
                        fontFamily = PlusJakartaSans,
                        fontWeight = FontWeight.Normal,
                        color = Color(0xFF94A3B8),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // 3. Price & Add CTA Row
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 4.dp),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            MoneyFormatter.format(model.sellingPrice),
                            fontSize = 13.5.sp,
                            fontFamily = PlusJakartaSans,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF0F172A),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (model.discountPercent > 0 && model.price > model.sellingPrice) {
                            Text(
                                MoneyFormatter.format(model.price),
                                fontSize = 10.sp,
                                fontFamily = PlusJakartaSans,
                                color = Color(0xFF94A3B8),
                                textDecoration = TextDecoration.LineThrough,
                                maxLines = 1
                            )
                        }
                    }
                    CardAddToCartButton(
                        model = model,
                        isReorderCard = isReorderCard,
                        onAddToCart = onAddToCart,
                        quantity = quantity,
                        onQuantityChange = onQuantityChange,
                        modifier = Modifier.width(68.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun WishlistHeartButton(
    isWishlisted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (isWishlisted) 1.15f else 1.0f,
        animationSpec = androidx.compose.animation.core.spring(
            dampingRatio = androidx.compose.animation.core.Spring.DampingRatioMediumBouncy,
            stiffness = androidx.compose.animation.core.Spring.StiffnessLow
        ),
        label = "WishlistHeartScale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = androidx.compose.runtime.remember { androidx.compose.foundation.interaction.MutableInteractionSource() },
                indication = null,
                onClick = onClick
            ),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            color = CommerceColors.Surface.copy(alpha = 0.92f),
            shape = CircleShape,
            shadowElevation = CommerceElevation.Raised,
            modifier = Modifier
                .size(32.dp)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                }
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isWishlisted) "Remove from wishlist" else "Add to wishlist",
                    tint = if (isWishlisted) CommerceColors.Danger else CommerceColors.TextMuted,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun DiscountBadge(percent: Int, modifier: Modifier = Modifier) {
    if (percent > 0) {
        Surface(
            color = Color(0xFF16A34A),
            shape = RoundedCornerShape(4.dp),
            modifier = modifier
        ) {
            Text(
                "$percent% OFF",
                color = Color.White,
                fontSize = 9.sp,
                fontWeight = FontWeight.ExtraBold,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun RatingAndDeliveryRow(model: ProductCardModel) {
    val eta = model.etaLabel
    if (!eta.isNullOrBlank()) {
        Surface(
            color = CommerceColors.SpeedYellow.copy(alpha = 0.22f),
            shape = RoundedCornerShape(Radius.Micro)
        ) {
            Text(
                eta.uppercase(),
                style = CommerceTypography.Meta,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.SushiInk,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun StatusChips(model: ProductCardModel) {
    if (model.rxRequired || model.coldChain) {
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
            if (model.rxRequired) {
                Surface(
                    color = CommerceColors.RxSoft,
                    shape = RoundedCornerShape(Radius.Micro)
                ) {
                    Text(
                        "Rx Required",
                        style = CommerceTypography.Meta,
                        color = CommerceColors.Rx,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                    )
                }
            }
            if (model.coldChain) {
                Surface(
                    color = CommerceColors.ColdChainSoft,
                    shape = RoundedCornerShape(Radius.Micro)
                ) {
                    Text(
                        "Cold Chain",
                        style = CommerceTypography.Meta,
                        color = CommerceColors.ColdChain,
                        modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PriceBlock(model: ProductCardModel) {
    val outOfStock = !model.inStock || model.stockCount == 0
    val discountPercent = model.discountPercent
    val lowStock = (model.stockCount ?: Int.MAX_VALUE) in 1..3

    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                MoneyFormatter.format(model.sellingPrice),
                fontSize = 15.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF0F172A)
            )
            if (discountPercent > 0) {
                Text(
                    MoneyFormatter.format(model.price),
                    fontSize = 11.sp,
                    color = Color(0xFF94A3B8),
                    textDecoration = TextDecoration.LineThrough
                )
                Text(
                    "$discountPercent% OFF",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534)
                )
            }
        }

        when {
            outOfStock ->
                Text(
                    "Out of stock",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFDC2626)
                )

            lowStock ->
                Text(
                    "Only ${model.stockCount} left",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFFD97706)
                )

            discountPercent > 0 ->
                Text(
                    "Save ${MoneyFormatter.format(model.price - model.sellingPrice)}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF16A34A)
                )
        }
    }
}

@Composable
private fun CardAddToCartButton(
    model: ProductCardModel,
    isReorderCard: Boolean,
    onAddToCart: () -> Unit,
    quantity: Int = 0,
    onQuantityChange: (Int) -> Unit = {},
    modifier: Modifier = Modifier
) {
    val outOfStock = !model.inStock || model.stockCount == 0
    val brandTeal = Color(0xFF0E675E) // Deep Medical Teal unified brand palette

    if (isReorderCard || outOfStock || quantity <= 0) {
        Surface(
            color = if (outOfStock) Color(0xFFF8FAFC) else Color.White,
            shape = RoundedCornerShape(6.dp),
            border = if (!outOfStock) androidx.compose.foundation.BorderStroke(1.2.dp, brandTeal) else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 0.dp,
            modifier = modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
                .then(
                    if (!outOfStock) Modifier.clickable(onClick = onAddToCart) else Modifier
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 2.dp)
            ) {
                if (isReorderCard) {
                    Text("REORDER", color = brandTeal, fontSize = 11.sp, fontWeight = FontWeight.Black)
                } else if (outOfStock) {
                    Text("OOS", color = CommerceColors.TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            "ADD",
                            color = brandTeal,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Black,
                            letterSpacing = 0.5.sp
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Add to cart",
                            tint = brandTeal,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            color = brandTeal,
            shape = RoundedCornerShape(6.dp),
            shadowElevation = 0.dp,
            modifier = modifier
                .height(30.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 1.dp)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onQuantityChange(quantity - 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Decrease quantity",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Text(
                    text = "$quantity",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 2.dp)
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onQuantityChange(quantity + 1) },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Increase quantity",
                        tint = Color.White,
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }
    }
}
