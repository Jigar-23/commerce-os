package com.commerceos.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.commerceos.android.model.ApiMedicine
import com.commerceos.android.util.MoneyFormatter
import com.commerceos.android.model.CartItem
import com.commerceos.android.model.CommerceEntity
import com.commerceos.android.model.CommerceProduct
import com.commerceos.android.model.HomeContext
import com.commerceos.android.model.HomeDestination
import com.commerceos.android.model.HomeHeroDto
import com.commerceos.android.model.HomeSection
import com.commerceos.android.model.HomeSectionType
import com.commerceos.android.model.HomeVertical
import com.commerceos.android.model.toProductCardModel
import com.commerceos.android.ui.components.CommerceEntityRenderer
import com.commerceos.android.ui.components.CommerceProductCard
import com.commerceos.android.ui.components.ElectronicsProductCard
import com.commerceos.android.ui.components.FashionProductCard
import com.commerceos.android.ui.components.GroceryProductCard
import com.commerceos.android.ui.components.PharmacyProductCard
import com.commerceos.android.ui.components.ProductCardVariant
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.components.SkeletonProductImage
import com.commerceos.android.ui.components.SkeletonText
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.ui.theme.Spacing
import com.commerceos.android.viewmodel.HomeViewModel

/**
 * Commerce OS Home. Renders the SERVER-AUTHORED merchandising home feed verbatim as [HomeSection]s.
 *
 * First viewport answers key consumer intent in order:
 * 1. Delivery location (where am I shopping)
 * 2. Universal Search (what am I looking for)
 * 3. Vertical Discovery (which store/vertical)
 * 4. Personalized Feed & Merchandising (Campaigns, Buy Again, Fast Near You, Deals, Recommendations)
 */
@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    customerId: String,
    cartItems: List<CartItem> = emptyList(),
    homeContext: HomeContext? = null,
    onChangeAddress: () -> Unit = {},
    onEntityClick: (CommerceEntity) -> Unit = {},
    onAddToCart: (CommerceProduct) -> Unit = {},
    onUpdateQuantity: (String, Int) -> Unit = { _, _ -> },
    onSearchClick: (String) -> Unit = {},
    onVerticalSelect: (HomeVertical) -> Unit = {},
    onOpenCatalog: () -> Unit = {}
) {
    // Feed is keyed on (customer, delivery address): changing location MUST
    // invalidate and refetch inventory/ETA/promotions for the new address.
    LaunchedEffect(customerId, homeContext?.addressId) {
        viewModel.loadHomeData(customerId, homeContext?.addressId)
    }

    val cartQuantityMap = remember(cartItems) {
        cartItems.associate { (if (it.sku.isNotBlank()) it.sku else it.productId) to it.quantity }
    }

    val isLoading = viewModel.isLoading
    val sections = viewModel.sections
    val homeError = viewModel.errorMessage

    val clientConfig = com.commerceos.android.config.LocalClientConfiguration.current
    val enabledSectionTypes = clientConfig.enabledHomeSections.toSet()
    val filteredSections = remember(sections, enabledSectionTypes) {
        sections.filter { it.type in enabledSectionTypes }.ifEmpty { sections }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(Spacing.md)
        ) {
            item {
                DeliveryAddressWidget(context = homeContext, onChangeAddress = onChangeAddress)
            }

            item {
                UniversalSearchBar(onClick = { onSearchClick("") })
            }

            if (viewModel.verticals.isNotEmpty()) {
                item {
                    VerticalRailWidget(
                        verticals = viewModel.verticals,
                        onVerticalSelect = onVerticalSelect
                    )
                }
            }

            item {
                QuickCategoriesRail(onOpenCatalog = onOpenCatalog)
            }

            if (isLoading && sections.isEmpty()) {
                item {
                    LoadingSkeletonWidget()
                }
            }

            if (homeError != null && !isLoading && sections.isEmpty()) {
                item {
                    ErrorRetryWidget(onRetry = { viewModel.loadHomeData(customerId, homeContext?.addressId, forceRefresh = true) })
                }
            }

            items(filteredSections) { section ->
                HomeSectionRenderer(
                    section = section,
                    cartQuantityMap = cartQuantityMap,
                    onEntityClick = onEntityClick,
                    onAddToCart = onAddToCart,
                    onUpdateQuantity = onUpdateQuantity,
                    onOpenCatalog = onOpenCatalog
                )
            }

            // Bottom space for floating cart
            item {
                Spacer(modifier = Modifier.height(if (cartItems.isNotEmpty()) 80.dp else 24.dp))
            }
        }
    }
}

private data class QuickCategoryTile(
    val mark: String,
    val label: String,
    val bgColor: Color,
    val fgColor: Color
)

@Composable
private fun QuickCategoriesRail(onOpenCatalog: () -> Unit) {
    val categories = listOf(
        QuickCategoryTile("Rx", "Medicines", CommerceColors.SuccessSoft, CommerceColors.PrimaryDark),
        QuickCategoryTile("D", "Dairy & Bread", Color(0xFFEFF7FF), Color(0xFF246B9F)),
        QuickCategoryTile("F", "Fresh Fruits", Color(0xFFFFF4CD), Color(0xFF8A5A00)),
        QuickCategoryTile("C", "Cold Drinks", Color(0xFFF2EEFF), Color(0xFF5A45A0)),
        QuickCategoryTile("M", "Munchies", Color(0xFFFFF0DF), Color(0xFF9A4F00)),
        QuickCategoryTile("B", "Bath & Body", Color(0xFFE7F8F4), Color(0xFF00796B)),
        QuickCategoryTile("E", "Essentials", Color(0xFFFFEEF1), Color(0xFFB42345)),
        QuickCategoryTile("Bk", "Bakery", Color(0xFFFFF7D6), Color(0xFF7A5A00))
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "Explore Categories",
            style = CommerceTypography.Label,
            fontWeight = FontWeight.Black,
            color = CommerceColors.TextPrimary,
            modifier = Modifier.padding(bottom = 10.dp)
        )

        categories.chunked(4).forEach { rowList ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowList.forEach { item ->
                    Surface(
                        color = CommerceColors.Surface,
                        shape = RoundedCornerShape(Radius.Card),
                        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
                        modifier = Modifier
                            .weight(1f)
                            .height(94.dp)
                            .clickable { onOpenCatalog() }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = item.bgColor,
                                shape = CircleShape,
                                modifier = Modifier.size(44.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = item.mark,
                                        style = CommerceTypography.Label,
                                        fontWeight = FontWeight.Black,
                                        color = item.fgColor
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.label,
                                style = CommerceTypography.Meta,
                                fontWeight = FontWeight.Bold,
                                color = CommerceColors.TextPrimary,
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Fixed first-viewport chrome
// ---------------------------------------------------------------------------

@Composable
private fun DeliveryAddressWidget(context: HomeContext?, onChangeAddress: () -> Unit) {
    val hasAddress = context?.hasAddress == true

    // Dynamic ETA calculation: distance between Store (28.202218, 76.615403) and Customer GeoPoint
    val etaDisplay = remember(context) {
        val geo = context?.geoPoint
        if (!geo.isNullOrBlank() && geo.contains(",")) {
            val parts = geo.split(",")
            val lat = parts.getOrNull(0)?.trim()?.toDoubleOrNull()
            val lng = parts.getOrNull(1)?.trim()?.toDoubleOrNull()
            if (lat != null && lng != null && lat != 0.0) {
                val r = 6371.0
                val dLat = Math.toRadians(lat - 28.202218)
                val dLon = Math.toRadians(lng - 76.615403)
                val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                        Math.cos(Math.toRadians(28.202218)) * Math.cos(Math.toRadians(lat)) *
                        Math.sin(dLon / 2) * Math.sin(dLon / 2)
                val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
                val distKm = r * c
                val eta = (3 + Math.ceil(distKm * 2.5).toInt()).coerceIn(4, 45)
                "$eta MINS"
            } else {
                context.formattedEta?.takeIf { it.isNotBlank() } ?: "10 MINS"
            }
        } else if (!context?.formattedEta.isNullOrBlank()) {
            context!!.formattedEta!!
        } else {
            "10 MINS"
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onChangeAddress)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = CommerceColors.SpeedYellow,
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = etaDisplay,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Black,
                        color = CommerceColors.SushiInk
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (hasAddress) "Delivery to Home" else "Select Location",
                        style = CommerceTypography.BodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.TextPrimary
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change Address",
                        tint = CommerceColors.TextMuted,
                        modifier = Modifier.size(16.dp)
                    )
                }
                Text(
                    text = context?.displayLabel ?: "Rewari Central, Haryana",
                    style = CommerceTypography.Meta,
                    fontWeight = FontWeight.Normal,
                    color = CommerceColors.TextMuted,
                    maxLines = 1
                )
            }

            Text(
                text = "Change",
                style = CommerceTypography.Caption,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.Primary
            )
        }
    }
}

@Composable
private fun UniversalSearchBar(onClick: () -> Unit) {
    Surface(
        color = CommerceColors.Surface,
        shape = RoundedCornerShape(Radius.Card),
        border = androidx.compose.foundation.BorderStroke(1.dp, CommerceColors.Border),
        shadowElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = CommerceColors.TextPrimary, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(10.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Search for milk, eggs, medicines...",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = CommerceColors.TextMuted,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

// Vertical availability is server-authored: unlaunched stores are advertised
// honestly as "Coming soon" instead of dead buttons.
@Composable
private fun VerticalRailWidget(
    verticals: List<HomeVertical>,
    onVerticalSelect: (HomeVertical) -> Unit
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm)
    ) {
        items(verticals) { vertical ->
            VerticalChip(
                vertical = vertical,
                onClick = { onVerticalSelect(vertical) },
                modifier = Modifier.width(92.dp)
            )
        }
    }
}

@Composable
private fun VerticalChip(
    vertical: HomeVertical,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLive = vertical.isLive
    val isServiceable = vertical.status?.isServiceable ?: true
    val enabled = isLive && isServiceable
    val accent = CommerceColors.Primary
    Card(
        colors = CardDefaults.cardColors(
            containerColor = CommerceColors.Surface
        ),
        shape = RoundedCornerShape(Radius.md),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (enabled) CommerceColors.Primary.copy(alpha = 0.35f) else CommerceColors.Border
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        modifier = modifier.then(
            if (enabled) Modifier.clickable(onClick = onClick) else Modifier
        )
    ) {
        Column(
            modifier = Modifier.padding(vertical = Spacing.sm, horizontal = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            VerticalGlyph(iconKey = vertical.iconKey, tint = if (enabled) accent else CommerceColors.TextMuted)
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                vertical.label,
                style = CommerceTypography.Caption,
                fontWeight = FontWeight.Bold,
                color = if (enabled) CommerceColors.TextPrimary else CommerceColors.TextMuted,
                maxLines = 1
            )
            val etaLabel = vertical.status?.etaLabel
            val statusLabel = when {
                !isLive -> "Coming soon"
                vertical.status?.status == com.commerceos.android.model.VerticalOperationalStatus.UNKNOWN -> "Unknown"
                !isServiceable -> "Unavailable"
                !etaLabel.isNullOrBlank() -> etaLabel
                else -> "Available"
            }
            Text(
                statusLabel,
                style = CommerceTypography.Meta,
                color = if (enabled) CommerceColors.Primary else CommerceColors.TextMuted
            )
        }
    }
}

@Composable
private fun VerticalGlyph(iconKey: String, tint: Color) {
    val symbol = when (iconKey.lowercase()) {
        "grocery", "fresh" -> "🛒"
        "food", "restaurant" -> "🍽️"
        "fashion", "style" -> "👔"
        "electronics", "tech" -> "📱"
        "health", "pharmacy" -> "💊"
        "local", "services" -> "🔧"
        else -> "🛍️"
    }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Box(
            modifier = Modifier
                .size(36.dp)
                .padding(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = symbol,
                style = CommerceTypography.Title
            )
        }
    }
}



// ---------------------------------------------------------------------------
// Server-authored feed sections
// ---------------------------------------------------------------------------

@Composable
fun HeroCampaignWidget(hero: HomeHeroDto, onCta: () -> Unit) {
    val accent = when (hero.themeKey) {
        "wellness" -> CommerceColors.Success
        "care" -> CommerceColors.Primary
        "pulse" -> CommerceColors.ColdChain
        else -> CommerceColors.Primary
    }
    val base = CommerceColors.HeroDark
    val deep = Color(
        red = base.red * 0.72f,
        green = base.green * 0.72f,
        blue = base.blue * 0.72f,
        alpha = 1f
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Brush.verticalGradient(listOf(base, deep)), RoundedCornerShape(Radius.Hero))
    ) {
        // Editorial Campaign Photography / Backdrop
        if (!hero.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.55f)
                    .align(Alignment.CenterEnd)
            ) {
                ProductImage(
                    imageUrl = hero.imageUrl,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(topEnd = Radius.Hero, bottomEnd = Radius.Hero),
                    modifier = Modifier.fillMaxSize()
                )
                // Smooth gradient overlay to protect text readability on left
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(deep, deep.copy(alpha = 0.6f), Color.Transparent)
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.68f)
                .padding(Spacing.lg),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(color = accent, shape = RoundedCornerShape(Radius.Micro)) {
                Text(
                    hero.badge,
                    style = CommerceTypography.Meta,
                    fontWeight = FontWeight.Bold,
                    color = CommerceColors.OnPrimary,
                    modifier = Modifier.padding(horizontal = Spacing.sm, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                hero.title,
                style = CommerceTypography.HeroTitle,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.Surface,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                hero.subtitle,
                style = CommerceTypography.BodySmall,
                color = CommerceColors.HeroOnDark,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                shape = RoundedCornerShape(Radius.Button),
                contentPadding = PaddingValues(horizontal = Spacing.md, vertical = Spacing.xs),
                modifier = Modifier.height(36.dp)
            ) {
                Text(hero.ctaText, style = CommerceTypography.Label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Loading / error states
// ---------------------------------------------------------------------------

@Composable
private fun LoadingSkeletonWidget() {
    Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
        repeat(3) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                shape = RoundedCornerShape(Radius.Card),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(modifier = Modifier.padding(Spacing.md)) {
                    SkeletonProductImage(
                        modifier = Modifier.size(96.dp),
                        shape = RoundedCornerShape(Radius.ImageTile)
                    )
                    Spacer(modifier = Modifier.width(Spacing.md))
                    Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        SkeletonText(width = 150.dp, height = 14.dp)
                        SkeletonText(width = 100.dp, height = 11.dp)
                        SkeletonText(width = 70.dp, height = 11.dp)
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorRetryWidget(onRetry: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.lg),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(Spacing.xl), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "Could not load home",
                style = CommerceTypography.BodySmall,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.Danger
            )
            Spacer(modifier = Modifier.height(Spacing.xs))
            Text(
                "Please check your connection and try again.",
                style = CommerceTypography.Caption,
                color = CommerceColors.TextMuted
            )
            Spacer(modifier = Modifier.height(Spacing.md))
            TextButton(onClick = onRetry) {
                Text("Retry", color = CommerceColors.Primary, fontWeight = FontWeight.Bold)
            }
        }
    }
}
