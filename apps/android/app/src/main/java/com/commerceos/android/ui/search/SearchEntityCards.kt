package com.commerceos.android.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.SearchEntityType
import com.commerceos.android.model.SearchResult
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.util.MoneyFormatter

/**
 * Universal Search Entity Renderer powering Commerce OS search result dispatch.
 * Renders bespoke search cards per SearchEntityType (Product, Restaurant, Service, Store, Brand, etc.).
 */
@Composable
fun SearchEntityRenderer(
    result: SearchResult,
    onClick: () -> Unit,
    onWishlistToggle: ((String) -> Unit)? = null
) {
    when (result.entityType) {
        SearchEntityType.RESTAURANT -> RestaurantSearchCard(result = result, onClick = onClick)
        SearchEntityType.SERVICE -> ServiceSearchCard(result = result, onClick = onClick)
        SearchEntityType.STORE -> StoreSearchCard(result = result, onClick = onClick)
        SearchEntityType.BRAND -> BrandSearchCard(result = result, onClick = onClick)
        SearchEntityType.CATEGORY -> CategorySearchCard(result = result, onClick = onClick)
        SearchEntityType.COLLECTION -> CollectionSearchCard(result = result, onClick = onClick)
        SearchEntityType.CAMPAIGN -> CampaignSearchCard(result = result, onClick = onClick)
        SearchEntityType.OFFER -> OfferSearchCard(result = result, onClick = onClick)
        SearchEntityType.PRODUCT -> ProductSearchCard(result = result, onClick = onClick, onWishlistToggle = onWishlistToggle)
    }
}

/**
 * Rich Product Search Card adhering to strict Search Data Integrity:
 * - isExpressEligible == true -> Express badge
 * - isExpressEligible == false / null -> Omit Express badge (never infer Express from ETA/vertical/subtitle)
 * - Omit missing ratings, cuisines, providers, ETAs, availability status
 */
@Composable
fun ProductSearchCard(
    result: SearchResult,
    onClick: () -> Unit,
    onWishlistToggle: ((String) -> Unit)? = null
) {
    var isWishlisted by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                ProductImage(
                    imageUrl = result.image,
                    contentDescription = result.title,
                    shape = RoundedCornerShape(Radius.ImageTile),
                    modifier = Modifier.size(96.dp)
                )
                // STRICT INTEGRITY: Only show Express badge when isExpressEligible == true
                if (result.isExpressEligible == true) {
                    Surface(
                        color = CommerceColors.Success,
                        shape = RoundedCornerShape(topStart = Radius.ImageTile, bottomEnd = Radius.Micro),
                        modifier = Modifier.align(Alignment.TopStart)
                    ) {
                        Text(
                            "⚡ Express",
                            style = CommerceTypography.Meta,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.OnPrimary,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = CommerceColors.InfoContainer, shape = RoundedCornerShape(Radius.Micro)) {
                            Text(
                                result.vertical.uppercase(),
                                style = CommerceTypography.Meta,
                                fontWeight = FontWeight.Bold,
                                color = CommerceColors.Primary,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        // STRICT INTEGRITY: Missing rating -> omit
                        val rating = result.rating
                        if (rating != null && rating > 0.0) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(color = CommerceColors.Success, shape = RoundedCornerShape(Radius.Micro)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        String.format("%.1f", rating),
                                        style = CommerceTypography.Meta,
                                        fontWeight = FontWeight.Bold,
                                        color = CommerceColors.OnPrimary
                                    )
                                    Spacer(modifier = Modifier.width(2.dp))
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = null,
                                        tint = CommerceColors.OnPrimary,
                                        modifier = Modifier.size(10.dp)
                                    )
                                }
                            }
                        }
                    }

                    // Wishlist icon button
                    IconButton(
                        onClick = {
                            isWishlisted = !isWishlisted
                            onWishlistToggle?.invoke(result.entityId)
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isWishlisted) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                            contentDescription = "Wishlist",
                            tint = if (isWishlisted) CommerceColors.Danger else CommerceColors.TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    result.title,
                    style = CommerceTypography.BodySmall,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.TextPrimary,
                    maxLines = 2
                )

                // Subtitle / Brand label
                if (result.subtitle.isNotBlank()) {
                    Text(
                        result.subtitle,
                        style = CommerceTypography.Meta,
                        color = CommerceColors.TextMuted,
                        maxLines = 1
                    )
                }

                // STRICT INTEGRITY: Missing ETA -> omit (render standard ETA pill only if non-null)
                val eta = result.etaLabel
                if (!eta.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        "Delivery in $eta",
                        style = CommerceTypography.Meta,
                        color = CommerceColors.Primary,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Pricing & Stock Availability
                if (result.price != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            MoneyFormatter.format(result.price),
                            style = CommerceTypography.BodyLarge,
                            fontWeight = FontWeight.Bold,
                            color = CommerceColors.TextPrimary
                        )

                        // STRICT INTEGRITY: Missing availabilityStatus -> omit
                        val avail = result.availabilityStatus
                        if (!avail.isNullOrBlank()) {
                            Text(
                                "• $avail",
                                style = CommerceTypography.Meta,
                                color = if (avail.contains("In Stock", ignoreCase = true)) CommerceColors.Success else CommerceColors.TextMuted
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RestaurantSearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = CommerceColors.Primary.copy(alpha = 0.12f), shape = RoundedCornerShape(Radius.Micro)) {
                        Text("FOOD", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                    }
                    // STRICT INTEGRITY: Missing rating -> omit
                    val rating = result.rating
                    if (rating != null && rating > 0.0) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Star, contentDescription = null, tint = CommerceColors.Rating, modifier = Modifier.size(12.dp))
                            Text(String.format("%.1f", rating), style = CommerceTypography.Meta, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                
                // STRICT INTEGRITY: Missing cuisine -> omit
                val cuisine = result.cuisine
                if (!cuisine.isNullOrBlank()) {
                    Text(cuisine, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                } else if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
            }
            Surface(
                color = CommerceColors.Primary,
                shape = RoundedCornerShape(Radius.Chip)
            ) {
                Text("View Menu", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun ServiceSearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(color = CommerceColors.InfoContainer, shape = RoundedCornerShape(Radius.Micro)) {
                    Text("SERVICE", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                
                // STRICT INTEGRITY: Missing provider -> omit
                val provider = result.providerId
                if (!provider.isNullOrBlank()) {
                    Text("Provider: $provider", style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                } else if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
            }
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                shape = RoundedCornerShape(Radius.Button),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.height(34.dp)
            ) {
                Text("Book", style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun StoreSearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
            }
            Surface(
                color = CommerceColors.Primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.Chip)
            ) {
                Text("View Store", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun BrandSearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(color = CommerceColors.InfoContainer, shape = RoundedCornerShape(Radius.Micro)) {
                    Text("BRAND", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
            }
            Surface(
                color = CommerceColors.Primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.Chip)
            ) {
                Text("Explore Brand", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun CategorySearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(color = CommerceColors.InfoContainer, shape = RoundedCornerShape(Radius.Micro)) {
                    Text("CATEGORY", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
            }
            Surface(
                color = CommerceColors.Primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.Chip)
            ) {
                Text("Shop Category", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun CollectionSearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(color = CommerceColors.InfoContainer, shape = RoundedCornerShape(Radius.Micro)) {
                    Text("COLLECTION", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
            }
            Surface(
                color = CommerceColors.Primary.copy(alpha = 0.12f),
                shape = RoundedCornerShape(Radius.Chip)
            ) {
                Text("View Collection", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun CampaignSearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.HeroDark),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 3.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                contentScale = ContentScale.Crop,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(64.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(color = CommerceColors.Primary, shape = RoundedCornerShape(Radius.Micro)) {
                    Text("CAMPAIGN", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = androidx.compose.ui.graphics.Color.White, maxLines = 1)
                if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = androidx.compose.ui.graphics.Color.White.copy(alpha = 0.8f), maxLines = 1)
                }
            }
            Surface(
                color = CommerceColors.Primary,
                shape = RoundedCornerShape(Radius.Chip)
            ) {
                Text("Explore →", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}

@Composable
fun OfferSearchCard(result: SearchResult, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            ProductImage(
                imageUrl = result.image,
                contentDescription = result.title,
                shape = RoundedCornerShape(Radius.ImageTile),
                modifier = Modifier.size(60.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Surface(color = CommerceColors.Discount, shape = RoundedCornerShape(Radius.Micro)) {
                    Text("SPECIAL OFFER", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                }
                Spacer(modifier = Modifier.height(2.dp))
                Text(result.title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                if (result.subtitle.isNotBlank()) {
                    Text(result.subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted, maxLines = 1)
                }
            }
            Surface(
                color = CommerceColors.Discount,
                shape = RoundedCornerShape(Radius.Chip)
            ) {
                Text("Claim Offer", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.OnPrimary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
            }
        }
    }
}
