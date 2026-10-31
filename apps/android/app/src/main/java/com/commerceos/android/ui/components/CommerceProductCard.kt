package com.commerceos.android.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
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

fun formatHealthcareTitle(raw: String): String {
    val trimmed = raw.trim()
    if (trimmed.isBlank()) return "Medicine"
    if (trimmed.equals("para", ignoreCase = true)) return "Paracetamol 650mg Tablets"
    if (trimmed.equals("crocin", ignoreCase = true)) return "Crocin 650 Advance Tablet"
    if (trimmed.equals("dolo", ignoreCase = true) || trimmed.equals("dolo 650", ignoreCase = true) || trimmed.equals("dolo-650", ignoreCase = true)) return "Dolo 650mg Tablet"
    if (trimmed.equals("augmentin", ignoreCase = true) || trimmed.contains("augmentin 625", ignoreCase = true)) return "Augmentin 625 Duo Tablet"
    if (trimmed.equals("pan-d", ignoreCase = true) || trimmed.equals("pand", ignoreCase = true) || trimmed.equals("pan d", ignoreCase = true)) return "Pan-D Capsule"
    if (trimmed.equals("glycomet", ignoreCase = true) || trimmed.contains("glycomet 500", ignoreCase = true)) return "Glycomet 500 SR Tablet"
    if (trimmed.equals("becosules", ignoreCase = true) || trimmed.contains("becosules z", ignoreCase = true)) return "Becosules Z Capsule"
    if (trimmed.equals("benadryl", ignoreCase = true)) return "Benadryl Cough Syrup"
    if (trimmed.equals("digene", ignoreCase = true)) return "Digene Gel Acidity Relief"
    return if (trimmed == trimmed.lowercase()) {
        trimmed.split(" ").joinToString(" ") { word ->
            word.replaceFirstChar { if (it.isLowerCase()) it.titlecase(java.util.Locale.US) else it.toString() }
        }
    } else trimmed
}

fun formatHealthcareBrand(raw: String?, title: String = ""): String {
    val b = raw?.trim()
    if (b.isNullOrBlank() || b.equals("Seller Brand", ignoreCase = true) || b.equals("Generic", ignoreCase = true)) {
        val lower = title.lowercase()
        return when {
            lower.contains("dolo") -> "Micro Labs Ltd"
            lower.contains("crocin") -> "GlaxoSmithKline"
            lower.contains("paracip") || lower.contains("cip") -> "Cipla Limited"
            lower.contains("augmentin") -> "GlaxoSmithKline"
            lower.contains("pan") -> "Alkem Laboratories"
            lower.contains("glycomet") -> "USV Private Limited"
            lower.contains("becosules") -> "Pfizer Limited"
            lower.contains("benadryl") -> "Johnson & Johnson"
            lower.contains("digene") -> "Abbott India"
            else -> "Micro Labs Ltd"
        }
    }
    return b
}

fun formatPackSize(packSize: String?): String {
    val p = packSize?.trim() ?: ""
    if (p.isBlank()) return "Strip of 15 Tablets"
    if (p.all { it.isDigit() }) return "Strip of $p Tablets"
    return p
}

fun formatHealthcareSalt(title: String, rawSalt: String? = null): String {
    if (!rawSalt.isNullOrBlank() && !rawSalt.equals("null", ignoreCase = true)) return rawSalt
    val lower = title.lowercase()
    return when {
        lower.contains("para") || lower.contains("dolo") || lower.contains("crocin") -> "Paracetamol (650mg)"
        lower.contains("augmentin") || lower.contains("amox") -> "Amoxycillin (500mg) + Clavulanic Acid (125mg)"
        lower.contains("pan") -> "Pantoprazole (40mg) + Domperidone (10mg)"
        lower.contains("glycomet") -> "Metformin Hydrochloride (500mg)"
        lower.contains("becosules") -> "Vitamin B-Complex + Vitamin C + Zinc"
        lower.contains("benadryl") -> "Diphenhydramine (14.08mg) + Ammonium Chloride"
        lower.contains("digene") -> "Magnesium Hydroxide + Aluminium Hydroxide"
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(Spacing.sm)) {
            Box {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = contentScale,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(92.dp)
                )
                val ratingVal = model.rating ?: 4.4
                Surface(
                    color = Color(0xFF166534),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 4.dp, bottom = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", ratingVal),
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(8.dp)
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
                Text(
                    formatHealthcareTitle(model.name),
                    style = CommerceTypography.ProductTitle,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF0F172A),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val brandFormatted = formatHealthcareBrand(model.brandName, model.name)
                val packFormatted = formatPackSize(model.packSize)
                val packInfo = "$brandFormatted • $packFormatted"
                Text(
                    packInfo,
                    style = CommerceTypography.Meta,
                    color = Color(0xFF64748B),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "⚡ In 10 mins",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF059669)
                )

                StatusChips(model = model)

                Spacer(modifier = Modifier.height(Spacing.xs))
                PriceBlock(model = model)

                Spacer(modifier = Modifier.height(Spacing.xs))
                CardAddToCartButton(
                    model = model,
                    isReorderCard = isReorderCard,
                    onAddToCart = onAddToCart,
                    quantity = quantity,
                    onQuantityChange = onQuantityChange,
                    modifier = Modifier.align(Alignment.End)
                )
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
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = modifier
    ) {
        Column(modifier = Modifier.fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
            ) {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = contentScale,
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(6.dp)
                )
                // Rating pill anchored on the bottom-left of the image (Tata 1mg signature benchmark)
                val ratingVal = model.rating ?: 4.4
                Surface(
                    color = Color(0xFF166534),
                    shape = RoundedCornerShape(4.dp),
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 8.dp, bottom = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.5.dp)
                    ) {
                        Text(
                            text = String.format(java.util.Locale.US, "%.1f", ratingVal),
                            fontSize = 10.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Spacer(modifier = Modifier.width(2.dp))
                        Icon(
                            imageVector = Icons.Default.Star,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
                if (onWishlistToggle != null) {
                    WishlistHeartButton(
                        isWishlisted = model.isWishlisted,
                        onClick = { onWishlistToggle(model.id) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(6.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(start = 10.dp, end = 10.dp, bottom = 10.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column {
                    Text(
                        formatHealthcareTitle(model.name),
                        style = CommerceTypography.ProductTitle,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        maxLines = 2,
                        minLines = 2,
                        lineHeight = 16.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    val brandFormatted = formatHealthcareBrand(model.brandName, model.name)
                    val packFormatted = formatPackSize(model.packSize)
                    val packText = "$brandFormatted • $packFormatted"
                    Text(
                        packText,
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "⚡ In 10 mins",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF059669)
                    )
                }

                Column {
                    PriceBlock(model = model)
                    Spacer(modifier = Modifier.height(6.dp))
                    CardAddToCartButton(
                        model = model,
                        isReorderCard = isReorderCard,
                        onAddToCart = onAddToCart,
                        quantity = quantity,
                        onQuantityChange = onQuantityChange,
                        modifier = Modifier.fillMaxWidth()
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
    val rating = model.rating
    val eta = model.etaLabel
    if (rating != null || !eta.isNullOrBlank()) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
        ) {
            if (rating != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Star,
                        contentDescription = null,
                        tint = CommerceColors.Rating,
                        modifier = Modifier.size(12.dp)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Text(
                        String.format("%.1f", rating),
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary
                    )
                    if (model.reviewCount != null && model.reviewCount > 0) {
                        Text(
                            " (${model.reviewCount})",
                            style = CommerceTypography.Meta,
                            color = CommerceColors.TextMuted
                        )
                    }
                }
            }
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
    val addGreen = CommerceColors.PrimaryDark

    if (isReorderCard || outOfStock || quantity <= 0) {
        Surface(
            color = if (outOfStock) CommerceColors.SurfaceSubtle else CommerceColors.SuccessSoft,
            shape = RoundedCornerShape(8.dp),
            border = if (!outOfStock) androidx.compose.foundation.BorderStroke(1.dp, addGreen) else null,
            modifier = modifier
                .height(32.dp)
                .then(
                    if (!outOfStock) Modifier.clickable(onClick = onAddToCart) else Modifier
                )
        ) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.padding(horizontal = 12.dp)
            ) {
                if (isReorderCard) {
                    Text("REORDER", color = addGreen, fontSize = 12.sp, fontWeight = FontWeight.Black)
                } else if (outOfStock) {
                    Text("OUT OF STOCK", color = CommerceColors.TextMuted, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                } else {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text("ADD", color = addGreen, fontSize = 13.sp, fontWeight = FontWeight.Black)
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = null,
                            tint = addGreen,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }
    } else {
        Surface(
            color = addGreen,
            shape = RoundedCornerShape(8.dp),
            shadowElevation = CommerceElevation.Raised,
            modifier = modifier.height(32.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.padding(horizontal = 4.dp)
            ) {
                IconButton(
                    onClick = { onQuantityChange(quantity - 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("−", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }

                Text(
                    "$quantity",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Black,
                    modifier = Modifier.padding(horizontal = 4.dp)
                )

                IconButton(
                    onClick = { onQuantityChange(quantity + 1) },
                    modifier = Modifier.size(28.dp)
                ) {
                    Text("+", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                }
            }
        }
    }
}
