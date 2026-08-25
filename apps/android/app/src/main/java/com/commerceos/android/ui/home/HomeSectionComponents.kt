package com.commerceos.android.ui.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.commerceos.android.model.*
import com.commerceos.android.ui.components.CommerceEntityRenderer
import com.commerceos.android.ui.components.ProductImage
import com.commerceos.android.ui.theme.CommerceColors
import com.commerceos.android.ui.theme.CommerceTypography
import com.commerceos.android.ui.theme.Radius
import com.commerceos.android.util.MoneyFormatter

/**
 * Universal Home Section Renderer powering Commerce OS feed layout.
 * Dispatches server-authored section types to specialized visual section composables.
 */
@Composable
fun HomeSectionRenderer(
    section: HomeSection,
    cartQuantityMap: Map<String, Int>,
    onEntityClick: (CommerceEntity) -> Unit,
    onAddToCart: (CommerceProduct) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    onOpenCatalog: () -> Unit
) {
    when (section.type) {
        HomeSectionType.HERO_CAMPAIGN -> section.heroDto?.let { hero ->
            HeroCampaignWidget(hero = hero, onCta = { onEntityClick(CommerceEntity.Shortcut(hero.campaignId, hero.title, "campaign", HomeDestination.Campaign(hero.campaignId))) })
        }

        HomeSectionType.RESTAURANT_SHELF -> RestaurantShelfSection(
            section = section,
            onEntityClick = onEntityClick
        )

        HomeSectionType.SERVICE_SHELF -> ServiceShelfSection(
            section = section,
            onEntityClick = onEntityClick
        )

        HomeSectionType.DEAL_GRID -> DealGridSection(
            section = section,
            onEntityClick = onEntityClick
        )

        HomeSectionType.EDITORIAL -> EditorialSection(
            section = section,
            onEntityClick = onEntityClick
        )

        HomeSectionType.BUY_AGAIN -> ProductRowSection(
            section = section,
            isReorder = true,
            cartQuantityMap = cartQuantityMap,
            onEntityClick = onEntityClick,
            onAddToCart = onAddToCart,
            onUpdateQuantity = onUpdateQuantity,
            actionLabel = "View catalog",
            onAction = onOpenCatalog
        )

        HomeSectionType.TOP_DEALS,
        HomeSectionType.POPULAR_PICKS,
        HomeSectionType.FAST_FULFILLMENT_NEAR_YOU,
        HomeSectionType.RECOMMENDED_FEED -> ProductRowSection(
            section = section,
            isReorder = false,
            cartQuantityMap = cartQuantityMap,
            onEntityClick = onEntityClick,
            onAddToCart = onAddToCart,
            onUpdateQuantity = onUpdateQuantity,
            actionLabel = if (section.type == HomeSectionType.RECOMMENDED_FEED) "See all" else null,
            onAction = onOpenCatalog
        )

        HomeSectionType.CATEGORY_GRID -> CategoryGridSection(
            section = section,
            onEntityClick = onEntityClick,
            onSeeAll = onOpenCatalog
        )

        HomeSectionType.BRAND_PARTNERS -> BrandRowSection(
            section = section,
            onEntityClick = onEntityClick
        )

        HomeSectionType.COLLECTION_SECTION -> CollectionSection(
            section = section,
            onEntityClick = onEntityClick
        )

        HomeSectionType.DISH_SHELF -> DishShelfSection(
            section = section,
            onEntityClick = onEntityClick
        )

        else -> Unit
    }
}

@Composable
fun ProductRowSection(
    section: HomeSection,
    isReorder: Boolean,
    cartQuantityMap: Map<String, Int>,
    onEntityClick: (CommerceEntity) -> Unit,
    onAddToCart: (CommerceProduct) -> Unit,
    onUpdateQuantity: (String, Int) -> Unit,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    val entities = section.entities
    if (entities.isEmpty()) return

    Column {
        SectionHeader(title = section.title, subtitle = section.subtitle, actionLabel = actionLabel, onAction = onAction)
        Spacer(modifier = Modifier.height(10.dp))
        val configuration = androidx.compose.ui.platform.LocalConfiguration.current
        val responsiveCardWidth = remember(configuration.screenWidthDp) {
            if (configuration.screenWidthDp > 600) 210.dp else (configuration.screenWidthDp * 0.42f).coerceIn(145f, 175f).dp
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(entities) { entity ->
                CommerceEntityRenderer(
                    entity = entity,
                    onEntityClick = onEntityClick,
                    onAddToCart = onAddToCart,
                    onUpdateQuantity = onUpdateQuantity,
                    cartQuantityMap = cartQuantityMap,
                    modifier = Modifier.width(responsiveCardWidth)
                )
            }
        }
    }
}

@Composable
fun CategoryGridSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit,
    onSeeAll: () -> Unit
) {
    val groups = section.entities
    if (groups.isEmpty()) return

    Column {
        SectionHeader(title = section.title, subtitle = null, actionLabel = "See all", onAction = onSeeAll)
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (rowPair in groups.chunked(2)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (entity in rowPair) {
                        val group = (entity as? CommerceEntity.CategoryItem)?.group
                        if (group != null) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                                shape = RoundedCornerShape(Radius.Card),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { onEntityClick(entity) }
                            ) {
                                Column {
                                    ProductImage(
                                        imageUrl = group.imageUrl,
                                        contentDescription = group.title,
                                        contentScale = ContentScale.Crop,
                                        shape = RoundedCornerShape(topStart = Radius.Card, topEnd = Radius.Card),
                                        modifier = Modifier.fillMaxWidth().height(100.dp)
                                    )
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        Text(
                                            group.title,
                                            style = CommerceTypography.BodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = CommerceColors.TextPrimary,
                                            maxLines = 1
                                        )
                                        if (!group.subtitle.isNullOrBlank()) {
                                            Spacer(modifier = Modifier.height(2.dp))
                                            Text(
                                                group.subtitle!!,
                                                style = CommerceTypography.Meta,
                                                color = CommerceColors.TextMuted,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BrandRowSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit
) {
    val brands = section.entities.mapNotNull { (it as? CommerceEntity.Brand)?.item }
    if (brands.isEmpty()) return

    Column {
        SectionHeader(title = section.title, subtitle = null, actionLabel = null, onAction = null)
        Spacer(modifier = Modifier.height(10.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            for (brand in brands) {
                val entity = CommerceEntity.Brand(item = brand, vertical = "general")
                Card(
                    colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
                    shape = RoundedCornerShape(Radius.md),
                    border = BorderStroke(1.dp, CommerceColors.Border.copy(alpha = 0.4f)),
                    modifier = Modifier.clickable { onEntityClick(entity) }
                ) {
                    Text(
                        brand.name,
                        style = CommerceTypography.BodySmall,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.NeutralDark,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RestaurantShelfSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit
) {
    val entities = section.entities
    if (entities.isEmpty()) return

    Column {
        SectionHeader(title = section.title ?: "Top Restaurants Near You", subtitle = section.subtitle, actionLabel = null, onAction = null)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(entities) { entity ->
                CommerceEntityRenderer(
                    entity = entity,
                    onEntityClick = onEntityClick,
                    modifier = Modifier.width(250.dp)
                )
            }
        }
    }
}

@Composable
fun ServiceShelfSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit
) {
    val entities = section.entities
    if (entities.isEmpty()) return

    Column {
        SectionHeader(title = section.title ?: "Local Services", subtitle = section.subtitle, actionLabel = null, onAction = null)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(entities) { entity ->
                CommerceEntityRenderer(
                    entity = entity,
                    onEntityClick = onEntityClick,
                    modifier = Modifier.width(280.dp)
                )
            }
        }
    }
}

@Composable
fun DealGridSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit
) {
    val entities = section.entities
    if (entities.isEmpty()) return

    Column {
        SectionHeader(title = section.title ?: "Top Deals", subtitle = section.subtitle, actionLabel = null, onAction = null)
        Spacer(modifier = Modifier.height(10.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for (rowPair in entities.chunked(2)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    for (entity in rowPair) {
                        DealCard(
                            entity = entity,
                            onClick = { onEntityClick(entity) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DealCard(
    entity: CommerceEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val product = (entity as? CommerceEntity.ProductItem)?.product
    val title = product?.name ?: (entity as? CommerceEntity.CategoryItem)?.group?.title ?: "Limited Deal"
    val imageUrl = product?.image ?: (entity as? CommerceEntity.CategoryItem)?.group?.imageUrl
    val sellingPrice = product?.sellingPrice
    val originalPrice = product?.price
    val discountPercent = product?.discountPercent ?: 0

    Card(
        colors = CardDefaults.cardColors(containerColor = CommerceColors.Surface),
        shape = RoundedCornerShape(Radius.Card),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Column {
            Box {
                ProductImage(
                    imageUrl = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Fit,
                    shape = RoundedCornerShape(topStart = Radius.Card, topEnd = Radius.Card),
                    modifier = Modifier.fillMaxWidth().height(110.dp)
                )
                Surface(
                    color = CommerceColors.Discount,
                    shape = RoundedCornerShape(topStart = Radius.Card, bottomEnd = Radius.Micro),
                    modifier = Modifier.align(Alignment.TopStart)
                ) {
                    Text(
                        if (discountPercent > 0) "$discountPercent% OFF" else "MEGA DEAL",
                        color = CommerceColors.OnPrimary,
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }
            Column(modifier = Modifier.padding(10.dp)) {
                Text(title, style = CommerceTypography.BodySmall, fontWeight = FontWeight.Bold, color = CommerceColors.TextPrimary, maxLines = 1)
                if (sellingPrice != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(MoneyFormatter.format(sellingPrice), style = CommerceTypography.Label, fontWeight = FontWeight.Bold, color = CommerceColors.Primary)
                        if (originalPrice != null && originalPrice > sellingPrice) {
                            Text(MoneyFormatter.format(originalPrice), style = CommerceTypography.Meta, color = CommerceColors.TextMuted, textDecoration = TextDecoration.LineThrough)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun EditorialSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit
) {
    val hero = section.heroDto
    if (hero != null) {
        EditorialCard(hero = hero, onCta = { onEntityClick(CommerceEntity.Shortcut(hero.campaignId, hero.title, "campaign", HomeDestination.Campaign(hero.campaignId))) })
    }
}

@Composable
fun EditorialCard(hero: HomeHeroDto, onCta: () -> Unit) {
    Card(
        shape = RoundedCornerShape(Radius.Hero),
        colors = CardDefaults.cardColors(containerColor = CommerceColors.HeroDark),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = Modifier.fillMaxWidth().clickable(onClick = onCta)
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
            if (!hero.imageUrl.isNullOrBlank()) {
                ProductImage(
                    imageUrl = hero.imageUrl,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                colors = listOf(CommerceColors.HeroDark.copy(alpha = 0.92f), Color.Transparent)
                            )
                        )
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp),
                verticalArrangement = Arrangement.Center
            ) {
                Surface(
                    color = CommerceColors.Primary,
                    shape = RoundedCornerShape(Radius.Micro)
                ) {
                    Text(
                        hero.badge ?: "EDITORIAL STORY",
                        style = CommerceTypography.Meta,
                        fontWeight = FontWeight.Bold,
                        color = CommerceColors.OnPrimary,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    hero.title,
                    style = CommerceTypography.Title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    maxLines = 1
                )
                Text(
                    hero.subtitle,
                    style = CommerceTypography.Caption,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 2
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onCta,
                    colors = ButtonDefaults.buttonColors(containerColor = CommerceColors.Primary),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                    modifier = Modifier.height(34.dp)
                ) {
                    Text(hero.ctaText ?: "Shop Story →", style = CommerceTypography.Meta, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
fun SectionHeader(
    title: String?,
    subtitle: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            if (title != null) {
                Text(
                    title,
                    style = CommerceTypography.Title,
                    fontWeight = FontWeight.SemiBold,
                    color = CommerceColors.TextPrimary
                )
            }
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = CommerceTypography.Caption,
                    color = CommerceColors.TextMuted
                )
            }
        }
        if (actionLabel != null && onAction != null) {
            Text(
                actionLabel,
                style = CommerceTypography.Caption,
                fontWeight = FontWeight.Bold,
                color = CommerceColors.Primary,
                modifier = Modifier.clickable(onClick = onAction)
            )
        }
    }
}

@Composable
fun CollectionSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit
) {
    val entities = section.entities
    if (entities.isEmpty()) return

    Column {
        SectionHeader(title = section.title ?: "Featured Collections", subtitle = section.subtitle, actionLabel = null, onAction = null)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(entities) { entity ->
                CommerceEntityRenderer(
                    entity = entity,
                    onEntityClick = onEntityClick,
                    modifier = Modifier.width(220.dp)
                )
            }
        }
    }
}

@Composable
fun DishShelfSection(
    section: HomeSection,
    onEntityClick: (CommerceEntity) -> Unit
) {
    val entities = section.entities
    if (entities.isEmpty()) return

    Column {
        SectionHeader(title = section.title ?: "Popular Dishes", subtitle = section.subtitle, actionLabel = null, onAction = null)
        Spacer(modifier = Modifier.height(10.dp))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 4.dp)
        ) {
            items(entities) { entity ->
                CommerceEntityRenderer(
                    entity = entity,
                    onEntityClick = onEntityClick,
                    modifier = Modifier.width(200.dp)
                )
            }
        }
    }
}
