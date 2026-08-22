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
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Flat),
        modifier = modifier
    ) {
        Row(modifier = Modifier.padding(Spacing.sm)) {
            Box {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = contentScale,
                    shape = RoundedCornerShape(Radius.ImageTile),
                    modifier = Modifier.size(92.dp)
                )
                if (model.discountPercent > 0) {
                    DiscountBadge(
                        percent = model.discountPercent,
                        modifier = Modifier.align(Alignment.TopStart).padding(Spacing.xxs)
                    )
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
                    model.name,
                    style = CommerceTypography.ProductTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (model.brandName.isNotBlank() || model.packSize.isNotBlank()) {
                    Text(
                        listOfNotNull(model.brandName.takeIf { it.isNotBlank() }, model.packSize.takeIf { it.isNotBlank() }).joinToString(" • "),
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                RatingAndDeliveryRow(model = model)

                StatusChips(model = model)

                Spacer(modifier = Modifier.height(Spacing.sm))
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
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Flat),
        modifier = modifier
    ) {
        Column {
            Box {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = contentScale,
                    shape = RoundedCornerShape(Radius.ImageTile),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .padding(Spacing.sm)
                )
                if (model.discountPercent > 0) {
                    DiscountBadge(
                        percent = model.discountPercent,
                        modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm)
                    )
                }
                if (onWishlistToggle != null) {
                    WishlistHeartButton(
                        isWishlisted = model.isWishlisted,
                        onClick = { onWishlistToggle(model.id) },
                        modifier = Modifier.align(Alignment.TopEnd).padding(Spacing.sm)
                    )
                }
            }

            Column(modifier = Modifier.padding(start = 10.dp, end = 10.dp, bottom = 10.dp)) {
                Text(
                    model.name,
                    style = CommerceTypography.ProductTitle,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (model.brandName.isNotBlank() || model.packSize.isNotBlank()) {
                    Text(
                        listOfNotNull(model.brandName.takeIf { it.isNotBlank() }, model.packSize.takeIf { it.isNotBlank() }).joinToString(" • "),
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Spacer(modifier = Modifier.height(Spacing.xs))
                RatingAndDeliveryRow(model = model)

                StatusChips(model = model)

                Spacer(modifier = Modifier.height(Spacing.sm))
                PriceBlock(model = model)

                Spacer(modifier = Modifier.height(Spacing.sm))
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
            color = CommerceColors.Savings,
            shape = RoundedCornerShape(Radius.Micro),
            modifier = modifier
        ) {
            Text(
                "$percent% OFF",
                color = CommerceColors.OnPrimary,
                style = CommerceTypography.Meta,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
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
            horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
        ) {
            Text(
                MoneyFormatter.format(model.sellingPrice),
                style = CommerceTypography.Title,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.TextPrimary
            )
            if (discountPercent > 0) {
                Text(
                    MoneyFormatter.format(model.price),
                    style = CommerceTypography.Meta,
                    color = CommerceColors.TextMuted,
                    textDecoration = TextDecoration.LineThrough
                )
            }
        }

        when {
            outOfStock ->
                Text(
                    "Out of stock",
                    style = CommerceTypography.Meta,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.Danger
                )

            lowStock ->
                Text(
                    "Only ${model.stockCount} left",
                    style = CommerceTypography.Meta,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.Warning
                )

            discountPercent > 0 ->
                Text(
                    "You save ${MoneyFormatter.format(model.price - model.sellingPrice)}",
                    style = CommerceTypography.Meta,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.Savings
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
                    if (quantity <= 1) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Remove item from cart",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                    } else {
                        Text("−", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Black)
                    }
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
