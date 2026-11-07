package com.commerceos.android.ui.home

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Chat
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.core.view.WindowCompat
import com.commerceos.android.R
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import com.commerceos.android.model.ApiAddress
import com.commerceos.android.model.ApiMedicine
import com.commerceos.android.model.CouponCampaign
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
    availableCampaigns: List<CouponCampaign> = emptyList(),
    homeContext: HomeContext? = null,
    selectedAddress: ApiAddress? = null,
    calculatedEtaMinutes: Int = 0,
    locationHeaderLabel: String? = null,
    isLocating: Boolean = false,
    onChangeAddress: () -> Unit = {},
    onProfileClick: () -> Unit = {},
    onNotificationsClick: () -> Unit = {},
    onEntityClick: (CommerceEntity) -> Unit = {},
    onAddToCart: (CommerceProduct) -> Unit = {},
    onUpdateQuantity: (String, Int) -> Unit = { _, _ -> },
    onSearchClick: (String) -> Unit = {},
    onVerticalSelect: (HomeVertical) -> Unit = {},
    onOpenCatalog: () -> Unit = {},
    onUploadPrescription: () -> Unit = {}
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

    val nonHealthSections = remember {
        setOf(
            HomeSectionType.CATEGORY_GRID,
            HomeSectionType.RESTAURANT_SHELF,
            HomeSectionType.SERVICE_SHELF,
            HomeSectionType.DISH_SHELF
        )
    }
    val filteredSections = remember(sections) {
        sections.filter { it.type !in nonHealthSections }
    }

    val context = LocalContext.current
    val handleContactSupport: (String) -> Unit = remember(context) {
        { mode ->
            try {
                if (mode == "whatsapp") {
                    val intent = android.content.Intent(android.content.Intent.ACTION_VIEW).apply {
                        data = android.net.Uri.parse("https://wa.me/919817916180?text=Hello%20CommerceOS%20Pharmacy%2C%20I%20would%20like%20to%20order%20medicines.")
                    }
                    context.startActivity(intent)
                } else if (mode == "call") {
                    val intent = android.content.Intent(android.content.Intent.ACTION_DIAL).apply {
                        data = android.net.Uri.parse("tel:+919817916180")
                    }
                    context.startActivity(intent)
                }
            } catch (_: Exception) {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF4F6F8))
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Edge-to-Edge Medical Teal Canopy Header
            item {
                HeaderCanopy(
                    homeContext = homeContext,
                    selectedAddress = selectedAddress,
                    calculatedEtaMinutes = calculatedEtaMinutes,
                    locationHeaderLabel = locationHeaderLabel,
                    isLocating = isLocating,
                    onChangeAddress = onChangeAddress,
                    onProfileClick = onProfileClick,
                    onNotificationsClick = onNotificationsClick,
                    onSearchClick = onSearchClick,
                    onUploadPrescription = onUploadPrescription
                )
            }

            // 2. Dual Service Hero Tiles (Medicines vs Lab Tests)
            item {
                HeroDualActionCards(
                    onMedicinesClick = { onSearchClick("") },
                    onLabTestsClick = { onSearchClick("Lab Tests") }
                )
            }

            // 2b. Tiered Promotional Campaigns Strip (Active Authoritative Coupons)
            if (availableCampaigns.isNotEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        PromotionalCampaignsStrip(
                            campaigns = availableCampaigns,
                            onSelectCampaign = { code ->
                                onSearchClick("")
                            }
                        )
                    }
                }
            }

            // 3. Assisted Commerce Ribbon (Upload Rx / WhatsApp / Call 24x7)
            item {
                AssistedCommerceRibbon(
                    onUploadPrescription = onUploadPrescription,
                    onContactSupport = handleContactSupport
                )
            }

            // 4. Popular Healthcare Categories
            item {
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    MedicineCategoryRail(
                        onSelectCategory = { categoryQuery -> onSearchClick(categoryQuery) },
                        onSeeAll = onOpenCatalog
                    )
                }
            }

            if (isLoading && sections.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        LoadingSkeletonWidget()
                    }
                }
            }

            if (homeError != null && !isLoading && sections.isEmpty()) {
                item {
                    Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                        ErrorRetryWidget(onRetry = { viewModel.loadHomeData(customerId, homeContext?.addressId, forceRefresh = true) })
                    }
                }
            }

            items(filteredSections) { section ->
                Box(modifier = Modifier.padding(horizontal = 14.dp)) {
                    HomeSectionRenderer(
                        section = section,
                        cartQuantityMap = cartQuantityMap,
                        onEntityClick = onEntityClick,
                        onAddToCart = onAddToCart,
                        onUpdateQuantity = onUpdateQuantity,
                        onOpenCatalog = onOpenCatalog
                    )
                }
            }

            // Bottom space for floating cart
            item {
                Spacer(modifier = Modifier.height(if (cartItems.isNotEmpty()) 84.dp else 28.dp))
            }
        }
    }
}

private data class MedicalCategory(
    val name: String,
    val query: String,
    val imageUrl: String,
    val badgeText: String? = null,
    val backgroundColor: Color = Color(0xFFFFF7ED)
)

@Composable
private fun MedicineCategoryRail(
    onSelectCategory: (String) -> Unit,
    onSeeAll: () -> Unit = {}
) {
    val categories = remember {
        listOf(
            MedicalCategory(
                name = "Pain & Fever",
                query = "Pain & Fever",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/059346/dolo-650mg-strip-of-15-tablets-front-2-1753347026-non-watermark.jpg",
                badgeText = "Best Seller",
                backgroundColor = Color(0xFFFFF5ED)
            ),
            MedicalCategory(
                name = "Cold & Cough",
                query = "Cold & Cough",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022615/benadryl-cough-formula-bottle-of-150ml-syrup-side-6.1-1785588733-non-watermark.jpg",
                badgeText = "Trending",
                backgroundColor = Color(0xFFEFF6FF)
            ),
            MedicalCategory(
                name = "Diabetes Care",
                query = "Diabetes",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/085775/glycomet-500mg-strip-of-10-tablets-box-front-1-1756904771-non-watermarked.jpg",
                badgeText = null,
                backgroundColor = Color(0xFFF0FDF4)
            ),
            MedicalCategory(
                name = "Antibiotics",
                query = "Antibiotics",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/255148/augmentin-duo-625mg-strip-of-10-tablets-box-front-1-1756827387-non-watermarked.jpg",
                badgeText = "Rx Verified",
                backgroundColor = Color(0xFFFEF3C7)
            ),
            MedicalCategory(
                name = "Vitamins",
                query = "Vitamins",
                imageUrl = "https://cdn01.pharmeasy.in/dam/productsnowatermark/022236/becosules-strip-of-20-capsules-front-2-1756894147-non-watermarked.jpg",
                badgeText = "Trending",
                backgroundColor = Color(0xFFFFF1F2)
            ),
            MedicalCategory(
                name = "Acidity & Gas",
                query = "Acidity & Gas",
                imageUrl = "https://cdn01.pharmeasy.in/dam/products_otc/255390/digene-gel-acidity-gas-relief-200ml-mint-flavour-sugar-free-2-1710939921.jpg",
                badgeText = "Fast Relief",
                backgroundColor = Color(0xFFFAF5FF)
            )
        )
    }

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Popular Categories",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color(0xFF0F172A)
                )
                Text(
                    text = "Genuine medicines verified by certified pharmacists",
                    fontSize = 11.5.sp,
                    color = Color(0xFF64748B)
                )
            }
            TextButton(
                onClick = onSeeAll,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "See All",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0E675E)
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = Color(0xFF0E675E),
                        modifier = Modifier.size(13.dp)
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 2.dp)
        ) {
            items(categories) { cat ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier
                        .width(86.dp)
                        .clickable { onSelectCategory(cat.query) }
                ) {
                    // Soft Pastel Rounded Container (Tata 1mg signature benchmark)
                    Surface(
                        color = cat.backgroundColor,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(86.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            ProductImage(
                                imageUrl = cat.imageUrl,
                                contentDescription = cat.name,
                                contentScale = ContentScale.Fit,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(horizontal = 8.dp, vertical = 6.dp)
                            )
                            if (cat.badgeText != null) {
                                Surface(
                                    color = Color(0xFFEA580C),
                                    shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp, bottomStart = 14.dp, bottomEnd = 14.dp),
                                    modifier = Modifier
                                        .align(Alignment.BottomCenter)
                                        .fillMaxWidth()
                                        .height(17.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = cat.badgeText,
                                            fontSize = 8.5.sp,
                                            fontWeight = FontWeight.Black,
                                            color = Color.White
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = cat.name,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E293B),
                        minLines = 2,
                        maxLines = 2,
                        lineHeight = 14.sp,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * Dual Service Hero Tiles (Medicines vs Lab Tests 50/50 split).
 */
@Composable
private fun HeroDualActionCards(
    onMedicinesClick: () -> Unit,
    onLabTestsClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Medicines Tile
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onMedicinesClick),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 1.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Column {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "FLAT 20% OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Medicines",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Express Delivery",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Order Now",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF0E675E)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF0E675E),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }

        // Lab Tests Tile
        Surface(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onLabTestsClick),
            shape = RoundedCornerShape(18.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
            shadowElevation = 1.dp
        ) {
            Box(modifier = Modifier.fillMaxWidth().padding(14.dp)) {
                Column {
                    Surface(
                        color = Color(0xFFDBEAFE),
                        shape = RoundedCornerShape(6.dp)
                    ) {
                        Text(
                            text = "UP TO 70% OFF",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color(0xFF1E40AF),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Lab Tests",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Free sample pickup",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Book Test",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF2563EB)
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = Color(0xFF2563EB),
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Tiered Promotional Campaigns Strip (HEALTH30, SAVE10 - Database Authoritative).
 */
@Composable
private fun PromotionalCampaignsStrip(
    campaigns: List<CouponCampaign>,
    onSelectCampaign: (String) -> Unit
) {
    if (campaigns.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.LocalOffer,
                    contentDescription = null,
                    tint = Color(0xFF0E675E),
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Special Offers & Discounts",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Black,
                    color = Color(0xFF0F172A)
                )
            }
            Text(
                text = "Apply at Cart",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF0E675E)
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            campaigns.forEachIndexed { index, camp ->
                val gradient = when (index % 3) {
                    0 -> Brush.horizontalGradient(listOf(Color(0xFF064E3B), Color(0xFF047857)))
                    1 -> Brush.horizontalGradient(listOf(Color(0xFF1E1B4B), Color(0xFF4338CA)))
                    else -> Brush.horizontalGradient(listOf(Color(0xFF155E75), Color(0xFF0891B2)))
                }

                Surface(
                    shape = RoundedCornerShape(14.dp),
                    shadowElevation = 1.dp,
                    modifier = Modifier
                        .width(220.dp)
                        .clickable { onSelectCampaign(camp.code) }
                ) {
                    Box(
                        modifier = Modifier
                            .background(gradient)
                            .padding(12.dp)
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    color = Color.White.copy(alpha = 0.2f),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = camp.title.ifBlank { camp.badge.ifBlank { "OFFER" } },
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color.White,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Icon(
                                    imageVector = Icons.Default.ConfirmationNumber,
                                    contentDescription = null,
                                    tint = Color.White.copy(alpha = 0.6f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Text(
                                text = camp.description.ifBlank { "Min order ${MoneyFormatter.format(camp.minOrderValue)}" },
                                fontSize = 11.sp,
                                color = Color.White.copy(alpha = 0.9f),
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Surface(
                                color = Color.White,
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "USE CODE: ",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        text = camp.code,
                                        fontSize = 10.5.sp,
                                        fontWeight = FontWeight.Black,
                                        color = Color(0xFF0F172A)
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

/**
 * Assisted Commerce Ribbon (Upload Rx / WhatsApp / Call 24x7).
 */
@Composable
private fun AssistedCommerceRibbon(
    onUploadPrescription: () -> Unit,
    onContactSupport: (String) -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp),
        shape = RoundedCornerShape(16.dp),
        color = Color.White,
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shadowElevation = 0.5.dp
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VerifiedUser,
                        contentDescription = null,
                        tint = Color(0xFF059669),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "ORDER WITH ASSISTANCE",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF065F46),
                        letterSpacing = 0.5.sp
                    )
                }
                Surface(
                    color = Color.White,
                    shape = RoundedCornerShape(4.dp),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Color(0xFF86EFAC))
                ) {
                    Text(
                        text = "Rx Required",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857),
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Don't know exact medicine name? Upload prescription or message our pharmacist directly.",
                fontSize = 11.5.sp,
                color = Color(0xFF047857),
                lineHeight = 16.sp
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Upload Rx Pill Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onUploadPrescription),
                    color = Color(0xFF0E675E),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.FileUpload,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Upload Rx",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }

                // WhatsApp Pill Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onContactSupport("whatsapp") },
                    color = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.Chat,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "WhatsApp",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    }
                }

                // Call Pharmacist Pill Button
                Surface(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { onContactSupport("call") },
                    color = Color.White,
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC))
                ) {
                    Row(
                        modifier = Modifier.padding(vertical = 9.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Phone,
                            contentDescription = null,
                            tint = Color(0xFF059669),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(5.dp))
                        Text(
                            text = "Call 24x7",
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------------------
// Medical Canopy Header & Sticky Search Chrome
// ---------------------------------------------------------------------------

@Composable
fun HeaderCanopy(
    homeContext: HomeContext?,
    selectedAddress: ApiAddress?,
    calculatedEtaMinutes: Int,
    locationHeaderLabel: String?,
    isLocating: Boolean,
    onChangeAddress: () -> Unit,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit = {},
    onSearchClick: (String) -> Unit,
    onUploadPrescription: () -> Unit
) {
    val view = LocalView.current
    val context = LocalContext.current

    // Synchronize notification status bar icons with dark medical teal canopy
    DisposableEffect(view) {
        var currentContext = context
        var activity: Activity? = null
        while (currentContext is ContextWrapper) {
            if (currentContext is Activity) {
                activity = currentContext
                break
            }
            currentContext = currentContext.baseContext
        }
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            val prevLightStatusBars = insetsController.isAppearanceLightStatusBars
            val prevStatusBarColor = window.statusBarColor

            insetsController.isAppearanceLightStatusBars = false
            window.statusBarColor = android.graphics.Color.TRANSPARENT

            onDispose {
                insetsController.isAppearanceLightStatusBars = prevLightStatusBars
                window.statusBarColor = prevStatusBarColor
            }
        } else {
            onDispose {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                color = Color(0xFF0E675E),
                shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp)
            )
            .statusBarsPadding()
            .padding(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 14.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(11.dp)
        ) {
            // Location Header & Profile
            CanopyLocationBar(
                context = homeContext,
                selectedAddress = selectedAddress,
                calculatedEtaMinutes = calculatedEtaMinutes,
                locationHeaderLabel = locationHeaderLabel,
                isLocating = isLocating,
                onChangeAddress = onChangeAddress,
                onProfileClick = onProfileClick,
                onNotificationsClick = onNotificationsClick
            )

            // High-Utility Stadium Search Bar with Hairline Divider and Upload Rx action
            CanopySearchBar(
                onClick = { onSearchClick("") },
                onUploadPrescription = onUploadPrescription
            )
        }
    }
}

@Composable
private fun CanopyLocationBar(
    context: HomeContext?,
    selectedAddress: ApiAddress? = null,
    calculatedEtaMinutes: Int = 0,
    locationHeaderLabel: String? = null,
    isLocating: Boolean = false,
    onChangeAddress: () -> Unit,
    onProfileClick: () -> Unit,
    onNotificationsClick: () -> Unit = {}
) {
    val hasAddress = context?.hasAddress == true || selectedAddress != null || !locationHeaderLabel.isNullOrBlank()

    val titleTag = (when {
        selectedAddress?.tag?.isNotBlank() == true -> selectedAddress.tag
        !locationHeaderLabel.isNullOrBlank() && locationHeaderLabel != "Select Delivery Location" -> locationHeaderLabel.take(20)
        hasAddress -> "Home"
        else -> "Deliver to"
    } ?: "Deliver to")

    val addressLine = (when {
        selectedAddress?.addressLine?.isNotBlank() == true -> selectedAddress.addressLine
        context?.displayLabel?.isNotBlank() == true -> context.displayLabel
        !locationHeaderLabel.isNullOrBlank() && locationHeaderLabel != "Select Delivery Location" -> locationHeaderLabel
        else -> "Select delivery location"
    } ?: "Select delivery location")

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Location Pin + Delivering To Title & ETA
        Row(
            modifier = Modifier
                .weight(1f)
                .clickable(onClick = onChangeAddress),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (isLocating && selectedAddress == null) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.NearMe,
                            contentDescription = "Delivery Location",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "DELIVERING TO",
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White.copy(alpha = 0.75f),
                    letterSpacing = 0.6.sp
                )
                Spacer(modifier = Modifier.height(1.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = titleTag,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = "Change Location",
                        tint = Color.White.copy(alpha = 0.85f),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (calculatedEtaMinutes > 0) "⚡ $calculatedEtaMinutes MINS" else "⚡ EXPRESS",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Black,
                            color = Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(1.dp))
                Text(
                    text = addressLine,
                    fontSize = 11.5.sp,
                    color = Color.White.copy(alpha = 0.82f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        Spacer(modifier = Modifier.width(8.dp))

        val localCtx = LocalContext.current
        var unreadCount by remember {
            mutableIntStateOf(com.commerceos.android.notifications.LocalNotificationStorage.getNotifications(localCtx).count { !it.isRead })
        }
        val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                    unreadCount = com.commerceos.android.notifications.LocalNotificationStorage.getNotifications(localCtx).count { !it.isRead }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose {
                lifecycleOwner.lifecycle.removeObserver(observer)
            }
        }

        // Notification Bell Button with live unread count badge (Properly layered outside circle)
        Box(
            modifier = Modifier.size(44.dp),
            contentAlignment = Alignment.Center
        ) {
            Surface(
                onClick = onNotificationsClick,
                color = Color.White.copy(alpha = 0.18f),
                shape = CircleShape,
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.25f)),
                modifier = Modifier
                    .size(38.dp)
                    .align(Alignment.Center)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        Icons.Default.Notifications,
                        contentDescription = "Notifications",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (unreadCount > 0) {
                Surface(
                    color = Color(0xFFEF4444),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Color(0xFF0E675E)),
                    shadowElevation = 2.dp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .clickable(onClick = onNotificationsClick)
                        .sizeIn(minWidth = 18.dp, minHeight = 18.dp)
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = if (unreadCount > 99) "99+" else unreadCount.toString(),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            textAlign = TextAlign.Center,
                            lineHeight = 11.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CanopySearchBar(
    onClick: () -> Unit,
    onUploadPrescription: () -> Unit
) {
    val searchHints = remember {
        listOf(
            "Search medicines, e.g. Dolo 650...",
            "Search for Paracetamol, Crocin...",
            "Search for Augmentin 625 Duo...",
            "Search for Benadryl Cough syrup...",
            "Search for Glycomet, Insulin...",
            "Search for Pan 40, Digene..."
        )
    }
    var hintIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3000)
            hintIndex = (hintIndex + 1) % searchHints.size
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Left Floating Stadium Search Pill (Fine-stroke vector icon + ample breathing room)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 2.dp,
            modifier = Modifier
                .weight(1f)
                .height(48.dp)
                .clickable(onClick = onClick)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    painter = painterResource(id = R.drawable.ic_thin_search),
                    contentDescription = "Search",
                    tint = Color(0xFFE84335),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                AnimatedContent(
                    targetState = hintIndex,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "SearchHintAnimation"
                ) { targetIndex ->
                    Text(
                        text = searchHints[targetIndex],
                        fontSize = 13.sp,
                        color = Color(0xFF64748B),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Right Floating Stadium Upload Prescription Pill (Tata 1mg signature benchmark)
        Surface(
            color = Color.White,
            shape = RoundedCornerShape(24.dp),
            shadowElevation = 2.dp,
            modifier = Modifier
                .height(48.dp)
                .clickable(onClick = onUploadPrescription)
        ) {
            Row(
                modifier = Modifier.padding(start = 6.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = Color(0xFFEF4444),
                    shape = CircleShape,
                    modifier = Modifier.size(34.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_camera),
                            contentDescription = "Upload Rx",
                            tint = Color.White,
                            modifier = Modifier.size(17.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(7.dp))
                Column(verticalArrangement = Arrangement.Center) {
                    Text(
                        text = "Upload",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B),
                        fontWeight = FontWeight.Medium,
                        lineHeight = 12.sp
                    )
                    Text(
                        text = "Prescription",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF0F172A),
                        lineHeight = 13.sp
                    )
                }
            }
        }
    }
}



// ---------------------------------------------------------------------------
// Server-authored feed sections
// ---------------------------------------------------------------------------

@Composable
fun HeroCampaignWidget(hero: HomeHeroDto, onCta: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(180.dp)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF064E3B),
                        Color(0xFF047857),
                        Color(0xFF0C831F)
                    )
                ),
                RoundedCornerShape(18.dp)
            )
    ) {
        // Editorial Campaign Photography / Backdrop
        if (!hero.imageUrl.isNullOrBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(0.52f)
                    .align(Alignment.CenterEnd)
            ) {
                ProductImage(
                    imageUrl = hero.imageUrl,
                    contentDescription = hero.title,
                    contentScale = ContentScale.Crop,
                    shape = RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp),
                    modifier = Modifier.fillMaxSize()
                )
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(
                                    Color(0xFF064E3B),
                                    Color(0xFF064E3B).copy(alpha = 0.7f),
                                    Color.Transparent
                                )
                            )
                        )
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.70f)
                .padding(18.dp),
            verticalArrangement = Arrangement.Center
        ) {
            Surface(
                color = Color.White.copy(alpha = 0.2f),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = hero.badge.ifBlank { "⚡ 10-MIN PHARMACY" },
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = hero.title,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                lineHeight = 22.sp,
                maxLines = 2
            )
            Spacer(modifier = Modifier.height(3.dp))
            Text(
                text = hero.subtitle,
                fontSize = 11.sp,
                color = Color(0xFFD1FAE5),
                maxLines = 2,
                lineHeight = 15.sp
            )
            Spacer(modifier = Modifier.height(10.dp))
            Button(
                onClick = onCta,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                modifier = Modifier.height(32.dp)
            ) {
                Text(
                    text = hero.ctaText.ifBlank { "Shop now" },
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF064E3B)
                )
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
        // 1. Shimmering Hero Banner Placeholder
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().height(130.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                SkeletonText(width = 160.dp, height = 16.dp)
            }
        }

        // 2. Row of Category Circular Icons (Blinkit / Zepto pattern)
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            repeat(4) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    SkeletonProductImage(
                        modifier = Modifier.size(60.dp),
                        shape = CircleShape
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    SkeletonText(width = 44.dp, height = 10.dp)
                }
            }
        }

        // 3. Shimmering Product Cards
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
