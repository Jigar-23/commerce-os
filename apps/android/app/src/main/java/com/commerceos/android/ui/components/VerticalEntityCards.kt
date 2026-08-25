package com.commerceos.android.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.ProductCardModel
import com.commerceos.android.model.toProductCardModel
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceElevation
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.util.MoneyFormatter

/**
 * Bespoke Fashion Product Card (Myntra-grade visual minimalism).
 * Tall 4:5 image ratio, prominent brand title, wishlist heart, zero medical/fulfillment clutter.
 */
@Composable
fun FashionProductCard(
    model: ProductCardModel,
    onSelect: () -> Unit,
    onWishlistToggle: (String) -> Unit,
    isWishlisted: Boolean = false,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
        modifier = modifier.clickable(onClick = onSelect)
    ) {
        Column {
            Box {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(topStart = Radius.Card, topEnd = Radius.Card),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(0.8f) // Tall 4:5 fashion aspect ratio
                )
                if (model.discountPercent > 0) {
                    Surface(
                        color = CommerceColors.Discount,
                        shape = RoundedCornerShape(Radius.Micro),
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .padding(Spacing.sm)
                    ) {
                        Text(
                            "${model.discountPercent}% OFF",
                            color = CommerceColors.OnPrimary,
                            style = CommerceTypography.Meta,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                        )
                    }
                }
                FashionWishlistHeartButton(
                    isWishlisted = isWishlisted,
                    onClick = { onWishlistToggle(model.id) },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(Spacing.xs)
                )
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                if (model.brandName.isNotBlank()) {
                    Text(
                        model.brandName.uppercase(),
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    model.name,
                    style = CommerceTypography.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    Text(
                        MoneyFormatter.format(model.sellingPrice),
                        style = CommerceTypography.Label,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary
                    )
                    if (model.discountPercent > 0) {
                        Text(
                            MoneyFormatter.format(model.price),
                            style = CommerceTypography.Meta,
                            color = CommerceColors.TextMuted,
                            textDecoration = TextDecoration.LineThrough
                        )
                    }
                }
            }
        }
    }
}

/**
 * Bespoke Electronics Product Card (Amazon-grade spec & warranty focus).
 * Clean fit image, brand title, spec highlights, star rating, warranty signal & price.
 */
@Composable
fun ElectronicsProductCard(
    model: ProductCardModel,
    onSelect: () -> Unit,
    onAddToCart: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
        modifier = modifier.clickable(onClick = onSelect)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .background(CommerceColors.SurfaceSubtle)
            ) {
                ProductImage(
                    imageUrl = model.image,
                    contentDescription = model.name,
                    contentScale = ContentScale.Fit,
                    shape = RoundedCornerShape(topStart = Radius.Card, topEnd = Radius.Card),
                    modifier = Modifier.fillMaxSize().padding(Spacing.sm)
                )
                if (model.discountPercent > 0) {
                    Surface(
                        color = CommerceColors.Savings,
                        shape = RoundedCornerShape(Radius.Micro),
                        modifier = Modifier.align(Alignment.TopStart).padding(Spacing.sm)
                    ) {
                        Text(
                            "${model.discountPercent}% OFF",
                            color = CommerceColors.OnPrimary,
                            style = CommerceTypography.Meta,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                if (model.brandName.isNotBlank()) {
                    Text(
                        model.brandName,
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.Primary,
                        maxLines = 1
                    )
                }
                Text(
                    model.name,
                    style = CommerceTypography.BodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.TextPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(Spacing.xs))

                // Rating & SLA row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(Spacing.xs)
                ) {
                    if (model.rating != null) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = CommerceColors.Rating, modifier = Modifier.size(12.dp))
                        Text(String.format("%.1f", model.rating), style = CommerceTypography.Meta, fontWeight = FontWeight.Bold)
                        if ((model.reviewCount ?: 0) > 0) {
                            Text("(${model.reviewCount})", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                        }
                    }
                    if (!model.etaLabel.isNullOrBlank()) {
                        Text("⚡ ${model.etaLabel}", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Delivery)
                    }
                }

                Spacer(modifier = Modifier.height(Spacing.sm))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column {
                        Text(
                            MoneyFormatter.format(model.sellingPrice),
                            style = CommerceTypography.Title,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.TextPrimary
                        )
                        if (model.discountPercent > 0) {
                            Text(
                                MoneyFormatter.format(model.price),
                                style = CommerceTypography.Meta,
                                color = CommerceColors.TextMuted,
                                textDecoration = TextDecoration.LineThrough
                            )
                        }
                    }
                    Button(
                        onClick = onAddToCart,
                        colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                        shape = RoundedCornerShape(Radius.Button),
                        contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                        modifier = Modifier.height(32.dp)
                    ) {
                        Text("ADD", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * Zomato-grade Restaurant Card.
 * Restaurant image, cuisine tags, star rating, delivery time, price for two & promo offer.
 */
@Composable
fun RestaurantCard(
    name: String,
    imageUrl: String?,
    cuisine: String? = null,
    rating: Double? = null,
    deliveryEta: String? = null,
    priceForTwo: String? = null,
    offerText: String? = null,
    onSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
        modifier = modifier.clickable(onClick = onSelect)
    ) {
        Column {
            Box {
                ProductImage(
                    imageUrl = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(topStart = Radius.Card, topEnd = Radius.Card),
                    modifier = Modifier.fillMaxWidth().aspectRatio(1.4f)
                )
                if (!offerText.isNullOrBlank()) {
                    Surface(
                        color = CommerceColors.Discount,
                        shape = RoundedCornerShape(topEnd = Radius.Micro, bottomEnd = Radius.Micro),
                        modifier = Modifier.align(Alignment.BottomStart).padding(bottom = Spacing.sm)
                    ) {
                        Text(
                            offerText,
                            color = CommerceColors.OnPrimary,
                            style = CommerceTypography.Meta,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 2.dp)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(Spacing.md)) {
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        name,
                        style = CommerceTypography.BodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    if (rating != null && rating > 0) {
                        Surface(
                            color = CommerceColors.Success,
                            shape = RoundedCornerShape(Radius.Micro)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = Spacing.xs, vertical = 2.dp)
                            ) {
                                Text(String.format("%.1f", rating), style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary)
                                Spacer(modifier = Modifier.width(2.dp))
                                Icon(Icons.Default.Star, contentDescription = null, tint = CommerceColors.OnPrimary, modifier = Modifier.size(10.dp))
                            }
                        }
                    }
                }
                if (!cuisine.isNullOrBlank()) {
                    Text(
                        cuisine,
                        style = CommerceTypography.Caption,
                        color = CommerceColors.TextMuted,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (!deliveryEta.isNullOrBlank()) {
                        Text(deliveryEta, style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Success)
                    } else {
                        Spacer(modifier = Modifier.width(1.dp))
                    }
                    if (!priceForTwo.isNullOrBlank()) {
                        Text(priceForTwo, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                    }
                }
            }
        }
    }
}

/**
 * Service Booking Card for Home Services, Repairs, and Care.
 * Provider info, rating, starting price, slot duration and "Book" CTA.
 */
@Composable
fun ServiceCard(
    title: String,
    providerName: String? = null,
    imageUrl: String?,
    rating: Double? = null,
    startingPrice: Double,
    duration: String? = null,
    onBook: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(Radius.Card),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
        modifier = modifier.clickable(onClick = onBook)
    ) {
        Row(modifier = Modifier.padding(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(80.dp)
            )
            Spacer(modifier = Modifier.width(Spacing.md))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                if (!providerName.isNullOrBlank()) {
                    Text(providerName, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    if (rating != null && rating > 0) {
                        Icon(Icons.Default.Star, contentDescription = null, tint = CommerceColors.Rating, modifier = Modifier.size(12.dp))
                        Text(String.format("%.1f", rating), style = CommerceTypography.Meta, fontWeight = FontWeight.Bold)
                    }
                    if (!duration.isNullOrBlank()) {
                        Text("• $duration", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                    }
                }
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text("Starts at ${MoneyFormatter.format(startingPrice)}", style = CommerceTypography.Label, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
            }
            Spacer(modifier = Modifier.width(Spacing.xs))
            Button(
                onClick = onBook,
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                shape = RoundedCornerShape(Radius.Button),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                modifier = Modifier.height(36.dp)
            ) {
                Text("Book", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun FashionWishlistHeartButton(
    isWishlisted: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scale by animateFloatAsState(
        targetValue = if (isWishlisted) 1.15f else 1.0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "FashionWishlistScale"
    )

    Box(
        modifier = modifier
            .size(48.dp)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
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

/**
 * Universal Entity Renderer powering Home, Search, and Vertical Hubs.
 * Maps CommerceEntity instances to specialized visual presentation components without silent fallback to Grocery.
 */
@Composable
fun CommerceEntityRenderer(
    entity: com.commerceos.android.model.CommerceEntity,
    onEntityClick: (com.commerceos.android.model.CommerceEntity) -> Unit,
    onAddToCart: ((com.commerceos.android.model.CommerceProduct) -> Unit)? = null,
    onUpdateQuantity: ((String, Int) -> Unit)? = null,
    cartQuantityMap: Map<String, Int> = emptyMap(),
    modifier: Modifier = Modifier
) {
    when (entity) {
        is com.commerceos.android.model.CommerceEntity.ProductItem -> {
            val prod = entity.product
            val cardModel = prod.toProductCardModel()
            val qty = cartQuantityMap[prod.sku] ?: cartQuantityMap[prod.id] ?: 0
            val verticalKey = prod.verticalId?.lowercase() ?: "general"
            when (verticalKey) {
                "fashion", "style", "apparel" -> FashionProductCard(
                    model = cardModel,
                    onSelect = { onEntityClick(entity) },
                    onWishlistToggle = {},
                    modifier = modifier
                )
                "electronics", "tech" -> ElectronicsProductCard(
                    model = cardModel,
                    onSelect = { onEntityClick(entity) },
                    onAddToCart = { onAddToCart?.invoke(prod) },
                    modifier = modifier
                )
                "health", "pharmacy" -> PharmacyProductCard(
                    model = cardModel,
                    onSelect = { onEntityClick(entity) },
                    onAddToCart = { onAddToCart?.invoke(prod) },
                    quantity = qty,
                    onQuantityChange = { newQty -> onUpdateQuantity?.invoke(prod.sku, newQty) },
                    modifier = modifier
                )
                "grocery", "fresh" -> GroceryProductCard(
                    model = cardModel,
                    onSelect = { onEntityClick(entity) },
                    onAddToCart = { onAddToCart?.invoke(prod) },
                    quantity = qty,
                    onQuantityChange = { newQty -> onUpdateQuantity?.invoke(prod.sku, newQty) },
                    modifier = modifier
                )
                else -> CommerceProductCard(
                    product = prod,
                    onSelect = { onEntityClick(entity) },
                    onAddToCart = { onAddToCart?.invoke(prod) },
                    quantity = qty,
                    onQuantityChange = { newQty -> onUpdateQuantity?.invoke(prod.sku, newQty) },
                    variant = ProductCardVariant.Grid,
                    modifier = modifier
                )
            }
        }
        is com.commerceos.android.model.CommerceEntity.RestaurantItem -> {
            RestaurantCard(
                name = entity.name,
                imageUrl = entity.imageUrl,
                cuisine = entity.cuisine ?: "Cuisine",
                rating = entity.rating,
                deliveryEta = entity.deliveryEta,
                priceForTwo = entity.priceForTwo ?: "₹500 for two",
                offerText = entity.offerText,
                onSelect = { onEntityClick(entity) },
                modifier = modifier
            )
        }
        is com.commerceos.android.model.CommerceEntity.ServiceItem -> {
            ServiceCard(
                title = entity.title,
                providerName = entity.providerName ?: "Verified Professional",
                imageUrl = entity.imageUrl,
                rating = entity.rating,
                startingPrice = entity.startingPrice,
                duration = entity.duration ?: "1 hr",
                onBook = { onEntityClick(entity) },
                modifier = modifier
            )
        }
        is com.commerceos.android.model.CommerceEntity.DishItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.sm)) {
                    ProductImage(imageUrl = entity.imageUrl, contentDescription = entity.name, modifier = Modifier.fillMaxWidth().height(90.dp))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(entity.name, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                    Text(MoneyFormatter.format(entity.price), style = CommerceTypography.Label, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.StoreItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("🏪 ${entity.name}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                    if (!entity.address.isNullOrBlank()) {
                        Text(entity.address, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                    }
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.CollectionItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("📦 ${entity.title}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                    if (!entity.description.isNullOrBlank()) {
                        Text(entity.description, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                    }
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.CampaignItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.HeroDark),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(entity.badge ?: "CAMPAIGN", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                    Text(entity.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.OfferItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("🏷️ ${entity.title}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Discount, maxLines = 1)
                    if (!entity.discountText.isNullOrBlank()) {
                        Text(entity.discountText, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                    }
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.ProviderItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = CommerceElevation.Raised),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("👤 ${entity.name}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.PrescriptionItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.VerificationSoft),
                shape = RoundedCornerShape(Radius.Card),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("📄 Prescription #${entity.prescription.id.take(8)}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.Verification)
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.BookingAppointmentItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("📅 ${entity.serviceName}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text("${entity.providerName} • ${entity.dateTimeText}", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.MembershipSubscriptionItem -> {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("⭐ ${entity.planTitle}", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text("${entity.priceFormatted} / ${entity.billingCycle}", style = CommerceTypography.Meta, color = CommerceColors.Primary)
                }
            }
        }
        is com.commerceos.android.model.CommerceEntity.UnknownEntity -> {
            // SAFE GENERIC FALLBACK CARD
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text(entity.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                    Text(entity.subtitle ?: "Type: ${entity.rawType}", style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
                }
            }
        }
        else -> {
            // SAFE GENERIC FALLBACK FOR ANY UNHANDLED ENTITY
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
                modifier = modifier.clickable { onEntityClick(entity) }
            ) {
                Column(modifier = Modifier.padding(Spacing.md)) {
                    Text("Commerce Entity", style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                }
            }
        }
    }
}
