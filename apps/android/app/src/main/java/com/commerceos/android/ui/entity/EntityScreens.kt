package com.commerceos.android.ui.entity

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.commerceos.android.model.CatalogQuery
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing

/** Dedicated Store / Merchant Hub screen. */
@Composable
fun StoreScreen(
    storeId: String,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    EntityDetailScaffold(
        title = "Store: $storeId",
        subtitle = "Local Merchant Storefront",
        badge = "Merchant Partner",
        icon = Icons.Default.Home,
        ctaLabel = "Browse Store Products",
        onBack = onBack,
        onExplore = { onOpenCatalog(CatalogQuery(storeId = storeId)) }
    )
}

/** Dedicated Restaurant Hub screen. */
@Composable
fun RestaurantScreen(
    restaurantId: String,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    EntityDetailScaffold(
        title = "Restaurant: $restaurantId",
        subtitle = "Dining & Kitchen Partner",
        badge = "Restaurant",
        icon = Icons.Default.ShoppingCart,
        ctaLabel = "View Restaurant Menu",
        onBack = onBack,
        onExplore = { onOpenCatalog(CatalogQuery(vertical = "food")) }
    )
}

/** Dedicated Service Marketplace screen. */
@Composable
fun ServiceScreen(
    serviceId: String,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    EntityDetailScaffold(
        title = "Service: $serviceId",
        subtitle = "On-Demand Service Offering",
        badge = "Service Provider",
        icon = Icons.Default.Build,
        ctaLabel = "Book Service",
        onBack = onBack,
        onExplore = { onOpenCatalog(CatalogQuery(vertical = "services")) }
    )
}

/** Dedicated Campaign Landing screen. */
@Composable
fun CampaignLandingScreen(
    campaignId: String,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    EntityDetailScaffold(
        title = "Campaign: $campaignId",
        subtitle = "Featured Platform Promotion",
        badge = "Special Offer",
        icon = Icons.Default.Star,
        ctaLabel = "Shop Campaign Offers",
        onBack = onBack,
        onExplore = { onOpenCatalog(CatalogQuery(campaignId = campaignId)) }
    )
}

/** Dedicated Brand Catalog screen. */
@Composable
fun BrandScreen(
    brandId: String,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    EntityDetailScaffold(
        title = "Brand: $brandId",
        subtitle = "Official Brand Products",
        badge = "Brand Catalog",
        icon = Icons.Default.Star,
        ctaLabel = "Shop Brand Products",
        onBack = onBack,
        onExplore = { onOpenCatalog(CatalogQuery(brandId = brandId)) }
    )
}

/** Dedicated Merchandising Collection screen. */
@Composable
fun CollectionScreen(
    collectionId: String,
    onBack: () -> Unit,
    onOpenCatalog: (CatalogQuery) -> Unit
) {
    EntityDetailScaffold(
        title = "Collection: $collectionId",
        subtitle = "Curated Product Showcase",
        badge = "Collection",
        icon = Icons.Default.Menu,
        ctaLabel = "View Collection",
        onBack = onBack,
        onExplore = { onOpenCatalog(CatalogQuery(collectionId = collectionId)) }
    )
}

@Composable
private fun EntityDetailScaffold(
    title: String,
    subtitle: String,
    badge: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    ctaLabel: String = "Explore Catalog",
    onBack: () -> Unit,
    onExplore: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(Spacing.lg),
        verticalArrangement = Arrangement.spacedBy(Spacing.lg)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Text(subtitle, style = CommerceTypography.Meta, color = CommerceColors.TextMuted)
            }
        }

        Card(
            colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
            shape = RoundedCornerShape(Radius.Card),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(color = CommerceColors.InfoContainer, shape = RoundedCornerShape(Radius.Chip)) {
                    Text(badge, style = CommerceTypography.Meta, fontWeight = FontWeight.Bold, color = CommerceColors.Primary, modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp))
                }
                Spacer(modifier = Modifier.height(Spacing.lg))
                Icon(icon, contentDescription = null, tint = CommerceColors.Primary, modifier = Modifier.size(64.dp))
                Spacer(modifier = Modifier.height(Spacing.md))
                Text(title, style = CommerceTypography.Title, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary)
                Spacer(modifier = Modifier.height(Spacing.xs))
                Text(subtitle, style = CommerceTypography.BodySmall, color = CommerceColors.TextMuted)
                Spacer(modifier = Modifier.height(Spacing.xl))
                Button(
                    onClick = onExplore,
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                    shape = RoundedCornerShape(Radius.md)
                ) {
                    Text(ctaLabel, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
