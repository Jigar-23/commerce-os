package com.commerceos.android.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import kotlinx.coroutines.delay
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
import com.commerceos.android.model.ApiAddress
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
    selectedAddress: ApiAddress? = null,
    calculatedEtaMinutes: Int = 11,
    locationHeaderLabel: String? = null,
    onChangeAddress: () -> Unit = {},
    onProfileClick: () -> Unit = {},
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
                DeliveryAddressWidget(
                    context = homeContext,
                    selectedAddress = selectedAddress,
                    calculatedEtaMinutes = calculatedEtaMinutes,
                    locationHeaderLabel = locationHeaderLabel,
                    onChangeAddress = onChangeAddress,
                    onProfileClick = onProfileClick
                )
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
    val icon: ImageVector,
    val label: String,
    val bgColor: Color,
    val iconTint: Color
)

@Composable
private fun QuickCategoriesRail(onOpenCatalog: () -> Unit) {
    val categories = listOf(
        QuickCategoryTile(Icons.Default.ShoppingCart, "Groceries", Color(0xFFE8F5E9), Color(0xFF16A34A)),
        QuickCategoryTile(Icons.Default.Favorite, "Medicines", Color(0xFFE0F2FE), Color(0xFF0284C7)),
        QuickCategoryTile(Icons.Default.Star, "Top Deals", Color(0xFFFEF3C7), Color(0xFFD97706)),
        QuickCategoryTile(Icons.Default.Home, "Essentials", Color(0xFFEDE9FE), Color(0xFF7C3AED)),
        QuickCategoryTile(Icons.Default.Person, "Personal Care", Color(0xFFFCE7F3), Color(0xFFDB2777)),
        QuickCategoryTile(Icons.Default.Phone, "Electronics", Color(0xFFCCFBF1), Color(0xFF0D9488)),
        QuickCategoryTile(Icons.Default.Build, "Home Repairs", Color(0xFFFFEDD5), Color(0xFFEA580C)),
        QuickCategoryTile(Icons.Default.LocationOn, "Local Stores", Color(0xFFF1F5F9), Color(0xFF475569))
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
                        color = Color.White,
                        shape = RoundedCornerShape(14.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .weight(1f)
                            .height(88.dp)
                            .clickable { onOpenCatalog() }
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Surface(
                                color = item.bgColor,
                                shape = CircleShape,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = item.icon,
                                        contentDescription = item.label,
                                        tint = item.iconTint,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = item.label,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF1E293B),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                maxLines = 1,
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
// Fixed first-viewport chrome (Blinkit & Zomato Native Top Header)
// ---------------------------------------------------------------------------

@Composable
private fun DeliveryAddressWidget(
    context: HomeContext?,
    selectedAddress: ApiAddress? = null,
    calculatedEtaMinutes: Int = 11,
    locationHeaderLabel: String? = null,
    onChangeAddress: () -> Unit,
    onProfileClick: () -> Unit
) {
    val hasAddress = context?.hasAddress == true || selectedAddress != null || !locationHeaderLabel.isNullOrBlank()

    val etaDisplay = remember(calculatedEtaMinutes, context) {
        if (calculatedEtaMinutes > 0) {
            "$calculatedEtaMinutes mins"
        } else if (!context?.formattedEta.isNullOrBlank()) {
            context!!.formattedEta!!
        } else {
            "11 mins"
        }
    }

    val displayAddress = if (!locationHeaderLabel.isNullOrBlank()) {
        locationHeaderLabel
    } else if (hasAddress) {
        val tag = selectedAddress?.tag?.ifBlank { "Home" } ?: "Home"
        val line = selectedAddress?.addressLine?.takeIf { it.isNotBlank() }
            ?: context?.displayLabel?.takeIf { it.isNotBlank() }
            ?: "Delivery Location"
        "$tag - $line"
    } else {
        "Select Delivery Location"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Column: Brand / Delivery SLA + Location Selector
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "Delivery in ",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = etaDisplay,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF059669) // Fresh Emerald Accent for ETA
                )
            }

            Spacer(modifier = Modifier.height(3.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clickable(onClick = onChangeAddress)
                    .padding(vertical = 2.dp)
            ) {
                Icon(
                    Icons.Default.LocationOn,
                    contentDescription = null,
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(15.dp)
                )
                Spacer(modifier = Modifier.width(3.dp))
                Text(
                    text = displayAddress,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF334155),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Spacer(modifier = Modifier.width(2.dp))
                Icon(
                    Icons.Default.KeyboardArrowDown,
                    contentDescription = "Change Address",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Right Profile Circle Button
        Surface(
            color = Color(0xFFF1F5F9),
            shape = CircleShape,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            modifier = Modifier
                .size(42.dp)
                .clickable(onClick = onProfileClick)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Person,
                    contentDescription = "Account",
                    tint = Color(0xFF0F172A),
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun UniversalSearchBar(onClick: () -> Unit) {
    val searchHints = remember {
        listOf(
            "Search \"fresh milk, breads, fruits...\"",
            "Search \"medicines, wellness & care...\"",
            "Search \"chocolates, snacks & ice cream...\"",
            "Search \"10-minute daily essentials...\""
        )
    }
    var hintIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(2800)
            hintIndex = (hintIndex + 1) % searchHints.size
        }
    }

    Surface(
        color = Color.White,
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 3.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Default.Search,
                contentDescription = null,
                tint = Color(0xFF059669),
                modifier = Modifier.size(22.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Box(modifier = Modifier.weight(1f)) {
                AnimatedContent(
                    targetState = hintIndex,
                    transitionSpec = {
                        fadeIn() togetherWith fadeOut()
                    },
                    label = "SearchHintAnimation"
                ) { targetIndex ->
                    Text(
                        text = searchHints[targetIndex],
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Surface(
                color = Color(0xFFF1F5F9),
                shape = CircleShape,
                modifier = Modifier.size(30.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        "🎙️",
                        fontSize = 13.sp
                    )
                }
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
    val icon = when (iconKey.lowercase()) {
        "grocery", "fresh" -> Icons.Default.ShoppingCart
        "food", "restaurant" -> Icons.Default.Star
        "fashion", "style" -> Icons.Default.Person
        "electronics", "tech" -> Icons.Default.Phone
        "health", "pharmacy", "wellness" -> Icons.Default.Favorite
        "local", "services" -> Icons.Default.Build
        else -> Icons.Default.Home
    }
    Surface(
        color = tint.copy(alpha = 0.12f),
        shape = CircleShape
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .padding(8.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(22.dp)
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
